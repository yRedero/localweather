package redero.localweather.weather;

import net.minecraft.util.RandomSource;

/** Kinds of storm cell. Ranges are rolled once when a cell spawns. */
public enum CellType {
    SHOWER(250, 500, 0.5F, 0.8F, 0.0F, 0.0F, 6, 12),
    RAIN(700, 1400, 0.7F, 1.0F, 0.0F, 0.3F, 15, 30),
    THUNDERSTORM(400, 800, 0.9F, 1.0F, 0.8F, 1.0F, 10, 20);

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
