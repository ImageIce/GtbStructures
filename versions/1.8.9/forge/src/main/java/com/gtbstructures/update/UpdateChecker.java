package com.gtbstructures.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/** Performs the session's single, best-effort GitHub release check off the client thread. */
public final class UpdateChecker {
    public static final UpdateChecker INSTANCE = new UpdateChecker(new GitHubReleaseSource());

    public static final String CURRENT_VERSION = "1.1.1";
    public static final String GITHUB_LATEST_API =
            "https://api.github.com/repos/ImageIce/GtbStructures/releases/latest";
    public static final String GITHUB_RELEASE_TAG_BASE =
            "https://github.com/ImageIce/GtbStructures/releases/tag/";
    public static final String MODRINTH_URL =
            "https://modrinth.com/mod/gtbstructures/versions";

    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 4000;
    private static final int MAX_RESPONSE_CHARS = 128 * 1024;

    private final Source source;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private UpdateChecker(Source source) {
        this.source = source;
    }

    /**
     * Starts at most one check for this checker instance. The listener runs on the worker thread.
     * Returns false when a check has already started or no listener was supplied.
     */
    public boolean checkOnceAsync(final Listener listener) {
        if (listener == null || !started.compareAndSet(false, true)) return false;

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    UpdateInfo update = source.fetchLatest();
                    if (update != null) listener.onUpdateAvailable(update);
                } catch (Exception ignored) {
                    // Update checks are advisory and must stay silent when GitHub is unavailable.
                }
            }
        }, "GTBS Update Check");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    static UpdateChecker forTesting(Source source) {
        if (source == null) throw new IllegalArgumentException("source");
        return new UpdateChecker(source);
    }

    static UpdateInfo parseLatestRelease(String json) {
        String tag = jsonStringField(json, "tag_name");
        if (!isNewerVersion(tag, CURRENT_VERSION)) return null;

        String displayVersion = normalizeVersionTag(tag);
        return new UpdateInfo(displayVersion, githubReleaseUrl(tag), MODRINTH_URL);
    }

    static boolean isNewerVersion(String candidate, String current) {
        ParsedVersion candidateVersion = ParsedVersion.parse(candidate);
        ParsedVersion currentVersion = ParsedVersion.parse(current);
        return candidateVersion != null && currentVersion != null
                && candidateVersion.compareTo(currentVersion) > 0;
    }

    static String githubReleaseUrl(String validatedTag) {
        return GITHUB_RELEASE_TAG_BASE + validatedTag.trim();
    }

    private static String normalizeVersionTag(String raw) {
        String normalized = raw == null ? "" : raw.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static String jsonStringField(String json, String field) {
        if (json == null || field == null) return null;
        String quotedField = "\"" + field + "\"";
        int searchFrom = 0;
        while (searchFrom < json.length()) {
            int keyAt = json.indexOf(quotedField, searchFrom);
            if (keyAt < 0) return null;
            int cursor = skipWhitespace(json, keyAt + quotedField.length());
            if (cursor >= json.length() || json.charAt(cursor) != ':') {
                searchFrom = keyAt + quotedField.length();
                continue;
            }
            cursor = skipWhitespace(json, cursor + 1);
            if (cursor >= json.length() || json.charAt(cursor) != '"') return null;
            return decodeJsonString(json, cursor + 1);
        }
        return null;
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

    public interface Listener {
        void onUpdateAvailable(UpdateInfo update);
    }

    interface Source {
        UpdateInfo fetchLatest() throws Exception;
    }

    public static final class UpdateInfo {
        private final String version;
        private final String githubUrl;
        private final String modrinthUrl;

        public UpdateInfo(String version, String githubUrl, String modrinthUrl) {
            this.version = version;
            this.githubUrl = githubUrl;
            this.modrinthUrl = modrinthUrl;
        }

        public String getVersion() {
            return version;
        }

        public String getGithubUrl() {
            return githubUrl;
        }

        public String getModrinthUrl() {
            return modrinthUrl;
        }
    }

    private static final class GitHubReleaseSource implements Source {
        @Override
        public UpdateInfo fetchLatest() throws Exception {
            HttpURLConnection connection = null;
            InputStream input = null;
            try {
                connection = (HttpURLConnection) new URL(GITHUB_LATEST_API).openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
                connection.setRequestProperty("User-Agent",
                        "GtbStructures/" + CURRENT_VERSION + " (+https://github.com/ImageIce/GtbStructures)");
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
                int contentLength = connection.getContentLength();
                if (contentLength > MAX_RESPONSE_CHARS) return null;
                input = connection.getInputStream();
                return parseLatestRelease(readLimited(input));
            } finally {
                if (input != null) {
                    try {
                        input.close();
                    } catch (IOException ignored) {
                    }
                }
                if (connection != null) connection.disconnect();
            }
        }

        private static String readLimited(InputStream input) throws IOException {
            InputStreamReader reader = new InputStreamReader(input, "UTF-8");
            StringBuilder out = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                if (out.length() + read > MAX_RESPONSE_CHARS) {
                    throw new IOException("GitHub update response exceeded limit");
                }
                out.append(buffer, 0, read);
            }
            return out.toString();
        }
    }

    private static final class ParsedVersion implements Comparable<ParsedVersion> {
        private final BigInteger[] core;
        private final String[] preRelease;

        private ParsedVersion(BigInteger[] core, String[] preRelease) {
            this.core = core;
            this.preRelease = preRelease;
        }

        static ParsedVersion parse(String raw) {
            String normalized = normalizeVersionTag(raw);
            if (normalized.isEmpty()) return null;
            int plus = normalized.indexOf('+');
            String withoutBuild = plus < 0 ? normalized : normalized.substring(0, plus);
            if (plus >= 0 && !validIdentifiers(normalized.substring(plus + 1))) return null;
            int dash = withoutBuild.indexOf('-');
            String coreText = dash < 0 ? withoutBuild : withoutBuild.substring(0, dash);
            String preText = dash < 0 ? null : withoutBuild.substring(dash + 1);
            if (preText != null && !validIdentifiers(preText)) return null;

            String[] pieces = coreText.split("\\.", -1);
            if (pieces.length == 0 || pieces.length > 8) return null;
            BigInteger[] core = new BigInteger[pieces.length];
            for (int i = 0; i < pieces.length; i++) {
                if (!numeric(pieces[i])) return null;
                core[i] = new BigInteger(pieces[i]);
            }
            return new ParsedVersion(core, preText == null ? null : preText.split("\\.", -1));
        }

        @Override
        public int compareTo(ParsedVersion other) {
            int count = Math.max(core.length, other.core.length);
            for (int i = 0; i < count; i++) {
                BigInteger left = i < core.length ? core[i] : BigInteger.ZERO;
                BigInteger right = i < other.core.length ? other.core[i] : BigInteger.ZERO;
                int compared = left.compareTo(right);
                if (compared != 0) return compared;
            }
            if (preRelease == null) return other.preRelease == null ? 0 : 1;
            if (other.preRelease == null) return -1;
            int identifiers = Math.max(preRelease.length, other.preRelease.length);
            for (int i = 0; i < identifiers; i++) {
                if (i >= preRelease.length) return -1;
                if (i >= other.preRelease.length) return 1;
                String left = preRelease[i];
                String right = other.preRelease[i];
                boolean leftNumeric = numeric(left);
                boolean rightNumeric = numeric(right);
                int compared;
                if (leftNumeric && rightNumeric) compared = new BigInteger(left).compareTo(new BigInteger(right));
                else if (leftNumeric != rightNumeric) compared = leftNumeric ? -1 : 1;
                else compared = left.compareTo(right);
                if (compared != 0) return compared;
            }
            return 0;
        }

        private static boolean validIdentifiers(String value) {
            if (value == null || value.isEmpty()) return false;
            String[] identifiers = value.split("\\.", -1);
            for (String identifier : identifiers) {
                if (identifier.isEmpty()) return false;
                for (int i = 0; i < identifier.length(); i++) {
                    char c = identifier.charAt(i);
                    if (!Character.isLetterOrDigit(c) && c != '-') return false;
                }
            }
            return true;
        }

        private static boolean numeric(String value) {
            if (value == null || value.isEmpty()) return false;
            for (int i = 0; i < value.length(); i++) {
                if (!Character.isDigit(value.charAt(i))) return false;
            }
            return true;
        }
    }
}
