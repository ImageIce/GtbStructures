package com.gtbstructures.stats;

import com.gtbstructures.chat.ChatCooldownCounter;
import com.gtbstructures.diagnostics.RawPacketRecorder;
import com.gtbstructures.GtbStructures;
import com.gtbstructures.hints.HintOverlay;
import com.gtbstructures.hints.ShowAllMatching;
import com.gtbstructures.player.PlayerListPacketListener;
import com.gtbstructures.player.PlayerMetaCache;
import com.gtbstructures.scoreboard.CustomScoreboard;
import com.gtbstructures.scoreboard.GameTracker;
import com.gtbstructures.scoreboard.GtbGame;
import com.gtbstructures.translation.ShortcutReminder;
import com.gtbstructures.translation.ThemePointReveal;
import com.gtbstructures.translation.TranslationData;
import com.gtbstructures.sound.GuessSoundController;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GameStats {

    public static final GameStats INSTANCE = new GameStats();

    // ── Constants ────────────────────────────────────────────────────────

    private static final Pattern ROUND_NUM_PATTERN = Pattern.compile("(\\d+)/(\\d+)");
    private static final Pattern LOBBY_COUNT_PATTERN = Pattern.compile("\\((\\d+)/(\\d+)\\)");
    // Post-game rankings: "N. <IGN>" optionally followed by a score separator + number.
    private static final Pattern RANK_LINE_PATTERN = Pattern.compile("^(\\d+)\\.\\s+(.+)$");
    private static final Pattern RANK_SCORE_PATTERN = Pattern.compile("^(.*?)[\\s:\\-]+(\\d+)$");
    // Hypixel scoreboards splice in a "DD/MM/YY mapNN" footer line; skip when extracting scores.
    // Pre-compiled to avoid recompiling the regex on every line, every tick.
    private static final Pattern DATE_FOOTER_PATTERN = Pattern.compile("\\d{2}/\\d{2}/\\d{2}.*");
    // A localized phase line can become unknown when Hypixel changes its text.
    // Reject its clock token before the generic "IGN: score" parser can turn
    // the minutes into a numeric ghost player (for example 00 from 00:05).
    private static final Pattern SIDEBAR_CLOCK_TOKEN_PATTERN =
            Pattern.compile(".*\\d{1,2}:\\d{2}.*");

    // ── State Enum ───────────────────────────────────────────────────────

    public enum TrackingState {
        NOT_IN_GAME,   // no GTB scoreboard
        LOBBY,         // scoreboard has GTB title + "Mode: Guess The Build"
        ROUND_PRE,     // "Starts In:" timer — builder picking theme
        ROUND_BUILD,   // "Time:" timer — guessing phase
        ROUND_END,     // "Next Round:" timer — between rounds
        POST_GAME,     // "1. " ranking lines on scoreboard
        PAUSED         // scoreboard disappeared mid-game (disconnect)
    }

    /** Returns true for any state that is part of an active game (not NOT_IN_GAME or PAUSED). */
    public static boolean isActiveGame(TrackingState s) {
        return s == TrackingState.LOBBY || s == TrackingState.ROUND_PRE
                || s == TrackingState.ROUND_BUILD || s == TrackingState.ROUND_END
                || s == TrackingState.POST_GAME;
    }

    /**
     * A recognized GTB title is positive evidence that the scoreboard has not
     * disappeared. Keep the current active state when its localized phase label
     * is temporarily missing or not yet known instead of treating that single
     * classification miss as a disconnect.
     */
    static TrackingState retainActiveStateForUnclassifiedGtb(TrackingState currentState,
                                                               TrackingState detectedState,
                                                               boolean scoreboardShowsGtb) {
        if (scoreboardShowsGtb
                && detectedState == TrackingState.NOT_IN_GAME
                && isActiveGame(currentState)) {
            return currentState;
        }
        return detectedState;
    }

    /** Returns true for states during an active round (ROUND_PRE, ROUND_BUILD, ROUND_END). */
    static boolean isInRound(TrackingState s) {
        return s == TrackingState.ROUND_PRE || s == TrackingState.ROUND_BUILD
                || s == TrackingState.ROUND_END;
    }

    /** Returns true while GTB should repair in-world player display formatting. */
    public static boolean isBbRankDisplayRepairState(TrackingState s) {
        return s == TrackingState.ROUND_PRE || s == TrackingState.ROUND_BUILD
                || s == TrackingState.ROUND_END || s == TrackingState.POST_GAME;
    }

    // ── Data Classes ─────────────────────────────────────────────────────

    public static class GuessEntry {
        public long t;
        public Integer physicalTimer;
        public String ign;
        public String text;
        public GuessEntry(long t, String ign, String text) {
            this.t = t;
            this.physicalTimer = GameStats.INSTANCE.getCurrentPhysicalTimerSeconds();
            this.ign = ign;
            this.text = text;
        }
    }

    public static class CorrectGuessEntry {
        public long t;
        public Integer physicalTimer;
        public String ign;
        public CorrectGuessEntry(long t, String ign) {
            this.t = t;
            this.physicalTimer = GameStats.INSTANCE.getCurrentPhysicalTimerSeconds();
            this.ign = ign;
        }
    }

    public static class HintEntry {
        public long t;
        public Integer physicalTimer;
        public String text;       // raw hint with underscores
        public HintEntry(long t, String text) {
            this.t = t;
            this.physicalTimer = GameStats.INSTANCE.getCurrentPhysicalTimerSeconds();
            this.text = text;
        }
    }

    public static class GameEvent {
        public long t;
        public Integer physicalTimer;
        public String type;       // "afk_skip", "disconnect", "rejoin"
        public String detail;     // optional: variant, builder IGN, theme, etc.
        public Integer physicalTimerBefore;
        public Integer physicalTimerAfter;
        public GameEvent(long t, String type, String detail) {
            this.t = t;
            this.physicalTimer = GameStats.INSTANCE.getCurrentPhysicalTimerSeconds();
            this.type = type;
            this.detail = detail;
        }
    }

    public static class RankEntry {
        public int place;
        public String ign;
        public int score;
        public RankEntry(int place, String ign, int score) {
            this.place = place; this.ign = ign; this.score = score;
        }
    }

    public static class RoundData {
        public int roundNumber;
        public int totalRounds;
        public long roundStart;
        public long roundEnd;
        public String builder;
        public String theme;
        public int themeCharCount;
        public String[] builderThemeOptions; // null if not builder
        public String hintStructure;         // shared across all hints in the round (e.g. "7-3")
        public String language;              // display name (e.g. "English") active when ROUND_BUILD began
        public String langCode;              // matching LangPatterns.lang code (e.g. "en", "ru", "pt_BR", "pirate")
        public List<GuessEntry> guesses = new ArrayList<>();
        public List<CorrectGuessEntry> correctGuesses = new ArrayList<>();
        public List<HintEntry> hints = new ArrayList<>();
        public List<HandheldItemEntry> handheldItems = new ArrayList<HandheldItemEntry>();
        public List<GameEvent> events = new ArrayList<>();
        public Map<String, ScoreDelta> scoreDeltas = new LinkedHashMap<>();
        // Local player was disconnected for some part of this round — chat events may be missing.
        public boolean dataGap = false;
        // First action-bar hint with at least one revealed character (space exclusive).
        // Used by /gtbs showallmatching to list every theme matching the hint pattern.
        public String effectiveInitialHint;
        // Whether the showallmatching message has already been emitted for this round.
        public boolean showAllMatchingSent;
        // Whether the shortcut reminder has already been emitted for this round.
        public boolean shortcutReminderSent;
        // Runtime-only state for /gtbs themepointreveal.
        public boolean themePointRevealSent;
        public boolean completeThemeRevealedToUser;
        public boolean localBuildOwner;
    }

    public static class HandheldItemInterval {
        public long start;
        public long end;
        public Integer startPhysicalTimer;
        public Integer endPhysicalTimer;

        public HandheldItemInterval(long start) {
            this.start = start;
            this.end = start;
            this.startPhysicalTimer = GameStats.INSTANCE.getCurrentPhysicalTimerSeconds();
            this.endPhysicalTimer = this.startPhysicalTimer;
        }

        public void touch(long now) {
            this.end = now;
            this.endPhysicalTimer = GameStats.INSTANCE.getCurrentPhysicalTimerSeconds();
        }
    }

    public static class HandheldItemEntry {
        public final String key;
        public String name;
        public String registryName;
        public int numericId;
        public int metadata;
        public int stackSize;
        public String unlocalizedName;
        public String modernKind;
        public String modernName;
        public String nbt;
        public long firstSeen;
        public long lastSeen;
        public List<HandheldItemInterval> intervals = new ArrayList<HandheldItemInterval>();

        public HandheldItemEntry(String key) {
            this.key = key;
        }
    }

    // ── Custom Scoreboard Hooks ──────────────────────────────────────────
    // Additive event surface consumed by com.gtbstructures.scoreboard.GameTracker.
    // Every method has a single emit site at an existing parse point; default
    // implementation is no-op so partial wiring never breaks the build.

    public interface GtbHooks {
        void onGameStart(List<String> players, String localIgn);
        default void onMidGameStart() {}
        default void onRoundPre() {}
        default void onRoundBuild() {}
        void onRoundStart(int currentRound, int totalRounds);
        default void onInferredRoundStart(int currentRound, int totalRounds,
                                          boolean guessOrderUncertain) {
            onRoundStart(currentRound, totalRounds);
        }
        void onBuilderChange(String previous, String current);
        void onCorrectGuess(List<String> players);
        void onCorrectGuessScore(String ign, int points);
        void onHintUpdate(String hint, String structure, String langCode);
        void onThemeUpdate(String theme, String langCode);
        void onRoundEnd(boolean skipped);
        void onRoundSkipped();
        void onGameEnd(Map<String, Integer> scores);
        void onTrueScoresUpdate(Map<String, Integer> observedScores);
        default void onConfirmedPlayerLeft(String ign) {}
        void onUserLeave();
        void onUserRejoin();
        void onUserRejoinSameRound(String builder);
        default void onUserRejoinPreRound() {}
        void onPlayerChat(String ign);
        default void onPlayerGuess(String ign, String text) {}
        default void onPlayerRejoin(String ign) {}
        void onGameRosterPlayer(String ign);
        void onPlayerTabPresent(String ign);
        void onPlayerTabAbsent(String ign);
        void onOneSecondAlert();
        void onTimerUpdate(String timer);
        void onTickActivity();
        void onIdentityTransfer(String oldIgn, String newIgn);
        void onPlayerEmblem(String ign, String emblem);
    }

    private GtbHooks gtbHooks;
    private RoundData lastBuilderHookRound;
    private String lastBuilderHookIgn;

    public void setGtbHooks(GtbHooks hooks) { this.gtbHooks = hooks; }

    /** Fires {@link GtbHooks#onTickActivity()} once per client tick from {@code GtbStructures.onClientTick}. */
    public void fireGtbTick() {
        serverSessionUnavailableTicks = 0;
        captureTrueIgn();
        confirmPendingBuilderTabReturns(System.currentTimeMillis());
        if (gtbHooks != null) gtbHooks.onTickActivity();
    }

    public void onServerSessionReset() {
        if (isInRound(state)) {
            if (state != TrackingState.ROUND_END) {
                handlePause();
            } else {
                serverSessionUnavailableTicks++;
                if (serverSessionUnavailableTicks >= ROUND_END_SESSION_RESET_GRACE_TICKS) {
                    handlePause();
                }
            }
        } else if (state != TrackingState.PAUSED) {
            serverSessionUnavailableTicks = 0;
        }
        lobbyLanguageFallback.resetForServerSession();
        pendingBuilderTabReturns.clear();
        serverConfirmedLocalIgn = null;
        clearPendingSelfGuessScore();
    }

    /** Assign currentRound.builder + emit onBuilderChange if the value really changed.
     *  The 5 in-place {@code currentRound.builder = X} sites all route through here. */
    private void setBuilder(String newBuilder) {
        if (currentRound == null) return;
        String prev = currentRound.builder;
        currentRound.builder = newBuilder;
        if (isLocalPlayerBuilder(currentRound)) {
            currentRound.localBuildOwner = true;
        }
        if (gtbHooks != null) {
            boolean same = (prev == null && newBuilder == null)
                    || (prev != null && prev.equals(newBuilder));
            if (!same) emitBuilderHook(prev, newBuilder);
        }
    }

    // ── Instance State ───────────────────────────────────────────────────

    private TrackingState state = TrackingState.NOT_IN_GAME;
    private boolean trackingEnabled = true;
    private long gameStart;
    private long pauseTime;
    private long activeTimeMs;
    private long activeTimeStartedAt;
    private String localPlayer;
    private boolean trollAutoDisableJapaneseRoundPending;
    private String trueIgn;
    private String launchPlayerUuid;
    private String playerUuid;
    private String nick;
    private boolean isNicked;
    private String serverConfirmedLocalIgn;
    String currentLanguage;
    String currentLangCode;          // tracks the LangPatterns.lang of the most recently selected language
    List<String> players = new ArrayList<>();
    List<RoundData> rounds = new ArrayList<>();
    RoundData currentRound;
    List<RankEntry> rankings = new ArrayList<>();
    // End-of-game residuals computed by ScoreReconciler.finalizeWithLeaderboard()
    Map<String, Integer> lastUnknownResidual = new LinkedHashMap<>();

    // Japanese split-line round state
    private boolean jaRoundLabelPending = false;

    // Hint deduplication — last hint text recorded for current round
    private String lastHintText = "";

    // Scoreboard state (updated by tick handler)
    private boolean scoreboardShowsGtb = false;

    // Player list collected during LOBBY phase
    private boolean lobbyTabDone = false;
    private final Set<String> lobbyQuitSuppress = new HashSet<>();
    private final List<String> handoffLobbyRoster = new ArrayList<>();
    private final List<String> lobbyPacketRemovals = new ArrayList<>();
    private int lobbyAnnouncedPlayerCount = 0;

    // Grace periods for transient client/sidebar handoffs. Hypixel can briefly
    // clear the world or rebuild the objective between adjacent BB rounds.
    private int notInGameTicks = 0;
    private static final int PAUSE_GRACE_TICKS = 5;
    private int serverSessionUnavailableTicks = 0;
    private static final int ROUND_END_SESSION_RESET_GRACE_TICKS = 20;
    private static final long GTB_HANDOFF_ROSTER_GRACE_MS = 3000L;
    private long gtbHandoffRosterUntilMs = 0L;
    private boolean gtbLobbyRosterConfirmed = false;
    private File statsDirOverrideForTest = null;

    // Track whether game data needs initialization on PAUSED recovery
    private boolean gameInitialized = false;

    // Duplicate endGame guard
    private boolean gameEnded = false;

    // Auto-requeue: join another GTB game after normal game end
    private boolean autoRequeue = false;
    private float autoRequeueDelaySeconds = GtbStructures.DEFAULT_AUTO_REQUEUE_DELAY_SECONDS;
    private long firstFinalRosterCaptureTime = 0L;
    private long requeueTime = 0; // timestamp when to send /play (0 = inactive)

    // Rejoin round-change detection: saved state from before pause
    private String pausedBuilder;       // builder IGN when pause started
    private int pausedRoundTotal;       // totalRounds when pause started
    private TrackingState pausedState;  // phase observed immediately before PAUSED
    private int pausedNextRoundCandidate;
    private boolean pausedRoundHadDataGapBeforeLeave;
    private boolean pendingRejoinCheck; // true after rejoin until same-round, gap, or round-boundary proof

    // Tab-list change detection: re-snapshot when the player info map grows
    // (all players appear with their BB ranks at game start). Initialized to -1
    // so the first non-empty map always triggers a snapshot.

    // BB-rank-in-tab detection: in the lobby, tab display names show no BB rank
    // prefixes. When the game starts, BB ranks (Rookie/Expert/etc.) appear as
    // prefixes — that is the exact moment to snapshot the full player list.

    // Deferred endGame after the "Winners" chat message, giving the scoreboard
    // time to populate rankings before we export.
    private long scheduledEndGameTime = 0;

    // "Builder: X" announces the NEXT round's builder. If it arrives before the
    // next round's RoundData exists (state==ROUND_END / LOBBY / PAUSED), buffer
    // it here and apply on next beginPreRound / handleRoundStart.
    private String pendingNextBuilder;

    // True once the user explicitly picked a language via "Selected language: X".
    // Auto-detection from chat patterns is suppressed once explicit.
    private boolean languageExplicitlySet;

    // True once the current language has been confirmed this game by either an
    // explicit "Selected language: X" or a chat pattern unique to one language.
    // Until this flips true, currentLanguage is only the seed from lastLangCode
    // and may be wrong — getStatusSummary annotates it as unverified.
    private boolean languageObservedThisGame;
    private final LobbyLanguageFallback lobbyLanguageFallback = new LobbyLanguageFallback();

    // Cached sidebar values, captured each scoreboard tick by captureSidebarTimer /
    // captureSidebarTheme. The custom scoreboard panel reads these so it doesn't
    // have to re-parse cleanLines per render frame.
    private String lastSidebarTimer;
    private String lastSidebarTheme;
    private Integer currentPhysicalTimerSeconds;
    private Integer lastTrackedPhysicalTimerSeconds;
    private TrackingState lastTrackedPhysicalTimerState;
    private RoundData lastTrackedPhysicalTimerRound;

    // Mid-game unattributed residual: points known to have been earned (from a
    // scoreboard observation that jumped > 3 or rejoined past a multi-round gap)
    // but not assignable to a specific round. Surfaced as the parenthesized
    // smaller value on the panel and folded into finalizeWithLeaderboard.
    private final Map<String, Integer> midGameUnattributedResidual = new LinkedHashMap<>();

    // Per-player count of complete rounds the player was disconnected for.
    // Drives the severe-uncertainty (!) flag for off-leaderboard players.
    private final Map<String, Integer> roundsMissedDuringDisconnect = new LinkedHashMap<>();

    // True iff the local player rejoined since the last ROUND_PRE and we haven't
    // seen the next ROUND_PRE yet (so the actual round count is unconfirmed).
    private boolean localRejoinedImmediatelyStage = false;

    // Per-player wall-clock timestamp of last observed activity (chat or
    // scoreboard score growth). Drives the inactive marker on the panel.
    private final Map<String, Long> lastActivityMs = new HashMap<>();
    /** 3 minutes of no chat or score growth flips a player to "inactive". */
    private static final long INACTIVE_THRESHOLD_MS = 3 * 60 * 1000L;

    // Per-player leaver flag, latched true when the player is a builder who
    // left mid-round, or when they fell off the roster between ticks.
    private final Set<String> leavers = new HashSet<>();
    // Subset of leavers with explicit builder-left chat evidence.
    private final Set<String> builderLeftLeavers = new HashSet<>();
    // Hypixel can rebuild tab with ADD/REMOVE bursts at round boundaries. Keep
    // builder returns pending long enough for the paired removal to cancel them.
    private static final long TAB_RETURN_CONFIRM_DELAY_MS = 250L;
    private final Map<String, Long> pendingBuilderTabReturns = new LinkedHashMap<>();

    // Snapshot of last tick's sidebar (ign -> score). Drives identity-transfer
    // detection across nick/denick cycles by diffing places with equal scores.
    private Map<String, Integer> lastSidebarScores = new LinkedHashMap<>();
    // Team prefix/suffix changes arrive as separate packets. A half-updated row
    // can briefly spell a valid but nonexistent IGN, so unknown scoreboard-only
    // names must survive one complete poll before joining the game roster.
    private final Set<String> pendingUnknownSidebarPlayers =
            new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    // Sidebar row disappearance can be a multi-frame Hypixel redraw artifact.
    // Require several consecutive stable observations before treating it as leave.
    private static final int SIDEBAR_DROP_REQUIRED_MISSING_TICKS = 4;
    private static final int SIDEBAR_DROP_REQUIRED_VISIBLE_ROWS = 3;
    private final Map<String, Integer> sidebarDropKnownScores = new LinkedHashMap<>();
    private final Map<String, Integer> sidebarDropMissingTicks = new HashMap<>();
    private final Map<String, Integer> sidebarDropMissingScores = new HashMap<>();
    private RoundData sidebarTrackingRound = null;
    private static final long SELF_GUESS_SCORE_WINDOW_MS = 2500L;
    private String pendingSelfGuessScoreIgn;
    private int pendingSelfGuessScoreRound;
    private long pendingSelfGuessScoreUntilMs;

    // Last seen local-player ign (mc.thePlayer.getName()). Used to trigger
    // immediate identity transfer when the local player nick/denick toggles.
    private String lastLocalIgn;

    // ── Utility Methods ──────────────────────────────────────────────────

    /** Strip ChatReplacer suffix and leading reset codes */
    public static String stripChatSuffix(String s) {
        if (s == null) return "";
        if (s.endsWith("\u00a77 [C]\u00a7r")) {
            s = s.substring(0, s.length() - 8);
        } else if (s.endsWith("\u00a77 [C]")) {
            s = s.substring(0, s.length() - 6);
        }
        if (s.endsWith(" [C]")) {
            s = s.substring(0, s.length() - 4);
        }
        while (s.startsWith("\u00a7r")) {
            s = s.substring(2);
        }
        return s.trim();
    }

    // ── Accessors ────────────────────────────────────────────────────────

    public TrackingState getState() { return state; }
    public boolean isTrackingEnabled() { return trackingEnabled; }
    public void setTrackingEnabled(boolean enabled) {
        this.trackingEnabled = enabled;
        if (!enabled) RawPacketRecorder.INSTANCE.discardSession();
    }
    public boolean isAutoRequeue() { return autoRequeue; }
    public void setAutoRequeue(boolean enabled) {
        this.autoRequeue = enabled;
        if (!enabled) {
            clearAutoRequeueSchedule();
        } else {
            rescheduleAutoRequeueFromFirstRoster();
        }
    }
    public float getAutoRequeueDelaySeconds() { return autoRequeueDelaySeconds; }
    public void setAutoRequeueDelaySeconds(float seconds) {
        autoRequeueDelaySeconds = GtbStructures.normalizeAutoRequeueDelaySeconds(seconds);
        rescheduleAutoRequeueFromFirstRoster();
    }
    public RoundData getCurrentRound() { return currentRound; }
    public boolean hasLocalPlayerCorrectlyGuessedCurrentRound() {
        if (currentRound == null || currentRound.correctGuesses == null
                || bestKnownLocalIgn() == null) return false;
        for (CorrectGuessEntry guess : currentRound.correctGuesses) {
            if (guess != null && isLocalPlayerIgn(guess.ign)) return true;
        }
        return false;
    }
    public List<RoundData> getRounds() { return rounds; }
    public List<String> getPlayers() { return players; }
    public String canonicalRosterIgn(String ign) {
        String clean = cleanIgn(ign);
        if (!PlayerMetaCache.isValidMcUsername(clean)) return clean;
        captureTrueIgn();
        if (PlayerMetaCache.isValidMcUsername(trueIgn)
                && PlayerMetaCache.isValidMcUsername(localPlayer)
                && !trueIgn.equalsIgnoreCase(localPlayer)
                && trueIgn.equalsIgnoreCase(clean)) {
            return localPlayer;
        }
        return clean;
    }
    public List<RankEntry> getRankings() { return rankings; }
    public String getCurrentLangCode() { return currentLangCode; }
    public Map<String, Integer> getLastUnknownResidual() { return lastUnknownResidual; }
    /** True iff the current state is one of ROUND_PRE / ROUND_BUILD / ROUND_END. */
    public boolean isInActiveRound() { return isInRound(state); }
    /** True while round-specific HUD elements still belong on screen. */
    public boolean shouldRenderRoundOverlays() {
        return shouldRenderRoundOverlays(state, currentRound);
    }
    static boolean shouldRenderRoundOverlays(TrackingState state, RoundData round) {
        return isInRound(state);
    }
    /** True iff tab/nameplate BB rank formatting may be repaired. */
    public boolean shouldRepairBbRankDisplay() { return isBbRankDisplayRepairState(state); }
    /** Returns the most recently captured sidebar timer value (e.g. "1:23"), or null. */
    public String getCurrentTimerText() { return lastSidebarTimer; }
    /** Returns the most recently captured sidebar Theme: line value (e.g. "Foxx Mask"), or null. */
    public String getCurrentSidebarTheme() { return lastSidebarTheme; }
    /** Returns the most recent XP-bar countdown level, or null when unknown. */
    public Integer getCurrentPhysicalTimerSeconds() { return currentPhysicalTimerSeconds; }

    public boolean acceptsGtbRosterPackets() {
        return state == TrackingState.LOBBY
                && gtbLobbyRosterConfirmed;
    }

    /** Applies sidebar-title boundaries in packet order before queued tab/team roster data. */
    public void onRosterObjectivePacket(String formattedTitle, int mode) {
        if ((mode != 0 && mode != 2) || state != TrackingState.LOBBY) return;
        String cleanTitle = StringUtils.stripControlCodes(
                formattedTitle == null ? "" : formattedTitle).trim();
        boolean gtbObjective = LangPatterns.isGtbTitle(cleanTitle);
        if (gtbObjective) {
            gtbLobbyRosterConfirmed = true;
            lobbyTabDone = false;
            return;
        }

        gtbLobbyRosterConfirmed = false;
        clearLobbyHandoff();
        lobbyTabDone = false;
        players.clear();
        rankings.clear();
        PlayerMetaCache.INSTANCE.clearHarvestedRanks();
        lobbyQuitSuppress.clear();
    }

    public void onPhysicalTimerUpdate(float progress, int totalExperience, int level) {
        if (level < 0 || level > 120) return;

        currentPhysicalTimerSeconds = Integer.valueOf(level);

        if (!trackingEnabled || currentRound == null || !isInRound(state)) {
            resetTrackedPhysicalTimerComparator();
            return;
        }

        boolean sameTimerBucket = lastTrackedPhysicalTimerSeconds != null
                && lastTrackedPhysicalTimerState == state
                && lastTrackedPhysicalTimerRound == currentRound;
        if (sameTimerBucket && Math.abs(level - lastTrackedPhysicalTimerSeconds.intValue()) >= 2) {
            GameEvent event = new GameEvent(System.currentTimeMillis(), "physical_timer_jump", null);
            event.physicalTimerBefore = lastTrackedPhysicalTimerSeconds;
            event.physicalTimerAfter = Integer.valueOf(level);
            currentRound.events.add(event);
            writeLiveFile();
        }

        lastTrackedPhysicalTimerSeconds = Integer.valueOf(level);
        lastTrackedPhysicalTimerState = state;
        lastTrackedPhysicalTimerRound = currentRound;
    }

    private void resetTrackedPhysicalTimerComparator() {
        lastTrackedPhysicalTimerSeconds = null;
        lastTrackedPhysicalTimerState = null;
        lastTrackedPhysicalTimerRound = null;
    }

    /** Returns mid-game unattributed residual for this player (points known to have
     *  been earned but unattributable to a specific round), or 0. */
    public int getMidGameResidual(String ign) {
        Integer v = midGameUnattributedResidual.get(ign);
        return v == null ? 0 : v;
    }

    /** Mark a player as actively alive right now (chat received, score grew,
     *  etc.). Concrete activity clears the leaver flag; passive roster/tab
     *  echoes do not, because Hypixel can replay stale roster presence around
     *  round-start skips. */
    public void markActivity(String ign) {
        recordActivity(ign, true);
    }

    private void markPassivePresence(String ign) {
        recordActivity(ign, false);
    }

    private void recordActivity(String ign, boolean clearLeaver) {
        if (ign == null || ign.isEmpty()) return;
        if (isReservedNonPlayerName(ign)) return;
        lastActivityMs.put(ign, System.currentTimeMillis());
        if (clearLeaver) {
            clearLeaverFromReturnEvidence(ign, "activity");
        }
    }

    private boolean clearLeaverFromReturnEvidence(String ign, String source) {
        if (!removeLeaverIgnoreCase(ign)) return false;
        removePendingBuilderTabReturnIgnoreCase(ign);
        removeBuilderLeftLeaverIgnoreCase(ign);
        recordPlayerRejoinEvidence(ign, source);
        if (gtbHooks != null) gtbHooks.onPlayerRejoin(ign);
        return true;
    }

    private void scheduleBuilderLeftReturnFromTab(String ign, long now) {
        if (!containsBuilderLeftLeaverIgnoreCase(ign)) return;
        removePendingBuilderTabReturnIgnoreCase(ign);
        pendingBuilderTabReturns.put(ign, Long.valueOf(now + TAB_RETURN_CONFIRM_DELAY_MS));
    }

    void confirmPendingBuilderTabReturns(long now) {
        java.util.Iterator<Map.Entry<String, Long>> it = pendingBuilderTabReturns.entrySet().iterator();
        List<String> confirmed = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (entry.getValue().longValue() > now) continue;
            confirmed.add(entry.getKey());
            it.remove();
        }
        for (String ign : confirmed) {
            if (!containsBuilderLeftLeaverIgnoreCase(ign)) continue;
            lastActivityMs.put(ign, Long.valueOf(now));
            clearLeaverFromReturnEvidence(ign, "tab");
        }
    }

    private boolean removePendingBuilderTabReturnIgnoreCase(String ign) {
        if (ign == null || ign.isEmpty()) return false;
        if (pendingBuilderTabReturns.remove(ign) != null) return true;
        java.util.Iterator<String> it = pendingBuilderTabReturns.keySet().iterator();
        while (it.hasNext()) {
            String existing = it.next();
            if (existing != null && existing.equalsIgnoreCase(ign)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    private boolean removeLeaverIgnoreCase(String ign) {
        if (ign == null || ign.isEmpty()) return false;
        if (leavers.remove(ign)) return true;
        java.util.Iterator<String> it = leavers.iterator();
        while (it.hasNext()) {
            String existing = it.next();
            if (existing != null && existing.equalsIgnoreCase(ign)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    private boolean containsBuilderLeftLeaverIgnoreCase(String ign) {
        if (ign == null || ign.isEmpty()) return false;
        if (builderLeftLeavers.contains(ign)) return true;
        for (String existing : builderLeftLeavers) {
            if (existing != null && existing.equalsIgnoreCase(ign)) return true;
        }
        return false;
    }

    private boolean removeBuilderLeftLeaverIgnoreCase(String ign) {
        if (ign == null || ign.isEmpty()) return false;
        if (builderLeftLeavers.remove(ign)) return true;
        java.util.Iterator<String> it = builderLeftLeavers.iterator();
        while (it.hasNext()) {
            String existing = it.next();
            if (existing != null && existing.equalsIgnoreCase(ign)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    private void recordPlayerRejoinEvidence(String ign, String source) {
        if (currentRound == null || !gameInitialized) return;
        String detail = "ign=" + ign;
        if (source != null && !source.isEmpty()) detail += ",source=" + source;
        currentRound.events.add(new GameEvent(System.currentTimeMillis(),
                "player_rejoin", detail));
        writeLiveFile();
    }

    public void onPacketRosterAdd(String ign) {
        ign = canonicalRosterIgn(ign);
        if (!PlayerMetaCache.isValidMcUsername(ign) || isReservedNonPlayerName(ign)) return;
        // Netty tab packets are drained at client-tick END, after vanilla can
        // already have delivered a later lobby-quit chat message. Never let
        // that older queued ADD_PLAYER observation undo authoritative chat
        // evidence; also purge the just-replayed name from the packet cache.
        if (state == TrackingState.LOBBY && isLobbyQuitSuppressed(ign)) {
            PlayerMetaCache.INSTANCE.observePacketRosterRemove(ign);
            return;
        }
        removeLobbyPacketRemovalIgnoreCase(ign);
        if (!acceptsGtbRosterPackets()) {
            if (isRosterTrackingState() && containsPlayerIgnoreCase(ign)) {
                scheduleBuilderLeftReturnFromTab(ign, System.currentTimeMillis());
                if (gtbHooks != null) gtbHooks.onPlayerTabPresent(ign);
            }
            return;
        }
        boolean added = false;
        if (!containsPlayerIgnoreCase(ign)) {
            players.add(ign);
            added = true;
        }
        markPassivePresence(ign);
        if (added && gtbHooks != null && gameInitialized) {
            gtbHooks.onGameRosterPlayer(ign);
        }
        if (gtbHooks != null) gtbHooks.onPlayerTabPresent(ign);
    }

    public void onPacketRosterRemove(String ign) {
        ign = canonicalRosterIgn(ign);
        if (!PlayerMetaCache.isValidMcUsername(ign) || isReservedNonPlayerName(ign)) return;
        if (state == TrackingState.LOBBY && gtbLobbyRosterConfirmed) {
            rememberLobbyPacketRemoval(ign);
            pruneLobbyRosterToAnnouncedCount();
            return;
        }
        if (!isRosterTrackingState()) return;
        removePendingBuilderTabReturnIgnoreCase(ign);
        // Hypixel rebuilds tab aggressively at game start/round boundaries.
        // A REMOVE_PLAYER packet is only useful to corroborate/sequence an
        // already-known leave; it is not standalone leave evidence.
        if (gtbHooks != null) gtbHooks.onPlayerTabAbsent(ign);
    }

    public void onEncodedGameRosterPlayer(String ign) {
        ign = canonicalRosterIgn(ign);
        if (!acceptsGtbRosterPackets()) return;
        if (!PlayerMetaCache.isValidMcUsername(ign) || isReservedNonPlayerName(ign)) return;
        if (isLobbyQuitSuppressed(ign)) return;
        if (!containsPlayerIgnoreCase(ign)) {
            players.add(ign);
        }
        markPassivePresence(ign);
        if (gtbHooks != null && gameInitialized) gtbHooks.onGameRosterPlayer(ign);
    }

    private boolean isRosterTrackingState() {
        return state == TrackingState.ROUND_PRE
                || state == TrackingState.ROUND_BUILD
                || state == TrackingState.ROUND_END;
    }

    private boolean isEncodedRosterTrackingState() {
        return (state == TrackingState.LOBBY && gtbLobbyRosterConfirmed)
                || state == TrackingState.ROUND_PRE
                || state == TrackingState.ROUND_BUILD
                || state == TrackingState.ROUND_END
                || (state == TrackingState.NOT_IN_GAME
                    && System.currentTimeMillis() <= gtbHandoffRosterUntilMs);
    }

    /** True iff this player has been silent + score-stable longer than the
     *  inactive threshold. Returns false when no activity was ever recorded
     *  (e.g. never seen) so unknown players don't show as inactive on first frame. */
    public boolean isInactive(String ign) {
        Long t = lastActivityMs.get(ign);
        if (t == null) return false;
        return (System.currentTimeMillis() - t) > INACTIVE_THRESHOLD_MS;
    }
    public boolean isLeaver(String ign) { return containsLeaverIgnoreCase(ign); }

    private boolean containsLeaverIgnoreCase(String ign) {
        if (ign == null || ign.isEmpty()) return false;
        if (leavers.contains(ign)) return true;
        for (String existing : leavers) {
            if (existing != null && existing.equalsIgnoreCase(ign)) return true;
        }
        return false;
    }

    /**
     * Rename a player across all GameStats state. Used for identity transfer
     * when a leaderboard player nick/denick toggles across an L/RJ — Hypixel
     * preserves the score, we just have a new IGN to point at the same data.
     */
    public void transferIdentity(String oldIgn, String newIgn) {
        if (oldIgn == null || newIgn == null) return;
        if (oldIgn.equalsIgnoreCase(newIgn)) return;
        // Players list
        replacePlayerIdentity(oldIgn, newIgn);
        PlayerMetaCache.INSTANCE.transferIdentity(oldIgn, newIgn);
        // Round data
        for (RoundData r : rounds) {
            if (r.builder != null && r.builder.equalsIgnoreCase(oldIgn)) r.builder = newIgn;
            renameMapKey(r.scoreDeltas, oldIgn, newIgn);
            for (CorrectGuessEntry c : r.correctGuesses) {
                if (c.ign != null && c.ign.equalsIgnoreCase(oldIgn)) c.ign = newIgn;
            }
            for (GuessEntry g : r.guesses) {
                if (g.ign != null && g.ign.equalsIgnoreCase(oldIgn)) g.ign = newIgn;
            }
        }
        // Maps and sets
        renameMapKey(midGameUnattributedResidual, oldIgn, newIgn);
        renameMapKey(roundsMissedDuringDisconnect, oldIgn, newIgn);
        if (localPlayer != null && localPlayer.equalsIgnoreCase(oldIgn)) {
            setLocalPlayerIdentity(newIgn);
        }
        renameMapKey(lastUnknownResidual, oldIgn, newIgn);
        renameMapKey(lastActivityMs, oldIgn, newIgn);
        renameMapKey(lastSidebarScores, oldIgn, newIgn);
        renameMapKey(sidebarDropKnownScores, oldIgn, newIgn);
        renameMapKey(pendingBuilderTabReturns, oldIgn, newIgn);
        if (leavers.remove(oldIgn)) leavers.add(newIgn);
        if (builderLeftLeavers.remove(oldIgn)) builderLeftLeavers.add(newIgn);
        // Audit trail
        if (currentRound != null) {
            currentRound.events.add(new GameEvent(System.currentTimeMillis(),
                    "identity_transfer", oldIgn + "->" + newIgn));
        }
        if (gtbHooks != null) gtbHooks.onIdentityTransfer(oldIgn, newIgn);
    }

    private void observeLocalIdentity(String localNow, TrackingState observedScoreboardState) {
        if (localNow == null || localNow.isEmpty()) return;

        captureTrueIgn();
        if (PlayerMetaCache.isValidMcUsername(serverConfirmedLocalIgn)
                && PlayerMetaCache.isValidMcUsername(trueIgn)
                && localNow.equalsIgnoreCase(trueIgn)
                && !serverConfirmedLocalIgn.equalsIgnoreCase(trueIgn)
                && PlayerMetaCache.isValidMcUsername(localPlayer)
                && localPlayer.equalsIgnoreCase(serverConfirmedLocalIgn)) {
            lastLocalIgn = serverConfirmedLocalIgn;
            return;
        }

        boolean confirmedPausedRecovery = state == TrackingState.PAUSED
                && isInRound(observedScoreboardState);
        if (state == TrackingState.PAUSED && !confirmedPausedRecovery) {
            return;
        }

        if (lastLocalIgn != null && !lastLocalIgn.equalsIgnoreCase(localNow)
                && (isActiveGame(state) || confirmedPausedRecovery)) {
            // The local entity name is authoritative. The returned name may
            // already exist as a roster shell, so it must not be rejected by
            // the conservative sidebar-only "new IGN" check.
            transferIdentity(lastLocalIgn, localNow);
        }
        lastLocalIgn = localNow;
    }

    private static <V> void renameMapKey(Map<String, V> m, String oldKey, String newKey) {
        if (m == null) return;
        String matched = findKeyIgnoreCase(m, oldKey);
        if (matched == null) return;
        V v = m.remove(matched);
        m.put(newKey, v);
    }

    private static <V> String findKeyIgnoreCase(Map<String, V> m, String key) {
        if (m == null || key == null) return null;
        for (String existing : m.keySet()) {
            if (existing != null && existing.equalsIgnoreCase(key)) return existing;
        }
        return null;
    }

    private void replacePlayerIdentity(String oldIgn, String newIgn) {
        boolean newAlreadyPresent = containsPlayerIgnoreCase(newIgn);
        int oldIndex = -1;
        for (int i = 0; i < players.size(); i++) {
            String existing = players.get(i);
            if (existing != null && existing.equalsIgnoreCase(oldIgn)) {
                if (oldIndex < 0) oldIndex = i;
                else players.remove(i--);
            }
        }
        if (oldIndex >= 0) {
            if (newAlreadyPresent) players.remove(oldIndex);
            else players.set(oldIndex, newIgn);
        } else if (!newAlreadyPresent) {
            players.add(newIgn);
        }
    }

    /**
     * Diff current vs. last sidebar snapshot for local nick/denick recovery.
     * Non-local same-score row swaps are too ambiguous: Hypixel can redraw rows
     * with transient scoreboard keys, which must not rewrite another player's
     * history.
     */
    private void detectIdentityTransfer(Map<String, Integer> currentSidebar) {
        if (lastSidebarScores == null || lastSidebarScores.isEmpty()) return;
        if (currentSidebar == null || currentSidebar.isEmpty()) return;
        // Compute disappeared / appeared sets.
        List<String> disappeared = new ArrayList<>();
        List<String> appeared = new ArrayList<>();
        for (String prev : lastSidebarScores.keySet()) {
            if (!currentSidebar.containsKey(prev)) disappeared.add(prev);
        }
        for (String now : currentSidebar.keySet()) {
            if (!lastSidebarScores.containsKey(now)) appeared.add(now);
        }
        if (disappeared.size() != 1 || appeared.size() != 1) return;
        String oldIgn = disappeared.get(0);
        String newIgn = appeared.get(0);
        Integer oldScore = lastSidebarScores.get(oldIgn);
        Integer newScore = currentSidebar.get(newIgn);
        if (oldScore == null || newScore == null) return;
        if (!oldScore.equals(newScore)) return;
        if (!isLocalSidebarIdentityTransfer(oldIgn, newIgn)) return;
        if (!isIgnNewToGameStats(newIgn)) return;
        transferIdentity(oldIgn, newIgn);
    }

    private boolean isLocalSidebarIdentityTransfer(String oldIgn, String newIgn) {
        String local = null;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            local = mc.thePlayer != null ? mc.thePlayer.getName() : null;
        } catch (Exception ignored) { /* best-effort */ }
        if (local == null || local.isEmpty()) local = lastLocalIgn;
        if (local == null || local.isEmpty()) return false;
        return oldIgn != null && oldIgn.equalsIgnoreCase(local)
                || newIgn != null && newIgn.equalsIgnoreCase(local);
    }

    /**
     * Treat a sidebar row that stays absent across several polls as a quiet leave
     * only when the current sidebar proves they could not have been pushed out:
     * either the visible top-three floor is below their last score, or Hypixel is
     * showing two-or-fewer leaderboard rows, which is the full active roster.
     */
    private void detectLeaderboardDrops(Map<String, Integer> currentSidebar) {
        boolean authoritativeLowRoster = currentSidebar != null
                && !currentSidebar.isEmpty()
                && currentSidebar.size() < SIDEBAR_DROP_REQUIRED_VISIBLE_ROWS;
        if (lastSidebarScores == null
                || (lastSidebarScores.isEmpty() && sidebarDropKnownScores.isEmpty()
                && sidebarDropMissingTicks.isEmpty()
                && !authoritativeLowRoster)
                || currentSidebar == null
                || state != TrackingState.ROUND_BUILD || currentRound == null
                || (!authoritativeLowRoster && (pendingRejoinCheck
                || localRejoinedImmediatelyStage
                || roundHasEvent(currentRound, "builder_left")
                || roundHasEvent(currentRound, "afk_skip")))) {
            sidebarDropMissingTicks.clear();
            sidebarDropMissingScores.clear();
            return;
        }

        List<String> trackedActiveRoster = new ArrayList<String>();
        for (String player : players) {
            if (player != null && !player.isEmpty() && !containsLeaverIgnoreCase(player)) {
                trackedActiveRoster.add(player);
            }
        }
        seedSidebarDropCandidatesFromKnownScores(
                sidebarDropKnownScores, currentSidebar, sidebarDropMissingTicks,
                sidebarDropMissingScores, trackedActiveRoster,
                SIDEBAR_DROP_REQUIRED_VISIBLE_ROWS);
        Set<String> confirmed = advanceSidebarDropCandidates(
                lastSidebarScores, currentSidebar, sidebarDropMissingTicks,
                sidebarDropMissingScores, trackedActiveRoster,
                SIDEBAR_DROP_REQUIRED_MISSING_TICKS,
                SIDEBAR_DROP_REQUIRED_VISIBLE_ROWS);
        long nowMs = System.currentTimeMillis();
        for (String ign : confirmed) {
            if (!authoritativeLowRoster && currentRound.builder != null
                    && currentRound.builder.equalsIgnoreCase(ign)) {
                continue;
            }
            if (containsLeaverIgnoreCase(ign)) continue;
            leavers.add(ign);
            if (currentRound != null) {
                currentRound.events.add(new GameEvent(nowMs, "leaderboard_drop_left", ign));
            }
            if (gtbHooks != null) gtbHooks.onConfirmedPlayerLeft(ign);
        }
    }

    private void clearLeaverOnLeaderboardReappearance(String ign) {
        if (ign == null || ign.isEmpty()
                || findKeyIgnoreCase(lastSidebarScores, ign) != null
                || !containsLeaverIgnoreCase(ign)) {
            return;
        }
        clearLeaverFromReturnEvidence(ign, "leaderboard");
    }

    private void syncSidebarTrackingRound() {
        if (sidebarTrackingRound == currentRound) return;
        sidebarTrackingRound = currentRound;
        resetSidebarTrackingBaseline(lastSidebarScores, sidebarDropMissingTicks, sidebarDropMissingScores);
        pendingUnknownSidebarPlayers.clear();
    }

    static void resetSidebarTrackingBaseline(
            Map<String, Integer> lastSidebarScores,
            Map<String, Integer> missingTicks) {
        resetSidebarTrackingBaseline(lastSidebarScores, missingTicks, null);
    }

    static void resetSidebarTrackingBaseline(
            Map<String, Integer> lastSidebarScores,
            Map<String, Integer> missingTicks,
            Map<String, Integer> missingScores) {
        if (lastSidebarScores != null) lastSidebarScores.clear();
        if (missingTicks != null) missingTicks.clear();
        if (missingScores != null) missingScores.clear();
    }

    static Set<String> advanceSidebarDropCandidates(
            Map<String, Integer> previousSidebar,
            Map<String, Integer> currentSidebar,
            Map<String, Integer> missingTicks,
            Map<String, Integer> missingScores,
            int requiredMissingTicks,
            int requiredVisibleRows) {
        return advanceSidebarDropCandidates(previousSidebar, currentSidebar,
                missingTicks, missingScores, null, requiredMissingTicks, requiredVisibleRows);
    }

    static Set<String> advanceSidebarDropCandidates(
            Map<String, Integer> previousSidebar,
            Map<String, Integer> currentSidebar,
            Map<String, Integer> missingTicks,
            Map<String, Integer> missingScores,
            Iterable<String> knownRoster,
            int requiredMissingTicks,
            int requiredVisibleRows) {
        Set<String> confirmed = new LinkedHashSet<String>();
        if (currentSidebar == null || missingTicks == null || missingScores == null
                || requiredMissingTicks <= 0) {
            return confirmed;
        }

        int visibleRows = currentSidebar.size();
        int minRows = Math.max(1, requiredVisibleRows);
        boolean lowVisibleFullRoster = visibleRows < minRows;
        boolean authoritativeKnownRoster = visibleRows > 0 && lowVisibleFullRoster;
        for (String present : currentSidebar.keySet()) {
            String tracked = findKeyIgnoreCase(missingTicks, present);
            if (tracked != null) {
                missingTicks.remove(tracked);
                removeKeyIgnoreCase(missingScores, present);
            }
        }
        if (previousSidebar != null) {
            for (String previous : previousSidebar.keySet()) {
                if (previous == null || previous.isEmpty()
                        || findKeyIgnoreCase(currentSidebar, previous) != null
                        || findKeyIgnoreCase(missingTicks, previous) != null) {
                    continue;
                }
                Integer previousScore = previousSidebar.get(previous);
                if (previousScore == null
                        || (!lowVisibleFullRoster
                        && !hasSidebarSuccessionEvidence(previousSidebar, currentSidebar))) {
                    continue;
                }
                missingTicks.put(previous, Integer.valueOf(0));
                missingScores.put(previous, previousScore);
            }
        }
        if (authoritativeKnownRoster && knownRoster != null) {
            for (String known : knownRoster) {
                if (known == null || known.isEmpty()
                        || findKeyIgnoreCase(currentSidebar, known) != null
                        || findKeyIgnoreCase(missingTicks, known) != null) {
                    continue;
                }
                missingTicks.put(known, Integer.valueOf(0));
                Integer previousScore = scoreIgnoreCase(previousSidebar, known);
                if (previousScore != null) missingScores.put(known, previousScore);
            }
        }

        Integer currentFloor = lowVisibleFullRoster
                ? null : visibleLeaderboardCutoffScore(currentSidebar, minRows);
        for (String missing : new ArrayList<String>(missingTicks.keySet())) {
            if (findKeyIgnoreCase(currentSidebar, missing) != null) {
                missingTicks.remove(missing);
                removeKeyIgnoreCase(missingScores, missing);
                continue;
            }
            Integer missingScore = scoreIgnoreCase(missingScores, missing);
            boolean knownRosterPlayer = authoritativeKnownRoster
                    && containsIgnoreCase(knownRoster, missing);
            boolean confirmedByFullLowRoster = lowVisibleFullRoster
                    && (missingScore != null || knownRosterPlayer);
            boolean confirmedByCutoff = currentFloor != null
                    && missingScore != null
                    && missingScore.intValue() > currentFloor.intValue();
            if (!confirmedByFullLowRoster && !confirmedByCutoff) {
                missingTicks.put(missing, Integer.valueOf(0));
                continue;
            }
            Integer prior = missingTicks.get(missing);
            int count = prior == null ? 1 : prior.intValue() + 1;
            if (count >= requiredMissingTicks) {
                confirmed.add(missing);
                missingTicks.remove(missing);
                removeKeyIgnoreCase(missingScores, missing);
            } else {
                missingTicks.put(missing, Integer.valueOf(count));
            }
        }
        return confirmed;
    }

    static void rememberSidebarDropScores(
            Map<String, Integer> knownScores,
            Map<String, Integer> currentSidebar) {
        if (knownScores == null || currentSidebar == null) return;
        for (Map.Entry<String, Integer> entry : currentSidebar.entrySet()) {
            String ign = entry.getKey();
            Integer score = entry.getValue();
            if (ign == null || ign.isEmpty() || score == null) continue;
            String knownKey = findKeyIgnoreCase(knownScores, ign);
            if (knownKey == null) {
                knownScores.put(ign, score);
                continue;
            }
            Integer knownScore = knownScores.get(knownKey);
            if (knownScore == null || score.intValue() > knownScore.intValue()) {
                knownScores.put(knownKey, score);
            }
        }
    }

    static void seedSidebarDropCandidatesFromKnownScores(
            Map<String, Integer> knownScores,
            Map<String, Integer> currentSidebar,
            Map<String, Integer> missingTicks,
            Map<String, Integer> missingScores,
            Iterable<String> knownRoster,
            int requiredVisibleRows) {
        if (knownScores == null || knownScores.isEmpty()
                || currentSidebar == null || missingTicks == null || missingScores == null) {
            return;
        }
        Integer cutoff = visibleLeaderboardCutoffScore(currentSidebar, requiredVisibleRows);
        if (cutoff == null) return;
        for (Map.Entry<String, Integer> entry : knownScores.entrySet()) {
            String ign = entry.getKey();
            Integer score = entry.getValue();
            if (ign == null || ign.isEmpty() || score == null
                    || !containsIgnoreCase(knownRoster, ign)
                    || findKeyIgnoreCase(currentSidebar, ign) != null
                    || findKeyIgnoreCase(missingTicks, ign) != null
                    || score.intValue() <= cutoff.intValue()) {
                continue;
            }
            missingTicks.put(ign, Integer.valueOf(0));
            missingScores.put(ign, score);
        }
    }

    private static boolean containsIgnoreCase(Iterable<String> values, String value) {
        if (values == null || value == null) return false;
        for (String existing : values) {
            if (existing != null && existing.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    static boolean shouldAdmitSidebarPlayer(String ign, Iterable<String> knownPlayers,
                                            Iterable<String> priorUnknownPlayers) {
        return containsIgnoreCase(knownPlayers, ign)
                || containsIgnoreCase(priorUnknownPlayers, ign);
    }

    private static boolean hasSidebarSuccessionEvidence(
            Map<String, Integer> previousSidebar,
            Map<String, Integer> currentSidebar) {
        if (previousSidebar == null || previousSidebar.isEmpty()
                || currentSidebar == null || currentSidebar.isEmpty()) return false;
        for (Map.Entry<String, Integer> current : currentSidebar.entrySet()) {
            String previousKey = findKeyIgnoreCase(previousSidebar, current.getKey());
            if (previousKey == null) return true;
            Integer previousScore = previousSidebar.get(previousKey);
            if (previousScore == null && current.getValue() != null) return true;
            if (previousScore != null && !previousScore.equals(current.getValue())) return true;
        }
        return false;
    }

    private static Integer visibleLeaderboardCutoffScore(
            Map<String, Integer> scores,
            int requiredVisibleRows) {
        if (scores == null) return null;
        int rowCount = Math.max(1, requiredVisibleRows);
        List<Integer> visibleScores = new ArrayList<Integer>();
        for (Integer value : scores.values()) {
            if (value != null) visibleScores.add(value);
        }
        if (visibleScores.size() < rowCount) return null;
        Collections.sort(visibleScores, Collections.reverseOrder());
        return visibleScores.get(rowCount - 1);
    }

    private static Integer scoreIgnoreCase(Map<String, Integer> scores, String key) {
        String actual = findKeyIgnoreCase(scores, key);
        return actual == null ? null : scores.get(actual);
    }

    private static void removeKeyIgnoreCase(Map<String, Integer> scores, String key) {
        String actual = findKeyIgnoreCase(scores, key);
        if (actual != null) scores.remove(actual);
    }

    /** True iff this IGN has no prior trace in GameStats — needed for the
     *  identity-transfer "B is fresh" confidence check. */
    private static boolean roundHasEvent(RoundData round, String type) {
        if (round == null || round.events == null || type == null) return false;
        for (GameEvent event : round.events) {
            if (event != null && type.equals(event.type)) return true;
        }
        return false;
    }

    private static boolean roundHasSkipEvent(RoundData round) {
        return roundHasEvent(round, "builder_left") || roundHasEvent(round, "afk_skip");
    }

    private boolean isIgnNewToGameStats(String ign) {
        if (ign == null || ign.isEmpty()) return false;
        for (RoundData r : rounds) {
            if (r.builder != null && r.builder.equalsIgnoreCase(ign)) return false;
            for (String k : r.scoreDeltas.keySet()) {
                if (k.equalsIgnoreCase(ign)) return false;
            }
            for (CorrectGuessEntry c : r.correctGuesses) {
                if (c.ign != null && c.ign.equalsIgnoreCase(ign)) return false;
            }
            for (GuessEntry g : r.guesses) {
                if (g.ign != null && g.ign.equalsIgnoreCase(ign)) return false;
            }
        }
        if (hasKeyIgnoreCase(midGameUnattributedResidual, ign)) return false;
        if (hasKeyIgnoreCase(roundsMissedDuringDisconnect, ign)) return false;
        if (hasKeyIgnoreCase(lastUnknownResidual, ign)) return false;
        for (String leaver : leavers) {
            if (leaver != null && leaver.equalsIgnoreCase(ign)) return false;
        }
        return true;
    }

    private static boolean hasKeyIgnoreCase(Map<String, ?> m, String key) {
        return findKeyIgnoreCase(m, key) != null;
    }
    /** Returns rounds-missed count for severe-uncertainty detection. */
    public int getRoundsMissedDuringDisconnect(String ign) {
        Integer v = roundsMissedDuringDisconnect.get(ign);
        return v == null ? 0 : v;
    }
    /** True when the local player rejoined and the round count is not yet
     *  reconfirmed (no ROUND_PRE seen since rejoin). Cleared at next ROUND_PRE. */
    public boolean isLocalRejoinedImmediatelyStage() { return localRejoinedImmediatelyStage; }

    /** Bounds on a player's cumulative score from definite/fork delta arithmetic.
     *  Does not include {@link #getMidGameResidual}. */
    public static final class CumulativeBounds {
        public final int definiteSum;
        public final int forkMin;   // sum across all rounds using each fork's min
        public final int forkMax;   // sum across all rounds using each fork's max
        CumulativeBounds(int d, int lo, int hi) {
            this.definiteSum = d; this.forkMin = lo; this.forkMax = hi;
        }
    }
    public CumulativeBounds cumulativeBoundsFor(String ign) {
        if (ign == null) return new CumulativeBounds(0, 0, 0);
        int definite = 0, lo = 0, hi = 0;
        for (RoundData r : rounds) {
            ScoreDelta d = r.scoreDeltas.get(ign);
            if (d == null) continue;
            if (d.isDefinite()) {
                definite += d.value;
                lo += d.value;
                hi += d.value;
            } else if (d.fork != null && !d.fork.isEmpty()) {
                int fLo = d.fork.get(0);
                int fHi = d.fork.get(0);
                for (Integer v : d.fork) {
                    if (v < fLo) fLo = v;
                    if (v > fHi) fHi = v;
                }
                lo += fLo;
                hi += fHi;
            }
        }
        return new CumulativeBounds(definite, lo, hi);
    }

    /** Returns the current round's delta for this player, or null. */
    public ScoreDelta currentRoundDeltaFor(String ign) {
        if (currentRound == null || ign == null) return null;
        return currentRound.scoreDeltas.get(ign);
    }
    public boolean currentRoundIsDataGap() {
        return currentRound != null && currentRound.dataGap;
    }

    private void handleLocalCorrectGuess(String ign, long now, boolean updateCooldown) {
        if (currentRound == null || !PlayerMetaCache.isValidMcUsername(ign)) return;
        if (updateCooldown) ChatCooldownCounter.INSTANCE.onUserCorrectGuess();
        rememberPendingSelfGuessScore(ign, now);
        ThemePointReveal.markCompleteThemeRevealed(currentRound);
        maybeSendThemePoint(currentRound.theme, currentRound.langCode);
        ShortcutReminder.maybeSend(currentRound);
        ShowAllMatching.maybeSend(currentRound);
    }

    private void rememberPendingSelfGuessScore(String ign, long now) {
        if (ign == null || currentRound == null) return;
        pendingSelfGuessScoreIgn = ign;
        pendingSelfGuessScoreRound = currentRound.roundNumber;
        pendingSelfGuessScoreUntilMs = now + SELF_GUESS_SCORE_WINDOW_MS;
    }

    private boolean applyPendingSelfGuessScore(int points, long now) {
        if (currentRound == null || pendingSelfGuessScoreIgn == null) return false;
        if (now > pendingSelfGuessScoreUntilMs) {
            clearPendingSelfGuessScore();
            return false;
        }
        if (pendingSelfGuessScoreRound != currentRound.roundNumber) {
            clearPendingSelfGuessScore();
            return false;
        }
        String ign = pendingSelfGuessScoreIgn;
        ScoreReconciler.recordExplicitGuessDelta(currentRound, ign, points, "self_points");
        if (gtbHooks != null) gtbHooks.onCorrectGuessScore(ign, points);
        clearPendingSelfGuessScore();
        return true;
    }

    private void clearPendingSelfGuessScore() {
        pendingSelfGuessScoreIgn = null;
        pendingSelfGuessScoreRound = 0;
        pendingSelfGuessScoreUntilMs = 0L;
    }

    // ── Event Handlers ───────────────────────────────────────────────────

    /**
     * Called from type-0 chat events. Both raw (with formatting codes) and clean text.
     */
    public void onChatMessage(String raw, String clean) {
        raw = stripChatSuffix(raw);
        clean = StringUtils.stripControlCodes(clean);
        if (clean == null) clean = "";
        clean = stripChatSuffix(clean);

        // Sound observation is deliberately independent of diagnostic/stat
        // tracking, but must run only on system lines.  In particular, a
        // party message can copy a localized correct-guess suffix verbatim.
        observeGuessSoundChat(clean, System.currentTimeMillis());

        maybeAutoDisableTrollModeFromRoundStart(clean);
        if (!trackingEnabled) return;

        // ── Player message filter: GTB title or localized guesser-chat prefix ──
        String guesserChatMessage = LangPatterns.stripGuesserChatPrefix(clean);
        boolean isPlayerMessage = clean.contains(": ")
                && (guesserChatMessage != null
                || LangPatterns.startsWithChatTitle(LangPatterns.stripEmojis(clean).trim()));

        if (isPlayerMessage) {
            // Always run the full chat parser so emblem-having players' IGN is
            // extracted by the same reverse-scan logic used inside the round.
            // During PAUSED it retains activity/emblem updates only for the
            // preserved game roster; unrelated lobby chat must not enter the
            // suspended game's custom scoreboard.
            parsePlayerChat(raw, guesserChatMessage != null ? guesserChatMessage : clean);
            return;
        }

        // Party/guild/direct-message envelopes are ordinary chat even when
        // their body copies a localized system notice verbatim. Do not let
        // those bodies fall through into suffix-based game-event parsing.
        if (isNonGameChatChannel(clean)) return;

        // ── System message parsing ──
        long now = System.currentTimeMillis();
        if (handleLobbyJoinMessage(raw, clean, now)) {
            return;
        }

        String fallbackLang = lobbyLanguageFallback.confirmFromChat(clean);
        if (fallbackLang != null) {
            applyLobbyLanguageFallback(fallbackLang);
            return;
        }

        Integer explicitSelfPoints = LangPatterns.selfCorrectGuessPoints(clean);
        if (explicitSelfPoints != null) {
            ChatCooldownCounter.INSTANCE.onUserCorrectGuess();
            applyPendingSelfGuessScore(explicitSelfPoints.intValue(), now);
            return;
        }

        // ── 1-second countdown: language autodetect (any state) + tab snapshot anchor (LOBBY only) ──
        // The 1s countdown is sometimes the only language clue available before round 1
        // (e.g. mid-game rejoin while a friend is in pirate). Autodetect runs in any state.
        // Tab snapshot only schedules from LOBBY (Hypixel populates the minigame tab there).
        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (lp.countdown1Sec != null && clean.equals(lp.countdown1Sec)) {
                disableLobbyLanguageFallback();
                maybeAutoDetectLanguage(lp);
                if (state == TrackingState.LOBBY && !lobbyTabDone) {
                    lobbyTabDone = snapshotTabList();
                }
                return;
            }
        }

        // ── Lobby join/leave detection: maintain player list from chat during LOBBY ──
        if (state == TrackingState.LOBBY) {
            String lobbyQuitIgn = parseLobbyQuitIgn(clean);
            if (lobbyQuitIgn != null) {
                suppressLobbyQuit(lobbyQuitIgn);
                return;
            }

        }

        // ── Rejoin detection via "To leave Build Battle, type /lobby" ──
        if (clean.contains("/lobby") && clean.contains("Build Battle")) {
            if (state == TrackingState.PAUSED) {
                state = TrackingState.ROUND_BUILD;
                pauseTime = 0;
                pendingRejoinCheck = true;
                invalidatePreLeaveSidebarSnapshot();
                snapshotTabList();
                if (currentRound != null) {
                    currentRound.events.add(new GameEvent(now, "rejoin", "lobby_message"));
                }
                if (gtbHooks != null) gtbHooks.onUserRejoin();
            }
            return;
        }

        // Japanese split-line round: if we got ラウンド： last time, this message should be X/Y
        if (jaRoundLabelPending) {
            jaRoundLabelPending = false;
            Matcher m = ROUND_NUM_PATTERN.matcher(clean.trim());
            if (m.matches()) {
                disableLobbyLanguageFallback();
                handleRoundStart(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), now);
                return;
            }
        }

        // Try all language patterns
        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            // ── Round ──
            if (lp.roundPrefix != null && clean.startsWith(lp.roundPrefix)) {
                String rest = clean.substring(lp.roundPrefix.length());
                if ("ja".equals(lp.lang) && rest.trim().isEmpty()) {
                    disableLobbyLanguageFallback();
                    jaRoundLabelPending = true;
                    return;
                }
                Matcher m = ROUND_NUM_PATTERN.matcher(rest.trim());
                if (m.matches()) {
                    disableLobbyLanguageFallback();
                    handleRoundStart(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), now);
                    return;
                }
            }

            // ── Builder ──
            if (lp.builderPrefix != null && clean.startsWith(lp.builderPrefix)) {
                disableLobbyLanguageFallback();
                String builder = cleanIgn(clean.substring(lp.builderPrefix.length()));
                if (!builder.isEmpty()) {
                    PlayerMetaCache.INSTANCE.harvestBuilderMessageRank(builder, raw, lp.builderPrefix);
                    // Once a round has an exact theme, a different builder belongs
                    // to the next round even if its Round: X/Y line has not arrived.
                    if (shouldBufferNextBuilder(builder)) {
                        pendingNextBuilder = builder;
                    } else if (currentRound != null
                            && (state == TrackingState.ROUND_BUILD
                                || state == TrackingState.ROUND_PRE)) {
                        if (!startReplacementRoundAfterPreStartSkip(builder, now)) {
                            reassignBuilder(currentRound, builder);
                        }
                    } else {
                        pendingNextBuilder = builder;
                    }
                }
                return;
            }

            // ── Correct Guess ──
            if (lp.correctGuessSuffix != null) {
                String ign = extractCorrectGuessIGN(clean, lp);
                if (ign != null && currentRound != null) {
                    disableLobbyLanguageFallback();
                    maybeAutoDetectLanguage(lp);
                    PlayerMetaCache.INSTANCE.harvestCorrectGuessMessageRank(ign, raw, lp.correctGuessPrefix);
                    ensureObservedPlayer(ign);
                    currentRound.correctGuesses.add(new CorrectGuessEntry(now, ign));
                    int orderZeroBased = currentRound.correctGuesses.size() - 1;
                    ScoreReconciler.recordGuessDelta(currentRound, ign, orderZeroBased);
                    // A correct guess is unambiguous activity — clear leaver
                    // and refresh activity timestamp (markActivity also drops
                    // the player from the leavers set).
                    markActivity(ign);
                    if (isLocalPlayerIgn(ign)) handleLocalCorrectGuess(ign, now, true);
                    if (gtbHooks != null) gtbHooks.onCorrectGuess(Collections.singletonList(ign));
                    return;
                }
            }

            // ── Theme Reveal ──
            if (lp.themeWasPrefix != null && clean.startsWith(lp.themeWasPrefix)) {
                String rest = clean.substring(lp.themeWasPrefix.length());
                if (lp.themeWasSuffix != null && rest.endsWith(lp.themeWasSuffix)) {
                    String theme = rest.substring(0, rest.length() - lp.themeWasSuffix.length());
                    disableLobbyLanguageFallback();
                    maybeAutoDetectLanguage(lp);
                    handleThemeReveal(theme, now);
                    return;
                }
            }

            // ── Character Length ──
            if (lp.charsLongPrefix != null && clean.startsWith(lp.charsLongPrefix)) {
                String rest = clean.substring(lp.charsLongPrefix.length());
                if (lp.charsLongSuffix == null) {
                    try {
                        int n = Integer.parseInt(rest.trim());
                        disableLobbyLanguageFallback();
                        maybeAutoDetectLanguage(lp);
                        if (currentRound != null) currentRound.themeCharCount = n;
                        if (gtbHooks != null) gtbHooks.onOneSecondAlert();
                        return;
                    } catch (NumberFormatException e) { /* continue */ }
                } else if (rest.endsWith(lp.charsLongSuffix)) {
                    String numStr = rest.substring(0, rest.length() - lp.charsLongSuffix.length()).trim();
                    try {
                        int n = Integer.parseInt(numStr);
                        disableLobbyLanguageFallback();
                        maybeAutoDetectLanguage(lp);
                        if (currentRound != null) currentRound.themeCharCount = n;
                        if (gtbHooks != null) gtbHooks.onOneSecondAlert();
                        return;
                    } catch (NumberFormatException e) { /* continue */ }
                }
            }

            // ── Winners ──
            if (lp.winnersKeyword != null && clean.trim().equals(lp.winnersKeyword)) {
                disableLobbyLanguageFallback();
                handleWinners();
                return;
            }

            // ── Language Change ──
            if (lp.langSelectPrefix != null && clean.startsWith(lp.langSelectPrefix)) {
                String name = null;
                if (lp.langSelectSuffix != null) {
                    String rest = clean.substring(lp.langSelectPrefix.length());
                    if (rest.endsWith(lp.langSelectSuffix)) {
                        name = rest.substring(0, rest.length() - lp.langSelectSuffix.length()).trim();
                    }
                } else {
                    name = clean.substring(lp.langSelectPrefix.length()).trim();
                }
                if (name != null && !name.isEmpty()) {
                    disableLobbyLanguageFallback();
                    currentLanguage = name;
                    // Many languages share the langSelectPrefix (e.g., en/pirate both use
                    // "Selected language: "); the displayed name disambiguates.
                    String code = LangPatterns.codeFromDisplayName(name);
                    currentLangCode = code != null ? code : lp.lang;
                    languageExplicitlySet = true;
                    languageObservedThisGame = true;
                    // A preliminary ROUND_PRE record represents the upcoming
                    // round, so a selection made before its Round: X/Y header
                    // replaces the language captured with that placeholder.
                    // Active rounds keep their start language.
                    if (currentRound != null
                            && state == TrackingState.ROUND_PRE
                            && currentRound.roundNumber == 0
                            && currentRound.roundEnd == 0) {
                        currentRound.language = currentLanguage;
                        currentRound.langCode = currentLangCode;
                    }
                    // Persist across sessions so a fresh launch starts in the same language.
                    if (currentLangCode != null
                            && !currentLangCode.equals(GtbStructures.lastLangCode)) {
                        GtbStructures.lastLangCode = currentLangCode;
                        GtbStructures.saveConfig();
                    }
                }
                return;
            }

            // ── AFK ──
            if (lp.afkSubstring != null && clean.contains(lp.afkSubstring)) {
                disableLobbyLanguageFallback();
                if (currentRound != null) {
                    currentRound.events.add(new GameEvent(now, "afk_skip", null));
                    zeroOutBuilderDelta(currentRound, "afk_skip");
                    if (gtbHooks != null) gtbHooks.onRoundSkipped();
                }
                return;
            }

            // ── Builder Left ──
            LangPatterns.BuilderLeftMessageKind builderLeftKind =
                    LangPatterns.builderLeftMessageKind(clean);
            if (builderLeftKind != LangPatterns.BuilderLeftMessageKind.NONE) {
                disableLobbyLanguageFallback();
                handleBuilderLeft(now, builderLeftKind);
                return;
            }

            // ── Rejoin (guesser) ──
            if (isAwaitingRejoinDetail() && lp.rejoinGuesserPrefix != null
                    && clean.startsWith(lp.rejoinGuesserPrefix)) {
                if (lp.rejoinGuesserSuffix != null && clean.endsWith(lp.rejoinGuesserSuffix)) {
                    String builder = clean.substring(lp.rejoinGuesserPrefix.length(),
                            clean.length() - lp.rejoinGuesserSuffix.length());
                    disableLobbyLanguageFallback();
                    handleRejoin("guesser", builder, null, now);
                    return;
                }
            }

            // ── Rejoin (builder) ──
            if (isAwaitingRejoinDetail() && lp.rejoinBuilderPrefix != null
                    && clean.startsWith(lp.rejoinBuilderPrefix)) {
                String rest = clean.substring(lp.rejoinBuilderPrefix.length());
                String theme;
                if (lp.rejoinBuilderSuffix != null && rest.endsWith(lp.rejoinBuilderSuffix)) {
                    theme = rest.substring(0, rest.length() - lp.rejoinBuilderSuffix.length());
                } else {
                    theme = rest;
                }
                disableLobbyLanguageFallback();
                handleRejoin("builder", null, theme, now);
                return;
            }
        }
    }

    private void maybeAutoDisableTrollModeFromRoundStart(String clean) {
        if (trollAutoDisableJapaneseRoundPending) {
            trollAutoDisableJapaneseRoundPending = false;
            Matcher standalone = ROUND_NUM_PATTERN.matcher(clean.trim());
            if (standalone.matches() && "1".equals(standalone.group(1))) {
                GtbStructures.autoDisableTrollModeAtGameStart();
                return;
            }
        }
        if (isFirstRoundStartForTrollAutoDisableForTest(clean)) {
            GtbStructures.autoDisableTrollModeAtGameStart();
            return;
        }
        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (lp.roundPrefix == null || !clean.startsWith(lp.roundPrefix)) continue;
            String rest = clean.substring(lp.roundPrefix.length());
            if ("ja".equals(lp.lang) && rest.trim().isEmpty()) {
                trollAutoDisableJapaneseRoundPending = true;
            }
            return;
        }
    }

    static boolean isFirstRoundStartForTrollAutoDisableForTest(String clean) {
        if (clean == null) return false;
        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (lp.roundPrefix == null || !clean.startsWith(lp.roundPrefix)) continue;
            String rest = clean.substring(lp.roundPrefix.length()).trim();
            Matcher round = ROUND_NUM_PATTERN.matcher(rest);
            return round.matches() && "1".equals(round.group(1));
        }
        return false;
    }

    /**
     * Called from type-2 actionbar events. Receives the pre-parsed result from
     * {@link HintOverlay#parse} so structure + trimmed hint text are computed
     * exactly once per message, with consistent prefix/suffix handling
     * (lastIndexOf for suffix to avoid truncating themes that contain the
     * suffix string).
     */
    public void onHint(HintOverlay.ParsedHint parsed, long timestamp) {
        if (!trackingEnabled || !isInRound(state) || currentRound == null) return;
        if (parsed == null || !parsed.isHint || parsed.structure.isEmpty()) return;

        String trimmed = parsed.trimmedHint.trim();
        if (currentRound.hintStructure == null) {
            currentRound.hintStructure = parsed.structure;
        }
        if (!trimmed.equals(lastHintText)) {
            lastHintText = trimmed;
            currentRound.hints.add(new HintEntry(timestamp, trimmed));
        }
        ThemePointReveal.maybeSendFromHint(currentRound,
                GtbStructures.themePointReveal,
                GtbStructures.themePointRevealShowAfterGuess);
        String hintLangCode = currentRound.langCode != null
                ? currentRound.langCode : currentLangCode;
        if (currentRound.completeThemeRevealedToUser
                && !TranslationData.findShortcutThemes(hintLangCode, trimmed).isEmpty()) {
            boolean themeChanged = !trimmed.equals(currentRound.theme);
            currentRound.theme = trimmed;
            ShortcutReminder.maybeSend(currentRound, trimmed);
            if (themeChanged) writeLiveFile();
        }
        if (gtbHooks != null) {
            gtbHooks.onHintUpdate(trimmed, parsed.structure, hintLangCode);
        }
        ShowAllMatching.onHint(currentRound, trimmed);
    }

    /**
     * Called when GUI theme selection is detected. themes = slot 11, 13, 15 item names.
     */
    public boolean onGuiThemePick(String[] themes) {
        Minecraft mc = Minecraft.getMinecraft();
        String localName = mc != null && mc.thePlayer != null ? mc.thePlayer.getName() : null;
        return onGuiThemePick(themes, localName);
    }

    public boolean onGuiThemePick(String[] themes, String localPlayerName) {
        if (!trackingEnabled || !isInRound(state) || currentRound == null) return false;

        // The local profile can retain the account IGN while the server has
        // confirmed a nick. A chooser identifies the local account, not a new IGN.
        String builder = canonicalRosterIgn(localPlayerName);
        boolean builderChanged = PlayerMetaCache.isValidMcUsername(builder)
                && (currentRound.builder == null
                || !currentRound.builder.equalsIgnoreCase(builder));
        if (PlayerMetaCache.isValidMcUsername(builder)) {
            setLocalPlayerIdentity(builder);
            reassignBuilder(currentRound, builder);
        }

        String[] captured = new String[3];
        boolean anyTheme = false;
        for (int i = 0; themes != null && i < captured.length && i < themes.length; i++) {
            String theme = themes[i] == null ? null : themes[i].trim();
            if (theme != null && !theme.isEmpty()) {
                captured[i] = theme;
                anyTheme = true;
            }
        }
        if (!anyTheme) {
            if (builderChanged) writeLiveFile();
            return false;
        }

        currentRound.builderThemeOptions = captured;
        maybeSendThemePoint(currentRound.theme, currentRound.langCode);
        writeLiveFile();
        return true;
    }

    /**
     * Called every ~1 second from the tick handler.
     *
     * {@code cleanLines} is the fully-normalized scoreboard line list produced
     * by {@link GtbStructures#readScoreboard} (control codes stripped, emojis
     * dropped, NFC-normalized, trimmed). Every check below consumes it directly
     * — no per-line re-stripping inside the tick.
     */
    public void onScoreboardTick(String titleClean, List<String> cleanLines) {
        onScoreboardTick(titleClean, cleanLines, null);
    }

    /** Variant that also accepts the raw (§-coded) sidebar lines; used to
     *  harvest per-player rank colors before the formatting is lost. */
    public void onScoreboardTick(String titleClean, List<String> cleanLines,
                                  List<String> rawFormattedLines) {
        if (!trackingEnabled) return;

        scoreboardShowsGtb = LangPatterns.isGtbTitle(titleClean);
        long now = System.currentTimeMillis();
        if (state == TrackingState.NOT_IN_GAME
                && gtbHandoffRosterUntilMs > 0L
                && now > gtbHandoffRosterUntilMs) {
            clearLobbyHandoff();
            PlayerMetaCache.INSTANCE.clearRosterEvidence();
        }
        // Poll the player-info-map for structured display names every tick.
        // Hypixel's BB pushes a transient tab-list update around game-start
        // carrying [title, name, emblem] siblings; the once-per-event refresh
        // path can miss it. Sticky merge in PlayerMetaCache handles ordering.
        PlayerMetaCache.INSTANCE.pollStructuredDisplayNames();

        if (state != TrackingState.LOBBY && isActiveGame(state)) {
            mergePacketRoster();
        }

        // Primary tab-list snapshot trigger: when BB rank prefixes (Rookie/Expert/...)
        // first appear in the tab list, the game has definitely started and the full
        // player list is available. Fall back to the deferred timer above on laggy
        // clients where even this check fires before the server pushes the tab update.
        // Packet-driven tab/team capture replaces the old BB-rank polling trigger.

        // Polling fallback: if we still have no players while in an active game,
        // retry snapshotTabList() every tick.  It safely no-ops when the tab list
        // is empty, and on laggy clients this covers the case where the BB-rank
        // detection above never fires (display names may be null).
        // Also re-snapshot when the tab list has grown beyond what we captured.
        // Active-game roster growth is now handled by PlayerListPacketListener.

        if (cleanLines == null) cleanLines = Collections.emptyList();

        // Derive target state from scoreboard content before delayed actions. Auto-requeue
        // must distinguish the results screen from a lobby or a manually joined game.
        TrackingState sbState = LangPatterns.getStateFromScoreboard(cleanLines, scoreboardShowsGtb);
        sbState = retainActiveStateForUnclassifiedGtb(state, sbState, scoreboardShowsGtb);

        // Local-player identity transfer: nick/denick across L/RJ changes
        // mc.thePlayer.getName() unambiguously. While PAUSED, retain the old
        // identity until this scoreboard proves that the player returned to a
        // round in the same game; otherwise an intermediate lobby/world can
        // consume the only old -> new observation before recovery runs.
        try {
            Minecraft mc = Minecraft.getMinecraft();
            String localNow = mc.thePlayer != null ? mc.thePlayer.getName() : null;
            observeLocalIdentity(localNow, sbState);
        } catch (Exception ignored) { /* best-effort */ }

        if (state == TrackingState.NOT_IN_GAME) {
            if (sbState == TrackingState.ROUND_PRE
                    || sbState == TrackingState.ROUND_BUILD
                    || sbState == TrackingState.ROUND_END
                    || sbState == TrackingState.POST_GAME) {
                loadPendingCurrent(runtimeStatsDir(), now, true);
            }
        }
        gtbLobbyRosterConfirmed = sbState == TrackingState.LOBBY;

        // Deferred endGame after Winners chat — rankings were collected each tick in
        // the POST_GAME state loop, so by now we have them.
        if (scheduledEndGameTime > 0 && now >= scheduledEndGameTime) {
            scheduledEndGameTime = 0;
            endGame(true, null);
            return;
        }

        // Auto-requeue: send /play after delay when a game ended normally.
        // Eligibility was captured before endGame cleared rankings. At fire time,
        // require the client to still be on the GTB results screen so manual
        // navigation to a lobby, another mode, or another game cancels the action.
        if (consumeAutoRequeueIfReady(now, sbState)) {
            // A very short configured delay may beat the normal Winners grace period.
            // Finalize and export the captured rankings before changing servers.
            if (!gameEnded) {
                endGame(true, null);
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                mc.thePlayer.sendChatMessage("/play build_battle_guess_the_build");
            }
            return;
        }

        // ── State transitions ──

        // Leave detection: any active state -> PAUSED when scoreboard disappears.
        // Exception: LOBBY → NOT_IN_GAME is just "user left the GTB lobby" (or a
        // brief scoreboard glitch). Pausing from LOBBY would cause the recovery
        // path to wrongly create a dataGap round when the next game starts.
        if (isActiveGame(state) && sbState == TrackingState.NOT_IN_GAME) {
            notInGameTicks++;
            int requiredPauseTicks = state == TrackingState.ROUND_END ? PAUSE_GRACE_TICKS : 1;
            if (notInGameTicks >= requiredPauseTicks) {
                if (state == TrackingState.POST_GAME) {
                    // Game already ended on scoreboard; finalize with collected rankings.
                    endGame(true, null);
                } else if (state == TrackingState.LOBBY) {
                    // No game in progress — drop straight to NOT_IN_GAME, no pause.
                    beginLobbyHandoff(System.currentTimeMillis());
                    state = TrackingState.NOT_IN_GAME;
                    lobbyTabDone = false;
                    players.clear();
                    rankings.clear();
                    // Preserve lobby rank colors across Hypixel's normal
                    // lobby-to-game NOT_IN_GAME handoff. Fresh lobby entry and
                    // game end still clear the cache.
                    lobbyQuitSuppress.clear();
                } else {
                    handlePause();
                }
            }
            return;
        }
        if (sbState != TrackingState.NOT_IN_GAME) {
            notInGameTicks = 0;
        }

        // PAUSED state — check for scoreboard recovery or timeout
        if (state == TrackingState.PAUSED) {
            if (sbState == TrackingState.NOT_IN_GAME) {
                return;
            }
            if (sbState == TrackingState.LOBBY) {
                endGame(false, "new_gtb_lobby");
                state = TrackingState.LOBBY;
                lobbyTabDone = false;
                players.clear();
                rounds.clear();
                currentRound = null;
                rankings.clear();
                PlayerMetaCache.INSTANCE.clearHarvestedRanks();
                lobbyQuitSuppress.clear();
                return;
            }
            if (sbState == TrackingState.POST_GAME) {
                handlePostGame(cleanLines, now);
                return;
            }
            if (!gameInitialized) {
                initNewGame(now);
            }
            // Ensure a round exists so guesses count even before "Round: X/Y" chat arrives
            if (isInRound(sbState) && currentRound == null) {
                beginPreRound(now);
            }
            applyScoreboardLanguageDetection(cleanLines);
            startActiveTime(now);
            state = sbState;
            pauseTime = 0;
            // Only treat this as a real rejoin if a round was actually in progress
            // when we paused. Otherwise the "pause" was just LOBBY scoreboard noise
            // and the new round is a fresh start, not a recovery.
            boolean hadRealGameAtPause = pausedBuilder != null || pausedRoundTotal > 0;
            pendingRejoinCheck = hadRealGameAtPause;
            // Severe-uncertainty: scoreboard-recovery rejoin without an
            // intervening ROUND_PRE chat. Cleared on next handleRoundStart /
            // beginPreRound. Skip if we are now the builder of this round, and
            // skip entirely if there was no real game in progress at pause.
            String localIgn = Minecraft.getMinecraft().thePlayer != null
                    ? Minecraft.getMinecraft().thePlayer.getName() : null;
            boolean iAmBuilder = currentRound != null && currentRound.builder != null
                    && currentRound.builder.equalsIgnoreCase(localIgn);
            localRejoinedImmediatelyStage = hadRealGameAtPause && !iAmBuilder;
            if (hadRealGameAtPause) {
                invalidatePreLeaveSidebarSnapshot();
            }
            snapshotTabList();
            if (currentRound != null && hadRealGameAtPause) {
                currentRound.events.add(new GameEvent(now, "rejoin", "scoreboard_recovery"));
            }
            if (gtbHooks != null && hadRealGameAtPause) gtbHooks.onUserRejoin();
            return;
        }

        // NOT_IN_GAME -> LOBBY
        if (state == TrackingState.NOT_IN_GAME && sbState == TrackingState.LOBBY) {
            state = TrackingState.LOBBY;
            clearLobbyHandoff();
            lobbyTabDone = false;
            players.clear();
            rounds.clear();
            currentRound = null;
            rankings.clear();
            // Fresh lobby — discard any rank colors harvested from the previous
            // game's lobby so they don't leak into this game's rendering.
            PlayerMetaCache.INSTANCE.clearHarvestedRanks();
            lobbyQuitSuppress.clear();
            return;
        }

        // NOT_IN_GAME -> ROUND_PRE/ROUND_BUILD: joined the lobby so close to the game
        // start that we never observed the LOBBY scoreboard. Initialize the game
        // directly so the rest of it is recorded.
        if (state == TrackingState.NOT_IN_GAME
                && (sbState == TrackingState.ROUND_PRE || sbState == TrackingState.ROUND_BUILD)) {
            initNewGame(now);
            if (gtbHooks != null) gtbHooks.onMidGameStart();
            applyScoreboardLanguageDetection(cleanLines);
            if (currentRound == null) {
                beginPreRound(now);
                currentRound.dataGap = true; // missed the lobby + any chat before now
            }
            state = sbState;
            return;
        }

        // LOBBY: retry until the first non-empty roster snapshot, then stop.
        // From that baseline onward, chat join/quit messages are the source of
        // truth for live lobby deltas and join-message rank backup.
        if (state == TrackingState.LOBBY && gtbLobbyRosterConfirmed && !lobbyTabDone) {
            lobbyTabDone = snapshotTabList();
        }

        // LOBBY: harvest per-player rank colors from the formatted sidebar.
        // Harvested values stick through the whole game. Active-game roster
        // rows are harvested separately below after their score syntax and IGN
        // have passed the normal leaderboard admission checks.
        if (state == TrackingState.LOBBY && sbState == TrackingState.LOBBY
                && gtbLobbyRosterConfirmed
                && rawFormattedLines != null
                && !rawFormattedLines.isEmpty()) {
            PlayerMetaCache.INSTANCE.harvestSidebarRanks(rawFormattedLines);
        }

        // LOBBY -> ROUND_PRE: game start
        if (state == TrackingState.LOBBY && (sbState == TrackingState.ROUND_PRE
                || sbState == TrackingState.ROUND_BUILD)) {
            GtbStructures.autoDisableTrollModeAtGameStart();
            initNewGame(now);
            applyScoreboardLanguageDetection(cleanLines);
            // Always create a preliminary round so guesses count before "Round: X/Y" arrives
            if (isInRound(sbState) && currentRound == null) {
                beginPreRound(now);
            }
            state = sbState;
        }

        // Active game -> GTB lobby: user joined a different GTB lobby midgame.
        // The old game is no longer recoverable, and its roster must not seed
        // the next custom scoreboard.
        if (state != TrackingState.LOBBY
                && state != TrackingState.POST_GAME
                && isActiveGame(state)
                && sbState == TrackingState.LOBBY) {
            endGame(false, "new_gtb_lobby");
            state = TrackingState.LOBBY;
            lobbyTabDone = false;
            players.clear();
            rounds.clear();
            currentRound = null;
            rankings.clear();
            PlayerMetaCache.INSTANCE.clearHarvestedRanks();
            lobbyQuitSuppress.clear();
            return;
        }

        // POST_GAME -> LOBBY must finalize before the generic active-state
        // transition below rewrites state to LOBBY.
        if (state == TrackingState.POST_GAME && sbState == TrackingState.LOBBY) {
            endGame(true, null);
            state = TrackingState.LOBBY;
            lobbyTabDone = false;
            players.clear();
            rounds.clear();
            currentRound = null;
            rankings.clear();
            PlayerMetaCache.INSTANCE.clearHarvestedRanks();
            lobbyQuitSuppress.clear();
            return;
        }

        // Within active game: follow scoreboard-driven state changes
        if (isActiveGame(state) && isActiveGame(sbState) && state != sbState) {
            boolean enteredBuildPhase = state == TrackingState.ROUND_PRE
                    && sbState == TrackingState.ROUND_BUILD;
            if (sbState == TrackingState.POST_GAME) {
                handlePostGame(cleanLines, now);
                return;
            }
            // Ignore backwards ROUND_BUILD → ROUND_PRE after a normal round header.
            // During rejoin recovery, however, a fresh "Starts In:" board is
            // authoritative proof that the newly reported builder has not started.
            if (state == TrackingState.ROUND_BUILD && sbState == TrackingState.ROUND_PRE) {
                if (!localRejoinedImmediatelyStage) return;
                if (gtbHooks != null) gtbHooks.onUserRejoinPreRound();
            }
            if (sbState == TrackingState.ROUND_PRE && state != TrackingState.ROUND_PRE) {
                beginPreRound(now);
            }
            // Safety: if entering any in-round state with no current round, create one
            if (isInRound(sbState) && currentRound == null) {
                beginPreRound(now);
            }
            state = sbState;
            if (enteredBuildPhase && gtbHooks != null) gtbHooks.onRoundBuild();
        }

        // Still in POST_GAME: keep re-parsing rankings in case they populate over several ticks
        if (state == TrackingState.POST_GAME && sbState == TrackingState.POST_GAME) {
            parseRankings(cleanLines, now);
            fireTrueScoresUpdate();
            return;
        }

        // POST_GAME -> LOBBY: finalize game now that we're leaving the results screen
        if (state == TrackingState.POST_GAME && sbState == TrackingState.LOBBY) {
            endGame(true, null);
            state = TrackingState.LOBBY;
            lobbyTabDone = false;
            players.clear();
            rounds.clear();
            currentRound = null;
            rankings.clear();
            PlayerMetaCache.INSTANCE.clearHarvestedRanks();
            lobbyQuitSuppress.clear();
        }

        // ── Score + builder extraction while in round ──
        if (isInRound(state) && currentRound != null) {
            if (!resolvePendingRejoinBeforeScores(cleanLines, now)) {
                pendingUnknownSidebarPlayers.clear();
                return;
            }
            syncSidebarTrackingRound();

            // Fresh per-tick snapshot for identity-transfer diff.
            Map<String, Integer> currentSidebar = new LinkedHashMap<>();
            Set<String> unknownSidebarPlayersThisTick =
                    new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (int sidebarLineIndex = 0;
                 sidebarLineIndex < cleanLines.size(); sidebarLineIndex++) {
                String cleanLine = cleanLines.get(sidebarLineIndex);
                if (cleanLine.isEmpty() || cleanLine.contains("hypixel.net")
                        || cleanLine.startsWith("www.") || cleanLine.equals("???")
                        || DATE_FOOTER_PATTERN.matcher(cleanLine).matches()
                        || containsSidebarClockToken(cleanLine)) {
                    continue;
                }
                if (LangPatterns.isScoreboardLabel(cleanLine)) continue;
                int colonIdx = cleanLine.lastIndexOf(':');
                if (colonIdx > 0 && colonIdx < cleanLine.length() - 1) {
                    String ign = cleanIgn(cleanLine.substring(0, colonIdx));
                    String scoreStr = cleanLine.substring(colonIdx + 1).trim();
                    if (!PlayerMetaCache.isValidMcUsername(ign)
                            || isReservedNonPlayerName(ign)) continue;
                    try {
                        int observed = Integer.parseInt(scoreStr);
                        if (!ign.isEmpty()) {
                            if (!shouldAdmitSidebarPlayer(
                                    ign, players, pendingUnknownSidebarPlayers)) {
                                unknownSidebarPlayersThisTick.add(ign);
                                continue;
                            }
                            ensureObservedPlayer(ign);
                            if (rawFormattedLines != null
                                    && sidebarLineIndex < rawFormattedLines.size()) {
                                PlayerMetaCache.INSTANCE.harvestScoreboardRosterRankColor(
                                        ign, rawFormattedLines.get(sidebarLineIndex));
                            }
                            currentSidebar.put(ign, observed);
                            clearLeaverOnLeaderboardReappearance(ign);
                            Integer prior = ScoreReconciler.knownPriorCumulative(rounds, ign, currentRound);
                            // Score growth (above this round's prior cumulative) signals activity.
                            // 3-minute threshold absorbs a bit of over-marking.
                            if (prior != null && observed > prior.intValue()) {
                                markActivity(ign);
                                leavers.remove(ign); // back from leaver if score grew
                            }
                            ScoreReconciler.observeScoreboard(currentRound, ign, observed, prior,
                                    midGameUnattributedResidual);
                        }
                    } catch (NumberFormatException e) { /* not a score line */ }
                }
            }
            pendingUnknownSidebarPlayers.clear();
            pendingUnknownSidebarPlayers.addAll(unknownSidebarPlayersThisTick);
            // Extract builder from scoreboard
            if (currentRound.builder == null) {
                String sbBuilder = extractBuilderFromScoreboard(cleanLines);
                if (sbBuilder != null) setBuilder(sbBuilder);
            }
            if (state == TrackingState.ROUND_PRE || state == TrackingState.ROUND_BUILD) {
                syncBuilderHookForCurrentRound();
            }
            maybeSendThemePointFromScoreboard();
            // Crosscheck builder fork via the live scoreboard Theme: line, paired with
            // the LIVE current language (NOT the round's locked language — the scoreboard
            // theme syncs with whatever language is selected at this moment).
            String sbTheme = extractThemeFromScoreboard(cleanLines);
            String canonicalSbTheme = sbTheme == null ? null
                    : TranslationData.resolveCanonicalDisplayedTheme(currentLangCode, sbTheme);
            String displayedSbTheme = canonicalSbTheme == null ? sbTheme : canonicalSbTheme;
            if (sbTheme != null && currentRound.builder != null) {
                ScoreReconciler.crosscheckBuilderFromScoreboard(
                        currentRound, currentLangCode, displayedSbTheme);
            }
            if (sbTheme != null && state == TrackingState.ROUND_BUILD) {
                boolean themeChanged = !displayedSbTheme.equals(currentRound.theme);
                currentRound.theme = displayedSbTheme;
                if (!isLocalPlayerBuilder(currentRound)) {
                    ThemePointReveal.markCompleteThemeRevealed(currentRound);
                }
                if (canonicalSbTheme != null) {
                    ShortcutReminder.maybeSend(
                            currentRound, canonicalSbTheme, currentLangCode);
                }
                if (gtbHooks != null) {
                    gtbHooks.onThemeUpdate(displayedSbTheme, currentLangCode);
                }
                maybeSendThemePoint(displayedSbTheme, currentLangCode);
                if (themeChanged) writeLiveFile();
            } else if (sbTheme != null && state == TrackingState.ROUND_END
                    && gtbHooks != null) {
                // The revealed vanilla Theme row follows the live language even
                // between rounds. Keep the replacement Default panel in sync
                // without replaying round-scoring or notification work.
                gtbHooks.onThemeUpdate(displayedSbTheme, currentLangCode);
            }
            // Identity-transfer detection: local-only nick/denick recovery.
            // Non-local same-score row swaps are treated as redraw noise.
            detectIdentityTransfer(currentSidebar);
            detectLeaderboardDrops(currentSidebar);
            rememberSidebarDropScores(sidebarDropKnownScores, currentSidebar);
            // Push the live leaderboard into GtbHooks. The custom scoreboard uses
            // these scores to collapse forks and render per-row leaderboard
            // values during play — without it forks accumulate forever.
            if (gtbHooks != null && !currentSidebar.isEmpty()
                    && !currentSidebar.equals(lastSidebarScores)) {
                gtbHooks.onTrueScoresUpdate(new LinkedHashMap<String, Integer>(currentSidebar));
            }
            lastSidebarScores = currentSidebar;
        }

    }

    // ── Lifecycle Methods ────────────────────────────────────────────────

    private boolean resolvePendingRejoinBeforeScores(List<String> cleanLines, long now) {
        if (!pendingRejoinCheck) return true;

        boolean hadRealGameAtPause = pausedBuilder != null || pausedRoundTotal > 0;
        if (!hadRealGameAtPause) {
            clearPendingRejoinState();
            return true;
        }

        String sbBuilder = extractBuilderFromScoreboard(cleanLines);
        boolean sameBuilder = pausedBuilder != null && sbBuilder != null
                && sbBuilder.equals(pausedBuilder);
        if (sameBuilder) {
            String builder = sbBuilder;
            materializePausedPreRoundCandidate(now);
            if (gtbHooks != null) gtbHooks.onUserRejoinSameRound(builder);
            if (currentRound != null) {
                currentRound.events.add(new GameEvent(now, "rejoin_same_round",
                        "builder=" + builder));
            }
            clearPendingRejoinState();
            return true;
        }

        boolean builderChanged = pausedBuilder != null && sbBuilder != null
                && !sbBuilder.equals(pausedBuilder);
        boolean betweenRounds = state == TrackingState.ROUND_PRE
                || state == TrackingState.ROUND_END;
        if (builderChanged || betweenRounds) {
            int inferredRound = builderChanged ? inferredRoundForChangedBuilder() : 0;
            startRejoinGapRound(now, sbBuilder,
                    builderChanged ? "builder_changed" : "between_rounds", inferredRound);
            clearPendingRejoinState();
            return true;
        }

        return false;
    }

    private boolean isAwaitingRejoinDetail() {
        return state == TrackingState.PAUSED
                || (state == TrackingState.ROUND_BUILD
                    && (pendingRejoinCheck || localRejoinedImmediatelyStage));
    }

    private boolean resolveRejoinMessageBuilder(String newBuilder, long now) {
        if (newBuilder == null || newBuilder.isEmpty()) return false;

        String oldBuilder = pausedBuilder;
        if ((oldBuilder == null || oldBuilder.isEmpty())
                && currentRound != null && currentRound.dataGap) {
            oldBuilder = currentRound.builder;
        }
        boolean hadRealGameAtPause = oldBuilder != null || pausedRoundTotal > 0
                || (currentRound != null && currentRound.dataGap);
        if (!hadRealGameAtPause) {
            return false;
        }

        if ((oldBuilder == null || oldBuilder.isEmpty())
                && pausedState == TrackingState.ROUND_PRE
                && pausedNextRoundCandidate > 0) {
            materializePausedPreRoundCandidate(now);
            if (currentRound != null) {
                reassignBuilder(currentRound, newBuilder);
                currentRound.events.add(new GameEvent(now, "rejoin_same_round",
                        "builder=" + newBuilder + ",round=inferred"));
            }
            clearPendingRejoinState();
            return true;
        }

        if (oldBuilder == null || oldBuilder.isEmpty()) return false;

        if (oldBuilder.equalsIgnoreCase(newBuilder)) {
            materializePausedPreRoundCandidate(now);
            if (gtbHooks != null) gtbHooks.onUserRejoinSameRound(newBuilder);
            if (currentRound != null) {
                currentRound.events.add(new GameEvent(now, "rejoin_same_round",
                        "builder=" + newBuilder));
            }
            clearPendingRejoinState();
            return true;
        }

        if (pausedBuilder == null || pausedBuilder.isEmpty()) {
            pausedBuilder = oldBuilder;
        }
        if (pausedRoundTotal == 0 && currentRound != null) {
            pausedRoundTotal = currentRound.totalRounds;
        }
        startRejoinGapRound(now, newBuilder, "rejoin_message_builder_changed",
                inferredRoundForChangedBuilder());
        clearPendingRejoinState();
        return true;
    }

    private void startRejoinGapRound(long now, String sbBuilder, String reason,
                                     int inferredRound) {
        GuessSoundController.INSTANCE.onRoundBoundary();
        String oldBuilder = pausedBuilder;
        RoundData previousRound = currentRound;
        List<GameEvent> movedBoundaryEvents = inferredRound > 0
                ? detachBoundaryLeaveEvents(previousRound)
                : Collections.<GameEvent>emptyList();
        if (currentRound != null && currentRound.roundEnd == 0) {
            currentRound.roundEnd = now;
        }
        if (inferredRound > 0 && pausedState == TrackingState.ROUND_END
                && gtbHooks != null) {
            gtbHooks.onRoundEnd(false);
        }
        currentRound = new RoundData();
        currentRound.roundNumber = inferredRound > 0 ? inferredRound : -1;
        currentRound.totalRounds = pausedRoundTotal;
        currentRound.roundStart = now;
        currentRound.language = currentLanguage;
        currentRound.langCode = currentLangCode;
        currentRound.dataGap = true;
        lastHintText = "";
        clearPendingSelfGuessScore();
        rounds.add(currentRound);
        currentRound.events.addAll(movedBoundaryEvents);
        if (inferredRound > 0) {
            localRejoinedImmediatelyStage = false;
            if (gtbHooks != null) {
                gtbHooks.onInferredRoundStart(inferredRound, pausedRoundTotal, true);
            }
        }
        if (sbBuilder != null && !sbBuilder.isEmpty()) setBuilder(sbBuilder);
        currentRound.events.add(new GameEvent(now, "rejoin_round_change",
                "reason=" + reason + ",pausedBuilder=" + oldBuilder + ",newBuilder=" + sbBuilder
                        + (inferredRound > 0 ? ",inferredRound=" + inferredRound : "")));
    }

    private void materializePausedPreRoundCandidate(long now) {
        if (pausedState != TrackingState.ROUND_PRE || pausedNextRoundCandidate <= 0
                || currentRound == null) {
            return;
        }
        if (currentRound.roundNumber > 0
                && currentRound.roundNumber != pausedNextRoundCandidate) {
            return;
        }
        currentRound.roundNumber = pausedNextRoundCandidate;
        if (currentRound.totalRounds <= 0) currentRound.totalRounds = pausedRoundTotal;
        currentRound.dataGap = true;
        localRejoinedImmediatelyStage = false;
        currentRound.events.add(new GameEvent(now, "round_inferred",
                "reason=same_builder_pre_rejoin,round=" + pausedNextRoundCandidate));
        if (gtbHooks != null) {
            gtbHooks.onInferredRoundStart(pausedNextRoundCandidate,
                    currentRound.totalRounds, true);
        }
    }

    private int inferredRoundForChangedBuilder() {
        return pausedState == TrackingState.ROUND_END ? pausedNextRoundCandidate : 0;
    }

    private List<GameEvent> detachBoundaryLeaveEvents(RoundData round) {
        List<GameEvent> moved = new ArrayList<GameEvent>();
        if (round == null || round.events == null || round.events.isEmpty()) return moved;
        long boundary = round.roundEnd;
        for (int i = round.events.size() - 1; i >= 0; i--) {
            GameEvent event = round.events.get(i);
            if (event == null || event.t < boundary) continue;
            if ("disconnect".equals(event.type) || "rejoin".equals(event.type)) {
                moved.add(0, event);
                round.events.remove(i);
            }
        }
        round.dataGap = pausedRoundHadDataGapBeforeLeave;
        return moved;
    }

    private void clearPendingRejoinState() {
        pendingRejoinCheck = false;
        pausedBuilder = null;
        pausedRoundTotal = 0;
        pausedState = null;
        pausedNextRoundCandidate = 0;
        pausedRoundHadDataGapBeforeLeave = false;
    }

    private void invalidatePreLeaveSidebarSnapshot() {
        // A post-rejoin sidebar can be byte-for-byte identical while still being
        // fresh authoritative evidence for every visible player.
        resetSidebarTrackingBaseline(lastSidebarScores, sidebarDropMissingTicks,
                sidebarDropMissingScores);
    }

    private void initNewGame(long now) {
        // Preserve the language already detected during LOBBY (typically via the
        // countdown1Sec chat). Without this, the LOBBY → ROUND_PRE transition would
        // clobber a verified observation with the lastLangCode seed and re-mark the
        // language as unverified.
        boolean preserveObserved = languageObservedThisGame && currentLangCode != null;
        String observedLangCode = currentLangCode;
        String observedLanguage = currentLanguage;
        boolean observedExplicit = languageExplicitlySet;

        if (state == TrackingState.PAUSED) {
            endGame(false, "new_game");
        }
        playerUuid = launchPlayerUuid;
        gameStart = now;
        activeTimeMs = 0L;
        activeTimeStartedAt = now;
        RawPacketRecorder.INSTANCE.startSession(now);
        rounds.clear();
        rankings.clear();
        clearAutoRequeueSchedule();
        lastUnknownResidual.clear();
        // Preserve the lobby tab capture as the baseline. The lobby GTB queue
        // shows all participants; clearing here and re-snapshotting at game-start
        // would lose them when the tab has already shrunk to 2-3 nearby players.
        // BB rank detection / countdown snapshot supplement any gaps.
        currentRound = null;
        resetSidebarTrackingBaseline(lastSidebarScores, sidebarDropMissingTicks, sidebarDropMissingScores);
        sidebarDropKnownScores.clear();
        pendingUnknownSidebarPlayers.clear();
        sidebarTrackingRound = null;
        // Seed from persisted config so users who keep their client in (e.g.) pirate or
        // Russian don't get round 1 misclassified before any chat clue arrives.
        String seed = GtbStructures.lastLangCode;
        if (seed != null && !seed.isEmpty()) {
            currentLangCode = seed;
            String display = LangPatterns.mapLanguageName(seed);
            currentLanguage = display;
        } else {
            currentLanguage = null;
            currentLangCode = null;
        }
        jaRoundLabelPending = false;
        lastHintText = "";
        clearPendingSelfGuessScore();
        gameInitialized = true;
        gameEnded = false;
        currentPhysicalTimerSeconds = null;
        resetTrackedPhysicalTimerComparator();
        pendingRejoinCheck = false;
        pausedBuilder = null;
        pausedRoundTotal = 0;
        pausedState = null;
        pausedNextRoundCandidate = 0;
        pausedRoundHadDataGapBeforeLeave = false;
        scheduledEndGameTime = 0;
        pendingNextBuilder = null;
        languageExplicitlySet = false;
        languageObservedThisGame = false;

        if (preserveObserved) {
            currentLangCode = observedLangCode;
            currentLanguage = observedLanguage;
            languageObservedThisGame = true;
            languageExplicitlySet = observedExplicit;
        }

        String localIgn = bestKnownLocalIgn();
        setLocalPlayerIdentity(localIgn);

        seedGameStartRoster(now, localIgn);

        // Custom-scoreboard hook: emit a snapshot of the current player roster.
        if (gtbHooks != null) {
            gtbHooks.onGameStart(new ArrayList<String>(players), localIgn);
        }
    }

    private void handlePostGame(List<String> cleanLines, long now) {
        stopActiveTime(System.currentTimeMillis());
        state = TrackingState.POST_GAME;

        if (currentRound != null && currentRound.roundEnd == 0) {
            currentRound.roundEnd = System.currentTimeMillis();
        }

        parseRankings(cleanLines, now);
        fireTrueScoresUpdate();
        writeLiveFile();
    }

    /** Fire onTrueScoresUpdate from the current rankings list. Idempotent — safe to call
     *  after any successful parseRankings. */
    private void fireTrueScoresUpdate() {
        if (gtbHooks == null || rankings == null || rankings.isEmpty()) return;
        Map<String, Integer> obs = new LinkedHashMap<String, Integer>();
        for (RankEntry r : rankings) {
            if (r != null && r.ign != null) obs.put(r.ign, r.score);
        }
        gtbHooks.onTrueScoresUpdate(obs);
    }

    /**
     * Parse ranking lines from the post-game scoreboard. Accepts several formats:
     *   "1. IGN: 250"
     *   "1. IGN 250"
     *   "1. IGN - 250"
     *   "1. IGN" (no score)
     * The IGN may contain spaces (e.g. rank prefix). Replaces rankings only when
     * at least one line parsed, so transient empty scoreboards don't wipe good data.
     */
    private void parseRankings(List<String> cleanLines, long now) {
        List<RankEntry> next = new ArrayList<>();
        for (String line : cleanLines) {
            Matcher m = RANK_LINE_PATTERN.matcher(line);
            if (!m.matches()) continue;
            int place;
            try { place = Integer.parseInt(m.group(1)); }
            catch (NumberFormatException e) { continue; }
            String rest = m.group(2).trim();
            int score = 0;
            String ign = rest;
            Matcher sm = RANK_SCORE_PATTERN.matcher(rest);
            if (sm.matches()) {
                ign = sm.group(1).trim();
                try { score = Integer.parseInt(sm.group(2)); }
                catch (NumberFormatException e) { /* leave score = 0 */ }
            }
            ign = cleanIgn(ign);
            if (isReservedNonPlayerName(ign)) continue;
            if (!ign.isEmpty()) {
                next.add(new RankEntry(place, ign, score));
            }
        }
        if (!next.isEmpty()) {
            // getSortedScores returns entries in ASCENDING score order (lowest first),
            // but repairTruncatedScores expects DESCENDING order (highest first) for
            // its bottom-up monotonicity walk. Reverse before repairing.
            java.util.Collections.reverse(next);
            repairTruncatedScores(next);
            java.util.Collections.reverse(next);
            rankings.clear();
            rankings.addAll(next);
            scheduleAutoRequeue(now, true, true);
        }
    }

    /**
     * Hypixel's sidebar clips lines past ~16 visible chars, which lops trailing
     * digits off the score for players with long IGNs. The result is a
     * monotonicity violation: an entry placed above a lower one ends up with a
     * smaller numeric score (e.g. place 4 = 1 next to place 5 = 9, where the
     * real value was 11).
     *
     * Walks bottom-up through the ranking (already in place order) and, when an
     * entry's score falls below its already-trusted lower neighbour, treats the
     * visible value as a decimal prefix of the actual score and extends it with
     * trailing digits to find the smallest integer that
     *   (a) keeps the visible digits as leading digits, and
     *   (b) is ≥ the lower-neighbour's score.
     * Any unrecoverable gap still surfaces in {@code unknownResidual}.
     */
    static void repairTruncatedScores(List<RankEntry> rks) {
        if (rks == null || rks.size() < 2) return;
        // Pre-pass: within each tied-place group, every entry shares the same
        // score in Hypixel's leaderboard. Truncation only ever lowers a score,
        // so the group's max is the canonical value — extend low entries up to
        // it. (A group of one is a no-op.)
        int i = 0;
        while (i < rks.size()) {
            int j = i + 1;
            int groupMax = rks.get(i).score;
            while (j < rks.size() && rks.get(j).place == rks.get(i).place) {
                if (rks.get(j).score > groupMax) groupMax = rks.get(j).score;
                j++;
            }
            if (groupMax > 0) {
                for (int k = i; k < j; k++) {
                    RankEntry r = rks.get(k);
                    if (r.score > 0 && r.score < groupMax) {
                        int rep = extendPrefixToAtLeast(r.score, groupMax);
                        if (rep > r.score) r.score = rep;
                    }
                }
            }
            i = j;
        }
        // Main pass: walk bottom-up. Once an entry is "trusted" it sets the
        // monotonic lower bound for the entry above; if the entry above shows
        // a smaller value, it must be a truncation, so extend.
        int lowerBound = Integer.MIN_VALUE;
        for (int k = rks.size() - 1; k >= 0; k--) {
            RankEntry cur = rks.get(k);
            if (cur.score > 0 && cur.score < lowerBound) {
                int rep = extendPrefixToAtLeast(cur.score, lowerBound);
                if (rep > cur.score) cur.score = rep;
            }
            if (cur.score > lowerBound) lowerBound = cur.score;
        }
    }

    /**
     * Smallest integer ≥ {@code minVal} whose decimal representation begins
     * with {@code prefix}. Caps appended-digit count to keep pathological
     * inputs bounded; returns {@code prefix} unchanged on overflow or when
     * extension is unsafe (prefix ≤ 0).
     */
    static int extendPrefixToAtLeast(int prefix, int minVal) {
        if (prefix >= minVal) return prefix;
        if (prefix <= 0) return prefix;
        int p = prefix;
        int extDigits = 0;
        while (p < minVal && extDigits < 6) {
            p *= 10;
            extDigits++;
            if (minVal <= p) return p;
            int suffixMax = intPow10(extDigits) - 1;
            if (minVal <= p + suffixMax) return minVal;
        }
        return prefix;
    }

    private static int intPow10(int n) {
        int r = 1;
        for (int i = 0; i < n; i++) r *= 10;
        return r;
    }

    private void beginPreRound(long timestamp) {
        GuessSoundController.INSTANCE.onRoundBoundary();
        clearPendingRejoinState();
        clearPendingSelfGuessScore();
        if (currentRound != null) {
            if (currentRound.roundEnd == 0) {
                currentRound.roundEnd = timestamp;
            }
            boolean skipped = roundHasSkipEvent(currentRound);
            if (skipped && currentRound.roundNumber == 0) {
                currentRound.roundNumber = -1;
            }
            // Fire onRoundEnd here — the prior round actually ends at the
            // ROUND_PRE transition, not at the next round's "Round X/Y" chat.
            // Pre-start skip events can end a placeholder before it ever gets
            // a Round: X/Y line, so preserve that skipped state here.
            if (gtbHooks != null) gtbHooks.onRoundEnd(skipped);
        }
        currentRound = new RoundData();
        currentRound.roundStart = timestamp;
        currentRound.language = currentLanguage;
        currentRound.langCode = currentLangCode;
        String pnb = pendingNextBuilder;
        if (pnb != null) {
            pendingNextBuilder = null;
        }
        lastHintText = "";
        rounds.add(currentRound);
        if (gtbHooks != null) gtbHooks.onRoundPre();
        if (pnb != null) setBuilder(pnb);
        // Authoritative round boundary — round count is reconfirmed.
        localRejoinedImmediatelyStage = false;
        resetActivityForNewRound(timestamp);
    }

    private boolean startReplacementRoundAfterPreStartSkip(String builder, long timestamp) {
        if (currentRound == null || builder == null || builder.isEmpty()) return false;
        if (currentRound.roundNumber != 0) return false;
        if (currentRound.builder == null || currentRound.builder.equals(builder)) return false;
        if (!roundHasSkipEvent(currentRound)) return false;

        if (currentRound.roundEnd == 0) {
            currentRound.roundEnd = timestamp;
        }
        currentRound.roundNumber = -1;
        if (gtbHooks != null) gtbHooks.onRoundEnd(true);

        currentRound = new RoundData();
        currentRound.roundStart = timestamp;
        currentRound.language = currentLanguage;
        currentRound.langCode = currentLangCode;
        lastHintText = "";
        clearPendingSelfGuessScore();
        rounds.add(currentRound);
        resetTrackedPhysicalTimerComparator();
        resetActivityForNewRound(timestamp);
        if (gtbHooks != null) gtbHooks.onRoundPre();
        setBuilder(builder);
        return true;
    }

    private void startKnownRoundAfterPreStartSkip(int round, int total, long timestamp) {
        GuessSoundController.INSTANCE.onRoundBoundary();
        if (currentRound.roundEnd == 0) {
            currentRound.roundEnd = timestamp;
        }
        currentRound.roundNumber = -1;
        if (gtbHooks != null) gtbHooks.onRoundEnd(true);

        currentRound = new RoundData();
        currentRound.roundNumber = round;
        currentRound.totalRounds = total;
        currentRound.language = currentLanguage;
        currentRound.langCode = currentLangCode;
        currentRound.roundStart = timestamp;
        String pnb = pendingNextBuilder;
        if (pnb != null) {
            pendingNextBuilder = null;
        }
        lastHintText = "";
        rounds.add(currentRound);
        deduceUnknownRoundsBefore(rounds.size() - 2, round);
        resetTrackedPhysicalTimerComparator();
        if (pnb != null) setBuilder(pnb);
        if (gtbHooks != null) gtbHooks.onRoundStart(round, total);
    }

    /** Reset every known player's last-activity stamp to "now" at a round boundary
     *  so a fresh round doesn't immediately show silent players as inactive. */
    private void resetActivityForNewRound(long now) {
        for (String ign : players) {
            lastActivityMs.put(ign, now);
        }
    }

    private void handleRoundStart(int round, int total, long timestamp) {
        if (!isInRound(state) && state != TrackingState.LOBBY) return;
        if (!gameInitialized) {
            initNewGame(timestamp);
        }
        clearPendingRejoinState();
        clearPendingSelfGuessScore();
        ChatCooldownCounter.INSTANCE.onRoundStart();

        // Authoritative round boundary — round count is reconfirmed.
        localRejoinedImmediatelyStage = false;
        resetActivityForNewRound(timestamp);

        // The "Round: X/Y" chat fires at the start of the build phase. Eagerly flip
        // state to ROUND_BUILD so guesses/hints arriving before the next scoreboard
        // poll (up to 0.5s later) are attributed to the right phase.
        if (state == TrackingState.ROUND_PRE || state == TrackingState.LOBBY
                || state == TrackingState.ROUND_END) {
            state = TrackingState.ROUND_BUILD;
        }

        // A leave during ROUND_PRE can make the X/Y chat unavailable while the
        // preceding known round and same builder still prove this round number.
        // When the real header later arrives, confirm that inferred placeholder
        // instead of closing it and creating a duplicate round.
        if (currentRound != null && currentRound.roundNumber == round
                && currentRound.roundEnd == 0) {
            currentRound.totalRounds = total;
            if (currentRound.language == null) currentRound.language = currentLanguage;
            if (currentRound.langCode == null) currentRound.langCode = currentLangCode;
            if (gtbHooks != null) gtbHooks.onRoundStart(round, total);
            return;
        }

        if (currentRound != null && currentRound.roundNumber == 0
                && roundHasSkipEvent(currentRound)) {
            startKnownRoundAfterPreStartSkip(round, total, timestamp);
            return;
        }

        // Fill in preliminary round (roundNumber == 0 from beginPreRound)
        if (currentRound != null && currentRound.roundNumber == 0
                && currentRound.roundEnd == 0) {
            currentRound.roundNumber = round;
            currentRound.totalRounds = total;
            if (currentRound.language == null) currentRound.language = currentLanguage;
            if (currentRound.langCode == null) currentRound.langCode = currentLangCode;
            // Deduce unknown rounds before this one (skip the just-filled-in current round)
            deduceUnknownRoundsBefore(rounds.size() - 2, round);
            if (gtbHooks != null) gtbHooks.onRoundStart(round, total);
            return;
        }

        // Fill in unknown round (roundNumber == -1 from rejoin placeholder)
        if (currentRound != null && currentRound.roundNumber == -1
                && currentRound.roundEnd == 0) {
            currentRound.roundNumber = round;
            currentRound.totalRounds = total;
            if (currentRound.language == null) currentRound.language = currentLanguage;
            if (currentRound.langCode == null) currentRound.langCode = currentLangCode;
            deduceUnknownRoundsBefore(rounds.size() - 2, round);
            if (gtbHooks != null) gtbHooks.onRoundStart(round, total);
            return;
        }

        if (currentRound != null && currentRound.roundEnd == 0) {
            currentRound.roundEnd = timestamp;
        }

        // Detect skipped builder rounds only. Do not infer non-builder leavers
        // here; explicit builder-left chat is the only leaver source for this path.
        boolean roundCutShort = false;
        if (currentRound != null && currentRound.roundEnd > 0 && currentRound.events != null) {
            for (GameStats.GameEvent ev : currentRound.events) {
                if ("builder_left".equals(ev.type) || "afk_skip".equals(ev.type)) {
                    roundCutShort = true;
                    break;
                }
            }
        }
        if (gtbHooks != null) gtbHooks.onRoundEnd(roundCutShort);

        // Deduce before creating the new round (current round is still the last element)
        deduceUnknownRoundsBefore(rounds.size() - 1, round);

        currentRound = new RoundData();
        currentRound.roundNumber = round;
        currentRound.totalRounds = total;
        currentRound.language = currentLanguage;
        currentRound.langCode = currentLangCode;
        currentRound.roundStart = timestamp;
        String pnb = pendingNextBuilder;
        if (pnb != null) {
            pendingNextBuilder = null;
        }
        lastHintText = "";
        rounds.add(currentRound);
        if (pnb != null) setBuilder(pnb);
        if (gtbHooks != null) gtbHooks.onRoundStart(round, total);
    }

    /**
     * Walk backwards from startIdx, filling in unknown round numbers by
     * counting down from knownRound. A prior preliminary round can still be 0
     * if the "Round: 1/N" chat line was missed before the next pre-round.
     */
    private void deduceUnknownRoundsBefore(int startIdx, int knownRound) {
        int nextRound = knownRound;
        for (int i = startIdx; i >= 0; i--) {
            RoundData r = rounds.get(i);
            if (r.roundNumber <= 0) {
                nextRound--;
                if (nextRound >= 1) {
                    r.roundNumber = nextRound;
                }
            } else {
                break;
            }
        }
    }

    private void handleThemeReveal(String theme, long timestamp) {
        if ((!isInRound(state) && state != TrackingState.PAUSED && state != TrackingState.POST_GAME)
                || currentRound == null) return;

        currentRound.theme = theme;
        currentRound.roundEnd = timestamp;

        if (!isLocalPlayerBuilder(currentRound)) {
            ThemePointReveal.markCompleteThemeRevealed(currentRound);
        }

        ScoreReconciler.computeBuilderDelta(currentRound);

        // Fallback when the live sidebar did not expose the theme earlier.
        ShortcutReminder.maybeSend(currentRound);
        ShowAllMatching.maybeSend(currentRound);

        String themeLangCode = currentRound.langCode != null
                ? currentRound.langCode : currentLangCode;
        if (gtbHooks != null
                && (state == TrackingState.ROUND_BUILD
                || state == TrackingState.ROUND_END)) {
            gtbHooks.onThemeUpdate(theme, themeLangCode);
        }
        maybeSendThemePoint(theme, themeLangCode);

        writeLiveFile();
    }

    private void handleWinners() {
        if (!isActiveGame(state) && state != TrackingState.PAUSED) return;

        stopActiveTime(System.currentTimeMillis());
        if (currentRound != null && currentRound.roundEnd == 0) {
            currentRound.roundEnd = System.currentTimeMillis();
        }

        // Transition to POST_GAME so scoreboard parse picks up rankings on next ticks.
        // Defer endGame by ~1.5s — gives the scoreboard time to fully populate rankings
        // whether the chat fires before or after the scoreboard flips.
        state = TrackingState.POST_GAME;
        scheduledEndGameTime = System.currentTimeMillis() + 1500;

        // onGameEnd hook fires from endGame() once rankings are finalized — firing
        // here would deliver an empty map because POST_GAME ranking parsing has not
        // run yet on the next-tick scoreboard polls.
    }

    private void handlePause() {
        if (!isActiveGame(state)) return;

        TrackingState stateAtLeave = state;
        int inferredRoundAtLeave = inferPausedRoundCandidate(stateAtLeave);
        stopActiveTime(System.currentTimeMillis());
        state = TrackingState.PAUSED;
        pauseTime = System.currentTimeMillis();
        notInGameTicks = 0;
        PlayerMetaCache.INSTANCE.clearRosterEvidence();
        lastBuilderHookRound = null;
        lastBuilderHookIgn = null;

        // Save builder and totalRounds for rejoin round-change detection
        pausedBuilder = null;
        pausedRoundTotal = 0;
        pausedState = stateAtLeave;
        pausedNextRoundCandidate = inferredRoundAtLeave;
        pausedRoundHadDataGapBeforeLeave = currentRound != null && currentRound.dataGap;
        if (currentRound != null) {
            pausedBuilder = currentRound.builder;
            pausedRoundTotal = currentRound.totalRounds;
            String detail = "state=" + stateAtLeave;
            if (pausedNextRoundCandidate > 0) {
                detail += ",nextRoundCandidate=" + pausedNextRoundCandidate;
            }
            currentRound.events.add(new GameEvent(pauseTime, "disconnect", detail));
            // Mark round as having a data gap — we won't see chat/scoreboard while paused.
            currentRound.dataGap = true;
        }
        // Fallback: use previous round's totalRounds if current round hasn't received it yet
        if (pausedRoundTotal == 0 && rounds.size() >= 2) {
            pausedRoundTotal = rounds.get(rounds.size() - 2).totalRounds;
        }
        if (gtbHooks != null) gtbHooks.onUserLeave();

        writeLiveFile();
    }

    private int inferPausedRoundCandidate(TrackingState stateAtLeave) {
        if (currentRound == null) return 0;
        int candidate = 0;
        if (stateAtLeave == TrackingState.ROUND_PRE) {
            if (currentRound.roundNumber > 0) {
                candidate = currentRound.roundNumber;
            } else {
                int currentIndex = rounds.lastIndexOf(currentRound);
                if (currentIndex > 0) {
                    RoundData previous = rounds.get(currentIndex - 1);
                    if (previous != null && previous.roundNumber > 0) {
                        candidate = previous.roundNumber + 1;
                    }
                }
            }
        } else if (stateAtLeave == TrackingState.ROUND_END
                && currentRound.roundNumber > 0 && currentRound.roundEnd > 0) {
            candidate = currentRound.roundNumber + 1;
        }
        int total = currentRound.totalRounds > 0 ? currentRound.totalRounds : pausedRoundTotal;
        if (candidate <= 0 || (total > 0 && candidate > total)) return 0;
        return candidate;
    }

    private void handleRejoin(String variant, String builder, String theme, long timestamp) {
        boolean wasPaused = state == TrackingState.PAUSED;
        if (!wasPaused && !isAwaitingRejoinDetail()) return;

        startActiveTime(timestamp);
        state = TrackingState.ROUND_BUILD;
        pauseTime = 0;
        pendingRejoinCheck = true;

        // Severe-uncertainty marker: we rejoined without seeing a fresh ROUND_PRE.
        // Cleared on the next handleRoundStart / beginPreRound. Skip when we ARE
        // the builder of this round (rejoinBuilder variant gives us the theme,
        // so totals can be inferred confidently for the local player).
        localRejoinedImmediatelyStage = !"builder".equals(variant);

        if (wasPaused) {
            invalidatePreLeaveSidebarSnapshot();
        }
        snapshotTabList();

        String cleanBuilder = builder != null ? cleanIgn(builder) : null;

        if (wasPaused && gtbHooks != null) gtbHooks.onUserRejoin();

        if (currentRound != null) {
            String detail = "variant=" + variant;
            if (cleanBuilder != null) detail += ",builder=" + cleanBuilder;
            if (theme != null) detail += ",theme=" + theme;
            currentRound.events.add(new GameEvent(timestamp, "rejoin", detail));

            if ("builder".equals(variant) && theme != null) {
                String local = localPlayer;
                if (local == null || local.isEmpty()) {
                    Minecraft mc = Minecraft.getMinecraft();
                    local = mc != null && mc.thePlayer != null ? mc.thePlayer.getName() : null;
                }
                if (local != null && !local.isEmpty()) {
                    resolveRejoinMessageBuilder(local, timestamp);
                    reassignBuilder(currentRound, local);
                }
                boolean themeChanged = !theme.equals(currentRound.theme);
                currentRound.theme = theme;
                clearPendingRejoinState();
                if (themeChanged) writeLiveFile();
            }
            if ("guesser".equals(variant) && cleanBuilder != null) {
                if (resolveRejoinMessageBuilder(cleanBuilder, timestamp)) {
                    return;
                }
                reassignBuilder(currentRound, cleanBuilder);
            }
        }
    }

    private void endGame(boolean completed, String interruptReason) {
        endGame(completed, interruptReason, null);
    }

    private void endGame(boolean completed, String interruptReason, File forcedStatsDir) {
        // Guard against duplicate endGame calls for the same game
        if (gameEnded) return;
        gameEnded = true;

        long now = System.currentTimeMillis();

        // Give the custom tracker a final leaderboard anchor before JSON export.
        // Its conservative late-identity resolver may call transferIdentity(),
        // which mutates rounds/players so the export reflects the same merge.
        if (gtbHooks != null) {
            Map<String, Integer> finalScores = new LinkedHashMap<String, Integer>();
            for (RankEntry r : rankings) {
                if (r != null && r.ign != null) finalScores.put(r.ign, r.score);
            }
            gtbHooks.onGameEnd(finalScores);
        }

        // Reconcile per-round score deltas against the final leaderboard. Collapses
        // forks where arithmetic uniquely determines the answer; leftover residual
        // is exported as `unknownResidual` rather than being attributed.
        ScoreReconciler.FinalizationResult fin =
                ScoreReconciler.finalizeWithLeaderboard(rounds, rankings, midGameUnattributedResidual);
        lastUnknownResidual.clear();
        if (fin != null && fin.unknownResidual != null) {
            lastUnknownResidual.putAll(fin.unknownResidual);
        }

        // Skip the export entirely for interrupted games with no recorded rounds —
        // these are usually preempted lobby joins (player joined during countdown
        // and left before round 1 started). Writing them produces ~300-byte noise
        // files and would clobber a previously-meaningful current.json.
        boolean skipExport = !completed && rounds.isEmpty();

        File configDir = forcedStatsDir != null ? forcedStatsDir : runtimeStatsDir();
        if (configDir != null && !skipExport) {
            captureTrueIgn();
            refreshIdentityMetadata();
            String json = GameExporter.toJson(gameStart, now, completed, !completed,
                    interruptReason, currentLanguage, localPlayer,
                    trueIgn, playerUuid, nick, isNicked, activeTimeAt(now),
                    players, rounds, rankings,
                    lastUnknownResidual);
            if (completed) {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(now));
                GameExporter.writeJsonFile(new File(configDir, timestamp + ".json"), json);
            } else {
                GameExporter.writeJsonFile(new File(configDir, "interrupted_" +
                        new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(now)) + ".json"), json);
            }
            GameExporter.writeJsonFile(new File(configDir, "current.json"), json);
        }

        // Keep end-game residuals in the log/export only; do not surface this
        // bookkeeping detail in chat during normal play.
        try {
            String me = Minecraft.getMinecraft().thePlayer != null
                    ? Minecraft.getMinecraft().thePlayer.getName() : null;
            Integer res = me != null ? lastUnknownResidual.get(me) : null;
            if (res != null && res > 0) {
                GameExporter.LOGGER.info("Local player {} has +{} points across rounds we lost track of (game-end residual)",
                        me, res);
            }
        } catch (Exception ignored) { /* never break end-of-game export */ }

        String retainedLocalIgn = bestKnownLocalIgn();

        state = TrackingState.NOT_IN_GAME;
        clearLobbyHandoff();
        pauseTime = 0;
        gameInitialized = false;
        pendingRejoinCheck = false;
        pausedBuilder = null;
        pausedRoundTotal = 0;
        pausedState = null;
        pausedNextRoundCandidate = 0;
        pausedRoundHadDataGapBeforeLeave = false;
        lastBuilderHookRound = null;
        lastBuilderHookIgn = null;
        scheduledEndGameTime = 0;
        midGameUnattributedResidual.clear();
        roundsMissedDuringDisconnect.clear();
        localRejoinedImmediatelyStage = false;
        lastActivityMs.clear();
        leavers.clear();
        builderLeftLeavers.clear();
        pendingBuilderTabReturns.clear();
        RawPacketRecorder.INSTANCE.finishSession();
        lastSidebarScores.clear();
        sidebarDropKnownScores.clear();
        pendingUnknownSidebarPlayers.clear();
        sidebarDropMissingTicks.clear();
        sidebarDropMissingScores.clear();
        sidebarTrackingRound = null;
        lastLocalIgn = PlayerMetaCache.isValidMcUsername(retainedLocalIgn)
                ? retainedLocalIgn : null;
        currentPhysicalTimerSeconds = null;
        activeTimeMs = 0L;
        activeTimeStartedAt = 0L;
        localPlayer = null;
        resetTrackedPhysicalTimerComparator();
        players.clear();
        rankings.clear();
        currentRound = null;
        PlayerMetaCache.INSTANCE.clearHarvestedRanks();
        lobbyQuitSuppress.clear();

    }

    void scheduleAutoRequeue(long rosterCapturedAt, boolean completed, boolean hadFinalRankings) {
        if (!completed || !hadFinalRankings || !autoRequeue) return;
        if (firstFinalRosterCaptureTime <= 0L) {
            firstFinalRosterCaptureTime = rosterCapturedAt;
            rescheduleAutoRequeueFromFirstRoster();
        }
    }

    private void rescheduleAutoRequeueFromFirstRoster() {
        if (!autoRequeue || firstFinalRosterCaptureTime <= 0L) {
            requeueTime = 0L;
            return;
        }
        requeueTime = firstFinalRosterCaptureTime
                + Math.round(autoRequeueDelaySeconds * 1000F);
    }

    private void clearAutoRequeueSchedule() {
        firstFinalRosterCaptureTime = 0L;
        requeueTime = 0L;
    }

    boolean consumeAutoRequeueIfReady(long now, TrackingState scoreboardState) {
        if (requeueTime <= 0L) return false;
        if (!autoRequeue || scoreboardState != TrackingState.POST_GAME) {
            GameExporter.LOGGER.info("Auto-requeue skipped: enabled=" + autoRequeue
                    + " scoreboardState=" + scoreboardState);
            clearAutoRequeueSchedule();
            return false;
        }
        if (now < requeueTime) return false;
        clearAutoRequeueSchedule();
        return true;
    }

    // ── Parsing Helpers ──────────────────────────────────────────────────

    /** Extract the builder IGN from scoreboard lines (line above the "Builder:" label). */
    private String extractBuilderFromScoreboard(List<String> cleanLines) {
        for (int i = 1; i < cleanLines.size(); i++) {
            String line = cleanLines.get(i);
            for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
                if (lp.sbBuilder != null && line.startsWith(lp.sbBuilder)) {
                    String prev = cleanLines.get(i - 1).trim();
                    if (!prev.isEmpty() && !prev.contains(":")
                            && !prev.contains("hypixel.net") && !prev.equals("???")
                            && !DATE_FOOTER_PATTERN.matcher(prev).matches()) {
                        String cleaned = cleanIgn(prev);
                        return cleaned.isEmpty() ? null : cleaned;
                    }
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Capture the sidebar timer + theme into cached fields each scoreboard tick.
     * Called from GtbStructures.readScoreboard after onScoreboardTick. The custom
     * scoreboard panel reads {@link #getCurrentTimerText()} / {@link #getCurrentSidebarTheme()}
     * per render frame instead of re-parsing cleanLines.
     */
    public void captureSidebar(String titleClean, List<String> cleanLines) {
        if (cleanLines == null) {
            lastSidebarTimer = null;
            lastSidebarTheme = null;
            return;
        }
        lastSidebarTimer = scanSidebarTimer(cleanLines);
        lastSidebarTheme = extractThemeFromScoreboard(cleanLines);
        if (gtbHooks != null && lastSidebarTimer != null) {
            gtbHooks.onTimerUpdate(lastSidebarTimer);
        }
    }

    /**
     * Scan all lines for any language's sbTime / sbStartsIn / sbNextRound label and
     * return the timer value. Tries the active language first, then any other.
     */
    private String scanSidebarTimer(List<String> cleanLines) {
        // Active language first.
        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (currentLangCode != null && currentLangCode.equals(lp.lang)) {
                String v = matchTimerLine(cleanLines, lp);
                if (v != null) return v;
                break;
            }
        }
        // Fallback: any pattern.
        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (currentLangCode != null && currentLangCode.equals(lp.lang)) continue;
            String v = matchTimerLine(cleanLines, lp);
            if (v != null) return v;
        }
        return null;
    }

    static String matchTimerLine(List<String> cleanLines, LangPatterns lp) {
        if (lp == null) return null;
        for (String line : cleanLines) {
            if (line == null || line.isEmpty()) continue;
            if (lp.sbTime != null && line.startsWith(lp.sbTime)) {
                String v = line.substring(lp.sbTime.length()).trim();
                if (!v.isEmpty()) return v;
            }
            if (LangPatterns.startsWithRoundEndLabel(line, lp.sbNextRound)) {
                String v = line.substring(lp.sbNextRound.length()).trim();
                if (!v.isEmpty()) return v;
            }
            if (lp.sbStartsIn != null) {
                if (lp.sbStartsInIsSuffix) {
                    int idx = line.indexOf(lp.sbStartsIn);
                    if (idx > 0) {
                        String v = line.substring(0, idx).trim();
                        if (!v.isEmpty()) return v;
                    }
                } else if (line.startsWith(lp.sbStartsIn)) {
                    String v = line.substring(lp.sbStartsIn.length()).trim();
                    if (!v.isEmpty()) return v;
                }
            }
        }
        return null;
    }

    static boolean containsSidebarClockToken(String cleanLine) {
        return cleanLine != null && SIDEBAR_CLOCK_TOKEN_PATTERN.matcher(cleanLine).matches();
    }

    /**
     * Extract the live theme from scoreboard lines (line above the "Theme:" label).
     * The scoreboard theme syncs with the currently selected language, so the caller
     * must pair this with the LIVE language (not the round's locked language) when
     * crosschecking.
     */
    private String extractThemeFromScoreboard(List<String> cleanLines) {
        for (int i = 1; i < cleanLines.size(); i++) {
            String line = cleanLines.get(i);
            for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
                if (lp.sbTheme != null && line.startsWith(lp.sbTheme)) {
                    String prev = cleanLines.get(i - 1).trim();
                    if (!prev.isEmpty() && !prev.contains(":")
                            && !prev.contains("hypixel.net") && !prev.equals("???")
                            && !DATE_FOOTER_PATTERN.matcher(prev).matches()) {
                        return prev;
                    }
                    return null;
                }
            }
        }
        return null;
    }

    private String extractCorrectGuessIGN(String clean, LangPatterns lp) {
        String ign;
        if (lp.correctGuessPrefix != null) {
            if (!clean.startsWith(lp.correctGuessPrefix)) return null;
            String rest = clean.substring(lp.correctGuessPrefix.length());
            if (rest.endsWith(lp.correctGuessSuffix)) {
                ign = rest.substring(0, rest.length() - lp.correctGuessSuffix.length()).trim();
            } else {
                return null;
            }
        } else if (clean.endsWith(lp.correctGuessSuffix)) {
            ign = clean.substring(0, clean.length() - lp.correctGuessSuffix.length()).trim();
        } else {
            return null;
        }
        // Correct-guess system notices contain the bare IGN. Sanitizing an
        // arbitrary prefix here can concatenate a chat envelope, rank and
        // message body into a plausible ghost identity.
        ign = stripRankPrefixes(ign);
        ign = LangPatterns.stripLeadingBbTitle(ign);
        return PlayerMetaCache.isValidMcUsername(ign) ? ign : null;
    }

    /** Common system-line observer used with or without stats tracking. */
    private void observeGuessSoundChat(String clean, long now) {
        if (clean == null || isNonGameChatChannel(clean)) return;
        String guesser = LangPatterns.stripGuesserChatPrefix(clean);
        if (guesser != null || (clean.contains(": ")
                && LangPatterns.startsWithChatTitle(LangPatterns.stripEmojis(clean).trim()))) return;

        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (lp.countdown1Sec != null && clean.equals(lp.countdown1Sec)) {
                GuessSoundController.INSTANCE.onRoundBoundary();
                break;
            }
            if (lp.roundPrefix != null && clean.startsWith(lp.roundPrefix)) {
                GuessSoundController.INSTANCE.onRoundBoundary();
                break;
            }
            if (lp.themeWasPrefix != null && clean.startsWith(lp.themeWasPrefix)) {
                GuessSoundController.INSTANCE.onRoundBoundary();
                break;
            }
        }

        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (lp.builderPrefix == null || !clean.startsWith(lp.builderPrefix)) continue;
            String builder = cleanIgn(clean.substring(lp.builderPrefix.length()));
            if (!builder.isEmpty()) {
                GuessSoundController.INSTANCE.observeBuilderStatus(isLocalPlayerIgn(builder));
            }
            break;
        }

        Integer points = LangPatterns.selfCorrectGuessPoints(clean);
        if (points != null) {
            GuessSoundController.INSTANCE.observeLocalGuessPoints(points.intValue(), now);
            return;
        }

        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (lp.correctGuessSuffix == null) continue;
            String ign = extractCorrectGuessIGN(clean, lp);
            if (ign != null && isLocalPlayerIgn(ign)) {
                GuessSoundController.INSTANCE.observeLocalCorrectGuess(now);
                return;
            }
        }
    }

    static boolean isNonGameChatChannel(String clean) {
        if (clean == null) return false;
        String normalized = LangPatterns.stripEmojis(clean).trim();
        int colon = normalized.indexOf(": ");
        if (colon < 0) return false;
        String envelope = normalized.substring(0, colon).trim();
        return envelope.startsWith("Party >")
                || envelope.startsWith("Guild >")
                || envelope.startsWith("Officer >")
                || envelope.startsWith("From ")
                || envelope.startsWith("To ");
    }

    /** Strip bracketed rank prefixes like [VIP], [VIP+], [MVP], [MVP+], [MVP++] */
    private String stripRankPrefixes(String s) {
        boolean changed;
        do {
            changed = false;
            if (s.startsWith("[")) {
                int closeIdx = s.indexOf(']');
                if (closeIdx > 0) {
                    s = s.substring(closeIdx + 1).trim();
                    changed = true;
                }
            }
        } while (changed);
        return s;
    }

    /**
     * Reassign the round's builder. If the new builder differs from the prior
     * builder (which only happens when an earlier write — typically onGuiThemePick
     * — set it to the local player from a stale chest interaction), drop any
     * captured builderThemeOptions and any prior builder-sourced ScoreDelta
     * attributed to the old IGN. Prevents leaked GUI options from crediting the
     * wrong player when chat/scoreboard authoritatively names a different builder.
     */
    /**
     * Auto-detect language when a chat pattern unique to one language matches.
     * Suppressed once the user has explicitly picked a language via the
     * "Selected language: X" message — that takes precedence.
     * Backfills currentRound.language if it was locked in to a stale value
     * (e.g. fallback "en" before the first pirate-specific chat line arrived).
     */
    /**
     * Lock the language inferred from the scoreboard. Sets BOTH the display name
     * and the LangPatterns code, and marks the language as observed-this-game so
     * downstream consumers (notably {@link ScoreReconciler#crosscheckBuilderFromScoreboard},
     * which keys off {@code currentLangCode}) reconcile against the right
     * language on mid-game joins/rejoins.
     *
     * Callers used to set only {@code currentLanguage} (a display name) here,
     * leaving {@code currentLangCode} stale until a chat clue arrived.
     */
    private void applyScoreboardLanguageDetection(List<String> cleanLines) {
        if (languageExplicitlySet) return;
        if (languageObservedThisGame && currentLangCode != null) return;
        if (cleanLines == null) return;
        String code = LangPatterns.detectLanguageFromScoreboard(cleanLines);
        if (code == null) return;
        currentLangCode = code;
        String display = LangPatterns.mapLanguageName(code);
        if (display != null) currentLanguage = display;
        languageObservedThisGame = true;
        if (currentRound != null) {
            currentRound.langCode = currentLangCode;
            if (currentLanguage != null) currentRound.language = currentLanguage;
        }
    }

    private void maybeAutoDetectLanguage(LangPatterns lp) {
        if (languageExplicitlySet) return;
        if (lp == null || lp.lang == null) return;
        // A language-unique chat pattern matched, so the current language is
        // confirmed for this game even when the seed already had it right.
        languageObservedThisGame = true;
        if (lp.lang.equals(currentLangCode)) return;
        currentLangCode = lp.lang;
        String display = LangPatterns.mapLanguageName(lp.lang);
        if (display != null) currentLanguage = display;
        if (currentRound != null) {
            currentRound.langCode = currentLangCode;
            if (currentLanguage != null) currentRound.language = currentLanguage;
        }
    }

    private void applyLobbyLanguageFallback(String langCode) {
        if (langCode == null) return;
        currentLangCode = langCode;
        String display = LangPatterns.mapLanguageName(langCode);
        if (display != null) currentLanguage = display;
        languageObservedThisGame = true;
        if (currentRound != null) {
            currentRound.langCode = currentLangCode;
            if (currentLanguage != null) currentRound.language = currentLanguage;
        }
        if (!langCode.equals(GtbStructures.lastLangCode)) {
            GtbStructures.lastLangCode = langCode;
            GtbStructures.saveConfig();
        }
    }

    private void disableLobbyLanguageFallback() {
        lobbyLanguageFallback.disableForServerSession();
    }

    /**
     * Hypixel awards the builder theme-difficulty points (1/2/3 from the three
     * slot options) only if at least one player guessed correctly during the round.
     * If the builder is afk-skipped or leaves with zero correct guesses on the
     * board, they score 0 — so overwrite any fork/GUI value with a definite 0.
     * If correct guesses exist, leave the existing logic alone (scoreboard
     * observation or dictionary fork will resolve to the right theme points).
     */
    private void zeroOutBuilderDelta(RoundData r, String eventType) {
        if (r == null || r.builder == null || r.builder.isEmpty()) return;
        r.scoreDeltas.put(r.builder, ScoreDelta.definite(0, eventType));
    }

    private void zeroOutBuilderDeltaIfNoGuesses(RoundData r, String eventType) {
        if (r == null || r.builder == null || r.builder.isEmpty()) return;
        if (r.correctGuesses != null && !r.correctGuesses.isEmpty()) return;
        zeroOutBuilderDelta(r, eventType);
    }

    private boolean materializePendingBuilderForPreStartSkip(long now) {
        if (currentRound != null) return true;
        String skippedBuilder = pendingNextBuilder;
        if (skippedBuilder == null || skippedBuilder.isEmpty()) return false;

        if (!gameInitialized) {
            initNewGame(now);
        }
        if (state == TrackingState.LOBBY) {
            state = TrackingState.ROUND_PRE;
        }
        if (currentRound == null) {
            pendingNextBuilder = skippedBuilder;
            beginPreRound(now);
        }
        if (currentRound == null) return false;
        if (currentRound.builder == null || currentRound.builder.isEmpty()) {
            setBuilder(skippedBuilder);
        }
        if (pendingNextBuilder != null && pendingNextBuilder.equals(skippedBuilder)) {
            pendingNextBuilder = null;
        }
        return true;
    }

    private void handleBuilderLeft(long now, LangPatterns.BuilderLeftMessageKind kind) {
        if (!materializePendingBuilderForPreStartSkip(now)) return;
        currentRound.events.add(new GameEvent(now, "builder_left", kind.detail));
        zeroOutBuilderDeltaIfNoGuesses(currentRound, "builder_left");
        // Latch the leaver flag so the panel marker persists past chat.
        if (currentRound.builder != null && !currentRound.builder.isEmpty()) {
            removePendingBuilderTabReturnIgnoreCase(currentRound.builder);
            leavers.add(currentRound.builder);
            builderLeftLeavers.add(currentRound.builder);
        }
        if (gtbHooks != null) gtbHooks.onRoundSkipped();
    }

    private void reassignBuilder(RoundData r, String newBuilder) {
        if (r == null || newBuilder == null || newBuilder.isEmpty()) return;
        String old = r.builder;
        r.builder = newBuilder;
        if (isLocalPlayerBuilder(r)) {
            r.localBuildOwner = true;
        }
        boolean changed = (old == null && newBuilder != null)
                || (old != null && !old.equals(newBuilder));
        // Ensure the builder is in the players list even if the tab snapshot
        // missed them — the builder must always appear in the scoreboard panel.
        if (!containsPlayerIgnoreCase(newBuilder)) {
            players.add(newBuilder);
        }
        if (old != null && !old.equals(newBuilder)) {
            r.builderThemeOptions = null;
            ScoreDelta d = r.scoreDeltas.get(old);
            if (d != null && ("builder".equals(d.source) || "builder_gui".equals(d.source))) {
                r.scoreDeltas.remove(old);
            }
        }
        if (changed && gtbHooks != null) {
            emitBuilderHook(old, newBuilder);
        }
    }

    private void syncBuilderHookForCurrentRound() {
        if (gtbHooks == null || currentRound == null
                || currentRound.builder == null || currentRound.builder.isEmpty()) return;
        if (lastBuilderHookRound == currentRound
                && lastBuilderHookIgn != null
                && lastBuilderHookIgn.equalsIgnoreCase(currentRound.builder)) return;
        emitBuilderHook(currentRound.builder, currentRound.builder);
    }

    private void emitBuilderHook(String previous, String current) {
        if (gtbHooks == null) return;
        gtbHooks.onBuilderChange(previous, current);
        lastBuilderHookRound = currentRound;
        lastBuilderHookIgn = current;
    }

    /**
     * Normalize an IGN by stripping rank prefixes and all non-IGN characters
     * (Minecraft names are [A-Za-z0-9_]{1,16}). Removes seasonal cosmetics like
     * the ⚽ World-Cup marker Hypixel splices into sidebar / leaderboard names.
     */
    private String cleanIgn(String s) {
        if (s == null) return "";
        s = stripRankPrefixes(s.trim());
        s = LangPatterns.stripLeadingBbTitle(s);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Unified player-chat parser. Extracts IGN + emblem via the reverse-scan
     * logic, fires activity/leaver/hook updates in every applicable phase, and
     * only appends the line as a guess when we are in ROUND_BUILD. While the
     * game is PAUSED, unknown speakers belong to the intervening lobby/server
     * and are ignored; preserved roster players still update normally.
     */
    private void parsePlayerChat(String raw, String clean) {
        long now = System.currentTimeMillis();

        int colonIdx = clean.indexOf(": ");
        if (colonIdx < 0) return;

        String beforeColon = clean.substring(0, colonIdx).trim();
        String text = clean.substring(colonIdx + 2).trim();

        String[] parts = beforeColon.split("\\s+");
        if (parts.length == 0) return;

        // Scan parts in reverse to find the IGN — emblem-owning players' chat
        // lines have a trailing emblem token (e.g. "Player ⚑: text") which a
        // naive parts.last lookup mistakes for the IGN.
        String ign = null;
        String emblem = null;
        for (int i = parts.length - 1; i >= 0; i--) {
            String stripped = StringUtils.stripControlCodes(parts[i]);
            String cleaned = cleanIgn(stripped);
            if (PlayerMetaCache.isValidMcUsername(cleaned)) {
                ign = cleaned;
                if (i < parts.length - 1) {
                    String rawAfter = parts[i + 1];
                    String afterIgn = StringUtils.stripControlCodes(rawAfter);
                    if (afterIgn.length() == 1 && isEmblemChar(afterIgn.charAt(0))) {
                        // Preserve the §x color code so the scoreboard renders the
                        // emblem in its in-game shop color. Pull the LAST § code in
                        // the raw token (handles "§r§6⚑" prefix sequences).
                        String color = extractLastFormatCode(rawAfter);
                        emblem = (color != null ? color : "") + afterIgn;
                    }
                }
                break;
            }
        }
        if (ign == null || ign.isEmpty()) return;

        if (isReservedNonPlayerName(ign)) return;
        // `/lrj` temporarily exposes chat from the main lobby while the old
        // game remains suspended. Only its preserved roster is authoritative
        // in PAUSED; active-game chat may still complement a missed roster.
        if (state == TrackingState.PAUSED && !containsPlayerIgnoreCase(ign)) return;

        // Activity / leaver clear fires outside ROUND_BUILD as well so a known
        // player speaking between rounds or during rejoin returns to active.
        if (isLocalPlayerIgn(ign)) {
            ChatCooldownCounter.INSTANCE.onUserChatAccepted(now);
        }
        PlayerMetaCache.INSTANCE.harvestChatMessageRank(ign, raw);
        ensureObservedPlayer(ign);
        markActivity(ign);
        if (gtbHooks != null) gtbHooks.onPlayerChat(ign);
        if (emblem != null && gtbHooks != null) gtbHooks.onPlayerEmblem(ign, emblem);

        // Guesses only count during ROUND_BUILD. Chat between "Builder: xxx"
        // and "Round: X/Y" (ROUND_PRE) is pre-round chatter, not a guess.
        if (state == TrackingState.ROUND_BUILD && currentRound != null) {
            currentRound.guesses.add(new GuessEntry(now, ign, text));
            if (gtbHooks != null) gtbHooks.onPlayerGuess(ign, text);
            maybeSendThemePoint(currentRound.theme, currentRound.langCode);
        }
    }

    private void maybeSendThemePoint(String displayedTheme, String liveLangCode) {
        if (currentRound == null) return;
        GtbGame game = GameTracker.INSTANCE.game;
        Collection<String> ruledOut = Collections.emptySet();
        if (game != null && currentRound.roundNumber > 0
                && game.currentRound == currentRound.roundNumber) {
            ruledOut = game.ruledOutEnglishThemesThisRound;
        }
        ThemePointReveal.maybeSendFromTheme(currentRound, displayedTheme, liveLangCode,
                ruledOut, GtbStructures.themePointReveal,
                GtbStructures.themePointRevealShowAfterGuess);
    }

    private void maybeSendThemePointFromScoreboard() {
        if (currentRound == null || currentRound.themePointRevealSent
                || !GtbStructures.themePointReveal) return;
        ThemePointReveal.maybeSendFromScoreDelta(currentRound, true,
                GtbStructures.themePointRevealShowAfterGuess);
    }

    private boolean isLocalPlayerBuilder(RoundData round) {
        if (round == null) return false;
        if (round.localBuildOwner) return true;
        if (round.builder == null) return false;
        String localIgn = localPlayer;
        if (localIgn == null) {
            Minecraft mc = Minecraft.getMinecraft();
            localIgn = mc == null || mc.thePlayer == null ? null : mc.thePlayer.getName();
        }
        return localIgn != null && round.builder.equalsIgnoreCase(localIgn);
    }

    private boolean shouldBufferNextBuilder(String builder) {
        return state == TrackingState.ROUND_BUILD
                && currentRound != null
                && currentRound.theme != null
                && !currentRound.theme.isEmpty()
                && currentRound.builder != null
                && !currentRound.builder.equalsIgnoreCase(builder);
    }

    /** Returns the last §x color code (0-9, a-f) in the string, or null. Format
     *  codes (k-o, r) are skipped — only colors carry the emblem's identity. */
    private static String extractLastFormatCode(String s) {
        if (s == null) return null;
        for (int i = s.length() - 2; i >= 0; i--) {
            if (s.charAt(i) == '§') {
                char code = Character.toLowerCase(s.charAt(i + 1));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    return "§" + code;
                }
            }
        }
        return null;
    }

    /** GTB shop emblem allowlist (kept narrow to avoid false positives from
     *  punctuation that occasionally trails an IGN in international chat). */
    private static boolean isEmblemChar(char c) {
        return c == '≈'  // ≈
            || c == 'α'  // α
            || c == 'Ω'  // Ω
            || c == '$'
            || c == 'π'  // π
            || c == 'ƒ'  // ƒ
            || c == '☺'  // ☺
            || c == '☀'  // ☀
            || c == '★'  // ★
            || c == '⚑'  // ⚑
            || c == '⧈'  // ⧈
            || c == '»'; // »
    }

    private void mergePacketRoster() {
        if (state != TrackingState.LOBBY) return;
        List<String> observed = PlayerMetaCache.INSTANCE.packetRosterSnapshot();
        if (observed.isEmpty()) return;
        List<String> added = new ArrayList<String>();
        for (String observedIgn : observed) {
            String ign = canonicalRosterIgn(observedIgn);
            if (!PlayerMetaCache.isValidMcUsername(ign)) continue;
            if (isLobbyQuitSuppressed(ign)) continue;
            if (!containsPlayerIgnoreCase(ign)) {
                players.add(ign);
                markPassivePresence(ign);
                added.add(ign);
            }
        }
        if (!added.isEmpty()) {
            if (gtbHooks != null && gameInitialized && isRosterTrackingState()) {
                for (String ign : added) {
                    gtbHooks.onGameRosterPlayer(ign);
                }
            }
        }
    }

    void beginLobbyHandoff(long now) {
        handoffLobbyRoster.clear();
        mergeRosterInto(handoffLobbyRoster, players);
        gtbHandoffRosterUntilMs = now + GTB_HANDOFF_ROSTER_GRACE_MS;
        PlayerMetaCache.INSTANCE.clearRosterEvidence();
    }

    void seedGameStartRoster(long now, String localIgn) {
        boolean seedFromLobbyRoster = state == TrackingState.LOBBY || gtbHandoffRosterUntilMs > 0L;
        pruneLobbyRosterToAnnouncedCount();
        if (gtbHandoffRosterUntilMs > 0L) {
            if (now <= gtbHandoffRosterUntilMs) {
                mergeRosterInto(players, handoffLobbyRoster);
            } else {
                PlayerMetaCache.INSTANCE.clearRosterEvidence();
            }
            clearLobbyHandoff();
        }

        if (seedFromLobbyRoster) {
            mergeUnsuppressedLobbyRosterIntoPlayers(
                    PlayerMetaCache.INSTANCE.encodedGameRosterSnapshot());
            mergeUnsuppressedLobbyRosterIntoPlayers(
                    PlayerMetaCache.INSTANCE.packetRosterSnapshot());
            pruneLobbyRosterToAnnouncedCount();
        }

        if (players.isEmpty() && state == TrackingState.LOBBY) {
            snapshotTabList();
        }
        boolean confirmedLocalAlreadyPresent = PlayerMetaCache.isValidMcUsername(serverConfirmedLocalIgn)
                && containsPlayerIgnoreCase(serverConfirmedLocalIgn);
        if (PlayerMetaCache.isValidMcUsername(localIgn)
                && !containsPlayerIgnoreCase(localIgn)
                && !confirmedLocalAlreadyPresent) {
            players.add(localIgn);
        }
        clearLobbyPacketRemovalEvidence();
    }

    private void clearLobbyHandoff() {
        gtbHandoffRosterUntilMs = 0L;
        handoffLobbyRoster.clear();
    }

    private boolean handleLobbyJoinMessage(String raw, String clean, long now) {
        String ign = parseLobbyJoinIgn(clean);
        if (ign == null) return false;
        Integer announcedCount = parseLobbyAnnouncedPlayerCount(clean);
        disableLobbyLanguageFallback();

        if (isLocalPlayerIgn(ign)) {
            finalizeForSelfLobbyJoin(now);
            enterFreshLobbyFromJoin(ign);
            if (announcedCount != null) {
                lobbyAnnouncedPlayerCount = announcedCount.intValue();
            }
            recordLobbyJoin(ign, raw);
            return true;
        }

        if (state == TrackingState.LOBBY) {
            gtbLobbyRosterConfirmed = true;
            if (announcedCount != null) {
                lobbyAnnouncedPlayerCount = announcedCount.intValue();
            }
            recordLobbyJoin(ign, raw);
        }
        return true;
    }

    private void finalizeForSelfLobbyJoin(long now) {
        boolean hasInMemoryGame = !gameEnded
                && (gameInitialized || currentRound != null || !rounds.isEmpty());
        if (hasInMemoryGame) {
            boolean completed = state == TrackingState.POST_GAME;
            endGame(completed, completed ? null : "new_gtb_lobby");
            return;
        }
        finalizePendingCurrent(runtimeStatsDir(), "new_gtb_lobby", now);
    }

    public boolean isLocalPlayerIdentity(String ign) {
        return isLocalPlayerIgn(ign);
    }

    private boolean isLocalPlayerIgn(String ign) {
        if (ign == null || ign.isEmpty()) return false;
        captureTrueIgn();
        return ign.equalsIgnoreCase(localPlayer)
                || ign.equalsIgnoreCase(trueIgn)
                || ign.equalsIgnoreCase(nick)
                || ign.equalsIgnoreCase(lastLocalIgn)
                || ign.equalsIgnoreCase(clientPlayerIgn())
                || ign.equalsIgnoreCase(sessionUsername());
    }

    private String bestKnownLocalIgn() {
        captureTrueIgn();
        if (PlayerMetaCache.isValidMcUsername(localPlayer)) return localPlayer;
        String ign = clientPlayerIgn();
        if (PlayerMetaCache.isValidMcUsername(ign)) return ign;
        if (PlayerMetaCache.isValidMcUsername(nick)) return nick;
        if (PlayerMetaCache.isValidMcUsername(lastLocalIgn)) return lastLocalIgn;
        return PlayerMetaCache.isValidMcUsername(trueIgn) ? trueIgn : null;
    }

    private void setLocalPlayerIdentity(String activeIgn) {
        localPlayer = PlayerMetaCache.isValidMcUsername(activeIgn) ? activeIgn : null;
        if (localPlayer != null) lastLocalIgn = localPlayer;
        refreshIdentityMetadata();
    }

    private void captureTrueIgn() {
        if (PlayerMetaCache.isValidMcUsername(trueIgn)) return;
        String accountIgn = sessionUsername();
        if (PlayerMetaCache.isValidMcUsername(accountIgn)) {
            trueIgn = accountIgn;
            refreshIdentityMetadata();
        }
    }

    public void setAccountPlayerName(String ign) {
        String clean = cleanIgn(ign);
        if (PlayerMetaCache.isValidMcUsername(clean)) {
            trueIgn = clean;
            refreshIdentityMetadata();
        }
    }

    /** Promotes a server-visible identity already tied to the logged-in local account. */
    public void confirmServerLocalPlayerName(String ign) {
        String clean = cleanIgn(ign);
        if (!PlayerMetaCache.isValidMcUsername(clean)) return;
        captureTrueIgn();
        serverConfirmedLocalIgn = clean;
        if (!PlayerMetaCache.isValidMcUsername(localPlayer)) {
            setLocalPlayerIdentity(clean);
            return;
        }
        if (localPlayer.equalsIgnoreCase(clean)) {
            refreshIdentityMetadata();
            return;
        }
        transferIdentity(localPlayer, clean);
    }

    public void setAccountPlayerUuid(String uuid) {
        String normalized = PlayerUuid.normalize(uuid);
        if (normalized != null && launchPlayerUuid == null) {
            launchPlayerUuid = normalized;
        }
    }

    private void refreshIdentityMetadata() {
        boolean distinct = PlayerMetaCache.isValidMcUsername(trueIgn)
                && PlayerMetaCache.isValidMcUsername(localPlayer)
                && !trueIgn.equalsIgnoreCase(localPlayer);
        isNicked = distinct;
        nick = distinct ? localPlayer : null;
    }

    private String clientPlayerIgn() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            return mc == null || mc.thePlayer == null ? null : mc.thePlayer.getName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String sessionUsername() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            return mc == null || mc.getSession() == null ? null : mc.getSession().getUsername();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void enterFreshLobbyFromJoin(String localIgn) {
        resetFreshLobbyRoster();
        setLocalPlayerIdentity(localIgn);
        state = TrackingState.LOBBY;
        gtbLobbyRosterConfirmed = true;
        notInGameTicks = 0;
    }

    private void resetFreshLobbyRoster() {
        gtbLobbyRosterConfirmed = false;
        clearLobbyHandoff();
        lobbyTabDone = false;
        players.clear();
        rounds.clear();
        currentRound = null;
        rankings.clear();
        PlayerMetaCache.INSTANCE.clearHarvestedRanks();
        lobbyQuitSuppress.clear();
        clearLobbyPacketRemovalEvidence();
    }

    private void recordLobbyJoin(String ign, String raw) {
        if (ign == null || ign.isEmpty()) return;
        lobbyQuitSuppress.remove(ign.toLowerCase(java.util.Locale.ROOT));
        removeLobbyPacketRemovalIgnoreCase(ign);
        PlayerMetaCache.INSTANCE.harvestJoinMessageRank(ign, raw);
        if (!containsPlayerIgnoreCase(ign)) {
            players.add(ign);
        }
        markPassivePresence(ign);
    }

    private static Integer parseLobbyAnnouncedPlayerCount(String clean) {
        if (clean == null || clean.isEmpty()) return null;
        Matcher matcher = LOBBY_COUNT_PATTERN.matcher(clean);
        if (!matcher.find()) return null;
        try {
            return Integer.valueOf(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void rememberLobbyPacketRemoval(String ign) {
        if (ign == null || ign.isEmpty()) return;
        if (!containsIgnoreCase(lobbyPacketRemovals, ign)) {
            lobbyPacketRemovals.add(ign);
        }
    }

    private void pruneLobbyRosterToAnnouncedCount() {
        if (lobbyAnnouncedPlayerCount <= 0
                || players.size() <= lobbyAnnouncedPlayerCount
                || lobbyPacketRemovals.isEmpty()) {
            return;
        }
        Iterator<String> removals = lobbyPacketRemovals.iterator();
        while (players.size() > lobbyAnnouncedPlayerCount && removals.hasNext()) {
            String removed = removals.next();
            removePlayerIgnoreCaseFromRoster(removed);
            removals.remove();
        }
    }

    private void removeLobbyPacketRemovalIgnoreCase(String ign) {
        if (ign == null || ign.isEmpty()) return;
        Iterator<String> removals = lobbyPacketRemovals.iterator();
        while (removals.hasNext()) {
            String existing = removals.next();
            if (existing != null && existing.equalsIgnoreCase(ign)) {
                removals.remove();
            }
        }
    }

    private void clearLobbyPacketRemovalEvidence() {
        lobbyPacketRemovals.clear();
        lobbyAnnouncedPlayerCount = 0;
    }

    private void mergeRosterInto(List<String> target, Collection<String> observed) {
        if (target == null || observed == null) return;
        for (String observedIgn : observed) {
            String ign = canonicalRosterIgn(observedIgn);
            if (!PlayerMetaCache.isValidMcUsername(ign) || isReservedNonPlayerName(ign)) {
                continue;
            }
            boolean present = false;
            for (String existing : target) {
                if (existing != null && existing.equalsIgnoreCase(ign)) {
                    present = true;
                    break;
                }
            }
            if (!present) target.add(ign);
        }
    }

    private void mergeUnsuppressedLobbyRosterIntoPlayers(Collection<String> observed) {
        if (observed == null) return;
        for (String observedIgn : observed) {
            String ign = canonicalRosterIgn(observedIgn);
            if (isLobbyQuitSuppressed(ign)) continue;
            mergeRosterInto(players, java.util.Collections.singletonList(ign));
        }
    }

    private boolean containsPlayerIgnoreCase(String ign) {
        for (String existing : players) {
            if (existing != null && existing.equalsIgnoreCase(ign)) return true;
        }
        return false;
    }

    private boolean isLobbyQuitSuppressed(String ign) {
        return ign != null
                && lobbyQuitSuppress.contains(ign.toLowerCase(java.util.Locale.ROOT));
    }

    private String parseLobbyJoinIgn(String clean) {
        if (clean == null || clean.isEmpty()) return null;
        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (lp.lobbyJoinSuffix == null) continue;
            String work = clean;
            if (lp.lobbyJoinPrefix != null && !lp.lobbyJoinPrefix.isEmpty()) {
                if (!work.startsWith(lp.lobbyJoinPrefix)) continue;
                work = work.substring(lp.lobbyJoinPrefix.length());
            }
            String sfx = lp.lobbyJoinSuffix;
            int countIdx = sfx.indexOf("\\d+/\\d+");
            if (countIdx < 0) continue;
            String before = sfx.substring(0, countIdx);
            String after = sfx.substring(countIdx + "\\d+/\\d+".length());
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "^(.+?)" + java.util.regex.Pattern.quote(before)
                            + "(\\d+/\\d+)" + java.util.regex.Pattern.quote(after) + "$")
                    .matcher(work);
            if (m.matches()) {
                String ign = cleanIgn(m.group(1));
                return ign.isEmpty() ? null : ign;
            }
        }
        return null;
    }

    private String parseLobbyQuitIgn(String clean) {
        if (clean == null || clean.isEmpty()) return null;
        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (lp.lobbyQuitSuffix == null) continue;
            String work = clean;
            if (lp.lobbyQuitPrefix != null && !lp.lobbyQuitPrefix.isEmpty()) {
                if (!work.startsWith(lp.lobbyQuitPrefix)) continue;
                work = work.substring(lp.lobbyQuitPrefix.length());
            }
            if (!work.endsWith(lp.lobbyQuitSuffix)) continue;
            String ign = cleanIgn(work.substring(0, work.length() - lp.lobbyQuitSuffix.length()));
            return ign.isEmpty() ? null : ign;
        }
        return null;
    }

    private void suppressLobbyQuit(String ign) {
        if (ign == null || ign.isEmpty()) return;
        removePlayerIgnoreCaseFromRoster(ign);
        lobbyQuitSuppress.add(ign.toLowerCase(java.util.Locale.ROOT));
        PlayerMetaCache.INSTANCE.observePacketRosterRemove(ign);
    }

    private void removePlayerIgnoreCaseFromRoster(String ign) {
        if (ign == null) return;
        Iterator<String> it = players.iterator();
        while (it.hasNext()) {
            String existing = it.next();
            if (existing != null && existing.equalsIgnoreCase(ign)) {
                it.remove();
            }
        }
    }

    private void ensureObservedPlayer(String ign) {
        if (ign == null || ign.isEmpty()) return;
        if (isReservedNonPlayerName(ign)) return;
        if (!PlayerMetaCache.isValidMcUsername(ign)) return;
        if (!isActiveGame(state)) return;
        if (!containsPlayerIgnoreCase(ign)) {
            players.add(ign);
        }
    }

    private static boolean isReservedNonPlayerName(String ign) {
        if (ign == null) return false;
        String s = ign.trim().toLowerCase(java.util.Locale.ROOT);
        return s.equals("wins")
                || s.equals("losses")
                || s.equals("winstreak")
                || s.equals("games")
                || s.equals("kills")
                || s.equals("deaths")
                || s.equals("coins")
                || s.equals("score")
                || s.equals("level")
                || s.equals("karma")
                || s.equals("rank")
                || s.equals("guild")
                || s.equals("quests")
                || s.equals("achievements");
    }

    private boolean snapshotTabList() {
        // New roster design: take one valid lobby baseline only. In pre-round /
        // in-game Hypixel mutates tab/team formatting for BB ranks and emblems,
        // so snapshots there can poison Hypixel rank colors or wipe the roster.
        if (state != TrackingState.LOBBY) {

            return false;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.sendQueue == null) return false;

        // Build into a temp list first; only replace 'players' if we got
        // something. On laggy clients the tab list may not be populated yet,
        // and we must not wipe a previously-good snapshot with an empty one.
        List<String> snap = new ArrayList<>();
        captureTrueIgn();
        String accountUuid = launchPlayerUuid;
        for (NetworkPlayerInfo info : mc.thePlayer.sendQueue.getPlayerInfoMap()) {
            String name = info.getGameProfile().getName();
            String entryUuid = PlayerUuid.normalize(info.getGameProfile().getId());
            if (accountUuid != null && accountUuid.equals(entryUuid)) {
                confirmServerLocalPlayerName(name);
            }
            name = canonicalRosterIgn(name);
            if (name != null && !name.isEmpty()) {
                snap.add(name);
            }
        }

        if (!snap.isEmpty()) {
            // Initialize activity timestamp for any new player (ensures they start
            // as "active" rather than immediately appearing as inactive).
            if (!players.isEmpty()) {
                for (String name : snap) {
                    if (!players.contains(name)) markPassivePresence(name);
                }
            }
            players.clear();
            for (String name : snap) {
                if (!isLobbyQuitSuppressed(name)) players.add(name);
            }
            mergePacketRoster();
            // Refresh the rank/emblem metadata from the same player-info source
            // so CustomScoreboard's rank-color / emblem rendering has data.
            PlayerMetaCache.INSTANCE.refresh(true);

            return !players.isEmpty();
        }

        return false;
    }

    /** Returns true if any player's display name in the tab list contains a BB rank
     *  title (e.g. "[Rookie]", "[Expert]"). This is the signal that a game has
     *  started — in the lobby these prefixes do not appear. */
    private boolean hasBBRankInTab() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.sendQueue == null) return false;
        for (NetworkPlayerInfo info : mc.thePlayer.sendQueue.getPlayerInfoMap()) {
            String display = null;
            try {
                if (info.getDisplayName() != null) {
                    display = StringUtils.stripControlCodes(info.getDisplayName().getFormattedText());
                }
            } catch (Exception ignored) { /* best-effort */ }
            if (display == null || display.isEmpty()) continue;
            // A display name like "Rookie PlayerName" — tokenize and check each token
            // against the CHAT_TITLES set (BB rank names across 24 languages).
            for (String token : display.split("\\s+")) {
                if (LangPatterns.CHAT_TITLES.contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Check if a GUI title matches any language's theme selection GUI title */
    public boolean isThemeSelectionGui(String title) {
        return LangPatterns.isThemeSelectionGui(title);
    }

    // ── Export Methods ────────────────────────────────────────────────────

    /** Manual export from /gtbs export */
    public IChatComponent manualExport(File gameDir) {
        if (!trackingEnabled) {
            return new ChatComponentText("Stats tracking is off; no data will be exported.");
        }
        if (state == TrackingState.NOT_IN_GAME && rounds.isEmpty()) {
            return new ChatComponentText("No game data to export.");
        }

        long now = System.currentTimeMillis();
        boolean interrupted = (state == TrackingState.PAUSED);
        String reason = interrupted ? "manual_export" : null;
        captureTrueIgn();
        refreshIdentityMetadata();
        String json = GameExporter.toJson(gameStart, now, false, interrupted,
                reason, currentLanguage, localPlayer,
                trueIgn, playerUuid, nick, isNicked, activeTimeAt(now),
                players, rounds, rankings, lastUnknownResidual);

        File statsDir = GameExporter.getStatsDir(gameDir);
        if (statsDir != null) {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(now));
            File file = new File(statsDir, "export_" + timestamp + ".json");
            GameExporter.writeJsonFile(file, json);
            return GameExporter.localFileNotice("Exported to ", gameDir, file);
        }
        return new ChatComponentText("Failed to export (no config directory).");
    }

    /** Manual raw packet export from /gtbs exportraw. */
    public IChatComponent manualExportRaw(File gameDir) {
        if (!trackingEnabled) {
            return new ChatComponentText(
                    "Stats tracking is off; raw packets are not being recorded.");
        }
        File statsDir = GameExporter.getStatsDir(gameDir);
        if (statsDir == null) {
            return new ChatComponentText(
                    "Failed to export raw packets (no config directory).");
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(System.currentTimeMillis()));
        File file = RawPacketRecorder.INSTANCE.writeSnapshot(statsDir, "raw_" + timestamp);
        if (file == null) return new ChatComponentText("No raw packet data to export.");
        return GameExporter.localFileNotice("Raw packets exported to ", gameDir, file);
    }

    public void writeLiveFile() {
        if (!trackingEnabled) return;
        File statsDir = runtimeStatsDir();
        if (statsDir != null) {
            long now = System.currentTimeMillis();
            captureTrueIgn();
            refreshIdentityMetadata();
            String json = GameExporter.toJson(gameStart, now, false, false,
                    null, currentLanguage, localPlayer,
                    trueIgn, playerUuid, nick, isNicked, activeTimeAt(now),
                    players, rounds, rankings, lastUnknownResidual);
            GameExporter.writeJsonFile(new File(statsDir, "current.json"), json);
        }
    }

    void setStatsDirOverrideForTest(File statsDir) {
        statsDirOverrideForTest = statsDir;
    }

    private File runtimeStatsDir() {
        return statsDirOverrideForTest != null ? statsDirOverrideForTest : GameExporter.getStatsDir();
    }

    boolean loadPendingCurrent(File statsDir, long now, boolean emitHooks) {
        if (!trackingEnabled) return false;
        JsonObject session = readRecoverableCurrent(statsDir);
        if (session == null) return false;
        if (!pendingSessionBelongsToCurrentAccount(session)) return false;

        restorePendingSession(session, now);
        if (emitHooks) {
            replayPendingSessionHooks();
        }
        return true;
    }

    private boolean pendingSessionBelongsToCurrentAccount(JsonObject session) {
        if (session.has("playerUuid")) {
            String recorded = PlayerUuid.normalize(stringValue(session, "playerUuid", null));
            return recorded != null && launchPlayerUuid != null
                    && recorded.equals(launchPlayerUuid);
        }
        String recordedTrueIgn = stringValue(session, "trueIgn", null);
        if (PlayerMetaCache.isValidMcUsername(recordedTrueIgn)
                && PlayerMetaCache.isValidMcUsername(trueIgn)) {
            return recordedTrueIgn.equalsIgnoreCase(trueIgn);
        }
        String recordedLocal = stringValue(session, "localPlayer", null);
        return PlayerMetaCache.isValidMcUsername(recordedLocal)
                && (recordedLocal.equalsIgnoreCase(trueIgn)
                || recordedLocal.equalsIgnoreCase(localPlayer)
                || recordedLocal.equalsIgnoreCase(lastLocalIgn));
    }

    boolean finalizePendingCurrent(File statsDir, String reason, long now) {
        if (!loadPendingCurrent(statsDir, now, false)) return false;
        endGame(false, reason, statsDir);
        return true;
    }

    private JsonObject readRecoverableCurrent(File statsDir) {
        if (statsDir == null) return null;
        File file = new File(statsDir, "current.json");
        if (!file.isFile()) return null;
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed == null || !parsed.isJsonObject()) return null;
            JsonObject session = parsed.getAsJsonObject();
            if (!isRecoverableCurrent(session)) return null;
            return session;
        } catch (Exception e) {
            GameExporter.LOGGER.warn("Failed to read recoverable current stats", e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) { /* best-effort */ }
            }
        }
    }

    private boolean isRecoverableCurrent(JsonObject session) {
        if (session == null) return false;
        if (boolValue(session, "completed", false)) return false;
        if (boolValue(session, "interrupted", false)) return false;
        JsonArray savedRounds = arrayValue(session, "rounds");
        return savedRounds != null && savedRounds.size() > 0;
    }

    private void restorePendingSession(JsonObject session, long now) {
        gameStart = longValue(session, "gameStart", now);
        activeTimeMs = longValue(session, "activeTimeMs", 0L);
        activeTimeStartedAt = 0L;
        captureTrueIgn();
        String recordedTrueIgn = stringValue(session, "trueIgn", null);
        if (!PlayerMetaCache.isValidMcUsername(trueIgn)
                && PlayerMetaCache.isValidMcUsername(recordedTrueIgn)) {
            trueIgn = recordedTrueIgn;
        }
        playerUuid = session.has("playerUuid")
                ? PlayerUuid.normalize(stringValue(session, "playerUuid", null))
                : launchPlayerUuid;
        localPlayer = stringValue(session, "localPlayer", null);
        String recordedNick = stringValue(session, "nick", null);
        boolean recordedIsNicked = boolValue(session, "isNicked", false);
        if (recordedIsNicked && PlayerMetaCache.isValidMcUsername(recordedNick)
                && recordedNick.equalsIgnoreCase(localPlayer)) {
            nick = recordedNick;
            isNicked = true;
        } else {
            refreshIdentityMetadata();
        }
        currentLanguage = stringValue(session, "language", null);
        currentLangCode = null;

        players.clear();
        for (String player : stringList(arrayValue(session, "players"))) {
            if (PlayerMetaCache.isValidMcUsername(player)
                    && !containsPlayerIgnoreCase(player)) {
                players.add(player);
            }
        }

        rounds.clear();
        JsonArray savedRounds = arrayValue(session, "rounds");
        if (savedRounds != null) {
            for (JsonElement element : savedRounds) {
                if (element != null && element.isJsonObject()) {
                    rounds.add(parseRound(element.getAsJsonObject()));
                }
            }
        }
        currentRound = rounds.isEmpty() ? null : rounds.get(rounds.size() - 1);
        if (currentRound != null) {
            currentLangCode = currentRound.langCode;
            if (currentLanguage == null) currentLanguage = currentRound.language;
        }
        if ((currentLangCode == null || currentLangCode.isEmpty()) && currentLanguage != null) {
            currentLangCode = LangPatterns.codeFromDisplayName(currentLanguage);
        }

        rankings.clear();
        JsonObject result = objectValue(session, "result");
        JsonArray savedRankings = result == null ? null : arrayValue(result, "rankings");
        if (savedRankings != null) {
            for (JsonElement element : savedRankings) {
                if (element == null || !element.isJsonObject()) continue;
                JsonObject entry = element.getAsJsonObject();
                String ign = stringValue(entry, "ign", null);
                rankings.add(new RankEntry(
                        intValue(entry, "place", 0),
                        ign,
                        intValue(entry, "score", 0)));
            }
        }

        lastUnknownResidual.clear();
        JsonObject residual = objectValue(session, "unknownResidual");
        if (residual != null) {
            for (Map.Entry<String, JsonElement> entry : residual.entrySet()) {
                JsonElement value = entry.getValue();
                if (value != null && !value.isJsonNull()) {
                    try {
                        lastUnknownResidual.put(entry.getKey(), value.getAsInt());
                    } catch (Exception ignored) { /* skip malformed value */ }
                }
            }
        }

        state = TrackingState.PAUSED;
        pauseTime = now;
        notInGameTicks = 0;
        gameInitialized = true;
        gameEnded = false;
        scheduledEndGameTime = 0L;
        clearAutoRequeueSchedule();
        pendingRejoinCheck = false;
        pausedBuilder = currentRound == null ? null : currentRound.builder;
        pausedRoundTotal = currentRound == null ? 0 : currentRound.totalRounds;
        pausedState = null;
        pausedNextRoundCandidate = 0;
        pausedRoundHadDataGapBeforeLeave = currentRound != null && currentRound.dataGap;
        if (pausedRoundTotal == 0 && rounds.size() >= 2) {
            pausedRoundTotal = rounds.get(rounds.size() - 2).totalRounds;
        }
        jaRoundLabelPending = false;
        lastHintText = "";
        clearPendingSelfGuessScore();
        lastBuilderHookRound = null;
        lastBuilderHookIgn = null;
        languageExplicitlySet = false;
        languageObservedThisGame = currentLangCode != null && !currentLangCode.isEmpty();
        currentPhysicalTimerSeconds = null;
        resetTrackedPhysicalTimerComparator();
        midGameUnattributedResidual.clear();
        roundsMissedDuringDisconnect.clear();
        localRejoinedImmediatelyStage = false;
        lastActivityMs.clear();
        leavers.clear();
        builderLeftLeavers.clear();
        pendingBuilderTabReturns.clear();
        resetSidebarTrackingBaseline(lastSidebarScores, sidebarDropMissingTicks,
                sidebarDropMissingScores);
        sidebarDropKnownScores.clear();
        sidebarTrackingRound = null;
        lastLocalIgn = localPlayer;
        PlayerMetaCache.INSTANCE.clearRosterEvidence();
        ensurePendingDisconnectEvent(now);
        RawPacketRecorder.INSTANCE.startSession(gameStart > 0L ? gameStart : now);
    }

    private void ensurePendingDisconnectEvent(long now) {
        if (currentRound == null) return;
        currentRound.dataGap = true;
        for (GameEvent event : currentRound.events) {
            if (event == null || event.type == null) continue;
            if ("disconnect".equals(event.type)
                    && event.t >= currentRound.roundStart) {
                return;
            }
        }
        currentRound.events.add(new GameEvent(now, "disconnect", "pending_session"));
    }

    private void replayPendingSessionHooks() {
        if (gtbHooks == null) return;
        gtbHooks.onGameStart(new ArrayList<String>(players), localPlayer);
        if (currentRound != null && currentRound.roundNumber > 0) {
            int total = currentRound.totalRounds > 0
                    ? currentRound.totalRounds
                    : Math.max(players.size(), currentRound.roundNumber);
            gtbHooks.onRoundStart(currentRound.roundNumber, total);
            if (currentRound.builder != null && !currentRound.builder.isEmpty()) {
                gtbHooks.onBuilderChange(null, currentRound.builder);
            }
            if (currentRound.theme != null && !currentRound.theme.isEmpty()) {
                gtbHooks.onThemeUpdate(currentRound.theme, currentRound.langCode);
            }
        }
        gtbHooks.onUserLeave();
    }

    private RoundData parseRound(JsonObject object) {
        RoundData round = new RoundData();
        round.roundNumber = roundNumValue(object, "round", 0);
        round.totalRounds = roundNumValue(object, "totalRounds", 0);
        round.roundStart = longValue(object, "roundStart", 0L);
        round.roundEnd = longValue(object, "roundEnd", 0L);
        round.builder = stringValue(object, "builder", null);
        round.theme = stringValue(object, "theme", null);
        round.themeCharCount = intValue(object, "themeCharCount", 0);
        round.language = stringValue(object, "language", null);
        round.langCode = stringValue(object, "languageCode", null);
        round.dataGap = boolValue(object, "dataGap", false);
        List<String> options = stringList(arrayValue(object, "builderThemeOptions"));
        if (!options.isEmpty()) {
            round.builderThemeOptions = options.toArray(new String[options.size()]);
        }

        JsonArray guesses = arrayValue(object, "guesses");
        if (guesses != null) {
            for (JsonElement element : guesses) {
                if (element == null || !element.isJsonObject()) continue;
                JsonObject guessObject = element.getAsJsonObject();
                GuessEntry guess = new GuessEntry(
                        longValue(guessObject, "t", 0L),
                        stringValue(guessObject, "ign", null),
                        stringValue(guessObject, "text", null));
                guess.physicalTimer = nullableIntValue(guessObject, "physicalTimer");
                round.guesses.add(guess);
            }
        }

        JsonArray correctGuesses = arrayValue(object, "correctGuesses");
        if (correctGuesses != null) {
            for (JsonElement element : correctGuesses) {
                if (element == null || !element.isJsonObject()) continue;
                JsonObject correctObject = element.getAsJsonObject();
                CorrectGuessEntry correct = new CorrectGuessEntry(
                        longValue(correctObject, "t", 0L),
                        stringValue(correctObject, "ign", null));
                correct.physicalTimer = nullableIntValue(correctObject, "physicalTimer");
                round.correctGuesses.add(correct);
            }
        }

        JsonObject hints = objectValue(object, "hints");
        if (hints != null) {
            round.hintStructure = stringValue(hints, "structure", null);
            JsonArray items = arrayValue(hints, "items");
            if (items != null) {
                for (JsonElement element : items) {
                    if (element == null || !element.isJsonObject()) continue;
                    JsonObject hintObject = element.getAsJsonObject();
                    HintEntry hint = new HintEntry(
                            longValue(hintObject, "t", 0L),
                            stringValue(hintObject, "text", null));
                    hint.physicalTimer = nullableIntValue(hintObject, "physicalTimer");
                    round.hints.add(hint);
                }
            }
        }

        JsonArray handheldItems = arrayValue(object, "handheldItems");
        if (handheldItems != null) {
            for (JsonElement element : handheldItems) {
                if (element != null && element.isJsonObject()) {
                    round.handheldItems.add(parseHandheldItem(element.getAsJsonObject()));
                }
            }
        }

        JsonArray events = arrayValue(object, "events");
        if (events != null) {
            for (JsonElement element : events) {
                if (element == null || !element.isJsonObject()) continue;
                JsonObject eventObject = element.getAsJsonObject();
                GameEvent event = new GameEvent(
                        longValue(eventObject, "t", 0L),
                        stringValue(eventObject, "type", null),
                        stringValue(eventObject, "detail", null));
                event.physicalTimer = nullableIntValue(eventObject, "physicalTimer");
                event.physicalTimerBefore =
                        nullableIntValue(eventObject, "physicalTimerBefore");
                event.physicalTimerAfter =
                        nullableIntValue(eventObject, "physicalTimerAfter");
                round.events.add(event);
            }
        }

        JsonObject deltas = objectValue(object, "scoreDeltas");
        if (deltas != null) {
            for (Map.Entry<String, JsonElement> entry : deltas.entrySet()) {
                JsonElement value = entry.getValue();
                if (value != null && value.isJsonObject()) {
                    round.scoreDeltas.put(entry.getKey(),
                            parseScoreDelta(value.getAsJsonObject()));
                }
            }
        }
        return round;
    }

    private HandheldItemEntry parseHandheldItem(JsonObject object) {
        HandheldItemEntry item = new HandheldItemEntry(stringValue(object, "key", ""));
        item.name = stringValue(object, "name", null);
        item.registryName = stringValue(object, "registryName", null);
        item.numericId = intValue(object, "numericId", 0);
        item.metadata = intValue(object, "metadata", 0);
        item.stackSize = intValue(object, "stackSize", 0);
        item.unlocalizedName = stringValue(object, "unlocalizedName", null);
        item.modernKind = stringValue(object, "modernKind", null);
        item.modernName = stringValue(object, "modernName", null);
        item.nbt = stringValue(object, "nbt", null);
        item.firstSeen = longValue(object, "firstSeen", 0L);
        item.lastSeen = longValue(object, "lastSeen", 0L);
        JsonArray intervals = arrayValue(object, "intervals");
        if (intervals != null) {
            for (JsonElement element : intervals) {
                if (element == null || !element.isJsonObject()) continue;
                JsonObject intervalObject = element.getAsJsonObject();
                HandheldItemInterval interval = new HandheldItemInterval(
                        longValue(intervalObject, "start", 0L));
                interval.end = longValue(intervalObject, "end", interval.start);
                interval.startPhysicalTimer =
                        nullableIntValue(intervalObject, "startPhysicalTimer");
                interval.endPhysicalTimer =
                        nullableIntValue(intervalObject, "endPhysicalTimer");
                item.intervals.add(interval);
            }
        }
        return item;
    }

    private ScoreDelta parseScoreDelta(JsonObject object) {
        String source = stringValue(object, "source", "restored");
        Integer value = nullableIntValue(object, "value");
        if (value != null) return ScoreDelta.definite(value.intValue(), source);
        JsonArray forkArray = arrayValue(object, "fork");
        Set<Integer> fork = new TreeSet<Integer>();
        if (forkArray != null) {
            for (JsonElement element : forkArray) {
                if (element == null || element.isJsonNull()) continue;
                try {
                    fork.add(Integer.valueOf(element.getAsInt()));
                } catch (Exception ignored) { /* skip malformed option */ }
            }
        }
        return ScoreDelta.fork(fork, source);
    }

    private static JsonElement member(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key)) return null;
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element;
    }

    private static JsonObject objectValue(JsonObject object, String key) {
        JsonElement element = member(object, key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray arrayValue(JsonObject object, String key) {
        JsonElement element = member(object, key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        JsonElement element = member(object, key);
        if (element == null) return fallback;
        try {
            return element.getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        JsonElement element = member(object, key);
        if (element == null) return fallback;
        try {
            return element.getAsLong();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement element = member(object, key);
        if (element == null) return fallback;
        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Integer nullableIntValue(JsonObject object, String key) {
        JsonElement element = member(object, key);
        if (element == null) return null;
        try {
            return Integer.valueOf(element.getAsInt());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean boolValue(JsonObject object, String key, boolean fallback) {
        JsonElement element = member(object, key);
        if (element == null) return fallback;
        try {
            return element.getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int roundNumValue(JsonObject object, String key, int fallback) {
        JsonElement element = member(object, key);
        if (element == null) return fallback;
        try {
            if (element.isJsonPrimitive()) {
                String text = element.getAsString();
                if (text != null && text.matches("\\d+")) {
                    return Integer.parseInt(text);
                }
            }
        } catch (Exception ignored) { /* fall through */ }
        return fallback;
    }

    private static List<String> stringList(JsonArray array) {
        List<String> out = new ArrayList<String>();
        if (array == null) return out;
        for (JsonElement element : array) {
            if (element == null || element.isJsonNull()) continue;
            try {
                String value = element.getAsString();
                if (value != null) out.add(value);
            } catch (Exception ignored) { /* skip malformed value */ }
        }
        return out;
    }

    private void startActiveTime(long now) {
        if (activeTimeStartedAt == 0L) activeTimeStartedAt = now;
    }

    private void stopActiveTime(long now) {
        if (activeTimeStartedAt == 0L) return;
        activeTimeMs += Math.max(0L, now - activeTimeStartedAt);
        activeTimeStartedAt = 0L;
    }

    private long activeTimeAt(long now) {
        return activeTimeMs + (activeTimeStartedAt == 0L
                ? 0L : Math.max(0L, now - activeTimeStartedAt));
    }

    /** Get current status string for /gtbs stats */
    public String getStatusSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("State: ").append(state);

        if (state == TrackingState.LOBBY) {
            return sb.toString();
        }

        if (currentLanguage != null) {
            sb.append(" | Lang: ").append(LangPatterns.mapLanguageName(currentLanguage));
            if (!languageObservedThisGame) sb.append(" (unverified)");
        }

        // Show round details in ROUND_PRE, ROUND_BUILD, ROUND_END, or PAUSED
        if ((state == TrackingState.ROUND_PRE || state == TrackingState.ROUND_BUILD
                || state == TrackingState.ROUND_END || state == TrackingState.PAUSED)
                && currentRound != null) {
            int rn = currentRound.roundNumber;
            int rt = currentRound.totalRounds;
            if (rn == 0 && rounds.size() >= 2) {
                RoundData prev = rounds.get(rounds.size() - 2);
                rn = prev.roundNumber + 1;
                rt = prev.totalRounds;
            }
            String rnStr = (rn == -1 || rn == 0) ? "?" : String.valueOf(rn);
            String rtStr = (rt == 0) ? "?" : String.valueOf(rt);
            sb.append(" | Round: ").append(rnStr).append("/").append(rtStr);
            if (currentRound.builder != null) {
                sb.append(" | Builder: ").append(currentRound.builder);
            }
            // Guess counts only meaningful once BUILD/END/PAUSED
            if (state != TrackingState.ROUND_PRE) {
                sb.append(" | Guesses: ").append(currentRound.guesses.size());
                sb.append(" | Correct: ").append(currentRound.correctGuesses.size());
            }
        }
        return sb.toString();
    }
}
