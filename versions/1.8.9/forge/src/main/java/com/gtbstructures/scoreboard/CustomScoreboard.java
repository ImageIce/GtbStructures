package com.gtbstructures.scoreboard;

import com.gtbstructures.diagnostics.RawPacketRecorder;
import com.gtbstructures.GtbStructures;
import com.gtbstructures.player.PlayerMetaCache;
import com.gtbstructures.stats.GameStats;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Faithful port of {@code com.aembr.guesstheutils.modules.CustomScoreboard}
 * (Fabric 1.21) onto Forge 1.8.9. Reads only from
 * {@link GameTracker#game}; never re-parses sidebar strings.
 */
public final class CustomScoreboard {

    /** Drawn size of the row icons. Slightly taller than the font to look
     *  proportional next to text — drawn with a -1px y offset so the icon's
     *  visual center sits on the text baseline. */
    private static final int ICON_DRAW_SIZE = 8;
    /** Horizontal gap between the icon's right edge and the panel's left edge. */
    private static final int ICON_OUTSIDE_GAP = 2;
    private static final int INLINE_STATUS_GAP = 2;
    private static final float ROW_HIGHLIGHT_Y_SHIFT = -0.5f;
    private static long lastRenderProbeMs;

    static final class MetadataRow {
        final String kind;
        final String left;
        final String right;

        MetadataRow(String kind, String left, String right) {
            this.kind = kind;
            this.left = left;
            this.right = right;
        }
    }

    @SubscribeEvent
    public void onRenderText(RenderGameOverlayEvent.Post event) {
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
        // The hypixel skin gets its own renderer (HypixelSkinScoreboard); skip
        // this fully-custom panel when that style is active.
        if (!GtbStructures.sbConfig.isSkin("default")) return false;
        GtbGame g = GameTracker.INSTANCE.game;
        if (g == null) return false;
        return GameStats.INSTANCE.shouldRenderRoundOverlays();
    }

    private void renderPanel() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return;
        FontRenderer fr = mc.fontRendererObj;
        ScaledResolution sr = new ScaledResolution(mc);
        GtbGame g = GameTracker.INSTANCE.game;
        if (g == null) return;

        GameStats.TrackingState st = GameStats.INSTANCE.getState();
        boolean isBuildPhase = (st == GameStats.TrackingState.ROUND_BUILD);
        boolean isPrePhase   = (st == GameStats.TrackingState.ROUND_PRE);

        // ── Header lines ──
        String headerLeft  = "§7Round";
        String headerRight = roundProgressForScoreboard(
                g, isPrePhase);

        String timerLabel;
        switch (st) {
            case ROUND_PRE:   timerLabel = "Starts In";  break;
            case ROUND_BUILD: timerLabel = "Time Left";  break;
            case ROUND_END:   timerLabel = "Next Round"; break;
            default:          timerLabel = "Time";       break;
        }
        String timerLeft  = "§7" + timerLabel;
        String timerValue = (g.currentTimer == null || g.currentTimer.isEmpty())
                ? "0:00" : trimLeadingChar(g.currentTimer);
        String timerRight = "§a" + timerValue;
        GtbPlayer displayedBuilder = displayedBuilder(g);
        String builderName = displayedBuilder == null ? null : displayedBuilder.name;
        List<MetadataRow> metadataRows = defaultMetadataRowsForScoreboard(
                headerLeft, headerRight, timerLeft, timerRight,
                GtbStructures.sbConfig.defaultExplicitlyShowBuilders, builderName);

        String themeLabel;
        String themeValueLine;
        if (g.currentTheme == null || g.currentTheme.isEmpty()) {
            themeLabel = "§7Theme:";
            themeValueLine = "§c???";
        } else {
            themeLabel = "§7Theme [§a" + getLengthAsString(g.currentTheme) + "§7]:";
            themeValueLine = "§a" + g.currentTheme;
        }

        // Sort players by the configured leaver grouping, live score, and stable ties.
        // Live score = leaderboard observation if present (Hypixel sidebar), else
        // our cumulative totalMax CLAMPED by the leaderboard floor. The clamp
        // is critical: an off-leaderboard player's totalMax is an *upper bound*,
        // not a real score. Hypixel's visible top-N tells us their actual score
        // is ≤ the lowest visible score, so a fork max of e.g. 8 must not rank
        // them above a leaderboard-visible player at 5.
        final GtbGame gameRef = g;
        List<GtbPlayer> rows = new ArrayList<GtbPlayer>(g.players.values());
        Collections.sort(rows, new Comparator<GtbPlayer>() {
            @Override public int compare(GtbPlayer a, GtbPlayer b) {
                boolean aOn = a.name != null && hasScoreIgnoreCase(gameRef.visibleTrueScore, a.name);
                boolean bOn = b.name != null && hasScoreIgnoreCase(gameRef.visibleTrueScore, b.name);
                return comparePlayerRows(a, b,
                        effectiveScore(a, gameRef), effectiveScore(b, gameRef),
                        aOn, bOn, GtbStructures.sbConfig.leftPlayersAtBottom);
            }
        });

        // ── Build per-row strings (left, right) ──
        // Icons are drawn OUTSIDE the panel (to the left of bgX1), so they
        // don't participate in content-width measurement at all.
        String[] leftParts  = new String[rows.size()];
        String[] rightParts = new String[rows.size()];
        int[] buildIcons = new int[rows.size()];
        int[] statusIcons = new int[rows.size()];
        boolean[] builderRows = new boolean[rows.size()];
        boolean expanded = isPlayerListKeyDown(mc);
        boolean showPointsGained = GtbStructures.sbConfig.showPointsGained(expanded);
        boolean showPlaces = GtbStructures.sbConfig.showPlaces(expanded);
        boolean showTitles = GtbStructures.sbConfig.showTitles(expanded);
        boolean showEmblems = GtbStructures.sbConfig.showEmblems(expanded);
        boolean textShadow = GtbStructures.sbConfig.textShadow;
        int[] densePlaces = densePlaces(rows, g);
        for (int i = 0; i < rows.size(); i++) {
            GtbPlayer pl = rows.get(i);
            boolean isBuilder = (pl == displayedBuilder);
            boolean leaver = isLeaver(pl);
            boolean inactive = isInactive(pl);
            builderRows[i] = isBuilder;
            buildIcons[i] = buildSlotIcon(pl, g, isBuildPhase, isPrePhase);
            statusIcons[i] = statusIcon(pl);
            String name = pl.name == null ? "?" : pl.name;
            // Builder no longer gets a §b highlight — the spinner icon is the
            // sole differentiator. Away/left names go to §7 (gray) for
            // readability without disappearing into §8 (dark gray).
            String nameColor = rankColor(pl.name);
            String place = showPlaces ? placePrefix(densePlaces[i]) : "";
            String bbRank = showTitles ? bbRankPrefix(pl.name, true) : "";
            // Prefer per-player emblem captured by the chat parser, then the
            // emblem harvested from the transient structured tab-list at game-start.
            String emblem = "";
            if (showEmblems) {
                String emblemTok = pl.emblem != null && !pl.emblem.isEmpty()
                        ? pl.emblem
                        : PlayerMetaCache.INSTANCE.getEmblem(pl.name);
                emblemTok = PlayerMetaCache.ensureEmblemColor(emblemTok);
                emblem = (emblemTok != null && !emblemTok.isEmpty()) ? (" " + emblemTok) : "";
            }
            // Emblems carry their own §-color from the chat-parse; only blank
            // them out for dim rows so leaver/away rows stay flat-gray.
            String pending = pendingBuilderFlag(pl, g);
            String left = place + bbRank + nameColor + name + emblem + pending;

            String chip = isPrePhase || !showPointsGained ? "" : formatChip(pl, g);
            String total = formatTotal(pl, g, isBuilder);
            String severe = severeFlag(pl, g) ? " §c(!)" : "";
            String right = (chip.isEmpty() ? "" : chip + " ") + total + severe;
            leftParts[i] = left;
            rightParts[i] = right;
        }
        List<RawPacketRecorder.ScoreboardLine> snapshotRows =
                new ArrayList<RawPacketRecorder.ScoreboardLine>();
        for (MetadataRow metadata : metadataRows) {
            snapshotRows.add(new RawPacketRecorder.ScoreboardLine(
                    metadata.kind, metadata.left, metadata.right));
        }
        for (int i = 0; i < rows.size(); i++) {
            snapshotRows.add(new RawPacketRecorder.ScoreboardLine("player",
                    leftParts[i], rightParts[i]));
        }
        snapshotRows.add(new RawPacketRecorder.ScoreboardLine("theme_label", themeLabel, ""));
        snapshotRows.add(new RawPacketRecorder.ScoreboardLine("theme", themeValueLine, ""));
        RawPacketRecorder.INSTANCE.captureCustomScoreboard("default", snapshotRows);
        // ── Measure widest line ──
        int contentWidth = 0;
        for (MetadataRow metadata : metadataRows) {
            contentWidth = Math.max(contentWidth,
                    measurePair(fr, metadata.left, metadata.right));
        }
        contentWidth = Math.max(contentWidth, fr.getStringWidth(themeLabel));
        contentWidth = Math.max(contentWidth, fr.getStringWidth(themeValueLine));
        for (int i = 0; i < rows.size(); i++) {
            int leftWidth = fr.getStringWidth(leftParts[i]);
            if (statusIcons[i] >= 0) leftWidth += ICON_DRAW_SIZE + INLINE_STATUS_GAP;
            int w = leftWidth + 6 /* min gap */ + fr.getStringWidth(rightParts[i]);
            if (w > contentWidth) contentWidth = w;
        }
        contentWidth += 6;  // outer gap
        if (contentWidth < 80) contentWidth = 80;

        // Lines: metadata + sep + N players + sep + theme label + theme value
        int playerLines = rows.size();
        int lineCount = metadataRows.size() + 1 + Math.max(playerLines, 0) + 1 + 2;
        ScoreboardLayout.Placement p = ScoreboardLayout.place(
                sr, fr, contentWidth, lineCount, GtbStructures.sbConfig.heightOffset);

        Gui.drawRect(p.bgX1, p.bgY1, p.bgX2, p.bgY2, GtbStructures.sbConfig.backgroundColor());

        int y = p.y;
        for (MetadataRow metadata : metadataRows) {
            drawPair(fr, metadata.left, metadata.right, p.x, y, contentWidth, textShadow);
            y += ScoreboardLayout.LINE_HEIGHT;
        }
        Gui.drawRect(p.x - 1, y + 3, p.x + contentWidth + 1, y + 4, 0x60AAAAAA);
        y += ScoreboardLayout.LINE_HEIGHT;

        // Player rows. Icons are rendered OUTSIDE the panel — to the left of
        // bgX1 — so they don't share the panel's translucent background and
        // can be visually larger than the font without distorting the row.
        // The build background is drawn in that slot; active spinner frames use
        // a matching masked background so the moving bar doesn't double-stroke.
        // Left/away status icons are drawn inline before the rank/name text.
        // Vertical centering: shift up by 1px so the icon's center sits roughly
        // on the text's optical baseline (font ~9px, icon 11px).
        for (int i = 0; i < rows.size(); i++) {
            int buildIcon = buildIcons[i];
            int iconX = p.bgX1 - ICON_OUTSIDE_GAP - ICON_DRAW_SIZE;
            int iconY = y + (ScoreboardLayout.LINE_HEIGHT - ICON_DRAW_SIZE) / 2 - 2;
            boolean hasStatusIcon = statusIcons[i] >= 0;
            if (!hasStatusIcon && builderRows[i]) {
                drawRowHighlight(p.bgX1, y, p.bgX2, ScoreboardLayout.LINE_HEIGHT, false, true);
                drawRowMarker(p.bgX1, y, ScoreboardLayout.LINE_HEIGHT, -1, true);
            }
            int buildBgIcon = BuilderIcons.backgroundForBuildIcon(buildIcon);
            BuilderIcons.draw(buildBgIcon, iconX, iconY, ICON_DRAW_SIZE,
                    BuilderIcons.tintFor(BuilderIcons.IDX_BUILD_BG), 0.5f);
            if (buildIcon >= 0) {
                BuilderIcons.draw(buildIcon, iconX, iconY, ICON_DRAW_SIZE,
                        BuilderIcons.tintFor(buildIcon), 0.5f);
            }
            int textX = p.x;
            int statusIcon = statusIcons[i];
            if (statusIcon >= 0) {
                textX += ICON_DRAW_SIZE + INLINE_STATUS_GAP;
            }
            drawText(fr, leftParts[i], textX, y, textShadow);
            int rw = fr.getStringWidth(rightParts[i]);
            drawText(fr, rightParts[i], p.x + contentWidth - rw, y, textShadow);
            if (hasStatusIcon) {
                drawRowHighlight(p.bgX1, y, p.bgX2, ScoreboardLayout.LINE_HEIGHT, true, false);
                drawRowMarker(p.bgX1, y, ScoreboardLayout.LINE_HEIGHT, statusIcon, false);
                BuilderIcons.draw(statusIcon, p.x, iconY, ICON_DRAW_SIZE,
                        BuilderIcons.tintFor(statusIcon), 0.5f);
            }
            y += ScoreboardLayout.LINE_HEIGHT;
        }

        Gui.drawRect(p.x - 1, y + 3, p.x + contentWidth + 1, y + 4, 0x60AAAAAA);
        y += ScoreboardLayout.LINE_HEIGHT;

        // Theme label / value: left-aligned. Value gets a one-space indent
        // under the label to read as a continuation, not a header repeat.
        drawText(fr, themeLabel, p.x, y, textShadow);
        y += ScoreboardLayout.LINE_HEIGHT;
        int spaceWidth = fr.getStringWidth(" ");
        drawText(fr, themeValueLine, p.x + spaceWidth, y, textShadow);
    }

    static String roundProgressForScoreboard(int currentRound, int totalRounds,
                                             boolean roundCountUnknown, boolean prePhase) {
        int displayRound = prePhase ? Math.max(1, currentRound + 1) : currentRound;
        return formatRoundProgress(displayRound, totalRounds, roundCountUnknown);
    }

    static String roundProgressForScoreboard(GtbGame game, boolean prePhase) {
        if (game == null) return formatRoundProgress(0, 0, false);
        // A builder/scoreboard row can implicitly establish round 1 before the
        // authoritative Round: 1/Y chat arrives. Keep that startup pre-round at
        // 1; once round 1 is confirmed, a pre-round means the upcoming round.
        boolean implicitInitialRound = prePhase && game.currentRound == 1
                && !game.initialRoundHeaderConfirmed && game.lastCompletedRound == 0;
        int displayRound = prePhase && !implicitInitialRound
                ? Math.max(1, game.currentRound + 1) : game.currentRound;
        return formatRoundProgress(displayRound, game.totalRounds, game.roundCountUnknown);
    }

    private static String formatRoundProgress(int displayRound, int totalRounds,
                                              boolean roundCountUnknown) {
        String roundDigits = roundCountUnknown || displayRound <= 0
                ? "?" : String.valueOf(displayRound);
        String totalDigits = totalRounds > 0 ? String.valueOf(totalRounds) : "?";
        return "\u00a7a" + roundDigits + "\u00a77/\u00a7a" + totalDigits;
    }

    static List<MetadataRow> defaultMetadataRowsForScoreboard(
            String headerLeft, String headerRight, String timerLeft, String timerRight,
            boolean explicitlyShowBuilder, String builderName) {
        List<MetadataRow> rows = new ArrayList<MetadataRow>();
        rows.add(new MetadataRow("header", headerLeft, headerRight));
        if (explicitlyShowBuilder) {
            rows.add(new MetadataRow("builder", "\u00a77Builder",
                    builderValueForScoreboard(builderName)));
        }
        rows.add(new MetadataRow("timer", timerLeft, timerRight));
        return rows;
    }

    static String builderValueForScoreboard(String builderName) {
        if (builderName == null || builderName.isEmpty()) return "\u00a7c???";
        return rankColor(builderName) + builderName;
    }

    /** Hypixel rank → name color code. Defaults to §f (white) for unranked /
     *  unknown so we don't dim regular players to gray indiscriminately. */
    static String rankColor(String ign) {
        if (ign == null) return "§f";
        // Sidebar-harvested § color is the most reliable source on Hypixel BB.
        // Lobby rows provide the initial roster colors, while in-game roster
        // rows can supply colors for identities introduced after a nick change.
        String harvested = PlayerMetaCache.INSTANCE.getRankColor(ign);
        if (harvested != null) return harvested;
        PlayerMetaCache.RankInfo info = PlayerMetaCache.INSTANCE.byIgn(ign);
        if (info == null || info.hypixelRank == null) return "§f";
        String r = info.hypixelRank.toUpperCase();
        if (r.contains("MVP++") || r.equals("MVP++") || r.equals("MVP+++")) return "§6";
        if (r.startsWith("MVP")) return "§b";
        if (r.startsWith("VIP")) return "§a";
        if (r.equals("YOUTUBE") || r.equals("YT")) return "§c";
        if (r.equals("OWNER") || r.equals("ADMIN") || r.equals("ADMINISTRATOR")) return "§c";
        if (r.contains("MOD") || r.contains("HELPER") || r.contains("GAME MASTER") || r.equals("GM")) return "§2";
        return "§f";
    }

    static boolean isPlayerListKeyDown(Minecraft mc) {
        if (mc == null || mc.gameSettings == null || mc.gameSettings.keyBindPlayerList == null) {
            return false;
        }
        if (mc.gameSettings.keyBindPlayerList.isKeyDown()) return true;
        return isKeyboardKeyDown(mc.gameSettings.keyBindPlayerList.getKeyCode());
    }

    private static boolean isKeyboardKeyDown(int keyCode) {
        if (keyCode <= 0) return false;
        try {
            return Keyboard.isCreated() && Keyboard.isKeyDown(keyCode);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static String bbRankPrefix(String ign) {
        return bbRankPrefix(ign, false);
    }

    static String bbRankPrefix(String ign, boolean english) {
        String rank = english
                ? PlayerMetaCache.INSTANCE.getBbRankEnglish(ign)
                : PlayerMetaCache.INSTANCE.getBbRank(ign);
        return rank == null || rank.isEmpty() ? "" : rank + " ";
    }

    /** Severe (!) means the displayed cumulative score may be stale. Every
     *  off-sidebar player stays marked through an unresolved disconnect gap;
     *  a pre-gap exact total is only the lower baseline, never post-gap proof. */
    static boolean severeFlag(GtbPlayer pl, GtbGame g) {
        if (pl == null || pl.isUser) return false;
        if (g == null) return false;
        if (isLeaver(pl)) return false;
        Integer visibleScore = pl.name == null ? null : scoreIgnoreCase(g.visibleTrueScore, pl.name);
        int knownLowerBound = Math.max(pl.totalMin(),
                Math.max(pl.observedScoreFloor, pl.preDisconnectScoreBaseline));
        boolean currentlyVisible = visibleScore != null
                && visibleScore.intValue() >= knownLowerBound;
        if (currentlyVisible) return false;
        if (pl.unresolvedIdentity && anyScoreSeen(g)) return true;
        if (!g.userDisconnectedThisGame) return false;
        if (!pl.disconnectUncertainRounds.isEmpty() || pl.rejoinRoundScoreUncertain) return true;
        if (g.rejoinBuilderChangedBeforeRoundHeader) return true;
        if (pl.roundsMissedDuringDisconnect >= 1) return true;
        return false;
    }

    static Integer authoritativeScore(GtbPlayer pl, GtbGame g) {
        if (pl == null || pl.name == null || g == null) return null;
        Integer visible = scoreIgnoreCase(g.visibleTrueScore, pl.name);
        Integer latest = scoreIgnoreCase(g.latestTrueScore, pl.name);
        Integer floor = pl.observedScoreFloor >= 0
                ? Integer.valueOf(pl.observedScoreFloor)
                : null;
        return maxScore(visible, maxScore(latest, floor));
    }

    private static Integer maxScore(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return Integer.valueOf(Math.max(a.intValue(), b.intValue()));
    }

    private static Integer scoreIgnoreCase(Map<String, Integer> scores, String ign) {
        if (scores == null || ign == null) return null;
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            String key = e.getKey();
            if (key != null && key.equalsIgnoreCase(ign)) return e.getValue();
        }
        return null;
    }

    private static boolean hasScoreIgnoreCase(Map<String, Integer> scores, String ign) {
        return scoreIgnoreCase(scores, ign) != null;
    }

    static String pendingBuilderFlag(GtbPlayer pl, GtbGame g) {
        if (pl == null || g == null || pl.name == null) return "";
        if (g.correctGuessesThisRound <= 0) return "";
        if (g.currentRound <= 0 || pl.pendingBuilderScoreRound <= 0) return "";
        if (pl.pendingBuilderScoreRound != g.currentRound) return "";
        int r = g.currentRound - 1;
        if (r < 0 || pl.points == null || r >= pl.points.length) return "";
        if (pl.points[r].kind != PointEntry.Kind.ZERO) return "";
        return " \u00a77(\u00a7b+\u00a77)";
    }

    private static boolean anyScoreSeen(GtbGame g) {
        if (g == null) return false;
        if (g.latestTrueScore != null) {
            for (Integer v : g.latestTrueScore.values()) {
                if (v != null && v.intValue() > 0) return true;
            }
        }
        for (GtbPlayer p : g.players.values()) {
            if (p != null && p.totalMax() > 0) return true;
        }
        return false;
    }

    static int rowIcon(GtbPlayer pl, GtbGame g, boolean isBuildPhase, boolean isPrePhase) {
        return buildSlotIcon(pl, g, isBuildPhase, isPrePhase);
    }

    static int buildSlotIcon(GtbPlayer pl, GtbGame g, boolean isBuildPhase, boolean isPrePhase) {
        boolean isBuilder = (pl == displayedBuilder(g));
        boolean wasBuilder = (pl.buildRound > 0 && !isBuilder);
        return BuilderIcons.buildProgressIconFor(isBuilder, wasBuilder, isBuildPhase, isPrePhase);
    }

    static GtbPlayer displayedBuilder(GtbGame g) {
        if (g == null) return null;
        return g.state == GtbGame.State.ROUND_END && g.roundEndBuilder != null
                ? g.roundEndBuilder : g.currentBuilder;
    }

    static int statusIcon(GtbPlayer pl) {
        boolean leaver = isLeaver(pl);
        boolean inactive = isInactive(pl);
        return BuilderIcons.statusIconFor(leaver, inactive);
    }

    static int rowHighlightColor(boolean hasStatusIcon, boolean currentBuilder) {
        if (hasStatusIcon) return GtbStructures.sbConfig.applyHighlightIntensity(0x3D262626);
        if (currentBuilder) return GtbStructures.sbConfig.applyHighlightIntensity(0x21FFFF55);
        return 0;
    }

    static int rowMarkerColor(int statusIcon, boolean currentBuilder) {
        if (statusIcon == BuilderIcons.IDX_EXIT) return 0xCCFF5555;
        if (statusIcon >= 0) return 0xCCAAAAAA;
        if (currentBuilder) return 0xC7FFFF55;
        return 0;
    }

    static int[] densePlaces(List<GtbPlayer> sortedRows, GtbGame g) {
        int[] places = new int[sortedRows == null ? 0 : sortedRows.size()];
        int place = 0;
        Integer previousScore = null;
        for (int i = 0; i < places.length; i++) {
            GtbPlayer player = sortedRows.get(i);
            if (isLeaver(player)) continue;
            int score = effectiveScore(player, g);
            if (previousScore == null || score != previousScore.intValue()) {
                place++;
                previousScore = Integer.valueOf(score);
            }
            places[i] = place;
        }
        return places;
    }

    static int comparePlayerRows(GtbPlayer a, GtbPlayer b, int aScore, int bScore,
                                 boolean aOnLeaderboard, boolean bOnLeaderboard,
                                 boolean leftPlayersAtBottom) {
        if (leftPlayersAtBottom) {
            boolean aLeft = isLeaver(a);
            boolean bLeft = isLeaver(b);
            if (aLeft != bLeft) return aLeft ? 1 : -1;
        }

        int cmp = Integer.compare(bScore, aScore);
        if (cmp != 0) return cmp;
        // Hypixel's visible leaderboard ordering is authoritative for ties.
        if (aOnLeaderboard != bOnLeaderboard) return aOnLeaderboard ? -1 : 1;
        boolean aDown = isLeaver(a) || isInactive(a);
        boolean bDown = isLeaver(b) || isInactive(b);
        if (aDown != bDown) return aDown ? 1 : -1;
        String an = a == null || a.name == null ? "" : a.name;
        String bn = b == null || b.name == null ? "" : b.name;
        return an.compareTo(bn);
    }

    static String placePrefix(int place) {
        return place <= 0 ? "" : "\u00a7e" + place + ". ";
    }

    static void drawRowHighlight(int left, int rowY, int right, int rowHeight,
                                 boolean hasStatusIcon, boolean currentBuilder) {
        int color = rowHighlightColor(hasStatusIcon, currentBuilder);
        if (color == 0) return;
        float top = rowY + ROW_HIGHLIGHT_Y_SHIFT;
        float bottom = rowY + rowHeight + ROW_HIGHLIGHT_Y_SHIFT;
        drawFloatRect(left, top, right, bottom, color);
    }

    static void drawRowMarker(int left, int rowY, int rowHeight,
                              int statusIcon, boolean currentBuilder) {
        int color = rowMarkerColor(statusIcon, currentBuilder);
        if (color == 0) return;
        float top = rowY + ROW_HIGHLIGHT_Y_SHIFT;
        float bottom = rowY + rowHeight + ROW_HIGHLIGHT_Y_SHIFT;
        drawFloatRect(left - 1, top, left, bottom, color);
    }

    private static void drawFloatRect(float left, float top, float right, float bottom, int color) {
        if (left < right) {
            float tmp = left;
            left = right;
            right = tmp;
        }
        if (top < bottom) {
            float tmp = top;
            top = bottom;
            bottom = tmp;
        }

        float a = (float)(color >> 24 & 255) / 255.0F;
        float r = (float)(color >> 16 & 255) / 255.0F;
        float g = (float)(color >> 8 & 255) / 255.0F;
        float b = (float)(color & 255) / 255.0F;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(770, 771);
        GlStateManager.color(r, g, b, a);
        renderer.begin(7, DefaultVertexFormats.POSITION);
        renderer.pos((double)left, (double)bottom, 0.0D).endVertex();
        renderer.pos((double)right, (double)bottom, 0.0D).endVertex();
        renderer.pos((double)right, (double)top, 0.0D).endVertex();
        renderer.pos((double)left, (double)top, 0.0D).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** A player is a true leaver if either:
     *  • GameStats's chat-event tracker confirmed they left (builder_left, etc.), OR
     *  • our own LeaverState was promoted to LEAVER. POTENTIAL_LEAVER is NOT a leaver here. */
    static boolean isLeaver(GtbPlayer pl) {
        if (pl == null || pl.name == null) return false;
        if (pl.leaverState == GtbPlayer.LeaverState.LEAVER) return true;
        return GameStats.INSTANCE.isLeaver(pl.name);
    }

    /** A player is "away" (zzz icon) when they have been silent for 2 or more
     *  consecutive present rounds — i.e. neither chatted nor correctly guessed
     *  for two full rounds in a row, including the inter-round interval, while
     *  present in the game. Rounds in which the local user was disconnected
     *  don't count (see GameTracker.onUserLeave/onUserRejoin). */
    static boolean isInactive(GtbPlayer pl) {
        if (pl == null || pl.name == null) return false;
        return pl.silentRoundsConsecutive >= 2;
    }

    /** Live cumulative score for sort positioning.
     *  • On-leaderboard: authoritative sidebar value (Hypixel).
     *  • Off-leaderboard: totalMax clamped by the leaderboard floor — Hypixel's
     *    visible top-N implies they sit ≤ the lowest visible score. Without the
     *    clamp, a fork-max of e.g. 8 would float them above a leaderboard player
     *    at 5, which is wrong. */
    static int effectiveScore(GtbPlayer pl, GtbGame g) {
        int lo = pl == null ? 0 : Math.max(pl.totalMin(), pl.preDisconnectScoreBaseline);
        if (pl != null && pl.name != null) {
            Integer lb = authoritativeScore(pl, g);
            Integer tracked = trackedExactScoreIfNewer(pl, g, lb);
            if (tracked != null) return tracked.intValue();
            // Stale observations below the proven minimum must not win.
            if (lb != null && lb.intValue() >= lo) return lb;
        }
        int hi = clampedOffLeaderboardMax(pl, g);
        return hi < lo ? lo : hi;
    }

    static Integer trackedExactScoreIfNewer(GtbPlayer pl, GtbGame g, Integer observed) {
        if (pl == null || g == null || observed == null) return null;
        if (g.roundCountUnknown) return null;

        Integer builderTotal = GameTracker.authoritativeBuilderTotal(pl, g);
        if (builderTotal != null) return builderTotal;

        int lo = pl.totalMin();
        int hi = pl.totalMax();
        if (lo != hi) return null;
        if (hi <= observed.intValue()) return null;
        return Integer.valueOf(hi);
    }

    private static int clampedOffLeaderboardMax(GtbPlayer pl, GtbGame g) {
        if (pl == null) return 0;
        int hi = Math.max(pl.totalMax(), pl.preDisconnectScoreBaseline);
        Integer floor = leaderboardFloor(g);
        return floor == null ? hi : Math.min(hi, floor.intValue());
    }

    private static Integer leaderboardFloor(GtbGame g) {
        if (g == null || g.visibleTrueScore == null || g.visibleTrueScore.isEmpty()) return null;
        Integer floor = null;
        for (Integer score : g.visibleTrueScore.values()) {
            if (score == null) continue;
            if (floor == null || score.intValue() < floor.intValue()) floor = score;
        }
        return floor;
    }

    private static List<Integer> displayForkValues(GtbPlayer pl, GtbGame g, int roundIndex,
                                                   PointEntry entry) {
        List<Integer> values = new ArrayList<Integer>(entry.fork);
        if (pl == null || pl.name == null || pl.points == null || g == null) return values;

        Integer visible = authoritativeScore(pl, g);
        Integer exactOtherRounds = exactTotalExceptRound(pl.points, roundIndex);
        if (visible != null && exactOtherRounds != null) {
            int residual = visible.intValue() - exactOtherRounds.intValue();
            if (entry.fork.contains(Integer.valueOf(residual))) {
                values.clear();
                values.add(Integer.valueOf(residual));
                return values;
            }
        }

        if (visible != null) return values;
        Integer floor = leaderboardFloor(g);
        if (floor == null) return values;

        int fixedMin = totalMinExceptRound(pl.points, roundIndex);
        List<Integer> kept = new ArrayList<Integer>();
        for (Integer v : values) {
            if (v != null && fixedMin + v.intValue() <= floor.intValue()) {
                kept.add(v);
            }
        }
        return kept.isEmpty() ? values : kept;
    }

    private static Integer exactTotalExceptRound(PointEntry[] points, int excludedIndex) {
        if (points == null) return null;
        int sum = 0;
        for (int i = 0; i < points.length; i++) {
            if (i == excludedIndex) continue;
            PointEntry e = points[i];
            if (e == null || e.kind == PointEntry.Kind.ZERO) continue;
            if (e.kind == PointEntry.Kind.FORK) return null;
            sum += e.value;
        }
        return Integer.valueOf(sum);
    }

    private static int totalMinExceptRound(PointEntry[] points, int excludedIndex) {
        if (points == null) return 0;
        int sum = 0;
        for (int i = 0; i < points.length; i++) {
            if (i == excludedIndex) continue;
            PointEntry e = points[i];
            if (e == null || e.kind == PointEntry.Kind.ZERO) continue;
            sum += e.min();
        }
        return sum;
    }

    /** Chip for current round's slot. ZERO → empty; DEFINITE → "+N"; FORK → "+max(+other,...)". */
    static String formatChip(GtbPlayer pl, GtbGame g) {
        if (g == null || pl == null || g.roundCountUnknown) return "";
        if (g.suppressPointBadgesUntilRoundHeader) return "";
        if (g.currentRound <= 0 || pl.points == null) return "";
        if (pl.suppressedPointBadgeRound == g.currentRound) return "";
        int r = g.currentRound - 1;
        if (r >= pl.points.length) return "";
        PointEntry e = pl.points[r];
        if (e == null || e.kind == PointEntry.Kind.ZERO) return "";
        if (e.kind == PointEntry.Kind.DEFINITE) {
            if (e.value == 0) return "";
            return tierColor(e.value) + "+" + e.value;
        }
        // Fork
        if (e.fork == null || e.fork.isEmpty()) return "";
        List<Integer> sorted = displayForkValues(pl, g, r, e);
        Collections.sort(sorted, Collections.reverseOrder());
        StringBuilder sb = new StringBuilder();
        int primary = sorted.get(0);
        sb.append(primary == 0 ? "§80" : tierColor(primary) + "+" + primary);
        if (sorted.size() > 1) {
            sb.append(" §7(");
            for (int i = 1; i < sorted.size(); i++) {
                if (i > 1) sb.append("§7,");
                int v = sorted.get(i);
                sb.append(v == 0 ? "§80" : tierColor(v) + "+" + v);
            }
            sb.append("§7)");
        }
        return sb.toString();
    }

    static boolean hasInformationLossBounds(GtbPlayer pl, GtbGame g) {
        if (pl == null || g == null) return false;
        return pl.unresolvedIdentity
                || !pl.disconnectUncertainRounds.isEmpty()
                || pl.rejoinRoundScoreUncertain
                || pl.roundsMissedDuringDisconnect >= 1
                || g.rejoinBuilderChangedBeforeRoundHeader;
    }

    /** Total: authoritative when present; otherwise render provenance-specific bounds. */
    static String formatTotal(GtbPlayer pl, GtbGame g, boolean isBuilder) {
        Integer lb = authoritativeScore(pl, g);
        Integer tracked = trackedExactScoreIfNewer(pl, g, lb);
        if (tracked != null) return totalColor(isBuilder, false) + tracked.intValue();
        int lo = Math.max(pl.totalMin(), pl.preDisconnectScoreBaseline);
        // A leaderboard observation below the proven minimum is stale (e.g.
        // the all-zero game-start board carried across a leave/rejoin); fall
        // through to the tracked range instead of resetting the row to it.
        if (lb != null && lb.intValue() >= lo) return totalColor(isBuilder, false) + lb;
        int hi = clampedOffLeaderboardMax(pl, g);
        if (hi < lo) hi = lo;
        if (lo == hi) return totalColor(isBuilder, false) + lo;
        if (hasInformationLossBounds(pl, g)) {
            return "\u00a77(" + totalColor(false, true) + hi
                    + "\u00a77," + totalColor(false, true) + lo + "\u00a77)";
        }
        return totalColor(isBuilder, false) + hi
                + " §7(" + totalColor(false, true) + lo + "§7)";
    }

    /** Lower-bound parens → §2; otherwise → §a. Builder no longer gets a
     *  §b highlight; the spinner icon is the sole builder differentiator. */
    private static String totalColor(boolean isBuilder, boolean isLowerBound) {
        if (isLowerBound) return "§2";
        return "§a";
    }

    /** 1 → §a green, 2 → §e yellow, 3+ → §c red (mirrors GTU's tier coloring). */
    private static String tierColor(int v) {
        if (v <= 1) return "§a";
        if (v <= 2) return "§e";
        return "§c";
    }

    private static String getLengthAsString(String theme) {
        if (theme == null || theme.isEmpty()) return "";
        String[] words = theme.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            sb.append(words[i].length());
            if (i < words.length - 1) sb.append('-');
        }
        return sb.toString();
    }

    private static String trimLeadingChar(String s) {
        if (s == null || s.length() < 2) return s == null ? "" : s;
        char c = s.charAt(0);
        if (c == ' ' || c == '0') return s.substring(1);
        return s;
    }

    private static int measurePair(FontRenderer fr, String left, String right) {
        return fr.getStringWidth(left) + fr.getStringWidth(right);
    }

    private static void drawPair(FontRenderer fr, String left, String right,
                                 int x, int y, int contentWidth, boolean shadow) {
        drawText(fr, left, x, y, shadow);
        int rw = fr.getStringWidth(right);
        drawText(fr, right, x + contentWidth - rw, y, shadow);
    }

    static void drawText(FontRenderer fr, String text, int x, int y, boolean shadow) {
        if (shadow) {
            fr.drawStringWithShadow(text, x, y, 0xFFFFFF);
        } else {
            fr.drawString(text, x, y, 0xFFFFFF);
        }
    }

}
