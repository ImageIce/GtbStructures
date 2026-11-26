package com.gtbstructures.chat;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.commands.CommandGtb;
import com.gtbstructures.stats.GameStats;
import net.minecraft.client.Minecraft;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Captures final chat packets only after their actual network write succeeds. */
final class SuccessfulGuessNotice {
    private static final String HANDLER_NAME = "gtbs_successful_guess";
    private static final GuessSentNotice NOTICE = new GuessSentNotice();
    private static final Queue<SentGuess> PENDING = new ConcurrentLinkedQueue<>();
    private static volatile long epoch;
    private static volatile boolean suppressed;
    private static Object activeRound;
    private static Object installedListener;
    private static Channel installedChannel;
    private static final ClassValue<Field[]> INSTANCE_FIELDS = new ClassValue<>() {
        @Override protected Field[] computeValue(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && field.trySetAccessible()) fields.add(field);
                }
            }
            return fields.toArray(new Field[0]);
        }
    };

    private SuccessfulGuessNotice() { }

    static void onRoundStart() { reset(); suppressed = false; }
    static void onUserCorrectGuess() { reset(); suppressed = true; }
    static void onDisconnect() {
        reset();
        suppressed = false;
        installedListener = null;
        installedChannel = null;
    }

    static void tick() {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        GameStats stats = GameStats.INSTANCE;
        if (settings.modEnabled && GameStats.isActiveGame(stats.getState())) ensureInstalled();
        if (stats.getState() != GameStats.TrackingState.ROUND_BUILD) suppressed = false;
        Object round = stats.getCurrentRound();
        for (String notice : drain(round, eligible(), settings.guessSentOnlySpam,
                settings.guessSentMinSpam, settings.guessSentShowGuess)) {
            stats.queueNotificationMessage(CommandGtb.head + notice);
        }
    }

    private static boolean eligible() {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        GameStats stats = GameStats.INSTANCE;
        return settings.modEnabled && settings.guessSentNotice && stats.isTrackingEnabled()
                && stats.getState() == GameStats.TrackingState.ROUND_BUILD
                && stats.getCurrentRound() != null
                && !stats.isLocalPlayerIdentity(stats.getCurrentBuilder())
                && !stats.hasLocalPlayerCorrectlyGuessedCurrentRound();
    }

    static SentGuess capture(String message, long now, Object round, boolean eligible) {
        if (!eligible || suppressed || round == null || !ChatCooldownLogic.isNormalChatMessage(message)) return null;
        return new SentGuess(message, now, round, epoch);
    }

    static void enqueue(SentGuess sent) {
        if (sent.epoch == epoch) PENDING.add(sent);
    }

    static List<String> drain(Object round, boolean eligible, boolean onlySpam, int threshold, boolean showGuess) {
        if (!eligible || suppressed || round == null) {
            reset();
            return Collections.emptyList();
        }
        if (activeRound != round) {
            NOTICE.reset();
            activeRound = round;
        }
        List<String> notices = new ArrayList<>();
        SentGuess sent;
        while ((sent = PENDING.poll()) != null) {
            if (sent.epoch != epoch || sent.round != round) continue;
            String notice = NOTICE.onOutbound(sent.message, sent.time, onlySpam, threshold, showGuess);
            if (notice != null) notices.add(notice);
        }
        return notices;
    }

    private static void reset() {
        epoch++;
        PENDING.clear();
        NOTICE.reset();
        activeRound = null;
    }

    private static void ensureInstalled() {
        try {
            Object listener = Minecraft.getInstance().getConnection();
            if (listener == null || listener == installedListener && installedChannel != null
                    && installedChannel.isOpen()) return;
            Channel channel = findChannel(listener);
            if (channel == null || !channel.isOpen()) return;
            // Outbound traversal is reversed: this observes packets immediately before native encoding.
            if (channel.pipeline().get(HANDLER_NAME) == null) {
                if (channel.pipeline().get("encoder") == null) return;
                channel.pipeline().addAfter("encoder", HANDLER_NAME, observer(SuccessfulGuessNotice::packetMessage,
                        System::currentTimeMillis, (message, time) -> capture(message, time,
                                GameStats.INSTANCE.getCurrentRound(), eligible()), SuccessfulGuessNotice::enqueue));
            }
            installedListener = listener;
            installedChannel = channel;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // An unavailable adapter must not prevent chat; a later tick can retry installation.
        }
    }

    static Channel findChannel(Object listener) throws IllegalAccessException {
        if (listener == null) return null;
        for (Field field : INSTANCE_FIELDS.get(listener.getClass())) {
            if (Channel.class.isAssignableFrom(field.getType())) return (Channel) field.get(listener);
            String type = field.getType().getName();
            if (type.equals("net.minecraft.network.Connection") || type.equals("net.minecraft.network.ClientConnection")
                    || type.equals("net.minecraft.class_2535")) {
                Object connection = field.get(listener);
                if (connection == null) continue;
                for (Field member : INSTANCE_FIELDS.get(connection.getClass())) {
                    if (Channel.class.isAssignableFrom(member.getType())) return (Channel) member.get(connection);
                }
            }
        }
        return null;
    }

    static String packetMessage(Object packet) {
        if (packet == null) return null;
        String type = packet.getClass().getName();
        if (!type.equals("net.minecraft.network.protocol.game.ServerboundChatPacket")
                && !type.equals("net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket")
                && !type.equals("net.minecraft.class_2797")) return null;
        try {
            // This record has exactly one String field: the final signed/serialized chat content.
            for (Field field : INSTANCE_FIELDS.get(packet.getClass())) {
                if (field.getType() == String.class) return (String) field.get(packet);
            }
        } catch (IllegalAccessException ignored) { }
        return null;
    }

    static ChannelDuplexHandler observer(Function<Object, String> messageReader, LongSupplier clock,
                                         BiFunction<String, Long, SentGuess> capture, Consumer<SentGuess> successful) {
        return new ChannelDuplexHandler() {
            @Override public void write(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) throws Exception {
                SentGuess captured = null;
                try {
                    String message = messageReader.apply(packet);
                    if (message != null) captured = capture.apply(message, clock.getAsLong());
                } catch (RuntimeException ignored) {
                    // Notification capture must never interfere with an outgoing packet.
                }
                SentGuess sent = captured;
                if (sent != null) {
                    ChannelPromise original = promise;
                    boolean wasVoid = promise.isVoid();
                    if (wasVoid) promise = ctx.newPromise();
                    promise.addListener(future -> {
                        if (future.isSuccess()) successful.accept(sent);
                        else if (wasVoid && !future.isCancelled()) original.tryFailure(future.cause());
                    });
                }
                super.write(ctx, packet, promise);
            }
        };
    }

    static final class SentGuess {
        final String message;
        final long time;
        final Object round;
        final long epoch;
        SentGuess(String message, long time, Object round, long epoch) {
            this.message = message;
            this.time = time;
            this.round = round;
            this.epoch = epoch;
        }
    }
}
