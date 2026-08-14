package com.gtbstructures.chat;

import com.gtbstructures.GtbStructures;
import com.gtbstructures.player.PlayerMetaCache;
import com.gtbstructures.stats.GameStats;

import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class TabNameCompleter {
    public static final TabNameCompleter INSTANCE = new TabNameCompleter();

    private static Field inputFieldRef;

    private GuiChat activeGui;
    private String activePrefix = "";
    private int activeStart = -1;
    private String activeBase = "";
    private List<String> activeMatches = new ArrayList<String>();
    private int activeIndex = 0;

    private TabNameCompleter() {
    }

    @SubscribeEvent
    public void onKeyboardInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!completionEnabledForMaster(GtbStructures.modEnabled)) {
            clearActive();
            return;
        }
        if (!(event.gui instanceof GuiChat)) return;
        if (!Keyboard.getEventKeyState() || Keyboard.getEventKey() != Keyboard.KEY_TAB) return;
        if (!isCompletionState(GameStats.INSTANCE.getState())) return;
        try {
            if (complete((GuiChat) event.gui)) {
                event.setCanceled(true);
            }
        } catch (Throwable ignored) {
            clearActive();
        }
    }

    static List<String> matchingNamesForTest(Collection<String> names, String token, boolean commandHead) {
        return matchingNames(names, token, commandHead);
    }

    static int tokenStartForTest(String text, int cursor) {
        return tokenStart(text, cursor);
    }

    static boolean canCycleTokenForTest(List<String> activeMatches, String activeBase, String token) {
        return canCycleToken(activeMatches, activeBase, token);
    }

    public static boolean completionEnabledForMaster(boolean modEnabled) {
        return modEnabled;
    }

    void clearForTest() {
        clearActive();
    }

    private boolean complete(GuiChat gui) throws IllegalAccessException, NoSuchFieldException {
        GuiTextField input = inputField(gui);
        if (input == null) return false;

        String text = input.getText();
        int cursor = input.getCursorPosition();
        int start = tokenStart(text, cursor);
        String prefix = text.substring(0, start);
        String token = text.substring(start, cursor);
        boolean commandHead = isCommandHead(text, cursor, start);

        if (canCycle(gui, prefix, start, token, commandHead)) {
            String replacement = activeMatches.get(activeIndex++ % activeMatches.size());
            input.deleteFromCursor(start - cursor);
            input.writeText(replacement);
            return true;
        }

        List<String> matches = matchingNames(GameStats.INSTANCE.getPlayers(), token, commandHead);
        if (matches.isEmpty()) {
            clearActive();
            return false;
        }

        String replacement;
        String common = commonPrefix(matches);
        if (common.length() > token.length()) {
            replacement = matchTokenCase(token, common);
            activeMatches = matches;
            activeIndex = 0;
        } else {
            replacement = matches.get(0);
            activeMatches = matches;
            activeIndex = matches.size() > 1 ? 1 : 0;
        }
        activeGui = gui;
        activePrefix = prefix;
        activeStart = start;
        activeBase = token;

        input.deleteFromCursor(start - cursor);
        input.writeText(replacement);
        return true;
    }

    private boolean canCycle(GuiChat gui, String prefix, int start, String token, boolean commandHead) {
        if (commandHead) return false;
        if (gui != activeGui) return false;
        if (start != activeStart || !prefix.equals(activePrefix)) return false;
        if (activeMatches == null || activeMatches.size() <= 1) return false;
        return canCycleToken(activeMatches, activeBase, token);
    }

    private static List<String> matchingNames(Collection<String> names, String token, boolean commandHead) {
        List<String> out = new ArrayList<String>();
        if (commandHead || names == null) return out;
        String lower = token == null ? "" : token.toLowerCase();
        for (String name : names) {
            if (!PlayerMetaCache.isValidMcUsername(name)) continue;
            if (containsIgnoreCase(out, name)) continue;
            if (lower.isEmpty() || name.toLowerCase().startsWith(lower)) {
                out.add(name);
            }
        }
        return out;
    }

    private static int tokenStart(String text, int cursor) {
        if (text == null) return 0;
        int i = Math.max(0, Math.min(cursor, text.length()));
        while (i > 0 && isUsernameChar(text.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    private static boolean isCommandHead(String text, int cursor, int start) {
        if (text == null || text.isEmpty() || text.charAt(0) != '/') return false;
        int limitedCursor = Math.max(0, Math.min(cursor, text.length()));
        return start <= 1 && text.substring(0, limitedCursor).indexOf(' ') < 0;
    }

    private static boolean isCompletionState(GameStats.TrackingState state) {
        return state == GameStats.TrackingState.LOBBY
                || state == GameStats.TrackingState.ROUND_PRE
                || state == GameStats.TrackingState.ROUND_BUILD
                || state == GameStats.TrackingState.ROUND_END
                || state == GameStats.TrackingState.POST_GAME;
    }

    private static boolean isUsernameChar(char c) {
        return c == '_' || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static String commonPrefix(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        String prefix = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            String next = values.get(i);
            int max = Math.min(prefix.length(), next.length());
            int j = 0;
            while (j < max && Character.toLowerCase(prefix.charAt(j)) == Character.toLowerCase(next.charAt(j))) {
                j++;
            }
            prefix = prefix.substring(0, j);
            if (prefix.isEmpty()) break;
        }
        return prefix;
    }

    private static String matchTokenCase(String token, String replacement) {
        if (token == null || token.isEmpty() || replacement == null || replacement.isEmpty()) return replacement;
        boolean upper = true;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isLetter(c) && !Character.isUpperCase(c)) {
                upper = false;
                break;
            }
        }
        return upper ? replacement.toUpperCase() : replacement;
    }

    private static boolean containsIgnoreCase(List<String> values, String value) {
        for (String existing : values) {
            if (existing.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private static boolean canCycleToken(List<String> activeMatches, String activeBase, String token) {
        if (activeMatches == null || activeMatches.size() <= 1) return false;
        String current = token == null ? "" : token;
        if (current.equalsIgnoreCase(activeBase == null ? "" : activeBase)) return true;
        for (String match : activeMatches) {
            if (match.equalsIgnoreCase(current)) return true;
        }
        return false;
    }

    private static GuiTextField inputField(GuiChat gui) throws NoSuchFieldException, IllegalAccessException {
        if (inputFieldRef == null) {
            inputFieldRef = ReflectionHelper.findField(GuiChat.class, "inputField", "field_146415_a");
            inputFieldRef.setAccessible(true);
        }
        Object value = inputFieldRef.get(gui);
        return value instanceof GuiTextField ? (GuiTextField) value : null;
    }

    private void clearActive() {
        activeGui = null;
        activePrefix = "";
        activeStart = -1;
        activeBase = "";
        activeMatches = new ArrayList<String>();
        activeIndex = 0;
    }
}
