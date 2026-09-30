package com.gtbstructures.stats;

import org.apache.logging.log4j.LogManager;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Finds Minecraft game-log folders without depending on a launcher brand. */
final class MinecraftLogDirectoryFinder {
    private static final int MAX_DEPTH = 10;
    private static final int MAX_VISITED_DIRECTORIES = 20000;
    private static final long CACHE_TTL_MS = 5L * 60L * 1000L;
    private static final Set<String> PRUNED_DIRECTORY_NAMES = prunedDirectoryNames();
    private static final Object CACHE_LOCK = new Object();

    private static DiscoveryCache cache;

    private MinecraftLogDirectoryFinder() {}

    static List<File> discover(File minecraftDataDir) {
        File workingDir = systemDirectory("user.dir");
        List<File> roots = new ArrayList<File>();
        addRoot(roots, minecraftDataDir);
        addRoot(roots, launcherRootFor(minecraftDataDir));
        if (isPlausibleDiscoveryRoot(workingDir)) {
            addRoot(roots, workingDir);
            addRoot(roots, launcherRootFor(workingDir));
        }
        for (File logDir : activeLogDirectories(workingDir)) {
            addRoot(roots, logDir);
            addRoot(roots, launcherRootFor(logDir));
        }
        for (File runtimeDir : runtimeDirectories(workingDir)) {
            File launcherRoot = launcherRootFor(runtimeDir);
            if (launcherRoot != null && (!launcherRoot.equals(runtimeDir)
                    || isPlausibleDiscoveryRoot(runtimeDir))) {
                addRoot(roots, launcherRoot);
            }
        }
        return discoverCached(roots);
    }

    static List<File> discoverFromRoots(List<File> roots) {
        return scan(normalizeRoots(roots));
    }

    static File launcherRootFor(File activePath) {
        File path = canonical(activePath);
        if (path == null) return null;

        File userHome = canonical(systemDirectory("user.home"));
        File hiddenCandidate = null;
        File cursor = path.isFile() ? path.getParentFile() : path;
        while (cursor != null) {
            String name = cursor.getName();
            if (("instances".equalsIgnoreCase(name)
                    || "profiles".equalsIgnoreCase(name)
                    || "modpacks".equalsIgnoreCase(name))
                    && cursor.getParentFile() != null) {
                return cursor.getParentFile();
            }
            if (userHome != null && isContained(userHome, cursor)
                    && isLauncherDirectoryName(cursor)) {
                hiddenCandidate = cursor;
            }
            if (cursor.equals(userHome)) break;
            cursor = cursor.getParentFile();
        }
        return hiddenCandidate == null ? path : hiddenCandidate;
    }

    private static boolean isLauncherDirectoryName(File directory) {
        if (directory == null || !directory.getName().startsWith(".")) return false;
        String name = directory.getName().toLowerCase(Locale.ROOT);
        return !".cache".equals(name) && !".config".equals(name)
                && !".local".equals(name) && !".var".equals(name);
    }

    private static List<File> discoverCached(List<File> roots) {
        List<File> normalized = normalizeRoots(roots);
        String key = rootsKey(normalized);
        long now = System.currentTimeMillis();
        synchronized (CACHE_LOCK) {
            if (cache != null && cache.key.equals(key)
                    && now - cache.createdAt <= CACHE_TTL_MS) {
                return new ArrayList<File>(cache.logDirectories);
            }
        }

        List<File> discovered = scan(normalized);
        synchronized (CACHE_LOCK) {
            cache = new DiscoveryCache(key, now, discovered);
        }
        return new ArrayList<File>(discovered);
    }

    private static List<File> scan(List<File> roots) {
        List<File> found = new ArrayList<File>();
        Set<String> foundKeys = new HashSet<String>();
        Set<String> visited = new HashSet<String>();
        Deque<SearchDirectory> queue = new ArrayDeque<SearchDirectory>();
        for (File root : roots) {
            queue.addLast(new SearchDirectory(root, root, 0));
        }

        boolean capped = false;
        while (!queue.isEmpty()) {
            if (visited.size() >= MAX_VISITED_DIRECTORIES) {
                capped = true;
                break;
            }
            SearchDirectory next = queue.removeFirst();
            File directory = canonical(next.directory);
            if (directory == null || !directory.isDirectory()
                    || !isContained(next.root, directory)) {
                continue;
            }
            String directoryKey = pathKey(directory);
            if (!visited.add(directoryKey)) continue;

            if (next.depth > 0 && shouldPrune(directory)) {
                continue;
            }

            File[] entries;
            try {
                entries = directory.listFiles();
            } catch (SecurityException ignored) {
                entries = null;
            }
            if (entries == null) continue;
            boolean containsLatestLog = false;
            List<File> childDirectories = new ArrayList<File>();
            for (File entry : entries) {
                if (entry == null) continue;
                if (entry.isDirectory()) childDirectories.add(entry);
                else if (isLatestLogFile(entry)) containsLatestLog = true;
            }
            if (containsLatestLog && foundKeys.add(directoryKey)) {
                found.add(directory);
            }
            if (next.depth >= MAX_DEPTH) continue;
            Collections.sort(childDirectories, FILE_ORDER);
            for (File child : childDirectories) {
                queue.addLast(new SearchDirectory(next.root, child, next.depth + 1));
            }
        }

        if (capped) {
            GameExporter.LOGGER.warn(
                    "Stopped Minecraft log directory discovery after {} directories",
                    MAX_VISITED_DIRECTORIES);
        }
        Collections.sort(found, FILE_ORDER);
        return found;
    }

    private static boolean isLatestLogFile(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName();
        return "latest.log".equalsIgnoreCase(name)
                || "latest.txt".equalsIgnoreCase(name);
    }

    private static List<File> normalizeRoots(List<File> roots) {
        List<File> normalized = new ArrayList<File>();
        Set<String> seen = new HashSet<String>();
        if (roots != null) {
            for (File root : roots) {
                File canonical = canonical(root);
                if (canonical != null && canonical.isDirectory()
                        && seen.add(pathKey(canonical))) {
                    normalized.add(canonical);
                }
            }
        }
        Collections.sort(normalized, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                int depth = Integer.compare(pathDepth(left), pathDepth(right));
                return depth != 0 ? depth : FILE_ORDER.compare(left, right);
            }
        });

        List<File> minimal = new ArrayList<File>();
        for (File candidate : normalized) {
            boolean nested = false;
            for (File root : minimal) {
                if (isContained(root, candidate)) {
                    nested = true;
                    break;
                }
            }
            if (!nested) minimal.add(candidate);
        }
        return minimal;
    }

    private static List<File> activeLogDirectories(File workingDir) {
        LinkedHashSet<File> directories = new LinkedHashSet<File>();
        try {
            Object context = LogManager.getContext(false);
            Object configuration = invokeNoArg(context, "getConfiguration");
            Object appenders = invokeNoArg(configuration, "getAppenders");
            if (appenders instanceof Map) {
                for (Object appender : ((Map<?, ?>) appenders).values()) {
                    addAppenderDirectory(directories, workingDir, appender, "getFileName");
                    addAppenderDirectory(directories, workingDir, appender, "getFilePattern");
                }
            }
        } catch (Throwable ignored) {
            // Custom clients may replace Log4j internals; other discovery roots remain valid.
        }
        return new ArrayList<File>(directories);
    }

    private static List<File> runtimeDirectories(File workingDir) {
        LinkedHashSet<File> directories = new LinkedHashSet<File>();
        addRuntimePropertyDirectories(directories, workingDir, "java.class.path");
        addRuntimePropertyDirectories(directories, workingDir, "java.library.path");
        return new ArrayList<File>(directories);
    }

    private static void addRuntimePropertyDirectories(Set<File> out, File workingDir,
                                                      String propertyName) {
        String value;
        try {
            value = System.getProperty(propertyName);
        } catch (SecurityException ignored) {
            return;
        }
        if (value == null || value.length() == 0) return;
        String[] entries = value.split(Pattern.quote(File.pathSeparator));
        int limit = Math.min(entries.length, 512);
        for (int i = 0; i < limit; i++) {
            if (entries[i] == null || entries[i].trim().length() == 0) continue;
            File entry = new File(entries[i]);
            if (!entry.isAbsolute() && workingDir != null) {
                entry = new File(workingDir, entries[i]);
            }
            File directory = entry.isDirectory() ? entry : entry.getParentFile();
            if (directory != null) out.add(canonical(directory));
        }
    }

    private static void addAppenderDirectory(Set<File> out, File workingDir,
                                              Object appender, String methodName) {
        Object value = invokeNoArg(appender, methodName);
        if (!(value instanceof String) || ((String) value).trim().length() == 0) return;
        File configured = new File((String) value);
        if (!configured.isAbsolute() && workingDir != null) {
            configured = new File(workingDir, (String) value);
        }
        File directory = configured.getAbsoluteFile().getParentFile();
        if (directory != null) out.add(directory);
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isContained(File root, File candidate) {
        File canonicalRoot = canonical(root);
        File canonicalCandidate = canonical(candidate);
        return canonicalRoot != null && canonicalCandidate != null
                && canonicalCandidate.toPath().startsWith(canonicalRoot.toPath());
    }

    private static int pathDepth(File file) {
        return file == null ? Integer.MAX_VALUE : file.toPath().getNameCount();
    }

    private static String rootsKey(List<File> roots) {
        StringBuilder out = new StringBuilder();
        for (File root : roots) out.append(pathKey(root)).append('\n');
        return out.toString();
    }

    private static String pathKey(File file) {
        String path = canonical(file).getPath();
        return File.separatorChar == '\\' ? path.toLowerCase(Locale.ROOT) : path;
    }

    private static File canonical(File file) {
        if (file == null) return null;
        try {
            return file.getCanonicalFile();
        } catch (Exception ignored) {
            return file.getAbsoluteFile();
        }
    }

    private static File systemDirectory(String property) {
        try {
            String value = System.getProperty(property);
            return value == null || value.trim().length() == 0 ? null : new File(value);
        } catch (SecurityException ignored) {
            return null;
        }
    }

    private static void addRoot(List<File> roots, File root) {
        if (root != null) roots.add(root);
    }

    private static boolean isPlausibleDiscoveryRoot(File directory) {
        File root = canonical(directory);
        if (root == null || !root.isDirectory() || root.getParentFile() == null) {
            return false;
        }
        File userHome = canonical(systemDirectory("user.home"));
        if (root.equals(userHome)) return false;
        String name = root.getName().toLowerCase(Locale.ROOT);
        return name.startsWith(".") || "minecraft".equals(name)
                || "multiver".equals(name) || new File(root, "logs").isDirectory()
                || new File(root, "instances").isDirectory()
                || new File(root, "profiles").isDirectory()
                || new File(root, "versions").isDirectory();
    }

    private static boolean shouldPrune(File directory) {
        return PRUNED_DIRECTORY_NAMES.contains(
                directory.getName().toLowerCase(Locale.ROOT));
    }

    private static Set<String> prunedDirectoryNames() {
        Set<String> names = new HashSet<String>();
        Collections.addAll(names,
                ".git", "assets", "cache", "caches", "crash-reports",
                "downloads", "jre", "libraries", "natives", "resourcepacks",
                "runtime", "runtimes", "saves", "screenshots", "shaderpacks",
                "textures", "ui", "webcache");
        return names;
    }

    private static final Comparator<File> FILE_ORDER = new Comparator<File>() {
        @Override
        public int compare(File left, File right) {
            return pathKey(left).compareTo(pathKey(right));
        }
    };

    private static final class SearchDirectory {
        final File root;
        final File directory;
        final int depth;

        SearchDirectory(File root, File directory, int depth) {
            this.root = root;
            this.directory = directory;
            this.depth = depth;
        }
    }

    private static final class DiscoveryCache {
        final String key;
        final long createdAt;
        final List<File> logDirectories;

        DiscoveryCache(String key, long createdAt, List<File> logDirectories) {
            this.key = key;
            this.createdAt = createdAt;
            this.logDirectories = new ArrayList<File>(logDirectories);
        }
    }
}
