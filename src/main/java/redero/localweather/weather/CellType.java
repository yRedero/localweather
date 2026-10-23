package redero.localweather.weather;

import net.minecraft.util.RandomSource;

/** Kinds of storm cell. Ranges are rolled once when a cell spawns. */
public enum CellType {
    // Sized to be seen from far away with Local Weather's far clouds.
    SHOWER(500, 1000, 0.5F, 0.8F, 0.0F, 0.0F, 8, 16),
    RAIN(1600, 3200, 0.7F, 1.0F, 0.0F, 0.3F, 20, 40),
    THUNDERSTORM(900, 1600, 0.9F, 1.0F, 0.8F, 1.0F, 15, 30);

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

    public float rollRadius(RandomSource random) {
        return minRadius + random.nextFloat() * (maxRadius - minRadius);
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
