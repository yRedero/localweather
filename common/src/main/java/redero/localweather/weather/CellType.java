package redero.localweather.weather;

import net.minecraft.util.RandomSource;

/** Kinds of storm cell. Ranges are rolled once when a cell spawns. */
public enum CellType {
    // Sized to be seen from far away with Local Weather's far clouds.
    SHOWER(900, 1600, 0.5F, 0.8F, 0.0F, 0.0F, 10, 20),
    RAIN(2600, 4800, 0.7F, 1.0F, 0.0F, 0.3F, 30, 60),
    THUNDERSTORM(1400, 2400, 0.9F, 1.0F, 0.8F, 1.0F, 20, 40);

    private final int minRadius, maxRadius;
    private final float minIntensity, maxIntensity;
    private final float minThunder, maxThunder;
    private final int minMinutes, maxMinutes;

    CellType(int minRadius, int maxRadius, float minIntensity, float maxIntensity,
             float minThunder, float maxThunder, int minMinutes, int maxMinutes) {
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.minIntensity = minIntensity;
        this.maxIntensity = maxIntensity;
        this.minThunder = minThunder;
        this.maxThunder = maxThunder;
        this.minMinutes = minMinutes;
        this.maxMinutes = maxMinutes;
    }

    /** How far a storm's weather system reaches, in radii: cloud build-up plus outline noise. */
    public static final double SYSTEM_REACH = 3.3;
    /** Above 1 skews radii towards the small end: at 2.2 the median storm sits 22% up the range, and only 7% roll in its top 15%. */
    private static final double SIZE_SKEW = 2.2;

    /** Bigger storms are rarer: radii cluster near the minimum and thin out towards the maximum. */
    public float rollRadius(RandomSource random) {
        return minRadius + (float) Math.pow(random.nextFloat(), SIZE_SKEW) * (maxRadius - minRadius);
    }

    public float rollIntensity(RandomSource random) {
        return minIntensity + random.nextFloat() * (maxIntensity - minIntensity);
    }

    public float rollThunder(RandomSource random) {
        return minThunder + random.nextFloat() * (maxThunder - minThunder);
    }

    public int rollLifetime(RandomSource random) {
        return (minMinutes + random.nextInt(maxMinutes - minMinutes + 1)) * 60 * 20;
    }
}
