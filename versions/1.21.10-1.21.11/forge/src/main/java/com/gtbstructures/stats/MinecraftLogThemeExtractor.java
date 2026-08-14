package com.gtbstructures.stats;

import com.gtbstructures.translation.TranslationData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

/** Extracts GTB theme occurrence data from vanilla Minecraft chat logs. */
final class MinecraftLogThemeExtractor {
    private static final Logger LOGGER = Logger.getLogger("GtbStructures");
    private static final Charset LOG_CHARSET = Charset.forName("GB18030");
    private static final Pattern LOG_FILE =
            Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})-.*\\.log(?:\\.gz)?$");
    private static final Pattern CHAT_LINE =
            Pattern.compile("^\\[(\\d{2}):(\\d{2}):(\\d{2})\\] \\[[^\\]]+\\]: "
                    + "(?:\\[[^\\]]+\\] )?\\[CHAT\\] (.*)$");
    private static final Pattern COLOR_CODE = Pattern.compile("\u00a7.");
    private static final Pattern ROUND_NUM = Pattern.compile("(\\d+)/(\\d+)");
    private static final Pattern PLAYER_COUNT =
            Pattern.compile("^.*\\((\\d{1,2})/(?:10|12|16)\\)!$");
    private static final Pattern GTBS_COPY =
            Pattern.compile("^.{1,32} Message copied to clipboard: (.+)$");
    private static final Pattern GTBS_MATCHING = Pattern.compile(
            "^.{1,32} All themes matching the initial hint \\((.*?)\\): (.+)$");
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final long COPY_ATTACH_MS = 30000L;
    private static final int FINAL_HEADER_LOOKAHEAD_LINES = 10;
    private static final int LOG_BUFFER_SIZE = 64 * 1024;
    private static final int CACHE_VERSION = 2;
    private static final String CACHE_MODE = "theme-raw";
    private static final Pattern IGN_TOKEN =
            Pattern.compile("(?i)\\b[A-Za-z0-9_]{3,16}\\b");

    private final File cacheDir;
    private final ProgressListener progressListener;
    private final List<ExtractedThemeGame> games = new ArrayList<ExtractedThemeGame>();
    private ExtractedThemeGame currentGame;
    private ExtractedThemeRound currentRound;
    private String currentLangCode;
    private boolean japaneseRoundLabelPending;
    private long pendingBuildBattleAt;
    private int pendingBuildBattleLines;

    private MinecraftLogThemeExtractor(File cacheDir,
                                       ProgressListener progressListener) {
        this.cacheDir = cacheDir;
        this.progressListener = progressListener;
    }

    static List<ExtractedThemeGame> extract(File logsDir, String localIgn) {
        return extract(logsDir, localIgn, null, null);
    }

    static List<ExtractedThemeGame> extract(File logsDir, String localIgn,
                                            File cacheDir,
                                            ProgressListener progressListener) {
        Set<String> identities = new HashSet<String>();
        if (localIgn != null && !localIgn.trim().isEmpty()) identities.add(localIgn);
        return extractForIdentities(logsDir, identities, cacheDir, progressListener);
    }

    static List<ExtractedThemeGame> extractForIdentities(
            File logsDir, Set<String> localIdentities, File cacheDir,
            ProgressListener progressListener) {
        if (logsDir == null || !logsDir.isDirectory()) {
            return Collections.emptyList();
        }
        MinecraftLogThemeExtractor extractor =
                new MinecraftLogThemeExtractor(cacheDir, progressListener);
        extractor.readLogs(logsDir);
        return filterByLocalIdentities(extractor.games, localIdentities);
    }

    private void readLogs(File logsDir) {
        File[] listed = logsDir.listFiles();
        if (listed == null || listed.length == 0) return;
        List<File> files = new ArrayList<File>();
        for (File file : listed) {
            if (file != null && file.isFile() && LOG_FILE.matcher(file.getName()).matches()) {
                files.add(file);
            }
        }
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        ProgressState progress = new ProgressState(files);
        notifyProgress(progress.snapshot(false));
        if (files.size() > 1) {
            readLogsParallel(files, progress);
            notifyProgress(progress.snapshot(true));
            return;
        }
        for (File file : files) {
            CachedLogResult result = readCachedOrParse(file);
            games.addAll(result.games);
            notifyProgress(progress.record(result, false));
        }
        notifyProgress(progress.snapshot(true));
    }

    private void readLogsParallel(List<File> files, ProgressState progress) {
        int threads = Math.min(files.size(),
                Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 1)));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CompletionService<CachedLogResult> completion =
                new ExecutorCompletionService<CachedLogResult>(pool);
        int submitted = 0;
        try {
            for (final File file : files) {
                completion.submit(new Callable<CachedLogResult>() {
                    @Override
                    public CachedLogResult call() {
                        return readCachedOrParse(file);
                    }
                });
                submitted++;
            }
            for (int i = 0; i < submitted; i++) {
                try {
                    Future<CachedLogResult> future = completion.take();
                    CachedLogResult result = future.get();
                    games.addAll(result.games);
                    notifyProgress(progress.record(result, false));
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Skipping unreadable Minecraft log task", e);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        Collections.sort(games, new Comparator<ExtractedThemeGame>() {
            @Override
            public int compare(ExtractedThemeGame left, ExtractedThemeGame right) {
                return Long.compare(left.start, right.start);
            }
        });
    }

    private CachedLogResult readCachedOrParse(File file) {
        CachedLogResult cached = readCache(file);
        if (cached != null) return cached;

        MinecraftLogThemeExtractor parser =
                new MinecraftLogThemeExtractor(null, null);
        parser.readLog(file);
        CachedLogResult parsed = new CachedLogResult(file, parser.games, false);
        writeCache(file, parsed.games);
        return parsed;
    }

    private CachedLogResult readCache(File logFile) {
        File cacheFile = cacheFile(logFile);
        if (cacheFile == null || !cacheFile.isFile()) return null;
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(cacheFile), "UTF-8");
            JsonElement parsed = new JsonParser().parse(reader);
            if (!parsed.isJsonObject()) return null;
            JsonObject root = parsed.getAsJsonObject();
            if (getInt(root, "version", -1) != CACHE_VERSION) return null;
            if (getLong(root, "length", -1L) != logFile.length()) return null;
            if (getLong(root, "lastModified", -1L) != logFile.lastModified()) return null;
            String path = canonicalPath(logFile);
            if (!path.equals(getString(root, "path", ""))) return null;
            if (!cacheMode().equals(getString(root, "mode", ""))) return null;

            List<ExtractedThemeGame> cachedGames =
                    gamesFromJson(root.getAsJsonArray("games"));
            return new CachedLogResult(logFile, cachedGames, true);
        } catch (Exception e) {
            return null;
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void writeCache(File logFile, List<ExtractedThemeGame> parsedGames) {
        File cacheFile = cacheFile(logFile);
        if (cacheFile == null) return;
        File parent = cacheFile.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) return;

        JsonObject root = new JsonObject();
        root.addProperty("version", CACHE_VERSION);
        root.addProperty("path", canonicalPath(logFile));
        root.addProperty("mode", cacheMode());
        root.addProperty("length", logFile.length());
        root.addProperty("lastModified", logFile.lastModified());
        root.add("games", gamesToJson(parsedGames));

        File temp = new File(parent, cacheFile.getName() + ".tmp");
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(temp), "UTF-8"));
            writer.write(root.toString());
            writer.close();
            writer = null;
            if (cacheFile.exists() && !cacheFile.delete()) return;
            if (!temp.renameTo(cacheFile)) {
                BufferedWriter fallback = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(cacheFile), "UTF-8"));
                fallback.write(root.toString());
                fallback.close();
                temp.delete();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to write Minecraft log theme cache " + cacheFile.getName(), e);
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (Exception ignored) {}
            }
            if (temp.exists()) temp.delete();
        }
    }

    private File cacheFile(File logFile) {
        if (cacheDir == null || logFile == null) return null;
        return new File(cacheDir, sha1(canonicalPath(logFile)
                + "\n" + cacheMode() + "\n" + CACHE_VERSION) + ".json");
    }

    private String cacheMode() {
        return CACHE_MODE;
    }

    private static String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (Exception e) {
            return file.getAbsolutePath();
        }
    }

    private static String sha1(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(text.getBytes("UTF-8"));
            char[] out = new char[bytes.length * 2];
            char[] hex = "0123456789abcdef".toCharArray();
            for (int i = 0; i < bytes.length; i++) {
                int value = bytes[i] & 0xff;
                out[i * 2] = hex[value >>> 4];
                out[i * 2 + 1] = hex[value & 0x0f];
            }
            return new String(out);
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private void readLog(File file) {
        Matcher fileMatcher = LOG_FILE.matcher(file.getName());
        if (!fileMatcher.matches()) return;
        long base = localMidnight(
                Integer.parseInt(fileMatcher.group(1)),
                Integer.parseInt(fileMatcher.group(2)),
                Integer.parseInt(fileMatcher.group(3)));
        BufferedReader reader = null;
        long lastTimestamp = 0L;
        int lastSecondOfDay = -1;
        int dayOffset = 0;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    open(file), LOG_CHARSET), LOG_BUFFER_SIZE);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.indexOf("[CHAT]") < 0) continue;
                boolean relevant = mightAffectUnfilteredThemeStats(line);
                if (!relevant && currentGame == null) continue;
                Matcher chat = CHAT_LINE.matcher(line);
                if (!chat.matches()) continue;
                int secondOfDay = Integer.parseInt(chat.group(1)) * 3600
                        + Integer.parseInt(chat.group(2)) * 60
                        + Integer.parseInt(chat.group(3));
                if (lastSecondOfDay >= 0 && secondOfDay + 3600 < lastSecondOfDay) {
                    dayOffset++;
                }
                lastSecondOfDay = secondOfDay;
                long timestamp = base + dayOffset * DAY_MS + secondOfDay * 1000L;
                lastTimestamp = timestamp;
                String clean = clean(chat.group(4));
                if (!relevant) {
                    recordPlayerEvidence(clean);
                    continue;
                }
                processChat(clean, timestamp);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Skipping unreadable Minecraft log " + file.getName(), e);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
            closeCurrentGame(lastTimestamp);
        }
    }

    private static InputStream open(File file) throws Exception {
        InputStream in = new BufferedInputStream(
                new FileInputStream(file), LOG_BUFFER_SIZE);
        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".gz")) {
            return new GZIPInputStream(in, LOG_BUFFER_SIZE);
        }
        return in;
    }

    private static long localMidnight(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        return calendar.getTimeInMillis();
    }

    private static String clean(String raw) {
        if (raw == null) return "";
        String clean = COLOR_CODE.matcher(raw).replaceAll("");
        clean = clean.replace("\r", "").replace("\n", "").trim();
        clean = clean.replaceAll("\\s*\\[C\\]\\s*$", "").trim();
        return clean;
    }

    private void processChat(String clean, long timestamp) {
        if (clean == null || clean.isEmpty()) return;

        if (LangPatterns.isMiniServerTransferMessage(clean)) {
            if (currentGame != null && currentGame.hasThemeRounds()) {
                closeCurrentGame(timestamp);
            }
            ensureGame(timestamp);
            return;
        }

        if (PLAYER_COUNT.matcher(clean).matches()) {
            ensureGame(timestamp);
        }
        if (currentGame != null) {
            recordPlayerEvidence(clean);
            if (LangPatterns.selfCorrectGuessPoints(clean) != null) {
                currentGame.sawLocalPlayer = true;
            }
        }

        if (parseLanguageSelect(clean)) return;
        if (parseRound(clean, timestamp)) return;
        if (parseThemeReveal(clean, timestamp)) return;
        if (parseGtbsCopy(clean, timestamp)) return;
        if (parseGtbsMatching(clean, timestamp)) return;
        if ("Build Battle".equals(clean)) {
            pendingBuildBattleAt = timestamp;
            pendingBuildBattleLines = 0;
            return;
        }
        if (pendingBuildBattleAt > 0L) {
            pendingBuildBattleLines++;
            if (isWinnersLine(clean)) {
                closeCurrentGame(timestamp);
            } else if (pendingBuildBattleLines > FINAL_HEADER_LOOKAHEAD_LINES) {
                pendingBuildBattleAt = 0L;
                pendingBuildBattleLines = 0;
            }
        }
    }

    private boolean parseLanguageSelect(String clean) {
        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (lp.langSelectPrefix == null || !clean.startsWith(lp.langSelectPrefix)) {
                continue;
            }
            String name = null;
            if (lp.langSelectSuffix != null) {
                String rest = clean.substring(lp.langSelectPrefix.length());
                if (rest.endsWith(lp.langSelectSuffix)) {
                    name = rest.substring(0, rest.length() - lp.langSelectSuffix.length()).trim();
                }
            } else {
                name = clean.substring(lp.langSelectPrefix.length()).trim();
            }
            if (name != null && !name.isEmpty()) {
                String code = LangPatterns.codeFromDisplayName(name);
                currentLangCode = code != null ? code : lp.lang;
                if (currentRound != null && currentRound.langCode == null) {
                    currentRound.langCode = currentLangCode;
                }
                return true;
            }
        }
        return false;
    }

    private boolean parseRound(String clean, long timestamp) {
        if (japaneseRoundLabelPending) {
            japaneseRoundLabelPending = false;
            Matcher m = ROUND_NUM.matcher(clean.trim());
            if (m.matches()) {
                startRound(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        "ja", timestamp);
                return true;
            }
        }
        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (lp.roundPrefix == null || !clean.startsWith(lp.roundPrefix)) continue;
            String rest = clean.substring(lp.roundPrefix.length());
            if ("ja".equals(lp.lang) && rest.trim().isEmpty()) {
                japaneseRoundLabelPending = true;
                return true;
            }
            Matcher m = ROUND_NUM.matcher(rest.trim());
            if (m.matches()) {
                startRound(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        lp.lang, timestamp);
                return true;
            }
        }
        return false;
    }

    private void startRound(int roundNumber, int totalRounds, String langCode, long timestamp) {
        if (currentGame != null && currentGame.hasRounds()
                && roundNumber == 1 && currentRound != null
                && currentRound.roundNumber > 0) {
            closeCurrentGame(timestamp - 1L);
        }
        ExtractedThemeGame game = ensureGame(timestamp);
        currentLangCode = langCode != null ? langCode : currentLangCode;
        currentRound = new ExtractedThemeRound();
        currentRound.roundNumber = roundNumber;
        currentRound.totalRounds = totalRounds;
        currentRound.langCode = currentLangCode;
        currentRound.seenAt = timestamp;
        game.rounds.add(currentRound);
        game.end = Math.max(game.end, timestamp);
    }

    private boolean parseThemeReveal(String clean, long timestamp) {
        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (lp.themeWasPrefix == null || !clean.startsWith(lp.themeWasPrefix)) continue;
            String rest = clean.substring(lp.themeWasPrefix.length());
            if (lp.themeWasSuffix != null && rest.endsWith(lp.themeWasSuffix)) {
                String theme = rest.substring(0, rest.length() - lp.themeWasSuffix.length()).trim();
                if (!theme.isEmpty()) {
                    currentLangCode = lp.lang;
                    ExtractedThemeRound round = ensureRound(timestamp);
                    round.theme = theme;
                    round.langCode = lp.lang;
                    round.seenAt = timestamp;
                    ExtractedThemeGame game = ensureGame(timestamp);
                    game.end = Math.max(game.end, timestamp);
                }
                return true;
            }
        }
        return false;
    }

    private static boolean mightAffectUnfilteredThemeStats(String line) {
        if (line.indexOf("GtbStructures") >= 0
                || line.indexOf("Message copied to clipboard:") >= 0
                || line.indexOf("All themes matching the initial hint") >= 0
                || line.indexOf("Build Battle") >= 0
                || line.indexOf("mini") >= 0
                || line.indexOf("Mini") >= 0
                || line.indexOf("/10)!") >= 0
                || line.indexOf("/12)!") >= 0
                || line.indexOf("/16)!") >= 0) {
            return true;
        }
        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (containsRaw(line, lp.roundPrefix)
                    || containsRaw(line, lp.themeWasPrefix)
                    || containsRaw(line, lp.winnersKeyword)
                    || containsRaw(line, lp.langSelectPrefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsRaw(String line, String value) {
        return value != null && !value.isEmpty() && line.indexOf(value) >= 0;
    }

    private boolean parseGtbsCopy(String clean, long timestamp) {
        Matcher matcher = GTBS_COPY.matcher(clean);
        if (!matcher.matches()) return false;
        if (currentRound == null || currentRound.theme == null
                || timestamp - currentRound.seenAt > COPY_ATTACH_MS) {
            return true;
        }
        String first = firstCsvValue(matcher.group(1));
        String english = TranslationData.resolveEnglishTheme(first);
        if (english != null) {
            currentRound.englishThemes.clear();
            currentRound.englishThemes.add(english);
        }
        return true;
    }

    private boolean parseGtbsMatching(String clean, long timestamp) {
        Matcher matcher = GTBS_MATCHING.matcher(clean);
        if (!matcher.matches()) return false;
        ExtractedThemeRound round = ensureRound(timestamp);
        if (round.theme == null || round.theme.trim().isEmpty()) {
            List<String> candidates = csvValues(matcher.group(2));
            if (candidates.size() == 1) {
                round.theme = candidates.get(0);
                round.langCode = currentLangCode;
                round.seenAt = timestamp;
                ExtractedThemeGame game = ensureGame(timestamp);
                game.end = Math.max(game.end, timestamp);
            }
        }
        return true;
    }

    private ExtractedThemeGame ensureGame(long timestamp) {
        if (currentGame == null) {
            currentGame = new ExtractedThemeGame();
            currentGame.start = timestamp;
            currentGame.end = timestamp;
        } else {
            currentGame.end = Math.max(currentGame.end, timestamp);
        }
        return currentGame;
    }

    private ExtractedThemeRound ensureRound(long timestamp) {
        ExtractedThemeGame game = ensureGame(timestamp);
        if (currentRound == null) {
            currentRound = new ExtractedThemeRound();
            currentRound.langCode = currentLangCode;
            currentRound.seenAt = timestamp;
            game.rounds.add(currentRound);
        }
        return currentRound;
    }

    private void closeCurrentGame(long timestamp) {
        if (currentGame == null) return;
        if (timestamp > 0L) currentGame.end = Math.max(currentGame.end, timestamp);
        if (currentGame.hasThemeRounds()) {
            games.add(currentGame);
        }
        currentGame = null;
        currentRound = null;
        pendingBuildBattleAt = 0L;
        pendingBuildBattleLines = 0;
        japaneseRoundLabelPending = false;
    }

    private void recordPlayerEvidence(String clean) {
        if (currentGame == null || clean == null || clean.isEmpty()) return;
        Matcher matcher = IGN_TOKEN.matcher(clean);
        while (matcher.find()) {
            currentGame.mentionedNames.add(matcher.group().toLowerCase(Locale.ROOT));
        }
    }

    private static boolean isWinnersLine(String clean) {
        for (LangPatterns lp : LangPatterns.ALL_PATTERNS) {
            if (lp.winnersKeyword != null && clean.equals(lp.winnersKeyword)) return true;
        }
        return false;
    }

    private static String firstCsvValue(String payload) {
        List<String> values = csvValues(payload);
        return values.isEmpty() ? "" : values.get(0);
    }

    private static List<String> csvValues(String payload) {
        ArrayList<String> values = new ArrayList<String>();
        if (payload == null) return values;
        String[] split = payload.split(",");
        for (String value : split) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) values.add(trimmed);
        }
        return values;
    }

    private void notifyProgress(Progress progress) {
        if (progressListener == null || progress == null) return;
        try {
            progressListener.onProgress(progress);
        } catch (Exception ignored) {}
    }

    private static JsonArray gamesToJson(List<ExtractedThemeGame> games) {
        JsonArray array = new JsonArray();
        if (games == null) return array;
        for (ExtractedThemeGame game : games) {
            JsonObject gameJson = new JsonObject();
            gameJson.addProperty("start", game.start);
            gameJson.addProperty("end", game.end);
            gameJson.addProperty("sawLocalPlayer", game.sawLocalPlayer);
            if (!game.mentionedNames.isEmpty()) {
                JsonArray mentionedNames = new JsonArray();
                for (String name : game.mentionedNames) {
                    mentionedNames.add(new JsonPrimitive(name));
                }
                gameJson.add("mentionedNames", mentionedNames);
            }
            JsonArray rounds = new JsonArray();
            for (ExtractedThemeRound round : game.rounds) {
                JsonObject roundJson = new JsonObject();
                roundJson.addProperty("roundNumber", round.roundNumber);
                roundJson.addProperty("totalRounds", round.totalRounds);
                if (round.langCode != null) roundJson.addProperty("langCode", round.langCode);
                if (round.theme != null) roundJson.addProperty("theme", round.theme);
                roundJson.addProperty("seenAt", round.seenAt);
                JsonArray englishThemes = new JsonArray();
                for (String englishTheme : round.englishThemes) {
                    englishThemes.add(new JsonPrimitive(englishTheme));
                }
                roundJson.add("englishThemes", englishThemes);
                rounds.add(roundJson);
            }
            gameJson.add("rounds", rounds);
            array.add(gameJson);
        }
        return array;
    }

    private static List<ExtractedThemeGame> gamesFromJson(JsonArray array) {
        List<ExtractedThemeGame> out = new ArrayList<ExtractedThemeGame>();
        if (array == null) return out;
        for (JsonElement gameElement : array) {
            if (!gameElement.isJsonObject()) continue;
            JsonObject gameJson = gameElement.getAsJsonObject();
            ExtractedThemeGame game = new ExtractedThemeGame();
            game.start = getLong(gameJson, "start", 0L);
            game.end = getLong(gameJson, "end", game.start);
            game.sawLocalPlayer = getBoolean(gameJson, "sawLocalPlayer", false);
            JsonArray mentionedNames = getArray(gameJson, "mentionedNames");
            if (mentionedNames != null) {
                for (JsonElement nameElement : mentionedNames) {
                    if (nameElement == null || nameElement.isJsonNull()) continue;
                    String name = nameElement.getAsString();
                    if (name != null && !name.trim().isEmpty()) {
                        game.mentionedNames.add(name.trim().toLowerCase(Locale.ROOT));
                    }
                }
            }
            JsonArray rounds = getArray(gameJson, "rounds");
            if (rounds != null) {
                for (JsonElement roundElement : rounds) {
                    if (!roundElement.isJsonObject()) continue;
                    JsonObject roundJson = roundElement.getAsJsonObject();
                    ExtractedThemeRound round = new ExtractedThemeRound();
                    round.roundNumber = getInt(roundJson, "roundNumber", 0);
                    round.totalRounds = getInt(roundJson, "totalRounds", 0);
                    round.langCode = getString(roundJson, "langCode", null);
                    round.theme = getString(roundJson, "theme", null);
                    round.seenAt = getLong(roundJson, "seenAt", 0L);
                    JsonArray englishThemes = getArray(roundJson, "englishThemes");
                    if (englishThemes != null) {
                        for (JsonElement englishTheme : englishThemes) {
                            if (englishTheme.isJsonPrimitive()) {
                                round.englishThemes.add(englishTheme.getAsString());
                            }
                        }
                    }
                    game.rounds.add(round);
                }
            }
            out.add(game);
        }
        return out;
    }

    private static JsonArray getArray(JsonObject object, String key) {
        if (object == null) return null;
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray()
                ? element.getAsJsonArray()
                : null;
    }

    private static String getString(JsonObject object, String key, String fallback) {
        if (object == null) return fallback;
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive()
                ? element.getAsString()
                : fallback;
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        if (object == null) return fallback;
        JsonElement element = object.get(key);
        try {
            return element != null && element.isJsonPrimitive()
                    ? element.getAsInt()
                    : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long getLong(JsonObject object, String key, long fallback) {
        if (object == null) return fallback;
        JsonElement element = object.get(key);
        try {
            return element != null && element.isJsonPrimitive()
                    ? element.getAsLong()
                    : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
        if (object == null) return fallback;
        JsonElement element = object.get(key);
        try {
            return element != null && element.isJsonPrimitive()
                    ? element.getAsBoolean()
                    : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static List<ExtractedThemeGame> filterByLocalIdentities(
            List<ExtractedThemeGame> games, Set<String> localIdentities) {
        if (localIdentities == null || localIdentities.isEmpty()) return games;
        Set<String> keys = new HashSet<String>();
        for (String identity : localIdentities) {
            if (identity != null && !identity.trim().isEmpty()) {
                keys.add(identity.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (keys.isEmpty()) return games;
        List<ExtractedThemeGame> out = new ArrayList<ExtractedThemeGame>();
        for (ExtractedThemeGame game : games) {
            if (game == null) continue;
            boolean mentioned = false;
            for (String key : keys) {
                if (game.mentionedNames.contains(key)) {
                    mentioned = true;
                    break;
                }
            }
            if (game.sawLocalPlayer || mentioned) {
                out.add(game);
            }
        }
        return out;
    }

    interface ProgressListener {
        void onProgress(Progress progress);
    }

    static final class Progress {
        final int totalFiles;
        final int processedFiles;
        final int cacheHits;
        final int cacheMisses;
        final long totalBytes;
        final long processedBytes;
        final boolean complete;

        Progress(int totalFiles, int processedFiles, int cacheHits,
                 int cacheMisses, long totalBytes, long processedBytes,
                 boolean complete) {
            this.totalFiles = totalFiles;
            this.processedFiles = processedFiles;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
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

    private static final class ProgressState {
        final int totalFiles;
        final long totalBytes;
        int processedFiles;
        int cacheHits;
        int cacheMisses;
        long processedBytes;

        ProgressState(List<File> files) {
            this.totalFiles = files == null ? 0 : files.size();
            long bytes = 0L;
            if (files != null) {
                for (File file : files) {
                    if (file != null) bytes += Math.max(0L, file.length());
                }
            }
            this.totalBytes = bytes;
        }

        Progress record(CachedLogResult result, boolean complete) {
            processedFiles++;
            if (result != null) {
                processedBytes += result.bytes;
                if (result.cacheHit) cacheHits++;
                else cacheMisses++;
            }
            return snapshot(complete);
        }

        Progress snapshot(boolean complete) {
            return new Progress(totalFiles, processedFiles, cacheHits, cacheMisses,
                    totalBytes, processedBytes, complete);
        }
    }

    private static final class CachedLogResult {
        final File file;
        final List<ExtractedThemeGame> games;
        final boolean cacheHit;
        final long bytes;

        CachedLogResult(File file, List<ExtractedThemeGame> games, boolean cacheHit) {
            this.file = file;
            this.games = games == null
                    ? Collections.<ExtractedThemeGame>emptyList()
                    : games;
            this.cacheHit = cacheHit;
            this.bytes = file == null ? 0L : Math.max(0L, file.length());
        }
    }

    static final class ExtractedThemeGame {
        long start;
        long end;
        boolean sawLocalPlayer;
        final Set<String> mentionedNames = new HashSet<String>();
        final List<ExtractedThemeRound> rounds = new ArrayList<ExtractedThemeRound>();

        boolean hasRounds() {
            return !rounds.isEmpty();
        }

        boolean hasThemeRounds() {
            for (ExtractedThemeRound round : rounds) {
                if (round.theme != null && !round.theme.trim().isEmpty()) return true;
            }
            return false;
        }
    }

    static final class ExtractedThemeRound {
        int roundNumber;
        int totalRounds;
        String langCode;
        String theme;
        long seenAt;
        final List<String> englishThemes = new ArrayList<String>();
    }
}
