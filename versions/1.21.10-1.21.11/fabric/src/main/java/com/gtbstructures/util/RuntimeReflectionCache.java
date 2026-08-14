package com.gtbstructures.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Runtime-name reflection with positive and negative lookup caching. */
public final class RuntimeReflectionCache {
    private static final Object MISS = new Object();
    private static final int INSTANCE = 0;
    private static final int STATIC = 1;
    private static final int ANY = 2;
    private static final int COMPATIBLE = 0;
    private static final int ARITY = 1;
    private static final int DEEP_NO_ARG = 2;

    private static final Map<String, Object> CLASSES = new ConcurrentHashMap<String, Object>();
    private static final Map<FieldKey, Object> FIELDS = new ConcurrentHashMap<FieldKey, Object>();
    private static final Map<MethodKey, Object> METHODS = new ConcurrentHashMap<MethodKey, Object>();
    private static final AtomicLong CLASS_SCANS = new AtomicLong();
    private static final AtomicLong FIELD_SCANS = new AtomicLong();
    private static final AtomicLong METHOD_SCANS = new AtomicLong();
    private static final AtomicLong METHOD_KEY_BUILDS = new AtomicLong();

    private RuntimeReflectionCache() {
    }

    public static Class<?> findClassAny(String... names) {
        if (names == null) return null;
        for (String name : names) {
            if (name == null || name.isEmpty()) continue;
            Object value = CLASSES.computeIfAbsent(name, RuntimeReflectionCache::loadClass);
            if (value instanceof Class) return (Class<?>) value;
        }
        return null;
    }

    public static Class<?> classAny(String... names) throws ClassNotFoundException {
        Class<?> type = findClassAny(names);
        if (type != null) return type;
        throw new ClassNotFoundException(names == null ? "" : Arrays.toString(names));
    }

    private static Object loadClass(String name) {
        CLASS_SCANS.incrementAndGet();
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
            return MISS;
        }
    }

    public static Object fieldAny(Object target, String... names) throws IllegalAccessException {
        return fieldValue(target, false, names);
    }

    public static Object fieldAnyDeep(Object target, String... names) throws IllegalAccessException {
        return fieldValue(target, true, names);
    }

    private static Object fieldValue(Object target, boolean deep, String... names)
            throws IllegalAccessException {
        if (target == null) return null;
        Field field = findField(target.getClass(), deep, false, names);
        return field == null ? null : field.get(target);
    }

    public static Object staticFieldAny(Class<?> type, String... names) throws IllegalAccessException {
        Field field = findField(type, false, true, names);
        return field == null ? null : field.get(null);
    }

    public static Field findField(Class<?> type, boolean deep, boolean staticOnly,
                                  String... names) {
        if (type == null) return null;
        FieldKey key = new FieldKey(type, deep, staticOnly, names);
        Object value = FIELDS.computeIfAbsent(key, RuntimeReflectionCache::resolveField);
        return value instanceof Field ? (Field) value : null;
    }

    private static Object resolveField(FieldKey key) {
        FIELD_SCANS.incrementAndGet();
        for (String name : key.names) {
            if (!key.deep) {
                Field field = namedField(key.type.getFields(), name, key.staticOnly);
                if (field != null) return field;
            }
            for (Class<?> current = key.type; current != null; current = current.getSuperclass()) {
                Field field = namedField(current.getDeclaredFields(), name, key.staticOnly);
                if (field != null) {
                    field.trySetAccessible();
                    return field;
                }
                if (!key.deep) break;
            }
        }
        return MISS;
    }

    private static Field namedField(Field[] fields, String name, boolean staticOnly) {
        for (Field field : fields) {
            if (field.getName().equals(name)
                    && (!staticOnly || Modifier.isStatic(field.getModifiers()))) return field;
        }
        return null;
    }

    public static Method methodAny(Class<?> type, String... names) throws NoSuchMethodException {
        Method method = findCompatibleMethod(type, ANY, names, new Object[0]);
        if (method != null) return method;
        throw new NoSuchMethodException(type == null ? "" : type.getName());
    }

    public static Method findCompatibleMethod(Class<?> type, boolean staticOnly,
                                              String[] names, Object... args) {
        return findCompatibleMethod(type, staticOnly ? STATIC : INSTANCE, names, args);
    }

    private static Method findCompatibleMethod(Class<?> type, int staticMode,
                                               String[] names, Object... args) {
        if (type == null) return null;
        MethodKey key = MethodKey.compatible(type, staticMode, names, args);
        Object value = METHODS.computeIfAbsent(key, RuntimeReflectionCache::resolveMethod);
        return value instanceof Method ? (Method) value : null;
    }

    public static Method findMethodByArity(Class<?> type, boolean staticOnly,
                                           String[] names, int arity) {
        if (type == null) return null;
        MethodKey key = MethodKey.arity(type, staticOnly ? STATIC : INSTANCE, names, arity);
        Object value = METHODS.computeIfAbsent(key, RuntimeReflectionCache::resolveMethod);
        return value instanceof Method ? (Method) value : null;
    }

    public static Object invokeAny(Object target, String... names)
            throws ReflectiveOperationException {
        return invokeAnyArgs(target, names, new Object[0]);
    }

    public static Object invokeAnyDeep(Object target, String... names)
            throws ReflectiveOperationException {
        if (target == null) return null;
        MethodKey key = MethodKey.deepNoArg(target.getClass(), names);
        Object value = METHODS.computeIfAbsent(key, RuntimeReflectionCache::resolveMethod);
        return value instanceof Method ? ((Method) value).invoke(target) : null;
    }

    public static Object invokeAnyWithArg(Object target, Object arg, String... names)
            throws ReflectiveOperationException {
        return invokeAnyArgs(target, names, arg);
    }

    public static Object invokeAnyArgs(Object target, String[] names, Object... args)
            throws ReflectiveOperationException {
        if (target == null) return null;
        Method method = findCompatibleMethod(target.getClass(), INSTANCE, names, args);
        return method == null ? null : method.invoke(target, args);
    }

    public static Object invokeStaticAnyArgs(Class<?> type, String[] names, Object... args)
            throws ReflectiveOperationException {
        Method method = findCompatibleMethod(type, STATIC, names, args);
        return method == null ? null : method.invoke(null, args);
    }

    /**
     * Creates a reusable instance-method call site. Unlike the general lookup helpers, stable
     * invocations do not rebuild a method key, copy method names, or allocate argument-type lists.
     */
    public static InvocationSite instanceSite(String... names) {
        return new InvocationSite(false, names);
    }

    /** Creates a reusable static-method call site with the same allocation-free hit path. */
    public static InvocationSite staticSite(String... names) {
        return new InvocationSite(true, names);
    }

    private static Object resolveMethod(MethodKey key) {
        METHOD_SCANS.incrementAndGet();
        Method method = namedMethod(key.type.getMethods(), key);
        if (method != null) {
            method.trySetAccessible();
            return method;
        }
        if (key.mode == DEEP_NO_ARG) {
            for (Class<?> current = key.type; current != null; current = current.getSuperclass()) {
                method = namedMethod(current.getDeclaredMethods(), key);
                if (method != null) {
                    method.trySetAccessible();
                    return method;
                }
            }
        }
        return MISS;
    }

    private static Method namedMethod(Method[] methods, MethodKey key) {
        for (String name : key.names) {
            for (Method method : methods) {
                if (!method.getName().equals(name) || !staticMatches(method, key.staticMode)) continue;
                if (key.mode == ARITY && method.getParameterCount() == key.arity) return method;
                if ((key.mode == COMPATIBLE || key.mode == DEEP_NO_ARG)
                        && compatible(method.getParameterTypes(), key.argumentTypes)) return method;
            }
        }
        return null;
    }

    private static boolean staticMatches(Method method, int staticMode) {
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        return staticMode == ANY || (staticMode == STATIC ? isStatic : !isStatic);
    }

    private static boolean compatible(Class<?>[] parameters, List<Class<?>> arguments) {
        if (parameters.length != arguments.size()) return false;
        for (int i = 0; i < parameters.length; i++) {
            Class<?> argument = arguments.get(i);
            if (argument == NullArgument.class) {
                if (parameters[i].isPrimitive()) return false;
            } else if (!boxed(parameters[i]).isAssignableFrom(argument)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == Integer.TYPE) return Integer.class;
        if (type == Boolean.TYPE) return Boolean.class;
        if (type == Float.TYPE) return Float.class;
        if (type == Long.TYPE) return Long.class;
        if (type == Double.TYPE) return Double.class;
        if (type == Byte.TYPE) return Byte.class;
        if (type == Short.TYPE) return Short.class;
        if (type == Character.TYPE) return Character.class;
        return type;
    }

    static void resetForTest() {
        CLASSES.clear();
        FIELDS.clear();
        METHODS.clear();
        CLASS_SCANS.set(0L);
        FIELD_SCANS.set(0L);
        METHOD_SCANS.set(0L);
        METHOD_KEY_BUILDS.set(0L);
    }

    static Stats statsForTest() {
        return new Stats(CLASS_SCANS.get(), FIELD_SCANS.get(), METHOD_SCANS.get(),
                METHOD_KEY_BUILDS.get());
    }

    static final class Stats {
        final long classScans;
        final long fieldScans;
        final long methodScans;
        final long methodKeyBuilds;

        private Stats(long classScans, long fieldScans, long methodScans, long methodKeyBuilds) {
            this.classScans = classScans;
            this.fieldScans = fieldScans;
            this.methodScans = methodScans;
            this.methodKeyBuilds = methodKeyBuilds;
        }
    }

    public static final class InvocationSite {
        private static final int MAX_RESOLUTIONS = 16;
        private static final Resolution[] EMPTY_RESOLUTIONS = new Resolution[0];

        private final boolean staticOnly;
        private final String[] names;
        private volatile Resolution[] resolutions = EMPTY_RESOLUTIONS;

        private InvocationSite(boolean staticOnly, String[] names) {
            this.staticOnly = staticOnly;
            this.names = names == null ? new String[0] : names.clone();
        }

        public boolean invokeIfPresent(Object target, Object... args)
                throws ReflectiveOperationException {
            if (target == null || staticOnly) return false;
            Method method = resolve(target.getClass(), args);
            if (method == null) return false;
            method.invoke(target, args);
            return true;
        }

        public Object invoke(Object target, Object... args) throws ReflectiveOperationException {
            if (target == null || staticOnly) return null;
            Method method = resolve(target.getClass(), args);
            return method == null ? null : method.invoke(target, args);
        }

        public boolean invokeStaticIfPresent(Class<?> type, Object... args)
                throws ReflectiveOperationException {
            if (type == null || !staticOnly) return false;
            Method method = resolve(type, args);
            if (method == null) return false;
            method.invoke(null, args);
            return true;
        }

        public Object invokeStatic(Class<?> type, Object... args) throws ReflectiveOperationException {
            if (type == null || !staticOnly) return null;
            Method method = resolve(type, args);
            return method == null ? null : method.invoke(null, args);
        }

        private Method resolve(Class<?> type, Object[] args) {
            Object[] actualArgs = args == null ? new Object[0] : args;
            Resolution[] current = resolutions;
            for (Resolution resolution : current) {
                if (resolution.matches(type, actualArgs)) return resolution.method;
            }
            synchronized (this) {
                current = resolutions;
                for (Resolution resolution : current) {
                    if (resolution.matches(type, actualArgs)) return resolution.method;
                }
                Method method = findCompatibleMethod(type, staticOnly, names, actualArgs);
                Resolution learned = new Resolution(type, actualArgs, method);
                int retained = Math.min(current.length, MAX_RESOLUTIONS - 1);
                Resolution[] updated = new Resolution[retained + 1];
                updated[0] = learned;
                if (retained > 0) System.arraycopy(current, 0, updated, 1, retained);
                resolutions = updated;
                return method;
            }
        }
    }

    private static final class Resolution {
        final Class<?> targetType;
        final Class<?>[] argumentTypes;
        final Method method;

        Resolution(Class<?> targetType, Object[] args, Method method) {
            this.targetType = targetType;
            this.argumentTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                argumentTypes[i] = args[i] == null ? NullArgument.class : args[i].getClass();
            }
            this.method = method;
        }

        boolean matches(Class<?> type, Object[] args) {
            if (targetType != type || argumentTypes.length != args.length) return false;
            for (int i = 0; i < args.length; i++) {
                Class<?> actual = args[i] == null ? NullArgument.class : args[i].getClass();
                if (argumentTypes[i] != actual) return false;
            }
            return true;
        }
    }

    private static List<String> names(String[] values) {
        if (values == null || values.length == 0) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(values.clone())));
    }

    private static final class FieldKey {
        final Class<?> type;
        final boolean deep;
        final boolean staticOnly;
        final List<String> names;

        FieldKey(Class<?> type, boolean deep, boolean staticOnly, String[] names) {
            this.type = type;
            this.deep = deep;
            this.staticOnly = staticOnly;
            this.names = RuntimeReflectionCache.names(names);
        }

        @Override public boolean equals(Object value) {
            if (!(value instanceof FieldKey)) return false;
            FieldKey other = (FieldKey) value;
            return type.equals(other.type) && deep == other.deep && staticOnly == other.staticOnly
                    && names.equals(other.names);
        }

        @Override public int hashCode() {
            return (((type.hashCode() * 31) + names.hashCode()) * 31 + (deep ? 1 : 0)) * 31
                    + (staticOnly ? 1 : 0);
        }
    }

    private static final class MethodKey {
        final Class<?> type;
        final int staticMode;
        final int mode;
        final int arity;
        final List<String> names;
        final List<Class<?>> argumentTypes;

        private MethodKey(Class<?> type, int staticMode, int mode, int arity,
                          String[] names, List<Class<?>> argumentTypes) {
            this.type = type;
            this.staticMode = staticMode;
            this.mode = mode;
            this.arity = arity;
            this.names = RuntimeReflectionCache.names(names);
            this.argumentTypes = argumentTypes;
        }

        static MethodKey compatible(Class<?> type, int staticMode, String[] names, Object[] args) {
            METHOD_KEY_BUILDS.incrementAndGet();
            List<Class<?>> types = new ArrayList<Class<?>>(args == null ? 0 : args.length);
            if (args != null) {
                for (Object arg : args) types.add(arg == null ? NullArgument.class : arg.getClass());
            }
            return new MethodKey(type, staticMode, COMPATIBLE, types.size(), names,
                    Collections.unmodifiableList(types));
        }

        static MethodKey arity(Class<?> type, int staticMode, String[] names, int arity) {
            return new MethodKey(type, staticMode, ARITY, arity, names, Collections.emptyList());
        }

        static MethodKey deepNoArg(Class<?> type, String[] names) {
            return new MethodKey(type, INSTANCE, DEEP_NO_ARG, 0, names, Collections.emptyList());
        }

        @Override public boolean equals(Object value) {
            if (!(value instanceof MethodKey)) return false;
            MethodKey other = (MethodKey) value;
            return type.equals(other.type) && staticMode == other.staticMode && mode == other.mode
                    && arity == other.arity && names.equals(other.names)
                    && argumentTypes.equals(other.argumentTypes);
        }

        @Override public int hashCode() {
            int result = type.hashCode();
            result = result * 31 + staticMode;
            result = result * 31 + mode;
            result = result * 31 + arity;
            result = result * 31 + names.hashCode();
            return result * 31 + argumentTypes.hashCode();
        }
    }

    private static final class NullArgument {
    }
}
