package com.gtbstructures.chat;

import com.gtbstructures.diagnostics.RawPacketRecorder;
import com.gtbstructures.GtbStructures;
import com.gtbstructures.overlay.HudPanelScale;
import com.gtbstructures.overlay.HudPosition;
import com.gtbstructures.scoreboard.InterfacePreview;
import com.gtbstructures.scoreboard.ScoreboardLayout;
import com.gtbstructures.stats.GameStats;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;

public final class ChatCooldownCounter {
    public static final ChatCooldownCounter INSTANCE = new ChatCooldownCounter();

    private static final String HANDLER = "gtbstructures_chat_cooldown_counter";
    private static final long COOLDOWN_MS = 3100L;
    private static final long DISPLAY_MAX_MS = 3000L;
    private static final long START_GATE_PADDING_MS = COOLDOWN_MS - DISPLAY_MAX_MS;
    private static final long ACCEPTANCE_CONFIRMATION_MS = 5000L;
    private static final int BAR_WIDTH = 42;
    private static final int BAR_HEIGHT = 4;
    private static final int TIMER_BG_PAD_X = 2;
    private static final int TIMER_BG_PAD_Y = 1;

    private final Queue<SentChat> pending = new ConcurrentLinkedQueue<SentChat>();
    private Channel channel;
    private long cooldownEndMs;
    private long pendingRestartSentAtMs;
    private boolean suppressUntilRoundEnds;

    private ChatCooldownCounter() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!runtimeEnabledForMaster(GtbStructures.modEnabled)) {
            resetRuntimeState();
            return;
        }
        registerIfNeeded();
        drainPending();
        updateState(System.currentTimeMillis());
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CHAT) return;
        if (!runtimeEnabledForMaster(GtbStructures.modEnabled)) return;
        render();
    }

    static boolean isNormalChatMessage(String message) {
        if (message == null) return false;
        String trimmed = message.trim();
        return !trimmed.isEmpty() && !trimmed.startsWith("/");
    }

    static boolean shouldStartCooldown(boolean enabled, GameStats.TrackingState state,
                                       String message, long now, long cooldownEnd) {
        return shouldStartCooldown(enabled, state, message, now, cooldownEnd, false);
    }

    static boolean shouldStartCooldown(boolean enabled, GameStats.TrackingState state,
                                       String message, long now, long cooldownEnd,
                                       boolean suppressAfterCorrectGuess) {
        return enabled
                && !suppressAfterCorrectGuess
                && state == GameStats.TrackingState.ROUND_BUILD
                && isNormalChatMessage(message)
                && now >= cooldownEnd;
    }

    static boolean shouldAwaitAcceptance(boolean enabled, GameStats.TrackingState state,
                                         String message, long now, long cooldownEnd,
                                         boolean suppressAfterCorrectGuess) {
        return enabled
                && !suppressAfterCorrectGuess
                && state == GameStats.TrackingState.ROUND_BUILD
                && isNormalChatMessage(message)
                && cooldownEnd > now
                && now >= cooldownEnd - START_GATE_PADDING_MS;
    }

    static String displayRemainingForTest(long remainingMs) {
        return displayRemaining(remainingMs);
    }

    static int fillWidthForTest(int width, long remainingMs) {
        return fillWidth(width, remainingMs);
    }

    static int barWidthForTest() {
        return BAR_WIDTH;
    }

    static boolean shouldRenderForRoundForTest(boolean enabled, boolean previewActive,
                                               GameStats.TrackingState state,
                                               GameStats.RoundData round,
                                               String localIgn) {
        return shouldRenderForRound(enabled, previewActive, state, round, localIgn);
    }

    void resetForTest() {
        resetRuntimeState();
    }

    private void resetRuntimeState() {
        pending.clear();
        cooldownEndMs = 0L;
        pendingRestartSentAtMs = 0L;
        suppressUntilRoundEnds = false;
    }

    public void onRoundStart() {
        cooldownEndMs = 0L;
        pendingRestartSentAtMs = 0L;
        pending.clear();
        suppressUntilRoundEnds = false;
    }

    public void onUserCorrectGuess() {
        cooldownEndMs = 0L;
        pendingRestartSentAtMs = 0L;
        pending.clear();
        suppressUntilRoundEnds = true;
    }

    public void onUserChatAccepted(long now) {
        drainPending();
        if (suppressUntilRoundEnds || pendingRestartSentAtMs <= 0L) return;
        long sentAt = pendingRestartSentAtMs;
        pendingRestartSentAtMs = 0L;
        if (now < sentAt || now - sentAt > ACCEPTANCE_CONFIRMATION_MS) return;
        long confirmedEnd = sentAt + COOLDOWN_MS;
        cooldownEndMs = confirmedEnd > now ? confirmedEnd : 0L;
    }

    private void registerIfNeeded() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.thePlayer == null || mc.getNetHandler() == null) return;
            Channel ch = mc.getNetHandler().getNetworkManager().channel();
            if (ch == null || !ch.isOpen()) return;
            if (ch == channel && ch.pipeline().get(HANDLER) != null) return;
            installHandler(ch);
            channel = ch;
        } catch (Throwable ignored) {
            // Optional HUD aid only; never risk the connection.
        }
    }

    private void installHandler(Channel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        if (pipeline.get(HANDLER) != null) return;
        if (pipeline.get("encoder") != null) {
            // Outbound traversal runs in reverse. Placing this immediately
            // after the encoder lets replacement handlers run first while the
            // message is still a C01 packet when it reaches us.
            pipeline.addAfter("encoder", HANDLER, new Handler());
        } else {
            pipeline.addBefore("packet_handler", HANDLER, new Handler());
        }
    }

    private void drainPending() {
        if (suppressUntilRoundEnds) {
            pending.clear();
            cooldownEndMs = 0L;
            return;
        }
        SentChat sent;
        while ((sent = pending.poll()) != null) {
            boolean enabled = GtbStructures.chatCooldownCounter && GameStats.INSTANCE.isTrackingEnabled();
            applyOutbound(enabled, GameStats.INSTANCE.getState(), sent.message, sent.t);
        }
    }

    private void applyOutbound(boolean enabled, GameStats.TrackingState state,
                               String message, long now) {
        expirePendingRestart(now);
        if (cooldownEndMs > 0L && now >= cooldownEndMs) cooldownEndMs = 0L;
        if (shouldStartCooldown(enabled, state, message, now, cooldownEndMs,
                suppressUntilRoundEnds)) {
            cooldownEndMs = now + COOLDOWN_MS;
            pendingRestartSentAtMs = 0L;
        } else if (shouldAwaitAcceptance(enabled, state, message, now, cooldownEndMs,
                suppressUntilRoundEnds)) {
            pendingRestartSentAtMs = now;
        }
    }

    private void updateState(long now) {
        if (!GtbStructures.chatCooldownCounter
                || !GameStats.INSTANCE.isTrackingEnabled()
                || GameStats.INSTANCE.getState() != GameStats.TrackingState.ROUND_BUILD) {
            cooldownEndMs = 0L;
            pendingRestartSentAtMs = 0L;
            pending.clear();
            suppressUntilRoundEnds = false;
            return;
        }

        if (suppressUntilRoundEnds) {
            cooldownEndMs = 0L;
            pendingRestartSentAtMs = 0L;
            pending.clear();
            return;
        }

        if (cooldownEndMs > 0L && now >= cooldownEndMs) {
            cooldownEndMs = 0L;
        }
        expirePendingRestart(now);
    }

    private void expirePendingRestart(long now) {
        if (pendingRestartSentAtMs > 0L
                && (now < pendingRestartSentAtMs
                || now - pendingRestartSentAtMs > ACCEPTANCE_CONFIRMATION_MS)) {
            pendingRestartSentAtMs = 0L;
        }
    }

    void applyOutboundForTest(boolean enabled, GameStats.TrackingState state,
                              String message, long now) {
        applyOutbound(enabled, state, message, now);
    }

    long cooldownEndForTest() {
        return cooldownEndMs;
    }

    long pendingRestartForTest() {
        return pendingRestartSentAtMs;
    }

    void installHandlerForTest(Channel ch) {
        installHandler(ch);
    }

    String pendingMessageForTest() {
        SentChat sent = pending.peek();
        return sent == null ? null : sent.message;
    }

    private void render() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) return;
        if (!shouldRenderForRound(GtbStructures.chatCooldownCounter,
                InterfacePreview.isActive(),
                GameStats.INSTANCE.getState(),
                GameStats.INSTANCE.getCurrentRound(),
                mc.thePlayer.getName())) return;

        long now = System.currentTimeMillis();
        long remaining = cooldownEndMs - now;
        boolean coolingDown = remaining > 0L;
        if (!coolingDown) return;
        boolean showTimer = GtbStructures.chatCooldownTimerEnabled;
        boolean showBar = GtbStructures.chatCooldownBarEnabled;
        if (!showTimer && !showBar) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int fill = fillWidth(BAR_WIDTH, remaining);
        int fillColor = GtbStructures.minecraftColorArgb(GtbStructures.chatCooldownBarColor, 0xCC);
        String label = GtbStructures.normalizeMinecraftColorCode(GtbStructures.chatCooldownTimerColor, "\u00a7f")
                + displayRemaining(remaining);

        int labelWidth = showTimer ? mc.fontRendererObj.getStringWidth(label) : 0;
        int widgetWidth = Math.max(showBar ? BAR_WIDTH : 0, labelWidth);
        int widgetHeight = (showTimer ? mc.fontRendererObj.FONT_HEIGHT : 0)
                + (showTimer && showBar ? 3 : 0)
                + (showBar ? BAR_HEIGHT : 0);
        float panelScale = HudPanelScale.normalize(GtbStructures.chatCooldownScale);
        int scaledWidth = HudPanelScale.scaledPixels(widgetWidth, panelScale);
        int scaledHeight = HudPanelScale.scaledPixels(widgetHeight, panelScale);
        int widgetLeft = HudPosition.left(GtbStructures.chatCooldownPosX, sr.getScaledWidth(), scaledWidth, 2);
        int widgetTop = HudPosition.top(GtbStructures.chatCooldownPosY, sr.getScaledHeight(), scaledHeight, 2);
        int centerX = widgetWidth / 2;
        int x = centerX - BAR_WIDTH / 2;
        int y = showTimer ? mc.fontRendererObj.FONT_HEIGHT + 3 : 0;
        int labelX = centerX - labelWidth / 2;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(widgetLeft, widgetTop, 0.0F);
            GlStateManager.scale(panelScale, panelScale, 1.0F);
            if (showTimer && GtbStructures.chatCooldownBackground) {
                Gui.drawRect(
                        labelX - TIMER_BG_PAD_X,
                        -TIMER_BG_PAD_Y,
                        labelX + labelWidth + TIMER_BG_PAD_X,
                        mc.fontRendererObj.FONT_HEIGHT + TIMER_BG_PAD_Y,
                        ScoreboardLayout.BACKGROUND_COLOR
                );
            }
            if (showBar) {
                if (GtbStructures.chatCooldownBarBackground) {
                    Gui.drawRect(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, ScoreboardLayout.BACKGROUND_COLOR);
                    Gui.drawRect(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0x66000000);
                }
                Gui.drawRect(x, y, x + fill, y + BAR_HEIGHT, fillColor);
            }
            if (showTimer) {
                mc.fontRendererObj.drawStringWithShadow(label, labelX, 0, 0xFFFFFF);
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static boolean shouldRenderForRound(boolean enabled, boolean previewActive,
                                                GameStats.TrackingState state,
                                                GameStats.RoundData round,
                                                String localIgn) {
        if (!enabled || previewActive) return false;
        if (state != GameStats.TrackingState.ROUND_BUILD) return false;
        if (round == null || round.builder == null || localIgn == null || localIgn.isEmpty()) return true;
        return !round.builder.equalsIgnoreCase(localIgn);
    }

    private static String displayRemaining(long remainingMs) {
        long clamped = Math.max(0L, Math.min(DISPLAY_MAX_MS, remainingMs));
        int tenths = (int) ((clamped + 99L) / 100L);
        int seconds = tenths / 10;
        int dec = tenths % 10;
        return seconds + "." + dec + "s";
    }

    private static int fillWidth(int width, long remainingMs) {
        long clamped = Math.max(0L, Math.min(DISPLAY_MAX_MS, remainingMs));
        return Math.max(0, Math.min(width, (int) ((clamped * width + DISPLAY_MAX_MS - 1L) / DISPLAY_MAX_MS)));
    }

    private final class Handler extends ChannelDuplexHandler {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof C01PacketChatMessage) {
                try {
                    String original = ((C01PacketChatMessage) msg).getMessage();
                    String replacement = transformOutboundForMaster(
                            GtbStructures.modEnabled, original);
                    if (replacement != null && !replacement.equals(original)) {
                        msg = new C01PacketChatMessage(replacement);
                    }
                } catch (Throwable ignored) {
                    // Experimental replacement must never block outbound chat.
                }
                if (runtimeEnabledForMaster(GtbStructures.modEnabled)) {
                    try {
                        RawPacketRecorder.INSTANCE.captureOutbound(msg);
                        pending.add(new SentChat(System.currentTimeMillis(),
                                ((C01PacketChatMessage) msg).getMessage()));
                    } catch (Throwable ignored) {
                        // Best-effort capture.
                    }
                }
            }
            super.write(ctx, msg, promise);
        }
    }

    public static boolean runtimeEnabledForMaster(boolean modEnabled) {
        return modEnabled;
    }

    public static String transformOutboundForMaster(boolean modEnabled, String original) {
        if (!runtimeEnabledForMaster(modEnabled)) return original;
        String replacement = ScrambleGuess.transformOutbound(original);
        return TrollMode.transformOutbound(replacement);
    }

    private static final class SentChat {
        final long t;
        final String message;

        SentChat(long t, String message) {
            this.t = t;
            this.message = message;
        }
    }
}
