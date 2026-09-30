package com.gtbstructures.stats;

import java.io.File;
import java.util.List;

/** Combines per-directory raw-log progress into one monotonic operation total. */
final class AggregateMinecraftLogProgress
        implements MinecraftLogThemeExtractor.ProgressListener {
    private final int totalFiles;
    private final long totalBytes;
    private final MinecraftLogThemeExtractor.ProgressListener delegate;

    private int completedFiles;
    private int completedCacheHits;
    private int completedCacheMisses;
    private long completedBytes;
    private boolean lastDirectory;
    private boolean directoryComplete;

    AggregateMinecraftLogProgress(
            List<File> logDirectories,
            MinecraftLogThemeExtractor.ProgressListener delegate) {
        Totals totals = countTotals(logDirectories);
        this.totalFiles = totals.files;
        this.totalBytes = totals.bytes;
        this.delegate = delegate;
    }

    AggregateMinecraftLogProgress(
            int totalFiles, long totalBytes,
            MinecraftLogThemeExtractor.ProgressListener delegate) {
        this.totalFiles = Math.max(0, totalFiles);
        this.totalBytes = Math.max(0L, totalBytes);
        this.delegate = delegate;
    }

    void startDirectory(boolean lastDirectory) {
        this.lastDirectory = lastDirectory;
        this.directoryComplete = false;
    }

    @Override
    public void onProgress(MinecraftLogThemeExtractor.Progress progress) {
        if (delegate == null || progress == null || directoryComplete) return;

        int processedFiles = completedFiles + progress.processedFiles;
        int cacheHits = completedCacheHits + progress.cacheHits;
        int cacheMisses = completedCacheMisses + progress.cacheMisses;
        long processedBytes = completedBytes + progress.processedBytes;
        int reportedTotalFiles = Math.max(totalFiles,
                completedFiles + progress.totalFiles);
        long reportedTotalBytes = Math.max(totalBytes,
                completedBytes + progress.totalBytes);
        boolean complete = lastDirectory && progress.complete;
        delegate.onProgress(new MinecraftLogThemeExtractor.Progress(
                reportedTotalFiles, processedFiles, cacheHits, cacheMisses,
                reportedTotalBytes, processedBytes, complete));

        if (progress.complete) {
            completedFiles = processedFiles;
            completedCacheHits = cacheHits;
            completedCacheMisses = cacheMisses;
            completedBytes = processedBytes;
            directoryComplete = true;
        }
    }

    private static Totals countTotals(List<File> logDirectories) {
        Totals totals = new Totals();
        if (logDirectories == null) return totals;
        for (File directory : logDirectories) {
            File[] files = listFiles(directory);
            if (files == null) continue;
            for (File file : files) {
                if (!isSupportedLog(file)) continue;
                totals.files++;
                totals.bytes += Math.max(0L, file.length());
            }
        }
        return totals;
    }

    private static File[] listFiles(File directory) {
        if (directory == null || !directory.isDirectory()) return null;
        try {
            return directory.listFiles();
        } catch (SecurityException ignored) {
            return null;
        }
    }

    private static boolean isSupportedLog(File file) {
        return file != null && file.isFile()
                && MinecraftLogThemeExtractor.isSupportedLogFileName(file.getName());
    }

    private static final class Totals {
        int files;
        long bytes;
    }
}
