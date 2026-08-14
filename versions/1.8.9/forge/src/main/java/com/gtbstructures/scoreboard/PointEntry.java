package com.gtbstructures.scoreboard;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Per-round per-player score record. Either a definite int (incl. 0 for "did not score
 * this round") or a fork — multiple possible values, used when a non-English theme maps
 * to multiple English candidates with different point awards (multiword) or when a
 * disconnect-rejoin span left a round fully unobserved (fork {0,1,2,3}).
 */
public final class PointEntry {

    public enum Kind { ZERO, DEFINITE, FORK }

    public Kind kind;
    public int value;          // valid when DEFINITE; 0 means "did not score"
    public Set<Integer> fork;  // valid when FORK

    private PointEntry() {}

    public static PointEntry zero() {
        PointEntry e = new PointEntry();
        e.kind = Kind.ZERO;
        return e;
    }

    public static PointEntry definite(int v) {
        PointEntry e = new PointEntry();
        e.kind = Kind.DEFINITE;
        e.value = v;
        return e;
    }

    /** Auto-collapses to DEFINITE when the fork has exactly one member. */
    public static PointEntry fork(Set<Integer> values) {
        if (values == null || values.isEmpty()) return zero();
        if (values.size() == 1) return definite(values.iterator().next());
        PointEntry e = new PointEntry();
        e.kind = Kind.FORK;
        e.fork = new HashSet<Integer>(values);
        return e;
    }

    /** Narrowing from indirect evidence (leaderboard floor): keeps FORK kind
     *  even for a single survivor. Only a direct observation of the player's
     *  own row may finalize a fork to DEFINITE. */
    public static PointEntry narrowedFork(Set<Integer> values) {
        if (values == null || values.isEmpty()) return zero();
        PointEntry e = new PointEntry();
        e.kind = Kind.FORK;
        e.fork = new HashSet<Integer>(values);
        return e;
    }

    public int min() {
        if (kind == Kind.FORK) return Collections.min(fork);
        return value;
    }

    public int max() {
        if (kind == Kind.FORK) return Collections.max(fork);
        return value;
    }

    public boolean isDefinite() {
        return kind != Kind.FORK;
    }

    /** Lock a fork to a specific value (used by collapseForks). No-op if not a fork. */
    public PointEntry collapseTo(int v) {
        if (kind == Kind.FORK && fork.contains(v)) {
            return definite(v);
        }
        return this;
    }
}
