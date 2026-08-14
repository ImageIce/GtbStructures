package com.gtbstructures.util;

import java.lang.reflect.Method;

/** Resolves the client UI objects across the pre-26.2 and 26.2 layouts. */
public final class MinecraftClientUiBridge {
    private static final String[] SET_SCREEN_METHODS = {
            "setScreen", "setScreenAndShow", "method_1507"
    };
    private static final String[] SCREEN_GETTER_METHODS = {"screen", "getScreen"};
    private static final String[] CHAT_GETTER_METHODS = {
            "getChatHud", "getChat", "method_1743"
    };

    private MinecraftClientUiBridge() {
    }

    public static Object currentScreen(Object client) throws ReflectiveOperationException {
        if (client == null) return null;
        Object direct = RuntimeReflectionCache.fieldAny(
                client, "screen", "currentScreen", "field_1755");
        if (direct != null) return direct;

        Object gui = clientGui(client);
        Object nested = RuntimeReflectionCache.invokeAnyArgs(
                gui, SCREEN_GETTER_METHODS, new Object[0]);
        return nested != null ? nested
                : RuntimeReflectionCache.fieldAny(gui, "screen", "currentScreen");
    }

    public static boolean setScreen(Object client, Object screen)
            throws ReflectiveOperationException {
        Object gui = clientGui(client);
        if (invokeIfPresent(gui, screen, "setScreen", "method_1507")) return true;
        return invokeIfPresent(client, screen, SET_SCREEN_METHODS);
    }

    public static Object chatHud(Object client) throws ReflectiveOperationException {
        Object gui = clientGui(client);
        Object direct = RuntimeReflectionCache.invokeAnyArgs(
                gui, CHAT_GETTER_METHODS, new Object[0]);
        if (direct != null) return direct;

        Object hud = RuntimeReflectionCache.fieldAny(gui, "hud", "inGameHud");
        return RuntimeReflectionCache.invokeAnyArgs(hud, CHAT_GETTER_METHODS, new Object[0]);
    }

    private static Object clientGui(Object client) throws IllegalAccessException {
        return RuntimeReflectionCache.fieldAny(
                client, "inGameHud", "gui", "field_1705");
    }

    private static boolean invokeIfPresent(Object target, Object argument, String... names)
            throws ReflectiveOperationException {
        if (target == null) return false;
        Method method = RuntimeReflectionCache.findCompatibleMethod(
                target.getClass(), false, names, argument);
        if (method == null) return false;
        method.invoke(target, argument);
        return true;
    }
}
