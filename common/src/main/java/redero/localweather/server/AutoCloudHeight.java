package redero.localweather.server;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import redero.localweather.LocalWeather;
import redero.localweather.weather.LocalWeatherApi;

/**
 * Puts the cloud base above the world's real mountains (heightPreset AUTO). The build limit alone doesn't say
 * how tall terrain gets (vanilla and default Tectonic both stop at 320 but their mountains differ by 100 blocks),
 * so this asks the world generator for the surface height across a wide grid, without generating any chunks.
 * The result is saved per dimension and only redone when the build height changes. Measuring takes a few seconds
 * with heavy terrain mods, so it runs on a background thread; until it's done the manual offset applies.
 */
public final class AutoCloudHeight {
    /** Grid of GRID x GRID samples, SPACING blocks apart, centred on the world spawn: ±7.7 km. */
    private static final int GRID = 25;
    private static final int SPACING = 640;
    /** The cloud base goes this far above most peaks (98th percentile), and at least above the highest sample. */
    private static final int MARGIN = 32;
    private static final double PEAK_PERCENTILE = 0.98;

    private AutoCloudHeight() {
    }

    /** The cloud height offset players see and the dry-above-clouds rule uses, for this dimension. */
    public static int offset(ServerLevel level) {
        if (!LocalWeatherServerConfig.autoHeight()) return LocalWeatherServerConfig.cloudHeightOffset();
        return cloudBase(level) - LocalWeatherApi.VANILLA_CLOUD_HEIGHT;
    }

    private static final Map<ResourceKey<Level>, CompletableFuture<Integer>> RUNNING = new ConcurrentHashMap<>();

    /**
     * The automatic cloud base, worked out once and remembered with the dimension's weather. While it is still
     * being measured this falls back to the manual offset.
     */
    public static int cloudBase(ServerLevel level) {
        WeatherSavedData data = WeatherSavedData.get(level);
        if (data.autoCloudBase != Integer.MIN_VALUE && data.autoBuildHeight == level.getMaxBuildHeight()) return data.autoCloudBase;
        RUNNING.computeIfAbsent(level.dimension(), key -> {
            int buildHeight = level.getMaxBuildHeight();
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> compute(level), Util.backgroundExecutor());
            future.whenComplete((base, error) -> level.getServer().execute(() -> {
                RUNNING.remove(key);
                if (error != null) {
                    LocalWeather.LOGGER.warn("Couldn't measure the terrain for the automatic cloud height", error);
                    return;
                }
                data.autoCloudBase = base;
                data.autoBuildHeight = buildHeight;
                data.setDirty();
                WeatherSimulator.syncAll(level);
            }));
            return future;
        });
        return LocalWeatherApi.VANILLA_CLOUD_HEIGHT + LocalWeatherServerConfig.cloudHeightOffset();
    }

    /** Forgets the saved result, e.g. after changing a terrain mod's settings. */
    public static void reset(ServerLevel level) {
        WeatherSavedData data = WeatherSavedData.get(level);
        data.autoCloudBase = Integer.MIN_VALUE;
        data.setDirty();
    }

    private static int compute(ServerLevel level) {
        long start = System.nanoTime();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        BlockPos spawn = level.getSharedSpawnPos();
        int[] heights = new int[GRID * GRID];
        int n = 0;
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                int x = spawn.getX() + (i - GRID / 2) * SPACING;
                int z = spawn.getZ() + (j - GRID / 2) * SPACING;
                heights[n++] = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level,
                        level.getChunkSource().randomState());
            }
        }
        Arrays.sort(heights);
        int peak = heights[Math.min(heights.length - 1, (int) (heights.length * PEAK_PERCENTILE))];
        int highest = heights[heights.length - 1];
        // Above most mountains and at least the tallest one found; never below vanilla's 192, and not absurdly far
        // above the build limit.
        int base = Mth.clamp(Mth.roundToward(Math.max(peak + MARGIN, highest + 8), 8), LocalWeatherApi.VANILLA_CLOUD_HEIGHT,
                level.getMaxBuildHeight() + 64);
        LocalWeather.LOGGER.info("Automatic cloud height for {}: peaks around y={} (highest sample {}), cloud base y={} ({} ms)",
                level.dimension().location(), peak, highest, base, (System.nanoTime() - start) / 1_000_000);
        return base;
    }
}
