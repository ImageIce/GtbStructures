package com.gtbstructures.player;

import com.gtbstructures.GtbStructures;
import com.gtbstructures.stats.LangPatterns;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BuilderLeaderboardFallback {
    private static final Logger LOGGER = LogManager.getLogger("GtbStructures");
    private static final String FILE_NAME = "gtbstructures_builder_leaderboard.txt";
    private static final String HYPIXEL_LEADERBOARD_URL = "https://hypixel.net/build-battle/leaderboard";
    private static final int MAX_LOCAL_RANKS = 10;
    private static final Pattern ROW_PATTERN = Pattern.compile("(?is)<tr[^>]*>.*?</tr>");
    private static final Pattern PLAYER_PATTERN = Pattern.compile("(?is)<a\\s+href=\"/player/[^\"]+\"[^>]*>\\s*([A-Za-z0-9_]{1,16})\\s*</a>");
    private static final Pattern DATA_PATTERN = Pattern.compile("(?is)<td\\s+class=\"data\"[^>]*>\\s*([0-9,]+)");
    public static final BuilderLeaderboardFallback INSTANCE = new BuilderLeaderboardFallback();

    private final List<String> roster = new ArrayList<String>();
    private File file;

    private BuilderLeaderboardFallback() {
    }

    public synchronized void init(File configDir) {
        if (configDir == null) return;
        file = new File(configDir, FILE_NAME);
        if (file.isFile()) {
            load();
        } else {
            roster.clear();
        }
    }

    public void refreshFromHypixelAsync() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    List<ScoreEntry> entries = fetchHypixelScoreEntries();
                    if (entries.size() < MAX_LOCAL_RANKS) return;
                    applyScoreEntries(entries);
                    LOGGER.info("Updated local Build Battle score leaderboard fallback from Hypixel site");
                } catch (Throwable t) {
                    LOGGER.warn("Build Battle score leaderboard fallback refresh failed: {}", t.getMessage());
                }
            }
        }, "GtbStructures-BB-Leaderboard-Refresh");
        t.setDaemon(true);
        t.start();
    }

    public synchronized Integer rankIndexForIgn(String ign) {
        int rank = rankForIgn(ign);
        return rank <= 0 ? null : LangPatterns.bbLeaderboardRankIndexForPosition(rank);
    }

    public synchronized int rankForIgn(String ign) {
        int index = indexOf(ign);
        return index < 0 ? -1 : index + 1;
    }

    public synchronized void observeRankIndex(String ign, int rankIndex) {
        Integer rank = LangPatterns.bbLeaderboardPositionForRankIndex(rankIndex);
        if (rank != null) {
            observeRank(ign, rank.intValue());
        }
    }

    public synchronized void observeRank(String ign, int rank) {
        String clean = cleanIgn(ign);
        if (clean == null || rank < 1 || rank > MAX_LOCAL_RANKS) return;

        int existing = indexOf(clean);
        if (existing >= 0) {
            String canonical = roster.remove(existing);
            int target = Math.min(rank - 1, roster.size());
            roster.add(target, canonical);
        } else {
            int target = Math.min(rank - 1, roster.size());
            roster.add(target, clean);
        }

        while (roster.size() > MAX_LOCAL_RANKS) {
            roster.remove(roster.size() - 1);
        }
        saveQuietly();
    }

    public synchronized List<String> snapshot() {
        return Collections.unmodifiableList(new ArrayList<String>(roster));
    }

    static List<String> parseTopScoreRosterForTest(String html) {
        List<ScoreEntry> entries = parseScoreEntries(html);
        Collections.sort(entries, SCORE_DESC);
        ArrayList<String> out = new ArrayList<String>();
        for (ScoreEntry entry : entries) {
            out.add(entry.ign);
            if (out.size() >= MAX_LOCAL_RANKS) break;
        }
        return out;
    }

    synchronized void resetForTest(String... names) {
        roster.clear();
        if (names != null) {
            for (String name : names) {
                addIfValid(name);
            }
        }
        file = null;
    }

    private void load() {
        roster.clear();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String clean = line.trim();
                if (clean.isEmpty() || clean.startsWith("#")) continue;
                addIfValid(clean);
                if (roster.size() >= MAX_LOCAL_RANKS) break;
            }
        } catch (IOException ignored) {
            roster.clear();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    private static List<ScoreEntry> fetchHypixelScoreEntries() throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(HYPIXEL_LEADERBOARD_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "GtbStructures/1.1");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            } finally {
                reader.close();
            }
            return parseScoreEntries(sb.toString());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private synchronized void applyScoreEntries(List<ScoreEntry> entries) {
        Collections.sort(entries, SCORE_DESC);
        ArrayList<String> next = new ArrayList<String>();
        for (ScoreEntry entry : entries) {
            if (entry == null || !PlayerMetaCache.isValidMcUsername(entry.ign)) continue;
            if (containsIgnoreCase(next, entry.ign)) continue;
            next.add(entry.ign);
            if (next.size() >= MAX_LOCAL_RANKS) break;
        }
        if (next.size() < MAX_LOCAL_RANKS) return;
        roster.clear();
        roster.addAll(next);
        saveQuietly();
    }

    private static List<ScoreEntry> parseScoreEntries(String html) {
        ArrayList<ScoreEntry> entries = new ArrayList<ScoreEntry>();
        if (html == null || html.isEmpty()) return entries;
        Matcher rowMatcher = ROW_PATTERN.matcher(html);
        while (rowMatcher.find()) {
            String row = rowMatcher.group();
            Matcher playerMatcher = PLAYER_PATTERN.matcher(row);
            if (!playerMatcher.find()) continue;
            String ign = playerMatcher.group(1).trim();
            ArrayList<Integer> data = new ArrayList<Integer>();
            Matcher dataMatcher = DATA_PATTERN.matcher(row);
            while (dataMatcher.find()) {
                data.add(parseNumber(dataMatcher.group(1)));
            }
            if (data.size() < 3) continue;
            int score = data.get(2).intValue();
            entries.add(new ScoreEntry(ign, score));
        }
        return entries;
    }

    private void addIfValid(String ign) {
        String clean = cleanIgn(ign);
        if (clean == null || indexOf(clean) >= 0) return;
        roster.add(clean);
    }

    private void saveQuietly() {
        if (file == null) return;
        PrintWriter writer = null;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) parent.mkdirs();
            writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8));
            writer.println("# GtbStructures local Build Battle top-builder roster.");
            writer.println("# One Minecraft username per line, rank order 1-10.");
            writer.println("# Packet/chat observations can reorder this file when a #N Builder marker is seen.");
            for (String ign : roster) {
                writer.println(ign);
            }
        } catch (IOException ignored) {
            // fallback data is optional
        } finally {
            if (writer != null) writer.close();
        }
    }

    private int indexOf(String ign) {
        if (ign == null) return -1;
        String lower = ign.toLowerCase(Locale.ROOT);
        for (int i = 0; i < roster.size(); i++) {
            if (roster.get(i).toLowerCase(Locale.ROOT).equals(lower)) return i;
        }
        return -1;
    }

    private static String cleanIgn(String ign) {
        if (ign == null) return null;
        String clean = ign.trim();
        return PlayerMetaCache.isValidMcUsername(clean) ? clean : null;
    }

    private static boolean containsIgnoreCase(List<String> values, String value) {
        if (values == null || value == null) return false;
        for (String existing : values) {
            if (existing != null && existing.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private static int parseNumber(String raw) {
        if (raw == null) return 0;
        try {
            return Integer.parseInt(raw.replace(",", "").trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static final Comparator<ScoreEntry> SCORE_DESC = new Comparator<ScoreEntry>() {
        @Override
        public int compare(ScoreEntry a, ScoreEntry b) {
            return Integer.compare(b.score, a.score);
        }
    };

    private static final class ScoreEntry {
        final String ign;
        final int score;

        ScoreEntry(String ign, int score) {
            this.ign = ign;
            this.score = score;
        }
    }
}
