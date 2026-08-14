package com.gtbstructures.items;

import com.gtbstructures.GtbStructuresSettings;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class SpawnEggItemModelOverride {
    private static final String CLASSIC_MODEL_ROOT = "gtbstructures:spawn_egg/classic/";
    private static volatile MethodHandle stackModelGetter;
    private static volatile MethodHandle resourceLocationFactory;
    private static volatile Class<?> resourceLocationClass;

    private SpawnEggItemModelOverride() {
    }

    public static String modelIdFor(String registryName, String selectedSkin) {
        if (!"classic".equals(selectedSkin)) return null;
        ModernSpawnEggData egg = HandheldItemTracker.spawnEggDataFromRegistry(registryName);
        return egg == null ? null : CLASSIC_MODEL_ROOT + egg.textureName;
    }

    public static Object resolveModel(Object stack, Object componentType, String selectedSkin) {
        Object vanillaModel = invokeStackModelGetter(stack, componentType);
        if (vanillaModel == null) return null;
        if (!GtbStructuresSettings.INSTANCE.modEnabled) return vanillaModel;
        String override = modelIdFor(vanillaModel.toString(), selectedSkin);
        return override == null ? vanillaModel : createResourceLocation(vanillaModel.getClass(), override);
    }

    private static Object invokeStackModelGetter(Object stack, Object componentType) {
        try {
            MethodHandle handle = stackModelGetter;
            if (handle == null) {
                synchronized (SpawnEggItemModelOverride.class) {
                    handle = stackModelGetter;
                    if (handle == null) {
                        handle = findStackModelGetter(stack.getClass(), componentType.getClass());
                        stackModelGetter = handle;
                    }
                }
            }
            return (Object) handle.invokeExact(stack, componentType);
        } catch (Throwable error) {
            throw new IllegalStateException("Unable to read the vanilla item model component", error);
        }
    }

    private static MethodHandle findStackModelGetter(Class<?> stackClass, Class<?> componentTypeClass)
            throws IllegalAccessException, NoSuchMethodException {
        for (String name : new String[]{"method_58694", "get", "a"}) {
            for (Method method : stackClass.getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 1
                        || method.getReturnType().isPrimitive()
                        || !method.getParameterTypes()[0].isAssignableFrom(componentTypeClass)) {
                    continue;
                }
                return MethodHandles.publicLookup().unreflect(method).asType(
                        MethodType.methodType(Object.class, Object.class, Object.class));
            }
        }
        throw new NoSuchMethodException(stackClass.getName() + ".method_58694(componentType)");
    }

    private static Object createResourceLocation(Class<?> type, String value) {
        try {
            MethodHandle handle = resourceLocationFactory;
            if (handle == null || resourceLocationClass != type) {
                synchronized (SpawnEggItemModelOverride.class) {
                    handle = resourceLocationFactory;
                    if (handle == null || resourceLocationClass != type) {
                        handle = findResourceLocationFactory(type);
                        resourceLocationClass = type;
                        resourceLocationFactory = handle;
                    }
                }
            }
            return (Object) handle.invokeExact(value);
        } catch (Throwable error) {
            throw new IllegalStateException("Unable to create the selected spawn egg model id", error);
        }
    }

    private static MethodHandle findResourceLocationFactory(Class<?> type)
            throws IllegalAccessException, NoSuchMethodException {
        for (String name : new String[]{"method_60654", "parse", "of"}) {
            for (Method method : type.getMethods()) {
                if (!method.getName().equals(name) || !Modifier.isStatic(method.getModifiers())
                        || method.getParameterCount() != 1 || method.getParameterTypes()[0] != String.class
                        || method.getReturnType() != type) {
                    continue;
                }
                return MethodHandles.publicLookup().unreflect(method).asType(
                        MethodType.methodType(Object.class, String.class));
            }
        }
        throw new NoSuchMethodException(type.getName() + ".method_60654(String)");
    }
}
