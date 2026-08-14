package com.gtbstructures.scoreboard;

import java.util.HashSet;
import java.util.Set;

/**
 * Per-player tracker state owned by a {@link GtbGame}.
 */
public final class GtbPlayer {

    public enum LeaverState { NORMAL, POTENTIAL_LEAVER, LEAVER }

    public String name;
    public String rankColor;
    public String emblem;
    public String title;
    public PointEntry[] points;
    public int buildRound;
    public boolean isUser;
    public int inactiveTicks;
    public LeaverState leaverState = LeaverState.NORMAL;
    public int roundsMissedDuringDisconnect;
    /** Rounds with an unobserved interval whose score deltas are not yet globally resolved. */
    public final Set<Integer> disconnectUncertainRounds = new HashSet<Integer>();
    /** The local user rejoined after scoring may already have begun in the current round. */
    public boolean rejoinRoundScoreUncertain;
    public int scoreMismatchCounter;

    public int silentRoundsConsecutive;
    public boolean spokeThisRound;
    public boolean absentThisRound;

    public boolean presentAtGameStart;
    public boolean unresolvedIdentity;
    public boolean everOnLeaderboard;
    public boolean everScored;
    public int firstSeenTick = -1;
    public int lastMessageOrScoreTick = -1;
    public int pendingBuilderScoreRound;
    public int authoritativeBuilderScoreRound;
    public int suppressedPointBadgeRound;
    /** Highest cumulative score Hypixel has shown for this player. */
    public int observedScoreFloor = -1;
    /** Highest exact cumulative score known immediately before the latest disconnect. */
    public int preDisconnectScoreBaseline = -1;
    public int builderAssignmentActivityRound;
    public int silentRoundsBeforeBuilderAssignment;

    public GtbPlayer(String name) {
        this.name = name;
    }

    public int definiteSum() {
        if (points == null) return 0;
        int sum = 0;
        for (PointEntry entry : points) {
            if (entry != null && entry.kind == PointEntry.Kind.DEFINITE) {
                sum += entry.value;
            }
        }
        return sum;
    }

    public int forkMin() {
        if (points == null) return 0;
        int sum = 0;
        for (PointEntry entry : points) {
            if (entry != null && entry.kind == PointEntry.Kind.FORK) {
                sum += entry.min();
            }
        }
        return sum;
    }

    public int forkMax() {
        if (points == null) return 0;
        int sum = 0;
        for (PointEntry entry : points) {
            if (entry != null && entry.kind == PointEntry.Kind.FORK) {
                sum += entry.max();
            }
        }
        return sum;
    }

    public int totalMin() {
        return definiteSum() + forkMin();
    }

    public int totalMax() {
        return definiteSum() + forkMax();
    }

    public boolean hasUncertainty() {
        return totalMin() != totalMax();
    }
}
