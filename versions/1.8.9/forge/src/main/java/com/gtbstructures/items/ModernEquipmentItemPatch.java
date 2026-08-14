package com.gtbstructures.items;

import com.gtbstructures.GtbStructures;
import com.gtbstructures.diagnostics.RawPacketRecorder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.Map;

public final class ModernEquipmentItemPatch {
    public static final ModernEquipmentItemPatch INSTANCE = new ModernEquipmentItemPatch();

    private static final String HANDLER = "gtbstructures_modern_equipment_items";
    private static final long STALE_ENTRY_MS = 30000L;

    private final Map<Long, Entry> entries = new ConcurrentHashMap<Long, Entry>();
    private Channel channel;
    private boolean handlerUnavailable;
    private long lastCleanupMs;

    private ModernEquipmentItemPatch() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!runtimeEnabledForMaster(GtbStructures.modEnabled)) {
            entries.clear();
            return;
        }
        registerIfNeeded();
        cleanupStaleEntries(System.currentTimeMillis());
    }

    ModernEquipmentItem armorItem(EntityArmorStand armorStand, int armorIndex, ItemStack decodedStack) {
        if (!runtimeEnabledForMaster(GtbStructures.modEnabled)) return null;
        if (armorStand == null || shouldTrustDecodedItem(decodedStack)) return null;
        Entry entry = entries.get(key(armorStand.getEntityId(), armorIndex + 1));
        return entry == null ? null : entry.item;
    }

    ModernEquipmentItem heldItem(EntityLivingBase entity, ItemStack decodedStack) {
        if (!runtimeEnabledForMaster(GtbStructures.modEnabled)) return null;
        if (entity == null || shouldTrustDecodedItem(decodedStack)) return null;
        Entry entry = entries.get(key(entity.getEntityId(), 0));
        return entry == null ? null : entry.item;
    }

    private void registerIfNeeded() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.thePlayer == null || mc.getNetHandler() == null) return;
            Channel ch = mc.getNetHandler().getNetworkManager().channel();
            if (ch == null || !ch.isOpen()) return;
            if (ch != channel) handlerUnavailable = false;
            if (ch == channel && ch.pipeline().get(HANDLER) != null) return;
            if (ch.pipeline().get(HANDLER) != null) {
                channel = ch;
                return;
            }
            if (handlerUnavailable) return;
            if (ch.pipeline().get("decoder") == null) {
                handlerUnavailable = true;
                return;
            }

            ch.pipeline().addBefore("decoder", HANDLER, new Handler());
            channel = ch;
        } catch (Throwable ignored) {
            // Best-effort compatibility patch; never risk the client connection.
        }
    }

    private void captureRawInbound(Object msg) {
        if (!runtimeEnabledForMaster(GtbStructures.modEnabled)) return;
        if (!(msg instanceof ByteBuf)) return;

        try {
            RawEquipment equipment = RawEquipment.read(((ByteBuf) msg).duplicate());
            if (equipment == null) return;

            long key = key(equipment.entityId, equipment.equipmentSlot);
            Entry previous = entries.get(key);
            ModernEquipmentItem previousItem = previous == null ? null : previous.item;
            if (equipment.rawItemId < 0) {
                RawPacketRecorder.INSTANCE.captureEntityEquipment(equipment.entityId,
                        equipment.equipmentSlot, equipment.rawItemId, equipment.rawDamage,
                        previousItem, null);
                entries.remove(key);
                return;
            }

            ModernEquipmentItem item = cachedItemAfterRawEquipment(
                    previousItem, equipment.rawItemId, equipment.rawDamage);
            RawPacketRecorder.INSTANCE.captureEntityEquipment(equipment.entityId,
                    equipment.equipmentSlot, equipment.rawItemId, equipment.rawDamage,
                    previousItem, item);
            if (item == null) {
                entries.remove(key);
            } else {
                entries.put(key, new Entry(item, System.currentTimeMillis()));
            }
        } catch (Throwable ignored) {
            // Best-effort packet inspection.
        }
    }

    static ModernEquipmentItem cachedItemAfterRawEquipment(ModernEquipmentItem previous,
                                                            int rawItemId, int rawDamage) {
        ModernEquipmentItem decoded = ModernEquipmentItem.fromRawItem(rawItemId, rawDamage);
        if (decoded != null) return decoded;
        if (rawItemId == 383 && rawDamage == 0
                && previous != null && previous.kind == ModernEquipmentItem.Kind.SPAWN_EGG) {
            return previous;
        }
        return null;
    }

    static boolean runtimeEnabledForMaster(boolean modEnabled) {
        return modEnabled;
    }

    private void cleanupStaleEntries(long nowMs) {
        if (nowMs - lastCleanupMs < 5000L) return;
        lastCleanupMs = nowMs;

        Iterator<Map.Entry<Long, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Entry> entry = iterator.next();
            if (nowMs - entry.getValue().lastSeenMs > STALE_ENTRY_MS) {
                iterator.remove();
            }
        }
    }

    private static boolean shouldTrustDecodedItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        Item item = stack.getItem();
        if (item == Items.spawn_egg && ModernEquipmentItem.spawnEgg(stack.getMetadata()) == null) {
            return false;
        }
        if (item == Items.bed) {
            return false;
        }
        return true;
    }

    private static long key(int entityId, int equipmentSlot) {
        return (((long) entityId) << 32) ^ (equipmentSlot & 0xffffffffL);
    }

    private final class Handler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            captureRawInbound(msg);
            super.channelRead(ctx, msg);
        }
    }

    private static final class Entry {
        final ModernEquipmentItem item;
        final long lastSeenMs;

        Entry(ModernEquipmentItem item, long lastSeenMs) {
            this.item = item;
            this.lastSeenMs = lastSeenMs;
        }
    }

    private static final class RawEquipment {
        final int entityId;
        final int equipmentSlot;
        final int rawItemId;
        final int rawDamage;

        RawEquipment(int entityId, int equipmentSlot, int rawItemId, int rawDamage) {
            this.entityId = entityId;
            this.equipmentSlot = equipmentSlot;
            this.rawItemId = rawItemId;
            this.rawDamage = rawDamage;
        }

        static RawEquipment read(ByteBuf buffer) {
            if (buffer == null) return null;
            int start = buffer.readerIndex();
            int end = buffer.writerIndex();
            VarInt packetId = readVarInt(buffer, start, end);
            if (packetId == null || packetId.value != 0x04) return null;

            VarInt entityId = readVarInt(buffer, packetId.nextIndex, end);
            if (entityId == null) return null;
            int index = entityId.nextIndex;
            if (index + 4 > end) return null;

            int equipmentSlot = buffer.getShort(index);
            index += 2;
            int rawItemId = buffer.getShort(index);
            index += 2;

            int rawDamage = -1;
            if (rawItemId >= 0 && index + 3 <= end) {
                index += 1; // stack size
                rawDamage = buffer.getShort(index);
            }
            return new RawEquipment(entityId.value, equipmentSlot, rawItemId, rawDamage);
        }

        private static VarInt readVarInt(ByteBuf buffer, int index, int end) {
            int value = 0;
            int shift = 0;
            int cursor = index;
            while (cursor < end && shift < 35) {
                int b = buffer.getUnsignedByte(cursor++);
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) return new VarInt(value, cursor);
                shift += 7;
            }
            return null;
        }
    }

    private static final class VarInt {
        final int value;
        final int nextIndex;

        VarInt(int value, int nextIndex) {
            this.value = value;
            this.nextIndex = nextIndex;
        }
    }
}
