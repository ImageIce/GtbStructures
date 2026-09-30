package com.gtbstructures.sound;

import com.gtbstructures.GtbStructures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

import java.util.Random;

/**
 * Runtime bridge for replacing the local player's correct-guess orb.
 *
 * <p>The chat parser arms a short-lived local-guess token.  The sound hook
 * consumes the matching vanilla packet/event and the score hook supplies the
 * actual award (3, 2, or 1) before the replacement is played.  Builder score
 * updates never arm a token and therefore cannot trigger this controller.</p>
 */
public final class GuessSoundController {
    public static final GuessSoundController INSTANCE = new GuessSoundController();

    /** The observed packet is close to the chat line, but score text can lag. */
    static final long TOKEN_WINDOW_MS = 1500L;
    static final String VANILLA_SOUND = "random.orb";
    static final float VANILLA_VOLUME = 20.0F;
    static final float VANILLA_PITCH = 1.0F;

    private final Random random = new Random();
    private long tokenUntil;
    private int confirmedPoints;
    private boolean originalConsumed;
    private boolean scoreApplied;
    private boolean resolved;
    private boolean playingReplacement;
    private boolean localBuilderRound;
    private Playback playback = new MinecraftPlayback();

    private GuessSoundController() { }

    /** Arms a token for a local correct guess. Call on the client/main thread. */
    public synchronized void observeLocalCorrectGuess(long now) {
        if (!enabled() || localBuilderRound) return;
        if (tokenLive(now)) return;
        confirmedPoints = 0;
        originalConsumed = false;
        scoreApplied = false;
        resolved = false;
        tokenUntil = now + TOKEN_WINDOW_MS;
    }

    /** Records the authoritative self-award. Builder awards must not call this. */
    public synchronized void observeLocalGuessPoints(int points, long now) {
        if (!validPoints(points) || !enabled() || !tokenLive(now) || resolved) return;
        confirmedPoints = points;
        scoreApplied = true;
        if (originalConsumed) playSelected(points);
    }

    /**
     * Returns true when the incoming vanilla orb should be suppressed. The raw
     * packet recorder must run before this method so diagnostics retain it.
     */
    public synchronized boolean consumeOriginal(String soundName, float volume,
                                                  float pitch, long now) {
        if (playingReplacement) return false;
        if (!enabled() || !isVanillaOrb(soundName)
                || volume != VANILLA_VOLUME || pitch != VANILLA_PITCH
                || !tokenLive(now)) return false;
        if (originalConsumed) return true;
        originalConsumed = true;
        if (scoreApplied) playSelected(confirmedPoints);
        return true;
    }

    /** Plays a configured sound at the local player for the experimental UI. */
    public synchronized void preview(int points) {
        if (!enabled() || !validPoints(points)) return;
        playSelected(points, false);
    }

    /** Clears a pending token at round/session boundaries. */
    public synchronized void reset() {
        clearToken();
        localBuilderRound = false;
    }

    /** Keeps the sound path independent from GameStats' tracking toggle. */
    public synchronized void observeBuilderStatus(boolean localBuilder) {
        clearToken();
        localBuilderRound = localBuilder;
    }

    public synchronized void onRoundBoundary() { reset(); }
    public synchronized void onWorldUnload() { reset(); }
    public synchronized void onDisconnect() { reset(); }

    /** Exposed for tests and packet/event adapters. */
    synchronized int pendingPoints(long now) {
        if (!tokenLive(now) || resolved) return 0;
        return confirmedPoints;
    }

    private boolean tokenLive(long now) {
        if (tokenUntil == 0L || now > tokenUntil) {
            clearToken();
            return false;
        }
        return true;
    }

    private void playSelected(int points) { playSelected(points, true); }

    private void playSelected(int points, boolean consumeToken) {
        String sound = GuessSounds.resolveSound(points, random);
        // Retain the consumed token until its deadline so duplicate packets
        // and score notices cannot play the guess twice.
        if (consumeToken) resolved = true;
        if (sound == null || GuessSounds.getVolume() <= 0F) return;
        playingReplacement = true;
        try {
            playback.play(sound, GuessSounds.getVolume(), VANILLA_PITCH);
        } finally {
            playingReplacement = false;
        }
    }

    void setPlaybackForTest(Playback injected) {
        synchronized (this) { playback = injected == null ? new MinecraftPlayback() : injected; }
    }

    interface Playback { void play(String sound, float volume, float pitch); }

    private static final class MinecraftPlayback implements Playback {
        public void play(String sound, float volume, float pitch) {
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayerSP player = mc == null ? null : mc.thePlayer;
            if (player != null && mc.theWorld != null) {
                mc.theWorld.playSound(player.posX, player.posY, player.posZ,
                        sound, volume, pitch, false);
            }
        }
    }

    private void clearToken() {
        tokenUntil = 0L;
        confirmedPoints = 0;
        originalConsumed = false;
        scoreApplied = false;
        resolved = false;
    }

    private static boolean validPoints(int points) { return points >= 1 && points <= 3; }

    private static boolean isVanillaOrb(String soundName) {
        return VANILLA_SOUND.equals(soundName)
                || (soundName != null && ("minecraft:" + VANILLA_SOUND).equals(soundName));
    }

    private static boolean enabled() {
        return GtbStructures.modEnabled;
    }
}
