package com.gtbstructures;

import com.gtbstructures.chat.ChatCooldownCounter;
import com.gtbstructures.chat.ChatHeader;
import com.gtbstructures.chat.GuessChatAnnotator;
import com.gtbstructures.chat.TabNameCompleter;
import com.gtbstructures.commands.CommandGtb;
import com.gtbstructures.diagnostics.RawPacketRecorder;
import com.gtbstructures.gtw.CommandGtw;
import com.gtbstructures.gtw.CommandGuess;
import com.gtbstructures.gtw.GtwGame;
import com.gtbstructures.hints.HintOverlay;
import com.gtbstructures.hints.ShowAllMatching;
import com.gtbstructures.hotbar.SavedHotbars;
import com.gtbstructures.items.HandheldItemTracker;
import com.gtbstructures.items.ModernDragonHeadBlockPatch;
import com.gtbstructures.items.ModernEquipmentItemPatch;
import com.gtbstructures.items.ModernEquipmentRenderPatch;
import com.gtbstructures.items.ModernSpawnEggItemRenderPatch;
import com.gtbstructures.overlay.HudPosition;
import com.gtbstructures.overlay.HudPanelScale;
import com.gtbstructures.overlay.WorldEnvironmentOverlay;
import com.gtbstructures.player.BbRankDisplayRepair;
import com.gtbstructures.player.BuilderLeaderboardFallback;
import com.gtbstructures.player.PlayerListPacketListener;
import com.gtbstructures.scoreboard.CustomScoreboard;
import com.gtbstructures.scoreboard.GameTracker;
import com.gtbstructures.scoreboard.HypixelSkinScoreboard;
import com.gtbstructures.scoreboard.InterfacePreview;
import com.gtbstructures.scoreboard.PreviewLayoutEditor;
import com.gtbstructures.scoreboard.ScoreboardConfig;
import com.gtbstructures.scoreboard.ScoreboardLayout;
import com.gtbstructures.sound.GuessSounds;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.stats.LangPatterns;
import com.gtbstructures.translation.ShortcutLanguageGroups;
import com.gtbstructures.translation.ShortcutReminder;
import com.gtbstructures.translation.ThemePointReveal;
import com.gtbstructures.translation.TranslationData;
import com.gtbstructures.update.UpdateChecker;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringUtils;
import net.minecraft.event.ClickEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.File;
import java.text.Normalizer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mod(modid = GtbStructures.MODID, name = GtbStructures.NAME, version = GtbStructures.VERSION)
public class GtbStructures {

    public static final String MODID = "gtbstructures";
    public static final String NAME = "GtbStructures";
    public static final String VERSION = UpdateChecker.CURRENT_VERSION;
    public static final float DEFAULT_AUTO_REQUEUE_DELAY_SECONDS = 5.0F;
    public static final float MAX_AUTO_REQUEUE_DELAY_SECONDS = 10.0F;
    public static final float DEFAULT_LRJ_DELAY_SECONDS = 0.0F;

    private static final Logger LOGGER = LogManager.getLogger("GtbStructures");

    /** Master switch. Missing legacy config entries intentionally migrate to enabled. */
    public static boolean modEnabled = true;
    public static boolean enabled = false;
    public static boolean trackingEnabled = true;
    public static boolean autoRequeue = false;
    public static float autoRequeueDelaySeconds = DEFAULT_AUTO_REQUEUE_DELAY_SECONDS;
    public static float lrjDelaySeconds = DEFAULT_LRJ_DELAY_SECONDS;
    public static boolean lrjBypassLobbyProtection = true;
    public static boolean updateNoticeEnabled = true;
    public static boolean endGameFireworkSounds = true;
    public static boolean showAllMatching = false;
    public static boolean showAllMatchingShowEnglish = false;
    public static boolean showAllMatchingShowCount = true;
    public static boolean themePointReveal = false;
    public static boolean themePointRevealShowAfterGuess = false;
    public static boolean shortcutReminder = true;
    public static boolean shortcutReminderShowNonAscii = false;
    public static boolean shortcutReminderShowExtendedLatin = true;
    public static boolean shortcutReminderShowCyrillic = true;
    public static boolean shortcutReminderShowHanzi = true;
    public static boolean shortcutReminderShowJapanese = true;
    public static boolean shortcutReminderShowHangul = true;
    public static boolean shortcutReminderShowAllShortcuts = false;
    public static boolean shortcutReminderShowMultiwords = true;
    public static boolean shortcutReminderShowAllMultiwords = false;

    public enum ShortcutNonAsciiCategory {
        EXTENDED_LATIN,
        CYRILLIC,
        HANZI,
        JAPANESE,
        HANGUL
    }

    public static boolean hintBackgroundOverlay = false;
    public static boolean hintStructureBackground = false;
    public static boolean handheldItemOverlay = false;
    public static boolean handheldItemBackground = false;
    public static boolean handheldItemCaptionName = true;
    public static boolean handheldItemCaptionId = true;
    public static boolean handheldItemCaptionMetadata = true;
    public static boolean handheldItemCaptionSpawnEggMob = true;
    public static boolean handheldItemVerticalLayout = true;
    public static boolean handheldItemVerticalNewestAtBottom = true;
    public static boolean handheldItemHorizontalNewestAtRight = true;
    public static boolean handheldItemHorizontalNewestAtBottom = true;
    public static boolean environmentOverlay = false;
    public static boolean environmentBackground = false;
    public static boolean environmentWeatherEnabled = true;
    public static boolean environmentBiomeEnabled = true;
    public static boolean environmentTimeEnabled = true;
    public static boolean environmentWeatherChatNotice = false;
    public static boolean environmentBiomeChatNotice = false;
    public static boolean environmentTimeChatNotice = false;
    public static boolean chatCooldownCounter = false;
    public static boolean chatCooldownTimerEnabled = true;
    public static boolean chatCooldownBarEnabled = false;
    public static boolean chatCooldownSfxEnabled = true;
    public static boolean chatCooldownProgressBeats = false;
    public static boolean chatCooldownBackground = true;
    public static boolean chatCooldownBarBackground = false;
    public static boolean gtwShowEnglish = false;
    public static boolean gtwShowCount = true;
    public static String gtwDifficulty = "all";
    public static boolean copyTranslationPrefix = true;
    public static boolean copyTranslationCaps = true;
    public static boolean guessPeek = true;
    public static boolean guessPeekShowLangCodes = true;
    public static String guessPeekDisplay = "hover";
    public static boolean scrambleGuess = false;
    public static boolean scrambleGuessFancy = false;
    public static boolean trollMode = false;
    public static boolean trollModeAutoDisable = false;
    public static String spawnEggSkin = "classic";
    public static String environmentTimeIconPreset = "clock";
    /** Default language code for /gtbs gtw start when no <language> argument is given.
     *  Initialised to the user's preferred language and updated on each successful start. */
    public static String gtwDefaultLang = "en";
    public static float posX = 0.5f;
    public static float posY = 0.8522013f;
    public static float handheldItemPosX = 0.0f;
    public static float handheldItemPosY = 0.03686636f;
    public static float environmentPosX = 0.15f;
    public static float environmentPosY = 0.52f;
    public static float chatCooldownPosX = 0.5f;
    public static float chatCooldownPosY = 0.76f;
    public static float hintStructureScale = HudPanelScale.DEFAULT;
    public static float chatCooldownScale = HudPanelScale.DEFAULT;
    public static float handheldItemScale = HudPanelScale.DEFAULT;
    public static float environmentScale = HudPanelScale.DEFAULT;
    public static int handheldItemMaxVisible = 6;
    public static final float DEFAULT_HANDHELD_ITEM_PANEL_WIDTH_RATIO = 0.328125f;
    public static final float DEFAULT_HANDHELD_ITEM_PANEL_HEIGHT_RATIO = 0.3323263f;
    /** Normalized against the scaled screen width. */
    public static float handheldItemPanelWidthRatio = DEFAULT_HANDHELD_ITEM_PANEL_WIDTH_RATIO;
    /** Normalized against the scaled screen height. */
    public static float handheldItemPanelHeightRatio = DEFAULT_HANDHELD_ITEM_PANEL_HEIGHT_RATIO;
    private static int legacyHandheldItemPanelWidth = -1;
    private static int legacyHandheldItemPanelHeight = -1;
    public static String colorNumbers = "\u00a7e";
    public static String colorHyphens = "\u00a77";
    public static String colorBrackets = "\u00a76";
    public static String chatCooldownBarColor = "\u00a7e";
    public static String chatCooldownTimerColor = "\u00a7e";
    /** Last explicitly-selected language code (e.g. "en", "pirate", "ru"). Used as the
     * starting language for new games so users who keep their client in a non-default
     * language don't get round 1 misclassified before any chat clue arrives. */
    public static String lastLangCode = "en";
    public static boolean previewQuickstartRan = false;

    /** Knob-bag for the custom GTB scoreboard panel. */
    public static final ScoreboardConfig sbConfig = new ScoreboardConfig();

    private static Configuration config;
    private static String currentStructure = "";
    private static long overlayStartTime = 0;
    private static final long OVERLAY_DURATION_MS = 2500;
    private static String currentActionbarHintText = "";
    private static long actionbarHintStartTime = 0;
    private static final long ACTIONBAR_HINT_DURATION_MS = 3000;
    private static final int HINT_BG_PAD_X = 2;
    private static final int HINT_BG_PAD_Y = 1;
    private static volatile long customScoreboardFallbackUntilMs = 0L;
    private static long lastCustomScoreboardFailureLogMs = 0L;

    // Tick throttle for scoreboard reading
    private int tickCounter = 0;

    // GUI deferred check (5 ticks after GuiChest opens)
    private int guiCheckCountdown = 0;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new Configuration(new File(event.getModConfigurationDirectory(), "GtbStructures.cfg"));
        loadConfig();
        ModernSpawnEggItemRenderPatch.install();
        BuilderLeaderboardFallback.INSTANCE.init(event.getModConfigurationDirectory());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        // New custom scoreboard: graft of GuessTheUtils. Subscribe the renderer to
        // the render-overlay event bus, and bind the tracker to GameStats's hook surface.
        MinecraftForge.EVENT_BUS.register(new com.gtbstructures.scoreboard.CustomScoreboard());
        MinecraftForge.EVENT_BUS.register(new com.gtbstructures.scoreboard.HypixelSkinScoreboard());
        MinecraftForge.EVENT_BUS.register(PlayerListPacketListener.INSTANCE);
        MinecraftForge.EVENT_BUS.register(BbRankDisplayRepair.INSTANCE);
        MinecraftForge.EVENT_BUS.register(RawPacketRecorder.INSTANCE);
        SavedHotbars.INSTANCE.init();
        MinecraftForge.EVENT_BUS.register(SavedHotbars.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ChatCooldownCounter.INSTANCE);
        MinecraftForge.EVENT_BUS.register(TabNameCompleter.INSTANCE);
        MinecraftForge.EVENT_BUS.register(InterfacePreview.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ModernEquipmentItemPatch.INSTANCE);
        MinecraftForge.EVENT_BUS.register(HandheldItemTracker.INSTANCE);
        MinecraftForge.EVENT_BUS.register(WorldEnvironmentOverlay.INSTANCE);
        MinecraftForge.EVENT_BUS.register(com.gtbstructures.scoreboard.PreviewLayoutEditor.INSTANCE);
        ModernEquipmentRenderPatch.install();
        ModernDragonHeadBlockPatch.install();
        Minecraft minecraft = Minecraft.getMinecraft();
        GameStats.INSTANCE.setAccountPlayerUuid(minecraft == null || minecraft.getSession() == null
                ? null : minecraft.getSession().getPlayerID());
        GameStats.INSTANCE.setGtbHooks(com.gtbstructures.scoreboard.GameTracker.INSTANCE);
        CommandGtb.register();
        CommandGtw.register();
        CommandGuess.register();
        GameStats.INSTANCE.setTrackingEnabled(trackingEnabled);
        GameStats.INSTANCE.setAutoRequeueDelaySeconds(autoRequeueDelaySeconds);
        GameStats.INSTANCE.setAutoRequeue(autoRequeue);
        // Archive the prior cache before refreshing translations for this instance.
        TranslationData.initAsync(Minecraft.getMinecraft().mcDataDir);
        BuilderLeaderboardFallback.INSTANCE.refreshFromHypixelAsync();
    }

    public static void loadConfig() {
        boolean removedLegacyRawPacketConfig = false;
        try {
            config.load();
            if (config.hasKey(Configuration.CATEGORY_GENERAL, "rawPacketLogEnabled")) {
                removedLegacyRawPacketConfig = config.getCategory(Configuration.CATEGORY_GENERAL)
                        .remove("rawPacketLogEnabled") != null;
            }

            modEnabled = !config.hasKey(Configuration.CATEGORY_GENERAL, "modEnabled")
                    || config.getBoolean("modEnabled", Configuration.CATEGORY_GENERAL, true,
                    "Whether all GTBS features are enabled");
            posX = config.getFloat("posX", Configuration.CATEGORY_GENERAL, 0.5f, 0f, 1f, "Horizontal position of hint overlay (0-1)");
            posY = config.getFloat("posY", Configuration.CATEGORY_GENERAL, 0.8522013f, 0f, 1f, "Vertical position of hint overlay (0-1)");
            enabled = config.getBoolean("enabled", Configuration.CATEGORY_GENERAL, false, "Whether the hint overlay is enabled");
            trackingEnabled = config.getBoolean("trackingEnabled", Configuration.CATEGORY_GENERAL, true, "Whether game stats tracking is enabled");
            autoRequeue = config.getBoolean("autoRequeue", Configuration.CATEGORY_GENERAL, false, "Whether to auto-requeue for GTB after a game ends");
            autoRequeueDelaySeconds = normalizeAutoRequeueDelaySeconds(config.getFloat(
                    "autoRequeueDelaySeconds", Configuration.CATEGORY_GENERAL,
                    DEFAULT_AUTO_REQUEUE_DELAY_SECONDS, 0F, MAX_AUTO_REQUEUE_DELAY_SECONDS,
                    "Seconds after the first final GTB roster capture before auto-requeueing (0.0-10.0)"));
            lrjDelaySeconds = normalizeLrjDelaySeconds(config.getFloat(
                    "lrjDelaySeconds", Configuration.CATEGORY_GENERAL,
                    DEFAULT_LRJ_DELAY_SECONDS, 0F, MAX_AUTO_REQUEUE_DELAY_SECONDS,
                    "Seconds to wait after /lrj is ready to rejoin (0.0-10.0)"));
            lrjBypassLobbyProtection = config.getBoolean("lrjBypassLobbyProtection",
                    Configuration.CATEGORY_GENERAL, true,
                    "Whether /lrj retries once after Hypixel's translated /l protection prompt");
            updateNoticeEnabled = config.getBoolean("updateNoticeEnabled", Configuration.CATEGORY_GENERAL, true, "Whether to check GitHub and show a notice when a newer GTBS version is available");
            endGameFireworkSounds = config.getBoolean("endGameFireworkSounds", Configuration.CATEGORY_GENERAL, true, "Whether to play vanilla game-end firework sounds");
            showAllMatching = config.getBoolean("showAllMatching", Configuration.CATEGORY_GENERAL, false, "Whether to show all themes matching the effective initial hint after a round resolves");
            showAllMatchingShowEnglish = config.getBoolean("showAllMatchingShowEnglish", Configuration.CATEGORY_GENERAL, false, "When showAllMatching is on and the language is not English, also show the English theme(s) next to each translation");
            showAllMatchingShowCount = config.getBoolean("showAllMatchingShowCount", Configuration.CATEGORY_GENERAL, true, "Whether Show All Matching output ends with the total number of displayed themes");
            themePointReveal = config.getBoolean("themePointReveal", Configuration.CATEGORY_GENERAL, false, "Whether to reveal an exact current GTB theme point value in chat");
            themePointRevealShowAfterGuess = config.getBoolean("themePointRevealShowAfterGuess", Configuration.CATEGORY_GENERAL, false, "Whether theme point reveal may still fire after the complete theme is revealed");
            shortcutReminder = config.getBoolean("shortcutReminder", Configuration.CATEGORY_GENERAL, true, "Whether to show accepted shortcut guesses after each revealed GTB theme");
            shortcutReminderShowNonAscii = config.getBoolean("shortcutReminderShowNonAscii", Configuration.CATEGORY_GENERAL, false, "Whether shortcut reminders may include enabled non-ASCII categories");
            shortcutReminderShowExtendedLatin = config.getBoolean("shortcutReminderShowExtendedLatin", Configuration.CATEGORY_GENERAL, true, "Whether shortcut reminders include non-decomposable extended Latin letters such as ae, o-stroke, and eszett");
            shortcutReminderShowCyrillic = config.getBoolean("shortcutReminderShowCyrillic", Configuration.CATEGORY_GENERAL, true, "Whether shortcut reminders include Cyrillic");
            shortcutReminderShowHanzi = config.getBoolean("shortcutReminderShowHanzi", Configuration.CATEGORY_GENERAL, true, "Whether shortcut reminders include Chinese Hanzi");
            shortcutReminderShowJapanese = config.getBoolean("shortcutReminderShowJapanese", Configuration.CATEGORY_GENERAL, true, "Whether shortcut reminders include Japanese Kanji and Kana");
            shortcutReminderShowHangul = config.getBoolean("shortcutReminderShowHangul", Configuration.CATEGORY_GENERAL, true, "Whether shortcut reminders include Korean Hangul");
            shortcutReminderShowAllShortcuts = config.getBoolean("shortcutReminderShowAllShortcuts", Configuration.CATEGORY_GENERAL, false, "Whether shortcut reminders show every shortest accepted shortcut instead of only the selected shortcut");
            shortcutReminderShowMultiwords = config.getBoolean("shortcutReminderShowMultiwords", Configuration.CATEGORY_GENERAL, true, "Whether shortcut reminders also show accepted multiword metadata after each revealed GTB theme");
            shortcutReminderShowAllMultiwords = config.getBoolean("shortcutReminderShowAllMultiwords", Configuration.CATEGORY_GENERAL, false, "Whether multiword reminders show every accepted multiword per theme set instead of only the shortest one");
            String languageGroupsJson = config.getString(
                    "shortcutReminderLanguageGroups", Configuration.CATEGORY_GENERAL,
                    ShortcutLanguageGroups.DEFAULT_SERIALIZED,
                    "Versioned JSON configuration for independent shortcut language groups");
            String languageGroupsBackup = config.getString(
                    "shortcutReminderLanguageGroupsBackup", Configuration.CATEGORY_GENERAL,
                    ShortcutLanguageGroups.DEFAULT_SERIALIZED,
                    "Last recoverable shortcut language-group JSON configuration");
            ShortcutLanguageGroups.LoadReport languageGroupsReport =
                    ShortcutLanguageGroups.load(languageGroupsJson, languageGroupsBackup);
            for (String warning : languageGroupsReport.warnings) {
                LOGGER.warn(warning);
            }
            String normalizedLanguageGroups = ShortcutLanguageGroups.serialize();
            if (!normalizedLanguageGroups.equals(languageGroupsJson)) {
                config.get(Configuration.CATEGORY_GENERAL,
                        "shortcutReminderLanguageGroups",
                        ShortcutLanguageGroups.DEFAULT_SERIALIZED)
                        .set(normalizedLanguageGroups);
            }
            hintBackgroundOverlay = config.getBoolean("hintBackgroundOverlay", Configuration.CATEGORY_GENERAL, false, "Whether to draw the scoreboard-style background behind GTB actionbar hint text");
            hintStructureBackground = config.getBoolean("hintStructureBackground", Configuration.CATEGORY_GENERAL, hintBackgroundOverlay, "Whether to draw the scoreboard-style background behind the GTB hint structure overlay");
            handheldItemOverlay = config.getBoolean("handheldItemOverlay", Configuration.CATEGORY_GENERAL, false, "Whether to draw the builder handheld item history overlay during GTB rounds");
            handheldItemBackground = config.getBoolean("handheldItemBackground", Configuration.CATEGORY_GENERAL, false, "Whether to draw a scoreboard-style background behind the builder handheld item history overlay");
            handheldItemCaptionName = config.getBoolean("handheldItemCaptionName", Configuration.CATEGORY_GENERAL, true, "Whether the handheld item overlay shows item names");
            handheldItemCaptionId = config.getBoolean("handheldItemCaptionId", Configuration.CATEGORY_GENERAL, true, "Whether the handheld item overlay shows item IDs");
            handheldItemCaptionMetadata = config.getBoolean("handheldItemCaptionMetadata", Configuration.CATEGORY_GENERAL, true, "Whether the handheld item overlay shows item metadata");
            handheldItemCaptionSpawnEggMob = config.getBoolean("handheldItemCaptionSpawnEggMob", Configuration.CATEGORY_GENERAL, true, "Whether the handheld item overlay shows spawn egg mob names");
            handheldItemVerticalLayout = config.getBoolean("handheldItemVerticalLayout", Configuration.CATEGORY_GENERAL, true, "Whether the handheld item overlay uses a vertical caption layout");
            handheldItemVerticalNewestAtBottom = config.getBoolean("handheldItemVerticalNewestAtBottom", Configuration.CATEGORY_GENERAL, true, "Whether the newest vertical handheld item is placed at the bottom");
            handheldItemHorizontalNewestAtRight = config.getBoolean("handheldItemHorizontalNewestAtRight", Configuration.CATEGORY_GENERAL, true, "Whether the newest horizontal handheld item is placed at the right");
            handheldItemHorizontalNewestAtBottom = config.getBoolean("handheldItemHorizontalNewestAtBottom", Configuration.CATEGORY_GENERAL, true, "Whether the newest horizontal handheld item is placed at the bottom");
            environmentOverlay = config.getBoolean("environmentOverlay", Configuration.CATEGORY_GENERAL, false, "Whether to draw the GTB weather, biome, and time overlay during active games");
            environmentBackground = config.getBoolean("environmentBackground", Configuration.CATEGORY_GENERAL, false, "Whether to draw a scoreboard-style background behind the GTB environment overlay");
            environmentWeatherEnabled = config.getBoolean("environmentWeatherEnabled", Configuration.CATEGORY_GENERAL, true, "Whether the GTB environment overlay shows weather");
            environmentBiomeEnabled = config.getBoolean("environmentBiomeEnabled", Configuration.CATEGORY_GENERAL, true, "Whether the GTB environment overlay shows biome");
            environmentTimeEnabled = config.getBoolean("environmentTimeEnabled", Configuration.CATEGORY_GENERAL, true, "Whether the GTB environment overlay shows time");
            environmentWeatherChatNotice = config.getBoolean("environmentWeatherChatNotice", Configuration.CATEGORY_GENERAL, false, "Whether weather changes are announced in chat during active GTB rounds");
            environmentBiomeChatNotice = config.getBoolean("environmentBiomeChatNotice", Configuration.CATEGORY_GENERAL, false, "Whether biome changes are announced in chat during active GTB rounds");
            environmentTimeChatNotice = config.getBoolean("environmentTimeChatNotice", Configuration.CATEGORY_GENERAL, false, "Whether time changes of at least three in-game hours are announced in chat during active GTB rounds");
            environmentTimeIconPreset = WorldEnvironmentOverlay.normalizeTimeIconPreset(config.getString("environmentTimeIconPreset", Configuration.CATEGORY_GENERAL, "clock", "Time icon preset: \"clock\" uses the vanilla clock item, \"blocks\" uses colored blocks where each block represents a 3-hour time period."));
            chatCooldownCounter = config.getBoolean("chatCooldownCounter", Configuration.CATEGORY_GENERAL, false, "Whether to show a GTB chat cooldown counter after accepted outbound chat packets");
            chatCooldownTimerEnabled = config.getBoolean("chatCooldownTimerEnabled", Configuration.CATEGORY_GENERAL, true, "Whether the GTB chat cooldown counter shows timer text");
            chatCooldownBarEnabled = config.getBoolean("chatCooldownBarEnabled", Configuration.CATEGORY_GENERAL, false, "Whether the GTB chat cooldown counter shows the progress bar");
            chatCooldownSfxEnabled = config.getBoolean("chatCooldownSfxEnabled", Configuration.CATEGORY_GENERAL, true, "Whether to play a sound when the GTB chat cooldown ends");
            chatCooldownProgressBeats = config.getBoolean("chatCooldownProgressBeats", Configuration.CATEGORY_GENERAL, false, "Whether cooldown SFX includes quieter beats at the elapsed 1s and 2s marks");
            chatCooldownBackground = config.getBoolean("chatCooldownBackground", Configuration.CATEGORY_GENERAL, true, "Whether to draw a scoreboard-style background behind the chat cooldown timer text");
            chatCooldownBarBackground = config.getBoolean("chatCooldownBarBackground", Configuration.CATEGORY_GENERAL, false, "Whether to draw a scoreboard-style background behind the chat cooldown progress bar");
            handheldItemPosX = config.getFloat("handheldItemPosX", Configuration.CATEGORY_GENERAL, 0.0f, 0f, 1f, "Horizontal center of the builder handheld item overlay (0-1)");
            handheldItemPosY = config.getFloat("handheldItemPosY", Configuration.CATEGORY_GENERAL, 0.03686636f, 0f, 1f, "Vertical center of the builder handheld item overlay (0-1)");
            environmentPosX = config.getFloat("environmentPosX", Configuration.CATEGORY_GENERAL, 0.15f, 0f, 1f, "Horizontal center of the GTB weather/biome overlay (0-1)");
            environmentPosY = config.getFloat("environmentPosY", Configuration.CATEGORY_GENERAL, 0.52f, 0f, 1f, "Vertical bottom of the GTB weather/biome overlay (0-1)");
            chatCooldownPosX = config.getFloat("chatCooldownPosX", Configuration.CATEGORY_GENERAL, 0.5f, 0f, 1f, "Horizontal center of the chat cooldown counter (0-1)");
            chatCooldownPosY = config.getFloat("chatCooldownPosY", Configuration.CATEGORY_GENERAL, 0.76f, 0f, 1f, "Vertical center of the chat cooldown counter (0-1)");
            hintStructureScale = HudPanelScale.normalize(config.getFloat("hintStructureScale", Configuration.CATEGORY_GENERAL, HudPanelScale.DEFAULT, HudPanelScale.MIN, HudPanelScale.MAX, "Scale of the hint structure panel (0.5-1.5)"));
            chatCooldownScale = HudPanelScale.normalize(config.getFloat("chatCooldownScale", Configuration.CATEGORY_GENERAL, HudPanelScale.DEFAULT, HudPanelScale.MIN, HudPanelScale.MAX, "Scale of the chat cooldown panel (0.5-1.5)"));
            handheldItemScale = HudPanelScale.normalize(config.getFloat("handheldItemScale", Configuration.CATEGORY_GENERAL, HudPanelScale.DEFAULT, HudPanelScale.MIN, HudPanelScale.MAX, "Scale of the handheld item panel (0.5-1.5)"));
            environmentScale = HudPanelScale.normalize(config.getFloat("environmentScale", Configuration.CATEGORY_GENERAL, HudPanelScale.DEFAULT, HudPanelScale.MIN, HudPanelScale.MAX, "Scale of the environment panel (0.5-1.5)"));
            handheldItemMaxVisible = config.getInt("handheldItemMaxVisible", Configuration.CATEGORY_GENERAL, 6, 2, 10, "Maximum builder handheld items shown in the overlay. The full round history is still recorded.");
            handheldItemMaxVisible = HandheldItemTracker.clampMaxVisible(handheldItemMaxVisible);
            if (config.hasKey(Configuration.CATEGORY_GENERAL, "handheldItemPanelWidthRatio")) {
                handheldItemPanelWidthRatio = HandheldItemTracker.clampPanelRatio(config.getFloat("handheldItemPanelWidthRatio", Configuration.CATEGORY_GENERAL, DEFAULT_HANDHELD_ITEM_PANEL_WIDTH_RATIO, 0f, 1f, "Width of the handheld item overlay panel as a scaled-screen ratio (0-1)"));
            } else if (config.hasKey(Configuration.CATEGORY_GENERAL, "handheldItemPanelWidth")) {
                legacyHandheldItemPanelWidth = config.getInt("handheldItemPanelWidth", Configuration.CATEGORY_GENERAL, 198, 1, Integer.MAX_VALUE, "Legacy handheld item panel width in GUI pixels");
            }
            if (config.hasKey(Configuration.CATEGORY_GENERAL, "handheldItemPanelHeightRatio")) {
                handheldItemPanelHeightRatio = HandheldItemTracker.clampPanelRatio(config.getFloat("handheldItemPanelHeightRatio", Configuration.CATEGORY_GENERAL, DEFAULT_HANDHELD_ITEM_PANEL_HEIGHT_RATIO, 0f, 1f, "Height of the handheld item overlay panel as a scaled-screen ratio (0-1)"));
            } else if (config.hasKey(Configuration.CATEGORY_GENERAL, "handheldItemPanelHeight")) {
                legacyHandheldItemPanelHeight = config.getInt("handheldItemPanelHeight", Configuration.CATEGORY_GENERAL, 108, 1, Integer.MAX_VALUE, "Legacy handheld item panel height in GUI pixels");
            }
            gtwShowEnglish = config.getBoolean("gtwShowEnglish", Configuration.CATEGORY_GENERAL, false, "Whether /gtbs gtw reveal/list lines show the English theme name(s) next to each translation");
            gtwShowCount = config.getBoolean("gtwShowCount", Configuration.CATEGORY_GENERAL, true, "Whether /gtbs gtw matching-theme output ends with the total number of displayed themes");
            gtwDifficulty = GtwGame.normalizeDifficulty(config.getString("gtwDifficulty", Configuration.CATEGORY_GENERAL, "all", "GTW theme difficulty: easy, medium, hard, or all. Difficulty follows the space-inclusive English theme length point value."));
            copyTranslationPrefix = config.getBoolean("copyTranslationPrefix", Configuration.CATEGORY_GENERAL, true, "Whether /gtbs copytranslation includes the \"The theme is \" prefix in copied messages");
            copyTranslationCaps = config.getBoolean("copyTranslationCaps", Configuration.CATEGORY_GENERAL, true, "Whether /gtbs copytranslation capitalizes the first letter of every copied translation");
            guessPeek = config.getBoolean("guessPeek", Configuration.CATEGORY_GENERAL, true, "Whether GTB player guesses get hover/click English-theme annotations");
            guessPeekShowLangCodes = config.getBoolean("guessPeekShowLangCodes", Configuration.CATEGORY_GENERAL, true, "Whether guesspeek hover text includes source language codes");
            guessPeekDisplay = normalizeGuessPeekDisplay(config.getString("guessPeekDisplay", Configuration.CATEGORY_GENERAL, "hover", "Guess peek result display: hover or suffix"));
            scrambleGuess = config.getBoolean("scrambleGuess", Configuration.CATEGORY_GENERAL, false, "Whether valid outbound GTB guesses are replaced with equivalent translations");
            scrambleGuessFancy = config.getBoolean("scrambleGuessFancy", Configuration.CATEGORY_GENERAL, false, "Whether scrambled guesses may use random GTB-accepted spelling modifications");
            trollMode = config.getBoolean("trollMode", Configuration.CATEGORY_GENERAL, false, "Whether valid outbound GTB guesses are changed into visually similar invalid guesses");
            trollModeAutoDisable = config.getBoolean("trollModeAutoDisable", Configuration.CATEGORY_GENERAL, false, "Whether Troll Mode automatically turns off when a GTB game starts");
            loadGuessSoundsConfig();
            if (!ChatHeader.set(config.getString("chatHeader", Configuration.CATEGORY_GENERAL,
                    ChatHeader.DEFAULT_RAW, "Custom formatting-aware prefix for GTBS chat notices"))) {
                ChatHeader.reset();
            }
            CommandGtb.refreshHeader();
            spawnEggSkin = normalizeSpawnEggSkin(config.getString("spawnEggSkin", Configuration.CATEGORY_GENERAL, "classic", "Spawn egg equipment skin: \"classic\" = vanilla 1.8 eggs plus 1.17-style template for modern accepted eggs, \"new\" = extracted 1.21 per-mob egg sprites."));
            gtwDefaultLang = config.getString("gtwDefaultLang", Configuration.CATEGORY_GENERAL, "en", "Default language for /gtbs gtw start when no <language> argument is given. Auto-updates to the last game's language.");
            colorNumbers = config.getString("colorNumbers", Configuration.CATEGORY_GENERAL, "\u00a7e", "Color code for numbers");
            colorHyphens = config.getString("colorHyphens", Configuration.CATEGORY_GENERAL, "\u00a77", "Color code for hyphens");
            colorBrackets = config.getString("colorBrackets", Configuration.CATEGORY_GENERAL, "\u00a76", "Color code for brackets");
            chatCooldownBarColor = normalizeMinecraftColorCode(config.getString("chatCooldownBarColor", Configuration.CATEGORY_GENERAL, "\u00a7e", "Minecraft color code for the chat cooldown progress bar"), "\u00a7e");
            chatCooldownTimerColor = normalizeMinecraftColorCode(config.getString("chatCooldownTimerColor", Configuration.CATEGORY_GENERAL, "\u00a7e", "Minecraft color code for the chat cooldown timer text"), "\u00a7e");
            lastLangCode = config.getString("lastLangCode", Configuration.CATEGORY_GENERAL, "en", "Last explicitly-selected GTB language code (e.g. en, pirate, ru). Used as the starting language for new games.");
            previewQuickstartRan = config.getBoolean("previewQuickstartRan", Configuration.CATEGORY_GENERAL, false, "Whether /gtbs preview has been successfully run at least once");
            if (shortcutReminderShowNonAscii && !hasShortcutNonAsciiCategoryEnabled()) {
                shortcutReminderShowNonAscii = false;
                config.get(Configuration.CATEGORY_GENERAL, "shortcutReminderShowNonAscii", false).set(false);
            }

            boolean scoreboardEnabled = config.getBoolean("enabled", "scoreboard", false, "Whether the custom GTB scoreboard panel is enabled. When on, the vanilla sidebar is hidden during ROUND_PRE/BUILD/END.");
            sbConfig.heightOffset = config.getInt("heightOffset", "scoreboard", 9, -200, 200, "Pixels to lift the panel above vertical center");
            String scoreboardSkin = config.getString("skin", "scoreboard", "vanilla", "Visual style: \"vanilla\" = server sidebar, \"default\" = fully custom panel, \"hypixel\" = vanilla sidebar with the leaderboard rows replaced by our custom rows.");
            sbConfig.backgroundOpacity = config.getFloat("backgroundOpacity", "scoreboard", ScoreboardConfig.DEFAULT_BACKGROUND_OPACITY, 0f, 1f, "Custom scoreboard background opacity from 0.0 to 1.0.");
            boolean hasHighlightIntensity = config.hasKey("scoreboard", "highlightIntensity");
            boolean pureHighlightBenchmark = config.getBoolean("highlightIntensityPureBenchmark", "scoreboard", false,
                    "Internal migration flag: true when highlightIntensity uses full-color opacity as 1.0.");
            float rawHighlightIntensity = config.getFloat("highlightIntensity", "scoreboard",
                    ScoreboardConfig.DEFAULT_HIGHLIGHT_INTENSITY, 0f, 1f,
                    "Custom scoreboard row highlight intensity from 0.0 to 1.0. 1.0 is fully opaque.");
            sbConfig.highlightIntensity = hasHighlightIntensity && !pureHighlightBenchmark
                    ? ScoreboardConfig.migrateLegacyHighlightIntensity(rawHighlightIntensity)
                    : rawHighlightIntensity;
            if (!pureHighlightBenchmark) {
                config.get("scoreboard", "highlightIntensity", ScoreboardConfig.DEFAULT_HIGHLIGHT_INTENSITY)
                        .set(sbConfig.highlightIntensity);
                config.get("scoreboard", "highlightIntensityPureBenchmark", true).set(true);
            }
            sbConfig.showPointsGainedInRound = ScoreboardConfig.parseOption(config.getString("showPointsGainedInRound", "scoreboard", "on", "Whether to show current-round score deltas: off, expanded, or on."));
            sbConfig.showPlaces = ScoreboardConfig.parseOption(config.getString("showPlaces", "scoreboard", "off", "Whether to show leaderboard place numbers: off, expanded, or on."));
            sbConfig.showTitles = ScoreboardConfig.parseOption(config.getString("showTitles", "scoreboard", "expanded", "Whether to show Build Battle titles: off, expanded, or on."));
            sbConfig.showEmblems = ScoreboardConfig.parseOption(config.getString("showEmblems", "scoreboard", "on", "Whether to show player emblems: off, expanded, or on."));
            sbConfig.defaultExplicitlyShowBuilders = config.getBoolean("defaultExplicitlyShowBuilders", "scoreboard", false,
                    "Whether the Default custom scoreboard shows an explicit Builder row.");
            sbConfig.hypixelShowRounds = ScoreboardConfig.parseOption(config.getString("hypixelShowRounds", "scoreboard", "off",
                    "Whether the Hypixel custom scoreboard shows round progress beside its Builder label: off, expanded, or on."));
            sbConfig.textShadow = config.getBoolean("textShadow", "scoreboard", true, "Whether custom scoreboard text is drawn with a shadow.");
            sbConfig.leftPlayersAtBottom = config.getBoolean("leftPlayersAtBottom", "scoreboard", true,
                    "Whether confirmed leavers are grouped at the bottom of custom scoreboards.");
            sbConfig.clampVisuals();
            scoreboardSkin = ScoreboardConfig.normalizeSkin(scoreboardSkin);
            if (!scoreboardEnabled || "vanilla".equals(scoreboardSkin)) {
                sbConfig.setSkin("vanilla");
            } else {
                sbConfig.setSkin(scoreboardSkin);
            }

        } catch (Exception e) {
            LOGGER.warn("Failed to load config!", e);
        } finally {
            if (removedLegacyRawPacketConfig || config.hasChanged()) config.save();
        }
    }

    public static void saveConfig() {
        try {
            config.get(Configuration.CATEGORY_GENERAL, "modEnabled", true).set(modEnabled);
            config.get(Configuration.CATEGORY_GENERAL, "posX", posX).set(posX);
            config.get(Configuration.CATEGORY_GENERAL, "posY", posY).set(posY);
            config.get(Configuration.CATEGORY_GENERAL, "enabled", enabled).set(enabled);
            config.get(Configuration.CATEGORY_GENERAL, "trackingEnabled", trackingEnabled).set(trackingEnabled);
            config.get(Configuration.CATEGORY_GENERAL, "autoRequeue", autoRequeue).set(autoRequeue);
            autoRequeueDelaySeconds = normalizeAutoRequeueDelaySeconds(autoRequeueDelaySeconds);
            config.get(Configuration.CATEGORY_GENERAL, "autoRequeueDelaySeconds",
                    DEFAULT_AUTO_REQUEUE_DELAY_SECONDS).set(autoRequeueDelaySeconds);
            lrjDelaySeconds = normalizeLrjDelaySeconds(lrjDelaySeconds);
            config.get(Configuration.CATEGORY_GENERAL, "lrjDelaySeconds",
                    DEFAULT_LRJ_DELAY_SECONDS).set(lrjDelaySeconds);
            config.get(Configuration.CATEGORY_GENERAL, "lrjBypassLobbyProtection", true)
                    .set(lrjBypassLobbyProtection);
            config.get(Configuration.CATEGORY_GENERAL, "updateNoticeEnabled", true).set(updateNoticeEnabled);
            config.get(Configuration.CATEGORY_GENERAL, "endGameFireworkSounds", true).set(endGameFireworkSounds);
            config.get(Configuration.CATEGORY_GENERAL, "showAllMatching", showAllMatching).set(showAllMatching);
            config.get(Configuration.CATEGORY_GENERAL, "showAllMatchingShowEnglish", showAllMatchingShowEnglish).set(showAllMatchingShowEnglish);
            config.get(Configuration.CATEGORY_GENERAL, "showAllMatchingShowCount", true).set(showAllMatchingShowCount);
            config.get(Configuration.CATEGORY_GENERAL, "themePointReveal", false).set(themePointReveal);
            config.get(Configuration.CATEGORY_GENERAL, "themePointRevealShowAfterGuess", false).set(themePointRevealShowAfterGuess);
            config.get(Configuration.CATEGORY_GENERAL, "shortcutReminder", true).set(shortcutReminder);
            config.get(Configuration.CATEGORY_GENERAL, "shortcutReminderShowNonAscii", false).set(shortcutReminderShowNonAscii);
            config.get(Configuration.CATEGORY_GENERAL, "shortcutReminderShowExtendedLatin", true).set(shortcutReminderShowExtendedLatin);
            config.get(Configuration.CATEGORY_GENERAL, "shortcutReminderShowCyrillic", true).set(shortcutReminderShowCyrillic);
            config.get(Configuration.CATEGORY_GENERAL, "shortcutReminderShowHanzi", true).set(shortcutReminderShowHanzi);
            config.get(Configuration.CATEGORY_GENERAL, "shortcutReminderShowJapanese", true).set(shortcutReminderShowJapanese);
            config.get(Configuration.CATEGORY_GENERAL, "shortcutReminderShowHangul", true).set(shortcutReminderShowHangul);
            config.get(Configuration.CATEGORY_GENERAL, "shortcutReminderShowAllShortcuts", false).set(shortcutReminderShowAllShortcuts);
            config.get(Configuration.CATEGORY_GENERAL, "shortcutReminderShowMultiwords", true).set(shortcutReminderShowMultiwords);
            config.get(Configuration.CATEGORY_GENERAL, "shortcutReminderShowAllMultiwords", false).set(shortcutReminderShowAllMultiwords);
            String serializedLanguageGroups = ShortcutLanguageGroups.serialize();
            Property languageGroupsProperty = config.get(Configuration.CATEGORY_GENERAL,
                    "shortcutReminderLanguageGroups",
                    ShortcutLanguageGroups.DEFAULT_SERIALIZED);
            String previousLanguageGroups = languageGroupsProperty.getString();
            if (!serializedLanguageGroups.equals(previousLanguageGroups)
                    && ShortcutLanguageGroups.isValidSerialized(previousLanguageGroups)) {
                config.get(Configuration.CATEGORY_GENERAL,
                        "shortcutReminderLanguageGroupsBackup",
                        ShortcutLanguageGroups.DEFAULT_SERIALIZED)
                        .set(previousLanguageGroups);
            }
            languageGroupsProperty.set(serializedLanguageGroups);
            config.get(Configuration.CATEGORY_GENERAL, "hintBackgroundOverlay", hintBackgroundOverlay).set(hintBackgroundOverlay);
            config.get(Configuration.CATEGORY_GENERAL, "hintStructureBackground", false).set(hintStructureBackground);
            config.get(Configuration.CATEGORY_GENERAL, "handheldItemOverlay", handheldItemOverlay).set(handheldItemOverlay);
            config.get(Configuration.CATEGORY_GENERAL, "handheldItemBackground", false).set(handheldItemBackground);
            config.get(Configuration.CATEGORY_GENERAL, "handheldItemCaptionName", true).set(handheldItemCaptionName);
            config.get(Configuration.CATEGORY_GENERAL, "handheldItemCaptionId", true).set(handheldItemCaptionId);
            config.get(Configuration.CATEGORY_GENERAL, "handheldItemCaptionMetadata", true).set(handheldItemCaptionMetadata);
            config.get(Configuration.CATEGORY_GENERAL, "handheldItemCaptionSpawnEggMob", true).set(handheldItemCaptionSpawnEggMob);
            config.get(Configuration.CATEGORY_GENERAL, "handheldItemVerticalLayout", true).set(handheldItemVerticalLayout);
            config.get(Configuration.CATEGORY_GENERAL, "handheldItemVerticalNewestAtBottom", true).set(handheldItemVerticalNewestAtBottom);
            config.get(Configuration.CATEGORY_GENERAL, "handheldItemHorizontalNewestAtRight", true).set(handheldItemHorizontalNewestAtRight);
            config.get(Configuration.CATEGORY_GENERAL, "handheldItemHorizontalNewestAtBottom", true).set(handheldItemHorizontalNewestAtBottom);
            config.get(Configuration.CATEGORY_GENERAL, "environmentOverlay", false).set(environmentOverlay);
            config.get(Configuration.CATEGORY_GENERAL, "environmentBackground", false).set(environmentBackground);
            config.get(Configuration.CATEGORY_GENERAL, "environmentWeatherEnabled", true).set(environmentWeatherEnabled);
            config.get(Configuration.CATEGORY_GENERAL, "environmentBiomeEnabled", true).set(environmentBiomeEnabled);
            config.get(Configuration.CATEGORY_GENERAL, "environmentTimeEnabled", true).set(environmentTimeEnabled);
            config.get(Configuration.CATEGORY_GENERAL, "environmentWeatherChatNotice", false).set(environmentWeatherChatNotice);
            config.get(Configuration.CATEGORY_GENERAL, "environmentBiomeChatNotice", false).set(environmentBiomeChatNotice);
            config.get(Configuration.CATEGORY_GENERAL, "environmentTimeChatNotice", false).set(environmentTimeChatNotice);
            config.get(Configuration.CATEGORY_GENERAL, "environmentTimeIconPreset", "clock").set(WorldEnvironmentOverlay.normalizeTimeIconPreset(environmentTimeIconPreset));
            config.get(Configuration.CATEGORY_GENERAL, "chatCooldownCounter", false).set(chatCooldownCounter);
            config.get(Configuration.CATEGORY_GENERAL, "chatCooldownTimerEnabled", true).set(chatCooldownTimerEnabled);
            config.get(Configuration.CATEGORY_GENERAL, "chatCooldownBarEnabled", false).set(chatCooldownBarEnabled);
            config.get(Configuration.CATEGORY_GENERAL, "chatCooldownSfxEnabled", true).set(chatCooldownSfxEnabled);
            config.get(Configuration.CATEGORY_GENERAL, "chatCooldownProgressBeats", false).set(chatCooldownProgressBeats);
            config.get(Configuration.CATEGORY_GENERAL, "chatCooldownBackground", true).set(chatCooldownBackground);
            config.get(Configuration.CATEGORY_GENERAL, "chatCooldownBarBackground", false).set(chatCooldownBarBackground);
            config.get(Configuration.CATEGORY_GENERAL, "handheldItemPosX", handheldItemPosX).set(handheldItemPosX);
            config.get(Configuration.CATEGORY_GENERAL, "handheldItemPosY", handheldItemPosY).set(handheldItemPosY);
            config.get(Configuration.CATEGORY_GENERAL, "environmentPosX", environmentPosX).set(environmentPosX);
            config.get(Configuration.CATEGORY_GENERAL, "environmentPosY", environmentPosY).set(environmentPosY);
            config.get(Configuration.CATEGORY_GENERAL, "chatCooldownPosX", chatCooldownPosX).set(chatCooldownPosX);
            config.get(Configuration.CATEGORY_GENERAL, "chatCooldownPosY", chatCooldownPosY).set(chatCooldownPosY);
            config.get(Configuration.CATEGORY_GENERAL, "hintStructureScale", HudPanelScale.DEFAULT).set(HudPanelScale.normalize(hintStructureScale));
            config.get(Configuration.CATEGORY_GENERAL, "chatCooldownScale", HudPanelScale.DEFAULT).set(HudPanelScale.normalize(chatCooldownScale));
            config.get(Configuration.CATEGORY_GENERAL, "handheldItemScale", HudPanelScale.DEFAULT).set(HudPanelScale.normalize(handheldItemScale));
            config.get(Configuration.CATEGORY_GENERAL, "environmentScale", HudPanelScale.DEFAULT).set(HudPanelScale.normalize(environmentScale));
            config.get(Configuration.CATEGORY_GENERAL, "handheldItemMaxVisible", 6).set(HandheldItemTracker.clampMaxVisible(handheldItemMaxVisible));
            config.get(Configuration.CATEGORY_GENERAL, "handheldItemPanelWidthRatio", DEFAULT_HANDHELD_ITEM_PANEL_WIDTH_RATIO).set(HandheldItemTracker.clampPanelRatio(handheldItemPanelWidthRatio));
            config.get(Configuration.CATEGORY_GENERAL, "handheldItemPanelHeightRatio", DEFAULT_HANDHELD_ITEM_PANEL_HEIGHT_RATIO).set(HandheldItemTracker.clampPanelRatio(handheldItemPanelHeightRatio));
            config.get(Configuration.CATEGORY_GENERAL, "gtwShowEnglish", gtwShowEnglish).set(gtwShowEnglish);
            config.get(Configuration.CATEGORY_GENERAL, "gtwShowCount", true).set(gtwShowCount);
            config.get(Configuration.CATEGORY_GENERAL, "gtwDifficulty", "all").set(GtwGame.normalizeDifficulty(gtwDifficulty));
            config.get(Configuration.CATEGORY_GENERAL, "copyTranslationPrefix", copyTranslationPrefix).set(copyTranslationPrefix);
            config.get(Configuration.CATEGORY_GENERAL, "copyTranslationCaps", true).set(copyTranslationCaps);
            config.get(Configuration.CATEGORY_GENERAL, "guessPeek", true).set(guessPeek);
            config.get(Configuration.CATEGORY_GENERAL, "guessPeekShowLangCodes", true).set(guessPeekShowLangCodes);
            config.get(Configuration.CATEGORY_GENERAL, "guessPeekDisplay", "hover").set(normalizeGuessPeekDisplay(guessPeekDisplay));
            config.get(Configuration.CATEGORY_GENERAL, "scrambleGuess", false).set(scrambleGuess);
            config.get(Configuration.CATEGORY_GENERAL, "scrambleGuessFancy", false).set(scrambleGuessFancy);
            config.get(Configuration.CATEGORY_GENERAL, "trollMode", false).set(trollMode);
            config.get(Configuration.CATEGORY_GENERAL, "trollModeAutoDisable", false).set(trollModeAutoDisable);
            config.get(Configuration.CATEGORY_GENERAL, "guessSound1", GuessSounds.DEFAULT_SOUND).set(GuessSounds.getSound(1));
            config.get(Configuration.CATEGORY_GENERAL, "guessSound2", GuessSounds.DEFAULT_SOUND).set(GuessSounds.getSound(2));
            config.get(Configuration.CATEGORY_GENERAL, "guessSound3", GuessSounds.DEFAULT_SOUND).set(GuessSounds.getSound(3));
            config.get(Configuration.CATEGORY_GENERAL, "guessSoundVolume", GuessSounds.DEFAULT_VOLUME).set(GuessSounds.getVolume());
            config.get(Configuration.CATEGORY_GENERAL, "chatHeader", ChatHeader.DEFAULT_RAW).set(ChatHeader.raw());
            config.get(Configuration.CATEGORY_GENERAL, "spawnEggSkin", "classic").set(normalizeSpawnEggSkin(spawnEggSkin));
            config.get(Configuration.CATEGORY_GENERAL, "gtwDefaultLang", gtwDefaultLang).set(gtwDefaultLang == null ? "en" : gtwDefaultLang);
            config.get(Configuration.CATEGORY_GENERAL, "colorNumbers", colorNumbers).set(colorNumbers);
            config.get(Configuration.CATEGORY_GENERAL, "colorHyphens", colorHyphens).set(colorHyphens);
            config.get(Configuration.CATEGORY_GENERAL, "colorBrackets", colorBrackets).set(colorBrackets);
            config.get(Configuration.CATEGORY_GENERAL, "chatCooldownBarColor", "\u00a7e").set(normalizeMinecraftColorCode(chatCooldownBarColor, "\u00a7e"));
            config.get(Configuration.CATEGORY_GENERAL, "chatCooldownTimerColor", "\u00a7e").set(normalizeMinecraftColorCode(chatCooldownTimerColor, "\u00a7e"));
            config.get(Configuration.CATEGORY_GENERAL, "previewQuickstartRan", false).set(previewQuickstartRan);
            config.get(Configuration.CATEGORY_GENERAL, "lastLangCode", lastLangCode).set(lastLangCode == null ? "en" : lastLangCode);

            config.get("scoreboard", "enabled", false).set(sbConfig.isCustomEnabled());
            config.get("scoreboard", "heightOffset", 9).set(sbConfig.heightOffset);
            config.get("scoreboard", "skin", "vanilla").set(ScoreboardConfig.normalizeSkin(sbConfig.skin));
            sbConfig.clampVisuals();
            config.get("scoreboard", "backgroundOpacity", ScoreboardConfig.DEFAULT_BACKGROUND_OPACITY).set(sbConfig.backgroundOpacity);
            config.get("scoreboard", "highlightIntensity", ScoreboardConfig.DEFAULT_HIGHLIGHT_INTENSITY).set(sbConfig.highlightIntensity);
            config.get("scoreboard", "highlightIntensityPureBenchmark", true).set(true);
            config.get("scoreboard", "showPointsGainedInRound", "on").set(scoreboardOptionName(sbConfig.showPointsGainedInRound));
            config.get("scoreboard", "showPlaces", "off").set(scoreboardOptionName(sbConfig.showPlaces));
            config.get("scoreboard", "showTitles", "expanded").set(scoreboardOptionName(sbConfig.showTitles));
            config.get("scoreboard", "showEmblems", "on").set(scoreboardOptionName(sbConfig.showEmblems));
            config.get("scoreboard", "defaultExplicitlyShowBuilders", false).set(sbConfig.defaultExplicitlyShowBuilders);
            config.get("scoreboard", "hypixelShowRounds", "off").set(scoreboardOptionName(sbConfig.hypixelShowRounds));
            config.get("scoreboard", "textShadow", true).set(sbConfig.textShadow);
            config.get("scoreboard", "leftPlayersAtBottom", true).set(sbConfig.leftPlayersAtBottom);

            config.save();
        } catch (Exception e) {
            LOGGER.warn("Failed to save config!", e);
        }
    }

    private static void loadGuessSoundsConfig() {
        String[] defaults = {GuessSounds.DEFAULT_SOUND, GuessSounds.DEFAULT_SOUND, GuessSounds.DEFAULT_SOUND};
        for (int points = 1; points <= 3; points++) {
            String raw = config.getString("guessSound" + points, Configuration.CATEGORY_GENERAL,
                    defaults[points - 1], "Sound played for a correct guess worth " + points + " points.");
            String normalized = GuessSounds.normalizeChoice(raw);
            try {
                GuessSounds.setSound(points, normalized == null ? defaults[points - 1] : normalized);
            } catch (IllegalArgumentException ignored) {
                GuessSounds.setSound(points, defaults[points - 1]);
            }
        }
        String rawVolume = config.getString("guessSoundVolume", Configuration.CATEGORY_GENERAL,
                Float.toString(GuessSounds.DEFAULT_VOLUME), "Global guess sound volume (0-30).");
        try {
            GuessSounds.setVolume(Float.parseFloat(rawVolume));
        } catch (IllegalArgumentException ignored) {
            GuessSounds.setVolume(GuessSounds.DEFAULT_VOLUME);
        }
    }

    /**
     * Converts the retired pixel settings only after a real scaled screen is available,
     * preserving a legacy panel's visible size on the first client render.
     */
    public static void migrateHandheldPanelRatios(int scaledWidth, int scaledHeight) {
        boolean migrated = false;
        if (legacyHandheldItemPanelWidth >= 0) {
            handheldItemPanelWidthRatio = legacyPanelRatio(legacyHandheldItemPanelWidth, scaledWidth,
                    DEFAULT_HANDHELD_ITEM_PANEL_WIDTH_RATIO);
            legacyHandheldItemPanelWidth = -1;
            migrated = true;
        }
        if (legacyHandheldItemPanelHeight >= 0) {
            handheldItemPanelHeightRatio = legacyPanelRatio(legacyHandheldItemPanelHeight, scaledHeight,
                    DEFAULT_HANDHELD_ITEM_PANEL_HEIGHT_RATIO);
            legacyHandheldItemPanelHeight = -1;
            migrated = true;
        }
        if (migrated && config != null) saveConfig();
    }

    private static float legacyPanelRatio(int legacyPixels, int scaledDimension, float fallback) {
        if (legacyPixels <= 0 || scaledDimension <= 0 || legacyPixels > scaledDimension) return fallback;
        return HandheldItemTracker.clampPanelRatio((float) legacyPixels / (float) scaledDimension);
    }

    private static String scoreboardOptionName(ScoreboardConfig.DisplayOption option) {
        option = ScoreboardConfig.normalizeOption(option, ScoreboardConfig.DisplayOption.OFF);
        if (option == ScoreboardConfig.DisplayOption.ON) return "on";
        if (option == ScoreboardConfig.DisplayOption.EXPANDED) return "expanded";
        return "off";
    }

    public static Float parseAutoRequeueDelaySeconds(String raw) {
        if (raw == null) return null;
        try {
            float value = Float.parseFloat(raw.trim());
            if (Float.isNaN(value) || Float.isInfinite(value)
                    || value < 0F || value > MAX_AUTO_REQUEUE_DELAY_SECONDS) {
                return null;
            }
            return Float.valueOf(normalizeAutoRequeueDelaySeconds(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static float normalizeAutoRequeueDelaySeconds(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)
                || value < 0F || value > MAX_AUTO_REQUEUE_DELAY_SECONDS) {
            return DEFAULT_AUTO_REQUEUE_DELAY_SECONDS;
        }
        return Math.round(value * 10F) / 10F;
    }

    public static String formatAutoRequeueDelaySeconds(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f",
                Float.valueOf(normalizeAutoRequeueDelaySeconds(value)));
    }

    public static Float parseLrjDelaySeconds(String raw) {
        if (raw == null) return null;
        try {
            float value = Float.parseFloat(raw.trim());
            if (Float.isNaN(value) || Float.isInfinite(value)
                    || value < 0F || value > MAX_AUTO_REQUEUE_DELAY_SECONDS) {
                return null;
            }
            return Float.valueOf(normalizeLrjDelaySeconds(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static float normalizeLrjDelaySeconds(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)
                || value < 0F || value > MAX_AUTO_REQUEUE_DELAY_SECONDS) {
            return DEFAULT_LRJ_DELAY_SECONDS;
        }
        return Math.round(value * 10F) / 10F;
    }

    public static String formatLrjDelaySeconds(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f",
                Float.valueOf(normalizeLrjDelaySeconds(value)));
    }

    public static void setShortcutReminderShowNonAscii(boolean enabled) {
        if (enabled && !hasShortcutNonAsciiCategoryEnabled()) {
            shortcutReminderShowExtendedLatin = true;
        }
        shortcutReminderShowNonAscii = enabled;
    }

    public static void setShortcutNonAsciiCategoryEnabled(ShortcutNonAsciiCategory category,
                                                           boolean enabled) {
        if (!enabled && shortcutNonAsciiCategoryEnabled(category)
                && shortcutNonAsciiCategoryCount() == 1) {
            // The final category remains as the restoration choice while the
            // parent setting collapses and becomes unchecked.
            shortcutReminderShowNonAscii = false;
            return;
        }

        setShortcutNonAsciiCategoryValue(category, enabled);
        if (!hasShortcutNonAsciiCategoryEnabled()) {
            shortcutReminderShowNonAscii = false;
        }
    }

    public static boolean shortcutNonAsciiCategoryEnabled(ShortcutNonAsciiCategory category) {
        if (category == ShortcutNonAsciiCategory.EXTENDED_LATIN) {
            return shortcutReminderShowExtendedLatin;
        }
        if (category == ShortcutNonAsciiCategory.CYRILLIC) {
            return shortcutReminderShowCyrillic;
        }
        if (category == ShortcutNonAsciiCategory.HANZI) {
            return shortcutReminderShowHanzi;
        }
        if (category == ShortcutNonAsciiCategory.JAPANESE) {
            return shortcutReminderShowJapanese;
        }
        return shortcutReminderShowHangul;
    }

    public static boolean hasShortcutNonAsciiCategoryEnabled() {
        return shortcutNonAsciiCategoryCount() > 0;
    }

    private static int shortcutNonAsciiCategoryCount() {
        int count = 0;
        if (shortcutReminderShowExtendedLatin) count++;
        if (shortcutReminderShowCyrillic) count++;
        if (shortcutReminderShowHanzi) count++;
        if (shortcutReminderShowJapanese) count++;
        if (shortcutReminderShowHangul) count++;
        return count;
    }

    private static void setShortcutNonAsciiCategoryValue(ShortcutNonAsciiCategory category,
                                                          boolean enabled) {
        if (category == ShortcutNonAsciiCategory.EXTENDED_LATIN) {
            shortcutReminderShowExtendedLatin = enabled;
        } else if (category == ShortcutNonAsciiCategory.CYRILLIC) {
            shortcutReminderShowCyrillic = enabled;
        } else if (category == ShortcutNonAsciiCategory.HANZI) {
            shortcutReminderShowHanzi = enabled;
        } else if (category == ShortcutNonAsciiCategory.JAPANESE) {
            shortcutReminderShowJapanese = enabled;
        } else {
            shortcutReminderShowHangul = enabled;
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (!modEnabled) {
            // Preview owns its native-looking sample actionbar. Outside preview,
            // leave the real vanilla actionbar and chat message untouched.
            if (event.type == 2 && InterfacePreview.isActive()) {
                event.setCanceled(true);
            }
            return;
        }
        if (event.type == 2) {
            // Actionbar — hint overlay (v1.0 behavior). Parse once and share the
            // result with the stats tracker so we don't strip + re-strip on the
            // hint hot path.
            String hintText = event.message.getUnformattedText();
            long now = System.currentTimeMillis();
            HintOverlay.ParsedHint parsed = HintOverlay.parse(hintText);
            if (parsed.isHint) {
                currentActionbarHintText = hintText == null ? "" : hintText;
                actionbarHintStartTime = now;
            } else {
                currentActionbarHintText = "";
                actionbarHintStartTime = 0;
            }
            currentStructure = parsed.structure;
            if (!currentStructure.isEmpty()) {
                overlayStartTime = now;
            }
            GameStats.INSTANCE.onHint(parsed, now);
            if (InterfacePreview.isActive()) {
                event.setCanceled(true);
            }
        } else if (event.type == 0 || event.type == 1) {
            // Chat (type 0) and system messages (type 1) — route to stats tracker
            String raw = event.message.getFormattedText();
            String clean = event.message.getUnformattedText();
            GameStats.INSTANCE.onChatMessage(raw, clean);
            event.message = GuessChatAnnotator.annotate(event.message);
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if (!modEnabled || InterfacePreview.isActive()) return;

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);

        boolean drawActionbarHintBg = hintBackgroundOverlay
                && GameStats.INSTANCE.shouldRenderRoundOverlays();
        boolean drawHintStructureBg = hintStructureBackground
                && GameStats.INSTANCE.shouldRenderRoundOverlays();

        if (drawActionbarHintBg) {
            drawActionbarHintBackground(mc, sr);
        }

        if (!enabled || currentStructure.isEmpty()) {
            return;
        }

        long elapsed = System.currentTimeMillis() - overlayStartTime;
        if (elapsed > OVERLAY_DURATION_MS) {
            return;
        }

        float alpha = 1.0f;
        final int FADE_DURATION_MS = 500;
        if (elapsed > OVERLAY_DURATION_MS - FADE_DURATION_MS) {
            alpha = 1.0f - (elapsed - (OVERLAY_DURATION_MS - FADE_DURATION_MS)) / (float) FADE_DURATION_MS;
        }
        alpha = Math.max(0f, Math.min(alpha, 1f));

        int alphaInt = (int)(alpha * 255) << 24;
        int color = 0xFFFFFF | alphaInt;

        String display = colorBrackets + "[ " + colorNumbers + currentStructure.replace("-", colorHyphens + "-" + colorNumbers) + colorBrackets + " ]";
        int displayWidth = mc.fontRendererObj.getStringWidth(display);
        float panelScale = HudPanelScale.normalize(hintStructureScale);
        int scaledWidth = HudPanelScale.scaledPixels(displayWidth, panelScale);
        int scaledHeight = HudPanelScale.scaledPixels(mc.fontRendererObj.FONT_HEIGHT, panelScale);
        int displayX = HudPosition.left(posX, sr.getScaledWidth(), scaledWidth, 2);
        int y = HudPosition.top(posY, sr.getScaledHeight(), scaledHeight, 2);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(displayX, y, 0.0F);
            GlStateManager.scale(panelScale, panelScale, 1.0F);
            if (drawHintStructureBg) {
                drawTextBackground(mc, 0, 0, displayWidth);
            }
            mc.fontRendererObj.drawStringWithShadow(display, 0, 0, color);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static void drawActionbarHintBackground(Minecraft mc, ScaledResolution sr) {
        if (currentActionbarHintText == null || currentActionbarHintText.isEmpty()) return;
        long elapsed = System.currentTimeMillis() - actionbarHintStartTime;
        if (elapsed < 0 || elapsed > ACTIONBAR_HINT_DURATION_MS) return;

        int textWidth = mc.fontRendererObj.getStringWidth(currentActionbarHintText);
        int textX = sr.getScaledWidth() / 2 - textWidth / 2;
        int textY = sr.getScaledHeight() - 72;
        drawTextBackground(mc, textX, textY, textWidth);
    }

    private static void drawTextBackground(Minecraft mc, int textX, int textY, int textWidth) {
        if (textWidth <= 0) return;
        Gui.drawRect(
            textX - HINT_BG_PAD_X,
            textY - HINT_BG_PAD_Y,
            textX + textWidth + HINT_BG_PAD_X,
            textY + mc.fontRendererObj.FONT_HEIGHT + HINT_BG_PAD_Y,
            ScoreboardLayout.BACKGROUND_COLOR
        );
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Explicit /lrj is a user-invoked utility command, so its queued /rej
        // half must continue while the master feature switch is off.
        CommandGtb.onClientTick();

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            CommandGtb.onClientConnectionBoundary(false);
            WorldEnvironmentOverlay.INSTANCE.resetChangeNotices();
            GameStats.INSTANCE.onServerSessionReset();
            return;
        }

        if (!modEnabled) {
            WorldEnvironmentOverlay.INSTANCE.resetChangeNotices();
            ThemePointReveal.discardPending();
            ShortcutReminder.discardPending();
            ShowAllMatching.discardPending();
            GuiIngameForge.renderObjective = !shouldSuppressVanillaSidebar(mc);
            return;
        }

        ModernEquipmentRenderPatch.install();

        // Drain any showallmatching messages queued from chat events on the previous
        // tick. Running here guarantees they print after the trigger line.
        ThemePointReveal.drainPending();
        ShortcutReminder.drainPending();
        ShowAllMatching.drainPending();

        // Custom-scoreboard tracker tick — bumps inactiveTicks and the spinner counter.
        GameStats.INSTANCE.fireGtbTick();

        WorldEnvironmentOverlay.INSTANCE.onClientTick();

        // Advance the /gtbs gtw mini-game timer (if a session is active).
        GtwGame.INSTANCE.onClientTick();

        // GUI deferred check — countdown each tick
        if (guiCheckCountdown > 0) {
            guiCheckCountdown--;
            if (guiCheckCountdown == 0) {
                if (checkGuiThemePick(mc)) guiCheckCountdown = 1;
            }
        }

        // Suppress vanilla sidebar EVERY tick (not throttled), because
        // GuiIngameForge.renderObjective is read every render frame. If we
        // only re-asserted every 10 ticks, any other code that toggles the
        // flag (or our own bootstrap timing) could let vanilla render
        // alongside our custom panel. Both skins ('default' and 'hypixel')
        // own the entire panel and want vanilla hidden during active rounds.
        GuiIngameForge.renderObjective = !shouldSuppressVanillaSidebar(mc);

        // Throttle scoreboard PARSE to every 10 ticks (~0.5 seconds)
        tickCounter++;
        if (tickCounter < 10) {
            return;
        }
        tickCounter = 0;

        // Read scoreboard and pass to GameStats
        readScoreboard(mc);
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!modEnabled) return;
        if (event.gui instanceof GuiChest) {
            guiCheckCountdown = 5;
        }
    }

    @SubscribeEvent
    public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        CommandGtb.onClientConnectionBoundary(true);
        if (event == null || event.isLocal || !updateNoticeEnabled) return;
        UpdateChecker.INSTANCE.checkOnceAsync(new UpdateChecker.Listener() {
            @Override
            public void onUpdateAvailable(final UpdateChecker.UpdateInfo update) {
                final Minecraft mc = Minecraft.getMinecraft();
                if (mc == null) return;
                mc.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        if (!updateNoticeEnabled || mc.thePlayer == null) return;
                        mc.thePlayer.addChatMessage(buildUpdateNoticeMessage(update));
                        for (String line : update.getChangelogLines()) {
                            mc.thePlayer.addChatMessage(new ChatComponentText("\u00a77" + line));
                        }
                    }
                });
            }
        });
    }

    @SubscribeEvent
    public void onDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        CommandGtb.onClientConnectionBoundary(false);
    }

    public static IChatComponent buildUpdateNoticeMessage(UpdateChecker.UpdateInfo update) {
        IChatComponent message = new ChatComponentText(CommandGtb.head
                + "New version available: \u00a7f" + update.getVersion() + "\u00a77. ");
        ChatComponentText github = new ChatComponentText("\u00a78[\u00a77GitHub\u00a78]\u00a77 ");
        github.getChatStyle().setChatClickEvent(new ClickEvent(
                ClickEvent.Action.OPEN_URL, update.getGithubUrl()));
        ChatComponentText modrinth = new ChatComponentText("\u00a72[\u00a7aModrinth\u00a72]");
        modrinth.getChatStyle().setChatClickEvent(new ClickEvent(
                ClickEvent.Action.OPEN_URL, update.getModrinthUrl()));
        message.appendSibling(github);
        message.appendSibling(modrinth);
        return message;
    }

    // ── Private Helpers ──────────────────────────────────────────────────

    private void readScoreboard(Minecraft mc) {
        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        ScoreObjective sidebar = scoreboard.getObjectiveInDisplaySlot(1);

        if (sidebar == null) {
            GameStats.INSTANCE.onScoreboardTick(null, null);
            return;
        }

        String titleClean = StringUtils.stripControlCodes(sidebar.getDisplayName());

        // Build the fully-normalized line list in one pass: control codes stripped,
        // emojis dropped, NFC-normalized, trimmed. Every downstream consumer
        // (state detection, language detection, builder/theme/score extraction,
        // ranking parse) reuses this list verbatim — no per-line re-stripping.
        Collection<Score> scores = scoreboard.getSortedScores(sidebar);
        List<String> cleanLines = new ArrayList<String>(scores.size());
        List<String> rawFormattedLines = new ArrayList<String>(scores.size());
        for (Score score : scores) {
            ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
            String formatted = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
            String visibleText = scoreboardVisibleText(team, formatted);
            rawFormattedLines.add(formatted);
            String stripped = StringUtils.stripControlCodes(visibleText);
            if (stripped == null) stripped = "";
            String cleaned = LangPatterns.stripEmojis(stripped).trim();
            cleaned = Normalizer.normalize(cleaned, Normalizer.Form.NFC);
            cleanLines.add(cleaned);
        }
        GameStats.INSTANCE.onScoreboardTick(titleClean, cleanLines, rawFormattedLines);
        GameStats.INSTANCE.captureSidebar(titleClean, cleanLines);
        // Vanilla suppression is now re-asserted every tick in onClientTick
        // above, regardless of whether this 10-tick parse fires.
    }

    private static String scoreboardVisibleText(ScorePlayerTeam team, String fallbackFormatted) {
        if (team == null) return fallbackFormatted;
        return scoreboardVisibleTextForRead(team.getColorPrefix(), team.getColorSuffix(), fallbackFormatted);
    }

    static String scoreboardVisibleTextForRead(String prefix, String suffix, String fallbackFormatted) {
        String teamOnly = (prefix == null ? "" : prefix) + (suffix == null ? "" : suffix);
        String cleanTeamOnly = StringUtils.stripControlCodes(teamOnly);
        if (cleanTeamOnly != null && !cleanTeamOnly.trim().isEmpty()) {
            return teamOnly;
        }
        return fallbackFormatted == null ? "" : fallbackFormatted;
    }

    public static void noteCustomScoreboardRenderFailure(Throwable t) {
        long now = System.currentTimeMillis();
        customScoreboardFallbackUntilMs = now + 2000L;
        if (now - lastCustomScoreboardFailureLogMs > 10000L) {
            lastCustomScoreboardFailureLogMs = now;
            LOGGER.warn("Custom scoreboard render failed; temporarily restoring vanilla sidebar.", t);
        }
    }

    private static boolean shouldSuppressVanillaSidebar(Minecraft mc) {
        boolean previewActive = InterfacePreview.isActive();
        if (!modEnabled || previewActive) {
            return shouldSuppressVanillaSidebarForMaster(modEnabled, previewActive, false);
        }
        if (shouldRelocateVanillaSidebar(mc)) return true;
        if (!GtbStructures.sbConfig.isCustomEnabled()) return false;
        if (System.currentTimeMillis() < customScoreboardFallbackUntilMs) return false;
        if (!GameStats.INSTANCE.shouldRenderRoundOverlays()) return false;
        if (GameTracker.INSTANCE.game == null) return false;

        if (GtbStructures.sbConfig.isSkin("hypixel")) {
            try {
                if (mc == null || mc.theWorld == null) return false;
                Scoreboard scoreboard = mc.theWorld.getScoreboard();
                return scoreboard != null && scoreboard.getObjectiveInDisplaySlot(1) != null;
            } catch (Throwable ignored) {
                return false;
            }
        }

        return GtbStructures.sbConfig.isSkin("default");
    }

    @SubscribeEvent
    public void onRenderRelocatedVanillaSidebar(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!modEnabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!shouldRelocateVanillaSidebar(mc)) return;
        ScoreObjective objective = vanillaSidebarObjective(mc);
        if (objective == null) return;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(0.0F, -GtbStructures.sbConfig.heightOffset, 0.0F);
            Method renderer = GuiIngame.class.getDeclaredMethod(
                    "renderScoreboard", ScoreObjective.class, ScaledResolution.class);
            renderer.setAccessible(true);
            renderer.invoke(mc.ingameGUI, objective, new ScaledResolution(mc));
        } catch (Throwable t) {
            noteCustomScoreboardRenderFailure(t);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static boolean shouldRelocateVanillaSidebar(Minecraft mc) {
        return shouldRelocateVanillaSidebarForTest(
                GtbStructures.modEnabled, GtbStructures.sbConfig.isCustomEnabled(), GtbStructures.sbConfig.skin,
                GtbStructures.sbConfig.heightOffset, InterfacePreview.isActive(),
                System.currentTimeMillis() < customScoreboardFallbackUntilMs,
                GameStats.INSTANCE.shouldRenderRoundOverlays(), GameTracker.INSTANCE.game != null,
                vanillaSidebarObjective(mc) != null);
    }

    static boolean shouldRelocateVanillaSidebarForTest(boolean modEnabled,
                                                       boolean customEnabled, String skin,
                                                       int heightOffset, boolean previewActive,
                                                       boolean fallbackActive, boolean roundActive,
                                                       boolean gameActive, boolean sidebarPresent) {
        return modEnabled && customEnabled && "vanilla".equals(ScoreboardConfig.normalizeSkin(skin))
                && heightOffset != 0 && !previewActive && !fallbackActive
                && roundActive && gameActive && sidebarPresent;
    }

    static boolean shouldSuppressVanillaSidebarForMaster(boolean modEnabled,
                                                          boolean previewActive,
                                                          boolean configuredSuppression) {
        return modEnabled ? previewActive || configuredSuppression : previewActive;
    }

    private static ScoreObjective vanillaSidebarObjective(Minecraft mc) {
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) return null;
        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        if (scoreboard == null) return null;
        ScorePlayerTeam team = scoreboard.getPlayersTeam(mc.thePlayer.getName());
        ScoreObjective teamObjective = null;
        if (team != null) {
            int slot = team.getChatFormat().getColorIndex();
            if (slot >= 0) teamObjective = scoreboard.getObjectiveInDisplaySlot(3 + slot);
        }
        return teamObjective != null ? teamObjective : scoreboard.getObjectiveInDisplaySlot(1);
    }

    private boolean checkGuiThemePick(Minecraft mc) {
        if (!(mc.currentScreen instanceof GuiChest)) return false;

        GuiChest guiChest = (GuiChest) mc.currentScreen;
        ContainerChest container = (ContainerChest) guiChest.inventorySlots;
        IInventory lower = container.getLowerChestInventory();

        String title = lower.getDisplayName().getUnformattedText();
        title = title.trim();

        if (!GameStats.INSTANCE.isThemeSelectionGui(title)) return false;

        // Read theme options from slots 11, 13, 15
        String[] themes = new String[3];
        int[] slots = {11, 13, 15};
        for (int i = 0; i < 3; i++) {
            if (slots[i] < lower.getSizeInventory()) {
                ItemStack stack = lower.getStackInSlot(slots[i]);
                if (stack != null && stack.hasDisplayName()) {
                    String name = StringUtils.stripControlCodes(stack.getDisplayName());
                    themes[i] = name != null ? name.trim() : null;
                } else {
                    themes[i] = null;
                }
            }
        }

        return !GameStats.INSTANCE.onGuiThemePick(themes);
    }

    public static String normalizeSpawnEggSkin(String skin) {
        if (skin == null) return "classic";
        String normalized = skin.trim().toLowerCase();
        return "new".equals(normalized) ? "new" : "classic";
    }

    public static String normalizeGuessPeekDisplay(String display) {
        return display != null && "suffix".equalsIgnoreCase(display.trim()) ? "suffix" : "hover";
    }

    public static String normalizeMinecraftColorCode(String color, String fallback) {
        String fb = fallback == null ? "\u00a7f" : fallback;
        if (color == null) return fb;
        String value = color.trim().toLowerCase();
        if (value.startsWith("\u00a7") || value.startsWith("&")) {
            value = value.substring(1);
        }
        if (value.length() == 1 && "0123456789abcdef".indexOf(value.charAt(0)) >= 0) {
            return "\u00a7" + value;
        }
        return fb;
    }

    public static int minecraftColorArgb(String color, int alpha) {
        String normalized = normalizeMinecraftColorCode(color, "\u00a7f");
        char code = normalized.charAt(1);
        int rgb;
        switch (code) {
            case '0': rgb = 0x000000; break;
            case '1': rgb = 0x0000AA; break;
            case '2': rgb = 0x00AA00; break;
            case '3': rgb = 0x00AAAA; break;
            case '4': rgb = 0xAA0000; break;
            case '5': rgb = 0xAA00AA; break;
            case '6': rgb = 0xFFAA00; break;
            case '7': rgb = 0xAAAAAA; break;
            case '8': rgb = 0x555555; break;
            case '9': rgb = 0x5555FF; break;
            case 'a': rgb = 0x55FF55; break;
            case 'b': rgb = 0x55FFFF; break;
            case 'c': rgb = 0xFF5555; break;
            case 'd': rgb = 0xFF55FF; break;
            case 'e': rgb = 0xFFFF55; break;
            case 'f':
            default: rgb = 0xFFFFFF; break;
        }
        return ((alpha & 0xFF) << 24) | rgb;
    }

    public static void toggle() {
        enabled = !enabled;
        Minecraft.getMinecraft().thePlayer.addChatMessage(
                new ChatComponentText(CommandGtb.head + (enabled ? "\u00a7aEnabled" : "\u00a7cDisabled")));
    }

    public static boolean shouldAutoDisableTrollMode(boolean autoDisable, boolean enabled) {
        return autoDisable && enabled;
    }

    public static boolean autoDisableTrollModeAtGameStart() {
        if (!shouldAutoDisableTrollMode(trollModeAutoDisable, trollMode)) return false;
        trollMode = false;
        saveConfig();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(CommandGtb.head
                    + "Troll mode is currently \u00a7cOFF"));
        }
        return true;
    }

}
