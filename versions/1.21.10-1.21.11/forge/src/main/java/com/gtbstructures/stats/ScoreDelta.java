package com.gtbstructures.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Per-round score change attributed to a single player. Either definite or a
 * fork with a small sorted set of possible integer awards.
 */
public final class ScoreDelta {
    public final Integer value;
    public final List<Integer> fork;
    public final String source;

    private ScoreDelta(Integer value, List<Integer> fork, String source) {
        this.value = value;
        this.fork = fork;
        this.source = source;
    }

    public boolean isDefinite() {
        return value != null;
    }

    public static ScoreDelta definite(int value, String source) {
        return new ScoreDelta(value, null, source);
    }

    public static ScoreDelta fork(Set<Integer> options, String source) {
        if (options == null || options.isEmpty()) {
            return definite(0, source);
        }
        if (options.size() == 1) {
            return definite(options.iterator().next(), source);
        }
        List<Integer> sorted = new ArrayList<Integer>(new TreeSet<Integer>(options));
        Collections.sort(sorted);
        return new ScoreDelta(null, Collections.unmodifiableList(sorted), source);
    }

    public boolean canBe(int value) {
        if (isDefinite()) return this.value == value;
        return fork.contains(value);
    }

    public String toJson() {
        StringBuilder out = new StringBuilder("{");
        if (isDefinite()) {
            out.append("\"value\":").append(value);
        } else {
            out.append("\"fork\":[");
            for (int i = 0; i < fork.size(); i++) {
                if (i > 0) out.append(",");
                out.append(fork.get(i));
            }
            out.append("]");
        }
        out.append(",\"source\":").append(JsonStrings.jsonStr(source));
        out.append("}");
        return out.toString();
    }
}
