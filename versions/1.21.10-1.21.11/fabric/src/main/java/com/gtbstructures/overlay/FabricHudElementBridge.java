package com.gtbstructures.overlay;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;

/** Reflection bridge for Fabric's layered HUD API when it is available. */
public final class FabricHudElementBridge {
    private static final String MOD_ID = "gtbstructures";

    private FabricHudElementBridge() {
    }

    public interface Renderer {
        void render(Object graphics) throws Exception;
    }

    public static boolean registerBeforeChat(String path, Renderer renderer) {
        return registerBeforeVanilla("CHAT", path, renderer);
    }

    public static boolean registerLast(String path, Renderer renderer) {
        if (path == null || path.trim().isEmpty() || renderer == null) return false;
        try {
            Class<?> elementClass = Class.forName(
                    "net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement");
            Object element = element(elementClass, path, renderer);
            Object identifier = identifier(MOD_ID + ":" + path);
            if (identifier == null) return false;

            Class<?> registryClass = Class.forName(
                    "net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry");
            for (Method method : registryClass.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())
                        || !method.getName().equals("addLast")
                        || method.getParameterCount() != 2
                        || !method.getParameterTypes()[1].isAssignableFrom(elementClass)) {
                    continue;
                }
                method.invoke(null, identifier, element);
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
        return false;
    }

    public static boolean registerBeforeVanilla(String vanillaField, String path, Renderer renderer) {
        if (vanillaField == null || vanillaField.trim().isEmpty()
                || path == null || path.trim().isEmpty() || renderer == null) return false;
        try {
            Class<?> elementClass = Class.forName(
                    "net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement");
            Object element = element(elementClass, path, renderer);

            Class<?> vanillaClass = Class.forName(
                    "net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements");
            Object anchor = vanillaClass.getField(vanillaField).get(null);
            Object identifier = identifier(MOD_ID + ":" + path);
            if (anchor == null || identifier == null) return false;

            Class<?> registryClass = Class.forName(
                    "net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry");
            for (Method method : registryClass.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())
                        || !method.getName().equals("attachElementBefore")
                        || method.getParameterCount() != 3
                        || !method.getParameterTypes()[2].isAssignableFrom(elementClass)) {
                    continue;
                }
                method.invoke(null, anchor, identifier, element);
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
        return false;
    }

    public static boolean replaceVanillaScoreboard(BooleanSupplier suppressor, IntSupplier heightOffset) {
        try {
            Class<?> elementClass = Class.forName(
                    "net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement");
            Class<?> vanillaClass = Class.forName(
                    "net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements");
            Object scoreboard = vanillaClass.getField("SCOREBOARD").get(null);
            if (scoreboard == null) return false;

            Class<?> registryClass = Class.forName(
                    "net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry");
            for (Method method : registryClass.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())
                        || !method.getName().equals("replaceElement")
                        || method.getParameterCount() != 2
                        || !method.getParameterTypes()[1].isAssignableFrom(Function.class)) {
                    continue;
                }
                Function<Object, Object> wrapper = original -> java.lang.reflect.Proxy.newProxyInstance(
                        FabricHudElementBridge.class.getClassLoader(),
                        new Class<?>[] {elementClass},
                        (proxy, elementMethod, args) -> {
                            if ((elementMethod.getName().equals("render")
                                    || elementMethod.getName().equals("extractRenderState"))
                                    && args != null && args.length >= 1) {
                                if (suppressor == null || !suppressor.getAsBoolean()) {
                                    int offset = heightOffset == null ? 0 : heightOffset.getAsInt();
                                    return invokeElementWithTranslation(original, elementMethod, args, offset);
                                }
                                return null;
                            }
                            if (elementMethod.getName().equals("toString")) {
                                return "GtbStructures guarded vanilla scoreboard";
                            }
                            return original == null ? null : elementMethod.invoke(original, args);
                        });
                method.invoke(null, scoreboard, wrapper);
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
        return false;
    }

    private static Object invokeElementWithTranslation(Object original, Method elementMethod,
                                                       Object[] args, int heightOffset) throws Throwable {
        if (original == null) return null;
        PoseRestore restore = args == null || args.length == 0
                ? null : pushScoreboardTranslation(args[0], heightOffset);
        try {
            return elementMethod.invoke(original, args);
        } catch (InvocationTargetException e) {
            throw e.getCause() == null ? e : e.getCause();
        } finally {
            if (restore != null) restore.pop();
        }
    }

    public static Object invokeElementWithTranslationForTest(Object original, Method elementMethod,
                                                             Object[] args, int heightOffset) throws Throwable {
        return invokeElementWithTranslation(original, elementMethod, args, heightOffset);
    }

    private static PoseRestore pushScoreboardTranslation(Object graphics, int heightOffset) {
        if (graphics == null || heightOffset == 0) return null;
        try {
            Method poseMethod = noArgMethod(graphics.getClass(), "pose", "getPose", "getPoseStack");
            if (poseMethod == null) return null;
            Object pose = poseMethod.invoke(graphics);
            if (pose == null) return null;
            Method push = noArgMethod(pose.getClass(), "pushPose", "pushMatrix", "push");
            Method pop = noArgMethod(pose.getClass(), "popPose", "popMatrix", "pop");
            Method translate = translationMethod(pose.getClass());
            if (push == null || pop == null || translate == null) return null;

            boolean pushed = false;
            try {
                push.invoke(pose);
                pushed = true;
                Object[] values = translate.getParameterCount() == 2
                        ? new Object[] {number(translate.getParameterTypes()[0], 0F),
                        number(translate.getParameterTypes()[1], -heightOffset)}
                        : new Object[] {number(translate.getParameterTypes()[0], 0F),
                        number(translate.getParameterTypes()[1], -heightOffset),
                        number(translate.getParameterTypes()[2], 0F)};
                translate.invoke(pose, values);
                return new PoseRestore(pose, pop);
            } catch (ReflectiveOperationException | RuntimeException e) {
                if (pushed) {
                    try {
                        pop.invoke(pose);
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                        // Leave the original element usable even if this runtime cannot translate it.
                    }
                }
                return null;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Method noArgMethod(Class<?> type, String... names) {
        for (String name : names) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // Try the next cross-version method name.
            }
        }
        return null;
    }

    private static Method translationMethod(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals("translate")) continue;
            if (method.getParameterCount() == 2 || method.getParameterCount() == 3) return method;
        }
        return null;
    }

    private static Object number(Class<?> type, float value) {
        if (type == Double.TYPE || type == Double.class) return Double.valueOf(value);
        if (type == Long.TYPE || type == Long.class) return Long.valueOf((long) value);
        if (type == Integer.TYPE || type == Integer.class) return Integer.valueOf((int) value);
        return Float.valueOf(value);
    }

    private static final class PoseRestore {
        private final Object pose;
        private final Method pop;

        private PoseRestore(Object pose, Method pop) {
            this.pose = pose;
            this.pop = pop;
        }

        private void pop() {
            try {
                pop.invoke(pose);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Best effort: the original scoreboard has already been extracted/rendered.
            }
        }
    }

    private static Object element(Class<?> elementClass, String path, Renderer renderer) {
        return java.lang.reflect.Proxy.newProxyInstance(
                FabricHudElementBridge.class.getClassLoader(),
                new Class<?>[] {elementClass},
                (proxy, method, args) -> {
                    if ((method.getName().equals("render")
                            || method.getName().equals("extractRenderState"))
                            && args != null && args.length >= 1) {
                        renderer.render(args[0]);
                        return null;
                    }
                    if (method.getName().equals("toString")) {
                        return "GtbStructures HUD element " + path;
                    }
                    return null;
                });
    }

    private static Object identifier(String registry) throws ReflectiveOperationException {
        Class<?> idClass = classAny("net.minecraft.resources.ResourceLocation",
                "net.minecraft.resources.Identifier",
                "net.minecraft.util.Identifier", "net.minecraft.class_2960", "amj", "amo");
        String value = registry.indexOf(':') >= 0 ? registry : "minecraft:" + registry;
        int colon = value.indexOf(':');
        if (colon > 0 && colon + 1 < value.length()) {
            String namespace = value.substring(0, colon);
            String path = value.substring(colon + 1);
            for (Method method : idClass.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())
                        || !idClass.isAssignableFrom(method.getReturnType())
                        || method.getParameterCount() != 2
                        || !method.getParameterTypes()[0].equals(String.class)
                        || !method.getParameterTypes()[1].equals(String.class)) {
                    continue;
                }
                try {
                    Object out = method.invoke(null, namespace, path);
                    if (out != null) return out;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Try the next Identifier factory.
                }
            }
            try {
                Constructor<?> constructor = idClass.getDeclaredConstructor(String.class, String.class);
                constructor.setAccessible(true);
                return constructor.newInstance(namespace, path);
            } catch (NoSuchMethodException ignored) {
                // Try the one-string constructor below.
            }
        }
        for (Method method : idClass.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())
                    || !idClass.isAssignableFrom(method.getReturnType())
                    || method.getParameterCount() != 1
                    || !method.getParameterTypes()[0].equals(String.class)) {
                continue;
            }
            try {
                Object out = method.invoke(null, value);
                if (out != null) return out;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next Identifier factory.
            }
        }
        try {
            Constructor<?> constructor = idClass.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(value);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
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
        throw last == null ? new ClassNotFoundException() : last;
    }
}
