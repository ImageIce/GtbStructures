package com.gtbstructures.font;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.util.RuntimeReflectionCache;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Adds the GTBS classic-font toggle to Minecraft's Font Settings screen. */
public final class ClassicFontLanguageScreenButton {
    private static final String MODE_CLASSIC = "classic";
    private static final String MODE_NEW = "new";
    private static final String OPTION_LABEL = "Force 1.8 Font";
    private static final String LABEL_PREFIX = OPTION_LABEL + ": ";
    private static final Set<Object> INSTALLED =
            Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    private ClassicFontLanguageScreenButton() {
    }

    public static void install(Object screen, Object minecraft, File gameDir) throws ReflectiveOperationException {
        if (!isFontOptionsScreen(screen)) return;
        synchronized (INSTALLED) {
            if (!INSTALLED.add(screen)) return;
        }
        try {
            addOptionRow(screen, createBooleanOption(screen, minecraft, gameDir));
        } catch (ReflectiveOperationException | RuntimeException e) {
            synchronized (INSTALLED) {
                INSTALLED.remove(screen);
            }
            throw e;
        }
    }

    static boolean isFontOptionsScreenClassNameForTest(String className) {
        return isFontOptionsScreenClassName(className);
    }

    static boolean isFontOptionsScreenForTest(Object screen) {
        return isFontOptionsScreen(screen);
    }

    static String labelForTest(String mode) {
        return label(mode);
    }

    static boolean addOptionToListForTest(Object target, Object option) throws ReflectiveOperationException {
        return addOptionToList(target, option);
    }

    static Object createBooleanOptionForTest(Class<?> optionClass, boolean current,
                                             Consumer<Object> onChange)
            throws ReflectiveOperationException {
        return createBooleanOption(optionClass, current, onChange);
    }

    private static boolean isFontOptionsScreen(Object screen) {
        if (screen == null) return false;
        if (isFontOptionsScreenClassName(screen.getClass().getName())) return true;
        String title = componentDebugString(title(screen));
        return title.contains("options.font.title") || "Font Settings".equals(title);
    }

    private static boolean isFontOptionsScreenClassName(String className) {
        if (className == null) return false;
        return "net.minecraft.client.gui.screens.options.FontOptionsScreen".equals(className)
                || "net.minecraft.class_9246".equals(className)
                || "gqd".equals(className)
                || "gvx".equals(className)
                || className.endsWith(".FontOptionsScreen");
    }

    private static Object title(Object screen) {
        Object title = tryInvokeNoArg(screen, "getTitle", "method_25440", "aF_");
        if (title != null) return title;
        title = fieldValueQuietly(screen, "title", "field_22785", "m");
        if (title != null) return title;
        for (Object value : fieldValues(screen)) {
            String text = componentDebugString(value);
            if (text.contains("options.font.title") || "Font Settings".equals(text)) return value;
        }
        return null;
    }

    private static String componentDebugString(Object component) {
        if (component == null) return "";
        String debug = String.valueOf(component);
        Object string = tryInvokeNoArg(component, "getString", "asString", "method_10851");
        if (string != null) return String.valueOf(string) + "\n" + debug;
        return debug;
    }

    private static Object createBooleanOption(Object screen, Object minecraft, File gameDir)
            throws ReflectiveOperationException {
        Class<?> optionClass = optionClass(screen);
        boolean current = MODE_CLASSIC.equals(ClassicFontResourcePack.normalizeMode(
                GtbStructuresSettings.INSTANCE.fontMode));
        Consumer<Object> onChange = value -> setClassic(Boolean.TRUE.equals(value), minecraft, gameDir);
        return createBooleanOption(optionClass, current, onChange);
    }

    private static Object createBooleanOption(Class<?> optionClass, boolean current,
                                              Consumer<Object> onChange)
            throws ReflectiveOperationException {
        Method factory = findBooleanOptionFactory(optionClass);
        Object listener = booleanUpdateListener(factory.getParameterTypes()[2], onChange);
        return factory.invoke(null, OPTION_LABEL, Boolean.valueOf(current), listener);
    }

    private static Class<?> optionClass(Object screen) throws ClassNotFoundException {
        Class<?> known = tryClassAny(
                "net.minecraft.client.OptionInstance",
                "net.minecraft.client.option.SimpleOption",
                "net.minecraft.class_7172",
                "gac",
                "gfn");
        if (known != null) return known;
        Object list = optionsList(screen);
        Class<?> discovered = discoverOptionClass(list);
        if (discovered != null) return discovered;
        for (Object value : fieldValues(screen)) {
            discovered = discoverOptionClass(value);
            if (discovered != null) return discovered;
        }
        throw new ClassNotFoundException("Minecraft OptionInstance");
    }

    private static Class<?> discoverOptionClass(Object target) {
        if (target == null) return null;
        for (Method method : methods(target.getClass())) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1) continue;
            Class<?> candidate = params[0].isArray() ? params[0].getComponentType() : params[0];
            if (candidate == null || candidate.isPrimitive()) continue;
            try {
                findBooleanOptionFactory(candidate);
                return candidate;
            } catch (NoSuchMethodException ignored) {
                // Try the next method parameter.
            }
        }
        return null;
    }

    private static Method findBooleanOptionFactory(Class<?> optionClass) throws NoSuchMethodException {
        for (Method method : methods(optionClass)) {
            Class<?>[] params = method.getParameterTypes();
            if (!Modifier.isStatic(method.getModifiers())
                    || params.length != 3
                    || params[0] != String.class
                    || params[1] != Boolean.TYPE
                    || !isBooleanUpdateListener(params[2])
                    || !optionClass.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            method.setAccessible(true);
            return method;
        }
        throw new NoSuchMethodException(optionClass.getName() + ".createBoolean");
    }

    private static boolean isBooleanUpdateListener(Class<?> type) {
        return Consumer.class.isAssignableFrom(type) || singleArgumentCallback(type) != null;
    }

    private static Object booleanUpdateListener(Class<?> type, Consumer<Object> onChange)
            throws NoSuchMethodException {
        if (type.isInstance(onChange)) return onChange;
        Method callback = singleArgumentCallback(type);
        if (callback == null || !type.isInterface()) {
            throw new NoSuchMethodException(type.getName() + ".boolean update callback");
        }
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> {
                    if (method.equals(callback)) {
                        onChange.accept(args == null || args.length == 0 ? null : args[0]);
                        return null;
                    }
                    if (method.getDeclaringClass() == Object.class) {
                        if ("toString".equals(method.getName())) return "GtbStructures classic-font option listener";
                        if ("hashCode".equals(method.getName())) return Integer.valueOf(System.identityHashCode(proxy));
                        if ("equals".equals(method.getName())) {
                            return Boolean.valueOf(args != null && args.length == 1 && proxy == args[0]);
                        }
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
    }

    private static Method singleArgumentCallback(Class<?> type) {
        if (type == null || !type.isInterface()) return null;
        Method callback = null;
        for (Method method : type.getMethods()) {
            if (!Modifier.isAbstract(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) continue;
            if (method.getParameterCount() != 1 || method.getReturnType() != Void.TYPE) return null;
            if (callback != null && !callback.equals(method)) return null;
            callback = method;
        }
        return callback;
    }

    private static void addOptionRow(Object screen, Object option) throws ReflectiveOperationException {
        Object list = optionsList(screen);
        if (addOptionToList(list, option)) return;
        for (Object value : fieldValues(screen)) {
            if (addOptionToList(value, option)) return;
        }
        throw new NoSuchMethodException(screen.getClass().getName() + ".OptionsList.addOption");
    }

    private static Object optionsList(Object screen) {
        Object list = fieldValueQuietly(screen, "list", "field_27916", "field_51824", "d");
        if (looksLikeOptionsList(list)) return list;
        for (Object value : fieldValues(screen)) {
            if (looksLikeOptionsList(value)) return value;
        }
        return list;
    }

    private static boolean looksLikeOptionsList(Object value) {
        if (value == null) return false;
        String className = value.getClass().getName();
        if (className.endsWith(".OptionsList")
                || "net.minecraft.class_353".equals(className)
                || "gen".equals(className)
                || "gkc".equals(className)) return true;
        return discoverOptionClass(value) != null;
    }

    private static boolean addOptionToList(Object target, Object option) throws ReflectiveOperationException {
        if (tryAddOptionArrayToList(target, option, true)) return true;
        if (tryAddOptionArrayToList(target, option, false)) return true;
        if (tryAddOptionToList(target, option, true)) return true;
        return tryAddOptionToList(target, option, false);
    }

    private static boolean tryAddOptionArrayToList(Object target, Object option, boolean namedOnly)
            throws ReflectiveOperationException {
        if (target == null || option == null) return false;
        Class<?> optionClass = option.getClass();
        for (Method method : methods(target.getClass())) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || !params[0].isArray()) continue;
            Class<?> componentType = params[0].getComponentType();
            if (componentType == null || !componentType.isAssignableFrom(optionClass)) continue;
            boolean namedAdd = isNamedAddOptionArrayMethod(method);
            if (namedOnly != namedAdd) continue;
            if (!namedAdd && !isGenericListMutationMethod(method)) continue;
            Object options = Array.newInstance(componentType, 1);
            Array.set(options, 0, option);
            int before = entryCount(target);
            method.setAccessible(true);
            method.invoke(target, new Object[] { options });
            int after = entryCount(target);
            if (before >= 0 && after <= before) continue;
            return true;
        }
        return false;
    }

    private static boolean tryAddOptionToList(Object target, Object option, boolean namedOnly)
            throws ReflectiveOperationException {
        if (target == null || option == null) return false;
        Class<?> optionClass = option.getClass();
        for (Method method : methods(target.getClass())) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || !params[0].isAssignableFrom(optionClass)) continue;
            boolean namedAdd = isNamedAddOptionMethod(method);
            if (namedOnly != namedAdd) continue;
            if (!namedAdd && !isGenericListMutationMethod(method)) continue;
            int before = entryCount(target);
            method.setAccessible(true);
            method.invoke(target, option);
            int after = entryCount(target);
            if (before >= 0 && after <= before) continue;
            return true;
        }
        return false;
    }

    private static boolean isNamedAddOptionArrayMethod(Method method) {
        String name = method.getName();
        return "addSmall".equals(name)
                || "addAll".equals(name)
                || "m_232533_".equals(name)
                || "method_20408".equals(name)
                || "method_41745".equals(name)
                || "a".equals(name);
    }

    private static boolean isNamedAddOptionMethod(Method method) {
        String name = method.getName();
        return "addSmall".equals(name)
                || "addBig".equals(name)
                || "addOption".equals(name)
                || "m_232528_".equals(name)
                || "method_20406".equals(name)
                || "method_41745".equals(name)
                || "a".equals(name);
    }

    private static boolean isGenericListMutationMethod(Method method) {
        Class<?> returnType = method.getReturnType();
        return returnType == Void.TYPE || returnType == Integer.TYPE || returnType == Boolean.TYPE;
    }

    private static int entryCount(Object target) {
        Object entries = tryInvokeNoArg(target, "children", "method_25396", "m_6702_", "a");
        if (entries instanceof Collection) return ((Collection<?>) entries).size();
        return -1;
    }

    private static void setClassic(boolean classic, Object minecraft, File gameDir) {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        settings.fontMode = classic ? MODE_CLASSIC : MODE_NEW;
        settings.save();
        ClassicFontResourcePack.applyWithoutReload(gameDir, minecraft, settings.fontMode);
        ClassicFontResourcePack.updateFontOptions(minecraft);
    }

    private static String label(String mode) {
        return LABEL_PREFIX + (MODE_CLASSIC.equals(ClassicFontResourcePack.normalizeMode(mode)) ? "ON" : "OFF");
    }

    private static Object fieldValueQuietly(Object target, String... names) {
        try {
            return fieldValue(target, names);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Object fieldValue(Object target, String... names) throws ReflectiveOperationException {
        if (target == null) return null;
        Class<?> type = target.getClass();
        while (type != null) {
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                    // Try the next alias.
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static ArrayList<Object> fieldValues(Object target) {
        ArrayList<Object> out = new ArrayList<Object>();
        if (target == null) return out;
        Class<?> type = target.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value != null && !out.contains(value)) out.add(value);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Try other fields.
                }
            }
            type = type.getSuperclass();
        }
        return out;
    }

    private static Object tryInvokeNoArg(Object target, String... names) {
        try {
            return invokeNoArg(target, names);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String... names) throws ReflectiveOperationException {
        return RuntimeReflectionCache.invokeAnyDeep(target, names);
    }

    private static Class<?> classAny(String... names) throws ClassNotFoundException {
        Class<?> type = tryClassAny(names);
        if (type != null) return type;
        throw new ClassNotFoundException(names.length == 0 ? "" : names[0]);
    }

    private static Class<?> tryClassAny(String... names) {
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
                // Try the next runtime namespace.
            }
        }
        return null;
    }

    private static Method[] methods(Class<?> start) {
        ArrayList<Method> out = new ArrayList<Method>();
        Class<?> type = start;
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) out.add(method);
            type = type.getSuperclass();
        }
        return out.toArray(new Method[0]);
    }

    private static boolean matches(String value, String... options) {
        for (String option : options) {
            if (option.equals(value)) return true;
        }
        return false;
    }
}
