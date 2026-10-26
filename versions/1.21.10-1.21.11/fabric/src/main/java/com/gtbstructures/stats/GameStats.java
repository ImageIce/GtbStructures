package com.gtbstructures.stats;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.sound.GuessSoundController;
import com.gtbstructures.sound.EndGameFireworkController;
import com.gtbstructures.sound.MinecraftClientSoundBridge;
import com.gtbstructures.hints.HintStructureParser;
import com.gtbstructures.hints.ShowAllMatching;
import com.gtbstructures.chat.ModernChatCooldownCounter;
import com.gtbstructures.chat.AcceptedGuessTracker;
import com.gtbstructures.player.PlayerMetaCache;
import com.gtbstructures.diagnostics.RawPacketRecorder;
import com.gtbstructures.scoreboard.GameTracker;
import com.gtbstructures.scoreboard.GtbGame;
import com.gtbstructures.translation.ShortcutReminder;
import com.gtbstructures.translation.ShortcutLanguageGroups;
import com.gtbstructures.translation.ThemePointReveal;
import com.gtbstructures.translation.TranslationData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform-neutral stats data model used by the GTBS JSON exporter.
 * Live collection hooks are layered on top of this model per loader.
 */
public class GameStats {
    private static final Logger LOGGER = Logger.getLogger("GtbStructures");
    private static final Pattern LOBBY_COUNT_PATTERN = Pattern.compile("\\((\\d+)/(\\d+)\\)");
    private static final Pattern DATE_FOOTER_PATTERN = Pattern.compile("\\d{2}/\\d{2}/\\d{2}.*");
    // Reject phase clocks before the score parser can derive a numeric ghost IGN.
    private static final Pattern SIDEBAR_CLOCK_TOKEN_PATTERN =
            Pattern.compile(".*\\d{1,2}:\\d{2}.*");
    private static final Pattern RANK_LINE_PATTERN = Pattern.compile("^(\\d+)\\.\\s+(.+)$");
    private static final Pattern RANK_SCORE_PATTERN = Pattern.compile("^(.*?)[\\s:\\-]+(\\d+)$");
    private static final long END_GAME_DELAY_MS = 1500L;
    private static final long END_GAME_RANKINGS_WAIT_MS = 10000L;
    private static final long SELF_GUESS_SCORE_WINDOW_MS = 2500L;
    private static final int PAUSE_GRACE_TICKS = 1;
    private static final int AUTHORITATIVE_LOW_ROSTER_STABLE_TICKS = 4;
    private static final int SIDEBAR_DROP_REQUIRED_MISSING_TICKS = 4;
    private static final int SIDEBAR_DROP_REQUIRED_VISIBLE_ROWS = 3;

    public static final GameStats INSTANCE = new GameStats();

    private boolean trackingEnabled = true;
    private boolean autoRequeue;
    private float autoRequeueDelaySeconds = GtbStructuresSettings.DEFAULT_AUTO_REQUEUE_DELAY_SECONDS;
    private long firstFinalRosterCaptureTime;
    private long requeueTime;
    private TrackingState state = TrackingState.NOT_IN_GAME;
    private long gameStart;
    private long pauseTime;
    private long activeTimeMs;
    private long activeTimeStartedAt;
    private long scheduledEndGameTime;
    private String localPlayer;
    private String trueIgn;
    private String launchPlayerUuid;
    private String playerUuid;
    private String nick;
    private boolean isNicked;
    private String lastLocalIgn;
    private String observedLocalIgn;
    private String serverConfirmedLocalIgn;
    String currentLanguage;
    String currentLangCode;
    List<String> players = new ArrayList<String>();
    List<RoundData> rounds = new ArrayList<RoundData>();
    RoundData currentRound;
    List<RankEntry> rankings = new ArrayList<RankEntry>();
    Map<String, Integer> lastUnknownResidual = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> midGameUnattributedResidual = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> lastSidebarScores = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> sidebarDropKnownScores = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> sidebarDropMissingTicks = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> sidebarDropMissingScores = new LinkedHashMap<String, Integer>();
    private final Set<String> previousScoreboardOnlyPlayers = new LinkedHashSet<String>();
    private File runtimeGameDir;
    private File statsDirOverrideForTest;
    private GtbHooks gtbHooks;
    private String lastHintText;
    private RoundData lastBuilderHookRound;
    private String lastBuilderHookIgn;
    private boolean gameInitialized;
    private boolean gameEnded;
    private boolean jaRoundLabelPending;
    private boolean trollAutoDisableJapaneseRoundPending;
    private String pendingNextBuilder;
    private boolean pendingRejoinCheck;
    private String pausedBuilder;
    private int pausedRoundTotal;
    private TrackingState pausedState;
    private int pausedNextRoundCandidate;
    private boolean pausedRoundHadDataGapBeforeLeave;
    private boolean pausedLeaveCommitted;
    private boolean serverSessionBoundaryPending;
    private boolean localRejoinedImmediatelyStage;
    private static final long GTB_HANDOFF_ROSTER_GRACE_MS = 3000L;
    private long gtbHandoffRosterUntilMs;
    private InitialRoundHeader pendingLobbyRoundStart;
    private boolean gtbLobbyRosterConfirmed;
    private boolean lobbyTabDone;
    private TrackingState lastScoreboardState = TrackingState.NOT_IN_GAME;
    private int notInGameTicks;
    private final List<String> handoffLobbyRoster = new ArrayList<String>();
    private final Set<String> lobbyPacketRemovals = new LinkedHashSet<String>();
    private int lobbyAnnouncedPlayerCount;
    private final Set<String> lobbyQuitSuppress = new LinkedHashSet<String>();
    private final Set<String> leavers = new LinkedHashSet<String>();
    private final Set<String> builderLeftLeavers = new LinkedHashSet<String>();
    private final Set<String> authoritativeLowRosterLeavers = new LinkedHashSet<String>();
    private final Set<String> pendingTabReturnConfirmations = new LinkedHashSet<String>();
    private final Set<String> lowRosterCandidatePlayers = new LinkedHashSet<String>();
    private int lowRosterCandidateTicks;
    private final Queue<String> notificationMessages = new ConcurrentLinkedQueue<String>();
    private String lastSidebarTimer;
    private String lastSidebarTheme;
    private Integer currentPhysicalTimerSeconds;
    private Integer lastTrackedPhysicalTimerSeconds;
    private TrackingState lastTrackedPhysicalTimerState;
    private RoundData lastTrackedPhysicalTimerRound;
    private RoundData sidebarTrackingRound;
    private String pendingSelfGuessScoreIgn;
    private int pendingSelfGuessScoreRound;
    private long pendingSelfGuessScoreUntilMs;
    private final AcceptedGuessTracker acceptedGuessTracker = new AcceptedGuessTracker();
    private final LobbyLanguageFallback lobbyLanguageFallback = new LobbyLanguageFallback();

    private static final class InitialRoundHeader {
        final int totalRounds;
        final long timestamp;

        InitialRoundHeader(int totalRounds, long timestamp) {
            this.totalRounds = totalRounds;
            this.timestamp = timestamp;
        }
    }

    public enum TrackingState {
        NOT_IN_GAME,
        LOBBY,
        ROUND_PRE,
        ROUND_BUILD,
        ROUND_END,
        POST_GAME,
        PAUSED
    }

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
        default void onAuthoritativeLowRosterSnapshot(Set<String> presentPlayers) {}
        default void onConfirmedPlayerLeft(String ign) {}
        void onUserLeave();
        void onUserRejoin();
        void onUserRejoinSameRound(String builder);
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

    public static boolean isActiveGame(TrackingState state) {
        return state == TrackingState.LOBBY
                || state == TrackingState.ROUND_PRE
                || state == TrackingState.ROUND_BUILD
                || state == TrackingState.ROUND_END
                || state == TrackingState.POST_GAME;
    }

    public static boolean isBbRankDisplayRepairState(TrackingState state) {
        return state == TrackingState.ROUND_PRE
                || state == TrackingState.ROUND_BUILD
                || state == TrackingState.ROUND_END
                || state == TrackingState.POST_GAME;
    }

    public TrackingState getState() {
        return state;
    }

    public RoundData getCurrentRound() {
        return currentRound;
    }

    public boolean hasLocalPlayerCorrectlyGuessedCurrentRound() {
        if (currentRound == null || currentRound.correctGuesses == null
                || bestKnownLocalIgn() == null) {
            return false;
        }
        for (CorrectGuessEntry guess : currentRound.correctGuesses) {
            if (guess != null && isLocalPlayerIgn(guess.ign)) {
                return true;
            }
        }
        return false;
    }

    public String getCurrentBuilder() {
        return currentRound == null ? null : currentRound.builder;
    }

    public List<RoundData> getRounds() {
        return rounds;
    }

    public List<RankEntry> getRankings() {
        return rankings;
    }

    public String getCurrentLangCode() {
        return currentLangCode;
    }

    public Map<String, Integer> getLastUnknownResidual() {
        return lastUnknownResidual;
    }

    public boolean isInActiveRound() {
        return isInRound(state);
    }

    public boolean shouldRenderRoundOverlays() {
        return shouldRenderRoundOverlays(state, currentRound);
    }

    static boolean shouldRenderRoundOverlays(TrackingState state, RoundData round) {
        return isInRound(state);
    }

    public boolean shouldRepairBbRankDisplay() {
        return isBbRankDisplayRepairState(state);
    }

    public String getCurrentTimerText() {
        return lastSidebarTimer;
    }

    public String getCurrentSidebarTheme() {
        return lastSidebarTheme;
    }

    public int getMidGameResidual(String ign) {
        Integer value = midGameUnattributedResidual.get(ign);
        return value == null ? 0 : value.intValue();
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
        Set<String> confirmed = new LinkedHashSet<String>();
        if (currentSidebar == null || missingTicks == null || missingScores == null
                || requiredMissingTicks <= 0) {
            return confirmed;
        }

        int visibleRows = currentSidebar.size();
        int minRows = Math.max(1, requiredVisibleRows);
        boolean lowVisibleFullRoster = visibleRows < minRows;
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

        Integer currentFloor = lowVisibleFullRoster
                ? null : visibleLeaderboardCutoffScore(currentSidebar, minRows);
        for (String missing : new ArrayList<String>(missingTicks.keySet())) {
            if (findKeyIgnoreCase(currentSidebar, missing) != null) {
                missingTicks.remove(missing);
                removeKeyIgnoreCase(missingScores, missing);
                continue;
            }
            Integer missingScore = scoreIgnoreCase(missingScores, missing);
            boolean confirmedByFullLowRoster = missingScore != null && lowVisibleFullRoster;
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
                    || findKeyIgnoreCase(currentSidebar, ign) != null
                    || findKeyIgnoreCase(missingTicks, ign) != null
                    || score.intValue() <= cutoff.intValue()) {
                continue;
            }
            missingTicks.put(ign, Integer.valueOf(0));
            missingScores.put(ign, score);
        }
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

    private static <V> String findKeyIgnoreCase(Map<String, V> map, String key) {
        if (map == null || key == null) return null;
        for (String existing : map.keySet()) {
            if (existing != null && existing.equalsIgnoreCase(key)) return existing;
        }
        return null;
    }

    private static <V> void renameMapKey(Map<String, V> map, String oldKey, String newKey) {
        String matched = findKeyIgnoreCase(map, oldKey);
        if (matched == null) return;
        V value = map.remove(matched);
        map.put(newKey, value);
    }

    public boolean isAutoRequeue() {
        return autoRequeue;
    }

    public void onServerSessionReset() {
        acceptedGuessTracker.reset();
        GuessSoundController.INSTANCE.onDisconnect();
        ModernChatCooldownCounter.onDisconnect();
        cancelPendingAutoRequeue("server session reset");
        lobbyLanguageFallback.resetForServerSession();
        pendingTabReturnConfirmations.clear();
        serverConfirmedLocalIgn = null;
        clearPendingSelfGuessScore();
        long now = System.currentTimeMillis();
        if (state == TrackingState.PAUSED) {
            commitPausedLeave(now);
        } else {
            serverSessionBoundaryPending = state == TrackingState.ROUND_PRE
                    || state == TrackingState.ROUND_BUILD
                    || state == TrackingState.ROUND_END;
        }
    }

    public boolean isTrackingEnabled() {
        return trackingEnabled;
    }

    public void setTrackingEnabled(boolean enabled) {
        this.trackingEnabled = enabled;
        if (!enabled) acceptedGuessTracker.reset();
        if (!enabled) {
            activeTimeMs = activeTimeAt(System.currentTimeMillis());
            activeTimeStartedAt = 0L;
            RawPacketRecorder.INSTANCE.discardSession();
        } else if (GameStats.isActiveGame(state) && activeTimeStartedAt == 0L) {
            activeTimeStartedAt = System.currentTimeMillis();
        }
    }

    public void setAutoRequeue(boolean enabled) {
        this.autoRequeue = enabled;
        if (enabled) {
            rescheduleAutoRequeueFromFirstRoster();
        } else {
            clearAutoRequeueSchedule();
        }
    }

    public float getAutoRequeueDelaySeconds() {
        return autoRequeueDelaySeconds;
    }

    public void setAutoRequeueDelaySeconds(float seconds) {
        autoRequeueDelaySeconds = GtbStructuresSettings.normalizeAutoRequeueDelaySeconds(seconds);
        rescheduleAutoRequeueFromFirstRoster();
    }

    public void onOutboundServerCommand(String command) {
        if ((requeueTime <= 0L && firstFinalRosterCaptureTime <= 0L) || command == null) return;
        String normalized = command.trim();
        if (normalized.startsWith("/")) normalized = normalized.substring(1).trim();
        int separator = normalized.indexOf(' ');
        String root = (separator < 0 ? normalized : normalized.substring(0, separator))
                .toLowerCase(Locale.ROOT);
        if (isManualNavigationCommand(root)) {
            cancelPendingAutoRequeue("outbound /" + root + " command");
        }
    }

    private static boolean isManualNavigationCommand(String root) {
        return "play".equals(root)
                || "lobby".equals(root)
                || "hub".equals(root)
                || "leave".equals(root)
                || "l".equals(root)
                || "rejoin".equals(root)
                || "rej".equals(root)
                || "server".equals(root);
    }

    private void cancelPendingAutoRequeue(String reason) {
        boolean pending = requeueTime > 0L || firstFinalRosterCaptureTime > 0L;
        clearAutoRequeueSchedule();
        if (pending) LOGGER.info("Auto-requeue cancelled: " + reason);
    }

    private void clearAutoRequeueSchedule() {
        firstFinalRosterCaptureTime = 0L;
        requeueTime = 0L;
    }

    private void rescheduleAutoRequeueFromFirstRoster() {
        if (!autoRequeue || firstFinalRosterCaptureTime <= 0L) {
            requeueTime = 0L;
            return;
        }
        requeueTime = firstFinalRosterCaptureTime
                + Math.round(autoRequeueDelaySeconds * 1000F);
    }

    void scheduleAutoRequeue(long rosterCapturedAt, boolean completed, boolean hadFinalRankings) {
        if (!completed || !autoRequeue || !hadFinalRankings) return;
        if (firstFinalRosterCaptureTime <= 0L) {
            firstFinalRosterCaptureTime = rosterCapturedAt;
            rescheduleAutoRequeueFromFirstRoster();
        }
    }

    boolean consumeAutoRequeueIfReady(long now, TrackingState scoreboardState) {
        if (requeueTime <= 0L) return false;
        if (!autoRequeue || scoreboardState != TrackingState.POST_GAME) {
            clearAutoRequeueSchedule();
            LOGGER.info("Auto-requeue skipped: enabled=" + autoRequeue
                    + " scoreboardState=" + scoreboardState);
            return false;
        }
        if (now < requeueTime) return false;
        if (!gameEnded) {
            scheduledEndGameTime = 0L;
            endGame(true, null, now);
        }
        clearAutoRequeueSchedule();
        return true;
    }

    public boolean consumeAutoRequeueIfReady(long now) {
        return consumeAutoRequeueIfReady(now, lastScoreboardState);
    }

    boolean loadPendingCurrent(File statsDir, long now, boolean emitHooks) {
        JsonObject session = readRecoverableCurrent(statsDir);
        if (session == null || !pendingSessionBelongsToCurrentAccount(session)) return false;

        restorePendingSession(session, now);
        return true;
    }

    boolean finalizePendingCurrent(File statsDir, String reason, long now) {
        if (!loadPendingCurrent(statsDir, now, false)) return false;
        endPendingGame(statsDir, reason, now);
        return true;
    }

    private void endPendingGame(File statsDir, String reason, long now) {
        if (statsDir != null && !rounds.isEmpty()) {
            refreshIdentityMetadata();
            String json = GameExporter.toJson(gameStart, now, false, true,
                    reason, currentLanguage, localPlayer,
                    trueIgn, playerUuid, nick, isNicked, activeTimeAt(now),
                    players, rounds, rankings, lastUnknownResidual);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(now));
            GameExporter.writeJsonFile(new File(statsDir, "interrupted_" + timestamp + ".json"), json);
            GameExporter.writeJsonFile(new File(statsDir, "current.json"), json);
        }

        state = TrackingState.NOT_IN_GAME;
        pauseTime = 0L;
        notInGameTicks = 0;
        activeTimeMs = 0L;
        activeTimeStartedAt = 0L;
        String retainedLocalIgn = bestKnownLocalIgn();
        localPlayer = null;
        refreshIdentityMetadata();
        lastLocalIgn = isValidMcUsername(retainedLocalIgn) ? retainedLocalIgn : null;
        players.clear();
        rankings.clear();
        currentRound = null;
        clearPendingSelfGuessScore();
        resetPhysicalTimerTracking();
        clearAutoRequeueSchedule();
    }

    private JsonObject readRecoverableCurrent(File statsDir) {
        if (statsDir == null) return null;
        File file = new File(statsDir, "current.json");
        if (!file.isFile()) return null;
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) return null;
            JsonObject session = parsed.getAsJsonObject();
            if (!isRecoverableCurrent(session)) return null;
            return session;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read recoverable current stats", e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                    // Best-effort close.
                }
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

    private boolean pendingSessionBelongsToCurrentAccount(JsonObject session) {
        if (session.has("playerUuid")) {
            String recorded = PlayerUuid.normalize(stringValue(session, "playerUuid", null));
            return recorded != null && launchPlayerUuid != null
                    && recorded.equals(launchPlayerUuid);
        }
        String recordedTrueIgn = stringValue(session, "trueIgn", null);
        if (isValidMcUsername(recordedTrueIgn) && isValidMcUsername(trueIgn)) {
            return recordedTrueIgn.equalsIgnoreCase(trueIgn);
        }
        String recordedLocal = stringValue(session, "localPlayer", null);
        return isValidMcUsername(recordedLocal)
                && (recordedLocal.equalsIgnoreCase(trueIgn)
                || recordedLocal.equalsIgnoreCase(localPlayer)
                || recordedLocal.equalsIgnoreCase(lastLocalIgn));
    }

    private void restorePendingSession(JsonObject session, long now) {
        clearAutoRequeueSchedule();
        gameStart = longValue(session, "gameStart", now);
        activeTimeMs = longValue(session, "activeTimeMs", 0L);
        activeTimeStartedAt = 0L;
        String recordedTrueIgn = stringValue(session, "trueIgn", null);
        if (!isValidMcUsername(trueIgn) && isValidMcUsername(recordedTrueIgn)) {
            trueIgn = recordedTrueIgn;
        }
        playerUuid = session.has("playerUuid")
                ? PlayerUuid.normalize(stringValue(session, "playerUuid", null))
                : launchPlayerUuid;
        localPlayer = stringValue(session, "localPlayer", null);
        if (isValidMcUsername(localPlayer)) lastLocalIgn = localPlayer;
        String recordedNick = stringValue(session, "nick", null);
        boolean recordedIsNicked = boolValue(session, "isNicked", false);
        if (recordedIsNicked && isValidMcUsername(recordedNick)
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
            if (isValidMcUsername(player) && !containsPlayerIgnoreCase(player)) {
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
                rankings.add(new RankEntry(
                        intValue(entry, "place", 0),
                        stringValue(entry, "ign", null),
                        intValue(entry, "score", 0)));
            }
        }

        lastUnknownResidual.clear();
        JsonObject residual = objectValue(session, "unknownResidual");
        if (residual != null) {
            for (Map.Entry<String, JsonElement> entry : residual.entrySet()) {
                JsonElement value = entry.getValue();
                if (value == null || value.isJsonNull()) continue;
                try {
                    lastUnknownResidual.put(entry.getKey(), Integer.valueOf(value.getAsInt()));
                } catch (Exception ignored) {
                    // Skip malformed residual values.
                }
            }
        }

        state = TrackingState.PAUSED;
        pauseTime = now;
        notInGameTicks = 0;
        pausedBuilder = currentRound == null ? null : currentRound.builder;
        pausedRoundTotal = currentRound == null ? 0 : currentRound.totalRounds;
        pausedState = TrackingState.ROUND_BUILD;
        pausedRoundHadDataGapBeforeLeave = currentRound != null && currentRound.dataGap;
        pausedLeaveCommitted = true;
        serverSessionBoundaryPending = false;
        ensurePendingDisconnectEvent(now);
    }

    private void ensurePendingDisconnectEvent(long now) {
        if (currentRound == null) return;
        currentRound.dataGap = true;
        for (GameEvent event : currentRound.events) {
            if (event == null || event.type == null) continue;
            if ("disconnect".equals(event.type) && event.t >= currentRound.roundStart) {
                return;
            }
        }
        currentRound.events.add(new GameEvent(now, "disconnect", "pending_session"));
    }

    private RoundData parseRound(JsonObject object) {
        RoundData round = new RoundData();
        round.roundNumber = roundNumValue(object, "round", 0);
        round.totalRounds = roundNumValue(object, "totalRounds", 0);
        round.roundStart = longValue(object, "roundStart", 0L);
        round.roundEnd = longValue(object, "roundEnd", 0L);
        round.builder = stringValue(object, "builder", null);
        round.theme = stringValue(object, "theme", null);
        round.acceptedGuessTexts.addAll(stringList(arrayValue(object, "acceptedGuessTexts")));
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
                event.physicalTimerBefore = nullableIntValue(eventObject, "physicalTimerBefore");
                event.physicalTimerAfter = nullableIntValue(eventObject, "physicalTimerAfter");
                round.events.add(event);
            }
        }

        JsonObject deltas = objectValue(object, "scoreDeltas");
        if (deltas != null) {
            for (Map.Entry<String, JsonElement> entry : deltas.entrySet()) {
                JsonElement value = entry.getValue();
                if (value != null && value.isJsonObject()) {
                    round.scoreDeltas.put(entry.getKey(), parseScoreDelta(value.getAsJsonObject()));
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
                interval.startPhysicalTimer = nullableIntValue(intervalObject, "startPhysicalTimer");
                interval.endPhysicalTimer = nullableIntValue(intervalObject, "endPhysicalTimer");
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
                } catch (Exception ignored) {
                    // Skip malformed fork options.
                }
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
        } catch (Exception ignored) {
            // Fall through to fallback.
        }
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
            } catch (Exception ignored) {
                // Skip malformed values.
            }
        }
        return out;
    }

    private long activeTimeAt(long now) {
        return activeTimeMs + (activeTimeStartedAt == 0L
                ? 0L : Math.max(0L, now - activeTimeStartedAt));
    }

    private boolean containsPlayerIgnoreCase(String ign) {
        if (ign == null) return false;
        for (String player : players) {
            if (player != null && player.equalsIgnoreCase(ign)) return true;
        }
        return false;
    }

    public List<String> getPlayers() {
        return players;
    }

    public void setGtbHooks(GtbHooks hooks) {
        this.gtbHooks = hooks;
    }

    public void setRuntimeGameDir(File gameDir) {
        runtimeGameDir = gameDir;
    }

    public void setLocalPlayerName(String ign) {
        String clean = cleanIgn(ign);
        if (!isValidMcUsername(clean)) return;
        observedLocalIgn = clean;
        if (isValidMcUsername(serverConfirmedLocalIgn)
                && isValidMcUsername(trueIgn)
                && clean.equalsIgnoreCase(trueIgn)
                && !serverConfirmedLocalIgn.equalsIgnoreCase(trueIgn)) {
            return;
        }
        if (!isValidMcUsername(lastLocalIgn)
                || state == TrackingState.NOT_IN_GAME
                || state == TrackingState.LOBBY) {
            if (state == TrackingState.LOBBY
                    && isValidMcUsername(localPlayer)
                    && !localPlayer.equalsIgnoreCase(clean)) {
                replaceRosterIdentityIfPresent(players, localPlayer, clean);
                replaceRosterIdentityIfPresent(handoffLobbyRoster, localPlayer, clean);
                PlayerMetaCache.INSTANCE.transferIdentity(localPlayer, clean);
            }
            setLocalPlayerIdentity(clean);
        }
    }

    public String canonicalRosterIgn(String ign) {
        String clean = cleanIgn(ign);
        if (!isValidMcUsername(clean)) return clean;
        if (isValidMcUsername(trueIgn)
                && isValidMcUsername(localPlayer)
                && !trueIgn.equalsIgnoreCase(localPlayer)
                && trueIgn.equalsIgnoreCase(clean)) {
            return localPlayer;
        }
        return clean;
    }

    public void setAccountPlayerName(String ign) {
        if (samePlainIdentity(trueIgn, ign)) return;
        String clean = cleanIgn(ign);
        if (isValidMcUsername(clean)) {
            trueIgn = clean;
            refreshIdentityMetadata();
        }
    }

    public void setAccountPlayerUuid(String uuid) {
        String normalized = PlayerUuid.normalize(uuid);
        if (normalized != null && launchPlayerUuid == null) {
            launchPlayerUuid = normalized;
        }
    }

    /** Promotes a server-visible identity already tied to the logged-in local account. */
    public void confirmServerLocalPlayerName(String ign) {
        String clean = cleanIgn(ign);
        if (!isValidMcUsername(clean)) return;
        serverConfirmedLocalIgn = clean;
        if (!isValidMcUsername(localPlayer)) {
            setLocalPlayerIdentity(clean);
            return;
        }
        if (localPlayer.equalsIgnoreCase(clean)) {
            refreshIdentityMetadata();
            return;
        }
        String previous = localPlayer;
        PlayerMetaCache.INSTANCE.transferIdentity(previous, clean);
        transferIdentity(previous, clean);
    }

    static boolean samePlainIdentity(String current, String candidate) {
        return isValidMcUsername(candidate)
                && current != null
                && current.equalsIgnoreCase(candidate);
    }

    public void fireGtbTick() {
        if (!trackingEnabled) return;
        finalizeCompletedGameIfReady(System.currentTimeMillis());
        if (gtbHooks != null) gtbHooks.onTickActivity();
    }

    public List<String> drainNotificationMessages() {
        List<String> out = new ArrayList<String>();
        String message;
        while ((message = notificationMessages.poll()) != null) {
            out.add(message);
        }
        return out;
    }

    public void discardPendingAutonomousActions() {
        clearAutoRequeueSchedule();
        notificationMessages.clear();
    }

    public void queueNotificationMessage(String message) {
        if (message != null && !message.isEmpty()) notificationMessages.add(message);
    }

    public void onHint(HintStructureParser.ParsedHint parsed, long timestamp) {
        if (!trackingEnabled
                || (!isInRound(state) && state != TrackingState.PAUSED)
                || currentRound == null) return;
        if (parsed == null || !parsed.isHint || parsed.structure == null
                || parsed.structure.isEmpty()) return;
        resumePausedRoundFromLiveSignal(timestamp);

        String trimmed = parsed.trimmedHint == null ? "" : parsed.trimmedHint.trim();
        if (trimmed.isEmpty()) return;
        if (currentRound.hintStructure == null) {
            currentRound.hintStructure = parsed.structure;
        }
        if (!trimmed.equals(lastHintText)) {
            lastHintText = trimmed;
            currentRound.hints.add(new HintEntry(timestamp, trimmed));
        }
        captureShowAllMatchingHint(currentRound, trimmed);
        queueThemePointFromHint(currentRound);
        String hintLangCode = firstNonEmpty(currentRound.langCode, currentLangCode);
        if (currentRound.completeThemeRevealedToUser
                && !TranslationData.findShortcutThemes(hintLangCode, trimmed).isEmpty()) {
            boolean themeChanged = !trimmed.equals(currentRound.theme);
            currentRound.theme = trimmed;
            queueShortcutReminder(currentRound, trimmed, GtbStructuresSettings.INSTANCE);
            if (themeChanged) writeLiveFile();
        }
        if (gtbHooks != null) {
            gtbHooks.onHintUpdate(trimmed, parsed.structure, hintLangCode);
        }
    }

    public boolean onGuiThemePick(String[] themes) {
        return onGuiThemePick(themes, localPlayer);
    }

    public boolean onGuiThemePick(String[] themes, String localPlayerName) {
        if (!trackingEnabled || !isInRound(state) || currentRound == null) return false;
        String builder = canonicalRosterIgn(localPlayerName);
        boolean builderChanged = isValidMcUsername(builder)
                && (currentRound.builder == null || !currentRound.builder.equalsIgnoreCase(builder));
        if (isValidMcUsername(builder)) {
            setLocalPlayerIdentity(builder);
            addPlayerIfAbsent(builder);
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
        queueThemePointFromTheme(currentRound.theme, currentRound.langCode);
        writeLiveFile();
        return true;
    }

    public boolean isThemeSelectionGui(String title) {
        return LangPatterns.isThemeSelectionGui(title);
    }

    public String manualExport(File gameDir) {
        if (!trackingEnabled) {
            return "Stats tracking is off; no data will be exported.";
        }
        if (state == TrackingState.NOT_IN_GAME && rounds.isEmpty()) {
            return "No game data to export.";
        }

        long now = System.currentTimeMillis();
        boolean interrupted = state == TrackingState.PAUSED;
        String reason = interrupted ? "manual_export" : null;
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
        return "Failed to export (no config directory).";
    }

    public String manualExportRaw(File gameDir) {
        if (!trackingEnabled) {
            return "Stats tracking is off; raw packets are not being recorded.";
        }
        File statsDir = GameExporter.getStatsDir(gameDir);
        if (statsDir == null) return "Failed to export raw packets (no config directory).";
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(System.currentTimeMillis()));
        File file = RawPacketRecorder.INSTANCE.writeSnapshot(statsDir, "raw_" + timestamp);
        if (file == null) return "No raw packet data to export.";
        return GameExporter.localFileNotice("Raw packets exported to ", gameDir, file);
    }

    public String formatHaveIBuilt(String localPlayerName) {
        TrackingState st = state;
        if (st == TrackingState.NOT_IN_GAME || st == TrackingState.LOBBY) {
            return "\u00a77You are currently not in a game";
        }
        String me = localPlayerName != null && !localPlayerName.trim().isEmpty()
                ? localPlayerName.trim() : localPlayer;
        if (me == null || me.trim().isEmpty()) {
            return "\u00a77You are currently not in a game";
        }

        List<Integer> roundsBuilt = new ArrayList<Integer>();
        for (RoundData round : rounds) {
            if (round.builder == null || !round.builder.equalsIgnoreCase(me)) continue;
            if (round == currentRound && st == TrackingState.ROUND_PRE) continue;
            if (round.roundNumber > 0) roundsBuilt.add(Integer.valueOf(round.roundNumber));
        }

        if (roundsBuilt.isEmpty()) {
            return "\u00a77No, you have not built yet";
        }
        StringBuilder out = new StringBuilder("\u00a77Yes, you have built on \u00a7fRound ");
        for (int i = 0; i < roundsBuilt.size(); i++) {
            if (i > 0) out.append("\u00a77, \u00a7f");
            out.append(roundsBuilt.get(i));
        }
        return out.toString();
    }

    public String formatWhoHasBuilt() {
        TrackingState st = state;
        if (st == TrackingState.NOT_IN_GAME || st == TrackingState.LOBBY) {
            return "\u00a77You are currently not in a game";
        }

        String pending = null;
        String pendingStatus = null;
        if (currentRound != null && currentRound.builder != null
                && !currentRound.builder.isEmpty()) {
            if (st == TrackingState.ROUND_PRE) {
                pending = currentRound.builder;
                pendingStatus = "is about to build";
            } else if (st == TrackingState.ROUND_BUILD || st == TrackingState.PAUSED) {
                pending = currentRound.builder;
                pendingStatus = "is currently building";
            }
        }

        LinkedHashSet<String> built = new LinkedHashSet<String>();
        Set<String> seenLower = new LinkedHashSet<String>();
        for (RoundData round : rounds) {
            if (round.builder == null || round.builder.isEmpty()) continue;
            if (pending != null && round.builder.equalsIgnoreCase(pending)) continue;
            if (round == currentRound
                    && st != TrackingState.ROUND_END
                    && st != TrackingState.POST_GAME) {
                continue;
            }
            String key = round.builder.toLowerCase(Locale.ROOT);
            if (seenLower.add(key)) built.add(round.builder);
        }

        if (built.isEmpty() && pending == null) {
            return "\u00a77No one has built yet this game";
        }

        StringBuilder out = new StringBuilder();
        if (!built.isEmpty()) {
            List<String> builtList = new ArrayList<String>(built);
            out.append(joinNames(builtList));
            out.append(builtList.size() > 1 ? " \u00a77have built" : " \u00a77has built");
            if (pending != null) {
                out.append(", \u00a7f").append(pending).append(" \u00a77").append(pendingStatus);
            }
        } else {
            out.append("\u00a77No one has built yet, \u00a7f").append(pending)
                    .append(" \u00a77").append(pendingStatus);
        }
        return out.toString();
    }

    private static String joinNames(List<String> names) {
        StringBuilder out = new StringBuilder();
        int n = names.size();
        for (int i = 0; i < n; i++) {
            if (i == 0) {
                out.append("\u00a7f").append(names.get(i));
            } else if (i == n - 1) {
                out.append(" \u00a77and \u00a7f").append(names.get(i));
            } else {
                out.append("\u00a77, \u00a7f").append(names.get(i));
            }
        }
        return out.toString();
    }

    void setStatsDirOverrideForTest(File statsDir) {
        statsDirOverrideForTest = statsDir;
    }

    public void writeLiveFile() {
        if (!trackingEnabled) return;
        File statsDir = runtimeStatsDir();
        if (statsDir == null || rounds.isEmpty()) return;
        long now = System.currentTimeMillis();
        refreshIdentityMetadata();
        String json = GameExporter.toJson(gameStart, now, false, false,
                null, currentLanguage, localPlayer,
                trueIgn, playerUuid, nick, isNicked, activeTimeAt(now),
                players, rounds, rankings, lastUnknownResidual);
        GameExporter.writeJsonFile(new File(statsDir, "current.json"), json);
    }

    private File runtimeStatsDir() {
        if (statsDirOverrideForTest != null) return statsDirOverrideForTest;
        return GameExporter.getStatsDir(runtimeGameDir);
    }

    public boolean isLeaver(String ign) {
        return containsIgnoreCase(leavers, ign);
    }

    public void transferIdentity(String oldIgn, String newIgn) {
        if (oldIgn == null || newIgn == null || oldIgn.equalsIgnoreCase(newIgn)) return;
        replacePlayerIdentity(oldIgn, newIgn);
        for (RoundData round : rounds) {
            if (round == null) continue;
            if (round.builder != null && round.builder.equalsIgnoreCase(oldIgn)) {
                round.builder = newIgn;
            }
            renameMapKey(round.scoreDeltas, oldIgn, newIgn);
            for (CorrectGuessEntry correct : round.correctGuesses) {
                if (correct != null && correct.ign != null
                        && correct.ign.equalsIgnoreCase(oldIgn)) {
                    correct.ign = newIgn;
                }
            }
            for (GuessEntry guess : round.guesses) {
                if (guess != null && guess.ign != null
                        && guess.ign.equalsIgnoreCase(oldIgn)) {
                    guess.ign = newIgn;
                }
            }
        }
        renameMapKey(midGameUnattributedResidual, oldIgn, newIgn);
        renameMapKey(lastUnknownResidual, oldIgn, newIgn);
        renameMapKey(lastSidebarScores, oldIgn, newIgn);
        renameMapKey(sidebarDropKnownScores, oldIgn, newIgn);
        if (localPlayer != null && localPlayer.equalsIgnoreCase(oldIgn)) {
            setLocalPlayerIdentity(newIgn);
        }
        moveSetValue(leavers, oldIgn, newIgn);
        moveSetValue(builderLeftLeavers, oldIgn, newIgn);
        moveSetValue(authoritativeLowRosterLeavers, oldIgn, newIgn);
        moveSetValue(pendingTabReturnConfirmations, oldIgn, newIgn);
        moveSetValue(lowRosterCandidatePlayers, oldIgn, newIgn);
        if (currentRound != null) {
            currentRound.events.add(new GameEvent(System.currentTimeMillis(),
                    "identity_transfer", oldIgn + "->" + newIgn));
        }
        if (gtbHooks != null) gtbHooks.onIdentityTransfer(oldIgn, newIgn);
    }

    private void observeLocalIdentity(String localNow, TrackingState observedScoreboardState) {
        if (!isValidMcUsername(localNow)) return;

        if (isValidMcUsername(serverConfirmedLocalIgn)
                && isValidMcUsername(trueIgn)
                && localNow.equalsIgnoreCase(trueIgn)
                && !serverConfirmedLocalIgn.equalsIgnoreCase(trueIgn)
                && isValidMcUsername(localPlayer)
                && localPlayer.equalsIgnoreCase(serverConfirmedLocalIgn)) {
            lastLocalIgn = serverConfirmedLocalIgn;
            return;
        }

        boolean confirmedPausedRecovery = state == TrackingState.PAUSED
                && isInGameScoreboardState(observedScoreboardState);
        if (state == TrackingState.PAUSED && !confirmedPausedRecovery) {
            return;
        }

        if (lastLocalIgn != null && !lastLocalIgn.equalsIgnoreCase(localNow)
                && (isActiveGame(state) || confirmedPausedRecovery)) {
            // The local entity is authoritative. The returned identity may
            // already be present as a roster shell, so transferIdentity must
            // merge it instead of applying sidebar-only admission rules.
            transferIdentity(lastLocalIgn, localNow);
        }
        lastLocalIgn = localNow;
    }

    public void markActivity(String ign) {
        notePlayerActivity(ign, "activity");
    }

    public boolean acceptsGtbRosterPackets() {
        return state == TrackingState.LOBBY && gtbLobbyRosterConfirmed;
    }

    public boolean acceptsLobbyRosterEvidence(String ign) {
        return acceptsGtbRosterPackets() && !isLobbyQuitSuppressed(ign);
    }

    /** Applies the current sidebar objective before the same-tick player-list snapshot. */
    public void onRosterObjectiveTitle(String formattedTitle) {
        if (!trackingEnabled || formattedTitle == null || state != TrackingState.LOBBY) return;
        String cleanTitle = stripControlCodes(formattedTitle).trim();
        if (cleanTitle.isEmpty()) return;
        if (LangPatterns.isGtbTitle(cleanTitle)) {
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
        clearLobbyPacketRemovalEvidence();
    }

    public void onPacketRosterAdd(String ign) {
        ign = canonicalRosterIgn(ign);
        if (!isValidMcUsername(ign) || isReservedNonPlayerName(ign)) return;
        removeIgnoreCase(lobbyPacketRemovals, ign);
        if (!acceptsGtbRosterPackets()) {
            if (isRosterTrackingState() && containsPlayerIgnoreCase(ign)) {
                noteTabPresence(ign);
                if (gtbHooks != null) gtbHooks.onPlayerTabPresent(ign);
            }
            return;
        }
        if (isLobbyQuitSuppressed(ign)) return;
        boolean added = !containsPlayerIgnoreCase(ign);
        addPlayerIfAbsent(ign);
        noteTabPresence(ign);
        if (added && gtbHooks != null && gameInitialized) {
            gtbHooks.onGameRosterPlayer(ign);
        }
        if (gtbHooks != null) gtbHooks.onPlayerTabPresent(ign);
    }

    public void onPacketRosterRemove(String ign) {
        ign = canonicalRosterIgn(ign);
        if (!isValidMcUsername(ign) || isReservedNonPlayerName(ign)) return;
        if (state == TrackingState.LOBBY && gtbLobbyRosterConfirmed) {
            addIgnoreCase(lobbyPacketRemovals, ign);
            pruneLobbyRosterToAnnouncedCount();
            return;
        }
        if (!isRosterTrackingState()) return;
        removeIgnoreCase(pendingTabReturnConfirmations, ign);
        if (gtbHooks != null) gtbHooks.onPlayerTabAbsent(ign);
    }

    public void onEncodedGameRosterPlayer(String ign) {
        ign = canonicalRosterIgn(ign);
        if (!isValidMcUsername(ign) || !acceptsLobbyRosterEvidence(ign)) return;
        addPlayerIfAbsent(ign);
    }

    void beginLobbyHandoff(long now) {
        pendingLobbyRoundStart = null;
        handoffLobbyRoster.clear();
        mergeRosterInto(handoffLobbyRoster, players);
        gtbHandoffRosterUntilMs = now + GTB_HANDOFF_ROSTER_GRACE_MS;
        PlayerMetaCache.INSTANCE.clearRosterEvidence();
    }

    /** Whether an arrival belongs to the bounded, previously observed GTB lobby handoff. */
    public boolean isLobbyHandoffActive(long timestamp) {
        return state == TrackingState.NOT_IN_GAME && gtbHandoffRosterUntilMs > 0L
                && timestamp >= gtbHandoffRosterUntilMs - GTB_HANDOFF_ROSTER_GRACE_MS
                && timestamp <= gtbHandoffRosterUntilMs;
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
            mergeRosterInto(players, PlayerMetaCache.INSTANCE.encodedGameRosterSnapshot());
            mergeRosterInto(players, PlayerMetaCache.INSTANCE.packetRosterSnapshot());
            pruneLobbyRosterToAnnouncedCount();
        }
        if (isValidMcUsername(localIgn)) {
            addPlayerIfAbsent(localIgn);
        }
        clearLobbyPacketRemovalEvidence();
    }

    public void onScoreboardTick(String titleClean, List<String> cleanLines) {
        onScoreboardTick(titleClean, cleanLines, null);
    }

    public void onScoreboardTick(String titleClean, List<String> cleanLines, List<String> rawFormattedLines) {
        onScoreboardTickAt(titleClean, cleanLines, rawFormattedLines, System.currentTimeMillis());
    }

    void onScoreboardTickAt(String titleClean, List<String> cleanLines, List<String> rawFormattedLines, long now) {
        boolean scoreboardShowsGtb = LangPatterns.isGtbTitle(titleClean);
        TrackingState rawScoreboardState = scoreboardState(cleanLines, scoreboardShowsGtb);
        if (GtbStructuresSettings.INSTANCE.modEnabled) {
            EndGameFireworkController.INSTANCE.observeScoreboard(rawScoreboardState, scoreboardShowsGtb);
        } else {
            EndGameFireworkController.INSTANCE.reset();
        }
        if (!trackingEnabled) return;
        boolean unrelatedSidebar = titleClean != null && !titleClean.trim().isEmpty()
                && !scoreboardShowsGtb;
        TrackingState sbState = retainActiveStateForUnclassifiedGtb(
                state, rawScoreboardState, scoreboardShowsGtb);
        // The runtime observes the local entity before this tick, but a nick
        // change must wait for the derived scoreboard state. A paused lobby or
        // world transition is not evidence that it belongs to the same game.
        observeLocalIdentity(observedLocalIgn, sbState);
        if (state != TrackingState.ROUND_BUILD || sbState != TrackingState.ROUND_BUILD) {
            resetAuthoritativeLowRosterCandidate();
        }
        lastScoreboardState = sbState;
        if (sbState != TrackingState.POST_GAME) clearAutoRequeueSchedule();
        // Persisted current.json is resolved by the lobby/user-joined boundary.
        // An arbitrary active sidebar can belong to a different GTB instance.
        if (state == TrackingState.NOT_IN_GAME
                && gtbHandoffRosterUntilMs > 0L
                && (now > gtbHandoffRosterUntilMs || unrelatedSidebar)) {
            clearLobbyHandoff();
            pendingNextBuilder = null;
            PlayerMetaCache.INSTANCE.clearRosterEvidence();
        }

        if (isActiveGame(state) && sbState == TrackingState.NOT_IN_GAME) {
            notInGameTicks++;
            if (notInGameTicks >= PAUSE_GRACE_TICKS) {
                if (state == TrackingState.POST_GAME) {
                    endGame(true, null, now);
                } else if (state == TrackingState.LOBBY) {
                    if (unrelatedSidebar) {
                        clearLobbyHandoff();
                        pendingNextBuilder = null;
                        PlayerMetaCache.INSTANCE.clearRosterEvidence();
                    } else {
                        beginLobbyHandoff(now);
                    }
                    state = TrackingState.NOT_IN_GAME;
                    lobbyTabDone = false;
                    players.clear();
                    rankings.clear();
                    lobbyQuitSuppress.clear();
                    gtbLobbyRosterConfirmed = false;
                } else {
                    handlePause(now);
                }
            }
            return;
        }
        if (sbState != TrackingState.NOT_IN_GAME) {
            notInGameTicks = 0;
        }

        if (handlePausedScoreboardRecovery(sbState, cleanLines, now)) {
            return;
        }

        if (isActiveGame(state) && state != TrackingState.LOBBY
                && sbState == TrackingState.LOBBY) {
            boolean completed = state == TrackingState.POST_GAME;
            endGame(completed, completed ? null : "new_gtb_lobby", now);
            resetFreshLobbyRoster();
            state = TrackingState.LOBBY;
            gtbLobbyRosterConfirmed = true;
            return;
        }

        if (state == TrackingState.NOT_IN_GAME && sbState == TrackingState.LOBBY) {
            finalizePendingCurrent(runtimeStatsDir(), "new_gtb_lobby", now);
            state = TrackingState.LOBBY;
            clearLobbyHandoff();
            pendingNextBuilder = null;
            lobbyTabDone = false;
            players.clear();
            rounds.clear();
            currentRound = null;
            rankings.clear();
            PlayerMetaCache.INSTANCE.clearHarvestedRanks();
            lobbyQuitSuppress.clear();
            gtbLobbyRosterConfirmed = true;
            return;
        }

        if (state == TrackingState.NOT_IN_GAME && isInGameScoreboardState(sbState)) {
            startScoreboardOnlyRound(sbState, cleanLines, now);
            return;
        }

        if (state == TrackingState.LOBBY
                && (sbState == TrackingState.ROUND_PRE || sbState == TrackingState.ROUND_BUILD)) {
            startScoreboardOnlyRound(sbState, cleanLines, now);
            return;
        }
        boolean enteredBuildPhase = state == TrackingState.ROUND_PRE
                && sbState == TrackingState.ROUND_BUILD;
        applyScoreboardRoundState(sbState, now);
        if (enteredBuildPhase && gtbHooks != null) gtbHooks.onRoundBuild();
        if (state == TrackingState.POST_GAME && sbState == TrackingState.POST_GAME) {
            if (parseRankings(cleanLines, now)) {
                fireTrueScoresUpdate();
            }
            return;
        }
        if (isInRound(state) && currentRound != null) {
            processRoundSidebar(cleanLines, now);
        }
    }

    public void onChatMessage(String raw, String clean) {
        onChatMessageAt(raw, clean, System.currentTimeMillis());
    }

    void onChatMessageAt(String raw, String clean, long now) {
        String text = clean != null ? clean : raw;
        if (text == null) text = "";
        text = stripControlCodes(text).trim();
        if (text.isEmpty()) return;
        observeGuessSoundChat(text, now);
        maybeAutoDisableTrollModeFromRoundStart(text);
        if (!trackingEnabled) return;
        expireLobbyHandoff(now);

        if (handleLobbyJoinMessage(raw != null ? raw : text, text, now)) {
            return;
        }

        String fallbackLang = lobbyLanguageFallback.confirmFromChat(text);
        if (fallbackLang != null) {
            applyLobbyLanguageFallback(fallbackLang);
            return;
        }

        if (LangPatterns.isAlreadyCorrectGuessMessage(text)) {
            acceptPendingGuess(now);
            return;
        }
        Integer explicitSelfPoints = LangPatterns.selfCorrectGuessPoints(text);
        if (explicitSelfPoints != null) {
            ModernChatCooldownCounter.onUserCorrectGuess();
            applyPendingSelfGuessScore(explicitSelfPoints.intValue(), now);
            return;
        }

        // Party, guild, officer, and direct-message envelopes may copy a
        // localized correct-guess notice verbatim. They are ordinary chat,
        // not evidence for the active GTB game.
        if (isNonGameChatChannel(text)) return;

        if (state == TrackingState.LOBBY) {
            String lobbyQuitIgn = parseLobbyQuitIgn(text);
            if (lobbyQuitIgn != null) {
                suppressLobbyQuit(lobbyQuitIgn);
                return;
            }
        }

        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (handleLocalizedGtbChat(raw != null ? raw : text, text, lp, now)) {
                return;
            }
        }

        LangPatterns.BuilderLeftMessageKind builderLeftKind =
                LangPatterns.builderLeftMessageKind(text);
        if (builderLeftKind != LangPatterns.BuilderLeftMessageKind.NONE) {
            handleBuilderLeft(now, builderLeftKind.detail);
            return;
        }

        handlePlayerChat(raw != null ? raw : text, text, now);
    }

    private void maybeAutoDisableTrollModeFromRoundStart(String text) {
        if (trollAutoDisableJapaneseRoundPending) {
            trollAutoDisableJapaneseRoundPending = false;
            int[] round = parseRoundHeader(text);
            if (round != null && round[0] == 1) {
                autoDisableTrollModeAtGameStart();
                return;
            }
        }
        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (lp.roundPrefix == null || !text.startsWith(lp.roundPrefix)) continue;
            String rest = text.substring(lp.roundPrefix.length());
            int[] round = parseRoundHeader(rest);
            if (round != null && round[0] == 1) {
                autoDisableTrollModeAtGameStart();
            } else if ("ja".equals(lp.lang) && rest.trim().isEmpty()) {
                trollAutoDisableJapaneseRoundPending = true;
            }
            return;
        }
    }

    static boolean isFirstRoundStartForTrollAutoDisableForTest(String text) {
        if (text == null) return false;
        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (lp.roundPrefix == null || !text.startsWith(lp.roundPrefix)) continue;
            int[] round = parseRoundHeader(text.substring(lp.roundPrefix.length()));
            return round != null && round[0] == 1;
        }
        return false;
    }

    private void autoDisableTrollModeAtGameStart() {
        if (GtbStructuresSettings.INSTANCE.autoDisableTrollModeAtGameStart()) {
            notificationMessages.add("Troll mode is currently \u00a7cOFF");
        }
    }

    private boolean handleLocalizedGtbChat(String raw, String text, LangPatterns lp, long now) {
        if (lp == null) return false;

        if (lp.builderPrefix != null && text.startsWith(lp.builderPrefix)) {
            disableLobbyLanguageFallback();
            resumePausedRoundFromLiveSignal(now);
            String builderText = text.substring(lp.builderPrefix.length());
            String builder = cleanIgn(builderText);
            if (isValidMcUsername(builder)) {
                PlayerMetaCache.INSTANCE.harvestBuilderMessageRank(builder, raw, lp.builderPrefix);
            }
            handleBuilderMessage(builderText, now);
            return true;
        }

        if (lp.roundPrefix != null && text.startsWith(lp.roundPrefix)) {
            int[] round = parseRoundHeader(text.substring(lp.roundPrefix.length()));
            if (round != null) {
                disableLobbyLanguageFallback();
                handleRoundStart(round[0], round[1], now);
                return true;
            }
        }

        String correctGuessIgn = extractCorrectGuessIgn(text, lp);
        if (correctGuessIgn != null) {
            disableLobbyLanguageFallback();
            resumePausedRoundFromLiveSignal(now);
            handleCorrectGuess(correctGuessIgn, raw, lp, now);
            return true;
        }

        String theme = extractThemeReveal(text, lp);
        if (theme != null) {
            disableLobbyLanguageFallback();
            maybeAutoDetectLanguage(lp);
            resumePausedRoundFromLiveSignal(now);
            handleThemeReveal(theme, now);
            return true;
        }

        Integer themeCharCount = extractThemeCharCount(text, lp);
        if (themeCharCount != null) {
            disableLobbyLanguageFallback();
            maybeAutoDetectLanguage(lp);
            resumePausedRoundFromLiveSignal(now);
            if (currentRound != null) currentRound.themeCharCount = themeCharCount.intValue();
            if (gtbHooks != null) gtbHooks.onOneSecondAlert();
            return true;
        }

        if (lp.winnersKeyword != null && text.equals(lp.winnersKeyword)) {
            disableLobbyLanguageFallback();
            handleWinners(now);
            return true;
        }

        return handleLanguageSelection(text, lp);
    }

    private void handleBuilderMessage(String text, long now) {
        String builder = cleanIgn(text);
        if (!isValidMcUsername(builder)) return;
        if (currentRound != null && state == TrackingState.ROUND_PRE
                && !roundHasEvent(currentRound, "builder_left")) {
            reassignBuilder(currentRound, builder);
        } else if (currentRound != null && state == TrackingState.ROUND_BUILD) {
            if (shouldBufferNextBuilder(builder)) {
                pendingNextBuilder = builder;
            } else {
                reassignBuilder(currentRound, builder);
            }
        } else {
            pendingNextBuilder = builder;
        }
    }

    private void handleRoundStart(int roundNumber, int totalRounds, long now) {
        expireLobbyHandoff(now);
        if (state == TrackingState.NOT_IN_GAME && isLobbyHandoffActive(now)
                && roundNumber == 1 && totalRounds > 0) {
            // The chat can precede the new sidebar. Keep its first arrival time
            // and wait for GTB sidebar confirmation before starting the game.
            long firstTimestamp = pendingLobbyRoundStart == null
                    ? now : pendingLobbyRoundStart.timestamp;
            pendingLobbyRoundStart = new InitialRoundHeader(totalRounds, firstTimestamp);
            return;
        }
        if (!gameInitialized) {
            seedGameStartRoster(now, bestKnownLocalIgn());
            initNewGame(now);
        }
        clearPendingRejoinState();
        if (currentRound != null
                && currentRound.roundNumber > 0
                && currentRound.roundNumber != roundNumber
                && currentRound.roundEnd <= 0L) {
            currentRound.roundEnd = now;
            if (gtbHooks != null) gtbHooks.onRoundEnd(false);
        }
        state = TrackingState.ROUND_BUILD;

        if (currentRound != null && currentRound.roundNumber == roundNumber
                && currentRound.roundEnd <= 0L) {
            currentRound.totalRounds = totalRounds;
            if (currentRound.language == null) currentRound.language = currentLanguage;
            if (currentRound.langCode == null) currentRound.langCode = currentLangCode;
            if (gtbHooks != null) gtbHooks.onRoundStart(roundNumber, totalRounds);
            return;
        }

        RoundData previous = rounds.isEmpty() ? null : rounds.get(rounds.size() - 1);
        if (currentRound == null && previous != null && roundHasEvent(previous, "builder_left")) {
            if (previous.roundNumber <= 0 && roundNumber > 1) {
                previous.roundNumber = roundNumber - 1;
            }
            if (previous.totalRounds <= 0) previous.totalRounds = totalRounds;
            if (previous.roundEnd <= 0L) previous.roundEnd = now;
        }

        if (fillUnknownCurrentRound(roundNumber, totalRounds)) {
            return;
        }

        RoundData round = new RoundData();
        round.roundNumber = roundNumber;
        round.totalRounds = totalRounds;
        round.roundStart = now;
        round.language = currentLanguage;
        round.langCode = currentLangCode;
        if (pendingNextBuilder != null) {
            round.builder = pendingNextBuilder;
            pendingNextBuilder = null;
        }
        currentRound = round;
        rounds.add(round);
        ModernChatCooldownCounter.onRoundStart();
        jaRoundLabelPending = false;
        if (gtbHooks != null) {
            gtbHooks.onRoundStart(roundNumber, totalRounds);
            if (round.builder != null) {
                emitBuilderHook(null, round.builder);
            }
        }
    }

    private boolean fillUnknownCurrentRound(int roundNumber, int totalRounds) {
        if (currentRound == null || currentRound.roundNumber > 0) return false;
        if (roundHasEvent(currentRound, "builder_left") || roundHasEvent(currentRound, "afk_skip")) {
            return false;
        }
        currentRound.roundNumber = roundNumber;
        currentRound.totalRounds = totalRounds;
        if (currentRound.language == null) currentRound.language = currentLanguage;
        if (currentRound.langCode == null) currentRound.langCode = currentLangCode;
        ModernChatCooldownCounter.onRoundStart();
        jaRoundLabelPending = false;
        if (gtbHooks != null) {
            gtbHooks.onRoundStart(roundNumber, totalRounds);
        }
        return true;
    }

    private void handleCorrectGuess(String ign, String raw, LangPatterns lp, long now) {
        if (currentRound == null || !isValidMcUsername(ign)) return;
        maybeAutoDetectLanguage(lp);
        PlayerMetaCache.INSTANCE.harvestCorrectGuessMessageRank(ign, raw, lp.correctGuessPrefix);
        addPlayerIfAbsent(ign);
        if (!hasCorrectGuess(currentRound, ign)) {
            currentRound.correctGuesses.add(new CorrectGuessEntry(now, ign));
            int orderZeroBased = currentRound.correctGuesses.size() - 1;
            ScoreReconciler.recordGuessDelta(currentRound, ign, orderZeroBased);
        }
        notePlayerActivity(ign, "correct_guess");
        if (isLocalPlayerIgn(ign)) handleLocalCorrectGuess(ign, now, true);
        if (gtbHooks != null) gtbHooks.onCorrectGuess(Collections.singletonList(ign));
    }

    /** Receives the post-transform text actually submitted by the loader's send hook. */
    public void onOutboundGuess(String actuallySent, long now) {
        RoundData round = currentRound;
        if (!trackingEnabled || state != TrackingState.ROUND_BUILD || round == null
                || isLocalPlayerBuilder(round)) return;
        acceptedGuessTracker.record(round, actuallySent, now);
    }

    private void acceptPendingGuess(long now) {
        if (currentRound == null || (state != TrackingState.ROUND_BUILD
                && state != TrackingState.ROUND_END)) return;
        String sent = acceptedGuessTracker.consumeAccepted(currentRound, now);
        if (sent == null) return;
        currentRound.acceptedGuessTexts.add(sent);
        ScoreReconciler.computeBuilderDelta(currentRound);
        if (GameTracker.INSTANCE.game != null
                && GameTracker.INSTANCE.game.currentRound == currentRound.roundNumber) {
            GameTracker.INSTANCE.onAcceptedGuess(sent);
        }
        queueThemePointFromTheme(currentRound.theme, currentRound.langCode);
    }

    private void handleLocalCorrectGuess(String ign, long now, boolean updateCooldown) {
        if (currentRound == null || !isValidMcUsername(ign)) return;
        acceptPendingGuess(now);
        if (updateCooldown) ModernChatCooldownCounter.onUserCorrectGuess();
        rememberPendingSelfGuessScore(ign, now);
        markCompleteThemeRevealed(currentRound);
        queueThemePointFromTheme(currentRound.theme, currentRound.langCode);
        queueRoundRevealNotifications(currentRound, currentRound.theme);
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
        if (points < 1 || points > 3) {
            clearPendingSelfGuessScore();
            return false;
        }
        String ign = pendingSelfGuessScoreIgn;
        currentRound.scoreDeltas.put(ign, ScoreDelta.definite(points, "self_points"));
        if (gtbHooks != null) gtbHooks.onCorrectGuessScore(ign, points);
        clearPendingSelfGuessScore();
        return true;
    }

    private void clearPendingSelfGuessScore() {
        pendingSelfGuessScoreIgn = null;
        pendingSelfGuessScoreRound = 0;
        pendingSelfGuessScoreUntilMs = 0L;
    }

    private void handleThemeReveal(String theme, long now) {
        if (currentRound == null || !isInRound(state)) return;
        String cleanTheme = theme == null ? "" : theme.trim();
        if (cleanTheme.isEmpty()) return;

        boolean themeChanged = !cleanTheme.equals(currentRound.theme);
        currentRound.theme = cleanTheme;
        if (currentRound.langCode == null) currentRound.langCode = currentLangCode;
        if (currentRound.language == null) currentRound.language = currentLanguage;
        if (!isLocalPlayerBuilder(currentRound)) {
            markCompleteThemeRevealed(currentRound);
        }
        ScoreReconciler.computeBuilderDelta(currentRound);
        if (gtbHooks != null) {
            String lang = currentRound.langCode != null ? currentRound.langCode : currentLangCode;
            gtbHooks.onThemeUpdate(cleanTheme, lang);
        }
        queueThemePointFromTheme(cleanTheme, currentLangCode);
        queueRoundRevealNotifications(currentRound, cleanTheme);
        if (themeChanged) writeLiveFile();
        finishCurrentRound(now, false);
    }

    private void handleWinners(long now) {
        if (!isActiveGame(state) && state != TrackingState.PAUSED) return;
        activeTimeMs = activeTimeAt(now);
        activeTimeStartedAt = 0L;
        if (currentRound != null && currentRound.roundEnd <= 0L) {
            currentRound.roundEnd = now;
            if (gtbHooks != null) gtbHooks.onRoundEnd(false);
        }
        state = TrackingState.POST_GAME;
        scheduledEndGameTime = now + END_GAME_DELAY_MS;
    }

    private void handlePause(long now) {
        if (!isActiveGame(state) || state == TrackingState.LOBBY) return;

        TrackingState stateAtLeave = state;
        int inferredRoundAtLeave = inferPausedRoundCandidate(stateAtLeave);
        activeTimeMs = activeTimeAt(now);
        activeTimeStartedAt = 0L;
        state = TrackingState.PAUSED;
        pauseTime = now;
        notInGameTicks = 0;

        pausedBuilder = null;
        pausedRoundTotal = 0;
        pausedState = stateAtLeave;
        pausedNextRoundCandidate = inferredRoundAtLeave;
        pausedRoundHadDataGapBeforeLeave = currentRound != null && currentRound.dataGap;
        pausedLeaveCommitted = false;
        if (currentRound != null) {
            pausedBuilder = currentRound.builder;
            pausedRoundTotal = currentRound.totalRounds;
        }
        if (pausedRoundTotal == 0 && rounds.size() >= 2) {
            pausedRoundTotal = rounds.get(rounds.size() - 2).totalRounds;
        }
        if (stateAtLeave != TrackingState.ROUND_END || serverSessionBoundaryPending) {
            commitPausedLeave(now);
        }

        writeLiveFile();
    }

    private void commitPausedLeave(long now) {
        serverSessionBoundaryPending = false;
        if (state != TrackingState.PAUSED || pausedLeaveCommitted) return;
        pausedLeaveCommitted = true;
        PlayerMetaCache.INSTANCE.clearRosterEvidence();
        lastBuilderHookRound = null;
        lastBuilderHookIgn = null;
        if (currentRound != null) {
            String detail = "state=" + pausedState;
            if (pausedNextRoundCandidate > 0) detail += ",nextRoundCandidate=" + pausedNextRoundCandidate;
            currentRound.events.add(new GameEvent(now, "disconnect", detail));
            currentRound.dataGap = true;
        }
        if (gtbHooks != null) gtbHooks.onUserLeave();
    }

    private int inferPausedRoundCandidate(TrackingState stateAtLeave) {
        if (currentRound == null) return 0;
        int candidate = 0;
        if (stateAtLeave == TrackingState.ROUND_PRE) {
            if (currentRound.roundNumber > 0) candidate = currentRound.roundNumber;
            else {
                int index = rounds.lastIndexOf(currentRound);
                if (index > 0 && rounds.get(index - 1).roundNumber > 0) {
                    candidate = rounds.get(index - 1).roundNumber + 1;
                }
            }
        } else if (stateAtLeave == TrackingState.ROUND_END && currentRound.roundNumber > 0
                && currentRound.roundEnd > 0L) {
            candidate = currentRound.roundNumber + 1;
        }
        int total = currentRound.totalRounds > 0 ? currentRound.totalRounds : pausedRoundTotal;
        return candidate > 0 && (total <= 0 || candidate <= total) ? candidate : 0;
    }

    private void resumePausedRoundFromLiveSignal(long now) {
        resumePausedRoundFromLiveSignal(now, false);
    }

    private void resumePausedRoundFromLiveSignal(long now, boolean confirmedRejoin) {
        if (state != TrackingState.PAUSED || currentRound == null) return;
        if (confirmedRejoin) commitPausedLeave(now);
        boolean leaveCommitted = pausedLeaveCommitted;
        state = TrackingState.ROUND_BUILD;
        pauseTime = 0L;
        notInGameTicks = 0;
        activeTimeStartedAt = now;
        if (leaveCommitted) {
            beginPendingRejoinRecovery(now, "live_signal_recovery");
        } else {
            resetSidebarObservationEpoch();
            clearPendingRejoinState();
        }
    }

    private boolean beginPendingRejoinRecovery(long now, String detail) {
        boolean hadRealGameAtPause = pausedBuilder != null || pausedRoundTotal > 0
                || (currentRound != null && currentRound.dataGap);
        pendingRejoinCheck = hadRealGameAtPause;
        boolean iAmBuilder = currentRound != null && currentRound.builder != null
                && localPlayer != null && currentRound.builder.equalsIgnoreCase(localPlayer);
        localRejoinedImmediatelyStage = hadRealGameAtPause && !iAmBuilder;
        if (!hadRealGameAtPause) return false;

        // The first post-gap board starts a fresh observation epoch. Scores
        // absent throughout the gap cannot prove a leave, but later drops can.
        resetSidebarObservationEpoch();
        currentRound.events.add(new GameEvent(now, "rejoin", detail));
        if (gtbHooks != null) gtbHooks.onUserRejoin();
        return true;
    }

    private boolean handlePausedScoreboardRecovery(TrackingState sbState, List<String> cleanLines, long now) {
        if (state != TrackingState.PAUSED) return false;
        if (sbState == TrackingState.NOT_IN_GAME) return true;
        if (sbState == TrackingState.LOBBY) {
            endGame(false, "new_gtb_lobby", now);
            resetFreshLobbyRoster();
            state = TrackingState.LOBBY;
            gtbLobbyRosterConfirmed = true;
            return true;
        }
        if (sbState == TrackingState.POST_GAME) {
            handlePostGame(cleanLines, now);
            return true;
        }
        if (!isInRound(sbState)) return true;

        if (!gameInitialized) initNewGame(now);
        if (currentRound == null && !rounds.isEmpty()) {
            currentRound = rounds.get(rounds.size() - 1);
        }
        if (currentRound == null) return true;

        if (!pausedLeaveCommitted) {
            TrackingState resumeState = pausedState != null ? pausedState : sbState;
            state = resumeState;
            pauseTime = 0L;
            notInGameTicks = 0;
            activeTimeStartedAt = now;
            resetSidebarObservationEpoch();
            clearPendingRejoinState();
            writeLiveFile();
            return false;
        }

        activeTimeStartedAt = now;
        state = sbState;
        pauseTime = 0L;
        beginPendingRejoinRecovery(now, "scoreboard_recovery");
        writeLiveFile();
        return true;
    }

    private void handlePostGame(List<String> cleanLines, long now) {
        activeTimeMs = activeTimeAt(now);
        activeTimeStartedAt = 0L;
        state = TrackingState.POST_GAME;
        if (currentRound != null && currentRound.roundEnd <= 0L) {
            currentRound.roundEnd = now;
            if (gtbHooks != null) gtbHooks.onRoundEnd(false);
        }
        if (parseRankings(cleanLines, now)) {
            fireTrueScoresUpdate();
        }
        writeLiveFile();
    }

    void finalizeCompletedGameIfReady(long now) {
        if (scheduledEndGameTime > 0L && now >= scheduledEndGameTime) {
            if (rankings.isEmpty()
                    && now < scheduledEndGameTime + END_GAME_RANKINGS_WAIT_MS) {
                return;
            }
            scheduledEndGameTime = 0L;
            endGame(true, null, now);
        }
    }

    private void endGame(boolean completed, String interruptReason, long now) {
        if (gameEnded) return;
        gameEnded = true;

        if (gtbHooks != null) {
            Map<String, Integer> finalScores = new LinkedHashMap<String, Integer>();
            for (RankEntry ranking : rankings) {
                if (ranking != null && ranking.ign != null) {
                    finalScores.put(ranking.ign, Integer.valueOf(ranking.score));
                }
            }
            gtbHooks.onGameEnd(finalScores);
        }

        ScoreReconciler.FinalizationResult finalization =
                ScoreReconciler.finalizeGameStatsWithLeaderboard(rounds, rankings,
                        midGameUnattributedResidual);
        lastUnknownResidual.clear();
        if (finalization != null && finalization.unknownResidual != null) {
            lastUnknownResidual.putAll(finalization.unknownResidual);
        }

        boolean skipExport = !completed && rounds.isEmpty();
        File statsDir = runtimeStatsDir();
        if (statsDir != null && !skipExport) {
            refreshIdentityMetadata();
            String json = GameExporter.toJson(gameStart, now, completed, !completed,
                    interruptReason, currentLanguage, localPlayer,
                    trueIgn, playerUuid, nick, isNicked, activeTimeAt(now),
                    players, rounds, rankings, lastUnknownResidual);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(now));
            if (completed) {
                GameExporter.writeJsonFile(new File(statsDir, timestamp + ".json"), json);
            } else {
                GameExporter.writeJsonFile(new File(statsDir, "interrupted_" + timestamp + ".json"), json);
            }
            GameExporter.writeJsonFile(new File(statsDir, "current.json"), json);
        }

        state = TrackingState.NOT_IN_GAME;
        clearLobbyHandoff();
        pauseTime = 0L;
        notInGameTicks = 0;
        activeTimeMs = 0L;
        activeTimeStartedAt = 0L;
        scheduledEndGameTime = 0L;
        String retainedLocalIgn = bestKnownLocalIgn();
        localPlayer = null;
        refreshIdentityMetadata();
        lastLocalIgn = isValidMcUsername(retainedLocalIgn) ? retainedLocalIgn : null;
        gameInitialized = false;
        pendingRejoinCheck = false;
        pausedLeaveCommitted = false;
        serverSessionBoundaryPending = false;
        pausedBuilder = null;
        pausedRoundTotal = 0;
        pausedState = null;
        pausedNextRoundCandidate = 0;
        pausedRoundHadDataGapBeforeLeave = false;
        localRejoinedImmediatelyStage = false;
        lastBuilderHookRound = null;
        lastBuilderHookIgn = null;
        leavers.clear();
        builderLeftLeavers.clear();
        authoritativeLowRosterLeavers.clear();
        pendingTabReturnConfirmations.clear();
        resetAuthoritativeLowRosterCandidate();
        midGameUnattributedResidual.clear();
        lastSidebarScores.clear();
        sidebarDropKnownScores.clear();
        sidebarDropMissingTicks.clear();
        sidebarDropMissingScores.clear();
        previousScoreboardOnlyPlayers.clear();
        sidebarTrackingRound = null;
        RawPacketRecorder.INSTANCE.finishSession();
        players.clear();
        rankings.clear();
        currentRound = null;
        clearPendingSelfGuessScore();
        resetPhysicalTimerTracking();
        PlayerMetaCache.INSTANCE.clearHarvestedRanks();
        lobbyQuitSuppress.clear();

        if (!completed) clearAutoRequeueSchedule();
    }

    private boolean handleLanguageSelection(String text, LangPatterns lp) {
        if (lp.langSelectPrefix == null || !text.startsWith(lp.langSelectPrefix)) return false;
        String name = null;
        if (lp.langSelectSuffix != null) {
            String rest = text.substring(lp.langSelectPrefix.length());
            if (rest.endsWith(lp.langSelectSuffix)) {
                name = rest.substring(0, rest.length() - lp.langSelectSuffix.length()).trim();
            }
        } else {
            name = text.substring(lp.langSelectPrefix.length()).trim();
        }
        if (name == null || name.isEmpty()) return false;

        disableLobbyLanguageFallback();
        currentLanguage = name;
        String code = LangPatterns.codeFromDisplayName(name);
        currentLangCode = code != null ? code : lp.lang;
        persistCurrentLanguageCode();
        // A preliminary ROUND_PRE record represents the upcoming round, so a
        // selection made before its Round: X/Y header replaces the language
        // captured with that placeholder. Active rounds keep their start language.
        if (currentRound != null
                && state == TrackingState.ROUND_PRE
                && currentRound.roundNumber == 0
                && currentRound.roundEnd <= 0L) {
            currentRound.language = currentLanguage;
            currentRound.langCode = currentLangCode;
        }
        return true;
    }

    private void applyLobbyLanguageFallback(String langCode) {
        if (langCode == null) return;
        currentLangCode = langCode;
        String display = LangPatterns.mapLanguageName(langCode);
        if (display != null) currentLanguage = display;
        persistCurrentLanguageCode();
        if (currentRound != null) {
            currentRound.langCode = currentLangCode;
            if (currentLanguage != null) currentRound.language = currentLanguage;
        }
    }

    private void disableLobbyLanguageFallback() {
        lobbyLanguageFallback.disableForServerSession();
    }

    private void maybeAutoDetectLanguage(LangPatterns lp) {
        if (lp == null || lp.lang == null) return;
        if (lp.lang.equals(currentLangCode)) return;
        currentLangCode = lp.lang;
        String display = LangPatterns.mapLanguageName(lp.lang);
        if (display != null) currentLanguage = display;
        if (currentRound != null) {
            currentRound.langCode = currentLangCode;
            if (currentLanguage != null) currentRound.language = currentLanguage;
        }
    }

    private void applyScoreboardRoundState(TrackingState sbState, long now) {
        if (state == TrackingState.ROUND_BUILD && sbState == TrackingState.ROUND_PRE) {
            boolean freshRejoinPreRound = currentRound != null
                    && currentRound.roundNumber == -1
                    && currentRound.dataGap
                    && roundHasEvent(currentRound, "rejoin_round_change");
            if (!freshRejoinPreRound) return;
        }
        if (sbState == TrackingState.ROUND_PRE && state != TrackingState.ROUND_PRE) {
            beginPreRound(now, false);
        } else if (sbState == TrackingState.ROUND_BUILD && currentRound != null && isInRound(state)) {
            state = TrackingState.ROUND_BUILD;
        } else if (sbState == TrackingState.ROUND_END && currentRound != null && isInRound(state)) {
            finishCurrentRound(now, false);
        } else if (sbState == TrackingState.POST_GAME && isActiveGame(state)) {
            handleWinners(now);
        }
    }

    private void startScoreboardOnlyRound(TrackingState sbState, List<String> cleanLines, long now) {
        boolean lobbyStart = state == TrackingState.LOBBY || isLobbyHandoffActive(now);
        InitialRoundHeader initialHeader = lobbyStart ? pendingLobbyRoundStart : null;
        long startTime = initialHeader == null ? now : initialHeader.timestamp;
        if (lobbyStart) {
            autoDisableTrollModeAtGameStart();
        } else {
            pendingNextBuilder = null;
        }
        String detectedLang = LangPatterns.detectLanguageFromScoreboard(cleanLines);
        if (detectedLang != null) {
            currentLangCode = detectedLang;
            String display = LangPatterns.mapLanguageName(detectedLang);
            if (display != null) currentLanguage = display;
        }
        seedGameStartRoster(startTime, bestKnownLocalIgn());
        initNewGame(startTime);
        if (!lobbyStart && gtbHooks != null) gtbHooks.onMidGameStart();
        if (initialHeader != null) {
            state = sbState;
            handleRoundStart(1, initialHeader.totalRounds, initialHeader.timestamp);
            return;
        }
        beginPreRound(startTime, !lobbyStart);
        state = sbState;
        if (sbState == TrackingState.ROUND_BUILD && gtbHooks != null) gtbHooks.onRoundBuild();
    }

    private void beginPreRound(long now, boolean dataGap) {
        clearPendingRejoinState();
        if (!gameInitialized) {
            seedGameStartRoster(now, bestKnownLocalIgn());
            initNewGame(now);
        }
        if (currentRound != null && state != TrackingState.ROUND_PRE) {
            if (currentRound.roundEnd <= 0L) currentRound.roundEnd = now;
            boolean skipped = roundHasEvent(currentRound, "builder_left")
                    || roundHasEvent(currentRound, "afk_skip");
            if (skipped && currentRound.roundNumber == 0) currentRound.roundNumber = -1;
            if (gtbHooks != null) gtbHooks.onRoundEnd(skipped);
        }
        currentRound = new RoundData();
        currentRound.roundStart = now;
        currentRound.language = currentLanguage;
        currentRound.langCode = currentLangCode;
        currentRound.dataGap = dataGap;
        if (pendingNextBuilder != null) {
            currentRound.builder = pendingNextBuilder;
            pendingNextBuilder = null;
        }
        rounds.add(currentRound);
        state = TrackingState.ROUND_PRE;
        if (gtbHooks != null) {
            gtbHooks.onRoundPre();
            if (currentRound.builder != null) {
                emitBuilderHook(null, currentRound.builder);
            }
        }
    }

    private void persistCurrentLanguageCode() {
        if (currentLangCode == null) return;
        String normalized = currentLangCode.trim();
        if (normalized.isEmpty()) return;
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        if (!normalized.equals(settings.lastLangCode)) {
            settings.lastLangCode = normalized;
            settings.save();
        }
    }

    private void fireTrueScoresUpdate() {
        if (gtbHooks == null || rankings.isEmpty()) return;
        Map<String, Integer> observedScores = new LinkedHashMap<String, Integer>();
        for (RankEntry ranking : rankings) {
            if (ranking != null && ranking.ign != null) {
                observedScores.put(ranking.ign, ranking.score);
            }
        }
        if (!observedScores.isEmpty()) {
            gtbHooks.onTrueScoresUpdate(observedScores);
        }
    }

    private boolean parseRankings(List<String> cleanLines, long now) {
        if (cleanLines == null || cleanLines.isEmpty()) return false;
        List<RankEntry> next = new ArrayList<RankEntry>();
        for (String line : cleanLines) {
            if (line == null) continue;
            Matcher rankMatcher = RANK_LINE_PATTERN.matcher(line);
            if (!rankMatcher.matches()) continue;

            int place;
            try {
                place = Integer.parseInt(rankMatcher.group(1));
            } catch (NumberFormatException ignored) {
                continue;
            }

            String rest = rankMatcher.group(2).trim();
            String ign = rest;
            int score = 0;
            Matcher scoreMatcher = RANK_SCORE_PATTERN.matcher(rest);
            if (scoreMatcher.matches()) {
                ign = scoreMatcher.group(1).trim();
                try {
                    score = Integer.parseInt(scoreMatcher.group(2));
                } catch (NumberFormatException ignored) {
                    score = 0;
                }
            }

            ign = cleanIgn(ign);
            if (!isValidMcUsername(ign) || isReservedNonPlayerName(ign)) continue;
            next.add(new RankEntry(place, ign, score));
        }
        if (next.isEmpty()) return false;

        sortRankingsByPlace(next);
        repairTruncatedScores(next);
        rankings.clear();
        rankings.addAll(next);
        scheduleAutoRequeue(now, true, true);
        return true;
    }

    private void processRoundSidebar(List<String> cleanLines, long now) {
        if (cleanLines == null) cleanLines = Collections.emptyList();
        if (!resolvePendingRejoinBeforeScores(cleanLines, now)) {
            previousScoreboardOnlyPlayers.clear();
            return;
        }
        syncSidebarTrackingRound();

        Map<String, Integer> currentSidebar = new LinkedHashMap<String, Integer>();
        Set<String> scoreboardOnlyPlayers = new LinkedHashSet<String>();
        String sidebarBuilder = extractBuilderFromScoreboard(cleanLines);
        for (String cleanLine : cleanLines) {
            if (cleanLine == null) continue;
            String line = cleanLine.trim();
            if (line.isEmpty() || line.contains("hypixel.net") || line.startsWith("www.")
                    || line.equals("???") || DATE_FOOTER_PATTERN.matcher(line).matches()
                    || containsSidebarClockToken(line)) {
                continue;
            }
            if (LangPatterns.isScoreboardLabel(line)) continue;

            int colonIdx = line.lastIndexOf(':');
            if (colonIdx <= 0 || colonIdx >= line.length() - 1) continue;
            String ign = cleanIgn(line.substring(0, colonIdx));
            if (!isValidMcUsername(ign) || isReservedNonPlayerName(ign)) continue;
            String scoreText = line.substring(colonIdx + 1).trim();
            try {
                int observed = Integer.parseInt(scoreText);
                boolean knownPlayer = containsPlayerIgnoreCase(ign)
                        || (sidebarBuilder != null && sidebarBuilder.equalsIgnoreCase(ign));
                if (!knownPlayer && !containsIgnoreCase(previousScoreboardOnlyPlayers, ign)) {
                    addIgnoreCase(scoreboardOnlyPlayers, ign);
                    continue;
                }
                addPlayerIfAbsent(ign);
                currentSidebar.put(ign, Integer.valueOf(observed));
                clearLeaverOnLeaderboardReappearance(ign);
                Integer prior = ScoreReconciler.knownPriorCumulative(rounds, ign, currentRound);
                if (prior != null && observed > prior.intValue()) {
                    notePlayerActivity(ign, "scoreboard");
                }
                ScoreReconciler.observeScoreboard(currentRound, ign, observed, prior,
                        midGameUnattributedResidual);
            } catch (NumberFormatException ignored) {
                // Not a live score row.
            }
        }

        if (currentRound.builder == null && sidebarBuilder != null) {
            reassignBuilder(currentRound, sidebarBuilder);
        }

        queueThemePointFromScoreboard();

        String sbTheme = extractThemeFromScoreboard(cleanLines);
        String sbThemeLang = firstNonEmpty(currentLangCode, currentRound.langCode);
        if (sbTheme != null) {
            String resolvedTheme = TranslationData.resolveExactOrUniqueLongDisplayedTheme(
                    sbThemeLang, sbTheme);
            if (resolvedTheme != null) sbTheme = resolvedTheme;
        }
        if (sbTheme != null && currentRound.builder != null) {
            ScoreReconciler.crosscheckBuilderFromScoreboard(currentRound, sbThemeLang, sbTheme);
        }
        if (sbTheme != null && state == TrackingState.ROUND_BUILD) {
            boolean themeChanged = !sbTheme.equals(currentRound.theme);
            currentRound.theme = sbTheme;
            if (!isLocalPlayerBuilder(currentRound)) {
                markCompleteThemeRevealed(currentRound);
            }
            queueShortcutReminder(currentRound, sbTheme, GtbStructuresSettings.INSTANCE, sbThemeLang);
            if (gtbHooks != null) gtbHooks.onThemeUpdate(sbTheme, sbThemeLang);
            queueThemePointFromTheme(sbTheme, sbThemeLang);
            if (themeChanged) writeLiveFile();
        } else if (sbTheme != null && state == TrackingState.ROUND_END
                && gtbHooks != null) {
            // The revealed vanilla Theme row follows the live language even
            // between rounds. Keep the replacement Default panel in sync
            // without replaying round-scoring or notification work.
            gtbHooks.onThemeUpdate(sbTheme, sbThemeLang);
        }

        if (gtbHooks != null && !currentSidebar.isEmpty()
                && !currentSidebar.equals(lastSidebarScores)) {
            gtbHooks.onTrueScoresUpdate(new LinkedHashMap<String, Integer>(currentSidebar));
        }
        detectLeaderboardDrops(currentSidebar);
        rememberSidebarDropScores(sidebarDropKnownScores, currentSidebar);
        observeAuthoritativeLowRosterSnapshot(currentSidebar);
        lastSidebarScores.clear();
        lastSidebarScores.putAll(currentSidebar);
        previousScoreboardOnlyPlayers.clear();
        previousScoreboardOnlyPlayers.addAll(scoreboardOnlyPlayers);
    }

    private boolean resolvePendingRejoinBeforeScores(List<String> cleanLines, long now) {
        if (!pendingRejoinCheck) return true;

        boolean hadRealGameAtPause = pausedBuilder != null || pausedRoundTotal > 0
                || (currentRound != null && currentRound.dataGap);
        if (!hadRealGameAtPause) {
            clearPendingRejoinState();
            return true;
        }

        String sbBuilder = extractBuilderFromScoreboard(cleanLines);
        boolean sameBuilder = pausedBuilder != null && sbBuilder != null
                && sbBuilder.equalsIgnoreCase(pausedBuilder);
        if (sameBuilder) {
            materializePausedPreRoundCandidate(now);
            if (gtbHooks != null) gtbHooks.onUserRejoinSameRound(sbBuilder);
            if (currentRound != null) {
                currentRound.events.add(new GameEvent(now, "rejoin_same_round",
                        "builder=" + sbBuilder));
            }
            clearPendingRejoinState();
            return true;
        }

        boolean builderChanged = pausedBuilder != null && sbBuilder != null
                && !sbBuilder.equalsIgnoreCase(pausedBuilder);
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

    private void syncSidebarTrackingRound() {
        if (sidebarTrackingRound == currentRound) return;
        sidebarTrackingRound = currentRound;
        lastSidebarScores.clear();
        sidebarDropMissingTicks.clear();
        sidebarDropMissingScores.clear();
        previousScoreboardOnlyPlayers.clear();
        resetAuthoritativeLowRosterCandidate();
    }

    private void detectLeaderboardDrops(Map<String, Integer> currentSidebar) {
        boolean fullLeaderboard = currentSidebar != null
                && currentSidebar.size() >= SIDEBAR_DROP_REQUIRED_VISIBLE_ROWS;
        if (!fullLeaderboard || state != TrackingState.ROUND_BUILD || currentRound == null
                || pendingRejoinCheck || localRejoinedImmediatelyStage
                || roundHasEvent(currentRound, "builder_left")
                || roundHasEvent(currentRound, "afk_skip")) {
            sidebarDropMissingTicks.clear();
            sidebarDropMissingScores.clear();
            return;
        }

        seedSidebarDropCandidatesFromKnownScores(
                sidebarDropKnownScores, currentSidebar, sidebarDropMissingTicks,
                sidebarDropMissingScores, SIDEBAR_DROP_REQUIRED_VISIBLE_ROWS);
        Set<String> confirmed = advanceSidebarDropCandidates(
                lastSidebarScores, currentSidebar, sidebarDropMissingTicks,
                sidebarDropMissingScores, SIDEBAR_DROP_REQUIRED_MISSING_TICKS,
                SIDEBAR_DROP_REQUIRED_VISIBLE_ROWS);
        long now = System.currentTimeMillis();
        for (String ign : confirmed) {
            if (!containsPlayerIgnoreCase(ign) || containsIgnoreCase(leavers, ign)) continue;
            if (currentRound.builder != null && currentRound.builder.equalsIgnoreCase(ign)) continue;
            addIgnoreCase(leavers, ign);
            addIgnoreCase(authoritativeLowRosterLeavers, ign);
            currentRound.events.add(new GameEvent(now, "leaderboard_drop_left", ign));
            if (gtbHooks != null) gtbHooks.onConfirmedPlayerLeft(ign);
        }
    }

    private void clearLeaverOnLeaderboardReappearance(String ign) {
        if (ign == null || ign.isEmpty()
                || findKeyIgnoreCase(lastSidebarScores, ign) != null
                || !containsIgnoreCase(leavers, ign)) {
            return;
        }
        clearLeaverFromReturnEvidence(ign, "leaderboard");
    }

    private void observeAuthoritativeLowRosterSnapshot(Map<String, Integer> currentSidebar) {
        if (gtbHooks == null || state != TrackingState.ROUND_BUILD || currentRound == null
                || currentSidebar == null || currentSidebar.isEmpty()) {
            resetAuthoritativeLowRosterCandidate();
            return;
        }

        Set<String> presentPlayers = new LinkedHashSet<String>();
        for (Map.Entry<String, Integer> row : currentSidebar.entrySet()) {
            String ign = row.getKey();
            if (row.getValue() == null || row.getValue().intValue() < 0) {
                resetAuthoritativeLowRosterCandidate();
                return;
            }
            if (isValidMcUsername(ign)) addIgnoreCase(presentPlayers, ign);
        }
        if (presentPlayers.isEmpty() || presentPlayers.size() > 2) {
            resetAuthoritativeLowRosterCandidate();
            return;
        }

        // Sidebar teams redraw incrementally. Require the same row set across
        // several ticks before a small board can be treated as the full roster.
        if (!samePlayersIgnoreCase(lowRosterCandidatePlayers, presentPlayers)) {
            lowRosterCandidatePlayers.clear();
            lowRosterCandidatePlayers.addAll(presentPlayers);
            lowRosterCandidateTicks = 1;
            return;
        }
        if (lowRosterCandidateTicks < AUTHORITATIVE_LOW_ROSTER_STABLE_TICKS) {
            lowRosterCandidateTicks++;
        }
        if (lowRosterCandidateTicks < AUTHORITATIVE_LOW_ROSTER_STABLE_TICKS) return;

        for (String present : presentPlayers) {
            clearLeaverFromReturnEvidence(present, "leaderboard");
        }
        long now = System.currentTimeMillis();
        for (String tracked : players) {
            if (!isValidMcUsername(tracked) || containsIgnoreCase(presentPlayers, tracked)) continue;
            if (!containsIgnoreCase(leavers, tracked)) {
                currentRound.events.add(new GameEvent(now, "leaderboard_drop_left", tracked));
            }
            addIgnoreCase(leavers, tracked);
            addIgnoreCase(authoritativeLowRosterLeavers, tracked);
        }
        gtbHooks.onAuthoritativeLowRosterSnapshot(
                new LinkedHashSet<String>(presentPlayers));
    }

    private static boolean samePlayersIgnoreCase(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.size() != right.size()) return false;
        for (String player : left) {
            if (!containsIgnoreCase(right, player)) return false;
        }
        return true;
    }

    private void resetAuthoritativeLowRosterCandidate() {
        lowRosterCandidatePlayers.clear();
        lowRosterCandidateTicks = 0;
    }

    private void resetLowRosterCandidateForReturnEvidence(String ign) {
        if (!lowRosterCandidatePlayers.isEmpty()
                && !containsIgnoreCase(lowRosterCandidatePlayers, ign)) {
            resetAuthoritativeLowRosterCandidate();
        }
    }

    private String extractBuilderFromScoreboard(List<String> cleanLines) {
        if (cleanLines == null) return null;
        for (int i = 1; i < cleanLines.size(); i++) {
            String line = cleanLines.get(i);
            if (line == null) continue;
            for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
                if (lp.sbBuilder != null && line.startsWith(lp.sbBuilder)) {
                    String prev = cleanLines.get(i - 1);
                    prev = prev == null ? "" : prev.trim();
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

    private String extractThemeFromScoreboard(List<String> cleanLines) {
        if (cleanLines == null) return null;
        for (int i = 1; i < cleanLines.size(); i++) {
            String line = cleanLines.get(i);
            if (line == null) continue;
            for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
                if (lp.sbTheme != null && line.startsWith(lp.sbTheme)) {
                    String prev = cleanLines.get(i - 1);
                    prev = prev == null ? "" : prev.trim();
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

    private String scanSidebarTimer(List<String> cleanLines) {
        for (LangPatterns pattern : LangPatterns.ALL_PATTERNS) {
            if (currentLangCode != null && currentLangCode.equals(pattern.lang)) {
                String value = matchTimerLine(cleanLines, pattern);
                if (value != null) return value;
                break;
            }
        }
        for (LangPatterns pattern : LangPatterns.ALL_PATTERNS) {
            if (currentLangCode != null && currentLangCode.equals(pattern.lang)) continue;
            String value = matchTimerLine(cleanLines, pattern);
            if (value != null) return value;
        }
        return null;
    }

    static String matchTimerLine(List<String> cleanLines, LangPatterns pattern) {
        if (pattern == null || cleanLines == null) return null;
        for (String line : cleanLines) {
            if (line == null || line.isEmpty()) continue;
            if (pattern.sbTime != null && line.startsWith(pattern.sbTime)) {
                String value = line.substring(pattern.sbTime.length()).trim();
                if (!value.isEmpty()) return value;
            }
            if (LangPatterns.startsWithRoundEndLabel(line, pattern.sbNextRound)) {
                String value = line.substring(pattern.sbNextRound.length()).trim();
                if (!value.isEmpty()) return value;
            }
            if (pattern.sbStartsIn != null) {
                if (pattern.sbStartsInIsSuffix) {
                    int index = line.indexOf(pattern.sbStartsIn);
                    if (index > 0) {
                        String value = line.substring(0, index).trim();
                        if (!value.isEmpty()) return value;
                    }
                } else if (line.startsWith(pattern.sbStartsIn)) {
                    String value = line.substring(pattern.sbStartsIn.length()).trim();
                    if (!value.isEmpty()) return value;
                }
            }
        }
        return null;
    }

    static boolean containsSidebarClockToken(String cleanLine) {
        return cleanLine != null && SIDEBAR_CLOCK_TOKEN_PATTERN.matcher(cleanLine).matches();
    }

    private static void sortRankingsByPlace(List<RankEntry> values) {
        Collections.sort(values, new java.util.Comparator<RankEntry>() {
            @Override
            public int compare(RankEntry a, RankEntry b) {
                return a.place - b.place;
            }
        });
    }

    static void repairTruncatedScores(List<RankEntry> values) {
        if (values == null || values.size() < 2) return;

        int i = 0;
        while (i < values.size()) {
            int j = i + 1;
            int groupMax = values.get(i).score;
            while (j < values.size() && values.get(j).place == values.get(i).place) {
                if (values.get(j).score > groupMax) groupMax = values.get(j).score;
                j++;
            }
            if (groupMax > 0) {
                for (int k = i; k < j; k++) {
                    RankEntry ranking = values.get(k);
                    if (ranking.score > 0 && ranking.score < groupMax) {
                        int repaired = extendPrefixToAtLeast(ranking.score, groupMax);
                        if (repaired > ranking.score) ranking.score = repaired;
                    }
                }
            }
            i = j;
        }

        int lowerBound = Integer.MIN_VALUE;
        for (int k = values.size() - 1; k >= 0; k--) {
            RankEntry current = values.get(k);
            if (current.score > 0 && current.score < lowerBound) {
                int repaired = extendPrefixToAtLeast(current.score, lowerBound);
                if (repaired > current.score) current.score = repaired;
            }
            if (current.score > lowerBound) lowerBound = current.score;
        }
    }

    static int extendPrefixToAtLeast(int prefix, int minValue) {
        if (prefix >= minValue) return prefix;
        if (prefix <= 0) return prefix;
        int value = prefix;
        int extraDigits = 0;
        while (value < minValue && extraDigits < 6) {
            value *= 10;
            extraDigits++;
            if (minValue <= value) return value;
            int suffixMax = intPow10(extraDigits) - 1;
            if (minValue <= value + suffixMax) return minValue;
        }
        return prefix;
    }

    private static int intPow10(int exponent) {
        int result = 1;
        for (int i = 0; i < exponent; i++) result *= 10;
        return result;
    }

    private void finishCurrentRound(long now, boolean skipped) {
        boolean newlyEnded = currentRound != null && currentRound.roundEnd <= 0L;
        if (currentRound != null && newlyEnded) {
            currentRound.roundEnd = now;
        }
        state = TrackingState.ROUND_END;
        if (newlyEnded && gtbHooks != null) {
            gtbHooks.onRoundEnd(skipped);
        }
    }

    private void handleBuilderLeft(long now, String detail) {
        if (currentRound == null && pendingNextBuilder != null) {
            if (!gameInitialized) initNewGame(now);
            currentRound = new RoundData();
            currentRound.roundNumber = -1;
            currentRound.roundStart = now;
            currentRound.builder = pendingNextBuilder;
            currentRound.language = currentLanguage;
            currentRound.langCode = currentLangCode;
            rounds.add(currentRound);
            pendingNextBuilder = null;
            state = TrackingState.ROUND_PRE;
            if (gtbHooks != null) gtbHooks.onRoundPre();
            emitBuilderHook(null, currentRound.builder);
        }
        if (currentRound == null) return;
        if (currentRound.roundEnd <= 0L) currentRound.roundEnd = now;
        currentRound.events.add(new GameEvent(now, "builder_left", detail));
        zeroOutBuilderDeltaIfNoGuesses(currentRound, "builder_left");
        if (currentRound.builder != null && !currentRound.builder.isEmpty()) {
            removeIgnoreCase(pendingTabReturnConfirmations, currentRound.builder);
            addIgnoreCase(leavers, currentRound.builder);
            addIgnoreCase(builderLeftLeavers, currentRound.builder);
        }
        if (gtbHooks != null) {
            gtbHooks.onRoundSkipped();
            gtbHooks.onRoundEnd(true);
        }
        currentRound = null;
    }

    private void handleRejoin(String variant, String builder, String theme, long timestamp) {
        if (state == TrackingState.PAUSED) {
            resumePausedRoundFromLiveSignal(timestamp, true);
        }
        if (!pendingRejoinCheck) return;
        String cleanBuilder = builder == null ? null : cleanIgn(builder);
        if ("guesser".equals(variant) && isValidMcUsername(cleanBuilder)) {
            resolveRejoinMessageBuilder(cleanBuilder, timestamp);
        } else if ("builder".equals(variant) && currentRound != null) {
            String cleanTheme = theme == null ? "" : theme.trim();
            boolean themeChanged = !cleanTheme.isEmpty()
                    && !cleanTheme.equals(currentRound.theme);
            if (themeChanged) currentRound.theme = cleanTheme;
            clearPendingRejoinState();
            if (themeChanged) writeLiveFile();
        }
    }

    private void resolveRejoinMessageBuilder(String builder, long timestamp) {
        if (currentRound == null) {
            clearPendingRejoinState();
            return;
        }
        String oldBuilder = pausedBuilder;
        if ((oldBuilder == null || oldBuilder.isEmpty()) && currentRound.dataGap) {
            oldBuilder = currentRound.builder;
        }
        boolean hadRealGameAtPause = oldBuilder != null || pausedRoundTotal > 0 || currentRound.dataGap;
        if (!hadRealGameAtPause) return;
        if ((oldBuilder == null || oldBuilder.isEmpty())
                && pausedState == TrackingState.ROUND_PRE && pausedNextRoundCandidate > 0) {
            materializePausedPreRoundCandidate(timestamp);
            reassignBuilder(currentRound, builder);
            currentRound.events.add(new GameEvent(timestamp, "rejoin_same_round",
                    "builder=" + builder + ",round=inferred"));
            clearPendingRejoinState();
            return;
        }
        if (oldBuilder == null || oldBuilder.isEmpty()) return;
        if (oldBuilder != null && oldBuilder.equalsIgnoreCase(builder)) {
            materializePausedPreRoundCandidate(timestamp);
            currentRound.events.add(new GameEvent(timestamp, "rejoin_same_round",
                    "builder=" + builder));
            clearPendingRejoinState();
            if (gtbHooks != null) gtbHooks.onUserRejoinSameRound(builder);
            return;
        }

        if (pausedBuilder == null || pausedBuilder.isEmpty()) pausedBuilder = oldBuilder;
        if (pausedRoundTotal == 0) pausedRoundTotal = currentRound.totalRounds;
        startRejoinGapRound(timestamp, builder, "rejoin_message_builder_changed",
                inferredRoundForChangedBuilder());
        clearPendingRejoinState();
    }

    private void startRejoinGapRound(long now, String builder, String reason, int inferredRound) {
        String oldBuilder = pausedBuilder;
        RoundData previous = currentRound;
        List<GameEvent> movedBoundaryEvents = inferredRound > 0
                ? detachBoundaryLeaveEvents(previous) : Collections.<GameEvent>emptyList();
        if (previous != null && previous.roundEnd <= 0L) previous.roundEnd = now;
        if (inferredRound > 0 && gtbHooks != null) gtbHooks.onRoundEnd(false);
        RoundData gap = new RoundData();
        gap.roundNumber = inferredRound > 0 ? inferredRound : -1;
        gap.totalRounds = pausedRoundTotal > 0 ? pausedRoundTotal
                : previous == null ? 0 : previous.totalRounds;
        gap.roundStart = now;
        gap.builder = isValidMcUsername(builder) ? builder : null;
        gap.language = currentLanguage;
        gap.langCode = currentLangCode;
        gap.dataGap = true;
        gap.events.addAll(movedBoundaryEvents);
        gap.events.add(new GameEvent(now, "rejoin_round_change",
                "reason=" + reason + ",pausedBuilder=" + oldBuilder + ",newBuilder=" + builder
                        + (inferredRound > 0 ? ",inferredRound=" + inferredRound : "")));
        rounds.add(gap);
        currentRound = gap;
        if (inferredRound > 0 && gtbHooks != null) {
            gtbHooks.onInferredRoundStart(inferredRound, gap.totalRounds, true);
        }
        if (gap.builder != null) emitBuilderHook(oldBuilder, gap.builder);
    }

    private int inferredRoundForChangedBuilder() {
        return pausedState == TrackingState.ROUND_END ? pausedNextRoundCandidate : 0;
    }

    private void materializePausedPreRoundCandidate(long now) {
        if (pausedState != TrackingState.ROUND_PRE || pausedNextRoundCandidate <= 0
                || currentRound == null) return;
        if (currentRound.roundNumber > 0 && currentRound.roundNumber != pausedNextRoundCandidate) return;
        currentRound.roundNumber = pausedNextRoundCandidate;
        if (currentRound.totalRounds <= 0) currentRound.totalRounds = pausedRoundTotal;
        currentRound.dataGap = true;
        localRejoinedImmediatelyStage = false;
        currentRound.events.add(new GameEvent(now, "round_inferred",
                "reason=same_builder_pre_rejoin,round=" + pausedNextRoundCandidate));
        if (gtbHooks != null) {
            gtbHooks.onInferredRoundStart(pausedNextRoundCandidate, currentRound.totalRounds, true);
        }
    }

    private List<GameEvent> detachBoundaryLeaveEvents(RoundData round) {
        List<GameEvent> moved = new ArrayList<GameEvent>();
        if (round == null || round.events == null) return moved;
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
        localRejoinedImmediatelyStage = false;
        pausedLeaveCommitted = false;
        serverSessionBoundaryPending = false;
        pausedBuilder = null;
        pausedRoundTotal = 0;
        pausedState = null;
        pausedNextRoundCandidate = 0;
        pausedRoundHadDataGapBeforeLeave = false;
    }

    private void resetSidebarObservationEpoch() {
        lastSidebarScores.clear();
        sidebarDropKnownScores.clear();
        sidebarDropMissingTicks.clear();
        sidebarDropMissingScores.clear();
        previousScoreboardOnlyPlayers.clear();
        resetAuthoritativeLowRosterCandidate();
    }

    private void initNewGame(long now) {
        clearAutoRequeueSchedule();
        playerUuid = launchPlayerUuid;
        String localIgn = bestKnownLocalIgn();
        if (isValidMcUsername(localIgn)) {
            setLocalPlayerIdentity(localIgn);
            // A non-empty lobby/game roster is server evidence. Do not invent a
            // second row from the client account name when that name is absent
            // from the server roster; nicked clients can keep exposing the true
            // profile name locally. An empty roster still needs a local fallback.
            if (players.isEmpty()) addPlayerIfAbsent(localIgn);
        }
        gameInitialized = true;
        gameEnded = false;
        gameStart = now;
        activeTimeMs = 0L;
        activeTimeStartedAt = now;
        scheduledEndGameTime = 0L;
        lastUnknownResidual.clear();
        midGameUnattributedResidual.clear();
        lastSidebarScores.clear();
        sidebarDropKnownScores.clear();
        sidebarDropMissingTicks.clear();
        sidebarDropMissingScores.clear();
        previousScoreboardOnlyPlayers.clear();
        sidebarTrackingRound = null;
        authoritativeLowRosterLeavers.clear();
        pendingTabReturnConfirmations.clear();
        resetAuthoritativeLowRosterCandidate();
        clearPendingSelfGuessScore();
        RawPacketRecorder.INSTANCE.startSession(now);
        if (gtbHooks != null) {
            gtbHooks.onGameStart(new ArrayList<String>(players), localPlayer);
        }
    }

    private boolean handleLobbyJoinMessage(String raw, String clean, long now) {
        String ign = parseLobbyJoinIgn(clean);
        if (ign == null) return false;
        Integer announcedCount = parseLobbyAnnouncedPlayerCount(clean);
        disableLobbyLanguageFallback();

        if (isLocalPlayerIgn(ign)) {
            finalizeForSelfLobbyJoin(now);
            resetFreshLobbyRoster();
            setLocalPlayerIdentity(ign);
            state = TrackingState.LOBBY;
            gtbLobbyRosterConfirmed = true;
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
            clearAutoRequeueSchedule();
            endGame(completed, completed ? null : "new_gtb_lobby", now);
            return;
        }
        finalizePendingCurrent(runtimeStatsDir(), "new_gtb_lobby", now);
    }

    public boolean isLocalPlayerIdentity(String ign) {
        return isLocalPlayerIgn(ign);
    }

    private boolean isLocalPlayerIgn(String ign) {
        return ign != null && (ign.equalsIgnoreCase(localPlayer)
                || ign.equalsIgnoreCase(trueIgn)
                || ign.equalsIgnoreCase(nick)
                || ign.equalsIgnoreCase(lastLocalIgn));
    }

    private String bestKnownLocalIgn() {
        if (isValidMcUsername(localPlayer)) return localPlayer;
        if (isValidMcUsername(nick)) return nick;
        if (isValidMcUsername(lastLocalIgn)) return lastLocalIgn;
        return isValidMcUsername(trueIgn) ? trueIgn : null;
    }

    private void setLocalPlayerIdentity(String activeIgn) {
        localPlayer = isValidMcUsername(activeIgn) ? activeIgn : null;
        if (localPlayer != null) lastLocalIgn = localPlayer;
        refreshIdentityMetadata();
        recordLocalBuildOwner(currentRound, currentRound == null ? null : currentRound.builder);
    }

    private void refreshIdentityMetadata() {
        boolean distinct = isValidMcUsername(trueIgn)
                && isValidMcUsername(localPlayer)
                && !trueIgn.equalsIgnoreCase(localPlayer);
        isNicked = distinct;
        nick = distinct ? localPlayer : null;
    }

    private void recordLobbyJoin(String ign, String raw) {
        if (!isValidMcUsername(ign)) return;
        removeIgnoreCase(lobbyPacketRemovals, ign);
        PlayerMetaCache.INSTANCE.harvestJoinMessageRank(ign, raw);
        lobbyQuitSuppress.remove(ign.toLowerCase(Locale.ROOT));
        addPlayerIfAbsent(ign);
    }

    private void resetFreshLobbyRoster() {
        gtbLobbyRosterConfirmed = false;
        clearLobbyHandoff();
        pendingNextBuilder = null;
        lobbyTabDone = false;
        notInGameTicks = 0;
        players.clear();
        rounds.clear();
        currentRound = null;
        clearPendingSelfGuessScore();
        resetPhysicalTimerTracking();
        rankings.clear();
        pendingTabReturnConfirmations.clear();
        PlayerMetaCache.INSTANCE.clearHarvestedRanks();
        lobbyQuitSuppress.clear();
        clearLobbyPacketRemovalEvidence();
    }

    private void expireLobbyHandoff(long now) {
        if (state == TrackingState.NOT_IN_GAME && gtbHandoffRosterUntilMs > 0L
                && now > gtbHandoffRosterUntilMs) {
            clearLobbyHandoff();
            pendingNextBuilder = null;
            PlayerMetaCache.INSTANCE.clearRosterEvidence();
        }
    }

    private void clearLobbyHandoff() {
        gtbHandoffRosterUntilMs = 0L;
        handoffLobbyRoster.clear();
        pendingLobbyRoundStart = null;
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

    private void pruneLobbyRosterToAnnouncedCount() {
        if (lobbyAnnouncedPlayerCount <= 0
                || players.size() <= lobbyAnnouncedPlayerCount
                || lobbyPacketRemovals.isEmpty()) {
            return;
        }
        Iterator<String> removals = lobbyPacketRemovals.iterator();
        while (players.size() > lobbyAnnouncedPlayerCount && removals.hasNext()) {
            removePlayerIgnoreCaseFromRoster(removals.next());
            removals.remove();
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
            if (!isValidMcUsername(ign) || isLobbyQuitSuppressed(ign)) continue;
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

    private void reassignBuilder(RoundData round, String builder) {
        if (round == null || !isValidMcUsername(builder)) return;
        String previous = round.builder;
        if (previous != null && !previous.equalsIgnoreCase(builder)) {
            round.builderThemeOptions = null;
            ScoreDelta oldDelta = round.scoreDeltas.get(previous);
            if (oldDelta != null && "builder_gui".equals(oldDelta.source)) {
                round.scoreDeltas.remove(previous);
            }
        }
        round.builder = builder;
        recordLocalBuildOwner(round, builder);
        if ((previous == null && builder != null)
                || (previous != null && !previous.equalsIgnoreCase(builder))) {
            emitBuilderHook(previous, builder);
        }
    }

    private void recordLocalBuildOwner(RoundData round, String builder) {
        if (round != null && builder != null && !round.localBuildOwner && isLocalPlayerIgn(builder)) {
            round.localBuildOwner = true;
        }
    }

    private boolean shouldBufferNextBuilder(String builder) {
        return currentRound != null
                && currentRound.theme != null
                && !currentRound.theme.isEmpty()
                && currentRound.builder != null
                && !currentRound.builder.equalsIgnoreCase(builder);
    }

    private void emitBuilderHook(String previous, String current) {
        if (gtbHooks == null) return;
        gtbHooks.onBuilderChange(previous, current);
        lastBuilderHookRound = currentRound;
        lastBuilderHookIgn = current;
    }

    private void zeroOutBuilderDeltaIfNoGuesses(RoundData round, String eventType) {
        if (round == null || round.builder == null || round.builder.isEmpty()) return;
        if (round.correctGuesses != null && !round.correctGuesses.isEmpty()) return;
        round.scoreDeltas.put(round.builder, ScoreDelta.definite(0, eventType));
    }

    private void notePlayerActivity(String ign, String source) {
        if (!isValidMcUsername(ign)) return;
        resetLowRosterCandidateForReturnEvidence(ign);
        clearLeaverFromReturnEvidence(ign, source);
    }

    private void noteTabPresence(String ign) {
        if (!isValidMcUsername(ign)) return;
        resetLowRosterCandidateForReturnEvidence(ign);
        clearLeaverFromTabEvidence(ign);
    }

    private boolean clearLeaverFromTabEvidence(String ign) {
        boolean builderLeft = containsIgnoreCase(builderLeftLeavers, ign);
        if (!builderLeft && !containsIgnoreCase(authoritativeLowRosterLeavers, ign)) {
            removeIgnoreCase(pendingTabReturnConfirmations, ign);
            return false;
        }
        if (!builderLeft) {
            return clearLeaverFromReturnEvidence(ign, "tab");
        }
        if (!containsIgnoreCase(pendingTabReturnConfirmations, ign)) {
            addIgnoreCase(pendingTabReturnConfirmations, ign);
            return false;
        }
        removeIgnoreCase(pendingTabReturnConfirmations, ign);
        return clearLeaverFromReturnEvidence(ign, "tab");
    }

    private boolean clearLeaverFromReturnEvidence(String ign, String source) {
        removeIgnoreCase(pendingTabReturnConfirmations, ign);
        boolean wasLeaver = removeIgnoreCase(leavers, ign);
        boolean wasBuilderLeftLeaver = removeIgnoreCase(builderLeftLeavers, ign);
        boolean wasLowRosterLeaver = removeIgnoreCase(authoritativeLowRosterLeavers, ign);
        if (wasLeaver || wasBuilderLeftLeaver || wasLowRosterLeaver) {
            addPlayerRejoinEvent(ign, source);
            if (gtbHooks != null) gtbHooks.onPlayerRejoin(ign);
            return true;
        }
        return false;
    }

    private boolean isRosterTrackingState() {
        return state == TrackingState.ROUND_PRE
                || state == TrackingState.ROUND_BUILD
                || state == TrackingState.ROUND_END;
    }

    private void addPlayerRejoinEvent(String ign, String source) {
        if (currentRound == null || hasPlayerRejoinEvent(ign)) return;
        currentRound.events.add(new GameEvent(System.currentTimeMillis(),
                "player_rejoin", "ign=" + ign + ",source=" + source));
    }

    private boolean hasPlayerRejoinEvent(String ign) {
        if (currentRound == null || ign == null) return false;
        for (GameEvent event : currentRound.events) {
            if (!"player_rejoin".equals(event.type) || event.detail == null) continue;
            if (event.detail.toLowerCase().contains(("ign=" + ign).toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static boolean roundHasEvent(RoundData round, String type) {
        if (round == null || type == null) return false;
        for (GameEvent event : round.events) {
            if (event != null && type.equals(event.type)) return true;
        }
        return false;
    }

    private static boolean hasCorrectGuess(RoundData round, String ign) {
        if (round == null || ign == null) return false;
        for (CorrectGuessEntry entry : round.correctGuesses) {
            if (entry != null && entry.ign != null && entry.ign.equalsIgnoreCase(ign)) {
                return true;
            }
        }
        return false;
    }

    private void captureShowAllMatchingHint(RoundData round, String trimmedHint) {
        ShowAllMatching.RoundState state = showAllMatchingState(round);
        ShowAllMatching.captureInitialHint(state, trimmedHint);
        copyShowAllMatchingState(round, state);
    }

    private void queueThemePointFromHint(RoundData round) {
        if (round == null) return;
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        ThemePointReveal.RoundState state = themePointRevealState(round);
        String message = ThemePointReveal.maybeBuildFromHint(state,
                settings.themePointReveal, settings.themePointRevealShowAfterGuess);
        copyThemePointRevealState(round, state);
        if (message != null && !message.isEmpty()) notificationMessages.add(message);
    }

    private void queueThemePointFromTheme(String displayedTheme,
                                          String liveLangCode) {
        if (currentRound == null) return;
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        GtbGame game = GameTracker.INSTANCE.game;
        Collection<String> ruledOut = Collections.emptySet();
        if (game != null && currentRound.roundNumber > 0
                && game.currentRound == currentRound.roundNumber) {
            ruledOut = game.ruledOutEnglishThemesThisRound;
        }
        ThemePointReveal.RoundState state = themePointRevealState(currentRound);
        String message = ThemePointReveal.maybeBuildFromTheme(state, displayedTheme,
                liveLangCode, ruledOut, currentBuilderScoreDelta(),
                settings.themePointReveal, settings.themePointRevealShowAfterGuess);
        copyThemePointRevealState(currentRound, state);
        if (message != null && !message.isEmpty()) notificationMessages.add(message);
    }

    private void queueThemePointFromScoreboard() {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        if (currentRound == null || currentRound.themePointRevealSent
                || !settings.themePointReveal) return;
        ThemePointReveal.RoundState state = themePointRevealState(currentRound);
        String message = ThemePointReveal.maybeBuildFromScoreDelta(state,
                currentBuilderScoreDelta(), true,
                settings.themePointRevealShowAfterGuess);
        copyThemePointRevealState(currentRound, state);
        if (message != null && !message.isEmpty()) notificationMessages.add(message);
    }

    private ScoreDelta currentBuilderScoreDelta() {
        if (currentRound == null || currentRound.builder == null) return null;
        String builderKey = findKeyIgnoreCase(currentRound.scoreDeltas, currentRound.builder);
        return builderKey == null
                ? null : currentRound.scoreDeltas.get(builderKey);
    }

    private ThemePointReveal.RoundState themePointRevealState(RoundData round) {
        ThemePointReveal.RoundState state = new ThemePointReveal.RoundState();
        if (round == null) return state;
        state.langCode = firstNonEmpty(round.langCode, currentLangCode);
        state.hintStructure = round.hintStructure;
        state.builderThemeOptions = round.builderThemeOptions;
        state.acceptedGuessTexts = round.acceptedGuessTexts;
        state.selfBuildRound = isLocalPlayerBuilder(round);
        state.completeThemeRevealedToUser = round.completeThemeRevealedToUser;
        state.themePointRevealSent = round.themePointRevealSent;
        return state;
    }

    private static void copyThemePointRevealState(RoundData round,
                                                   ThemePointReveal.RoundState state) {
        if (round == null || state == null) return;
        round.completeThemeRevealedToUser = state.completeThemeRevealedToUser;
        round.themePointRevealSent = state.themePointRevealSent;
    }

    private void markCompleteThemeRevealed(RoundData round) {
        if (round == null) return;
        ThemePointReveal.RoundState state = themePointRevealState(round);
        ThemePointReveal.markCompleteThemeRevealed(state);
        copyThemePointRevealState(round, state);
    }

    private boolean isLocalPlayerBuilder(RoundData round) {
        if (round == null) return false;
        if (round.localBuildOwner) return true;
        if (round.builder == null) return false;
        if (isLocalPlayerIgn(round.builder)) {
            round.localBuildOwner = true;
            return true;
        }
        GtbGame game = GameTracker.INSTANCE.game;
        boolean localBuilder = game != null && game.currentBuilder != null
                && game.currentBuilder.isUser
                && game.currentBuilder.name != null
                && round.builder.equalsIgnoreCase(game.currentBuilder.name);
        if (localBuilder) round.localBuildOwner = true;
        return localBuilder;
    }

    private void queueRoundRevealNotifications(RoundData round, String displayedTheme) {
        if (round == null) return;
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        queueShortcutReminder(round, displayedTheme, settings);
        queueShowAllMatching(round, settings);
    }

    private void queueShortcutReminder(RoundData round, String displayedTheme,
                                       GtbStructuresSettings settings) {
        queueShortcutReminder(round, displayedTheme, settings,
                firstNonEmpty(round.langCode, currentLangCode));
    }

    private void queueShortcutReminder(RoundData round, String displayedTheme,
                                       GtbStructuresSettings settings, String langCode) {
        ShortcutReminder.RoundState state = new ShortcutReminder.RoundState();
        state.langCode = langCode;
        state.theme = firstNonEmpty(displayedTheme, round.theme);
        state.shortcutReminderSent = round.shortcutReminderSent;

        List<String> messages = ShortcutReminder.prepareMessages(
                shortcutReminderOptions(settings), state, state.theme);
        round.shortcutReminderSent = state.shortcutReminderSent;
        notificationMessages.addAll(messages);
    }

    private void queueShowAllMatching(RoundData round, GtbStructuresSettings settings) {
        ShowAllMatching.RoundState state = showAllMatchingState(round);
        String message = ShowAllMatching.maybeBuildMessage(settings.showAllMatching,
                state, localPlayer, settings.showAllMatchingShowEnglish);
        copyShowAllMatchingState(round, state);
        if (message != null && !message.isEmpty()) {
            notificationMessages.add(message);
        }
    }

    private ShowAllMatching.RoundState showAllMatchingState(RoundData round) {
        ShowAllMatching.RoundState state = new ShowAllMatching.RoundState();
        if (round == null) return state;
        state.langCode = firstNonEmpty(round.langCode, currentLangCode);
        state.builder = round.builder;
        state.effectiveInitialHint = round.effectiveInitialHint;
        state.themeCharCount = round.themeCharCount;
        state.showAllMatchingSent = round.showAllMatchingSent;
        return state;
    }

    private static void copyShowAllMatchingState(RoundData round, ShowAllMatching.RoundState state) {
        if (round == null || state == null) return;
        round.effectiveInitialHint = state.effectiveInitialHint;
        round.showAllMatchingSent = state.showAllMatchingSent;
    }

    private static ShortcutReminder.Options shortcutReminderOptions(GtbStructuresSettings settings) {
        ShortcutReminder.Options options = new ShortcutReminder.Options();
        if (settings == null) return options;
        options.enabled = settings.shortcutReminder;
        options.showAllShortcuts = settings.shortcutReminderShowAllShortcuts;
        options.showMultiwords = settings.shortcutReminderShowMultiwords;
        options.showAllMultiwords = settings.shortcutReminderShowAllMultiwords;
        options.showNonAscii = settings.shortcutReminderShowNonAscii;
        options.showExtendedLatin = settings.shortcutReminderShowExtendedLatin;
        options.showCyrillic = settings.shortcutReminderShowCyrillic;
        options.showHanzi = settings.shortcutReminderShowHanzi;
        options.showJapanese = settings.shortcutReminderShowJapanese;
        options.showHangul = settings.shortcutReminderShowHangul;
        options.languageGroups = ShortcutLanguageGroups.snapshot();
        return options;
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && !first.trim().isEmpty()) return first;
        return second != null && !second.trim().isEmpty() ? second : null;
    }

    private void observeGuessSoundChat(String clean, long now) {
        if (!GtbStructuresSettings.INSTANCE.modEnabled) {
            GuessSoundController.INSTANCE.reset();
            EndGameFireworkController.INSTANCE.reset();
            return;
        }
        MinecraftClientSoundBridge.ensureInstalled();
        if (clean == null || isNonGameChatChannel(clean)) return;
        String guesser = LangPatterns.stripGuesserChatPrefix(clean);
        if (guesser != null || (clean.contains(": ")
                && LangPatterns.startsWithChatTitle(LangPatterns.stripEmojis(clean).trim()))) return;

        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (lp.winnersKeyword != null && clean.trim().equals(lp.winnersKeyword)) {
                EndGameFireworkController.INSTANCE.observeWinnersNotice();
                break;
            }
        }

        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (lp.countdown1Sec != null && clean.equals(lp.countdown1Sec)) {
                GuessSoundController.INSTANCE.onRoundBoundary();
                EndGameFireworkController.INSTANCE.onRoundBoundary();
                break;
            }
            if (lp.roundPrefix != null && clean.startsWith(lp.roundPrefix)) {
                GuessSoundController.INSTANCE.onRoundBoundary();
                EndGameFireworkController.INSTANCE.onRoundBoundary();
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
            String ign = extractCorrectGuessIgn(clean, lp);
            if (ign != null && isLocalPlayerIgn(ign)) {
                MinecraftClientSoundBridge.ensureCatalog();
                // The initial native resource lookup may load classes before the token is armed.
                GuessSoundController.INSTANCE.observeLocalCorrectGuess(System.currentTimeMillis());
                return;
            }
        }
    }

    private static String extractCorrectGuessIgn(String text, LangPatterns lp) {
        if (text == null || lp == null || lp.correctGuessSuffix == null) return null;
        String ign;
        if (lp.correctGuessPrefix != null) {
            if (!text.startsWith(lp.correctGuessPrefix)) return null;
            String rest = text.substring(lp.correctGuessPrefix.length());
            if (!rest.endsWith(lp.correctGuessSuffix)) return null;
            ign = rest.substring(0, rest.length() - lp.correctGuessSuffix.length()).trim();
        } else {
            if (!text.endsWith(lp.correctGuessSuffix)) return null;
            ign = text.substring(0, text.length() - lp.correctGuessSuffix.length()).trim();
        }
        ign = stripRankPrefixes(stripControlCodes(ign).trim());
        ign = LangPatterns.stripLeadingBbTitle(ign);
        return isValidMcUsername(ign) ? ign : null;
    }

    /**
     * Recognize a complete non-game chat envelope without treating a bare
     * player/system line beginning with "Party" or "From" as a channel.
     */
    static boolean isNonGameChatChannel(String clean) {
        if (clean == null) return false;
        String normalized = stripControlCodes(LangPatterns.stripEmojis(clean)).trim();
        return normalized.matches("(?i)^(?:Party|Guild|Officer)\\s*>\\s+(?:\\[[^\\r\\n\\]]+\\]\\s+)*[A-Za-z0-9_]{1,16}\\s*:\\s+.*$")
                || normalized.matches("(?i)^(?:From|To)\\s+(?:\\[[^\\r\\n\\]]+\\]\\s+)*[A-Za-z0-9_]{1,16}\\s*:\\s+.*$");
    }

    private static String extractThemeReveal(String text, LangPatterns lp) {
        if (text == null || lp == null || lp.themeWasPrefix == null) return null;
        if (!text.startsWith(lp.themeWasPrefix)) return null;
        String rest = text.substring(lp.themeWasPrefix.length());
        if (lp.themeWasSuffix != null) {
            if (!rest.endsWith(lp.themeWasSuffix)) return null;
            rest = rest.substring(0, rest.length() - lp.themeWasSuffix.length());
        }
        rest = rest.trim();
        return rest.isEmpty() ? null : rest;
    }

    private static Integer extractThemeCharCount(String text, LangPatterns lp) {
        if (text == null || lp == null || lp.charsLongPrefix == null) return null;
        if (!text.startsWith(lp.charsLongPrefix)) return null;
        String rest = text.substring(lp.charsLongPrefix.length());
        if (lp.charsLongSuffix != null) {
            if (!rest.endsWith(lp.charsLongSuffix)) return null;
            rest = rest.substring(0, rest.length() - lp.charsLongSuffix.length());
        }
        try {
            return Integer.valueOf(Integer.parseInt(rest.trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void handlePlayerChat(String raw, String text, long now) {
        if (!isInRound(state) || currentRound == null) return;
        PlayerChatObservation chat = extractPlayerChatObservation(raw, text);
        if (chat == null || isReservedNonPlayerName(chat.ign)) return;
        if (isLocalPlayerIgn(chat.ign)) {
            acceptedGuessTracker.discardEcho(currentRound, chat.message, now,
                    (observed, sent) -> com.gtbstructures.translation.HypixelGuessChat
                            .matchesObserved(observed, sent, "co"));
            ModernChatCooldownCounter.onUserChatAccepted(now);
        }
        PlayerMetaCache.INSTANCE.harvestChatMessageRank(chat.ign, raw);
        addPlayerIfAbsent(chat.ign);
        notePlayerActivity(chat.ign, "chat");
        if (gtbHooks != null) {
            gtbHooks.onPlayerChat(chat.ign);
            if (chat.emblem != null) gtbHooks.onPlayerEmblem(chat.ign, chat.emblem);
        }
        if (state == TrackingState.ROUND_BUILD && currentRound != null) {
            currentRound.guesses.add(new GuessEntry(now, chat.ign, chat.message));
            ScoreReconciler.computeBuilderDelta(currentRound);
            if (gtbHooks != null) gtbHooks.onPlayerGuess(chat.ign, chat.message);
            queueThemePointFromTheme(currentRound.theme, currentRound.langCode);
        }
    }

    private static PlayerChatObservation extractPlayerChatObservation(String raw, String text) {
        String visible = stripControlCodes(text).trim();
        String guesserChat = LangPatterns.stripGuesserChatPrefix(visible);
        String playerText = guesserChat != null ? guesserChat : visible;
        if (guesserChat == null
                && !LangPatterns.startsWithChatTitle(LangPatterns.stripEmojis(playerText).trim())) {
            return null;
        }
        int colon = playerText.indexOf(": ");
        if (colon < 0) return null;
        String speakerPrefix = playerText.substring(0, colon);
        if (speakerPrefix.indexOf('\n') >= 0 || speakerPrefix.indexOf('\r') >= 0) return null;
        String beforeColon = speakerPrefix.trim();
        String message = playerText.substring(colon + 2).trim();
        String[] parts = beforeColon.split("\\s+");
        String[] rawParts = guesserChat == null ? rawChatSpeakerParts(raw) : null;
        for (int i = parts.length - 1; i >= 0; i--) {
            String ign = cleanIgn(parts[i]);
            if (!isValidMcUsername(ign)) continue;
            String emblem = null;
            if (i < parts.length - 1) {
                String afterIgn = stripControlCodes(parts[i + 1]);
                if (afterIgn.length() == 1 && isEmblemChar(afterIgn.charAt(0))) {
                    String rawToken = rawParts != null && rawParts.length == parts.length
                            ? rawParts[i + 1] : parts[i + 1];
                    String color = extractColorBeforeVisibleChar(rawToken, afterIgn.charAt(0));
                    if (color == null) color = extractLastFormatCode(rawToken);
                    emblem = (color == null ? "" : color) + afterIgn;
                }
            }
            return new PlayerChatObservation(ign, message, emblem);
        }
        return null;
    }

    private static String[] rawChatSpeakerParts(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        int colon = raw.indexOf(": ");
        if (colon < 0) return null;
        String beforeColon = raw.substring(0, colon).trim();
        return beforeColon.isEmpty() ? null : beforeColon.split("\\s+");
    }

    private static String stripControlCodes(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\u00a7' && i + 1 < value.length()) {
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String extractColorBeforeVisibleChar(String rawToken, char visible) {
        if (rawToken == null || rawToken.isEmpty()) return null;
        for (int i = 0; i < rawToken.length(); i++) {
            if (rawToken.charAt(i) == visible) return lastColorBefore(rawToken, i);
        }
        return null;
    }

    private static String extractLastFormatCode(String value) {
        if (value == null) return null;
        for (int i = value.length() - 2; i >= 0; i--) {
            if (value.charAt(i) != '\u00a7') continue;
            char code = Character.toLowerCase(value.charAt(i + 1));
            if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                return "\u00a7" + code;
            }
        }
        return null;
    }

    private static String lastColorBefore(String raw, int rawIndex) {
        if (raw == null) return null;
        for (int i = Math.min(rawIndex, raw.length() - 1); i >= 1; i--) {
            if (raw.charAt(i - 1) != '\u00a7') continue;
            char code = Character.toLowerCase(raw.charAt(i));
            if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                return "\u00a7" + code;
            }
        }
        return null;
    }

    private static boolean isEmblemChar(char c) {
        return c == '\u2248' || c == '\u03b1' || c == '\u03a9'
                || c == '$' || c == '\u03c0' || c == '\u0192'
                || c == '\u263a' || c == '\u2600' || c == '\u2601'
                || c == '\u2605' || c == '\u2691' || c == '\u29c8'
                || c == '\u00ba' || c == '\u2022' || c == '\u26a1'
                || c == '\u2764' || c == '\u00bb';
    }

    private static final class PlayerChatObservation {
        final String ign;
        final String message;
        final String emblem;

        PlayerChatObservation(String ign, String message, String emblem) {
            this.ign = ign;
            this.message = message;
            this.emblem = emblem;
        }
    }

    private String parseLobbyJoinIgn(String text) {
        if (text == null || text.isEmpty()) return null;
        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            String ign = parseLocalizedLobbyJoinIgn(text, lp);
            if (ign != null) return ign;
        }
        return null;
    }

    private String parseLocalizedLobbyJoinIgn(String text, LangPatterns lp) {
        if (text == null || lp == null || lp.lobbyJoinSuffix == null) return null;
        String work = text;
        if (lp.lobbyJoinPrefix != null && !lp.lobbyJoinPrefix.isEmpty()) {
            if (!work.startsWith(lp.lobbyJoinPrefix)) return null;
            work = work.substring(lp.lobbyJoinPrefix.length());
        }
        Matcher matcher = Pattern.compile("^(.+?)" + localizedLobbyJoinSuffixRegex(lp.lobbyJoinSuffix) + "$")
                .matcher(work);
        if (!matcher.matches()) return null;
        String ign = cleanIgn(matcher.group(1));
        return ign.isEmpty() ? null : ign;
    }

    private static String localizedLobbyJoinSuffixRegex(String suffix) {
        if (suffix == null) return "";
        int countIdx = suffix.indexOf("\\d+/\\d+");
        if (countIdx < 0) return Pattern.quote(suffix);
        String before = suffix.substring(0, countIdx);
        String after = suffix.substring(countIdx + "\\d+/\\d+".length());
        return Pattern.quote(before) + "(\\d+/\\d+)" + Pattern.quote(after);
    }

    private String parseLobbyQuitIgn(String text) {
        if (text == null || text.isEmpty()) return null;
        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            String ign = parseLocalizedLobbyQuitIgn(text, lp);
            if (ign != null) return ign;
        }
        return null;
    }

    private String parseLocalizedLobbyQuitIgn(String text, LangPatterns lp) {
        if (text == null || lp == null || lp.lobbyQuitSuffix == null) return null;
        String work = text;
        if (lp.lobbyQuitPrefix != null && !lp.lobbyQuitPrefix.isEmpty()) {
            if (!work.startsWith(lp.lobbyQuitPrefix)) return null;
            work = work.substring(lp.lobbyQuitPrefix.length());
        }
        if (!work.endsWith(lp.lobbyQuitSuffix)) return null;
        String ign = cleanIgn(work.substring(0, work.length() - lp.lobbyQuitSuffix.length()));
        return ign.isEmpty() ? null : ign;
    }

    private void suppressLobbyQuit(String ign) {
        if (!isValidMcUsername(ign)) return;
        removePlayerIgnoreCaseFromRoster(ign);
        lobbyQuitSuppress.add(ign.toLowerCase(Locale.ROOT));
        PlayerMetaCache.INSTANCE.observePacketRosterRemove(ign);
    }

    private boolean isLobbyQuitSuppressed(String ign) {
        return ign != null && lobbyQuitSuppress.contains(ign.toLowerCase(Locale.ROOT));
    }

    private void removePlayerIgnoreCaseFromRoster(String ign) {
        if (ign == null) return;
        Iterator<String> iterator = players.iterator();
        while (iterator.hasNext()) {
            String existing = iterator.next();
            if (existing != null && existing.equalsIgnoreCase(ign)) {
                iterator.remove();
            }
        }
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

    private static void replaceRosterIdentityIfPresent(List<String> roster,
                                                       String oldIgn, String newIgn) {
        if (roster == null || oldIgn == null || newIgn == null
                || oldIgn.equalsIgnoreCase(newIgn)) return;
        int replacementIndex = -1;
        boolean newPresent = false;
        for (int i = 0; i < roster.size(); i++) {
            String existing = roster.get(i);
            if (existing == null) continue;
            if (existing.equalsIgnoreCase(newIgn)) newPresent = true;
            if (existing.equalsIgnoreCase(oldIgn)) {
                if (replacementIndex < 0) replacementIndex = i;
                else roster.remove(i--);
            }
        }
        if (replacementIndex < 0) return;
        if (newPresent) roster.remove(replacementIndex);
        else roster.set(replacementIndex, newIgn);
    }

    private void addPlayerIfAbsent(String ign) {
        if (isValidMcUsername(ign) && !containsPlayerIgnoreCase(ign)) {
            players.add(ign);
        }
    }

    private TrackingState scoreboardState(List<String> cleanLines, boolean scoreboardShowsGtb) {
        if (!scoreboardShowsGtb || cleanLines == null) return TrackingState.NOT_IN_GAME;
        if (cleanLines.isEmpty() && isInRound(state) && currentRound != null) {
            return state;
        }
        GtbGame.State gtbState = LangPatterns.getStateFromScoreboard(cleanLines, scoreboardShowsGtb);
        if (gtbState == GtbGame.State.LOBBY) return TrackingState.LOBBY;
        if (gtbState == GtbGame.State.ROUND_PRE) return TrackingState.ROUND_PRE;
        if (gtbState == GtbGame.State.ROUND_BUILD) return TrackingState.ROUND_BUILD;
        if (gtbState == GtbGame.State.ROUND_END) return TrackingState.ROUND_END;
        if (gtbState == GtbGame.State.POST_GAME) return TrackingState.POST_GAME;
        for (String line : cleanLines) {
            if (line != null && line.equalsIgnoreCase("Mode: Guess The Build")) {
                return TrackingState.LOBBY;
            }
        }
        return TrackingState.NOT_IN_GAME;
    }

    static TrackingState retainActiveStateForUnclassifiedGtb(
            TrackingState currentState, TrackingState rawScoreboardState,
            boolean scoreboardShowsGtb) {
        if (scoreboardShowsGtb
                && rawScoreboardState == TrackingState.NOT_IN_GAME
                && isActiveGame(currentState)) {
            return currentState;
        }
        return rawScoreboardState;
    }

    private static boolean isInRound(TrackingState state) {
        return state == TrackingState.ROUND_PRE
                || state == TrackingState.ROUND_BUILD
                || state == TrackingState.ROUND_END;
    }

    private static boolean isInGameScoreboardState(TrackingState state) {
        return state == TrackingState.ROUND_PRE
                || state == TrackingState.ROUND_BUILD
                || state == TrackingState.ROUND_END;
    }

    private static String cleanIgn(String value) {
        if (value == null) return "";
        value = stripRankPrefixes(value.trim());
        value = LangPatterns.stripLeadingBbTitle(value);
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '_') {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String stripRankPrefixes(String value) {
        boolean changed;
        do {
            changed = false;
            if (value.startsWith("[")) {
                int close = value.indexOf(']');
                if (close > 0) {
                    value = value.substring(close + 1).trim();
                    changed = true;
                }
            }
        } while (changed);
        return value;
    }

    private static int[] parseRoundHeader(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        int slash = trimmed.indexOf('/');
        if (slash < 0) return null;
        try {
            int current = Integer.parseInt(trimmed.substring(0, slash).trim());
            int total = Integer.parseInt(trimmed.substring(slash + 1).trim());
            return new int[] {current, total};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean containsIgnoreCase(Set<String> values, String value) {
        if (values == null || value == null) return false;
        for (String existing : values) {
            if (existing != null && existing.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private static void addIgnoreCase(Set<String> values, String value) {
        if (values == null || value == null) return;
        if (!containsIgnoreCase(values, value)) values.add(value);
    }

    private static void moveSetValue(Set<String> values, String oldValue, String newValue) {
        if (values == null || oldValue == null || newValue == null) return;
        boolean removed = false;
        Iterator<String> iterator = values.iterator();
        while (iterator.hasNext()) {
            String existing = iterator.next();
            if (existing != null && existing.equalsIgnoreCase(oldValue)) {
                iterator.remove();
                removed = true;
            }
        }
        if (removed && !containsIgnoreCase(values, newValue)) values.add(newValue);
    }

    private static boolean removeIgnoreCase(Set<String> values, String value) {
        if (values == null || value == null) return false;
        for (String existing : new ArrayList<String>(values)) {
            if (existing != null && existing.equalsIgnoreCase(value)) {
                values.remove(existing);
                return true;
            }
        }
        return false;
    }

    private static boolean isValidMcUsername(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{1,16}");
    }

    private static boolean isReservedNonPlayerName(String ign) {
        if (ign == null) return false;
        String value = ign.trim().toLowerCase(Locale.ROOT);
        return value.equals("wins")
                || value.equals("losses")
                || value.equals("winstreak")
                || value.equals("games")
                || value.equals("kills")
                || value.equals("deaths")
                || value.equals("coins")
                || value.equals("score")
                || value.equals("level")
                || value.equals("karma")
                || value.equals("rank")
                || value.equals("guild")
                || value.equals("quests")
                || value.equals("achievements");
    }

    public Integer getCurrentPhysicalTimerSeconds() {
        return currentPhysicalTimerSeconds;
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

    private void resetPhysicalTimerTracking() {
        currentPhysicalTimerSeconds = null;
        resetTrackedPhysicalTimerComparator();
    }

    private void resetTrackedPhysicalTimerComparator() {
        lastTrackedPhysicalTimerSeconds = null;
        lastTrackedPhysicalTimerState = null;
        lastTrackedPhysicalTimerRound = null;
    }

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
        public String text;

        public HintEntry(long t, String text) {
            this.t = t;
            this.physicalTimer = GameStats.INSTANCE.getCurrentPhysicalTimerSeconds();
            this.text = text;
        }
    }

    public static class GameEvent {
        public long t;
        public Integer physicalTimer;
        public String type;
        public String detail;
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
            this.place = place;
            this.ign = ign;
            this.score = score;
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
        public String[] builderThemeOptions;
        public String hintStructure;
        public String language;
        public String langCode;
        public List<GuessEntry> guesses = new ArrayList<GuessEntry>();
        public List<String> acceptedGuessTexts = new ArrayList<String>();
        public List<CorrectGuessEntry> correctGuesses = new ArrayList<CorrectGuessEntry>();
        public List<HintEntry> hints = new ArrayList<HintEntry>();
        public List<HandheldItemEntry> handheldItems = new ArrayList<HandheldItemEntry>();
        public List<GameEvent> events = new ArrayList<GameEvent>();
        public Map<String, ScoreDelta> scoreDeltas = new LinkedHashMap<String, ScoreDelta>();
        public boolean dataGap = false;
        public String effectiveInitialHint;
        public boolean showAllMatchingSent;
        public boolean shortcutReminderSent;
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
}
