package redero.localweather.client.betterclouds;

import net.minecraft.util.Mth;
import redero.localweather.DevFlags;
import redero.localweather.weather.WeatherRegions;

/**
 * Rare big fair-weather cumulus heaps: where they stand and how tall they are right now. Pure function of the weather
 * regions, the game time and the position, so the cloud generator can ask from its worker thread.
 */
final class CumulusHeaps {
    private CumulusHeaps() {
    }

    /** Grid of possible cumulus heaps, in blocks; about one in six cells holds one. */
    private static final double CUMULUS_CELL = 1600.0;
    private static final float CUMULUS_CHANCE = DevFlags.CUMULUS_TEST ? 0.9F : 0.17F;
    /** Ticks a heap takes to grow and fall apart again (about half a day). */
    private static final double CUMULUS_LIFE = 12000.0;

    /**
     * Height of a fair-weather cumulus heap over this point, 0 where there is none: rare big heaps, only where the
     * weather regions are clear to fair. They drift with the regions (so with the wind) and grow and fall apart over
     * about half a day each, at their own times.
     */
    static float heightAt(WeatherRegions.State regions, long gameTime, double worldX, double worldZ) {
        double x = worldX - regions.offsetX();
        double z = worldZ - regions.offsetZ();
        int cellX = Mth.floor(x / CUMULUS_CELL), cellZ = Mth.floor(z / CUMULUS_CELL);
        float best = 0.0F;
        // The heap can reach over a cell's edge: look at the neighbours too.
        for (int i = cellX - 1; i <= cellX + 1; i++) {
            for (int j = cellZ - 1; j <= cellZ + 1; j++) {
                if (CloudGenContext.hash(i, j, 70) > CUMULUS_CHANCE) continue;
                double centerX = (i + 0.2 + 0.6 * CloudGenContext.hash(i, j, 71)) * CUMULUS_CELL;
                double centerZ = (j + 0.2 + 0.6 * CloudGenContext.hash(i, j, 72)) * CUMULUS_CELL;
                double radius = 75.0 + 125.0 * CloudGenContext.hash(i, j, 73);
                double d = Math.sqrt(Mth.square(x - centerX) + Mth.square(z - centerZ)) / radius;
                if (d >= 1.0) continue;
                // Good weather only: anywhere from clear to fair, not under cloudy or overcast regions (always without them).
                float region = WeatherRegions.cloudiness(regions, centerX + regions.offsetX(), centerZ + regions.offsetZ(), gameTime);
                float fair = Float.isNaN(region) ? 1.0F : 1.0F - CloudGenContext.smoothstep(0.5F, 0.68F, region);
                if (fair <= 0.0F) continue;
                double phase = gameTime / CUMULUS_LIFE + CloudGenContext.hash(i, j, 74);
                float life = (float) Math.sin(Math.PI * (phase - Math.floor(phase)));
                double height = (130.0 + 200.0 * CloudGenContext.hash(i, j, 75)) * fair * life;
                // Steep sides and a rounded top, taller than wide; lumpy so it reads as a heap of puffs, not a ball.
                double lumps = 0.75 + 0.25 * CloudGenContext.STORM_NOISE.getValue(worldX / 60.0 + i * 7.0, worldZ / 60.0 + j * 7.0);
                best = Math.max(best, (float) (height * Math.pow(1.0 - d * d, 0.4) * lumps));
            }
        }
        return best > 12.0F ? best : 0.0F;
    }
}
