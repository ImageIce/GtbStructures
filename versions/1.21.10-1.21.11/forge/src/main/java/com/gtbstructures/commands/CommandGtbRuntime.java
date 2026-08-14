package com.gtbstructures.commands;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.GtbStructuresSettings.ShortcutNonAsciiCategory;
import com.gtbstructures.chat.ChatHeader;
import com.gtbstructures.chat.GuessChatAnnotator;
import com.gtbstructures.gtw.GtwLogic;
import com.gtbstructures.items.HandheldItemTracker;
import com.gtbstructures.overlay.WorldEnvironmentLogic;
import com.gtbstructures.scoreboard.InterfacePreview;
import com.gtbstructures.scoreboard.ScoreboardConfig;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.stats.StatsArchive;
import com.gtbstructures.translation.CopyTranslation;
import com.gtbstructures.translation.TranslationData;
import com.gtbstructures.util.MinecraftFormatting;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Runtime adapter for the platform-neutral /gtbs command core. */
public final class CommandGtbRuntime {
    private static final String[] ROOT_COMMANDS = {
            "gtbs", "gtbstructures", "gtb", "lrj", "copytranslation", "copyt", "trollmode", "gtw", "guess"
    };
    private static final String PLAY_GTB_COMMAND = "/play build_battle_guess_the_build";
    private static final String LOBBY_BB_COMMAND = "/l";
    private static final String REJOIN_COMMAND = "/rej";
    private static final int GTW_TICKS_PER_HINT = 240;
    private static final CommandGtb COMMAND = new CommandGtb();
    private static final GtbStructuresSettings SETTINGS = GtbStructuresSettings.INSTANCE;
    private static final Services DEFAULT_SERVICES = new DefaultServices();
    private static Services services = DEFAULT_SERVICES;
    private static final GtwSession GTW = new GtwSession();

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
        default void sendServerCommandAfterLobby(String command) {
            sendServerCommand(command);
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
        applySettingsToRuntime();
    }

    public static GtbStructuresSettings settingsForTest() {
        return SETTINGS;
    }

    public static Result execute(String root, String rawArgs) {
        String normalizedRoot = normalizeRoot(root);
        String[] args = parseExecutionArgs(rawArgs);
        if ("gtbstructures".equals(normalizedRoot)) {
            normalizedRoot = "gtbs";
        }
        if ("copyt".equals(normalizedRoot)) {
            normalizedRoot = "copytranslation";
        }

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
            if (args.length != 0) {
                return Result.messages(CommandGtb.head + "Usage: /lrj");
            }
            services.sendServerCommand(LOBBY_BB_COMMAND);
            services.sendServerCommandAfterLobby(REJOIN_COMMAND);
            return Result.empty();
        }
        if ("copytranslation".equals(normalizedRoot)) {
            return executeGtbs(prefixedArgs("copytranslation", args));
        }

        return Result.messages(CommandGtb.head + "Unknown command root. Usage: see /gtbs help");
    }

    public static Result onClientTick() {
        ArrayList<String> messages = new ArrayList<String>();
        messages.addAll(prefixAll(GTW.onClientTick(), CommandGtb.head));
        messages.addAll(StatsArchive.drainAsyncMessages());
        return Result.messages(messages);
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

        if ("gtb".equals(normalizedRoot) || "lrj".equals(normalizedRoot)) {
            return Collections.emptyList();
        }
        if ("copytranslation".equals(normalizedRoot)) {
            return completeCopyTranslation(rawArgs);
        }
        if ("gtw".equals(normalizedRoot)) {
            return completeGtbsShortcut("gtw", rawArgs);
        }
        if ("trollmode".equals(normalizedRoot)) {
            return completeGtbsShortcut("trollmode", rawArgs);
        }
        if ("guess".equals(normalizedRoot)) {
            return Collections.emptyList();
        }
        if (!"gtbs".equals(normalizedRoot)) {
            return completeRoot(rawArgs);
        }

        String[] args = parseCompletionArgs(rawArgs);
        List<String> suggestions = COMMAND.addTabCompletionOptions(null, args, null);
        if (suggestions == null || suggestions.isEmpty()) {
            return Collections.emptyList();
        }
        return replaceLastArgument(rawArgs, suggestions);
    }

    private static Result executeGtbs(String[] args) {
        if (args.length == 0) {
            return Result.messages(CommandGtb.buildHelpMessages(new String[0]));
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if ("help".equals(first)) {
            List<String> messages = new ArrayList<String>(
                    CommandGtb.buildHelpMessages(sliceFrom(args, 1)));
            if (!SETTINGS.helpQuickstartSeen) {
                SETTINGS.helpQuickstartSeen = true;
                saveSettings();
                messages.add(CommandGtb.head + "Quickstart: you can use \u00a7f/gtbs preview\u00a77"
                        + " to configurate and preview most of the visual settings through GUI.");
            }
            return Result.messages(messages);
        }
        if ("gtb".equals(first)) {
            return execute("gtb", join(sliceFrom(args, 1)));
        }
        if ("lrj".equals(first)) {
            return execute("lrj", join(sliceFrom(args, 1)));
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
            if (args.length != 1) return Result.messages(CommandGtb.head + "Usage: /gtbs autorequeue");
            SETTINGS.autoRequeue = !GameStats.INSTANCE.isAutoRequeue();
            GameStats.INSTANCE.setAutoRequeue(SETTINGS.autoRequeue);
            saveSettings();
            return Result.messages(CommandGtb.head + "Auto-requeue is currently " + onOff(SETTINGS.autoRequeue));
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
            return Result.messages(CommandGtb.head + (enabled
                    ? "Interface preview is currently \u00a7aON\u00a77. Click \u00a7fDone\u00a77 or press \u00a7fEsc\u00a77 to exit."
                    : "Interface preview is currently \u00a7cOFF"));
        }

        return Result.messages(CommandGtb.head + "Unknown argument. Usage: see /gtbs help");
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
        return Result.messages(CommandGtb.head + "Usage: /gtbs hintstructure [setlocation|setcolor|background] ...");
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
        return Result.messages(CommandGtb.head + "Usage: /gtbs showallmatching [showenglish <true|false>]");
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

        return Result.messages(CommandGtb.head
                + "Usage: /gtbs shortcutreminder [shownonascii|showallshortcuts|showmultiwords] ...");
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

        CopyTranslation.Request request = CopyTranslation.parseRequest(args);
        if (request == null || request.theme == null || request.theme.isEmpty()) {
            return Result.messages(CommandGtb.head + "Usage: /gtbs copytranslation <theme> [langs]");
        }
        if (request.langError != null) {
            return Result.messages(CommandGtb.head + request.langError);
        }
        CopyTranslation.Result result = CopyTranslation.buildMessage(request.theme, request.langCodes,
                SETTINGS.copyTranslationPrefix);
        if (!result.ok) {
            return Result.messages(CommandGtb.head + result.error);
        }
        services.copyToClipboard(result.message);
        return Result.messages(CommandGtb.head + "Message copied to clipboard: \u00a7f" + result.message);
    }

    private static Result handleGtw(String[] args, String usageRoot) {
        if (args.length == 0) {
            return Result.messages(CommandGtb.head + "Usage: " + usageRoot + " <start|stop|showenglish> ...");
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
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
            if (args.length != 1) return Result.messages(CommandGtb.head + "Usage: " + usageRoot + " <start|stop|showenglish> ...");
            return Result.messages(prefixAll(GTW.stop(), CommandGtb.head));
        }
        return Result.messages(CommandGtb.head + "Usage: " + usageRoot + " <start|stop|showenglish> ...");
    }

    private static Result handleGuess(String[] args, String usageRoot) {
        if (args.length == 0) return Result.messages(CommandGtb.head + "Usage: " + usageRoot + " <guess>");
        return Result.messages(prefixAll(GTW.guess(join(args)), CommandGtb.head));
    }

    static boolean matchesAnyGtwTranslation(String guess, String englishTheme) {
        Map<String, String> translations = TranslationData.getAllTranslations(englishTheme);
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            if (GtwLogic.gtbMatches(guess, entry.getValue(), entry.getKey())) return true;
        }
        return GtwLogic.gtbMatches(guess, englishTheme, "en");
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
            return Result.messages(CommandGtb.head + "Usage: /gtbs scoreboard " + option
                    + " <off|expanded|on>");
        }
        ScoreboardConfig.DisplayOption value = ScoreboardConfig.parseOption(args[1]);
        if (value == null) {
            return Result.messages(CommandGtb.head + "Usage: /gtbs scoreboard " + option
                    + " <off|expanded|on>");
        }
        if ("showpoints".equals(option)) {
            SETTINGS.sbConfig.showPointsGainedInRound = value;
        } else if ("showplaces".equals(option)) {
            SETTINGS.sbConfig.showPlaces = value;
        } else if ("showtitles".equals(option)) {
            SETTINGS.sbConfig.showTitles = value;
        } else {
            SETTINGS.sbConfig.showEmblems = value;
        }
        saveSettings();
        return Result.messages(CommandGtb.head + scoreboardTriStateStatus(option));
    }

    private static Result handleScoreboardBoolean(String[] args) {
        String option = scoreboardOptionKey(args[0]);
        boolean current = "leftlast".equals(option)
                ? SETTINGS.sbConfig.leftPlayersAtBottom : SETTINGS.sbConfig.textShadow;
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
        } else {
            label = "Show emblems";
            value = SETTINGS.sbConfig.showEmblems;
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
                || "showtitles".equals(key) || "showemblems".equals(key);
    }

    private static boolean isScoreboardBooleanOption(String raw) {
        String key = scoreboardOptionKey(raw);
        return "textshadow".equals(key) || "leftlast".equals(key);
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
        return Result.messages(CommandGtb.head + "Usage: /gtbs handhelditem [setlocation|max|background|captions|layout|vertical|horizontal|size] ...");
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
        return Result.messages(CommandGtb.head + "Usage: /gtbs environment [setlocation|weather|biome|time|timepreset|background|chatnotice] ...");
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
        if ("background".equalsIgnoreCase(args[0])) {
            return handleCooldownCounterBackground(args);
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
        return Result.messages(CommandGtb.head + "Usage: /gtbs cooldowncounter [setlocation|setcolor|timer|bar|background] ...");
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
        if (args.length == 1 && "prefix".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            suggestions.add("prefix");
        } else if (args.length == 2 && "prefix".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if ("true".startsWith(prefix)) suggestions.add("true");
            if ("false".startsWith(prefix)) suggestions.add("false");
        }
        return replaceLastArgument(rawArgs, suggestions);
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
            String pickedEnglish = TranslationData.pickRandomEnglishTheme(rng);
            if (pickedEnglish == null) return Collections.singletonList("No themes available.");
            String displayed = TranslationData.getDisplayedTheme(requestedLangCode, pickedEnglish);
            if (displayed == null || displayed.isEmpty()) {
                return Collections.singletonList("Failed to resolve theme translation.");
            }
            active = true;
            langCode = requestedLangCode;
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

            boolean isPirate = "pirate".equalsIgnoreCase(langCode);
            StringBuilder sb = new StringBuilder();
            sb.append("All themes matching the initial hint: ");
            boolean first = true;
            for (Map.Entry<String, Set<String>> entry : matches.entrySet()) {
                if (!first) sb.append("\u00a77, ");
                first = false;
                sb.append("\u00a7f").append(entry.getKey());
                if (shouldShowAllMatchingEnglish(isPirate, entry.getKey(), entry.getValue())) {
                    sb.append("\u00a77 (");
                    boolean firstEnglish = true;
                    for (String english : entry.getValue()) {
                        if (!firstEnglish) sb.append(", ");
                        firstEnglish = false;
                        sb.append(english);
                    }
                    sb.append(")");
                }
            }
            messages.add(sb.toString());
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

    public static final class Result {
        private final List<String> messages;

        private Result(List<String> messages) {
            this.messages = Collections.unmodifiableList(new ArrayList<String>(messages));
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

        public List<String> messages() {
            return messages;
        }
    }
}
