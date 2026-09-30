package com.gtbstructures.scoreboard;

import com.gtbstructures.ModernRuntimeBridge;
import com.gtbstructures.player.PlayerMetaCache;
import com.gtbstructures.stats.LangPatterns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Platform-neutral row splicing for the Hypixel-flavored scoreboard skin.
 * Vanilla Hypixel rows stay in place while GTBS replaces the leaderboard rows.
 */
public final class HypixelSkinScoreboardLogic {

    private HypixelSkinScoreboardLogic() {
    }

    public static final class PanelRows {
        public final String title;
        public final List<Row> rowsBottomToTop;

        private PanelRows(String title, List<Row> rowsBottomToTop) {
            this.title = title;
            this.rowsBottomToTop = rowsBottomToTop;
        }

        public int lineCount() {
            return 1 + rowsBottomToTop.size();
        }
    }

    public static final class Row {
        public final GtbPlayer player;
        public final String left;
        public final String right;
        public final int buildIcon;
        public final int statusIcon;
        public final boolean currentBuilder;

        private Row(GtbPlayer player, String left, String right, int buildIcon,
                    int statusIcon, boolean currentBuilder) {
            this.player = player;
            this.left = left == null ? "" : left;
            this.right = right == null ? "" : right;
            this.buildIcon = buildIcon;
            this.statusIcon = statusIcon;
            this.currentBuilder = currentBuilder;
        }
    }

    private static final class ObservedLeaderboardRow {
        final String name;
        final int score;

        ObservedLeaderboardRow(String name, int score) {
            this.name = name;
            this.score = score;
        }
    }

    public static boolean shouldRender(ScoreboardConfig config, GtbGame game,
                                       boolean previewActive) {
        return !previewActive
                && config != null
                && config.isCustomEnabled()
                && config.isSkin("hypixel")
                && game != null
                && CustomScoreboardLogic.isActiveRound(game.state);
    }

    public static PanelRows buildPanel(String title, List<String> vanillaRowsBottomToTop,
                                       GtbGame game, boolean showBbRanks, int tick) {
        return buildPanel(title, vanillaRowsBottomToTop, game, null, showBbRanks, tick);
    }

    public static PanelRows buildPanel(String title, List<String> vanillaRowsBottomToTop,
                                       GtbGame game, ScoreboardConfig config,
                                       boolean expanded, int tick) {
        return buildPanel(title, vanillaRowsBottomToTop, game, config, expanded, tick,
                game == null ? null : game.currentLangCode);
    }

    public static PanelRows buildPanel(String title, List<String> vanillaRowsBottomToTop,
                                       GtbGame game, ScoreboardConfig config,
                                       boolean expanded, int tick, String liveLangCode) {
        return buildPanel(title, vanillaRowsBottomToTop, game, config, expanded, tick,
                liveLangCode, null);
    }

    public static PanelRows buildPanel(String title, List<String> vanillaRowsBottomToTop,
                                       GtbGame game, ScoreboardConfig config,
                                       boolean expanded, int tick, String liveLangCode,
                                       String canonicalTheme) {
        if (game == null) return null;
        List<String> vanillaRows = vanillaRowsBottomToTop == null
                ? Collections.<String>emptyList()
                : vanillaRowsBottomToTop;
        if (vanillaRows.isEmpty()) return null;

        List<Row> lowerVanilla = new ArrayList<Row>();
        List<Row> upperVanilla = new ArrayList<Row>();
        List<ObservedLeaderboardRow> observedRows = new ArrayList<ObservedLeaderboardRow>();
        boolean[] replacementRows = new boolean[vanillaRows.size()];
        int insertionIndex = -1;
        LeaderboardRowClassifier.Session classifier =
                LeaderboardRowClassifier.forGame(game);

        for (int i = 0; i < vanillaRows.size(); i++) {
            String line = vanillaRows.get(i);
            LeaderboardRowClassifier.Classification classification =
                    classifier.classify(line);
            String knownName = classification.knownName;
            Integer score = classification.score;
            boolean builderValueRow = score != null && knownName != null
                    && isValueForNextBuilderLabel(vanillaRows, i);
            boolean leaderboardRow = !builderValueRow
                    && (classification.leaderboardRow
                    || (score != null && knownName != null));
            boolean replacementRow = isLeaderboardSeparator(line) || leaderboardRow;

            if (replacementRow) {
                replacementRows[i] = true;
                insertionIndex = i;
                if (knownName != null && score != null) {
                    addObservedRow(observedRows, knownName, score.intValue());
                }
            }
        }

        for (int i = 0; i < vanillaRows.size(); i++) {
            if (replacementRows[i]) continue;
            String line = expandThemeValueRow(vanillaRows, i, liveLangCode, canonicalTheme);
            String clean = ModernRuntimeBridge.cleanText(line);
            String right = showsHypixelRounds(config, expanded)
                    && LangPatterns.isScoreboardBuilderLabel(clean)
                    ? CustomScoreboardLogic.formatRoundProgress(game) : "";
            Row vanilla = new Row(null, line, right, -1, -1, false);
            if (insertionIndex < 0 || i < insertionIndex) lowerVanilla.add(vanilla);
            else upperVanilla.add(vanilla);
        }

        List<Row> rows = new ArrayList<Row>();
        rows.addAll(lowerVanilla);
        rows.addAll(buildCustomRows(game, observedRows, config, expanded, tick, liveLangCode));
        rows.addAll(upperVanilla);

        String panelTitle = title == null || title.trim().isEmpty()
                ? LangPatterns.scoreboardTitle(game.currentLangCode)
                : title;
        return new PanelRows(panelTitle, Collections.unmodifiableList(rows));
    }

    public static int measureContentWidth(PanelRows panel,
                                          ScoreboardLayout.StringMeasurer measurer) {
        if (panel == null || measurer == null) return 80;
        int contentWidth = measurer.width(panel.title);
        for (Row row : panel.rowsBottomToTop) {
            int leftWidth = measurer.width(row.left);
            if (row.statusIcon >= 0) {
                leftWidth += CustomScoreboardLogic.ICON_DRAW_SIZE
                        + CustomScoreboardLogic.INLINE_STATUS_GAP;
            }
            int width = leftWidth;
            if (!row.right.isEmpty()) width += 6 + measurer.width(row.right);
            if (width > contentWidth) contentWidth = width;
        }
        contentWidth += 6;
        return Math.max(80, contentWidth);
    }

    private static List<Row> buildCustomRows(final GtbGame game,
                                             final List<ObservedLeaderboardRow> observedRows,
                                             ScoreboardConfig config, boolean expanded, int tick,
                                             String liveLangCode) {
        boolean isBuildPhase = game.state == GtbGame.State.ROUND_BUILD;
        boolean isPrePhase = game.state == GtbGame.State.ROUND_PRE;
        boolean showPointsGained = config == null || config.showPointsGained(expanded);
        boolean showPlaces = config != null && config.showPlaces(expanded);
        boolean showTitles = config == null ? expanded : config.showTitles(expanded);
        boolean showEmblems = config == null || config.showEmblems(expanded);
        List<GtbPlayer> players = new ArrayList<GtbPlayer>(game.players.values());
        final boolean leftPlayersAtBottom = config != null && config.leftPlayersAtBottom;
        Collections.sort(players, new Comparator<GtbPlayer>() {
            @Override public int compare(GtbPlayer a, GtbPlayer b) {
                if (leftPlayersAtBottom) {
                    boolean aLeft = CustomScoreboardLogic.isLeaver(a);
                    boolean bLeft = CustomScoreboardLogic.isLeaver(b);
                    if (aLeft != bLeft) return aLeft ? 1 : -1;
                }
                int cmp = Integer.compare(effectiveScore(a, game, observedRows),
                        effectiveScore(b, game, observedRows));
                if (cmp != 0) return -cmp;
                boolean aOn = observedScore(observedRows, a == null ? null : a.name) != null;
                boolean bOn = observedScore(observedRows, b == null ? null : b.name) != null;
                if (aOn != bOn) return aOn ? -1 : 1;
                boolean aDown = CustomScoreboardLogic.isLeaver(a)
                        || CustomScoreboardLogic.isInactive(a);
                boolean bDown = CustomScoreboardLogic.isLeaver(b)
                        || CustomScoreboardLogic.isInactive(b);
                if (aDown != bDown) return aDown ? 1 : -1;
                String an = a == null || a.name == null ? "" : a.name;
                String bn = b == null || b.name == null ? "" : b.name;
                return an.compareTo(bn);
            }
        });

        List<Row> rowsBottomToTop = new ArrayList<Row>(players.size());
        int[] densePlaces = densePlaces(players, game, observedRows);
        for (int i = players.size() - 1; i >= 0; i--) {
            GtbPlayer player = players.get(i);
            boolean isBuilder = player == game.currentBuilder;
            int buildIcon = CustomScoreboardLogic.buildSlotIcon(player, game,
                    isBuildPhase, isPrePhase, tick);
            int statusIcon = CustomScoreboardLogic.statusIcon(player);
            String name = player.name == null ? "?" : player.name;
            String place = showPlaces ? CustomScoreboardLogic.placePrefix(densePlaces[i]) : "";
            String bbRank = showTitles ? localizedBbRankPrefix(player.name, liveLangCode) : "";
            String emblemToken = player.emblem != null && !player.emblem.isEmpty()
                    ? player.emblem : PlayerMetaCache.INSTANCE.getEmblem(player.name);
            emblemToken = PlayerMetaCache.ensureEmblemColor(emblemToken);
            String emblem = !showEmblems || emblemToken == null || emblemToken.isEmpty()
                    ? "" : " " + emblemToken;
            String left = place + bbRank + CustomScoreboardLogic.rankColor(player.name)
                    + name + emblem + CustomScoreboardLogic.pendingBuilderFlag(player, game);
            String chip = isPrePhase || !showPointsGained ? "" : CustomScoreboardLogic.formatChip(player, game);
            String total = CustomScoreboardLogic.formatTotal(player, game, isBuilder);
            String severe = CustomScoreboardLogic.severeFlag(player, game) ? " \u00a7c(!)" : "";
            String right = (chip.isEmpty() ? "" : chip + " ") + total + severe;
            rowsBottomToTop.add(new Row(player, left, right, buildIcon, statusIcon, isBuilder));
        }
        return rowsBottomToTop;
    }

    private static String localizedBbRankPrefix(String ign, String langCode) {
        Integer rankIndex = PlayerMetaCache.INSTANCE.getBbRankIndex(ign);
        if (rankIndex == null) return "";
        String title = LangPatterns.bbTitleForRankIndex(rankIndex.intValue(), langCode);
        return title == null || title.isEmpty() ? ""
                : LangPatterns.bbColorForRankIndex(rankIndex.intValue()) + title + " ";
    }

    private static int[] densePlaces(List<GtbPlayer> sortedRows, GtbGame game,
                                     List<ObservedLeaderboardRow> observedRows) {
        int[] places = new int[sortedRows == null ? 0 : sortedRows.size()];
        int place = 0;
        Integer previousScore = null;
        for (int i = 0; i < places.length; i++) {
            GtbPlayer player = sortedRows.get(i);
            if (CustomScoreboardLogic.isLeaver(player)) continue;
            int score = effectiveScore(player, game, observedRows);
            if (previousScore == null || score != previousScore.intValue()) {
                place++;
                previousScore = Integer.valueOf(score);
            }
            places[i] = place;
        }
        return places;
    }

    private static int effectiveScore(GtbPlayer player, GtbGame game,
                                      List<ObservedLeaderboardRow> observedRows) {
        if (player != null && player.name != null) {
            Integer observed = observedScore(observedRows, player.name);
            if (observed == null) observed = scoreIgnoreCase(game.visibleTrueScore, player.name);
            if (observed == null) observed = CustomScoreboardLogic.authoritativeScore(player, game);
            Integer tracked = CustomScoreboardLogic.trackedExactScoreIfNewer(player, game, observed);
            if (tracked != null) return tracked.intValue();
            if (observed != null && observed.intValue() >= player.totalMin()) {
                return observed.intValue();
            }
        }
        return CustomScoreboardLogic.effectiveScore(player, game);
    }

    private static void addObservedRow(List<ObservedLeaderboardRow> observedRows,
                                       String name, int score) {
        String key = lowerKey(name);
        for (int i = 0; i < observedRows.size(); i++) {
            ObservedLeaderboardRow existing = observedRows.get(i);
            if (lowerKey(existing.name).equals(key)) {
                if (score > existing.score) observedRows.set(i, new ObservedLeaderboardRow(name, score));
                return;
            }
        }
        observedRows.add(new ObservedLeaderboardRow(name, score));
    }

    private static Integer observedScore(List<ObservedLeaderboardRow> observedRows, String name) {
        String key = lowerKey(name);
        if (key.isEmpty()) return null;
        for (ObservedLeaderboardRow row : observedRows) {
            if (lowerKey(row.name).equals(key)) return Integer.valueOf(row.score);
        }
        return null;
    }

    private static Integer scoreIgnoreCase(Map<String, Integer> scores, String ign) {
        if (scores == null || ign == null) return null;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            String key = entry.getKey();
            if (key != null && key.equalsIgnoreCase(ign)) return entry.getValue();
        }
        return null;
    }

    private static boolean isValueForNextBuilderLabel(List<String> rows, int index) {
        if (index + 1 >= rows.size()) return false;
        String next = ModernRuntimeBridge.cleanText(rows.get(index + 1));
        return LangPatterns.isScoreboardBuilderLabel(next);
    }

    private static boolean showsHypixelRounds(ScoreboardConfig config, boolean expanded) {
        if (config == null) return false;
        ScoreboardConfig.DisplayOption option =
                ScoreboardConfig.normalizeOption(config.hypixelShowRounds);
        return option == ScoreboardConfig.DisplayOption.ON
                || (option == ScoreboardConfig.DisplayOption.EXPANDED && expanded);
    }

    private static String expandThemeValueRow(List<String> rows, int index,
                                              String liveLangCode, String canonicalTheme) {
        String line = rows.get(index);
        if (line == null || canonicalTheme == null || index + 1 >= rows.size()) return line;
        String next = ModernRuntimeBridge.cleanText(rows.get(index + 1));
        String themeLabel = LangPatterns.scoreboardThemeLabel(liveLangCode);
        if (themeLabel == null || !next.startsWith(themeLabel)) return line;

        String displayed = ModernRuntimeBridge.cleanText(line);
        String fullTheme = ModernRuntimeBridge.cleanText(canonicalTheme);
        if (displayed.isEmpty() || fullTheme.length() <= displayed.length()
                || !fullTheme.startsWith(displayed)) {
            return line;
        }

        int trailingFormatting = line.length();
        while (trailingFormatting >= 2 && line.charAt(trailingFormatting - 2) == '\u00a7') {
            trailingFormatting -= 2;
        }
        return line.substring(0, trailingFormatting)
                + fullTheme.substring(displayed.length())
                + line.substring(trailingFormatting);
    }

    private static boolean isLeaderboardSeparator(String line) {
        String clean = ModernRuntimeBridge.stripMinecraftFormatting(line).trim();
        if (clean.isEmpty()) return false;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c != '.' && c != '\u2026') return false;
        }
        return true;
    }

    private static String lowerKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
