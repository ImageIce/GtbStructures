package com.gtbstructures.commands;

import com.gtbstructures.chat.ChatHeader;
import com.gtbstructures.gtw.GtwLogic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Platform-neutral /gtbs command help and tab-completion core. */
public class CommandGtb {
    public static String head = ChatHeader.prefix();
    public static String colorBrackets = "\u00a76";
    public static String colorNumbers = "\u00a7e";
    public static String colorHyphens = "\u00a77";
    public static String chatCooldownBarColor = "\u00a7e";
    public static String chatCooldownTimerColor = "\u00a7f";

    public static void refreshHeader() {
        head = ChatHeader.prefix();
    }

    private static final String[] SUBCOMMANDS = {
        "hintstructure", "hs", "toggle", "setlocation", "setcolor", "colorlist", "stats", "export", "exportraw", "tracking", "autorequeue", "updatenotice",
        "haveibuilt", "whohasbuilt", "showallmatching", "themepointreveal", "shortcutreminder", "copytranslation", "gtw", "guess", "scoreboard",
        "spawnegg", "handhelditem", "environment", "env", "cooldowncounter", "guesspeek", "scrambleguess", "trollmode", "header", "preview", "overlay", "gtb", "lrj", "help"
    };
    private static final int HELP_LINES_PER_PAGE = 8;

    public List<String> addTabCompletionOptions(Object sender, String[] args, Object pos) {
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
                return getListOfStringsMatchingLastWord(args, "setlocation", "max", "background",
                        "captions", "layout", "vertical", "horizontal", "size");
            }
            if (isEnvironmentCommand(args[0])) {
                return getListOfStringsMatchingLastWord(args, "setlocation", "weather", "biome", "time",
                        "chatnotice", "timepreset", "background");
            }
            if (args[0].equalsIgnoreCase("cooldowncounter")) {
                return getListOfStringsMatchingLastWord(args, "setlocation", "setcolor", "timer", "bar", "background");
            }
            if (args[0].equalsIgnoreCase("guesspeek")) {
                return getListOfStringsMatchingLastWord(args, "showlangcodes", "display");
            }
            if (args[0].equalsIgnoreCase("scrambleguess")) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "true", "false", "fancy");
            }
            if (args[0].equalsIgnoreCase("trollmode")) {
                return getListOfStringsMatchingLastWord(args, "autodisable", "on", "off", "true", "false");
            }
            if (args[0].equalsIgnoreCase("updatenotice")) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "toggle");
            }
            if (args[0].equalsIgnoreCase("font")) {
                return Collections.emptyList();
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
        if (args.length == 3 && args[0].equalsIgnoreCase("trollmode")
                && args[1].equalsIgnoreCase("autodisable")) {
            return getListOfStringsMatchingLastWord(args, "on", "off", "true", "false");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("help")) {
            return getListOfStringsMatchingLastWord(args, "1", "2", "3", "4", "5");
        }
        if (args[0].equalsIgnoreCase("gtw")) {
            String[] sub = sliceFrom(args, 1);
            return tabGtw(sub);
        }
        if (args[0].equalsIgnoreCase("guess")) {
            String[] sub = sliceFrom(args, 1);
            return tabGuess(sub);
        }
        if (args[0].equalsIgnoreCase("scoreboard")) {
            if (args.length == 2) {
                return getListOfStringsMatchingLastWord(args,
                        "skin", "vanilla", "default", "hypixel",
                        "backgroundopacity", "highlightintensity",
                        "showpoints", "showplaces", "showtitles", "showemblems",
                        "leftlast", "textshadow");
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
            if (args.length == 4 && args[1].equalsIgnoreCase("captions")) {
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
                if ("left".equalsIgnoreCase(args[2]) || "right".equalsIgnoreCase(args[2])) {
                    return getListOfStringsMatchingLastWord(args, "top", "bottom");
                }
                if ("top".equalsIgnoreCase(args[2]) || "bottom".equalsIgnoreCase(args[2])) {
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
            if (args.length == 3 && isEnvironmentPart(args[1])) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "true", "false", "toggle");
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("chatnotice")) {
                return getListOfStringsMatchingLastWord(args, "weather", "biome", "time");
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("chatnotice")
                    && isEnvironmentPart(args[2])) {
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
                return getListOfStringsMatchingLastWord(args, "on", "off", "true", "false");
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

    public static String locationStatusMessageForTest(String label, String axis, float value) {
        return locationStatusMessage(label, axis, value);
    }

    private static String locationStatusMessage(String label, String axis, float value) {
        return label + " " + axis.toUpperCase(Locale.ROOT) + " position is currently \u00a7f" + value;
    }

    public static String invalidColorMessageForTest() {
        return invalidColorMessage();
    }

    private static String invalidColorMessage() {
        return "Color must be a single character 0-9 or a-f. Use \u00a7f/gtbs colorlist\u00a77.";
    }

    public static String currentHintStructureColorMessageForTest(String target) {
        if ("bracket".equals(target)) return "Bracket color is currently " + colorCodeLabel(colorBrackets, "\u00a76");
        if ("number".equals(target)) return "Number color is currently " + colorCodeLabel(colorNumbers, "\u00a7e");
        if ("hyphen".equals(target)) return "Hyphen color is currently " + colorCodeLabel(colorHyphens, "\u00a77");
        return null;
    }

    public static String currentCooldownColorMessageForTest(String target) {
        if ("bar".equals(target)) return "Cooldown bar color is currently " + colorCodeLabel(chatCooldownBarColor, "\u00a7e");
        if ("timer".equals(target)) return "Cooldown timer color is currently " + colorCodeLabel(chatCooldownTimerColor, "\u00a7f");
        return null;
    }

    private static String colorCodeLabel(String color, String fallback) {
        String normalized = normalizeMinecraftColorCode(color, fallback);
        return normalized + normalized.substring(1);
    }

    private static String normalizeMinecraftColorCode(String raw, String fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        String value = raw.trim();
        if (value.length() == 1) value = "\u00a7" + value;
        if (value.length() == 2 && value.charAt(0) == '\u00a7') {
            char c = Character.toLowerCase(value.charAt(1));
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) return "\u00a7" + c;
        }
        return fallback;
    }

    public static String displaySpawnEggSkinForTest(String skin) {
        return displaySpawnEggSkin(skin);
    }

    private static String displaySpawnEggSkin(String skin) {
        return "new".equalsIgnoreCase(skin) ? "New" : "Classic";
    }

    public static String displayEnvironmentTimePresetForTest(String preset) {
        return displayEnvironmentTimePreset(preset);
    }

    private static String displayEnvironmentTimePreset(String preset) {
        return "blocks".equalsIgnoreCase(preset) ? "Blocks" : "Clock";
    }

    public static String backgroundStatusMessageForTest(String label, boolean enabled) {
        return backgroundStatusMessage(label, enabled);
    }

    private static String backgroundStatusMessage(String label, boolean enabled) {
        return label + " is currently " + (enabled ? "\u00a7aON" : "\u00a7cOFF");
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

    private static boolean isCooldownCounterPart(String value) {
        return value != null && (value.equalsIgnoreCase("timer") || value.equalsIgnoreCase("bar"));
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
        return "leftlast".equals(key) || "textshadow".equals(key);
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
        if (key.equals("leftlast")) return "leftlast";
        if (key.equals("shadow") || key.equals("textshadow")) return "textshadow";
        return key;
    }

    private static List<String> getListOfStringsMatchingLastWord(String[] args, String... options) {
        String prefix = args == null || args.length == 0 ? "" : args[args.length - 1];
        ArrayList<String> out = new ArrayList<String>();
        String normalizedPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) out.add(option);
        }
        return out;
    }

    private static List<String> tabGtw(String[] args) {
        if (args == null || args.length == 0) return Arrays.asList("start", "stop", "showenglish");
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "start", "stop", "showenglish");
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && sub.equals("start")) {
            List<String> options = GtwLogic.languageCompletionOptions();
            return getListOfStringsMatchingLastWord(args, options.toArray(new String[options.size()]));
        }
        if (args.length == 2 && sub.equals("showenglish")) {
            return getListOfStringsMatchingLastWord(args, "true", "false");
        }
        return null;
    }

    private static List<String> tabGuess(String[] args) {
        return Collections.emptyList();
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
                    HelpLine.command("/gtbs updatenotice [on|off|toggle]", "Controls the update notice shown after the first server join of each startup."),
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
        int pageSize = pageSize(category);
        int pageCount = pageCount(category.lines, pageSize);
        int page = Math.max(1, Math.min(requestedPage, pageCount));
        boolean balanced = "all".equals(category.key);
        int start = balanced
                ? firstLineForPage(category.lines.length, pageCount, page)
                : (page - 1) * pageSize;
        int end = balanced
                ? start + lineCountForPage(category.lines.length, pageCount, page)
                : Math.min(category.lines.length, start + pageSize);

        out.add(helpBanner());
        out.add("\u00a76Category: \u00a77" + category.title + "\u00a76, Page: \u00a77" + page + "/" + pageCount);
        for (int i = start; i < end; i++) {
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

    private static int pageSize(HelpCategory category) {
        return HELP_LINES_PER_PAGE;
    }

    private static int pageCount(HelpLine[] lines, int pageSize) {
        return Math.max(1, (lines.length + pageSize - 1) / pageSize);
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
}
