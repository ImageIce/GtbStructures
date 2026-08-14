package com.gtbstructures.overlay;

import com.gtbstructures.GtbStructuresForge;
import net.minecraftforge.client.gui.overlay.ForgeLayer;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Registers Forge HUD layers without hard-linking Minecraft's renamed identifier class.
 *
 * <p>Minecraft 1.21.10 exposes {@code net.minecraft.resources.ResourceLocation}, while
 * 1.21.11 renamed that class to {@code net.minecraft.resources.Identifier}. Forge's
 * {@code ForgeLayeredDraw} signatures changed with it, so identifier fields, factories,
 * and relative-layer methods must all be resolved from the active runtime.</p>
 */
public final class ForgeHudLayerBridge {
    private static final Method LAYER_RENDER_METHOD = findLayerRenderMethod();
    private static final AtomicInteger POSE_PLAN_RESOLUTIONS = new AtomicInteger();
    private static final ClassValue<PosePlan> POSE_PLANS = new ClassValue<>() {
        @Override
        protected PosePlan computeValue(Class<?> type) {
            POSE_PLAN_RESOLUTIONS.incrementAndGet();
            return PosePlan.resolve(type);
        }
    };

    private ForgeHudLayerBridge() {
    }

    public static void addAboveChat(Object layeredDraw, String path, ForgeLayer layer) {
        addRelative(layeredDraw, "addAbove", "CHAT_OVERLAY", path, layer);
    }

    public static void addBelowChat(Object layeredDraw, String path, ForgeLayer layer) {
        addRelative(layeredDraw, "addBelow", "CHAT_OVERLAY", path, layer);
    }

    public static void addBelowHotbarMessage(Object layeredDraw, String path, ForgeLayer layer) {
        addRelative(layeredDraw, "addBelow", "HOTBAR_MESSAGE", path, layer);
    }

    public static void guardVanillaScoreboard(Object layeredDraw, BooleanSupplier suppressor) {
        if (layeredDraw == null) throw new IllegalArgumentException("layeredDraw");
        try {
            Class<?> drawClass = layeredDraw.getClass();
            Object stackId = drawClass.getField("POST_SLEEP_STACK").get(null);
            Object scoreboardId = drawClass.getField("SCOREBOARD").get(null);
            BooleanSupplier visibility = () -> suppressor == null || !suppressor.getAsBoolean();
            Method conditionMethod = findConditionMethod(drawClass, stackId, scoreboardId, visibility);
            conditionMethod.invoke(layeredDraw, stackId, scoreboardId, visibility);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Unable to guard the Forge vanilla scoreboard layer", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to guard the Forge vanilla scoreboard layer", e);
        }
    }

    /** Replays the vanilla scoreboard layer with GTBS's saved vertical offset. */
    public static void relocateVanillaScoreboard(Object layeredDraw, BooleanSupplier suppressor,
                                                IntSupplier heightOffset) {
        if (layeredDraw == null) throw new IllegalArgumentException("layeredDraw");
        try {
            Class<?> drawClass = layeredDraw.getClass();
            Object stackId = drawClass.getField("POST_SLEEP_STACK").get(null);
            Object scoreboardId = drawClass.getField("SCOREBOARD").get(null);
            Object child = drawClass.getMethod("getChild", stackId.getClass()).invoke(layeredDraw, stackId);
            Object original = drawClass.getMethod("getLayer", scoreboardId.getClass()).invoke(child, scoreboardId);
            if (!(original instanceof ForgeLayer originalLayer)) {
                throw new IllegalStateException("Forge scoreboard layer is not a ForgeLayer");
            }
            BooleanSupplier hiddenOriginal = () -> false;
            findConditionMethod(drawClass, stackId, scoreboardId, hiddenOriginal)
                    .invoke(layeredDraw, stackId, scoreboardId, hiddenOriginal);
            ForgeLayer replacement = (graphics, deltaTracker) -> {
                if (suppressor != null && suppressor.getAsBoolean()) return;
                int offset = heightOffset == null ? 0 : heightOffset.getAsInt();
                if (offset == 0) {
                    invokeLayer(originalLayer, graphics, deltaTracker);
                    return;
                }
                Object pose = graphics.pose();
                withPoseOffset(pose, -offset,
                        () -> invokeLayer(originalLayer, graphics, deltaTracker));
            };
            addRelative(layeredDraw, "addAbove", "SCOREBOARD", "relocated_vanilla_scoreboard", replacement);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Unable to relocate the Forge vanilla scoreboard layer", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to relocate the Forge vanilla scoreboard layer", e);
        }
    }

    private static void invokeLayer(ForgeLayer layer, Object graphics, Object deltaTracker) {
        if (LAYER_RENDER_METHOD == null) {
            throw new IllegalStateException("No supported render method on ForgeLayer");
        }
        try {
            LAYER_RENDER_METHOD.invoke(layer, graphics, deltaTracker);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Unable to replay the Forge vanilla scoreboard layer", cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to replay the Forge vanilla scoreboard layer", e);
        }
    }

    private static Method findLayerRenderMethod() {
        for (Method method : ForgeLayer.class.getMethods()) {
            if (("render".equals(method.getName()) || "extract".equals(method.getName()))
                    && method.getParameterCount() == 2) {
                return method;
            }
        }
        return null;
    }

    private static void withPoseOffset(Object pose, int verticalOffset, Runnable action) {
        if (pose == null) throw new IllegalStateException("GUI pose stack is unavailable");
        PosePlan plan = POSE_PLANS.get(pose.getClass());
        if (!plan.supported()) {
            throw new IllegalStateException("No supported pose methods on " + pose.getClass().getName());
        }
        try {
            plan.push.invoke(pose);
            try {
                plan.translate(pose, verticalOffset);
                action.run();
            } finally {
                plan.pop.invoke(pose);
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Unable to translate the vanilla scoreboard layer", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to translate the vanilla scoreboard layer", e);
        }
    }

    static int posePlanResolutionsForTest() {
        return POSE_PLAN_RESOLUTIONS.get();
    }

    static void withPoseOffsetForTest(Object pose, int verticalOffset, Runnable action) {
        withPoseOffset(pose, verticalOffset, action);
    }

    private static final class PosePlan {
        private final Method push;
        private final Method pop;
        private final Method translate;
        private final boolean twoDimensional;

        private PosePlan(Method push, Method pop, Method translate, boolean twoDimensional) {
            this.push = push;
            this.pop = pop;
            this.translate = translate;
            this.twoDimensional = twoDimensional;
        }

        private static PosePlan resolve(Class<?> type) {
            Method push = null;
            Method pop = null;
            Method translate = null;
            boolean twoDimensional = false;
            for (Method method : type.getMethods()) {
                String name = method.getName();
                if (method.getParameterCount() == 0) {
                    if (push == null && ("pushPose".equals(name) || "pushMatrix".equals(name))) {
                        push = method;
                    } else if (pop == null && ("popPose".equals(name) || "popMatrix".equals(name))) {
                        pop = method;
                    }
                } else if (translate == null && "translate".equals(name)) {
                    int count = method.getParameterCount();
                    if (count == 2 || count == 3) {
                        translate = method;
                        twoDimensional = count == 2;
                    }
                }
            }
            return new PosePlan(push, pop, translate, twoDimensional);
        }

        private boolean supported() {
            return push != null && pop != null && translate != null;
        }

        private void translate(Object pose, int verticalOffset) throws ReflectiveOperationException {
            if (twoDimensional) {
                translate.invoke(pose, Float.valueOf(0.0F), Float.valueOf(verticalOffset));
            } else {
                translate.invoke(pose, Double.valueOf(0.0D),
                        Double.valueOf(verticalOffset), Double.valueOf(0.0D));
            }
        }
    }

    private static void addRelative(Object layeredDraw, String methodName, String anchorFieldName,
                                    String path, ForgeLayer layer) {
        if (layeredDraw == null) throw new IllegalArgumentException("layeredDraw");
        try {
            Class<?> drawClass = layeredDraw.getClass();
            Field stackField = drawClass.getField("POST_SLEEP_STACK");
            Field anchorField = drawClass.getField(anchorFieldName);
            Object stackId = stackField.get(null);
            Object anchorId = anchorField.get(null);
            Object layerId = createIdentifier(anchorId.getClass(), GtbStructuresForge.MOD_ID, path);
            Method addMethod = findRelativeMethod(
                    drawClass, methodName, stackId, anchorId, layerId, layer);
            // Both supported Forge generations keep these vanilla anchors in POST_SLEEP_STACK.
            // The four-argument overload takes stack, new layer, existing anchor, callback.
            addMethod.invoke(layeredDraw, stackId, layerId, anchorId, layer);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Unable to register Forge HUD layer "
                    + GtbStructuresForge.MOD_ID + ":" + path,
                    cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to register Forge HUD layer "
                    + GtbStructuresForge.MOD_ID + ":" + path,
                    e);
        }
    }

    private static Object createIdentifier(Class<?> identifierClass, String namespace, String path)
            throws ReflectiveOperationException {
        for (String factoryName : new String[] {"fromNamespaceAndPath", "tryBuild"}) {
            try {
                Method factory = identifierClass.getMethod(factoryName, String.class, String.class);
                if (Modifier.isStatic(factory.getModifiers())) {
                    Object identifier = factory.invoke(null, namespace, path);
                    if (identifier != null) return identifier;
                }
            } catch (NoSuchMethodException ignored) {
                // Try the single-string factories used by other mapped runtimes.
            }
        }
        for (String factoryName : new String[] {"parse", "tryParse"}) {
            try {
                Method factory = identifierClass.getMethod(factoryName, String.class);
                if (Modifier.isStatic(factory.getModifiers())) {
                    Object identifier = factory.invoke(null, namespace + ":" + path);
                    if (identifier != null) return identifier;
                }
            } catch (NoSuchMethodException ignored) {
                // Try the next identifier factory.
            }
        }
        throw new NoSuchMethodException("No supported identifier factory on " + identifierClass.getName());
    }

    private static Method findRelativeMethod(Class<?> drawClass, String methodName,
                                             Object stackId, Object anchorId,
                                             Object layerId, ForgeLayer layer)
            throws NoSuchMethodException {
        for (Method method : drawClass.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getName().equals(methodName)
                    && parameters.length == 4
                    && parameters[0].isInstance(stackId)
                    && parameters[1].isInstance(layerId)
                    && parameters[2].isInstance(anchorId)
                    && parameters[3].isInstance(layer)) {
                return method;
            }
        }
        throw new NoSuchMethodException(drawClass.getName() + "." + methodName
                + "(stack identifier, layer identifier, anchor identifier, ForgeLayer)");
    }

    private static Method findConditionMethod(Class<?> drawClass, Object stackId,
                                              Object layerId, BooleanSupplier condition)
            throws NoSuchMethodException {
        for (Method method : drawClass.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getName().equals("addConditionTo")
                    && parameters.length == 3
                    && parameters[0].isInstance(stackId)
                    && parameters[1].isInstance(layerId)
                    && parameters[2].isInstance(condition)) {
                return method;
            }
        }
        throw new NoSuchMethodException(drawClass.getName()
                + ".addConditionTo(stack identifier, layer identifier, BooleanSupplier)");
    }
}
