package com.gtbstructures.hints;

import com.gtbstructures.commands.CommandGtb;
import com.gtbstructures.GtbStructures;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.translation.TranslationData;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Optional /gtbs showallmatching feature: when a round resolves (local player
 * correct-guess or theme reveal), list every theme whose displayed name in the
 * round's language matches the effective initial hint pattern.
 *
 * Effective initial hint: the first action-bar hint that revealed at least one
 * non-underscore character (space exclusive). For 1-letter themes in some
 * languages no character is ever revealed; we fall back to listing every
 * 1-letter theme in the language.
 */
public final class ShowAllMatching {

    /**
     * Pending chat lines queued from chat-event handlers. Drained on the next
     * client tick by {@link GtbStructures#onClientTick}. {@code Minecraft.addScheduledTask}
     * was the obvious choice but executes synchronously when called from the main
     * thread, which is the case for chat events — so it would print our message
     * before the trigger line is appended to the chat GUI. A real next-tick drain
     * is required to land after the trigger line.
     */
    private static final Queue<String> PENDING = new ConcurrentLinkedQueue<String>();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "GtbStructures-ShowAllMatching");
            t.setDaemon(true);
            return t;
        }
    });

    private ShowAllMatching() {}

    /** Captures the effective initial hint on the round if not already captured. */
    public static void onHint(GameStats.RoundData r, String trimmedHint) {
        if (r == null || r.showAllMatchingSent || r.effectiveInitialHint != null) return;
        if (trimmedHint == null || trimmedHint.isEmpty()) return;
        if (trimmedHint.indexOf('_') < 0) return;
        if (!hasRevealedChar(trimmedHint)) return;
        r.effectiveInitialHint = trimmedHint;
    }

    /** True if the hint contains any character other than '_' or ' '. */
    private static boolean hasRevealedChar(String hint) {
        for (int i = 0; i < hint.length(); i++) {
            char c = hint.charAt(i);
            if (c != '_' && c != ' ') return true;
        }
        return false;
    }

    /**
     * Send the matching-themes message to chat if conditions are met.
     * Idempotent per round via {@link GameStats.RoundData#showAllMatchingSent}.
     */
    public static void maybeSend(GameStats.RoundData r) {
        if (!GtbStructures.showAllMatching) return;
        if (r == null || r.showAllMatchingSent) return;
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;

        // Builders already know their theme — no point listing matches for them.
        String localIgn = mc.thePlayer.getName();
        if (r.builder != null && localIgn != null && r.builder.equalsIgnoreCase(localIgn)) {
            r.showAllMatchingSent = true;
            return;
        }

        final String asyncLangCode = r.langCode;
        final String asyncHint = r.effectiveInitialHint;
        final int asyncThemeCharCount = r.themeCharCount;
        final boolean asyncShowEnglish = GtbStructures.showAllMatchingShowEnglish;
        final boolean asyncShowCount = GtbStructures.showAllMatchingShowCount;
        r.showAllMatchingSent = true;
        if (queueAsyncMessage(asyncLangCode, asyncHint, asyncThemeCharCount,
                asyncShowEnglish, asyncShowCount)) return;

        String langCode = r.langCode;
        String hint = r.effectiveInitialHint;

        Map<String, Set<String>> matches;
        String displayedHint;
        if (hint != null && !hint.isEmpty()) {
            matches = TranslationData.findMatchingDisplayedThemes(langCode, hint);
            displayedHint = hint;
        } else if (r.themeCharCount == 1) {
            // Exception 1: 1-letter themes (typically nordic / east-asian languages)
            // never reveal a hint character — enumerate every 1-letter theme.
            matches = TranslationData.findOneLetterDisplayedThemes(langCode);
            displayedHint = "_";
        } else {
            // Exception 2: user guessed before the first effective hint showed up,
            // and the theme is multi-character — we have no signal to match against.
            r.showAllMatchingSent = true;
            return;
        }

        r.showAllMatchingSent = true;

        if (matches == null || matches.isEmpty()) return;

        boolean isEnglish = isEnglish(langCode);
        boolean isPirate = isPirate(langCode);
        // Showing the English origin is meaningless for real English (it IS the English).
        boolean withEnglish = asyncShowEnglish && !isEnglish;

        StringBuilder sb = new StringBuilder();
        sb.append(CommandGtb.head);
        sb.append("All themes matching the initial hint (§e")
                .append(displayedHint)
                .append("§7): ");

        boolean first = true;
        for (Map.Entry<String, Set<String>> e : matches.entrySet()) {
            if (!first) sb.append("§7, ");
            first = false;
            sb.append("§f").append(e.getKey());
            if (withEnglish && shouldShowEnglish(isPirate, e.getKey(), e.getValue())) {
                sb.append("§7 (");
                boolean firstEn = true;
                for (String en : e.getValue()) {
                    if (!firstEn) sb.append(", ");
                    firstEn = false;
                    sb.append(en);
                }
                sb.append(")");
            }
        }
        appendCountSuffix(sb, matches.size(), asyncShowCount);

        // Queue for the next client tick so this message lands AFTER the chat event
        // that triggered it. Minecraft.addScheduledTask runs synchronously when the
        // caller is already on the main thread (which is true inside chat-event
        // handlers), so it's not actually a deferral.
        PENDING.add(sb.toString());
    }

    private static boolean queueAsyncMessage(final String langCode, final String hint,
                                             final int themeCharCount,
                                             final boolean showEnglishConfig,
                                             final boolean showCountConfig) {
        WORKER.execute(new Runnable() {
            @Override public void run() {
                try {
                    String line = buildMessage(langCode, hint, themeCharCount,
                            showEnglishConfig, showCountConfig);
                    if (line != null && !line.isEmpty()) PENDING.add(line);
                } catch (Throwable ignored) {
                    // Optional chat feature; never risk the client thread.
                }
            }
        });
        return true;
    }

    private static String buildMessage(String langCode, String hint, int themeCharCount,
                                       boolean showEnglishConfig, boolean showCountConfig) {
        Map<String, Set<String>> matches;
        String displayedHint;
        if (hint != null && !hint.isEmpty()) {
            matches = TranslationData.findMatchingDisplayedThemes(langCode, hint);
            displayedHint = hint;
        } else if (themeCharCount == 1) {
            matches = TranslationData.findOneLetterDisplayedThemes(langCode);
            displayedHint = "_";
        } else {
            return null;
        }

        if (matches == null || matches.isEmpty()) return null;

        boolean isEnglish = isEnglish(langCode);
        boolean isPirate = isPirate(langCode);
        boolean withEnglish = showEnglishConfig && !isEnglish;

        StringBuilder sb = new StringBuilder();
        sb.append(CommandGtb.head);
        sb.append("All themes matching the initial hint (\u00a7e")
                .append(displayedHint)
                .append("\u00a77): ");

        boolean first = true;
        for (Map.Entry<String, Set<String>> e : matches.entrySet()) {
            if (!first) sb.append("\u00a77, ");
            first = false;
            sb.append("\u00a7f").append(e.getKey());
            if (withEnglish && shouldShowEnglish(isPirate, e.getKey(), e.getValue())) {
                sb.append("\u00a77 (");
                boolean firstEn = true;
                for (String en : e.getValue()) {
                    if (!firstEn) sb.append(", ");
                    firstEn = false;
                    sb.append(en);
                }
                sb.append(")");
            }
        }
        appendCountSuffix(sb, matches.size(), showCountConfig);
        return sb.toString();
    }

    static String buildMessageForTest(String langCode, String hint, int themeCharCount,
                                      boolean showEnglishConfig, boolean showCountConfig) {
        return buildMessage(langCode, hint, themeCharCount,
                showEnglishConfig, showCountConfig);
    }

    private static void appendCountSuffix(StringBuilder sb, int count, boolean showCount) {
        if (showCount) sb.append(" \u00a77(\u00a7e").append(count).append("\u00a77)");
    }

    /** Drain queued chat lines. Called from {@link GtbStructures#onClientTick} once per tick. */
    public static void drainPending() {
        if (PENDING.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;
        String text;
        while ((text = PENDING.poll()) != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(text));
        }
    }

    public static void discardPending() {
        PENDING.clear();
    }

    /**
     * For pirate, suppress brackets when the pirate translation already equals its
     * single English origin — the parenthesis would just repeat the displayed name.
     * Multiwords (one pirate name → many English themes) and rephrased pirate
     * translations are still annotated. Other non-English languages always
     * annotate when withEnglish is on.
     */
    private static boolean shouldShowEnglish(boolean isPirate, String displayed, Set<String> englishOrigins) {
        if (!isPirate) return true;
        if (englishOrigins == null || englishOrigins.isEmpty()) return false;
        if (englishOrigins.size() == 1 && englishOrigins.contains(displayed)) return false;
        return true;
    }

    private static boolean isEnglish(String langCode) {
        return langCode != null && "en".equalsIgnoreCase(langCode);
    }

    private static boolean isPirate(String langCode) {
        return langCode != null && "pirate".equalsIgnoreCase(langCode);
    }
}
