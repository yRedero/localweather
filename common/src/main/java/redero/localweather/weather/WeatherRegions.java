package redero.localweather.weather;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Weather regions: how cloudy the sky is between the storms, from 0 (clear) to 1 (overcast). A slow noise field over
 * the world, several kilometres across, that drifts with the wind and changes over a day or two, so a clear spell or
 * a cloud front can be seen coming. Storms mostly form where it is cloudy. Server and clients work it out the same way
 * from a few synced numbers ({@link State}) and the game time.
 */
public final class WeatherRegions {
    /** Size of the large regions and of the finer patches on them, in blocks. */
    private static final double SCALE = 3200.0;
    private static final double DETAIL = 1300.0;
    /** Game ticks over which a spot's weather changes noticeably (about one and a half days). */
    private static final double PERIOD = 36000.0;
    /** Spreads the noise so roughly a third of the world is clear, a third fair, a third cloudy. */
    private static final double SPREAD = 1.25;

    /**
     * What the field needs: whether regions are on, the dimension's seed, how far the wind has carried the field, and
     * the world's balance (negative: clearer, positive: cloudier).
     */
    public record State(boolean enabled, long seed, double offsetX, double offsetZ, float balance) {
        public static final State OFF = new State(false, 0L, 0.0, 0.0, 0.0F);

        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, State::enabled,
                ByteBufCodecs.VAR_LONG, State::seed,
                ByteBufCodecs.DOUBLE, State::offsetX,
                ByteBufCodecs.DOUBLE, State::offsetZ,
                ByteBufCodecs.FLOAT, State::balance,
                State::new);

        /** Carried along by the wind. */
        public State moved(double dx, double dz) {
            return new State(enabled, seed, offsetX + dx, offsetZ + dz, balance);
        }
    }

    private static volatile long cachedSeed;
    private static volatile SimplexNoise cached;

    private WeatherRegions() {
    }

    /** 0 (clear) to 1 (overcast) at a point and time, or NaN if regions are off. Thread-safe. */
    public static float cloudiness(State state, double x, double z, long gameTime) {
        if (!state.enabled()) return Float.NaN;
        SimplexNoise noise = noise(state.seed());
        double px = (x - state.offsetX());
        double pz = (z - state.offsetZ());
        double t = gameTime / PERIOD;
        double value = 0.65 * noise.getValue(px / SCALE, pz / SCALE, t)
                + 0.35 * noise.getValue(px / DETAIL + 40.0, pz / DETAIL - 40.0, t * 1.7 + 20.0);
        return Mth.clamp((float) (0.5 + SPREAD * 0.5 * value) + state.balance(), 0.0F, 1.0F);
    }

    private static SimplexNoise noise(long seed) {
        SimplexNoise noise = cached;
        if (noise == null || cachedSeed != seed) {
            noise = new SimplexNoise(new LegacyRandomSource(seed ^ 0x57EA7E5L));
            cachedSeed = seed;
            cached = noise;
        }
        return noise;
    }
}
