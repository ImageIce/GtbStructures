package com.gtbstructures.translation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtbstructures.stats.ScoreDelta;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Instance-local, content-addressed history for GTB translation snapshots.
 *
 * <p>Complete snapshots observed at runtime are stored below the Minecraft
 * game directory. The JAR also contains the earliest upstream snapshot plus
 * exact forward deltas for later GitHub versions, so a fresh installation can
 * resolve old logs without depending on mutable live translations.</p>
 */
final class TranslationHistory {
    private static final Logger LOGGER = Logger.getLogger("GtbStructures");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int INDEX_SCHEMA = 2;
    private static final int LEGACY_INDEX_SCHEMA = 1;
    private static final int SEED_SCHEMA = 4;
    private static final int LEGACY_SEED_SCHEMA_V3 = 3;
    private static final int LEGACY_SEED_SCHEMA = 2;
    private static final int DATASET_CACHE_LIMIT = 2;
    private static final String SEED_RESOURCE = "/translation-history-seed.json";
    private static final String SNAPSHOT_BUNDLE_RESOURCE =
            "/translation-history-snapshots.bin";
    private static final String APPROVAL_HISTORY_RESOURCE =
            "/translation-approval-history.json.gz";
    private static final int APPROVAL_HISTORY_SCHEMA = 1;
    private static final String SNAPSHOT_BUNDLE_V2_MAGIC = "GTBHIST2";
    private static final String SNAPSHOT_BUNDLE_V3_MAGIC = "GTBHIST3";
    private static final int MAX_BUNDLE_ENTRIES = 1000;
    private static final int MAX_COMPRESSED_SNAPSHOT_BYTES = 16 * 1024 * 1024;
    private static final int MAX_RAW_SNAPSHOT_BYTES = 32 * 1024 * 1024;
    private static final int MAX_APPROVAL_HISTORY_BYTES = 4 * 1024 * 1024;
    private static final int MAX_APPROVAL_HISTORY_JSON_BYTES = 16 * 1024 * 1024;
    private static final int MAX_DELTA_OPERATIONS = 1000000;
    private static final int BUNDLE_ENTRY_FULL = 0;
    private static final int BUNDLE_ENTRY_DELTA = 1;
    private static final String INDEX_FILE = "index.json";
    private static final String EXTRACTED_SEED_FILE = "bundled-history.json";

    private static File historyDir;
    private static HistoryIndex index = new HistoryIndex();
    private static SeedFile seed = new SeedFile();
    private static byte[] seedBytes;
    private static boolean configured;
    private static ApprovalTimeline approvalTimeline = new ApprovalTimeline();
    /** Stable theme IDs from the active translation snapshot, keyed to its canonical English text. */
    private static final Map<Long, String> currentThemeUniverse = new HashMap<Long, String>();

    private static final Map<String, HistoricalDataset> DATASET_CACHE =
            new LinkedHashMap<String, HistoricalDataset>(4, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, HistoricalDataset> eldest) {
                    return size() > DATASET_CACHE_LIMIT;
                }
            };

    private TranslationHistory() {
    }

    static synchronized void configure(File gameDir) {
        if (gameDir == null) return;
        File selected = new File(new File(new File(gameDir, "config"), "gtbstats"),
                "translation-history");
        try {
            selected = selected.getCanonicalFile();
        } catch (IOException ignored) {
            selected = selected.getAbsoluteFile();
        }
        if (configured && selected.equals(historyDir)) return;

        historyDir = selected;
        configured = true;
        DATASET_CACHE.clear();
        loadBundledSeed();
        loadBundledApprovalTimeline();
        if (!historyDir.exists() && !historyDir.mkdirs()) {
            LOGGER.warning("Could not create translation history directory " + historyDir);
            return;
        }
        materializeBundledSeed();
        index = readIndex();
        approvalTimeline.addAll(index.approvalEvents);
        persistMigratedIndex();
        importLegacyCaches(gameDir);
    }

    static synchronized boolean isConfigured() {
        return configured && historyDir != null && historyDir.isDirectory();
    }

    /**
     * Sets the only English-theme universe historical data may resolve into.
     * Historical snapshots are revision sources, not alternate theme catalogs:
     * a theme absent from the active snapshot must stay absent even for dated logs.
     */
    static synchronized void setCurrentThemeUniverse(String json) {
        Map<Long, String> parsed = new HashMap<Long, String>();
        try {
            JsonElement root = new JsonParser().parse(json);
            if (root.isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray()) {
                    if (!element.isJsonObject()) continue;
                    JsonObject entry = element.getAsJsonObject();
                    long themeId = number(entry, "id");
                    String english = string(entry, "theme");
                    if (themeId <= 0L || english == null || english.isEmpty()) continue;
                    parsed.put(Long.valueOf(themeId),
                            Normalizer.normalize(english, Normalizer.Form.NFC));
                }
            }
        } catch (Exception ignored) {
            // An invalid active snapshot has no trustworthy stable theme IDs.
        }
        currentThemeUniverse.clear();
        currentThemeUniverse.putAll(parsed);
        DATASET_CACHE.clear();
    }

    static synchronized StoredData prepareBundled(String json) throws IOException {
        String hash = sha256Utf8(json);
        long boundary = seed.currentValidFrom;
        if (boundary <= 0L || !hash.equalsIgnoreCase(seed.currentHash)) {
            boundary = System.currentTimeMillis();
        }
        if (!isConfigured()) return new StoredData(json, hash, boundary);

        storeSnapshot(hash, json);
        Interval active = openInterval();
        if (active == null) {
            activate(hash, boundary, "bundled");
        } else if (!hash.equalsIgnoreCase(active.hash)
                && boundary > active.validFrom) {
            activate(hash, boundary, "bundled");
        }

        active = openInterval();
        if (active != null) {
            String activeJson = readSnapshot(active.hash);
            if (activeJson != null) {
                return new StoredData(activeJson, active.hash, active.validFrom);
            }
            long recoveryTime = System.currentTimeMillis();
            activate(hash, recoveryTime, "bundled-recovery");
            return new StoredData(json, hash, recoveryTime);
        }
        return new StoredData(json, hash, boundary);
    }

    static synchronized StoredData recordRemote(String json, long observedAt) {
        if (!isConfigured()) return null;
        try {
            HistoricalDataset checked = HistoricalDataset.parse(json);
            if (checked.englishThemeCount() < 100) {
                LOGGER.warning("Rejected implausibly small remote translation snapshot");
                return null;
            }
            String hash = sha256Utf8(json);
            storeSnapshot(hash, json);
            int ordinal = ensureSnapshotOrdinal(hash);
            List<ApprovalEvent> events = extractApprovedEvents(json, ordinal);
            addRuntimeApprovalEvents(events);
            DATASET_CACHE.put(hash, HistoricalDataset.parse(json, ordinal));
            activate(hash, observedAt, "remote");
            Interval active = openInterval();
            long validFrom = active == null ? observedAt : active.validFrom;
            return new StoredData(json, hash, validFrom);
        } catch (Exception e) {
            LOGGER.warning("Rejected invalid remote translation snapshot: " + e.getMessage());
            return null;
        }
    }

    static synchronized HistoricalResolution resolve(String requestedHash, long seenAt,
                                                      String langCode, String displayedTheme,
                                                      ScoreDelta builderDelta) {
        if (displayedTheme == null || displayedTheme.trim().isEmpty()) {
            return HistoricalResolution.found(Collections.<String>emptyList());
        }

        String hash = normalizeHash(requestedHash);
        boolean approvalOnly = seenAt > 0L && requiresApprovalTime(langCode);
        if (hash != null && seenAt > 0L) {
            int ordinal = snapshotOrdinal(hash);
            if (ordinal != Integer.MAX_VALUE) {
                ApprovalResolution approved = approvalTimeline.resolveAt(
                        seenAt, langCode, displayedTheme, builderDelta, ordinal);
                if (approved.found) return HistoricalResolution.found(approved.englishThemes);
            }
        }
        if (hash != null) {
            HistoricalResolution exact = resolveHash(
                    hash, langCode, displayedTheme, builderDelta, approvalOnly);
            if (exact.found) return exact;
        }

        if (seenAt > 0L) {
            ApprovalResolution approved = approvalTimeline.resolveAt(
                    seenAt, langCode, displayedTheme, builderDelta, Integer.MAX_VALUE);
            if (approved.found) return HistoricalResolution.found(approved.englishThemes);
            HistoricalChoice choice = selectAt(seenAt);
            if (choice != null) {
                if (choice.seedVersion != null) {
                    return resolveSeed(choice.seedVersion, langCode, displayedTheme, builderDelta,
                            approvalOnly);
                }
                return resolveStoredHash(
                        choice.hash, langCode, displayedTheme, builderDelta, approvalOnly);
            }
        }
        return HistoricalResolution.notFound();
    }

    static synchronized String sha256Utf8(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(Character.forDigit((b >>> 4) & 0x0F, 16));
                out.append(Character.forDigit(b & 0x0F, 16));
            }
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static synchronized File historyDirForTest() {
        return historyDir;
    }

    static synchronized int bundledVersionCountForTest() {
        return seed.versions.size();
    }

    static synchronized long earliestBundledValidFromForTest() {
        return seed.versions.isEmpty() ? -1L : seed.versions.get(0).validFrom;
    }

    static synchronized int approvalEventCountForTest() {
        return approvalTimeline.eventCount();
    }

    static synchronized List<String> verifyBundledArchiveForTest() throws IOException {
        DATASET_CACHE.clear();
        InputStream resource = TranslationHistory.class.getResourceAsStream(
                SNAPSHOT_BUNDLE_RESOURCE);
        if (resource == null) throw new IOException("Missing bundled translation archive");
        DataInputStream bundle = new DataInputStream(resource);
        try {
            String magic = readBundleMagic(bundle);
            if (SNAPSHOT_BUNDLE_V3_MAGIC.equals(magic)) {
                readV3BundledDataset(bundle, null, true);
            } else if (SNAPSHOT_BUNDLE_V2_MAGIC.equals(magic)) {
                verifyV2BundledArchive(bundle);
            } else {
                throw new IOException("invalid bundle header");
            }
            if (bundle.read() >= 0) throw new IOException("trailing bundle data");
            List<String> hashes = new ArrayList<String>();
            for (SeedVersion version : seed.versions) hashes.add(version.hash);
            return hashes;
        } finally {
            try {
                bundle.close();
            } catch (IOException ignored) {
            }
        }
    }

    static byte[] reconstructV3DeltaForTest(
            String expectedHash, byte[] base, int rawLength, byte[] compressed) throws IOException {
        byte[] reconstructed = applyForwardDelta(base, rawLength, compressed);
        verifySnapshotHash(expectedHash, reconstructed);
        return reconstructed;
    }

    static synchronized StoredData recordSnapshotForTest(
            String json, long observedAt, String source) throws IOException {
        if (!isConfigured()) throw new IOException("Translation history is not configured");
        String hash = sha256Utf8(json);
        storeSnapshot(hash, json);
        int ordinal = ensureSnapshotOrdinal(hash);
        List<ApprovalEvent> events = extractApprovedEvents(json, ordinal);
        addRuntimeApprovalEvents(events);
        DATASET_CACHE.put(hash, HistoricalDataset.parse(json, ordinal));
        activate(hash, observedAt, source == null ? "test" : source);
        Interval active = openInterval();
        return new StoredData(json, hash, active == null ? observedAt : active.validFrom);
    }

    static synchronized void resetForTest(File gameDir) {
        configured = false;
        historyDir = new File(gameDir, "config/gtbstats/translation-history");
        index = new HistoryIndex();
        seed = new SeedFile();
        seedBytes = null;
        DATASET_CACHE.clear();
        approvalTimeline = new ApprovalTimeline();
        if (!historyDir.isDirectory() && !historyDir.mkdirs() && !historyDir.isDirectory()) {
            throw new IllegalStateException("Could not create translation history test directory: "
                    + historyDir);
        }
        configured = true;
        loadBundledSeed();
        loadBundledApprovalTimeline();
        materializeBundledSeed();
        index = readIndex();
        approvalTimeline.addAll(index.approvalEvents);
        persistMigratedIndex();
    }

    private static HistoricalResolution resolveHash(String hash, String langCode,
                                                     String displayedTheme,
                                                     ScoreDelta builderDelta,
                                                     boolean englishOnly) {
        SeedVersion version = seed.versionByHash(hash);
        if (version != null && (!isConfigured() || !snapshotFile(hash).isFile())) {
            return resolveSeed(version, langCode, displayedTheme, builderDelta, englishOnly);
        }
        HistoricalResolution stored = resolveStoredHash(
                hash, langCode, displayedTheme, builderDelta, englishOnly);
        if (stored.found) return stored;
        return version == null
                ? HistoricalResolution.notFound()
                : resolveSeed(version, langCode, displayedTheme, builderDelta, englishOnly);
    }

    private static HistoricalResolution resolveStoredHash(String hash, String langCode,
                                                           String displayedTheme,
                                                           ScoreDelta builderDelta,
                                                           boolean englishOnly) {
        if (!isConfigured()) return HistoricalResolution.notFound();
        try {
            HistoricalDataset data = DATASET_CACHE.get(hash);
            if (data == null) {
                String json = readSnapshot(hash);
                if (json == null) return HistoricalResolution.notFound();
                data = HistoricalDataset.parse(json, snapshotOrdinal(hash));
                DATASET_CACHE.put(hash, data);
            }
            return HistoricalResolution.found(englishOnly
                    ? data.resolveEnglishOnly(displayedTheme, builderDelta)
                    : data.resolve(langCode, displayedTheme, builderDelta));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not read historical translation snapshot " + hash, e);
            return HistoricalResolution.notFound();
        }
    }

    private static HistoricalResolution resolveSeed(SeedVersion version, String langCode,
                                                     String displayedTheme,
                                                     ScoreDelta builderDelta,
                                                     boolean englishOnly) {
        if (version.bundledSnapshot) {
            HistoricalDataset data = readBundledDataset(version.hash);
            return data == null
                    ? HistoricalResolution.notFound()
                    : HistoricalResolution.found(englishOnly
                            ? data.resolveEnglishOnly(displayedTheme, builderDelta)
                            : data.resolve(langCode, displayedTheme, builderDelta));
        }
        SeedOverride override = version.findOverride(langCode, displayedTheme);
        if (override != null) {
            return HistoricalResolution.found(
                    narrow(override.englishThemes, builderDelta));
        }
        return HistoricalResolution.found(
                TranslationData.resolveEnglishThemes(langCode, displayedTheme, builderDelta));
    }

    private static boolean requiresApprovalTime(String langCode) {
        if (!approvalTimeline.complete) return false;
        String language = TranslationData.normalizeLangCode(langCode);
        return language != null && !"en".equals(language);
    }

    private static HistoricalChoice selectAt(long seenAt) {
        HistoricalChoice best = null;
        for (Interval interval : index.intervals) {
            if (!interval.contains(seenAt) || normalizeHash(interval.hash) == null) continue;
            if (best == null || interval.validFrom > best.validFrom) {
                best = HistoricalChoice.stored(interval.hash, interval.validFrom);
            }
        }
        for (SeedVersion version : seed.versions) {
            if (!version.contains(seenAt) || normalizeHash(version.hash) == null) continue;
            if (best == null || version.validFrom > best.validFrom) {
                best = HistoricalChoice.seed(version);
            }
        }
        return best;
    }

    private static List<String> narrow(List<String> themes, ScoreDelta builderDelta) {
        TreeSet<String> ordered = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        if (themes != null) ordered.addAll(themes);
        if (ordered.size() <= 1 || builderDelta == null) {
            return new ArrayList<String>(ordered);
        }
        List<String> narrowed = new ArrayList<String>();
        for (String theme : ordered) {
            if (builderDelta.canBe(TranslationData.lengthBucket(theme))) narrowed.add(theme);
        }
        return narrowed.isEmpty() ? new ArrayList<String>(ordered) : narrowed;
    }

    private static void activate(String hash, long validFrom, String source) throws IOException {
        if (validFrom <= 0L) validFrom = System.currentTimeMillis();
        Interval active = openInterval();
        if (active != null && hash.equalsIgnoreCase(active.hash)) {
            active.lastObservedAt = Math.max(active.lastObservedAt, validFrom);
            writeIndex();
            return;
        }
        if (active != null) {
            if (validFrom <= active.validFrom) {
                writeIndex();
                return;
            }
            active.validUntil = validFrom;
        }
        Interval next = new Interval();
        next.hash = hash;
        next.validFrom = validFrom;
        next.validUntil = 0L;
        next.lastObservedAt = validFrom;
        next.source = source;
        index.intervals.add(next);
        Collections.sort(index.intervals, new Comparator<Interval>() {
            @Override
            public int compare(Interval left, Interval right) {
                return left.validFrom < right.validFrom ? -1
                        : left.validFrom == right.validFrom ? 0 : 1;
            }
        });
        writeIndex();
    }

    private static Interval openInterval() {
        Interval selected = null;
        for (Interval interval : index.intervals) {
            if (interval.validUntil != 0L || normalizeHash(interval.hash) == null) continue;
            if (selected == null || interval.validFrom > selected.validFrom) selected = interval;
        }
        return selected;
    }

    private static int nextSnapshotOrdinal() {
        int next = seed.versions.size();
        for (Integer ordinal : index.snapshotOrdinals.values()) {
            if (ordinal != null && ordinal >= next) next = ordinal + 1;
        }
        return next;
    }

    private static int ensureSnapshotOrdinal(String hash) {
        int existing = snapshotOrdinal(hash);
        if (existing != Integer.MAX_VALUE) return existing;
        int ordinal = nextSnapshotOrdinal();
        index.snapshotOrdinals.put(hash, ordinal);
        return ordinal;
    }

    private static void addRuntimeApprovalEvents(List<ApprovalEvent> events) {
        if (events == null) return;
        for (ApprovalEvent event : events) {
            if (approvalTimeline.add(event)) index.approvalEvents.add(event);
        }
    }

    private static int snapshotOrdinal(String hash) {
        SeedVersion version = seed.versionByHash(hash);
        if (version != null) return version.snapshotOrdinal;
        Integer ordinal = hash == null ? null : index.snapshotOrdinals.get(hash.toLowerCase(Locale.ROOT));
        if (ordinal != null) return ordinal;
        return Integer.MAX_VALUE;
    }

    private static void importLegacyCaches(File gameDir) {
        List<File> candidates = new ArrayList<File>();
        File instanceCache = new File(
                new File(new File(gameDir, "config"), "gtbstructures"),
                "translations-data.json");
        if (instanceCache.isFile()) {
            candidates.add(instanceCache);
        } else {
            String home = System.getProperty("user.home");
            if (home != null && !home.isEmpty()) {
                candidates.add(new File(new File(new File(home, ".gtbstructures"), "cache"),
                        "translations-data.json"));
            }
        }
        Collections.sort(candidates, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.lastModified() < right.lastModified() ? -1
                        : left.lastModified() == right.lastModified() ? 0 : 1;
            }
        });
        boolean bootstrapIntervals = index.intervals.isEmpty();
        boolean changed = false;
        for (File candidate : candidates) {
            if (!candidate.isFile() || candidate.length() <= 0L) continue;
            String importKey = canonicalPath(candidate) + "|" + candidate.length()
                    + "|" + candidate.lastModified();
            if (index.importedSources.contains(importKey)) continue;
            try {
                String json = readUtf8(candidate);
                HistoricalDataset parsed = HistoricalDataset.parse(json);
                if (parsed.englishThemeCount() < 100) continue;
                String hash = sha256Utf8(json);
                storeSnapshot(hash, json);
                int ordinal = ensureSnapshotOrdinal(hash);
                addRuntimeApprovalEvents(extractApprovedEvents(json, ordinal));
                DATASET_CACHE.put(hash, HistoricalDataset.parse(json, ordinal));
                if (bootstrapIntervals) {
                    long observedAt = candidate.lastModified();
                    activate(hash, observedAt > 0L ? observedAt : System.currentTimeMillis(),
                            "legacy-cache");
                }
                index.importedSources.add(importKey);
                changed = true;
                LOGGER.info("Imported legacy translation cache into Minecraft history: " + hash);
            } catch (Exception e) {
                LOGGER.warning("Could not import legacy translation cache " + candidate + ": "
                        + e.getMessage());
            }
        }
        if (changed) {
            try {
                writeIndex();
            } catch (IOException e) {
                LOGGER.warning("Could not record legacy translation import: " + e.getMessage());
            }
        }
    }

    private static void loadBundledSeed() {
        seed = new SeedFile();
        seedBytes = null;
        InputStream in = TranslationHistory.class.getResourceAsStream(SEED_RESOURCE);
        if (in == null) return;
        try {
            seedBytes = readAll(in);
            SeedFile parsed = GSON.fromJson(new String(seedBytes, "UTF-8"), SeedFile.class);
            if (parsed != null && (parsed.schemaVersion == SEED_SCHEMA
                    || parsed.schemaVersion == LEGACY_SEED_SCHEMA_V3
                    || parsed.schemaVersion == LEGACY_SEED_SCHEMA)) {
                parsed.prepare();
                seed = parsed;
            }
        } catch (Exception e) {
            LOGGER.warning("Could not load bundled translation history: " + e.getMessage());
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void loadBundledApprovalTimeline() {
        approvalTimeline = new ApprovalTimeline();
        ApprovalTimeline loaded = new ApprovalTimeline();
        InputStream raw = TranslationHistory.class.getResourceAsStream(APPROVAL_HISTORY_RESOURCE);
        if (raw == null) return;
        GZIPInputStream gzip = null;
        try {
            ApprovalHistoryMetadata metadata = seed.approvalHistory;
            byte[] compressed = readAllBounded(raw, MAX_APPROVAL_HISTORY_BYTES);
            raw.close();
            raw = null;
            if (metadata == null || !"translation-approval-history.json.gz".equals(metadata.resource)
                    || metadata.schemaVersion != APPROVAL_HISTORY_SCHEMA
                    || metadata.sha256 == null || !metadata.sha256.equalsIgnoreCase(sha256(compressed))) {
                throw new IOException("approval history manifest checksum mismatch");
            }
            gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
            byte[] json = readAllBounded(gzip, MAX_APPROVAL_HISTORY_JSON_BYTES);
            JsonElement parsed = new JsonParser().parse(new String(json, "UTF-8"));
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IOException("invalid approval history root");
            }
            JsonObject file = parsed.getAsJsonObject();
            JsonElement schema = file.get("schemaVersion");
            if (schema == null || !schema.isJsonPrimitive()
                    || schema.getAsInt() != APPROVAL_HISTORY_SCHEMA) {
                throw new IOException("unsupported approval history schema");
            }
            if (!seed.currentHash.equalsIgnoreCase(string(file, "currentHash"))) {
                throw new IOException("approval history current hash mismatch");
            }
            JsonObject themes = objectById(file.get("themes"));
            int entryCount = 0;
            int eventCount = 0;
            JsonElement entriesElement = file.get("entries");
            if (entriesElement != null && entriesElement.isJsonArray()) {
                for (JsonElement entryElement : entriesElement.getAsJsonArray()) {
                    if (!entryElement.isJsonArray()) continue;
                    JsonArray entry = entryElement.getAsJsonArray();
                    if (entry.size() != 3 || !entry.get(0).isJsonPrimitive()
                            || !entry.get(1).isJsonPrimitive() || !entry.get(2).isJsonArray()) continue;
                    String themeKey = entry.get(0).getAsString();
                    String english = string(themes, themeKey);
                    String language = entry.get(1).getAsString();
                    if (english == null || language == null) continue;
                    entryCount++;
                    if ("en".equals(language)) language = "pirate";
                    for (JsonElement versionElement : entry.get(2).getAsJsonArray()) {
                        if (!versionElement.isJsonArray()) continue;
                        JsonArray version = versionElement.getAsJsonArray();
                        if (version.size() != 3 || !version.get(0).isJsonPrimitive()
                                || !version.get(1).isJsonPrimitive()
                                || !version.get(2).isJsonPrimitive()) continue;
                        ApprovalEvent event = new ApprovalEvent();
                        event.themeId = entry.get(0).getAsLong();
                        event.languageCode = language;
                        event.approvedAt = version.get(0).getAsLong();
                        event.firstSnapshotOrdinal = version.get(1).getAsInt();
                        event.translation = version.get(2).getAsString();
                        event.englishTheme = english;
                        if (!loaded.add(event)) {
                            throw new IOException("invalid or duplicate approval history event");
                        }
                        eventCount++;
                    }
                }
            }
            if (themes.entrySet().size() != metadata.themeCount || entryCount != metadata.entryCount
                    || eventCount != metadata.eventCount) {
                throw new IOException("approval history count mismatch");
            }
            loaded.complete = true;
            approvalTimeline = loaded;
        } catch (Exception e) {
            LOGGER.warning("Could not load bundled translation approval history: "
                    + e.getMessage());
        } finally {
            if (gzip != null) {
                try {
                    gzip.close();
                } catch (IOException ignored) {
                }
            } else if (raw != null) {
                try {
                    raw.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static JsonObject objectById(JsonElement element) {
        JsonObject out = new JsonObject();
        if (element == null || !element.isJsonArray()) return out;
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonArray()) continue;
            JsonArray pair = item.getAsJsonArray();
            if (pair.size() != 2 || !pair.get(0).isJsonPrimitive()
                    || !pair.get(1).isJsonPrimitive()) continue;
            out.addProperty(pair.get(0).getAsString(), pair.get(1).getAsString());
        }
        return out;
    }

    private static List<ApprovalEvent> extractApprovedEvents(String json, int ordinal) {
        List<ApprovalEvent> out = new ArrayList<ApprovalEvent>();
        JsonElement root = new JsonParser().parse(json);
        if (!root.isJsonArray()) return out;
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            long themeId = number(entry, "id");
            String english = string(entry, "theme");
            JsonObject translations = object(entry, "translations");
            if (themeId <= 0L || english == null || english.isEmpty() || translations == null) continue;
            for (Map.Entry<String, JsonElement> item : translations.entrySet()) {
                if (!item.getValue().isJsonObject()) continue;
                String lang = "en".equals(item.getKey()) ? "pirate" : item.getKey();
                if ("co".equals(lang)) continue;
                JsonObject body = item.getValue().getAsJsonObject();
                JsonElement approved = body.get("is_approved");
                if (approved == null || !approved.isJsonPrimitive() || !approved.getAsBoolean()) {
                    continue;
                }
                String translation = string(body, "translation");
                long approvedAt = approvalTime(string(body, "approved_at"));
                if (translation == null || translation.isEmpty() || approvedAt <= 0L) continue;
                ApprovalEvent event = new ApprovalEvent();
                event.themeId = themeId;
                event.languageCode = lang;
                event.translation = translation;
                event.englishTheme = english;
                event.approvedAt = approvedAt;
                event.firstSnapshotOrdinal = ordinal;
                out.add(event);
            }
        }
        return out;
    }

    private static long approvalTime(String value) {
        if (value == null || value.trim().isEmpty()) return 0L;
        try {
            return OffsetDateTime.parse(value).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static void materializeBundledSeed() {
        if (seedBytes == null || historyDir == null) return;
        File target = new File(historyDir, EXTRACTED_SEED_FILE);
        try {
            if (target.isFile()) {
                byte[] existing = readBytes(target);
                if (sha256(existing).equals(sha256(seedBytes))) return;
            }
            writeAtomic(target, seedBytes);
        } catch (Exception e) {
            LOGGER.warning("Could not save bundled translation history: " + e.getMessage());
        }
    }

    private static HistoryIndex readIndex() {
        File file = new File(historyDir, INDEX_FILE);
        if (!file.isFile()) return new HistoryIndex();
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            HistoryIndex parsed = GSON.fromJson(reader, HistoryIndex.class);
            if (parsed == null || (parsed.schemaVersion != INDEX_SCHEMA
                    && parsed.schemaVersion != LEGACY_INDEX_SCHEMA)) return new HistoryIndex();
            parsed.prepare();
            if (parsed.schemaVersion == LEGACY_INDEX_SCHEMA) migrateLegacyIndex(parsed);
            return parsed;
        } catch (Exception e) {
            LOGGER.warning("Could not read translation history index: " + e.getMessage());
            return new HistoryIndex();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void migrateLegacyIndex(HistoryIndex legacy) {
        int ordinal = seed.versions.size();
        Collections.sort(legacy.intervals, new Comparator<Interval>() {
            @Override
            public int compare(Interval left, Interval right) {
                return left.validFrom < right.validFrom ? -1
                        : left.validFrom == right.validFrom ? 0 : 1;
            }
        });
        approvalTimeline.addAll(legacy.approvalEvents);
        for (Interval interval : legacy.intervals) {
            if (seed.versionByHash(interval.hash) != null) continue;
            String normalizedHash = normalizeHash(interval.hash);
            if (normalizedHash == null) continue;
            Integer existingOrdinal = legacy.snapshotOrdinals.get(normalizedHash);
            if (existingOrdinal == null) {
                existingOrdinal = ordinal++;
                legacy.snapshotOrdinals.put(normalizedHash, existingOrdinal);
            }
            String snapshot = readSnapshot(interval.hash);
            if (snapshot == null) continue;
            for (ApprovalEvent event : extractApprovedEvents(snapshot, existingOrdinal)) {
                if (approvalTimeline.add(event)) legacy.approvalEvents.add(event);
            }
        }
        legacy.schemaVersion = INDEX_SCHEMA;
        legacy.needsWrite = true;
    }

    private static void persistMigratedIndex() {
        if (!index.needsWrite) return;
        try {
            writeIndex();
            index.needsWrite = false;
        } catch (IOException e) {
            LOGGER.warning("Could not persist migrated translation history index: "
                    + e.getMessage());
        }
    }

    private static void writeIndex() throws IOException {
        index.schemaVersion = INDEX_SCHEMA;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Writer writer = new OutputStreamWriter(bytes, "UTF-8");
        GSON.toJson(index, writer);
        writer.close();
        writeAtomic(new File(historyDir, INDEX_FILE), bytes.toByteArray());
    }

    private static void storeSnapshot(String hash, String json) throws IOException {
        File target = snapshotFile(hash);
        if (target.isFile() && target.length() > 0L) return;
        File temp = new File(historyDir, target.getName() + ".tmp");
        GZIPOutputStream gzip = null;
        try {
            gzip = new GZIPOutputStream(new FileOutputStream(temp));
            gzip.write(json.getBytes("UTF-8"));
            gzip.close();
            gzip = null;
            moveAtomic(temp, target);
        } finally {
            if (gzip != null) {
                try {
                    gzip.close();
                } catch (IOException ignored) {
                }
            }
            if (temp.exists() && !temp.equals(target)) temp.delete();
        }
    }

    private static String readSnapshot(String hash) {
        File file = snapshotFile(hash);
        if (!file.isFile()) return null;
        GZIPInputStream gzip = null;
        try {
            gzip = new GZIPInputStream(new FileInputStream(file));
            return new String(readAll(gzip), "UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            if (gzip != null) {
                try {
                    gzip.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static HistoricalDataset readBundledDataset(String requestedHash) {
        HistoricalDataset cached = DATASET_CACHE.get(requestedHash);
        if (cached != null) return cached;
        InputStream resource = TranslationHistory.class.getResourceAsStream(
                SNAPSHOT_BUNDLE_RESOURCE);
        if (resource == null) return null;
        DataInputStream bundle = new DataInputStream(resource);
        try {
            String magic = readBundleMagic(bundle);
            if (SNAPSHOT_BUNDLE_V2_MAGIC.equals(magic)) {
                return readV2BundledDataset(bundle, requestedHash);
            }
            if (SNAPSHOT_BUNDLE_V3_MAGIC.equals(magic)) {
                return readV3BundledDataset(bundle, requestedHash, false);
            }
            throw new IOException("invalid bundle header");
        } catch (Exception e) {
            LOGGER.warning("Could not read bundled translation snapshot " + requestedHash
                    + ": " + e.getMessage());
            return null;
        } finally {
            try {
                bundle.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String readBundleMagic(DataInputStream bundle) throws IOException {
        byte[] magic = new byte[SNAPSHOT_BUNDLE_V3_MAGIC.length()];
        bundle.readFully(magic);
        return new String(magic, "US-ASCII");
    }

    private static HistoricalDataset readV2BundledDataset(
            DataInputStream bundle, String requestedHash) throws IOException {
        int count = readBundleEntryCount(bundle);
        for (int entry = 0; entry < count; entry++) {
            String hash = readBundleHash(bundle);
            int compressedLength = readCompressedLength(bundle);
            if (!requestedHash.equalsIgnoreCase(hash)) {
                skipFully(bundle, compressedLength);
                continue;
            }
            byte[] compressed = new byte[compressedLength];
            bundle.readFully(compressed);
            byte[] jsonBytes = readGzipBytes(compressed, -1);
            verifySnapshotHash(requestedHash, jsonBytes);
            HistoricalDataset parsed = HistoricalDataset.parse(
                    new String(jsonBytes, "UTF-8"), snapshotOrdinal(requestedHash));
            DATASET_CACHE.put(requestedHash, parsed);
            return parsed;
        }
        return null;
    }

    private static void verifyV2BundledArchive(DataInputStream bundle) throws IOException {
        int count = readBundleEntryCount(bundle);
        if (count != seed.versions.size()) {
            throw new IOException("bundle entry count " + count
                    + " does not match manifest count " + seed.versions.size());
        }
        for (int entry = 0; entry < count; entry++) {
            String hash = readBundleHash(bundle);
            if (!seed.versions.get(entry).hash.equalsIgnoreCase(hash)) {
                throw new IOException("bundle hash does not match manifest at entry " + entry);
            }
            int compressedLength = readCompressedLength(bundle);
            byte[] compressed = new byte[compressedLength];
            bundle.readFully(compressed);
            verifySnapshotHash(hash, readGzipBytes(compressed, -1));
        }
    }

    private static HistoricalDataset readV3BundledDataset(
            DataInputStream bundle, String requestedHash, boolean verifyAll) throws IOException {
        int count = readBundleEntryCount(bundle);
        if (count != seed.versions.size()) {
            throw new IOException("bundle entry count " + count
                    + " does not match manifest count " + seed.versions.size());
        }
        byte[] previous = null;
        HistoricalDataset requested = null;
        for (int entry = 0; entry < count; entry++) {
            int kind = bundle.readUnsignedByte();
            String hash = readBundleHash(bundle);
            SeedVersion expected = seed.versions.get(entry);
            if (!expected.hash.equalsIgnoreCase(hash)) {
                throw new IOException("bundle hash does not match manifest at entry " + entry);
            }
            int rawLength = readRawLength(bundle);
            int compressedLength = readCompressedLength(bundle);
            byte[] compressed = new byte[compressedLength];
            bundle.readFully(compressed);
            if (entry == 0) {
                if (kind != BUNDLE_ENTRY_FULL) {
                    throw new IOException("first bundled snapshot is not full");
                }
                previous = readGzipBytes(compressed, rawLength);
            } else {
                if (kind != BUNDLE_ENTRY_DELTA) {
                    throw new IOException("bundled snapshot " + entry + " is not a delta");
                }
                previous = applyForwardDelta(previous, rawLength, compressed);
            }
            verifySnapshotHash(hash, previous);
            if (requestedHash != null && requestedHash.equalsIgnoreCase(hash)
                    && requested == null) {
                requested = HistoricalDataset.parse(
                        new String(previous, "UTF-8"), expected.snapshotOrdinal);
                DATASET_CACHE.put(requestedHash, requested);
                if (!verifyAll) return requested;
            }
        }
        return requested;
    }

    private static int readBundleEntryCount(DataInputStream bundle) throws IOException {
        int count = bundle.readInt();
        if (count <= 0 || count > MAX_BUNDLE_ENTRIES) {
            throw new IOException("invalid bundle entry count " + count);
        }
        return count;
    }

    private static String readBundleHash(DataInputStream bundle) throws IOException {
        byte[] hashBytes = new byte[32];
        bundle.readFully(hashBytes);
        return hex(hashBytes);
    }

    private static int readRawLength(DataInputStream bundle) throws IOException {
        int rawLength = bundle.readInt();
        if (rawLength <= 0 || rawLength > MAX_RAW_SNAPSHOT_BYTES) {
            throw new IOException("invalid raw snapshot length " + rawLength);
        }
        return rawLength;
    }

    private static int readCompressedLength(DataInputStream bundle) throws IOException {
        int compressedLength = bundle.readInt();
        if (compressedLength <= 0 || compressedLength > MAX_COMPRESSED_SNAPSHOT_BYTES) {
            throw new IOException("invalid compressed snapshot length " + compressedLength);
        }
        return compressedLength;
    }

    private static byte[] readGzipBytes(byte[] compressed, int expectedLength) throws IOException {
        GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
        try {
            byte[] bytes = readAllBounded(gzip, MAX_RAW_SNAPSHOT_BYTES);
            if (expectedLength >= 0 && bytes.length != expectedLength) {
                throw new IOException("bundled snapshot length mismatch");
            }
            return bytes;
        } finally {
            gzip.close();
        }
    }

    private static byte[] applyForwardDelta(
            byte[] base, int rawLength, byte[] compressed) throws IOException {
        if (base == null) throw new IOException("delta has no base snapshot");
        DataInputStream delta = new DataInputStream(new GZIPInputStream(
                new ByteArrayInputStream(compressed)));
        try {
            int operationCount = delta.readInt();
            if (operationCount < 0 || operationCount > MAX_DELTA_OPERATIONS) {
                throw new IOException("invalid delta operation count " + operationCount);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream(rawLength);
            byte[] buffer = new byte[8192];
            for (int operation = 0; operation < operationCount; operation++) {
                int kind = delta.readUnsignedByte();
                if (kind == 0) {
                    int offset = delta.readInt();
                    int length = delta.readInt();
                    if (offset < 0 || length <= 0 || length > base.length
                            || offset > base.length - length) {
                        throw new IOException("invalid delta copy operation");
                    }
                    ensureDeltaOutputLength(output.size(), length, rawLength);
                    output.write(base, offset, length);
                } else if (kind == 1) {
                    int length = delta.readInt();
                    if (length <= 0) throw new IOException("invalid delta add length");
                    ensureDeltaOutputLength(output.size(), length, rawLength);
                    int remaining = length;
                    while (remaining > 0) {
                        int chunk = Math.min(remaining, buffer.length);
                        delta.readFully(buffer, 0, chunk);
                        output.write(buffer, 0, chunk);
                        remaining -= chunk;
                    }
                } else {
                    throw new IOException("unknown delta operation " + kind);
                }
            }
            if (output.size() != rawLength) {
                throw new IOException("delta output length mismatch");
            }
            if (delta.read() >= 0) throw new IOException("trailing delta data");
            return output.toByteArray();
        } finally {
            delta.close();
        }
    }

    private static void ensureDeltaOutputLength(int current, int added, int expected)
            throws IOException {
        if (added > expected - current) throw new IOException("delta output exceeds expected length");
    }

    private static void verifySnapshotHash(String expectedHash, byte[] bytes) throws IOException {
        if (!expectedHash.equalsIgnoreCase(sha256(bytes))) {
            throw new IOException("bundled snapshot hash mismatch " + expectedHash);
        }
    }

    private static void skipFully(InputStream in, int byteCount) throws IOException {
        int remaining = byteCount;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped > 0L) {
                remaining -= (int) skipped;
            } else {
                if (in.read() < 0) throw new IOException("truncated snapshot bundle");
                remaining--;
            }
        }
    }

    private static File snapshotFile(String hash) {
        return new File(historyDir, hash.toLowerCase(Locale.ROOT) + ".json.gz");
    }

    private static String normalizeHash(String hash) {
        if (hash == null) return null;
        String normalized = hash.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() != 64) return null;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) return null;
        }
        return normalized;
    }

    private static void writeAtomic(File target, byte[] bytes) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        File temp = new File(parent, target.getName() + ".tmp");
        OutputStream out = null;
        try {
            out = new FileOutputStream(temp);
            out.write(bytes);
            out.close();
            out = null;
            moveAtomic(temp, target);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
            if (temp.exists() && !temp.equals(target)) temp.delete();
        }
    }

    private static void moveAtomic(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String readUtf8(File file) throws IOException {
        return new String(readBytes(file), "UTF-8");
    }

    private static byte[] readBytes(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            return readAll(in);
        } finally {
            in.close();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        return out.toByteArray();
    }

    private static byte[] readAllBounded(InputStream in, int maximumBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) > 0) {
            if (read > maximumBytes - total) {
                throw new IOException("bundled snapshot exceeds maximum size");
            }
            out.write(buffer, 0, read);
            total += read;
        }
        return out.toByteArray();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >>> 4) & 0x0F, 16));
            out.append(Character.forDigit(b & 0x0F, 16));
        }
        return out.toString();
    }

    private static String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    static final class StoredData {
        final String json;
        final String hash;
        final long validFrom;

        StoredData(String json, String hash, long validFrom) {
            this.json = json;
            this.hash = hash;
            this.validFrom = validFrom;
        }
    }

    static final class HistoricalResolution {
        final boolean found;
        final List<String> englishThemes;

        private HistoricalResolution(boolean found, List<String> englishThemes) {
            this.found = found;
            this.englishThemes = englishThemes;
        }

        static HistoricalResolution found(List<String> themes) {
            return new HistoricalResolution(true,
                    themes == null ? Collections.<String>emptyList() : themes);
        }

        static HistoricalResolution notFound() {
            return new HistoricalResolution(false, Collections.<String>emptyList());
        }
    }

    private static final class HistoricalChoice {
        final String hash;
        final long validFrom;
        final SeedVersion seedVersion;

        private HistoricalChoice(String hash, long validFrom, SeedVersion seedVersion) {
            this.hash = hash;
            this.validFrom = validFrom;
            this.seedVersion = seedVersion;
        }

        static HistoricalChoice stored(String hash, long validFrom) {
            return new HistoricalChoice(hash, validFrom, null);
        }

        static HistoricalChoice seed(SeedVersion version) {
            return new HistoricalChoice(version.hash, version.validFrom, version);
        }
    }

    private static final class HistoryIndex {
        int schemaVersion = INDEX_SCHEMA;
        List<Interval> intervals = new ArrayList<Interval>();
        List<String> importedSources = new ArrayList<String>();
        List<ApprovalEvent> approvalEvents = new ArrayList<ApprovalEvent>();
        Map<String, Integer> snapshotOrdinals = new HashMap<String, Integer>();
        transient boolean needsWrite;

        void prepare() {
            if (intervals == null) intervals = new ArrayList<Interval>();
            if (importedSources == null) importedSources = new ArrayList<String>();
            if (approvalEvents == null) approvalEvents = new ArrayList<ApprovalEvent>();
            if (snapshotOrdinals == null) snapshotOrdinals = new HashMap<String, Integer>();
        }
    }

    private static final class Interval {
        String hash;
        long validFrom;
        long validUntil;
        long lastObservedAt;
        String source;

        boolean contains(long timestamp) {
            return timestamp >= validFrom && (validUntil <= 0L || timestamp < validUntil);
        }
    }

    private static final class SeedFile {
        int schemaVersion = SEED_SCHEMA;
        String currentHash = "";
        long currentValidFrom;
        ApprovalHistoryMetadata approvalHistory;
        List<SeedVersion> versions = new ArrayList<SeedVersion>();

        void prepare() {
            if (versions == null) versions = new ArrayList<SeedVersion>();
            for (int i = 0; i < versions.size(); i++) {
                SeedVersion version = versions.get(i);
                version.snapshotOrdinal = i;
                version.prepare();
            }
        }

        SeedVersion versionByHash(String hash) {
            for (SeedVersion version : versions) {
                if (hash.equalsIgnoreCase(version.hash)) return version;
            }
            return null;
        }
    }

    private static final class ApprovalHistoryMetadata {
        String resource;
        int schemaVersion;
        String sha256;
        int themeCount;
        int entryCount;
        int eventCount;
    }

    private static final class SeedVersion {
        String hash;
        long validFrom;
        long validUntil;
        long sourceTimestamp;
        String sourceCommit;
        boolean bundledSnapshot;
        transient int snapshotOrdinal;
        List<SeedOverride> overrides = new ArrayList<SeedOverride>();
        transient Map<String, SeedOverride> overrideIndex;

        void prepare() {
            if (overrides == null) overrides = new ArrayList<SeedOverride>();
            overrideIndex = new HashMap<String, SeedOverride>();
            for (SeedOverride override : overrides) {
                if (override == null) continue;
                if (override.englishThemes == null) {
                    override.englishThemes = new ArrayList<String>();
                }
                overrideIndex.put(historyKey(
                        override.languageCode, override.displayedTheme), override);
            }
        }

        boolean contains(long timestamp) {
            return timestamp >= validFrom && (validUntil <= 0L || timestamp < validUntil);
        }

        SeedOverride findOverride(String langCode, String displayedTheme) {
            if (overrideIndex == null) prepare();
            return overrideIndex.get(historyKey(langCode, displayedTheme));
        }
    }

    private static final class SeedOverride {
        String languageCode;
        String displayedTheme;
        List<String> englishThemes = new ArrayList<String>();
    }

    static synchronized String latestApproved(long themeId, String languageCode) {
        return latestApproved(themeId, languageCode, Integer.MAX_VALUE);
    }

    private static String latestApproved(long themeId, String languageCode, int maxSnapshotOrdinal) {
        if (canonicalEnglishTheme(themeId) == null) return null;
        ApprovalEvent event = approvalTimeline.latest(themeId, languageCode,
                Long.MAX_VALUE, maxSnapshotOrdinal);
        return event == null ? null : event.translation;
    }

    private static String canonicalEnglishTheme(long themeId) {
        return currentThemeUniverse.get(Long.valueOf(themeId));
    }

    private static String timelineKey(long themeId, String languageCode) {
        String language = TranslationData.normalizeLangCode(languageCode);
        if (language == null) language = languageCode;
        return themeId + "\u0000" + language;
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive()
                ? null : value.getAsString();
    }

    private static long number(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return 0L;
        try {
            return value.getAsLong();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static JsonObject object(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static final class ApprovalEvent {
        long themeId;
        String languageCode;
        String translation;
        String englishTheme;
        long approvedAt;
        int firstSnapshotOrdinal;
    }

    private static final class ApprovalTimeline {
        final Map<String, List<ApprovalEvent>> byThemeLanguage =
                new HashMap<String, List<ApprovalEvent>>();
        final Map<String, List<ApprovalEvent>> byDisplayed =
                new HashMap<String, List<ApprovalEvent>>();
        boolean complete;

        void addAll(List<ApprovalEvent> events) {
            if (events == null) return;
            for (ApprovalEvent event : events) add(event);
        }

        boolean add(ApprovalEvent event) {
            if (event == null || event.themeId <= 0L || event.approvedAt <= 0L
                    || event.languageCode == null || event.languageCode.isEmpty()
                    || event.translation == null || event.translation.isEmpty()
                    || event.englishTheme == null || event.englishTheme.isEmpty()) return false;
            String language = TranslationData.normalizeLangCode(event.languageCode);
            if (language == null || "en".equals(language) || "co".equals(language)) return false;
            event.languageCode = language;
            event.translation = Normalizer.normalize(event.translation, Normalizer.Form.NFC);
            event.englishTheme = Normalizer.normalize(event.englishTheme, Normalizer.Form.NFC);
            String key = timelineKey(event.themeId, language);
            List<ApprovalEvent> themeEvents = byThemeLanguage.get(key);
            if (themeEvents == null) {
                themeEvents = new ArrayList<ApprovalEvent>();
                byThemeLanguage.put(key, themeEvents);
            }
            for (ApprovalEvent existing : themeEvents) {
                if (existing.approvedAt == event.approvedAt
                        && existing.translation.equals(event.translation)) return false;
            }
            themeEvents.add(event);
            Collections.sort(themeEvents, new Comparator<ApprovalEvent>() {
                @Override
                public int compare(ApprovalEvent left, ApprovalEvent right) {
                    if (left.approvedAt != right.approvedAt) {
                        return left.approvedAt < right.approvedAt ? -1 : 1;
                    }
                    return left.firstSnapshotOrdinal < right.firstSnapshotOrdinal ? -1
                            : left.firstSnapshotOrdinal == right.firstSnapshotOrdinal ? 0 : 1;
                }
            });
            String displayedKey = historyKey(language, event.translation);
            List<ApprovalEvent> displayedEvents = byDisplayed.get(displayedKey);
            if (displayedEvents == null) {
                displayedEvents = new ArrayList<ApprovalEvent>();
                byDisplayed.put(displayedKey, displayedEvents);
            }
            displayedEvents.add(event);
            return true;
        }

        ApprovalEvent latest(long themeId, String languageCode, long cutoff,
                             int maxSnapshotOrdinal) {
            List<ApprovalEvent> events = byThemeLanguage.get(timelineKey(themeId, languageCode));
            if (events == null) return null;
            ApprovalEvent latest = null;
            for (ApprovalEvent event : events) {
                if (event.approvedAt > cutoff || event.firstSnapshotOrdinal > maxSnapshotOrdinal) {
                    continue;
                }
                latest = event;
            }
            return latest;
        }

        int eventCount() {
            int count = 0;
            for (List<ApprovalEvent> events : byThemeLanguage.values()) count += events.size();
            return count;
        }

        ApprovalResolution resolveAt(long cutoff, String languageCode, String displayedTheme,
                                     ScoreDelta builderDelta, int maxSnapshotOrdinal) {
            String language = TranslationData.normalizeLangCode(languageCode);
            if (language == null || "en".equals(language)) return ApprovalResolution.notFound();
            List<ApprovalEvent> candidates = byDisplayed.get(historyKey(language, displayedTheme));
            if (candidates == null) return ApprovalResolution.notFound();
            TreeSet<String> themes = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
            for (ApprovalEvent candidate : candidates) {
                ApprovalEvent latest = latest(candidate.themeId, language, cutoff,
                        maxSnapshotOrdinal);
                String english = canonicalEnglishTheme(candidate.themeId);
                if (latest == candidate && english != null) themes.add(english);
            }
            return ApprovalResolution.found(narrow(new ArrayList<String>(themes), builderDelta));
        }
    }

    private static final class ApprovalResolution {
        final boolean found;
        final List<String> englishThemes;

        private ApprovalResolution(boolean found, List<String> englishThemes) {
            this.found = found;
            this.englishThemes = englishThemes;
        }

        static ApprovalResolution found(List<String> themes) {
            return new ApprovalResolution(true, themes);
        }

        static ApprovalResolution notFound() {
            return new ApprovalResolution(false, Collections.<String>emptyList());
        }
    }

    private static String historyKey(String langCode, String displayedTheme) {
        String lang = TranslationData.normalizeLangCode(langCode);
        if (lang == null) lang = "en";
        String displayed = displayedTheme == null ? ""
                : Normalizer.normalize(displayedTheme.trim(), Normalizer.Form.NFC);
        if ("en".equals(lang)) displayed = displayed.toLowerCase(Locale.ROOT);
        return lang + '\u0000' + displayed;
    }

    private static final class HistoricalDataset {
        final Map<String, Map<String, Set<String>>> index =
                new HashMap<String, Map<String, Set<String>>>();
        final Map<String, String> englishThemes = new HashMap<String, String>();

        static HistoricalDataset parse(String json) {
            return parse(json, Integer.MAX_VALUE, false);
        }

        static HistoricalDataset parse(String json, int maxSnapshotOrdinal) {
            return parse(json, maxSnapshotOrdinal, true);
        }

        private static HistoricalDataset parse(String json, int maxSnapshotOrdinal,
                                               boolean currentThemeIdsOnly) {
            JsonElement root = new JsonParser().parse(json);
            if (!root.isJsonArray()) throw new IllegalArgumentException("translation root is not an array");
            HistoricalDataset out = new HistoricalDataset();
            if (currentThemeIdsOnly) {
                for (String currentEnglish : currentThemeUniverse.values()) {
                    out.englishThemes.put(currentEnglish.toLowerCase(Locale.ROOT), currentEnglish);
                }
            }
            JsonArray array = root.getAsJsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject entry = element.getAsJsonObject();
                long themeId = number(entry, "id");
                String english = string(entry, "theme");
                if (english == null || english.isEmpty()) continue;
                english = Normalizer.normalize(english, Normalizer.Form.NFC);
                if (currentThemeIdsOnly) {
                    english = canonicalEnglishTheme(themeId);
                    if (english == null) continue;
                } else {
                    out.englishThemes.put(english.toLowerCase(Locale.ROOT), english);
                }
                JsonObject translations = object(entry, "translations");
                if (translations == null) continue;
                for (Map.Entry<String, JsonElement> translation : translations.entrySet()) {
                    String lang = "en".equals(translation.getKey())
                            ? "pirate" : translation.getKey();
                    if ("co".equals(lang) || !translation.getValue().isJsonObject()) continue;
                    JsonObject body = translation.getValue().getAsJsonObject();
                    JsonElement approved = body.get("is_approved");
                    String displayed = string(body, "translation");
                    if (approved != null && approved.isJsonPrimitive()
                            && !approved.getAsBoolean()) {
                        displayed = latestApproved(themeId, lang, maxSnapshotOrdinal);
                    }
                    if (displayed == null || displayed.isEmpty()) continue;
                    displayed = Normalizer.normalize(displayed, Normalizer.Form.NFC);
                    Map<String, Set<String>> language = out.index.get(lang);
                    if (language == null) {
                        language = new HashMap<String, Set<String>>();
                        out.index.put(lang, language);
                    }
                    Set<String> origins = language.get(displayed);
                    if (origins == null) {
                        origins = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
                        language.put(displayed, origins);
                    }
                    origins.add(english);
                }
            }
            return out;
        }

        int englishThemeCount() {
            return englishThemes.size();
        }

        List<String> resolve(String langCode, String displayedTheme, ScoreDelta builderDelta) {
            String displayed = Normalizer.normalize(
                    displayedTheme.trim(), Normalizer.Form.NFC);
            String lang = TranslationData.normalizeLangCode(langCode);
            TreeSet<String> resolved = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
            if (lang == null || "en".equals(lang)) {
                String english = resolveEnglish(displayed);
                if (english != null) resolved.add(english);
            } else {
                Map<String, Set<String>> language = index.get(lang);
                if (language != null) {
                    Set<String> origins = language.get(displayed);
                    if (origins != null) resolved.addAll(origins);
                }
                if (resolved.isEmpty()) {
                    String english = resolveEnglish(displayed);
                    if (english != null) resolved.add(english);
                }
            }
            return narrow(new ArrayList<String>(resolved), builderDelta);
        }

        List<String> resolveEnglishOnly(String displayedTheme, ScoreDelta builderDelta) {
            String displayed = Normalizer.normalize(
                    displayedTheme.trim(), Normalizer.Form.NFC);
            String english = resolveEnglish(displayed);
            return english == null
                    ? Collections.<String>emptyList()
                    : narrow(Collections.singletonList(english), builderDelta);
        }

        private String resolveEnglish(String displayed) {
            String key = displayed.toLowerCase(Locale.ROOT);
            String exact = englishThemes.get(key);
            if (exact != null) return exact;
            String compact = compactSpaces(key);
            String best = null;
            for (String candidate : englishThemes.values()) {
                if (!compact.equals(compactSpaces(candidate.toLowerCase(Locale.ROOT)))) continue;
                if (best == null || candidate.length() < best.length()
                        || (candidate.length() == best.length()
                        && String.CASE_INSENSITIVE_ORDER.compare(candidate, best) < 0)) {
                    best = candidate;
                }
            }
            return best;
        }

        private static String compactSpaces(String value) {
            StringBuilder out = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (!Character.isWhitespace(c)) out.append(c);
            }
            return out.toString();
        }

        private static String string(JsonObject object, String name) {
            JsonElement value = object.get(name);
            return value == null || value.isJsonNull() || !value.isJsonPrimitive()
                    ? null : value.getAsString();
        }

        private static JsonObject object(JsonObject object, String name) {
            JsonElement value = object.get(name);
            return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
        }
    }
}
