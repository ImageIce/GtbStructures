package com.gtbstructures.chat;

import java.util.function.BiPredicate;

/** Conservative single-flight pairing of final wire text with a textless server reply. */
public final class AcceptedGuessTracker {
    private static final long WINDOW_MS = 5000L;
    private Object round;
    private String pending;
    private long sentAt;
    private long ambiguousUntil;

    public synchronized void record(Object token, String actualSent, long now) {
        if (token == null || actualSent == null || actualSent.trim().isEmpty()
                || actualSent.trim().startsWith("/")) return;
        if (round != token) {
            long outstandingUntil = pending != null ? sentAt + WINDOW_MS : ambiguousUntil;
            reset();
            round = token;
            // An old round's textless reply must not acknowledge a new round's input.
            if (outstandingUntil > 0L && now <= outstandingUntil) {
                sentAt = now;
                ambiguousUntil = Math.max(outstandingUntil, now + WINDOW_MS);
                return;
            }
        }
        if (now < sentAt) {
            pending = null;
            ambiguousUntil = sentAt + WINDOW_MS;
            return;
        }
        if (now <= ambiguousUntil || (pending != null && now - sentAt <= WINDOW_MS)) {
            pending = null;
            ambiguousUntil = now + WINDOW_MS;
            sentAt = now;
            return;
        }
        pending = actualSent;
        sentAt = now;
        ambiguousUntil = 0L;
    }

    public synchronized String consumeAccepted(Object token, long now) {
        if (round != token || pending == null || now < sentAt
                || now - sentAt > WINDOW_MS || now <= ambiguousUntil) return null;
        String result = pending;
        pending = null;
        return result;
    }

    public synchronized void discardEcho(Object token, String received, long now,
                                          BiPredicate<String, String> matcher) {
        if (round == token && pending != null && now >= sentAt
                && now - sentAt <= WINDOW_MS && matcher.test(received, pending)) {
            pending = null;
        }
    }

    public synchronized void reset() {
        round = null;
        pending = null;
        sentAt = 0L;
        ambiguousUntil = 0L;
    }
}
