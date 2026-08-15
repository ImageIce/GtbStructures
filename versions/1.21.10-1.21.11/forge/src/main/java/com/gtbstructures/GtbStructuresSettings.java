package com.gtbstructures;

import com.gtbstructures.chat.ChatHeader;
import com.gtbstructures.items.HandheldItemTracker;
import com.gtbstructures.overlay.WorldEnvironmentLogic;
import com.gtbstructures.scoreboard.ScoreboardConfig;
import com.gtbstructures.util.MinecraftFormatting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/** Modern runtime settings mirroring the non-excluded 1.8.9 GTBS config. */
public final class GtbStructuresSettings {
    public static final GtbStructuresSettings INSTANCE = new GtbStructuresSettings();
    public static final float DEFAULT_HANDHELD_PANEL_WIDTH_RATIO = 0.328125F;
    public static final float DEFAULT_HANDHELD_PANEL_HEIGHT_RATIO = 0.3323263F;

    private File configFile;
    private int legacyHandheldItemPanelWidth = -1;
    private int legacyHandheldItemPanelHeight = -1;

    /** Master switch. Missing legacy config entries intentionally migrate to enabled. */
    public boolean modEnabled = true;
    public boolean enabled = false;
    public boolean trackingEnabled = true;
    public boolean autoRequeue = false;
    public boolean showAllMatching = false;
    public boolean showAllMatchingShowEnglish = false;
    public boolean themePointReveal = false;
    public boolean themePointRevealShowAfterGuess = false;
    public boolean shortcutReminder = true;
    public boolean shortcutReminderShowNonAscii = false;
    public boolean shortcutReminderShowExtendedLatin = true;
    public boolean shortcutReminderShowCyrillic = true;
    public boolean shortcutReminderShowHanzi = true;
    public boolean shortcutReminderShowJapanese = true;
    public boolean shortcutReminderShowHangul = true;
    public boolean shortcutReminderShowAllShortcuts = false;
    public boolean shortcutReminderShowMultiwords = true;
    public boolean shortcutReminderShowAllMultiwords = false;
    public boolean hintBackgroundOverlay = false;
    public boolean hintStructureBackground = false;
    public boolean handheldItemOverlay = false;
    public boolean handheldItemBackground = false;
    public boolean handheldItemCaptionName = true;
    public boolean handheldItemCaptionId = true;
    public boolean handheldItemCaptionMetadata = true;
    public boolean handheldItemCaptionSpawnEggMob = true;
    public boolean handheldItemVerticalLayout = true;
    public boolean handheldItemVerticalNewestAtBottom = true;
    public boolean handheldItemHorizontalNewestAtRight = true;
    public boolean handheldItemHorizontalNewestAtBottom = true;
    public boolean environmentOverlay = false;
    public boolean environmentBackground = false;
    public boolean environmentWeatherEnabled = true;
    public boolean environmentBiomeEnabled = true;
    public boolean environmentTimeEnabled = true;
    public boolean environmentWeatherChatNotice = false;
    public boolean environmentBiomeChatNotice = false;
    public boolean environmentTimeChatNotice = false;
    public boolean previewQuickstartRan = false;
    public boolean chatCooldownCounter = false;
    public boolean chatCooldownTimerEnabled = true;
    public boolean chatCooldownBarEnabled = false;
    public boolean chatCooldownBackground = true;
    public boolean chatCooldownBarBackground = false;
    public boolean gtwShowEnglish = false;
    public boolean copyTranslationPrefix = true;
    public boolean guessPeek = true;
    public boolean guessPeekShowLangCodes = true;
    public String guessPeekDisplay = "hover";
    public boolean scrambleGuess = false;
    public boolean scrambleGuessFancy = false;
    public boolean trollMode = false;
    public boolean trollModeAutoDisable = false;
    public String fontMode = "new";
    public String spawnEggSkin = "new";
    public String environmentTimeIconPreset = "clock";
    public String gtwDefaultLang = "en";
    public float posX = 0.5F;
    public float posY = 0.8522013F;
    public float handheldItemPosX = 0.0F;
    public float handheldItemPosY = 0.03686636F;
    public float environmentPosX = 0.15F;
    public float environmentPosY = 0.52F;
    public float chatCooldownPosX = 0.5F;
    public float chatCooldownPosY = 0.76F;
    public int handheldItemMaxVisible = 6;
    public float handheldItemPanelWidthRatio = DEFAULT_HANDHELD_PANEL_WIDTH_RATIO;
    public float handheldItemPanelHeightRatio = DEFAULT_HANDHELD_PANEL_HEIGHT_RATIO;
    public String colorNumbers = "\u00a7e";
    public String colorHyphens = "\u00a77";
    public String colorBrackets = "\u00a76";
    public String chatCooldownBarColor = "\u00a7e";
    public String chatCooldownTimerColor = "\u00a7e";
    public String lastLangCode = "en";
    public final ScoreboardConfig sbConfig = new ScoreboardConfig();

    public enum ShortcutNonAsciiCategory {
        EXTENDED_LATIN,
        CYRILLIC,
        HANZI,
        JAPANESE,
        HANGUL
    }

    private GtbStructuresSettings() {
    }

    public synchronized void init(File configDir) {
        if (configDir != null) {
            configFile = new File(configDir, "GtbStructures.properties");
        }
        load();
    }

    public synchronized void load() {
        if (configFile == null || !configFile.isFile()) {
            normalize();
            return;
        }
        Properties p = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(configFile);
            p.load(in);
        } catch (IOException ignored) {
            normalize();
            return;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }

        modEnabled = bool(p, "modEnabled", true);
        enabled = bool(p, "enabled", enabled);
        trackingEnabled = bool(p, "trackingEnabled", trackingEnabled);
        autoRequeue = bool(p, "autoRequeue", autoRequeue);
        showAllMatching = bool(p, "showAllMatching", showAllMatching);
        showAllMatchingShowEnglish = bool(p, "showAllMatchingShowEnglish", showAllMatchingShowEnglish);
        themePointReveal = bool(p, "themePointReveal", themePointReveal);
        themePointRevealShowAfterGuess = bool(p, "themePointRevealShowAfterGuess", themePointRevealShowAfterGuess);
        shortcutReminder = bool(p, "shortcutReminder", shortcutReminder);
        shortcutReminderShowNonAscii = bool(p, "shortcutReminderShowNonAscii", shortcutReminderShowNonAscii);
        shortcutReminderShowExtendedLatin = bool(p, "shortcutReminderShowExtendedLatin", shortcutReminderShowExtendedLatin);
        shortcutReminderShowCyrillic = bool(p, "shortcutReminderShowCyrillic", shortcutReminderShowCyrillic);
        shortcutReminderShowHanzi = bool(p, "shortcutReminderShowHanzi", shortcutReminderShowHanzi);
        shortcutReminderShowJapanese = bool(p, "shortcutReminderShowJapanese", shortcutReminderShowJapanese);
        shortcutReminderShowHangul = bool(p, "shortcutReminderShowHangul", shortcutReminderShowHangul);
        shortcutReminderShowAllShortcuts = bool(p, "shortcutReminderShowAllShortcuts", shortcutReminderShowAllShortcuts);
        shortcutReminderShowMultiwords = bool(p, "shortcutReminderShowMultiwords", shortcutReminderShowMultiwords);
        shortcutReminderShowAllMultiwords = bool(p, "shortcutReminderShowAllMultiwords", shortcutReminderShowAllMultiwords);
        hintBackgroundOverlay = bool(p, "hintBackgroundOverlay", hintBackgroundOverlay);
        hintStructureBackground = bool(p, "hintStructureBackground", hintStructureBackground);
        handheldItemOverlay = bool(p, "handheldItemOverlay", handheldItemOverlay);
        handheldItemBackground = bool(p, "handheldItemBackground", handheldItemBackground);
        handheldItemCaptionName = bool(p, "handheldItemCaptionName", handheldItemCaptionName);
        handheldItemCaptionId = bool(p, "handheldItemCaptionId", handheldItemCaptionId);
        handheldItemCaptionMetadata = bool(p, "handheldItemCaptionMetadata", handheldItemCaptionMetadata);
        handheldItemCaptionSpawnEggMob = bool(p, "handheldItemCaptionSpawnEggMob", handheldItemCaptionSpawnEggMob);
        handheldItemVerticalLayout = bool(p, "handheldItemVerticalLayout", handheldItemVerticalLayout);
        handheldItemVerticalNewestAtBottom = bool(p, "handheldItemVerticalNewestAtBottom", handheldItemVerticalNewestAtBottom);
        handheldItemHorizontalNewestAtRight = bool(p, "handheldItemHorizontalNewestAtRight", handheldItemHorizontalNewestAtRight);
        handheldItemHorizontalNewestAtBottom = bool(p, "handheldItemHorizontalNewestAtBottom", handheldItemHorizontalNewestAtBottom);
        environmentOverlay = bool(p, "environmentOverlay", environmentOverlay);
        environmentBackground = bool(p, "environmentBackground", environmentBackground);
        environmentWeatherEnabled = bool(p, "environmentWeatherEnabled", environmentWeatherEnabled);
        environmentBiomeEnabled = bool(p, "environmentBiomeEnabled", environmentBiomeEnabled);
        environmentTimeEnabled = bool(p, "environmentTimeEnabled", environmentTimeEnabled);
        environmentWeatherChatNotice = bool(p, "environmentWeatherChatNotice", environmentWeatherChatNotice);
        environmentBiomeChatNotice = bool(p, "environmentBiomeChatNotice", environmentBiomeChatNotice);
        environmentTimeChatNotice = bool(p, "environmentTimeChatNotice", environmentTimeChatNotice);
        previewQuickstartRan = bool(p, "previewQuickstartRan", previewQuickstartRan);
        chatCooldownCounter = bool(p, "chatCooldownCounter", chatCooldownCounter);
        chatCooldownTimerEnabled = bool(p, "chatCooldownTimerEnabled", chatCooldownTimerEnabled);
        chatCooldownBarEnabled = bool(p, "chatCooldownBarEnabled", chatCooldownBarEnabled);
        chatCooldownBackground = bool(p, "chatCooldownBackground", chatCooldownBackground);
        chatCooldownBarBackground = bool(p, "chatCooldownBarBackground", chatCooldownBarBackground);
        gtwShowEnglish = bool(p, "gtwShowEnglish", gtwShowEnglish);
        copyTranslationPrefix = bool(p, "copyTranslationPrefix", copyTranslationPrefix);
        guessPeek = bool(p, "guessPeek", guessPeek);
        guessPeekShowLangCodes = bool(p, "guessPeekShowLangCodes", guessPeekShowLangCodes);
        guessPeekDisplay = p.getProperty("guessPeekDisplay", guessPeekDisplay);
        scrambleGuess = bool(p, "scrambleGuess", scrambleGuess);
        scrambleGuessFancy = bool(p, "scrambleGuessFancy", scrambleGuessFancy);
        trollMode = bool(p, "trollMode", trollMode);
        trollModeAutoDisable = bool(p, "trollModeAutoDisable", trollModeAutoDisable);
        if (!ChatHeader.set(p.getProperty("chatHeader", ChatHeader.DEFAULT_RAW))) {
            ChatHeader.reset();
        }
        fontMode = p.getProperty("fontMode", fontMode);
        spawnEggSkin = p.getProperty("spawnEggSkin", spawnEggSkin);
        environmentTimeIconPreset = p.getProperty("environmentTimeIconPreset", environmentTimeIconPreset);
        gtwDefaultLang = p.getProperty("gtwDefaultLang", gtwDefaultLang);
        posX = flt(p, "posX", posX);
        posY = flt(p, "posY", posY);
        handheldItemPosX = flt(p, "handheldItemPosX", handheldItemPosX);
        handheldItemPosY = flt(p, "handheldItemPosY", handheldItemPosY);
        environmentPosX = flt(p, "environmentPosX", environmentPosX);
        environmentPosY = flt(p, "environmentPosY", environmentPosY);
        chatCooldownPosX = flt(p, "chatCooldownPosX", chatCooldownPosX);
        chatCooldownPosY = flt(p, "chatCooldownPosY", chatCooldownPosY);
        handheldItemMaxVisible = integer(p, "handheldItemMaxVisible", handheldItemMaxVisible);
        if (p.containsKey("handheldItemPanelWidthRatio")) {
            handheldItemPanelWidthRatio = flt(p, "handheldItemPanelWidthRatio", handheldItemPanelWidthRatio);
        } else if (p.containsKey("handheldItemPanelWidth")) {
            legacyHandheldItemPanelWidth = integer(p, "handheldItemPanelWidth", -1);
        }
        if (p.containsKey("handheldItemPanelHeightRatio")) {
            handheldItemPanelHeightRatio = flt(p, "handheldItemPanelHeightRatio", handheldItemPanelHeightRatio);
        } else if (p.containsKey("handheldItemPanelHeight")) {
            legacyHandheldItemPanelHeight = integer(p, "handheldItemPanelHeight", -1);
        }
        colorNumbers = p.getProperty("colorNumbers", colorNumbers);
        colorHyphens = p.getProperty("colorHyphens", colorHyphens);
        colorBrackets = p.getProperty("colorBrackets", colorBrackets);
        chatCooldownBarColor = p.getProperty("chatCooldownBarColor", chatCooldownBarColor);
        chatCooldownTimerColor = p.getProperty("chatCooldownTimerColor", chatCooldownTimerColor);
        lastLangCode = p.getProperty("lastLangCode", lastLangCode);
        sbConfig.enabled = bool(p, "scoreboardEnabled", sbConfig.enabled);
        sbConfig.heightOffset = integer(p, "scoreboardHeightOffset", sbConfig.heightOffset);
        sbConfig.skin = p.getProperty("scoreboardSkin", sbConfig.skin);
        sbConfig.backgroundOpacity = flt(p, "scoreboardBackgroundOpacity", sbConfig.backgroundOpacity);
        boolean hasHighlightIntensity = p.containsKey("scoreboardHighlightIntensity");
        boolean pureHighlightBenchmark = bool(p, "scoreboardHighlightIntensityPureBenchmark", false);
        float rawHighlightIntensity = flt(p, "scoreboardHighlightIntensity", sbConfig.highlightIntensity);
        sbConfig.highlightIntensity = hasHighlightIntensity && !pureHighlightBenchmark
                ? ScoreboardConfig.migrateLegacyHighlightIntensity(rawHighlightIntensity)
                : rawHighlightIntensity;
        sbConfig.showPointsGainedInRound = ScoreboardConfig.parseOption(
                p.getProperty("scoreboardShowPointsGainedInRound"), sbConfig.showPointsGainedInRound);
        sbConfig.showPlaces = ScoreboardConfig.parseOption(
                p.getProperty("scoreboardShowPlaces"), sbConfig.showPlaces);
        sbConfig.showTitles = ScoreboardConfig.parseOption(
                p.getProperty("scoreboardShowTitles"), sbConfig.showTitles);
        sbConfig.showEmblems = ScoreboardConfig.parseOption(
                p.getProperty("scoreboardShowEmblems"), sbConfig.showEmblems);
        sbConfig.leftPlayersAtBottom = bool(p, "scoreboardLeftPlayersAtBottom",
                sbConfig.leftPlayersAtBottom);
        sbConfig.textShadow = bool(p, "scoreboardTextShadow", sbConfig.textShadow);
        normalize();
    }

    public synchronized void save() {
        normalize();
        if (configFile == null) return;
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        Properties p = new Properties();
        put(p, "modEnabled", modEnabled);
        put(p, "enabled", enabled);
        put(p, "trackingEnabled", trackingEnabled);
        put(p, "autoRequeue", autoRequeue);
        put(p, "showAllMatching", showAllMatching);
        put(p, "showAllMatchingShowEnglish", showAllMatchingShowEnglish);
        put(p, "themePointReveal", themePointReveal);
        put(p, "themePointRevealShowAfterGuess", themePointRevealShowAfterGuess);
        put(p, "shortcutReminder", shortcutReminder);
        put(p, "shortcutReminderShowNonAscii", shortcutReminderShowNonAscii);
        put(p, "shortcutReminderShowExtendedLatin", shortcutReminderShowExtendedLatin);
        put(p, "shortcutReminderShowCyrillic", shortcutReminderShowCyrillic);
        put(p, "shortcutReminderShowHanzi", shortcutReminderShowHanzi);
        put(p, "shortcutReminderShowJapanese", shortcutReminderShowJapanese);
        put(p, "shortcutReminderShowHangul", shortcutReminderShowHangul);
        put(p, "shortcutReminderShowAllShortcuts", shortcutReminderShowAllShortcuts);
        put(p, "shortcutReminderShowMultiwords", shortcutReminderShowMultiwords);
        put(p, "shortcutReminderShowAllMultiwords", shortcutReminderShowAllMultiwords);
        put(p, "hintBackgroundOverlay", hintBackgroundOverlay);
        put(p, "hintStructureBackground", hintStructureBackground);
        put(p, "handheldItemOverlay", handheldItemOverlay);
        put(p, "handheldItemBackground", handheldItemBackground);
        put(p, "handheldItemCaptionName", handheldItemCaptionName);
        put(p, "handheldItemCaptionId", handheldItemCaptionId);
        put(p, "handheldItemCaptionMetadata", handheldItemCaptionMetadata);
        put(p, "handheldItemCaptionSpawnEggMob", handheldItemCaptionSpawnEggMob);
        put(p, "handheldItemVerticalLayout", handheldItemVerticalLayout);
        put(p, "handheldItemVerticalNewestAtBottom", handheldItemVerticalNewestAtBottom);
        put(p, "handheldItemHorizontalNewestAtRight", handheldItemHorizontalNewestAtRight);
        put(p, "handheldItemHorizontalNewestAtBottom", handheldItemHorizontalNewestAtBottom);
        put(p, "environmentOverlay", environmentOverlay);
        put(p, "environmentBackground", environmentBackground);
        put(p, "environmentWeatherEnabled", environmentWeatherEnabled);
        put(p, "environmentBiomeEnabled", environmentBiomeEnabled);
        put(p, "environmentTimeEnabled", environmentTimeEnabled);
        put(p, "environmentWeatherChatNotice", environmentWeatherChatNotice);
        put(p, "environmentBiomeChatNotice", environmentBiomeChatNotice);
        put(p, "environmentTimeChatNotice", environmentTimeChatNotice);
        put(p, "previewQuickstartRan", previewQuickstartRan);
        put(p, "chatCooldownCounter", chatCooldownCounter);
        put(p, "chatCooldownTimerEnabled", chatCooldownTimerEnabled);
        put(p, "chatCooldownBarEnabled", chatCooldownBarEnabled);
        put(p, "chatCooldownBackground", chatCooldownBackground);
        put(p, "chatCooldownBarBackground", chatCooldownBarBackground);
        put(p, "gtwShowEnglish", gtwShowEnglish);
        put(p, "copyTranslationPrefix", copyTranslationPrefix);
        put(p, "guessPeek", guessPeek);
        put(p, "guessPeekShowLangCodes", guessPeekShowLangCodes);
        p.setProperty("guessPeekDisplay", normalizeGuessPeekDisplay(guessPeekDisplay));
        put(p, "scrambleGuess", scrambleGuess);
        put(p, "scrambleGuessFancy", scrambleGuessFancy);
        put(p, "trollMode", trollMode);
        put(p, "trollModeAutoDisable", trollModeAutoDisable);
        p.setProperty("chatHeader", ChatHeader.raw());
        p.setProperty("fontMode", fontMode);
        p.setProperty("spawnEggSkin", spawnEggSkin);
        p.setProperty("environmentTimeIconPreset", environmentTimeIconPreset);
        p.setProperty("gtwDefaultLang", gtwDefaultLang);
        p.setProperty("posX", Float.toString(posX));
        p.setProperty("posY", Float.toString(posY));
        p.setProperty("handheldItemPosX", Float.toString(handheldItemPosX));
        p.setProperty("handheldItemPosY", Float.toString(handheldItemPosY));
        p.setProperty("environmentPosX", Float.toString(environmentPosX));
        p.setProperty("environmentPosY", Float.toString(environmentPosY));
        p.setProperty("chatCooldownPosX", Float.toString(chatCooldownPosX));
        p.setProperty("chatCooldownPosY", Float.toString(chatCooldownPosY));
        p.setProperty("handheldItemMaxVisible", Integer.toString(handheldItemMaxVisible));
        p.setProperty("handheldItemPanelWidthRatio", Float.toString(handheldItemPanelWidthRatio));
        p.setProperty("handheldItemPanelHeightRatio", Float.toString(handheldItemPanelHeightRatio));
        p.setProperty("colorNumbers", colorNumbers);
        p.setProperty("colorHyphens", colorHyphens);
        p.setProperty("colorBrackets", colorBrackets);
        p.setProperty("chatCooldownBarColor", chatCooldownBarColor);
        p.setProperty("chatCooldownTimerColor", chatCooldownTimerColor);
        p.setProperty("lastLangCode", lastLangCode);
        put(p, "scoreboardEnabled", sbConfig.enabled);
        p.setProperty("scoreboardHeightOffset", Integer.toString(sbConfig.heightOffset));
        p.setProperty("scoreboardSkin", sbConfig.skin);
        p.setProperty("scoreboardBackgroundOpacity", Float.toString(sbConfig.backgroundOpacity));
        p.setProperty("scoreboardHighlightIntensity", Float.toString(sbConfig.highlightIntensity));
        put(p, "scoreboardHighlightIntensityPureBenchmark", true);
        p.setProperty("scoreboardShowPointsGainedInRound",
                ScoreboardConfig.displayOption(sbConfig.showPointsGainedInRound));
        p.setProperty("scoreboardShowPlaces", ScoreboardConfig.displayOption(sbConfig.showPlaces));
        p.setProperty("scoreboardShowTitles", ScoreboardConfig.displayOption(sbConfig.showTitles));
        p.setProperty("scoreboardShowEmblems", ScoreboardConfig.displayOption(sbConfig.showEmblems));
        put(p, "scoreboardLeftPlayersAtBottom", sbConfig.leftPlayersAtBottom);
        put(p, "scoreboardTextShadow", sbConfig.textShadow);

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(configFile);
            p.store(out, "GtbStructures modern settings");
        } catch (IOException ignored) {
            // User-visible commands should not fail because the config write failed.
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    public synchronized void resetForTest() {
        modEnabled = true;
        enabled = false;
        trackingEnabled = true;
        autoRequeue = false;
        showAllMatching = false;
        showAllMatchingShowEnglish = false;
        themePointReveal = false;
        themePointRevealShowAfterGuess = false;
        shortcutReminder = true;
        shortcutReminderShowNonAscii = false;
        shortcutReminderShowExtendedLatin = true;
        shortcutReminderShowCyrillic = true;
        shortcutReminderShowHanzi = true;
        shortcutReminderShowJapanese = true;
        shortcutReminderShowHangul = true;
        shortcutReminderShowAllShortcuts = false;
        shortcutReminderShowMultiwords = true;
        shortcutReminderShowAllMultiwords = false;
        hintBackgroundOverlay = false;
        hintStructureBackground = false;
        handheldItemOverlay = false;
        handheldItemBackground = false;
        handheldItemCaptionName = true;
        handheldItemCaptionId = true;
        handheldItemCaptionMetadata = true;
        handheldItemCaptionSpawnEggMob = true;
        handheldItemVerticalLayout = true;
        handheldItemVerticalNewestAtBottom = true;
        handheldItemHorizontalNewestAtRight = true;
        handheldItemHorizontalNewestAtBottom = true;
        environmentOverlay = false;
        environmentBackground = false;
        environmentWeatherEnabled = true;
        environmentBiomeEnabled = true;
        environmentTimeEnabled = true;
        environmentWeatherChatNotice = false;
        environmentBiomeChatNotice = false;
        environmentTimeChatNotice = false;
        previewQuickstartRan = false;
        chatCooldownCounter = false;
        chatCooldownTimerEnabled = true;
        chatCooldownBarEnabled = false;
        chatCooldownBackground = true;
        chatCooldownBarBackground = false;
        gtwShowEnglish = false;
        copyTranslationPrefix = true;
        guessPeek = true;
        guessPeekShowLangCodes = true;
        guessPeekDisplay = "hover";
        scrambleGuess = false;
        scrambleGuessFancy = false;
        trollMode = false;
        trollModeAutoDisable = false;
        ChatHeader.reset();
        fontMode = "new";
        spawnEggSkin = "new";
        environmentTimeIconPreset = "clock";
        gtwDefaultLang = "en";
        posX = 0.5F;
        posY = 0.8522013F;
        handheldItemPosX = 0.0F;
        handheldItemPosY = 0.03686636F;
        environmentPosX = 0.15F;
        environmentPosY = 0.52F;
        chatCooldownPosX = 0.5F;
        chatCooldownPosY = 0.76F;
        handheldItemMaxVisible = 6;
        handheldItemPanelWidthRatio = DEFAULT_HANDHELD_PANEL_WIDTH_RATIO;
        handheldItemPanelHeightRatio = DEFAULT_HANDHELD_PANEL_HEIGHT_RATIO;
        legacyHandheldItemPanelWidth = -1;
        legacyHandheldItemPanelHeight = -1;
        colorNumbers = "\u00a7e";
        colorHyphens = "\u00a77";
        colorBrackets = "\u00a76";
        chatCooldownBarColor = "\u00a7e";
        chatCooldownTimerColor = "\u00a7e";
        lastLangCode = "en";
        sbConfig.enabled = false;
        sbConfig.heightOffset = 9;
        sbConfig.skin = "vanilla";
        sbConfig.backgroundOpacity = ScoreboardConfig.DEFAULT_BACKGROUND_OPACITY;
        sbConfig.highlightIntensity = ScoreboardConfig.DEFAULT_HIGHLIGHT_INTENSITY;
        sbConfig.showPointsGainedInRound = ScoreboardConfig.DisplayOption.ON;
        sbConfig.showPlaces = ScoreboardConfig.DisplayOption.OFF;
        sbConfig.showTitles = ScoreboardConfig.DisplayOption.EXPANDED;
        sbConfig.showEmblems = ScoreboardConfig.DisplayOption.ON;
        sbConfig.leftPlayersAtBottom = true;
        sbConfig.textShadow = true;
        configFile = null;
        normalize();
    }

    public void setShortcutReminderShowNonAscii(boolean enabled) {
        if (enabled && !hasShortcutNonAsciiCategoryEnabled()) {
            shortcutReminderShowExtendedLatin = true;
        }
        shortcutReminderShowNonAscii = enabled;
    }

    public void setShortcutNonAsciiCategoryEnabled(ShortcutNonAsciiCategory category, boolean enabled) {
        if (category == null) return;
        if (!enabled && shortcutNonAsciiCategoryEnabled(category)
                && shortcutNonAsciiCategoryCount() == 1) {
            shortcutReminderShowNonAscii = false;
            return;
        }

        setShortcutNonAsciiCategoryValue(category, enabled);
        if (!hasShortcutNonAsciiCategoryEnabled()) {
            shortcutReminderShowNonAscii = false;
        }
    }

    public boolean shortcutNonAsciiCategoryEnabled(ShortcutNonAsciiCategory category) {
        if (category == ShortcutNonAsciiCategory.EXTENDED_LATIN) return shortcutReminderShowExtendedLatin;
        if (category == ShortcutNonAsciiCategory.CYRILLIC) return shortcutReminderShowCyrillic;
        if (category == ShortcutNonAsciiCategory.HANZI) return shortcutReminderShowHanzi;
        if (category == ShortcutNonAsciiCategory.JAPANESE) return shortcutReminderShowJapanese;
        return category == ShortcutNonAsciiCategory.HANGUL && shortcutReminderShowHangul;
    }

    private void normalize() {
        fontMode = normalizeFontMode(fontMode);
        guessPeekDisplay = normalizeGuessPeekDisplay(guessPeekDisplay);
        spawnEggSkin = MinecraftFormatting.normalizeSpawnEggSkin(spawnEggSkin);
        environmentTimeIconPreset = WorldEnvironmentLogic.normalizeTimeIconPreset(environmentTimeIconPreset);
        posX = clamp01(posX);
        posY = clamp01(posY);
        handheldItemPosX = clamp01(handheldItemPosX);
        handheldItemPosY = clamp01(handheldItemPosY);
        environmentPosX = clamp01(environmentPosX);
        environmentPosY = clamp01(environmentPosY);
        chatCooldownPosX = clamp01(chatCooldownPosX);
        chatCooldownPosY = clamp01(chatCooldownPosY);
        handheldItemMaxVisible = HandheldItemTracker.clampMaxVisible(handheldItemMaxVisible);
        handheldItemPanelWidthRatio = normalizeHandheldPanelRatio(handheldItemPanelWidthRatio);
        handheldItemPanelHeightRatio = normalizeHandheldPanelRatio(handheldItemPanelHeightRatio);
        colorNumbers = MinecraftFormatting.normalizeMinecraftColorCode(colorNumbers, "\u00a7e");
        colorHyphens = MinecraftFormatting.normalizeMinecraftColorCode(colorHyphens, "\u00a77");
        colorBrackets = MinecraftFormatting.normalizeMinecraftColorCode(colorBrackets, "\u00a76");
        chatCooldownBarColor = MinecraftFormatting.normalizeMinecraftColorCode(chatCooldownBarColor, "\u00a7e");
        chatCooldownTimerColor = MinecraftFormatting.normalizeMinecraftColorCode(chatCooldownTimerColor, "\u00a7e");
        sbConfig.skin = ScoreboardConfig.normalizeSkin(sbConfig.skin);
        sbConfig.enabled = !"vanilla".equals(sbConfig.skin) && sbConfig.enabled;
        sbConfig.clampVisuals();
        if (lastLangCode == null || lastLangCode.trim().isEmpty()) lastLangCode = "en";
        if (gtwDefaultLang == null || gtwDefaultLang.trim().isEmpty()) gtwDefaultLang = "en";
    }

    private static String normalizeFontMode(String value) {
        if (value != null && "classic".equalsIgnoreCase(value.trim())) return "classic";
        return "new";
    }

    public static String normalizeGuessPeekDisplay(String value) {
        return value != null && "suffix".equalsIgnoreCase(value.trim()) ? "suffix" : "hover";
    }

    public static float normalizeHandheldPanelRatio(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0F;
        return Math.max(0F, Math.min(1F, value));
    }

    public static int handheldPanelWidthPixels(float ratio, int screenWidth) {
        return panelPixels(ratio, screenWidth, 32);
    }

    public static int handheldPanelHeightPixels(float ratio, int screenHeight) {
        return panelPixels(ratio, screenHeight, HandheldItemTracker.ROW_HEIGHT);
    }

    public static float handheldPanelWidthRatioForPixels(int pixels, int screenWidth) {
        return panelRatioForPixels(pixels, screenWidth, 32);
    }

    public static float handheldPanelHeightRatioForPixels(int pixels, int screenHeight) {
        return panelRatioForPixels(pixels, screenHeight, HandheldItemTracker.ROW_HEIGHT);
    }

    public synchronized boolean migrateLegacyHandheldPanelSize(int screenWidth, int screenHeight) {
        boolean changed = false;
        if (legacyHandheldItemPanelWidth >= 0 && screenWidth > 0) {
            int maximum = Math.max(32, screenWidth - 4);
            handheldItemPanelWidthRatio = legacyHandheldItemPanelWidth <= maximum
                    ? normalizeHandheldPanelRatio((float) legacyHandheldItemPanelWidth / (float) screenWidth)
                    : DEFAULT_HANDHELD_PANEL_WIDTH_RATIO;
            legacyHandheldItemPanelWidth = -1;
            changed = true;
        }
        if (legacyHandheldItemPanelHeight >= 0 && screenHeight > 0) {
            int maximum = Math.max(HandheldItemTracker.ROW_HEIGHT, screenHeight - 4);
            handheldItemPanelHeightRatio = legacyHandheldItemPanelHeight <= maximum
                    ? normalizeHandheldPanelRatio((float) legacyHandheldItemPanelHeight / (float) screenHeight)
                    : DEFAULT_HANDHELD_PANEL_HEIGHT_RATIO;
            legacyHandheldItemPanelHeight = -1;
            changed = true;
        }
        return changed;
    }

    private static int panelPixels(float ratio, int screenSize, int minimum) {
        int maximum = Math.max(1, screenSize - 4);
        int effectiveMinimum = Math.min(minimum, maximum);
        int requested = Math.round(normalizeHandheldPanelRatio(ratio) * Math.max(0, screenSize));
        return Math.max(effectiveMinimum, Math.min(maximum, requested));
    }

    private static float panelRatioForPixels(int pixels, int screenSize, int minimum) {
        int maximum = Math.max(1, screenSize - 4);
        int effective = Math.max(Math.min(minimum, maximum), Math.min(maximum, pixels));
        return screenSize <= 0 ? 0F : normalizeHandheldPanelRatio((float) effective / screenSize);
    }

    private boolean hasShortcutNonAsciiCategoryEnabled() {
        return shortcutNonAsciiCategoryCount() > 0;
    }

    private int shortcutNonAsciiCategoryCount() {
        int count = 0;
        if (shortcutReminderShowExtendedLatin) count++;
        if (shortcutReminderShowCyrillic) count++;
        if (shortcutReminderShowHanzi) count++;
        if (shortcutReminderShowJapanese) count++;
        if (shortcutReminderShowHangul) count++;
        return count;
    }

    private void setShortcutNonAsciiCategoryValue(ShortcutNonAsciiCategory category, boolean enabled) {
        if (category == ShortcutNonAsciiCategory.EXTENDED_LATIN) {
            shortcutReminderShowExtendedLatin = enabled;
        } else if (category == ShortcutNonAsciiCategory.CYRILLIC) {
            shortcutReminderShowCyrillic = enabled;
        } else if (category == ShortcutNonAsciiCategory.HANZI) {
            shortcutReminderShowHanzi = enabled;
        } else if (category == ShortcutNonAsciiCategory.JAPANESE) {
            shortcutReminderShowJapanese = enabled;
        } else if (category == ShortcutNonAsciiCategory.HANGUL) {
            shortcutReminderShowHangul = enabled;
        }
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String value = p.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static float flt(Properties p, String key, float fallback) {
        try {
            return Float.parseFloat(p.getProperty(key, Float.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int integer(Properties p, String key, int fallback) {
        try {
            return Integer.parseInt(p.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float clamp01(float value) {
        if (value < 0F) return 0F;
        if (value > 1F) return 1F;
        return value;
    }

    private static void put(Properties p, String key, boolean value) {
        p.setProperty(key, Boolean.toString(value));
    }
}
