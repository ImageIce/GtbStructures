package com.gtbstructures.overlay;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Draws GUI texture regions without linking either modern identifier class name. */
public final class ForgeGuiTextureBridge {
    private static final Object MISS = new Object();
    private static final String[] DRAW_METHODS = {
            "blit", "drawTexture", "method_25293", "method_25302", "method_25291", "method_25290"
    };
    private static final DrawLayout[] DIRECT_DRAW_LAYOUTS = {
            new DrawLayout(false, true, true, true),
            new DrawLayout(false, true, true, false),
            new DrawLayout(false, true, false, true),
            new DrawLayout(false, true, false, false),
            new DrawLayout(false, false, false, false)
    };
    private static final DrawLayout[] PIPELINE_DRAW_LAYOUTS = {
            new DrawLayout(true, true, true, true),
            new DrawLayout(true, true, true, false),
            new DrawLayout(true, true, false, true),
            new DrawLayout(true, true, false, false)
    };
    private static final Map<String, Object> CLASSES = new ConcurrentHashMap<>();
    private static final Map<String, Object> IDENTIFIERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<Class<?>, Object>> PIPELINES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, GraphicsDrawPlans> DRAW_PLANS = new ConcurrentHashMap<>();
    private static final AtomicLong CLASS_RESOLUTIONS = new AtomicLong();
    private static final AtomicLong IDENTIFIER_RESOLUTIONS = new AtomicLong();
    private static final AtomicLong PIPELINE_RESOLUTIONS = new AtomicLong();
    private static final AtomicLong DRAW_METHOD_RESOLUTIONS = new AtomicLong();
    private static final AtomicLong DRAW_PLAN_CACHE_NODE_BUILDS = new AtomicLong();
    private static final AtomicLong DRAW_PLAN_SLOT_BUILDS = new AtomicLong();
    private static final AtomicLong DRAW_ARGUMENT_BUFFER_BUILDS = new AtomicLong();
    private static volatile Object shaderColorMethod;

    private ForgeGuiTextureBridge() {
    }

    public static boolean drawRegion(Object graphics, String textureName,
                                     int x, int y, int width, int height,
                                     int u, int v, int sourceWidth, int sourceHeight,
                                     int textureWidth, int textureHeight, int tintRgb) {
        if (graphics == null || textureName == null || textureName.isEmpty()
                || width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0
                || textureWidth <= 0 || textureHeight <= 0) {
            return false;
        }
        try {
            Object texture = identifier(textureName);
            if (texture == null) return false;
            Object pipeline = texturePipeline(graphics, texture);
            int color = 0xFF000000 | (tintRgb & 0x00FFFFFF);
            if (pipeline != null && tryDrawTexture(graphics, pipeline, texture,
                    x, y, width, height, u, v, sourceWidth, sourceHeight,
                    textureWidth, textureHeight, color)) {
                return true;
            }
            setShaderColor(tintRgb);
            try {
                return tryDrawTexture(graphics, null, texture,
                        x, y, width, height, u, v, sourceWidth, sourceHeight,
                        textureWidth, textureHeight, color);
            } finally {
                setShaderColor(0xFFFFFF);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean tryDrawTexture(Object graphics, Object pipeline, Object texture,
                                          int x, int y, int width, int height,
                                          int u, int v, int sourceWidth, int sourceHeight,
                                          int textureWidth, int textureHeight, int color)
            throws ReflectiveOperationException {
        DrawPlan plan = drawPlan(graphics.getClass(),
                pipeline == null ? null : pipeline.getClass(), texture.getClass());
        if (plan == null) return false;
        plan.invoke(graphics, pipeline, texture, x, y, width, height, u, v,
                sourceWidth, sourceHeight, textureWidth, textureHeight, color);
        return true;
    }

    private static Object texturePipeline(Object graphics, Object texture)
            throws ReflectiveOperationException {
        Class<?> graphicsClass = graphics.getClass();
        Class<?> textureClass = texture.getClass();
        Map<Class<?>, Object> byTexture = PIPELINES.get(graphicsClass);
        if (byTexture == null) {
            Map<Class<?>, Object> created = new ConcurrentHashMap<>();
            Map<Class<?>, Object> existing = PIPELINES.putIfAbsent(graphicsClass, created);
            byTexture = existing == null ? created : existing;
        }
        Object cached = byTexture.get(textureClass);
        if (cached == null) {
            Object resolved = resolveTexturePipeline(graphicsClass, textureClass);
            Object existing = byTexture.putIfAbsent(textureClass, resolved);
            cached = existing == null ? resolved : existing;
        }
        return cached == MISS ? null : cached;
    }

    private static Object resolveTexturePipeline(Class<?> graphicsClass, Class<?> textureClass) {
        PIPELINE_RESOLUTIONS.incrementAndGet();
        Class<?> pipelineType = null;
        for (Method method : graphicsClass.getMethods()) {
            if (!matches(method.getName(), DRAW_METHODS) || method.getParameterCount() < 2) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters[1].isAssignableFrom(textureClass)
                    && !parameters[0].isAssignableFrom(textureClass)) {
                pipelineType = parameters[0];
                break;
            }
        }
        if (pipelineType == null) return MISS;
        String[] holders = {
                "net.minecraft.client.renderer.RenderPipelines",
                "net.minecraft.client.render.RenderPipelines",
                "net.minecraft.client.gl.RenderPipelines",
                "net.minecraft.client.gui.render.RenderPipelines",
                "net.minecraft.class_10799"
        };
        for (String holderName : holders) {
            try {
                Class<?> holder = cachedClass(holderName);
                if (holder == null) continue;
                Object pipeline = staticAssignableField(holder, pipelineType, "GUI_TEXTURED");
                if (pipeline == null) {
                    pipeline = staticAssignableField(holder, pipelineType, "field_56883");
                }
                if (pipeline == null) pipeline = staticAssignableField(holder, pipelineType, "");
                if (pipeline != null) return pipeline;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next mappings family or holder field.
            }
        }
        return MISS;
    }

    private static Object identifier(String registry) throws ReflectiveOperationException {
        String value = registry.indexOf(':') >= 0 ? registry : "minecraft:" + registry;
        Object cached = IDENTIFIERS.get(value);
        if (cached == null) {
            Object resolved = resolveIdentifier(value);
            Object existing = IDENTIFIERS.putIfAbsent(value, resolved);
            cached = existing == null ? resolved : existing;
        }
        return cached == MISS ? null : cached;
    }

    private static Object resolveIdentifier(String value) {
        IDENTIFIER_RESOLUTIONS.incrementAndGet();
        try {
        Class<?> idClass = classAny(
                "net.minecraft.resources.ResourceLocation",
                "net.minecraft.resources.Identifier",
                "net.minecraft.util.Identifier",
                "net.minecraft.class_2960", "amj", "amo");
        int colon = value.indexOf(':');
        if (colon > 0 && colon + 1 < value.length()) {
            String namespace = value.substring(0, colon);
            String path = value.substring(colon + 1);
            for (Method method : idClass.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())
                        || !idClass.isAssignableFrom(method.getReturnType())
                        || method.getParameterCount() != 2
                        || method.getParameterTypes()[0] != String.class
                        || method.getParameterTypes()[1] != String.class) {
                    continue;
                }
                try {
                    Object out = method.invoke(null, namespace, path);
                    if (out != null) return out;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Try the next namespace/path factory.
                }
            }
            try {
                Constructor<?> constructor = idClass.getDeclaredConstructor(String.class, String.class);
                constructor.setAccessible(true);
                return constructor.newInstance(namespace, path);
            } catch (NoSuchMethodException ignored) {
                // Try single-string factories below.
            }
        }
        for (Method method : idClass.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())
                    || !idClass.isAssignableFrom(method.getReturnType())
                    || method.getParameterCount() != 1
                    || method.getParameterTypes()[0] != String.class) {
                continue;
            }
            try {
                Object out = method.invoke(null, value);
                if (out != null) return out;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next single-string factory.
            }
        }
        try {
            Constructor<?> constructor = idClass.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(value);
        } catch (NoSuchMethodException ignored) {
            return MISS;
        }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return MISS;
        }
    }

    private static Class<?> classAny(String... names) throws ClassNotFoundException {
        for (String name : names) {
            Class<?> type = cachedClass(name);
            if (type != null) return type;
        }
        throw new ClassNotFoundException(String.join(", ", names));
    }

    private static Class<?> cachedClass(String name) {
        Object cached = CLASSES.computeIfAbsent(name, candidate -> {
            CLASS_RESOLUTIONS.incrementAndGet();
            try {
                return Class.forName(candidate);
            } catch (ClassNotFoundException ignored) {
                return MISS;
            }
        });
        return cached instanceof Class ? (Class<?>) cached : null;
    }

    private static Object staticAssignableField(Class<?> type, Class<?> fieldType, String preferred)
            throws ReflectiveOperationException {
        Field[] fields = type.getDeclaredFields();
        for (Field field : fields) {
            if (!Modifier.isStatic(field.getModifiers())
                    || !fieldType.isAssignableFrom(field.getType())
                    || (!preferred.isEmpty() && !preferred.equals(field.getName()))) {
                continue;
            }
            field.setAccessible(true);
            return field.get(null);
        }
        return null;
    }

    private static DrawPlan drawPlan(Class<?> graphicsClass, Class<?> pipelineClass,
                                     Class<?> textureClass) {
        GraphicsDrawPlans graphicsPlans = DRAW_PLANS.get(graphicsClass);
        if (graphicsPlans == null) {
            GraphicsDrawPlans created = new GraphicsDrawPlans(graphicsClass);
            GraphicsDrawPlans existing = DRAW_PLANS.putIfAbsent(graphicsClass, created);
            graphicsPlans = existing == null ? created : existing;
            if (existing == null) DRAW_PLAN_CACHE_NODE_BUILDS.incrementAndGet();
        }
        return graphicsPlans.planFor(pipelineClass, textureClass);
    }

    private static Object resolveDrawPlan(Class<?> graphicsClass, Class<?> pipelineClass,
                                          Class<?> textureClass) {
        DRAW_METHOD_RESOLUTIONS.incrementAndGet();
        Method[] methods = graphicsClass.getMethods();
        DrawLayout[] layouts = pipelineClass == null ? DIRECT_DRAW_LAYOUTS : PIPELINE_DRAW_LAYOUTS;
        for (DrawLayout layout : layouts) {
            for (String name : DRAW_METHODS) {
                for (Method method : methods) {
                    if (method.getName().equals(name)
                            && layout.accepts(method.getParameterTypes(), pipelineClass, textureClass)) {
                        return new DrawPlan(method, layout);
                    }
                }
            }
        }
        return MISS;
    }

    private static boolean matches(String value, String[] candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(value)) return true;
        }
        return false;
    }

    private static boolean isCompatible(Class<?> parameter, Class<?> valueType) {
        if (!parameter.isPrimitive()) return parameter.isAssignableFrom(valueType);
        if (parameter == int.class) return valueType == Integer.class;
        if (parameter == float.class) return valueType == Float.class;
        if (parameter == double.class) return valueType == Double.class;
        if (parameter == long.class) return valueType == Long.class;
        if (parameter == boolean.class) return valueType == Boolean.class;
        if (parameter == byte.class) return valueType == Byte.class;
        if (parameter == short.class) return valueType == Short.class;
        if (parameter == char.class) return valueType == Character.class;
        return false;
    }

    private static void setShaderColor(int tintRgb) {
        try {
            Method method = shaderColorMethod();
            if (method == null) return;
            method.invoke(null,
                    Float.valueOf(((tintRgb >> 16) & 0xFF) / 255.0F),
                    Float.valueOf(((tintRgb >> 8) & 0xFF) / 255.0F),
                    Float.valueOf((tintRgb & 0xFF) / 255.0F), Float.valueOf(1.0F));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Color-bearing blit overloads do not need the legacy shader multiplier.
        }
    }

    private static Method shaderColorMethod() {
        Object cached = shaderColorMethod;
        if (cached == null) {
            synchronized (ForgeGuiTextureBridge.class) {
                cached = shaderColorMethod;
                if (cached == null) {
                    try {
                        Class<?> renderSystem = cachedClass("com.mojang.blaze3d.systems.RenderSystem");
                        cached = renderSystem == null ? MISS : renderSystem.getMethod(
                                "setShaderColor", float.class, float.class, float.class, float.class);
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                        cached = MISS;
                    }
                    shaderColorMethod = cached;
                }
            }
        }
        return cached instanceof Method ? (Method) cached : null;
    }

    static long[] reflectionResolutionsForTest() {
        return new long[] {CLASS_RESOLUTIONS.get(), IDENTIFIER_RESOLUTIONS.get(),
                PIPELINE_RESOLUTIONS.get(), DRAW_METHOD_RESOLUTIONS.get()};
    }

    static long[] drawPlanAllocationsForTest() {
        return new long[] {DRAW_PLAN_CACHE_NODE_BUILDS.get(), DRAW_PLAN_SLOT_BUILDS.get(),
                DRAW_ARGUMENT_BUFFER_BUILDS.get()};
    }

    private static final class GraphicsDrawPlans {
        private final Class<?> graphicsClass;
        private final Map<Class<?>, TextureDrawPlans> byTexture = new ConcurrentHashMap<>();

        private GraphicsDrawPlans(Class<?> graphicsClass) {
            this.graphicsClass = graphicsClass;
        }

        private DrawPlan planFor(Class<?> pipelineClass, Class<?> textureClass) {
            TextureDrawPlans texturePlans = byTexture.get(textureClass);
            if (texturePlans == null) {
                TextureDrawPlans created = new TextureDrawPlans(graphicsClass, textureClass);
                TextureDrawPlans existing = byTexture.putIfAbsent(textureClass, created);
                texturePlans = existing == null ? created : existing;
                if (existing == null) DRAW_PLAN_CACHE_NODE_BUILDS.incrementAndGet();
            }
            return texturePlans.planFor(pipelineClass);
        }
    }

    private static final class TextureDrawPlans {
        private final Class<?> graphicsClass;
        private final Class<?> textureClass;
        private final Map<Class<?>, Object> pipelinePlans = new ConcurrentHashMap<>();
        private volatile Object directPlan;

        private TextureDrawPlans(Class<?> graphicsClass, Class<?> textureClass) {
            this.graphicsClass = graphicsClass;
            this.textureClass = textureClass;
        }

        private DrawPlan planFor(Class<?> pipelineClass) {
            Object cached;
            if (pipelineClass == null) {
                cached = directPlan;
                if (cached == null) {
                    synchronized (this) {
                        cached = directPlan;
                        if (cached == null) {
                            cached = resolveDrawPlan(graphicsClass, null, textureClass);
                            DRAW_PLAN_SLOT_BUILDS.incrementAndGet();
                            directPlan = cached;
                        }
                    }
                }
            } else {
                cached = pipelinePlans.get(pipelineClass);
                if (cached == null) {
                    Object resolved = resolveDrawPlan(graphicsClass, pipelineClass, textureClass);
                    DRAW_PLAN_SLOT_BUILDS.incrementAndGet();
                    Object existing = pipelinePlans.putIfAbsent(pipelineClass, resolved);
                    cached = existing == null ? resolved : existing;
                }
            }
            return cached instanceof DrawPlan ? (DrawPlan) cached : null;
        }
    }

    private static final class DrawPlan {
        private final Method method;
        private final DrawLayout layout;
        private final ThreadLocal<Object[]> argumentBuffer;

        private DrawPlan(Method method, DrawLayout layout) {
            this.method = method;
            this.layout = layout;
            this.argumentBuffer = ThreadLocal.withInitial(() -> {
                DRAW_ARGUMENT_BUFFER_BUILDS.incrementAndGet();
                return new Object[layout.argumentCount];
            });
        }

        private void invoke(Object graphics, Object pipeline, Object texture,
                            int x, int y, int width, int height, int u, int v,
                            int sourceWidth, int sourceHeight,
                            int textureWidth, int textureHeight, int color)
                throws ReflectiveOperationException {
            Object[] args = argumentBuffer.get();
            int index = 0;
            if (layout.pipeline) args[index++] = pipeline;
            args[index++] = texture;
            args[index++] = Integer.valueOf(x);
            args[index++] = Integer.valueOf(y);
            if (layout.floatUv) {
                args[index++] = Float.valueOf(u);
                args[index++] = Float.valueOf(v);
            } else {
                args[index++] = Integer.valueOf(u);
                args[index++] = Integer.valueOf(v);
            }
            args[index++] = Integer.valueOf(width);
            args[index++] = Integer.valueOf(height);
            if (layout.sourceDimensions) {
                args[index++] = Integer.valueOf(sourceWidth);
                args[index++] = Integer.valueOf(sourceHeight);
            }
            args[index++] = Integer.valueOf(textureWidth);
            args[index++] = Integer.valueOf(textureHeight);
            if (layout.color) args[index] = Integer.valueOf(color);
            method.invoke(graphics, args);
        }
    }

    private static final class DrawLayout {
        private final boolean pipeline;
        private final boolean floatUv;
        private final boolean sourceDimensions;
        private final boolean color;
        private final int argumentCount;

        private DrawLayout(boolean pipeline, boolean floatUv,
                           boolean sourceDimensions, boolean color) {
            this.pipeline = pipeline;
            this.floatUv = floatUv;
            this.sourceDimensions = sourceDimensions;
            this.color = color;
            this.argumentCount = (pipeline ? 1 : 0) + 9
                    + (sourceDimensions ? 2 : 0) + (color ? 1 : 0);
        }

        private boolean accepts(Class<?>[] parameters, Class<?> pipelineClass,
                                Class<?> textureClass) {
            if (parameters.length != argumentCount) return false;
            int index = 0;
            if (pipeline && !isCompatible(parameters[index++], pipelineClass)) return false;
            if (!isCompatible(parameters[index++], textureClass)) return false;
            if (!isCompatible(parameters[index++], Integer.class)
                    || !isCompatible(parameters[index++], Integer.class)) return false;
            Class<?> uvType = floatUv ? Float.class : Integer.class;
            if (!isCompatible(parameters[index++], uvType)
                    || !isCompatible(parameters[index++], uvType)) return false;
            if (!isCompatible(parameters[index++], Integer.class)
                    || !isCompatible(parameters[index++], Integer.class)) return false;
            if (sourceDimensions && (!isCompatible(parameters[index++], Integer.class)
                    || !isCompatible(parameters[index++], Integer.class))) return false;
            if (!isCompatible(parameters[index++], Integer.class)
                    || !isCompatible(parameters[index++], Integer.class)) return false;
            return !color || isCompatible(parameters[index], Integer.class);
        }
    }
}
