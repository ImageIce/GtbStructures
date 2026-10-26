package com.gtbstructures.scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Active GTB game state. One instance represents one tracked game.
 */
public final class GtbGame {

    public enum State { NONE, LOBBY, SETUP, ROUND_PRE, ROUND_BUILD, ROUND_END, POST_GAME }

    public final Map<String, GtbPlayer> players = new LinkedHashMap<String, GtbPlayer>();

    public int currentRound = 0;
    /** Highest round whose end boundary has been observed in this game. */
    public int lastCompletedRound = 0;
    public int totalRounds = 0;
    public boolean initialRoundHeaderConfirmed;
    public String currentTheme = "";
    public String currentHintStructure = "";
    public String currentTimer = "";
    public String currentLangCode = "en";
    public GtbPlayer currentBuilder;
    /** Builder retained only for Default-scoreboard presentation during ROUND_END. */
    public GtbPlayer roundEndBuilder;
    public int correctGuessesThisRound = 0;
    public boolean oneSecondAlertReached;
    public boolean userRoundSkipped;
    public boolean userDisconnectedThisGame;
    public boolean roundSkippedThisRound;
    public State state = State.NONE;
    public int skippedRounds;
    public int potentialLeaverAmount;

    public boolean roundCountUnknown;
    /** A missed-lobby bootstrap must wait for a server round header, not roster size. */
    public boolean awaitingAuthoritativeRoundTotal;
    public boolean rejoinGuessOrderUncertain;
    public final List<String> rejoinObservedGuessOrder = new ArrayList<String>();
    public int rejoinPriorGuessFloor;
    public boolean rejoinBuilderChangedBeforeRoundHeader;
    public boolean suppressPointBadgesUntilRoundHeader;
    public boolean roundStartScoreBaselinePending;

    public State leaveState;
    public int leaveRound = -1;
    public GtbPlayer leaveBuilder;

    public int pendingRejoinFromRound = -1;
    public boolean pendingRejoinFromUserBuildRound;
    /** Local build round abandoned by disconnect; later sidebar residuals cannot belong to it. */
    public int abandonedLocalBuildRound;
    public Map<String, Integer> latestTrueScore = new HashMap<String, Integer>();
    public Map<String, Integer> roundStartTrueScore = new HashMap<String, Integer>();
    public Map<String, Integer> visibleTrueScore = new HashMap<String, Integer>();
    public final Set<String> ruledOutEnglishThemesThisRound =
            new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    public final Set<String> wrongGuessesThisRound = new LinkedHashSet<String>();
    /** Actual submitted answers acknowledged by the server, including hidden duplicate-correct replies. */
    public final List<String> acceptedGuessTextsThisRound = new ArrayList<String>();
    /** Builder deltas derived only from real sidebar observations, never synthesized theme totals. */
    public final Map<String, Integer> scoreboardBuilderPointsThisRound = new HashMap<String, Integer>();
    /** First post-round snapshot could not prove these players' new-round baseline. */
    public final Set<GtbPlayer> uncertainBuilderBaselinesThisRound = new LinkedHashSet<GtbPlayer>();

    public int maxPanelContentWidth;
}
