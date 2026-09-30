package com.gtbstructures.commands;

final class LeaveRejoinController {
    static final long MIN_REJOIN_WAIT_MS = 750L;
    static final long MAX_REJOIN_WAIT_MS = 3500L;
    static final int TIMEOUT_TICKS = 20 * 60;

    interface Clock {
        long nowMillis();
    }

    enum Action {
        NONE,
        CANCELLED,
        REJOIN
    }

    private int pendingTicks = -1;
    private boolean connectionBoundaryObserved;
    private long pendingSinceMs;
    private final Clock clock;

    LeaveRejoinController() {
        this(new Clock() {
            @Override
            public long nowMillis() {
                return System.currentTimeMillis();
            }
        });
    }

    LeaveRejoinController(Clock clock) {
        if (clock == null) throw new IllegalArgumentException("clock");
        this.clock = clock;
    }

    void start() {
        pendingTicks = 0;
        connectionBoundaryObserved = false;
        pendingSinceMs = clock.nowMillis();
    }

    void onConnectionBoundary(boolean playReady) {
        if (pendingTicks >= 0 && playReady) connectionBoundaryObserved = true;
    }

    Action tick(boolean playerAvailable) {
        if (pendingTicks < 0) return Action.NONE;
        pendingTicks++;
        if (pendingTicks > TIMEOUT_TICKS) {
            cancel();
            return Action.CANCELLED;
        }
        if (!playerAvailable) return Action.NONE;

        long elapsedMs = clock.nowMillis() - pendingSinceMs;
        if (elapsedMs < 0L) elapsedMs = 0L;
        if (elapsedMs < MIN_REJOIN_WAIT_MS) return Action.NONE;
        if (!connectionBoundaryObserved && elapsedMs < MAX_REJOIN_WAIT_MS) {
            return Action.NONE;
        }
        cancel();
        return Action.REJOIN;
    }

    boolean isPending() {
        return pendingTicks >= 0;
    }

    void reset() {
        cancel();
    }

    private void cancel() {
        pendingTicks = -1;
        connectionBoundaryObserved = false;
        pendingSinceMs = 0L;
    }
}
