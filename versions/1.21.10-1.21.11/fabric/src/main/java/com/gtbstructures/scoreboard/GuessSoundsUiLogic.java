package com.gtbstructures.scoreboard;

import com.gtbstructures.sound.GuessSounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure layout and filtering rules for the Guess Sounds menu subview. */
final class GuessSoundsUiLogic {
    static final int TAB_COUNT = 3;
    static final int ROW_HEIGHT = 20;
    static final int TAB_WIDTH = 54;
    static final int TAB_STEP = 58;
    static final int BUTTON_HEIGHT = 18;
    static final int TEST_WIDTH = 36;
    static final int RESET_WIDTH = 42;

    static final class Choice {
        final String id, label;
        Choice(String id, String label) { this.id = id; this.label = label; }
    }

    private GuessSoundsUiLogic() { }

    static int tabForPoints(int points) {
        return points < 1 ? 0 : points > TAB_COUNT ? TAB_COUNT - 1 : points - 1;
    }

    static float clampVolume(float value) {
        if (Float.isNaN(value) || value < 0F) return 0F;
        return Math.min(GuessSounds.MAX_VOLUME, value);
    }

    static int maxScroll(int itemCount, int visibleRows) {
        return Math.max(0, itemCount - Math.max(1, visibleRows));
    }

    static int clampScroll(int scroll, int itemCount, int visibleRows) {
        return Math.max(0, Math.min(maxScroll(itemCount, visibleRows), scroll));
    }

    static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    static int tabAt(Layout layout, int mouseX, int mouseY) {
        for (int tab = 0; tab < TAB_COUNT; tab++) {
            if (contains(mouseX, mouseY, layout.searchX + tab * TAB_STEP, layout.tabsY,
                    TAB_WIDTH, BUTTON_HEIGHT)) return tab;
        }
        return -1;
    }

    static String tabCaption(int tab) {
        return (tab == 0 ? "\u00a7a" : tab == 1 ? "\u00a7e" : "\u00a7c")
                + (tab + 1) + "\u00a7f" + (tab == 0 ? " point" : " points");
    }

    /** All controls derive from the original menu rectangle, including its footer. */
    static final class Layout {
        final int x, y, width, height;
        final int tabsY, testX, resetX;
        final int searchX, searchY, searchWidth;
        final int listX, listY, listWidth, listHeight, visibleRows;
        final int volumeY;

        Layout(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            searchX = x + 6;
            searchWidth = Math.max(1, width - 12);
            tabsY = y + 21;
            resetX = x + width - 6 - RESET_WIDTH;
            testX = resetX - 4 - TEST_WIDTH;
            searchY = tabsY + BUTTON_HEIGHT + 4;
            listX = searchX;
            listY = searchY + 22;
            listWidth = Math.max(1, searchWidth - 6);
            volumeY = y + height - 24;
            visibleRows = Math.max(0, (volumeY - listY - 6) / ROW_HEIGHT);
            listHeight = visibleRows * ROW_HEIGHT;
        }
    }

    static List<Choice> filter(List<Choice> choices, String query) {
        List<Choice> result = new ArrayList<Choice>();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (Choice choice : choices) {
            if (needle.isEmpty() || choice.id.toLowerCase(Locale.ROOT).contains(needle)
                    || choice.label.toLowerCase(Locale.ROOT).contains(needle)) result.add(choice);
        }
        return result;
    }
}
