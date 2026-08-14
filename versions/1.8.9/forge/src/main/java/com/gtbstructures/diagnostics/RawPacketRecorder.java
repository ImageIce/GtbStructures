package com.gtbstructures.diagnostics;

import com.gtbstructures.GtbStructures;
import com.gtbstructures.items.ModernEquipmentItem;
import com.gtbstructures.stats.GameStats;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S1FPacketSetExperience;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.play.server.S2BPacketChangeGameState;
import net.minecraft.network.play.server.S2CPacketSpawnGlobalEntity;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.network.play.server.S3EPacketTeams;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringUtils;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

/**
 * Manual-export trace of high-signal server packets for GTB debugging.
 *
 * <p>The packet hook keeps capture best-effort and bounded: high-volume packet
 * classes use direct summaries, and reflective fallback reuses cached field
 * metadata. The client tick adds GameStats context and owns the in-memory
 * session buffer. Nothing is written unless the user runs {@code /gtbs
 * exportraw}.</p>
 */
public final class RawPacketRecorder {
    public static final RawPacketRecorder INSTANCE = new RawPacketRecorder();

    private static final int MAX_DRAIN_PER_TICK = 768;
    private static final long PRE_SESSION_WINDOW_MS = 30000L;
    private static final Map<Class<?>, Field[]> FIELD_CACHE = new ConcurrentHashMap<Class<?>, Field[]>();

    private final Queue<PacketSample> pending = new ConcurrentLinkedQueue<PacketSample>();
    private final ArrayDeque<PacketRecord> preSession = new ArrayDeque<PacketRecord>();
    private final ArrayDeque<PacketRecord> session = new ArrayDeque<PacketRecord>();

    private long sessionStartMs;
    private boolean sessionActive;
    private String lastCustomScoreboardSignature;

    public static final class ScoreboardLine {
        public final String kind;
        public final String label;
        public final String score;

        public ScoreboardLine(String kind, String label, String score) {
            this.kind = kind == null ? "" : kind;
            this.label = label == null ? "" : label;
            this.score = score == null ? "" : score;
        }
    }

    public void capture(Object packet) {
        if (!captureEnabledForMaster(GtbStructures.modEnabled)) return;
        if (!GameStats.INSTANCE.isTrackingEnabled() || packet == null) return;
        if (!shouldCapturePacket(packet)) return;
        String simple = packet.getClass().getSimpleName();
        if (!isInterestingPacket(simple)) return;
        pending.add(new PacketSample(System.currentTimeMillis(), simple, summarizePacket(packet, simple)));
    }

    public void captureOutbound(Object packet) {
        if (!captureEnabledForMaster(GtbStructures.modEnabled)) return;
        if (!GameStats.INSTANCE.isTrackingEnabled() || packet == null) return;
        if (!shouldCapturePacket(packet)) return;
        String simple = packet.getClass().getSimpleName();
        if (!isInterestingPacket(simple)) return;
        pending.add(new PacketSample(System.currentTimeMillis(), simple, summarizePacket(packet, simple)));
    }

    public void captureEntityEquipment(int entityId, int equipmentSlot, int rawItemId, int rawDamage,
                                       ModernEquipmentItem previous, ModernEquipmentItem resolved) {
        if (!captureEnabledForMaster(GtbStructures.modEnabled)) return;
        if (!GameStats.INSTANCE.isTrackingEnabled()) return;
        pending.add(new PacketSample(System.currentTimeMillis(), "S04PacketEntityEquipment",
                summarizeEntityEquipment(entityId, equipmentSlot, rawItemId, rawDamage,
                        previous, resolved)));
    }

    public void captureCustomScoreboard(String skin, List<ScoreboardLine> rows) {
        if (!captureEnabledForMaster(GtbStructures.modEnabled)) return;
        if (!GameStats.INSTANCE.isTrackingEnabled()) return;
        if (rows == null || rows.isEmpty()) return;
        String signature = customScoreboardSignature(skin, rows);
        synchronized (this) {
            if (signature.equals(lastCustomScoreboardSignature)) return;
            lastCustomScoreboardSignature = signature;
        }
        String summary = summarizeCustomScoreboard(skin, rows);
        pending.add(new PacketSample(System.currentTimeMillis(),
                "CustomScoreboardSnapshot", summary));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!captureEnabledForMaster(GtbStructures.modEnabled)) {
            pending.clear();
            return;
        }
        drainPending(MAX_DRAIN_PER_TICK);
    }

    public static boolean captureEnabledForMaster(boolean modEnabled) {
        return modEnabled;
    }

    public synchronized void startSession(long gameStartMs) {
        drainAllPending();
        session.clear();
        sessionStartMs = gameStartMs;
        sessionActive = true;
        lastCustomScoreboardSignature = null;

        long minPreTime = gameStartMs - PRE_SESSION_WINDOW_MS;
        Iterator<PacketRecord> it = preSession.iterator();
        while (it.hasNext()) {
            PacketRecord record = it.next();
            if (record.t >= minPreTime) {
                appendSessionRecord(record);
            }
        }
        preSession.clear();
    }

    public synchronized void finishSession() {
        drainAllPending();
        sessionActive = false;
        preSession.clear();
    }

    public synchronized void discardSession() {
        sessionActive = false;
        sessionStartMs = 0L;
        session.clear();
        preSession.clear();
        pending.clear();
        lastCustomScoreboardSignature = null;
    }

    public synchronized File writeSnapshot(File statsDir, String baseName) {
        if (statsDir == null || baseName == null || baseName.trim().isEmpty()) return null;
        drainAllPending();
        if (session.isEmpty()) return null;
        if (!statsDir.exists()) statsDir.mkdirs();
        File file = new File(statsDir, baseName + ".packets.jsonl");
        writeRecords(file);
        return file;
    }

    public synchronized int recordCountForTest() {
        return session.size();
    }

    public synchronized int preSessionCountForTest() {
        return preSession.size();
    }

    synchronized void clearForTest() {
        pending.clear();
        preSession.clear();
        session.clear();
        sessionActive = false;
        sessionStartMs = 0L;
        lastCustomScoreboardSignature = null;
    }

    synchronized void addSyntheticRecordForTest(long t, String packet, String summary) {
        appendSessionRecord(new PacketRecord(t, "ROUND_BUILD", Integer.valueOf(1),
                Integer.valueOf(10), Integer.valueOf(60), "Builder", packet, summary));
    }

    synchronized void addSyntheticSampleForTest(long t, String packet, String summary) {
        addRecord(new PacketSample(t, packet, summary));
    }

    private void drainAllPending() {
        drainPending(Integer.MAX_VALUE);
    }

    private void drainPending(int maxRecords) {
        int drained = 0;
        PacketSample sample;
        while (drained < maxRecords && (sample = pending.poll()) != null) {
            drained++;
            addRecord(sample);
        }
    }

    private synchronized void addRecord(PacketSample sample) {
        if (sample == null) return;
        PacketRecord record = withContext(sample);
        if (sessionActive) {
            appendSessionRecord(record);
            return;
        }
        GameStats.TrackingState state = GameStats.INSTANCE.getState();
        if (state == GameStats.TrackingState.LOBBY
                || state == GameStats.TrackingState.ROUND_PRE
                || state == GameStats.TrackingState.ROUND_BUILD
                || state == GameStats.TrackingState.ROUND_END) {
            appendPreSessionRecord(record);
        }
    }

    private PacketRecord withContext(PacketSample sample) {
        GameStats stats = GameStats.INSTANCE;
        GameStats.TrackingState state = stats.getState();
        GameStats.RoundData round = stats.getCurrentRound();
        Integer roundNumber = round == null || round.roundNumber <= 0 ? null : Integer.valueOf(round.roundNumber);
        Integer totalRounds = round == null || round.totalRounds <= 0 ? null : Integer.valueOf(round.totalRounds);
        return new PacketRecord(sample.t, state == null ? "null" : state.name(),
                roundNumber, totalRounds, stats.getCurrentPhysicalTimerSeconds(),
                round == null ? null : round.builder, sample.packet, sample.summary);
    }

    private void appendPreSessionRecord(PacketRecord record) {
        prunePreSession(record == null ? System.currentTimeMillis() : record.t);
        preSession.addLast(record);
    }

    private void prunePreSession(long now) {
        long minTime = now - PRE_SESSION_WINDOW_MS;
        while (!preSession.isEmpty() && preSession.peekFirst().t < minTime) {
            preSession.removeFirst();
        }
    }

    private void appendSessionRecord(PacketRecord record) {
        if (record == null) return;
        session.addLast(record);
    }

    private void writeRecords(File file) {
        try {
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            w.write(metaLine(file));
            w.newLine();
            for (PacketRecord record : session) {
                w.write(record.toJson(sessionStartMs));
                w.newLine();
            }
            w.close();
        } catch (IOException ignored) {
            // Diagnostics only; never break normal stat export.
        }
    }

    private String metaLine(File file) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"meta\":true")
                .append(",\"format\":\"gtb-packets-jsonl-v1\"")
                .append(",\"createdAt\":").append(json(System.currentTimeMillis()))
                .append(",\"createdText\":").append(json(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())))
                .append(",\"file\":").append(json(file == null ? null : file.getName()))
                .append(",\"sessionStart\":").append(json(sessionStartMs))
                .append(",\"records\":").append(session.size())
                .append("}");
        return sb.toString();
    }

    private static boolean isInterestingPacket(String simple) {
        if (simple == null) return false;
        return simple.equals("S38PacketPlayerListItem")
                || simple.equals("S3EPacketTeams")
                || simple.equals("S1FPacketSetExperience")
                || simple.equals("C01PacketChatMessage")
                || simple.equals("S02PacketChat")
                || simple.equals("S29PacketSoundEffect")
                || simple.equals("S2BPacketChangeGameState")
                || simple.equals("S2CPacketSpawnGlobalEntity")
                || simple.equals("S45PacketTitle")
                || simple.equals("S3BPacketScoreboardObjective")
                || simple.equals("S3CPacketUpdateScore")
                || simple.equals("S3DPacketDisplayScoreboard");
    }

    private static boolean shouldCapturePacket(Object packet) {
        try {
            if (packet instanceof S29PacketSoundEffect) {
                return isThunderSound(((S29PacketSoundEffect) packet).getSoundName());
            }
            if (packet instanceof S2CPacketSpawnGlobalEntity) {
                return ((S2CPacketSpawnGlobalEntity) packet).func_149053_g() == 1;
            }
        } catch (Throwable ignored) {
            return false;
        }
        return true;
    }

    private static String summarizePacket(Object packet, String simple) {
        try {
            if (packet instanceof S38PacketPlayerListItem) {
                return summarizeTab((S38PacketPlayerListItem) packet);
            }
            if (packet instanceof S3EPacketTeams) {
                return summarizeTeam((S3EPacketTeams) packet);
            }
            if (packet instanceof S1FPacketSetExperience) {
                return summarizeXp((S1FPacketSetExperience) packet);
            }
            if (packet instanceof S02PacketChat) {
                return summarizeChat((S02PacketChat) packet);
            }
            if (packet instanceof S2BPacketChangeGameState) {
                return summarizeChangeGameState((S2BPacketChangeGameState) packet);
            }
            if (packet instanceof S2CPacketSpawnGlobalEntity) {
                return summarizeGlobalEntity((S2CPacketSpawnGlobalEntity) packet);
            }
            if (packet instanceof S29PacketSoundEffect) {
                return summarizeSound((S29PacketSoundEffect) packet);
            }
            if (packet instanceof C01PacketChatMessage) {
                return summarizeOutboundChat((C01PacketChatMessage) packet);
            }
            return summarizeReflective(packet, simple);
        } catch (Throwable t) {
            return "{\"error\":" + json(t.getClass().getSimpleName()) + "}";
        }
    }

    static String summarizePacketForTest(Object packet) {
        return summarizePacket(packet, packet == null ? null : packet.getClass().getSimpleName());
    }

    private static String summarizeTab(S38PacketPlayerListItem packet) {
        StringBuilder sb = new StringBuilder();
        S38PacketPlayerListItem.Action action = packet.func_179768_b();
        List<S38PacketPlayerListItem.AddPlayerData> entries = packet.func_179767_a();
        sb.append("{\"action\":").append(json(action == null ? null : action.name()))
                .append(",\"entryCount\":").append(entries == null ? 0 : entries.size())
                .append(",\"entries\":[");
        if (entries != null) {
            int max = Math.min(entries.size(), 8);
            for (int i = 0; i < max; i++) {
                S38PacketPlayerListItem.AddPlayerData entry = entries.get(i);
                if (i > 0) sb.append(",");
                GameProfile profile = entry == null ? null : entry.getProfile();
                UUID uuid = profile == null ? null : profile.getId();
                String name = profile == null ? null : profile.getName();
                sb.append("{\"uuid\":").append(json(uuid == null ? null : uuid.toString()))
                        .append(",\"name\":").append(json(name))
                        .append(",\"display\":").append(componentJson(entry == null ? null : entry.getDisplayName()))
                        .append("}");
            }
            if (entries.size() > max) sb.append(",{\"more\":").append(entries.size() - max).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String summarizeTeam(S3EPacketTeams packet) {
        Collection<String> packetMembers = packet.func_149310_g();
        List<String> members = packetMembers == null
                ? new ArrayList<String>() : new ArrayList<String>(packetMembers);
        return "{\"teamName\":" + json(packet.func_149312_c())
                + ",\"mode\":" + packet.func_149307_h()
                + ",\"prefix\":" + json(packet.func_149311_e())
                + ",\"suffix\":" + json(packet.func_149309_f())
                + ",\"memberCount\":" + members.size()
                + ",\"members\":" + jsonSample(members, 6)
                + "}";
    }

    private static String summarizeXp(S1FPacketSetExperience packet) {
        return "{\"totalExperience\":" + packet.getTotalExperience()
                + ",\"level\":" + packet.getLevel()
                + "}";
    }

    private static String summarizeChat(S02PacketChat packet) {
        return "{\"class\":" + json(packet.getClass().getName())
                + ",\"component\":" + componentJson(packet.getChatComponent())
                + ",\"type\":" + packet.getType()
                + "}";
    }

    private static String summarizeOutboundChat(C01PacketChatMessage packet) {
        return "{\"direction\":\"outbound\""
                + ",\"message\":" + json(packet == null ? null : packet.getMessage())
                + "}";
    }

    private static String summarizeChangeGameState(S2BPacketChangeGameState packet) {
        int state = packet.getGameState();
        return "{\"state\":" + state
                + ",\"value\":" + packet.func_149137_d()
                + ",\"meaning\":" + json(changeGameStateMeaning(state))
                + "}";
    }

    private static String summarizeGlobalEntity(S2CPacketSpawnGlobalEntity packet) {
        int type = packet.func_149053_g();
        return "{\"entityId\":" + packet.func_149052_c()
                + ",\"type\":" + type
                + ",\"meaning\":" + json(type == 1 ? "lightning" : "global_entity_" + type)
                + ",\"rawX\":" + packet.func_149051_d()
                + ",\"rawY\":" + packet.func_149050_e()
                + ",\"rawZ\":" + packet.func_149049_f()
                + "}";
    }

    private static String summarizeSound(S29PacketSoundEffect packet) {
        String soundName = packet.getSoundName();
        return "{\"soundName\":" + json(soundName)
                + ",\"weatherThunder\":" + isThunderSound(soundName)
                + ",\"x\":" + packet.getX()
                + ",\"y\":" + packet.getY()
                + ",\"z\":" + packet.getZ()
                + ",\"volume\":" + packet.getVolume()
                + ",\"pitch\":" + packet.getPitch()
                + "}";
    }

    private static String changeGameStateMeaning(int state) {
        switch (state) {
            case 1: return "begin_raining";
            case 2: return "end_raining";
            case 7: return "rain_strength";
            case 8: return "thunder_strength";
            default: return "state_" + state;
        }
    }

    private static boolean isThunderSound(String soundName) {
        if (soundName == null) return false;
        String key = soundName.toLowerCase(Locale.ROOT);
        return key.contains("weather.thunder") || key.contains("ambient.weather.thunder");
    }

    private static String summarizeEntityEquipment(int entityId, int equipmentSlot,
                                                   int rawItemId, int rawDamage,
                                                   ModernEquipmentItem previous,
                                                   ModernEquipmentItem resolved) {
        ModernEquipmentItem decoded = ModernEquipmentItem.fromRawItem(rawItemId, rawDamage);
        boolean retainedPrevious = decoded == null && previous != null && resolved == previous;
        boolean clearsCache = resolved == null;
        return "{\"packetId\":\"0x04\""
                + ",\"entityId\":" + entityId
                + ",\"slot\":" + equipmentSlot
                + ",\"slotName\":" + json(equipmentSlotName(equipmentSlot))
                + ",\"rawItemId\":" + rawItemId
                + ",\"rawDamage\":" + rawDamage
                + ",\"decoded\":" + modernItemJson(decoded)
                + ",\"previous\":" + modernItemJson(previous)
                + ",\"resolved\":" + modernItemJson(resolved)
                + ",\"retainedPrevious\":" + retainedPrevious
                + ",\"clearsCache\":" + clearsCache
                + "}";
    }

    static String summarizeEntityEquipmentForTest(int entityId, int equipmentSlot,
                                                  int rawItemId, int rawDamage,
                                                  ModernEquipmentItem previous,
                                                  ModernEquipmentItem resolved) {
        return summarizeEntityEquipment(entityId, equipmentSlot, rawItemId, rawDamage,
                previous, resolved);
    }

    private static String summarizeCustomScoreboard(String skin, List<ScoreboardLine> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"skin\":").append(json(skin))
                .append(",\"order\":\"top_to_bottom\"")
                .append(",\"rowCount\":").append(rows == null ? 0 : rows.size())
                .append(",\"rows\":[");
        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                ScoreboardLine row = rows.get(i);
                if (i > 0) sb.append(",");
                String label = row == null ? "" : row.label;
                String score = row == null ? "" : row.score;
                sb.append("{\"kind\":").append(json(row == null ? "" : row.kind))
                        .append(",\"label\":").append(json(label))
                        .append(",\"labelPlain\":").append(json(plainScoreboardText(label)))
                        .append(",\"score\":").append(json(score))
                        .append(",\"scorePlain\":").append(json(plainScoreboardText(score)))
                        .append("}");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String customScoreboardSignature(String skin, List<ScoreboardLine> rows) {
        StringBuilder sb = new StringBuilder();
        appendSignatureValue(sb, skin);
        sb.append('|').append(rows == null ? 0 : rows.size());
        if (rows != null) {
            for (ScoreboardLine row : rows) {
                sb.append('|');
                appendSignatureValue(sb, row == null ? "" : row.kind);
                appendSignatureValue(sb, row == null ? "" : row.label);
                appendSignatureValue(sb, row == null ? "" : row.score);
            }
        }
        return sb.toString();
    }

    private static void appendSignatureValue(StringBuilder sb, String value) {
        String clean = value == null ? "" : value;
        sb.append(clean.length()).append(':').append(clean);
    }

    private static String plainScoreboardText(String formatted) {
        if (formatted == null) return "";
        String stripped = StringUtils.stripControlCodes(formatted);
        return stripped == null ? "" : stripped;
    }

    private static String modernItemJson(ModernEquipmentItem item) {
        if (item == null) return "null";
        Integer spawnEggDamage = item.spawnEggDamage();
        return "{\"kind\":" + json(item.kindName())
                + ",\"colorMeta\":" + item.colorMeta()
                + ",\"spawnEggDamage\":"
                + (spawnEggDamage == null ? "null" : String.valueOf(spawnEggDamage))
                + ",\"spawnEggMob\":" + json(item.spawnEggTextureName())
                + "}";
    }

    private static String equipmentSlotName(int equipmentSlot) {
        switch (equipmentSlot) {
            case 0: return "held";
            case 1: return "boots";
            case 2: return "leggings";
            case 3: return "chestplate";
            case 4: return "helmet";
            default: return "slot_" + equipmentSlot;
        }
    }

    private static String summarizeReflective(Object packet, String simple) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"class\":").append(json(packet.getClass().getName()));
        appendDeclaredFields(sb, packet, simple);
        sb.append("}");
        return sb.toString();
    }

    private static void appendDeclaredFields(StringBuilder sb, Object packet, String simple) {
        Field[] fields = cachedFields(packet.getClass());
        sb.append(",\"fields\":{");
        int written = 0;
        for (Field field : fields) {
            try {
                Object value = field.get(packet);
                if (written++ > 0) sb.append(",");
                sb.append(json(field.getName())).append(":").append(valueJson(value, simple));
            } catch (Throwable ignored) {
                // Field-level best effort.
            }
        }
        sb.append("}");
    }

    private static Field[] cachedFields(Class<?> cls) {
        if (cls == null) return new Field[0];
        Field[] cached = FIELD_CACHE.get(cls);
        if (cached != null) return cached;

        Field[] declared = cls.getDeclaredFields();
        List<Field> usable = new ArrayList<Field>();
        for (Field field : declared) {
            if (field == null || Modifier.isStatic(field.getModifiers())) continue;
            try {
                field.setAccessible(true);
                usable.add(field);
            } catch (Throwable ignored) {
                // Field-level best effort.
            }
        }
        Field[] prepared = usable.toArray(new Field[usable.size()]);
        Field[] previous = FIELD_CACHE.putIfAbsent(cls, prepared);
        return previous == null ? prepared : previous;
    }

    private static String valueJson(Object value, String packetSimpleName) {
        if (value == null) return "null";
        if (value instanceof String) return json((String) value);
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        if (value instanceof Enum) return json(((Enum) value).name());
        if (value instanceof UUID) return json(value.toString());
        if (value instanceof IChatComponent) return componentJson((IChatComponent) value);
        Class<?> cls = value.getClass();
        if (cls.isArray()) {
            int len = Array.getLength(value);
            StringBuilder sb = new StringBuilder();
            sb.append("{\"arrayLength\":").append(len);
            int sampleMax = 6;
            sb.append(",\"sample\":[");
            for (int i = 0; i < Math.min(len, sampleMax); i++) {
                if (i > 0) sb.append(",");
                sb.append(json(shortValue(Array.get(value, i))));
            }
            sb.append("]}");
            return sb.toString();
        }
        if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            StringBuilder sb = new StringBuilder();
            sb.append("{\"size\":").append(collection.size()).append(",\"sample\":[");
            int i = 0;
            for (Object item : collection) {
                if (i >= 8) break;
                if (i++ > 0) sb.append(",");
                sb.append(json(shortValue(item)));
            }
            sb.append("]}");
            return sb.toString();
        }
        return json(shortValue(value));
    }

    private static String componentJson(IChatComponent comp) {
        if (comp == null) return "null";
        int siblings = -1;
        try {
            List<IChatComponent> children = comp.getSiblings();
            siblings = children == null ? 0 : children.size();
        } catch (Throwable ignored) { /* best-effort */ }
        return "{\"formatted\":" + json(comp.getFormattedText())
                + ",\"plain\":" + json(comp.getUnformattedText())
                + ",\"siblings\":" + siblings
                + ",\"json\":" + json(IChatComponent.Serializer.componentToJson(comp))
                + "}";
    }

    private static String jsonSample(List<String> values, int max) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        if (values != null) {
            for (int i = 0; i < Math.min(values.size(), max); i++) {
                if (i > 0) sb.append(",");
                sb.append(json(values.get(i)));
            }
            if (values.size() > max) {
                if (max > 0) sb.append(",");
                sb.append(json("...+" + (values.size() - max)));
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static String shortValue(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value);
        s = StringUtils.stripControlCodes(s);
        if (s == null) return null;
        s = s.replace('\n', ' ').replace('\r', ' ');
        return s.length() > 240 ? s.substring(0, 240) + "..." : s;
    }

    private static String json(long value) {
        return String.valueOf(value);
    }

    private static String json(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private static final class PacketSample {
        final long t;
        final String packet;
        final String summary;

        PacketSample(long t, String packet, String summary) {
            this.t = t;
            this.packet = packet;
            this.summary = summary;
        }
    }

    private static final class PacketRecord {
        final long t;
        final String state;
        final Integer round;
        final Integer totalRounds;
        final Integer physicalTimer;
        final String builder;
        final String packet;
        final String summary;

        PacketRecord(long t, String state, Integer round, Integer totalRounds,
                     Integer physicalTimer, String builder, String packet, String summary) {
            this.t = t;
            this.state = state;
            this.round = round;
            this.totalRounds = totalRounds;
            this.physicalTimer = physicalTimer;
            this.builder = builder;
            this.packet = packet;
            this.summary = summary == null ? "{}" : summary;
        }

        String toJson(long sessionStartMs) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"t\":").append(t)
                    .append(",\"dt\":").append(sessionStartMs > 0 ? t - sessionStartMs : 0)
                    .append(",\"state\":").append(json(state))
                    .append(",\"round\":").append(round == null ? "null" : round.toString())
                    .append(",\"totalRounds\":").append(totalRounds == null ? "null" : totalRounds.toString())
                    .append(",\"physicalTimer\":").append(physicalTimer == null ? "null" : physicalTimer.toString())
                    .append(",\"builder\":").append(json(builder))
                    .append(",\"packet\":").append(json(packet))
                    .append(",\"summary\":").append(summary)
                    .append("}");
            return sb.toString();
        }
    }

    private RawPacketRecorder() {}
}
