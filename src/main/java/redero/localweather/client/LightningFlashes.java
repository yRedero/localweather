package redero.localweather.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import redero.localweather.weather.StormCell;

/**
 * Short glows inside storm clouds: one for every real lightning bolt, plus flashes inside thunderstorm cores on
 * their own, so far storms flicker too (the server only spawns bolts near players). Each flash flickers like
 * lightning, a strong pulse followed by weaker ones, and in-cloud flashes are followed by distant thunder.
 */
public final class LightningFlashes {
    /** At most this many flashes at once; the shader takes this many. */
    public static final int MAX = 4;
    /** Per tick, a fully grown thunderstorm starts an in-cloud flash with this chance (about every 4 seconds). */
    private static final float CLOUD_FLASH_CHANCE = 0.012F;
    /** Sound travels about this many blocks per tick (340 m/s). */
    private static final float SOUND_SPEED = 17.0F;
    /** Thunder from flashes further than this isn't heard. */
    private static final double THUNDER_RANGE = 6000.0;

    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static long ticks;

    /** A glow centred on (x, z) with the given radius; flicker is picked per flash. */
    private record Flash(double x, double z, float radius, float strength, long start, int pattern) {
        float intensity(float time) {
            float t = (time - start) / 20.0F;
            if (t < 0.0F) return 0.0F;
            float glow = pulse(t, 0.0F, 1.0F);
            // Most lightning flickers: one or two weaker return strokes shortly after.
            if (pattern >= 1) glow = Math.max(glow, pulse(t, 0.12F, 0.7F));
            if (pattern >= 2) glow = Math.max(glow, pulse(t, 0.31F, 0.5F));
            return strength * glow;
        }

        boolean finished(float time) {
            return (time - start) / 20.0F > 0.9F;
        }

        private static float pulse(float t, float at, float peak) {
            return t < at ? 0.0F : peak * (float) Math.exp(-(t - at) * 13.0F);
        }
    }

    private LightningFlashes() {
    }

    /** Called every client tick while local weather is active and the game isn't paused. */
    static void tick(List<StormCell> cells, int aheadTicks, Entity camera) {
        ticks++;
        FLASHES.removeIf(flash -> flash.finished(ticks));
        if (!enabled()) return;
        for (StormCell cell : cells) {
            float strength = cell.thunder * cell.envelope(aheadTicks);
            if (strength < 0.4F || RANDOM.nextFloat() > CLOUD_FLASH_CHANCE * strength) continue;
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            double distance = cell.radius * 0.55 * Math.sqrt(RANDOM.nextDouble());
            double x = cell.centerX(aheadTicks) + Math.cos(angle) * distance;
            double z = cell.centerZ(aheadTicks) + Math.sin(angle) * distance;
            add(x, z, 120.0F + RANDOM.nextFloat() * 220.0F, 0.6F + 0.5F * RANDOM.nextFloat());
            thunderFrom(x, z, camera);
        }
    }

    /** A real lightning bolt lights up the clouds above it, brighter than an in-cloud flash. */
    public static void bolt(double x, double z) {
        if (enabled()) add(x, z, 260.0F, 1.3F);
    }

    private static void add(double x, double z, float radius, float strength) {
        if (FLASHES.size() >= MAX) FLASHES.remove(0);
        FLASHES.add(new Flash(x, z, radius, strength, ticks, RANDOM.nextInt(3)));
    }

    /** Thunder rolls in after the flash, later and quieter the further away it is. */
    private static void thunderFrom(double x, double z, Entity camera) {
        if (!LocalWeatherClientConfig.SPEC.isLoaded() || !LocalWeatherClientConfig.DISTANT_THUNDER.get()) return;
        double distance = Math.sqrt(Mth.square(x - camera.getX()) + Mth.square(z - camera.getZ()));
        if (distance > THUNDER_RANGE) return;
        float volume = (float) (1.0 - distance / THUNDER_RANGE) * 0.9F;
        float pitch = 0.55F + RANDOM.nextFloat() * 0.3F;
        int delay = (int) (distance / SOUND_SPEED);
        Minecraft.getInstance().getSoundManager().playDelayed(new SimpleSoundInstance(
                SoundEvents.LIGHTNING_BOLT_THUNDER.getLocation(), SoundSource.WEATHER, volume, pitch, RANDOM, false, 0,
                SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true), delay);
    }

    /**
     * Writes up to {@link #MAX} flashes as (x and z relative to the camera, radius, intensity) into {@code out}.
     * @return how many were written
     */
    public static int write(double cameraX, double cameraZ, float partialTick, float[] out) {
        float time = ticks + partialTick;
        int count = 0;
        for (Flash flash : FLASHES) {
            float intensity = flash.intensity(time);
            if (intensity <= 0.01F) continue;
            out[count * 4] = (float) (flash.x - cameraX);
            out[count * 4 + 1] = (float) (flash.z - cameraZ);
            out[count * 4 + 2] = flash.radius;
            out[count * 4 + 3] = intensity;
            count++;
        }
        return count;
    }

    public static void clear() {
        FLASHES.clear();
    }

    private static boolean enabled() {
        return !LocalWeatherClientConfig.SPEC.isLoaded() || LocalWeatherClientConfig.LIGHTNING_FLASHES.get();
    }
}
