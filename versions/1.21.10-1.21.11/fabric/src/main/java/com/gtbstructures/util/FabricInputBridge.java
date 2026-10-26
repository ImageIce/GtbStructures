package com.gtbstructures.util;

import java.lang.reflect.Method;

/** Resolves logical Fabric input through GLFW on older clients and vanilla SDL input on 26.x. */
public final class FabricInputBridge {
    private static final String INPUT_CONSTANTS = "com.mojang.blaze3d.platform.InputConstants";
    private static final String GLFW = "org.lwjgl.glfw.GLFW";
    private static final String SDL_MOUSE = "org.lwjgl.sdl.SDLMouse";
    private static final Object[] SDL_NO_COORDINATES = {null, null};
    // field_1729 is MinecraftClient.mouse; field_1724 is the player, not input state.
    private static final String[] MOUSE_HANDLER_FIELDS = {"mouseHandler", "mouse", "field_1729"};
    private static final String[] WINDOW_FIELDS = {"window", "field_1704"};
    private static final RuntimeReflectionCache.InvocationSite WINDOW_HANDLE =
            RuntimeReflectionCache.instanceSite("handle", "getHandle", "method_4490");
    private static final RuntimeReflectionCache.InvocationSite WINDOW_CURSOR_X =
            RuntimeReflectionCache.instanceSite("getScaledXPos");
    private static final RuntimeReflectionCache.InvocationSite WINDOW_CURSOR_Y =
            RuntimeReflectionCache.instanceSite("getScaledYPos");
    private static final RuntimeReflectionCache.InvocationSite MOUSE_X =
            RuntimeReflectionCache.instanceSite("xpos", "getXpos", "getX");
    private static final RuntimeReflectionCache.InvocationSite MOUSE_Y =
            RuntimeReflectionCache.instanceSite("ypos", "getYpos", "getY");
    private static final RuntimeReflectionCache.InvocationSite IS_KEY_DOWN =
            RuntimeReflectionCache.staticSite("isKeyDown");
    private static final RuntimeReflectionCache.InvocationSite GET_KEY_EVENT =
            RuntimeReflectionCache.staticSite("getKey");
    private static final RuntimeReflectionCache.InvocationSite GET_KEY_PAIR =
            RuntimeReflectionCache.staticSite("getKey");
    private static final RuntimeReflectionCache.InvocationSite GLFW_GET_KEY =
            RuntimeReflectionCache.staticSite("glfwGetKey");
    private static final RuntimeReflectionCache.InvocationSite GLFW_GET_MOUSE_BUTTON =
            RuntimeReflectionCache.staticSite("glfwGetMouseButton");
    private static final RuntimeReflectionCache.InvocationSite SDL_GET_MOUSE_STATE =
            RuntimeReflectionCache.staticSite("SDL_GetMouseState");
    private static final RuntimeReflectionCache.InvocationSite GLFW_GET_CURSOR_POS =
            RuntimeReflectionCache.staticSite("glfwGetCursorPos");
    private static final RuntimeReflectionCache.InvocationSite GLFW_GET_WINDOW_SIZE =
            RuntimeReflectionCache.staticSite("glfwGetWindowSize");
    private static final RuntimeReflectionCache.InvocationSite FOCUS_CHANGE =
            RuntimeReflectionCache.instanceSite("onTextInputFocusChange");
    private static volatile int shiftMask = Integer.MIN_VALUE;
    private static volatile int controlMask = Integer.MIN_VALUE;
    private static volatile Class<?> checkedInputConstantsClass;
    private static volatile boolean sdlInputBackend;

    private FabricInputBridge() {
    }

    public enum Key {
        UNKNOWN("UNKNOWN", "UNKNOWN"),
        ESCAPE("ESCAPE", "ESCAPE"),
        ENTER("RETURN", "ENTER"),
        NUMPAD_ENTER("NUMPADENTER", "KP_ENTER"),
        TAB("TAB", "TAB"),
        LEFT("LEFT", "LEFT"),
        RIGHT("RIGHT", "RIGHT"),
        UP("UP", "UP"),
        DOWN("DOWN", "DOWN"),
        DELETE("DELETE", "DELETE"),
        Z("Z", "Z"),
        LEFT_SHIFT("LSHIFT", "LEFT_SHIFT"),
        RIGHT_SHIFT("RSHIFT", "RIGHT_SHIFT"),
        LEFT_CONTROL("LCONTROL", "LEFT_CONTROL"),
        RIGHT_CONTROL("RCONTROL", "RIGHT_CONTROL");

        private final String vanillaName;
        private final String glfwName;
        private volatile int vanillaCode = Integer.MIN_VALUE;
        private volatile int glfwCode = Integer.MIN_VALUE;

        Key(String vanillaName, String glfwName) {
            this.vanillaName = vanillaName;
            this.glfwName = glfwName;
        }
    }

    public enum MouseButton {
        LEFT("LEFT", "LEFT"),
        MIDDLE("MIDDLE", "MIDDLE"),
        RIGHT("RIGHT", "RIGHT");

        private final String vanillaName;
        private final String glfwName;
        private volatile int vanillaCode = Integer.MIN_VALUE;
        private volatile int glfwCode = Integer.MIN_VALUE;
        private volatile int sdlMask = Integer.MIN_VALUE;

        MouseButton(String vanillaName, String glfwName) {
            this.vanillaName = vanillaName;
            this.glfwName = glfwName;
        }
    }

    public static int keyCode(Key key) {
        if (key == null) key = Key.UNKNOWN;
        int vanilla = vanillaCode(key);
        return vanilla >= 0 ? vanilla : glfwKeyCode(key);
    }

    /** Returns the vanilla key value for an event, translating SDL's physical key through InputConstants. */
    public static int keyCode(Object event) {
        if (event == null) return keyCode(Key.UNKNOWN);
        Class<?> constants = RuntimeReflectionCache.findClassAny(INPUT_CONSTANTS);
        if (constants != null) {
            Object mapped = invokeStaticSite(GET_KEY_EVENT, constants, event);
            if (mapped == null) mapped = mapVanillaKey(constants, event);
            if (mapped != null) {
                Object value = invokeNoArg(mapped, "getValue");
                if (value instanceof Number) return ((Number) value).intValue();
            }
            return keyCode(Key.UNKNOWN);
        }
        Object raw = invokeNoArg(event, "key", "comp_4795", "getKey");
        return raw instanceof Number ? ((Number) raw).intValue() : keyCode(Key.UNKNOWN);
    }

    public static boolean isKeyCode(int code, Key key) {
        int expected = keyCode(key);
        return expected >= 0 && code == expected;
    }

    public static boolean isKeyDown(Object client, Key key) {
        Class<?> constants = RuntimeReflectionCache.findClassAny(INPUT_CONSTANTS);
        int code = keyCode(key);
        if (usesSdlInputBackend(constants)) {
            Object state = code < 0 ? null : invokeStaticSite(IS_KEY_DOWN, constants, code);
            // SDL windows must never reach GLFW, even if another mod supplies GLFW classes.
            return Boolean.TRUE.equals(state);
        }
        if (constants != null && code >= 0) {
            Object window = window(client);
            long handle = longValue(invokeSite(WINDOW_HANDLE, window));
            Object state = invokeStaticSite(IS_KEY_DOWN, constants, handle, code);
            if (state instanceof Boolean) return ((Boolean) state).booleanValue();
        }

        Object window = window(client);
        long handle = longValue(invokeSite(WINDOW_HANDLE, window));
        Class<?> glfw = RuntimeReflectionCache.findClassAny(GLFW);
        Object rawCode = Integer.valueOf(glfwKeyCode(key == null ? Key.UNKNOWN : key));
        Object press = staticField(glfw, "GLFW_PRESS");
        if (handle == 0L || !(rawCode instanceof Number) || !(press instanceof Number)) return false;
        Object state = invokeStaticSite(GLFW_GET_KEY, glfw, handle, ((Number) rawCode).intValue());
        return state instanceof Number && ((Number) state).intValue() == ((Number) press).intValue();
    }

    public static int mouseButtonCode(MouseButton button) {
        if (button == null) button = MouseButton.LEFT;
        int vanilla = vanillaMouseCode(button);
        return vanilla >= 0 ? vanilla : glfwMouseCode(button);
    }

    /** Maps a vanilla mouse event's button value to a logical button without assuming SDL numbering. */
    public static MouseButton mouseButton(Object event) {
        Object raw = invokeNoArg(event, "button", "comp_4790", "getButton");
        if (!(raw instanceof Number)) return null;
        int code = ((Number) raw).intValue();
        for (MouseButton button : MouseButton.values()) {
            if (code == mouseButtonCode(button)) return button;
        }
        return null;
    }

    public static boolean isMouseButtonDown(Object client, MouseButton button) {
        return isMouseButtonDown(client, button, null);
    }

    static boolean isMouseButtonDown(Object client, MouseButton button,
                                     PhysicalMousePoller injectedPoller) {
        MouseButton logicalButton = button == null ? MouseButton.LEFT : button;
        if (injectedPoller != null) {
            Boolean physical = injectedPoller.isPressed(logicalButton);
            if (physical != null) return physical.booleanValue();
        }

        Class<?> inputConstants = RuntimeReflectionCache.findClassAny(INPUT_CONSTANTS);
        if (usesSdlInputBackend(inputConstants)) {
            Boolean physical = sdlMouseButtonDown(logicalButton);
            // Do not use MouseHandler's gameplay-only flags if the SDL poller is unavailable.
            return physical != null && physical.booleanValue();
        }

        Boolean glfwPhysical = glfwMouseButtonDown(client, logicalButton);
        if (glfwPhysical != null) return glfwPhysical.booleanValue();

        // Old-runtime fallback for test doubles/clients without a usable window handle.
        Object mouse = mouseHandler(client);
        String stateMethod;
        switch (logicalButton) {
            case MIDDLE: stateMethod = "isMiddlePressed"; break;
            case RIGHT: stateMethod = "isRightPressed"; break;
            case LEFT:
            default: stateMethod = "isLeftPressed"; break;
        }
        Object state = invokeNoArg(mouse, stateMethod);
        return state instanceof Boolean && ((Boolean) state).booleanValue();
    }

    interface PhysicalMousePoller {
        Boolean isPressed(MouseButton button);
    }

    private static Boolean sdlMouseButtonDown(MouseButton button) {
        Class<?> sdlMouse = RuntimeReflectionCache.findClassAny(SDL_MOUSE);
        Object state = invokeStaticSite(SDL_GET_MOUSE_STATE, sdlMouse, SDL_NO_COORDINATES);
        int mask = sdlMouseMask(button, sdlMouse);
        if (!(state instanceof Number) || mask < 0) return null;
        return Boolean.valueOf((((Number) state).intValue() & mask) != 0);
    }

    private static int sdlMouseMask(MouseButton button, Class<?> sdlMouse) {
        int cached = button.sdlMask;
        if (cached != Integer.MIN_VALUE) return cached;
        String field;
        switch (button) {
            case MIDDLE: field = "SDL_BUTTON_MMASK"; break;
            case RIGHT: field = "SDL_BUTTON_RMASK"; break;
            case LEFT:
            default: field = "SDL_BUTTON_LMASK"; break;
        }
        Object value = staticField(sdlMouse, field);
        cached = value instanceof Number ? ((Number) value).intValue() : -1;
        button.sdlMask = cached;
        return cached;
    }

    private static Boolean glfwMouseButtonDown(Object client, MouseButton button) {
        Object window = window(client);
        long handle = longValue(invokeSite(WINDOW_HANDLE, window));
        Class<?> glfw = RuntimeReflectionCache.findClassAny(GLFW);
        int rawButton = glfwMouseCode(button);
        Object press = staticField(glfw, "GLFW_PRESS");
        if (handle == 0L || rawButton < 0 || !(press instanceof Number)) return null;
        Object state = invokeStaticSite(GLFW_GET_MOUSE_BUTTON, glfw, handle, rawButton);
        if (!(state instanceof Number)) return null;
        return Boolean.valueOf(((Number) state).intValue() == ((Number) press).intValue());
    }

    private static boolean usesSdlInputBackend(Class<?> inputConstants) {
        if (inputConstants == null) return false;
        if (checkedInputConstantsClass == inputConstants) return sdlInputBackend;
        synchronized (FabricInputBridge.class) {
            if (checkedInputConstantsClass != inputConstants) {
                Method method = RuntimeReflectionCache.findCompatibleMethod(
                        inputConstants, true, new String[] {"isKeyDown"}, Integer.valueOf(0));
                sdlInputBackend = method != null
                        && method.getReturnType() == Boolean.TYPE
                        && method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0] == Integer.TYPE;
                checkedInputConstantsClass = inputConstants;
            }
            return sdlInputBackend;
        }
    }

    public static boolean hasShiftModifier(Object event) {
        return hasModifier(event, "MOD_SHIFT", "GLFW_MOD_SHIFT");
    }

    public static boolean hasControlModifier(Object event) {
        return hasModifier(event, "MOD_CONTROL", "GLFW_MOD_CONTROL");
    }

    private static boolean hasModifier(Object event, String vanillaName, String glfwName) {
        Object raw = invokeNoArg(event, "modifiers", "comp_4797", "getModifiers");
        if (!(raw instanceof Number)) return false;
        int mask = modifierMask(vanillaName, glfwName, "MOD_SHIFT".equals(vanillaName));
        return mask >= 0 && ((((Number) raw).intValue() & mask) != 0);
    }

    public static CursorPosition cursorPosition(Object client, Object window,
                                                int scaledWidth, int scaledHeight) {
        Object mouse = mouseHandler(client);
        Object x = invokeSite(WINDOW_CURSOR_X, mouse, window);
        Object y = invokeSite(WINDOW_CURSOR_Y, mouse, window);
        if (x instanceof Number && y instanceof Number) {
            return new CursorPosition(clamp((int) Math.floor(((Number) x).doubleValue()), scaledWidth),
                    clamp((int) Math.floor(((Number) y).doubleValue()), scaledHeight));
        }

        Object rawX = invokeSite(MOUSE_X, mouse);
        Object rawY = invokeSite(MOUSE_Y, mouse);
        if (!(rawX instanceof Number)) rawX = fieldValue(mouse, "x", "field_1795");
        if (!(rawY instanceof Number)) rawY = fieldValue(mouse, "y", "field_1794");
        Object screenWidth = invokeNoArg(window, "getScreenWidth", "getWidth",
                "method_4495", "method_4489", "method_4480");
        Object screenHeight = invokeNoArg(window, "getScreenHeight", "getHeight",
                "method_4494", "method_4506", "method_4507");
        long handle = longValue(invokeSite(WINDOW_HANDLE, window));
        Class<?> glfw = usesSdlInputBackend(RuntimeReflectionCache.findClassAny(INPUT_CONSTANTS))
                ? null : RuntimeReflectionCache.findClassAny(GLFW);
        if (handle != 0L && glfw != null) {
            int[] windowWidth = {0};
            int[] windowHeight = {0};
            invokeStaticSite(GLFW_GET_WINDOW_SIZE, glfw, handle, windowWidth, windowHeight);
            if (windowWidth[0] > 0) screenWidth = Integer.valueOf(windowWidth[0]);
            if (windowHeight[0] > 0) screenHeight = Integer.valueOf(windowHeight[0]);
            double[] cursorX = {rawX instanceof Number ? ((Number) rawX).doubleValue() : 0.0D};
            double[] cursorY = {rawY instanceof Number ? ((Number) rawY).doubleValue() : 0.0D};
            invokeStaticSite(GLFW_GET_CURSOR_POS, glfw, handle, cursorX, cursorY);
            rawX = Double.valueOf(cursorX[0]);
            rawY = Double.valueOf(cursorY[0]);
        }
        int sourceWidth = screenWidth instanceof Number ? ((Number) screenWidth).intValue() : scaledWidth;
        int sourceHeight = screenHeight instanceof Number ? ((Number) screenHeight).intValue() : scaledHeight;
        return new CursorPosition(
                scale(rawX, sourceWidth, scaledWidth), scale(rawY, sourceHeight, scaledHeight));
    }

    public static void onTextInputFocusChange(Object client, Object owner, boolean focused) {
        if (client == null || owner == null) return;
        if (invokeSiteIfPresent(FOCUS_CHANGE, client, owner, Boolean.valueOf(focused))) return;
        Object manager = invokeNoArg(client, "textInputManager");
        invokeSiteIfPresent(FOCUS_CHANGE, manager, Boolean.valueOf(focused));
    }

    private static Object mapVanillaKey(Class<?> constants, Object event) {
        Object key = invokeNoArg(event, "key", "comp_4795", "getKey");
        Object scanCode = invokeNoArg(event, "scanCode", "scancode", "comp_4796", "getScanCode");
        if (!(key instanceof Number) || !(scanCode instanceof Number)) return null;
        return invokeStaticSite(GET_KEY_PAIR, constants, ((Number) key).intValue(),
                ((Number) scanCode).intValue());
    }

    private static int vanillaCode(Key key) {
        int cached = key.vanillaCode;
        if (cached != Integer.MIN_VALUE) return cached;
        Class<?> constants = RuntimeReflectionCache.findClassAny(INPUT_CONSTANTS);
        if (constants == null) return -1;
        Object value = key == Key.UNKNOWN
                ? staticField(constants, "UNKNOWN", "KEY_UNKNOWN")
                : staticField(constants, "KEY_" + key.vanillaName);
        if (value != null && !(value instanceof Number)) value = invokeNoArg(value, "getValue");
        cached = value instanceof Number ? ((Number) value).intValue() : -1;
        key.vanillaCode = cached;
        return cached;
    }

    private static int glfwKeyCode(Key key) {
        int cached = key.glfwCode;
        if (cached != Integer.MIN_VALUE) return cached;
        Object value = staticField(RuntimeReflectionCache.findClassAny(GLFW),
                "GLFW_KEY_" + key.glfwName);
        cached = value instanceof Number ? ((Number) value).intValue() : -1;
        key.glfwCode = cached;
        return cached;
    }

    private static int vanillaMouseCode(MouseButton button) {
        int cached = button.vanillaCode;
        if (cached != Integer.MIN_VALUE) return cached;
        Object value = staticField(RuntimeReflectionCache.findClassAny(INPUT_CONSTANTS),
                "MOUSE_BUTTON_" + button.vanillaName);
        cached = value instanceof Number ? ((Number) value).intValue() : -1;
        button.vanillaCode = cached;
        return cached;
    }

    private static int glfwMouseCode(MouseButton button) {
        int cached = button.glfwCode;
        if (cached != Integer.MIN_VALUE) return cached;
        Object value = staticField(RuntimeReflectionCache.findClassAny(GLFW),
                "GLFW_MOUSE_BUTTON_" + button.glfwName);
        cached = value instanceof Number ? ((Number) value).intValue() : -1;
        button.glfwCode = cached;
        return cached;
    }

    private static int modifierMask(String vanillaName, String glfwName, boolean shift) {
        int cached = shift ? shiftMask : controlMask;
        if (cached != Integer.MIN_VALUE) return cached;
        Object value = staticField(RuntimeReflectionCache.findClassAny(INPUT_CONSTANTS), vanillaName);
        if (!(value instanceof Number)) {
            value = staticField(RuntimeReflectionCache.findClassAny(GLFW), glfwName);
        }
        cached = value instanceof Number ? ((Number) value).intValue() : -1;
        if (shift) shiftMask = cached; else controlMask = cached;
        return cached;
    }

    private static Object mouseHandler(Object client) {
        Object mouse = invokeNoArg(client, "getMouseHandler", "mouseHandler", "getMouse");
        if (mouse != null) return mouse;
        try {
            return RuntimeReflectionCache.fieldAnyDeep(client, MOUSE_HANDLER_FIELDS);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Object window(Object client) {
        Object value = invokeNoArg(client, "getWindow", "method_22683", "window", "getWindowHandle");
        if (value != null) return value;
        try {
            return RuntimeReflectionCache.fieldAnyDeep(client, WINDOW_FIELDS);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Object staticField(Class<?> type, String... names) {
        if (type == null) return null;
        try {
            return RuntimeReflectionCache.staticFieldAny(type, names);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Object fieldValue(Object target, String... names) {
        if (target == null) return null;
        try {
            return RuntimeReflectionCache.fieldAnyDeep(target, names);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String... names) {
        try {
            return RuntimeReflectionCache.invokeAny(target, names);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object invokeSite(RuntimeReflectionCache.InvocationSite site,
                                     Object target, Object... args) {
        try {
            return site.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean invokeSiteIfPresent(RuntimeReflectionCache.InvocationSite site,
                                                Object target, Object... args) {
        try {
            return site.invokeIfPresent(target, args);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static Object invokeStaticSite(RuntimeReflectionCache.InvocationSite site,
                                           Class<?> type, Object... args) {
        if (type == null) return null;
        try {
            return site.invokeStatic(type, args);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static int scale(Object raw, int sourceSize, int targetSize) {
        if (!(raw instanceof Number) || targetSize <= 0) return 0;
        int source = Math.max(1, sourceSize);
        int value = (int) Math.floor(((Number) raw).doubleValue());
        return clamp(value * targetSize / source, targetSize);
    }

    private static int clamp(int value, int limit) {
        return limit <= 0 ? 0 : Math.max(0, Math.min(limit - 1, value));
    }

    private static long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    public static final class CursorPosition {
        public final int x;
        public final int y;

        private CursorPosition(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
