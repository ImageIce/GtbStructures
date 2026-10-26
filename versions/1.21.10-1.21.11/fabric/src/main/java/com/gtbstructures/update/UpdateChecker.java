package com.gtbstructures.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Performs the single update lookup allowed during a Minecraft startup. */
public final class UpdateChecker {
    public static final UpdateChecker INSTANCE = new UpdateChecker();
    public static final String CURRENT_VERSION = "1.2.0";
    public static final String MODRINTH_URL = "https://modrinth.com/mod/gtbstructures/versions";

    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/ImageIce/GtbStructures/releases/latest";
    private static final String GITHUB_RELEASE_TAG_URL =
            "https://github.com/ImageIce/GtbStructures/releases/tag/";
    private static final String USER_AGENT =
            "GtbStructures/" + CURRENT_VERSION + " (+https://github.com/ImageIce/GtbStructures)";
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 4000;
    private static final int MAX_RESPONSE_CHARS = 64 * 1024;
    private static final Pattern SEMANTIC_VERSION = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?"
                    + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");

    private final ReleaseFetcher fetcher;
    private final Executor executor;
    private boolean checkRequested;

    private UpdateChecker() {
        this(new GitHubReleaseFetcher(), UpdateChecker::startDaemonThread);
    }

    UpdateChecker(ReleaseFetcher fetcher, Executor executor) {
        this.fetcher = fetcher;
        this.executor = executor;
    }

    /**
     * Starts the lookup unless one was already requested during this startup.
     * The once gate is consumed when the request is accepted, regardless of its result.
     */
    public boolean checkOnceAsync(Listener listener) {
        if (listener == null) return false;
        synchronized (this) {
            if (checkRequested) return false;
            checkRequested = true;
        }

        try {
            executor.execute(() -> {
                try {
                    UpdateInfo info = parseLatestRelease(fetcher.fetch(), CURRENT_VERSION);
                    if (info != null) listener.onUpdateAvailable(info);
                } catch (Exception ignored) {
                    // Update checks are advisory and intentionally silent on every failure path.
                }
            });
        } catch (RuntimeException ignored) {
            // Scheduling failure is also silent; the request still consumed this startup's gate.
        }
        return true;
    }

    static UpdateInfo parseLatestRelease(String json, String currentVersion) {
        if (json == null || currentVersion == null) return null;
        JsonObject release;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return null;
            release = parsed.getAsJsonObject();
        } catch (RuntimeException ignored) {
            String truncatedTag = tagBeforeTruncatedJsonString(json);
            return updateInfo(truncatedTag, currentVersion, json);
        }
        String tag = stringField(release, "tag_name");
        return updateInfo(tag, currentVersion, json);
    }

    private static UpdateInfo updateInfo(String tag, String currentVersion, String json) {
        String latestVersion = normalizeVersion(tag);
        if (latestVersion == null || compareVersions(latestVersion, currentVersion) <= 0) return null;
        return new UpdateInfo(latestVersion, GITHUB_RELEASE_TAG_URL + tag.trim(), MODRINTH_URL,
                ReleaseChangelog.fromReleaseJson(json));
    }

    /** Preserve the validated tag if the response was cut off inside its release-body string. */
    private static String tagBeforeTruncatedJsonString(String json) {
        if (json == null || json.indexOf("\"body\"") < 0 || !endsInsideJsonString(json)) return null;
        int key = json.indexOf("\"tag_name\"");
        if (key < 0) return null;
        int colon = json.indexOf(':', key + "\"tag_name\"".length());
        if (colon < 0) return null;
        int value = skipWhitespace(json, colon + 1);
        if (value >= json.length() || json.charAt(value) != '"') return null;
        return decodeJsonString(json, value + 1);
    }

    private static boolean endsInsideJsonString(String json) {
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (inString && c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inString = !inString;
            }
        }
        return inString;
    }

    private static int skipWhitespace(String value, int cursor) {
        while (cursor < value.length() && Character.isWhitespace(value.charAt(cursor))) cursor++;
        return cursor;
    }

    private static String decodeJsonString(String json, int cursor) {
        StringBuilder out = new StringBuilder();
        while (cursor < json.length()) {
            char c = json.charAt(cursor++);
            if (c == '"') return out.toString();
            if (c < 0x20) return null;
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (cursor >= json.length()) return null;
            char escaped = json.charAt(cursor++);
            if (escaped == '"' || escaped == '\\' || escaped == '/') out.append(escaped);
            else if (escaped == 'b') out.append('\b');
            else if (escaped == 'f') out.append('\f');
            else if (escaped == 'n') out.append('\n');
            else if (escaped == 'r') out.append('\r');
            else if (escaped == 't') out.append('\t');
            else if (escaped == 'u') {
                if (cursor + 4 > json.length()) return null;
                int codePoint = 0;
                for (int i = 0; i < 4; i++) {
                    int digit = Character.digit(json.charAt(cursor++), 16);
                    if (digit < 0) return null;
                    codePoint = (codePoint << 4) | digit;
                }
                out.append((char) codePoint);
            } else {
                return null;
            }
        }
        return null;
    }

    static int compareVersions(String left, String right) {
        SemanticVersion a = SemanticVersion.parse(left);
        SemanticVersion b = SemanticVersion.parse(right);
        if (a == null || b == null) throw new IllegalArgumentException("Malformed semantic version");
        return a.compareTo(b);
    }

    private static String normalizeVersion(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        return SemanticVersion.parse(normalized) == null ? null : normalized;
    }

    private static String stringField(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                ? null : value.getAsString();
    }

    private static void startDaemonThread(Runnable task) {
        Thread thread = new Thread(task, "GTBS Update Checker");
        thread.setDaemon(true);
        thread.start();
    }

    public interface Listener {
        void onUpdateAvailable(UpdateInfo info);
    }

    public static final class UpdateInfo {
        private final String version;
        private final String githubUrl;
        private final String modrinthUrl;
        private final java.util.List<String> changelogLines;

        private UpdateInfo(String version, String githubUrl, String modrinthUrl) {
            this(version, githubUrl, modrinthUrl, java.util.Collections.emptyList());
        }

        private UpdateInfo(String version, String githubUrl, String modrinthUrl,
                           java.util.List<String> changelogLines) {
            this.version = version;
            this.githubUrl = githubUrl;
            this.modrinthUrl = modrinthUrl;
            this.changelogLines = java.util.Collections.unmodifiableList(
                    new java.util.ArrayList<String>(changelogLines));
        }

        public String version() {
            return version;
        }

        public String githubUrl() {
            return githubUrl;
        }

        public String modrinthUrl() {
            return modrinthUrl;
        }

        public java.util.List<String> changelogLines() {
            return changelogLines;
        }
    }

    interface ReleaseFetcher {
        String fetch() throws Exception;
    }

    private static final class GitHubReleaseFetcher implements ReleaseFetcher {
        @Override
        public String fetch() throws IOException {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(LATEST_RELEASE_API).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException("GitHub update endpoint returned " + connection.getResponseCode());
                }
                return readCapped(connection.getInputStream());
            } finally {
                if (connection != null) connection.disconnect();
            }
        }

        private static String readCapped(InputStream input) throws IOException {
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                char[] buffer = new char[2048];
                int read;
                while ((read = reader.read(buffer)) >= 0) {
                    if (out.length() + read > MAX_RESPONSE_CHARS) {
                        throw new IOException("GitHub update response is too large");
                    }
                    out.append(buffer, 0, read);
                }
            }
            return out.toString();
        }
    }

    private static final class SemanticVersion implements Comparable<SemanticVersion> {
        private final BigInteger major;
        private final BigInteger minor;
        private final BigInteger patch;
        private final String[] prerelease;

        private SemanticVersion(BigInteger major, BigInteger minor, BigInteger patch,
                                String[] prerelease) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.prerelease = prerelease;
        }

        private static SemanticVersion parse(String raw) {
            if (raw == null) return null;
            String normalized = raw.trim();
            if (normalized.startsWith("v") || normalized.startsWith("V")) {
                normalized = normalized.substring(1);
            }
            Matcher matcher = SEMANTIC_VERSION.matcher(normalized);
            if (!matcher.matches()) return null;
            String[] prerelease = matcher.group(4) == null ? null : matcher.group(4).split("\\.");
            if (prerelease != null) {
                for (String identifier : prerelease) {
                    if (isNumeric(identifier) && identifier.length() > 1 && identifier.charAt(0) == '0') {
                        return null;
                    }
                }
            }
            return new SemanticVersion(new BigInteger(matcher.group(1)),
                    new BigInteger(matcher.group(2)), new BigInteger(matcher.group(3)), prerelease);
        }

        @Override
        public int compareTo(SemanticVersion other) {
            int compared = major.compareTo(other.major);
            if (compared != 0) return compared;
            compared = minor.compareTo(other.minor);
            if (compared != 0) return compared;
            compared = patch.compareTo(other.patch);
            if (compared != 0) return compared;
            if (prerelease == null) return other.prerelease == null ? 0 : 1;
            if (other.prerelease == null) return -1;
            int shared = Math.min(prerelease.length, other.prerelease.length);
            for (int i = 0; i < shared; i++) {
                compared = compareIdentifier(prerelease[i], other.prerelease[i]);
                if (compared != 0) return compared;
            }
            return Integer.compare(prerelease.length, other.prerelease.length);
        }

        private static int compareIdentifier(String left, String right) {
            boolean leftNumeric = isNumeric(left);
            boolean rightNumeric = isNumeric(right);
            if (leftNumeric && rightNumeric) {
                return new BigInteger(left).compareTo(new BigInteger(right));
            }
            if (leftNumeric != rightNumeric) return leftNumeric ? -1 : 1;
            return left.compareTo(right);
        }

        private static boolean isNumeric(String value) {
            if (value.isEmpty()) return false;
            for (int i = 0; i < value.length(); i++) {
                if (!Character.isDigit(value.charAt(i))) return false;
            }
            return true;
        }
    }
}
