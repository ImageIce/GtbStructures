package com.gtbstructures.scoreboard;

import com.gtbstructures.overlay.FabricHudElementBridge;
import com.gtbstructures.sound.SoundIconMapping;
import com.gtbstructures.util.FabricInputBridge;
import com.gtbstructures.util.RuntimeReflectionCache;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

public final class PreviewEditorOverlayRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(PreviewEditorOverlayRenderer.class);
    private static final String MINECRAFT_CLIENT = "net.minecraft.class_310";
    private static final String TEXT = "net.minecraft.class_2561";
    private static final String TEXT_RENDERER = "net.minecraft.class_327";
    private static final String DRAW_CONTEXT = "net.minecraft.class_332";
    private static final String WINDOW = "net.minecraft.class_1041";
    private static final String BUTTON_WIDGET = "net.minecraft.class_4185";
    private static final String DRAWABLE = "net.minecraft.class_4068";
    private static final RuntimeReflectionCache.InvocationSite DRAW_ITEM =
            RuntimeReflectionCache.instanceSite("drawItem", "renderItem", "item", "method_51427");
    private static boolean itemRenderWarningLogged;

    private PreviewEditorOverlayRenderer() {
    }

    public static void registerBackdrop() {
        if (FabricHudElementBridge.registerBeforeChat("preview_backdrop",
                PreviewEditorOverlayRenderer::renderBackdrop)) {
            return;
        }
        try {
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback");
            Object event = callbackClass.getField("EVENT").get(null);
            Object callback = Proxy.newProxyInstance(
                    PreviewEditorOverlayRenderer.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onHudRender") && args != null && args.length >= 1) {
                            renderBackdrop(args[0]);
                            return null;
                        }
                        if (method.getName().equals("toString")) return "GtbStructures preview backdrop";
                        return null;
                    });
            Method register = event.getClass().getMethod("register", Object.class);
            register.setAccessible(true);
            register.invoke(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric preview backdrop", e);
        }
    }

    public static void register() {
        if (FabricHudElementBridge.registerLast("preview_editor",
                PreviewEditorOverlayRenderer::renderEditor)) {
            return;
        }
        try {
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback");
            Object event = callbackClass.getField("EVENT").get(null);
            Object callback = Proxy.newProxyInstance(
                    PreviewEditorOverlayRenderer.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onHudRender") && args != null && args.length >= 1) {
                            renderEditor(args[0]);
                            return null;
                        }
                        if (method.getName().equals("toString")) return "GtbStructures preview editor overlay";
                        return null;
                    });
            Method register = event.getClass().getMethod("register", Object.class);
            register.setAccessible(true);
            register.invoke(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric preview editor overlay", e);
        }
    }

    private static void renderBackdrop(Object graphics) {
        if (!InterfacePreview.isActive()) return;
        try {
            Object client = minecraftClient();
            Object font = fieldAny(client, "textRenderer", "font",
                    minecraftFieldName(MINECRAFT_CLIENT, "field_1772", "Lnet/minecraft/class_327;"),
                    "field_1772");
            Object window = invokeAny(client, "getWindow",
                    minecraftMethodName(MINECRAFT_CLIENT, "method_22683", "()Lnet/minecraft/class_1041;"),
                    "method_22683");
            if (graphics == null || font == null || window == null) return;
            int scaledWidth = intAny(window, 0, "getScaledWidth", "getGuiScaledWidth",
                    minecraftMethodName(WINDOW, "method_4486", "()I"), "method_4486");
            int scaledHeight = intAny(window, 0, "getScaledHeight", "getGuiScaledHeight",
                    minecraftMethodName(WINDOW, "method_4502", "()I"), "method_4502");
            if (scaledWidth <= 0 || scaledHeight <= 0) return;
            PreviewLayoutEditor.renderBackdrop(new FabricEditorRenderer(graphics, font),
                    scaledWidth, scaledHeight);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to render GtbStructures Fabric preview backdrop", e);
        }
    }

    private static void renderEditor(Object graphics) {
        if (!InterfacePreview.isActive()) return;
        try {
            Object client = minecraftClient();
            Object font = fieldAny(client, "textRenderer", "font",
                    minecraftFieldName(MINECRAFT_CLIENT, "field_1772", "Lnet/minecraft/class_327;"),
                    "field_1772");
            Object window = invokeAny(client, "getWindow",
                    minecraftMethodName(MINECRAFT_CLIENT, "method_22683", "()Lnet/minecraft/class_1041;"),
                    "method_22683");
            if (graphics == null || font == null || window == null) return;
            int scaledWidth = intAny(window, 0, "getScaledWidth", "getGuiScaledWidth",
                    minecraftMethodName(WINDOW, "method_4486", "()I"), "method_4486");
            int scaledHeight = intAny(window, 0, "getScaledHeight", "getGuiScaledHeight",
                    minecraftMethodName(WINDOW, "method_4502", "()I"), "method_4502");
            if (scaledWidth <= 0 || scaledHeight <= 0) return;
            PreviewLayoutEditor.render(
                    new FabricEditorRenderer(graphics, font),
                    inputState(client, window, scaledWidth, scaledHeight),
                    scaledWidth,
                    scaledHeight);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to render GtbStructures Fabric preview editor", e);
        }
    }

    private static PreviewLayoutEditor.InputState inputState(Object client, Object window,
                                                              int scaledWidth, int scaledHeight)
            throws ReflectiveOperationException {
        FabricInputBridge.CursorPosition cursor = FabricInputBridge.cursorPosition(
                client, window, scaledWidth, scaledHeight);
        boolean shiftDown = FabricInputBridge.isKeyDown(client, FabricInputBridge.Key.LEFT_SHIFT)
                || FabricInputBridge.isKeyDown(client, FabricInputBridge.Key.RIGHT_SHIFT);
        boolean controlDown = FabricInputBridge.isKeyDown(client, FabricInputBridge.Key.LEFT_CONTROL)
                || FabricInputBridge.isKeyDown(client, FabricInputBridge.Key.RIGHT_CONTROL);
        return new PreviewLayoutEditor.InputState(
                cursor.x,
                cursor.y,
                FabricInputBridge.isMouseButtonDown(client, FabricInputBridge.MouseButton.LEFT),
                FabricInputBridge.isMouseButtonDown(client, FabricInputBridge.MouseButton.RIGHT),
                FabricInputBridge.isKeyDown(client, FabricInputBridge.Key.ESCAPE),
                shiftDown || controlDown,
                FabricInputBridge.isKeyDown(client, FabricInputBridge.Key.LEFT),
                FabricInputBridge.isKeyDown(client, FabricInputBridge.Key.RIGHT),
                FabricInputBridge.isKeyDown(client, FabricInputBridge.Key.UP),
                FabricInputBridge.isKeyDown(client, FabricInputBridge.Key.DOWN),
                FabricInputBridge.isKeyDown(client, FabricInputBridge.Key.DELETE),
                FabricInputBridge.isKeyDown(client, FabricInputBridge.Key.Z),
                shiftDown,
                controlDown,
                PreviewScreenController.consumeScrollDelta());
    }

    private static Object minecraftClient() throws ReflectiveOperationException {
        Object gameInstance = FabricLoader.getInstance().getGameInstance();
        if (gameInstance != null) return gameInstance;
        Class<?> clientClass = classAny("net.minecraft.client.MinecraftClient",
                "net.minecraft.client.Minecraft", MINECRAFT_CLIENT);
        Method method = methodAny(clientClass, "getInstance",
                minecraftMethodName(MINECRAFT_CLIENT, "method_1551", "()Lnet/minecraft/class_310;"),
                "method_1551");
        return method.invoke(null);
    }

    private static Class<?> classAny(String... names) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                last = e;
            }
        }
        for (String name : names) {
            if (!name.startsWith("net.minecraft.class_")) continue;
            String mapped = minecraftClassName(name);
            if (mapped.equals(name)) continue;
            try {
                return Class.forName(mapped);
            } catch (ClassNotFoundException e) {
                last = e;
            }
        }
        throw last == null ? new ClassNotFoundException() : last;
    }

    private static Object fieldAny(Object target, String... names) throws ReflectiveOperationException {
        if (target == null) return null;
        for (String name : names) {
            try {
                java.lang.reflect.Field field = target.getClass().getField(name);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                try {
                    java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignoredAgain) {
                    // Try next field name.
                }
            }
        }
        return null;
    }

    private static Object invokeAny(Object target, String... names) throws ReflectiveOperationException {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                // Try next method name.
            }
        }
        return null;
    }

    private static Method methodAny(Class<?> type, String... names) throws NoSuchMethodException {
        for (String name : names) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // Try next method name.
            }
        }
        throw new NoSuchMethodException(type.getName());
    }

    private static int intAny(Object target, int fallback, String... names) throws ReflectiveOperationException {
        Object value = invokeAny(target, names);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static long longAny(Object target, long fallback, String... names) throws ReflectiveOperationException {
        Object value = invokeAny(target, names);
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    private static Object invokeAnyArgs(Object target, String[] names, Object... args)
            throws ReflectiveOperationException {
        if (target == null) return null;
        for (String name : names) {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                try {
                    return method.invoke(target, args);
                } catch (IllegalArgumentException ignored) {
                    // Signature with same arity but different types; try next.
                }
            }
        }
        return null;
    }

    private static Object literalText(String text) throws ReflectiveOperationException {
        Class<?> textClass = classAny("net.minecraft.text.Text",
                "net.minecraft.network.chat.Component", TEXT);
        Method method = textFactory(textClass, "literal",
                minecraftMethodName(TEXT, "method_43470", "(Ljava/lang/String;)Lnet/minecraft/class_5250;"),
                "method_43470");
        if (method == null) method = anyTextFactory(textClass);
        if (method == null) throw new NoSuchMethodException("Text literal factory");
        method.setAccessible(true);
        return method.invoke(null, text == null ? "" : text);
    }

    private static Method textFactory(Class<?> textClass, String... names) {
        for (String name : names) {
            for (Method method : textClass.getMethods()) {
                if (!method.getName().equals(name)
                        || method.getParameterCount() != 1
                        || !Modifier.isStatic(method.getModifiers())
                        || !method.getParameterTypes()[0].equals(String.class)
                        || !textClass.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                return method;
            }
        }
        return null;
    }

    private static Method anyTextFactory(Class<?> textClass) {
        for (Method method : textClass.getMethods()) {
            if (method.getParameterCount() == 1
                    && Modifier.isStatic(method.getModifiers())
                    && method.getParameterTypes()[0].equals(String.class)
                    && textClass.isAssignableFrom(method.getReturnType())) {
                return method;
            }
        }
        return null;
    }

    private static boolean renderButtonWidget(Object graphics, String text, int x, int y,
                                              int width, int height, int mouseX, int mouseY)
            throws ReflectiveOperationException {
        Class<?> buttonClass = classAny("net.minecraft.client.gui.widget.ButtonWidget",
                "net.minecraft.client.gui.components.Button",
                BUTTON_WIDGET);
        Object button = createButtonWidget(buttonClass, text, x, y, width, height);
        if (button == null) return false;
        String renderMethodName = minecraftMethodName(DRAWABLE, "method_25394",
                "(Lnet/minecraft/class_332;IIF)V");
        for (Method method : button.getClass().getMethods()) {
            if (!(method.getName().equals("render")
                    || method.getName().equals("extractRenderState")
                    || method.getName().equals(renderMethodName)
                    || method.getName().equals("method_25394"))
                    && !(method.getReturnType().equals(Void.TYPE) && method.getParameterCount() == 4)) {
                continue;
            }
            if (method.getParameterCount() != 4) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            if (!types[0].isInstance(graphics)
                    || !intParameter(types[1])
                    || !intParameter(types[2])
                    || !floatParameter(types[3])) {
                continue;
            }
            method.invoke(button, graphics, Integer.valueOf(mouseX), Integer.valueOf(mouseY), Float.valueOf(0F));
            return true;
        }
        return false;
    }

    private static Object createButtonWidget(Class<?> buttonClass, String text,
                                             int x, int y, int width, int height)
            throws ReflectiveOperationException {
        Object message = literalText(text);
        for (Method builderMethod : buttonClass.getMethods()) {
            if (!Modifier.isStatic(builderMethod.getModifiers())
                    || builderMethod.getParameterCount() != 2
                    || !builderMethod.getParameterTypes()[0].isInstance(message)
                    || !builderMethod.getParameterTypes()[1].isInterface()) {
                continue;
            }
            Class<?> pressAction = builderMethod.getParameterTypes()[1];
            Object action = Proxy.newProxyInstance(
                    PreviewEditorOverlayRenderer.class.getClassLoader(),
                    new Class<?>[] {pressAction},
                    (proxy, method, args) -> null);
            Object builder = builderMethod.invoke(null, message, action);
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
            if (method.getParameterCount() != 4
                    || !intParameter(method.getParameterTypes()[0])
                    || !intParameter(method.getParameterTypes()[1])
                    || !intParameter(method.getParameterTypes()[2])
                    || !intParameter(method.getParameterTypes()[3])) {
                continue;
            }
            Object result = method.invoke(builder, Integer.valueOf(x), Integer.valueOf(y),
                    Integer.valueOf(width), Integer.valueOf(height));
            if (result != null && builder.getClass().isInstance(result)) return result;
        }
        return builder;
    }

    private static Object buildButton(Class<?> buttonClass, Object builder) throws ReflectiveOperationException {
        for (Method method : builder.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && buttonClass.isAssignableFrom(method.getReturnType())) {
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

    private static boolean renderItem(Object graphics, String soundId, int x, int y) {
        try {
            String registryId = "minecraft:" + SoundIconMapping.itemIdFor(soundId);
            Class<?> identifierClass = RuntimeReflectionCache.classAny(
                    "net.minecraft.resources.ResourceLocation", "net.minecraft.resources.Identifier",
                    "net.minecraft.util.Identifier", "net.minecraft.class_2960", "amj", "amo");
            Object identifier = null;
            for (Method method : identifierClass.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())
                        || !identifierClass.isAssignableFrom(method.getReturnType())) continue;
                if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == String.class) {
                    try { identifier = method.invoke(null, registryId); } catch (ReflectiveOperationException | RuntimeException ignored) { }
                } else if (method.getParameterCount() == 2
                        && method.getParameterTypes()[0] == String.class
                        && method.getParameterTypes()[1] == String.class) {
                    int colon = registryId.indexOf(':');
                    try { identifier = method.invoke(null, registryId.substring(0, colon), registryId.substring(colon + 1)); }
                    catch (ReflectiveOperationException | RuntimeException ignored) { }
                }
                if (identifier != null) break;
            }
            if (identifier == null) return iconFailure(soundId, null);
            Class<?> registryClass = RuntimeReflectionCache.classAny(
                    "net.minecraft.core.registries.BuiltInRegistries", "net.minecraft.registry.Registries",
                    "net.minecraft.class_7923", "mo", "mi");
            Object registry = RuntimeReflectionCache.staticFieldAny(registryClass, "ITEM", "h", "field_41178", "field_11142");
            Class<?> stackClass = RuntimeReflectionCache.classAny("net.minecraft.world.item.ItemStack",
                    "net.minecraft.item.ItemStack", "net.minecraft.class_1799", "dhp", "dlt");
            Object stack = null;
            if (registry != null) {
                registryMethods:
                for (Method method : registry.getClass().getMethods()) {
                    if (method.getParameterCount() != 1
                            || !method.getParameterTypes()[0].isInstance(identifier)) continue;
                    Object item;
                    try { item = method.invoke(registry, identifier); }
                    catch (ReflectiveOperationException | RuntimeException ignored) { continue; }
                    if (item instanceof java.util.Optional) item = ((java.util.Optional<?>) item).orElse(null);
                    if (item == null) continue;
                    for (java.lang.reflect.Constructor<?> constructor : stackClass.getConstructors()) {
                        if (constructor.getParameterCount() == 1
                                && constructor.getParameterTypes()[0].isInstance(item)) {
                            stack = constructor.newInstance(item);
                            break registryMethods;
                        }
                    }
                }
            }
            if (stack == null) return iconFailure(soundId, null);
            if (drawItemForTest(graphics, stack, x, y)) return true;
            return iconFailure(soundId, null);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return iconFailure(soundId, failure);
        }
    }

    static boolean drawItemForTest(Object graphics, Object stack, int x, int y)
            throws ReflectiveOperationException {
        return DRAW_ITEM.invokeIfPresent(graphics, stack, Integer.valueOf(x), Integer.valueOf(y));
    }

    private static boolean iconFailure(String soundId, Throwable error) {
        if (itemRenderWarningLogged) return false;
        itemRenderWarningLogged = true;
        if (error == null) LOGGER.warn("Unable to render Guess Sounds icon for {}", soundId);
        else LOGGER.warn("Unable to render Guess Sounds icon for " + soundId, error);
        return false;
    }

    private static Object fieldAny(Class<?> type, String... names) throws ReflectiveOperationException {
        for (String name : names) {
            try { return type.getField(name).get(null); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName());
    }

    private static String minecraftClassName(String intermediaryName) {
        try {
            MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();
            return resolver.mapClassName("intermediary", intermediaryName);
        } catch (RuntimeException | LinkageError e) {
            return intermediaryName;
        }
    }

    private static String minecraftMethodName(String owner, String name, String descriptor) {
        try {
            MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();
            return resolver.mapMethodName("intermediary", owner, name, descriptor);
        } catch (RuntimeException | LinkageError e) {
            return name;
        }
    }

    private static String minecraftFieldName(String owner, String name, String descriptor) {
        try {
            MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();
            return resolver.mapFieldName("intermediary", owner, name, descriptor);
        } catch (RuntimeException | LinkageError e) {
            return name;
        }
    }

    private static final class FabricEditorRenderer implements PreviewLayoutEditor.EditorRenderer {
        private final Object graphics;
        private final Object font;

        private FabricEditorRenderer(Object graphics, Object font) {
            this.graphics = graphics;
            this.font = font;
        }

        @Override
        public int fontHeight() {
            try {
                Object value = fieldAny(font, "fontHeight", "lineHeight",
                        minecraftFieldName(TEXT_RENDERER, "field_2000", "I"), "field_2000");
                return value instanceof Number ? ((Number) value).intValue() : 9;
            } catch (ReflectiveOperationException e) {
                return 9;
            }
        }

        @Override
        public int textWidth(String text) {
            try {
                Object value = invokeAnyArgs(font, new String[] {"getWidth", "width",
                                minecraftMethodName(TEXT_RENDERER, "method_1727", "(Ljava/lang/String;)I"),
                                "method_1727"},
                        text == null ? "" : text);
                return value instanceof Number ? ((Number) value).intValue() : 0;
            } catch (ReflectiveOperationException e) {
                return 0;
            }
        }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            try {
                invokeAnyArgs(graphics, new String[] {"fill",
                                minecraftMethodName(DRAW_CONTEXT, "method_25294", "(IIIII)V"),
                                "method_25294"},
                        left, top, right, bottom, color);
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("Unable to draw GtbStructures preview editor rectangle", e);
            }
        }

        @Override
        public void drawString(String text, int x, int y, int color, boolean shadow) {
            try {
                invokeAnyArgs(graphics, new String[] {"drawText", "drawString", "text",
                                minecraftMethodName(DRAW_CONTEXT, "method_51433",
                                        "(Lnet/minecraft/class_327;Ljava/lang/String;IIIZ)I"),
                                "method_51433"},
                        font, text == null ? "" : text, x, y, opaqueColor(color), Boolean.valueOf(shadow));
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("Unable to draw GtbStructures preview editor text", e);
            }
        }

        @Override
        public void enableScissor(int left, int top, int right, int bottom) {
            try {
                invokeAnyArgs(graphics, new String[] {"enableScissor",
                                minecraftMethodName(DRAW_CONTEXT, "method_44379", "(IIII)V"),
                                "method_44379"},
                        left, top, right, bottom);
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("Unable to enable GtbStructures preview editor scissor", e);
            }
        }

        @Override
        public void disableScissor() {
            try {
                invokeAnyArgs(graphics, new String[] {"disableScissor",
                        minecraftMethodName(DRAW_CONTEXT, "method_44380", "()V"),
                        "method_44380"});
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("Unable to disable GtbStructures preview editor scissor", e);
            }
        }

        private static int opaqueColor(int color) {
            return (color & 0xFF000000) == 0 ? 0xFF000000 | color : color;
        }

        @Override
        public boolean drawVanillaButton(int x, int y, int width, int height,
                                         String text, int mouseX, int mouseY) {
            try {
                return renderButtonWidget(graphics, text, x, y, width, height, mouseX, mouseY);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }

        @Override
        public void drawSoundIcon(String soundId, int x, int y) {
            renderItem(graphics, soundId, x, y);
        }
    }
}
