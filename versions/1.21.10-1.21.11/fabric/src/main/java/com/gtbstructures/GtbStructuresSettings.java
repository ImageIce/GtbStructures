package com.gtbstructures;

import com.gtbstructures.chat.ChatHeader;
import com.gtbstructures.gtw.GtwLogic;
import com.gtbstructures.items.HandheldItemTracker;
import com.gtbstructures.overlay.HudPanelScale;
import com.gtbstructures.overlay.WorldEnvironmentLogic;
import com.gtbstructures.scoreboard.ScoreboardConfig;
import com.gtbstructures.sound.GuessSounds;
import com.gtbstructures.translation.ShortcutLanguageGroups;
import com.gtbstructures.util.MinecraftFormatting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

/** Modern runtime settings mirroring the non-excluded 1.8.9 GTBS config. */
public final class GtbStructuresSettings {
    public static final GtbStructuresSettings INSTANCE = new GtbStructuresSettings();
    public static final float DEFAULT_HANDHELD_PANEL_WIDTH_RATIO = 0.328125F;
    public static final float DEFAULT_HANDHELD_PANEL_HEIGHT_RATIO = 0.3323263F;
    public static final float DEFAULT_AUTO_REQUEUE_DELAY_SECONDS = 5.0F;
    public static final float MAX_AUTO_REQUEUE_DELAY_SECONDS = 10.0F;

    private File configFile;
    private int legacyHandheldItemPanelWidth = -1;
    private int legacyHandheldItemPanelHeight = -1;
    private String shortcutReminderLanguageGroupsPrimary =
            ShortcutLanguageGroups.DEFAULT_SERIALIZED;
    private String shortcutReminderLanguageGroupsBackup =
            ShortcutLanguageGroups.DEFAULT_SERIALIZED;

    /** Master switch. Missing legacy config entries intentionally migrate to enabled. */
    public boolean modEnabled = true;
    public boolean enabled = false;
    public boolean trackingEnabled = true;
    public boolean autoRequeue = false;
    /** Missing configs intentionally use the legacy-enabled lobby-protection bypass. */
    public boolean lrjBypassLobbyProtection = true;
    public float autoRequeueDelaySeconds = DEFAULT_AUTO_REQUEUE_DELAY_SECONDS;
    public boolean updateNoticeEnabled = true;
    public boolean showAllMatching = false;
    public boolean showAllMatchingShowEnglish = false;
    /** Missing configs intentionally migrate to the legacy-enabled count suffix. */
    public boolean showAllMatchingShowCount = true;
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
    /** Captions are deliberately independent so a compact vertical panel remains useful. */
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
    /** Missing configs intentionally migrate to the legacy-enabled count suffix. */
    public boolean gtwShowCount = true;
    public boolean copyTranslationPrefix = true;
    public boolean copyTranslationCaps = true;
    public boolean guessPeek = true;
    public boolean guessPeekShowLangCodes = true;
    public String guessPeekDisplay = "hover";
    public boolean scrambleGuess = false;
    public boolean scrambleGuessFancy = false;
    public boolean trollMode = false;
    public boolean trollModeAutoDisable = false;
    public String guessSound1 = GuessSounds.DEFAULT_SOUND;
    public String guessSound2 = GuessSounds.DEFAULT_SOUND;
    public String guessSound3 = GuessSounds.DEFAULT_SOUND;
    public float guessSoundVolume = GuessSounds.DEFAULT_VOLUME;
    public String fontMode = "new";
    public String spawnEggSkin = "new";
    public String environmentTimeIconPreset = "clock";
    public String gtwDefaultLang = "en";
    /** English-origin GTW pool; missing legacy configs intentionally use all themes. */
    public String gtwDifficulty = "all";
    public float posX = 0.5F;
    public float posY = 0.8522013F;
    public float handheldItemPosX = 0.0F;
    public float handheldItemPosY = 0.03686636F;
    public float environmentPosX = 0.15F;
    public float environmentPosY = 0.52F;
    public float chatCooldownPosX = 0.5F;
    public float chatCooldownPosY = 0.76F;
    public float hintStructureScale = HudPanelScale.DEFAULT;
    public float chatCooldownScale = HudPanelScale.DEFAULT;
    public float handheldItemScale = HudPanelScale.DEFAULT;
    public float environmentScale = HudPanelScale.DEFAULT;
    public int handheldItemMaxVisible = 6;
    /** Resizable bounds as scaled-screen ratios; vertical mode uses width for responsive captions. */
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
            ShortcutLanguageGroups.load(ShortcutLanguageGroups.DEFAULT_SERIALIZED,
                    ShortcutLanguageGroups.DEFAULT_SERIALIZED);
            shortcutReminderLanguageGroupsPrimary = ShortcutLanguageGroups.DEFAULT_SERIALIZED;
            shortcutReminderLanguageGroupsBackup = ShortcutLanguageGroups.DEFAULT_SERIALIZED;
            normalize();
            return;
        }
        Properties p = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(configFile);
            p.load(in);
        } catch (IOException ignored) {
            ShortcutLanguageGroups.load(ShortcutLanguageGroups.DEFAULT_SERIALIZED,
                    ShortcutLanguageGroups.DEFAULT_SERIALIZED);
            shortcutReminderLanguageGroupsPrimary = ShortcutLanguageGroups.DEFAULT_SERIALIZED;
            shortcutReminderLanguageGroupsBackup = ShortcutLanguageGroups.DEFAULT_SERIALIZED;
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
        lrjBypassLobbyProtection = bool(p, "lrjBypassLobbyProtection", true);
        autoRequeueDelaySeconds = normalizeAutoRequeueDelaySeconds(
                flt(p, "autoRequeueDelaySeconds", DEFAULT_AUTO_REQUEUE_DELAY_SECONDS));
        updateNoticeEnabled = bool(p, "updateNoticeEnabled", updateNoticeEnabled);
        showAllMatching = bool(p, "showAllMatching", showAllMatching);
        showAllMatchingShowEnglish = bool(p, "showAllMatchingShowEnglish", showAllMatchingShowEnglish);
        showAllMatchingShowCount = bool(p, "showAllMatchingShowCount", true);
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
        shortcutReminderLanguageGroupsPrimary = p.getProperty("shortcutReminderLanguageGroups",
                ShortcutLanguageGroups.DEFAULT_SERIALIZED);
        shortcutReminderLanguageGroupsBackup = p.getProperty("shortcutReminderLanguageGroupsBackup",
                ShortcutLanguageGroups.DEFAULT_SERIALIZED);
        ShortcutLanguageGroups.load(shortcutReminderLanguageGroupsPrimary,
                shortcutReminderLanguageGroupsBackup);
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
        gtwShowCount = bool(p, "gtwShowCount", true);
        copyTranslationPrefix = bool(p, "copyTranslationPrefix", copyTranslationPrefix);
        copyTranslationCaps = bool(p, "copyTranslationCaps", true);
        guessPeek = bool(p, "guessPeek", guessPeek);
        guessPeekShowLangCodes = bool(p, "guessPeekShowLangCodes", guessPeekShowLangCodes);
        guessPeekDisplay = p.getProperty("guessPeekDisplay", guessPeekDisplay);
        scrambleGuess = bool(p, "scrambleGuess", scrambleGuess);
        scrambleGuessFancy = bool(p, "scrambleGuessFancy", scrambleGuessFancy);
        trollMode = bool(p, "trollMode", trollMode);
        trollModeAutoDisable = bool(p, "trollModeAutoDisable", trollModeAutoDisable);
        guessSound1 = p.getProperty("guessSound1", GuessSounds.DEFAULT_SOUND);
        guessSound2 = p.getProperty("guessSound2", GuessSounds.DEFAULT_SOUND);
        guessSound3 = p.getProperty("guessSound3", GuessSounds.DEFAULT_SOUND);
        guessSoundVolume = flt(p, "guessSoundVolume", GuessSounds.DEFAULT_VOLUME);
        if (!ChatHeader.set(p.getProperty("chatHeader", ChatHeader.DEFAULT_RAW))) {
            ChatHeader.reset();
        }
        fontMode = p.getProperty("fontMode", fontMode);
        spawnEggSkin = p.getProperty("spawnEggSkin", spawnEggSkin);
        environmentTimeIconPreset = p.getProperty("environmentTimeIconPreset", environmentTimeIconPreset);
        gtwDefaultLang = p.getProperty("gtwDefaultLang", gtwDefaultLang);
        gtwDifficulty = p.getProperty("gtwDifficulty", gtwDifficulty);
        posX = flt(p, "posX", posX);
        posY = flt(p, "posY", posY);
        handheldItemPosX = flt(p, "handheldItemPosX", handheldItemPosX);
        handheldItemPosY = flt(p, "handheldItemPosY", handheldItemPosY);
        environmentPosX = flt(p, "environmentPosX", environmentPosX);
        environmentPosY = flt(p, "environmentPosY", environmentPosY);
        chatCooldownPosX = flt(p, "chatCooldownPosX", chatCooldownPosX);
        chatCooldownPosY = flt(p, "chatCooldownPosY", chatCooldownPosY);
        hintStructureScale = HudPanelScale.normalize(
                flt(p, "hintStructureScale", HudPanelScale.DEFAULT));
        chatCooldownScale = HudPanelScale.normalize(
                flt(p, "chatCooldownScale", HudPanelScale.DEFAULT));
        handheldItemScale = HudPanelScale.normalize(
                flt(p, "handheldItemScale", HudPanelScale.DEFAULT));
        environmentScale = HudPanelScale.normalize(
                flt(p, "environmentScale", HudPanelScale.DEFAULT));
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
        sbConfig.defaultExplicitlyShowBuilders = bool(p, "defaultExplicitlyShowBuilders", false);
        sbConfig.hypixelShowRounds = ScoreboardConfig.parseOption(
                p.getProperty("hypixelShowRounds"), ScoreboardConfig.DisplayOption.OFF);
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
        put(p, "lrjBypassLobbyProtection", lrjBypassLobbyProtection);
        p.setProperty("autoRequeueDelaySeconds", formatAutoRequeueDelaySeconds(autoRequeueDelaySeconds));
        put(p, "updateNoticeEnabled", updateNoticeEnabled);
        put(p, "showAllMatching", showAllMatching);
        put(p, "showAllMatchingShowEnglish", showAllMatchingShowEnglish);
        put(p, "showAllMatchingShowCount", showAllMatchingShowCount);
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
        String serializedLanguageGroups = ShortcutLanguageGroups.serialize();
        if (!serializedLanguageGroups.equals(shortcutReminderLanguageGroupsPrimary)
                && ShortcutLanguageGroups.isValidSerialized(shortcutReminderLanguageGroupsPrimary)) {
            shortcutReminderLanguageGroupsBackup = shortcutReminderLanguageGroupsPrimary;
        }
        shortcutReminderLanguageGroupsPrimary = serializedLanguageGroups;
        p.setProperty("shortcutReminderLanguageGroups", shortcutReminderLanguageGroupsPrimary);
        p.setProperty("shortcutReminderLanguageGroupsBackup", shortcutReminderLanguageGroupsBackup);
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
        put(p, "gtwShowCount", gtwShowCount);
        put(p, "copyTranslationPrefix", copyTranslationPrefix);
        put(p, "copyTranslationCaps", copyTranslationCaps);
        put(p, "guessPeek", guessPeek);
        put(p, "guessPeekShowLangCodes", guessPeekShowLangCodes);
        p.setProperty("guessPeekDisplay", normalizeGuessPeekDisplay(guessPeekDisplay));
        put(p, "scrambleGuess", scrambleGuess);
        put(p, "scrambleGuessFancy", scrambleGuessFancy);
        put(p, "trollMode", trollMode);
        put(p, "trollModeAutoDisable", trollModeAutoDisable);
        p.setProperty("guessSound1", guessSound1);
        p.setProperty("guessSound2", guessSound2);
        p.setProperty("guessSound3", guessSound3);
        p.setProperty("guessSoundVolume", Float.toString(guessSoundVolume));
        p.setProperty("chatHeader", ChatHeader.raw());
        p.setProperty("fontMode", fontMode);
        p.setProperty("spawnEggSkin", spawnEggSkin);
        p.setProperty("environmentTimeIconPreset", environmentTimeIconPreset);
        p.setProperty("gtwDefaultLang", gtwDefaultLang);
        p.setProperty("gtwDifficulty", gtwDifficulty);
        p.setProperty("posX", Float.toString(posX));
        p.setProperty("posY", Float.toString(posY));
        p.setProperty("handheldItemPosX", Float.toString(handheldItemPosX));
        p.setProperty("handheldItemPosY", Float.toString(handheldItemPosY));
        p.setProperty("environmentPosX", Float.toString(environmentPosX));
        p.setProperty("environmentPosY", Float.toString(environmentPosY));
        p.setProperty("chatCooldownPosX", Float.toString(chatCooldownPosX));
        p.setProperty("chatCooldownPosY", Float.toString(chatCooldownPosY));
        p.setProperty("hintStructureScale", Float.toString(hintStructureScale));
        p.setProperty("chatCooldownScale", Float.toString(chatCooldownScale));
        p.setProperty("handheldItemScale", Float.toString(handheldItemScale));
        p.setProperty("environmentScale", Float.toString(environmentScale));
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
        put(p, "defaultExplicitlyShowBuilders", sbConfig.defaultExplicitlyShowBuilders);
        p.setProperty("hypixelShowRounds", ScoreboardConfig.displayOption(sbConfig.hypixelShowRounds));
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
        lrjBypassLobbyProtection = true;
        autoRequeueDelaySeconds = DEFAULT_AUTO_REQUEUE_DELAY_SECONDS;
        updateNoticeEnabled = true;
        showAllMatching = false;
        showAllMatchingShowEnglish = false;
        showAllMatchingShowCount = true;
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
        shortcutReminderLanguageGroupsPrimary = ShortcutLanguageGroups.DEFAULT_SERIALIZED;
        shortcutReminderLanguageGroupsBackup = ShortcutLanguageGroups.DEFAULT_SERIALIZED;
        ShortcutLanguageGroups.restore(ShortcutLanguageGroups.State.empty());
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
        gtwShowCount = true;
        copyTranslationPrefix = true;
        copyTranslationCaps = true;
        guessPeek = true;
        guessPeekShowLangCodes = true;
        guessPeekDisplay = "hover";
        scrambleGuess = false;
        scrambleGuessFancy = false;
        trollMode = false;
        trollModeAutoDisable = false;
        guessSound1 = GuessSounds.DEFAULT_SOUND;
        guessSound2 = GuessSounds.DEFAULT_SOUND;
        guessSound3 = GuessSounds.DEFAULT_SOUND;
        guessSoundVolume = GuessSounds.DEFAULT_VOLUME;
        ChatHeader.reset();
        fontMode = "new";
        spawnEggSkin = "new";
        environmentTimeIconPreset = "clock";
        gtwDefaultLang = "en";
        gtwDifficulty = "all";
        posX = 0.5F;
        posY = 0.8522013F;
        handheldItemPosX = 0.0F;
        handheldItemPosY = 0.03686636F;
        environmentPosX = 0.15F;
        environmentPosY = 0.52F;
        chatCooldownPosX = 0.5F;
        chatCooldownPosY = 0.76F;
        hintStructureScale = HudPanelScale.DEFAULT;
        chatCooldownScale = HudPanelScale.DEFAULT;
        handheldItemScale = HudPanelScale.DEFAULT;
        environmentScale = HudPanelScale.DEFAULT;
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
        sbConfig.defaultExplicitlyShowBuilders = false;
        sbConfig.hypixelShowRounds = ScoreboardConfig.DisplayOption.OFF;
        sbConfig.leftPlayersAtBottom = true;
        sbConfig.textShadow = true;
        configFile = null;
        normalize();
    }

    public synchronized boolean autoDisableTrollModeAtGameStart() {
        if (!trollModeAutoDisable || !trollMode) return false;
        trollMode = false;
        save();
        return true;
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
        guessSound1 = normalizeGuessSoundChoice(guessSound1);
        guessSound2 = normalizeGuessSoundChoice(guessSound2);
        guessSound3 = normalizeGuessSoundChoice(guessSound3);
        guessSoundVolume = normalizeGuessSoundVolume(guessSoundVolume);
        autoRequeueDelaySeconds = normalizeAutoRequeueDelaySeconds(autoRequeueDelaySeconds);
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
        hintStructureScale = HudPanelScale.normalize(hintStructureScale);
        chatCooldownScale = HudPanelScale.normalize(chatCooldownScale);
        handheldItemScale = HudPanelScale.normalize(handheldItemScale);
        environmentScale = HudPanelScale.normalize(environmentScale);
        handheldItemMaxVisible = HandheldItemTracker.clampMaxVisible(handheldItemMaxVisible);
        handheldItemPanelWidthRatio = normalizeHandheldPanelRatio(handheldItemPanelWidthRatio);
        handheldItemPanelHeightRatio = normalizeHandheldPanelRatio(handheldItemPanelHeightRatio);
        colorNumbers = MinecraftFormatting.normalizeMinecraftColorCode(colorNumbers, "\u00a7e");
        colorHyphens = MinecraftFormatting.normalizeMinecraftColorCode(colorHyphens, "\u00a77");
        colorBrackets = MinecraftFormatting.normalizeMinecraftColorCode(colorBrackets, "\u00a76");
        chatCooldownBarColor = MinecraftFormatting.normalizeMinecraftColorCode(chatCooldownBarColor, "\u00a7e");
        chatCooldownTimerColor = MinecraftFormatting.normalizeMinecraftColorCode(chatCooldownTimerColor, "\u00a7f");
        sbConfig.skin = ScoreboardConfig.normalizeSkin(sbConfig.skin);
        sbConfig.enabled = !"vanilla".equals(sbConfig.skin) && sbConfig.enabled;
        sbConfig.clampVisuals();
        if (lastLangCode == null || lastLangCode.trim().isEmpty()) lastLangCode = "en";
        if (gtwDefaultLang == null || gtwDefaultLang.trim().isEmpty()) gtwDefaultLang = "en";
        gtwDifficulty = GtwLogic.normalizeDifficulty(gtwDifficulty);
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

    public String guessSound(int points) {
        if (points == 1) return guessSound1;
        if (points == 2) return guessSound2;
        if (points == 3) return guessSound3;
        throw new IllegalArgumentException("Guess points must be 1, 2 or 3");
    }

    public void setGuessSound(int points, String value) {
        String normalized = GuessSounds.normalizeChoice(value);
        if (normalized == null) throw new IllegalArgumentException("Unknown Minecraft sound: " + value);
        if (points == 1) guessSound1 = normalized;
        else if (points == 2) guessSound2 = normalized;
        else if (points == 3) guessSound3 = normalized;
        else throw new IllegalArgumentException("Guess points must be 1, 2 or 3");
    }

    private static String normalizeGuessSoundChoice(String value) {
        String normalized = GuessSounds.normalizeConfiguredChoice(value);
        return normalized == null ? GuessSounds.DEFAULT_SOUND : normalized;
    }

    public static float normalizeGuessSoundVolume(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) || value < 0F || value > GuessSounds.MAX_VOLUME
                ? GuessSounds.DEFAULT_VOLUME : value;
    }

    public static Float parseGuessSoundVolume(String raw) {
        if (raw == null) return null;
        try {
            float value = Float.parseFloat(raw.trim());
            if (!Float.isFinite(value) || value < 0F || value > GuessSounds.MAX_VOLUME) return null;
            return value;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String formatGuessSoundVolume(float value) {
        return String.format(Locale.ROOT, "%.1f", normalizeGuessSoundVolume(value));
    }

    public static Float parseAutoRequeueDelaySeconds(String raw) {
        if (raw == null) return null;
        final float parsed;
        try {
            parsed = Float.parseFloat(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (!Float.isFinite(parsed) || parsed < 0F || parsed > MAX_AUTO_REQUEUE_DELAY_SECONDS) {
            return null;
        }
        return Float.valueOf(normalizeAutoRequeueDelaySeconds(parsed));
    }

    public static float normalizeAutoRequeueDelaySeconds(float value) {
        if (!Float.isFinite(value) || value < 0F || value > MAX_AUTO_REQUEUE_DELAY_SECONDS) {
            return DEFAULT_AUTO_REQUEUE_DELAY_SECONDS;
        }
        return Math.round(value * 10F) / 10F;
    }

    public static String formatAutoRequeueDelaySeconds(float value) {
        return String.format(Locale.ROOT, "%.1f", normalizeAutoRequeueDelaySeconds(value));
    }

    public static int handheldPanelWidthPixels(float ratio, int scaledScreenWidth) {
        return panelPixels(ratio, scaledScreenWidth, 32);
    }

    public static int handheldPanelHeightPixels(float ratio, int scaledScreenHeight) {
        return panelPixels(ratio, scaledScreenHeight, HandheldItemTracker.ROW_HEIGHT);
    }

    public static float handheldPanelWidthRatioForPixels(int pixels, int scaledScreenWidth) {
        return panelRatioForPixels(pixels, scaledScreenWidth, 32);
    }

    public static float handheldPanelHeightRatioForPixels(int pixels, int scaledScreenHeight) {
        return panelRatioForPixels(pixels, scaledScreenHeight, HandheldItemTracker.ROW_HEIGHT);
    }

    private static int panelPixels(float ratio, int scaledScreenSize, int minimum) {
        int maximum = Math.max(1, scaledScreenSize - 4);
        int effectiveMinimum = Math.min(minimum, maximum);
        int requested = Math.round(normalizeHandheldPanelRatio(ratio) * Math.max(0, scaledScreenSize));
        return Math.max(effectiveMinimum, Math.min(maximum, requested));
    }

    private static float panelRatioForPixels(int pixels, int scaledScreenSize, int minimum) {
        int maximum = Math.max(1, scaledScreenSize - 4);
        int effective = Math.max(Math.min(minimum, maximum), Math.min(maximum, pixels));
        return scaledScreenSize <= 0 ? 0F : normalizeHandheldPanelRatio((float) effective / scaledScreenSize);
    }

    public synchronized boolean migrateLegacyHandheldPanelSize(int scaledScreenWidth, int scaledScreenHeight) {
        boolean changed = false;
        if (legacyHandheldItemPanelWidth >= 0 && scaledScreenWidth > 0) {
            int maximum = Math.max(32, scaledScreenWidth - 4);
            handheldItemPanelWidthRatio = legacyHandheldItemPanelWidth <= maximum
                    ? normalizeHandheldPanelRatio(
                    (float) legacyHandheldItemPanelWidth / (float) scaledScreenWidth)
                    : DEFAULT_HANDHELD_PANEL_WIDTH_RATIO;
            legacyHandheldItemPanelWidth = -1;
            changed = true;
        }
        if (legacyHandheldItemPanelHeight >= 0 && scaledScreenHeight > 0) {
            int maximum = Math.max(HandheldItemTracker.ROW_HEIGHT, scaledScreenHeight - 4);
            handheldItemPanelHeightRatio = legacyHandheldItemPanelHeight <= maximum
                    ? normalizeHandheldPanelRatio(
                    (float) legacyHandheldItemPanelHeight / (float) scaledScreenHeight)
                    : DEFAULT_HANDHELD_PANEL_HEIGHT_RATIO;
            legacyHandheldItemPanelHeight = -1;
            changed = true;
        }
        return changed;
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
