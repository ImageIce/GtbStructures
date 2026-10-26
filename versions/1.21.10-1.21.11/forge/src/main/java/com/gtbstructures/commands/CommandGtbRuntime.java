package com.gtbstructures.commands;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.sound.GuessSounds;
import com.gtbstructures.sound.MinecraftClientSoundBridge;
import com.gtbstructures.GtbStructuresSettings.ShortcutNonAsciiCategory;
import com.gtbstructures.chat.ChatHeader;
import com.gtbstructures.chat.GuessChatAnnotator;
import com.gtbstructures.gtw.GtwLogic;
import com.gtbstructures.items.HandheldItemTracker;
import com.gtbstructures.overlay.HudPanelScale;
import com.gtbstructures.overlay.WorldEnvironmentLogic;
import com.gtbstructures.scoreboard.InterfacePreview;
import com.gtbstructures.scoreboard.PreviewLayoutEditor;
import com.gtbstructures.scoreboard.ScoreboardConfig;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.stats.GameExporter;
import com.gtbstructures.stats.StatsArchive;
import com.gtbstructures.translation.CopyTranslation;
import com.gtbstructures.translation.ShortcutLanguageGroups;
import com.gtbstructures.translation.TranslationData;
import com.gtbstructures.util.MinecraftFormatting;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Runtime adapter for the platform-neutral /gtbs command core. */
public final class CommandGtbRuntime {
    private static final String[] ROOT_COMMANDS = {
            "gtbs", "gtbstructures", "gtb", "lrj", "copytranslation", "copyt", "trollmode", "troll", "gtw", "guess",
            "shortcutreminder", "screminder"
    };
    private static final String PLAY_GTB_COMMAND = "/play build_battle_guess_the_build";
    private static final String LOBBY_COMMAND = "/l";
    private static final String BYPASS_LOBBY_PROTECTION_COMMAND = "/l bb";
    private static final String REJOIN_COMMAND = "/rej";
    private static final int GTW_TICKS_PER_HINT = 240;
    private static final String PANEL_SCALE_RANGE = "[0.5-1.5|50%-150%]";
    private static final CommandGtb COMMAND = new CommandGtb();
    private static final GtbStructuresSettings SETTINGS = GtbStructuresSettings.INSTANCE;
    private static final Services DEFAULT_SERVICES = new DefaultServices();
    private static Services services = DEFAULT_SERVICES;
    private static final GtwSession GTW = new GtwSession();
    private static final LeaveRejoinController LEAVE_REJOIN = new LeaveRejoinController();

    private CommandGtbRuntime() {
    }

    public static String[] rootCommands() {
        String[] copy = new String[ROOT_COMMANDS.length];
        System.arraycopy(ROOT_COMMANDS, 0, copy, 0, ROOT_COMMANDS.length);
        return copy;
    }

    public static boolean isRootCommand(String root) {
        String normalizedRoot = normalizeRoot(root);
        for (String command : ROOT_COMMANDS) {
            if (command.equals(normalizedRoot)) return true;
        }
        return false;
    }

    public interface Services {
        File gameDir();
        File configDir();
        String localPlayerName();
        default String accountPlayerName() {
            return localPlayerName();
        }
        default String accountPlayerUuid() {
            return null;
        }
        void sendServerCommand(String command);
        default void sendChat(String message) {
            throw new UnsupportedOperationException("Chat sending is not configured");
        }
        default boolean playerAvailable() {
            return false;
        }
        boolean copyToClipboard(String text);
    }

    public static synchronized void configure(Services runtimeServices) {
        services = runtimeServices == null ? DEFAULT_SERVICES : runtimeServices;
        SETTINGS.init(services.configDir());
        applySettingsToRuntime();
    }

    public static synchronized void resetForTest() {
        services = DEFAULT_SERVICES;
        SETTINGS.resetForTest();
        GTW.reset();
        LEAVE_REJOIN.reset();
        applySettingsToRuntime();
    }

    public static GtbStructuresSettings settingsForTest() {
        return SETTINGS;
    }

    public static Result execute(String root, String rawArgs) {
        String normalizedRoot = normalizeRoot(root);
        if ("gtbstructures".equals(normalizedRoot)) {
            normalizedRoot = "gtbs";
        }
        if ("copyt".equals(normalizedRoot)) {
            normalizedRoot = "copytranslation";
        }
        if ("screminder".equals(normalizedRoot)) {
            normalizedRoot = "shortcutreminder";
        }

        if ("troll".equals(normalizedRoot)) {
            return handleOneShotTroll(rawArgs, "/troll <message>");
        }
        if ("gtbs".equals(normalizedRoot)) {
            String oneShotMessage = extractGtbsTrollMessage(rawArgs);
            if (oneShotMessage != null) {
                return handleOneShotTroll(oneShotMessage, "/gtbs troll <message>");
            }
        }

        String[] args = parseExecutionArgs(rawArgs);

        if ("gtw".equals(normalizedRoot)) {
            return handleGtw(args, "/gtw");
        }
        if ("guess".equals(normalizedRoot)) {
            return handleGuess(args, "/guess");
        }
        if ("trollmode".equals(normalizedRoot)) {
            return executeGtbs(prefixedArgs("trollmode", args));
        }
        if ("gtbs".equals(normalizedRoot)) {
            return executeGtbs(args);
        }
        if ("gtb".equals(normalizedRoot)) {
            if (args.length != 0) {
                return Result.messages(CommandGtb.head + "Usage: /gtb");
            }
            services.sendServerCommand(PLAY_GTB_COMMAND);
            return Result.empty();
        }
        if ("lrj".equals(normalizedRoot)) {
            return handleLeaveRejoin(args, "/lrj");
        }
        if ("copytranslation".equals(normalizedRoot)) {
            return executeGtbs(prefixedArgs("copytranslation", args));
        }
        if ("shortcutreminder".equals(normalizedRoot)) {
            return executeGtbs(prefixedArgs("shortcutreminder", args));
        }

        return Result.messages(CommandGtb.head + "Unknown command root. Usage: see /gtbs help");
    }

    public static Result onClientTick() {
        ArrayList<String> messages = new ArrayList<String>();
        messages.addAll(prefixAll(GTW.onClientTick(), CommandGtb.head));
        messages.addAll(StatsArchive.drainAsyncMessages());
        return Result.messages(messages);
    }

    /** Advances an explicit /lrj independently of the GTBS master switch. */
    public static void onLeaveRejoinClientTick() {
        LeaveRejoinController.Action action = LEAVE_REJOIN.tick(services.playerAvailable());
        if (action == LeaveRejoinController.Action.REJOIN) {
            services.sendServerCommand(REJOIN_COMMAND);
        }
    }

    /** Keeps a pending /lrj alive and marks the next ready play connection. */
    public static void onClientConnectionBoundary(boolean playReady) {
        LEAVE_REJOIN.onConnectionBoundary(playReady);
    }

    public static void discardPendingAutonomousActions() {
        GTW.reset();
        StatsArchive.discardAsyncMessages();
    }

    public static List<String> complete(String root, String rawArgs) {
        String normalizedRoot = normalizeRoot(root);
        if ("gtbstructures".equals(normalizedRoot)) {
            normalizedRoot = "gtbs";
        }
        if ("copyt".equals(normalizedRoot)) {
            normalizedRoot = "copytranslation";
        }
        if ("screminder".equals(normalizedRoot)) {
            normalizedRoot = "shortcutreminder";
        }

        if ("gtb".equals(normalizedRoot)) {
            return Collections.emptyList();
        }
        if ("lrj".equals(normalizedRoot)) {
            return completeGtbsShortcut("lrj", rawArgs);
        }
        if ("copytranslation".equals(normalizedRoot)) {
            return completeCopyTranslation(rawArgs);
        }
        if ("gtw".equals(normalizedRoot)) {
            return completeGtw(rawArgs);
        }
        if ("trollmode".equals(normalizedRoot)) {
            return completeGtbsShortcut("trollmode", rawArgs);
        }
        if ("shortcutreminder".equals(normalizedRoot)) {
            return completeGtbsShortcut("shortcutreminder", rawArgs);
        }
        if ("guess".equals(normalizedRoot)) {
            return Collections.emptyList();
        }
        if ("troll".equals(normalizedRoot)) {
            return Collections.emptyList();
        }
        if (!"gtbs".equals(normalizedRoot)) {
            return completeRoot(rawArgs);
        }

        String[] args = parseCompletionArgs(rawArgs);
        if (args.length > 0 && "troll".equalsIgnoreCase(args[0])) {
            return Collections.emptyList();
        }
        if (args.length > 0 && "gtw".equalsIgnoreCase(args[0])) {
            return completeForwardedGtw(rawArgs);
        }
        if (args.length > 0 && "copytranslation".equalsIgnoreCase(args[0])) {
            return completeForwardedCopyTranslation(rawArgs);
        }
        List<String> suggestions = COMMAND.addTabCompletionOptions(null, args, null);
        if (suggestions == null || suggestions.isEmpty()) {
            return Collections.emptyList();
        }
        return replaceLastArgument(rawArgs, suggestions);
    }

    private static Result executeGtbs(String[] args) {
        if (args.length == 0) {
            CommandGtb.HelpOutput output = CommandGtb.buildHelpOutput(new String[0]);
            return Result.helpPage(output.messages, output.navigation);
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if ("help".equals(first)) {
            CommandGtb.HelpOutput output = CommandGtb.buildHelpOutput(sliceFrom(args, 1));
            List<String> messages = new ArrayList<String>(output.messages);
            if (!SETTINGS.previewQuickstartRan) {
                messages.add(CommandGtb.head + "Quickstart: use \u00a7f/gtbs preview\u00a77"
                        + " to configure and preview most visual settings in the GUI.");
            }
            return Result.helpPage(messages, output.navigation);
        }
        if ("search".equals(first)) {
            return executeGtbs(prefixedArgs("help", args));
        }
        if ("gtb".equals(first)) {
            return execute("gtb", join(sliceFrom(args, 1)));
        }
        if ("lrj".equals(first)) {
            return handleLeaveRejoin(sliceFrom(args, 1), "/gtbs lrj");
        }
        if ("fireworks".equals(first)) {
            return handleEndGameFireworkSounds(sliceFrom(args, 1));
        }
        if (isHintStructureCommand(first)) {
            return handleHintStructure(sliceFrom(args, 1));
        }
        if ("toggle".equals(first)) {
            if (args.length != 1) return Result.messages(CommandGtb.head + "Usage: /gtbs toggle");
            SETTINGS.modEnabled = !SETTINGS.modEnabled;
            saveSettings();
            return Result.messages(CommandGtb.head + "GTBS is currently " + onOff(SETTINGS.modEnabled));
        }
        if ("overlay".equals(first)) {
            return handleHintStructure(args.length == 1
                    ? new String[] {"background", "toggle"}
                    : prefixedArgs("background", sliceFrom(args, 1)));
        }
        if ("setlocation".equals(first)) {
            return handleHintStructure(prefixedArgs("setlocation", sliceFrom(args, 1)));
        }
        if ("setcolor".equals(first)) {
            return handleHintStructure(prefixedArgs("setcolor", sliceFrom(args, 1)));
        }
        if ("colorlist".equals(first)) {
            if (args.length != 1) {
                return Result.messages(CommandGtb.head + "Usage: /gtbs colorlist");
            }
            return Result.messages(CommandGtb.head + "Available color codes: " + colorList());
        }
        if ("stats".equals(first)) {
            return handleStats(sliceFrom(args, 1));
        }
        if ("openstatsfolder".equals(first)) {
            if (args.length != 1) return Result.empty();
            GameExporter.openStatsFolderAsync(services.gameDir());
            return Result.empty();
        }
        if ("export".equals(first)) {
            if (args.length != 1) return Result.messages(CommandGtb.head + "Usage: /gtbs export");
            return Result.messages(CommandGtb.head + GameStats.INSTANCE.manualExport(services.gameDir()));
        }
        if ("exportraw".equals(first)) {
            if (args.length != 1) return Result.messages(CommandGtb.head + "Usage: /gtbs exportraw");
            return Result.messages(CommandGtb.head + GameStats.INSTANCE.manualExportRaw(services.gameDir()));
        }
        if ("tracking".equals(first)) {
            if (args.length != 1) return Result.messages(CommandGtb.head + "Usage: /gtbs tracking");
            SETTINGS.trackingEnabled = !GameStats.INSTANCE.isTrackingEnabled();
            GameStats.INSTANCE.setTrackingEnabled(SETTINGS.trackingEnabled);
            saveSettings();
            return Result.messages(CommandGtb.head + "Stats tracking is currently " + onOff(SETTINGS.trackingEnabled));
        }
        if ("autorequeue".equals(first)) {
            if (args.length == 1) {
                SETTINGS.autoRequeue = !GameStats.INSTANCE.isAutoRequeue();
                GameStats.INSTANCE.setAutoRequeue(SETTINGS.autoRequeue);
                saveSettings();
                return Result.messages(CommandGtb.head + "Auto-requeue is currently " + onOff(SETTINGS.autoRequeue));
            }
            if (!"delay".equalsIgnoreCase(args[1]) || args.length > 3) {
                return Result.messages(CommandGtb.head + "Usage: /gtbs autorequeue [delay <time>]");
            }
            if (args.length == 2) {
                return Result.messages(CommandGtb.head + "Auto-requeue delay is currently \u00a7f"
                        + GtbStructuresSettings.formatAutoRequeueDelaySeconds(
                        SETTINGS.autoRequeueDelaySeconds) + " seconds.");
            }
            Float delay = GtbStructuresSettings.parseAutoRequeueDelaySeconds(args[2]);
            if (delay == null) {
                return Result.messages(CommandGtb.head
                        + "Please enter a time from 0.0 to 10.0 seconds.");
            }
            SETTINGS.autoRequeueDelaySeconds = delay.floatValue();
            GameStats.INSTANCE.setAutoRequeueDelaySeconds(delay.floatValue());
            saveSettings();
            return Result.messages(CommandGtb.head + "Auto-requeue delay set to \u00a7f"
                    + GtbStructuresSettings.formatAutoRequeueDelaySeconds(delay.floatValue())
                    + " seconds.");
        }
        if ("updatenotice".equals(first)) {
            if (args.length == 1) {
                SETTINGS.updateNoticeEnabled = !SETTINGS.updateNoticeEnabled;
            } else if (args.length == 2) {
                Boolean enabled = parseToggleBoolean(args[1], SETTINGS.updateNoticeEnabled);
                if (enabled == null) {
                    return Result.messages(CommandGtb.head + "Usage: /gtbs updatenotice [on|off|toggle]");
                }
                SETTINGS.updateNoticeEnabled = enabled.booleanValue();
            } else {
                return Result.messages(CommandGtb.head + "Usage: /gtbs updatenotice [on|off|toggle]");
            }
            saveSettings();
            return Result.messages(CommandGtb.head + "Update notice is currently "
                    + onOff(SETTINGS.updateNoticeEnabled));
        }
        if ("haveibuilt".equals(first)) {
            if (args.length != 1) return Result.messages(CommandGtb.head + "Usage: /gtbs haveibuilt");
            return Result.messages(CommandGtb.head + GameStats.INSTANCE.formatHaveIBuilt(services.localPlayerName()));
        }
        if ("whohasbuilt".equals(first)) {
            if (args.length != 1) return Result.messages(CommandGtb.head + "Usage: /gtbs whohasbuilt");
            return Result.messages(CommandGtb.head + GameStats.INSTANCE.formatWhoHasBuilt());
        }
        if ("showallmatching".equals(first)) {
            return handleShowAllMatching(sliceFrom(args, 1));
        }
        if ("themepointreveal".equals(first)) {
            return handleThemePointReveal(sliceFrom(args, 1));
        }
        if ("shortcutreminder".equals(first)) {
            return handleShortcutReminder(sliceFrom(args, 1));
        }
        if ("copytranslation".equals(first)) {
            return handleCopyTranslation(sliceFrom(args, 1));
        }
        if ("gtw".equals(first)) {
            return handleGtw(sliceFrom(args, 1), "/gtbs gtw");
        }
        if ("guess".equals(first)) {
            return handleGuess(sliceFrom(args, 1), "/gtbs guess");
        }
        if ("scoreboard".equals(first)) {
            return handleScoreboard(sliceFrom(args, 1));
        }
        if ("spawnegg".equals(first)) {
            return handleSpawnEgg(sliceFrom(args, 1));
        }
        if ("handhelditem".equals(first)) {
            return handleHandheldItem(sliceFrom(args, 1));
        }
        if (isEnvironmentCommand(first)) {
            return handleEnvironment(sliceFrom(args, 1));
        }
        if ("cooldowncounter".equals(first)) {
            return handleCooldownCounter(sliceFrom(args, 1));
        }
        if ("guesspeek".equals(first)) {
            return handleGuessPeek(sliceFrom(args, 1));
        }
        if ("guesssound".equals(first)) return handleGuessSound(sliceFrom(args, 1));
        if ("scrambleguess".equals(first)) {
            return handleScrambleGuess(sliceFrom(args, 1));
        }
        if ("trollmode".equals(first)) {
            return handleTrollMode(sliceFrom(args, 1));
        }
        if ("header".equals(first)) {
            return handleHeader(sliceFrom(args, 1));
        }
        if ("copyguess".equals(first)) {
            if (args.length >= 2) {
                String guess = GuessChatAnnotator.decodeCopyPayload(args[1]);
                if (guess != null && !guess.isEmpty()) services.copyToClipboard(guess);
            }
            return Result.empty();
        }
        if ("preview".equals(first)) {
            if (args.length != 1) return Result.messages(CommandGtb.head + "Usage: /gtbs preview");
            boolean enabled = InterfacePreview.INSTANCE.toggle();
            if (!SETTINGS.previewQuickstartRan) {
                SETTINGS.previewQuickstartRan = true;
                saveSettings();
            }
            return Result.messages(CommandGtb.head + (enabled
                    ? "Interface preview is currently \u00a7aON\u00a77. Click \u00a7fDone\u00a77 or press \u00a7fEsc\u00a77 to exit."
                    : "Interface preview is currently \u00a7cOFF"));
        }

        return Result.messages(CommandGtb.head + "Unknown argument. Usage: see /gtbs help");
    }

    private static Result handleOneShotTroll(String rawMessage, String usage) {
        String markedMessage = com.gtbstructures.chat.TrollMode.prepareOneShot(rawMessage);
        if (markedMessage == null) {
            return Result.messages(CommandGtb.head + "Usage: " + usage);
        }
        services.sendChat(markedMessage);
        return Result.empty();
    }

    private static String extractGtbsTrollMessage(String rawArgs) {
        if (rawArgs == null) return null;
        int start = 0;
        while (start < rawArgs.length() && Character.isWhitespace(rawArgs.charAt(start))) start++;
        int end = start + "troll".length();
        if (end > rawArgs.length() || !rawArgs.regionMatches(true, start, "troll", 0, "troll".length())) {
            return null;
        }
        if (end < rawArgs.length() && !Character.isWhitespace(rawArgs.charAt(end))) return null;
        while (end < rawArgs.length() && Character.isWhitespace(rawArgs.charAt(end))) end++;
        return rawArgs.substring(end);
    }

    private static Result handleHintStructure(String[] args) {
        if (args.length == 0) {
            SETTINGS.enabled = !SETTINGS.enabled;
            saveSettings();
            return Result.messages(CommandGtb.head + "Hint structure overlay is currently " + onOff(SETTINGS.enabled));
        }
        if ("background".equalsIgnoreCase(args[0])) {
            if (args.length == 1) {
                return Result.messages(CommandGtb.head
                        + backgroundStatusMessage("Hint structure background", SETTINGS.hintStructureBackground));
            }
            if (args.length != 2) {
                return Result.messages(CommandGtb.head + "Usage: /gtbs hintstructure background <on|off>");
            }
            Boolean next = parseToggleBoolean(args[1], SETTINGS.hintStructureBackground);
            if (next == null) return Result.messages(CommandGtb.head + "Usage: /gtbs hintstructure background <on|off>");
            SETTINGS.hintStructureBackground = next.booleanValue();
            saveSettings();
            return Result.messages(CommandGtb.head
                    + backgroundStatusMessage("Hint structure background", SETTINGS.hintStructureBackground));
        }
        if ("scale".equalsIgnoreCase(args[0])) {
            return handlePanelScale(args, "hint", "Hint structure");
        }
        if ("setlocation".equalsIgnoreCase(args[0])) {
            return handlePosition(args, "Text", "/gtbs hintstructure setlocation <x | y> <value>",
                    SETTINGS.posX, SETTINGS.posY, (axis, value) -> {
                        if ("x".equals(axis)) SETTINGS.posX = value;
                        else SETTINGS.posY = value;
                    });
        }
        if ("setcolor".equalsIgnoreCase(args[0])) {
            return handleHintStructureColor(args);
        }
        return Result.messages(CommandGtb.head + "Usage: /gtbs hintstructure [setlocation|setcolor|background|scale] ...");
    }

    private static Result handleHintStructureColor(String[] args) {
        if (args.length == 2) {
            String target = args[1].toLowerCase(Locale.ROOT);
            String message = currentHintStructureColorMessage(target);
            return Result.messages(CommandGtb.head + (message == null
                    ? "Usage: /gtbs hintstructure setcolor <bracket|number|hyphen> <color>"
                    : message));
        }
        if (args.length != 3) {
            return Result.messages(CommandGtb.head + "Usage: /gtbs hintstructure setcolor <bracket|number|hyphen> <color>");
        }
        String target = args[1].toLowerCase(Locale.ROOT);
        String color = normalizeCommandColor(args[2]);
        if (color == null) return Result.messages(CommandGtb.head + invalidColorMessage());
        String colorCode = color.substring(1);
        String label;
        if ("bracket".equals(target)) {
            SETTINGS.colorBrackets = color;
            label = "Bracket";
        } else if ("number".equals(target)) {
            SETTINGS.colorNumbers = color;
            label = "Number";
        } else if ("hyphen".equals(target)) {
            SETTINGS.colorHyphens = color;
            label = "Hyphen";
        } else {
            return Result.messages(CommandGtb.head + "Usage: /gtbs hintstructure setcolor <bracket|number|hyphen> <color>");
        }
        saveSettings();
        return Result.messages(CommandGtb.head + label + " color set to " + color + colorCode);
    }

    private static Result handleShowAllMatching(String[] args) {
        if (args.length == 0) {
            SETTINGS.showAllMatching = !SETTINGS.showAllMatching;
            saveSettings();
            return Result.messages(CommandGtb.head + "Show all matching is currently " + onOff(SETTINGS.showAllMatching));
        }
        if (args.length >= 1 && "showenglish".equalsIgnoreCase(args[0])) {
            if (args.length == 1) {
                SETTINGS.showAllMatchingShowEnglish = !SETTINGS.showAllMatchingShowEnglish;
            } else if (args.length == 2) {
                Boolean enabled = parseOptionalBoolean(args[1]);
                if (enabled == null) {
                    return Result.messages(CommandGtb.head + "Usage: /gtbs showallmatching showenglish <true|false>");
                }
                SETTINGS.showAllMatchingShowEnglish = enabled.booleanValue();
            } else {
                return Result.messages(CommandGtb.head + "Usage: /gtbs showallmatching showenglish <true|false>");
            }
            saveSettings();
            return Result.messages(CommandGtb.head + "Show all matching (showEnglish) is currently "
                    + onOff(SETTINGS.showAllMatchingShowEnglish));
        }
        if (args.length >= 1 && "showcount".equalsIgnoreCase(args[0])) {
            if (args.length == 1) return showAllMatchingCountStatus();
            if (args.length != 2) {
                return Result.messages(CommandGtb.head
                        + "Usage: /gtbs showallmatching showcount [on|off]");
            }
            Boolean enabled = parseOptionalBoolean(args[1]);
            if (enabled == null) {
                return Result.messages(CommandGtb.head
                        + "Usage: /gtbs showallmatching showcount [on|off]");
            }
            SETTINGS.showAllMatchingShowCount = enabled.booleanValue();
            saveSettings();
            return showAllMatchingCountStatus();
        }
        return Result.messages(CommandGtb.head
                + "Usage: /gtbs showallmatching [showenglish <true|false>|showcount [on|off]]");
    }

    private static Result handleThemePointReveal(String[] args) {
        if (args.length == 0) {
            SETTINGS.themePointReveal = !SETTINGS.themePointReveal;
            saveSettings();
            return Result.messages(CommandGtb.head + "Theme point reveal is currently "
                    + onOff(SETTINGS.themePointReveal));
        }
        if (args.length == 2 && "showafterguess".equalsIgnoreCase(args[0])) {
            Boolean enabled = parseOptionalBoolean(args[1]);
            if (enabled != null) {
                SETTINGS.themePointRevealShowAfterGuess = enabled.booleanValue();
                saveSettings();
                return Result.messages(CommandGtb.head
                        + "Theme point reveal after correct guess is currently "
                        + onOff(SETTINGS.themePointRevealShowAfterGuess));
            }
        }
        return Result.messages(CommandGtb.head
                + "Usage: /gtbs themepointreveal [showafterguess <on|off>]");
    }

    private static Result handleShortcutReminder(String[] args) {
        if (args.length == 0) {
            SETTINGS.shortcutReminder = !SETTINGS.shortcutReminder;
            saveSettings();
            return shortcutStatus("Shortcut reminder", SETTINGS.shortcutReminder);
        }

        if ("showallshortcuts".equalsIgnoreCase(args[0])) {
            if (args.length == 1) return shortcutStatus("Show all shortcuts", SETTINGS.shortcutReminderShowAllShortcuts);
            if (args.length == 2) {
                Boolean enabled = parseToggleBoolean(args[1], SETTINGS.shortcutReminderShowAllShortcuts);
                if (enabled != null) {
                    SETTINGS.shortcutReminderShowAllShortcuts = enabled.booleanValue();
                    saveSettings();
                    return shortcutStatus("Show all shortcuts", SETTINGS.shortcutReminderShowAllShortcuts);
                }
            }
            return Result.messages(CommandGtb.head + "Usage: /gtbs shortcutreminder showallshortcuts [on|off|toggle]");
        }

        if ("showmultiwords".equalsIgnoreCase(args[0])) {
            if (args.length == 1) return shortcutStatus("Show multiwords", SETTINGS.shortcutReminderShowMultiwords);
            if (args.length == 2 && "showall".equalsIgnoreCase(args[1])) {
                return shortcutStatus("Show all multiwords", SETTINGS.shortcutReminderShowAllMultiwords);
            }
            if (args.length == 2) {
                Boolean enabled = parseToggleBoolean(args[1], SETTINGS.shortcutReminderShowMultiwords);
                if (enabled != null) {
                    SETTINGS.shortcutReminderShowMultiwords = enabled.booleanValue();
                    saveSettings();
                    return shortcutStatus("Show multiwords", SETTINGS.shortcutReminderShowMultiwords);
                }
            }
            if (args.length == 3 && "showall".equalsIgnoreCase(args[1])) {
                Boolean enabled = parseToggleBoolean(args[2], SETTINGS.shortcutReminderShowAllMultiwords);
                if (enabled != null) {
                    SETTINGS.shortcutReminderShowAllMultiwords = enabled.booleanValue();
                    saveSettings();
                    return shortcutStatus("Show all multiwords", SETTINGS.shortcutReminderShowAllMultiwords);
                }
            }
            return Result.messages(CommandGtb.head
                    + "Usage: /gtbs shortcutreminder showmultiwords [on|off|toggle|showall [on|off|toggle]]");
        }

        if ("shownonascii".equalsIgnoreCase(args[0])) {
            if (args.length == 1) return shortcutStatus("Show non-ASCII shortcuts", SETTINGS.shortcutReminderShowNonAscii);
            if (args.length == 2 && isShortcutNonAsciiCategory(args[1])) {
                return shortcutStatus(shortcutCategoryLabel(args[1]),
                        SETTINGS.shortcutNonAsciiCategoryEnabled(shortcutCategory(args[1])));
            }
            if (args.length == 2) {
                Boolean enabled = parseToggleBoolean(args[1], SETTINGS.shortcutReminderShowNonAscii);
                if (enabled != null) {
                    SETTINGS.setShortcutReminderShowNonAscii(enabled.booleanValue());
                    saveSettings();
                    return shortcutStatus("Show non-ASCII shortcuts", SETTINGS.shortcutReminderShowNonAscii);
                }
            }
            if (args.length == 3 && isShortcutNonAsciiCategory(args[1])) {
                ShortcutNonAsciiCategory category = shortcutCategory(args[1]);
                Boolean enabled = parseToggleBoolean(args[2], SETTINGS.shortcutNonAsciiCategoryEnabled(category));
                if (enabled != null) {
                    SETTINGS.setShortcutNonAsciiCategoryEnabled(category, enabled.booleanValue());
                    saveSettings();
                    return shortcutStatus(shortcutCategoryLabel(args[1]),
                            SETTINGS.shortcutNonAsciiCategoryEnabled(category));
                }
            }
            return Result.messages(CommandGtb.head
                    + "Usage: /gtbs shortcutreminder shownonascii "
                    + "[on|off|toggle|<extendedlatin|cyrillic|hanzi|japanese|hangul> [on|off|toggle]]");
        }

        if ("languagegroups".equalsIgnoreCase(args[0])) {
            return handleShortcutLanguageGroups(sliceFrom(args, 1));
        }

        return Result.messages(CommandGtb.head
                + "Usage: /gtbs shortcutreminder [shownonascii|showallshortcuts|showmultiwords|languagegroups] ...");
    }

    private static Result handleShortcutLanguageGroups(String[] args) {
        if (args.length == 0) {
            return shortcutStatus("Language group shortcuts", ShortcutLanguageGroups.isEnabled());
        }
        if (args.length == 1) {
            Boolean enabled = parseToggleBoolean(args[0], ShortcutLanguageGroups.isEnabled());
            if (enabled != null) {
                ShortcutLanguageGroups.setEnabled(enabled.booleanValue());
                saveSettings();
                return shortcutStatus("Language group shortcuts", ShortcutLanguageGroups.isEnabled());
            }
            if (args[0].equalsIgnoreCase("list")) return languageGroupList();
        }
        if (args[0].equalsIgnoreCase("create")) {
            if (args.length < 3) return languageGroupUsage("create <name> <lang1,lang2,...>");
            LanguageListParse languages = parseLanguageList(args[args.length - 1]);
            if (!languages.ok) return languageGroupValidation(
                    ShortcutLanguageGroups.ERROR_UNKNOWN_LANGUAGE, languages.invalid);
            ShortcutLanguageGroups.Result result = ShortcutLanguageGroups.create(
                    joinRange(args, 1, args.length - 1), languages.codes);
            if (!result.success) return languageGroupValidation(result.error, result.detail);
            saveSettings();
            return Result.messages(CommandGtb.head + "Created shortcut language group \u00a7f"
                    + result.group.name + "\u00a77 as group \u00a7f"
                    + ShortcutLanguageGroups.groups().size() + "\u00a77.");
        }
        if (args[0].equalsIgnoreCase("enable")) {
            if (args.length != 2 && args.length != 3) {
                return languageGroupUsage("enable <number> [on|off]");
            }
            Integer index = parseLanguageGroupIndex(args[1]);
            ShortcutLanguageGroups.Group group = index == null ? null
                    : ShortcutLanguageGroups.groupAt(index.intValue());
            if (group == null) return languageGroupValidation(
                    ShortcutLanguageGroups.ERROR_GROUP_NOT_FOUND, args[1]);
            if (args.length == 3) {
                Boolean enabled = parseOptionalBoolean(args[2]);
                if (enabled == null) return languageGroupUsage("enable <number> [on|off]");
                ShortcutLanguageGroups.Result result = ShortcutLanguageGroups.setGroupEnabled(
                        index.intValue(), enabled.booleanValue());
                if (!result.success) return languageGroupValidation(result.error, result.detail);
                group = result.group;
                saveSettings();
            }
            return languageGroupOptionStatus(index, group, "", group.enabled);
        }
        if (args[0].equalsIgnoreCase("rawtranslation")) {
            return handleShortcutLanguageGroupRawOption(args, false);
        }
        if (args[0].equalsIgnoreCase("lowercase")) {
            return handleShortcutLanguageGroupRawOption(args, true);
        }
        if (args[0].equalsIgnoreCase("rename")) {
            if (args.length < 3) return languageGroupUsage("rename <number> <name>");
            Integer index = parseLanguageGroupIndex(args[1]);
            if (index == null) return languageGroupValidation(
                    ShortcutLanguageGroups.ERROR_GROUP_NOT_FOUND, args[1]);
            ShortcutLanguageGroups.Result result = ShortcutLanguageGroups.rename(
                    index.intValue(), joinRange(args, 2, args.length));
            if (!result.success) return languageGroupValidation(result.error, result.detail);
            saveSettings();
            return Result.messages(CommandGtb.head + "Shortcut language group \u00a7f" + index
                    + "\u00a77 renamed to \u00a7f" + result.group.name + "\u00a77.");
        }
        if (args[0].equalsIgnoreCase("languages")) {
            if (args.length != 3) return languageGroupUsage("languages <number> <lang1,lang2,...>");
            Integer index = parseLanguageGroupIndex(args[1]);
            if (index == null) return languageGroupValidation(
                    ShortcutLanguageGroups.ERROR_GROUP_NOT_FOUND, args[1]);
            LanguageListParse languages = parseLanguageList(args[2]);
            if (!languages.ok) return languageGroupValidation(
                    ShortcutLanguageGroups.ERROR_UNKNOWN_LANGUAGE, languages.invalid);
            ShortcutLanguageGroups.Result result = ShortcutLanguageGroups.setLanguages(
                    index.intValue(), languages.codes);
            if (!result.success) return languageGroupValidation(result.error, result.detail);
            saveSettings();
            return Result.messages(CommandGtb.head + "Shortcut language group \u00a7f" + index
                    + "\u00a77 now contains \u00a7f" + result.group.languages.size()
                    + "\u00a77 languages.");
        }
        if (args[0].equalsIgnoreCase("delete")) {
            if (args.length != 2) return languageGroupUsage("delete <number>");
            Integer index = parseLanguageGroupIndex(args[1]);
            if (index == null) return languageGroupValidation(
                    ShortcutLanguageGroups.ERROR_GROUP_NOT_FOUND, args[1]);
            ShortcutLanguageGroups.Result result = ShortcutLanguageGroups.delete(index.intValue());
            if (!result.success) return languageGroupValidation(result.error, result.detail);
            saveSettings();
            return Result.messages(CommandGtb.head + "Deleted shortcut language group \u00a7f"
                    + result.group.name + "\u00a77.");
        }
        return languageGroupUsage(
                "[on|off|list|create|enable|rawtranslation|lowercase|rename|languages|delete] ...");
    }

    private static Result handleShortcutLanguageGroupRawOption(String[] args, boolean lowercase) {
        String command = lowercase ? "lowercase" : "rawtranslation";
        if (args.length != 2 && args.length != 3) {
            return languageGroupUsage(command + " <number> [on|off]");
        }
        Integer index = parseLanguageGroupIndex(args[1]);
        ShortcutLanguageGroups.Group group = index == null ? null
                : ShortcutLanguageGroups.groupAt(index.intValue());
        if (group == null) return languageGroupValidation(
                ShortcutLanguageGroups.ERROR_GROUP_NOT_FOUND, args[1]);
        if (args.length == 3) {
            Boolean enabled = parseOptionalBoolean(args[2]);
            if (enabled == null) return languageGroupUsage(command + " <number> [on|off]");
            ShortcutLanguageGroups.Result result = lowercase
                    ? ShortcutLanguageGroups.setRawTranslationAllLowercase(
                    index.intValue(), enabled.booleanValue())
                    : ShortcutLanguageGroups.setRawTranslation(index.intValue(), enabled.booleanValue());
            if (!result.success) return languageGroupValidation(result.error, result.detail);
            group = result.group;
            saveSettings();
        }
        boolean enabled = lowercase ? group.rawTranslationAllLowercase : group.rawTranslation;
        String label = lowercase ? "all-lowercase raw translation" : "raw translation";
        return languageGroupOptionStatus(index, group, label + " ", enabled);
    }

    private static Result languageGroupList() {
        List<ShortcutLanguageGroups.Group> groups = ShortcutLanguageGroups.groups();
        if (groups.isEmpty()) return Result.messages(CommandGtb.head
                + "No shortcut language groups are configured.");
        ArrayList<String> messages = new ArrayList<String>();
        messages.add(CommandGtb.head + "Shortcut language groups: \u00a7f" + groups.size()
                + "\u00a77 total.");
        for (int i = 0; i < groups.size(); i++) {
            ShortcutLanguageGroups.Group group = groups.get(i);
            messages.add("\u00a77" + (i + 1) + ". "
                    + (group.enabled ? "\u00a7aON" : "\u00a7cOFF") + " \u00a7f"
                    + group.name + "\u00a77: " + languageGroupSummary(group.languages)
                    + " \u00a77[raw: " + (group.rawTranslation ? "\u00a7aON" : "\u00a7cOFF")
                    + "\u00a77, lowercase: "
                    + (group.rawTranslationAllLowercase ? "\u00a7aON" : "\u00a7cOFF") + "\u00a77]");
        }
        return Result.messages(messages);
    }

    private static Result languageGroupOptionStatus(Integer index, ShortcutLanguageGroups.Group group,
                                                    String labelPrefix, boolean enabled) {
        String label = labelPrefix.isEmpty() ? "is currently " : labelPrefix + "is currently ";
        return Result.messages(CommandGtb.head + "Shortcut language group \u00a7f" + index
                + "\u00a77 (\u00a7f" + group.name + "\u00a77) " + label + onOff(enabled));
    }

    private static String languageGroupSummary(List<String> languages) {
        StringBuilder out = new StringBuilder();
        for (String code : languages) {
            if (out.length() > 0) out.append("\u00a77, ");
            if (ShortcutLanguageGroups.isSupportedLanguage(code)) {
                out.append("\u00a7f").append(ShortcutLanguageGroups.languageDisplayName(code))
                        .append("\u00a77 (").append(code).append(')');
            } else {
                out.append("\u00a7cUnavailable\u00a77 (").append(code).append(')');
            }
        }
        return out.toString();
    }

    private static Result languageGroupValidation(String error, String detail) {
        return Result.messages(shortcutLanguageGroupValidationNotice(error, detail));
    }

    /** Shared by command and preview paths so rejected group changes have one public notice. */
    public static String shortcutLanguageGroupValidationNotice(String error, String detail) {
        if (ShortcutLanguageGroups.ERROR_GROUP_NOT_FOUND.equals(error)) {
            return CommandGtb.head + "Shortcut language group \u00a7f" + safeDetail(detail)
                    + "\u00a77 does not exist.";
        }
        if (ShortcutLanguageGroups.ERROR_NAME_REQUIRED.equals(error)) {
            return CommandGtb.head + "Shortcut language group names cannot be empty.";
        }
        if (ShortcutLanguageGroups.ERROR_NAME_TOO_LONG.equals(error)) {
            return CommandGtb.head + "Shortcut language group names must be at most \u00a7f"
                    + ShortcutLanguageGroups.MAX_NAME_CODE_POINTS + "\u00a77 characters.";
        }
        if (ShortcutLanguageGroups.ERROR_DUPLICATE_NAME.equals(error)) {
            return CommandGtb.head + "A shortcut language group named \u00a7f"
                    + safeDetail(detail) + "\u00a77 already exists.";
        }
        if (ShortcutLanguageGroups.ERROR_LANGUAGES_REQUIRED.equals(error)) {
            return CommandGtb.head + "Select at least one language for the shortcut language group.";
        }
        if (ShortcutLanguageGroups.ERROR_UNKNOWN_LANGUAGE.equals(error)) {
            return CommandGtb.head + "Unknown shortcut language: \u00a7f"
                    + safeDetail(detail) + "\u00a77. Available codes: \u00a7f" + availableLanguageCodes();
        }
        return CommandGtb.head + "The shortcut language group change was rejected.";
    }

    private static Result languageGroupUsage(String suffix) {
        return Result.messages(CommandGtb.head + "Usage: /gtbs shortcutreminder languagegroups " + suffix);
    }

    private static Result handleCopyTranslation(String[] args) {
        if (args.length == 0) {
            return Result.messages(CommandGtb.head + "Usage: /gtbs copytranslation <theme> [langs]");
        }
        if ("prefix".equalsIgnoreCase(args[0])) {
            if (args.length != 2) {
                return Result.messages(CommandGtb.head + "Usage: /gtbs copytranslation prefix <true|false>");
            }
            Boolean enabled = parseOptionalBoolean(args[1]);
            if (enabled == null) {
                return Result.messages(CommandGtb.head + "Usage: /gtbs copytranslation prefix <true|false>");
            }
            SETTINGS.copyTranslationPrefix = enabled.booleanValue();
            saveSettings();
            return Result.messages(CommandGtb.head + "Copy translation prefix is currently "
                    + onOff(SETTINGS.copyTranslationPrefix));
        }
        if ("caps".equalsIgnoreCase(args[0])) {
            if (args.length > 2) {
                return Result.messages(CommandGtb.head
                        + "Usage: /gtbs copytranslation caps [on|off]");
            }
            if (args.length == 2) {
                Boolean enabled = parseOptionalBoolean(args[1]);
                if (enabled == null) {
                    return Result.messages(CommandGtb.head
                            + "Usage: /gtbs copytranslation caps [on|off]");
                }
                SETTINGS.copyTranslationCaps = enabled.booleanValue();
                saveSettings();
            }
            return Result.messages(CommandGtb.head
                    + "Copy translation capitalization is currently "
                    + onOff(SETTINGS.copyTranslationCaps));
        }

        CopyTranslation.Request request = CopyTranslation.parseRequest(args);
        if (request == null || request.theme == null || request.theme.isEmpty()) {
            return Result.messages(CommandGtb.head + "Usage: /gtbs copytranslation <theme> [langs]");
        }
        if (request.langError != null) {
            return Result.messages(CommandGtb.head + request.langError);
        }
        CopyTranslation.Result result = CopyTranslation.buildMessage(request.theme, request.langCodes,
                SETTINGS.copyTranslationPrefix, SETTINGS.copyTranslationCaps);
        if (!result.ok) {
            return Result.messages(CommandGtb.head + result.error);
        }
        services.copyToClipboard(result.message);
        return Result.messages(CommandGtb.head + "Message copied to clipboard: \u00a7f" + result.message);
    }

    private static Result handleGtw(String[] args, String usageRoot) {
        if (args.length == 0) {
            return Result.messages(CommandGtb.head + "Usage: " + usageRoot + " <start|stop|showenglish|showcount|difficulty> ...");
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("difficulty".equals(sub)) {
            if (args.length == 1) {
                return Result.messages(CommandGtb.head + "GTW difficulty is currently: \u00a7f"
                        + GtwLogic.normalizeDifficulty(SETTINGS.gtwDifficulty));
            }
            if (args.length != 2) {
                return Result.messages(CommandGtb.head
                        + "Usage: /gtw difficulty <easy|medium|hard|all>");
            }
            GtwLogic.Difficulty difficulty = GtwLogic.parseDifficulty(args[1]);
            if (difficulty == null) {
                return Result.messages(CommandGtb.head
                        + "Usage: /gtw difficulty <easy|medium|hard|all>");
            }
            SETTINGS.gtwDifficulty = difficulty.id;
            saveSettings();
            return Result.messages(CommandGtb.head + "GTW difficulty is currently: \u00a7f" + difficulty.id);
        }
        if ("showenglish".equals(sub)) {
            if (args.length < 2) {
                return Result.messages(CommandGtb.head + "Show English (in " + usageRoot + ") is currently "
                        + onOff(SETTINGS.gtwShowEnglish));
            }
            if (args.length != 2) return Result.messages(CommandGtb.head + "Usage: " + usageRoot + " showenglish <true|false>");
            Boolean enabled = parseOptionalBoolean(args[1]);
            if (enabled == null) return Result.messages(CommandGtb.head + "Usage: " + usageRoot + " showenglish <true|false>");
            SETTINGS.gtwShowEnglish = enabled.booleanValue();
            saveSettings();
            return Result.messages(CommandGtb.head + "Show English (in " + usageRoot + ") is currently "
                    + onOff(SETTINGS.gtwShowEnglish));
        }
        if ("showcount".equals(sub)) {
            if (args.length == 1) return gtwShowCountStatus();
            if (args.length != 2) {
                return Result.messages(CommandGtb.head + "Usage: /gtw showcount [on|off]");
            }
            Boolean enabled = parseOptionalBoolean(args[1]);
            if (enabled == null) {
                return Result.messages(CommandGtb.head + "Usage: /gtw showcount [on|off]");
            }
            SETTINGS.gtwShowCount = enabled.booleanValue();
            saveSettings();
            return gtwShowCountStatus();
        }
        if ("start".equals(sub)) {
            String langCode;
            if (args.length < 2) {
                langCode = SETTINGS.gtwDefaultLang == null || SETTINGS.gtwDefaultLang.isEmpty()
                        ? "en" : SETTINGS.gtwDefaultLang;
            } else {
                langCode = GtwLogic.resolveLanguage(join(sliceFrom(args, 1)));
                if (langCode == null) {
                    return Result.messages(CommandGtb.head + "Unknown language. Available codes: \u00a7f"
                            + GtwLogic.listAllCodes());
                }
            }
            return Result.messages(prefixAll(GTW.start(langCode), CommandGtb.head));
        }
        if ("stop".equals(sub)) {
            if (args.length != 1) return Result.messages(CommandGtb.head + "Usage: " + usageRoot + " <start|stop|showenglish|showcount|difficulty> ...");
            return Result.messages(prefixAll(GTW.stop(), CommandGtb.head));
        }
        return Result.messages(CommandGtb.head + "Usage: " + usageRoot + " <start|stop|showenglish|showcount|difficulty> ...");
    }

    private static Result handleGuess(String[] args, String usageRoot) {
        if (args.length == 0) return Result.messages(CommandGtb.head + "Usage: " + usageRoot + " <guess>");
        return Result.messages(prefixAll(GTW.guess(join(args)), CommandGtb.head));
    }

    static boolean matchesAnyGtwTranslation(String guess, String englishTheme) {
        return TranslationData.findActiveGuessThemes(guess).contains(englishTheme);
    }

    private static Result handleScoreboard(String[] args) {
        if (args.length == 0) {
            SETTINGS.sbConfig.toggleCustom();
            saveSettings();
            return Result.messages(CommandGtb.head + "Custom scoreboard is now "
                    + onOff(SETTINGS.sbConfig.isCustomEnabled()));
        }
        if (isScoreboardSliderOption(args[0])) {
            return handleScoreboardSlider(args);
        }
        if (isScoreboardTriStateOption(args[0])) {
            return handleScoreboardTriState(args);
        }
        if (isScoreboardBooleanOption(args[0])) {
            return handleScoreboardBoolean(args);
        }
        String requested = null;
        if ("skin".equalsIgnoreCase(args[0])) {
            if (args.length < 2) {
                return Result.messages(CommandGtb.head + "Current skin: \u00a7f"
                        + SETTINGS.sbConfig.skin + "\u00a77. Usage: /gtbs scoreboard skin <vanilla|default|hypixel>");
            }
            requested = args[1];
        } else if ("vanilla".equalsIgnoreCase(args[0])
                || "default".equalsIgnoreCase(args[0])
                || "hypixel".equalsIgnoreCase(args[0])) {
            requested = args[0];
        }
        if (requested == null || (args.length > 2 || (!"skin".equalsIgnoreCase(args[0]) && args.length > 1))) {
            return Result.messages(CommandGtb.head + "Usage: /gtbs scoreboard [skin <vanilla|default|hypixel>]");
        }
        String norm = requested.trim().toLowerCase(Locale.ROOT);
        if (!"vanilla".equals(norm) && !"default".equals(norm) && !"hypixel".equals(norm)) {
            return Result.messages(CommandGtb.head + "Unknown skin \u00a7f" + requested
                    + "\u00a77. Valid: \u00a7fvanilla\u00a77, \u00a7fdefault\u00a77, \u00a7fhypixel\u00a77.");
        }
        SETTINGS.sbConfig.setSkin(norm);
        saveSettings();
        String display = "hypixel".equals(norm) ? "Hypixel" : "default".equals(norm) ? "Default" : "Vanilla";
        return Result.messages(CommandGtb.head + "Scoreboard skin set to \u00a7f" + display
                + (SETTINGS.sbConfig.isCustomEnabled() ? ""
                : "\u00a77 (panel is currently \u00a7cOFF\u00a77 - toggle with /gtbs scoreboard)"));
    }

    private static Result handleScoreboardSlider(String[] args) {
        String option = scoreboardOptionKey(args[0]);
        if (args.length == 1) {
            return Result.messages(CommandGtb.head + scoreboardSliderStatus(option));
        }
        if (args.length != 2) {
            return Result.messages(CommandGtb.head + "Usage: /gtbs scoreboard " + option + " <0-1>");
        }
        Float value = parseScoreboardFloat(args[1]);
        if (value == null) {
            return Result.messages(CommandGtb.head + "Usage: /gtbs scoreboard " + option + " <0-1>");
        }
        if ("backgroundopacity".equals(option)) {
            SETTINGS.sbConfig.backgroundOpacity = value.floatValue();
        } else {
            SETTINGS.sbConfig.highlightIntensity = value.floatValue();
        }
        SETTINGS.sbConfig.clampVisuals();
        saveSettings();
        return Result.messages(CommandGtb.head + scoreboardSliderStatus(option));
    }

    private static Result handleScoreboardTriState(String[] args) {
        String option = scoreboardOptionKey(args[0]);
        if (args.length == 1) {
            return Result.messages(CommandGtb.head + scoreboardTriStateStatus(option));
        }
        if (args.length != 2) {
            return Result.messages(CommandGtb.head + scoreboardTriStateUsage(option));
        }
        ScoreboardConfig.DisplayOption value = ScoreboardConfig.parseOption(args[1]);
        if (value == null) {
            return Result.messages(CommandGtb.head + scoreboardTriStateUsage(option));
        }
        if ("showpoints".equals(option)) {
            SETTINGS.sbConfig.showPointsGainedInRound = value;
        } else if ("showplaces".equals(option)) {
            SETTINGS.sbConfig.showPlaces = value;
        } else if ("showtitles".equals(option)) {
            SETTINGS.sbConfig.showTitles = value;
        } else if ("showemblems".equals(option)) {
            SETTINGS.sbConfig.showEmblems = value;
        } else {
            SETTINGS.sbConfig.hypixelShowRounds = value;
        }
        saveSettings();
        return Result.messages(CommandGtb.head + scoreboardTriStateStatus(option));
    }

    private static String scoreboardTriStateUsage(String option) {
        return "Usage: /gtbs scoreboard " + option
                + ("showrounds".equals(option) ? " [off|expanded|on]" : " <off|expanded|on>");
    }

    private static Result handleScoreboardBoolean(String[] args) {
        String option = scoreboardOptionKey(args[0]);
        boolean current;
        if ("leftlast".equals(option)) current = SETTINGS.sbConfig.leftPlayersAtBottom;
        else if ("showbuilders".equals(option)) current = SETTINGS.sbConfig.defaultExplicitlyShowBuilders;
        else current = SETTINGS.sbConfig.textShadow;
        if (args.length == 1) {
            return Result.messages(CommandGtb.head + scoreboardBooleanStatus(option));
        }
        if (args.length != 2) {
            return Result.messages(CommandGtb.head + "Usage: /gtbs scoreboard " + option + " [on|off]");
        }
        Boolean enabled = parseToggleBoolean(args[1], current);
        if (enabled == null) {
            return Result.messages(CommandGtb.head + "Usage: /gtbs scoreboard " + option + " [on|off]");
        }
        if ("leftlast".equals(option)) {
            SETTINGS.sbConfig.leftPlayersAtBottom = enabled.booleanValue();
        } else if ("showbuilders".equals(option)) {
            SETTINGS.sbConfig.defaultExplicitlyShowBuilders = enabled.booleanValue();
        } else {
            SETTINGS.sbConfig.textShadow = enabled.booleanValue();
        }
        saveSettings();
        return Result.messages(CommandGtb.head + scoreboardBooleanStatus(option));
    }

    private static String scoreboardBooleanStatus(String option) {
        if ("leftlast".equals(option)) {
            return "Place left players at bottom is currently "
                    + onOff(SETTINGS.sbConfig.leftPlayersAtBottom);
        }
        if ("showbuilders".equals(option)) {
            return "Explicitly show builders is currently "
                    + onOff(SETTINGS.sbConfig.defaultExplicitlyShowBuilders);
        }
        return "Text shadow is currently " + onOff(SETTINGS.sbConfig.textShadow);
    }

    private static String scoreboardSliderStatus(String option) {
        String label = "backgroundopacity".equals(option) ? "Background opacity" : "Highlight intensity";
        float value = "backgroundopacity".equals(option)
                ? SETTINGS.sbConfig.backgroundOpacity
                : SETTINGS.sbConfig.highlightIntensity;
        return label + " is currently \u00a7f" + formatScoreboardFloat(value);
    }

    private static String scoreboardTriStateStatus(String option) {
        String label;
        ScoreboardConfig.DisplayOption value;
        if ("showpoints".equals(option)) {
            label = "Show points gained in round";
            value = SETTINGS.sbConfig.showPointsGainedInRound;
        } else if ("showplaces".equals(option)) {
            label = "Show places";
            value = SETTINGS.sbConfig.showPlaces;
        } else if ("showtitles".equals(option)) {
            label = "Show titles";
            value = SETTINGS.sbConfig.showTitles;
        } else if ("showemblems".equals(option)) {
            label = "Show emblems";
            value = SETTINGS.sbConfig.showEmblems;
        } else {
            label = "Show rounds";
            value = SETTINGS.sbConfig.hypixelShowRounds;
        }
        return label + " is currently \u00a7f" + ScoreboardConfig.displayOption(value);
    }

    private static Float parseScoreboardFloat(String raw) {
        try {
            float value = Float.parseFloat(raw);
            if (value < 0F || value > 1F) return null;
            return Float.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatScoreboardFloat(float value) {
        return String.format(Locale.ROOT, "%.2f", Float.valueOf(value));
    }

    private static boolean isScoreboardSliderOption(String raw) {
        String key = scoreboardOptionKey(raw);
        return "backgroundopacity".equals(key) || "highlightintensity".equals(key);
    }

    private static boolean isScoreboardTriStateOption(String raw) {
        String key = scoreboardOptionKey(raw);
        return "showpoints".equals(key) || "showplaces".equals(key)
                || "showtitles".equals(key) || "showemblems".equals(key)
                || "showrounds".equals(key);
    }

    private static boolean isScoreboardBooleanOption(String raw) {
        String key = scoreboardOptionKey(raw);
        return "textshadow".equals(key) || "leftlast".equals(key)
                || "showbuilders".equals(key);
    }

    private static String scoreboardOptionKey(String raw) {
        if (raw == null) return "";
        String key = raw.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (key.equals("opacity") || key.equals("background") || key.equals("backgroundopacity")) {
            return "backgroundopacity";
        }
        if (key.equals("highlight") || key.equals("highlightintensity")) {
            return "highlightintensity";
        }
        if (key.equals("points") || key.equals("showpoints")
                || key.equals("showpointsgained") || key.equals("showpointsgainedinround")) {
            return "showpoints";
        }
        if (key.equals("places") || key.equals("showplaces")) return "showplaces";
        if (key.equals("titles") || key.equals("showtitles")) return "showtitles";
        if (key.equals("emblems") || key.equals("showemblems")) return "showemblems";
        if (key.equals("builder") || key.equals("builders") || key.equals("showbuilder")
                || key.equals("showbuilders") || key.equals("explicitlyshowbuilders")) return "showbuilders";
        if (key.equals("round") || key.equals("rounds") || key.equals("showround")
                || key.equals("showrounds")) return "showrounds";
        if (key.equals("shadow") || key.equals("textshadow")) return "textshadow";
        if (key.equals("leftlast")) return "leftlast";
        return key;
    }

    private static Result handleSpawnEgg(String[] args) {
        if (args.length != 1) {
            return Result.messages(CommandGtb.head + "Current spawn egg skin: \u00a7f"
                    + displaySpawnEggSkin(SETTINGS.spawnEggSkin)
                    + "\u00a77. Usage: /gtbs spawnegg <Classic|New>");
        }
        if (!"classic".equalsIgnoreCase(args[0]) && !"new".equalsIgnoreCase(args[0])) {
            return Result.messages(CommandGtb.head + "Unknown spawn egg skin \u00a7f" + args[0]
                    + "\u00a77. Valid: \u00a7fClassic\u00a77, \u00a7fNew\u00a77.");
        }
        SETTINGS.spawnEggSkin = MinecraftFormatting.normalizeSpawnEggSkin(args[0]);
        saveSettings();
        return Result.messages(CommandGtb.head + "Spawn egg skin set to \u00a7f"
                + displaySpawnEggSkin(SETTINGS.spawnEggSkin));
    }

    private static Result handleHandheldItem(String[] args) {
        if (args.length == 0) {
            SETTINGS.handheldItemOverlay = !SETTINGS.handheldItemOverlay;
            saveSettings();
            return Result.messages(CommandGtb.head + "Handheld item overlay is currently "
                    + onOff(SETTINGS.handheldItemOverlay));
        }
        if ("background".equalsIgnoreCase(args[0])) {
            return handleSimpleBackground(args, "Handheld item background", "/gtbs handhelditem background [on|off]",
                    SETTINGS.handheldItemBackground, value -> SETTINGS.handheldItemBackground = value);
        }
        if ("scale".equalsIgnoreCase(args[0])) {
            return handlePanelScale(args, "handheld", "Handheld item");
        }
        if ("setlocation".equalsIgnoreCase(args[0])) {
            return handlePosition(args, "Handheld item", "/gtbs handhelditem setlocation <x | y> <value>",
                    SETTINGS.handheldItemPosX, SETTINGS.handheldItemPosY, (axis, value) -> {
                        if ("x".equals(axis)) SETTINGS.handheldItemPosX = value;
                        else SETTINGS.handheldItemPosY = value;
                    });
        }
        if ("max".equalsIgnoreCase(args[0])) {
            if (args.length != 2) return Result.messages(CommandGtb.head + "Usage: /gtbs handhelditem max <2-10>");
            try {
                int value = Integer.parseInt(args[1]);
                if (value < 2 || value > 10) {
                    return Result.messages(CommandGtb.head + "Please enter an integer between 2 and 10.");
                }
                SETTINGS.handheldItemMaxVisible = HandheldItemTracker.clampMaxVisible(value);
                saveSettings();
                return Result.messages(CommandGtb.head + "Handheld item visible max set to \u00a7f"
                        + SETTINGS.handheldItemMaxVisible);
            } catch (NumberFormatException e) {
                return Result.messages(CommandGtb.head + "Usage: /gtbs handhelditem max <2-10>");
            }
        }
        if ("captions".equalsIgnoreCase(args[0])) return handleHandheldCaptions(args);
        if ("layout".equalsIgnoreCase(args[0])) return handleHandheldLayout(args);
        if ("vertical".equalsIgnoreCase(args[0])) return handleHandheldVertical(args);
        if ("horizontal".equalsIgnoreCase(args[0])) return handleHandheldHorizontal(args);
        if ("size".equalsIgnoreCase(args[0])) return handleHandheldSize(args);
        return Result.messages(CommandGtb.head + "Usage: /gtbs handhelditem [setlocation|max|background|captions|layout|vertical|horizontal|size|scale] ...");
    }

    private static Result handleHandheldCaptions(String[] args) {
        if (args.length < 2 || args.length > 3) {
            return Result.messages(CommandGtb.head + "Usage: /gtbs handhelditem captions <name|id|metadata|mob> [on|off]");
        }
        String caption = args[1].toLowerCase(Locale.ROOT);
        Boolean current = handheldCaptionValue(caption);
        if (current == null) return Result.messages(CommandGtb.head
                + "Usage: /gtbs handhelditem captions <name|id|metadata|mob> [on|off]");
        if (args.length == 2) return Result.messages(CommandGtb.head + "Handheld item " + caption
                + " caption is currently " + onOff(current.booleanValue()));
        Boolean value = parseOptionalBoolean(args[2]);
        if (value == null) return Result.messages(CommandGtb.head
                + "Usage: /gtbs handhelditem captions <name|id|metadata|mob> [on|off]");
        setHandheldCaptionValue(caption, value.booleanValue());
        saveSettings();
        return Result.messages(CommandGtb.head + "Handheld item " + caption + " caption is currently "
                + onOff(value.booleanValue()));
    }

    private static Boolean handheldCaptionValue(String caption) {
        if ("name".equals(caption)) return Boolean.valueOf(SETTINGS.handheldItemCaptionName);
        if ("id".equals(caption)) return Boolean.valueOf(SETTINGS.handheldItemCaptionId);
        if ("metadata".equals(caption)) return Boolean.valueOf(SETTINGS.handheldItemCaptionMetadata);
        if ("mob".equals(caption)) return Boolean.valueOf(SETTINGS.handheldItemCaptionSpawnEggMob);
        return null;
    }

    private static void setHandheldCaptionValue(String caption, boolean value) {
        if ("name".equals(caption)) SETTINGS.handheldItemCaptionName = value;
        else if ("id".equals(caption)) SETTINGS.handheldItemCaptionId = value;
        else if ("metadata".equals(caption)) SETTINGS.handheldItemCaptionMetadata = value;
        else if ("mob".equals(caption)) SETTINGS.handheldItemCaptionSpawnEggMob = value;
    }

    private static Result handleHandheldLayout(String[] args) {
        if (args.length == 1) return Result.messages(CommandGtb.head + "Handheld item layout is currently §f"
                + (SETTINGS.handheldItemVerticalLayout ? "Vertical" : "Horizontal"));
        if (args.length != 2 || !("vertical".equalsIgnoreCase(args[1]) || "horizontal".equalsIgnoreCase(args[1]))) {
            return Result.messages(CommandGtb.head + "Usage: /gtbs handhelditem layout [vertical|horizontal]");
        }
        SETTINGS.handheldItemVerticalLayout = "vertical".equalsIgnoreCase(args[1]);
        saveSettings();
        return Result.messages(CommandGtb.head + "Handheld item layout set to §f"
                + (SETTINGS.handheldItemVerticalLayout ? "Vertical" : "Horizontal"));
    }

    private static Result handleHandheldVertical(String[] args) {
        if (args.length == 1) return Result.messages(CommandGtb.head
                + "Vertical layout places newly held items at the §f"
                + (SETTINGS.handheldItemVerticalNewestAtBottom ? "bottom" : "top"));
        if (args.length != 2 || !("top".equalsIgnoreCase(args[1]) || "bottom".equalsIgnoreCase(args[1]))) {
            return Result.messages(CommandGtb.head + "Usage: /gtbs handhelditem vertical [top|bottom]");
        }
        SETTINGS.handheldItemVerticalNewestAtBottom = "bottom".equalsIgnoreCase(args[1]);
        saveSettings();
        return Result.messages(CommandGtb.head + "Vertical layout places newly held items at the §f"
                + args[1].toLowerCase(Locale.ROOT));
    }

    private static Result handleHandheldHorizontal(String[] args) {
        if (args.length == 1) return Result.messages(CommandGtb.head
                + "Horizontal layout places newly held items at the §f"
                + (SETTINGS.handheldItemHorizontalNewestAtRight ? "right" : "left") + " §7and §f"
                + (SETTINGS.handheldItemHorizontalNewestAtBottom ? "bottom" : "top"));
        if (args.length > 3) return Result.messages(CommandGtb.head
                + "Usage: /gtbs handhelditem horizontal [left|right] [top|bottom]");
        Boolean right = null;
        Boolean bottom = null;
        for (int i = 1; i < args.length; i++) {
            String axis = args[i].toLowerCase(Locale.ROOT);
            if ("left".equals(axis) || "right".equals(axis)) {
                if (right != null) return Result.messages(CommandGtb.head
                        + "Usage: /gtbs handhelditem horizontal [left|right] [top|bottom]");
                right = Boolean.valueOf("right".equals(axis));
            } else if ("top".equals(axis) || "bottom".equals(axis)) {
                if (bottom != null) return Result.messages(CommandGtb.head
                        + "Usage: /gtbs handhelditem horizontal [left|right] [top|bottom]");
                bottom = Boolean.valueOf("bottom".equals(axis));
            } else {
                return Result.messages(CommandGtb.head
                        + "Usage: /gtbs handhelditem horizontal [left|right] [top|bottom]");
            }
        }
        if (right != null) SETTINGS.handheldItemHorizontalNewestAtRight = right.booleanValue();
        if (bottom != null) SETTINGS.handheldItemHorizontalNewestAtBottom = bottom.booleanValue();
        saveSettings();
        return handleHandheldHorizontal(new String[] {"horizontal"});
    }

    private static Result handleHandheldSize(String[] args) {
        if (args.length < 2 || args.length > 3 || !("width".equalsIgnoreCase(args[1])
                || "height".equalsIgnoreCase(args[1]))) {
            return Result.messages(CommandGtb.head + "Usage: /gtbs handhelditem size <width|height> [value]");
        }
        boolean width = "width".equalsIgnoreCase(args[1]);
        if (args.length == 2) return Result.messages(CommandGtb.head + "Handheld item panel " + args[1].toLowerCase(Locale.ROOT)
                + " is currently §f" + (width ? SETTINGS.handheldItemPanelWidthRatio
                : SETTINGS.handheldItemPanelHeightRatio));
        try {
            float value = Float.parseFloat(args[2]);
            if (Float.isNaN(value) || Float.isInfinite(value) || value < 0F || value > 1F) return Result.messages(CommandGtb.head + "Handheld item panel "
                    + args[1].toLowerCase(Locale.ROOT) + " must be a ratio from 0.0 to 1.0.");
            if (width) SETTINGS.handheldItemPanelWidthRatio = value;
            else SETTINGS.handheldItemPanelHeightRatio = value;
            saveSettings();
            return Result.messages(CommandGtb.head + "Handheld item panel " + args[1].toLowerCase(Locale.ROOT)
                    + " set to §f" + value);
        } catch (NumberFormatException e) {
            return Result.messages(CommandGtb.head + "Usage: /gtbs handhelditem size <width|height> [value]");
        }
    }

    private static Result handleEnvironment(String[] args) {
        if (args.length == 0) {
            SETTINGS.environmentOverlay = !SETTINGS.environmentOverlay;
            saveSettings();
            return Result.messages(CommandGtb.head + "Environment overlay is currently "
                    + onOff(SETTINGS.environmentOverlay));
        }
        if ("background".equalsIgnoreCase(args[0])) {
            return handleSimpleBackground(args, "Environment background", "/gtbs environment background [on|off]",
                    SETTINGS.environmentBackground, value -> SETTINGS.environmentBackground = value);
        }
        if ("scale".equalsIgnoreCase(args[0])) {
            return handlePanelScale(args, "environment", "Environment");
        }
        if ("setlocation".equalsIgnoreCase(args[0])) {
            return handlePosition(args, "Environment", "/gtbs environment setlocation <x | y> <value>",
                    SETTINGS.environmentPosX, SETTINGS.environmentPosY, (axis, value) -> {
                        if ("x".equals(axis)) SETTINGS.environmentPosX = value;
                        else SETTINGS.environmentPosY = value;
                    });
        }
        if ("chatnotice".equalsIgnoreCase(args[0])) {
            return handleEnvironmentChatNotice(args);
        }
        if (isEnvironmentPart(args[0])) {
            return handleEnvironmentPart(args);
        }
        if ("timepreset".equalsIgnoreCase(args[0])) {
            if (args.length == 1) {
                return Result.messages(CommandGtb.head + "Environment time item preset is currently \u00a7f"
                        + displayEnvironmentTimePreset(SETTINGS.environmentTimeIconPreset));
            }
            if (args.length != 2) return Result.messages(CommandGtb.head + "Usage: /gtbs environment timepreset <Clock|Blocks>");
            String raw = args[1].trim().toLowerCase(Locale.ROOT);
            if (!"clock".equals(raw) && !"blocks".equals(raw)) {
                return Result.messages(CommandGtb.head + "Unknown time item preset \u00a7f" + args[1]
                        + "\u00a77. Valid: \u00a7fClock\u00a77, \u00a7fBlocks\u00a77.");
            }
            SETTINGS.environmentTimeIconPreset = WorldEnvironmentLogic.normalizeTimeIconPreset(raw);
            saveSettings();
            return Result.messages(CommandGtb.head + "Environment time item preset set to \u00a7f"
                    + displayEnvironmentTimePreset(SETTINGS.environmentTimeIconPreset));
        }
        return Result.messages(CommandGtb.head + "Usage: /gtbs environment [setlocation|weather|biome|time|timepreset|background|chatnotice|scale] ...");
    }

    private static Result handleEnvironmentChatNotice(String[] args) {
        if (args.length < 2 || !isEnvironmentPart(args[1])) {
            return Result.messages(CommandGtb.head
                    + "Usage: /gtbs environment chatnotice <weather|biome|time> [on|off]");
        }
        String target = args[1].toLowerCase(Locale.ROOT);
        boolean current = environmentChatNoticeEnabled(target);
        if (args.length == 2) {
            return Result.messages(CommandGtb.head + "Environment " + target
                    + " chat notice is currently " + onOff(current));
        }
        if (args.length != 3) {
            return Result.messages(CommandGtb.head
                    + "Usage: /gtbs environment chatnotice <weather|biome|time> [on|off]");
        }
        Boolean enabled = parseToggleBoolean(args[2], current);
        if (enabled == null) {
            return Result.messages(CommandGtb.head
                    + "Usage: /gtbs environment chatnotice <weather|biome|time> [on|off]");
        }
        if ("weather".equals(target)) SETTINGS.environmentWeatherChatNotice = enabled.booleanValue();
        else if ("biome".equals(target)) SETTINGS.environmentBiomeChatNotice = enabled.booleanValue();
        else SETTINGS.environmentTimeChatNotice = enabled.booleanValue();
        saveSettings();
        return Result.messages(CommandGtb.head + "Environment " + target
                + " chat notice is currently " + onOff(enabled.booleanValue()));
    }

    private static boolean environmentChatNoticeEnabled(String target) {
        if ("weather".equals(target)) return SETTINGS.environmentWeatherChatNotice;
        if ("biome".equals(target)) return SETTINGS.environmentBiomeChatNotice;
        return SETTINGS.environmentTimeChatNotice;
    }

    private static Result handleEnvironmentPart(String[] args) {
        String target = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return Result.messages(CommandGtb.head + "Environment " + target + " row is currently "
                    + onOff(environmentPartEnabled(target)));
        }
        if (args.length != 2) {
            return Result.messages(CommandGtb.head + "Usage: /gtbs environment " + target + " [on|off]");
        }
        Boolean enabled = parseToggleBoolean(args[1], environmentPartEnabled(target));
        if (enabled == null) {
            return Result.messages(CommandGtb.head + "Usage: /gtbs environment " + target + " [on|off]");
        }
        if ("weather".equals(target)) SETTINGS.environmentWeatherEnabled = enabled.booleanValue();
        else if ("biome".equals(target)) SETTINGS.environmentBiomeEnabled = enabled.booleanValue();
        else SETTINGS.environmentTimeEnabled = enabled.booleanValue();
        if (enabled.booleanValue()) SETTINGS.environmentOverlay = true;
        saveSettings();
        return Result.messages(CommandGtb.head + "Environment " + target + " row is currently " + onOff(enabled.booleanValue()));
    }

    private static Result handleCooldownCounter(String[] args) {
        if (args.length == 0) {
            SETTINGS.chatCooldownCounter = !SETTINGS.chatCooldownCounter;
            saveSettings();
            return Result.messages(CommandGtb.head + "Chat cooldown counter is currently "
                    + onOff(SETTINGS.chatCooldownCounter));
        }
        if ("timer".equalsIgnoreCase(args[0]) || "bar".equalsIgnoreCase(args[0])) {
            return handleCooldownCounterPart(args);
        }
        if ("sfx".equalsIgnoreCase(args[0])) {
            return handleCooldownCounterSfx(args);
        }
        if ("background".equalsIgnoreCase(args[0])) {
            return handleCooldownCounterBackground(args);
        }
        if ("scale".equalsIgnoreCase(args[0])) {
            return handlePanelScale(args, "cooldown", "Chat cooldown");
        }
        if ("setcolor".equalsIgnoreCase(args[0])) {
            return handleCooldownCounterColor(args);
        }
        if ("setlocation".equalsIgnoreCase(args[0])) {
            return handlePosition(args, "Chat cooldown", "/gtbs cooldowncounter setlocation <x | y> <value>",
                    SETTINGS.chatCooldownPosX, SETTINGS.chatCooldownPosY, (axis, value) -> {
                        if ("x".equals(axis)) SETTINGS.chatCooldownPosX = value;
                        else SETTINGS.chatCooldownPosY = value;
                    });
        }
        return Result.messages(CommandGtb.head + "Usage: /gtbs cooldowncounter [setlocation|setcolor|timer|bar|sfx|background|scale] ...");
    }

    private static Result handleCooldownCounterSfx(String[] args) {
        boolean progressBeats = args.length >= 2 && "progressbeats".equalsIgnoreCase(args[1]);
        int valueIndex = progressBeats ? 2 : 1;
        boolean current = progressBeats
                ? SETTINGS.chatCooldownProgressBeats : SETTINGS.chatCooldownSfxEnabled;
        String label = progressBeats ? "Chat cooldown progress beats" : "Chat cooldown SFX";
        String usage = CommandGtb.head + "Usage: /gtbs cooldowncounter sfx"
                + (progressBeats ? " progressbeats" : "") + " [on|off|toggle]";
        if (args.length == valueIndex) {
            return Result.messages(CommandGtb.head + backgroundStatusMessage(label, current));
        }
        if (args.length != valueIndex + 1) return Result.messages(usage);
        Boolean next = parseToggleBoolean(args[valueIndex], current);
        if (next == null) return Result.messages(usage);
        if (progressBeats) SETTINGS.chatCooldownProgressBeats = next.booleanValue();
        else SETTINGS.chatCooldownSfxEnabled = next.booleanValue();
        saveSettings();
        return Result.messages(CommandGtb.head + backgroundStatusMessage(label, next.booleanValue()));
    }

    private static Result handlePanelScale(String[] args, String panel, String label) {
        if (args.length == 1) {
            return Result.messages(CommandGtb.head + panelScaleStatusMessage(label, panelScale(panel)));
        }
        if (args.length != 2) {
            return Result.messages(CommandGtb.head + "Usage: " + panelScaleUsage(panel));
        }
        Float requested = HudPanelScale.parse(args[1]);
        if (requested == null) {
            return Result.messages(CommandGtb.head + "Usage: " + panelScaleUsage(panel));
        }
        PreviewLayoutEditor.INSTANCE.beginExternalScaleChange(panel);
        try {
            setPanelScale(panel, requested.floatValue());
        } finally {
            PreviewLayoutEditor.INSTANCE.endExternalScaleChange(panel);
        }
        saveSettings();
        return Result.messages(CommandGtb.head + panelScaleStatusMessage(label, panelScale(panel)));
    }

    private static String panelScaleUsage(String panel) {
        return "/gtbs " + panelScaleCommandName(panel) + " scale " + PANEL_SCALE_RANGE;
    }

    private static String panelScaleCommandName(String panel) {
        if ("hint".equals(panel)) return "hintstructure";
        if ("cooldown".equals(panel)) return "cooldowncounter";
        if ("handheld".equals(panel)) return "handhelditem";
        return "environment";
    }

    private static float panelScale(String panel) {
        if ("hint".equals(panel)) return SETTINGS.hintStructureScale;
        if ("cooldown".equals(panel)) return SETTINGS.chatCooldownScale;
        if ("handheld".equals(panel)) return SETTINGS.handheldItemScale;
        return SETTINGS.environmentScale;
    }

    private static void setPanelScale(String panel, float value) {
        value = HudPanelScale.normalize(value);
        if ("hint".equals(panel)) SETTINGS.hintStructureScale = value;
        else if ("cooldown".equals(panel)) SETTINGS.chatCooldownScale = value;
        else if ("handheld".equals(panel)) SETTINGS.handheldItemScale = value;
        else SETTINGS.environmentScale = value;
    }

    static String panelScaleStatusMessageForTest(String label, float value) {
        return panelScaleStatusMessage(label, value);
    }

    private static String panelScaleStatusMessage(String label, float value) {
        return label + " scale is currently \u00a7f" + HudPanelScale.formatPercent(value);
    }

    private static Result handleCooldownCounterPart(String[] args) {
        String part = args[0].toLowerCase(Locale.ROOT);
        boolean current = "timer".equals(part) ? SETTINGS.chatCooldownTimerEnabled : SETTINGS.chatCooldownBarEnabled;
        String label = "timer".equals(part) ? "Chat cooldown timer" : "Chat cooldown bar";
        if (args.length == 1) return Result.messages(CommandGtb.head + backgroundStatusMessage(label, current));
        if (args.length != 2) return Result.messages(CommandGtb.head + "Usage: /gtbs cooldowncounter " + part + " [on|off]");
        Boolean next = parseToggleBoolean(args[1], current);
        if (next == null) return Result.messages(CommandGtb.head + "Usage: /gtbs cooldowncounter " + part + " [on|off]");
        if ("timer".equals(part)) SETTINGS.chatCooldownTimerEnabled = next.booleanValue();
        else SETTINGS.chatCooldownBarEnabled = next.booleanValue();
        saveSettings();
        return Result.messages(CommandGtb.head + backgroundStatusMessage(label, next.booleanValue()));
    }

    private static Result handleCooldownCounterBackground(String[] args) {
        if (args.length == 1) {
            ArrayList<String> messages = new ArrayList<String>();
            messages.add(CommandGtb.head + backgroundStatusMessage("Chat cooldown timer background", SETTINGS.chatCooldownBackground));
            messages.add(CommandGtb.head + backgroundStatusMessage("Chat cooldown bar background", SETTINGS.chatCooldownBarBackground));
            return Result.messages(messages);
        }
        if (args.length == 2 && isCooldownCounterPart(args[1])) {
            return Result.messages(CommandGtb.head + cooldownBackgroundStatus(args[1]));
        }
        if (args.length == 2) {
            Boolean next = parseToggleBoolean(args[1], SETTINGS.chatCooldownBackground);
            if (next == null) return Result.messages(CommandGtb.head + "Usage: /gtbs cooldowncounter background <timer|bar> [on|off]");
            SETTINGS.chatCooldownBackground = next.booleanValue();
            saveSettings();
            return Result.messages(CommandGtb.head
                    + backgroundStatusMessage("Chat cooldown timer background", SETTINGS.chatCooldownBackground));
        }
        if (args.length == 3 && isCooldownCounterPart(args[1])) {
            String part = args[1].toLowerCase(Locale.ROOT);
            boolean current = "timer".equals(part) ? SETTINGS.chatCooldownBackground : SETTINGS.chatCooldownBarBackground;
            Boolean next = parseToggleBoolean(args[2], current);
            if (next == null) return Result.messages(CommandGtb.head + "Usage: /gtbs cooldowncounter background <timer|bar> [on|off]");
            if ("timer".equals(part)) SETTINGS.chatCooldownBackground = next.booleanValue();
            else SETTINGS.chatCooldownBarBackground = next.booleanValue();
            saveSettings();
            return Result.messages(CommandGtb.head + cooldownBackgroundStatus(part));
        }
        return Result.messages(CommandGtb.head + "Usage: /gtbs cooldowncounter background <timer|bar> [on|off]");
    }

    private static Result handleCooldownCounterColor(String[] args) {
        if (args.length == 2) {
            String target = args[1].toLowerCase(Locale.ROOT);
            String message = currentCooldownColorMessage(target);
            return Result.messages(CommandGtb.head + (message == null
                    ? "Usage: /gtbs cooldowncounter setcolor <bar|timer> <color>"
                    : message));
        }
        if (args.length != 3) {
            return Result.messages(CommandGtb.head + "Usage: /gtbs cooldowncounter setcolor <bar|timer> <color>");
        }
        String target = args[1].toLowerCase(Locale.ROOT);
        String color = normalizeCommandColor(args[2]);
        if (color == null) return Result.messages(CommandGtb.head + invalidColorMessage());
        String colorCode = color.substring(1);
        if ("bar".equals(target)) {
            SETTINGS.chatCooldownBarColor = color;
            saveSettings();
            return Result.messages(CommandGtb.head + "Cooldown bar color set to " + color + colorCode);
        }
        if ("timer".equals(target)) {
            SETTINGS.chatCooldownTimerColor = color;
            saveSettings();
            return Result.messages(CommandGtb.head + "Cooldown timer color set to " + color + colorCode);
        }
        return Result.messages(CommandGtb.head + "Usage: /gtbs cooldowncounter setcolor <bar|timer> <color>");
    }

    private static Result handleGuessPeek(String[] args) {
        if (args.length == 0) {
            SETTINGS.guessPeek = !SETTINGS.guessPeek;
            saveSettings();
            return Result.messages(CommandGtb.head + "Guess peek is currently " + onOff(SETTINGS.guessPeek));
        }
        if ("showlangcodes".equalsIgnoreCase(args[0])) {
            if (args.length == 1) {
                return Result.messages(CommandGtb.head + "Guess peek language codes are currently "
                        + onOff(SETTINGS.guessPeekShowLangCodes));
            }
            if (args.length != 2) return Result.messages(CommandGtb.head + "Usage: /gtbs guesspeek showlangcodes [on|off]");
            Boolean enabled = parseOptionalBoolean(args[1]);
            if (enabled == null) return Result.messages(CommandGtb.head + "Usage: /gtbs guesspeek showlangcodes [on|off]");
            SETTINGS.guessPeekShowLangCodes = enabled.booleanValue();
            saveSettings();
            return Result.messages(CommandGtb.head + "Guess peek language codes are currently "
                    + onOff(SETTINGS.guessPeekShowLangCodes));
        }
        if ("display".equalsIgnoreCase(args[0])) {
            if (args.length == 1) {
                SETTINGS.guessPeekDisplay = GtbStructuresSettings.normalizeGuessPeekDisplay(
                        SETTINGS.guessPeekDisplay);
                return Result.messages(CommandGtb.head + "Guess peek display is currently \u00a7f"
                        + SETTINGS.guessPeekDisplay);
            }
            if (args.length != 2 || !("hover".equalsIgnoreCase(args[1])
                    || "suffix".equalsIgnoreCase(args[1]))) {
                return Result.messages(CommandGtb.head + "Usage: /gtbs guesspeek display [hover|suffix]");
            }
            SETTINGS.guessPeekDisplay = GtbStructuresSettings.normalizeGuessPeekDisplay(args[1]);
            saveSettings();
            return Result.messages(CommandGtb.head + "Guess peek display is currently \u00a7f"
                    + SETTINGS.guessPeekDisplay);
        }
        return Result.messages(CommandGtb.head + "Usage: /gtbs guesspeek [showlangcodes [on|off]|display [hover|suffix]]");
    }

    private static Result handleScrambleGuess(String[] args) {
        if (args.length > 0 && "fancy".equalsIgnoreCase(args[0])) {
            if (args.length == 1) {
                return Result.messages(CommandGtb.head + "Fancy scramble guess is currently "
                        + onOff(SETTINGS.scrambleGuessFancy));
            }
            if (args.length != 2) {
                return Result.messages(CommandGtb.head + "Usage: /gtbs scrambleguess fancy [on|off]");
            }
            Boolean enabled = parseOptionalBoolean(args[1]);
            if (enabled == null) {
                return Result.messages(CommandGtb.head + "Usage: /gtbs scrambleguess fancy [on|off]");
            }
            SETTINGS.scrambleGuessFancy = enabled.booleanValue();
            saveSettings();
            return Result.messages(CommandGtb.head + "Fancy scramble guess is currently "
                    + onOff(SETTINGS.scrambleGuessFancy));
        }
        if (args.length == 0) {
            SETTINGS.scrambleGuess = !SETTINGS.scrambleGuess;
        } else if (args.length == 1) {
            Boolean enabled = parseOptionalBoolean(args[0]);
            if (enabled == null) {
                return Result.messages(CommandGtb.head + "Usage: /gtbs scrambleguess [on|off]");
            }
            SETTINGS.scrambleGuess = enabled.booleanValue();
        } else {
            return Result.messages(CommandGtb.head + "Usage: /gtbs scrambleguess [on|off]");
        }
        saveSettings();
        return Result.messages(CommandGtb.head + "Scramble guess is currently "
                + onOff(SETTINGS.scrambleGuess));
    }


    private static Result handleGuessSound(String[] args) {
        MinecraftClientSoundBridge.ensureCatalog();
        if (args.length == 0) return Result.messages(CommandGtb.head + "Guess sounds: \u00a7a1\u00a7f: " + SETTINGS.guessSound1
                + ", \u00a7e2\u00a7f: " + SETTINGS.guessSound2 + ", \u00a7c3\u00a7f: " + SETTINGS.guessSound3
                + "; volume: " + GtbStructuresSettings.formatGuessSoundVolume(SETTINGS.guessSoundVolume));
        if ("volume".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                Float volume = GtbStructuresSettings.parseGuessSoundVolume(args[1]);
                if (volume == null) return Result.messages(CommandGtb.head + "Volume must be between 0.0 and 30.0.");
                SETTINGS.guessSoundVolume = volume; saveSettings();
            } else if (args.length != 1) return Result.messages(CommandGtb.head + "Usage: /gtbs guesssound volume [0.0-30.0]");
            return Result.messages(CommandGtb.head + "Guess sound volume: \u00a7f"
                    + GtbStructuresSettings.formatGuessSoundVolume(SETTINGS.guessSoundVolume));
        }
        if (args.length <= 2 && (args[0].equals("1") || args[0].equals("2") || args[0].equals("3"))) {
            int points = Integer.parseInt(args[0]);
            if (args.length == 2) {
                String choice = GuessSounds.normalizeChoice(args[1]);
                if (choice == null) return Result.messages(CommandGtb.head + "Unknown Minecraft sound: \u00a7f" + args[1]);
                SETTINGS.setGuessSound(points, choice); saveSettings();
            }
            return Result.messages(CommandGtb.head + "Guess sound for " + guessSoundPointColor(points)
                    + points + "\u00a77 points: \u00a7f" + SETTINGS.guessSound(points));
        }
        return Result.messages(CommandGtb.head + "Usage: /gtbs guesssound <1|2|3> [sound|none|random]");
    }

    private static String guessSoundPointColor(int points) {
        if (points == 1) return "\u00a7a";
        if (points == 2) return "\u00a7e";
        return "\u00a7c";
    }

    private static Result handleTrollMode(String[] args) {
        if (args.length > 0 && "autodisable".equalsIgnoreCase(args[0])) {
            if (args.length == 1) {
                return Result.messages(CommandGtb.head + "Troll mode auto-disable is currently "
                        + onOff(SETTINGS.trollModeAutoDisable));
            }
            if (args.length == 2) {
                Boolean enabled = parseOptionalBoolean(args[1]);
                if (enabled != null) {
                    SETTINGS.trollModeAutoDisable = enabled.booleanValue();
                    saveSettings();
                    return Result.messages(CommandGtb.head + "Troll mode auto-disable is currently "
                            + onOff(SETTINGS.trollModeAutoDisable));
                }
            }
            return Result.messages(CommandGtb.head + "Usage: /gtbs trollmode autodisable [on|off]");
        }
        if (args.length == 0) {
            SETTINGS.trollMode = !SETTINGS.trollMode;
        } else if (args.length == 1) {
            Boolean enabled = parseOptionalBoolean(args[0]);
            if (enabled == null) return Result.messages(CommandGtb.head + "Usage: /gtbs trollmode [on|off|autodisable [on|off]]");
            SETTINGS.trollMode = enabled.booleanValue();
        } else {
            return Result.messages(CommandGtb.head + "Usage: /gtbs trollmode [on|off|autodisable [on|off]]");
        }
        saveSettings();
        return Result.messages(CommandGtb.head + "Troll mode is currently " + onOff(SETTINGS.trollMode));
    }

    private static Result handleHeader(String[] args) {
        String requested = join(args);
        if (!ChatHeader.set(requested)) {
            return Result.messages(CommandGtb.head + "Header must be at most \u00a7f"
                    + ChatHeader.MAX_VISIBLE_LENGTH + "\u00a77 visible characters.");
        }
        saveSettings();
        if (requested.trim().isEmpty()) {
            return Result.messages(CommandGtb.head + "Header reset to default.");
        }
        return Result.messages(CommandGtb.head + "Header set to \u00a7r" + ChatHeader.rendered());
    }

    private static Result handleStats(String[] args) {
        return Result.messages(StatsArchive.handleCommand(
                services.gameDir(), services.accountPlayerUuid(),
                services.accountPlayerName(), services.localPlayerName(),
                SETTINGS.lastLangCode, args));
    }

    private static Result handlePosition(String[] args, String label, String usage,
                                         float currentX, float currentY, PositionSetter setter) {
        if (args.length == 2 && isLocationAxis(args[1])) {
            float value = "x".equalsIgnoreCase(args[1]) ? currentX : currentY;
            return Result.messages(CommandGtb.head + locationStatusMessage(label, args[1], value));
        }
        if (args.length != 3) return Result.messages(CommandGtb.head + "Usage: " + usage);
        String axis = args[1].toLowerCase(Locale.ROOT);
        try {
            float value = Float.parseFloat(args[2]);
            if (value < 0F || value > 1F) {
                return Result.messages(CommandGtb.head + "Please enter a value between 0 and 1.");
            }
            if (!"x".equals(axis) && !"y".equals(axis)) {
                return Result.messages(CommandGtb.head + "Usage: " + usage);
            }
            setter.set(axis, value);
            saveSettings();
            return Result.messages(CommandGtb.head + label + " " + axis.toUpperCase(Locale.ROOT)
                    + " position set to \u00a7f" + value);
        } catch (NumberFormatException e) {
            return Result.messages(CommandGtb.head + "Usage: " + usage);
        }
    }

    private static Result handleSimpleBackground(String[] args, String label, String usage,
                                                 boolean current, BooleanSetter setter) {
        if (args.length == 1) return Result.messages(CommandGtb.head + backgroundStatusMessage(label, current));
        if (args.length != 2) return Result.messages(CommandGtb.head + "Usage: " + usage);
        Boolean next = parseToggleBoolean(args[1], current);
        if (next == null) return Result.messages(CommandGtb.head + "Usage: " + usage);
        setter.set(next.booleanValue());
        saveSettings();
        return Result.messages(CommandGtb.head + backgroundStatusMessage(label, next.booleanValue()));
    }

    private static Result shortcutStatus(String label, boolean enabled) {
        return Result.messages(CommandGtb.head + label + " is currently " + onOff(enabled));
    }

    private static String cooldownBackgroundStatus(String part) {
        return "timer".equalsIgnoreCase(part)
                ? backgroundStatusMessage("Chat cooldown timer background", SETTINGS.chatCooldownBackground)
                : backgroundStatusMessage("Chat cooldown bar background", SETTINGS.chatCooldownBarBackground);
    }

    private static boolean environmentPartEnabled(String target) {
        if ("weather".equals(target)) return SETTINGS.environmentWeatherEnabled;
        if ("biome".equals(target)) return SETTINGS.environmentBiomeEnabled;
        return SETTINGS.environmentTimeEnabled;
    }

    private static String currentHintStructureColorMessage(String target) {
        if ("bracket".equals(target)) return "Bracket color is currently " + colorCodeLabel(SETTINGS.colorBrackets, "\u00a76");
        if ("number".equals(target)) return "Number color is currently " + colorCodeLabel(SETTINGS.colorNumbers, "\u00a7e");
        if ("hyphen".equals(target)) return "Hyphen color is currently " + colorCodeLabel(SETTINGS.colorHyphens, "\u00a77");
        return null;
    }

    private static String currentCooldownColorMessage(String target) {
        if ("bar".equals(target)) return "Cooldown bar color is currently " + colorCodeLabel(SETTINGS.chatCooldownBarColor, "\u00a7e");
        if ("timer".equals(target)) return "Cooldown timer color is currently " + colorCodeLabel(SETTINGS.chatCooldownTimerColor, "\u00a7f");
        return null;
    }

    private static String colorCodeLabel(String color, String fallback) {
        String normalized = MinecraftFormatting.normalizeMinecraftColorCode(color, fallback);
        return normalized + normalized.substring(1);
    }

    private static String normalizeCommandColor(String raw) {
        String color = MinecraftFormatting.normalizeMinecraftColorCode(raw, "");
        return color.isEmpty() ? null : color;
    }

    private static String invalidColorMessage() {
        return "Color must be a single character 0-9 or a-f. Use \u00a7f/gtbs colorlist\u00a77.";
    }

    private static String locationStatusMessage(String label, String axis, float value) {
        return label + " " + axis.toUpperCase(Locale.ROOT) + " position is currently \u00a7f" + value;
    }

    private static String backgroundStatusMessage(String label, boolean enabled) {
        return label + " is currently " + onOff(enabled);
    }

    private static String onOff(boolean enabled) {
        return enabled ? "\u00a7aON" : "\u00a7cOFF";
    }

    private static String displaySpawnEggSkin(String skin) {
        return "new".equalsIgnoreCase(skin) ? "New" : "Classic";
    }

    private static String displayEnvironmentTimePreset(String preset) {
        return "blocks".equalsIgnoreCase(preset) ? "Blocks" : "Clock";
    }

    private static Boolean parseToggleBoolean(String raw, boolean current) {
        if (raw != null && raw.trim().equalsIgnoreCase("toggle")) return Boolean.valueOf(!current);
        return parseOptionalBoolean(raw);
    }

    private static Boolean parseOptionalBoolean(String raw) {
        if (raw == null) return null;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(value) || "on".equals(value) || "1".equals(value)
                || "yes".equals(value)) return Boolean.TRUE;
        if ("false".equals(value) || "off".equals(value) || "0".equals(value)
                || "no".equals(value)) return Boolean.FALSE;
        return null;
    }

    private static Integer parseLanguageGroupIndex(String raw) {
        try {
            int value = Integer.parseInt(raw);
            return value < 1 ? null : Integer.valueOf(value);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static LanguageListParse parseLanguageList(String raw) {
        if (raw == null || raw.trim().isEmpty()) return LanguageListParse.error(raw);
        String[] pieces = raw.split(",");
        LinkedHashSet<String> codes = new LinkedHashSet<String>();
        for (String piece : pieces) {
            String code = CopyTranslation.resolveLanguageCode(piece);
            if (code == null || !ShortcutLanguageGroups.isSupportedLanguage(code)) {
                return LanguageListParse.error(piece);
            }
            codes.add(ShortcutLanguageGroups.normalizeLanguageCode(code));
        }
        return codes.isEmpty() ? LanguageListParse.error(raw)
                : LanguageListParse.ok(new ArrayList<String>(codes));
    }

    private static String joinRange(String[] args, int start, int endExclusive) {
        StringBuilder out = new StringBuilder();
        for (int i = start; i < endExclusive; i++) {
            if (i > start) out.append(' ');
            out.append(args[i]);
        }
        return out.toString().trim();
    }

    private static String safeDetail(String detail) {
        return detail == null ? "" : detail;
    }

    private static String availableLanguageCodes() {
        StringBuilder out = new StringBuilder();
        for (String code : ShortcutLanguageGroups.supportedLanguageCodes()) {
            if (out.length() > 0) out.append(", ");
            out.append(code);
        }
        return out.toString();
    }

    private static Result handleLeaveRejoin(String[] args, String commandRoot) {
        if (args == null || args.length == 0) {
            LEAVE_REJOIN.start(Math.round(SETTINGS.lrjDelaySeconds * 1000F));
            services.sendServerCommand(SETTINGS.lrjBypassLobbyProtection
                    ? BYPASS_LOBBY_PROTECTION_COMMAND : LOBBY_COMMAND);
            return Result.empty();
        }
        if ("delay".equalsIgnoreCase(args[0])) {
            String usage = "Usage: " + commandRoot + " delay [time]";
            if (args.length == 1) return Result.messages(CommandGtb.head + lrjDelayStatusMessage());
            if (args.length != 2) return Result.messages(CommandGtb.head + usage);
            Float delay = GtbStructuresSettings.parseLrjDelaySeconds(args[1]);
            if (delay == null) return Result.messages(CommandGtb.head
                    + "Please enter a time from 0.0 to 10.0 seconds.");
            SETTINGS.lrjDelaySeconds = delay.floatValue();
            saveSettings();
            return Result.messages(CommandGtb.head + "LRJ delay set to \u00a7f"
                    + GtbStructuresSettings.formatLrjDelaySeconds(SETTINGS.lrjDelaySeconds)
                    + " seconds.");
        }
        String usage = "Usage: " + commandRoot + " bypassprotection [on|off]";
        if (!"bypassprotection".equalsIgnoreCase(args[0]) || args.length > 2) {
            return Result.messages(CommandGtb.head + usage);
        }
        if (args.length == 2) {
            Boolean enabled = parseOptionalBoolean(args[1]);
            if (enabled == null) return Result.messages(CommandGtb.head + usage);
            SETTINGS.lrjBypassLobbyProtection = enabled.booleanValue();
            saveSettings();
        }
        return Result.messages(CommandGtb.head
                + leaveRejoinProtectionStatusMessage(SETTINGS.lrjBypassLobbyProtection));
    }

    static String leaveRejoinProtectionStatusMessage(boolean enabled) {
        return "Bypass /l protection is currently " + onOff(enabled);
    }

    private static String lrjDelayStatusMessage() {
        return "LRJ delay is currently \u00a7f"
                + GtbStructuresSettings.formatLrjDelaySeconds(SETTINGS.lrjDelaySeconds) + " seconds.";
    }

    private static Result handleEndGameFireworkSounds(String[] args) {
        if (args.length > 1) return Result.messages(CommandGtb.head
                + "Usage: /gtbs fireworks [on|off|toggle]");
        if (args.length == 1) {
            Boolean value = parseToggleBoolean(args[0], SETTINGS.endGameFireworkSounds);
            if (value == null) return Result.messages(CommandGtb.head
                    + "Usage: /gtbs fireworks [on|off|toggle]");
            SETTINGS.endGameFireworkSounds = value.booleanValue();
            saveSettings();
        }
        return Result.messages(CommandGtb.head + "End-game firework sounds are currently "
                + onOff(SETTINGS.endGameFireworkSounds));
    }

    private static boolean isHintStructureCommand(String command) {
        return command != null && ("hintstructure".equalsIgnoreCase(command) || "hs".equalsIgnoreCase(command));
    }

    private static boolean isEnvironmentCommand(String command) {
        return command != null && ("environment".equalsIgnoreCase(command) || "env".equalsIgnoreCase(command));
    }

    private static boolean isEnvironmentPart(String command) {
        return command != null && ("weather".equalsIgnoreCase(command)
                || "biome".equalsIgnoreCase(command) || "time".equalsIgnoreCase(command));
    }

    private static boolean isLocationAxis(String axis) {
        return axis != null && ("x".equalsIgnoreCase(axis) || "y".equalsIgnoreCase(axis));
    }

    private static boolean isCooldownCounterPart(String value) {
        return value != null && ("timer".equalsIgnoreCase(value) || "bar".equalsIgnoreCase(value));
    }

    private static boolean isShortcutNonAsciiCategory(String raw) {
        if (raw == null) return false;
        String category = raw.trim().toLowerCase(Locale.ROOT);
        return "extendedlatin".equals(category)
                || "cyrillic".equals(category)
                || "hanzi".equals(category)
                || "japanese".equals(category)
                || "hangul".equals(category);
    }

    private static ShortcutNonAsciiCategory shortcutCategory(String raw) {
        String category = raw.trim().toLowerCase(Locale.ROOT);
        if ("extendedlatin".equals(category)) return ShortcutNonAsciiCategory.EXTENDED_LATIN;
        if ("cyrillic".equals(category)) return ShortcutNonAsciiCategory.CYRILLIC;
        if ("hanzi".equals(category)) return ShortcutNonAsciiCategory.HANZI;
        if ("japanese".equals(category)) return ShortcutNonAsciiCategory.JAPANESE;
        return ShortcutNonAsciiCategory.HANGUL;
    }

    private static String shortcutCategoryLabel(String raw) {
        String category = raw.trim().toLowerCase(Locale.ROOT);
        if ("extendedlatin".equals(category)) return "Extended Latin shortcuts";
        if ("cyrillic".equals(category)) return "Cyrillic shortcuts";
        if ("hanzi".equals(category)) return "Hanzi shortcuts";
        if ("japanese".equals(category)) return "Kanji & Kana shortcuts";
        return "Hangul shortcuts";
    }

    private static void applySettingsToRuntime() {
        CommandGtb.refreshHeader();
        CommandGtb.colorBrackets = SETTINGS.colorBrackets;
        CommandGtb.colorNumbers = SETTINGS.colorNumbers;
        CommandGtb.colorHyphens = SETTINGS.colorHyphens;
        CommandGtb.chatCooldownBarColor = SETTINGS.chatCooldownBarColor;
        CommandGtb.chatCooldownTimerColor = SETTINGS.chatCooldownTimerColor;
        GameStats.INSTANCE.setTrackingEnabled(SETTINGS.trackingEnabled);
        GameStats.INSTANCE.setAutoRequeueDelaySeconds(SETTINGS.autoRequeueDelaySeconds);
        GameStats.INSTANCE.setAutoRequeue(SETTINGS.autoRequeue);
    }

    private static void saveSettings() {
        applySettingsToRuntime();
        SETTINGS.save();
    }

    private static List<String> prefixAll(List<String> messages, String prefix) {
        if (messages == null || messages.isEmpty()) return Collections.emptyList();
        ArrayList<String> out = new ArrayList<String>(messages.size());
        for (String message : messages) out.add(prefix + message);
        return out;
    }

    private static List<String> completeCopyTranslation(String rawArgs) {
        String[] args = parseCompletionArgs(rawArgs);
        ArrayList<String> suggestions = new ArrayList<String>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            if ("prefix".startsWith(prefix)) suggestions.add("prefix");
            if ("caps".startsWith(prefix)) suggestions.add("caps");
        } else if (args.length == 2 && "prefix".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if ("true".startsWith(prefix)) suggestions.add("true");
            if ("false".startsWith(prefix)) suggestions.add("false");
        } else if (args.length == 2 && "caps".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if ("on".startsWith(prefix)) suggestions.add("on");
            if ("off".startsWith(prefix)) suggestions.add("off");
        }
        return replaceLastArgument(rawArgs, suggestions);
    }

    private static List<String> completeForwardedCopyTranslation(String rawArgs) {
        String suffix = rawArgs == null ? "" : rawArgs.trim();
        if (!suffix.toLowerCase(Locale.ROOT).startsWith("copytranslation")) {
            return Collections.emptyList();
        }
        String command = "copytranslation";
        String copyArgs = suffix.length() == command.length() ? ""
                : suffix.substring(command.length()).trim();
        boolean trailingSpace = rawArgs != null && !rawArgs.isEmpty()
                && Character.isWhitespace(rawArgs.charAt(rawArgs.length() - 1));
        if (trailingSpace) copyArgs += " ";
        List<String> direct = completeCopyTranslation(copyArgs);
        ArrayList<String> out = new ArrayList<String>(direct.size());
        for (String suggestion : direct) out.add(command + " " + suggestion);
        return out;
    }

    private static List<String> completeGtbsShortcut(String command, String rawArgs) {
        String[] args = parseCompletionArgs(rawArgs);
        List<String> suggestions = COMMAND.addTabCompletionOptions(
                null, prefixedArgs(command, args), null);
        if (suggestions == null || suggestions.isEmpty()) {
            return Collections.emptyList();
        }
        String commandPrefix = command + " ";
        ArrayList<String> stripped = new ArrayList<String>(suggestions.size());
        for (String suggestion : suggestions) {
            if (suggestion.startsWith(commandPrefix)) stripped.add(suggestion.substring(commandPrefix.length()));
            else if (!command.equals(suggestion)) stripped.add(suggestion);
        }
        return replaceLastArgument(rawArgs, stripped);
    }

    private static List<String> completeGtw(String rawArgs) {
        String[] args = parseCompletionArgs(rawArgs);
        ArrayList<String> suggestions = new ArrayList<String>();
        if (args.length == 1) {
            addMatching(suggestions, args[0], "start", "stop", "showenglish", "showcount", "difficulty");
        } else if ("start".equalsIgnoreCase(args[0])) {
            String languagePrefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            for (String option : GtwLogic.languageCompletionOptions()) {
                if (option.toLowerCase(Locale.ROOT).startsWith(languagePrefix)) suggestions.add(option);
            }
        } else if ("difficulty".equalsIgnoreCase(args[0]) && args.length == 2) {
            addMatching(suggestions, args[1], "easy", "medium", "hard", "all");
        } else if ("showenglish".equalsIgnoreCase(args[0]) && args.length == 2) {
            addMatching(suggestions, args[1], "true", "false");
        } else if ("showcount".equalsIgnoreCase(args[0]) && args.length == 2) {
            addMatching(suggestions, args[1], "on", "off");
        }
        return replaceLastArgument(rawArgs, suggestions);
    }

    private static List<String> completeForwardedGtw(String rawArgs) {
        String suffix = rawArgs == null ? "" : rawArgs.trim();
        if (!suffix.toLowerCase(Locale.ROOT).startsWith("gtw")) return Collections.emptyList();
        String gtwArgs = suffix.length() == 3 ? "" : suffix.substring(3).trim();
        boolean trailingSpace = rawArgs != null && !rawArgs.isEmpty()
                && Character.isWhitespace(rawArgs.charAt(rawArgs.length() - 1));
        if (trailingSpace) gtwArgs += " ";
        List<String> direct = completeGtw(gtwArgs);
        ArrayList<String> out = new ArrayList<String>(direct.size());
        for (String suggestion : direct) out.add("gtw " + suggestion);
        return out;
    }

    private static void addMatching(List<String> out, String prefix, String... options) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.startsWith(normalized)) out.add(option);
        }
    }

    private static List<String> completeRoot(String rawArgs) {
        String prefix = rawArgs == null ? "" : rawArgs.trim().toLowerCase(Locale.ROOT);
        ArrayList<String> out = new ArrayList<String>();
        for (String root : ROOT_COMMANDS) {
            if (root.startsWith(prefix)) out.add(root);
        }
        return out;
    }

    private static String normalizeRoot(String root) {
        return root == null ? "" : root.trim().toLowerCase(Locale.ROOT);
    }

    private static String[] parseExecutionArgs(String rawArgs) {
        if (rawArgs == null) return new String[0];
        String trimmed = rawArgs.trim();
        if (trimmed.isEmpty()) return new String[0];
        return trimmed.split("\\s+");
    }

    private static String[] parseCompletionArgs(String rawArgs) {
        if (rawArgs == null || rawArgs.isEmpty()) return new String[] {""};
        String trimmedLeading = trimLeading(rawArgs);
        if (trimmedLeading.isEmpty()) return new String[] {""};
        String[] split = trimmedLeading.trim().split("\\s+");
        if (!Character.isWhitespace(trimmedLeading.charAt(trimmedLeading.length() - 1))) {
            return split;
        }
        String[] out = new String[split.length + 1];
        System.arraycopy(split, 0, out, 0, split.length);
        out[split.length] = "";
        return out;
    }

    private static String trimLeading(String raw) {
        int index = 0;
        while (index < raw.length() && Character.isWhitespace(raw.charAt(index))) {
            index++;
        }
        return raw.substring(index);
    }

    private static List<String> replaceLastArgument(String rawArgs, List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return Collections.emptyList();
        }
        String raw = rawArgs == null ? "" : trimLeading(rawArgs);
        String prefix;
        if (raw.isEmpty()) {
            prefix = "";
        } else if (Character.isWhitespace(raw.charAt(raw.length() - 1))) {
            prefix = raw;
        } else {
            int start = raw.length() - 1;
            while (start >= 0 && !Character.isWhitespace(raw.charAt(start))) {
                start--;
            }
            prefix = raw.substring(0, start + 1);
        }

        ArrayList<String> out = new ArrayList<String>(suggestions.size());
        for (String suggestion : suggestions) {
            out.add(prefix + suggestion);
        }
        return out;
    }

    private static String[] prefixedArgs(String first, String[] rest) {
        int n = rest == null ? 0 : rest.length;
        String[] out = new String[n + 1];
        out[0] = first;
        for (int i = 0; i < n; i++) out[i + 1] = rest[i];
        return out;
    }

    private static String[] sliceFrom(String[] args, int start) {
        int n = Math.max(0, args.length - start);
        String[] out = new String[n];
        for (int i = 0; i < n; i++) out[i] = args[start + i];
        return out;
    }

    private static String join(String[] args) {
        if (args == null || args.length == 0) return "";
        StringBuilder out = new StringBuilder(args[0]);
        for (int i = 1; i < args.length; i++) {
            out.append(' ').append(args[i]);
        }
        return out.toString();
    }

    private static String colorList() {
        String[] colors = {"0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f"};
        StringBuilder out = new StringBuilder();
        for (String color : colors) {
            if (out.length() > 0) out.append(' ');
            out.append('\u00a7').append(color).append(color);
        }
        return out.toString();
    }

    private interface PositionSetter {
        void set(String axis, float value);
    }

    private interface BooleanSetter {
        void set(boolean value);
    }

    private static final class DefaultServices implements Services {
        @Override
        public File gameDir() {
            return new File(System.getProperty("user.dir", "."));
        }

        @Override
        public File configDir() {
            return new File(gameDir(), "config");
        }

        @Override
        public String localPlayerName() {
            return null;
        }

        @Override
        public void sendServerCommand(String command) {
            // Loader entrypoints replace this with a live Minecraft command sender.
        }

        @Override
        public void sendChat(String message) {
            // Loader entrypoints replace this with a live Minecraft chat sender.
        }

        @Override
        public boolean copyToClipboard(String text) {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
                return true;
            } catch (RuntimeException | Error e) {
                return false;
            }
        }
    }

    private static final class GtwSession {
        private final Random rng = new Random();
        private boolean active;
        private String langCode;
        private GtwLogic.Difficulty difficulty;
        private String englishTheme;
        private String displayedTheme;
        private String[] hints;
        private int hintLevel;
        private int ticksToNext;
        private int guessCount;

        List<String> onClientTick() {
            if (!active) return Collections.emptyList();
            ticksToNext--;
            if (ticksToNext > 0) return Collections.emptyList();

            hintLevel++;
            if (hintLevel < hints.length) {
                ticksToNext = GTW_TICKS_PER_HINT;
                return Collections.singletonList("Hint " + (hintLevel + 1) + ": \u00a7f" + hints[hintLevel]);
            }
            return end("Game over!");
        }

        List<String> start(String requestedLangCode) {
            if (active) {
                return Collections.singletonList("A Guess The Wordhint game is already in progress. Use \u00a7f/gtbs gtw stop\u00a77 first.");
            }
            TranslationData.ensureLocalLoaded();
            if (!TranslationData.isLoaded()) {
                return Collections.singletonList("Translation data isn't loaded yet — try again in a moment.");
            }
            GtwLogic.Difficulty capturedDifficulty = GtwLogic.parseDifficulty(SETTINGS.gtwDifficulty);
            if (capturedDifficulty == null) capturedDifficulty = GtwLogic.Difficulty.ALL;
            String pickedEnglish = GtwLogic.pickRandomEnglishTheme(rng, capturedDifficulty);
            if (pickedEnglish == null) return Collections.singletonList("No themes available.");
            String displayed = TranslationData.getDisplayedTheme(requestedLangCode, pickedEnglish);
            if (displayed == null || displayed.isEmpty()) {
                return Collections.singletonList("Failed to resolve theme translation.");
            }
            active = true;
            langCode = requestedLangCode;
            difficulty = capturedDifficulty;
            englishTheme = pickedEnglish;
            displayedTheme = displayed;
            hints = GtwLogic.buildProgressiveHints(displayed, 3, rng);
            hintLevel = 0;
            ticksToNext = GTW_TICKS_PER_HINT;
            guessCount = 0;
            if (!requestedLangCode.equals(SETTINGS.gtwDefaultLang)) {
                SETTINGS.gtwDefaultLang = requestedLangCode;
                saveSettings();
            }
            ArrayList<String> messages = new ArrayList<String>();
            messages.add("Starting a Guess The Wordhint game...");
            messages.add("Selected language: \u00a7f" + GtwLogic.languageDisplayName(langCode)
                    + "\u00a77; difficulty: " + difficulty.color + difficulty.displayName);
            messages.add("Hint 1: \u00a7f" + hints[0] + " \u00a77( " + GtwLogic.structureNotation(displayed) + " )");
            return messages;
        }

        List<String> stop() {
            if (!active) return Collections.singletonList("You are not currently in a Guess The Wordhint game");
            return end("Game over!");
        }

        List<String> guess(String guess) {
            if (!active) return Collections.singletonList("You are not currently in a Guess The Wordhint game");
            guessCount++;
            ArrayList<String> messages = new ArrayList<String>();
            messages.add("Guess " + guessCount + ": \u00a7f" + guess);
            if (matchesAnyGtwTranslation(guess, englishTheme)) {
                messages.addAll(end("Correct!"));
            }
            return messages;
        }

        void reset() {
            active = false;
            langCode = null;
            difficulty = null;
            englishTheme = null;
            displayedTheme = null;
            hints = null;
            hintLevel = 0;
            ticksToNext = 0;
            guessCount = 0;
        }

        private List<String> end(String prefix) {
            ArrayList<String> messages = new ArrayList<String>();
            messages.add(prefix + " The theme is: \u00a7f" + displayedTheme + englishParens());
            addAllMatching(messages);
            active = false;
            return messages;
        }

        private void addAllMatching(List<String> messages) {
            if (hints == null || hints.length == 0) return;
            Map<String, Set<String>> matches = TranslationData.findMatchingDisplayedThemes(langCode, hints[0]);
            if (matches == null || matches.isEmpty()) return;

            Map<String, Set<String>> displayedMatches = new LinkedHashMap<String, Set<String>>();
            for (Map.Entry<String, Set<String>> entry : matches.entrySet()) {
                Set<String> filteredOrigins = filterOriginsForDifficulty(entry.getValue());
                if (!filteredOrigins.isEmpty()) {
                    displayedMatches.put(entry.getKey(), filteredOrigins);
                }
            }
            if (displayedMatches.isEmpty()) return;

            boolean isPirate = "pirate".equalsIgnoreCase(langCode);
            StringBuilder sb = new StringBuilder();
            sb.append("All themes matching the initial hint: ");
            boolean first = true;
            for (Map.Entry<String, Set<String>> entry : displayedMatches.entrySet()) {
                Set<String> filteredOrigins = entry.getValue();
                if (!first) sb.append("\u00a77, ");
                first = false;
                sb.append("\u00a7f").append(entry.getKey());
                if (shouldShowAllMatchingEnglish(isPirate, entry.getKey(), filteredOrigins)) {
                    sb.append("\u00a77 (");
                    boolean firstEnglish = true;
                    for (String english : filteredOrigins) {
                        if (!firstEnglish) sb.append(", ");
                        firstEnglish = false;
                        sb.append(english);
                    }
                    sb.append(")");
                }
            }
            if (SETTINGS.gtwShowCount) {
                sb.append(" \u00a77(\u00a7e").append(displayedMatches.size()).append("\u00a77)");
            }
            messages.add(sb.toString());
        }

        private Set<String> filterOriginsForDifficulty(Set<String> origins) {
            if (origins == null || origins.isEmpty()) return Collections.emptySet();
            GtwLogic.Difficulty effective = difficulty == null ? GtwLogic.Difficulty.ALL : difficulty;
            if (effective == GtwLogic.Difficulty.ALL) return origins;
            Set<String> filtered = new java.util.LinkedHashSet<String>();
            for (String origin : origins) {
                if (GtwLogic.matchesDifficulty(origin, effective)) filtered.add(origin);
            }
            return filtered;
        }

        private boolean shouldShowAllMatchingEnglish(boolean isPirate, String displayed, Set<String> englishOrigins) {
            if (!SETTINGS.gtwShowEnglish || "en".equalsIgnoreCase(langCode)) return false;
            if (!isPirate) return true;
            if (englishOrigins == null || englishOrigins.isEmpty()) return false;
            return englishOrigins.size() != 1 || !englishOrigins.contains(displayed);
        }

        private String englishParens() {
            if (!SETTINGS.gtwShowEnglish || "en".equalsIgnoreCase(langCode)) return "";
            if (displayedTheme != null && displayedTheme.equals(englishTheme)) return "";
            return "\u00a77 (" + englishTheme + ")";
        }
    }

    private static Result showAllMatchingCountStatus() {
        return Result.messages(CommandGtb.head + "Show All Matching total theme count is currently "
                + onOff(SETTINGS.showAllMatchingShowCount));
    }

    private static Result gtwShowCountStatus() {
        return Result.messages(CommandGtb.head + "GTW total theme count is currently "
                + onOff(SETTINGS.gtwShowCount));
    }

    private static final class LanguageListParse {
        final boolean ok;
        final List<String> codes;
        final String invalid;

        private LanguageListParse(boolean ok, List<String> codes, String invalid) {
            this.ok = ok;
            this.codes = codes;
            this.invalid = invalid;
        }

        static LanguageListParse ok(List<String> codes) {
            return new LanguageListParse(true, codes, null);
        }

        static LanguageListParse error(String invalid) {
            return new LanguageListParse(false, null, invalid == null ? "" : invalid.trim());
        }
    }

    public static final class Result {
        private final List<String> messages;
        private final HelpPageNavigation helpPage;

        private Result(List<String> messages) {
            this(messages, null);
        }

        private Result(List<String> messages, HelpPageNavigation helpPage) {
            this.messages = Collections.unmodifiableList(new ArrayList<String>(messages));
            this.helpPage = helpPage;
        }

        public static Result empty() {
            return new Result(Collections.<String>emptyList());
        }

        public static Result messages(String message) {
            ArrayList<String> messages = new ArrayList<String>();
            messages.add(message);
            return new Result(messages);
        }

        public static Result messages(List<String> messages) {
            return new Result(messages == null ? Collections.<String>emptyList() : messages);
        }

        public static Result helpPage(List<String> messages, HelpPageNavigation navigation) {
            return new Result(messages == null ? Collections.<String>emptyList() : messages, navigation);
        }

        public List<String> messages() {
            return messages;
        }

        public HelpPageNavigation helpPage() {
            return helpPage;
        }
    }
}
