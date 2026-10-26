package com.gtbstructures.commands;

import com.gtbstructures.chat.ChatHeader;
import com.gtbstructures.sound.GuessSounds;
import com.gtbstructures.sound.MinecraftClientSoundBridge;
import com.gtbstructures.gtw.GtwLogic;
import com.gtbstructures.translation.ShortcutLanguageGroups;

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
        "spawnegg", "handhelditem", "environment", "env", "cooldowncounter", "guesspeek", "scrambleguess", "trollmode", "troll", "guesssound", "header", "preview", "overlay", "gtb", "lrj", "fireworks", "help", "search"
    };
    private static final int HELP_LINES_PER_PAGE = 8;

    public List<String> addTabCompletionOptions(Object sender, String[] args, Object pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("help")) {
                return getListOfStringsMatchingLastWord(args,
                        "tracking", "hints", "scoreboard", "items", "environment", "game", "chat", "experimental", "copy", "gtw", "all", "search");
            }
            if (args[0].equalsIgnoreCase("stats")) {
                return getListOfStringsMatchingLastWord(args, "session", "theme");
            }
            if (isHintStructureCommand(args[0])) {
                return getListOfStringsMatchingLastWord(args,
                        "setlocation", "setcolor", "background", "scale");
            }
            if (args[0].equalsIgnoreCase("setlocation")) {
                return getListOfStringsMatchingLastWord(args, "x", "y");
            }
            if (args[0].equalsIgnoreCase("setcolor")) {
                return getListOfStringsMatchingLastWord(args, "bracket", "number", "hyphen");
            }
            if (args[0].equalsIgnoreCase("showallmatching")) {
                return getListOfStringsMatchingLastWord(args, "showenglish", "showcount");
            }
            if (args[0].equalsIgnoreCase("autorequeue")) {
                return getListOfStringsMatchingLastWord(args, "delay");
            }
            if (args[0].equalsIgnoreCase("fireworks")) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "toggle");
            }
            if (args[0].equalsIgnoreCase("themepointreveal")) {
                return getListOfStringsMatchingLastWord(args, "showafterguess");
            }
            if (args[0].equalsIgnoreCase("shortcutreminder")) {
                return getListOfStringsMatchingLastWord(args,
                        "shownonascii", "showallshortcuts", "showmultiwords",
                        "languagegroups");
            }
            if (args[0].equalsIgnoreCase("copytranslation")) {
                return getListOfStringsMatchingLastWord(args, "caps", "prefix");
            }
            if (args[0].equalsIgnoreCase("spawnegg")) {
                return getListOfStringsMatchingLastWord(args, "classic", "new");
            }
            if (args[0].equalsIgnoreCase("handhelditem")) {
                return getListOfStringsMatchingLastWord(args, "setlocation", "max", "background",
                        "captions", "layout", "vertical", "horizontal", "size", "scale");
            }
            if (isEnvironmentCommand(args[0])) {
                return getListOfStringsMatchingLastWord(args, "setlocation", "weather", "biome", "time",
                        "timepreset", "background", "chatnotice", "scale");
            }
            if (args[0].equalsIgnoreCase("cooldowncounter")) {
                return getListOfStringsMatchingLastWord(args, "setlocation", "setcolor", "timer", "bar", "sfx", "background", "scale");
            }
            if (args[0].equalsIgnoreCase("guesspeek")) {
                return getListOfStringsMatchingLastWord(args, "showlangcodes", "display");
            }
            if (args[0].equalsIgnoreCase("guesssound")) {
                return getListOfStringsMatchingLastWord(args, "1", "2", "3", "volume");
            }
            if (args[0].equalsIgnoreCase("scrambleguess")) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "true", "false", "fancy");
            }
            if (args[0].equalsIgnoreCase("trollmode")) {
                return getListOfStringsMatchingLastWord(args, "autodisable", "on", "off", "true", "false");
            }
            if (args[0].equalsIgnoreCase("troll")) {
                return Collections.emptyList();
            }
            if (args[0].equalsIgnoreCase("lrj")) {
                return getListOfStringsMatchingLastWord(args, "bypassprotection", "delay");
            }
            if (args[0].equalsIgnoreCase("updatenotice")) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "toggle");
            }
            if (args[0].equalsIgnoreCase("font")) {
                return Collections.emptyList();
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("guesssound")) {
            if (args[1].equalsIgnoreCase("volume")) return getListOfStringsMatchingLastWord(args, "0.0", "20.0", "30.0");
            if (args[1].equals("1") || args[1].equals("2") || args[1].equals("3")) {
                MinecraftClientSoundBridge.ensureCatalog();
                List<String> sounds = new ArrayList<>();
                sounds.add(GuessSounds.NONE); sounds.add(GuessSounds.RANDOM);
                for (GuessSounds.Entry entry : GuessSounds.catalog().entries()) sounds.add(entry.id);
                return getListOfStringsMatchingLastWord(args, sounds.toArray(new String[0]));
            }
        }
        if (isHintStructureCommand(args[0])) {
            if (args.length == 3 && args[1].equalsIgnoreCase("scale")) {
                return panelScaleSuggestions(args);
            }
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
        if (args.length == 3 && args[0].equalsIgnoreCase("showallmatching")
                && args[1].equalsIgnoreCase("showcount")) {
            return getListOfStringsMatchingLastWord(args, "on", "off");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("autorequeue")
                && args[1].equalsIgnoreCase("delay")) {
            return getListOfStringsMatchingLastWord(args, "0.0", "2.5", "5.0", "7.5", "10.0");
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
            if (args[1].equalsIgnoreCase("languagegroups")) {
                return getListOfStringsMatchingLastWord(args,
                        "on", "off", "list", "create",
                        "enable", "rawtranslation", "lowercase",
                        "rename", "languages", "delete");
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
        if (args.length == 4 && args[0].equalsIgnoreCase("shortcutreminder")
                && args[1].equalsIgnoreCase("languagegroups")) {
            if (isLanguageGroupIndexOperation(args[2])) {
                return getListOfStringsMatchingLastWord(args, languageGroupNumbers());
            }
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("shortcutreminder")
                && args[1].equalsIgnoreCase("languagegroups")
                && (args[2].equalsIgnoreCase("enable")
                || args[2].equalsIgnoreCase("rawtranslation")
                || args[2].equalsIgnoreCase("lowercase"))) {
            return getListOfStringsMatchingLastWord(args, "on", "off");
        }
        if (args.length >= 5 && args[0].equalsIgnoreCase("shortcutreminder")
                && args[1].equalsIgnoreCase("languagegroups")
                && (args[2].equalsIgnoreCase("create")
                || args[2].equalsIgnoreCase("languages"))) {
            return languageListCompletions(args[args.length - 1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("lrj")
                && args[1].equalsIgnoreCase("bypassprotection")) {
            return getListOfStringsMatchingLastWord(args, "on", "off");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("lrj")
                && args[1].equalsIgnoreCase("delay")) {
            return getListOfStringsMatchingLastWord(args, "0.0", "5.0", "10.0");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("copytranslation")
                && args[1].equalsIgnoreCase("prefix")) {
            return getListOfStringsMatchingLastWord(args, "true", "false");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("copytranslation")
                && args[1].equalsIgnoreCase("caps")) {
            return getListOfStringsMatchingLastWord(args, "on", "off");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("trollmode")
                && args[1].equalsIgnoreCase("autodisable")) {
            return getListOfStringsMatchingLastWord(args, "on", "off", "true", "false");
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("help")
                && args[1].equalsIgnoreCase("search")) {
            String last = args[args.length - 1];
            return last.isEmpty() || last.startsWith("-")
                    ? getListOfStringsMatchingLastWord(args, "--page")
                    : Collections.<String>emptyList();
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("search")) {
            String last = args[args.length - 1];
            return last.isEmpty() || last.startsWith("-")
                    ? getListOfStringsMatchingLastWord(args, "--page")
                    : Collections.<String>emptyList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("help")) {
            return getListOfStringsMatchingLastWord(args, "1", "2", "3", "4", "5", "search");
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
                        "showbuilders", "showrounds", "textshadow", "leftlast");
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
            if (args.length == 3 && args[1].equalsIgnoreCase("scale")) {
                return panelScaleSuggestions(args);
            }
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
            if (args.length == 3 && args[1].equalsIgnoreCase("scale")) {
                return panelScaleSuggestions(args);
            }
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
            if (args.length == 3 && args[1].equalsIgnoreCase("sfx")) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "true", "false", "toggle", "progressbeats");
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("sfx")
                    && args[2].equalsIgnoreCase("progressbeats")) {
                return getListOfStringsMatchingLastWord(args, "on", "off", "true", "false", "toggle");
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("scale")) {
                return panelScaleSuggestions(args);
            }
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

    private static List<String> panelScaleSuggestions(String[] args) {
        return getListOfStringsMatchingLastWord(args,
                "0.5", "0.75", "1", "1.25", "1.5", "50%", "100%", "150%");
    }

    private static boolean isLanguageGroupIndexOperation(String raw) {
        return raw != null && (raw.equalsIgnoreCase("enable")
                || raw.equalsIgnoreCase("rawtranslation")
                || raw.equalsIgnoreCase("lowercase")
                || raw.equalsIgnoreCase("rename")
                || raw.equalsIgnoreCase("languages")
                || raw.equalsIgnoreCase("delete"));
    }

    private static String[] languageGroupNumbers() {
        String[] numbers = new String[ShortcutLanguageGroups.groups().size()];
        for (int i = 0; i < numbers.length; i++) numbers[i] = Integer.toString(i + 1);
        return numbers;
    }

    private static List<String> languageListCompletions(String raw) {
        String value = raw == null ? "" : raw;
        int comma = value.lastIndexOf(',');
        String prefix = comma < 0 ? "" : value.substring(0, comma + 1);
        String fragment = comma < 0 ? value : value.substring(comma + 1);
        List<String> matches = new ArrayList<String>();
        for (String code : ShortcutLanguageGroups.supportedLanguageCodes()) {
            if (code.regionMatches(true, 0, fragment, 0, fragment.length())) {
                matches.add(prefix + code);
            }
        }
        return matches;
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
        if (args == null || args.length == 0) return Arrays.asList("start", "stop", "showenglish", "showcount", "difficulty");
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "start", "stop", "showenglish", "showcount", "difficulty");
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && sub.equals("start")) {
            List<String> options = GtwLogic.languageCompletionOptions();
            return getListOfStringsMatchingLastWord(args, options.toArray(new String[options.size()]));
        }
        if (args.length == 2 && sub.equals("showenglish")) {
            return getListOfStringsMatchingLastWord(args, "true", "false");
        }
        if (args.length == 2 && sub.equals("showcount")) {
            return getListOfStringsMatchingLastWord(args, "on", "off");
        }
        if (args.length == 2 && sub.equals("difficulty")) {
            return getListOfStringsMatchingLastWord(args, "easy", "medium", "hard", "all");
        }
        return null;
    }

    private static List<String> tabGuess(String[] args) {
        return Collections.emptyList();
    }
    private static final HelpCategory[] HELP_CATEGORIES = new HelpCategory[] {
            new HelpCategory("tracking", "TRACKING", "Stats tracking and exports", new HelpLine[] {
                    HelpLine.command("/gtbs stats session [days]", "Summarizes the current player's recorded sessions."),
                    HelpLine.command("/gtbs stats theme [entries|all]", "Summarizes recorded theme occurrences for the current player; entries is a positive result limit, and all includes every local player."),
                    HelpLine.command("/gtbs stats theme [all] export", "Exports the theme-occurrence report for the current player or every local player."),
                    HelpLine.command("/gtbs export", "Exports the current game's stats to a file."),
                    HelpLine.command("/gtbs exportraw", "Exports recorded raw packet diagnostics."),
                    HelpLine.command("/gtbs tracking", "Toggles stats tracking."),
                    HelpLine.note("Tracks GTB rounds, builders, guesses, themes, scores, rejoin gaps, and final leaderboards."),
                    HelpLine.note("Output folder: .minecraft/config/gtbstats/")
            }),
            new HelpCategory("hints", "HINTS", "Hint structure and matching-theme output settings", new HelpLine[] {
                    HelpLine.command("/gtbs hintstructure", "Toggles the compact hint structure overlay. Shortcut: /gtbs hs."),
                    HelpLine.command("/gtbs hintstructure background [on|off|toggle]", "Shows or sets the hint background. Shortcut: /gtbs overlay [on|off|toggle]; without a value, the shortcut toggles it."),
                    HelpLine.command("/gtbs hintstructure scale [0.5-1.5|50%-150%]", "Shows or sets the hint panel scale."),
                    HelpLine.command("/gtbs hintstructure setlocation <x|y> [value]", "Shows or sets overlay position. Value is 0.0-1.0."),
                    HelpLine.command("/gtbs hintstructure setcolor <bracket|number|hyphen> [color]", "Shows or sets overlay bracket/number/hyphen color."),
                    HelpLine.command("/gtbs colorlist", "Lists shared Minecraft color codes."),
                    HelpLine.command("/gtbs showallmatching", "Toggles showing every theme that matches the first revealed hint after the theme is revealed."),
                    HelpLine.command("/gtbs showallmatching showenglish [on|off]", "Toggles or sets whether English source names are included in matching-theme output."),
                    HelpLine.command("/gtbs showallmatching showcount [on|off]", "Adds or removes the number of displayed matching themes.")
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
                    HelpLine.command("/gtbs scoreboard skin [vanilla|default|hypixel]", "Shows or switches the scoreboard skin; the skin name also works without skin."),
                    HelpLine.command("/gtbs scoreboard <backgroundopacity|highlightintensity> [0-1]", "Shows or sets scoreboard background opacity or highlight intensity."),
                    HelpLine.command("/gtbs scoreboard showpoints [off|expanded|on]", "Controls current-round score deltas."),
                    HelpLine.command("/gtbs scoreboard showplaces [off|expanded|on]", "Controls leaderboard place numbers."),
                    HelpLine.command("/gtbs scoreboard showtitles [off|expanded|on]", "Controls Build Battle titles."),
                    HelpLine.command("/gtbs scoreboard showemblems [off|expanded|on]", "Controls player emblems."),
                    HelpLine.command("/gtbs scoreboard showbuilders [on|off|toggle]", "Controls the explicit current-builder row in the Default skin."),
                    HelpLine.command("/gtbs scoreboard showrounds [off|expanded|on]", "Controls round progress beside the Builder row in the Hypixel skin."),
                    HelpLine.command("/gtbs scoreboard textshadow [on|off|toggle]", "Shows or hides custom scoreboard text shadow."),
                    HelpLine.command("/gtbs scoreboard leftlast [on|off|toggle]", "Places confirmed leavers at the bottom of custom scoreboards.")
            }),
            new HelpCategory("items", "ITEMS", "Handheld item overlay and item settings", new HelpLine[] {
                    HelpLine.command("/gtbs handhelditem", "Toggles the builder handheld item history overlay."),
                    HelpLine.command("/gtbs handhelditem background [on|off|toggle]", "Shows or hides the handheld item list background."),
                    HelpLine.command("/gtbs handhelditem scale [0.5-1.5|50%-150%]", "Shows or sets the handheld item panel scale."),
                    HelpLine.command("/gtbs handhelditem captions <name|id|metadata|mob> [on|off]", "Shows or sets an item caption. Captions appear only in vertical layout."),
                    HelpLine.command("/gtbs handhelditem layout [vertical|horizontal]", "Switches between captioned vertical items and an icon-only horizontal grid."),
                    HelpLine.command("/gtbs handhelditem vertical [top|bottom]", "Places newly held items at the top or bottom in vertical layout."),
                    HelpLine.command("/gtbs handhelditem horizontal [left|right] [top|bottom]", "Places newly held icons along each axis in horizontal layout."),
                    HelpLine.command("/gtbs handhelditem size <width|height> [value]", "Shows or sets the resizable panel bounds as a 0.0-1.0 scaled-screen ratio."),
                    HelpLine.command("/gtbs handhelditem setlocation <x|y> [value]", "Shows or sets the position of the handheld overlay. Value is 0.0-1.0."),
                    HelpLine.command("/gtbs handhelditem max [rows]", "Shows or sets visible rows of items on the overlay. Accepted value: 2-10."),
                    HelpLine.command("/gtbs spawnegg [classic|new]", "Shows or sets the appearance of spawn egg icons."),
                    HelpLine.note("classic: Uses the classic spawn egg appearance."),
                    HelpLine.note("new: Uses the modern spawn egg appearance.")
            }),
            new HelpCategory("environment", "ENVIRONMENT", "Weather, biome, and time labels", new HelpLine[] {
                    HelpLine.command("/gtbs environment", "Toggles the weather, biome, and time overlay. Shortcut: /gtbs env."),
                    HelpLine.command("/gtbs environment background [on|off|toggle]", "Shows or hides the environment panel background."),
                    HelpLine.command("/gtbs environment scale [0.5-1.5|50%-150%]", "Shows or sets the environment panel scale."),
                    HelpLine.command("/gtbs environment <weather|biome|time> [on|off|toggle]", "Shows or hides an environment row."),
                    HelpLine.command("/gtbs environment chatnotice <weather|biome|time> [on|off|toggle]", "Shows or controls chat notices when an environment value changes."),
                    HelpLine.command("/gtbs environment timepreset [clock|blocks]", "Shows or switches the time icon between clock and colored blocks; each block represents a 3-hour period."),
                    HelpLine.command("/gtbs environment setlocation <x|y> [value]", "Shows or sets the environment overlay bottom position. Value is 0.0-1.0.")
            }),
            new HelpCategory("chat", "CHAT", "Cooldown counter and guess annotations", new HelpLine[] {
                    HelpLine.command("/gtbs header <content>", "Sets the formatting-aware prefix shown before GTBS notices."),
                    HelpLine.command("/gtbs cooldowncounter", "Toggles the chat cooldown indicator while guessing."),
                    HelpLine.command("/gtbs cooldowncounter scale [0.5-1.5|50%-150%]", "Shows or sets the cooldown panel scale."),
                    HelpLine.command("/gtbs cooldowncounter <timer|bar> [on|off|toggle]", "Shows or hides the cooldown timer text or progress bar."),
                    HelpLine.command("/gtbs cooldowncounter sfx [on|off|toggle]", "Shows or sets the cooldown-ready sound (default ON)."),
                    HelpLine.command("/gtbs cooldowncounter sfx progressbeats [on|off|toggle]", "Shows or sets quieter beats at the elapsed 1s and 2s marks (default OFF; requires SFX)."),
                    HelpLine.command("/gtbs cooldowncounter background <timer|bar> [on|off|toggle]", "Shows or hides timer or bar backgrounds."),
                    HelpLine.command("/gtbs cooldowncounter setcolor <bar|timer> [color]", "Shows or sets cooldown bar or timer text color."),
                    HelpLine.command("/gtbs cooldowncounter setlocation <x|y> [value]", "Shows or sets cooldown counter position. Value is 0.0-1.0."),
                    HelpLine.command("/gtbs guesspeek", "Toggles English-theme annotations on matching GTB guesses."),
                    HelpLine.command("/gtbs guesspeek display [hover|suffix]", "Shows or sets where guess-peek translations appear."),
                    HelpLine.command("/gtbs guesspeek showlangcodes [on|off]", "Shows or hides source language codes in guess-peek hover text."),
                    HelpLine.command("/gtbs themepointreveal", "Toggles chat notices showing theme point values."),
                    HelpLine.command("/gtbs themepointreveal showafterguess <on|off>", "Controls whether a point-value notice may appear after a correct guess."),
                    HelpLine.command("/gtbs shortcutreminder", "Toggles shortcut reminders after themes are revealed. Shortcuts: /shortcutreminder, /screminder."),
                    HelpLine.command("/gtbs shortcutreminder shownonascii [on|off|toggle]", "Shows or hides enabled non-ASCII shortcut categories."),
                    HelpLine.command("/gtbs shortcutreminder shownonascii <extendedlatin|cyrillic|hanzi|japanese|hangul> [on|off|toggle]", "Customizes shown non-ASCII shortcut categories."),
                    HelpLine.command("/gtbs shortcutreminder showallshortcuts [on|off|toggle]", "Shows every shortest shortcut instead of only the selected shortcut."),
                    HelpLine.command("/gtbs shortcutreminder showmultiwords [on|off|toggle]", "Shows or hides multiword reminders after shortcut reminders."),
                    HelpLine.command("/gtbs shortcutreminder showmultiwords showall [on|off|toggle]", "Shows every accepted multiword per theme set instead of only the shortest one."),
                    HelpLine.command("/gtbs shortcutreminder languagegroups [on|off]", "Controls independent shortest shortcuts from configured language groups."),
                    HelpLine.command("/gtbs shortcutreminder languagegroups list", "Lists configured language groups, states, and selected languages."),
                    HelpLine.command("/gtbs shortcutreminder languagegroups create <name> <langs>", "Creates an enabled group from comma-separated language codes."),
                    HelpLine.command("/gtbs shortcutreminder languagegroups enable <number> [on|off]", "Shows or sets one language group's enabled state."),
                    HelpLine.command("/gtbs shortcutreminder languagegroups rawtranslation <number> [on|off]", "Uses each stored translation unchanged as that group's shortcut candidate."),
                    HelpLine.command("/gtbs shortcutreminder languagegroups lowercase <number> [on|off]", "Lowercases those raw translation candidates; requires rawtranslation."),
                    HelpLine.command("/gtbs shortcutreminder languagegroups rename <number> <name>", "Renames a configured language group."),
                    HelpLine.command("/gtbs shortcutreminder languagegroups languages <number> <langs>", "Replaces one group's comma-separated language selection."),
                    HelpLine.command("/gtbs shortcutreminder languagegroups delete <number>", "Deletes a configured language group.")
            }),
            new HelpCategory("experimental", "EXPERIMENTAL", "Experimental options", new HelpLine[] {
                    HelpLine.command("/gtbs guesssound [1|2|3] [id|none|random]", "Shows or sets the sound for a correct guess by point value."),
                    HelpLine.command("/gtbs guesssound volume [0-30]", "Shows or sets the global correct-guess sound volume."),
                    HelpLine.command("/gtbs scrambleguess [on|off]", "Toggles replacing a valid guess with another valid guess for the same theme."),
                    HelpLine.command("/gtbs scrambleguess fancy [on|off]", "Toggles randomized text changes on the replacement guess."),
                    HelpLine.command("/gtbs trollmode [on|off]", "Toggles whether to change a valid guess into a visually similar invalid guess. Shortcut: /trollmode [on|off]."),
                    HelpLine.command("/gtbs trollmode autodisable [on|off]", "Controls whether Troll Mode turns off when a GTB game starts. Shortcut: /trollmode autodisable [on|off]."),
                    HelpLine.command("/gtbs troll <message>", "Sends one message with the Troll Mode marker. Shortcut: /troll <message>."),
                    HelpLine.command("/gtbs fireworks [on|off|toggle]", "Shows or controls end-game firework sounds.")
            }),
            new HelpCategory("game", "GAME", "Queue controls, builder checks, and preview", new HelpLine[] {
                    HelpLine.command("/gtbs toggle", "Toggles all GTBS features without changing their individual settings."),
                    HelpLine.command("/gtbs gtb", "Joins Guess the Build. Shortcut: /gtb."),
                    HelpLine.command("/gtbs lrj", "Leaves to lobby, then rejoins. Shortcut: /lrj."),
                    HelpLine.command("/gtbs lrj bypassprotection [on|off]", "Retries /lrj when Hypixel requires confirmation before leaving. Shortcut: /lrj bypassprotection [on|off]."),
                    HelpLine.command("/gtbs lrj delay [time]", "Shows or sets the delay after LRJ is ready to rejoin. Accepted value: 0.0-10.0 seconds. Shortcut: /lrj delay [time]."),
                    HelpLine.command("/gtbs preview", "Toggles the session-only GUI for previewing and configuring interface features."),
                    HelpLine.command("/gtbs autorequeue", "Toggles auto-requeue after a game ends."),
                    HelpLine.command("/gtbs autorequeue delay [time]", "Shows or sets the wait before rejoining after a game ends. Accepted value: 0.0-10.0 seconds."),
                    HelpLine.command("/gtbs updatenotice [on|off|toggle]", "Toggles or sets the startup update notice shown after the first multiplayer join."),
                    HelpLine.command("/gtbs haveibuilt", "Reports whether you have built this game."),
                    HelpLine.command("/gtbs whohasbuilt", "Lists players who have built and identifies the current or upcoming builder this game.")
            }),
            new HelpCategory("copy", "COPY", "Copy theme translations", new HelpLine[] {
                    HelpLine.command("/gtbs copytranslation <theme> [langs]", "Copies theme translations. Shortcuts: /copytranslation, /copyt."),
                    HelpLine.note("With no language value, copies translations from every available language."),
                    HelpLine.note("You can use language codes or full names to select desired translations to copy; for multiple languages, use comma to separate. e.g. en,ru; pirate_speak,Chinese_Simplified."),
                    HelpLine.command("/gtbs copytranslation caps [on|off]", "Uses an initial capital when on; lowercases every letter when off."),
                    HelpLine.command("/gtbs copytranslation prefix <true|false>", "Toggles whether to show the copied \"The theme is\" prefix.")
            }),
            new HelpCategory("gtw", "GTW", "Guess The Wordhint mini-game", new HelpLine[] {
                    HelpLine.command("/gtbs gtw start [language]", "Starts a Guess The Wordhint mini-session. Shortcut: /gtw start [language]."),
                    HelpLine.command("/gtbs guess <guess>", "Submits a Guess The Wordhint answer. Shortcut: /guess <guess>."),
                    HelpLine.command("/gtbs gtw stop", "Stops the Guess The Wordhint session and reveals the theme. Shortcut: /gtw stop."),
                    HelpLine.command("/gtbs gtw showenglish [true|false]", "Shows or sets whether GTW reveal and list lines include English names."),
                    HelpLine.command("/gtbs gtw showcount [on|off]", "Shows or sets whether GTW output includes the number of matching themes. Shortcut: /gtw showcount [on|off]."),
                    HelpLine.command("/gtbs gtw difficulty [easy|medium|hard|all]", "Shows or selects GTW themes by their English length-based point value. Shortcut: /gtw difficulty [easy|medium|hard|all].")
            })
    };
    private static final HelpCategory ALL_HELP_CATEGORY = buildAllHelpCategory();
    private static final HelpSearch HELP_SEARCH = buildHelpSearch();

    static List<String> buildHelpMessages(String[] args) {
        return buildHelpOutput(args).messages;
    }

    static HelpOutput buildHelpOutput(String[] args) {
        ArrayList<String> out = new ArrayList<String>();
        if (args == null || args.length == 0) {
            appendHelpIndex(out);
            return new HelpOutput(out, null);
        }

        String requested = args[0];
        if (isInteger(requested)) {
            HelpPageNavigation navigation = appendHelpCategory(out, ALL_HELP_CATEGORY, parseHelpPage(args, 0),
                    "/gtbs help all", 1);
            return new HelpOutput(out, navigation);
        }

        if (requested.equalsIgnoreCase("all")) {
            HelpPageNavigation navigation = appendHelpCategory(out, ALL_HELP_CATEGORY, parseHelpPage(args, 1),
                    "/gtbs help all", 1);
            return new HelpOutput(out, navigation);
        }

        if (requested.equalsIgnoreCase("search")) {
            return buildHelpSearchOutput(args, 1);
        }

        HelpCategory category = findHelpCategory(requested);
        if (category == null) {
            appendUnknownHelpCategory(out, String.join(" ", args));
            return new HelpOutput(out, null);
        }
        HelpPageNavigation navigation = appendHelpCategory(out, category, parseHelpPage(args, 1),
                "/gtbs help " + normalizeHelpCategory(requested), 1);
        return new HelpOutput(out, navigation);
    }

    private static HelpOutput buildHelpSearchOutput(String[] args, int queryStart) {
        int requestedPage = 1;
        int queryEnd = args.length;
        if (args.length - queryStart >= 2 && "--page".equalsIgnoreCase(args[args.length - 2])) {
            try {
                requestedPage = Integer.parseInt(args[args.length - 1]);
                if (requestedPage < 1) throw new NumberFormatException();
            } catch (NumberFormatException invalid) {
                return helpSearchUsage();
            }
            queryEnd -= 2;
        } else if (args.length > queryStart && "--page".equalsIgnoreCase(args[args.length - 1])) {
            return helpSearchUsage();
        }
        StringBuilder queryBuilder = new StringBuilder();
        for (int i = queryStart; i < queryEnd; i++) {
            if (queryBuilder.length() > 0) queryBuilder.append(' ');
            queryBuilder.append(args[i]);
        }
        String query = queryBuilder.toString().trim();
        if (query.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            return helpSearchUsage();
        }
        List<HelpSearch.Entry> matches = HELP_SEARCH.search(query);
        ArrayList<String> out = new ArrayList<String>();
        if (matches.isEmpty()) {
            out.add(head + "No commands matched: \u00a7f" + query);
            out.add("\u00a77Try a command name, a short keyword, or fewer search terms.");
            return new HelpOutput(out, null);
        }
        int pageCount = Math.max(1, (matches.size() + HELP_LINES_PER_PAGE - 1) / HELP_LINES_PER_PAGE);
        int page = Math.max(1, Math.min(requestedPage, pageCount));
        String prefix = "\u00a76Search: \u00a77" + query + "\u00a76, Page: ";
        String suffix = "\u00a76, Matches: \u00a77" + matches.size() + "\u00a78 (most relevant first)";
        out.add(helpBanner());
        out.add(prefix + "\u00a77" + page + "/" + pageCount + suffix);
        int start = firstLineForPage(matches.size(), pageCount, page);
        int end = start + lineCountForPage(matches.size(), pageCount, page);
        for (int i = start; i < end; i++) {
            HelpSearch.Entry entry = matches.get(i);
            appendHelpLine(out, HelpLine.command(entry.usage, entry.description));
        }
        out.add(helpBanner());
        HelpPageNavigation navigation = pageCount <= 1 ? null : new HelpPageNavigation(1, prefix, page, pageCount, suffix,
                page > 1 ? "/gtbs help search " + query + " --page " + (page - 1) : null,
                page < pageCount ? "/gtbs help search " + query + " --page " + (page + 1) : null);
        return new HelpOutput(out, navigation);
    }

    private static HelpOutput helpSearchUsage() {
        ArrayList<String> out = new ArrayList<String>();
        out.add(head + "Usage: /gtbs help search <query> [--page <number>]");
        return new HelpOutput(out, null);
    }

    private static HelpSearch buildHelpSearch() {
        ArrayList<HelpSearch.Entry> entries = new ArrayList<HelpSearch.Entry>();
        for (HelpCategory helpCategory : HELP_CATEGORIES) {
            for (HelpLine line : helpCategory.lines) {
                if (line.command != null) entries.add(new HelpSearch.Entry(line.command, line.description,
                        helpCategory.key, helpCategory.title + " " + helpCategory.summary));
            }
        }
        return new HelpSearch(entries);
    }

    static final class HelpOutput {
        final List<String> messages;
        final HelpPageNavigation navigation;
        HelpOutput(List<String> messages, HelpPageNavigation navigation) {
            this.messages = java.util.Collections.unmodifiableList(new ArrayList<String>(messages));
            this.navigation = navigation;
        }
    }

    private static void appendHelpIndex(List<String> out) {
        out.add(helpBanner());
        for (HelpCategory category : HELP_CATEGORIES) {
            appendHelpLine(out, HelpLine.command("/gtbs help " + category.key, category.summary));
        }
        appendHelpLine(out, HelpLine.command("/gtbs help all [page]", "Display every command."));
        appendHelpLine(out, HelpLine.command("/gtbs help search <query> [--page <number>]",
                "Find commands by name, description, or category."));
        appendHelpLine(out, HelpLine.command("/gtbs search <query> [--page <number>]",
                "Shortcut for ranked help search."));
        out.add(helpBanner());
    }

    private static HelpPageNavigation appendHelpCategory(List<String> out, HelpCategory category,
            int requestedPage, String command, int rowIndex) {
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
        String prefix = "\u00a76Category: \u00a77" + category.title + "\u00a76, Page: \u00a77";
        out.add(prefix + page + "/" + pageCount);
        for (int i = start; i < end; i++) {
            appendHelpLine(out, category.lines[i]);
        }
        out.add(helpBanner());
        if (pageCount <= 1) return null;
        return new HelpPageNavigation(rowIndex, prefix, page, pageCount, "",
                page > 1 ? command + " " + (page - 1) : null,
                page < pageCount ? command + " " + (page + 1) : null);
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
        List<HelpSearch.Entry> matches = HELP_SEARCH.search(requested);
        ArrayList<String> categories = new ArrayList<String>();
        for (HelpSearch.Entry match : matches) {
            if (categories.contains(match.category)) continue;
            categories.add(match.category);
            out.add("\u00a77Did you mean: \u00a7f/gtbs help " + match.category + "\u00a77 ?");
            if (categories.size() == 3) break;
        }
        out.add("\u00a77Search commands: \u00a7f/gtbs help search "
                + (matches.isEmpty() ? "<query>" : requested));
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
