package com.gtbstructures.player;

import com.gtbstructures.GtbStructures;
import com.gtbstructures.diagnostics.RawPacketRecorder;
import com.gtbstructures.overlay.WorldEnvironmentOverlay;
import com.gtbstructures.stats.GameStats;

import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.play.server.S1FPacketSetExperience;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.play.server.S2BPacketChangeGameState;
import net.minecraft.network.play.server.S2CPacketSpawnGlobalEntity;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.network.play.server.S3BPacketScoreboardObjective;
import net.minecraft.network.play.server.S3EPacketTeams;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/**
 * Packet-arrival tab/team capture. Netty only enqueues immutable observations;
 * the client tick drains them into {@link PlayerMetaCache} so transient tab
 * flashes are captured without hard sleeps or timer guesses.
 */
public final class PlayerListPacketListener {

    public static final PlayerListPacketListener INSTANCE = new PlayerListPacketListener();

    private static final String HANDLER = "gtbstructures_tab_team_listener";

    private final Queue<Object> pending = new ConcurrentLinkedQueue<>();
    private Channel channel;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!captureEnabledForMaster(GtbStructures.modEnabled)) {
            pending.clear();
            return;
        }
        registerIfNeeded();
        drainPending();
    }

    private void registerIfNeeded() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.thePlayer == null || mc.getNetHandler() == null) return;
            Channel ch = mc.getNetHandler().getNetworkManager().channel();
            if (ch == null || !ch.isOpen()) return;
            if (ch == channel && ch.pipeline().get(HANDLER) != null) return;
            if (ch.pipeline().get(HANDLER) != null) {
                channel = ch;
                return;
            }
            ch.pipeline().addBefore("packet_handler", HANDLER, new Handler());
            channel = ch;
        } catch (Throwable ignored) {
            // Diagnostic/capture path only; never risk the client connection.
        }
    }

    private void drainPending() {
        int drained = 0;
        Object event;
        while ((event = pending.poll()) != null && drained++ < 512) {
            try {
                if (event instanceof TabEvent) {
                    applyTabEvent((TabEvent) event);
                } else if (event instanceof TeamEvent) {
                    applyTeamEvent((TeamEvent) event);
                } else if (event instanceof ObjectiveEvent) {
                    applyObjectiveEvent((ObjectiveEvent) event);
                } else if (event instanceof XpEvent) {
                    applyXpEvent((XpEvent) event);
                }
            } catch (Throwable ignored) {
                // Best-effort capture.
            }
        }
    }

    private void applyTabEvent(TabEvent event) {
        String name = event.name;
        if ((name == null || name.isEmpty()) && event.uuid != null) {
            name = resolveIgn(event.uuid);
        }

        if (event.action == S38PacketPlayerListItem.Action.ADD_PLAYER) {
            if (name != null && !name.isEmpty()) {
                PlayerMetaCache.INSTANCE.observePacketRosterAdd(event.uuid, name);
                GameStats.INSTANCE.onPacketRosterAdd(name);
            }
        } else if (event.action == S38PacketPlayerListItem.Action.REMOVE_PLAYER) {
            String removed = PlayerMetaCache.INSTANCE.observePacketRosterRemove(event.uuid, name);
            if (removed != null && !removed.isEmpty()) {
                name = removed;
                GameStats.INSTANCE.onPacketRosterRemove(removed);
            }
        }

        if (name != null && !name.isEmpty() && event.displayName != null) {
            PlayerMetaCache.INSTANCE.observePacketDisplayName(name, event.displayName);
        }
    }

    private void applyTeamEvent(TeamEvent event) {
        PlayerMetaCache.INSTANCE.observeTeamPacket(event.teamName, event.prefix,
                event.suffix, event.members, event.mode);
    }

    private void applyObjectiveEvent(ObjectiveEvent event) {
        GameStats.INSTANCE.onRosterObjectivePacket(event.title, event.mode);
    }

    private void applyXpEvent(XpEvent event) {
        GameStats.INSTANCE.onPhysicalTimerUpdate(event.progress, event.totalExperience, event.level);
    }

    private final class Handler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!captureEnabledForMaster(GtbStructures.modEnabled)) {
                super.channelRead(ctx, msg);
                return;
            }
            RawPacketRecorder.INSTANCE.capture(msg);
            if (msg instanceof S3BPacketScoreboardObjective) {
                captureObjectivePacket((S3BPacketScoreboardObjective) msg);
            } else if (msg instanceof S38PacketPlayerListItem) {
                captureTabPacket((S38PacketPlayerListItem) msg);
            } else if (msg instanceof S3EPacketTeams) {
                captureTeamPacket((S3EPacketTeams) msg);
            } else if (msg instanceof S1FPacketSetExperience) {
                captureXpPacket((S1FPacketSetExperience) msg);
            } else if (msg instanceof S2BPacketChangeGameState) {
                captureWeatherStatePacket((S2BPacketChangeGameState) msg);
            } else if (msg instanceof S2CPacketSpawnGlobalEntity) {
                captureGlobalEntityPacket((S2CPacketSpawnGlobalEntity) msg);
            } else if (msg instanceof S29PacketSoundEffect) {
                captureSoundPacket((S29PacketSoundEffect) msg);
            }
            super.channelRead(ctx, msg);
        }
    }

    public static boolean captureEnabledForMaster(boolean modEnabled) {
        return modEnabled;
    }

    private void captureTabPacket(S38PacketPlayerListItem packet) {
        try {
            S38PacketPlayerListItem.Action action = packet.func_179768_b();
            if (action != S38PacketPlayerListItem.Action.ADD_PLAYER
                    && action != S38PacketPlayerListItem.Action.UPDATE_DISPLAY_NAME
                    && action != S38PacketPlayerListItem.Action.REMOVE_PLAYER) {
                return;
            }

            List<S38PacketPlayerListItem.AddPlayerData> entries = packet.func_179767_a();
            if (entries == null) return;
            for (S38PacketPlayerListItem.AddPlayerData entry : entries) {
                if (entry == null) continue;
                GameProfile profile = entry.getProfile();
                UUID uuid = profile == null ? null : profile.getId();
                String name = profile == null ? null : profile.getName();
                pending.add(new TabEvent(action, uuid, name, entry.getDisplayName()));
            }
        } catch (Throwable ignored) {
            // Best-effort capture.
        }
    }

    private void captureXpPacket(S1FPacketSetExperience packet) {
        try {
            pending.add(new XpEvent(packet.func_149397_c(),
                    packet.getTotalExperience(), packet.getLevel()));
        } catch (Throwable ignored) {
            // Best-effort capture.
        }
    }

    private void captureTeamPacket(S3EPacketTeams packet) {
        try {
            int mode = packet.func_149307_h();
            if (mode < 0 || mode > 4) return;
            Collection<String> packetMembers = packet.func_149310_g();
            List<String> members = packetMembers == null
                    ? new ArrayList<String>() : new ArrayList<String>(packetMembers);
            pending.add(new TeamEvent(packet.func_149312_c(), packet.func_149311_e(),
                    packet.func_149309_f(), members, mode));
        } catch (Throwable ignored) {
            // Best-effort capture.
        }
    }

    private void captureObjectivePacket(S3BPacketScoreboardObjective packet) {
        try {
            int mode = packet.func_149338_e();
            if (mode == 0 || mode == 2) {
                pending.add(new ObjectiveEvent(packet.func_149337_d(), mode));
            }
        } catch (Throwable ignored) {
            // Best-effort capture.
        }
    }

    private void captureWeatherStatePacket(S2BPacketChangeGameState packet) {
        try {
            WorldEnvironmentOverlay.observeChangeGameState(packet.getGameState(), packet.func_149137_d());
        } catch (Throwable ignored) {
            // Best-effort capture.
        }
    }

    private void captureGlobalEntityPacket(S2CPacketSpawnGlobalEntity packet) {
        try {
            if (packet.func_149053_g() == 1) {
                WorldEnvironmentOverlay.observeLightningSignal();
            }
        } catch (Throwable ignored) {
            // Best-effort capture.
        }
    }

    private void captureSoundPacket(S29PacketSoundEffect packet) {
        try {
            WorldEnvironmentOverlay.observeThunderSound(packet.getSoundName());
        } catch (Throwable ignored) {
            // Best-effort capture.
        }
    }

    private static String componentSummary(IChatComponent comp) {
        if (comp == null) return "null sibs=-1";
        int sibs = -1;
        try {
            List<IChatComponent> children = comp.getSiblings();
            sibs = children == null ? 0 : children.size();
        } catch (Throwable ignored) { /* best-effort */ }

        return "\"" + safe(comp.getFormattedText()) + "\""
                + " plain=\"" + safe(comp.getUnformattedText()) + "\""
                + " sibs=" + sibs
                + " json=\"" + safe(IChatComponent.Serializer.componentToJson(comp)) + "\"";
    }

    private static String resolveIgn(UUID uuid) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.thePlayer == null || mc.thePlayer.sendQueue == null) return null;
            Collection<NetworkPlayerInfo> infos = mc.thePlayer.sendQueue.getPlayerInfoMap();
            for (NetworkPlayerInfo info : infos) {
                if (info == null || info.getGameProfile() == null) continue;
                if (uuid.equals(info.getGameProfile().getId())) {
                    return info.getGameProfile().getName();
                }
            }
        } catch (Throwable ignored) { /* best-effort */ }
        return null;
    }

    private static String sample(List<String> members) {
        if (members == null || members.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        int max = Math.min(3, members.size());
        for (int i = 0; i < max; i++) {
            if (i > 0) sb.append(',');
            sb.append(safe(members.get(i)));
        }
        if (members.size() > max) sb.append("...");
        return sb.toString();
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace('\u00a7', '&')
                .replace('搂', '&')
                .replace('\n', ' ')
                .replace('\r', ' ');
    }

    private static final class TabEvent {
        final S38PacketPlayerListItem.Action action;
        final UUID uuid;
        final String name;
        final IChatComponent displayName;

        TabEvent(S38PacketPlayerListItem.Action action, UUID uuid, String name,
                 IChatComponent displayName) {
            this.action = action;
            this.uuid = uuid;
            this.name = name;
            this.displayName = displayName;
        }
    }

    private static final class TeamEvent {
        final String teamName;
        final String prefix;
        final String suffix;
        final List<String> members;
        final int mode;

        TeamEvent(String teamName, String prefix, String suffix,
                  List<String> members, int mode) {
            this.teamName = teamName;
            this.prefix = prefix;
            this.suffix = suffix;
            this.members = members;
            this.mode = mode;
        }
    }

    private static final class ObjectiveEvent {
        final String title;
        final int mode;

        ObjectiveEvent(String title, int mode) {
            this.title = title;
            this.mode = mode;
        }
    }

    private static final class XpEvent {
        final float progress;
        final int totalExperience;
        final int level;

        XpEvent(float progress, int totalExperience, int level) {
            this.progress = progress;
            this.totalExperience = totalExperience;
            this.level = level;
        }
    }

    private PlayerListPacketListener() {}
}
