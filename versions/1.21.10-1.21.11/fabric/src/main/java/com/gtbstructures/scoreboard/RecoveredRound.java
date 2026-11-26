package com.gtbstructures.scoreboard;

import com.gtbstructures.stats.GameStats;
import java.util.HashMap;
import java.util.Map;

/** An extra scoring column, never an assumed absolute round number. */
final class RecoveredRound {
    final GameStats.RoundData source;
    final int slot;
    final int sequence;
    final Map<GtbPlayer, Integer> lowerBaselines = new HashMap<GtbPlayer, Integer>();
    private final Map<GtbPlayer, Integer> upperBaselines = new HashMap<GtbPlayer, Integer>();
    private final Map<GtbPlayer, Integer> freshBaselines = new HashMap<GtbPlayer, Integer>();
    GtbPlayer builder;
    boolean ended;
    boolean localBuildAbandoned;
    boolean firstGuessProven;

    RecoveredRound(GameStats.RoundData source, int slot, GtbGame game) {
        this.source = source;
        this.slot = slot;
        this.sequence = game.nextRecoveredSequence++;
        for (GtbPlayer player : game.players.values()) {
            int lower = game.totalMinimum(player);
            Integer known = game.latestTrueScore.get(player.name);
            if (known != null) lower = Math.max(lower, known.intValue());
            lowerBaselines.put(player, Integer.valueOf(lower));
            if (player.isUser) upperBaselines.put(player, Integer.valueOf(lower));
        }
    }

    PointEntry entry(GtbPlayer player) {
        return player.points != null && slot < player.points.length
                ? player.points[slot] : PointEntry.zero();
    }

    /** Only an actual observation before this player's award is an exact baseline. */
    boolean observeBaseline(GtbPlayer player, int score, GtbGame game) {
        if (ended || entry(player).kind != PointEntry.Kind.ZERO
                || freshBaselines.containsKey(player)) return false;
        if (score < lowerBaseline(player)) return false;
        if (player == builder && game.correctGuessesThisRound > 0) return false;
        freshBaselines.put(player, Integer.valueOf(score));
        game.roundStartTrueScore.put(player.name, Integer.valueOf(score));
        return true;
    }

    Integer freshBaseline(GtbPlayer player) {
        // A builder may already have earned the theme award before we returned.
        // Proving that our first observed guess was first rules that out.
        if (player == builder && !firstGuessProven) return null;
        return freshBaselines.get(player);
    }

    void observeOffSidebarBounds(GtbGame game, Map<String, Integer> scores) {
        if (ended || scores.isEmpty()) return;
        int floor = Integer.MAX_VALUE;
        for (Integer score : scores.values()) floor = Math.min(floor, score.intValue());
        for (GtbPlayer player : game.players.values()) {
            if (scores.containsKey(player.name) || entry(player).kind != PointEntry.Kind.ZERO) continue;
            if (floor < lowerBaseline(player)) continue;
            Integer prior = upperBaselines.get(player);
            int upper = prior == null ? floor : Math.min(prior.intValue(), floor);
            upperBaselines.put(player, Integer.valueOf(Math.max(lowerBaseline(player), upper)));
        }
    }

    int lowerBaseline(GtbPlayer player) {
        Integer fresh = freshBaseline(player);
        Integer frozen = lowerBaselines.get(player);
        return Math.max(frozen == null ? 0 : frozen.intValue(), fresh == null ? 0 : fresh.intValue());
    }

    int upperBaseline(GtbPlayer player, GtbGame game) {
        Integer fresh = freshBaseline(player);
        if (fresh != null) return fresh.intValue();
        Integer upper = upperBaselines.get(player);
        return upper == null ? Math.max(lowerBaseline(player), Math.max(0, game.totalRounds - 1) * 3)
                : Math.max(lowerBaseline(player), upper.intValue());
    }

    /** Proven subtotal before a bound round; later awards remain separate. */
    static final class ScoreFloor {
        final int firstRoundIndex;
        final int sequence;
        final Map<GtbPlayer, Integer> baselines = new HashMap<GtbPlayer, Integer>();
        final Map<GtbPlayer, Integer> upperBaselines = new HashMap<GtbPlayer, Integer>();

        ScoreFloor(int firstRoundIndex, int sequence) {
            this.firstRoundIndex = firstRoundIndex;
            this.sequence = sequence;
        }
    }
}
