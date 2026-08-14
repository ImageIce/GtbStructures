package com.gtbstructures.player;

import com.gtbstructures.diagnostics.RawPacketRecorder;
import com.gtbstructures.stats.GameStats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ModernPlayerListLogic {
    private ModernPlayerListLogic() {
    }

    public static void applySnapshot(Snapshot snapshot, Set<String> previousRoster) {
        if (snapshot == null) snapshot = new Snapshot();

        RawPacketRecorder.INSTANCE.capturePlayerListSnapshot(
                snapshot.playerListRawSummary(), snapshot.teamRawSummary());

        Set<String> currentLower = new LinkedHashSet<String>();
        for (Map.Entry<String, String> entry : snapshot.displayNames.entrySet()) {
            String ign = entry.getKey();
            if (!PlayerMetaCache.isValidMcUsername(ign)) continue;
            currentLower.add(ign.toLowerCase(Locale.ROOT));
            PlayerMetaCache.INSTANCE.observePacketRosterAdd(ign);
            GameStats.INSTANCE.onPacketRosterAdd(ign);
            String display = entry.getValue();
            if (display != null && !display.isEmpty()) {
                PlayerMetaCache.INSTANCE.captureDisplayName(ign, display);
            }
        }

        for (TeamObservation team : snapshot.teams) {
            PlayerMetaCache.INSTANCE.observeTeamPacket(team.teamName, team.prefix,
                    team.suffix, team.members, team.mode);
        }

        if (previousRoster != null) {
            List<String> removed = new ArrayList<String>();
            for (String previous : previousRoster) {
                if (previous == null) continue;
                if (!currentLower.contains(previous.toLowerCase(Locale.ROOT))) {
                    removed.add(previous);
                }
            }
            for (String ign : removed) {
                String removedIgn = PlayerMetaCache.INSTANCE.observePacketRosterRemove(ign);
                GameStats.INSTANCE.onPacketRosterRemove(removedIgn == null ? ign : removedIgn);
            }
            previousRoster.clear();
            previousRoster.addAll(snapshot.displayNames.keySet());
        }
        GameStats.INSTANCE.onPacketRosterSnapshotComplete();
    }

    public static void observePhysicalTimer(int level) {
        GameStats.INSTANCE.onPhysicalTimerUpdate(0.0F, 0, level);
        RawPacketRecorder.INSTANCE.capturePhysicalTimer(level);
    }

    public static final class Snapshot {
        private final LinkedHashMap<String, String> displayNames = new LinkedHashMap<String, String>();
        private final List<TeamObservation> teams = new ArrayList<TeamObservation>();

        public Snapshot addPlayer(String ign) {
            return addPlayer(ign, null);
        }

        public Snapshot addPlayer(String ign, String displayName) {
            if (!PlayerMetaCache.isValidMcUsername(ign)) return this;
            if (!displayNames.containsKey(ign)) {
                displayNames.put(ign, displayName);
            } else if (displayName != null && !displayName.isEmpty()) {
                displayNames.put(ign, displayName);
            }
            return this;
        }

        public Snapshot addTeam(String teamName, String prefix, String suffix,
                                Collection<String> members, int mode) {
            if (teamName == null || teamName.isEmpty()) return this;
            List<String> cleanMembers = new ArrayList<String>();
            if (members != null) {
                for (String member : members) {
                    if (PlayerMetaCache.isValidMcUsername(member)) cleanMembers.add(member);
                }
            }
            teams.add(new TeamObservation(teamName, prefix, suffix, cleanMembers, mode));
            return this;
        }

        public String playerListRawSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"action\":\"SNAPSHOT\"")
                    .append(",\"entries\":[");
            int i = 0;
            for (Map.Entry<String, String> entry : displayNames.entrySet()) {
                if (i++ > 0) sb.append(",");
                sb.append("{\"name\":").append(RawPacketRecorder.json(entry.getKey()))
                        .append(",\"displayName\":").append(RawPacketRecorder.json(entry.getValue()))
                        .append("}");
            }
            sb.append("]}");
            return sb.toString();
        }

        public String teamRawSummary() {
            if (teams.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            sb.append("{\"action\":\"SNAPSHOT\"")
                    .append(",\"teams\":[");
            for (int i = 0; i < teams.size(); i++) {
                TeamObservation team = teams.get(i);
                if (i > 0) sb.append(",");
                sb.append("{\"teamName\":").append(RawPacketRecorder.json(team.teamName))
                        .append(",\"mode\":").append(team.mode)
                        .append(",\"prefix\":").append(RawPacketRecorder.json(team.prefix))
                        .append(",\"suffix\":").append(RawPacketRecorder.json(team.suffix))
                        .append(",\"members\":[");
                for (int j = 0; j < team.members.size(); j++) {
                    if (j > 0) sb.append(",");
                    sb.append(RawPacketRecorder.json(team.members.get(j)));
                }
                sb.append("]}");
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    private static final class TeamObservation {
        private final String teamName;
        private final String prefix;
        private final String suffix;
        private final List<String> members;
        private final int mode;

        private TeamObservation(String teamName, String prefix, String suffix,
                                List<String> members, int mode) {
            this.teamName = teamName;
            this.prefix = prefix;
            this.suffix = suffix;
            this.members = members;
            this.mode = mode;
        }
    }
}
