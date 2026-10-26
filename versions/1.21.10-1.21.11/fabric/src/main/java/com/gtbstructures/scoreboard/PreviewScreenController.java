package com.gtbstructures.scoreboard;

import com.gtbstructures.chat.ChatHeader;
import com.gtbstructures.util.FabricInputBridge;
import com.gtbstructures.util.MinecraftClientUiBridge;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Predicate;

public final class PreviewScreenController implements InterfacePreview.ScreenController {
    enum TextFieldPurpose {
        HEADER(256),
        GROUP_NAME(128),
        GROUP_SEARCH(64);

        final int maxLength;

        TextFieldPurpose(int maxLength) {
            this.maxLength = maxLength;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(PreviewScreenController.class);
    private static final String PREVIEW_SCREEN_CLASS = "com.gtbstructures.scoreboard.FabricPreviewScreen";
    private static final String PREVIEW_SCREEN_INTERNAL = "com/gtbstructures/scoreboard/FabricPreviewScreen";
    private static final String MINECRAFT_CLIENT = "net.minecraft.class_310";
    private static final String SCREEN = "net.minecraft.class_437";
    private static final String DRAW_CONTEXT = "net.minecraft.class_332";
    private static final String DRAWABLE = "net.minecraft.class_4068";
    private static final String ELEMENT = "net.minecraft.class_364";
    private static final String PARENT_ELEMENT = "net.minecraft.class_4069";
    private static final String WIDGET = "net.minecraft.class_8021";
    private static final String CLICKABLE_WIDGET = "net.minecraft.class_339";
    private static final String TEXT_FIELD_WIDGET = "net.minecraft.class_342";
    private static final String KEY_INPUT = "net.minecraft.class_11908";
    private static final String CHAR_INPUT = "net.minecraft.class_11905";
    private static final String CLICK = "net.minecraft.class_11909";
    private static final String TEXT = "net.minecraft.class_2561";
    private static volatile Class<?> previewScreenClass;
    private static double pendingScrollDelta;
    private static Object headerEditBox;
    private static Object headerEditScreen;
    private static boolean headerEditNeedsLayoutRefresh;
    private static boolean previewOpenFailureLogged;

    private static final class RuntimeMinecraftNames {
        private static final boolean MAPPINGS_USABLE = resolverMappingsAreLoadable();

        private RuntimeMinecraftNames() {
        }
    }

    @Override
    public boolean isInGame() {
        try {
            Object client = minecraftClient();
            return world(client) != null && player(client) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public String currentSessionKey() {
        try {
            Object client = minecraftClient();
            Object world = world(client);
            Object player = player(client);
            if (world == null || player == null) return "";
            return Integer.toHexString(System.identityHashCode(world))
                    + ":" + Integer.toHexString(System.identityHashCode(player));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }

    @Override
    public boolean isPreviewScreenOpen() {
        try {
            return isPreviewScreen(MinecraftClientUiBridge.currentScreen(minecraftClient()));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public void openPreviewScreen() {
        try {
            Object client = minecraftClient();
            if (isPreviewScreen(MinecraftClientUiBridge.currentScreen(client))) return;
            clearScrollDelta();
            discardHeaderEditor();
            Object screen = previewScreen();
            if (!MinecraftClientUiBridge.setScreen(client, screen)) {
                if (!previewOpenFailureLogged) {
                    previewOpenFailureLogged = true;
                    LOGGER.warn("Unable to open GtbStructures Fabric preview screen: screen setter was not found");
                }
            } else {
                previewOpenFailureLogged = false;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            if (!previewOpenFailureLogged) {
                previewOpenFailureLogged = true;
                LOGGER.warn("Unable to open GtbStructures Fabric preview screen", e);
            }
        }
    }

    @Override
    public void closePreviewScreen() {
        try {
            Object client = minecraftClient();
            if (isPreviewScreen(MinecraftClientUiBridge.currentScreen(client))) {
                discardHeaderEditor();
                MinecraftClientUiBridge.setScreen(client, null);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to close GtbStructures Fabric preview screen", e);
        } finally {
            discardHeaderEditor();
            clearScrollDelta();
            previewOpenFailureLogged = false;
        }
    }

    public static boolean handlePreviewKeyInput(Object input) {
        int keyCode = keyCode(input);
        if (headerEditBox != null) {
            if (PreviewLayoutEditor.INSTANCE.onHeaderKeyPressed(keyCode)) return true;
            invokeWidgetBoolean(headerEditBox, ELEMENT, "method_25404",
                    "(Lnet/minecraft/class_11908;)Z", new Object[] {input},
                    "keyPressed", "method_25404");
            return true;
        }
        if (FabricInputBridge.isKeyCode(keyCode, FabricInputBridge.Key.ESCAPE)) {
            InterfacePreview.INSTANCE.disableSilently();
        }
        return true;
    }

    public static boolean handlePreviewCharInput(Object input) {
        if (headerEditBox != null) {
            invokeWidgetBoolean(headerEditBox, ELEMENT, "method_25400",
                    "(Lnet/minecraft/class_11905;)Z", new Object[] {input},
                    "charTyped", "method_25400");
        }
        return true;
    }

    public static boolean handlePreviewMouseClicked(Object click, boolean doubled) {
        if (headerEditBox != null) {
            invokeWidgetBoolean(headerEditBox, ELEMENT, "method_25402",
                    "(Lnet/minecraft/class_11909;Z)Z", new Object[] {click, doubled},
                    "mouseClicked", "method_25402");
        }
        return true;
    }

    public static boolean handlePreviewMouseReleased(Object click) {
        if (headerEditBox != null) {
            invokeWidgetBoolean(headerEditBox, ELEMENT, "method_25406",
                    "(Lnet/minecraft/class_11909;)Z", new Object[] {click},
                    "mouseReleased", "method_25406");
        }
        return true;
    }

    public static boolean handlePreviewMouseDragged(Object click, double deltaX, double deltaY) {
        if (headerEditBox != null) {
            invokeWidgetBoolean(headerEditBox, ELEMENT, "method_25403",
                    "(Lnet/minecraft/class_11909;DD)Z", new Object[] {click, deltaX, deltaY},
                    "mouseDragged", "method_25403");
        }
        return true;
    }

    public static synchronized boolean handlePreviewScroll(double verticalAmount) {
        pendingScrollDelta += verticalAmount;
        return true;
    }

    public static void renderPreviewScreen(Object context, int mouseX, int mouseY, float deltaTicks) {
        Object widget = headerEditBox;
        if (widget == null) return;
        try {
            invokeAny(widget, new Object[] {context, mouseX, mouseY, deltaTicks},
                    minecraftMethodName(DRAWABLE, "method_25394",
                            "(Lnet/minecraft/class_332;IIF)V"),
                    "render", "method_25394");
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to render GtbStructures Fabric header editor", e);
        }
    }

    static synchronized double consumeScrollDelta() {
        double delta = pendingScrollDelta;
        pendingScrollDelta = 0.0D;
        return delta;
    }

    static synchronized void queueScrollForTest(double delta) {
        pendingScrollDelta += delta;
    }

    private static synchronized void clearScrollDelta() {
        pendingScrollDelta = 0.0D;
    }

    static void beginHeaderEditing(String value) {
        beginTextEditing(value, TextFieldPurpose.HEADER);
    }

    static void beginTextEditing(String value, TextFieldPurpose purpose) {
        final TextFieldPurpose effectivePurpose = purpose == null
                ? TextFieldPurpose.HEADER : purpose;
        try {
            Object client = minecraftClient();
            Object screen = MinecraftClientUiBridge.currentScreen(client);
            if (!isPreviewScreen(screen)) return;
            if (headerEditBox == null || headerEditScreen != screen) {
                discardHeaderEditor();
                Object widget = createHeaderEditBox();
                headerEditBox = widget;
                headerEditScreen = screen;
                invokeAny(screen, new Object[] {widget},
                        minecraftMethodName(SCREEN, "method_37063",
                                "(Lnet/minecraft/class_364;)Lnet/minecraft/class_364;"),
                        "addDrawableChild", "addRenderableWidget", "method_37063");
                invokeAny(screen, new Object[] {widget},
                        minecraftMethodName(SCREEN, "method_48265",
                                "(Lnet/minecraft/class_364;)V"),
                        "setInitialFocus", "method_48265");
            }
            invokeAny(headerEditBox, new Object[] {effectivePurpose.maxLength},
                    minecraftMethodName(TEXT_FIELD_WIDGET, "method_1880", "(I)V"),
                    "setMaxLength", "method_1880");
            Predicate<String> filter = candidate -> validTextEditCandidate(
                    effectivePurpose, candidate);
            try {
                invokeAny(headerEditBox, new Object[] {filter},
                        minecraftMethodName(TEXT_FIELD_WIDGET, "method_1890",
                                "(Ljava/util/function/Predicate;)V"),
                        "setTextPredicate", "setFilter", "method_1890");
            } catch (NoSuchMethodException ignored) {
                // Minecraft 26.x removed the EditBox predicate hook; save validation remains authoritative.
            }
            setHeaderText(value == null ? "" : value);
            invokeAny(headerEditBox, new Object[] {false},
                    minecraftMethodName(TEXT_FIELD_WIDGET, "method_1872", "(Z)V"),
                    "setCursorToEnd", "moveCursorToEnd", "method_1872");
            invokeAny(headerEditBox, new Object[] {true},
                    minecraftMethodName(ELEMENT, "method_25365", "(Z)V"),
                    "setFocused", "method_25365");
            FabricInputBridge.onTextInputFocusChange(client, headerEditBox, true);
            headerEditNeedsLayoutRefresh = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            discardHeaderEditor();
            LOGGER.warn("Unable to create GtbStructures Fabric header editor", e);
        }
    }

    static String finishHeaderEditing() {
        String value = headerEditingValue(ChatHeader.raw());
        discardHeaderEditor();
        return value;
    }

    static void cancelHeaderEditing() {
        discardHeaderEditor();
    }

    static String headerEditingValue(String fallback) {
        Object widget = headerEditBox;
        if (widget == null) return fallback;
        try {
            Object value = invokeAny(widget, new Object[0],
                    minecraftMethodName(TEXT_FIELD_WIDGET, "method_1882", "()Ljava/lang/String;"),
                    "getText", "getValue", "method_1882");
            return value instanceof String ? (String) value : fallback;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to read GtbStructures Fabric header editor", e);
            return fallback;
        }
    }

    static void positionHeaderEditor(int x, int y, int width, int height) {
        Object widget = headerEditBox;
        if (widget == null) return;
        try {
            invokeAny(widget, new Object[] {Math.max(1, width)},
                    minecraftMethodName(CLICKABLE_WIDGET, "method_25358", "(I)V"),
                    "setWidth", "method_25358");
            invokeAny(widget, new Object[] {Math.max(1, height)},
                    minecraftMethodName(CLICKABLE_WIDGET, "method_53533", "(I)V"),
                    "setHeight", "method_53533");
            invokeAny(widget, new Object[] {x},
                    minecraftMethodName(WIDGET, "method_46421", "(I)V"),
                    "setX", "method_46421");
            invokeAny(widget, new Object[] {y},
                    minecraftMethodName(WIDGET, "method_46419", "(I)V"),
                    "setY", "method_46419");
            if (headerEditNeedsLayoutRefresh) {
                invokeAny(widget, new Object[] {false},
                        minecraftMethodName(TEXT_FIELD_WIDGET, "method_1872", "(Z)V"),
                        "setCursorToEnd", "moveCursorToEnd", "method_1872");
                headerEditNeedsLayoutRefresh = false;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to position GtbStructures Fabric header editor", e);
        }
    }

    static boolean validHeaderEditCandidate(String value) {
        return validTextEditCandidate(TextFieldPurpose.HEADER, value);
    }

    static boolean validGroupNameEditCandidate(String value) {
        return validTextEditCandidate(TextFieldPurpose.GROUP_NAME, value);
    }

    private static boolean validTextEditCandidate(TextFieldPurpose purpose, String value) {
        if (purpose == TextFieldPurpose.GROUP_SEARCH) return true;
        if (purpose == TextFieldPurpose.GROUP_NAME) {
            return value != null && value.codePointCount(0, value.length())
                    <= com.gtbstructures.translation.ShortcutLanguageGroups.MAX_NAME_CODE_POINTS;
        }
        if (ChatHeader.visibleLength(value) <= ChatHeader.MAX_VISIBLE_LENGTH) return true;
        if (value == null || value.isEmpty()) return false;
        char last = value.charAt(value.length() - 1);
        return (last == '&' || last == '\u00a7')
                && ChatHeader.visibleLength(value.substring(0, value.length() - 1))
                <= ChatHeader.MAX_VISIBLE_LENGTH;
    }

    public static Object previewTitle() {
        try {
            Class<?> textClass = classAny("net.minecraft.text.Text",
                    "net.minecraft.network.chat.Component", TEXT);
            Method empty = zeroArgTextFactory(textClass, "empty",
                    minecraftMethodName(TEXT, "method_43473", "()Lnet/minecraft/class_5250;"),
                    "method_43473");
            if (empty != null) {
                empty.setAccessible(true);
                return empty.invoke(null);
            }
            Method literal = stringTextFactory(textClass, "literal",
                    minecraftMethodName(TEXT, "method_43470",
                            "(Ljava/lang/String;)Lnet/minecraft/class_5250;"),
                    "method_43470");
            if (literal != null) {
                literal.setAccessible(true);
                return literal.invoke(null, "");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to create GtbStructures Fabric preview title", e);
        }
        return null;
    }

    static boolean isPreviewScreen(Object screen) {
        if (screen == null) return false;
        Class<?> type = previewScreenClass;
        return PREVIEW_SCREEN_CLASS.equals(screen.getClass().getName())
                || (type != null && type.isInstance(screen));
    }

    private static Object createHeaderEditBox() throws ReflectiveOperationException {
        Object client = minecraftClient();
        Object textRenderer = fieldAny(client, "textRenderer", "font",
                minecraftFieldName(MINECRAFT_CLIENT, "field_1772",
                        "Lnet/minecraft/class_327;"),
                "field_1772");
        Object title = previewTitle();
        if (textRenderer == null || title == null) {
            throw new ReflectiveOperationException("Minecraft text renderer or empty text is unavailable");
        }
        Class<?> widgetClass = classAny(
                "net.minecraft.client.gui.widget.TextFieldWidget",
                "net.minecraft.client.gui.components.EditBox",
                TEXT_FIELD_WIDGET);
        for (Constructor<?> constructor : widgetClass.getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length != 6
                    || !parameters[0].isInstance(textRenderer)
                    || parameters[1] != int.class
                    || parameters[2] != int.class
                    || parameters[3] != int.class
                    || parameters[4] != int.class
                    || !parameters[5].isInstance(title)) {
                continue;
            }
            constructor.setAccessible(true);
            return constructor.newInstance(textRenderer, -1000, -1000, 1, 1, title);
        }
        throw new NoSuchMethodException(widgetClass.getName() + " native text-field constructor");
    }

    private static void setHeaderText(String value) throws ReflectiveOperationException {
        invokeAny(headerEditBox, new Object[] {value},
                minecraftMethodName(TEXT_FIELD_WIDGET, "method_1852",
                        "(Ljava/lang/String;)V"),
                "setText", "setValue", "method_1852");
    }

    private static void discardHeaderEditor() {
        Object widget = headerEditBox;
        Object screen = headerEditScreen;
        headerEditBox = null;
        headerEditScreen = null;
        headerEditNeedsLayoutRefresh = false;
        if (widget == null) return;
        try {
            try {
                FabricInputBridge.onTextInputFocusChange(minecraftClient(), widget, false);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Field teardown must still proceed if the Minecraft client is unavailable.
            }
            invokeAny(widget, new Object[] {false},
                    minecraftMethodName(ELEMENT, "method_25365", "(Z)V"),
                    "setFocused", "method_25365");
            if (screen != null) {
                invokeAny(screen, new Object[] {widget},
                        minecraftMethodName(SCREEN, "method_37066",
                                "(Lnet/minecraft/class_364;)V"),
                        "remove", "removeWidget", "method_37066");
                invokeAny(screen, new Object[] {null},
                        minecraftMethodName(PARENT_ELEMENT, "method_25395",
                                "(Lnet/minecraft/class_364;)V"),
                        "setFocused", "method_25395");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to discard GtbStructures Fabric header editor", e);
        }
    }

    private static int keyCode(Object input) {
        return FabricInputBridge.keyCode(input);
    }

    private static boolean invokeWidgetBoolean(Object target, String owner, String name,
                                               String descriptor, Object[] args,
                                               String... fallbacks) {
        String[] names = new String[fallbacks.length + 1];
        names[0] = minecraftMethodName(owner, name, descriptor);
        System.arraycopy(fallbacks, 0, names, 1, fallbacks.length);
        try {
            Object result = invokeAny(target, args, names);
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to forward GtbStructures Fabric preview input", e);
            return false;
        }
    }

    private static Object minecraftClient() throws ReflectiveOperationException {
        Object gameInstance = FabricLoader.getInstance().getGameInstance();
        if (gameInstance != null) return gameInstance;
        Class<?> clientClass = classAny("net.minecraft.client.MinecraftClient",
                "net.minecraft.client.Minecraft", MINECRAFT_CLIENT);
        Method method = methodAny(clientClass, "getInstance",
                minecraftMethodName(MINECRAFT_CLIENT, "method_1551",
                        "()Lnet/minecraft/class_310;"),
                "method_1551");
        return method.invoke(null);
    }

    private static Object world(Object client) throws ReflectiveOperationException {
        return fieldAny(client, "world", "level",
                minecraftFieldName(MINECRAFT_CLIENT, "field_1687",
                        "Lnet/minecraft/class_638;"),
                "field_1687");
    }

    private static Object player(Object client) throws ReflectiveOperationException {
        return fieldAny(client, "player",
                minecraftFieldName(MINECRAFT_CLIENT, "field_1724",
                        "Lnet/minecraft/class_746;"),
                "field_1724");
    }

    private static Object previewScreen() throws ReflectiveOperationException {
        Class<?> type = previewScreenClass;
        if (type == null) {
            synchronized (PreviewScreenController.class) {
                type = previewScreenClass;
                if (type == null) {
                    type = definePreviewScreenClass();
                    previewScreenClass = type;
                }
            }
        }
        return type.getDeclaredConstructor().newInstance();
    }

    private static Class<?> definePreviewScreenClass() throws ReflectiveOperationException {
        try {
            return Class.forName(PREVIEW_SCREEN_CLASS, false,
                    PreviewScreenController.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            // Define it below.
        }
        return MethodHandles.lookup().defineClass(previewScreenBytes());
    }

    static byte[] previewScreenBytes() {
        String screenClass = minecraftClassName(SCREEN);
        String screenInternal = internalName(screenClass);
        String textInternal = internalName(minecraftClassName(TEXT));
        String drawContextDescriptor =
                "L" + internalName(minecraftClassName(DRAW_CONTEXT)) + ";";
        String keyInputDescriptor = "L" + internalName(minecraftClassName(KEY_INPUT)) + ";";
        String charInputDescriptor = "L" + internalName(minecraftClassName(CHAR_INPUT)) + ";";
        String clickDescriptor = "L" + internalName(minecraftClassName(CLICK)) + ";";
        String renderDescriptor = "(" + drawContextDescriptor + "IIF)V";
        ClassWriter writer =
                new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                PREVIEW_SCREEN_INTERNAL, null, screenInternal, null);

        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/gtbstructures/scoreboard/PreviewScreenController",
                "previewTitle", "()Ljava/lang/Object;", false);
        method.visitTypeInsn(Opcodes.CHECKCAST, textInternal);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, screenInternal,
                "<init>", "(L" + textInternal + ";)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();

        method = writer.visitMethod(Opcodes.ACC_PUBLIC,
                minecraftMethodName(DRAWABLE, "method_25394",
                        "(Lnet/minecraft/class_332;IIF)V"),
                renderDescriptor, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitVarInsn(Opcodes.FLOAD, 4);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/gtbstructures/scoreboard/PreviewScreenController",
                "renderPreviewScreen", "(Ljava/lang/Object;IIF)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();

        method = writer.visitMethod(Opcodes.ACC_PUBLIC,
                minecraftMethodName(SCREEN, "method_25420",
                        "(Lnet/minecraft/class_332;IIF)V"),
                renderDescriptor, null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();

        method = writer.visitMethod(Opcodes.ACC_PUBLIC,
                minecraftMethodName(ELEMENT, "method_25404",
                        "(Lnet/minecraft/class_11908;)Z"),
                "(" + keyInputDescriptor + ")Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/gtbstructures/scoreboard/PreviewScreenController",
                "handlePreviewKeyInput", "(Ljava/lang/Object;)Z", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();

        method = writer.visitMethod(Opcodes.ACC_PUBLIC,
                minecraftMethodName(ELEMENT, "method_16803",
                        "(Lnet/minecraft/class_11908;)Z"),
                "(" + keyInputDescriptor + ")Z", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();

        method = writer.visitMethod(Opcodes.ACC_PUBLIC,
                minecraftMethodName(ELEMENT, "method_25400",
                        "(Lnet/minecraft/class_11905;)Z"),
                "(" + charInputDescriptor + ")Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/gtbstructures/scoreboard/PreviewScreenController",
                "handlePreviewCharInput", "(Ljava/lang/Object;)Z", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();

        method = writer.visitMethod(Opcodes.ACC_PUBLIC,
                minecraftMethodName(ELEMENT, "method_25402",
                        "(Lnet/minecraft/class_11909;Z)Z"),
                "(" + clickDescriptor + "Z)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/gtbstructures/scoreboard/PreviewScreenController",
                "handlePreviewMouseClicked", "(Ljava/lang/Object;Z)Z", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();

        method = writer.visitMethod(Opcodes.ACC_PUBLIC,
                minecraftMethodName(ELEMENT, "method_25406",
                        "(Lnet/minecraft/class_11909;)Z"),
                "(" + clickDescriptor + ")Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/gtbstructures/scoreboard/PreviewScreenController",
                "handlePreviewMouseReleased", "(Ljava/lang/Object;)Z", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();

        method = writer.visitMethod(Opcodes.ACC_PUBLIC,
                minecraftMethodName(ELEMENT, "method_25403",
                        "(Lnet/minecraft/class_11909;DD)Z"),
                "(" + clickDescriptor + "DD)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.DLOAD, 2);
        method.visitVarInsn(Opcodes.DLOAD, 4);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/gtbstructures/scoreboard/PreviewScreenController",
                "handlePreviewMouseDragged", "(Ljava/lang/Object;DD)Z", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();

        method = writer.visitMethod(Opcodes.ACC_PUBLIC,
                minecraftMethodName(ELEMENT, "method_25401", "(DDDD)Z"),
                "(DDDD)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.DLOAD, 7);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/gtbstructures/scoreboard/PreviewScreenController",
                "handlePreviewScroll", "(D)Z", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();

        method = writer.visitMethod(Opcodes.ACC_PUBLIC,
                minecraftMethodName(SCREEN, "method_25421", "()Z"),
                "()Z", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_0);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();

        method = writer.visitMethod(Opcodes.ACC_PUBLIC,
                minecraftMethodName(SCREEN, "method_25422", "()Z"),
                "()Z", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_0);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
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

    private static Object fieldAny(Object target, String... names)
            throws ReflectiveOperationException {
        if (target == null) return null;
        for (String name : names) {
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    java.lang.reflect.Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                    // Try the next class or field name.
                }
            }
        }
        return null;
    }

    private static Object invokeAny(Object target, Object[] args, String... names)
            throws ReflectiveOperationException {
        if (target == null) throw new NoSuchMethodException("null target");
        for (String name : names) {
            Method publicMethod = compatibleMethod(target.getClass().getMethods(), name, args);
            if (publicMethod != null) return invoke(publicMethod, target, args);
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                Method declaredMethod =
                        compatibleMethod(type.getDeclaredMethods(), name, args);
                if (declaredMethod != null) return invoke(declaredMethod, target, args);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName());
    }

    private static Method compatibleMethod(Method[] methods, String name, Object[] args) {
        for (Method method : methods) {
            if (!method.getName().equals(name)
                    || method.getParameterCount() != args.length) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            boolean compatible = true;
            for (int i = 0; i < parameters.length; i++) {
                if (!isCompatible(parameters[i], args[i])) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) return method;
        }
        return null;
    }

    private static boolean isCompatible(Class<?> parameter, Object value) {
        if (value == null) return !parameter.isPrimitive();
        if (!parameter.isPrimitive()) return parameter.isInstance(value);
        if (parameter == boolean.class) return value instanceof Boolean;
        if (parameter == char.class) return value instanceof Character;
        return value instanceof Number;
    }

    private static Object invoke(Method method, Object target, Object[] args)
            throws ReflectiveOperationException {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ReflectiveOperationException) {
                throw (ReflectiveOperationException) cause;
            }
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw e;
        }
    }

    private static Method methodAny(Class<?> type, String... names)
            throws NoSuchMethodException {
        for (String name : names) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // Try next method name.
            }
        }
        throw new NoSuchMethodException(type.getName());
    }

    private static Method zeroArgTextFactory(Class<?> textClass, String... names) {
        for (String name : names) {
            for (Method method : textClass.getMethods()) {
                if (!method.getName().equals(name)
                        || method.getParameterCount() != 0
                        || !Modifier.isStatic(method.getModifiers())
                        || !textClass.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                return method;
            }
        }
        return null;
    }

    private static Method stringTextFactory(Class<?> textClass, String... names) {
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

    private static String minecraftClassName(String intermediaryName) {
        try {
            MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();
            String mapped = resolver.mapClassName("intermediary", intermediaryName);
            return runtimeMinecraftName(RuntimeMinecraftNames.MAPPINGS_USABLE,
                    mapped, officialClassName(intermediaryName));
        } catch (RuntimeException | LinkageError e) {
            // Fall through to the official 26.x names below.
        }
        return officialClassName(intermediaryName);
    }

    private static String minecraftMethodName(String owner, String name, String descriptor) {
        try {
            MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();
            String mapped = resolver.mapMethodName("intermediary", owner, name, descriptor);
            return runtimeMinecraftName(RuntimeMinecraftNames.MAPPINGS_USABLE,
                    mapped, officialMethodName(owner, name));
        } catch (RuntimeException | LinkageError e) {
            // Fall through to the official 26.x names below.
        }
        return officialMethodName(owner, name);
    }

    private static boolean resolverMappingsAreLoadable() {
        try {
            MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();
            String mappedScreen = resolver.mapClassName("intermediary", SCREEN);
            if (mappedScreen == null || mappedScreen.isBlank()) return false;
            Class.forName(mappedScreen, false, PreviewScreenController.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    static String runtimeMinecraftName(boolean mappingsUsable, String mapped, String official) {
        return mappingsUsable ? mappedNameOrFallback(mapped, official) : official;
    }

    static String mappedNameOrFallback(String mapped, String fallback) {
        return mapped == null || mapped.isBlank() ? fallback : mapped;
    }

    private static String officialClassName(String intermediaryName) {
        return switch (intermediaryName) {
            case MINECRAFT_CLIENT -> "net.minecraft.client.Minecraft";
            case SCREEN -> "net.minecraft.client.gui.screens.Screen";
            case DRAW_CONTEXT -> "net.minecraft.client.gui.GuiGraphicsExtractor";
            case DRAWABLE -> "net.minecraft.client.gui.components.Renderable";
            case ELEMENT -> "net.minecraft.client.gui.components.events.GuiEventListener";
            case PARENT_ELEMENT -> "net.minecraft.client.gui.components.events.ContainerEventHandler";
            case WIDGET -> "net.minecraft.client.gui.layouts.LayoutElement";
            case CLICKABLE_WIDGET -> "net.minecraft.client.gui.components.AbstractWidget";
            case TEXT_FIELD_WIDGET -> "net.minecraft.client.gui.components.EditBox";
            case KEY_INPUT -> "net.minecraft.client.input.KeyEvent";
            case CHAR_INPUT -> "net.minecraft.client.input.CharacterEvent";
            case CLICK -> "net.minecraft.client.input.MouseButtonEvent";
            case TEXT -> "net.minecraft.network.chat.Component";
            default -> intermediaryName;
        };
    }

    private static String officialMethodName(String owner, String name) {
        if (DRAWABLE.equals(owner) && "method_25394".equals(name)) return "extractRenderState";
        if (SCREEN.equals(owner) && "method_25420".equals(name)) return "extractBackground";
        if (SCREEN.equals(owner) && "method_25421".equals(name)) return "shouldCloseOnEsc";
        if (SCREEN.equals(owner) && "method_25422".equals(name)) return "isPauseScreen";
        if (ELEMENT.equals(owner)) {
            return switch (name) {
                case "method_25404" -> "keyPressed";
                case "method_16803" -> "keyReleased";
                case "method_25400" -> "charTyped";
                case "method_25402" -> "mouseClicked";
                case "method_25406" -> "mouseReleased";
                case "method_25403" -> "mouseDragged";
                case "method_25401" -> "mouseScrolled";
                default -> name;
            };
        }
        return name;
    }

    private static String minecraftFieldName(String owner, String name, String descriptor) {
        try {
            MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();
            return resolver.mapFieldName("intermediary", owner, name, descriptor);
        } catch (RuntimeException | LinkageError e) {
            return name;
        }
    }

    private static String internalName(String className) {
        return className.replace('.', '/');
    }
}
