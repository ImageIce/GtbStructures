package com.gtbstructures.font;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** Installs and selects the generated pack backing the classic font toggle. */
public final class ClassicFontResourcePack {
    private static final String MODE_CLASSIC = "classic";
    private static final String MODE_NEW = "new";
    private static final String PACK_FOLDER_NAME = "GTBS-Classic-1.8-Font";
    private static final String PACK_REPOSITORY_ID = "file/" + PACK_FOLDER_NAME;
    private static final String DEFAULT_FONT_ID = "minecraft:default";
    private static final String CLASSIC_FONT_ID = "gtbstructures:classic_default";
    private static final String RESOURCE_PREFIX = "/assets/gtbstructures/textures/font/classic_1_8/";
    private static final String TEXTURE_PREFIX = "gtbstructures:font/classic_1_8/";
    private static final String SOURCE_ASCII_TEXTURE = "ascii.png";
    private static final String ASCII_TEXTURE = "ascii_2x.png";
    private static final String UNICODE_4X_TEXTURE_PREFIX = "gtbstructures:font/classic_1_8_4x/";
    private static final String UNICODE_2X_TEXTURE_PREFIX = "gtbstructures:font/classic_1_8_2x/";
    private static final String GLYPH_SIZES_RESOURCE = RESOURCE_PREFIX + "glyph_sizes.bin";
    private static final String PACK_FORMAT_RESOURCE = "/gtbstructures-resource-pack.properties";
    private static final String CACHE_MANIFEST_NAME = ".gtbs-classic-font-cache";
    private static final int MAX_UNICODE_GENERATION_WORKERS = 4;
    private static final int[] PACK_FORMAT_RANGE = loadPackFormatRange();
    private static final int MIN_PACK_FORMAT = PACK_FORMAT_RANGE[0];
    private static final int MAX_PACK_FORMAT = PACK_FORMAT_RANGE[1];
    private static final String CACHE_GENERATOR_VERSION =
            "classic-font-runtime-v3-pack-format-" + MIN_PACK_FORMAT + "-" + MAX_PACK_FORMAT;
    private static final Map<Object, Object> ORIGINAL_DEFAULT_FONT_SETS =
            Collections.synchronizedMap(new WeakHashMap<Object, Object>());
    private static final Map<Class<?>, Field> FONT_MANAGER_FIELDS =
            Collections.synchronizedMap(new HashMap<Class<?>, Field>());
    private static final Map<Class<?>, Field> FONT_SET_FIELDS =
            Collections.synchronizedMap(new HashMap<Class<?>, Field>());
    private static final Map<Object, FontSetState> FONT_SET_STATES =
            Collections.synchronizedMap(new WeakHashMap<Object, FontSetState>());
    private static byte[] glyphSizes;

    private static final int[] LEGACY_ASCII_CODEPOINTS = new int[] {
            0x00C0, 0x00C1, 0x00C2, 0x00C8, 0x00CA, 0x00CB, 0x00CD, 0x00D3,
            0x00D4, 0x00D5, 0x00DA, 0x00DF, 0x00E3, 0x00F5, 0x011F, 0x0130,
            0x0131, 0x0152, 0x0153, 0x015E, 0x015F, 0x0174, 0x0175, 0x017E,
            0x0207, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
            0x0020, 0x0021, 0x0022, 0x0023, 0x0024, 0x0025, 0x0026, 0x0027,
            0x0028, 0x0029, 0x002A, 0x002B, 0x002C, 0x002D, 0x002E, 0x002F,
            0x0030, 0x0031, 0x0032, 0x0033, 0x0034, 0x0035, 0x0036, 0x0037,
            0x0038, 0x0039, 0x003A, 0x003B, 0x003C, 0x003D, 0x003E, 0x003F,
            0x0040, 0x0041, 0x0042, 0x0043, 0x0044, 0x0045, 0x0046, 0x0047,
            0x0048, 0x0049, 0x004A, 0x004B, 0x004C, 0x004D, 0x004E, 0x004F,
            0x0050, 0x0051, 0x0052, 0x0053, 0x0054, 0x0055, 0x0056, 0x0057,
            0x0058, 0x0059, 0x005A, 0x005B, 0x005C, 0x005D, 0x005E, 0x005F,
            0x0060, 0x0061, 0x0062, 0x0063, 0x0064, 0x0065, 0x0066, 0x0067,
            0x0068, 0x0069, 0x006A, 0x006B, 0x006C, 0x006D, 0x006E, 0x006F,
            0x0070, 0x0071, 0x0072, 0x0073, 0x0074, 0x0075, 0x0076, 0x0077,
            0x0078, 0x0079, 0x007A, 0x007B, 0x007C, 0x007D, 0x007E, 0x0000,
            0x00C7, 0x00FC, 0x00E9, 0x00E2, 0x00E4, 0x00E0, 0x00E5, 0x00E7,
            0x00EA, 0x00EB, 0x00E8, 0x00EF, 0x00EE, 0x00EC, 0x00C4, 0x00C5,
            0x00C9, 0x00E6, 0x00C6, 0x00F4, 0x00F6, 0x00F2, 0x00FB, 0x00F9,
            0x00FF, 0x00D6, 0x00DC, 0x00F8, 0x00A3, 0x00D8, 0x00D7, 0x0192,
            0x00E1, 0x00ED, 0x00F3, 0x00FA, 0x00F1, 0x00D1, 0x00AA, 0x00BA,
            0x00BF, 0x00AE, 0x00AC, 0x00BD, 0x00BC, 0x00A1, 0x00AB, 0x00BB,
            0x2591, 0x2592, 0x2593, 0x2502, 0x2524, 0x2561, 0x2562, 0x2556,
            0x2555, 0x2563, 0x2551, 0x2557, 0x255D, 0x255C, 0x255B, 0x2510,
            0x2514, 0x2534, 0x252C, 0x251C, 0x2500, 0x253C, 0x255E, 0x255F,
            0x255A, 0x2554, 0x2569, 0x2566, 0x2560, 0x2550, 0x256C, 0x2567,
            0x2568, 0x2564, 0x2565, 0x2559, 0x2558, 0x2552, 0x2553, 0x256B,
            0x256A, 0x2518, 0x250C, 0x2588, 0x2584, 0x258C, 0x2590, 0x2580,
            0x03B1, 0x03B2, 0x0393, 0x03C0, 0x03A3, 0x03C3, 0x00B5, 0x03C4,
            0x03A6, 0x0398, 0x03A9, 0x03B4, 0x221E, 0x2205, 0x2208, 0x2229,
            0x2261, 0x00B1, 0x2265, 0x2264, 0x2320, 0x2321, 0x00F7, 0x2248,
            0x00B0, 0x2219, 0x00B7, 0x221A, 0x207F, 0x00B2, 0x25A0, 0x0000
    };
    private static final String[] ASCII_ROWS = legacyAsciiRows();

    private ClassicFontResourcePack() {
    }

    public static String normalizeMode(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (MODE_CLASSIC.equals(value)) return MODE_CLASSIC;
        if (MODE_NEW.equals(value)) return MODE_NEW;
        return null;
    }

    public static String displayMode(String mode) {
        return MODE_CLASSIC.equals(normalizeMode(mode)) ? "Classic" : "New";
    }

    public static String apply(File gameDir, Object minecraft, String rawMode) {
        String mode = normalizeMode(rawMode);
        if (mode == null) mode = MODE_NEW;
        boolean classic = MODE_CLASSIC.equals(mode);
        try {
            ensurePackInstalled(gameDir);
            updateOptionsFile(gameDir, true);
            LiveApplyResult live = applyLive(minecraft, true, true);
            boolean switched = switchLoadedFontSet(minecraft, classic);
            updateOptionsFile(gameDir, true);
            if (classic) {
                if (live.reloaded) return "Classic 1.8 font support pack selected; resource reload requested.";
                if (switched) return "Classic 1.8 font enabled.";
                if (live.selected) return "Classic 1.8 font support pack selected.";
                return "Classic 1.8 font mode saved.";
            }
            if (switched) return "Classic 1.8 font disabled.";
            if (live.reloaded) return "Classic font support pack selected; resource reload requested.";
            return "Classic 1.8 font disabled in options.";
        } catch (IOException e) {
            return "Unable to update the classic font pack: " + e.getMessage();
        }
    }

    public static String applyWithoutReload(File gameDir, Object minecraft, String rawMode) {
        String mode = normalizeMode(rawMode);
        if (mode == null) mode = MODE_NEW;
        boolean classic = MODE_CLASSIC.equals(mode);
        try {
            boolean regenerated = ensurePackInstalled(gameDir);
            updateOptionsFile(gameDir, true);
            LiveApplyResult live = applyLive(minecraft, true, false);
            boolean switched = switchLoadedFontSet(minecraft, classic);
            updateFontOptions(minecraft);
            updateOptionsFile(gameDir, true);
            if (minecraft != null && (live.selected || regenerated)) {
                live.reloaded = reloadResources(minecraft);
            }
            if (classic) {
                if (live.reloaded) return "Classic 1.8 font enabled; resource reload requested.";
                if (switched) return "Classic 1.8 font enabled.";
                if (live.selected) return "Classic 1.8 font support pack selected.";
                return "Classic 1.8 font mode saved.";
            }
            if (switched) return "Classic 1.8 font disabled.";
            if (live.reloaded) return "Classic font support pack selected; resource reload requested.";
            return "Classic 1.8 font disabled in options.";
        } catch (IOException e) {
            return "Unable to update the classic font pack: " + e.getMessage();
        }
    }

    public static boolean refreshLoadedFontSet(Object minecraft, String rawMode) {
        String mode = normalizeMode(rawMode);
        if (mode == null) mode = MODE_NEW;
        boolean classic = MODE_CLASSIC.equals(mode);
        if (!classic && ORIGINAL_DEFAULT_FONT_SETS.isEmpty()) return false;
        boolean switched = switchLoadedFontSet(minecraft, classic);
        if (switched) updateFontOptions(minecraft);
        return switched;
    }

    public static String buildDefaultFontJsonForTest() {
        return buildDefaultFontJson();
    }

    public static int classicUnicodeBitmapProviderCountForTest() {
        return classicUnicode4xBitmapProviderCountForTest() + classicUnicode2xBitmapProviderCountForTest();
    }

    public static int classicUnicode4xBitmapProviderCountForTest() {
        int count = 0;
        for (int page = 0; page <= 255; page++) {
            if (unicodePageUses4x(page) && sourceUnicodePageExists(page)) count++;
        }
        return count;
    }

    public static int classicUnicode2xBitmapProviderCountForTest() {
        int count = 0;
        for (int page = 0; page <= 255; page++) {
            if (!unicodePageUses4x(page) && sourceUnicodePageExists(page)) count++;
        }
        return count;
    }

    public static int classicUnicodeSourcePageCountForTest() {
        int count = 0;
        for (int page = 0; page <= 255; page++) {
            if (hasResource(RESOURCE_PREFIX + unicodePageName(page))) count++;
        }
        return count;
    }

    public static int classicGlyphWidthForTest(int codePoint) {
        return classicGlyphWidth(codePoint);
    }

    public static int classicLegacyAsciiIndexForTest(int codePoint) {
        return legacyAsciiIndex(codePoint);
    }

    public static String packFolderNameForTest() {
        return PACK_FOLDER_NAME;
    }

    public static String classicFontIdForTest() {
        return CLASSIC_FONT_ID;
    }

    public static int minPackFormatForTest() {
        return MIN_PACK_FORMAT;
    }

    public static int maxPackFormatForTest() {
        return MAX_PACK_FORMAT;
    }

    public static boolean embeddedClassicFontDefinitionAvailableForTest() {
        InputStream in = ClassicFontResourcePack.class.getResourceAsStream(
                "/assets/gtbstructures/font/classic_default.json");
        if (in == null) return false;
        try {
            in.close();
        } catch (IOException ignored) {
            // Nothing to clean up beyond the stream.
        }
        return true;
    }

    /** Returns true when the runtime classic-font pack had to be regenerated. */
    public static boolean ensureRuntimePackGeneratedForTest(File gameDir) throws IOException {
        return ensurePackInstalled(gameDir);
    }

    private static final class LiveApplyResult {
        private boolean selected;
        private boolean reloaded;
    }

    private static final class FontSetState {
        private final Map<Object, Object> fontSets;
        private final Object defaultKey;
        private final Object classicKey;

        private FontSetState(Map<Object, Object> fontSets, Object defaultKey, Object classicKey) {
            this.fontSets = fontSets;
            this.defaultKey = defaultKey;
            this.classicKey = classicKey;
        }
    }

    private static synchronized boolean ensurePackInstalled(File gameDir) throws IOException {
        File root = gameDir == null ? new File(".") : gameDir;
        Path resourcePacks = new File(root, "resourcepacks").toPath();
        Files.createDirectories(resourcePacks);
        String sourceDigest = sourceDigest();
        Path pack = resourcePacks.resolve(PACK_FOLDER_NAME);
        if (isValidCachedPack(pack, sourceDigest)) return false;

        Path staging = resourcePacks.resolve(PACK_FOLDER_NAME + ".tmp-" + UUID.randomUUID());
        Path backup = resourcePacks.resolve(PACK_FOLDER_NAME + ".backup-" + UUID.randomUUID());
        boolean movedCurrent = false;
        try {
            writeGeneratedPack(staging);
            String payloadDigest = payloadDigest(staging);
            writeTextAtomically(staging.resolve(CACHE_MANIFEST_NAME), cacheManifest(sourceDigest, payloadDigest));
            if (!isValidCachedPack(staging, sourceDigest)) {
                throw new IOException("Generated classic font pack did not validate");
            }
            if (Files.exists(pack)) {
                moveAtomically(pack, backup);
                movedCurrent = true;
            }
            moveAtomically(staging, pack);
            if (movedCurrent) deleteRecursively(backup);
            return true;
        } catch (IOException e) {
            deleteRecursively(staging);
            if (movedCurrent && Files.exists(backup)) {
                deleteRecursively(pack);
                try {
                    if (!Files.exists(pack)) moveAtomically(backup, pack);
                } catch (IOException restoreFailure) {
                    e.addSuppressed(restoreFailure);
                }
            }
            throw e;
        }
    }

    private static String packMcmeta() {
        return "{\n"
                + "  \"pack\": {\n"
                + "    \"pack_format\": " + MAX_PACK_FORMAT + ",\n"
                + "    \"min_format\": " + MIN_PACK_FORMAT + ",\n"
                + "    \"max_format\": " + MAX_PACK_FORMAT + ",\n"
                + "    \"description\": \"GTBS Classic 1.8 Font\"\n"
                + "  }\n"
                + "}\n";
    }

    private static int[] loadPackFormatRange() {
        Properties properties = new Properties();
        try (InputStream in = ClassicFontResourcePack.class.getResourceAsStream(PACK_FORMAT_RESOURCE)) {
            if (in == null) return new int[] {69, 75};
            properties.load(in);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        try {
            int min = Integer.parseInt(properties.getProperty("min_format"));
            int max = Integer.parseInt(properties.getProperty("max_format"));
            if (min <= 0 || max < min) {
                throw new IllegalArgumentException("expected 0 < min_format <= max_format");
            }
            return new int[] {min, max};
        } catch (RuntimeException e) {
            throw new ExceptionInInitializerError(
                    "Invalid " + PACK_FORMAT_RESOURCE + ": " + e.getMessage());
        }
    }

    private static boolean isValidCachedPack(Path pack, String sourceDigest) throws IOException {
        if (!Files.isDirectory(pack)) return false;
        Path manifest = pack.resolve(CACHE_MANIFEST_NAME);
        if (!Files.isRegularFile(manifest)) return false;
        Map<String, String> values = readManifest(manifest);
        if (!CACHE_GENERATOR_VERSION.equals(values.get("generator"))
                || !sourceDigest.equals(values.get("source"))) return false;
        for (String relativePath : payloadPaths()) {
            Path payload = pack.resolve(relativePath);
            if (!Files.isRegularFile(payload) || Files.size(payload) == 0L) return false;
        }
        String expectedPayloadDigest = values.get("payload");
        return expectedPayloadDigest != null && expectedPayloadDigest.equals(payloadDigest(pack));
    }

    private static Map<String, String> readManifest(Path manifest) throws IOException {
        Map<String, String> values = new HashMap<String, String>();
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            int separator = line.indexOf('=');
            if (separator > 0) values.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return values;
    }

    private static String cacheManifest(String sourceDigest, String payloadDigest) {
        return "generator=" + CACHE_GENERATOR_VERSION + "\n"
                + "source=" + sourceDigest + "\n"
                + "payload=" + payloadDigest + "\n";
    }

    private static String sourceDigest() throws IOException {
        MessageDigest digest = sha256();
        updateDigest(digest, CACHE_GENERATOR_VERSION);
        updateDigest(digest, packMcmeta());
        updateDigest(digest, buildDefaultFontJson());
        updateDigest(digest, GLYPH_SIZES_RESOURCE);
        digest.update(readResourceBytes(GLYPH_SIZES_RESOURCE));
        updateDigest(digest, RESOURCE_PREFIX + SOURCE_ASCII_TEXTURE);
        digest.update(readResourceBytes(RESOURCE_PREFIX + SOURCE_ASCII_TEXTURE));
        for (int page = 0; page <= 255; page++) {
            String path = RESOURCE_PREFIX + unicodePageName(page);
            updateDigest(digest, path);
            if (hasResource(path)) digest.update(readResourceBytes(path));
        }
        return hex(digest.digest());
    }

    private static String payloadDigest(Path pack) throws IOException {
        MessageDigest digest = sha256();
        for (String relativePath : payloadPaths()) {
            Path payload = pack.resolve(relativePath);
            updateDigest(digest, relativePath);
            digest.update(Files.readAllBytes(payload));
        }
        return hex(digest.digest());
    }

    private static MessageDigest sha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    private static void updateDigest(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            int unsigned = value & 0xFF;
            if (unsigned < 16) out.append('0');
            out.append(Integer.toHexString(unsigned));
        }
        return out.toString();
    }

    private static List<String> payloadPaths() {
        ArrayList<String> paths = new ArrayList<String>();
        paths.add("pack.mcmeta");
        paths.add("assets/gtbstructures/font/classic_default.json");
        paths.add("assets/gtbstructures/textures/font/classic_1_8/" + ASCII_TEXTURE);
        for (int page = 0; page <= 255; page++) {
            if (!sourceUnicodePageExists(page)) continue;
            String directory = unicodePageUses4x(page) ? "classic_1_8_4x" : "classic_1_8_2x";
            paths.add("assets/gtbstructures/textures/font/" + directory + "/" + unicodePageName(page));
        }
        return paths;
    }

    private static void writeGeneratedPack(Path pack) throws IOException {
        writeTextAtomically(pack.resolve("pack.mcmeta"), packMcmeta());
        writeTextAtomically(pack.resolve("assets/gtbstructures/font/classic_default.json"), buildDefaultFontJson());
        writeNearestUpscale(readResourceImage(RESOURCE_PREFIX + SOURCE_ASCII_TEXTURE),
                pack.resolve("assets/gtbstructures/textures/font/classic_1_8/" + ASCII_TEXTURE), 2);
        writeUnicodePages(pack);
    }

    private static void writeUnicodePages(Path pack) throws IOException {
        int workers = Math.max(1, Math.min(MAX_UNICODE_GENERATION_WORKERS,
                Runtime.getRuntime().availableProcessors()));
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        ArrayList<Future<?>> futures = new ArrayList<Future<?>>();
        IOException failure = null;
        try {
            for (int page = 0; page <= 255; page++) {
                if (!sourceUnicodePageExists(page)) continue;
                final int unicodePage = page;
                final boolean use4x = unicodePageUses4x(page);
                futures.add(executor.submit(() -> {
                    writeUnicodePage(pack, unicodePage, use4x);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    cancelUnicodeGeneration(futures);
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while generating classic Unicode font pages", e);
                } catch (ExecutionException e) {
                    cancelUnicodeGeneration(futures);
                    Throwable cause = e.getCause();
                    if (cause instanceof IOException) throw (IOException) cause;
                    throw new IOException("Unable to generate classic Unicode font page", cause);
                }
            }
        } catch (IOException e) {
            failure = e;
            throw e;
        } finally {
            try {
                shutdownUnicodeGeneration(executor);
            } catch (IOException shutdownFailure) {
                if (failure != null) {
                    failure.addSuppressed(shutdownFailure);
                } else {
                    throw shutdownFailure;
                }
            }
        }
    }

    private static void writeUnicodePage(Path pack, int page, boolean use4x) throws IOException {
        BufferedImage source = readResourceImage(RESOURCE_PREFIX + unicodePageName(page));
        String directory = use4x ? "classic_1_8_4x" : "classic_1_8_2x";
        Path target = pack.resolve("assets/gtbstructures/textures/font/" + directory + "/" + unicodePageName(page));
        if (use4x) {
            writeUnicode4xPage(source, page, target);
        } else {
            writeUnicode2xPage(source, page, target);
        }
    }

    private static void cancelUnicodeGeneration(List<Future<?>> futures) {
        for (Future<?> future : futures) future.cancel(true);
    }

    private static void shutdownUnicodeGeneration(ExecutorService executor) throws IOException {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new IOException("Timed out while stopping classic Unicode font generation");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while stopping classic Unicode font generation", e);
        }
    }

    private static void writeNearestUpscale(BufferedImage source, Path target, int scale) throws IOException {
        BufferedImage out = new BufferedImage(source.getWidth() * scale, source.getHeight() * scale,
                BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                for (int yy = 0; yy < scale; yy++) {
                    for (int xx = 0; xx < scale; xx++) out.setRGB(x * scale + xx, y * scale + yy, argb);
                }
            }
        }
        writePngAtomically(out, target);
    }

    private static void writeUnicode4xPage(BufferedImage source, int page, Path target) throws IOException {
        BufferedImage out = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        byte[] sizes = glyphSizes();
        for (int index = 0; index < 256; index++) {
            int codePoint = (page << 8) + index;
            if (legacyAsciiIndex(codePoint) >= 0) continue;
            int packed = codePoint < sizes.length ? sizes[codePoint] & 0xFF : 0;
            if (packed == 0) continue;
            int left = packed >>> 4;
            int right = packed & 0x0F;
            if (right < left) continue;
            int width = right - left + 1;
            int sourceCellX = (index & 15) * 16;
            int sourceCellY = (index >>> 4) * 16;
            int targetCellX = (index & 15) * 64;
            int targetCellY = (index >>> 4) * 64;
            int narrowColumn = oddAdvanceNarrowColumn(source, sourceCellX, sourceCellY, left, right);
            for (int y = 0; y < 16; y++) {
                int targetX = targetCellX;
                for (int x = left; x <= right; x++) {
                    int argb = source.getRGB(sourceCellX + x, sourceCellY + y);
                    int copies = x == narrowColumn ? 3 : 4;
                    for (int yy = 0; yy < 4; yy++) {
                        for (int xx = 0; xx < copies; xx++) out.setRGB(targetX + xx, targetCellY + y * 4 + yy, argb);
                    }
                    targetX += copies;
                }
            }
            if (width == 16) out.setRGB(targetCellX + 63, targetCellY, 0x01FFFFFF);
        }
        writePngAtomically(out, target);
    }

    private static int oddAdvanceNarrowColumn(BufferedImage source, int sourceCellX, int sourceCellY,
            int left, int right) {
        int width = right - left + 1;
        if ((width & 1) == 0 || width <= 2) return -1;
        int best = left + 1;
        int bestInk = Integer.MAX_VALUE;
        for (int x = left + 1; x < right; x++) {
            int ink = 0;
            for (int y = 0; y < 16; y++) {
                if (((source.getRGB(sourceCellX + x, sourceCellY + y) >>> 24) & 0xFF) != 0) ink++;
            }
            if (ink < bestInk) {
                best = x;
                bestInk = ink;
            }
        }
        return best;
    }

    private static void writeUnicode2xPage(BufferedImage source, int page, Path target) throws IOException {
        BufferedImage out = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        byte[] sizes = glyphSizes();
        for (int index = 0; index < 256; index++) {
            int codePoint = (page << 8) + index;
            if (legacyAsciiIndex(codePoint) >= 0) continue;
            int packed = codePoint < sizes.length ? sizes[codePoint] & 0xFF : 0;
            if (packed == 0) continue;
            int left = packed >>> 4;
            int right = packed & 0x0F;
            if (right < left) continue;
            int width = right - left + 1;
            int sourceCellX = (index & 15) * 16;
            int sourceCellY = (index >>> 4) * 16;
            int targetCellX = (index & 15) * 32;
            int targetCellY = (index >>> 4) * 32;
            for (int y = 0; y < 16; y++) {
                int targetX = targetCellX;
                for (int x = left; x <= right; x++) {
                    int argb = source.getRGB(sourceCellX + x, sourceCellY + y);
                    for (int yy = 0; yy < 2; yy++) {
                        for (int xx = 0; xx < 2; xx++) out.setRGB(targetX + xx, targetCellY + y * 2 + yy, argb);
                    }
                    targetX += 2;
                }
            }
            if (width == 16) out.setRGB(targetCellX + 31, targetCellY, 0x01FFFFFF);
        }
        writePngAtomically(out, target);
    }

    private static BufferedImage readResourceImage(String path) throws IOException {
        try (InputStream in = ClassicFontResourcePack.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing classic font source resource: " + path);
            BufferedImage image = ImageIO.read(in);
            if (image == null) throw new IOException("Unreadable classic font source resource: " + path);
            return image;
        }
    }

    private static byte[] readResourceBytes(String path) throws IOException {
        try (InputStream in = ClassicFontResourcePack.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing classic font source resource: " + path);
            return in.readAllBytes();
        }
    }

    private static void writeTextAtomically(Path target, String contents) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            moveAtomically(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writePngAtomically(BufferedImage image, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            if (!ImageIO.write(image, "png", temporary.toFile())) {
                throw new IOException("No PNG writer is available for " + target);
            }
            moveAtomically(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteRecursively(Path path) {
        File file = path.toFile();
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child.toPath());
            }
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            path.toFile().deleteOnExit();
        }
    }

    private static String buildDefaultFontJson() {
        StringBuilder out = new StringBuilder(260000);
        out.append("{\n");
        out.append("  \"providers\": [\n");
        boolean[] first = new boolean[] {true};
        appendReferenceProvider(out, first, "minecraft:include/space", false);
        appendBitmapProvider(out, first, TEXTURE_PREFIX + ASCII_TEXTURE, ASCII_ROWS);
        for (int page = 0; page <= 255; page++) {
            String name = unicodePageName(page);
            if (unicodePageUses4x(page) && sourceUnicodePageExists(page)) {
                appendBitmapProvider(out, first, UNICODE_4X_TEXTURE_PREFIX + name, unicodePageRows(page));
            } else if (!unicodePageUses4x(page) && sourceUnicodePageExists(page)) {
                appendBitmapProvider(out, first, UNICODE_2X_TEXTURE_PREFIX + name, unicodePageRows(page));
            }
        }
        appendReferenceProvider(out, first, "minecraft:include/default", true);
        appendReferenceProvider(out, first, "minecraft:include/unifont", false);
        out.append("  ]\n");
        out.append("}\n");
        return out.toString();
    }

    private static void appendReferenceProvider(StringBuilder out, boolean[] first, String id, boolean uniformFalse) {
        beginProvider(out, first);
        out.append("    {\n");
        out.append("      \"type\": \"reference\",\n");
        out.append("      \"id\": \"").append(id).append("\"");
        if (uniformFalse) {
            out.append(",\n");
            out.append("      \"filter\": { \"uniform\": false }\n");
        } else {
            out.append('\n');
        }
        out.append("    }");
    }

    private static void appendBitmapProvider(StringBuilder out, boolean[] first, String file, String[] rows) {
        beginProvider(out, first);
        out.append("    {\n");
        out.append("      \"type\": \"bitmap\",\n");
        out.append("      \"file\": \"").append(file).append("\",\n");
        out.append("      \"height\": 8,\n");
        out.append("      \"ascent\": 7,\n");
        out.append("      \"filter\": { \"uniform\": false },\n");
        out.append("      \"chars\": [\n");
        for (int i = 0; i < rows.length; i++) {
            out.append("        \"");
            appendJsonEscaped(out, rows[i]);
            out.append('"');
            if (i + 1 < rows.length) out.append(',');
            out.append('\n');
        }
        out.append("      ]\n");
        out.append("    }");
    }

    private static void beginProvider(StringBuilder out, boolean[] first) {
        if (first[0]) {
            first[0] = false;
        } else {
            out.append(",\n");
        }
    }

    private static int classicGlyphWidth(int codePoint) {
        byte[] sizes = glyphSizes();
        if (codePoint < 0 || codePoint >= sizes.length) return 0;
        int packed = sizes[codePoint] & 0xFF;
        if (packed == 0) return 0;
        int left = packed >>> 4;
        int right = packed & 0x0F;
        if (right < left) return 0;
        return right - left + 1;
    }

    private static boolean unicodePageUses4x(int page) {
        for (int index = 0; index < 256; index++) {
            int codePoint = (page << 8) + index;
            if (legacyAsciiIndex(codePoint) >= 0) continue;
            int width = classicGlyphWidth(codePoint);
            if (width <= 8 && (width & 1) != 0) return true;
        }
        return false;
    }

    private static String unicodePageName(int page) {
        return "unicode_page_" + twoDigitsHex(page) + ".png";
    }

    private static String twoDigitsHex(int value) {
        String hex = Integer.toHexString(value & 0xFF);
        return hex.length() < 2 ? "0" + hex : hex;
    }

    private static String[] unicodePageRows(int page) {
        String[] rows = new String[16];
        byte[] sizes = glyphSizes();
        for (int row = 0; row < rows.length; row++) {
            StringBuilder line = new StringBuilder(16);
            for (int column = 0; column < 16; column++) {
                int codePoint = (page << 8) + row * 16 + column;
                int packed = codePoint < sizes.length ? sizes[codePoint] & 0xFF : 0;
                int left = packed >>> 4;
                int right = packed & 0x0F;
                if (packed != 0 && right >= left && legacyAsciiIndex(codePoint) < 0) {
                    line.append((char) codePoint);
                } else {
                    line.append('\0');
                }
            }
            rows[row] = line.toString();
        }
        return rows;
    }

    private static String[] legacyAsciiRows() {
        if (LEGACY_ASCII_CODEPOINTS.length != 256) {
            throw new IllegalStateException("Classic 1.8 ASCII remap must contain 256 cells");
        }
        String[] rows = new String[16];
        for (int row = 0; row < rows.length; row++) {
            StringBuilder line = new StringBuilder(16);
            for (int column = 0; column < 16; column++) {
                line.append((char) LEGACY_ASCII_CODEPOINTS[row * 16 + column]);
            }
            rows[row] = line.toString();
        }
        return rows;
    }

    private static int legacyAsciiIndex(int codePoint) {
        if (codePoint <= 0 || codePoint > Character.MAX_VALUE) return -1;
        for (int i = 0; i < LEGACY_ASCII_CODEPOINTS.length; i++) {
            if (LEGACY_ASCII_CODEPOINTS[i] == codePoint) return i;
        }
        return -1;
    }

    private static byte[] glyphSizes() {
        byte[] local = glyphSizes;
        if (local != null) return local;
        InputStream in = ClassicFontResourcePack.class.getResourceAsStream(GLYPH_SIZES_RESOURCE);
        if (in == null) {
            glyphSizes = new byte[65536];
            return glyphSizes;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(65536);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
            byte[] loaded = out.toByteArray();
            if (loaded.length >= 65536) {
                glyphSizes = loaded;
            } else {
                byte[] padded = new byte[65536];
                System.arraycopy(loaded, 0, padded, 0, loaded.length);
                glyphSizes = padded;
            }
            return glyphSizes;
        } catch (IOException ignored) {
            glyphSizes = new byte[65536];
            return glyphSizes;
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // Nothing to clean up beyond the stream.
            }
        }
    }

    private static boolean hasResource(String path) {
        InputStream in = ClassicFontResourcePack.class.getResourceAsStream(path);
        if (in == null) return false;
        try {
            in.close();
        } catch (IOException ignored) {
            // Nothing to clean up beyond the stream.
        }
        return true;
    }

    private static boolean sourceUnicodePageExists(int page) {
        return hasResource(RESOURCE_PREFIX + unicodePageName(page));
    }

    private static void appendJsonCodePoint(StringBuilder out, int codePoint) {
        appendJsonEscaped(out, String.valueOf((char) codePoint));
    }

    private static void appendJsonEscaped(StringBuilder out, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') {
                out.append("\\\\");
            } else if (c == '"') {
                out.append("\\\"");
            } else if (c < 0x20 || c > 0x7E) {
                out.append("\\u");
                String hex = Integer.toHexString(c);
                for (int j = hex.length(); j < 4; j++) out.append('0');
                out.append(hex);
            } else {
                out.append(c);
            }
        }
    }

    private static void updateOptionsFile(File gameDir, boolean classic) throws IOException {
        if (gameDir == null) return;
        File options = new File(gameDir, "options.txt");
        if (!options.isFile()) return;
        List<String> lines = Files.readAllLines(options.toPath(), StandardCharsets.UTF_8);
        boolean changed = false;
        changed |= updateOptionsLine(lines, "resourcePacks:", classic, true);
        changed |= updateOptionsLine(lines, "incompatibleResourcePacks:", false, false);
        if (changed) Files.write(options.toPath(), lines, StandardCharsets.UTF_8);
    }

    private static boolean updateOptionsLine(List<String> lines, String prefix, boolean classic, boolean insertWhenClassic) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.startsWith(prefix)) continue;
            List<String> packs = parseStringList(line.substring(prefix.length()));
            List<String> original = new ArrayList<String>(packs);
            removePackIds(packs);
            if (classic && insertWhenClassic) packs.add(0, PACK_REPOSITORY_ID);
            if (!packs.equals(original)) {
                lines.set(i, prefix + formatStringList(packs));
                return true;
            }
            return false;
        }
        if (classic && insertWhenClassic) {
            lines.add(prefix + formatStringList(singleton(PACK_REPOSITORY_ID)));
            return true;
        }
        return false;
    }

    private static List<String> singleton(String value) {
        ArrayList<String> values = new ArrayList<String>();
        values.add(value);
        return values;
    }

    private static List<String> parseStringList(String raw) {
        ArrayList<String> out = new ArrayList<String>();
        boolean inString = false;
        boolean escape = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (!inString) {
                if (c == '"') inString = true;
                continue;
            }
            if (escape) {
                current.append(c);
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                out.add(current.toString());
                current.setLength(0);
                inString = false;
            } else {
                current.append(c);
            }
        }
        return out;
    }

    private static String formatStringList(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(',');
            out.append('"');
            String value = values.get(i);
            for (int j = 0; j < value.length(); j++) {
                char c = value.charAt(j);
                if (c == '\\' || c == '"') out.append('\\');
                out.append(c);
            }
            out.append('"');
        }
        out.append(']');
        return out.toString();
    }

    private static LiveApplyResult applyLive(Object minecraft, boolean classic, boolean reloadLiveResources) {
        LiveApplyResult result = new LiveApplyResult();
        if (minecraft == null) return result;
        Object repository = null;
        try {
            repository = invokeNoArgAny(minecraft,
                    "getResourcePackRepository", "getResourcePackManager", "method_1520");
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // The options path below still handles clients where only options are reachable.
        }
        if (repository != null) result.selected |= updatePackRepository(repository, classic);
        result.selected |= updateLiveOptions(minecraft, repository, classic, reloadLiveResources);
        if (reloadLiveResources) {
            result.reloaded = reloadResources(minecraft);
        } else {
            updateFontOptions(minecraft);
        }
        return result;
    }

    public static boolean updateFontOptions(Object minecraft) {
        try {
            invokeNoArgAny(minecraft, "updateFontOptions", "method_53531", "k");
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean switchLoadedFontSet(Object minecraft, boolean classic) {
        try {
            Object fontManager = findFontManager(minecraft);
            if (fontManager == null) return false;
            FontSetState state = fontSetState(fontManager, classic);
            if (state == null) return false;
            Map<Object, Object> fontSets = state.fontSets;
            Object defaultKey = state.defaultKey;
            Object defaultSet = fontSets.get(defaultKey);
            if (classic) {
                Object classicKey = state.classicKey;
                if (classicKey == null) return false;
                Object classicSet = fontSets.get(classicKey);
                if (classicSet == null) return false;
                synchronized (ORIGINAL_DEFAULT_FONT_SETS) {
                    if (defaultSet != classicSet) {
                        ORIGINAL_DEFAULT_FONT_SETS.put(fontManager, defaultSet);
                    }
                }
                if (defaultSet == classicSet) return false;
                fontSets.put(defaultKey, classicSet);
                invalidateFontCaches(fontManager);
                return true;
            }
            Object original;
            synchronized (ORIGINAL_DEFAULT_FONT_SETS) {
                original = ORIGINAL_DEFAULT_FONT_SETS.remove(fontManager);
            }
            if (original == null || defaultSet == original) return false;
            fontSets.put(defaultKey, original);
            invalidateFontCaches(fontManager);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static Object findFontManager(Object minecraft) throws ReflectiveOperationException {
        if (minecraft == null) return null;
        Class<?> minecraftType = minecraft.getClass();
        Field field = FONT_MANAGER_FIELDS.get(minecraftType);
        if (field == null) {
            Field named = findNamedInstanceField(minecraftType, "fontManager", "f_91045_", "field_1771");
            if (named != null && isFontManagerCandidate(readFieldQuietly(named, minecraft))) field = named;
            if (field == null) field = discoverFontManagerField(minecraft);
            if (field == null) return null;
            FONT_MANAGER_FIELDS.put(minecraftType, field);
        }
        Object value = field.get(minecraft);
        if (value != null) return value;
        FONT_MANAGER_FIELDS.remove(minecraftType);
        return null;
    }

    private static Field discoverFontManagerField(Object minecraft) {
        for (Field field : instanceFields(minecraft.getClass())) {
            Object value = readFieldQuietly(field, minecraft);
            if (isFontManagerCandidate(value)) return field;
        }
        return null;
    }

    private static boolean isFontManagerCandidate(Object value) {
        if (value == null || value.getClass().getClassLoader() == null) return false;
        String name = value.getClass().getName();
        if (name.endsWith(".FontManager") || name.contains("FontManager")) return true;
        Field fontSets = discoverFontSetField(value, false);
        if (fontSets == null) return false;
        FONT_SET_FIELDS.put(value.getClass(), fontSets);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> fontSetMap(Object fontManager, boolean requireClassic)
            throws ReflectiveOperationException {
        Class<?> managerType = fontManager.getClass();
        Field field = FONT_SET_FIELDS.get(managerType);
        if (field == null) {
            Field named = findNamedInstanceField(managerType, "fontSets", "f_94999_", "field_2259", "h");
            if (named != null && isFontSetMap(readFieldQuietly(named, fontManager), requireClassic)) field = named;
            if (field == null) field = discoverFontSetField(fontManager, requireClassic);
            if (field == null) return null;
            FONT_SET_FIELDS.put(managerType, field);
        }
        Object value = field.get(fontManager);
        if (value instanceof Map<?, ?>) return (Map<Object, Object>) value;
        FONT_SET_FIELDS.remove(managerType);
        return null;
    }

    private static Field discoverFontSetField(Object fontManager, boolean requireClassic) {
        for (Field field : instanceFields(fontManager.getClass())) {
            if (isFontSetMap(readFieldQuietly(field, fontManager), requireClassic)) return field;
        }
        return null;
    }

    private static FontSetState fontSetState(Object fontManager, boolean requireClassic)
            throws ReflectiveOperationException {
        Map<Object, Object> fontSets = fontSetMap(fontManager, requireClassic);
        if (fontSets == null) return null;
        FontSetState cached = FONT_SET_STATES.get(fontManager);
        if (cached != null && cached.fontSets == fontSets
                && fontSets.containsKey(cached.defaultKey)
                && (!requireClassic || cached.classicKey != null && fontSets.containsKey(cached.classicKey))) {
            return cached;
        }
        Object defaultKey = fontSetKey(fontSets, DEFAULT_FONT_ID);
        if (defaultKey == null) return null;
        Object classicKey = fontSetKey(fontSets, CLASSIC_FONT_ID);
        if (requireClassic && classicKey == null) {
            FONT_SET_FIELDS.remove(fontManager.getClass());
            FONT_SET_STATES.remove(fontManager);
            fontSets = fontSetMap(fontManager, true);
            if (fontSets == null) return null;
            defaultKey = fontSetKey(fontSets, DEFAULT_FONT_ID);
            classicKey = fontSetKey(fontSets, CLASSIC_FONT_ID);
            if (defaultKey == null || classicKey == null) return null;
        }
        FontSetState resolved = new FontSetState(fontSets, defaultKey, classicKey);
        FONT_SET_STATES.put(fontManager, resolved);
        return !requireClassic || classicKey != null ? resolved : null;
    }

    private static boolean isFontSetMap(Object value, boolean requireClassic) {
        if (!(value instanceof Map<?, ?>)) return false;
        Map<?, ?> map = (Map<?, ?>) value;
        if (fontSetKey(map, DEFAULT_FONT_ID) == null) return false;
        return !requireClassic || fontSetKey(map, CLASSIC_FONT_ID) != null;
    }

    private static Object fontSetKey(Map<?, ?> fontSets, String id) {
        for (Object key : fontSets.keySet()) {
            if (id.equals(String.valueOf(key))) return key;
        }
        return null;
    }

    private static Field findNamedInstanceField(Class<?> type, String... names) {
        for (Field field : instanceFields(type)) {
            for (String name : names) {
                if (name.equals(field.getName())) return field;
            }
        }
        return null;
    }

    private static ArrayList<Field> instanceFields(Class<?> root) {
        ArrayList<Field> fields = new ArrayList<Field>();
        Class<?> type = root;
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    fields.add(field);
                } catch (RuntimeException ignored) {
                    // Inaccessible JDK internals cannot contain Minecraft font state.
                }
            }
            type = type.getSuperclass();
        }
        return fields;
    }

    private static Object readFieldQuietly(Field field, Object target) {
        try {
            return field.get(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static void invalidateFontCaches(Object fontManager) {
        for (Object value : fieldValues(fontManager)) {
            if (!isCachedFontProvider(value)) continue;
            try {
                invokeNoArgAny(value, "invalidate", "m_417583_", "method_72786", "close");
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // The next lookup still uses the swapped font set if this cache was already empty.
            }
        }
    }

    private static boolean isCachedFontProvider(Object value) {
        if (value == null) return false;
        String name = value.getClass().getName();
        return name.contains("CachedFontProvider")
                || name.contains("class_378$class_11638")
                || hasDeclaredField(value, "lastEntry", "whiteGlyph",
                        "f_411894_", "f_411721_", "field_61936", "field_61610")
                || hasNoArgMethod(value, "invalidate", "m_417583_", "method_72786");
    }

    private static boolean updateLiveOptions(
            Object minecraft, Object repository, boolean classic, boolean allowRepositorySync) {
        try {
            Object options = fieldAny(minecraft, "options", "field_1690");
            if (options == null) return false;
            boolean changed = false;
            boolean syncedFromRepository = allowRepositorySync && repository != null && invokeOneArgAny(options, repository,
                    "updateResourcePacks", "refreshResourcePacks", "method_49598");
            if (!syncedFromRepository) {
                changed |= updateListField(options, classic, true,
                        "resourcePacks", "resourcePacksList", "field_1887", "field_1825");
                changed |= updateListField(options, false, false,
                        "incompatibleResourcePacks", "incompatibleResourcePacksList", "field_1846", "field_1839");
            }
            if (changed || syncedFromRepository) invokeNoArgAny(options, "save", "method_1640");
            return changed || syncedFromRepository;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean updateListField(Object target, boolean classic, boolean insertWhenClassic, String... names)
            throws ReflectiveOperationException {
        Object value = fieldAny(target, names);
        if (!(value instanceof List<?>)) return false;
        List<String> list = (List<String>) value;
        ArrayList<String> before = new ArrayList<String>(list);
        removePackIds(list);
        if (classic && insertWhenClassic) list.add(0, PACK_REPOSITORY_ID);
        return !before.equals(list);
    }

    private static boolean updatePackRepository(Object repository, boolean classic) {
        try {
            tryInvokeNoArgAny(repository, "reload", "scanPacks", "method_14445", "method_14453");
            Collection<String> selected = selectedPackIds(repository);
            boolean changed = false;
            String discoveredPackId = discoverPackId(repository);
            if (classic) {
                changed |= invokeStringAny(repository, discoveredPackId,
                    "addPack", "enable", "enablePack", "method_49427", "method_14457");
            } else {
                for (String packId : packIdCandidates(repository)) {
                    changed |= invokeStringAny(repository, packId,
                            "removePack", "disable", "disablePack", "method_49428", "method_14456");
                }
            }
            if (selected == null) return changed;
            ArrayList<String> next = new ArrayList<String>(selected);
            removePackIds(next);
            if (classic) next.add(0, discoveredPackId);
            if (!next.equals(new ArrayList<String>(selected))) {
                changed |= invokeCollectionAny(repository, next,
                        "setSelected", "setSelectedIds", "setEnabled", "setEnabledProfiles",
                        "method_14447", "method_14458");
            }
            return changed;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static Collection<String> selectedPackIds(Object repository) throws ReflectiveOperationException {
        Object selected = tryInvokeNoArgAny(repository,
                "getSelectedIds", "getSelectedPackIds", "getEnabledIds", "method_29210", "method_14452");
        if (selected instanceof Collection<?>) {
            ArrayList<String> out = new ArrayList<String>();
            for (Object value : (Collection<?>) selected) {
                if (value != null) out.add(value.toString());
            }
            return out;
        }
        return null;
    }

    private static String discoverPackId(Object repository) throws ReflectiveOperationException {
        Object ids = tryInvokeNoArgAny(repository,
                "getAvailableIds", "getAvailablePackIds", "getIds", "method_29206", "method_14451");
        if (ids instanceof Iterable<?>) {
            for (Object id : (Iterable<?>) ids) {
                String packId = packIdFrom(id);
                if (packId != null) return packId;
            }
        }
        Object packs = tryInvokeNoArgAny(repository,
                "getAvailablePacks", "getProfiles", "getAllPacks", "method_14441");
        if (packs instanceof Iterable<?>) {
            for (Object pack : (Iterable<?>) packs) {
                String packId = packIdFrom(pack);
                if (packId != null) return packId;
            }
        }
        return PACK_REPOSITORY_ID;
    }

    private static boolean reloadResources(Object minecraft) {
        try {
            invokeNoArgAny(minecraft,
                    "reloadResourcePacks", "reloadResources", "method_1521", "method_1509");
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static LinkedHashSet<String> packIdCandidates(Object repository) throws ReflectiveOperationException {
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        out.add(PACK_FOLDER_NAME);
        out.add(PACK_REPOSITORY_ID);
        out.add(discoverPackId(repository));
        return out;
    }

    private static String packIdFrom(Object value) {
        if (value == null) return null;
        String direct = String.valueOf(value);
        if (isPackId(direct)) return direct;
        try {
            Object id = tryInvokeNoArgAny(value, "getId", "id", "getName", "name");
            if (id != null && isPackId(String.valueOf(id))) return String.valueOf(id);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall back to the default repository id below.
        }
        return null;
    }

    private static void removePackIds(List<String> packs) {
        for (int i = packs.size() - 1; i >= 0; i--) {
            if (isPackId(packs.get(i))) packs.remove(i);
        }
    }

    private static boolean isPackId(String value) {
        if (value == null) return false;
        String normalized = value.replace('\\', '/');
        return PACK_FOLDER_NAME.equals(normalized)
                || PACK_REPOSITORY_ID.equals(normalized)
                || normalized.endsWith("/" + PACK_FOLDER_NAME);
    }

    private static ArrayList<Object> fieldValues(Object target) {
        ArrayList<Object> out = new ArrayList<Object>();
        if (target == null) return out;
        Class<?> type = target.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value != null && !out.contains(value)) out.add(value);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Try other fields.
                }
            }
            type = type.getSuperclass();
        }
        return out;
    }

    private static boolean hasDeclaredField(Object target, String... names) {
        if (target == null) return false;
        Class<?> type = target.getClass();
        while (type != null) {
            for (String name : names) {
                try {
                    type.getDeclaredField(name);
                    return true;
                } catch (NoSuchFieldException ignored) {
                    // Try next name or superclass.
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean hasNoArgMethod(Object target, String... names) {
        if (target == null) return false;
        Class<?> type = target.getClass();
        while (type != null) {
            for (String name : names) {
                try {
                    type.getDeclaredMethod(name);
                    return true;
                } catch (NoSuchMethodException ignored) {
                    // Try next name or superclass.
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static Object fieldAny(Object target, String... names) throws ReflectiveOperationException {
        if (target == null) return null;
        Class<?> type = target.getClass();
        while (type != null) {
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                    // Try next name or superclass.
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Object invokeNoArgAny(Object target, String... names) throws ReflectiveOperationException {
        Object value = tryInvokeNoArgAny(target, names);
        if (value != null) return value;
        throw new NoSuchMethodException(target == null ? "null" : target.getClass().getName());
    }

    private static Object tryInvokeNoArgAny(Object target, String... names) throws ReflectiveOperationException {
        if (target == null) return null;
        Class<?> type = target instanceof Class<?> ? (Class<?>) target : target.getClass();
        while (type != null) {
            for (String name : names) {
                try {
                    Method method = type.getDeclaredMethod(name);
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (NoSuchMethodException ignored) {
                    // Try next name or superclass.
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean invokeCollectionAny(Object target, Collection<String> value, String... names)
            throws ReflectiveOperationException {
        Class<?> type = target.getClass();
        while (type != null) {
            Method[] methods = type.getDeclaredMethods();
            for (Method method : methods) {
                if (!matches(method.getName(), names) || method.getParameterTypes().length != 1) continue;
                Class<?> parameter = method.getParameterTypes()[0];
                if (!Collection.class.isAssignableFrom(parameter) && !Iterable.class.isAssignableFrom(parameter)) {
                    continue;
                }
                method.setAccessible(true);
                method.invoke(target, value);
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean invokeOneArgAny(Object target, Object value, String... names)
            throws ReflectiveOperationException {
        if (target == null || value == null) return false;
        Class<?> type = target.getClass();
        Class<?> valueType = value.getClass();
        while (type != null) {
            Method[] methods = type.getDeclaredMethods();
            for (Method method : methods) {
                if (!matches(method.getName(), names) || method.getParameterTypes().length != 1) continue;
                if (!method.getParameterTypes()[0].isAssignableFrom(valueType)) continue;
                method.setAccessible(true);
                method.invoke(target, value);
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean invokeStringAny(Object target, String value, String... names)
            throws ReflectiveOperationException {
        if (target == null || value == null) return false;
        Class<?> type = target.getClass();
        while (type != null) {
            Method[] methods = type.getDeclaredMethods();
            for (Method method : methods) {
                if (!matches(method.getName(), names) || method.getParameterTypes().length != 1) continue;
                if (!String.class.isAssignableFrom(method.getParameterTypes()[0])) continue;
                method.setAccessible(true);
                Object result = method.invoke(target, value);
                if (!(result instanceof Boolean) || Boolean.TRUE.equals(result)) return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean matches(String value, String... options) {
        for (String option : options) {
            if (option.equals(value)) return true;
        }
        return false;
    }
}
