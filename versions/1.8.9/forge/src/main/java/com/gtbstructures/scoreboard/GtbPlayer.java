package com.gtbstructures.scoreboard;

import com.gtbstructures.player.PlayerMetaCache;

import java.util.HashSet;
import java.util.Set;

/**
 * Per-player tracker state. Lives only inside {@link GtbGame}; mutated only by
 * {@link GameTracker}.
 */
public final class GtbPlayer {

    public enum LeaverState { NORMAL, POTENTIAL_LEAVER, LEAVER }

    public String name;                 // mutable via onIdentityTransfer
    public String rankColor;            // §x derived from PlayerMetaCache (Phase 6); null = default
    public String emblem;               // single unicode emblem char (Phase 6); null if absent
    public String title;                // RESERVED — Phase 6 dropped GTB titles from scope
    public PointEntry[] points;         // length = totalRounds; index = round-1
    public int buildRound;              // 0=hasn't built, >0=round N
    public boolean isUser;              // matches mc.thePlayer.getName()
    public int inactiveTicks;           // ++each tick; reset on activity
    public LeaverState leaverState = LeaverState.NORMAL;
    public int roundsMissedDuringDisconnect;  // for severe-uncertainty (!)
    /** Rounds with an unobserved interval whose score deltas are not yet globally resolved. */
    public final Set<Integer> disconnectUncertainRounds = new HashSet<Integer>();
    /** The local user rejoined after scoring may already have begun in the current round. */
    public boolean rejoinRoundScoreUncertain;
    public int scoreMismatchCounter;    // RESERVED — not wired in v1

    /** Round-based "away" detection. Counts rounds in which the player neither
     *  chatted nor correctly guessed; resets on any activity. ≥2 → zzz icon.
     *  Only counted while the player was present (absentThisRound gates it).*/
    public int silentRoundsConsecutive;
    public boolean spokeThisRound;
    /** Set when the player should not be counted toward the round-based away
     *  tally (local disconnect, or builder round skipped/AFK). */
    public boolean absentThisRound;

    /** True for players present in the game-start roster snapshot. */
    public boolean presentAtGameStart;
    /** Late identity created from chat/score evidence, pending nick-resolution. */
    public boolean unresolvedIdentity;
    public boolean everOnLeaderboard;
    public boolean everScored;
    public int firstSeenTick = -1;
    /** Last tick where this identity sent chat or gained score. */
    public int lastMessageOrScoreTick = -1;
    /** Current-round builder score is known to exist but its value is not visible yet. */
    public int pendingBuilderScoreRound;
    /** Exact current-round builder award proved by the revealed theme or English hint. */
    public int authoritativeBuilderScoreRound;
    /** Hide a current-round point chip after the local builder slot is invalidated by a same-round rejoin. */
    public int suppressedPointBadgeRound;
    /** Highest cumulative score Hypixel has shown for this player. */
    public int observedScoreFloor = -1;
    /** Highest exact cumulative score known immediately before the latest disconnect. */
    public int preDisconnectScoreBaseline = -1;
    /** Builder assignment clears away unless the later AFK skip proves no action. */
    public int builderAssignmentActivityRound;
    public int silentRoundsBeforeBuilderAssignment;

    public GtbPlayer(String name) {
        this.name = name;
    }

    public int definiteSum() {
        if (points == null) return 0;
        int sum = 0;
        for (PointEntry e : points) {
            if (e != null && e.kind == PointEntry.Kind.DEFINITE) sum += e.value;
        }
        return sum;
    }

    public int forkMin() {
        if (points == null) return 0;
        int sum = 0;
        for (PointEntry e : points) {
            if (e != null && e.kind == PointEntry.Kind.FORK) sum += e.min();
        }
        return sum;
    }

    public int forkMax() {
        if (points == null) return 0;
        int sum = 0;
        for (PointEntry e : points) {
            if (e != null && e.kind == PointEntry.Kind.FORK) sum += e.max();
        }
        return sum;
    }

    public int totalMin() { return definiteSum() + forkMin(); }
    public int totalMax() { return definiteSum() + forkMax(); }

    public boolean hasUncertainty() { return totalMin() != totalMax(); }
}
