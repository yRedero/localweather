package redero.localweather.client;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import redero.localweather.LocalWeather;
import redero.localweather.client.betterclouds.VoxyDepth;

/**
 * Rain haze: grey air below the cloud base wherever it rains, thicker in heavy rain and thunder, as fog in a screen
 * pass (shaders/core/rain_haze) rather than surfaces in the world, so it has no edges to show. The storms' rain around
 * the camera is written into a small map every half second, which the shader reads as it steps along each view ray.
 */
public final class RainHaze {
    public static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(LocalWeather.MODID, "rain_haze");
    /** Map texels per side; at the largest distance about 100 blocks each, plenty for something this soft. */
    private static final int SIZE = 128;
    private static final int REFRESH_TICKS = 10;
    /** Extinction per block in full rain, at rainHaze 1. */
    private static final float DENSITY = 0.0022F;

    @Nullable
    private static ShaderInstance shader;
    @Nullable
    private static DynamicTexture map;
    /** The map's corner and size in world blocks, and whether any of it rains. */
    private static double mapX, mapZ, mapSize;
    private static boolean raining;
    private static int ticks;
    private static final float[] SAMPLE = new float[3];

    private RainHaze() {
    }

    public static void setShader(ShaderInstance compiled) {
        shader = compiled;
    }

    private static boolean enabled() {
        return LocalWeatherClientConfig.SPEC.isLoaded() && LocalWeatherClientConfig.RAIN_HAZE.get() > 0.0
                && ClientWeather.INSTANCE.active();
    }

    static double distance() {
        return Math.min(LocalWeatherClientConfig.RAIN_SHAFT_DISTANCE.get(), LocalWeatherClientConfig.CLOUD_DISTANCE.get());
    }

    /** Client tick: rewrites the rain map around the camera now and then. */
    public static void tick() {
        if (!enabled()) {
            raining = false;
            return;
        }
        if (ticks++ % REFRESH_TICKS != 0) return;
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera == null) return;
        if (map == null) {
            map = new DynamicTexture(SIZE, SIZE, false);
            map.setFilter(true, false);
        }
        double size = 2.0 * distance();
        double texel = size / SIZE;
        // Snapped to whole texels, so the map doesn't shimmer as the camera moves.
        mapX = Math.floor((camera.getX() - size * 0.5) / texel) * texel;
        mapZ = Math.floor((camera.getZ() - size * 0.5) / texel) * texel;
        mapSize = size;
        NativeImage pixels = map.getPixels();
        if (pixels == null) return;
        ClientWeather weather = ClientWeather.INSTANCE;
        boolean any = false;
        for (int j = 0; j < SIZE; j++) {
            for (int i = 0; i < SIZE; i++) {
                weather.sample(mapX + (i + 0.5) * texel, mapZ + (j + 0.5) * texel, SAMPLE);
                int rain = Mth.clamp(Math.round(SAMPLE[0] * 255.0F), 0, 255);
                int thunder = Mth.clamp(Math.round(SAMPLE[1] * 255.0F), 0, 255);
                any |= rain > 0;
                pixels.setPixelRGBA(i, j, 0xFF000000 | thunder << 8 | rain);
            }
        }
        map.upload();
        raining = any;
    }

    public static boolean wanted() {
        return shader != null && map != null && raining && enabled();
    }

    /** Draws the haze over the scene; the depth copy and Voxy's depth come from {@link RainShafts}. */
    static void render(Camera camera, int sceneDepth, @Nullable VoxyDepth.Snapshot voxy, double cloudBase, float fade) {
        if (!wanted() || fade <= 0.0F) return;
        Minecraft mc = Minecraft.getInstance();
        ShaderInstance program = shader;
        Vec3 cam = camera.getPosition();
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        float tanX = 1.0F / projection.m00();
        float tanY = 1.0F / projection.m11();
        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();

        // Coloured like the sky, a little greyer and darker.
        float[] fog = RenderSystem.getShaderFogColor();
        float grey = 0.3F * fog[0] + 0.59F * fog[1] + 0.11F * fog[2];
        float red = Mth.lerp(0.4F, fog[0], grey) * 0.8F;
        float green = Mth.lerp(0.4F, fog[1], grey) * 0.8F;
        float blue = Mth.lerp(0.4F, fog[2], grey) * 0.84F;

        program.setSampler("SceneDepth", sceneDepth);
        program.setSampler("RainMap", map.getId());
        if (voxy != null) {
            program.setSampler("LodDepth", voxy.texture());
            program.safeGetUniform("LodInvProj").set(voxy.inverseProjection());
            program.safeGetUniform("LodInfo").set(1.0F, voxy.zeroToOne() ? 1.0F : 0.0F);
        } else {
            program.setSampler("LodDepth", sceneDepth);
            program.safeGetUniform("LodInfo").set(0.0F, 0.0F);
        }
        program.safeGetUniform("ScreenSize").set((float) mc.getMainRenderTarget().width, (float) mc.getMainRenderTarget().height);
        program.safeGetUniform("DepthRange").set(0.05F, mc.gameRenderer.getDepthFar());
        program.safeGetUniform("Look").set(look.x(), look.y(), look.z());
        program.safeGetUniform("Right").set(-left.x() * tanX, -left.y() * tanX, -left.z() * tanX);
        program.safeGetUniform("Up").set(up.x() * tanY, up.y() * tanY, up.z() * tanY);
        program.safeGetUniform("CamY").set((float) cam.y);
        program.safeGetUniform("CloudBase").set((float) cloudBase);
        program.safeGetUniform("MapOrigin").set((float) (mapX - cam.x), (float) (mapZ - cam.z));
        program.safeGetUniform("MapSize").set((float) mapSize);
        program.safeGetUniform("MaxDist").set((float) distance());
        program.safeGetUniform("HazeColor").set(red, green, blue);
        program.safeGetUniform("Density").set(DENSITY * LocalWeatherClientConfig.RAIN_HAZE.get().floatValue());
        program.safeGetUniform("Fade").set(fade);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.addVertex(-1.0F, -1.0F, 0.0F);
        buffer.addVertex(1.0F, -1.0F, 0.0F);
        buffer.addVertex(1.0F, 1.0F, 0.0F);
        buffer.addVertex(-1.0F, 1.0F, 0.0F);
        MeshData mesh = buffer.build();
        if (mesh == null) return;

        RenderSystem.enableBlend();
        // The shader writes the colour already multiplied by its alpha.
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        program.apply();
        BufferUploader.draw(mesh);
        program.clear();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}
