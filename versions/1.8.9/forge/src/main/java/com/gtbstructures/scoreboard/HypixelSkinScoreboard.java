package com.gtbstructures.scoreboard;

import com.gtbstructures.diagnostics.RawPacketRecorder;
import com.gtbstructures.GtbStructures;
import com.gtbstructures.player.PlayerMetaCache;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.stats.LangPatterns;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * "hypixel" skin for the custom scoreboard.
 *
 * <p>Renders the vanilla Hypixel sidebar layout (title, all UI lines, server
 * footer, etc.) in their original localized form, but replaces the
 * leaderboard-row block with our own rows (icons outside, rank-coloured IGN
 * + emblem, delta chip, total). The leaderboard chunk is detected purely
 * structurally — a row is a leaderboard row iff it matches the shape
 * {@code "<MC-username>: <int>"}. Localized UI lines (Builder/Time/Theme
 * in any locale) all have non-int values and are passed through unchanged.
 *
 * <p>The vanilla sidebar is suppressed via {@code GuiIngameForge.renderObjective}
 * (see {@link GtbStructures#onClientTick}) — we render it ourselves so we
 * can inject the custom rows where the leaderboard was.
 */
public final class HypixelSkinScoreboard {

    /** Drawn size of the row icons (matches CustomScoreboard for visual parity). */
    private static final int ICON_DRAW_SIZE = 8;
    /** Gap between the row-icon's right edge and the panel's left edge. */
    private static final int ICON_OUTSIDE_GAP = 2;
    private static final int INLINE_STATUS_GAP = 2;

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CHAT) return;
        if (!shouldRender()) return;
        try {
            renderPanel();
        } catch (Throwable t) {
            GtbStructures.noteCustomScoreboardRenderFailure(t);
        }
    }

    private static boolean shouldRender() {
        if (!GtbStructures.modEnabled) return false;
        if (InterfacePreview.isActive()) return false;
        if (!GtbStructures.sbConfig.isCustomEnabled()) return false;
        if (!GtbStructures.sbConfig.isSkin("hypixel")) return false;
        GtbGame g = GameTracker.INSTANCE.game;
        if (g == null) return false;
        return GameStats.INSTANCE.shouldRenderRoundOverlays();
    }

    /** A single rendered row. {@link #player} is null for vanilla rows. */
    private static final class Row {
        final String left;       // formatted left-side text
        final String right;      // formatted right-side text (right-aligned)
        final int buildIcon;     // spinner/check overlay for the outside build slot
        final int statusIcon;    // left/away inline icon before the name
        final GtbPlayer player;  // null for vanilla rows
        final boolean dim;
        final boolean currentBuilder;
        Row(String left, String right, int buildIcon, int statusIcon, GtbPlayer player) {
            this(left, right, buildIcon, statusIcon, player, false, false);
        }
        Row(String left, String right, int buildIcon, int statusIcon, GtbPlayer player, boolean dim) {
            this(left, right, buildIcon, statusIcon, player, dim, false);
        }
        Row(String left, String right, int buildIcon, int statusIcon, GtbPlayer player,
            boolean dim, boolean currentBuilder) {
            this.left = left; this.right = right; this.buildIcon = buildIcon;
            this.statusIcon = statusIcon; this.player = player; this.dim = dim;
            this.currentBuilder = currentBuilder;
        }
    }

    private static final class ObservedLeaderboardRow {
        final String name;
        final Integer score;
        ObservedLeaderboardRow(String name, Integer score) {
            this.name = name;
            this.score = score;
        }
    }

    private void renderPanel() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) return;
        Scoreboard sb = mc.theWorld.getScoreboard();
        if (sb == null) return;
        ScoreObjective obj = sb.getObjectiveInDisplaySlot(1);
        if (obj == null) return;
        FontRenderer fr = mc.fontRendererObj;
        ScaledResolution sr = new ScaledResolution(mc);

        GtbGame g = GameTracker.INSTANCE.game;
        if (g == null) return;
        GameStats.TrackingState st = GameStats.INSTANCE.getState();
        boolean isBuildPhase = (st == GameStats.TrackingState.ROUND_BUILD);
        boolean isPrePhase   = (st == GameStats.TrackingState.ROUND_PRE);
        boolean textShadow = GtbStructures.sbConfig.textShadow;
        int backgroundColor = GtbStructures.sbConfig.backgroundColor();
        int titleBackgroundColor = GtbStructures.sbConfig.titleBackgroundColor();
        boolean expanded = CustomScoreboard.isPlayerListKeyDown(mc);
        boolean showRounds = GtbStructures.sbConfig.showHypixelRounds(expanded);
        String roundProgress = CustomScoreboard.roundProgressForScoreboard(
                g, isPrePhase);

        // ── Pull the same score list vanilla would render (1.8.9 GuiIngame) ──
        Collection<Score> rawScores = sb.getSortedScores(obj);
        List<Score> filtered = new ArrayList<Score>();
        for (Score s : rawScores) {
            String n = s.getPlayerName();
            if (n != null && !n.startsWith("#")) filtered.add(s);
        }
        if (filtered.size() > 15) {
            filtered = new ArrayList<Score>(filtered.subList(filtered.size() - 15, filtered.size()));
        }

        // ── Categorize each score row ──
        // Purely structural classification: rows shaped "<MC-username>: <int>"
        // are leaderboard rows; everything else is a UI line. Decoupled from
        // our internal tracking state so timing (e.g. early-game empty maps)
        // can't affect what's displayed.
        List<Row> upperVanilla = new ArrayList<Row>();
        List<Row> lowerVanilla = new ArrayList<Row>();
        List<ObservedLeaderboardRow> observedLeaderboard = new ArrayList<ObservedLeaderboardRow>();
        boolean seenLb = false;
        StringBuilder dbg = new StringBuilder();
        int rowIdx = 0;
        for (int scoreIndex = 0; scoreIndex < filtered.size(); scoreIndex++) {
            Score s = filtered.get(scoreIndex);
            String pname = s.getPlayerName();
            ScorePlayerTeam team = sb.getPlayersTeam(pname);
            String left = ScorePlayerTeam.formatPlayerName(team, pname);
            String classifierText = visibleTeamTextWithoutScoreKey(team, left);
            left = canonicalThemeValueRow(left, classifierText,
                    visibleScoreText(filtered, scoreIndex + 1, sb), g.currentTheme);
            String knownName = LeaderboardRowClassifier.knownLeaderboardName(classifierText, g);
            Integer embeddedScore = LeaderboardRowClassifier.displayedLeaderboardScore(classifierText);
            boolean builderValueRow = knownName != null
                    && isValueForNextBuilderLabel(filtered, scoreIndex, sb);
            boolean isLb = !builderValueRow
                    && (LeaderboardRowClassifier.isLeaderboardRow(classifierText, g)
                    || knownName != null);
            // The "..." separator vanilla inserts between the visible top-N
            // and the local user's own row (when the user isn't in the top-N)
            // is a leaderboard-internal artifact. Drop it once we've started
            // seeing real leaderboard rows so it doesn't orphan in lowerVanilla.
            if (!isLb && seenLb && isLeaderboardSeparator(classifierText)) {
                dbg.append(rowIdx++).append(":SEP ");
                continue;
            }
            if (isLb) {
                seenLb = true;
                if (knownName != null) {
                    observedLeaderboard.add(new ObservedLeaderboardRow(knownName, embeddedScore));
                }
                dbg.append(rowIdx++).append(":LB ");
                continue;
            }
            dbg.append(rowIdx++).append(seenLb ? ":Lo " : ":Up ");
            // Strip vanilla's red score number on UI rows — Hypixel exposes
            // throwaway ordering values (13, 14, ...) on every line, which
            // looks like garbage next to localized labels. Our injected
            // leaderboard rows use the right column for delta+total, so the
            // panel reads consistently with right=empty everywhere else.
            String right = builderLabelRightForScoreboard(
                    classifierText, showRounds, roundProgress);
            Row row = new Row(left, right, -1, -1, null);
            if (seenLb) lowerVanilla.add(row);
            else upperVanilla.add(row);
        }

        // ── Build our custom rows in the same shape the default skin uses ──
        List<Row> ourRows = buildCustomRows(g, isBuildPhase, isPrePhase, observedLeaderboard);

        // Vanilla iterates filtered with idx=1..N, rowY = bottomY - idx*h.
        // That means filtered[0] sits at the BOTTOM and filtered[N-1] at the
        // TOP. We must mirror this so localized lines stay in the right
        // position. The full bottom→top render order is therefore:
        //   [upperVanilla forward...] [ourRows forward...] [lowerVanilla forward...]
        // → reversed for top→bottom display would be the inverse, but we're
        // matching vanilla's own bottom→top idx loop, so we use forward.
        //
        // Concretely: idx=1 draws at the bottom. The row at the bottom of
        // filtered is filtered[0], which (after categorisation) is the first
        // upperVanilla entry that came earliest — this preserves Hypixel's
        // own ordering choice for every vanilla row.
        List<Row> rendered = new ArrayList<Row>();
        rendered.addAll(upperVanilla);
        rendered.addAll(ourRows);
        rendered.addAll(lowerVanilla);
        if (rendered.isEmpty()) return;

        // ── Measure widest line ──
        String displayName = obj.getDisplayName();
        recordCustomScoreboardSnapshot(displayName, rendered);
        int titleWidth = fr.getStringWidth(displayName);
        int contentWidth = titleWidth;
        for (Row r : rendered) {
            int lw = fr.getStringWidth(r.left);
            if (r.statusIcon >= 0) lw += ICON_DRAW_SIZE + INLINE_STATUS_GAP;
            int rw = (r.right == null || r.right.isEmpty()) ? 0 : (6 + fr.getStringWidth(r.right));
            int w = lw + rw;
            if (w > contentWidth) contentWidth = w;
        }
        // ── Layout (vanilla) ──
        int totalLines = rendered.size();
        int lineH = fr.FONT_HEIGHT;
        int totalPanelHeight = (totalLines + 1) * lineH; // rows plus title block
        int bottomY = sr.getScaledHeight() / 2 + totalPanelHeight / 2
                - GtbStructures.sbConfig.heightOffset;
        int rightX = sr.getScaledWidth() - 3;
        int leftX = rightX - contentWidth;
        int rightCellRight = rightX + 2;  // matches vanilla's "+ 2" overshoot

        // ── Render rows ──
        for (int i = 0; i < totalLines; i++) {
            int idx = i + 1;
            Row row = rendered.get(i);
            int rowY = bottomY - idx * lineH;

            // Row background
            Gui.drawRect(leftX - 2, rowY, rightCellRight, rowY + lineH, backgroundColor);
            boolean hasStatusIcon = row.statusIcon >= 0;
            if (!hasStatusIcon && row.currentBuilder) {
                CustomScoreboard.drawRowHighlight(leftX - 2, rowY, rightCellRight, lineH, false, true);
                CustomScoreboard.drawRowMarker(leftX - 2, rowY, lineH, -1, true);
            }

            // Left text, with left/away status inside the row before rank/name.
            int leftTextX = leftX;
            int iconY = rowY + (fr.FONT_HEIGHT + 1 - ICON_DRAW_SIZE) / 2 - 2;
            if (row.statusIcon >= 0) {
                leftTextX += ICON_DRAW_SIZE + INLINE_STATUS_GAP;
            }
            CustomScoreboard.drawText(fr, row.left, leftTextX, rowY, textShadow);
            // Right text (right-aligned)
            int rw = fr.getStringWidth(row.right);
            CustomScoreboard.drawText(fr, row.right, rightCellRight - rw, rowY, textShadow);
            if (hasStatusIcon) {
                CustomScoreboard.drawRowHighlight(leftX - 2, rowY, rightCellRight, lineH, true, false);
                CustomScoreboard.drawRowMarker(leftX - 2, rowY, lineH, row.statusIcon, false);
                BuilderIcons.draw(row.statusIcon, leftX, iconY, ICON_DRAW_SIZE,
                        BuilderIcons.tintFor(row.statusIcon), 0.5f);
            }

            // Build slot — drawn OUTSIDE the panel (to the left of leftX-2),
            // matching the default skin's gutter placement. The dark build
            // background is present for custom player rows; active spinner
            // frames use a matching masked background to avoid double-stroking.
            if (row.player != null) {
                int iconX = (leftX - 2) - ICON_OUTSIDE_GAP - ICON_DRAW_SIZE;
                int buildBgIcon = BuilderIcons.backgroundForBuildIcon(row.buildIcon);
                BuilderIcons.draw(buildBgIcon, iconX, iconY, ICON_DRAW_SIZE,
                        BuilderIcons.tintFor(BuilderIcons.IDX_BUILD_BG), 0.5f);
                if (row.buildIcon >= 0) {
                    BuilderIcons.draw(row.buildIcon, iconX, iconY, ICON_DRAW_SIZE,
                            BuilderIcons.tintFor(row.buildIcon), 0.5f);
                }
            }

            // Title block: drawn above the topmost row (idx == totalLines).
            if (idx == totalLines) {
                Gui.drawRect(leftX - 2, rowY - lineH - 1, rightCellRight, rowY - 1, titleBackgroundColor);
                Gui.drawRect(leftX - 2, rowY - 1, rightCellRight, rowY, backgroundColor);
                CustomScoreboard.drawText(fr, displayName,
                        leftX + contentWidth / 2 - titleWidth / 2,
                        rowY - lineH, textShadow);
            }
        }
    }

    private static void recordCustomScoreboardSnapshot(String title, List<Row> rendered) {
        List<RawPacketRecorder.ScoreboardLine> snapshotRows =
                new ArrayList<RawPacketRecorder.ScoreboardLine>();
        snapshotRows.add(new RawPacketRecorder.ScoreboardLine("title", title, ""));
        for (int i = rendered.size() - 1; i >= 0; i--) {
            Row row = rendered.get(i);
            String kind = row.player == null ? "vanilla" : "player";
            snapshotRows.add(new RawPacketRecorder.ScoreboardLine(kind, row.left, row.right));
        }
        RawPacketRecorder.INSTANCE.captureCustomScoreboard("hypixel", snapshotRows);
    }

    /** Hypixel uses {@link Score#getPlayerName()} as an invisible row-position
     *  key inserted between the scoreboard team's prefix and suffix. That key
     *  is not stable across clients/languages/encodings, so classification
     *  must be based on the stitched visible team text only. */
    private static String visibleTeamTextWithoutScoreKey(ScorePlayerTeam team, String fallback) {
        if (team == null) return fallback;
        String prefix = team.getColorPrefix();
        String suffix = team.getColorSuffix();
        return (prefix == null ? "" : prefix) + (suffix == null ? "" : suffix);
    }

    private static String visibleScoreText(List<Score> filtered, int scoreIndex, Scoreboard sb) {
        if (filtered == null || sb == null || scoreIndex < 0 || scoreIndex >= filtered.size()) {
            return "";
        }
        Score score = filtered.get(scoreIndex);
        if (score == null) return "";
        String playerName = score.getPlayerName();
        ScorePlayerTeam team = sb.getPlayersTeam(playerName);
        String formatted = ScorePlayerTeam.formatPlayerName(team, playerName);
        return visibleTeamTextWithoutScoreKey(team, formatted);
    }

    static String canonicalThemeValueRow(String formattedLeft, String classifierText,
                                         String nextClassifierText, String canonicalTheme) {
        if (formattedLeft == null || classifierText == null || canonicalTheme == null) {
            return formattedLeft;
        }
        String nextClean = StringUtils.stripControlCodes(nextClassifierText);
        if (!LangPatterns.isScoreboardThemeLabel(nextClean == null ? "" : nextClean.trim())) {
            return formattedLeft;
        }
        String oldTheme = StringUtils.stripControlCodes(classifierText);
        oldTheme = oldTheme == null ? "" : oldTheme.trim();
        if (oldTheme.isEmpty() || canonicalTheme.length() <= oldTheme.length()
                || !canonicalTheme.startsWith(oldTheme)) {
            return formattedLeft;
        }

        String strippedLeft = StringUtils.stripControlCodes(formattedLeft);
        int visibleStart = strippedLeft == null ? -1 : strippedLeft.indexOf(oldTheme);
        if (visibleStart < 0) return formattedLeft;
        int rawInsert = rawIndexAtVisibleOffset(
                formattedLeft, visibleStart + oldTheme.length());
        return formattedLeft.substring(0, rawInsert)
                + canonicalTheme.substring(oldTheme.length())
                + formattedLeft.substring(rawInsert);
    }

    private static int rawIndexAtVisibleOffset(String formatted, int visibleOffset) {
        int visible = 0;
        for (int raw = 0; raw < formatted.length(); raw++) {
            if (visible == visibleOffset) return raw;
            if (formatted.charAt(raw) == '\u00a7' && raw + 1 < formatted.length()) {
                raw++;
            } else {
                visible++;
            }
        }
        return formatted.length();
    }

    private static boolean isValueForNextBuilderLabel(List<Score> filtered, int scoreIndex, Scoreboard sb) {
        String nextText = visibleScoreText(filtered, scoreIndex + 1, sb);
        String clean = StringUtils.stripControlCodes(nextText);
        return LangPatterns.isScoreboardBuilderLabel(clean == null ? "" : clean.trim());
    }

    static String builderLabelRightForScoreboard(String classifierText,
                                                  boolean showRounds,
                                                  String roundProgress) {
        if (!showRounds) return "";
        String clean = StringUtils.stripControlCodes(classifierText);
        if (!LangPatterns.isScoreboardBuilderLabel(clean == null ? "" : clean.trim())) {
            return "";
        }
        return roundProgress == null ? "" : roundProgress;
    }

    /** Vanilla inserts a row of dots ("...") between the visible top-N
     *  leaderboard and the user's own pinned row when the user isn't in the
     *  top-N. Treated as part of the leaderboard chunk. */
    private static boolean isLeaderboardSeparator(String formattedLeft) {
        if (formattedLeft == null) return false;
        String clean = StringUtils.stripControlCodes(formattedLeft).trim();
        if (clean.isEmpty()) return false;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c != '.' && c != '…' /* ellipsis */) return false;
        }
        return true;
    }

    /** Build custom rows for the leaderboard-replacement chunk. Mirrors the
     *  default skin's sort order, formatting, and icon resolution so the two
     *  styles render identical row content — only the surrounding chrome
     *  differs. */
    private static List<Row> buildCustomRows(final GtbGame g,
                                             final boolean isBuildPhase,
                                             final boolean isPrePhase,
                                             List<ObservedLeaderboardRow> observedLeaderboard) {
        final List<ObservedLeaderboardRow> observedRows = observedLeaderboard == null
                ? Collections.<ObservedLeaderboardRow>emptyList()
                : observedLeaderboard;
        List<GtbPlayer> sorted = new ArrayList<GtbPlayer>(g.players.values());
        Collections.sort(sorted, new Comparator<GtbPlayer>() {
            @Override public int compare(GtbPlayer a, GtbPlayer b) {
                boolean aOn = observedScore(a, observedRows) != null;
                boolean bOn = observedScore(b, observedRows) != null;
                return CustomScoreboard.comparePlayerRows(a, b,
                        effectiveScoreForHypixelSkin(a, g, observedRows),
                        effectiveScoreForHypixelSkin(b, g, observedRows),
                        aOn, bOn, GtbStructures.sbConfig.leftPlayersAtBottom);
            }
        });

        // Vanilla iterates idx=1..N from the BOTTOM to the TOP, so emit our
        // rows in REVERSE rank order: rank-1 (top of leaderboard) goes last
        // in this list, getting the highest idx and therefore the topmost
        // row. Without the reversal the leaderboard would appear upside-down.
        List<Row> rows = new ArrayList<Row>();
        boolean expanded = CustomScoreboard.isPlayerListKeyDown(Minecraft.getMinecraft());
        boolean showPointsGained = GtbStructures.sbConfig.showPointsGained(expanded);
        boolean showPlaces = GtbStructures.sbConfig.showPlaces(expanded);
        boolean showTitles = GtbStructures.sbConfig.showTitles(expanded);
        boolean showEmblems = GtbStructures.sbConfig.showEmblems(expanded);
        int[] densePlaces = densePlaces(sorted, g, observedRows);
        for (int i = sorted.size() - 1; i >= 0; i--) {
            GtbPlayer pl = sorted.get(i);
            boolean isBuilder = (pl == g.currentBuilder);
            boolean leaver = CustomScoreboard.isLeaver(pl);
            boolean inactive = CustomScoreboard.isInactive(pl);
            boolean dim = leaver || inactive;
            String name = pl.name == null ? "?" : pl.name;
            String nameColor = CustomScoreboard.rankColor(pl.name);
            String place = showPlaces ? CustomScoreboard.placePrefix(densePlaces[i]) : "";
            String bbRank = showTitles ? CustomScoreboard.bbRankPrefix(pl.name) : "";
            String emblem = "";
            if (showEmblems) {
                String emblemTok = pl.emblem != null && !pl.emblem.isEmpty()
                        ? pl.emblem
                        : com.gtbstructures.player.PlayerMetaCache.INSTANCE.getEmblem(pl.name);
                emblemTok = com.gtbstructures.player.PlayerMetaCache.ensureEmblemColor(emblemTok);
                emblem = (emblemTok != null && !emblemTok.isEmpty()) ? (" " + emblemTok) : "";
            }
            String pending = CustomScoreboard.pendingBuilderFlag(pl, g);
            String left = place + bbRank + nameColor + name + emblem + pending;

            String chip = isPrePhase || !showPointsGained ? "" : CustomScoreboard.formatChip(pl, g);
            String total = formatTotalForHypixelSkin(pl, g, isBuilder,
                    observedScore(pl, observedRows));
            String severe = CustomScoreboard.severeFlag(pl, g) ? " §c(!)" : "";
            String right = (chip.isEmpty() ? "" : chip + " ") + total + severe;

            int buildIcon = CustomScoreboard.buildSlotIcon(pl, g, isBuildPhase, isPrePhase);
            int statusIcon = CustomScoreboard.statusIcon(pl);
            rows.add(new Row(left, right, buildIcon, statusIcon, pl, dim, isBuilder));
        }
        return rows;
    }

    private static int[] densePlaces(List<GtbPlayer> sortedRows, GtbGame g,
                                     List<ObservedLeaderboardRow> observedRows) {
        int[] places = new int[sortedRows == null ? 0 : sortedRows.size()];
        int place = 0;
        Integer previousScore = null;
        for (int i = 0; i < places.length; i++) {
            GtbPlayer player = sortedRows.get(i);
            if (CustomScoreboard.isLeaver(player)) continue;
            int score = effectiveScoreForHypixelSkin(player, g, observedRows);
            if (previousScore == null || score != previousScore.intValue()) {
                place++;
                previousScore = Integer.valueOf(score);
            }
            places[i] = place;
        }
        return places;
    }

    private static int effectiveScoreForHypixelSkin(GtbPlayer pl, GtbGame g,
                                                    List<ObservedLeaderboardRow> observedRows) {
        return effectiveScoreForHypixelSkin(pl, g, observedScore(pl, observedRows));
    }

    static int effectiveScoreForHypixelSkin(GtbPlayer pl, GtbGame g, Integer observed) {
        Integer authoritative = newerScore(observed, CustomScoreboard.authoritativeScore(pl, g));
        Integer tracked = CustomScoreboard.trackedExactScoreIfNewer(pl, g, authoritative);
        if (tracked != null) return tracked.intValue();
        return authoritative == null || authoritative.intValue() < pl.totalMin()
                ? CustomScoreboard.effectiveScore(pl, g)
                : authoritative.intValue();
    }

    static String formatTotalForHypixelSkin(GtbPlayer pl, GtbGame g, boolean isBuilder,
                                             Integer observed) {
        Integer authoritative = newerScore(observed, CustomScoreboard.authoritativeScore(pl, g));
        Integer tracked = CustomScoreboard.trackedExactScoreIfNewer(pl, g, authoritative);
        if (tracked != null) return "\u00a7a" + tracked.intValue();
        if (authoritative == null || authoritative.intValue() < pl.totalMin()) {
            return CustomScoreboard.formatTotal(pl, g, isBuilder);
        }
        return "\u00a7a" + authoritative.intValue();
    }

    private static Integer newerScore(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return Integer.valueOf(Math.max(a.intValue(), b.intValue()));
    }

    private static Integer observedScore(GtbPlayer pl, List<ObservedLeaderboardRow> observedRows) {
        if (pl == null || pl.name == null || observedRows == null) return null;
        for (ObservedLeaderboardRow row : observedRows) {
            if (row != null && row.name != null && row.name.equalsIgnoreCase(pl.name)) {
                return row.score;
            }
        }
        return null;
    }

}
