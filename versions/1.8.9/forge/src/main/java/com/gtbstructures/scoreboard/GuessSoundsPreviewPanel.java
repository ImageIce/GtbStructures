package com.gtbstructures.scoreboard;

import com.gtbstructures.GtbStructures;
import com.gtbstructures.sound.GuessSoundController;
import com.gtbstructures.sound.GuessSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.Collections;
import java.util.List;

/** Sound settings rendered inside the preview editor's existing menu. */
final class GuessSoundsPreviewPanel {
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9EA8B5;
    private boolean opened;
    private int tab = 2;
    private int scroll;
    private boolean repeatEventsChanged;
    private boolean previousRepeatEvents;
    private GuiTextField search;
    private List<GuessSounds.Choice> filtered = Collections.emptyList();
    private GuessSoundsUiLogic.Layout layout;

    boolean isOpen() { return opened; }
    int scroll() { return scroll; }
    int itemCount() { return filtered.size(); }
    boolean isSearchFocused() { return search != null && search.isFocused(); }
    void blurSearch() { if (search != null) search.setFocused(false); }

    void open() {
        opened = true;
        tab = 2;
        scroll = 0;
        search = null;
        filtered = Collections.emptyList();
    }

    void close() {
        opened = false;
        if (search != null) search.setFocused(false);
        if (repeatEventsChanged) Keyboard.enableRepeatEvents(previousRepeatEvents);
        repeatEventsChanged = false;
    }

    void setLayout(Minecraft mc, GuessSoundsUiLogic.Layout next) {
        layout = next;
        if (search == null) {
            search = new GuiTextField(9101, mc.fontRendererObj, next.searchX, next.searchY,
                    next.searchWidth, 16);
            search.setMaxStringLength(48);
            previousRepeatEvents = Keyboard.areRepeatEventsEnabled();
            Keyboard.enableRepeatEvents(true);
            repeatEventsChanged = true;
        } else {
            search.xPosition = next.searchX;
            search.yPosition = next.searchY;
            search.width = next.searchWidth;
        }
        refreshFilter();
    }

    void draw(Minecraft mc, ScaledResolution sr, GuessSoundsUiLogic.Layout next,
              int mouseX, int mouseY) {
        setLayout(mc, next);
        for (int i = 0; i < 3; i++) {
            new GuiButton(9200 + i, layout.searchX + i * GuessSoundsUiLogic.TAB_STEP,
                    layout.tabsY, GuessSoundsUiLogic.TAB_WIDTH, GuessSoundsUiLogic.BUTTON_HEIGHT,
                    GuessSoundsUiLogic.tabCaption(i)).drawButton(mc, mouseX, mouseY);
        }
        new GuiButton(9300, layout.testX, layout.tabsY, GuessSoundsUiLogic.TEST_WIDTH,
                GuessSoundsUiLogic.BUTTON_HEIGHT, "\u00a7fTest").drawButton(mc, mouseX, mouseY);
        new GuiButton(9301, layout.resetX, layout.tabsY, GuessSoundsUiLogic.RESET_WIDTH,
                GuessSoundsUiLogic.BUTTON_HEIGHT, "\u00a7fReset").drawButton(mc, mouseX, mouseY);
        int tabX = layout.searchX + tab * GuessSoundsUiLogic.TAB_STEP;
        Gui.drawRect(tabX, layout.tabsY + 18, tabX + GuessSoundsUiLogic.TAB_WIDTH,
                layout.tabsY + 20, TEXT);
        search.updateCursorCounter();
        search.drawTextBox();
        if (search.getText().isEmpty() && !search.isFocused()) {
            mc.fontRendererObj.drawStringWithShadow("Search sounds", layout.searchX + 4,
                    layout.searchY + 4, MUTED);
        }
        drawRows(mc, sr, mouseX, mouseY);
    }

    private void drawRows(Minecraft mc, ScaledResolution sr, int mouseX, int mouseY) {
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        int scale = sr.getScaleFactor();
        GL11.glScissor(layout.listX * scale,
                mc.displayHeight - (layout.listY + layout.listHeight) * scale,
                layout.searchWidth * scale, layout.listHeight * scale);
        try {
            for (int row = 0; row < layout.visibleRows && scroll + row < filtered.size(); row++) {
                GuessSounds.Choice choice = filtered.get(scroll + row);
                int y = layout.listY + row * GuessSoundsUiLogic.ROW_HEIGHT;
                boolean selected = choice.id.equals(GuessSounds.getSound(tab + 1));
                if (selected || GuessSoundsUiLogic.contains(mouseX, mouseY, layout.listX, y,
                        layout.listWidth, GuessSoundsUiLogic.ROW_HEIGHT)) {
                    Gui.drawRect(layout.listX, y, layout.listX + layout.listWidth,
                            y + GuessSoundsUiLogic.ROW_HEIGHT - 1,
                            selected ? 0x80506A8A : 0x304A5260);
                }
                RenderHelper.enableGUIStandardItemLighting();
                mc.getRenderItem().renderItemAndEffectIntoGUI(choice.icon, layout.listX + 2, y + 2);
                RenderHelper.disableStandardItemLighting();
                GlStateManager.disableDepth();
                GlStateManager.color(1F, 1F, 1F, 1F);
                int textX = layout.listX + 22;
                int textWidth = Math.max(1, layout.listWidth - 24);
                mc.fontRendererObj.drawStringWithShadow(
                        mc.fontRendererObj.trimStringToWidth(choice.label, textWidth), textX, y + 2, TEXT);
                mc.fontRendererObj.drawStringWithShadow(
                        mc.fontRendererObj.trimStringToWidth(choice.id, textWidth), textX, y + 11, MUTED);
            }
            drawScrollbar();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private void drawScrollbar() {
        int max = GuessSoundsUiLogic.maxScroll(filtered.size(), layout.visibleRows);
        if (max <= 0 || layout.listHeight <= 0) return;
        int thumb = Math.min(layout.listHeight,
                Math.max(12, layout.listHeight * layout.visibleRows / filtered.size()));
        int y = layout.listY + (layout.listHeight - thumb) * scroll / max;
        int x = layout.x + layout.width - 10;
        Gui.drawRect(x, layout.listY, x + 4, layout.listY + layout.listHeight, 0x442A313B);
        Gui.drawRect(x, y, x + 4, y + thumb, MUTED);
    }

    void mouseClicked(Minecraft mc, GuessSoundsUiLogic.Layout next, int mouseX, int mouseY) {
        setLayout(mc, next);
        search.mouseClicked(mouseX, mouseY, 0);
        int clickedTab = GuessSoundsUiLogic.tabAt(layout, mouseX, mouseY);
        if (clickedTab >= 0) {
            tab = clickedTab;
            scroll = 0;
        } else if (GuessSoundsUiLogic.contains(mouseX, mouseY, layout.testX, layout.tabsY,
                GuessSoundsUiLogic.TEST_WIDTH, GuessSoundsUiLogic.BUTTON_HEIGHT)) {
            GuessSoundController.INSTANCE.preview(tab + 1);
        } else if (GuessSoundsUiLogic.contains(mouseX, mouseY, layout.resetX, layout.tabsY,
                GuessSoundsUiLogic.RESET_WIDTH, GuessSoundsUiLogic.BUTTON_HEIGHT)) {
            GuessSounds.setSound(tab + 1, GuessSounds.DEFAULT_SOUND);
            GtbStructures.saveConfig();
        } else if (GuessSoundsUiLogic.contains(mouseX, mouseY, layout.listX, layout.listY,
                layout.listWidth, layout.listHeight)) {
            int index = scroll + (mouseY - layout.listY) / GuessSoundsUiLogic.ROW_HEIGHT;
            if (index < filtered.size()) {
                GuessSounds.setSound(tab + 1, filtered.get(index).id);
                GtbStructures.saveConfig();
            }
        }
    }

    void scrollBy(int rows) {
        if (layout == null) return;
        scroll = GuessSoundsUiLogic.clampScroll(scroll + rows, filtered.size(), layout.visibleRows);
    }

    void setScrollFromScrollbar(int value, GuessSoundsUiLogic.Layout next) {
        scroll = GuessSoundsUiLogic.clampScroll(value, filtered.size(), next.visibleRows);
    }

    boolean onKeyTyped(char typedChar, int keyCode) {
        if (!opened) return false;
        if (keyCode == Keyboard.KEY_ESCAPE) close();
        else if (search != null && search.textboxKeyTyped(typedChar, keyCode)) {
            scroll = 0;
            refreshFilter();
        }
        return true;
    }

    private void refreshFilter() {
        filtered = GuessSoundsUiLogic.filter(GuessSounds.choices(), search.getText());
        scroll = GuessSoundsUiLogic.clampScroll(scroll, filtered.size(), layout.visibleRows);
    }

}
