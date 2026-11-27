package redero.localweather.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import redero.localweather.DevFlags;
import redero.localweather.weather.StormCell;

/**
 * Short glows inside storm clouds: one for every real lightning bolt, plus flashes inside thunderstorm cores on
 * their own, so far storms flicker too (the server only spawns bolts near players). Each flash flickers like
 * lightning, a strong pulse followed by weaker ones, and in-cloud flashes are followed by distant thunder.
 */
public final class LightningFlashes {
    /** At most this many flashes at once; the shader takes this many. */
    public static final int MAX = 8;
    /** Per tick, a fully grown thunderstorm starts an in-cloud flash with this chance (about every 4 seconds). */
    private static final float CLOUD_FLASH_CHANCE = 0.012F;
    /** Sound travels about this many blocks per tick (340 m/s). */
    private static final float SOUND_SPEED = 17.0F;

    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static long ticks;

    /** Distant thunder starts at this share of its volume and swells to full over {@link #THUNDER_SWELL_TICKS}. */
    private static final float THUNDER_START = 0.35F;
    private static final int THUNDER_SWELL_TICKS = 30;

    /** Thunder that rolls in: it starts quiet and swells, instead of opening with the sound's loud crack. */
    private static final class RollingThunder extends AbstractTickableSoundInstance {
        private final float fullVolume;
        private int age;

        /** Heard from the flash's direction; its loudness is set here by distance, not by the sound engine. */
        RollingThunder(double x, double y, double z, float volume, float pitch) {
            super(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, RANDOM);
            this.fullVolume = volume;
            this.volume = volume * THUNDER_START;
            this.pitch = pitch;
            this.x = x;
            this.y = y;
            this.z = z;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            age++;
            volume = fullVolume * Mth.lerp(Math.min(1.0F, age / (float) THUNDER_SWELL_TICKS), THUNDER_START, 1.0F);
        }
    }

    /** A glow centred on (x, z) with the given radius; flicker is picked per flash. */
    /** Dev only (-Dlocalweather.lightningTest=true): flashes 8x as often, and every far one shows a bolt. */
    private static final boolean TEST = DevFlags.LIGHTNING_TEST;
    /** A third of the in-cloud flashes further than this also show a bolt to the ground. */
    private static final double BOLT_MIN_DISTANCE = 200.0;
    private static final float BOLT_CHANCE = 0.35F;

    /**
     * Draws one visible bolt: where it strikes (groundY NaN: the surface there), its flicker right now, and a seed for
     * its shape.
     */
    public interface BoltSink {
        void bolt(double x, double groundY, double z, float intensity, long seed);
    }

    private record Flash(double x, double z, float radius, float strength, long start, int pattern, boolean ground,
                         double groundY, long seed) {
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
        double visible = ClientSettings.get().cloudDistance();
        for (StormCell cell : cells) {
            float strength = cell.thunder * cell.envelope(aheadTicks);
            if (strength < 0.4F || RANDOM.nextFloat() > CLOUD_FLASH_CHANCE * strength * (TEST ? 8.0F : 1.0F)) continue;
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            double distance = cell.radius * 0.55 * Math.sqrt(RANDOM.nextDouble());
            double x = cell.centerX(aheadTicks) + Math.cos(angle) * distance;
            double z = cell.centerZ(aheadTicks) + Math.sin(angle) * distance;
            double fromCamera = Math.sqrt(Mth.square(x - camera.getX()) + Mth.square(z - camera.getZ()));
            // Past the clouds nothing would light up; such flashes only took the slots of visible ones.
            if (fromCamera > visible) continue;
            boolean ground = ClientSettings.get().lightningBolts() && fromCamera > BOLT_MIN_DISTANCE
                    && (TEST || RANDOM.nextFloat() < BOLT_CHANCE);
            add(x, z, 120.0F + RANDOM.nextFloat() * 220.0F, 0.6F + 0.5F * RANDOM.nextFloat(), ground, Double.NaN);
            thunderFrom(x, z, camera);
        }
    }

    /**
     * A real lightning bolt lights up the clouds above it, brighter than an in-cloud flash, and is drawn as one of
     * these bolts where it strikes (vanilla's own is hidden, see LightningBoltRendererMixin).
     */
    public static void bolt(double x, double y, double z) {
        if (enabled()) add(x, z, 260.0F, 1.3F, ClientSettings.get().lightningBolts(), y);
    }

    private static void add(double x, double z, float radius, float strength, boolean ground, double groundY) {
        if (FLASHES.size() >= MAX) FLASHES.remove(0);
        FLASHES.add(new Flash(x, z, radius, strength, ticks, RANDOM.nextInt(3), ground, groundY, RANDOM.nextLong()));
    }

    /** The bolts of flashes that strike the ground, while they flicker. */
    public static void forEachBolt(float partialTick, BoltSink sink) {
        float time = ticks + partialTick;
        for (Flash flash : FLASHES) {
            if (!flash.ground) continue;
            float intensity = flash.intensity(time);
            if (intensity > 0.03F) sink.bolt(flash.x, flash.groundY, flash.z, intensity, flash.seed);
        }
    }

    /** Thunder rolls in after the flash from its direction, later and quieter the further away it is. */
    private static void thunderFrom(double x, double z, Entity camera) {
        ClientSettings settings = ClientSettings.get();
        if (!settings.distantThunder()) return;
        double range = thunderRange(settings);
        double distance = Math.sqrt(Mth.square(x - camera.getX()) + Mth.square(z - camera.getZ()));
        if (distance > range) return;
        // Fades faster than linearly: the far edge is a faint rumble, not a third of a nearby crack.
        double near = 1.0 - distance / range;
        float volume = (float) (near * near * settings.thunderVolume());
        if (volume < 0.02F) return;
        float pitch = 0.55F + RANDOM.nextFloat() * 0.3F;
        int delay = (int) (distance / SOUND_SPEED);
        // From the storm's direction, at the height of its clouds.
        double base = ClientWeather.cloudBase(camera.level());
        double y = Double.isNaN(base) ? camera.getY() + 100.0 : base;
        Minecraft.getInstance().getSoundManager().playDelayed(new RollingThunder(x, y, z, volume, pitch), delay);
    }

    /** How far thunder carries: the render distance (vanilla's or Voxy's, the further) up to the clouds, or fixed. */
    static double thunderRange(ClientSettings settings) {
        if (!settings.thunderFollowsRenderDistance()) return settings.thunderDistance();
        Minecraft mc = Minecraft.getInstance();
        double render = Math.max(mc.options.getEffectiveRenderDistance() * 16.0,
                redero.localweather.client.betterclouds.VoxyDepth.renderDistance());
        return Mth.clamp(render, 256.0, settings.cloudDistance());
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

    static boolean enabled() {
        return ClientSettings.get().lightningFlashes();
    }
}
