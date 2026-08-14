package com.gtbstructures.scoreboard;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Per-round per-player score record. Either a definite int, including 0 for
 * "did not score this round", or a fork with multiple possible values.
 */
public final class PointEntry {

    public enum Kind { ZERO, DEFINITE, FORK }

    public Kind kind;
    public int value;
    public Set<Integer> fork;

    private PointEntry() {
    }

    public static PointEntry zero() {
        PointEntry entry = new PointEntry();
        entry.kind = Kind.ZERO;
        return entry;
    }

    public static PointEntry definite(int value) {
        PointEntry entry = new PointEntry();
        entry.kind = Kind.DEFINITE;
        entry.value = value;
        return entry;
    }

    /** Auto-collapses to DEFINITE when the fork has exactly one member. */
    public static PointEntry fork(Set<Integer> values) {
        if (values == null || values.isEmpty()) return zero();
        if (values.size() == 1) return definite(values.iterator().next());
        PointEntry entry = new PointEntry();
        entry.kind = Kind.FORK;
        entry.fork = new HashSet<Integer>(values);
        return entry;
    }

    /**
     * Narrowing from indirect evidence keeps FORK kind even for a single
     * survivor. Only direct observation of the player's own row finalizes.
     */
    public static PointEntry narrowedFork(Set<Integer> values) {
        if (values == null || values.isEmpty()) return zero();
        PointEntry entry = new PointEntry();
        entry.kind = Kind.FORK;
        entry.fork = new HashSet<Integer>(values);
        return entry;
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

    /** Lock a fork to a specific value. No-op if this is not a matching fork. */
    public PointEntry collapseTo(int value) {
        if (kind == Kind.FORK && fork.contains(value)) {
            return definite(value);
        }
        return this;
    }
}
