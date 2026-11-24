package redero.localweather.client;

import javax.annotation.Nullable;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import redero.localweather.LocalWeather;
import redero.localweather.client.betterclouds.VoxyDepth;

/**
 * Weather drawn into the world past vanilla's rain: the haze (RainHaze, its own screen pass), the rain shafts
 * (RainShafts) and the lightning bolts (LightningBolts). The shafts and bolts are drawn by one shader
 * (shaders/core/rain_shaft) that hides them only where terrain is really nearer: much of this lies past the game's far
 * plane, where depth can't be compared the usual way, so it compares distances from a copy of the game's depth and
 * from Voxy's own.
 */
public final class FarWeather {
    /** The shafts' and bolts' core shader, registered by each loader. */
    public static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(LocalWeather.MODID, "rain_shaft");
    /** Registered by the loader's shader event; without it nothing is drawn. */
    @Nullable
    private static ShaderInstance shader;
    /** A copy of the scene's depth for the shaders to read while they draw onto the scene. */
    @Nullable
    private static TextureTarget depthCopy;
    /** Geometry closer than this in front of the camera, or behind it, is skipped (see {@link #ahead}). */
    private static final double NEAR = 1.0;
    private static float lookX, lookY, lookZ;

    private FarWeather() {
    }

    /** True when this draws lightning, so vanilla's bolt can be hidden (it would be drawn twice). */
    public static boolean drawsBolts() {
        return shader != null && ClientWeather.INSTANCE.active() && LightningFlashes.enabled() && ClientSettings.get().lightningBolts();
    }

    /** Called by the loader once the shader is compiled (also after resource reloads). */
    public static void setShader(ShaderInstance compiled) {
        shader = compiled;
    }

    /** Client tick. */
    public static void tick() {
        RainShafts.tick();
        RainHaze.tick();
    }

    public static void clear() {
        RainShafts.clear();
    }

    /**
     * Whether a point (relative to the camera) lies in front of it. These are drawn with depth clamping so far ones
     * aren't cut off, which also turns off clipping at the near plane: a corner behind the camera would stretch its
     * quad across the whole screen for a frame (a dark flash near the edge of storms).
     */
    static boolean ahead(double x, double y, double z) {
        return x * lookX + y * lookY + z * lookZ > NEAR;
    }

    static boolean ahead(double x0, double y0, double z0, double x1, double y1, double z1) {
        return ahead(x0, y0, z0) && ahead(x1, y1, z1);
    }

    /** The surface where it is loaded, else sea level (far weather ends in the fog or behind terrain anyway). */
    static double groundAt(ClientLevel level, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (level.hasChunk(bx >> 4, bz >> 4)) return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        return level.getSeaLevel();
    }

    static float smoothstep(double edge0, double edge1, double x) {
        double t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return (float) (t * t * (3.0 - 2.0 * t));
    }

    /** Draws it all; call after the clouds and the weather, with the world's view matrices still applied. */
    public static void render(Camera camera, float partialTick) {
        boolean weather = ClientWeather.INSTANCE.active();
        if (shader == null || !weather) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        double base = ClientWeather.cloudBase(level);
        if (Double.isNaN(base)) return;

        Vec3 cam = camera.getPosition();
        // From above the cloud base the rain beneath is hidden by the deck: fade out as the camera climbs past it.
        float below = 1.0F - smoothstep(base - 10.0, base + 40.0, cam.y);
        org.joml.Vector3f look = camera.getLookVector();
        lookX = look.x();
        lookY = look.y();
        lookZ = look.z();
        // The haze first, before this builds its own geometry: Tesselator has a single buffer, and beginning another
        // while one is open breaks the first (the haze went missing for the frames a bolt was drawn).
        VoxyDepth.Snapshot voxy = VoxyDepth.current();
        boolean haze = below > 0.0F && RainHaze.wanted();
        if (haze) {
            copyDepth(mc);
            RainHaze.render(camera, depthCopy.getDepthTextureId(), voxy, base, below);
        }

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        if (below > 0.0F) RainShafts.addTo(buffer, cam, base, below);
        if (drawsBolts()) LightningBolts.addTo(buffer, level, cam, base, partialTick);
        MeshData mesh = buffer.build();
        if (mesh == null) return;
        if (!haze) copyDepth(mc);

        ShaderInstance program = shader;
        program.setSampler("SceneDepth", depthCopy.getDepthTextureId());
        program.safeGetUniform("DepthRange").set(0.05F, mc.gameRenderer.getDepthFar());
        if (voxy != null) {
            program.setSampler("LodDepth", voxy.texture());
            program.safeGetUniform("LodInvProj").set(voxy.inverseProjection());
            program.safeGetUniform("LodInfo").set(1.0F, voxy.zeroToOne() ? 1.0F : 0.0F);
        } else {
            program.setSampler("LodDepth", depthCopy.getDepthTextureId());
            program.safeGetUniform("LodInfo").set(0.0F, 0.0F);
        }

        RenderSystem.setShader(() -> program);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // The shader does its own depth test by distance; depth is only clamped so far veils aren't clipped away.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        GL11.glEnable(GL32.GL_DEPTH_CLAMP);
        BufferUploader.drawWithShader(mesh);
        GL11.glDisable(GL32.GL_DEPTH_CLAMP);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** Copies the scene's depth for the shaders to read while they draw onto the scene. */
    private static void copyDepth(Minecraft mc) {
        RenderTarget main = mc.getMainRenderTarget();
        if (depthCopy == null) {
            depthCopy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        } else if (depthCopy.width != main.width || depthCopy.height != main.height) {
            depthCopy.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        depthCopy.copyDepthFrom(main);
        main.bindWrite(false);
    }
}
