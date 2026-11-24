package redero.localweather.client;

import static redero.localweather.client.FarWeather.ahead;
import static redero.localweather.client.FarWeather.groundAt;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.BufferBuilder;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Lightning bolts: from the cloud base, leaning a little, down to exactly where they strike, jagged at every scale,
 * with branches, drawn as a thin bright core in a soft glow. Real strikes and some of the flashes in far storms (see
 * LightningFlashes). Drawn by {@link FarWeather}.
 */
final class LightningBolts {
    private LightningBolts() {
    }

    /** Adds every bolt that is flickering right now to the buffer (positions relative to the camera). */
    static void addTo(BufferBuilder buffer, ClientLevel level, Vec3 cam, double base, float partialTick) {
        LightningFlashes.forEachBolt(partialTick, (x, groundY, z, intensity, seed) ->
                bolt(buffer, level, cam, base, x, groundY, z, intensity, seed));
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
}
