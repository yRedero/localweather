package redero.localweather.server;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import redero.localweather.weather.StormCell;
import redero.localweather.weather.WeatherView;

/** Storm cells and wind for one dimension, saved with that dimension's data. */
public final class WeatherSavedData extends SavedData implements WeatherView {
    private static final String NAME = "localweather";
    // Vanilla always runs a data fixer over saved data (only NeoForge accepts none). Command storage's fixer leaves
    // unknown tags like ours alone.
    private static final Factory<WeatherSavedData> FACTORY = new Factory<>(WeatherSavedData::new, WeatherSavedData::load,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    final List<StormCell> cells = new ArrayList<>();
    /** Direction the wind blows towards, in radians. */
    float windAngle;
    /** Blocks per tick. */
    float windSpeed = 0.07F;
    private int nextId;
    /** AutoCloudHeight's result and the build height it was worked out for; MIN_VALUE until then. */
    int autoCloudBase = Integer.MIN_VALUE;
    int autoBuildHeight;

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
        if (autoCloudBase != Integer.MIN_VALUE) {
            tag.putInt("auto_cloud_base", autoCloudBase);
            tag.putInt("auto_build_height", autoBuildHeight);
        }
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
        if (tag.contains("auto_cloud_base")) {
            data.autoCloudBase = tag.getInt("auto_cloud_base");
            data.autoBuildHeight = tag.getInt("auto_build_height");
        }
        return data;
    }
}
