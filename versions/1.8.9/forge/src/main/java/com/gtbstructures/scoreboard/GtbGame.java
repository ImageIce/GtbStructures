package com.gtbstructures.scoreboard;

import com.gtbstructures.stats.GameStats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * GTU-shaped game state. One instance lives at a time; null when no game is in progress.
 *
 * <p>State enum mirrors GTU's; values match {@code GameStats.TrackingState} where
 * possible to keep the cross-translation simple.
 */
public final class GtbGame {

    public enum State { NONE, LOBBY, SETUP, ROUND_PRE, ROUND_BUILD, ROUND_END, POST_GAME }

    public final Map<String, GtbPlayer> players = new LinkedHashMap<String, GtbPlayer>();

    public int currentRound = 0;
    /** Highest round whose end boundary has been observed in this game. */
    public int lastCompletedRound = 0;
    public int totalRounds = 0;
    /** Direct mid-game bootstrap has no authoritative lobby roster; keep the
     *  denominator unknown until the next Round: X/Y header arrives. */
    public boolean awaitingAuthoritativeRoundTotal;
    /** True after the authoritative initial "Round: 1/x" header is parsed. */
    public boolean initialRoundHeaderConfirmed;
    public String currentTheme = "";
    public String currentHintStructure = "";
    public String currentTimer = "";
    public String currentLangCode = "en";  // active at most-recent ROUND_BUILD start
    public GtbPlayer currentBuilder;
    /** Builder retained only for Default-scoreboard presentation during ROUND_END. */
    public GtbPlayer roundEndBuilder;
    public int correctGuessesThisRound = 0;
    public boolean oneSecondAlertReached;
    public boolean userRoundSkipped;
    /** True after the local user disconnects/rejoins during this game. */
    public boolean userDisconnectedThisGame;
    /** Set true when onRoundSkipped fires for the current round (builder_left or
     *  afk_skip). Consumed by onRoundEnd; cleared on onRoundStart for next round. */
    public boolean roundSkippedThisRound;
    public State state = State.NONE;
    public int skippedRounds;
    public int potentialLeaverAmount;

    /** Set on rejoin into ROUND_BUILD/ROUND_END; cleared on next onRoundStart. */
    public boolean roundCountUnknown;
    /** Guess order may be missing earlier correct guesses until the next fresh
     *  round header after a local disconnect/rejoin. */
    public boolean rejoinGuessOrderUncertain;
    /** Correct-guess messages observed after the latest rejoin, in server order. */
    public final List<String> rejoinObservedGuessOrder = new ArrayList<String>();
    /** Minimum number of correct guesses proved to predate those messages. */
    public int rejoinPriorGuessFloor;
    /** A post-rejoin builder change proved that scoring may have happened while
     *  no fresh round header has arrived yet. */
    public boolean rejoinBuilderChangedBeforeRoundHeader;
    public boolean suppressPointBadgesUntilRoundHeader;
    public boolean roundStartScoreBaselinePending;

    /** Leave/rejoin remembered state — mirrors GTU. */
    public State leaveState;
    public int leaveRound = -1;
    public GtbPlayer leaveBuilder;

    /** Set on onUserRejoin to the leaveRound; consumed by next onRoundStart to
     *  compute how many rounds were missed during the disconnect window. */
    public int pendingRejoinFromRound = -1;
    public boolean pendingRejoinFromUserBuildRound;
    /** Local build round abandoned by disconnect; later sidebar residuals cannot belong to it. */
    public int abandonedLocalBuildRound;
    /** Last known authoritative cumulative score per player. Scores never decrease. */
    public Map<String, Integer> latestTrueScore = new HashMap<String, Integer>();

    /** Snapshot of {@link #latestTrueScore} at the start of the current round. */
    public Map<String, Integer> roundStartTrueScore = new HashMap<String, Integer>();

    /** Current visible Hypixel sidebar score rows. Used only for visible top-N ordering/floors. */
    public Map<String, Integer> visibleTrueScore = new HashMap<String, Integer>();

    /** English themes ruled out by visible wrong player guesses in the current round. */
    public final Set<String> ruledOutEnglishThemesThisRound =
            new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    /** Unique visible wrong guesses seen this round. Applied only for ambiguous displayed themes. */
    public final Set<String> wrongGuessesThisRound = new LinkedHashSet<String>();
    /** Actual submitted answers acknowledged by the server, including hidden duplicate-correct replies. */
    public final List<String> acceptedGuessTextsThisRound = new ArrayList<String>();
    /** Builder deltas derived only from real sidebar observations, never synthesized theme totals. */
    public final Map<String, Integer> scoreboardBuilderPointsThisRound = new HashMap<String, Integer>();
    /** First post-round snapshot could not prove these players' new-round baseline. */
    public final Set<GtbPlayer> uncertainBuilderBaselinesThisRound = new LinkedHashSet<GtbPlayer>();

    /** Legacy layout cache. Current renderers measure visible content each
     *  frame so transient fork chips can expand and then shrink normally. */
    public int maxPanelContentWidth;
}
