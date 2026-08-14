package com.gtbstructures.scoreboard;

import com.gtbstructures.chat.ChatHeader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class PreviewScreenController implements InterfacePreview.ScreenController {
    private static double pendingScrollDelta;

    @Override
    public boolean isInGame() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null && mc.player != null;
    }

    @Override
    public String currentSessionKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return "";
        return Integer.toHexString(System.identityHashCode(mc.level))
                + ":" + Integer.toHexString(System.identityHashCode(mc.player));
    }

    @Override
    public boolean isPreviewScreenOpen() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.gui.screen() instanceof PreviewScreen;
    }

    @Override
    public void openPreviewScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui.screen() instanceof PreviewScreen) return;
        clearScrollDelta();
        cancelHeaderEditing();
        mc.gui.setScreen(new PreviewScreen());
    }

    @Override
    public void closePreviewScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gui.screen() instanceof PreviewScreen) {
            cancelHeaderEditing();
            mc.gui.setScreen(null);
        }
        cancelHeaderEditing();
        clearScrollDelta();
    }

    static synchronized double consumeScrollDelta() {
        double delta = pendingScrollDelta;
        pendingScrollDelta = 0.0D;
        return delta;
    }

    static synchronized void queueScrollForTest(double delta) {
        queueScrollDelta(delta);
    }

    private static synchronized void queueScrollDelta(double delta) {
        pendingScrollDelta += delta;
    }

    private static synchronized void clearScrollDelta() {
        pendingScrollDelta = 0.0D;
    }

    static void beginHeaderEditing(String value) {
        PreviewScreen screen = previewScreen();
        if (screen != null) screen.beginHeaderEditing(value);
    }

    static String finishHeaderEditing() {
        PreviewScreen screen = previewScreen();
        return screen == null ? null : screen.finishHeaderEditing();
    }

    static void cancelHeaderEditing() {
        PreviewScreen screen = previewScreen();
        if (screen != null) screen.cancelHeaderEditing();
    }

    static String headerEditingValue(String fallback) {
        PreviewScreen screen = previewScreen();
        return screen == null ? fallback : screen.headerEditingValue(fallback);
    }

    static void positionHeaderEditor(int x, int y, int width, int height) {
        PreviewScreen screen = previewScreen();
        if (screen != null) screen.positionHeaderEditor(x, y, width, height);
    }

    private static PreviewScreen previewScreen() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.gui.screen() instanceof PreviewScreen
                ? (PreviewScreen) mc.gui.screen() : null;
    }

    private static final class PreviewScreen extends Screen {
        private EditBox headerEditBox;
        private boolean headerEditNeedsLayoutRefresh;

        private PreviewScreen() {
            super(Component.empty());
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            // The preview editor is a HUD layer rendered immediately before this screen.
            // A normal Screen background would blur and cover those controls.
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (headerEditBox != null) {
                if (PreviewLayoutEditor.INSTANCE.onHeaderKeyPressed(event.key())) return true;
                if (headerEditBox.keyPressed(event)) return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                InterfacePreview.INSTANCE.disableSilently();
                return true;
            }
            return super.keyPressed(event);
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            if (headerEditBox != null) {
                String value = headerEditBox.getValue();
                int cursor = Math.max(0, Math.min(headerEditBox.getCursorPosition(), value.length()));
                String candidate = value.substring(0, cursor) + event.codepointAsString()
                        + value.substring(cursor);
                if (!validHeaderEditCandidate(candidate)) return true;
                if (headerEditBox.charTyped(event)) return true;
            }
            return super.charTyped(event);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY,
                                     double horizontalAmount, double verticalAmount) {
            queueScrollDelta(verticalAmount);
            return true;
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        private void beginHeaderEditing(String value) {
            if (headerEditBox == null) {
                headerEditBox = new EditBox(font, -1000, -1000, 1, 1, Component.empty());
                headerEditBox.setMaxLength(256);
                addRenderableWidget(headerEditBox);
                setInitialFocus(headerEditBox);
            }
            headerEditBox.setValue(value == null ? "" : value);
            headerEditBox.setCursorPosition(headerEditBox.getValue().length());
            headerEditBox.setFocused(true);
            headerEditNeedsLayoutRefresh = true;
        }

        private String finishHeaderEditing() {
            if (headerEditBox == null) return null;
            String value = headerEditBox.getValue();
            cancelHeaderEditing();
            return value;
        }

        private void cancelHeaderEditing() {
            if (headerEditBox == null) return;
            removeWidget(headerEditBox);
            clearFocus();
            headerEditBox = null;
            headerEditNeedsLayoutRefresh = false;
        }

        private String headerEditingValue(String fallback) {
            return headerEditBox == null ? fallback : headerEditBox.getValue();
        }

        private void positionHeaderEditor(int x, int y, int width, int height) {
            if (headerEditBox == null) return;
            headerEditBox.setWidth(Math.max(1, width));
            headerEditBox.setHeight(Math.max(1, height));
            headerEditBox.setX(x);
            headerEditBox.setY(y);
            if (headerEditNeedsLayoutRefresh) {
                headerEditBox.setCursorPosition(headerEditBox.getValue().length());
                headerEditNeedsLayoutRefresh = false;
            }
        }
    }

    private static boolean validHeaderEditCandidate(String value) {
        if (ChatHeader.visibleLength(value) <= ChatHeader.MAX_VISIBLE_LENGTH) return true;
        if (value == null || value.isEmpty()) return false;
        char last = value.charAt(value.length() - 1);
        return (last == '&' || last == '\u00a7')
                && ChatHeader.visibleLength(value.substring(0, value.length() - 1))
                <= ChatHeader.MAX_VISIBLE_LENGTH;
    }
}
