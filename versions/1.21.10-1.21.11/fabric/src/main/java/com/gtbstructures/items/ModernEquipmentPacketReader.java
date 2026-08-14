package com.gtbstructures.items;

import com.gtbstructures.util.RuntimeReflectionCache;

import java.lang.reflect.Method;
import java.util.Locale;

/** Reads the stable fields used by modern entity-equipment packets. */
public final class ModernEquipmentPacketReader {
    private ModernEquipmentPacketReader() {
    }

    public static MainHandUpdate readMainHand(Object packet) throws ReflectiveOperationException {
        if (packet == null) return null;
        Object entity = invokeAny(packet, "getEntity", "method_11820", "b");
        Object slots = invokeAny(packet, "getSlots", "method_30145", "e");
        if (!(entity instanceof Number) || !(slots instanceof Iterable<?>)) return null;

        for (Object pair : (Iterable<?>) slots) {
            if (pair == null) continue;
            Object slot = invokeAny(pair, "getFirst");
            if (!isMainHand(slot)) continue;
            return new MainHandUpdate(((Number) entity).intValue(), invokeAny(pair, "getSecond"));
        }
        return null;
    }

    private static boolean isMainHand(Object slot) throws ReflectiveOperationException {
        if (slot == null) return false;
        Object name = invokeAnyOrNull(slot, "getName", "method_5923", "e");
        String value = name == null || name == MissingValue.INSTANCE
                ? String.valueOf(slot) : String.valueOf(name);
        value = value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return "mainhand".equals(value);
    }

    private static Object invokeAny(Object target, String... names) throws ReflectiveOperationException {
        Object value = invokeAnyOrNull(target, names);
        if (value != MissingValue.INSTANCE) return value;
        throw new NoSuchMethodException(target.getClass().getName());
    }

    private static Object invokeAnyOrNull(Object target, String... names) throws ReflectiveOperationException {
        if (target == null) return MissingValue.INSTANCE;
        Method method = RuntimeReflectionCache.findMethodByArity(target.getClass(), false, names, 0);
        return method == null ? MissingValue.INSTANCE : method.invoke(target);
    }

    private enum MissingValue {
        INSTANCE
    }

    public static final class MainHandUpdate {
        public final int entityId;
        public final Object stack;

        private MainHandUpdate(int entityId, Object stack) {
            this.entityId = entityId;
            this.stack = stack;
        }
    }
}
