package com.gtbstructures.stats;

final class LobbyLanguageFallback {
    private static final String PLAYER_TOKEN = "%%player%%";
    private static final String[][] MVP_LOBBY_JOIN_TEMPLATES = new String[][] {
            {"en", "%%player%% %%gold%%joined the lobby!"},
            {"zh_cn", "%%player%%%%gold%%进入了大厅！"},
            {"zh_tw", "%%player%%%%gold%% 加入了大廳！"},
            {"cs", "%%gold%%Hráč %%player%% %%gold%%se připojil do lobby!"},
            {"da", "%%player%% %%gold%%tilsluttede sig lobbyen."},
            {"nl", "%%player%% %%gold%%is de lobby binnen gekomen!"},
            {"fi", "%%player%% %%gold%%saapui lobbyyn!"},
            {"fr", "%%player%% %%gold%%a rejoint le lobby!"},
            {"de", "%%player%% %%gold%%hat die Lobby betreten!"},
            {"hu", "%%player%% %%gold%%csatlakozott a lobbyba!"},
            {"it", "%%player%% %%gold%%è entrato nella lobby!"},
            {"ja", "%%player%% %%gold%%がロビーに参加しました！"},
            {"ko", "%%player%%%%gold%% 님이 로ビー에 접속했습니다!"},
            {"no", "%%player%% %%gold%%ble med i lobbyen!"},
            {"pirate", "%%player%% %%gold%%boarded th' port!"},
            {"pl", "%%gold%%Gracz %%player%% %%gold%%wszedł do poczekalni!"},
            {"pt", "%%player%% %%gold%%entrou no lobby!"},
            {"pt_br", "%%player%% %%gold%%entrou no lobby!"},
            {"ro", "%%player%% %%gold%%s-a alăturat lobby-ului!"},
            {"ru", "%%player%% %%gold%%зашёл в лобби!"},
            {"es", "%%gold%%;%%player%% %%gold%%se ha unido al lobby!"},
            {"sv", "%%player%% %%gold%%anslöt sig till lobbyn!"},
            {"tr", "%%player%% %%gold%%lobiye katıldı!"},
            {"uk", "%%gold%%Гравець %%player%% %%gold%%приєднався до лобі!"}
    };

    private boolean detectionOpen = true;

    String confirmFromChat(String cleanText) {
        if (!detectionOpen) return null;
        String langCode = detect(cleanText);
        if (langCode != null) {
            detectionOpen = false;
        }
        return langCode;
    }

    void disableForServerSession() {
        detectionOpen = false;
    }

    void resetForServerSession() {
        detectionOpen = true;
    }

    boolean isDetectionOpenForTest() {
        return detectionOpen;
    }

    static int templateCountForTest() {
        return MVP_LOBBY_JOIN_TEMPLATES.length;
    }

    private static String detect(String cleanText) {
        if (cleanText == null) return null;
        String text = cleanText.trim();
        if (text.isEmpty()) return null;
        for (String[] entry : MVP_LOBBY_JOIN_TEMPLATES) {
            if (matchesTemplate(entry[1], text)) {
                return entry[0];
            }
        }
        return null;
    }

    private static boolean matchesTemplate(String template, String text) {
        return matchesPlainTemplate(template, text) || matchesMvpPlusPlusTemplate(template, text);
    }

    private static boolean matchesMvpPlusPlusTemplate(String template, String text) {
        if (!text.startsWith(">>> ") || !text.endsWith(" <<<")) return false;
        String inner = text.substring(4, text.length() - 4).trim();
        return matchesPlainTemplate(template, inner);
    }

    private static boolean matchesPlainTemplate(String template, String text) {
        String plain = stripTokensExceptPlayer(template);
        int player = plain.indexOf(PLAYER_TOKEN);
        if (player < 0) return false;
        String prefix = plain.substring(0, player);
        String suffix = plain.substring(player + PLAYER_TOKEN.length());
        if (!text.startsWith(prefix) || !text.endsWith(suffix)) return false;
        String between = text.substring(prefix.length(), text.length() - suffix.length()).trim();
        return !between.isEmpty();
    }

    private static String stripTokensExceptPlayer(String template) {
        StringBuilder out = new StringBuilder(template.length());
        for (int i = 0; i < template.length(); ) {
            int start = template.indexOf("%%", i);
            if (start < 0) {
                out.append(template.substring(i));
                break;
            }
            out.append(template, i, start);
            int end = template.indexOf("%%", start + 2);
            if (end < 0) {
                out.append(template.substring(start));
                break;
            }
            String token = template.substring(start, end + 2);
            if (PLAYER_TOKEN.equals(token)) {
                out.append(PLAYER_TOKEN);
            }
            i = end + 2;
        }
        return out.toString();
    }
}
