package com.gtbstructures.overlay;

import java.lang.reflect.Method;

/** Reflective pose-stack adapter shared by Fabric HUD renderers across mapped API variants. */
public final class FabricHudPose {
    private FabricHudPose() {
    }

    public static Scope push(Object graphics, int x, int y, float scale)
            throws ReflectiveOperationException {
        Method poseMethod = noArgMethod(graphics.getClass(),
                "pose", "getPose", "getPoseStack", "getMatrices", "method_51448");
        if (poseMethod == null) throw new NoSuchMethodException("No HUD pose stack");
        Object pose = poseMethod.invoke(graphics);
        if (pose == null) throw new NoSuchMethodException("No HUD pose stack instance");
        Method push = noArgMethod(pose.getClass(), "pushPose", "pushMatrix", "push");
        Method pop = noArgMethod(pose.getClass(), "popPose", "popMatrix", "pop");
        Method translate = transformMethod(pose.getClass(), "translate");
        Method scaleMethod = transformMethod(pose.getClass(), "scale");
        if (push == null || pop == null || translate == null || scaleMethod == null) {
            throw new NoSuchMethodException("Incomplete HUD pose stack");
        }
        boolean pushed = false;
        try {
            push.invoke(pose);
            pushed = true;
            invokeTransform(translate, pose, x, y, 0.0F);
            invokeTransform(scaleMethod, pose, scale, scale, 1.0F);
            return new Scope(pose, pop);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (pushed) {
                try {
                    pop.invoke(pose);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // The outer renderer still reports the original transform failure.
                }
            }
            throw e;
        }
    }

    private static Method noArgMethod(Class<?> type, String... names) {
        for (String name : names) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // Try the next supported pose-stack name.
            }
        }
        return null;
    }

    private static Method transformMethod(Class<?> type, String name) {
        for (int parameterCount = 2; parameterCount <= 3; parameterCount++) {
            for (Method method : type.getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != parameterCount) continue;
                boolean numeric = true;
                for (Class<?> parameterType : method.getParameterTypes()) {
                    numeric &= isNumericType(parameterType);
                }
                if (numeric) return method;
            }
        }
        return null;
    }

    private static boolean isNumericType(Class<?> type) {
        return type == Float.TYPE || type == Float.class
                || type == Double.TYPE || type == Double.class
                || type == Integer.TYPE || type == Integer.class
                || type == Long.TYPE || type == Long.class;
    }

    private static void invokeTransform(Method method, Object pose, float x, float y, float z)
            throws ReflectiveOperationException {
        Class<?>[] types = method.getParameterTypes();
        Object[] values = method.getParameterCount() == 2
                ? new Object[] {number(types[0], x), number(types[1], y)}
                : new Object[] {number(types[0], x), number(types[1], y), number(types[2], z)};
        method.invoke(pose, values);
    }

    private static Object number(Class<?> type, float value) {
        if (type == Double.TYPE || type == Double.class) return Double.valueOf(value);
        if (type == Long.TYPE || type == Long.class) return Long.valueOf((long) value);
        if (type == Integer.TYPE || type == Integer.class) return Integer.valueOf((int) value);
        return Float.valueOf(value);
    }

    public static final class Scope {
        private final Object pose;
        private final Method pop;

        private Scope(Object pose, Method pop) {
            this.pose = pose;
            this.pop = pop;
        }

        public void pop() throws ReflectiveOperationException {
            pop.invoke(pose);
        }
    }
}
