package com.gtbstructures.scoreboard;

import com.gtbstructures.GtbStructuresFabric;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Uses the same queued component tooltip as Minecraft's chat SHOW_TEXT handler. */
final class NativePreviewTooltip {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativePreviewTooltip.class);
    private static boolean warningLogged;
    private static final ClassValue<Plan> PLANS = new ClassValue<>() {
        @Override protected Plan computeValue(Class<?> type) { return new Plan(type); }
    };

    private NativePreviewTooltip() { }

    static boolean draw(Object graphics, Object font, String text, int x, int y) {
        try {
            Plan plan = PLANS.get(graphics.getClass());
            if (plan.method == null) throw new NoSuchMethodException("Native component tooltip");
            plan.method.invoke(graphics, font, plan.component(text), x, y);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (!warningLogged) {
                warningLogged = true;
                LOGGER.warn("Unable to queue native GtbStructures preview tooltip", failure);
            }
            return false;
        }
    }

    static Method tooltipMethod(Class<?> graphics) {
        Method found = null;
        for (Method method : graphics.getMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                    || types.length != 4 || types[2] != int.class || types[3] != int.class) continue;
            String font = types[0].getName(), text = types[1].getName();
            if (!(font.equals("net.minecraft.client.gui.Font")
                    || font.equals("net.minecraft.client.font.TextRenderer") || font.equals("net.minecraft.class_327"))) continue;
            if (!(text.equals("net.minecraft.network.chat.Component")
                    || text.equals("net.minecraft.text.Text") || text.equals("net.minecraft.class_2561"))) continue;
            // This unique signature survives Yarn/intermediary and the 26.x graphics rename.
            if (found != null && !found.equals(method)) return null;
            found = method;
        }
        return found;
    }

    private static final class Plan {
        final Method method;
        private String lastText;
        private Object lastComponent;

        Plan(Class<?> graphics) { method = tooltipMethod(graphics); }

        Object component(String text) throws ReflectiveOperationException {
            if (lastComponent == null || !text.equals(lastText)) {
                lastComponent = GtbStructuresFabric.legacyFormattedText(method.getParameterTypes()[1], "\u00a7f" + text);
                lastText = text;
            }
            return lastComponent;
        }
    }
}
