package redero.localweather.client.betterclouds;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.annotation.Nullable;

import org.joml.Matrix4f;

import redero.localweather.LocalWeather;

/**
 * Reads Voxy's own depth texture and projection, by reflection so Local Weather doesn't depend on Voxy.
 * Past the game's far plane the main depth buffer can't tell which far terrain is in front of a cloud (all of
 * it is squashed against the far plane), but Voxy's own depth reaches 48 km.
 */
public final class VoxyDepth {
    private static boolean initialized;
    private static boolean broken;
    private static Method getNullable, getViewport, getDepthTex, isZero2One;
    private static Field pipeline, fb, textureId, projection, properties;

    /** Depth texture id, inverse projection, and whether depth is stored 0..1; null without Voxy rendering. */
    public record Snapshot(int texture, Matrix4f inverseProjection, boolean zeroToOne) {
    }

    private VoxyDepth() {
    }

    @Nullable
    public static Snapshot current() {
        if (broken) return null;
        try {
            if (!initialized) init();
            if (broken) return null;
            Object renderSystem = getNullable.invoke(null);
            if (renderSystem == null) return null;
            Object viewport = getViewport.invoke(renderSystem);
            Object renderPipeline = pipeline.get(renderSystem);
            if (viewport == null || renderPipeline == null) return null;
            Object texture = getDepthTex.invoke(fb.get(renderPipeline));
            Matrix4f proj = (Matrix4f) projection.get(viewport);
            if (texture == null || proj == null) return null;
            boolean zeroToOne = (boolean) isZero2One.invoke(properties.get(renderPipeline));
            return new Snapshot(textureId.getInt(texture), new Matrix4f(proj).invert(), zeroToOne);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            LocalWeather.LOGGER.warn("Couldn't read Voxy's depth; clouds may show over far Voxy terrain", e);
            return null;
        }
    }

    private static void init() throws ReflectiveOperationException {
        initialized = true;
        Class<?> holder;
        try {
            holder = Class.forName("me.cortex.voxy.client.core.IGetVoxyRenderSystem", false, VoxyDepth.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            broken = true;
            return;
        }
        Class<?> renderSystem = Class.forName("me.cortex.voxy.client.core.VoxyRenderSystem", false, holder.getClassLoader());
        Class<?> abstractPipeline = Class.forName("me.cortex.voxy.client.core.AbstractRenderPipeline", false, holder.getClassLoader());
        Class<?> depthFramebuffer = Class.forName("me.cortex.voxy.client.core.rendering.util.DepthFramebuffer", false, holder.getClassLoader());
        Class<?> glTexture = Class.forName("me.cortex.voxy.client.core.gl.GlTexture", false, holder.getClassLoader());
        Class<?> viewport = Class.forName("me.cortex.voxy.client.core.rendering.Viewport", false, holder.getClassLoader());
        Class<?> renderProperties = Class.forName("me.cortex.voxy.client.core.RenderProperties", false, holder.getClassLoader());

        getNullable = holder.getMethod("getNullable");
        getViewport = renderSystem.getMethod("getViewport");
        pipeline = renderSystem.getDeclaredField("pipeline");
        pipeline.setAccessible(true);
        fb = abstractPipeline.getField("fb");
        properties = abstractPipeline.getField("properties");
        getDepthTex = depthFramebuffer.getMethod("getDepthTex");
        textureId = glTexture.getField("id");
        projection = viewport.getField("projection");
        isZero2One = renderProperties.getMethod("isZero2One");
        LocalWeather.LOGGER.info("Voxy found: far clouds are hidden behind far Voxy terrain using its depth");
    }
}
