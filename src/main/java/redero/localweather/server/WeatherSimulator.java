package redero.localweather.server;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.network.PacketDistributor;
import redero.localweather.net.WeatherSyncPayload;
import redero.localweather.weather.CellType;
import redero.localweather.weather.LocalWeatherApi;
import redero.localweather.weather.StormCell;

/** Moves, ages, spawns and syncs storm cells. Runs in place of vanilla's weather cycle. */
public final class WeatherSimulator {
    /** Cells move and age in steps of this many ticks. */
    static final int STEP = 20;
    /** Cells whose weather system is further than this from every player are dropped. */
    private static final double DESPAWN_RANGE = 6000.0;
    /** Players are told about cells whose weather system comes within this range. */
    private static final double SYNC_RANGE = 6000.0;
    /** A player gets no new cells while LocalWeatherServerConfig#maxStormsNearby are already within this range. */
    private static final double CROWD_RANGE = 9000.0;
    /** At storm frequency 1, on average one spawn attempt per player every this many steps. */
    private static final int SPAWN_ODDS = 150;

    private WeatherSimulator() {
    }

    /** Called every tick from ServerLevel#advanceWeatherCycle. */
    public static void tick(ServerLevel level) {
        WeatherSavedData data = WeatherSavedData.get(level);
        if (level.getGameTime() % STEP == 0) {
            if (level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE) && !level.players().isEmpty()) {
                step(level, data);
            }
            for (ServerPlayer player : level.players()) {
                sync(player, data);
            }
        }
        updateGlobalLevels(level, data);
    }

    private static void step(ServerLevel level, WeatherSavedData data) {
        RandomSource random = level.random;
        data.windAngle += (float) random.nextGaussian() * 0.02F;
        data.windSpeed = Mth.clamp(data.windSpeed + (float) random.nextGaussian() * 0.002F,
                LocalWeatherServerConfig.windMin(), LocalWeatherServerConfig.windMax());
        float windX = data.windX();
        float windZ = data.windZ();

        for (StormCell cell : data.cells) {
            cell.vx = windX * cell.windFactor;
            cell.vz = windZ * cell.windFactor;
            cell.x += cell.vx * STEP;
            cell.z += cell.vz * STEP;
            cell.age += STEP;
        }
        data.cells.removeIf(cell -> cell.age >= cell.lifetime || farFromPlayers(level, cell));

        for (ServerPlayer player : level.players()) {
            if (random.nextDouble() * SPAWN_ODDS < LocalWeatherServerConfig.stormFrequency()
                    && countNear(data, player.getX(), player.getZ()) < LocalWeatherServerConfig.maxStormsNearby()) {
                trySpawnUpwind(level, data, player, random);
            }
        }
        data.setDirty();
    }

    /**
     * New cells form upwind of players with their rain still out of sight, scaled to their size, so they drift
     * in instead of popping up overhead. Their outer clouds may already reach the player while they form.
     */
    private static void trySpawnUpwind(ServerLevel level, WeatherSavedData data, ServerPlayer player, RandomSource random) {
        double roll = random.nextDouble();
        double shower = LocalWeatherServerConfig.showerChance();
        CellType type = roll < shower ? CellType.SHOWER
                : roll < shower + LocalWeatherServerConfig.thunderstormChance() ? CellType.THUNDERSTORM
                : CellType.RAIN;
        float radius = rollRadius(type, random);

        float dirX = (float) Math.cos(data.windAngle);
        float dirZ = (float) Math.sin(data.windAngle);
        double upwind = 600.0 + radius * (1.1 + 0.9 * random.nextDouble());
        double sideways = (random.nextDouble() - 0.5) * (2000.0 + 3.0 * radius);
        double x = player.getX() - dirX * upwind - dirZ * sideways;
        double z = player.getZ() - dirZ * upwind + dirX * sideways;

        Biome biome = level.getBiome(BlockPos.containing(x, level.getSeaLevel(), z)).value();
        if (random.nextFloat() > spawnWeight(biome)) return;
        if (type == CellType.THUNDERSTORM && biome.getBaseTemperature() < 0.5F) {
            type = CellType.RAIN;
            radius = rollRadius(type, random);
        }
        spawn(data, type, x, z, radius, random);
    }

    /** Wet biomes and oceans grow storms, dry ones rarely do. */
    private static float spawnWeight(Biome biome) {
        if (!biome.hasPrecipitation()) return 0.05F;
        float downfall = biome.modifiableBiomeInfo().get().climateSettings().downfall();
        return 0.35F + 0.65F * Mth.clamp(downfall, 0.0F, 1.0F);
    }

    /** A radius for a new storm of this type, scaled by the server's storm size setting. */
    public static float rollRadius(CellType type, RandomSource random) {
        return (float) (type.rollRadius(random) * LocalWeatherServerConfig.stormSize());
    }

    public static StormCell spawn(WeatherSavedData data, CellType type, double x, double z, float radius, RandomSource random) {
        StormCell cell = new StormCell(data.nextId(), type, x, z, radius, type.rollIntensity(random),
                // Storms ride the wind closely: clients drift the clouds with the same wind, so storms stay put among them.
                type.rollThunder(random), 0.95F + random.nextFloat() * 0.1F,
                Math.max(20, (int) (type.rollLifetime(random) * LocalWeatherServerConfig.stormLifetime())));
        cell.vx = data.windX() * cell.windFactor;
        cell.vz = data.windZ() * cell.windFactor;
        data.cells.add(cell);
        data.setDirty();
        return cell;
    }

    /**
     * Commands put storms right on top of players, so they start at full strength. Natural cells form far
     * upwind and have minutes to build up before they arrive.
     */
    public static void skipForming(StormCell cell) {
        cell.age = cell.lifetime * 15 / 100;
    }

    /** /weather rain|thunder puts a cell on every player; /weather clear removes all cells. */
    public static void applyWeatherCommand(ServerLevel level, int duration, boolean raining, boolean thundering) {
        WeatherSavedData data = WeatherSavedData.get(level);
        if (!raining) {
            data.cells.clear();
        } else {
            CellType type = thundering ? CellType.THUNDERSTORM : CellType.RAIN;
            for (ServerPlayer player : level.players()) {
                StormCell cell = spawn(data, type, player.getX(), player.getZ(), rollRadius(type, level.random), level.random);
                skipForming(cell);
                if (duration > 0) cell.lifetime = cell.age + duration;
            }
        }
        data.setDirty();
        syncAll(level);
    }

    /** Sleeping through the night clears the storms over the sleepers, not the whole world. */
    public static void clearOverPlayers(ServerLevel level) {
        WeatherSavedData data = WeatherSavedData.get(level);
        data.cells.removeIf(cell -> {
            for (ServerPlayer player : level.players()) {
                if (cell.rainAt(player.getX(), player.getZ(), 0) > 0.0F) return true;
            }
            return false;
        });
        data.setDirty();
        syncAll(level);
    }

    /**
     * Vanilla's global rain level still matters to code that only calls isRaining(), and to clients
     * without the mod. It follows the wettest player in the dimension.
     */
    private static void updateGlobalLevels(ServerLevel level, WeatherSavedData data) {
        float rain = 0.0F;
        float thunder = 0.0F;
        for (ServerPlayer player : level.players()) {
            rain = Math.max(rain, data.rainAt(player.getX(), player.getZ()));
            thunder = Math.max(thunder, data.thunderAt(player.getX(), player.getZ()));
        }
        boolean wasRaining = level.isRaining();
        float oldRain = level.rainLevel;
        float oldThunder = level.thunderLevel;
        level.setRainLevel(approach(oldRain, rain, 0.01F));
        level.setThunderLevel(approach(oldThunder, thunder, 0.01F));

        var players = level.getServer().getPlayerList();
        if (wasRaining != level.isRaining()) {
            players.broadcastAll(new ClientboundGameEventPacket(
                    wasRaining ? ClientboundGameEventPacket.STOP_RAINING : ClientboundGameEventPacket.START_RAINING, 0.0F), level.dimension());
        }
        if (oldRain != level.rainLevel) {
            players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level.rainLevel), level.dimension());
        }
        if (oldThunder != level.thunderLevel) {
            players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.thunderLevel), level.dimension());
        }
    }

    public static void syncAll(ServerLevel level) {
        WeatherSavedData data = WeatherSavedData.get(level);
        for (ServerPlayer player : level.players()) {
            sync(player, data);
        }
    }

    public static void sync(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level && LocalWeatherApi.managed(level)) {
            sync(player, WeatherSavedData.get(level));
        }
    }

    private static void sync(ServerPlayer player, WeatherSavedData data) {
        if (!player.connection.hasChannel(WeatherSyncPayload.TYPE)) return;
        // With the weather cycle off cells stand still; a velocity would make clients drift them and snap back.
        boolean moving = player.serverLevel().getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);
        List<StormCell> near = new ArrayList<>();
        for (StormCell cell : data.cells) {
            if (horizontalDistance(cell, player.getX(), player.getZ()) < SYNC_RANGE + cell.radius * CellType.SYSTEM_REACH) {
                StormCell copy = cell.copy();
                if (!moving) {
                    copy.vx = 0.0F;
                    copy.vz = 0.0F;
                }
                near.add(copy);
            }
        }
        PacketDistributor.sendToPlayer(player, new WeatherSyncPayload(moving ? data.windX() : 0.0F, moving ? data.windZ() : 0.0F,
                LocalWeatherServerConfig.cloudHeightOffset(), near));
    }

    private static boolean farFromPlayers(ServerLevel level, StormCell cell) {
        for (ServerPlayer player : level.players()) {
            if (horizontalDistance(cell, player.getX(), player.getZ()) < DESPAWN_RANGE + cell.radius * CellType.SYSTEM_REACH) return false;
        }
        return true;
    }

    private static int countNear(WeatherSavedData data, double x, double z) {
        int count = 0;
        for (StormCell cell : data.cells) {
            if (horizontalDistance(cell, x, z) < CROWD_RANGE + cell.radius) count++;
        }
        return count;
    }

    static double horizontalDistance(StormCell cell, double x, double z) {
        return Math.sqrt(Mth.square(cell.x - x) + Mth.square(cell.z - z));
    }

    private static float approach(float from, float to, float maxStep) {
        return from + Mth.clamp(to - from, -maxStep, maxStep);
    }
}
