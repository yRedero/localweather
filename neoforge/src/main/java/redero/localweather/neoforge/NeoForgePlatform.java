package redero.localweather.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.network.PacketDistributor;
import redero.localweather.platform.Platform;

public final class NeoForgePlatform implements Platform {
    @Override
    public boolean isModLoaded(String modId) {
        // ModList only exists once mods are constructed; mixin plugins run before that.
        ModList mods = ModList.get();
        return mods != null ? mods.isLoaded(modId) : LoadingModList.get().getModFileById(modId) != null;
    }

    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection.hasChannel(type);
    }

    @Override
    public void send(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
