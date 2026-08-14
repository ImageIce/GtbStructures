package com.gtbstructures.scoreboard;

import com.gtbstructures.player.PlayerMetaCache;
import com.gtbstructures.stats.GameStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Platform-neutral row-status and score-display rules used by custom
 * scoreboard renderers.
 */
public final class CustomScoreboardLogic {
    public static final int ICON_DRAW_SIZE = 8;
    public static final int ICON_OUTSIDE_GAP = 2;
    public static final int INLINE_STATUS_GAP = 2;
    public static final int STATE_DECORATION_Y_OFFSET = -1;

    private CustomScoreboardLogic() {
    }

    public static final class PanelRows {
        public final String headerLeft;
        public final String headerRight;
        public final String timerLeft;
        public final String timerRight;
        public final String themeLabel;
        public final String themeValueLine;
        public final List<Row> rows;

        private PanelRows(String headerLeft, String headerRight, String timerLeft, String timerRight,
                          String themeLabel, String themeValueLine, List<Row> rows) {
            this.headerLeft = headerLeft;
            this.headerRight = headerRight;
            this.timerLeft = timerLeft;
            this.timerRight = timerRight;
            this.themeLabel = themeLabel;
            this.themeValueLine = themeValueLine;
            this.rows = rows;
        }

        public int lineCount() {
            return 2 + 1 + rows.size() + 1 + 2;
        }
    }

    public static final class Row {
        public final GtbPlayer player;
        public final String left;
        public final String right;
        public final int buildIcon;
        public final int statusIcon;
        public final boolean builderRow;

        private Row(GtbPlayer player, String left, String right, int buildIcon,
                    int statusIcon, boolean builderRow) {
            this.player = player;
            this.left = left;
            this.right = right;
            this.buildIcon = buildIcon;
            this.statusIcon = statusIcon;
            this.builderRow = builderRow;
        }
    }

    public static boolean shouldRenderCustomPanel(ScoreboardConfig config, GtbGame game,
                                                  boolean previewActive) {
        return !previewActive
                && config != null
                && config.isCustomEnabled()
                && game != null
                && isActiveRound(game.state);
    }

    public static boolean isActiveRound(GtbGame.State state) {
        return state == GtbGame.State.ROUND_PRE
                || state == GtbGame.State.ROUND_BUILD
                || state == GtbGame.State.ROUND_END;
    }

    public static PanelRows buildPanelRows(GtbGame game, boolean showBbRanks, int tick) {
        return buildPanelRows(game, null, showBbRanks, tick);
    }

    public static PanelRows buildPanelRows(GtbGame game, ScoreboardConfig config,
                                           boolean expanded, int tick) {
        if (game == null) return null;
        boolean isBuildPhase = game.state == GtbGame.State.ROUND_BUILD;
        boolean isPrePhase = game.state == GtbGame.State.ROUND_PRE;
        boolean showPointsGained = config == null || config.showPointsGained(expanded);
        boolean showPlaces = config != null && config.showPlaces(expanded);
        boolean showTitles = config == null ? expanded : config.showTitles(expanded);
        boolean showEmblems = config == null || config.showEmblems(expanded);

        int displayRound = game.currentRound;
        if (isPrePhase) displayRound = Math.max(1, game.currentRound + 1);
        String roundDigits = game.roundCountUnknown ? "?"
                : (displayRound > 0 ? String.valueOf(displayRound) : "?");
        String totalDigits = game.totalRounds > 0 ? String.valueOf(game.totalRounds) : "?";
        String headerLeft = "\u00a77Round";
        String headerRight = "\u00a7a" + roundDigits + "\u00a77/\u00a7a" + totalDigits;

        String timerLabel;
        if (game.state == GtbGame.State.ROUND_PRE) timerLabel = "Starts In";
        else if (game.state == GtbGame.State.ROUND_BUILD) timerLabel = "Time Left";
        else if (game.state == GtbGame.State.ROUND_END) timerLabel = "Next Round";
        else timerLabel = "Time";
        String timerLeft = "\u00a77" + timerLabel;
        String timerValue = (game.currentTimer == null || game.currentTimer.isEmpty())
                ? "0:00" : trimLeadingChar(game.currentTimer);
        String timerRight = "\u00a7a" + timerValue;

        String themeLabel;
        String themeValueLine;
        if (game.currentTheme == null || game.currentTheme.isEmpty()) {
            themeLabel = "\u00a77Theme:";
            themeValueLine = "\u00a7c???";
        } else {
            themeLabel = "\u00a77Theme [\u00a7a" + lengthAsString(game.currentTheme) + "\u00a77]:";
            themeValueLine = "\u00a7a" + game.currentTheme;
        }

        List<GtbPlayer> players = sortedPlayers(game, config);
        List<Row> rows = new ArrayList<Row>(players.size());
        int[] densePlaces = densePlaces(players, game);
        for (int i = 0; i < players.size(); i++) {
            GtbPlayer player = players.get(i);
            boolean isBuilder = player == game.currentBuilder;
            int buildIcon = buildSlotIcon(player, game, isBuildPhase, isPrePhase, tick);
            int statusIcon = statusIcon(player);
            String name = player.name == null ? "?" : player.name;
            String place = showPlaces ? placePrefix(densePlaces[i]) : "";
            String bbRank = showTitles ? bbRankPrefix(player.name, true) : "";
            String emblemToken = player.emblem != null && !player.emblem.isEmpty()
                    ? player.emblem : PlayerMetaCache.INSTANCE.getEmblem(player.name);
            emblemToken = PlayerMetaCache.ensureEmblemColor(emblemToken);
            String emblem = !showEmblems || emblemToken == null || emblemToken.isEmpty()
                    ? "" : " " + emblemToken;
            String left = place + bbRank + rankColor(player.name) + name + emblem
                    + pendingBuilderFlag(player, game);
            String chip = isPrePhase || !showPointsGained ? "" : formatChip(player, game);
            String total = formatTotal(player, game, isBuilder);
            String severe = severeFlag(player, game) ? " \u00a7c(!)" : "";
            String right = (chip.isEmpty() ? "" : chip + " ") + total + severe;
            rows.add(new Row(player, left, right, buildIcon, statusIcon, isBuilder));
        }
        return new PanelRows(headerLeft, headerRight, timerLeft, timerRight,
                themeLabel, themeValueLine, rows);
    }

    public static int measureContentWidth(PanelRows panel, ScoreboardLayout.StringMeasurer measurer) {
        if (panel == null || measurer == null) return 80;
        int contentWidth = 0;
        contentWidth = Math.max(contentWidth, measurePair(measurer, panel.headerLeft, panel.headerRight));
        contentWidth = Math.max(contentWidth, measurePair(measurer, panel.timerLeft, panel.timerRight));
        contentWidth = Math.max(contentWidth, measurer.width(panel.themeLabel));
        contentWidth = Math.max(contentWidth, measurer.width(panel.themeValueLine));
        for (Row row : panel.rows) {
            int leftWidth = measurer.width(row.left);
            if (row.statusIcon >= 0) leftWidth += ICON_DRAW_SIZE + INLINE_STATUS_GAP;
            int width = leftWidth + 6 + measurer.width(row.right);
            if (width > contentWidth) contentWidth = width;
        }
        contentWidth += 6;
        return Math.max(80, contentWidth);
    }

    public static String rankColor(String ign) {
        String label = PlayerMetaCache.INSTANCE.getRankLabel(ign);
        if (isMvpPlusPlusOrSuperstar(label)) return "\u00a76";
        String harvested = PlayerMetaCache.INSTANCE.getRankColor(ign);
        if (harvested != null) return harvested;
        String fallback = rankColorFromStructuredLabel(label);
        return fallback == null ? "\u00a7f" : fallback;
    }

    private static boolean isMvpPlusPlusOrSuperstar(String label) {
        if (label == null) return false;
        String rank = label.toUpperCase(Locale.ROOT).replace(" ", "");
        return rank.contains("MVP++") || rank.equals("SUPERSTAR");
    }

    private static String rankColorFromStructuredLabel(String label) {
        if (label == null) return null;
        String rank = label.toUpperCase(Locale.ROOT).replace("_", " ").trim();
        if (rank.startsWith("MVP")) return "\u00a7b";
        if (rank.startsWith("VIP")) return "\u00a7a";
        if (rank.equals("YOUTUBE") || rank.equals("YT")) return "\u00a7c";
        if (rank.equals("OWNER") || rank.equals("ADMIN") || rank.equals("ADMINISTRATOR")) return "\u00a7c";
        if (rank.contains("MOD") || rank.contains("HELPER") || rank.contains("GAME MASTER")
                || rank.equals("GM")) return "\u00a72";
        return null;
    }

    public static String bbRankPrefix(String ign, boolean english) {
        String rank = english
                ? PlayerMetaCache.INSTANCE.getBbRankEnglish(ign)
                : PlayerMetaCache.INSTANCE.getBbRank(ign);
        return rank == null || rank.isEmpty() ? "" : rank + " ";
    }

    public static String lengthAsString(String theme) {
        if (theme == null || theme.isEmpty()) return "";
        String[] words = theme.split(" ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            out.append(words[i].length());
            if (i < words.length - 1) out.append('-');
        }
        return out.toString();
    }

    public static String trimLeadingChar(String value) {
        if (value == null || value.length() < 2) return value == null ? "" : value;
        char c = value.charAt(0);
        return c == ' ' || c == '0' ? value.substring(1) : value;
    }

    private static List<GtbPlayer> sortedPlayers(final GtbGame game, ScoreboardConfig config) {
        List<GtbPlayer> players = new ArrayList<GtbPlayer>(game.players.values());
        final boolean leftPlayersAtBottom = config != null && config.leftPlayersAtBottom;
        Collections.sort(players, new Comparator<GtbPlayer>() {
            @Override public int compare(GtbPlayer a, GtbPlayer b) {
                if (leftPlayersAtBottom) {
                    boolean aLeft = isLeaver(a);
                    boolean bLeft = isLeaver(b);
                    if (aLeft != bLeft) return aLeft ? 1 : -1;
                }
                int cmp = Integer.compare(effectiveScore(b, game), effectiveScore(a, game));
                if (cmp != 0) return cmp;
                boolean aOn = a != null && a.name != null && hasScoreIgnoreCase(game.visibleTrueScore, a.name);
                boolean bOn = b != null && b.name != null && hasScoreIgnoreCase(game.visibleTrueScore, b.name);
                if (aOn != bOn) return aOn ? -1 : 1;
                boolean aDown = isLeaver(a) || isInactive(a);
                boolean bDown = isLeaver(b) || isInactive(b);
                if (aDown != bDown) return aDown ? 1 : -1;
                String an = a == null || a.name == null ? "" : a.name;
                String bn = b == null || b.name == null ? "" : b.name;
                return an.compareTo(bn);
            }
        });
        return players;
    }

    static int[] densePlaces(List<GtbPlayer> sortedRows, GtbGame game) {
        int[] places = new int[sortedRows == null ? 0 : sortedRows.size()];
        int place = 0;
        Integer previousScore = null;
        for (int i = 0; i < places.length; i++) {
            GtbPlayer player = sortedRows.get(i);
            if (isLeaver(player)) continue;
            int score = effectiveScore(player, game);
            if (previousScore == null || score != previousScore.intValue()) {
                place++;
                previousScore = Integer.valueOf(score);
            }
            places[i] = place;
        }
        return places;
    }

    static String placePrefix(int place) {
        return place <= 0 ? "" : "\u00a7e" + place + ". ";
    }

    private static int measurePair(ScoreboardLayout.StringMeasurer measurer, String left, String right) {
        return measurer.width(left) + measurer.width(right);
    }

    public static boolean severeFlag(GtbPlayer player, GtbGame game) {
        if (player == null || player.isUser) return false;
        if (game == null) return false;
        if (isLeaver(player)) return false;
        Integer visible = scoreIgnoreCase(game.visibleTrueScore, player.name);
        int knownLowerBound = Math.max(player.totalMin(),
                Math.max(player.observedScoreFloor, player.preDisconnectScoreBaseline));
        boolean currentlyVisible = visible != null
                && visible.intValue() >= knownLowerBound;
        if (currentlyVisible) return false;
        if (player.unresolvedIdentity && anyScoreSeen(game)) return true;
        if (!game.userDisconnectedThisGame) return false;
        if (!player.disconnectUncertainRounds.isEmpty() || player.rejoinRoundScoreUncertain) return true;
        if (game.rejoinBuilderChangedBeforeRoundHeader) return true;
        return player.roundsMissedDuringDisconnect >= 1;
    }

    public static Integer authoritativeScore(GtbPlayer player, GtbGame game) {
        if (player == null || player.name == null || game == null) return null;
        Integer visible = scoreIgnoreCase(game.visibleTrueScore, player.name);
        Integer latest = scoreIgnoreCase(game.latestTrueScore, player.name);
        Integer floor = player.observedScoreFloor >= 0
                ? Integer.valueOf(player.observedScoreFloor)
                : null;
        return maxScore(visible, maxScore(latest, floor));
    }

    private static Integer maxScore(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return Integer.valueOf(Math.max(a.intValue(), b.intValue()));
    }

    public static String pendingBuilderFlag(GtbPlayer player, GtbGame game) {
        if (player == null || game == null || player.name == null) return "";
        if (game.correctGuessesThisRound <= 0) return "";
        if (game.currentRound <= 0 || player.pendingBuilderScoreRound <= 0) return "";
        if (player.pendingBuilderScoreRound != game.currentRound) return "";
        int roundIndex = game.currentRound - 1;
        if (roundIndex < 0 || player.points == null || roundIndex >= player.points.length) return "";
        PointEntry entry = player.points[roundIndex];
        if (entry == null || entry.kind != PointEntry.Kind.ZERO) return "";
        return " \u00a77(\u00a7b+\u00a77)";
    }

    public static int buildSlotIcon(GtbPlayer player, GtbGame game,
                                    boolean isBuildPhase, boolean isPrePhase,
                                    int tick) {
        boolean isBuilder = game != null && player == game.currentBuilder;
        boolean wasBuilder = player != null && player.buildRound > 0 && !isBuilder;
        return BuilderIconLogic.buildProgressIconFor(isBuilder, wasBuilder,
                isBuildPhase, isPrePhase, tick);
    }

    public static int statusIcon(GtbPlayer player) {
        return BuilderIconLogic.statusIconFor(isLeaver(player), isInactive(player));
    }

    public static int rowHighlightColor(boolean hasStatusIcon, boolean currentBuilder) {
        return rowHighlightColor(null, hasStatusIcon, currentBuilder);
    }

    public static int rowHighlightColor(ScoreboardConfig config,
                                        boolean hasStatusIcon, boolean currentBuilder) {
        int color;
        if (hasStatusIcon) color = 0x3D262626;
        else if (currentBuilder) color = 0x21FFFF55;
        else color = 0;
        return config == null ? color : config.applyHighlightIntensity(color);
    }

    public static int rowMarkerColor(int statusIcon, boolean currentBuilder) {
        return rowMarkerColor(null, statusIcon, currentBuilder);
    }

    public static int rowMarkerColor(ScoreboardConfig config, int statusIcon, boolean currentBuilder) {
        int color;
        if (statusIcon == BuilderIconLogic.IDX_EXIT) color = 0xCCFF5555;
        else if (statusIcon >= 0) color = 0xCCAAAAAA;
        else if (currentBuilder) color = 0xC7FFFF55;
        else color = 0;
        return config == null ? color : config.applyHighlightIntensity(color);
    }

    public static boolean isLeaver(GtbPlayer player) {
        if (player == null || player.name == null) return false;
        return player.leaverState == GtbPlayer.LeaverState.LEAVER
                || GameStats.INSTANCE.isLeaver(player.name);
    }

    public static boolean isInactive(GtbPlayer player) {
        return player != null && player.name != null && player.silentRoundsConsecutive >= 2;
    }

    public static int effectiveScore(GtbPlayer player, GtbGame game) {
        int lo = player == null ? 0
                : Math.max(player.totalMin(), player.preDisconnectScoreBaseline);
        if (player != null && player.name != null) {
            Integer observed = authoritativeScore(player, game);
            Integer tracked = trackedExactScoreIfNewer(player, game, observed);
            if (tracked != null) return tracked.intValue();
            if (observed != null && observed.intValue() >= lo) {
                return observed.intValue();
            }
        }
        int hi = clampedOffLeaderboardMax(player, game);
        return hi < lo ? lo : hi;
    }

    public static Integer trackedExactScoreIfNewer(GtbPlayer player, GtbGame game,
                                                   Integer observed) {
        if (player == null || game == null || observed == null) return null;
        if (game.roundCountUnknown) return null;

        int lo = player.totalMin();
        int hi = player.totalMax();
        if (lo != hi) return null;
        if (game.currentRound > 0 && player.authoritativeBuilderScoreRound == game.currentRound) {
            return Integer.valueOf(hi);
        }
        if (hi <= observed.intValue()) return null;
        return Integer.valueOf(hi);
    }

    public static String formatChip(GtbPlayer player, GtbGame game) {
        if (game == null || player == null || game.roundCountUnknown) return "";
        if (game.suppressPointBadgesUntilRoundHeader) return "";
        if (game.currentRound <= 0 || player.points == null) return "";
        if (player.suppressedPointBadgeRound == game.currentRound) return "";
        int roundIndex = game.currentRound - 1;
        if (roundIndex >= player.points.length) return "";
        PointEntry entry = player.points[roundIndex];
        if (entry == null || entry.kind == PointEntry.Kind.ZERO) return "";
        if (entry.kind == PointEntry.Kind.DEFINITE) {
            if (entry.value == 0) return "";
            return tierColor(entry.value) + "+" + entry.value;
        }
        if (entry.fork == null || entry.fork.isEmpty()) return "";

        List<Integer> sorted = displayForkValues(player, game, roundIndex, entry);
        Collections.sort(sorted, Collections.reverseOrder());
        StringBuilder out = new StringBuilder();
        int primary = sorted.get(0);
        out.append(primary == 0 ? "\u00a780" : tierColor(primary) + "+" + primary);
        if (sorted.size() > 1) {
            out.append(" \u00a77(");
            for (int i = 1; i < sorted.size(); i++) {
                if (i > 1) out.append("\u00a77,");
                int value = sorted.get(i).intValue();
                out.append(value == 0 ? "\u00a780" : tierColor(value) + "+" + value);
            }
            out.append("\u00a77)");
        }
        return out.toString();
    }

    public static String formatTotal(GtbPlayer player, GtbGame game, boolean isBuilder) {
        if (player == null) return totalColor(isBuilder, false) + "0";
        Integer observed = authoritativeScore(player, game);
        Integer tracked = trackedExactScoreIfNewer(player, game, observed);
        if (tracked != null) return totalColor(isBuilder, false) + tracked.intValue();

        int lo = Math.max(player.totalMin(), player.preDisconnectScoreBaseline);
        if (observed != null && observed.intValue() >= lo) {
            return totalColor(isBuilder, false) + observed.intValue();
        }
        int hi = clampedOffLeaderboardMax(player, game);
        if (hi < lo) hi = lo;
        if (lo == hi) return totalColor(isBuilder, false) + lo;
        if (hasInformationLossBound(player, game)) {
            return "\u00a77(" + totalColor(false, true) + hi
                    + "\u00a77," + totalColor(false, true) + lo + "\u00a77)";
        }
        return totalColor(isBuilder, false) + hi
                + " \u00a77(" + totalColor(false, true) + lo + "\u00a77)";
    }

    private static boolean hasInformationLossBound(GtbPlayer player, GtbGame game) {
        return player.unresolvedIdentity
                || !player.disconnectUncertainRounds.isEmpty()
                || player.rejoinRoundScoreUncertain
                || player.roundsMissedDuringDisconnect > 0
                || (game != null && game.rejoinBuilderChangedBeforeRoundHeader);
    }

    private static Integer scoreIgnoreCase(Map<String, Integer> scores, String ign) {
        if (scores == null || ign == null) return null;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            String key = entry.getKey();
            if (key != null && key.equalsIgnoreCase(ign)) return entry.getValue();
        }
        return null;
    }

    private static boolean hasScoreIgnoreCase(Map<String, Integer> scores, String ign) {
        return scoreIgnoreCase(scores, ign) != null;
    }

    private static boolean anyScoreSeen(GtbGame game) {
        if (game == null) return false;
        if (game.latestTrueScore != null) {
            for (Integer value : game.latestTrueScore.values()) {
                if (value != null && value.intValue() > 0) return true;
            }
        }
        for (GtbPlayer player : game.players.values()) {
            if (player != null && player.totalMax() > 0) return true;
        }
        return false;
    }

    private static int clampedOffLeaderboardMax(GtbPlayer player, GtbGame game) {
        if (player == null) return 0;
        int hi = Math.max(player.totalMax(), player.preDisconnectScoreBaseline);
        Integer floor = leaderboardFloor(game);
        return floor == null ? hi : Math.min(hi, floor.intValue());
    }

    private static Integer leaderboardFloor(GtbGame game) {
        if (game == null || game.visibleTrueScore == null || game.visibleTrueScore.isEmpty()) {
            return null;
        }
        Integer floor = null;
        for (Integer score : game.visibleTrueScore.values()) {
            if (score == null) continue;
            if (floor == null || score.intValue() < floor.intValue()) floor = score;
        }
        return floor;
    }

    private static List<Integer> displayForkValues(GtbPlayer player, GtbGame game,
                                                   int roundIndex, PointEntry entry) {
        List<Integer> values = new ArrayList<Integer>(entry.fork);
        if (player == null || player.name == null || player.points == null || game == null) {
            return values;
        }

        Integer visible = authoritativeScore(player, game);
        Integer exactOtherRounds = exactTotalExceptRound(player.points, roundIndex);
        if (visible != null && exactOtherRounds != null) {
            int residual = visible.intValue() - exactOtherRounds.intValue();
            if (entry.fork.contains(Integer.valueOf(residual))) {
                values.clear();
                values.add(Integer.valueOf(residual));
                return values;
            }
        }

        if (visible != null) return values;
        Integer floor = leaderboardFloor(game);
        if (floor == null) return values;

        int fixedMin = totalMinExceptRound(player.points, roundIndex);
        List<Integer> kept = new ArrayList<Integer>();
        for (Integer value : values) {
            if (value != null && fixedMin + value.intValue() <= floor.intValue()) {
                kept.add(value);
            }
        }
        return kept.isEmpty() ? values : kept;
    }

    private static Integer exactTotalExceptRound(PointEntry[] points, int excludedIndex) {
        if (points == null) return null;
        int sum = 0;
        for (int i = 0; i < points.length; i++) {
            if (i == excludedIndex) continue;
            PointEntry entry = points[i];
            if (entry == null || entry.kind == PointEntry.Kind.ZERO) continue;
            if (entry.kind == PointEntry.Kind.FORK) return null;
            sum += entry.value;
        }
        return Integer.valueOf(sum);
    }

    private static int totalMinExceptRound(PointEntry[] points, int excludedIndex) {
        if (points == null) return 0;
        int sum = 0;
        for (int i = 0; i < points.length; i++) {
            if (i == excludedIndex) continue;
            PointEntry entry = points[i];
            if (entry == null || entry.kind == PointEntry.Kind.ZERO) continue;
            sum += entry.min();
        }
        return sum;
    }

    private static String totalColor(boolean isBuilder, boolean isLowerBound) {
        if (isLowerBound) return "\u00a72";
        return "\u00a7a";
    }

    private static String tierColor(int value) {
        if (value <= 1) return "\u00a7a";
        if (value <= 2) return "\u00a7e";
        return "\u00a7c";
    }
}
