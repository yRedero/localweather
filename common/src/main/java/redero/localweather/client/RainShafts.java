package redero.localweather.client;

import static redero.localweather.client.FarWeather.ahead;
import static redero.localweather.client.FarWeather.groundAt;
import static redero.localweather.client.FarWeather.smoothstep;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import redero.localweather.weather.StormCell;

/**
 * Rain seen from afar: dark veils hanging from the cloud base under heavy rain, so you can tell where it rains hard
 * long before you get there (the lighter rain shows as haze, see RainHaze). Vanilla only draws rain right around the
 * camera; past that these take over. Drawn by {@link FarWeather}.
 *
 * <p>The veils stand on a grid fixed to the world, one where a storm's rain (the same shape the server rains with)
 * is strong enough. They turn to face the camera, lean with the wind and thin out towards the ground, and are drawn
 * in strips of different density so they look streaky.
 */
final class RainShafts {
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
        return ClientSettings.get().rainShafts() && ClientWeather.INSTANCE.active();
    }

    /** Client tick: find where the veils stand. */
    static void tick() {
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

    /** Adds the veils' strips to the buffer (positions relative to the camera). */
    static void addTo(BufferBuilder buffer, Vec3 cam, double base, float below) {
        if (!enabled()) return;
        double maxDistance = ClientSettings.get().weatherDistance();
        float strength = (float) ClientSettings.get().rainShaftStrength();
        ClientWeather weather = ClientWeather.INSTANCE;
        // Blocks per second the wind carries the rain; the veil's foot trails behind its top by the fall time.
        double windX = weather.windX() * 20.0;
        double windZ = weather.windZ() * 20.0;

        // A dark shade of the fog, as in a storm's shadow, so the veils match the sky at noon, at sunset and at night.
        float[] fog = RenderSystem.getShaderFogColor();
        float red = fog[0] * 0.42F + 0.03F;
        float green = fog[1] * 0.43F + 0.03F;
        float blue = fog[2] * 0.47F + 0.04F;

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
                if (!ahead(x0, top, z0, x1, top, z1) || !ahead(x0 + leanX, bottom, z0 + leanZ, x1 + leanX, bottom, z1 + leanZ)) continue;
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
    }

    static void clear() {
        SHAFTS.clear();
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
