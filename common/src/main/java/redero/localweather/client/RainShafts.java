package redero.localweather.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;

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
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import redero.localweather.weather.StormCell;

/**
 * Rain seen from afar: grey veils hanging from the cloud base under storms, so you can tell where it rains long
 * before you get there. Vanilla only draws rain right around the camera; past that these take over.
 *
 * <p>The veils stand on a grid fixed to the world, one where a storm's rain (the same shape the server rains with)
 * is strong enough. They turn to face the camera, lean with the wind and thin out towards the ground, and are drawn
 * in a few strips of different density so they look streaky. They are drawn after the clouds and terrain with depth
 * clamping, so storms past the game's far plane still get them, while nearer terrain hides them.
 */
public final class RainShafts {
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

    private record Shaft(double x, double z, float rain, float seed, double ground) {
    }

    private static final List<Shaft> SHAFTS = new ArrayList<>();
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
            return;
        }
        if (ticks++ % REFRESH_TICKS != 0) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        SHAFTS.clear();
        ClientWeather weather = ClientWeather.INSTANCE;
        int ahead = weather.aheadTicks();
        for (StormCell cell : weather.cells()) {
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

    /** The surface where it is loaded, else sea level (far veils end in the fog or behind terrain anyway). */
    private static double groundAt(ClientLevel level, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (level.hasChunk(bx >> 4, bz >> 4)) return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        return level.getSeaLevel();
    }

    /** Draws the veils; call after the clouds and the weather, with the world's view matrices still applied. */
    public static void render(Camera camera, float partialTick) {
        if (!enabled() || SHAFTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        double base = ClientWeather.cloudBase(level);
        if (Double.isNaN(base)) return;

        Vec3 cam = camera.getPosition();
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
        for (Shaft shaft : SHAFTS) {
            double dx = shaft.x - cam.x;
            double dz = shaft.z - cam.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < FADE_IN_START || distance > maxDistance) continue;
            float fade = smoothstep(FADE_IN_START, FADE_IN_END, distance)
                    * (1.0F - smoothstep(0.55 * maxDistance, maxDistance, distance));
            float alpha = BASE_ALPHA * strength * Math.min(1.0F, (shaft.rain - MIN_RAIN) / (1.0F - MIN_RAIN) * 1.6F) * fade;
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

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        // Past the far plane: clamp depth instead of clipping, like Better Clouds does for its far clouds.
        GL11.glEnable(GL32.GL_DEPTH_CLAMP);
        BufferUploader.drawWithShader(mesh);
        GL11.glDisable(GL32.GL_DEPTH_CLAMP);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    public static void clear() {
        SHAFTS.clear();
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
