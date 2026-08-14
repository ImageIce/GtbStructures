package com.gtbstructures.hotbar;

final class SavedHotbarLogic {
    private SavedHotbarLogic() {
    }

    static boolean shouldHandleHotbarChord(boolean saveDown, boolean loadDown, boolean keyAlreadyDown) {
        if (saveDown == loadDown) return false;
        return !keyAlreadyDown;
    }

    static int hotbarSlotForKey(int eventKey, int noneKey, int[] keyCodes, int hotbarCount) {
        if (eventKey == noneKey || keyCodes == null) return -1;
        int max = Math.min(hotbarCount, keyCodes.length);
        for (int i = 0; i < max; i++) {
            if (keyCodes[i] == eventKey) return i;
        }
        return -1;
    }

    static int containerHotbarSlot(int baseSlot, int hotbarSlot) {
        return baseSlot + hotbarSlot;
    }

    static int flatHotbarIndex(int hotbar, int slot, int hotbarSize) {
        return hotbar * hotbarSize + slot;
    }

    static boolean isEmptyRowHintSlot(int hotbar, int slot, int hotbarSize) {
        return hotbar >= 0 && hotbar < hotbarSize && hotbar == slot;
    }

    static int compactTopTabAt(int localX, int localY, int[] tabXs, int tabY, int tabWidth, int tabHeight) {
        if (tabXs == null || localY < tabY || localY >= tabY + tabHeight) return -1;
        for (int i = tabXs.length - 1; i >= 0; i--) {
            if (localX >= tabXs[i] && localX < tabXs[i] + tabWidth) return i;
        }
        return -1;
    }

    static int savedCellIndex(int mouseX, int mouseY, int guiLeft, int guiTop,
                              float scroll, int itemCount, int gridLeft, int gridTop,
                              int columns, int visibleRows, int slotSize) {
        int localX = mouseX - guiLeft - gridLeft;
        int localY = mouseY - guiTop - gridTop;
        if (localX < 0 || localY < 0) return -1;
        int column = localX / slotSize;
        int row = localY / slotSize;
        if (column < 0 || column >= columns || row < 0 || row >= visibleRows) return -1;
        int scrollRows = Math.max(0, (itemCount + columns - 1) / columns - visibleRows);
        int topRow = (int) ((double) (Math.max(0F, Math.min(1F, scroll)) * scrollRows) + 0.5D);
        int index = column + (row + topRow) * columns;
        return index >= 0 && index < itemCount ? index : -1;
    }
}
