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

    private record Shaft(double x, double z, float rain, float seed, double ground) {
    }

    private static final List<Shaft> SHAFTS = new ArrayList<>();
    /** Registered by the loader's shader event; without it the veils aren't drawn. */
    @Nullable
    private static ShaderInstance shader;
    /** A copy of the scene's depth for the shader to read while it draws onto the scene. */
    @Nullable
    private static TextureTarget depthCopy;
    private static int ticks;

    private RainShafts() {
    }

    /** True when this draws lightning, so vanilla's bolt can be hidden (it would be drawn twice). */
    public static boolean drawsBolts() {
        return shader != null && ClientWeather.INSTANCE.active() && LightningFlashes.enabled();
    }

    /** Geometry closer than this in front of the camera, or behind it, is skipped (see {@link #ahead}). */
    private static final double NEAR = 1.0;
    private static float lookX, lookY, lookZ;

    /**
     * Whether a point (relative to the camera) lies in front of it. These are drawn with depth clamping so far ones
     * aren't cut off, which also turns off clipping at the near plane: a corner behind the camera would stretch its
     * quad across the whole screen for a frame (a dark flash near the edge of storms).
     */
    private static boolean ahead(double x, double y, double z) {
        return x * lookX + y * lookY + z * lookZ > NEAR;
    }

    private static boolean ahead(double x0, double y0, double z0, double x1, double y1, double z1) {
        return ahead(x0, y0, z0) && ahead(x1, y1, z1);
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
        boolean veils = enabled();
        if (shader == null || !(veils || drawsBolts())) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        double base = ClientWeather.cloudBase(level);
        if (Double.isNaN(base)) return;

        Vec3 cam = camera.getPosition();
        // From above the cloud base the rain beneath is hidden by the deck: fade out as the camera climbs past it.
        float below = veils ? 1.0F - smoothstep(base - 10.0, base + 40.0, cam.y) : 0.0F;
        org.joml.Vector3f look = camera.getLookVector();
        lookX = look.x();
        lookY = look.y();
        lookZ = look.z();
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

        // The haze first, before this builds its own geometry: Tesselator has a single buffer, and beginning another
        // while one is open breaks the first (the haze went missing for the frames a bolt was drawn).
        VoxyDepth.Snapshot voxy = VoxyDepth.current();
        boolean haze = below > 0.0F && RainHaze.wanted();
        if (haze) {
            copyDepth(mc);
            RainHaze.render(camera, depthCopy.getDepthTextureId(), voxy, base, below);
        }

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Shaft shaft : below > 0.0F ? SHAFTS : List.<Shaft>of()) {
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
        // Far lightning: jagged bolts from the cloud base to the ground, flickering with their flash.
        final double baseY = base;
        LightningFlashes.forEachBolt(partialTick, (x, groundY, z, intensity, seed) ->
                bolt(buffer, level, cam, baseY, x, groundY, z, intensity, seed));
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

    private static void bolt(BufferBuilder buffer, ClientLevel level, Vec3 cam, double base, double x, double groundY,
                             double z, float intensity, long seed) {
        if (intensity <= 0.01F) return;
        java.util.Random random = new java.util.Random(seed);
        double ground = Double.isNaN(groundY) ? groundAt(level, x, z) : groundY;
        double height = base - ground;
        if (height <= 4.0) return;
        double distance = Math.sqrt(Mth.square(x - cam.x) + Mth.square(z - cam.z));
        // About one in six is a huge bolt: wider, brighter, branching several times.
        boolean huge = random.nextFloat() < 0.17F;
        // A thin core, never under a pixel or two far away (3 blocks per km); the glow around it is soft.
        double core = Math.max(0.45, distance * 0.003) * (huge ? 2.5 : 1.0);
        float alpha = Math.min(1.0F, intensity * (huge ? 2.0F : 1.5F));

        // From the cloud base, leaning a little to one side, down to exactly where it strikes.
        double[] top = {x + (random.nextDouble() - 0.5) * 0.35 * height, base, z + (random.nextDouble() - 0.5) * 0.35 * height};
        double[] foot = {x, ground, z};
        List<double[]> path = jagged(top, foot, random, 6, 0.22);
        drawPath(buffer, cam, path, core, alpha);
        // Branches fork off the upper two thirds and die out on the way down.
        int branches = huge ? 4 + random.nextInt(3) : 1 + random.nextInt(2);
        for (int b = 0; b < branches; b++) {
            double[] from = path.get(path.size() / 8 + random.nextInt(Math.max(1, path.size() * 5 / 8)));
            double angle = random.nextDouble() * Math.PI * 2.0;
            double drop = height * (0.15 + 0.25 * random.nextDouble());
            double reach = drop * (0.4 + 0.6 * random.nextDouble());
            double[] to = {from[0] + Math.cos(angle) * reach, Math.max(ground, from[1] - drop), from[2] + Math.sin(angle) * reach};
            drawPath(buffer, cam, jagged(from, to, random, 4, 0.25), core * 0.55, alpha * 0.6F);
        }
    }

    /** A line from a to b, its midpoints pushed sideways again and again (smaller each time): lightning's zigzag. */
    private static List<double[]> jagged(double[] a, double[] b, java.util.Random random, int depth, double roughness) {
        List<double[]> points = new ArrayList<>();
        points.add(a);
        points.add(b);
        for (int level = 0; level < depth; level++) {
            List<double[]> next = new ArrayList<>(points.size() * 2);
            for (int i = 0; i < points.size() - 1; i++) {
                double[] p = points.get(i), q = points.get(i + 1);
                double length = Math.sqrt(Mth.square(q[0] - p[0]) + Mth.square(q[1] - p[1]) + Mth.square(q[2] - p[2]));
                double push = length * roughness;
                next.add(p);
                next.add(new double[]{(p[0] + q[0]) * 0.5 + (random.nextDouble() - 0.5) * 2.0 * push,
                        (p[1] + q[1]) * 0.5 + (random.nextDouble() - 0.5) * 0.4 * push,
                        (p[2] + q[2]) * 0.5 + (random.nextDouble() - 0.5) * 2.0 * push});
            }
            next.add(points.get(points.size() - 1));
            points = next;
        }
        return points;
    }

    private static void drawPath(BufferBuilder buffer, Vec3 cam, List<double[]> path, double core, float alpha) {
        for (int i = 0; i < path.size() - 1; i++) {
            double[] p = path.get(i), q = path.get(i + 1);
            double x0 = p[0] - cam.x, y0 = p[1] - cam.y, z0 = p[2] - cam.z;
            double x1 = q[0] - cam.x, y1 = q[1] - cam.y, z1 = q[2] - cam.z;
            if (!ahead(x0, y0, z0, x1, y1, z1)) continue;
            softSegment(buffer, x0, y0, z0, x1, y1, z1, core * 8.0, 0.55F, 0.62F, 0.95F, alpha * 0.24F);
            softSegment(buffer, x0, y0, z0, x1, y1, z1, core, 0.92F, 0.95F, 1.0F, alpha);
        }
    }

    /**
     * A line segment as two quads turned to face the camera (at the origin): full alpha along the line, fading to
     * nothing at both edges, so it reads as light rather than a flat strip.
     */
    private static void softSegment(BufferBuilder buffer, double x0, double y0, double z0, double x1, double y1, double z1,
                                    double width, float r, float g, float b, float a) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double mx = (x0 + x1) * 0.5, my = (y0 + y1) * 0.5, mz = (z0 + z1) * 0.5;
        // Sideways = segment direction x view direction.
        double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (length < 1.0E-6) return;
        double half = width * 0.5 / length;
        sx *= half;
        sy *= half;
        sz *= half;
        buffer.addVertex((float) (x0 - sx), (float) (y0 - sy), (float) (z0 - sz)).setColor(r, g, b, 0.0F);
        buffer.addVertex((float) (x1 - sx), (float) (y1 - sy), (float) (z1 - sz)).setColor(r, g, b, 0.0F);
        buffer.addVertex((float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
        buffer.addVertex((float) x0, (float) y0, (float) z0).setColor(r, g, b, a);
        buffer.addVertex((float) x0, (float) y0, (float) z0).setColor(r, g, b, a);
        buffer.addVertex((float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
        buffer.addVertex((float) (x1 + sx), (float) (y1 + sy), (float) (z1 + sz)).setColor(r, g, b, 0.0F);
        buffer.addVertex((float) (x0 + sx), (float) (y0 + sy), (float) (z0 + sz)).setColor(r, g, b, 0.0F);
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

    /** Called by the loader once the shader is compiled (also after resource reloads). */
    public static void setShader(ShaderInstance compiled) {
        shader = compiled;
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
