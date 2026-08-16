package com.gtbstructures.commands;

import com.gtbstructures.chat.GuessChatAnnotator;
import com.gtbstructures.chat.ChatHeader;
import com.gtbstructures.GtbStructures;
import com.gtbstructures.gtw.GtwGame;
import com.gtbstructures.items.HandheldItemTracker;
import com.gtbstructures.overlay.WorldEnvironmentOverlay;
import com.gtbstructures.scoreboard.InterfacePreview;
import com.gtbstructures.scoreboard.ScoreboardConfig;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.stats.LangPatterns;
import com.gtbstructures.stats.StatsArchive;
import com.gtbstructures.translation.TranslationData;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.ClientCommandHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class CommandGtb extends CommandBase {

    private static final String[] SUBCOMMANDS = {
        "hintstructure", "hs", "toggle", "setlocation", "setcolor", "colorlist", "stats", "export", "exportraw", "tracking", "autorequeue", "updatenotice",
        "haveibuilt", "whohasbuilt", "showallmatching", "themepointreveal", "shortcutreminder", "copytranslation", "gtw", "guess", "scoreboard",
        "spawnegg", "handhelditem", "environment", "env", "cooldowncounter", "guesspeek", "scrambleguess", "trollmode", "header", "preview", "overlay", "gtb", "lrj", "help"
    };
    private static final String PLAY_GTB_COMMAND = "/play build_battle_guess_the_build";
    private static final String LOBBY_BB_COMMAND = "/l";
    private static final String REJOIN_COMMAND = "/rej";
    private static final int LRJ_STABLE_WORLD_TICKS = 8;
    private static final int LRJ_TIMEOUT_TICKS = 20 * 60;
    private static final int MAX_HELP_LINES_PER_PAGE = 8;
    private static int pendingLrjTicks = -1;
    private static int pendingLrjStableWorldTicks = 0;
    private static Object pendingLrjWorld;

    // /gtbs scoreboard alone toggles; sub-arguments control skins and visual detail options.

    public static String head = ChatHeader.prefix();

    public static void refreshHeader() {
        head = ChatHeader.prefix();
    }
    public static void register() {
        ClientCommandHandler.instance.registerCommand(new CommandGtb());
        ClientCommandHandler.instance.registerCommand(new CommandJoinGtb());
        ClientCommandHandler.instance.registerCommand(new CommandLeaveRejoin());
        ClientCommandHandler.instance.registerCommand(new CommandCopyTranslation());
        ClientCommandHandler.instance.registerCommand(new CommandTrollMode());
    }

    @Override
    public String getCommandName() {
        return "gtbs";
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("gtbstructures");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtbs <subcommand> | /gtbs help";
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("help")) {
                return getListOfStringsMatchingLastWord(args,
                        "tracking", "hints", "scoreboard", "items", "environment", "game", "chat", "experimental", "copy", "gtw", "all");
            }
            if (args[0].equalsIgnoreCase("stats")) {
                return getListOfStringsMatchingLastWord(args, "session", "theme");
            }
            if (isHintStructureCommand(args[0])) {
                return getListOfStringsMatchingLastWord(args,
                        "setlocation", "setcolor", "background");
            }
            if (args[0].equalsIgnoreCase("setlocation")) {
                return getListOfStringsMatchingLastWord(args, "x", "y");
            }
            if (args[0].equalsIgnoreCase("setcolor")) {
                return getListOfStringsMatchingLastWord(args, "bracket", "number", "hyphen");
            }
            if (args[0].equalsIgnoreCase("showallmatching")) {
                return getListOfStringsMatchingLastWord(args, "showenglish");
            }
            if (args[0].equalsIgnoreCase("themepointreveal")) {
                return getListOfStringsMatchingLastWord(args, "showafterguess");
            }
            if (args[0].equalsIgnoreCase("shortcutreminder")) {
                return getListOfStringsMatchingLastWord(args,
                        "shownonascii", "showallshortcuts", "showmultiwords");
            }
            if (args[0].equalsIgnoreCase("copytranslation")) {
                return getListOfStringsMatchingLastWord(args, "prefix");
            }
            if (args[0].equalsIgnoreCase("spawnegg")) {
                return getListOfStringsMatchingLastWord(args, "classic", "new");
            }
            if (args[0].equalsIgnoreCase("handhelditem")) {
                return getListOfStringsMatchingLastWord(args, "setlocation", "max", "background", "captions", "layout", "vertical", "horizontal", "size");
            }
            if (isEnvironmentCommand(args[0])) {
                return getListOfStringsMatchingLastWord(args, "setlocation", "weather", "biome", "time", "timepreset", "background", "chatnotice");
            }
            if (args[0].equalsIgnoreCase("cooldowncounter")) {
                return getListOfStringsMatchingLastWord(args, "setlocation", "setcolor", "timer", "bar", "background");
            }
            if (args[0].equalsIgnoreCase("guesspeek")) {
                return getListOfStringsMatchingLastWord(args, "showlangcodes", "display");
            }
            if (args[0].equalsIgnoreCase("scrambleguess")) {
                return getListOfStringsMatchingLastWord(args, "fancy", "on", "off");
            }
            if (args[0].equalsIgnoreCase("trollmode")) {
                return getListOfStringsMatchingLastWord(args, "autodisable", "on", "off");
            }
            if (args[0].equalsIgnoreCase("updatenotice")) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "toggle");
            }
        }
        if (isHintStructureCommand(args[0])) {
            if (args.length == 3 && args[1].equalsIgnoreCase("setlocation")) {
                return getListOfStringsMatchingLastWord(args, "x", "y");
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("setcolor")) {
                return getListOfStringsMatchingLastWord(args, "bracket", "number", "hyphen");
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("background")) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "true", "false", "toggle");
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("setcolor")) {
                return getListOfStringsMatchingLastWord(args,
                        "0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f");
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setcolor")) {
            return getListOfStringsMatchingLastWord(args,
                    "0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("stats")
                && args[1].equalsIgnoreCase("theme")) {
            return getListOfStringsMatchingLastWord(args, "all");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("showallmatching")
                && args[1].equalsIgnoreCase("showenglish")) {
            return getListOfStringsMatchingLastWord(args, "true", "false");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("themepointreveal")
                && args[1].equalsIgnoreCase("showafterguess")) {
            return getListOfStringsMatchingLastWord(args, "on", "off");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("trollmode")
                && args[1].equalsIgnoreCase("autodisable")) {
            return getListOfStringsMatchingLastWord(args, "on", "off");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("shortcutreminder")) {
            if (args[1].equalsIgnoreCase("shownonascii")) {
                return getListOfStringsMatchingLastWord(args,
                        "on", "off", "toggle",
                        "extendedlatin", "cyrillic", "hanzi", "japanese", "hangul");
            }
            if (args[1].equalsIgnoreCase("showallshortcuts")) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "toggle");
            }
            if (args[1].equalsIgnoreCase("showmultiwords")) {
                return getListOfStringsMatchingLastWord(args,
                        "on", "off", "toggle", "showall");
            }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("shortcutreminder")
                && args[1].equalsIgnoreCase("showmultiwords")
                && args[2].equalsIgnoreCase("showall")) {
            return getListOfStringsMatchingLastWord(args, "on", "off", "toggle");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("shortcutreminder")
                && args[1].equalsIgnoreCase("shownonascii")
                && isShortcutNonAsciiCategory(args[2])) {
            return getListOfStringsMatchingLastWord(args, "on", "off", "toggle");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("copytranslation")
                && args[1].equalsIgnoreCase("prefix")) {
            return getListOfStringsMatchingLastWord(args, "true", "false");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("help")) {
            return getListOfStringsMatchingLastWord(args, "1", "2", "3", "4", "5");
        }
        if (args[0].equalsIgnoreCase("gtw")) {
            String[] sub = sliceFrom(args, 1);
            return GtwGame.tabGtw(sub);
        }
        if (args[0].equalsIgnoreCase("guess")) {
            String[] sub = sliceFrom(args, 1);
            return GtwGame.tabGuess(sub);
        }
        if (args[0].equalsIgnoreCase("scoreboard")) {
            if (args.length == 2) {
                return getListOfStringsMatchingLastWord(args,
                        "skin", "vanilla", "default", "hypixel",
                        "backgroundopacity", "highlightintensity",
                        "showpoints", "showplaces", "showtitles", "showemblems",
                        "textshadow", "leftlast");
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("skin")) {
                return getListOfStringsMatchingLastWord(args, "vanilla", "default", "hypixel");
            }
            if (args.length == 3 && isScoreboardSliderOption(args[1])) {
                return getListOfStringsMatchingLastWord(args, "0", "0.25", "0.50", "0.75", "1");
            }
            if (args.length == 3 && isScoreboardTriStateOption(args[1])) {
                return getListOfStringsMatchingLastWord(args, "off", "expanded", "on");
            }
            if (args.length == 3 && isScoreboardBooleanOption(args[1])) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "toggle", "true", "false");
            }
            return null;
        }
        if (args[0].equalsIgnoreCase("handhelditem")) {
            if (args.length == 3 && args[1].equalsIgnoreCase("setlocation")) {
                return getListOfStringsMatchingLastWord(args, "x", "y");
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("background")) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "true", "false", "toggle");
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("captions")) {
                return getListOfStringsMatchingLastWord(args, "name", "id", "metadata", "mob");
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("captions") && isHandheldCaption(args[2])) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "true", "false");
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("layout")) {
                return getListOfStringsMatchingLastWord(args, "vertical", "horizontal");
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("vertical")) {
                return getListOfStringsMatchingLastWord(args, "top", "bottom");
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("horizontal")) {
                return getListOfStringsMatchingLastWord(args, "left", "right", "top", "bottom");
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("horizontal")) {
                if (args[2].equalsIgnoreCase("left") || args[2].equalsIgnoreCase("right")) {
                    return getListOfStringsMatchingLastWord(args, "top", "bottom");
                }
                if (args[2].equalsIgnoreCase("top") || args[2].equalsIgnoreCase("bottom")) {
                    return getListOfStringsMatchingLastWord(args, "left", "right");
                }
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("size")) {
                return getListOfStringsMatchingLastWord(args, "width", "height");
            }
            return null;
        }
        if (isEnvironmentCommand(args[0])) {
            if (args.length == 3 && args[1].equalsIgnoreCase("setlocation")) {
                return getListOfStringsMatchingLastWord(args, "x", "y");
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("chatnotice")) {
                return getListOfStringsMatchingLastWord(args, "weather", "biome", "time");
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("chatnotice")
                    && isEnvironmentPart(args[2])) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "true", "false", "toggle");
            }
            if (args.length == 3 && isEnvironmentPart(args[1])) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "true", "false", "toggle");
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("timepreset")) {
                return getListOfStringsMatchingLastWord(args, "clock", "blocks");
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("background")) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "true", "false", "toggle");
            }
            return null;
        }
        if (args[0].equalsIgnoreCase("cooldowncounter")) {
            if (args.length == 3 && args[1].equalsIgnoreCase("setlocation")) {
                return getListOfStringsMatchingLastWord(args, "x", "y");
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("setcolor")) {
                return getListOfStringsMatchingLastWord(args, "bar", "timer");
            }
            if (args.length == 3 && (args[1].equalsIgnoreCase("timer") || args[1].equalsIgnoreCase("bar"))) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "true", "false", "toggle");
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("background")) {
                return getListOfStringsMatchingLastWord(args, "timer", "bar", "on", "off", "true", "false", "toggle");
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("background")
                    && isCooldownCounterPart(args[2])) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "true", "false", "toggle");
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("setcolor")) {
                return getListOfStringsMatchingLastWord(args,
                        "0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f");
            }
            return null;
        }
        if (args[0].equalsIgnoreCase("guesspeek")) {
            if (args.length == 3 && args[1].equalsIgnoreCase("showlangcodes")) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "true", "false");
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("display")) {
                return getListOfStringsMatchingLastWord(args, "hover", "suffix");
            }
            return null;
        }
        if (args[0].equalsIgnoreCase("scrambleguess")) {
            if (args.length == 3 && args[1].equalsIgnoreCase("fancy")) {
                return getListOfStringsMatchingLastWord(args, "on", "off");
            }
            return null;
        }
        return null;
    }

    /** Return args[start..] as a fresh array. */
    static String[] sliceFrom(String[] args, int start) {
        int n = Math.max(0, args.length - start);
        String[] out = new String[n];
        for (int i = 0; i < n; i++) out[i] = args[start + i];
        return out;
    }

    private static boolean isHintStructureCommand(String command) {
        return command != null && (command.equalsIgnoreCase("hintstructure") || command.equalsIgnoreCase("hs"));
    }

    private static boolean isEnvironmentCommand(String command) {
        return command != null && (command.equalsIgnoreCase("environment") || command.equalsIgnoreCase("env"));
    }

    private static boolean isEnvironmentPart(String command) {
        return command != null && (command.equalsIgnoreCase("weather") || command.equalsIgnoreCase("biome")
                || command.equalsIgnoreCase("time"));
    }

    private static String[] prefixedArgs(String first, String[] rest) {
        int n = rest == null ? 0 : rest.length;
        String[] out = new String[n + 1];
        out[0] = first;
        for (int i = 0; i < n; i++) out[i + 1] = rest[i];
        return out;
    }

    private static void handleHintStructure(ICommandSender sender, String[] args) {
        if (args == null || args.length == 0) {
            toggleHintStructureOverlay(sender);
            return;
        }

        if (args[0].equalsIgnoreCase("setlocation")) {
            handleHintStructureSetLocation(sender, args);
            return;
        }
        if (args[0].equalsIgnoreCase("setcolor")) {
            handleHintStructureSetColor(sender, args);
            return;
        }
        if (args[0].equalsIgnoreCase("background")) {
            handleHintStructureBackground(sender, args);
            return;
        }

        sender.addChatMessage(new ChatComponentText(head
                + "Usage: /gtbs hintstructure [setlocation|setcolor|background] ..."));
    }

    private static void toggleHintStructureOverlay(ICommandSender sender) {
        GtbStructures.enabled = !GtbStructures.enabled;
        GtbStructures.saveConfig();
        sender.addChatMessage(new ChatComponentText(head + "Hint structure overlay is currently "
                + (GtbStructures.enabled ? "\u00a7aON" : "\u00a7cOFF")));
    }

    private static void handleHintStructureBackground(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            sendHintStructureBackgroundStatus(sender);
            return;
        }
        if (args.length != 2) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs hintstructure background <on|off>"));
            return;
        }
        if (args[1].equalsIgnoreCase("toggle")) {
            GtbStructures.hintStructureBackground = !GtbStructures.hintStructureBackground;
        } else {
            Boolean enabled = parseOptionalBoolean(args[1]);
            if (enabled == null) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs hintstructure background <on|off>"));
                return;
            }
            GtbStructures.hintStructureBackground = enabled.booleanValue();
        }
        GtbStructures.saveConfig();
        sendHintStructureBackgroundStatus(sender);
    }

    private static void sendHintStructureBackgroundStatus(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(head + "Hint structure background is currently "
                + (GtbStructures.hintStructureBackground ? "\u00a7aON" : "\u00a7cOFF")));
    }

    private static void handleHintStructureSetLocation(ICommandSender sender, String[] args) {
        if (args.length == 2 && isLocationAxis(args[1])) {
            sender.addChatMessage(new ChatComponentText(head
                    + locationStatusMessage("Text", args[1], hintStructureLocationValue(args[1]))));
            return;
        }
        if (args.length != 3) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs hintstructure setlocation <x | y> <value>"));
            return;
        }
        String axis = args[1].toLowerCase();
        try {
            float value = Float.parseFloat(args[2]);
            if (value < 0 || value > 1) {
                sender.addChatMessage(new ChatComponentText(head + "Please enter a value between 0 and 1."));
                return;
            }

            if (axis.equals("x")) {
                GtbStructures.posX = value;
                GtbStructures.saveConfig();
                sender.addChatMessage(new ChatComponentText(head + "Text X position set to \u00a7f" + value));
            } else if (axis.equals("y")) {
                GtbStructures.posY = value;
                GtbStructures.saveConfig();
                sender.addChatMessage(new ChatComponentText(head + "Text Y position set to \u00a7f" + value));
            } else {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs hintstructure setlocation <x | y> <value>"));
            }
        } catch (NumberFormatException e) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs hintstructure setlocation <x | y> <value>"));
        }
    }

    static String locationStatusMessageForTest(String label, String axis, float value) {
        return locationStatusMessage(label, axis, value);
    }

    private static boolean isLocationAxis(String axis) {
        return axis != null && (axis.equalsIgnoreCase("x") || axis.equalsIgnoreCase("y"));
    }

    private static float hintStructureLocationValue(String axis) {
        return axis.equalsIgnoreCase("x") ? GtbStructures.posX : GtbStructures.posY;
    }

    private static float handheldItemLocationValue(String axis) {
        return axis.equalsIgnoreCase("x") ? GtbStructures.handheldItemPosX : GtbStructures.handheldItemPosY;
    }

    private static float environmentLocationValue(String axis) {
        return axis.equalsIgnoreCase("x") ? GtbStructures.environmentPosX : GtbStructures.environmentPosY;
    }

    private static float chatCooldownLocationValue(String axis) {
        return axis.equalsIgnoreCase("x") ? GtbStructures.chatCooldownPosX : GtbStructures.chatCooldownPosY;
    }

    private static String locationStatusMessage(String label, String axis, float value) {
        return label + " " + axis.toUpperCase() + " position is currently \u00a7f" + value;
    }

    private static void sendMinecraftColorList(ICommandSender sender) {
        StringBuilder colorList = new StringBuilder();
        String[] colors = {"0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f"};
        for (String c : colors) {
            colorList.append("\u00a7").append(c).append(c).append(" ");
        }
        sender.addChatMessage(new ChatComponentText(head + "Available color codes: " + colorList.toString().trim()));
    }

    private static String normalizeCommandColor(String raw) {
        String color = GtbStructures.normalizeMinecraftColorCode(raw, "");
        return color.isEmpty() ? null : color;
    }

    private static String invalidColorMessage() {
        return "Color must be a single character 0-9 or a-f. Use \u00a7f/gtbs colorlist\u00a77.";
    }

    static String invalidColorMessageForTest() {
        return invalidColorMessage();
    }

    private static String colorCodeLabel(String color, String fallback) {
        String normalized = GtbStructures.normalizeMinecraftColorCode(color, fallback);
        return normalized + normalized.substring(1);
    }

    private static String currentHintStructureColorMessage(String target) {
        if (target.equals("bracket")) {
            return "Bracket color is currently " + colorCodeLabel(GtbStructures.colorBrackets, "\u00a76");
        }
        if (target.equals("number")) {
            return "Number color is currently " + colorCodeLabel(GtbStructures.colorNumbers, "\u00a7e");
        }
        if (target.equals("hyphen")) {
            return "Hyphen color is currently " + colorCodeLabel(GtbStructures.colorHyphens, "\u00a77");
        }
        return null;
    }

    static String currentHintStructureColorMessageForTest(String target) {
        return currentHintStructureColorMessage(target);
    }

    private static String currentCooldownColorMessage(String target) {
        if (target.equals("bar")) {
            return "Cooldown bar color is currently " + colorCodeLabel(GtbStructures.chatCooldownBarColor, "\u00a7e");
        }
        if (target.equals("timer")) {
            return "Cooldown timer color is currently " + colorCodeLabel(GtbStructures.chatCooldownTimerColor, "\u00a7f");
        }
        return null;
    }

    static String currentCooldownColorMessageForTest(String target) {
        return currentCooldownColorMessage(target);
    }

    private static void handleHintStructureSetColor(ICommandSender sender, String[] args) {
        if (args.length == 2) {
            String target = args[1].toLowerCase();
            String message = currentHintStructureColorMessage(target);
            if (message == null) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs hintstructure setcolor <bracket|number|hyphen> <color>"));
            } else {
                sender.addChatMessage(new ChatComponentText(head + message));
            }
            return;
        }
        if (args.length != 3) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs hintstructure setcolor <bracket|number|hyphen> <color>"));
            return;
        }
        String target = args[1].toLowerCase();
        String color = normalizeCommandColor(args[2]);
        if (color == null) {
            sender.addChatMessage(new ChatComponentText(head + invalidColorMessage()));
            return;
        }
        String colorCode = color.substring(1);
        if (target.equals("bracket")) {
            GtbStructures.colorBrackets = color;
            sender.addChatMessage(new ChatComponentText(head + "Bracket color set to " + color + colorCode));
        } else if (target.equals("number")) {
            GtbStructures.colorNumbers = color;
            sender.addChatMessage(new ChatComponentText(head + "Number color set to " + color + colorCode));
        } else if (target.equals("hyphen")) {
            GtbStructures.colorHyphens = color;
            sender.addChatMessage(new ChatComponentText(head + "Hyphen color set to " + color + colorCode));
        } else {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs hintstructure setcolor <bracket|number|hyphen> <color>"));
            return;
        }
        GtbStructures.saveConfig();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: see /gtbs help"));
            return;
        }

        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, sliceFrom(args, 1));
            return;
        }

        if (args[0].equalsIgnoreCase("gtb")) {
            joinGtb(sender);
            return;
        }

        if (args[0].equalsIgnoreCase("lrj")) {
            leaveRejoin(sender);
            return;
        }

        if (isHintStructureCommand(args[0])) {
            handleHintStructure(sender, sliceFrom(args, 1));
            return;
        }

        if (args[0].equalsIgnoreCase("toggle")) {
            if (args.length != 1) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs toggle"));
                return;
            }
            GtbStructures.modEnabled = !GtbStructures.modEnabled;
            GtbStructures.saveConfig();
            sender.addChatMessage(new ChatComponentText(head + "GTBS is currently "
                    + (GtbStructures.modEnabled ? "\u00a7aON" : "\u00a7cOFF")));
            return;
        }

        if (args[0].equalsIgnoreCase("overlay")) {
            handleHintStructure(sender, args.length == 1
                    ? new String[] {"background", "toggle"}
                    : prefixedArgs("background", sliceFrom(args, 1)));
            return;
        }

        else if ((args[0].equalsIgnoreCase("setlocation"))) {
            handleHintStructure(sender, prefixedArgs("setlocation", sliceFrom(args, 1)));
            return;
        } else if (args[0].equalsIgnoreCase("colorlist")) {
            if (args.length != 1) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs colorlist"));
                return;
            }
            sendMinecraftColorList(sender);
            return;
        } else if (args[0].equalsIgnoreCase("setcolor")) {
            handleHintStructure(sender, prefixedArgs("setcolor", sliceFrom(args, 1)));
            return;
        }

        // ── Stats Tracking Commands ──

        else if (args[0].equalsIgnoreCase("stats")) {
            StatsArchive.handleCommand(sender, sliceFrom(args, 1));
            return;
        }

        else if (args[0].equalsIgnoreCase("export")) {
            Minecraft mc = Minecraft.getMinecraft();
            String result = GameStats.INSTANCE.manualExport(mc.mcDataDir);
            sender.addChatMessage(new ChatComponentText(head + result));
            return;
        }

        else if (args[0].equalsIgnoreCase("exportraw")) {
            Minecraft mc = Minecraft.getMinecraft();
            String result = GameStats.INSTANCE.manualExportRaw(mc.mcDataDir);
            sender.addChatMessage(new ChatComponentText(head + result));
            return;
        }

        else if (args[0].equalsIgnoreCase("tracking")) {
            boolean next = !GameStats.INSTANCE.isTrackingEnabled();
            GtbStructures.trackingEnabled = next;
            GameStats.INSTANCE.setTrackingEnabled(next);
            GtbStructures.saveConfig();
            sender.addChatMessage(new ChatComponentText(head + "Stats tracking is currently " +
                    (next ? "\u00a7aON" : "\u00a7cOFF")));
            return;
        }

        else if (args[0].equalsIgnoreCase("autorequeue")) {
            boolean next = !GameStats.INSTANCE.isAutoRequeue();
            GtbStructures.autoRequeue = next;
            GameStats.INSTANCE.setAutoRequeue(next);
            GtbStructures.saveConfig();
            sender.addChatMessage(new ChatComponentText(head + "Auto-requeue is currently " +
                    (next ? "\u00a7aON" : "\u00a7cOFF")));
            return;
        }

        else if (args[0].equalsIgnoreCase("updatenotice")) {
            handleUpdateNotice(sender, sliceFrom(args, 1));
            return;
        }

        else if (args[0].equalsIgnoreCase("haveibuilt")) {
            sender.addChatMessage(new ChatComponentText(head + formatHaveIBuilt()));
            return;
        }

        else if (args[0].equalsIgnoreCase("whohasbuilt")) {
            sender.addChatMessage(new ChatComponentText(head + formatWhoHasBuilt()));
            return;
        }

        else if (args[0].equalsIgnoreCase("showallmatching")) {
            if (args.length == 1) {
                GtbStructures.showAllMatching = !GtbStructures.showAllMatching;
                GtbStructures.saveConfig();
                sender.addChatMessage(new ChatComponentText(head + "Show all matching is currently " +
                        (GtbStructures.showAllMatching ? "§aON" : "§cOFF")));
                return;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("showenglish")) {
                if (args.length == 2) {
                    GtbStructures.showAllMatchingShowEnglish = !GtbStructures.showAllMatchingShowEnglish;
                } else {
                    String v = args[2].toLowerCase();
                    if (v.equals("true") || v.equals("on") || v.equals("1")) {
                        GtbStructures.showAllMatchingShowEnglish = true;
                    } else if (v.equals("false") || v.equals("off") || v.equals("0")) {
                        GtbStructures.showAllMatchingShowEnglish = false;
                    } else {
                        sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs showallmatching showenglish <true|false>"));
                        return;
                    }
                }
                GtbStructures.saveConfig();
                sender.addChatMessage(new ChatComponentText(head + "Show all matching (showEnglish) is currently " +
                        (GtbStructures.showAllMatchingShowEnglish ? "§aON" : "§cOFF")));
                return;
            }
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs showallmatching [showenglish <true|false>]"));
            return;
        }

        else if (args[0].equalsIgnoreCase("shortcutreminder")) {
            handleShortcutReminder(sender, sliceFrom(args, 1));
            return;
        }

        else if (args[0].equalsIgnoreCase("themepointreveal")) {
            handleThemePointReveal(sender, sliceFrom(args, 1));
            return;
        }

        else if (args[0].equalsIgnoreCase("copytranslation")) {
            handleCopyTranslation(sender, sliceFrom(args, 1));
            return;
        }

        else if (args[0].equalsIgnoreCase("gtw")) {
            GtwGame.processGtw(sender, sliceFrom(args, 1));
            return;
        }

        else if (args[0].equalsIgnoreCase("guess")) {
            GtwGame.processGuess(sender, sliceFrom(args, 1));
            return;
        }

        else if (args[0].equalsIgnoreCase("scoreboard")) {
            handleScoreboard(sender, sliceFrom(args, 1));
            return;
        }

        else if (args[0].equalsIgnoreCase("spawnegg")) {
            handleSpawnEgg(sender, sliceFrom(args, 1));
            return;
        }

        else if (args[0].equalsIgnoreCase("handhelditem")) {
            handleHandheldItem(sender, sliceFrom(args, 1));
            return;
        }

        else if (isEnvironmentCommand(args[0])) {
            handleEnvironment(sender, sliceFrom(args, 1));
            return;
        }

        else if (args[0].equalsIgnoreCase("cooldowncounter")) {
            handleCooldownCounter(sender, sliceFrom(args, 1));
            return;
        }

        else if (args[0].equalsIgnoreCase("guesspeek")) {
            handleGuessPeek(sender, sliceFrom(args, 1));
            return;
        }

        else if (args[0].equalsIgnoreCase("scrambleguess")) {
            handleScrambleGuess(sender, sliceFrom(args, 1));
            return;
        }

        else if (args[0].equalsIgnoreCase("trollmode")) {
            handleTrollMode(sender, sliceFrom(args, 1));
            return;
        }

        else if (args[0].equalsIgnoreCase("header")) {
            handleHeader(sender, sliceFrom(args, 1));
            return;
        }

        else if (args[0].equalsIgnoreCase("copyguess")) {
            handleCopyGuess(args);
            return;
        }

        else if (args[0].equalsIgnoreCase("preview")) {
            handlePreview(sender, args);
            return;
        }

        else {
            sender.addChatMessage(new ChatComponentText(head + "Unknown argument. Usage: see /gtbs help"));
        }
    }

    private static void handlePreview(ICommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs preview"));
            return;
        }
        boolean enabled = InterfacePreview.INSTANCE.toggle();
        if (!GtbStructures.previewQuickstartRan) {
            GtbStructures.previewQuickstartRan = true;
            GtbStructures.saveConfig();
        }
        if (enabled) {
            sender.addChatMessage(new ChatComponentText(head
                    + "Interface preview is currently \u00a7aON\u00a77. Click \u00a7fDone\u00a77 or press \u00a7fEsc\u00a77 to exit."));
        } else {
            sender.addChatMessage(new ChatComponentText(head + "Interface preview is currently \u00a7cOFF"));
        }
    }

    private static void handleUpdateNotice(ICommandSender sender, String[] args) {
        if (args.length > 1) {
            sender.addChatMessage(new ChatComponentText(head
                    + "Usage: /gtbs updatenotice [on|off|toggle]"));
            return;
        }

        boolean next;
        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) {
            next = !GtbStructures.updateNoticeEnabled;
        } else if (args[0].equalsIgnoreCase("on")) {
            next = true;
        } else if (args[0].equalsIgnoreCase("off")) {
            next = false;
        } else {
            sender.addChatMessage(new ChatComponentText(head
                    + "Usage: /gtbs updatenotice [on|off|toggle]"));
            return;
        }

        GtbStructures.updateNoticeEnabled = next;
        GtbStructures.saveConfig();
        sender.addChatMessage(new ChatComponentText(head + "Update notice is currently "
                + (next ? "\u00a7aON" : "\u00a7cOFF")));
    }

    private static void handleThemePointReveal(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            GtbStructures.themePointReveal = !GtbStructures.themePointReveal;
            GtbStructures.saveConfig();
            sender.addChatMessage(new ChatComponentText(head + "Theme point reveal is currently "
                    + (GtbStructures.themePointReveal ? "\u00a7aON" : "\u00a7cOFF")));
            return;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("showafterguess")) {
            Boolean enabled = parseOptionalBoolean(args[1]);
            if (enabled != null) {
                GtbStructures.themePointRevealShowAfterGuess = enabled.booleanValue();
                GtbStructures.saveConfig();
                sender.addChatMessage(new ChatComponentText(head
                        + "Theme point reveal after correct guess is currently "
                        + (enabled.booleanValue() ? "\u00a7aON" : "\u00a7cOFF")));
                return;
            }
        }
        sender.addChatMessage(new ChatComponentText(head
                + "Usage: /gtbs themepointreveal [showafterguess <on|off>]"));
    }

    private static void handleScoreboard(ICommandSender sender, String[] args) {
        // /gtbs scoreboard          — toggle enabled
        // /gtbs scoreboard skin <s> — set skin
        // /gtbs scoreboard <s>      — shortcut for skin (when s ∈ {vanilla, default, hypixel})
        if (args.length == 0) {
            GtbStructures.sbConfig.toggleCustom();
            GtbStructures.saveConfig();
            sender.addChatMessage(new ChatComponentText(head + "Custom scoreboard is now "
                    + (GtbStructures.sbConfig.isCustomEnabled() ? "§aON" : "§cOFF")));
            return;
        }
        if (isScoreboardSliderOption(args[0])) {
            handleScoreboardSlider(sender, args);
            return;
        }
        if (isScoreboardTriStateOption(args[0])) {
            handleScoreboardTriState(sender, args);
            return;
        }
        if (isScoreboardBooleanOption(args[0])) {
            handleScoreboardBoolean(sender, args);
            return;
        }
        // Skin sub-command (with or without explicit "skin" keyword).
        String requested = null;
        if (args[0].equalsIgnoreCase("skin")) {
            if (args.length < 2) {
                sender.addChatMessage(new ChatComponentText(head + "Current skin: §f"
                        + GtbStructures.sbConfig.skin + "§7. Usage: /gtbs scoreboard skin <vanilla|default|hypixel>"));
                return;
            }
            requested = args[1];
        } else if (args[0].equalsIgnoreCase("vanilla")
                || args[0].equalsIgnoreCase("default")
                || args[0].equalsIgnoreCase("hypixel")) {
            requested = args[0];
        }
        if (requested == null) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs scoreboard [skin <vanilla|default|hypixel>]"));
            return;
        }
        String norm = requested.trim().toLowerCase();
        if (!norm.equals("vanilla") && !norm.equals("default") && !norm.equals("hypixel")) {
            sender.addChatMessage(new ChatComponentText(head + "Unknown skin §f" + requested
                    + "§7. Valid: §fvanilla§7, §fdefault§7, §fhypixel§7."));
            return;
        }
        GtbStructures.sbConfig.setSkin(norm);
        GtbStructures.saveConfig();
        // Display label keeps the brand capitalization ("Hypixel"), even
        // though the stored config value is normalized lowercase.
        String display = norm.equals("hypixel") ? "Hypixel"
                       : norm.equals("default") ? "Default"
                       : norm.equals("vanilla") ? "Vanilla"
                       : norm;
        sender.addChatMessage(new ChatComponentText(head + "Scoreboard skin set to §f" + display
                + (GtbStructures.sbConfig.isCustomEnabled() ? "" : "§7 (panel is currently §cOFF§7 — toggle with /gtbs scoreboard)")));
    }

    /**
     * Helpers for the custom scoreboard subcommands.
     */
    private static void handleScoreboardSlider(ICommandSender sender, String[] args) {
        String option = scoreboardOptionKey(args[0]);
        if (args.length == 1) {
            sendScoreboardSliderStatus(sender, option);
            return;
        }
        if (args.length != 2) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs scoreboard "
                    + option + " <0-1>"));
            return;
        }
        Float value = parseScoreboardFloat(args[1]);
        if (value == null) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs scoreboard "
                    + option + " <0-1>"));
            return;
        }
        if ("backgroundopacity".equals(option)) {
            GtbStructures.sbConfig.backgroundOpacity = value.floatValue();
        } else {
            GtbStructures.sbConfig.highlightIntensity = value.floatValue();
        }
        GtbStructures.sbConfig.clampVisuals();
        GtbStructures.saveConfig();
        sendScoreboardSliderStatus(sender, option);
    }

    private static void handleScoreboardTriState(ICommandSender sender, String[] args) {
        String option = scoreboardOptionKey(args[0]);
        if (args.length == 1) {
            sendScoreboardTriStateStatus(sender, option);
            return;
        }
        if (args.length != 2) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs scoreboard "
                    + option + " <off|expanded|on>"));
            return;
        }
        ScoreboardConfig.DisplayOption value = ScoreboardConfig.parseOption(args[1]);
        if (value == null) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs scoreboard "
                    + option + " <off|expanded|on>"));
            return;
        }
        if ("showpoints".equals(option)) {
            GtbStructures.sbConfig.showPointsGainedInRound = value;
        } else if ("showplaces".equals(option)) {
            GtbStructures.sbConfig.showPlaces = value;
        } else if ("showtitles".equals(option)) {
            GtbStructures.sbConfig.showTitles = value;
        } else {
            GtbStructures.sbConfig.showEmblems = value;
        }
        GtbStructures.saveConfig();
        sendScoreboardTriStateStatus(sender, option);
    }

    private static void handleScoreboardBoolean(ICommandSender sender, String[] args) {
        String option = scoreboardOptionKey(args[0]);
        boolean current = "textshadow".equals(option)
                ? GtbStructures.sbConfig.textShadow
                : GtbStructures.sbConfig.leftPlayersAtBottom;
        String label = "textshadow".equals(option) ? "Text shadow" : "Place left players at bottom";
        if (args.length == 1) {
            sendScoreboardBooleanStatus(sender, label, current);
            return;
        }
        if (args.length != 2) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs scoreboard "
                    + option + " [on|off]"));
            return;
        }
        Boolean enabled = parseToggleBoolean(args[1], current);
        if (enabled == null) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs scoreboard "
                    + option + " [on|off]"));
            return;
        }
        if ("textshadow".equals(option)) {
            GtbStructures.sbConfig.textShadow = enabled.booleanValue();
        } else {
            GtbStructures.sbConfig.leftPlayersAtBottom = enabled.booleanValue();
        }
        GtbStructures.saveConfig();
        sendScoreboardBooleanStatus(sender, label, enabled.booleanValue());
    }

    private static void sendScoreboardSliderStatus(ICommandSender sender, String option) {
        String label = "backgroundopacity".equals(option) ? "Background opacity" : "Highlight intensity";
        float value = "backgroundopacity".equals(option)
                ? GtbStructures.sbConfig.backgroundOpacity
                : GtbStructures.sbConfig.highlightIntensity;
        sender.addChatMessage(new ChatComponentText(head + label + " is currently \u00a7f"
                + formatScoreboardFloat(value)));
    }

    private static void sendScoreboardTriStateStatus(ICommandSender sender, String option) {
        String label;
        ScoreboardConfig.DisplayOption value;
        if ("showpoints".equals(option)) {
            label = "Show points gained in round";
            value = GtbStructures.sbConfig.showPointsGainedInRound;
        } else if ("showplaces".equals(option)) {
            label = "Show places";
            value = GtbStructures.sbConfig.showPlaces;
        } else if ("showtitles".equals(option)) {
            label = "Show titles";
            value = GtbStructures.sbConfig.showTitles;
        } else {
            label = "Show emblems";
            value = GtbStructures.sbConfig.showEmblems;
        }
        sender.addChatMessage(new ChatComponentText(head + label + " is currently \u00a7f"
                + ScoreboardConfig.displayOption(value)));
    }

    private static void sendScoreboardBooleanStatus(ICommandSender sender, String label, boolean enabled) {
        sender.addChatMessage(new ChatComponentText(head + label + " is currently "
                + (enabled ? "\u00a7aON" : "\u00a7cOFF")));
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

    private static void handleSpawnEgg(ICommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.addChatMessage(new ChatComponentText(head + "Current spawn egg skin: \u00a7f"
                    + displaySpawnEggSkin(GtbStructures.spawnEggSkin)
                    + "\u00a77. Usage: /gtbs spawnegg <Classic|New>"));
            return;
        }
        if (!args[0].equalsIgnoreCase("classic") && !args[0].equalsIgnoreCase("new")) {
            sender.addChatMessage(new ChatComponentText(head + "Unknown spawn egg skin \u00a7f" + args[0]
                    + "\u00a77. Valid: \u00a7fClassic\u00a77, \u00a7fNew\u00a77."));
            return;
        }
        String normalized = GtbStructures.normalizeSpawnEggSkin(args[0]);
        GtbStructures.spawnEggSkin = normalized;
        GtbStructures.saveConfig();
        sender.addChatMessage(new ChatComponentText(head + "Spawn egg skin set to \u00a7f"
                + displaySpawnEggSkin(normalized)));
    }

    static String displaySpawnEggSkinForTest(String skin) {
        return displaySpawnEggSkin(skin);
    }

    private static String displaySpawnEggSkin(String skin) {
        return "new".equalsIgnoreCase(skin) ? "New" : "Classic";
    }

    private static void handleHandheldItem(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            GtbStructures.handheldItemOverlay = !GtbStructures.handheldItemOverlay;
            GtbStructures.saveConfig();
            sender.addChatMessage(new ChatComponentText(head + "Handheld item overlay is currently "
                    + (GtbStructures.handheldItemOverlay ? "\u00a7aON" : "\u00a7cOFF")));
            return;
        }

        if (args[0].equalsIgnoreCase("background")) {
            handleHandheldItemBackground(sender, args);
            return;
        }

        if (args[0].equalsIgnoreCase("captions")) {
            handleHandheldItemCaptions(sender, args);
            return;
        }

        if (args[0].equalsIgnoreCase("layout")) {
            handleHandheldItemLayout(sender, args);
            return;
        }

        if (args[0].equalsIgnoreCase("vertical")) {
            handleHandheldItemVertical(sender, args);
            return;
        }

        if (args[0].equalsIgnoreCase("horizontal")) {
            handleHandheldItemHorizontal(sender, args);
            return;
        }

        if (args[0].equalsIgnoreCase("size")) {
            handleHandheldItemSize(sender, args);
            return;
        }

        if (args[0].equalsIgnoreCase("setlocation")) {
            if (args.length == 2 && isLocationAxis(args[1])) {
                sender.addChatMessage(new ChatComponentText(head
                        + locationStatusMessage("Handheld item", args[1], handheldItemLocationValue(args[1]))));
                return;
            }
            if (args.length != 3) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs handhelditem setlocation <x | y> <value>"));
                return;
            }
            String axis = args[1].toLowerCase();
            try {
                float value = Float.parseFloat(args[2]);
                if (value < 0 || value > 1) {
                    sender.addChatMessage(new ChatComponentText(head + "Please enter a value between 0 and 1."));
                    return;
                }
                if (axis.equals("x")) {
                    GtbStructures.handheldItemPosX = value;
                    GtbStructures.saveConfig();
                    sender.addChatMessage(new ChatComponentText(head + "Handheld item X position set to \u00a7f" + value));
                } else if (axis.equals("y")) {
                    GtbStructures.handheldItemPosY = value;
                    GtbStructures.saveConfig();
                    sender.addChatMessage(new ChatComponentText(head + "Handheld item Y position set to \u00a7f" + value));
                } else {
                    sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs handhelditem setlocation <x | y> <value>"));
                }
            } catch (NumberFormatException e) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs handhelditem setlocation <x | y> <value>"));
            }
            return;
        }

        if (args[0].equalsIgnoreCase("max")) {
            if (args.length != 2) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs handhelditem max <2-10>"));
                return;
            }
            try {
                int value = Integer.parseInt(args[1]);
                if (value < 2 || value > 10) {
                    sender.addChatMessage(new ChatComponentText(head + "Please enter an integer between 2 and 10."));
                    return;
                }
                GtbStructures.handheldItemMaxVisible = HandheldItemTracker.clampMaxVisible(value);
                GtbStructures.saveConfig();
                sender.addChatMessage(new ChatComponentText(head + "Handheld item visible max set to \u00a7f"
                        + GtbStructures.handheldItemMaxVisible));
            } catch (NumberFormatException e) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs handhelditem max <2-10>"));
            }
            return;
        }

        sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs handhelditem [setlocation|max|background|captions|layout|vertical|horizontal|size] ..."));
    }

    private static void handleHandheldItemCaptions(ICommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3 || !isHandheldCaption(args[1])) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs handhelditem captions <name|id|metadata|mob> [on|off]"));
            return;
        }
        boolean current = handheldCaptionEnabled(args[1]);
        if (args.length == 2) {
            sender.addChatMessage(new ChatComponentText(head + "Handheld item " + args[1] + " caption is currently " + (current ? "\u00a7aON" : "\u00a7cOFF")));
            return;
        }
        Boolean next;
        if (args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("true")) next = Boolean.TRUE;
        else if (args[2].equalsIgnoreCase("off") || args[2].equalsIgnoreCase("false")) next = Boolean.FALSE;
        else {
            sender.addChatMessage(new ChatComponentText(head
                    + "Usage: /gtbs handhelditem captions <name|id|metadata|mob> [on|off]"));
            return;
        }
        setHandheldCaption(args[1], next.booleanValue());
        GtbStructures.saveConfig();
        sender.addChatMessage(new ChatComponentText(head + "Handheld item " + args[1] + " caption is currently " + (next.booleanValue() ? "\u00a7aON" : "\u00a7cOFF")));
    }

    private static void handleHandheldItemLayout(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            sender.addChatMessage(new ChatComponentText(head + "Handheld item layout is currently \u00a7f" + (GtbStructures.handheldItemVerticalLayout ? "Vertical" : "Horizontal")));
            return;
        }
        if (args.length != 2 || (!args[1].equalsIgnoreCase("vertical") && !args[1].equalsIgnoreCase("horizontal"))) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs handhelditem layout [vertical|horizontal]"));
            return;
        }
        GtbStructures.handheldItemVerticalLayout = args[1].equalsIgnoreCase("vertical");
        GtbStructures.saveConfig();
        sender.addChatMessage(new ChatComponentText(head + "Handheld item layout set to \u00a7f" + (GtbStructures.handheldItemVerticalLayout ? "Vertical" : "Horizontal")));
    }

    private static void handleHandheldItemVertical(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            sender.addChatMessage(new ChatComponentText(head
                    + "Vertical layout places newly held items at the \u00a7f"
                    + (GtbStructures.handheldItemVerticalNewestAtBottom ? "bottom" : "top")));
            return;
        }
        if (args.length != 2 || (!args[1].equalsIgnoreCase("top") && !args[1].equalsIgnoreCase("bottom"))) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs handhelditem vertical [top|bottom]"));
            return;
        }
        GtbStructures.handheldItemVerticalNewestAtBottom = args[1].equalsIgnoreCase("bottom");
        GtbStructures.saveConfig();
        sender.addChatMessage(new ChatComponentText(head
                + "Vertical layout places newly held items at the \u00a7f" + args[1].toLowerCase(Locale.ROOT)));
    }

    private static void handleHandheldItemHorizontal(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            sender.addChatMessage(new ChatComponentText(head
                    + "Horizontal layout places newly held items at the \u00a7f"
                    + (GtbStructures.handheldItemHorizontalNewestAtRight ? "right" : "left")
                    + " \u00a77and \u00a7f"
                    + (GtbStructures.handheldItemHorizontalNewestAtBottom ? "bottom" : "top")));
            return;
        }
        if (args.length > 3) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs handhelditem horizontal [left|right] [top|bottom]"));
            return;
        }
        boolean right = GtbStructures.handheldItemHorizontalNewestAtRight;
        boolean bottom = GtbStructures.handheldItemHorizontalNewestAtBottom;
        boolean horizontalAxisSeen = false;
        boolean verticalAxisSeen = false;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("left")) {
                if (horizontalAxisSeen) { sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs handhelditem horizontal [left|right] [top|bottom]")); return; }
                right = false; horizontalAxisSeen = true;
            } else if (args[i].equalsIgnoreCase("right")) {
                if (horizontalAxisSeen) { sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs handhelditem horizontal [left|right] [top|bottom]")); return; }
                right = true; horizontalAxisSeen = true;
            } else if (args[i].equalsIgnoreCase("top")) {
                if (verticalAxisSeen) { sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs handhelditem horizontal [left|right] [top|bottom]")); return; }
                bottom = false; verticalAxisSeen = true;
            } else if (args[i].equalsIgnoreCase("bottom")) {
                if (verticalAxisSeen) { sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs handhelditem horizontal [left|right] [top|bottom]")); return; }
                bottom = true; verticalAxisSeen = true;
            }
            else { sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs handhelditem horizontal [left|right] [top|bottom]")); return; }
        }
        GtbStructures.handheldItemHorizontalNewestAtRight = right;
        GtbStructures.handheldItemHorizontalNewestAtBottom = bottom;
        GtbStructures.saveConfig();
        handleHandheldItemHorizontal(sender, new String[] {"horizontal"});
    }

    private static void handleHandheldItemSize(ICommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3 || (!args[1].equalsIgnoreCase("width") && !args[1].equalsIgnoreCase("height"))) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs handhelditem size <width|height> [value]"));
            return;
        }
        boolean width = args[1].equalsIgnoreCase("width");
        if (args.length == 2) {
            sender.addChatMessage(new ChatComponentText(head + "Handheld item panel " + args[1]
                    + " is currently \u00a7f"
                    + (width ? GtbStructures.handheldItemPanelWidthRatio : GtbStructures.handheldItemPanelHeightRatio)));
            return;
        }
        try {
            float value = Float.parseFloat(args[2]);
            if (Float.isNaN(value) || Float.isInfinite(value) || value < 0F || value > 1F) {
                sender.addChatMessage(new ChatComponentText(head + "Handheld item panel " + args[1]
                        + " must be a ratio from 0.0 to 1.0."));
                return;
            }
            if (width) GtbStructures.handheldItemPanelWidthRatio = value;
            else GtbStructures.handheldItemPanelHeightRatio = value;
            GtbStructures.saveConfig();
            sender.addChatMessage(new ChatComponentText(head + "Handheld item panel " + args[1]
                    + " set to \u00a7f" + value));
        } catch (NumberFormatException e) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs handhelditem size <width|height> [value]"));
        }
    }

    private static boolean isHandheldCaption(String value) {
        return value != null && (value.equalsIgnoreCase("name") || value.equalsIgnoreCase("id") || value.equalsIgnoreCase("metadata") || value.equalsIgnoreCase("mob"));
    }

    private static boolean handheldCaptionEnabled(String caption) {
        if (caption.equalsIgnoreCase("name")) return GtbStructures.handheldItemCaptionName;
        if (caption.equalsIgnoreCase("id")) return GtbStructures.handheldItemCaptionId;
        if (caption.equalsIgnoreCase("metadata")) return GtbStructures.handheldItemCaptionMetadata;
        return GtbStructures.handheldItemCaptionSpawnEggMob;
    }

    private static void setHandheldCaption(String caption, boolean value) {
        if (caption.equalsIgnoreCase("name")) GtbStructures.handheldItemCaptionName = value;
        else if (caption.equalsIgnoreCase("id")) GtbStructures.handheldItemCaptionId = value;
        else if (caption.equalsIgnoreCase("metadata")) GtbStructures.handheldItemCaptionMetadata = value;
        else GtbStructures.handheldItemCaptionSpawnEggMob = value;
    }

    private static void handleHandheldItemBackground(ICommandSender sender, String[] args) {
        Boolean next = parseBackgroundArgs(sender, args, "/gtbs handhelditem background [on|off]",
                GtbStructures.handheldItemBackground);
        if (next == null && args.length != 1) return;
        if (next == null) {
            sendBackgroundStatus(sender, "Handheld item background", GtbStructures.handheldItemBackground);
            return;
        }
        GtbStructures.handheldItemBackground = next.booleanValue();
        GtbStructures.saveConfig();
        sendBackgroundStatus(sender, "Handheld item background", GtbStructures.handheldItemBackground);
    }

    private static void handleEnvironment(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            GtbStructures.environmentOverlay = !GtbStructures.environmentOverlay;
            GtbStructures.saveConfig();
            sender.addChatMessage(new ChatComponentText(head + "Environment overlay is currently "
                    + (GtbStructures.environmentOverlay ? "\u00a7aON" : "\u00a7cOFF")));
            return;
        }

        if (args[0].equalsIgnoreCase("background")) {
            handleEnvironmentBackground(sender, args);
            return;
        }

        if (args[0].equalsIgnoreCase("chatnotice")) {
            handleEnvironmentChatNotice(sender, args);
            return;
        }

        if (args[0].equalsIgnoreCase("setlocation")) {
            if (args.length == 2 && isLocationAxis(args[1])) {
                sender.addChatMessage(new ChatComponentText(head
                        + locationStatusMessage("Environment", args[1], environmentLocationValue(args[1]))));
                return;
            }
            if (args.length != 3) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs environment setlocation <x | y> <value>"));
                return;
            }
            String axis = args[1].toLowerCase();
            try {
                float value = Float.parseFloat(args[2]);
                if (value < 0 || value > 1) {
                    sender.addChatMessage(new ChatComponentText(head + "Please enter a value between 0 and 1."));
                    return;
                }
                if (axis.equals("x")) {
                    GtbStructures.environmentPosX = value;
                    GtbStructures.saveConfig();
                    sender.addChatMessage(new ChatComponentText(head + "Environment X position set to \u00a7f" + value));
                } else if (axis.equals("y")) {
                    GtbStructures.environmentPosY = value;
                    GtbStructures.saveConfig();
                    sender.addChatMessage(new ChatComponentText(head + "Environment Y position set to \u00a7f" + value));
                } else {
                    sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs environment setlocation <x | y> <value>"));
                }
            } catch (NumberFormatException e) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs environment setlocation <x | y> <value>"));
            }
            return;
        }

        if (args[0].equalsIgnoreCase("weather")) {
            handleEnvironmentPart(sender, args, "Weather");
            return;
        }

        if (args[0].equalsIgnoreCase("biome")) {
            handleEnvironmentPart(sender, args, "Biome");
            return;
        }

        if (args[0].equalsIgnoreCase("time")) {
            handleEnvironmentPart(sender, args, "Time");
            return;
        }

        if (args[0].equalsIgnoreCase("timepreset")) {
            handleEnvironmentTimePreset(sender, args);
            return;
        }

        sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs environment [setlocation|weather|biome|time|timepreset|background|chatnotice] ..."));
    }

    private static void handleEnvironmentChatNotice(ICommandSender sender, String[] args) {
        String usage = "/gtbs environment chatnotice <weather|biome|time> [on|off]";
        if (args.length < 2 || args.length > 3 || !isEnvironmentPart(args[1])) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: " + usage));
            return;
        }

        String target = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            Boolean enabled = parseOptionalBoolean(args[2]);
            if (args[2].equalsIgnoreCase("toggle")) {
                enabled = Boolean.valueOf(!environmentChatNoticeEnabled(target));
            }
            if (enabled == null) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: " + usage));
                return;
            }
            setEnvironmentChatNoticeEnabled(target, enabled.booleanValue());
            GtbStructures.saveConfig();
        }

        sender.addChatMessage(new ChatComponentText(head + "Environment " + target
                + " chat notice is currently "
                + (environmentChatNoticeEnabled(target) ? "\u00a7aON" : "\u00a7cOFF")));
    }

    private static boolean environmentChatNoticeEnabled(String target) {
        if ("weather".equals(target)) return GtbStructures.environmentWeatherChatNotice;
        if ("biome".equals(target)) return GtbStructures.environmentBiomeChatNotice;
        return GtbStructures.environmentTimeChatNotice;
    }

    private static void setEnvironmentChatNoticeEnabled(String target, boolean enabled) {
        if ("weather".equals(target)) {
            GtbStructures.environmentWeatherChatNotice = enabled;
        } else if ("biome".equals(target)) {
            GtbStructures.environmentBiomeChatNotice = enabled;
        } else {
            GtbStructures.environmentTimeChatNotice = enabled;
        }
    }

    private static void handleEnvironmentBackground(ICommandSender sender, String[] args) {
        Boolean next = parseBackgroundArgs(sender, args, "/gtbs environment background [on|off]",
                GtbStructures.environmentBackground);
        if (next == null && args.length != 1) return;
        if (next == null) {
            sendBackgroundStatus(sender, "Environment background", GtbStructures.environmentBackground);
            return;
        }
        GtbStructures.environmentBackground = next.booleanValue();
        GtbStructures.saveConfig();
        sendBackgroundStatus(sender, "Environment background", GtbStructures.environmentBackground);
    }

    private static void handleEnvironmentPart(ICommandSender sender, String[] args, String label) {
        String target = label.toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            sender.addChatMessage(new ChatComponentText(head + "Environment " + target + " row is currently "
                    + (environmentPartEnabled(target) ? "\u00a7aON" : "\u00a7cOFF")));
            return;
        }
        if (args.length != 2) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs environment "
                    + target + " [on|off]"));
            return;
        }

        Boolean enabled = parseOptionalBoolean(args[1]);
        if (args[1].equalsIgnoreCase("toggle")) {
            enabled = Boolean.valueOf(!environmentPartEnabled(target));
        }
        if (enabled == null) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs environment "
                    + target + " [on|off]"));
            return;
        }
        if (target.equals("weather")) {
            GtbStructures.environmentWeatherEnabled = enabled.booleanValue();
        } else if (target.equals("biome")) {
            GtbStructures.environmentBiomeEnabled = enabled.booleanValue();
        } else {
            GtbStructures.environmentTimeEnabled = enabled.booleanValue();
        }
        if (enabled.booleanValue()) {
            GtbStructures.environmentOverlay = true;
        }
        GtbStructures.saveConfig();
        sender.addChatMessage(new ChatComponentText(head + "Environment " + target + " row is currently "
                + (enabled.booleanValue() ? "\u00a7aON" : "\u00a7cOFF")));
    }

    private static boolean environmentPartEnabled(String target) {
        if ("weather".equals(target)) return GtbStructures.environmentWeatherEnabled;
        if ("biome".equals(target)) return GtbStructures.environmentBiomeEnabled;
        return GtbStructures.environmentTimeEnabled;
    }

    private static void handleEnvironmentTimePreset(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            sender.addChatMessage(new ChatComponentText(head + "Environment time item preset is currently \u00a7f"
                    + displayEnvironmentTimePreset(GtbStructures.environmentTimeIconPreset)));
            return;
        }
        if (args.length != 2) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs environment timepreset <Clock|Blocks>"));
            return;
        }
        String raw = args[1].trim().toLowerCase(Locale.ROOT);
        if (!raw.equals("clock") && !raw.equals("blocks")) {
            sender.addChatMessage(new ChatComponentText(head + "Unknown time item preset \u00a7f" + args[1]
                    + "\u00a77. Valid: \u00a7fClock\u00a77, \u00a7fBlocks\u00a77."));
            return;
        }
        GtbStructures.environmentTimeIconPreset = WorldEnvironmentOverlay.normalizeTimeIconPreset(raw);
        GtbStructures.saveConfig();
        sender.addChatMessage(new ChatComponentText(head + "Environment time item preset set to \u00a7f"
                + displayEnvironmentTimePreset(GtbStructures.environmentTimeIconPreset)));
    }

    static String displayEnvironmentTimePresetForTest(String preset) {
        return displayEnvironmentTimePreset(preset);
    }

    private static String displayEnvironmentTimePreset(String preset) {
        return "blocks".equalsIgnoreCase(preset) ? "Blocks" : "Clock";
    }

    private static void handleCooldownCounter(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            GtbStructures.chatCooldownCounter = !GtbStructures.chatCooldownCounter;
            GtbStructures.saveConfig();
            sender.addChatMessage(new ChatComponentText(head + "Chat cooldown counter is currently "
                    + (GtbStructures.chatCooldownCounter ? "\u00a7aON" : "\u00a7cOFF")));
            return;
        }

        if (args[0].equalsIgnoreCase("background")) {
            handleCooldownCounterBackground(sender, args);
            return;
        }

        if (args[0].equalsIgnoreCase("timer") || args[0].equalsIgnoreCase("bar")) {
            handleCooldownCounterPart(sender, args);
            return;
        }

        if (args[0].equalsIgnoreCase("setcolor")) {
            handleCooldownCounterSetColor(sender, args);
            return;
        }

        if (args[0].equalsIgnoreCase("setlocation")) {
            if (args.length == 2 && isLocationAxis(args[1])) {
                sender.addChatMessage(new ChatComponentText(head
                        + locationStatusMessage("Chat cooldown", args[1], chatCooldownLocationValue(args[1]))));
                return;
            }
            if (args.length != 3) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs cooldowncounter setlocation <x | y> <value>"));
                return;
            }
            String axis = args[1].toLowerCase();
            try {
                float value = Float.parseFloat(args[2]);
                if (value < 0 || value > 1) {
                    sender.addChatMessage(new ChatComponentText(head + "Please enter a value between 0 and 1."));
                    return;
                }
                if (axis.equals("x")) {
                    GtbStructures.chatCooldownPosX = value;
                    GtbStructures.saveConfig();
                    sender.addChatMessage(new ChatComponentText(head + "Chat cooldown X position set to \u00a7f" + value));
                } else if (axis.equals("y")) {
                    GtbStructures.chatCooldownPosY = value;
                    GtbStructures.saveConfig();
                    sender.addChatMessage(new ChatComponentText(head + "Chat cooldown Y position set to \u00a7f" + value));
                } else {
                    sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs cooldowncounter setlocation <x | y> <value>"));
                }
            } catch (NumberFormatException e) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs cooldowncounter setlocation <x | y> <value>"));
            }
            return;
        }

        sender.addChatMessage(new ChatComponentText(head
                + "Usage: /gtbs cooldowncounter [setlocation|setcolor|timer|bar|background] ..."));
    }

    private static void handleCooldownCounterBackground(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            sendBackgroundStatus(sender, "Chat cooldown timer background", GtbStructures.chatCooldownBackground);
            sendBackgroundStatus(sender, "Chat cooldown bar background", GtbStructures.chatCooldownBarBackground);
            return;
        }
        if (args.length == 2 && isCooldownCounterPart(args[1])) {
            sendCooldownBackgroundStatus(sender, args[1]);
            return;
        }
        if (args.length == 2) {
            Boolean next = parseBooleanOrToggle(sender, args[1], "/gtbs cooldowncounter background <timer|bar> [on|off]",
                    GtbStructures.chatCooldownBackground);
            if (next == null) return;
            GtbStructures.chatCooldownBackground = next.booleanValue();
            GtbStructures.saveConfig();
            sendBackgroundStatus(sender, "Chat cooldown timer background", GtbStructures.chatCooldownBackground);
            return;
        }
        if (args.length == 3 && isCooldownCounterPart(args[1])) {
            String part = args[1].toLowerCase();
            boolean current = part.equals("timer")
                    ? GtbStructures.chatCooldownBackground
                    : GtbStructures.chatCooldownBarBackground;
            Boolean next = parseBooleanOrToggle(sender, args[2], "/gtbs cooldowncounter background <timer|bar> [on|off]",
                    current);
            if (next == null) return;
            if (part.equals("timer")) {
                GtbStructures.chatCooldownBackground = next.booleanValue();
            } else {
                GtbStructures.chatCooldownBarBackground = next.booleanValue();
            }
            GtbStructures.saveConfig();
            sendCooldownBackgroundStatus(sender, part);
            return;
        }
        sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs cooldowncounter background <timer|bar> [on|off]"));
    }

    private static void handleCooldownCounterPart(ICommandSender sender, String[] args) {
        String part = args[0].toLowerCase();
        boolean current = part.equals("timer")
                ? GtbStructures.chatCooldownTimerEnabled
                : GtbStructures.chatCooldownBarEnabled;
        String label = part.equals("timer") ? "Chat cooldown timer" : "Chat cooldown bar";
        Boolean next = parseBackgroundArgs(sender, args, "/gtbs cooldowncounter " + part + " [on|off]",
                current);
        if (next == null && args.length != 1) return;
        if (next == null) {
            sendBackgroundStatus(sender, label, current);
            return;
        }
        if (part.equals("timer")) {
            GtbStructures.chatCooldownTimerEnabled = next.booleanValue();
        } else {
            GtbStructures.chatCooldownBarEnabled = next.booleanValue();
        }
        GtbStructures.saveConfig();
        sendBackgroundStatus(sender, label, next.booleanValue());
    }

    private static boolean isCooldownCounterPart(String value) {
        return value != null && (value.equalsIgnoreCase("timer") || value.equalsIgnoreCase("bar"));
    }

    private static void sendCooldownBackgroundStatus(ICommandSender sender, String part) {
        if (part.equalsIgnoreCase("timer")) {
            sendBackgroundStatus(sender, "Chat cooldown timer background", GtbStructures.chatCooldownBackground);
        } else {
            sendBackgroundStatus(sender, "Chat cooldown bar background", GtbStructures.chatCooldownBarBackground);
        }
    }

    private static Boolean parseBooleanOrToggle(ICommandSender sender, String raw, String usage, boolean current) {
        if (raw != null && raw.equalsIgnoreCase("toggle")) {
            return Boolean.valueOf(!current);
        }
        Boolean enabled = parseOptionalBoolean(raw);
        if (enabled == null) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: " + usage));
        }
        return enabled;
    }

    private static Boolean parseBackgroundArgs(ICommandSender sender, String[] args, String usage, boolean current) {
        if (args.length == 1) return null;
        if (args.length != 2) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: " + usage));
            return null;
        }
        if (args[1].equalsIgnoreCase("toggle")) {
            return Boolean.valueOf(!current);
        }
        Boolean enabled = parseOptionalBoolean(args[1]);
        if (enabled == null) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: " + usage));
        }
        return enabled;
    }

    private static void sendBackgroundStatus(ICommandSender sender, String label, boolean enabled) {
        sender.addChatMessage(new ChatComponentText(head + backgroundStatusMessage(label, enabled)));
    }

    static String backgroundStatusMessageForTest(String label, boolean enabled) {
        return backgroundStatusMessage(label, enabled);
    }

    private static String backgroundStatusMessage(String label, boolean enabled) {
        return label + " is currently " + (enabled ? "\u00a7aON" : "\u00a7cOFF");
    }

    private static void handleCooldownCounterSetColor(ICommandSender sender, String[] args) {
        if (args.length == 2) {
            String target = args[1].toLowerCase();
            String message = currentCooldownColorMessage(target);
            if (message == null) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs cooldowncounter setcolor <bar|timer> <color>"));
            } else {
                sender.addChatMessage(new ChatComponentText(head + message));
            }
            return;
        }
        if (args.length != 3) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs cooldowncounter setcolor <bar|timer> <color>"));
            return;
        }
        String target = args[1].toLowerCase();
        String color = normalizeCommandColor(args[2]);
        if (color == null) {
            sender.addChatMessage(new ChatComponentText(head + invalidColorMessage()));
            return;
        }
        String colorCode = color.substring(1);
        if (target.equals("bar")) {
            GtbStructures.chatCooldownBarColor = color;
            sender.addChatMessage(new ChatComponentText(head + "Cooldown bar color set to " + color + colorCode));
        } else if (target.equals("timer")) {
            GtbStructures.chatCooldownTimerColor = color;
            sender.addChatMessage(new ChatComponentText(head + "Cooldown timer color set to " + color + colorCode));
        } else {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs cooldowncounter setcolor <bar|timer> <color>"));
            return;
        }
        GtbStructures.saveConfig();
    }

    private static void handleGuessPeek(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            GtbStructures.guessPeek = !GtbStructures.guessPeek;
            GtbStructures.saveConfig();
            sender.addChatMessage(new ChatComponentText(head + "Guess peek is currently "
                    + (GtbStructures.guessPeek ? "\u00a7aON" : "\u00a7cOFF")));
            return;
        }
        if (args[0].equalsIgnoreCase("showlangcodes")) {
            if (args.length == 1) {
                sender.addChatMessage(new ChatComponentText(head + "Guess peek language codes are currently "
                        + (GtbStructures.guessPeekShowLangCodes ? "\u00a7aON" : "\u00a7cOFF")));
                return;
            }
            if (args.length != 2) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs guesspeek showlangcodes [on|off]"));
                return;
            }
            Boolean enabled = parseOptionalBoolean(args[1]);
            if (enabled == null) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs guesspeek showlangcodes [on|off]"));
                return;
            }
            GtbStructures.guessPeekShowLangCodes = enabled.booleanValue();
            GtbStructures.saveConfig();
            sender.addChatMessage(new ChatComponentText(head + "Guess peek language codes are currently "
                    + (GtbStructures.guessPeekShowLangCodes ? "\u00a7aON" : "\u00a7cOFF")));
            return;
        }
        if (args[0].equalsIgnoreCase("display")) {
            if (args.length == 1) {
                sendGuessPeekDisplayStatus(sender);
                return;
            }
            if (args.length != 2
                    || (!args[1].equalsIgnoreCase("hover") && !args[1].equalsIgnoreCase("suffix"))) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs guesspeek display [hover|suffix]"));
                return;
            }
            GtbStructures.guessPeekDisplay = GtbStructures.normalizeGuessPeekDisplay(args[1]);
            GtbStructures.saveConfig();
            sendGuessPeekDisplayStatus(sender);
            return;
        }
        sender.addChatMessage(new ChatComponentText(head
                + "Usage: /gtbs guesspeek [showlangcodes [on|off]|display [hover|suffix]]"));
    }

    private static void sendGuessPeekDisplayStatus(ICommandSender sender) {
        GtbStructures.guessPeekDisplay = GtbStructures.normalizeGuessPeekDisplay(GtbStructures.guessPeekDisplay);
        sender.addChatMessage(new ChatComponentText(head + "Guess peek display is currently \u00a7f"
                + GtbStructures.guessPeekDisplay));
    }

    private static void handleScrambleGuess(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            GtbStructures.scrambleGuess = !GtbStructures.scrambleGuess;
            GtbStructures.saveConfig();
            sendScrambleGuessStatus(sender, false);
            return;
        }
        if (args[0].equalsIgnoreCase("fancy")) {
            if (args.length == 1) {
                sendScrambleGuessStatus(sender, true);
                return;
            }
            if (args.length == 2) {
                Boolean enabled = parseOptionalBoolean(args[1]);
                if (enabled != null) {
                    GtbStructures.scrambleGuessFancy = enabled.booleanValue();
                    GtbStructures.saveConfig();
                    sendScrambleGuessStatus(sender, true);
                    return;
                }
            }
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs scrambleguess fancy [on|off]"));
            return;
        }
        if (args.length == 1) {
            Boolean enabled = parseOptionalBoolean(args[0]);
            if (enabled != null) {
                GtbStructures.scrambleGuess = enabled.booleanValue();
                GtbStructures.saveConfig();
                sendScrambleGuessStatus(sender, false);
                return;
            }
        }
        sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs scrambleguess [on|off]"));
    }

    private static void sendScrambleGuessStatus(ICommandSender sender, boolean fancy) {
        boolean enabled = fancy ? GtbStructures.scrambleGuessFancy : GtbStructures.scrambleGuess;
        sender.addChatMessage(new ChatComponentText(head
                + (fancy ? "Fancy scramble guess" : "Scramble guess") + " is currently "
                + (enabled ? "\u00a7aON" : "\u00a7cOFF")));
    }

    private static void handleTrollMode(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            GtbStructures.trollMode = !GtbStructures.trollMode;
            GtbStructures.saveConfig();
            sendTrollModeStatus(sender);
            return;
        }
        if (args[0].equalsIgnoreCase("autodisable")) {
            if (args.length == 1) {
                sendTrollModeAutoDisableStatus(sender);
                return;
            }
            if (args.length == 2) {
                Boolean enabled = parseOptionalBoolean(args[1]);
                if (enabled != null) {
                    GtbStructures.trollModeAutoDisable = enabled.booleanValue();
                    GtbStructures.saveConfig();
                    sendTrollModeAutoDisableStatus(sender);
                    return;
                }
            }
            sender.addChatMessage(new ChatComponentText(head
                    + "Usage: /gtbs trollmode autodisable [on|off]"));
            return;
        }
        if (args.length == 1) {
            Boolean enabled = parseOptionalBoolean(args[0]);
            if (enabled != null) {
                GtbStructures.trollMode = enabled.booleanValue();
                GtbStructures.saveConfig();
                sendTrollModeStatus(sender);
                return;
            }
        }
        sender.addChatMessage(new ChatComponentText(head
                + "Usage: /gtbs trollmode [on|off|autodisable [on|off]]"));
    }

    private static void sendTrollModeStatus(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(head + "Troll mode is currently "
                + (GtbStructures.trollMode ? "\u00a7aON" : "\u00a7cOFF")));
    }

    private static void sendTrollModeAutoDisableStatus(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(head + "Troll mode auto-disable is currently "
                + (GtbStructures.trollModeAutoDisable ? "\u00a7aON" : "\u00a7cOFF")));
    }

    private static void handleHeader(ICommandSender sender, String[] args) {
        String requested = joinArgs(args);
        if (!ChatHeader.set(requested)) {
            sender.addChatMessage(new ChatComponentText(head + "Header must be at most \u00a7f"
                    + ChatHeader.MAX_VISIBLE_LENGTH + "\u00a77 visible characters."));
            return;
        }
        refreshHeader();
        GtbStructures.saveConfig();
        if (requested.trim().isEmpty()) {
            sender.addChatMessage(new ChatComponentText(head + "Header reset to default."));
        } else {
            sender.addChatMessage(new ChatComponentText(head + "Header set to \u00a7r"
                    + ChatHeader.rendered()));
        }
    }

    private static String joinArgs(String[] args) {
        if (args == null || args.length == 0) return "";
        StringBuilder out = new StringBuilder();
        for (String arg : args) {
            if (out.length() > 0) out.append(' ');
            out.append(arg == null ? "" : arg);
        }
        return out.toString();
    }

    private static void handleShortcutReminder(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            GtbStructures.shortcutReminder = !GtbStructures.shortcutReminder;
            GtbStructures.saveConfig();
            sendShortcutStatus(sender, "Shortcut reminder", GtbStructures.shortcutReminder);
            return;
        }

        if (args[0].equalsIgnoreCase("showallshortcuts")) {
            if (args.length == 1) {
                sendShortcutStatus(sender, "Show all shortcuts",
                        GtbStructures.shortcutReminderShowAllShortcuts);
                return;
            }
            if (args.length == 2) {
                Boolean enabled = parseToggleBoolean(args[1],
                        GtbStructures.shortcutReminderShowAllShortcuts);
                if (enabled != null) {
                    GtbStructures.shortcutReminderShowAllShortcuts = enabled.booleanValue();
                    GtbStructures.saveConfig();
                    sendShortcutStatus(sender, "Show all shortcuts",
                            GtbStructures.shortcutReminderShowAllShortcuts);
                    return;
                }
            }
            sender.addChatMessage(new ChatComponentText(head
                    + "Usage: /gtbs shortcutreminder showallshortcuts [on|off|toggle]"));
            return;
        }

        if (args[0].equalsIgnoreCase("showmultiwords")) {
            if (args.length == 1) {
                sendShortcutStatus(sender, "Show multiwords",
                        GtbStructures.shortcutReminderShowMultiwords);
                return;
            }
            if (args.length == 2 && args[1].equalsIgnoreCase("showall")) {
                sendShortcutStatus(sender, "Show all multiwords",
                        GtbStructures.shortcutReminderShowAllMultiwords);
                return;
            }
            if (args.length == 2) {
                Boolean enabled = parseToggleBoolean(args[1],
                        GtbStructures.shortcutReminderShowMultiwords);
                if (enabled != null) {
                    GtbStructures.shortcutReminderShowMultiwords = enabled.booleanValue();
                    GtbStructures.saveConfig();
                    sendShortcutStatus(sender, "Show multiwords",
                            GtbStructures.shortcutReminderShowMultiwords);
                    return;
                }
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("showall")) {
                Boolean enabled = parseToggleBoolean(args[2],
                        GtbStructures.shortcutReminderShowAllMultiwords);
                if (enabled != null) {
                    GtbStructures.shortcutReminderShowAllMultiwords = enabled.booleanValue();
                    GtbStructures.saveConfig();
                    sendShortcutStatus(sender, "Show all multiwords",
                            GtbStructures.shortcutReminderShowAllMultiwords);
                    return;
                }
            }
            sender.addChatMessage(new ChatComponentText(head
                    + "Usage: /gtbs shortcutreminder showmultiwords "
                    + "[on|off|toggle|showall [on|off|toggle]]"));
            return;
        }

        if (args[0].equalsIgnoreCase("shownonascii")) {
            if (args.length == 1) {
                sendShortcutStatus(sender, "Show non-ASCII shortcuts",
                        GtbStructures.shortcutReminderShowNonAscii);
                return;
            }
            if (args.length == 2 && isShortcutNonAsciiCategory(args[1])) {
                sendShortcutStatus(sender, shortcutCategoryLabel(args[1]),
                        shortcutCategoryEnabled(args[1]));
                return;
            }
            if (args.length == 2) {
                Boolean enabled = parseToggleBoolean(args[1],
                        GtbStructures.shortcutReminderShowNonAscii);
                if (enabled != null) {
                    GtbStructures.setShortcutReminderShowNonAscii(enabled.booleanValue());
                    GtbStructures.saveConfig();
                    sendShortcutStatus(sender, "Show non-ASCII shortcuts",
                            GtbStructures.shortcutReminderShowNonAscii);
                    return;
                }
            }
            if (args.length == 3 && isShortcutNonAsciiCategory(args[1])) {
                Boolean enabled = parseToggleBoolean(args[2],
                        shortcutCategoryEnabled(args[1]));
                if (enabled != null) {
                    setShortcutCategoryEnabled(args[1], enabled.booleanValue());
                    GtbStructures.saveConfig();
                    sendShortcutStatus(sender, shortcutCategoryLabel(args[1]),
                            shortcutCategoryEnabled(args[1]));
                    return;
                }
            }
            sender.addChatMessage(new ChatComponentText(head
                    + "Usage: /gtbs shortcutreminder shownonascii "
                    + "[on|off|toggle|<extendedlatin|cyrillic|hanzi|japanese|hangul> [on|off|toggle]]"));
            return;
        }

        sender.addChatMessage(new ChatComponentText(head
                + "Usage: /gtbs shortcutreminder [shownonascii|showallshortcuts|showmultiwords] ..."));
    }

    private static void sendShortcutStatus(ICommandSender sender, String label, boolean enabled) {
        sender.addChatMessage(new ChatComponentText(head + label + " is currently "
                + (enabled ? "\u00a7aON" : "\u00a7cOFF")));
    }

    private static Boolean parseToggleBoolean(String raw, boolean current) {
        if (raw != null && raw.trim().equalsIgnoreCase("toggle")) {
            return Boolean.valueOf(!current);
        }
        return parseOptionalBoolean(raw);
    }

    private static boolean isShortcutNonAsciiCategory(String raw) {
        if (raw == null) return false;
        String category = raw.trim().toLowerCase(Locale.ROOT);
        return category.equals("extendedlatin")
                || category.equals("cyrillic")
                || category.equals("hanzi")
                || category.equals("japanese")
                || category.equals("hangul");
    }

    private static boolean shortcutCategoryEnabled(String raw) {
        return GtbStructures.shortcutNonAsciiCategoryEnabled(shortcutCategory(raw));
    }

    private static void setShortcutCategoryEnabled(String raw, boolean enabled) {
        GtbStructures.setShortcutNonAsciiCategoryEnabled(shortcutCategory(raw), enabled);
    }

    private static GtbStructures.ShortcutNonAsciiCategory shortcutCategory(String raw) {
        String category = raw.trim().toLowerCase(Locale.ROOT);
        if (category.equals("extendedlatin")) {
            return GtbStructures.ShortcutNonAsciiCategory.EXTENDED_LATIN;
        } else if (category.equals("cyrillic")) {
            return GtbStructures.ShortcutNonAsciiCategory.CYRILLIC;
        } else if (category.equals("hanzi")) {
            return GtbStructures.ShortcutNonAsciiCategory.HANZI;
        } else if (category.equals("japanese")) {
            return GtbStructures.ShortcutNonAsciiCategory.JAPANESE;
        }
        return GtbStructures.ShortcutNonAsciiCategory.HANGUL;
    }

    private static String shortcutCategoryLabel(String raw) {
        String category = raw.trim().toLowerCase(Locale.ROOT);
        if (category.equals("extendedlatin")) return "Extended Latin shortcuts";
        if (category.equals("cyrillic")) return "Cyrillic shortcuts";
        if (category.equals("hanzi")) return "Hanzi shortcuts";
        if (category.equals("japanese")) return "Kanji & Kana shortcuts";
        return "Hangul shortcuts";
    }

    private static void handleCopyGuess(String[] args) {
        if (args.length < 2) return;
        String guess = GuessChatAnnotator.decodeCopyPayload(args[1]);
        if (guess == null || guess.isEmpty()) return;
        GuiScreen.setClipboardString(guess);
    }

    static void handleCopyTranslation(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs copytranslation <theme> [langs]"));
            return;
        }
        if (args[0].equalsIgnoreCase("prefix")) {
            if (args.length != 2) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs copytranslation prefix <true|false>"));
                return;
            }
            Boolean enabled = parseOptionalBoolean(args[1]);
            if (enabled == null) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs copytranslation prefix <true|false>"));
                return;
            }
            GtbStructures.copyTranslationPrefix = enabled.booleanValue();
            GtbStructures.saveConfig();
            sender.addChatMessage(new ChatComponentText(head + "Copy translation prefix is currently "
                    + (GtbStructures.copyTranslationPrefix ? "\u00a7aON" : "\u00a7cOFF")));
            return;
        }

        CopyTranslationRequest request = parseCopyTranslationRequest(args);
        if (request == null || request.theme == null || request.theme.isEmpty()) {
            sender.addChatMessage(new ChatComponentText(head + "Usage: /gtbs copytranslation <theme> [langs]"));
            return;
        }
        if (request.langError != null) {
            sender.addChatMessage(new ChatComponentText(head + request.langError));
            return;
        }
        CopyTranslationResult result = buildCopyTranslationMessage(request.theme, request.langCodes,
                GtbStructures.copyTranslationPrefix);
        if (!result.ok) {
            sender.addChatMessage(new ChatComponentText(head + result.error));
            return;
        }
        GuiScreen.setClipboardString(result.message);
        sender.addChatMessage(new ChatComponentText(head + "Message copied to clipboard: \u00a7f" + result.message));
    }

    private static final HelpCategory[] HELP_CATEGORIES = new HelpCategory[] {
            new HelpCategory("tracking", "TRACKING", "Stats tracking and exports", new HelpLine[] {
                    HelpLine.command("/gtbs stats session [days]", "Summarizes the current player's recorded sessions."),
                    HelpLine.command("/gtbs stats theme [entries|all]", "Summarizes recorded English-theme occurrences."),
                    HelpLine.command("/gtbs export", "Exports the current game's stats to a file."),
                    HelpLine.command("/gtbs exportraw", "Exports the current raw packet buffer."),
                    HelpLine.command("/gtbs tracking", "Toggles stats tracking."),
                    HelpLine.note("Tracks GTB rounds, builders, guesses, themes, scores, rejoin gaps, and final leaderboards."),
                    HelpLine.note("Output folder: .minecraft/config/gtbstats/")
            }),
            new HelpCategory("hints", "HINTS", "Hint structure and matching-theme output settings", new HelpLine[] {
                    HelpLine.command("/gtbs hintstructure", "Toggles the hint structure overlay in the form of numbers and hyphens. The number indicates the consecutive underscores and revealed letters, and the hyphen indicates the sandwiched space. Shortcut: /gtbs hs."),
                    HelpLine.command("/gtbs hintstructure background [on|off]", "Shows or sets the hint background."),
                    HelpLine.command("/gtbs hintstructure setlocation <x|y> [value]", "Shows or sets overlay position. Value is 0.0-1.0."),
                    HelpLine.command("/gtbs hintstructure setcolor <bracket|number|hyphen> [color]", "Shows or sets overlay bracket/number/hyphen color."),
                    HelpLine.command("/gtbs colorlist", "Lists shared Minecraft color codes."),
                    HelpLine.command("/gtbs showallmatching", "Toggles whether to show all themes matching the initial hint after the theme is revealed."),
                    HelpLine.command("/gtbs showallmatching showenglish [on|off]", "Controls whether to show English source themes in the matching-theme output.")
            }),
            new HelpCategory("scoreboard", "SCOREBOARD", "Custom scoreboard related settings", new HelpLine[] {
                    HelpLine.command("/gtbs scoreboard", "Toggles the custom GTB scoreboard panel."),
                    HelpLine.note("\u00b7 \u00a77:\u00a76sp\u00a7ein\u00a7fner\u00a77: \u00a78- \u00a77building/up next."),
                    HelpLine.note("\u00b7 \u00a77:\u00a7acheck\u00a77: \u00a78- \u00a77built."),
                    HelpLine.note("\u00b7 \u00a77:\u00a7cexit\u00a77: \u00a78- \u00a77left the game."),
                    HelpLine.note("\u00b7 \u00a77:zzz: \u00a78- \u00a77silent/away."),
                    HelpLine.note("\u00b7 \u00a77(\u00a7b+\u00a77) \u00a78- \u00a77pending score value change."),
                    HelpLine.note("\u00b7 \u00a77(\u00a72X\u00a77) \u00a78- \u00a77lower score bound or score interval."),
                    HelpLine.note("\u00b7 \u00a7c(!) \u00a78- \u00a77uncertain score."),
                    HelpLine.command("/gtbs scoreboard skin [vanilla|default|hypixel]", "Shows or switches between vanilla, full custom (GuessTheUtils-style), and Hypixel-style skins."),
                    HelpLine.command("/gtbs scoreboard <backgroundopacity|highlightintensity> [0-1]", "Shows or sets a custom scoreboard visual intensity."),
                    HelpLine.command("/gtbs scoreboard showpoints [off|expanded|on]", "Controls current-round score deltas."),
                    HelpLine.command("/gtbs scoreboard showplaces [off|expanded|on]", "Controls leaderboard place numbers."),
                    HelpLine.command("/gtbs scoreboard showtitles [off|expanded|on]", "Controls Build Battle titles."),
                    HelpLine.command("/gtbs scoreboard showemblems [off|expanded|on]", "Controls player emblems."),
                    HelpLine.command("/gtbs scoreboard textshadow [on|off]", "Shows or hides custom scoreboard text shadow."),
                    HelpLine.command("/gtbs scoreboard leftlast [on|off]", "Places confirmed leavers at the bottom of custom scoreboards.")
            }),
            new HelpCategory("items", "ITEMS", "Handheld item overlay and item settings", new HelpLine[] {
                    HelpLine.command("/gtbs handhelditem", "Toggles the builder handheld item history overlay."),
                    HelpLine.command("/gtbs handhelditem background [on|off]", "Shows or hides the handheld item list background."),
                    HelpLine.command("/gtbs handhelditem captions <name|id|metadata|mob> [on|off]", "Shows or sets an item caption. Captions appear only in vertical layout."),
                    HelpLine.command("/gtbs handhelditem layout [vertical|horizontal]", "Switches between captioned vertical items and an icon-only horizontal grid."),
                    HelpLine.command("/gtbs handhelditem vertical [top|bottom]", "Places newly held items at the top or bottom in vertical layout."),
                    HelpLine.command("/gtbs handhelditem horizontal [left|right] [top|bottom]", "Places newly held icons along each axis in horizontal layout."),
                    HelpLine.command("/gtbs handhelditem size <width|height> [value]", "Shows or sets the resizable panel bounds as a 0.0-1.0 scaled-screen ratio."),
                    HelpLine.command("/gtbs handhelditem setlocation <x|y> [value]", "Shows or sets the position of the handheld overlay. Value is 0.0-1.0."),
                    HelpLine.command("/gtbs handhelditem max [rows]", "Sets visible rows of items on the overlay. Accepted value: 2-10."),
                    HelpLine.command("/gtbs spawnegg [classic|new]", "Sets the appearance of spawn egg icons."),
                    HelpLine.note("classic: 1.8 spawn eggs and 1.17 templates for moderner mobs."),
                    HelpLine.note("new: 1.21 spawn eggs.")
            }),
            new HelpCategory("environment", "ENVIRONMENT", "Weather, biome, and time labels", new HelpLine[] {
                    HelpLine.command("/gtbs environment", "Toggles the weather, biome, and time overlay."),
                    HelpLine.command("/gtbs environment background [on|off]", "Shows or hides the environment panel background."),
                    HelpLine.command("/gtbs environment <weather|biome|time> [on|off]", "Shows or hides an environment row."),
                    HelpLine.command("/gtbs environment chatnotice <weather|biome|time> [on|off]", "Shows or controls chat notices when an environment value changes."),
                    HelpLine.command("/gtbs environment timepreset [clock|blocks]", "Shows or switches the time icon between clock and colored blocks; each block represents a 3-hour period."),
                    HelpLine.command("/gtbs environment setlocation <x|y> [value]", "Shows or sets the environment overlay bottom position. Value is 0.0-1.0.")
            }),
            new HelpCategory("chat", "CHAT", "Cooldown counter and guess annotations", new HelpLine[] {
                    HelpLine.command("/gtbs header <content>", "Sets the formatting-aware prefix shown before GTBS notices; empty content restores the default."),
                    HelpLine.command("/gtbs cooldowncounter", "Toggles the outbound chat cooldown counter when guessing."),
                    HelpLine.command("/gtbs cooldowncounter <timer|bar> [on|off]", "Shows or hides the cooldown timer text or progress bar."),
                    HelpLine.command("/gtbs cooldowncounter background <timer|bar> [on|off]", "Shows or hides timer or bar backgrounds."),
                    HelpLine.command("/gtbs cooldowncounter setcolor <bar|timer> [color]", "Shows or sets cooldown bar or timer text color."),
                    HelpLine.command("/gtbs cooldowncounter setlocation <x|y> [value]", "Shows or sets cooldown counter position. Value is 0.0-1.0."),
                    HelpLine.command("/gtbs guesspeek", "Toggles English-theme annotations on matching GTB guesses."),
                    HelpLine.command("/gtbs guesspeek display [hover|suffix]", "Shows or sets where guess-peek translations appear."),
                    HelpLine.command("/gtbs guesspeek showlangcodes [on|off]", "Shows or hides source language codes in guess-peek hover text."),
                    HelpLine.command("/gtbs themepointreveal", "Toggles exact theme point-value notices in chat."),
                    HelpLine.command("/gtbs themepointreveal showafterguess <on|off>", "Controls whether a notice may still appear after the complete theme is revealed."),
                    HelpLine.command("/gtbs shortcutreminder", "Toggles shortcut reminders after themes are revealed."),
                    HelpLine.command("/gtbs shortcutreminder shownonascii [on|off]", "Shows or hides enabled non-ASCII shortcut categories."),
                    HelpLine.command("/gtbs shortcutreminder shownonascii <extendedlatin|cyrillic|hanzi|japanese|hangul> [on|off]", "Customizes shown non-ASCII shortcut categories."),
                    HelpLine.command("/gtbs shortcutreminder showallshortcuts [on|off]", "Shows every shortest shortcut instead of only the selected shortcut."),
                    HelpLine.command("/gtbs shortcutreminder showmultiwords [on|off]", "Shows or hides multiword reminders after shortcut reminders."),
                    HelpLine.command("/gtbs shortcutreminder showmultiwords showall [on|off]", "Shows every accepted multiword per theme set instead of only the shortest one.")
            }),
            new HelpCategory("experimental", "EXPERIMENTAL", "Experimental options", new HelpLine[] {
                    HelpLine.command("/gtbs scrambleguess [on|off]", "Toggles whether to replace a valid guess with a random guess working for the exact same theme."),
                    HelpLine.command("/gtbs scrambleguess fancy [on|off]", "Toggles whether to apply randomized modifications to the final string."),
                    HelpLine.command("/gtbs trollmode [on|off]", "Toggles whether to change a valid guess into a visually similar invalid guess. Shortcut: /trollmode [on|off]."),
                    HelpLine.command("/gtbs trollmode autodisable [on|off]", "Controls whether Troll Mode turns off when a GTB game starts. Shortcut: /trollmode autodisable [on|off].")
            }),
            new HelpCategory("game", "GAME", "Queue controls, builder checks, and preview", new HelpLine[] {
                    HelpLine.command("/gtbs toggle", "Toggles all GTBS features without changing their individual settings."),
                    HelpLine.command("/gtbs gtb", "Joins Guess the Build. Shortcut: /gtb."),
                    HelpLine.command("/gtbs lrj", "Leaves to lobby, then rejoins. Shortcut: /lrj."),
                    HelpLine.command("/gtbs preview", "Toggles a session-only GUI preview for enabled interface features where you can customize the settings."),
                    HelpLine.command("/gtbs autorequeue", "Toggles auto-requeue after a game ends."),
                    HelpLine.command("/gtbs updatenotice [on|off|toggle]", "Toggles or sets the update notice shown after the first multiplayer join each startup."),
                    HelpLine.command("/gtbs haveibuilt", "Reports whether you have built this game."),
                    HelpLine.command("/gtbs whohasbuilt", "Lists players who have built so far this game.")
            }),
            new HelpCategory("copy", "COPY", "Copy theme translations", new HelpLine[] {
                    HelpLine.command("/gtbs copytranslation <theme> [langs]", "Copies theme translations. Shortcuts: /copytranslation, /copyt."),
                    HelpLine.note("With no language value, copies translations from every available language."),
                    HelpLine.note("You can use language codes or full names to select desired translations to copy; for multiple languages, use comma to separate. e.g. en,ru; pirate_speak,Chinese_Simplified."),
                    HelpLine.command("/gtbs copytranslation prefix <true|false>", "Toggles whether to show the copied \"The theme is\" prefix.")
            }),
            new HelpCategory("gtw", "GTW", "Guess The Wordhint mini-game", new HelpLine[] {
                    HelpLine.command("/gtbs gtw start [language]", "Starts a Guess The Wordhint mini-session. Shortcut: /gtw start [language]."),
                    HelpLine.command("/gtbs guess <guess>", "Submits a Guess The Wordhint answer. Shortcut: /guess <guess>."),
                    HelpLine.command("/gtbs gtw stop", "Stops the Guess The Wordhint session and reveals the theme. Shortcut: /gtw stop."),
                    HelpLine.command("/gtbs gtw showenglish <true|false>", "Sets whether GTW reveal/list lines include English names.")
            })
    };
    private static final HelpCategory ALL_HELP_CATEGORY = buildAllHelpCategory();

    private static void sendHelp(ICommandSender sender, String[] args) {
        if (sender == null) return;
        for (String line : buildHelpMessages(args)) {
            sender.addChatMessage(new ChatComponentText(line));
        }
        if (!GtbStructures.previewQuickstartRan) {
            sender.addChatMessage(new ChatComponentText(head
                    + "Quickstart: use \u00a7f/gtbs preview\u00a77 to configure and preview most visual settings in the GUI."));
        }
    }

    static List<String> buildHelpMessages(String[] args) {
        ArrayList<String> out = new ArrayList<String>();
        if (args == null || args.length == 0) {
            appendHelpIndex(out);
            return out;
        }

        String requested = args[0];
        if (isInteger(requested)) {
            appendHelpCategory(out, ALL_HELP_CATEGORY, parseHelpPage(args, 0));
            return out;
        }

        if (requested.equalsIgnoreCase("all")) {
            appendHelpCategory(out, ALL_HELP_CATEGORY, parseHelpPage(args, 1));
            return out;
        }

        HelpCategory category = findHelpCategory(requested);
        if (category == null) {
            appendUnknownHelpCategory(out, requested);
            return out;
        }
        appendHelpCategory(out, category, parseHelpPage(args, 1));
        return out;
    }

    private static void appendHelpIndex(List<String> out) {
        out.add(helpBanner());
        for (HelpCategory category : HELP_CATEGORIES) {
            appendHelpLine(out, HelpLine.command("/gtbs help " + category.key, category.summary));
        }
        appendHelpLine(out, HelpLine.command("/gtbs help all [page]", "Display every command."));
        out.add(helpBanner());
    }

    private static void appendHelpCategory(List<String> out, HelpCategory category, int requestedPage) {
        int lineCount = category.lines.length;
        int pageCount = pageCount(lineCount);
        int page = Math.max(1, Math.min(requestedPage, pageCount));
        int linesOnPage = lineCountForPage(lineCount, pageCount, page);
        int startLine = firstLineForPage(lineCount, pageCount, page);
        int endLine = startLine + linesOnPage;

        out.add(helpBanner());
        out.add("\u00a76Category: \u00a77" + category.title + "\u00a76, Page: \u00a77" + page + "/" + pageCount);
        for (int i = startLine; i < endLine; i++) {
            appendHelpLine(out, category.lines[i]);
        }
        out.add(helpBanner());
    }

    private static void appendHelpLine(List<String> out, HelpLine line) {
        if (line.command == null) {
            out.add("  \u00a77" + line.description);
            return;
        }
        if (line.command.length() + line.description.length() > 78) {
            out.add("\u00a76" + line.command);
            out.add("  \u00a78- \u00a77" + line.description);
            return;
        }
        out.add("\u00a76" + line.command + " \u00a78- \u00a77" + line.description);
    }

    private static String helpBanner() {
        return "\u00a78\u00a7m--------\u00a7r \u00a7eGtbStructures\u00a76 Help \u00a78\u00a7m--------";
    }

    private static int pageCount(int lineCount) {
        return Math.max(1, (lineCount + MAX_HELP_LINES_PER_PAGE - 1) / MAX_HELP_LINES_PER_PAGE);
    }

    private static int lineCountForPage(int lineCount, int pageCount, int page) {
        int base = lineCount / pageCount;
        int remainder = lineCount % pageCount;
        return base + (page <= remainder ? 1 : 0);
    }

    private static int firstLineForPage(int lineCount, int pageCount, int page) {
        int zeroBasedPage = page - 1;
        int base = lineCount / pageCount;
        int remainder = lineCount % pageCount;
        return zeroBasedPage * base + Math.min(zeroBasedPage, remainder);
    }

    private static int parseHelpPage(String[] args, int index) {
        if (args == null || args.length <= index) return 1;
        try {
            int page = Integer.parseInt(args[index]);
            return page < 1 ? 1 : page;
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static HelpCategory findHelpCategory(String raw) {
        String key = normalizeHelpCategory(raw);
        for (HelpCategory category : HELP_CATEGORIES) {
            if (category.key.equals(key)) return category;
        }
        return null;
    }

    private static String normalizeHelpCategory(String raw) {
        if (raw == null) return "";
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (key.equals("item")) return "items";
        if (key.equals("env") || key.equals("world")) return "environment";
        if (key.equals("overlay") || key.equals("hint") || key.equals("hintstructure") || key.equals("hs")) return "hints";
        if (key.equals("score") || key.equals("sb")) return "scoreboard";
        if (key.equals("cooldown") || key.equals("cooldowncounter")) return "chat";
        if (key.equals("copytranslation") || key.equals("copyt")) return "copy";
        if (key.equals("wordhint") || key.equals("guess")) return "gtw";
        if (key.equals("misc") || key.equals("general")) return "game";
        return key;
    }

    private static HelpCategory buildAllHelpCategory() {
        ArrayList<HelpLine> lines = new ArrayList<HelpLine>();
        for (HelpCategory category : HELP_CATEGORIES) {
            for (HelpLine line : category.lines) {
                if (line.command != null) lines.add(line);
            }
        }
        Collections.sort(lines, new Comparator<HelpLine>() {
            public int compare(HelpLine a, HelpLine b) {
                return a.command.compareToIgnoreCase(b.command);
            }
        });
        return new HelpCategory("all", "ALL", "Display every command", lines.toArray(new HelpLine[lines.size()]));
    }

    private static void appendUnknownHelpCategory(List<String> out, String requested) {
        out.add(head + "\u00a77Unknown help category:\u00a7f " + requested);
        String suggestion = suggestHelpCategory(requested);
        if (suggestion != null) {
            out.add("\u00a77Did you mean: \u00a7f/gtbs help " + suggestion + "\u00a77 ?");
        }
    }

    private static boolean isInteger(String raw) {
        if (raw == null || raw.trim().isEmpty()) return false;
        try {
            Integer.parseInt(raw.trim());
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String suggestHelpCategory(String requested) {
        if (requested == null) return null;
        String raw = requested.trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) return null;

        String bestKey = null;
        int bestDistance = Integer.MAX_VALUE;
        for (HelpCategory category : HELP_CATEGORIES) {
            int keyDistance = helpTokenDistance(raw, category.key);
            if (keyDistance < bestDistance) {
                bestDistance = keyDistance;
                bestKey = category.key;
            }
            int titleDistance = helpTokenDistance(raw, category.title.toLowerCase(Locale.ROOT).replace(" ", ""));
            if (titleDistance < bestDistance) {
                bestDistance = titleDistance;
                bestKey = category.key;
            }
            for (HelpLine line : category.lines) {
                String token = firstCommandToken(line.command);
                if (token == null) continue;
                int commandDistance = helpTokenDistance(raw, token);
                if (commandDistance < bestDistance) {
                    bestDistance = commandDistance;
                    bestKey = category.key;
                }
            }
        }

        int threshold = raw.length() <= 4 ? 1 : (raw.length() <= 8 ? 2 : 3);
        return bestDistance <= threshold ? bestKey : null;
    }

    private static String firstCommandToken(String command) {
        if (command == null || !command.startsWith("/gtbs ")) return null;
        String rest = command.substring("/gtbs ".length()).trim().toLowerCase(Locale.ROOT);
        int space = rest.indexOf(' ');
        return space < 0 ? rest : rest.substring(0, space);
    }

    private static int helpTokenDistance(String raw, String token) {
        if (token == null || token.isEmpty()) return Integer.MAX_VALUE;
        String normalizedToken = token.toLowerCase(Locale.ROOT);
        int distance = levenshtein(raw, normalizedToken);
        if (raw.startsWith(normalizedToken) || normalizedToken.startsWith(raw)) {
            distance = Math.min(distance, Math.abs(raw.length() - normalizedToken.length()));
        }
        return distance;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }

    private static final class HelpCategory {
        final String key;
        final String title;
        final String summary;
        final HelpLine[] lines;

        HelpCategory(String key, String title, String summary, HelpLine[] lines) {
            this.key = key;
            this.title = title;
            this.summary = summary;
            this.lines = lines;
        }
    }

    private static final class HelpLine {
        final String command;
        final String description;

        private HelpLine(String command, String description) {
            this.command = command;
            this.description = description;
        }

        static HelpLine command(String command, String description) {
            return new HelpLine(command, description);
        }

        static HelpLine note(String description) {
            return new HelpLine(null, description);
        }
    }

    private static void joinGtb(ICommandSender sender) {
        sendServerCommand(PLAY_GTB_COMMAND);
    }

    private static void leaveRejoin(ICommandSender sender) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) return;
        pendingLrjWorld = mc.theWorld;
        pendingLrjStableWorldTicks = 0;
        mc.thePlayer.sendChatMessage(LOBBY_BB_COMMAND);
        pendingLrjTicks = 0;
    }

    public static void onClientTick() {
        if (pendingLrjTicks < 0) return;
        pendingLrjTicks++;
        if (pendingLrjTicks > LRJ_TIMEOUT_TICKS) {
            pendingLrjTicks = -1;
            pendingLrjWorld = null;
            pendingLrjStableWorldTicks = 0;
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            pendingLrjStableWorldTicks = 0;
            return;
        }
        if (pendingLrjWorld != null && mc.theWorld == pendingLrjWorld) {
            pendingLrjStableWorldTicks = 0;
            return;
        }
        if (mc.thePlayer.ticksExisted < LRJ_STABLE_WORLD_TICKS) {
            pendingLrjStableWorldTicks = 0;
            return;
        }
        pendingLrjStableWorldTicks++;
        if (pendingLrjStableWorldTicks < LRJ_STABLE_WORLD_TICKS) return;
        pendingLrjTicks = -1;
        pendingLrjWorld = null;
        pendingLrjStableWorldTicks = 0;
        sendServerCommand(REJOIN_COMMAND);
    }

    private static void sendServerCommand(String command) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || command == null || command.isEmpty()) return;
        mc.thePlayer.sendChatMessage(command);
    }

    private static String formatHaveIBuilt() {
        GameStats gs = GameStats.INSTANCE;
        GameStats.TrackingState st = gs.getState();
        if (st == GameStats.TrackingState.NOT_IN_GAME || st == GameStats.TrackingState.LOBBY) {
            return "\u00a77You are currently not in a game";
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return "\u00a77You are currently not in a game";
        }
        String me = mc.thePlayer.getName();

        java.util.List<Integer> roundsBuilt = new java.util.ArrayList<Integer>();
        for (GameStats.RoundData r : gs.getRounds()) {
            if (r.builder == null || !r.builder.equalsIgnoreCase(me)) continue;
            // For the current round, only count it once we've actually started building.
            if (r == gs.getCurrentRound() && st == GameStats.TrackingState.ROUND_PRE) continue;
            if (r.roundNumber > 0) roundsBuilt.add(r.roundNumber);
        }

        if (roundsBuilt.isEmpty()) {
            return "\u00a77No, you have not built yet";
        }
        StringBuilder sb = new StringBuilder("\u00a77Yes, you have built on \u00a7fRound ");
        for (int i = 0; i < roundsBuilt.size(); i++) {
            if (i > 0) sb.append("\u00a77, \u00a7f");
            sb.append(roundsBuilt.get(i));
        }
        return sb.toString();
    }

    /** "/gtbs whohasbuilt" — list everyone who has built (or is about to build) this game. */
    private static String formatWhoHasBuilt() {
        GameStats gs = GameStats.INSTANCE;
        GameStats.TrackingState st = gs.getState();
        if (st == GameStats.TrackingState.NOT_IN_GAME || st == GameStats.TrackingState.LOBBY) {
            return "\u00a77You are currently not in a game";
        }

        // Determine the in-progress builder + verb (if any). Anyone whose round
        // hasn't entered ROUND_END yet is "in progress" — never "has built".
        String pending = null;
        String pendingStatus = null;
        GameStats.RoundData currentRound = gs.getCurrentRound();
        if (currentRound != null && currentRound.builder != null
                && !currentRound.builder.isEmpty()) {
            if (st == GameStats.TrackingState.ROUND_PRE) {
                pending = currentRound.builder;
                pendingStatus = "is about to build";
            } else if (st == GameStats.TrackingState.ROUND_BUILD
                    || st == GameStats.TrackingState.PAUSED) {
                pending = currentRound.builder;
                pendingStatus = "is currently building";
            }
        }

        // Collect everyone who has finished building, in order of first appearance.
        // Dedup by IGN (case-insensitive) so a stray placeholder/rejoin round can't
        // double-list the same builder. Skip the in-progress builder entirely —
        // they belong to `pending`, not "has built".
        java.util.LinkedHashSet<String> built = new java.util.LinkedHashSet<String>();
        java.util.Set<String> seenLower = new java.util.HashSet<String>();
        for (GameStats.RoundData r : gs.getRounds()) {
            if (r.builder == null || r.builder.isEmpty()) continue;
            if (pending != null && r.builder.equalsIgnoreCase(pending)) continue;
            // Current round only counts as "has built" when its build phase has
            // ended (ROUND_END / POST_GAME). Earlier states are filtered above
            // via the `pending` check.
            if (r == currentRound
                    && st != GameStats.TrackingState.ROUND_END
                    && st != GameStats.TrackingState.POST_GAME) {
                continue;
            }
            String key = r.builder.toLowerCase();
            if (seenLower.add(key)) built.add(r.builder);
        }

        if (built.isEmpty() && pending == null) {
            return "\u00a77No one has built yet this game";
        }

        StringBuilder sb = new StringBuilder();
        if (!built.isEmpty()) {
            java.util.List<String> builtList = new java.util.ArrayList<String>(built);
            sb.append(joinNames(builtList));
            sb.append(builtList.size() > 1 ? " \u00a77have built" : " \u00a77has built");
            if (pending != null) {
                sb.append(", \u00a7f").append(pending).append(" \u00a77").append(pendingStatus);
            }
        } else {
            // pending != null here (covered by the early-return above)
            sb.append("\u00a77No one has built yet, \u00a7f").append(pending)
                    .append(" \u00a77").append(pendingStatus);
        }
        return sb.toString();
    }

    /** Join IGNs as "&fA&7, &fB&7, &fC &7and &fD". Single name → "&fA". */
    private static String joinNames(java.util.List<String> names) {
        StringBuilder sb = new StringBuilder();
        int n = names.size();
        for (int i = 0; i < n; i++) {
            if (i == 0) {
                sb.append("\u00a7f").append(names.get(i));
            } else if (i == n - 1) {
                sb.append(" \u00a77and \u00a7f").append(names.get(i));
            } else {
                sb.append("\u00a77, \u00a7f").append(names.get(i));
            }
        }
        return sb.toString();
    }

    static CopyTranslationResult buildCopyTranslationMessage(String themeInput, List<String> requestedLangCodes,
                                                             boolean includePrefix) {
        if (!TranslationData.isLoaded()) {
            return CopyTranslationResult.error("Translations are still loading; try again in a few seconds.");
        }
        String english = TranslationData.resolveEnglishTheme(themeInput);
        if (english == null) {
            return CopyTranslationResult.error("Unknown English theme: \u00a7f" + themeInput);
        }

        List<String> langCodes = requestedLangCodes;
        if (langCodes == null || langCodes.isEmpty()) {
            langCodes = allLanguageCodes();
        }
        java.util.LinkedHashSet<String> uniqueTranslations = new java.util.LinkedHashSet<String>();
        for (String code : langCodes) {
            String displayed = TranslationData.getDisplayedTheme(code, english);
            if (displayed != null && !displayed.isEmpty()) {
                uniqueTranslations.add(displayed);
            }
        }
        if (uniqueTranslations.isEmpty()) {
            return CopyTranslationResult.error("No translations found for: \u00a7f" + english);
        }

        StringBuilder sb = new StringBuilder();
        if (includePrefix) sb.append("The theme is ");
        int i = 0;
        for (String translation : uniqueTranslations) {
            if (i++ > 0) sb.append(", ");
            sb.append(translation);
        }
        return CopyTranslationResult.ok(sb.toString());
    }

    static CopyTranslationRequest parseCopyTranslationRequest(String[] args) {
        if (args == null || args.length == 0) return null;
        String fullTheme = joinArgs(args, 0, args.length);
        if (TranslationData.resolveEnglishTheme(fullTheme) != null) {
            return new CopyTranslationRequest(fullTheme, null, null);
        }
        for (int split = args.length - 1; split >= 1; split--) {
            String themePrefix = joinArgs(args, 0, split);
            if (TranslationData.resolveEnglishTheme(themePrefix) != null) {
                LanguageListParse parsed = parseLanguageList(joinArgs(args, split, args.length));
                if (parsed.ok) {
                    return new CopyTranslationRequest(themePrefix, parsed.codes, null);
                }
                return new CopyTranslationRequest(themePrefix, null,
                        "Unknown language: \u00a7f" + parsed.invalid
                                + "\u00a77. Available codes/names: \u00a7f" + availableLanguageCodes());
            }
        }
        return new CopyTranslationRequest(fullTheme, null, null);
    }

    private static String joinArgs(String[] args, int start, int endExclusive) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < endExclusive; i++) {
            if (i > start) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString().trim();
    }

    private static LanguageListParse parseLanguageList(String raw) {
        if (raw == null || raw.trim().isEmpty()) return LanguageListParse.error(raw);
        String[] pieces = raw.split(",");
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (String piece : pieces) {
            String code = resolveLanguageCode(piece);
            if (code == null) return LanguageListParse.error(piece);
            String key = code.toLowerCase();
            if (seen.add(key)) out.add(code);
        }
        return out.isEmpty() ? LanguageListParse.error(raw) : LanguageListParse.ok(out);
    }

    private static List<String> allLanguageCodes() {
        return LangPatterns.languageCodes();
    }

    private static String availableLanguageCodes() {
        StringBuilder sb = new StringBuilder();
        List<String> codes = allLanguageCodes();
        for (int i = 0; i < codes.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(codes.get(i));
        }
        return sb.toString();
    }

    private static String resolveLanguageCode(String raw) {
        if (raw == null) return null;
        String token = raw.trim();
        if (token.isEmpty()) return null;
        String lower = token.toLowerCase(Locale.ROOT);
        if (lower.equals("jp")) lower = "ja";
        if (lower.equals("ptbr") || lower.equals("pt-br") || lower.equals("br")) lower = "pt_br";
        if (lower.equals("zhcn") || lower.equals("zh-cn") || lower.equals("cn")) lower = "zh_cn";
        if (lower.equals("zhtw") || lower.equals("zh-tw") || lower.equals("tw")) lower = "zh_tw";
        String compact = compactLanguageName(lower);
        if (compact.equals("portuguesebr") || compact.equals("portuguesebrazil")
                || compact.equals("brazilianportuguese")) lower = "pt_br";
        if (compact.equals("chinesesimplified") || compact.equals("simplifiedchinese")) lower = "zh_cn";
        if (compact.equals("chinesetraditional") || compact.equals("traditionalchinese")) lower = "zh_tw";
        if (compact.equals("piratespeak")) lower = "pirate";

        String normalized = TranslationData.normalizeLangCode(lower);
        for (String code : LangPatterns.languageCodes()) {
            if (code.equalsIgnoreCase(lower)) return code;
            String codeNorm = TranslationData.normalizeLangCode(code);
            if (codeNorm != null && codeNorm.equalsIgnoreCase(normalized)) return code;
        }

        String byDisplay = LangPatterns.codeFromDisplayName(token);
        if (byDisplay != null) return byDisplay;

        for (String code : LangPatterns.languageCodes()) {
            String displayName = LangPatterns.mapLanguageName(code);
            if (displayName != null && compactLanguageName(displayName).equals(compact)) {
                return code;
            }
        }
        return null;
    }

    private static String compactLanguageName(String raw) {
        if (raw == null) return "";
        String lower = raw.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Boolean parseOptionalBoolean(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toLowerCase();
        if (v.equals("true") || v.equals("on") || v.equals("1") || v.equals("yes")) return Boolean.TRUE;
        if (v.equals("false") || v.equals("off") || v.equals("0") || v.equals("no")) return Boolean.FALSE;
        return null;
    }

    static final class CopyTranslationRequest {
        final String theme;
        final List<String> langCodes;
        final String langError;

        CopyTranslationRequest(String theme, List<String> langCodes, String langError) {
            this.theme = theme;
            this.langCodes = langCodes;
            this.langError = langError;
        }
    }

    static final class LanguageListParse {
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

    static final class CopyTranslationResult {
        final boolean ok;
        final String message;
        final String error;

        private CopyTranslationResult(boolean ok, String message, String error) {
            this.ok = ok;
            this.message = message;
            this.error = error;
        }

        static CopyTranslationResult ok(String message) {
            return new CopyTranslationResult(true, message, null);
        }

        static CopyTranslationResult error(String error) {
            return new CopyTranslationResult(false, null, error);
        }
    }

    public static final class CommandCopyTranslation extends CommandBase {
        @Override
        public String getCommandName() {
            return "copytranslation";
        }

        @Override
        public List<String> getCommandAliases() {
            return Arrays.asList("copyt");
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/copytranslation <theme> [langs]";
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            CommandGtb.handleCopyTranslation(sender, args);
        }

        @Override
        public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
            if (args.length == 1) {
                return getListOfStringsMatchingLastWord(args, "prefix");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("prefix")) {
                return getListOfStringsMatchingLastWord(args, "true", "false");
            }
            return null;
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }
    }

    public static final class CommandTrollMode extends CommandBase {
        @Override
        public String getCommandName() {
            return "trollmode";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/trollmode [on|off|autodisable [on|off]]";
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            CommandGtb.handleTrollMode(sender, args);
        }

        @Override
        public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
            return new CommandGtb().addTabCompletionOptions(sender, prefixedArgs("trollmode", args), pos);
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }
    }

    public static final class CommandJoinGtb extends CommandBase {
        @Override
        public String getCommandName() {
            return "gtb";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/gtb";
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if (args.length != 0) {
                sender.addChatMessage(new ChatComponentText(head + "Usage: /gtb"));
                return;
            }
            joinGtb(sender);
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }
    }

    public static final class CommandLeaveRejoin extends CommandBase {
        @Override
        public String getCommandName() {
            return "lrj";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/lrj";
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            leaveRejoin(sender);
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

}
