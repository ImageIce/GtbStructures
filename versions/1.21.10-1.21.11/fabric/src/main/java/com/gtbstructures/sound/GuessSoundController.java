package com.gtbstructures.sound;

import java.util.Random;

/** Minecraft-neutral timing and replacement state machine for modern clients. */
public final class GuessSoundController {
    public static final GuessSoundController INSTANCE = new GuessSoundController();
    public static final long WINDOW = 1500L;
    public static final String CORRECT_SOUND = GuessSounds.DEFAULT_SOUND;
    public static final float ORIGINAL_VOLUME = 20.0F;
    public static final float ORIGINAL_PITCH = 1.0F;
    public static final float DEFAULT_ORIGINAL_PITCH = 1.0F;

    public interface Settings {
        boolean enabled();
        String selection(int points);
        float volume();
    }

    public interface Playback {
        void play(String canonicalSoundId, float volume, float pitch);
    }

    public interface OriginalSound {
        String canonicalSoundId();
        float volume();
        float pitch();
    }

    private final Random random = new Random();
    private Settings settings;
    private Playback playback;
    private long tokenUntil;
    private int points;
    private boolean originalConsumed;
    private boolean scoreApplied;
    private boolean resolved;
    private boolean playingReplacement;
    private boolean localBuilderRound;
    private float replacementPitch = DEFAULT_ORIGINAL_PITCH;

    private GuessSoundController() { }

    public synchronized void install(Settings settings, Playback playback) {
        this.settings = settings;
        this.playback = playback;
        clearToken();
    }

    public synchronized void clearInstall() {
        settings = null;
        playback = null;
        clearToken();
    }

    public synchronized void observeLocalCorrectGuess(long now) {
        if (!enabled() || localBuilderRound || tokenLive(now)) return;
        points = 0;
        originalConsumed = false;
        scoreApplied = false;
        resolved = false;
        replacementPitch = DEFAULT_ORIGINAL_PITCH;
        tokenUntil = now + WINDOW;
    }

    public synchronized void observeLocalGuessPoints(int awardedPoints, long now) {
        if (!validPoints(awardedPoints) || !enabled() || !tokenLive(now) || resolved) return;
        points = awardedPoints;
        scoreApplied = true;
        if (originalConsumed) playSelected(awardedPoints);
    }

    public synchronized boolean consumeOriginal(OriginalSound sound, long now) {
        if (playingReplacement || sound == null || !enabled() || !tokenLive(now)
                || !CORRECT_SOUND.equals(normalize(sound.canonicalSoundId()))
                || sound.volume() != ORIGINAL_VOLUME || sound.pitch() != ORIGINAL_PITCH) return false;
        if (originalConsumed) return true;
        originalConsumed = true;
        replacementPitch = sound.pitch();
        if (scoreApplied) playSelected(points);
        return true;
    }

    public synchronized void preview(int points) {
        if (!enabled() || !validPoints(points)) return;
        replacementPitch = DEFAULT_ORIGINAL_PITCH;
        playSelected(points, false);
    }

    public synchronized void observeBuilderStatus(boolean localBuilder) {
        clearToken();
        localBuilderRound = localBuilder;
    }

    public synchronized void onRoundBoundary() { reset(); }
    public synchronized void onWorldUnload() { reset(); }
    public synchronized void onDisconnect() { reset(); }
    public synchronized void reset() { clearToken(); localBuilderRound = false; }

    synchronized int pendingPoints(long now) {
        return tokenLive(now) && !resolved ? points : 0;
    }

    private void playSelected(int points) { playSelected(points, true); }

    private void playSelected(int points, boolean consumeToken) {
        if (!validPoints(points) || playback == null || settings == null) return;
        String sound = GuessSounds.resolveSound(settings.selection(points), random);
        float volume = settings.volume();
        if (consumeToken) resolved = true;
        if (sound == null || volume <= 0.0F) return;
        playingReplacement = true;
        try {
            playback.play(sound, volume, replacementPitch);
        } finally {
            playingReplacement = false;
        }
    }

    private boolean tokenLive(long now) {
        if (tokenUntil == 0L || now > tokenUntil) {
            clearToken();
            return false;
        }
        return true;
    }

    private void clearToken() {
        tokenUntil = 0L;
        points = 0;
        originalConsumed = false;
        scoreApplied = false;
        resolved = false;
    }

    private boolean enabled() { return settings != null && settings.enabled(); }
    private static boolean validPoints(int value) { return value >= 1 && value <= 3; }
    private static String normalize(String value) {
        if (value == null) return null;
        String result = value.toLowerCase(java.util.Locale.ROOT);
        return result.startsWith("minecraft:") ? result.substring("minecraft:".length()) : result;
    }
}
