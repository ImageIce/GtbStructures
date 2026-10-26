package com.gtbstructures.diagnostics;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.stats.GameStats;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Method;

/**
 * Platform-neutral JSONL recorder for GTBS raw/debug exports.
 * Loader packet hooks can feed summarized packet records into this buffer.
 */
public final class RawPacketRecorder {
    public static final RawPacketRecorder INSTANCE = new RawPacketRecorder();

    private static final long PRE_SESSION_WINDOW_MS = 30000L;

    private final ArrayDeque<PacketRecord> preSession = new ArrayDeque<PacketRecord>();
    private final ArrayDeque<PacketRecord> session = new ArrayDeque<PacketRecord>();
    private long sessionStartMs;
    private boolean sessionActive;
    private String lastCustomScoreboardSignature;
    private volatile long customScoreboardCaptureEpoch = 1L;
    private int customScoreboardSignatureBuildCountForTest;
    private final Map<String, String> lastDistinctSummaries = new HashMap<String, String>();
    private Integer lastPhysicalTimerLevel;

    private RawPacketRecorder() {
    }

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

    public synchronized void startSession(long gameStartMs) {
        session.clear();
        sessionStartMs = gameStartMs;
        sessionActive = true;
        lastCustomScoreboardSignature = null;
        customScoreboardCaptureEpoch++;
        lastDistinctSummaries.clear();
        lastPhysicalTimerLevel = null;

        long minPreTime = gameStartMs - PRE_SESSION_WINDOW_MS;
        Iterator<PacketRecord> it = preSession.iterator();
        while (it.hasNext()) {
            PacketRecord record = it.next();
            if (record.t >= minPreTime) appendSessionRecord(record);
        }
        preSession.clear();
    }

    public synchronized void finishSession() {
        sessionActive = false;
        preSession.clear();
    }

    public synchronized void discardSession() {
        sessionActive = false;
        sessionStartMs = 0L;
        session.clear();
        preSession.clear();
        lastCustomScoreboardSignature = null;
        customScoreboardCaptureEpoch++;
        lastDistinctSummaries.clear();
        lastPhysicalTimerLevel = null;
    }

    public synchronized void captureSummary(String packet, String summary) {
        if (!captureEnabled()) return;
        if (packet == null || packet.trim().isEmpty()) return;
        addRecord(withContext(System.currentTimeMillis(), packet, summary));
    }

    public synchronized void captureSoundPacket(Object packet) {
        if (!captureEnabled() || packet == null) return;
        captureSummary("S29PacketSoundEffect", summarizeSoundPacket(packet));
    }

    private static String summarizeSoundPacket(Object packet) {
        Object sound = invoke(packet, "getSound", "method_11919", "a");
        Object location = invoke(sound, "getLocation", "location", "method_10294");
        String soundName = location == null ? String.valueOf(sound) : String.valueOf(location);
        return "{\"soundName\":" + json(soundName)
                + ",\"source\":" + json(String.valueOf(invoke(packet, "getSource", "method_11917", "b")))
                + ",\"x\":" + number(invoke(packet, "getX", "method_11920", "c"))
                + ",\"y\":" + number(invoke(packet, "getY", "method_11921", "d"))
                + ",\"z\":" + number(invoke(packet, "getZ", "method_11922", "e"))
                + ",\"volume\":" + number(invoke(packet, "getVolume", "method_11923", "f"))
                + ",\"pitch\":" + number(invoke(packet, "getPitch", "method_11924", "g"))
                + ",\"seed\":" + number(invoke(packet, "getSeed", "method_11925", "h"))
                + "}";
    }

    private static Object invoke(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Try the next mapped name.
            }
        }
        return null;
    }

    private static String number(Object value) {
        return value instanceof Number ? String.valueOf(value) : "null";
    }

    public synchronized void captureDistinctSummary(String packet, String summary) {
        if (!captureEnabled()) return;
        if (packet == null || packet.trim().isEmpty()) return;
        long now = System.currentTimeMillis();
        if (!acceptsRecordAt(now)) return;
        String cleanSummary = summary == null ? "{}" : summary;
        String previous = lastDistinctSummaries.get(packet);
        if (cleanSummary.equals(previous)) return;
        lastDistinctSummaries.put(packet, cleanSummary);
        addRecord(withContext(now, packet, cleanSummary));
    }

    public synchronized void captureInboundChat(String source, String text, int type) {
        if (text == null || text.isEmpty()) return;
        captureSummary("S02PacketChat", "{\"class\":\"modern\""
                + ",\"source\":" + json(source)
                + ",\"component\":{\"text\":" + json(text) + "}"
                + ",\"type\":" + type
                + "}");
    }

    public synchronized void captureOutboundChat(String message) {
        if (message == null || message.isEmpty()) return;
        captureSummary("C01PacketChatMessage", "{\"direction\":\"outbound\""
                + ",\"message\":" + json(message)
                + "}");
    }

    public synchronized void captureScoreboardSnapshot(String title, List<String> lines) {
        captureDistinctSummary("ScoreboardSnapshot", summarizeScoreboard(title, lines));
    }

    public synchronized void capturePlayerListSnapshot(String playerSummary, String teamSummary) {
        if (playerSummary != null && !playerSummary.isEmpty()) {
            captureDistinctSummary("S38PacketPlayerListItem", playerSummary);
        }
        if (teamSummary != null && !teamSummary.isEmpty()) {
            captureDistinctSummary("S3EPacketTeams", teamSummary);
        }
    }

    public synchronized void capturePhysicalTimer(int level) {
        if (!captureEnabled() || !acceptsRecordAt(System.currentTimeMillis())) return;
        if (lastPhysicalTimerLevel != null && lastPhysicalTimerLevel.intValue() == level) return;
        lastPhysicalTimerLevel = Integer.valueOf(level);
        captureSummary("S1FPacketSetExperience", "{\"totalExperience\":0"
                + ",\"level\":" + level
                + ",\"experienceBar\":0.0"
                + "}");
    }

    public synchronized void captureCustomScoreboard(String skin, List<ScoreboardLine> rows) {
        if (!captureEnabled() || !acceptsRecordAt(System.currentTimeMillis())) return;
        if (rows == null || rows.isEmpty()) return;
        customScoreboardSignatureBuildCountForTest++;
        String signature = customScoreboardSignature(skin, rows);
        if (signature.equals(lastCustomScoreboardSignature)) return;
        lastCustomScoreboardSignature = signature;
        String summary = summarizeCustomScoreboard(skin, rows);
        addRecord(withContext(System.currentTimeMillis(), "CustomScoreboardSnapshot", summary));
    }

    /**
     * Changes whenever a new raw session needs its own initial custom-scoreboard sample.
     * A negative value tells renderers to avoid snapshot work while tracking is disabled.
     */
    public long customScoreboardCaptureEpoch() {
        return captureEnabled() ? customScoreboardCaptureEpoch : -1L;
    }

    public synchronized void captureOverlayDecision(String overlay, String decision, String detail) {
        if (!captureEnabled()) return;
        if (overlay == null || overlay.trim().isEmpty()) return;
        String summary = "{\"overlay\":" + json(overlay)
                + ",\"decision\":" + json(decision)
                + ",\"detail\":" + json(detail)
                + "}";
        captureDistinctSummary("GtbsOverlayState", summary);
    }

    public synchronized File writeSnapshot(File statsDir, String baseName) {
        if (statsDir == null || baseName == null || baseName.trim().isEmpty()) return null;
        if (session.isEmpty()) return null;
        if (!statsDir.exists()) statsDir.mkdirs();
        File file = new File(statsDir, baseName + ".packets.jsonl");
        writeRecords(file);
        return file;
    }

    public synchronized int recordCountForTest() {
        return session.size();
    }

    synchronized int preSessionCountForTest() {
        return preSession.size();
    }

    synchronized void clearForTest() {
        preSession.clear();
        session.clear();
        sessionActive = false;
        sessionStartMs = 0L;
        lastCustomScoreboardSignature = null;
        customScoreboardCaptureEpoch++;
        customScoreboardSignatureBuildCountForTest = 0;
        lastDistinctSummaries.clear();
        lastPhysicalTimerLevel = null;
    }

    synchronized void addSyntheticRecordForTest(long t, String packet, String summary) {
        appendSessionRecord(new PacketRecord(t, "ROUND_BUILD", Integer.valueOf(1),
                Integer.valueOf(10), Integer.valueOf(60), "Builder", packet, summary));
    }

    synchronized int customScoreboardSignatureBuildCountForTest() {
        return customScoreboardSignatureBuildCountForTest;
    }

    synchronized void addSyntheticSampleForTest(long t, String packet, String summary) {
        addRecord(withContext(t, packet, summary));
    }

    private void addRecord(PacketRecord record) {
        if (record == null || !acceptsRecordAt(record.t)) return;
        if (sessionActive) {
            appendSessionRecord(record);
        } else {
            appendPreSessionRecord(record);
        }
    }

    private static boolean captureEnabled() {
        return GtbStructuresSettings.INSTANCE.modEnabled && GameStats.INSTANCE.isTrackingEnabled();
    }

    private boolean acceptsRecordAt(long timestamp) {
        if (sessionActive) return true;
        GameStats.TrackingState state = GameStats.INSTANCE.getState();
        return state == GameStats.TrackingState.LOBBY
                || state == GameStats.TrackingState.ROUND_PRE
                || state == GameStats.TrackingState.ROUND_BUILD
                || state == GameStats.TrackingState.ROUND_END
                || state == GameStats.TrackingState.POST_GAME
                || state == GameStats.TrackingState.PAUSED
                || GameStats.INSTANCE.isLobbyHandoffActive(timestamp);
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
        if (record != null) session.addLast(record);
    }

    private PacketRecord withContext(long t, String packet, String summary) {
        GameStats stats = GameStats.INSTANCE;
        GameStats.RoundData round = stats.getCurrentRound();
        Integer physicalTimer = stats.getCurrentPhysicalTimerSeconds();
        return new PacketRecord(t,
                stats.getState() == null ? null : stats.getState().name(),
                round == null ? null : Integer.valueOf(round.roundNumber),
                round == null ? null : Integer.valueOf(round.totalRounds),
                physicalTimer,
                round == null ? null : round.builder,
                packet, summary);
    }

    private void writeRecords(File file) {
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            writer.write(metaLine(file));
            writer.newLine();
            for (PacketRecord record : session) {
                writer.write(record.toJson(sessionStartMs));
                writer.newLine();
            }
        } catch (IOException ignored) {
            // Diagnostics only; never break normal stat export.
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    private String metaLine(File file) {
        return "{\"meta\":true"
                + ",\"format\":\"gtb-packets-jsonl-v1\""
                + ",\"createdAt\":" + json(System.currentTimeMillis())
                + ",\"createdText\":" + json(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
                + ",\"file\":" + json(file == null ? null : file.getName())
                + ",\"sessionStart\":" + json(sessionStartMs)
                + ",\"records\":" + session.size()
                + "}";
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

    private static String summarizeScoreboard(String title, List<String> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"title\":").append(json(title))
                .append(",\"titlePlain\":").append(json(plainScoreboardText(title)))
                .append(",\"order\":\"top_to_bottom\"")
                .append(",\"lineCount\":").append(lines == null ? 0 : lines.size())
                .append(",\"lines\":[");
        if (lines != null) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (i > 0) sb.append(",");
                sb.append("{\"formatted\":").append(json(line))
                        .append(",\"plain\":").append(json(plainScoreboardText(line)))
                        .append("}");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String plainScoreboardText(String formatted) {
        if (formatted == null) return "";
        StringBuilder out = new StringBuilder(formatted.length());
        for (int i = 0; i < formatted.length(); i++) {
            char c = formatted.charAt(i);
            if (c == '\u00a7' && i + 1 < formatted.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String json(long value) {
        return String.valueOf(value);
    }

    public static String json(String value) {
        if (value == null) return "null";
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20 || Character.isSurrogate(c)) {
                        out.append(String.format("\\u%04x", Integer.valueOf(c)));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
        return out.toString();
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
            return "{\"t\":" + t
                    + ",\"dt\":" + (sessionStartMs > 0L ? t - sessionStartMs : 0L)
                    + ",\"state\":" + json(state)
                    + ",\"round\":" + (round == null ? "null" : round.toString())
                    + ",\"totalRounds\":" + (totalRounds == null ? "null" : totalRounds.toString())
                    + ",\"physicalTimer\":" + (physicalTimer == null ? "null" : physicalTimer.toString())
                    + ",\"builder\":" + json(builder)
                    + ",\"packet\":" + json(packet)
                    + ",\"summary\":" + summary
                    + "}";
        }
    }
}
