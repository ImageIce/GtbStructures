package com.gtbstructures.stats;

import com.gtbstructures.commands.CommandGtb;
import com.gtbstructures.GtbStructures;
import com.gtbstructures.player.PlayerMetaCache;
import com.gtbstructures.translation.TranslationData;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.TreeSet;

/** Reads canonical local GTB session logs and builds aggregate stats reports. */
public final class StatsArchive {
    private static final Pattern SESSION_FILE =
            Pattern.compile("^(?:interrupted_)?\\d{8}_\\d{6}\\.json$");
    private static final String LIVE_SESSION_FILE = "current.json";
    private static final String STATS_USAGE =
            "Usage: /gtbs stats session [days] | /gtbs stats theme [entries|all]";
    private static final String THEME_USAGE =
            "Usage: /gtbs stats theme [entries|all]";
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final long RAW_LOG_OVERLAP_TOLERANCE_MS = 2L * 60L * 1000L;
    private static final long STATS_PROGRESS_INTERVAL_MS = 15000L;
    private static final long THEME_EXPORT_PREHEAT_TTL_MS = 10L * 60L * 1000L;
    private static final String INTERRUPTED_PREFIX = "interrupted_";
    private static final int DEFAULT_THEME_ENTRIES = 10;
    private static final int MAX_THEME_ENTRIES = 20;
    private static final int MAX_THEME_EXPORT_PREHEATS = 8;
    private static final DecimalFormat TWO_DECIMAL =
            new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat UP_TO_TWO =
            new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private static final ExecutorService WORKER =
            Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "GtbStructures-StatsArchive");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    private static final ScheduledExecutorService PROGRESS_WORKER =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(
                            runnable, "GtbStructures-StatsProgress");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    private static final Map<String, CachedSessionFile> SESSION_CACHE =
            new LinkedHashMap<String, CachedSessionFile>();
    private static final Map<String, CachedThemeSummary> THEME_EXPORT_PREHEAT =
            new LinkedHashMap<String, CachedThemeSummary>();

    private StatsArchive() {}

    public static void handleCommand(final ICommandSender sender, String[] args) {
        if (sender == null) return;
        if (args == null || args.length == 0
                || (!"session".equalsIgnoreCase(args[0])
                && !"theme".equalsIgnoreCase(args[0]))) {
            sender.addChatMessage(new ChatComponentText(CommandGtb.head + STATS_USAGE));
            return;
        }

        final boolean theme = "theme".equalsIgnoreCase(args[0]);
        boolean parsedExportTheme = false;
        boolean parsedThemeAll = false;
        String parsedThemeCapNotice = null;
        final Integer days;
        final int themeEntries;
        if (theme) {
            if (args.length >= 2 && "all".equalsIgnoreCase(args[1])) {
                parsedThemeAll = true;
                if (args.length == 3 && "export".equalsIgnoreCase(args[2])) {
                    parsedExportTheme = true;
                } else if (args.length != 2) {
                    sender.addChatMessage(new ChatComponentText(CommandGtb.head
                            + THEME_USAGE));
                    return;
                }
                days = null;
                themeEntries = DEFAULT_THEME_ENTRIES;
            } else if (args.length >= 2 && "export".equalsIgnoreCase(args[1])) {
                parsedExportTheme = true;
                if (args.length != 2) {
                    sender.addChatMessage(new ChatComponentText(CommandGtb.head
                            + THEME_USAGE));
                    return;
                }
                days = null;
                themeEntries = DEFAULT_THEME_ENTRIES;
            } else {
                if (args.length > 2) {
                    sender.addChatMessage(new ChatComponentText(CommandGtb.head
                            + THEME_USAGE));
                    return;
                }
                Integer parsedEntries = parsePositiveWhole(args.length == 2 ? args[1] : null);
                if (args.length == 2 && parsedEntries == null) {
                    sender.addChatMessage(new ChatComponentText(CommandGtb.head
                            + "Entries must be a positive whole number."));
                    return;
                }
                days = null;
                if (parsedEntries == null) {
                    themeEntries = DEFAULT_THEME_ENTRIES;
                } else if (parsedEntries.intValue() > MAX_THEME_ENTRIES) {
                    parsedThemeCapNotice = themeEntriesCapNotice(parsedEntries.intValue());
                    themeEntries = MAX_THEME_ENTRIES;
                } else {
                    themeEntries = parsedEntries.intValue();
                }
            }
        } else {
            if (args.length > 2) {
                sender.addChatMessage(new ChatComponentText(CommandGtb.head
                        + "Usage: /gtbs stats session [days]"));
                return;
            }
            days = parsePositiveWhole(args.length == 2 ? args[1] : null);
            if (args.length == 2 && days == null) {
                sender.addChatMessage(new ChatComponentText(CommandGtb.head
                        + "Days must be a positive whole number."));
                return;
            }
            themeEntries = DEFAULT_THEME_ENTRIES;
        }
        final boolean exportTheme = parsedExportTheme;
        final boolean themeAll = parsedThemeAll;
        final String themeCapNotice = parsedThemeCapNotice;

        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.mcDataDir == null) {
            sender.addChatMessage(new ChatComponentText(CommandGtb.head
                    + "The Minecraft data directory is unavailable."));
            return;
        }
        if (!themeAll && mc.thePlayer == null) {
            sender.addChatMessage(new ChatComponentText(CommandGtb.head
                    + "The current player is unavailable."));
            return;
        }
        final File minecraftDataDir = mc.mcDataDir;
        final File statsDir = GameExporter.getStatsDir(minecraftDataDir);
        final File logCacheDir = statsDir == null
                ? null
                : new File(statsDir, "minecraft_log_theme_cache");
        final List<File> minecraftLogDirs = themeAll
                ? null
                : Collections.singletonList(new File(minecraftDataDir, "logs"));
        final PlayerIdentity identity = themeAll ? null : currentIdentity(mc);
        if (!themeAll && (identity == null || identity.displayIgn() == null)) {
            sender.addChatMessage(new ChatComponentText(CommandGtb.head
                    + "The current player identity is unavailable."));
            return;
        }
        final String themeExportKey = theme
                ? themeExportPreheatKey(minecraftDataDir, themeAll, identity)
                : null;
        final String currentLanguage = mappedLanguage(GtbStructures.lastLangCode);
        String notice = exportTheme
                ? "Exporting theme occurrence list..."
                : themeAll
                ? "Loading theme occurrence stats..."
                : theme
                ? "Loading your theme occurrence stats..."
                : "Loading your session stats...";
        if (themeCapNotice != null) {
            sender.addChatMessage(new ChatComponentText(CommandGtb.head + themeCapNotice));
        }
        sender.addChatMessage(new ChatComponentText(CommandGtb.head + notice));
        WORKER.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<IChatComponent> messages;
                    if (theme) {
                        ThemeSummary summary = exportTheme
                                ? preheatedThemeExport(themeExportKey,
                                        System.currentTimeMillis())
                                : null;
                        if (summary == null) {
                            StatsProgressNotifier progressNotifier =
                                    new StatsProgressNotifier();
                            try {
                                List<File> resolvedMinecraftLogDirs = themeAll
                                        ? discoverMinecraftLogDirs(minecraftDataDir)
                                        : minecraftLogDirs;
                                summary = summarizeThemes(
                                        statsDir, resolvedMinecraftLogDirs, identity,
                                        System.currentTimeMillis(), logCacheDir,
                                        progressNotifier);
                                preheatThemeExport(themeExportKey, summary,
                                        System.currentTimeMillis());
                                progressNotifier.finish();
                            } catch (Throwable throwable) {
                                progressNotifier.cancel();
                                throw throwable;
                            }
                        }
                        if (exportTheme) {
                            File file = exportThemeList(statsDir, summary);
                            messages = Collections.<IChatComponent>singletonList(
                                    GameExporter.localFileNotice(
                                            CommandGtb.head
                                                    + "Theme occurrence list exported as ",
                                            mc.mcDataDir, file));
                        } else {
                            messages = renderTheme(summary, themeEntries,
                                    System.currentTimeMillis(), themeAll);
                        }
                    } else {
                         SessionSummary summary = summarizeSessions(
                                 statsDir, identity, days, System.currentTimeMillis());
                        if (currentLanguage != null) summary.language = currentLanguage;
                        messages = renderSession(summary);
                    }
                    scheduleMessages(messages);
                } catch (final Throwable throwable) {
                    GameExporter.LOGGER.warn("Failed to aggregate local GTB stats", throwable);
                    scheduleMessages(Collections.<IChatComponent>singletonList(
                            new ChatComponentText(CommandGtb.head
                                    + "Failed to read the local stats archive.")));
                }
            }
        });
    }

    private static final class StatsProgressNotifier
            implements ThemeStatsProgressListener {
        private final ScheduledFuture<?> heartbeat;
        private ThemeSessionProgress sessionProgress;
        private MinecraftLogThemeExtractor.Progress latest;
        private boolean closed;

        StatsProgressNotifier() {
            heartbeat = PROGRESS_WORKER.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    sendHeartbeat();
                }
            }, STATS_PROGRESS_INTERVAL_MS, STATS_PROGRESS_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);
        }

        @Override
        public synchronized void onSessionProgress(
                ThemeSessionProgress progress) {
            if (!closed && progress != null) sessionProgress = progress;
        }

        @Override
        public synchronized void onProgress(
                MinecraftLogThemeExtractor.Progress progress) {
            if (!closed && progress != null) latest = progress;
        }

        private synchronized void sendHeartbeat() {
            if (closed) return;
            ThemeStatsProgress progress = combinedProgress();
            if (!shouldReportThemeStatsProgress(progress)) return;
            send(progress);
        }

        synchronized void finish() {
            if (closed) return;
            closed = true;
            heartbeat.cancel(false);
        }

        synchronized void cancel() {
            if (closed) return;
            closed = true;
            heartbeat.cancel(false);
        }

        private ThemeStatsProgress combinedProgress() {
            ThemeSessionProgress sessions = sessionProgress;
            MinecraftLogThemeExtractor.Progress logs = latest;
            if (logs != null && logs.totalFiles > 0) {
                int base = sessions != null && sessions.totalFiles > 0 ? 50 : 0;
                int span = sessions != null && sessions.totalFiles > 0 ? 50 : 100;
                int percent = Math.min(99,
                        base + (logs.percent() * span / 100));
                String detail = sessionDetail(sessions)
                        + "; \u00a7flogs \u00a77"
                        + logs.processedFiles + "/"
                        + logs.totalFiles + ", \u00a7f"
                        + mb(logs.processedBytes) + "\u00a77/\u00a7f"
                        + mb(logs.totalBytes) + "\u00a77 MB, \u00a7f"
                        + logs.cacheHits + "\u00a77 cached";
                return new ThemeStatsProgress(percent, detail, logs.complete);
            }
            if (sessions == null || sessions.totalFiles <= 0) return null;
            int percent = Math.min(49, sessions.percent() / 2);
            return new ThemeStatsProgress(percent, sessionDetail(sessions),
                    sessions.complete);
        }

        private String sessionDetail(ThemeSessionProgress progress) {
            if (progress == null) {
                return "\u00a7fsessions \u00a770/0";
            }
            return "\u00a7fsessions \u00a77"
                    + progress.processedFiles + "/"
                    + progress.totalFiles + ", \u00a7f"
                    + mb(progress.processedBytes) + "\u00a77/\u00a7f"
                    + mb(progress.totalBytes) + "\u00a77 MB";
        }

        private void send(ThemeStatsProgress progress) {
            String text = CommandGtb.head + "Loading theme occurrence stats... "
                    + progress.percent + "% (" + progress.detail + "\u00a77)";
            scheduleMessages(Collections.<IChatComponent>singletonList(
                    new ChatComponentText(text)));
        }
    }

    private interface ThemeStatsProgressListener
            extends MinecraftLogThemeExtractor.ProgressListener {
        void onSessionProgress(ThemeSessionProgress progress);
    }

    private static final class ThemeStatsProgress {
        final int percent;
        final String detail;
        final boolean complete;

        ThemeStatsProgress(int percent, String detail, boolean complete) {
            this.percent = percent;
            this.detail = detail;
            this.complete = complete;
        }
    }

    private static final class ThemeSessionProgress {
        final int totalFiles;
        final int processedFiles;
        final long totalBytes;
        final long processedBytes;
        final boolean complete;

        ThemeSessionProgress(int totalFiles, int processedFiles,
                             long totalBytes, long processedBytes,
                             boolean complete) {
            this.totalFiles = totalFiles;
            this.processedFiles = processedFiles;
            this.totalBytes = totalBytes;
            this.processedBytes = processedBytes;
            this.complete = complete;
        }

        int percent() {
            if (totalBytes > 0L) {
                return (int) Math.min(100L, processedBytes * 100L / totalBytes);
            }
            return totalFiles <= 0
                    ? 100
                    : Math.min(100, processedFiles * 100 / totalFiles);
        }
    }

    private static final class ThemeSessionProgressState {
        final int totalFiles;
        final long totalBytes;
        int processedFiles;
        long processedBytes;

        ThemeSessionProgressState(List<File> files) {
            this.totalFiles = files == null ? 0 : files.size();
            long bytes = 0L;
            if (files != null) {
                for (File file : files) {
                    if (file != null) bytes += Math.max(0L, file.length());
                }
            }
            this.totalBytes = bytes;
        }

        ThemeSessionProgress record(File file, boolean complete) {
            processedFiles++;
            if (file != null) processedBytes += Math.max(0L, file.length());
            return snapshot(complete);
        }

        ThemeSessionProgress snapshot(boolean complete) {
            return new ThemeSessionProgress(totalFiles, processedFiles,
                    totalBytes, processedBytes, complete);
        }
    }

    static boolean shouldReportThemeStatsProgress(
            ThemeStatsProgress progress) {
        return progress != null && !progress.complete;
    }

    static boolean shouldReportLogCacheProgress(
            MinecraftLogThemeExtractor.Progress progress) {
        return progress != null && !progress.complete && progress.totalFiles > 0;
    }

    private static String mb(long bytes) {
        if (bytes <= 0L) return "0";
        return UP_TO_TWO.format(bytes / 1048576.0D);
    }

    static String themeExportPreheatKey(File minecraftDataDir, boolean allLogs,
                                         PlayerIdentity identity) {
        if (minecraftDataDir == null || (!allLogs && identity == null)) return null;
        StringBuilder key = new StringBuilder(sessionCacheKey(minecraftDataDir));
        key.append('|').append(allLogs ? "all" : "player");
        if (!allLogs) {
            key.append('|').append(identityKeyPart(identity.playerUuid));
            key.append('|').append(identityKeyPart(identity.trueIgn));
            key.append('|').append(identityKeyPart(identity.activeIgn));
        }
        return key.toString();
    }

    static void preheatThemeExport(String key, ThemeSummary summary, long now) {
        if (key == null || summary == null) return;
        synchronized (THEME_EXPORT_PREHEAT) {
            pruneThemeExportPreheat(now);
            THEME_EXPORT_PREHEAT.remove(key);
            while (THEME_EXPORT_PREHEAT.size() >= MAX_THEME_EXPORT_PREHEATS) {
                String oldest = THEME_EXPORT_PREHEAT.keySet().iterator().next();
                THEME_EXPORT_PREHEAT.remove(oldest);
            }
            THEME_EXPORT_PREHEAT.put(key, new CachedThemeSummary(now, summary));
        }
    }

    static ThemeSummary preheatedThemeExport(String key, long now) {
        if (key == null) return null;
        synchronized (THEME_EXPORT_PREHEAT) {
            pruneThemeExportPreheat(now);
            CachedThemeSummary cached = THEME_EXPORT_PREHEAT.get(key);
            return cached == null ? null : cached.summary;
        }
    }

    static void clearThemeExportPreheat() {
        synchronized (THEME_EXPORT_PREHEAT) {
            THEME_EXPORT_PREHEAT.clear();
        }
    }

    private static void pruneThemeExportPreheat(long now) {
        for (String key : new ArrayList<String>(THEME_EXPORT_PREHEAT.keySet())) {
            CachedThemeSummary cached = THEME_EXPORT_PREHEAT.get(key);
            if (cached == null || now < cached.createdAt
                    || now - cached.createdAt > THEME_EXPORT_PREHEAT_TTL_MS) {
                THEME_EXPORT_PREHEAT.remove(key);
            }
        }
    }

    private static String identityKeyPart(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static void scheduleMessages(final List<IChatComponent> messages) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (mc.thePlayer == null || messages == null) return;
                for (IChatComponent message : messages) {
                    mc.thePlayer.addChatMessage(message);
                }
            }
        });
    }

    static SessionSummary summarizeSessions(File statsDir, String ign,
                                            Integer days, long now) {
        return summarizeSessions(statsDir, PlayerIdentity.single(ign), days, now);
    }

    static SessionSummary summarizeSessions(File statsDir, String trueIgn,
                                            String activeIgn, Integer days, long now) {
        return summarizeSessions(statsDir, new PlayerIdentity(trueIgn, activeIgn), days, now);
    }

    static SessionSummary summarizeSessions(File statsDir, String playerUuid,
                                            String trueIgn, String activeIgn,
                                            Integer days, long now) {
        return summarizeSessions(statsDir,
                new PlayerIdentity(playerUuid, trueIgn, activeIgn), days, now);
    }

    private static SessionSummary summarizeSessions(File statsDir, PlayerIdentity identity,
                                                     Integer days, long now) {
        List<JsonObject> sessions = loadSessions(statsDir, identity, days, now);
        SessionSummary out = new SessionSummary(identity == null ? null : identity.displayIgn());
        out.games = sessions.size();
        for (JsonObject session : sessions) {
            String sessionIgn = sessionPlayerIgn(session, identity);
            long gameStart = getLong(session, "gameStart", 0L);
            long gameEnd = getLong(session, "gameEnd", gameStart);
            out.firstGame = minPositive(out.firstGame, gameStart);
            out.lastGame = Math.max(out.lastGame, gameStart);
            out.language = mappedLanguage(getString(session, "language"));
            if (getBoolean(session, "completed", false)) out.completedGames++;
            if (getBoolean(session, "interrupted", false)) out.interruptedGames++;
            if (isWinner(session, sessionIgn)) out.wins++;

            JsonArray rounds = getArray(session, "rounds");
            Deque<Boolean> pendingLeaves = new ArrayDeque<Boolean>();
            for (JsonElement roundElement : rounds) {
                if (!roundElement.isJsonObject()) continue;
                JsonObject round = roundElement.getAsJsonObject();
                boolean correct = containsIgn(getArray(round, "correctGuesses"), sessionIgn);
                int correctCount = countIgn(getArray(round, "correctGuesses"), sessionIgn);
                out.correctGuesses += correctCount;
                out.totalGuesses += countIgn(getArray(round, "guesses"), sessionIgn) + correctCount;
                out.correctBeforeHint += countCorrectBeforeFirstHint(round, sessionIgn);
                if (isFullRound(round)) out.fullRounds++;

                int localDelta = scoreDeltaValue(round, sessionIgn);
                if (correct) {
                    out.correctGuessScore += localDelta;
                    if (localDelta == 3) out.threePointGuesses += correctCount;
                }

                List<JsonObject> events = objects(getArray(round, "events"));
                Collections.sort(events, EVENT_TIME_ORDER);
                for (JsonObject event : events) {
                    String type = getString(event, "type");
                    if ("disconnect".equals(type)) {
                        out.leaves++;
                        boolean buildRoundLeave = equalsIgnoreCase(
                                getString(round, "builder"), sessionIgn);
                        pendingLeaves.addLast(Boolean.valueOf(buildRoundLeave));
                        if (buildRoundLeave) {
                            out.buildRoundLeaves++;
                        }
                    } else if ("rejoin".equals(type)) {
                        if (!pendingLeaves.isEmpty()) {
                            out.rejoins++;
                            if (pendingLeaves.removeFirst().booleanValue()) {
                                out.buildRoundRejoins++;
                            }
                        }
                    }
                }
            }

            int score = gameScore(session, sessionIgn);
            out.totalScore += score;
            if (score == 30) out.perfectGames++;
            out.activeTimeMs += sessionActiveTime(session, gameStart, gameEnd);
        }
        return out;
    }

    static ThemeSummary summarizeThemes(File statsDir, String ign,
                                        long now) {
        return summarizeThemes(statsDir, (File) null, PlayerIdentity.single(ign), now);
    }

    static ThemeSummary summarizeThemes(File statsDir, String playerUuid,
                                        String trueIgn, String activeIgn, long now) {
        return summarizeThemes(statsDir, (File) null,
                new PlayerIdentity(playerUuid, trueIgn, activeIgn), now);
    }

    static ThemeSummary summarizeThemes(File statsDir, File minecraftLogsDir,
                                        String ign, long now) {
        return summarizeThemes(statsDir, minecraftLogsDir, PlayerIdentity.single(ign), now);
    }

    private static ThemeSummary summarizeThemes(File statsDir, File minecraftLogsDir,
                                                PlayerIdentity identity, long now) {
        return summarizeThemes(statsDir,
                minecraftLogsDir == null
                        ? Collections.<File>emptyList()
                        : Collections.singletonList(minecraftLogsDir),
                identity, now, null, null);
    }

    static ThemeSummary summarizeThemes(File statsDir, List<File> minecraftLogsDirs,
                                        String ign, long now) {
        return summarizeThemes(statsDir, minecraftLogsDirs,
                PlayerIdentity.single(ign), now, null, null);
    }

    static ThemeSummary summarizeThemes(File statsDir, List<File> minecraftLogsDirs,
                                        String ign, long now, File logCacheDir,
                                        MinecraftLogThemeExtractor.ProgressListener
                                                progressListener) {
        return summarizeThemes(statsDir, minecraftLogsDirs,
                PlayerIdentity.single(ign), now, logCacheDir, progressListener);
    }

    private static ThemeSummary summarizeThemes(File statsDir,
                                                List<File> minecraftLogsDirs,
                                                PlayerIdentity identity, long now,
                                                File logCacheDir,
                                                MinecraftLogThemeExtractor.ProgressListener
                                                        progressListener) {
        List<JsonObject> sessions = loadSessions(statsDir, identity, null, now,
                progressListener instanceof ThemeStatsProgressListener
                        ? (ThemeStatsProgressListener) progressListener
                        : null);
        ThemeSummary out = new ThemeSummary();
        out.games = sessions.size();
        out.totalEnglishThemes = TranslationData.getEnglishThemeCount();
        Set<String> seen = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        List<SessionWindow> canonicalWindows = new ArrayList<SessionWindow>();

        for (JsonObject session : sessions) {
            long gameStart = getLong(session, "gameStart", 0L);
            long gameEnd = getLong(session, "gameEnd", gameStart);
            if (gameStart > 0L) {
                canonicalWindows.add(new SessionWindow(gameStart,
                        gameEnd > 0L ? gameEnd : gameStart));
            }
            out.firstGame = minPositive(out.firstGame, gameStart);
            out.lastGame = Math.max(out.lastGame, gameStart);
            String sessionLanguage = getString(session, "language");
            String translationDataHash = getString(session, "translationDataHash");
            for (JsonElement roundElement : getArray(session, "rounds")) {
                if (!roundElement.isJsonObject()) continue;
                JsonObject round = roundElement.getAsJsonObject();
                long seenAt = getLong(round, "roundEnd", 0L);
                if (seenAt <= 0L) seenAt = getLong(round, "roundStart", 0L);
                if (seenAt <= 0L) seenAt = gameEnd;
                if (seenAt <= 0L) seenAt = gameStart;
                addBuilderOptionSightings(out, seen, round, sessionLanguage,
                        translationDataHash, seenAt);

                String rawTheme = getString(round, "theme");
                if (rawTheme == null || rawTheme.trim().isEmpty()) continue;
                List<String> englishThemes = resolveArchiveEnglishThemes(
                        round, rawTheme, sessionLanguage, translationDataHash, seenAt);
                addThemeRound(out, seen, englishThemes, seenAt);
            }
        }

        if (minecraftLogsDirs != null) {
            Set<String> rawGameKeys = new HashSet<String>();
            AggregateMinecraftLogProgress aggregateProgress = progressListener == null
                    ? null
                    : new AggregateMinecraftLogProgress(
                            minecraftLogsDirs, progressListener);
            for (int directoryIndex = 0;
                 directoryIndex < minecraftLogsDirs.size(); directoryIndex++) {
                File minecraftLogsDir = minecraftLogsDirs.get(directoryIndex);
                if (aggregateProgress != null) {
                    aggregateProgress.startDirectory(
                            directoryIndex == minecraftLogsDirs.size() - 1);
                }
                for (MinecraftLogThemeExtractor.ExtractedThemeGame game :
                        MinecraftLogThemeExtractor.extractForIdentities(
                                minecraftLogsDir, null,
                                logCacheDir, aggregateProgress)) {
                    if (overlapsCanonical(game.start, game.end, canonicalWindows)) continue;
                    if (!rawGameKeys.add(rawGameKey(game))) continue;
                    int roundsBefore = out.totalRounds;
                    for (MinecraftLogThemeExtractor.ExtractedThemeRound round : game.rounds) {
                        if (round.theme == null || round.theme.trim().isEmpty()) continue;
                        long seenAt = round.seenAt > 0L ? round.seenAt : game.end;
                        List<String> englishThemes = round.englishThemes.isEmpty()
                                ? TranslationData.resolveEnglishThemesAt(
                                        null, seenAt, round.langCode, round.theme, null)
                                : new ArrayList<String>(round.englishThemes);
                        addThemeRound(out, seen, englishThemes, seenAt);
                    }
                    if (out.totalRounds > roundsBefore) {
                        out.games++;
                        out.firstGame = minPositive(out.firstGame, game.start);
                        out.lastGame = Math.max(out.lastGame, game.start);
                    }
                }
            }
        }
        out.themesSeen = seen.size();
        out.sorted.addAll(out.byTheme.values());
        Collections.sort(out.sorted, THEME_ORDER);
        return out;
    }

    private static List<String> resolveArchiveEnglishThemes(
            JsonObject round, String rawTheme, String sessionLanguage,
            String translationDataHash, long seenAt) {
        List<String> stored = strings(getArray(round, "englishThemes"));
        if (!stored.isEmpty()) return stored;
        String languageCode = getString(round, "languageCode");
        if (languageCode == null) {
            String roundLanguage = getString(round, "language");
            languageCode = LangPatterns.codeFromDisplayName(
                    roundLanguage == null ? sessionLanguage : roundLanguage);
        }
        return TranslationData.resolveEnglishThemesAt(
                translationDataHash, seenAt, languageCode, rawTheme,
                scoreDelta(round, getString(round, "builder")));
    }

    private static void addBuilderOptionSightings(
            ThemeSummary summary, Set<String> seen, JsonObject round,
            String sessionLanguage, String translationDataHash, long seenAt) {
        List<String> options = strings(getArray(round, "builderThemeOptions"));
        if (options.isEmpty()) return;
        String selectedTheme = getString(round, "theme");
        String languageCode = getString(round, "languageCode");
        if (languageCode == null) {
            String roundLanguage = getString(round, "language");
            languageCode = LangPatterns.codeFromDisplayName(
                    roundLanguage == null ? sessionLanguage : roundLanguage);
        }
        for (String option : options) {
            if (option == null || option.trim().isEmpty()) continue;
            List<String> englishThemes = TranslationData.resolveEnglishThemesAt(
                    translationDataHash, seenAt, languageCode, option, null);
            boolean selected = selectedTheme != null
                    && option.trim().equalsIgnoreCase(selectedTheme.trim());
            addThemeSighting(summary, seen, englishThemes, seenAt,
                    true, !selected);
        }
    }

    private static void addThemeSighting(
            ThemeSummary summary, Set<String> seen,
            List<String> englishThemes, long seenAt, boolean builderOption,
            boolean countOccurrence) {
        if (englishThemes == null || englishThemes.isEmpty()) return;
        double weight = countOccurrence ? 1.0D / englishThemes.size() : 0.0D;
        for (String englishTheme : englishThemes) {
            ThemeOccurrence occurrence = summary.byTheme.get(englishTheme);
            if (occurrence == null) {
                occurrence = new ThemeOccurrence(englishTheme);
                summary.byTheme.put(englishTheme, occurrence);
            }
            if (builderOption) {
                occurrence.lastBuilderOptionSeen =
                        Math.max(occurrence.lastBuilderOptionSeen, seenAt);
            } else {
                occurrence.lastSeen = Math.max(occurrence.lastSeen, seenAt);
            }
            occurrence.weight += weight;
            seen.add(englishTheme);
        }
    }

    private static void addThemeRound(ThemeSummary summary, Set<String> seen,
                                      List<String> englishThemes, long seenAt) {
        summary.totalRounds++;
        if (englishThemes == null || englishThemes.isEmpty()) return;
        summary.themeDifficulty.addEnglishThemes(englishThemes);
        addThemeSighting(summary, seen, englishThemes, seenAt, false, true);
    }

    private static boolean overlapsCanonical(long start, long end,
                                             List<SessionWindow> canonicalWindows) {
        if (start <= 0L || canonicalWindows == null || canonicalWindows.isEmpty()) {
            return false;
        }
        long rawEnd = end >= start ? end : start;
        for (SessionWindow window : canonicalWindows) {
            if (window.overlaps(start, rawEnd)) return true;
        }
        return false;
    }

    private static String rawGameKey(
            MinecraftLogThemeExtractor.ExtractedThemeGame game) {
        StringBuilder key = new StringBuilder();
        key.append(game.start).append('|').append(game.end);
        for (MinecraftLogThemeExtractor.ExtractedThemeRound round : game.rounds) {
            key.append('\n').append(round.roundNumber).append('/')
                    .append(round.totalRounds).append('|')
                    .append(round.seenAt).append('|')
                    .append(round.langCode == null ? "" : round.langCode).append('|')
                    .append(round.theme == null ? "" : round.theme);
            for (String englishTheme : round.englishThemes) {
                key.append('|').append(englishTheme == null ? "" : englishTheme);
            }
        }
        return key.toString();
    }

    static List<File> discoverMinecraftLogDirs(File mcDataDir) {
        return MinecraftLogDirectoryFinder.discover(mcDataDir);
    }

    static List<File> discoverMinecraftLogDirs(File mcDataDir, File launcherRoot) {
        List<File> roots = new ArrayList<File>();
        if (mcDataDir != null) roots.add(mcDataDir);
        if (launcherRoot != null) roots.add(launcherRoot);
        return MinecraftLogDirectoryFinder.discoverFromRoots(roots);
    }

    private static PlayerIdentity currentIdentity(Minecraft mc) {
        if (mc == null) return null;
        String activeIgn = mc.thePlayer == null ? null : mc.thePlayer.getName();
        String trueIgn = null;
        String playerUuid = null;
        try {
            if (mc.getSession() != null) {
                trueIgn = mc.getSession().getUsername();
                playerUuid = mc.getSession().getPlayerID();
            }
        } catch (Throwable ignored) {}
        return new PlayerIdentity(playerUuid, trueIgn, activeIgn);
    }

    private static void addValidIgn(Set<String> identities, String ign) {
        if (identities != null && PlayerMetaCache.isValidMcUsername(ign)) {
            identities.add(ign);
        }
    }

    static final class PlayerIdentity {
        final String playerUuid;
        final String trueIgn;
        final String activeIgn;

        PlayerIdentity(String trueIgn, String activeIgn) {
            this(null, trueIgn, activeIgn);
        }

        PlayerIdentity(String playerUuid, String trueIgn, String activeIgn) {
            this.playerUuid = PlayerUuid.normalize(playerUuid);
            this.trueIgn = PlayerMetaCache.isValidMcUsername(trueIgn) ? trueIgn : null;
            this.activeIgn = PlayerMetaCache.isValidMcUsername(activeIgn) ? activeIgn : null;
        }

        static PlayerIdentity single(String ign) {
            return PlayerMetaCache.isValidMcUsername(ign)
                    ? new PlayerIdentity(ign, ign) : null;
        }

        String displayIgn() {
            return trueIgn != null ? trueIgn : activeIgn;
        }

        boolean hasIdentifier() {
            return playerUuid != null || !aliases().isEmpty();
        }

        boolean matchesPlayerUuid(String uuid) {
            String normalized = PlayerUuid.normalize(uuid);
            return playerUuid != null && playerUuid.equals(normalized);
        }

        Set<String> aliases() {
            Set<String> out = new LinkedHashSet<String>();
            addValidIgn(out, trueIgn);
            addValidIgn(out, activeIgn);
            return out;
        }

        boolean matchesTrueIgn(String ign) {
            return trueIgn != null && trueIgn.equalsIgnoreCase(ign);
        }

        boolean matchesAlias(String ign) {
            if (!PlayerMetaCache.isValidMcUsername(ign)) return false;
            return (trueIgn != null && trueIgn.equalsIgnoreCase(ign))
                    || (activeIgn != null && activeIgn.equalsIgnoreCase(ign));
        }
    }

    private static List<JsonObject> loadSessions(File statsDir, PlayerIdentity identity,
                                                 Integer days, long now) {
        return loadSessions(statsDir, identity, days, now, null);
    }

    private static List<JsonObject> loadSessions(File statsDir, PlayerIdentity identity,
                                                 Integer days, long now,
                                                 ThemeStatsProgressListener
                                                         progressListener) {
        List<JsonObject> out = new ArrayList<JsonObject>();
        if (statsDir == null) return out;
        List<File> files = listSessionFiles(statsDir, days, now);
        ThemeSessionProgressState progress =
                new ThemeSessionProgressState(files);
        notifySessionProgress(progressListener, progress.snapshot(false));
        boolean filterByIdentity = identity != null && identity.hasIdentifier();
        long cutoff = days == null ? Long.MIN_VALUE : now - days.longValue() * DAY_MS;
        JsonParser parser = new JsonParser();
        for (File file : files) {
            if (file == null) continue;
            String fileName = file.getName();
            try {
                JsonObject session = readSessionFile(file, parser);
                if (session == null) continue;
                if (LIVE_SESSION_FILE.equals(fileName) && !isLiveSession(session)) {
                    continue;
                }
                long gameStart = getLong(session, "gameStart", 0L);
                if (gameStart < cutoff
                        || (filterByIdentity && !belongsToPlayer(session, identity))) continue;
                out.add(session);
            } catch (Exception e) {
                GameExporter.LOGGER.warn("Skipping unreadable stats file {}", fileName, e);
            } finally {
                notifySessionProgress(progressListener,
                        progress.record(file, false));
            }
        }
        notifySessionProgress(progressListener, progress.snapshot(true));
        Collections.sort(out, new Comparator<JsonObject>() {
            @Override
            public int compare(JsonObject left, JsonObject right) {
                return Long.compare(getLong(left, "gameStart", 0L),
                        getLong(right, "gameStart", 0L));
            }
        });
        return out;
    }

    private static List<File> listSessionFiles(File statsDir, Integer days,
                                               long now) {
        List<File> out = new ArrayList<File>();
        if (statsDir == null) return out;
        File[] files = statsDir.listFiles();
        if (files == null) return out;
        long cutoff = days == null ? Long.MIN_VALUE : now - days.longValue() * DAY_MS;
        for (File file : files) {
            if (file == null) continue;
            String fileName = file.getName();
            if (!isStatsSessionFile(fileName)) continue;
            if (days != null && !LIVE_SESSION_FILE.equals(fileName)) {
                long archiveTime = sessionArchiveTime(fileName);
                if (archiveTime > 0L && archiveTime < cutoff) continue;
            }
            out.add(file);
        }
        Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        return out;
    }

    private static void notifySessionProgress(
            ThemeStatsProgressListener progressListener,
            ThemeSessionProgress progress) {
        if (progressListener == null || progress == null) return;
        try {
            progressListener.onSessionProgress(progress);
        } catch (Exception e) {
            GameExporter.LOGGER.warn("Stats progress listener failed", e);
        }
    }

    private static JsonObject readSessionFile(File file, JsonParser parser) throws Exception {
        if (LIVE_SESSION_FILE.equals(file.getName())) {
            return parseSessionFile(file, parser);
        }

        String key = sessionCacheKey(file);
        long modified = file.lastModified();
        long length = file.length();
        synchronized (SESSION_CACHE) {
            CachedSessionFile cached = SESSION_CACHE.get(key);
            if (cached != null
                    && cached.modified == modified
                    && cached.length == length) {
                return cached.session;
            }
        }

        JsonObject session = parseSessionFile(file, parser);
        if (session != null) {
            synchronized (SESSION_CACHE) {
                SESSION_CACHE.put(key, new CachedSessionFile(modified, length, session));
            }
        }
        return session;
    }

    private static JsonObject parseSessionFile(File file, JsonParser parser) throws Exception {
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            JsonElement parsed = parser.parse(reader);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static String sessionCacheKey(File file) {
        try {
            return file.getCanonicalPath();
        } catch (Exception e) {
            return file.getAbsolutePath();
        }
    }

    private static long sessionArchiveTime(String name) {
        if (name == null || LIVE_SESSION_FILE.equals(name)) return 0L;
        String base = name;
        if (base.startsWith(INTERRUPTED_PREFIX)) {
            base = base.substring(INTERRUPTED_PREFIX.length());
        }
        int suffix = base.indexOf(".json");
        if (suffix < 0) return 0L;
        base = base.substring(0, suffix);
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            format.setLenient(false);
            Date parsed = format.parse(base);
            return parsed == null ? 0L : parsed.getTime();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static boolean isStatsSessionFile(String name) {
        return LIVE_SESSION_FILE.equals(name) || SESSION_FILE.matcher(name).matches();
    }

    private static final class CachedSessionFile {
        final long modified;
        final long length;
        final JsonObject session;

        CachedSessionFile(long modified, long length, JsonObject session) {
            this.modified = modified;
            this.length = length;
            this.session = session;
        }
    }

    private static boolean isLiveSession(JsonObject session) {
        return session != null
                && !getBoolean(session, "completed", false)
                && !getBoolean(session, "interrupted", false)
                && getArray(session, "rounds").size() > 0;
    }

    private static boolean belongsToPlayer(JsonObject session, PlayerIdentity identity) {
        if (identity == null) return true;
        if (session.has("playerUuid")) {
            return identity.matchesPlayerUuid(getString(session, "playerUuid"));
        }
        String recordedTrueIgn = getString(session, "trueIgn");
        if (PlayerMetaCache.isValidMcUsername(recordedTrueIgn)) {
            return identity.matchesTrueIgn(recordedTrueIgn);
        }
        String recordedLocal = getString(session, "localPlayer");
        if (PlayerMetaCache.isValidMcUsername(recordedLocal)) {
            return identity.matchesAlias(recordedLocal);
        }
        if (getInt(session, "schemaVersion", 0) >= 4) return false;
        for (String player : strings(getArray(session, "players"))) {
            if (identity.matchesAlias(player)) return true;
        }
        return false;
    }

    private static String sessionPlayerIgn(JsonObject session, PlayerIdentity identity) {
        String recordedLocal = getString(session, "localPlayer");
        if (PlayerMetaCache.isValidMcUsername(recordedLocal)) return recordedLocal;
        String recordedNick = getString(session, "nick");
        if (getBoolean(session, "isNicked", false)
                && PlayerMetaCache.isValidMcUsername(recordedNick)) {
            return recordedNick;
        }
        if (identity != null) {
            for (String player : strings(getArray(session, "players"))) {
                if (identity.matchesAlias(player)) return player;
            }
            return identity.displayIgn();
        }
        return null;
    }

    private static int gameScore(JsonObject session, String ign) {
        JsonObject result = getObject(session, "result");
        if (result != null) {
            for (JsonElement element : getArray(result, "rankings")) {
                if (!element.isJsonObject()) continue;
                JsonObject rank = element.getAsJsonObject();
                if (equalsIgnoreCase(getString(rank, "ign"), ign)) {
                    return getInt(rank, "score", 0);
                }
            }
        }
        int total = 0;
        for (JsonElement element : getArray(session, "rounds")) {
            if (element.isJsonObject()) total += scoreDeltaValue(element.getAsJsonObject(), ign);
        }
        JsonObject residual = getObject(session, "unknownResidual");
        if (residual != null) {
            JsonElement value = findMemberIgnoreCase(residual, ign);
            if (value != null && value.isJsonPrimitive()) total += value.getAsInt();
        }
        return total;
    }

    private static int scoreDeltaValue(JsonObject round, String ign) {
        JsonObject deltas = getObject(round, "scoreDeltas");
        if (deltas == null) return 0;
        JsonElement element = findMemberIgnoreCase(deltas, ign);
        if (element == null || !element.isJsonObject()) return 0;
        JsonObject delta = element.getAsJsonObject();
        if (delta.has("value") && !delta.get("value").isJsonNull()) {
            return delta.get("value").getAsInt();
        }
        JsonArray fork = getArray(delta, "fork");
        int minimum = Integer.MAX_VALUE;
        for (JsonElement option : fork) minimum = Math.min(minimum, option.getAsInt());
        return minimum == Integer.MAX_VALUE ? 0 : minimum;
    }

    private static ScoreDelta scoreDelta(JsonObject round, String ign) {
        JsonObject deltas = getObject(round, "scoreDeltas");
        if (deltas == null || ign == null) return null;
        JsonElement element = findMemberIgnoreCase(deltas, ign);
        if (element == null || !element.isJsonObject()) return null;
        JsonObject delta = element.getAsJsonObject();
        String source = getString(delta, "source");
        if (delta.has("value") && !delta.get("value").isJsonNull()) {
            return ScoreDelta.definite(delta.get("value").getAsInt(), source);
        }
        Set<Integer> fork = new TreeSet<Integer>();
        for (JsonElement option : getArray(delta, "fork")) fork.add(option.getAsInt());
        return fork.isEmpty() ? null : ScoreDelta.fork(fork, source);
    }

    private static List<String> guessTexts(JsonObject round) {
        List<String> out = new ArrayList<String>();
        if (round == null) return out;
        for (JsonElement guessElement : getArray(round, "guesses")) {
            if (guessElement == null || !guessElement.isJsonObject()) continue;
            String text = getString(guessElement.getAsJsonObject(), "text");
            if (text != null && !text.trim().isEmpty()) out.add(text);
        }
        return out;
    }

    private static long sessionActiveTime(JsonObject session, long start, long end) {
        if (session.has("activeTimeMs") && !session.get("activeTimeMs").isJsonNull()) {
            return Math.max(0L, session.get("activeTimeMs").getAsLong());
        }
        long duration = Math.max(0L, end - start);
        if (getBoolean(session, "completed", false)) {
            duration = Math.max(0L, duration - 1500L);
        }
        List<JsonObject> events = new ArrayList<JsonObject>();
        for (JsonElement roundElement : getArray(session, "rounds")) {
            if (!roundElement.isJsonObject()) continue;
            events.addAll(objects(getArray(roundElement.getAsJsonObject(), "events")));
        }
        Collections.sort(events, EVENT_TIME_ORDER);
        long pauseStart = 0L;
        for (JsonObject event : events) {
            long t = getLong(event, "t", 0L);
            String type = getString(event, "type");
            if ("disconnect".equals(type) && pauseStart == 0L) {
                pauseStart = t;
            } else if ("rejoin".equals(type) && pauseStart > 0L) {
                duration -= Math.max(0L, Math.min(end, t) - Math.max(start, pauseStart));
                pauseStart = 0L;
            }
        }
        if (pauseStart > 0L) {
            duration -= Math.max(0L, end - Math.max(start, pauseStart));
        }
        return Math.max(0L, duration);
    }

    private static int countCorrectBeforeFirstHint(JsonObject round, String ign) {
        long firstHint = Long.MAX_VALUE;
        JsonObject hints = getObject(round, "hints");
        if (hints != null) {
            for (JsonElement element : getArray(hints, "items")) {
                if (!element.isJsonObject()) continue;
                firstHint = Math.min(firstHint,
                        getLong(element.getAsJsonObject(), "t", Long.MAX_VALUE));
            }
        }
        if (firstHint == Long.MAX_VALUE && getBoolean(round, "dataGap", false)) return 0;
        int count = 0;
        for (JsonElement element : getArray(round, "correctGuesses")) {
            if (!element.isJsonObject()) continue;
            JsonObject guess = element.getAsJsonObject();
            if (equalsIgnoreCase(getString(guess, "ign"), ign)
                    && getLong(guess, "t", 0L) < firstHint) {
                count++;
            }
        }
        return count;
    }

    private static boolean isFullRound(JsonObject round) {
        if (round.has("localParticipationComplete")) {
            return getBoolean(round, "localParticipationComplete", false);
        }
        if (getInt(round, "round", -1) <= 0
                || getLong(round, "roundStart", 0L) <= 0L
                || getLong(round, "roundEnd", 0L) < getLong(round, "roundStart", 0L)
                || getBoolean(round, "dataGap", false)) {
            return false;
        }
        for (JsonObject event : objects(getArray(round, "events"))) {
            String type = getString(event, "type");
            if ("disconnect".equals(type) || "rejoin".equals(type)
                    || "rejoin_round_change".equals(type)
                    || "rejoin_same_round".equals(type)
                    || "builder_left".equals(type) || "afk_skip".equals(type)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWinner(JsonObject session, String ign) {
        JsonObject result = getObject(session, "result");
        if (result == null) return false;
        for (JsonElement element : getArray(result, "rankings")) {
            if (!element.isJsonObject()) continue;
            JsonObject rank = element.getAsJsonObject();
            if (getInt(rank, "place", -1) == 1
                    && equalsIgnoreCase(getString(rank, "ign"), ign)) {
                return true;
            }
        }
        return false;
    }

    private static List<IChatComponent> renderSession(SessionSummary summary) {
        List<IChatComponent> lines = new ArrayList<IChatComponent>();
        if (summary.games == 0) {
            lines.add(new ChatComponentText(CommandGtb.head
                    + "No recorded sessions found for \u00a7f" + summary.ign + "\u00a77."));
            return lines;
        }
        lines.add(text(CommandGtb.head + "\u00a7f" + summary.ign
                + "\u00a77's stats across \u00a7f" + summary.games
                + "\u00a77 games (\u00a7f" + date(summary.firstGame)
                + "\u00a77 - \u00a7f" + date(summary.lastGame) + "\u00a77):"));
        add(lines, "Completed games", summary.completedGames);
        add(lines, "Interrupted games", summary.interruptedGames);
        add(lines, "Language", summary.language == null ? "Unknown" : summary.language);
        add(lines, "Wins", summary.wins);
        add(lines, "Win rate", percent(summary.wins, summary.games));
        blank(lines);
        add(lines, "Total guesses", summary.totalGuesses);
        add(lines, "Correct guesses", summary.correctGuesses);
        add(lines, "Correct guesses before hint", summary.correctBeforeHint);
        add(lines, "T/C", ratio(summary.totalGuesses, summary.correctGuesses));
        add(lines, "C/CBH", ratio(summary.correctGuesses, summary.correctBeforeHint));
        add(lines, "C/W", ratio(summary.correctGuesses, summary.wins));
        blank(lines);
        add(lines, "Total time played", totalTime(summary.activeTimeMs));
        add(lines, "Average time per game", averageTime(summary.activeTimeMs, summary.games));
        add(lines, "Average time per round",
                summary.fullRounds == 0 ? "N/A"
                        : ONE_DECIMAL.format(summary.activeTimeMs / 1000.0D / summary.fullRounds) + "s");
        add(lines, "Total rounds played", summary.fullRounds);
        add(lines, "Average rounds per game", twoDecimal(summary.fullRounds, summary.games));
        blank(lines);
        add(lines, "Total points", summary.totalScore);
        add(lines, "Average points per game", twoDecimal(summary.totalScore, summary.games));
        add(lines, "Average points per guess",
                twoDecimal(summary.correctGuessScore, summary.correctGuesses));
        add(lines, "% 3pt guess", percent(summary.threePointGuesses,
                summary.correctGuesses));
        add(lines, "Perfect games", summary.perfectGames);
        add(lines, "W/P", summary.perfectGames == 0
                ? "N/A" : ratio(summary.wins, summary.perfectGames));
        blank(lines);
        add(lines, "Total leaves", summary.leaves);
        add(lines, "Total rejoins", summary.rejoins);
        add(lines, "Total leaves on build round", summary.buildRoundLeaves);
        add(lines, "Total rejoins on build round", summary.buildRoundRejoins);
        return lines;
    }

    private static final DecimalFormat ONE_DECIMAL =
            new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));

    private static List<IChatComponent> renderTheme(ThemeSummary summary,
                                                    int entryLimit, long now,
                                                    boolean allLogs) {
        List<IChatComponent> lines = new ArrayList<IChatComponent>();
        if (summary.games == 0) {
            lines.add(new ChatComponentText(CommandGtb.head
                    + (allLogs
                    ? "No recorded theme occurrences found in local logs."
                    : "No recorded sessions found for the current player.")));
            return lines;
        }
        lines.add(text(CommandGtb.head + "Theme occurrence stats across \u00a7f"
                + summary.games + "\u00a77 games (\u00a7f" + date(summary.firstGame)
                + "\u00a77 - \u00a7f" + date(summary.lastGame) + "\u00a77):"));
        add(lines, "Total rounds", summary.totalRounds);
        add(lines, "Total themes seen", themeCoverage(summary));
        add(lines, "Theme difficulty distribution", themeDifficulty(summary.themeDifficulty));
        blank(lines);

        ChatComponentText prefix = new ChatComponentText(
                "\u00a77Summary of theme occurrence (click ");
        ChatComponentText here = new ChatComponentText("here");
        here.setChatStyle(new ChatStyle()
                .setColor(EnumChatFormatting.WHITE)
                .setUnderlined(true)
                .setChatHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentText("\u00a7fLeft Click\u00a77 to export")))
                .setChatClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        allLogs
                                ? "/gtbs stats theme all export"
                                : "/gtbs stats theme export")));
        prefix.appendSibling(here);
        prefix.appendSibling(new ChatComponentText("\u00a77 to export the full list):"));
        lines.add(prefix);

        int limit = Math.min(Math.max(1, entryLimit), summary.sorted.size());
        int rank = 0;
        double previous = Double.NaN;
        for (int i = 0; i < limit; i++) {
            ThemeOccurrence occurrence = summary.sorted.get(i);
            if (i == 0 || !sameWeight(previous, occurrence.weight)) rank = i + 1;
            previous = occurrence.weight;
            lines.add(text("\u00a77" + rank + ". \u00a7f" + occurrence.theme
                    + "\u00a77 - \u00a7f" + UP_TO_TWO.format(occurrence.weight)
                    + "\u00a77 (" + occurrenceSeenText(occurrence, now) + "\u00a77)"));
        }
        return lines;
    }

    private static File exportThemeList(File statsDir, ThemeSummary summary) throws Exception {
        if (statsDir == null) throw new IllegalStateException("No stats directory");
        if (!statsDir.exists() && !statsDir.mkdirs()) {
            throw new IllegalStateException("Could not create stats directory");
        }
        File file = new File(statsDir, "theme_occurrence_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".txt");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), "UTF-8"));
        try {
            writer.write("Theme occurrence stats across " + summary.games + " games ("
                    + date(summary.firstGame) + " - " + date(summary.lastGame) + ")");
            writer.newLine();
            writer.write("Total rounds: " + summary.totalRounds);
            writer.newLine();
            writer.write("Total themes seen: " + stripColors(themeCoverage(summary)));
            writer.newLine();
            writer.write("Theme difficulty distribution: "
                    + stripColors(themeDifficulty(summary.themeDifficulty)));
            writer.newLine();
            writer.newLine();
            int rank = 0;
            double previous = Double.NaN;
            long now = System.currentTimeMillis();
            int index = 0;
            for (ThemeOccurrence occurrence : summary.sorted) {
                if (index == 0 || !sameWeight(previous, occurrence.weight)) rank = index + 1;
                previous = occurrence.weight;
                writer.write(rank + ". " + occurrence.theme + " - "
                        + UP_TO_TWO.format(occurrence.weight) + " ("
                        + stripColors(occurrenceSeenText(occurrence, now)) + ")");
                writer.newLine();
                index++;
            }
            for (String englishTheme : TranslationData.getEnglishThemes()) {
                if (summary.byTheme.containsKey(englishTheme)) continue;
                if (index == 0 || !sameWeight(previous, 0.0D)) rank = index + 1;
                previous = 0.0D;
                writer.write(rank + ". " + englishTheme + " - 0 (Never seen)");
                writer.newLine();
                index++;
            }
        } finally {
            writer.close();
        }
        return file;
    }

    private static String lastSeenText(long timestamp, long now) {
        int days = calendarDaysBetween(timestamp, now);
        if (days <= 0) return "Last seen \u00a7ftoday";
        return "Last seen \u00a7f" + days + "\u00a77 day" + (days == 1 ? "" : "s") + " ago";
    }

    private static String occurrenceSeenText(ThemeOccurrence occurrence, long now) {
        return lastSeenText(Math.max(occurrence.lastSeen,
                occurrence.lastBuilderOptionSeen), now);
    }

    private static int calendarDaysBetween(long earlier, long later) {
        Calendar left = Calendar.getInstance();
        left.setTimeInMillis(earlier);
        zeroTime(left);
        Calendar right = Calendar.getInstance();
        right.setTimeInMillis(later);
        zeroTime(right);
        return (int) Math.max(0L, (right.getTimeInMillis() - left.getTimeInMillis()) / DAY_MS);
    }

    private static void zeroTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private static String totalTime(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds / 3600L) % 24L;
        long minutes = (totalSeconds / 60L) % 60L;
        long seconds = totalSeconds % 60L;
        if (days > 0L) {
            return days + ":" + two(hours) + ":" + two(minutes) + ":" + two(seconds);
        }
        long totalHours = totalSeconds / 3600L;
        if (totalHours > 0L) return totalHours + ":" + two(minutes) + ":" + two(seconds);
        return (totalSeconds / 60L) + ":" + two(seconds);
    }

    private static String averageTime(long millis, int divisor) {
        if (divisor <= 0) return "N/A";
        long seconds = Math.round(millis / 1000.0D / divisor);
        return (seconds / 60L) + ":" + two(seconds % 60L);
    }

    private static String two(long value) {
        return value < 10L ? "0" + value : String.valueOf(value);
    }

    private static String ratio(double numerator, double denominator) {
        return denominator == 0.0D ? "N/A" : TWO_DECIMAL.format(numerator / denominator);
    }

    private static String percent(double numerator, double denominator) {
        return denominator == 0.0D ? "N/A"
                : TWO_DECIMAL.format(numerator * 100.0D / denominator) + "%";
    }

    private static String themeCoverage(ThemeSummary summary) {
        return summary.themesSeen + "\u00a77/\u00a7f" + summary.totalEnglishThemes
                + "\u00a77 (\u00a7f"
                + percent(summary.themesSeen, summary.totalEnglishThemes) + "\u00a77)";
    }

    private static String themeDifficulty(GameExporter.ThemeDifficultyDistribution distribution) {
        double total = distribution.totalWeight();
        return percent(distribution.easyWeight, total)
                + "\u00a7a Easy\u00a77, \u00a7f"
                + percent(distribution.mediumWeight, total)
                + "\u00a7e Medium\u00a77, \u00a7f"
                + percent(distribution.hardWeight, total)
                + "\u00a7c Hard";
    }

    static String twoDecimal(double numerator, double denominator) {
        return denominator == 0.0D ? "N/A" : TWO_DECIMAL.format(numerator / denominator);
    }

    private static String date(long timestamp) {
        return new SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(new Date(timestamp));
    }

    private static String mappedLanguage(String language) {
        String mapped = LangPatterns.mapLanguageName(language);
        return mapped == null ? language : mapped;
    }

    private static Integer parsePositiveWhole(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? Integer.valueOf(parsed) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static String themeEntriesCapNotice(int requestedEntries) {
        return "Requested theme entry count \u00a7f" + requestedEntries
                + "\u00a77 exceeds the cap of \u00a7f"
                + MAX_THEME_ENTRIES + "\u00a77; showing \u00a7f"
                + MAX_THEME_ENTRIES + "\u00a77.";
    }

    private static void add(List<IChatComponent> lines, String label, Object value) {
        lines.add(text("\u00a77" + label + ": \u00a7f" + value));
    }

    private static void blank(List<IChatComponent> lines) {
        lines.add(text(""));
    }

    private static IChatComponent text(String value) {
        return new ChatComponentText(value);
    }

    private static String stripColors(String value) {
        return value == null ? "" : value.replaceAll("\u00a7.", "");
    }

    private static long minPositive(long current, long value) {
        if (value <= 0L) return current;
        return current <= 0L ? value : Math.min(current, value);
    }

    private static int countIgn(JsonArray array, String ign) {
        int count = 0;
        for (JsonElement element : array) {
            if (element.isJsonObject()
                    && equalsIgnoreCase(getString(element.getAsJsonObject(), "ign"), ign)) {
                count++;
            }
        }
        return count;
    }

    private static boolean containsIgn(JsonArray array, String ign) {
        return countIgn(array, ign) > 0;
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static JsonElement findMemberIgnoreCase(JsonObject object, String name) {
        if (object == null || name == null) return null;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return null;
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return null;
        try { return object.get(key).getAsString(); }
        catch (Exception ignored) { return null; }
    }

    private static long getLong(JsonObject object, String key, long fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try { return object.get(key).getAsLong(); }
        catch (Exception ignored) { return fallback; }
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try { return object.get(key).getAsInt(); }
        catch (Exception ignored) { return fallback; }
    }

    private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try { return object.get(key).getAsBoolean(); }
        catch (Exception ignored) { return fallback; }
    }

    private static JsonArray getArray(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return new JsonArray();
        }
        return object.getAsJsonArray(key);
    }

    private static JsonObject getObject(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonObject()) return null;
        return object.getAsJsonObject(key);
    }

    private static List<String> strings(JsonArray array) {
        List<String> out = new ArrayList<String>();
        for (JsonElement element : array) {
            if (!element.isJsonNull() && element.isJsonPrimitive()) {
                out.add(element.getAsString());
            }
        }
        return out;
    }

    private static List<JsonObject> objects(JsonArray array) {
        List<JsonObject> out = new ArrayList<JsonObject>();
        for (JsonElement element : array) {
            if (element.isJsonObject()) out.add(element.getAsJsonObject());
        }
        return out;
    }

    private static boolean sameWeight(double left, double right) {
        return Math.abs(left - right) < 0.0000001D;
    }

    private static final Comparator<JsonObject> EVENT_TIME_ORDER =
            new Comparator<JsonObject>() {
                @Override
                public int compare(JsonObject left, JsonObject right) {
                    return Long.compare(getLong(left, "t", 0L), getLong(right, "t", 0L));
                }
            };

    private static final Comparator<ThemeOccurrence> THEME_ORDER =
            new Comparator<ThemeOccurrence>() {
                @Override
                public int compare(ThemeOccurrence left, ThemeOccurrence right) {
                    int weight = Double.compare(right.weight, left.weight);
                    if (weight != 0) return weight;
                    return String.CASE_INSENSITIVE_ORDER.compare(left.theme, right.theme);
                }
            };

    static final class SessionSummary {
        final String ign;
        int games;
        int completedGames;
        int interruptedGames;
        int wins;
        int totalGuesses;
        int correctGuesses;
        int correctBeforeHint;
        long activeTimeMs;
        int fullRounds;
        int totalScore;
        int correctGuessScore;
        int threePointGuesses;
        int perfectGames;
        int leaves;
        int rejoins;
        int buildRoundLeaves;
        int buildRoundRejoins;
        long firstGame;
        long lastGame;
        String language;

        SessionSummary(String ign) {
            this.ign = ign;
        }
    }

    static final class ThemeSummary {
        int games;
        int totalRounds;
        int themesSeen;
        int totalEnglishThemes;
        long firstGame;
        long lastGame;
        final GameExporter.ThemeDifficultyDistribution themeDifficulty =
                new GameExporter.ThemeDifficultyDistribution();
        final Map<String, ThemeOccurrence> byTheme =
                new LinkedHashMap<String, ThemeOccurrence>();
        final List<ThemeOccurrence> sorted = new ArrayList<ThemeOccurrence>();
    }

    static final class ThemeOccurrence {
        final String theme;
        double weight;
        long lastSeen;
        long lastBuilderOptionSeen;

        ThemeOccurrence(String theme) {
            this.theme = theme;
        }
    }

    private static final class CachedThemeSummary {
        final long createdAt;
        final ThemeSummary summary;

        CachedThemeSummary(long createdAt, ThemeSummary summary) {
            this.createdAt = createdAt;
            this.summary = summary;
        }
    }

    private static final class SessionWindow {
        final long start;
        final long end;

        SessionWindow(long start, long end) {
            this.start = start;
            this.end = end >= start ? end : start;
        }

        boolean overlaps(long otherStart, long otherEnd) {
            long paddedStart = start - RAW_LOG_OVERLAP_TOLERANCE_MS;
            long paddedEnd = end + RAW_LOG_OVERLAP_TOLERANCE_MS;
            return otherEnd >= paddedStart && otherStart <= paddedEnd;
        }
    }
}
