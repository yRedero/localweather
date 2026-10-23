package redero.localweather.server;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import redero.localweather.weather.CellType;
import redero.localweather.weather.LocalWeatherApi;
import redero.localweather.weather.StormCell;

/** /localweather list | here | clear | spawn <type> [radius] */
public final class LocalWeatherCommand {
    private static final SimpleCommandExceptionType UNMANAGED =
            new SimpleCommandExceptionType(Component.literal("This dimension has no local weather"));
    private static final SimpleCommandExceptionType UNKNOWN_TYPE =
            new SimpleCommandExceptionType(Component.literal("Unknown storm type"));

    private LocalWeatherCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("localweather")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(LocalWeatherCommand::list))
                .then(Commands.literal("here").executes(LocalWeatherCommand::here))
                .then(Commands.literal("clear").executes(LocalWeatherCommand::clear))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(CellType.values()).map(t -> t.name().toLowerCase(Locale.ROOT)), builder))
                                .executes(ctx -> spawn(ctx, -1))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(50, 4000))
                                        .executes(ctx -> spawn(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))));
    }

    private static WeatherSavedData data(CommandSourceStack source) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        if (!LocalWeatherApi.managed(level)) throw UNMANAGED.create();
        return WeatherSavedData.get(level);
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        WeatherSavedData data = data(source);
        if (data.cells.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.localweather.none"), false);
            return 0;
        }
        Vec3 pos = source.getPosition();
        List<StormCell> sorted = data.cells.stream()
                .sorted(Comparator.comparingDouble(c -> WeatherSimulator.horizontalDistance(c, pos.x, pos.z)))
                .toList();
        for (StormCell cell : sorted) {
            String line = String.format(Locale.ROOT, "#%d %s %s, %.0f blocks away, radius %.0f, rain %.2f, thunder %.2f, %d/%d s",
                    cell.id, cell.type, cell.stage(), WeatherSimulator.horizontalDistance(cell, pos.x, pos.z),
                    cell.radius, cell.intensity, cell.thunder, cell.age / 20, cell.lifetime / 20);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return sorted.size();
    }

    private static int here(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        WeatherSavedData data = data(source);
        Vec3 pos = source.getPosition();
        String rain = String.format(Locale.ROOT, "%.2f", data.rainAt(pos.x, pos.z));
        String thunder = String.format(Locale.ROOT, "%.2f", data.thunderAt(pos.x, pos.z));
        source.sendSuccess(() -> Component.translatable("commands.localweather.here", rain, thunder), false);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        WeatherSavedData data = data(source);
        int count = data.cells.size();
        data.cells.clear();
        data.setDirty();
        WeatherSimulator.syncAll(source.getLevel());
        source.sendSuccess(() -> Component.translatable("commands.localweather.cleared", count), true);
        return count;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        WeatherSavedData data = data(source);
        CellType type;
        try {
            type = CellType.valueOf(StringArgumentType.getString(ctx, "type").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw UNKNOWN_TYPE.create();
        }
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        float r = radius > 0 ? radius : type.rollRadius(level.random);
        StormCell cell = WeatherSimulator.spawn(data, type, pos.x, pos.z, r, level.random);
        WeatherSimulator.skipForming(cell);
        WeatherSimulator.syncAll(level);
        source.sendSuccess(() -> Component.translatable("commands.localweather.spawned",
                type.name().toLowerCase(Locale.ROOT), (int) r, (int) pos.x, (int) pos.z), true);
        return cell.id;
    }
}
