package com.gtbstructures.font;

import com.gtbstructures.GtbStructuresSettings;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Reproduces the half-pixel style offsets used by Minecraft 1.8 Unicode glyphs. */
public final class ClassicFontGlyphMetrics {
    // The Unicode bitmap cells are rendered at the provider's configured 8px height.
    private static final float CLASSIC_UNICODE_4X_SCALE = 8.0F / 64.0F;
    private static final float CLASSIC_UNICODE_2X_SCALE = 8.0F / 32.0F;
    private static final float HALF_PIXEL_OFFSET = 0.5F;
    private static final byte ORIGINAL = 0;
    private static final byte BASE_ADVANCE = 1;
    private static final byte STYLE_OFFSET = 2;
    private static final byte BOLD_ADVANCE = 3;
    private static final AtomicInteger PLAN_BUILD_COUNT = new AtomicInteger();
    private static final ClassValue<GlyphMethodPlan> METHOD_PLANS = new ClassValue<>() {
        @Override
        protected GlyphMethodPlan computeValue(Class<?> type) {
            PLAN_BUILD_COUNT.incrementAndGet();
            return inspectPlan(type);
        }
    };

    private ClassicFontGlyphMetrics() {
    }

    public static boolean shouldAdjust(float scale) {
        return "classic".equals(ClassicFontResourcePack.normalizeMode(GtbStructuresSettings.INSTANCE.fontMode))
                && (Math.abs(scale - CLASSIC_UNICODE_4X_SCALE) < 0.001F
                || Math.abs(scale - CLASSIC_UNICODE_2X_SCALE) < 0.001F);
    }

    public static Object withHalfPixelStyleOffsets(Object original) {
        if (original == null || Proxy.isProxyClass(original.getClass())) return original;
        GlyphMethodPlan plan = METHOD_PLANS.get(original.getClass());
        if (plan.interfaces.length == 0) return original;
        Float baseAdvance = captureBaseAdvance(original, plan.baseAdvanceMethod);
        return Proxy.newProxyInstance(plan.classLoader, plan.interfaces,
                new GlyphInvocationHandler(original, plan.roles, baseAdvance));
    }

    private static GlyphMethodPlan inspectPlan(Class<?> type) {
        Class<?>[] interfaces = type.getInterfaces();
        if (interfaces.length == 0) return GlyphMethodPlan.EMPTY;
        Method baseAdvanceMethod = findBaseAdvanceMethod(type, interfaces);
        Map<Method, Byte> roles = new HashMap<>();
        for (Class<?> interfaceType : interfaces) {
            for (Method method : interfaceType.getMethods()) {
                byte role = methodRole(method, baseAdvanceMethod);
                if (role != ORIGINAL) roles.put(method, role);
            }
        }
        return new GlyphMethodPlan(interfaces[0].getClassLoader(), interfaces,
                baseAdvanceMethod, roles);
    }

    private static Method findBaseAdvanceMethod(Class<?> type, Class<?>[] interfaces) {
        for (Class<?> interfaceType : interfaces) {
            Method method = findBaseAdvanceMethod(interfaceType);
            if (method != null) return method;
        }
        return findBaseAdvanceMethod(type);
    }

    private static Method findBaseAdvanceMethod(Class<?> type) {
        Method named = findBaseAdvanceMethod(type, "getAdvance");
        if (named != null) return named;
        named = findBaseAdvanceMethod(type, "method_16797");
        if (named != null) return named;
        for (Method method : type.getMethods()) {
            if (isBaseAdvanceCandidate(method)) return method;
        }
        return null;
    }

    private static Method findBaseAdvanceMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (name.equals(method.getName()) && isBaseAdvanceCandidate(method)) return method;
        }
        return null;
    }

    private static boolean isBaseAdvanceCandidate(Method method) {
        return method.getParameterCount() == 0 && method.getReturnType() == Float.TYPE
                && !isStyleOffset(method);
    }

    private static Float captureBaseAdvance(Object original, Method method) {
        if (method == null) return null;
        try {
            return ((Number) method.invoke(original)).floatValue();
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return null;
        }
    }

    private static byte methodRole(Method method, Method baseAdvanceMethod) {
        if (baseAdvanceMethod != null && method.getParameterCount() == 0
                && method.getReturnType() == Float.TYPE
                && baseAdvanceMethod.getName().equals(method.getName())) {
            return BASE_ADVANCE;
        }
        if (isStyleOffset(method)) return STYLE_OFFSET;
        if (isBoldAdvance(method)) return BOLD_ADVANCE;
        return ORIGINAL;
    }

    private static Object invokeOriginal(Object original, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(original, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static boolean isBoldAdvance(Method method) {
        if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != Boolean.TYPE
                || method.getReturnType() != Float.TYPE) {
            return false;
        }
        String name = method.getName();
        return "a".equals(name) || "method_16798".equals(name) || "getAdvance".equals(name);
    }

    private static boolean isStyleOffset(Method method) {
        if (method.getParameterCount() != 0 || method.getReturnType() != Float.TYPE) return false;
        String name = method.getName();
        return "a".equals(name) || "b".equals(name)
                || "method_16799".equals(name) || "method_16800".equals(name)
                || "getBoldOffset".equals(name) || "getShadowOffset".equals(name);
    }

    static int planBuildCountForTest() {
        return PLAN_BUILD_COUNT.get();
    }

    private static final class GlyphMethodPlan {
        private static final GlyphMethodPlan EMPTY = new GlyphMethodPlan(
                null, new Class<?>[0], null, new HashMap<>());

        private final ClassLoader classLoader;
        private final Class<?>[] interfaces;
        private final Method baseAdvanceMethod;
        private final Map<Method, Byte> roles;

        private GlyphMethodPlan(ClassLoader classLoader, Class<?>[] interfaces,
                                Method baseAdvanceMethod, Map<Method, Byte> roles) {
            this.classLoader = classLoader;
            this.interfaces = interfaces;
            this.baseAdvanceMethod = baseAdvanceMethod;
            this.roles = roles;
        }
    }

    private static final class GlyphInvocationHandler implements java.lang.reflect.InvocationHandler {
        private final Object original;
        private final Map<Method, Byte> roles;
        private final Float baseAdvance;

        private GlyphInvocationHandler(Object original, Map<Method, Byte> roles, Float baseAdvance) {
            this.original = original;
            this.roles = roles;
            this.baseAdvance = baseAdvance;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Byte roleValue = roles.get(method);
            byte role = roleValue == null ? ORIGINAL : roleValue;
            if (role == BASE_ADVANCE && baseAdvance != null) return baseAdvance;
            if (role == STYLE_OFFSET) return HALF_PIXEL_OFFSET;
            if (role == BOLD_ADVANCE && baseAdvance != null) {
                boolean bold = args != null && Boolean.TRUE.equals(args[0]);
                return baseAdvance + (bold ? HALF_PIXEL_OFFSET : 0.0F);
            }
            return invokeOriginal(original, method, args);
        }
    }
}
