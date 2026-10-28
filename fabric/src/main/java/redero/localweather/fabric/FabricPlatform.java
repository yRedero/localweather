package redero.localweather.fabric;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import redero.localweather.platform.Platform;

public final class FabricPlatform implements Platform {
    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return ServerPlayNetworking.canSend(player, type);
    }

    @Override
    public void send(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
}
