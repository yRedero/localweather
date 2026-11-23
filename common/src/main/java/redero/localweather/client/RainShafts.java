package redero.localweather.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;

import javax.annotation.Nullable;

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
import redero.localweather.weather.StormCell;

/**
 * Rain seen from afar: grey veils hanging from the cloud base under storms, so you can tell where it rains long
 * before you get there. Vanilla only draws rain right around the camera; past that these take over.
 *
 * <p>The veils stand on a grid fixed to the world, one where a storm's rain (the same shape the server rains with)
 * is strong enough. They turn to face the camera, lean with the wind and thin out towards the ground, and are drawn
 * in a few strips of different density so they look streaky. They are drawn after the clouds and terrain by their
 * own shader (shaders/core/rain_shaft), which hides them only where terrain is really nearer: storms past the game's
 * far plane can't be depth tested the usual way, so it compares distances from the game's and Voxy's depth.
 */
public final class RainShafts {
    /** The veils' core shader, registered by each loader. */
    public static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(LocalWeather.MODID, "rain_shaft");
    /** Grid spacing of the veils, in blocks. */
    private static final double SPACING = 220.0;
    /** Veils are found again this often (they barely move in between). */
    private static final int REFRESH_TICKS = 10;
    /** Vanilla's rain covers the camera's surroundings; veils fade in between these distances. */
    private static final double FADE_IN_START = 450.0;
    private static final double FADE_IN_END = 1100.0;
    /** Rain needs to be at least this strong at a grid point for a veil, so veils stay under the dark core. */
    private static final float MIN_RAIN = 0.3F;
    /** Strips per veil; each gets its own density so the veil looks streaky. */
    private static final int STRIPS = 10;
    /** Share of a veil's height over which it fades in below the cloud base, so it has no hard top edge. */
    private static final double TOP_FADE = 0.15;
    /** Rain falls about this fast, in blocks per second, which sets how far the wind slants a veil. */
    private static final double FALL_SPEED = 9.0;
    private static final float BASE_ALPHA = 0.3F;
    /** The haze: a coarser grid of wide, soft veils that overlap into one, for any rain at all. */
    private static final double HAZE_SPACING = 300.0;
    private static final float HAZE_MIN_RAIN = 0.05F;
    private static final double HAZE_FADE_IN_START = 150.0;
    private static final double HAZE_FADE_IN_END = 450.0;
    private static final float HAZE_ALPHA = 0.17F;

    private record Shaft(double x, double z, float rain, float seed, double ground) {
    }

    private static final List<Shaft> SHAFTS = new ArrayList<>();
    private static final List<Shaft> HAZE = new ArrayList<>();
    /** Registered by the loader's shader event; without it the veils aren't drawn. */
    @Nullable
    private static ShaderInstance shader;
    /** A copy of the scene's depth for the shader to read while it draws onto the scene. */
    @Nullable
    private static TextureTarget depthCopy;
    private static int ticks;

    private RainShafts() {
    }

    private static boolean enabled() {
        return LocalWeatherClientConfig.SPEC.isLoaded() && LocalWeatherClientConfig.RAIN_SHAFTS.get()
                && ClientWeather.INSTANCE.active();
    }

    /** Client tick: find where the veils stand. */
    public static void tick() {
        if (!enabled()) {
            SHAFTS.clear();
            HAZE.clear();
            return;
        }
        if (ticks++ % REFRESH_TICKS != 0) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        SHAFTS.clear();
        HAZE.clear();
        ClientWeather weather = ClientWeather.INSTANCE;
        int ahead = weather.aheadTicks();
        boolean haze = LocalWeatherClientConfig.RAIN_HAZE.get() > 0.0;
        for (StormCell cell : weather.cells()) {
            if (haze) findHaze(level, cell, ahead);
            double cx = cell.centerX(ahead);
            double cz = cell.centerZ(ahead);
            double reach = cell.radius * 1.35;
            int minX = Mth.floor((cx - reach) / SPACING), maxX = Mth.floor((cx + reach) / SPACING);
            int minZ = Mth.floor((cz - reach) / SPACING), maxZ = Mth.floor((cz + reach) / SPACING);
            for (int gx = minX; gx <= maxX; gx++) {
                for (int gz = minZ; gz <= maxZ; gz++) {
                    // Jittered within its grid square, so the veils don't stand in rows.
                    double x = (gx + 0.15 + 0.7 * hash(gx, gz, 1)) * SPACING;
                    double z = (gz + 0.15 + 0.7 * hash(gx, gz, 2)) * SPACING;
                    float rain = cell.rainAt(x, z, ahead);
                    if (rain < MIN_RAIN) continue;
                    SHAFTS.add(new Shaft(x, z, rain, hash(gx, gz, 3), groundAt(level, x, z)));
                }
            }
        }
    }

    private static void findHaze(ClientLevel level, StormCell cell, int ahead) {
        double cx = cell.centerX(ahead);
        double cz = cell.centerZ(ahead);
        double reach = cell.radius * 1.35;
        int minX = Mth.floor((cx - reach) / HAZE_SPACING), maxX = Mth.floor((cx + reach) / HAZE_SPACING);
        int minZ = Mth.floor((cz - reach) / HAZE_SPACING), maxZ = Mth.floor((cz + reach) / HAZE_SPACING);
        for (int gx = minX; gx <= maxX; gx++) {
            for (int gz = minZ; gz <= maxZ; gz++) {
                double x = (gx + 0.25 + 0.5 * hash(gx, gz, 11)) * HAZE_SPACING;
                double z = (gz + 0.25 + 0.5 * hash(gx, gz, 12)) * HAZE_SPACING;
                float rain = cell.rainAt(x, z, ahead);
                if (rain < HAZE_MIN_RAIN) continue;
                HAZE.add(new Shaft(x, z, rain, hash(gx, gz, 13), groundAt(level, x, z)));
            }
        }
    }

    /** The surface where it is loaded, else sea level (far veils end in the fog or behind terrain anyway). */
    private static double groundAt(ClientLevel level, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (level.hasChunk(bx >> 4, bz >> 4)) return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        return level.getSeaLevel();
    }

    /** Draws the veils; call after the clouds and the weather, with the world's view matrices still applied. */
    public static void render(Camera camera, float partialTick) {
        if (!enabled() || (SHAFTS.isEmpty() && HAZE.isEmpty()) || shader == null) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        double base = ClientWeather.cloudBase(level);
        if (Double.isNaN(base)) return;

        Vec3 cam = camera.getPosition();
        // From above the cloud base the rain beneath is hidden by the deck: fade out as the camera climbs past it.
        float below = 1.0F - smoothstep(base - 10.0, base + 40.0, cam.y);
        if (below <= 0.0F) return;
        double maxDistance = Math.min(LocalWeatherClientConfig.RAIN_SHAFT_DISTANCE.get(), LocalWeatherClientConfig.CLOUD_DISTANCE.get());
        float strength = LocalWeatherClientConfig.RAIN_SHAFT_STRENGTH.get().floatValue();
        ClientWeather weather = ClientWeather.INSTANCE;
        // Blocks per second the wind carries the rain; the veil's foot trails behind its top by the fall time.
        double windX = weather.windX() * 20.0;
        double windZ = weather.windZ() * 20.0;

        // A dark shade of the fog, as in a storm's shadow, so the veils match the sky at noon, at sunset and at night.
        float[] fog = RenderSystem.getShaderFogColor();
        float red = fog[0] * 0.42F + 0.03F;
        float green = fog[1] * 0.43F + 0.03F;
        float blue = fog[2] * 0.47F + 0.04F;

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // The haze first, the shafts on top. Coloured like the sky, a little greyer.
        float hazeStrength = LocalWeatherClientConfig.RAIN_HAZE.get().floatValue();
        float grey = 0.3F * fog[0] + 0.59F * fog[1] + 0.11F * fog[2];
        float hazeRed = Mth.lerp(0.35F, fog[0], grey) * 0.85F;
        float hazeGreen = Mth.lerp(0.35F, fog[1], grey) * 0.85F;
        float hazeBlue = Mth.lerp(0.35F, fog[2], grey) * 0.88F;
        for (Shaft haze : HAZE) {
            double dx = haze.x - cam.x;
            double dz = haze.z - cam.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < HAZE_FADE_IN_START || distance > maxDistance) continue;
            float fade = smoothstep(HAZE_FADE_IN_START, HAZE_FADE_IN_END, distance)
                    * (1.0F - smoothstep(0.55 * maxDistance, maxDistance, distance));
            float a = HAZE_ALPHA * hazeStrength * Math.min(1.0F, haze.rain * 1.6F) * fade * below;
            if (a < 0.002F) continue;
            double top = base - cam.y;
            double bottom = haze.ground - cam.y;
            if (top <= bottom) continue;
            double fadeY = top - (top - bottom) * 0.25;
            double rightX = -dz / distance, rightZ = dx / distance;
            // Wide enough that neighbours overlap into one haze.
            double half = HAZE_SPACING * (1.0 + 0.2 * haze.seed);
            double x0 = dx - rightX * half, z0 = dz - rightZ * half;
            double x1 = dx + rightX * half, z1 = dz + rightZ * half;
            buffer.addVertex((float) x0, (float) top, (float) z0).setColor(hazeRed, hazeGreen, hazeBlue, 0.0F);
            buffer.addVertex((float) x0, (float) fadeY, (float) z0).setColor(hazeRed, hazeGreen, hazeBlue, a);
            buffer.addVertex((float) x1, (float) fadeY, (float) z1).setColor(hazeRed, hazeGreen, hazeBlue, a);
            buffer.addVertex((float) x1, (float) top, (float) z1).setColor(hazeRed, hazeGreen, hazeBlue, 0.0F);
            buffer.addVertex((float) x0, (float) fadeY, (float) z0).setColor(hazeRed, hazeGreen, hazeBlue, a);
            buffer.addVertex((float) x0, (float) bottom, (float) z0).setColor(hazeRed, hazeGreen, hazeBlue, a * 0.85F);
            buffer.addVertex((float) x1, (float) bottom, (float) z1).setColor(hazeRed, hazeGreen, hazeBlue, a * 0.85F);
            buffer.addVertex((float) x1, (float) fadeY, (float) z1).setColor(hazeRed, hazeGreen, hazeBlue, a);
        }
        for (Shaft shaft : SHAFTS) {
            double dx = shaft.x - cam.x;
            double dz = shaft.z - cam.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < FADE_IN_START || distance > maxDistance) continue;
            float fade = smoothstep(FADE_IN_START, FADE_IN_END, distance)
                    * (1.0F - smoothstep(0.55 * maxDistance, maxDistance, distance));
            float alpha = BASE_ALPHA * strength * Math.min(1.0F, (shaft.rain - MIN_RAIN) / (1.0F - MIN_RAIN) * 1.6F) * fade * below;
            if (alpha < 0.004F) continue;

            double top = base + 6.0 - cam.y;
            double bottom = shaft.ground - cam.y;
            if (top <= bottom) continue;
            double fallSeconds = (top - bottom) / FALL_SPEED;
            double leanX = windX * fallSeconds * 0.5;
            double leanZ = windZ * fallSeconds * 0.5;
            // Face the camera: the veil spreads across the line of sight.
            double rightX = -dz / distance;
            double rightZ = dx / distance;
            // Narrower than the grid, so there are gaps between veils and they read as separate shafts.
            double width = SPACING * (0.55 + 0.35 * shaft.seed);
            double fadeY = top - (top - bottom) * TOP_FADE;
            double fadeT = TOP_FADE;
            int seed = Mth.floor(shaft.seed * 100000.0F);
            for (int i = 0; i < STRIPS; i++) {
                float u0 = (float) i / STRIPS, u1 = (float) (i + 1) / STRIPS;
                // Denser in the middle, soft at the edges, and strips of very different density: streaks.
                float edge = (float) Math.sin(Math.PI * (u0 + u1) * 0.5);
                float streak = hash(seed, i, 4);
                float a = alpha * edge * streak * streak * 1.6F;
                double o0 = (u0 - 0.5) * width, o1 = (u1 - 0.5) * width;
                double x0 = dx + rightX * o0, z0 = dz + rightZ * o0;
                double x1 = dx + rightX * o1, z1 = dz + rightZ * o1;
                // Fades in just below the cloud base, then thins out on the way down.
                double mx0 = x0 + leanX * fadeT, mz0 = z0 + leanZ * fadeT, mx1 = x1 + leanX * fadeT, mz1 = z1 + leanZ * fadeT;
                buffer.addVertex((float) x0, (float) top, (float) z0).setColor(red, green, blue, 0.0F);
                buffer.addVertex((float) mx0, (float) fadeY, (float) mz0).setColor(red, green, blue, a);
                buffer.addVertex((float) mx1, (float) fadeY, (float) mz1).setColor(red, green, blue, a);
                buffer.addVertex((float) x1, (float) top, (float) z1).setColor(red, green, blue, 0.0F);
                buffer.addVertex((float) mx0, (float) fadeY, (float) mz0).setColor(red, green, blue, a);
                buffer.addVertex((float) (x0 + leanX), (float) bottom, (float) (z0 + leanZ)).setColor(red, green, blue, a * 0.3F);
                buffer.addVertex((float) (x1 + leanX), (float) bottom, (float) (z1 + leanZ)).setColor(red, green, blue, a * 0.3F);
                buffer.addVertex((float) mx1, (float) fadeY, (float) mz1).setColor(red, green, blue, a);
            }
        }
        MeshData mesh = buffer.build();
        if (mesh == null) return;

        RenderTarget main = mc.getMainRenderTarget();
        if (depthCopy == null) {
            depthCopy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        } else if (depthCopy.width != main.width || depthCopy.height != main.height) {
            depthCopy.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        depthCopy.copyDepthFrom(main);
        main.bindWrite(false);

        ShaderInstance program = shader;
        program.setSampler("SceneDepth", depthCopy.getDepthTextureId());
        program.safeGetUniform("DepthRange").set(0.05F, mc.gameRenderer.getDepthFar());
        VoxyDepth.Snapshot voxy = VoxyDepth.current();
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

    /** Called by the loader once the shader is compiled (also after resource reloads). */
    public static void setShader(ShaderInstance compiled) {
        shader = compiled;
    }

    public static void clear() {
        SHAFTS.clear();
        HAZE.clear();
    }

    private static float smoothstep(double edge0, double edge1, double x) {
        double t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return (float) (t * t * (3.0 - 2.0 * t));
    }

    /** 0..1, fixed for a grid point (and salt). */
    private static float hash(int x, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }
}
