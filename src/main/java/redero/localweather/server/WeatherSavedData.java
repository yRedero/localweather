package redero.localweather.server;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import redero.localweather.weather.StormCell;
import redero.localweather.weather.WeatherView;

/** Storm cells and wind for one dimension, saved with that dimension's data. */
public final class WeatherSavedData extends SavedData implements WeatherView {
    private static final String NAME = "localweather";
    private static final Factory<WeatherSavedData> FACTORY = new Factory<>(WeatherSavedData::new, WeatherSavedData::load);

    final List<StormCell> cells = new ArrayList<>();
    /** Direction the wind blows towards, in radians. */
    float windAngle;
    /** Blocks per tick. */
    float windSpeed = 0.07F;
    private int nextId;

    public static WeatherSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    @Override
    public List<StormCell> cells() {
        return cells;
    }

    public float windX() {
        return (float) Math.cos(windAngle) * windSpeed;
    }

    public float windZ() {
        return (float) Math.sin(windAngle) * windSpeed;
    }

    int nextId() {
        return nextId++;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (StormCell cell : cells) {
            list.add(cell.save());
        }
        tag.put("cells", list);
        tag.putFloat("wind_angle", windAngle);
        tag.putFloat("wind_speed", windSpeed);
        tag.putInt("next_id", nextId);
        return tag;
    }

    private static WeatherSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WeatherSavedData data = new WeatherSavedData();
        for (Tag cell : tag.getList("cells", Tag.TAG_COMPOUND)) {
            data.cells.add(StormCell.load((CompoundTag) cell));
        }
        data.windAngle = tag.getFloat("wind_angle");
        if (tag.contains("wind_speed")) data.windSpeed = tag.getFloat("wind_speed");
        data.nextId = tag.getInt("next_id");
        return data;
    }
}
