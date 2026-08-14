package com.gtbstructures.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Per-round score change attributed to a single player. Either definite (a single
 * integer) or a fork (a small set of possible integers, used when a non-English
 * translation maps to multiple English themes with different point values).
 */
public final class ScoreDelta {
    public final Integer value;        // null when forked
    public final List<Integer> fork;   // null when definite (otherwise sorted, distinct)
    public final String source;        // "guess" | "builder" | "builder_gui" | "scoreboard" | "residual"

    private ScoreDelta(Integer value, List<Integer> fork, String source) {
        this.value = value;
        this.fork = fork;
        this.source = source;
    }

    public boolean isDefinite() {
        return value != null;
    }

    public static ScoreDelta definite(int v, String source) {
        return new ScoreDelta(v, null, source);
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

    /** True if a definite delta of `v` is one of the possible values for this entry. */
    public boolean canBe(int v) {
        if (isDefinite()) return value == v;
        return fork.contains(v);
    }

    /** Render as JSON object matching the export schema. */
    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        if (isDefinite()) {
            sb.append("\"value\":").append(value);
        } else {
            sb.append("\"fork\":[");
            for (int i = 0; i < fork.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(fork.get(i));
            }
            sb.append("]");
        }
        sb.append(",\"source\":").append(GameExporter.jsonStr(source));
        sb.append("}");
        return sb.toString();
    }
}
