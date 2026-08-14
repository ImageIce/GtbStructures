package com.gtbstructures.scoreboard;

import com.gtbstructures.overlay.ForgeHudLayerBridge;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

public final class PreviewEditorOverlayRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(PreviewEditorOverlayRenderer.class);

    private PreviewEditorOverlayRenderer() {
    }

    public static void registerBackdrop() {
        AddGuiOverlayLayersEvent.BUS.addListener(PreviewEditorOverlayRenderer::addBackdropLayer);
    }

    public static void register() {
        AddGuiOverlayLayersEvent.BUS.addListener(PreviewEditorOverlayRenderer::addLayer);
    }

    private static void addBackdropLayer(AddGuiOverlayLayersEvent event) {
        ForgeHudLayerBridge.addBelowChat(event.getLayeredDraw(), "preview_backdrop",
                PreviewEditorOverlayRenderer::renderBackdrop);
    }

    private static void addLayer(AddGuiOverlayLayersEvent event) {
        ForgeHudLayerBridge.addAboveChat(event.getLayeredDraw(), "preview_editor",
                PreviewEditorOverlayRenderer::render);
    }

    private static void renderBackdrop(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!InterfacePreview.isActive()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.font == null || mc.getWindow() == null) return;
            Window window = mc.getWindow();
            PreviewLayoutEditor.renderBackdrop(
                    new ForgeEditorRenderer(graphics, mc.font),
                    window.getGuiScaledWidth(),
                    window.getGuiScaledHeight());
        } catch (RuntimeException e) {
            LOGGER.warn("Unable to render GtbStructures preview backdrop", e);
        }
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!InterfacePreview.isActive()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.font == null || mc.getWindow() == null) return;
            Window window = mc.getWindow();
            PreviewLayoutEditor.render(
                    new ForgeEditorRenderer(graphics, mc.font),
                    inputState(window),
                    window.getGuiScaledWidth(),
                    window.getGuiScaledHeight());
        } catch (RuntimeException e) {
            LOGGER.warn("Unable to render GtbStructures preview editor", e);
        }
    }

    private static PreviewLayoutEditor.InputState inputState(Window window) {
        long handle = window.handle();
        double[] cursorX = new double[1];
        double[] cursorY = new double[1];
        GLFW.glfwGetCursorPos(handle, cursorX, cursorY);
        int mouseX = scale((int) Math.floor(cursorX[0]),
                Math.max(1, window.getScreenWidth()), window.getGuiScaledWidth());
        int mouseY = scale((int) Math.floor(cursorY[0]),
                Math.max(1, window.getScreenHeight()), window.getGuiScaledHeight());
        return new PreviewLayoutEditor.InputState(
                mouseX,
                mouseY,
                keyMouse(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT),
                keyMouse(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT),
                key(handle, GLFW.GLFW_KEY_ESCAPE),
                key(handle, GLFW.GLFW_KEY_LEFT_SHIFT)
                        || key(handle, GLFW.GLFW_KEY_RIGHT_SHIFT)
                        || key(handle, GLFW.GLFW_KEY_LEFT_CONTROL)
                        || key(handle, GLFW.GLFW_KEY_RIGHT_CONTROL),
                key(handle, GLFW.GLFW_KEY_LEFT),
                key(handle, GLFW.GLFW_KEY_RIGHT),
                key(handle, GLFW.GLFW_KEY_UP),
                key(handle, GLFW.GLFW_KEY_DOWN),
                PreviewScreenController.consumeScrollDelta());
    }

    private static int scale(int value, int sourceSize, int targetSize) {
        return Math.max(0, Math.min(targetSize - 1, value * targetSize / Math.max(1, sourceSize)));
    }

    private static boolean keyMouse(long handle, int button) {
        return GLFW.glfwGetMouseButton(handle, button) == GLFW.GLFW_PRESS;
    }

    private static boolean key(long handle, int key) {
        return GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
    }

    private static boolean renderButtonWidget(GuiGraphics graphics, String text, int x, int y,
                                              int width, int height, int mouseX, int mouseY)
            throws ReflectiveOperationException {
        Object button = createButtonWidget(text, x, y, width, height);
        if (button == null) return false;
        for (Method method : button.getClass().getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getReturnType().equals(Void.TYPE)
                    && parameters.length == 4
                    && parameters[0].isInstance(graphics)
                    && intParameter(parameters[1])
                    && intParameter(parameters[2])
                    && floatParameter(parameters[3])) {
                method.invoke(button, graphics, Integer.valueOf(mouseX),
                        Integer.valueOf(mouseY), Float.valueOf(0.0F));
                return true;
            }
        }
        return false;
    }

    private static Object createButtonWidget(String text, int x, int y, int width, int height)
            throws ReflectiveOperationException {
        Class<?> buttonClass = Button.class;
        Object message = Component.literal(text == null ? "" : text);
        for (Method factory : buttonClass.getMethods()) {
            if (!Modifier.isStatic(factory.getModifiers())
                    || factory.getParameterCount() != 2
                    || !factory.getParameterTypes()[0].isInstance(message)
                    || !factory.getParameterTypes()[1].isInterface()) {
                continue;
            }
            Class<?> pressAction = factory.getParameterTypes()[1];
            Object action = Proxy.newProxyInstance(
                    PreviewEditorOverlayRenderer.class.getClassLoader(),
                    new Class<?>[] {pressAction},
                    (proxy, method, args) -> null);
            Object builder = factory.invoke(null, message, action);
            if (builder == null) continue;
            Object sized = applyButtonDimensions(builder, x, y, width, height);
            Object button = buildButton(buttonClass, sized == null ? builder : sized);
            if (button != null) return button;
        }
        return null;
    }

    private static Object applyButtonDimensions(Object builder, int x, int y, int width, int height)
            throws ReflectiveOperationException {
        for (Method method : builder.getClass().getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 4
                    && intParameter(parameters[0])
                    && intParameter(parameters[1])
                    && intParameter(parameters[2])
                    && intParameter(parameters[3])) {
                Object result = method.invoke(builder, Integer.valueOf(x), Integer.valueOf(y),
                        Integer.valueOf(width), Integer.valueOf(height));
                if (result != null && builder.getClass().isInstance(result)) return result;
            }
        }
        return builder;
    }

    private static Object buildButton(Class<?> buttonClass, Object builder)
            throws ReflectiveOperationException {
        for (Method method : builder.getClass().getMethods()) {
            if (method.getParameterCount() == 0
                    && buttonClass.isAssignableFrom(method.getReturnType())) {
                return method.invoke(builder);
            }
        }
        return null;
    }

    private static boolean intParameter(Class<?> type) {
        return type.equals(Integer.TYPE) || type.equals(Integer.class);
    }

    private static boolean floatParameter(Class<?> type) {
        return type.equals(Float.TYPE) || type.equals(Float.class);
    }

    private static int opaqueColor(int color) {
        return (color & 0xFF000000) == 0 ? 0xFF000000 | color : color;
    }

    private static final class ForgeEditorRenderer implements PreviewLayoutEditor.EditorRenderer {
        private final GuiGraphics graphics;
        private final Font font;

        private ForgeEditorRenderer(GuiGraphics graphics, Font font) {
            this.graphics = graphics;
            this.font = font;
        }

        @Override
        public int fontHeight() {
            return font.lineHeight;
        }

        @Override
        public int textWidth(String text) {
            return font.width(text == null ? "" : text);
        }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            graphics.fill(left, top, right, bottom, color);
        }

        @Override
        public void drawString(String text, int x, int y, int color, boolean shadow) {
            graphics.drawString(font, text == null ? "" : text,
                    x, y, opaqueColor(color), shadow);
        }

        @Override
        public boolean drawVanillaButton(int x, int y, int width, int height,
                                         String text, int mouseX, int mouseY) {
            try {
                return renderButtonWidget(graphics, text, x, y, width, height, mouseX, mouseY);
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.warn("Unable to render native GtbStructures preview button", e);
                return false;
            }
        }

        @Override
        public void enableScissor(int left, int top, int right, int bottom) {
            graphics.enableScissor(left, top, right, bottom);
        }

        @Override
        public void disableScissor() {
            graphics.disableScissor();
        }
    }
}
