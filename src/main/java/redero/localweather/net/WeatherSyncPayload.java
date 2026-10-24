package redero.localweather.net;

import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import redero.localweather.LocalWeather;
import redero.localweather.weather.StormCell;

/**
 * Every storm cell near one player, sent once a second. The cells are copies, because singleplayer
 * hands payload objects to the client without encoding them.
 */
public record WeatherSyncPayload(float windX, float windZ, int cloudHeightOffset, List<StormCell> cells)
        implements CustomPacketPayload {
    public static final Type<WeatherSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LocalWeather.MODID, "weather_sync"));

    public static final StreamCodec<FriendlyByteBuf, WeatherSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, WeatherSyncPayload::windX,
            ByteBufCodecs.FLOAT, WeatherSyncPayload::windZ,
            ByteBufCodecs.VAR_INT, WeatherSyncPayload::cloudHeightOffset,
            StreamCodec.<FriendlyByteBuf, StormCell>of((buf, cell) -> cell.write(buf), StormCell::read)
                    .apply(ByteBufCodecs.list(256)), WeatherSyncPayload::cells,
            WeatherSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
