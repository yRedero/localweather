package redero.localweather.platform;

import java.util.ServiceLoader;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** The few things the shared code needs from the mod loader. Each loader provides one through META-INF/services. */
public interface Platform {
    Platform INSTANCE = ServiceLoader.load(Platform.class, Platform.class.getClassLoader()).findFirst()
            .orElseThrow(() -> new IllegalStateException("No Local Weather platform implementation found"));

    /** Works from the earliest point on, including mixin plugins. */
    boolean isModLoaded(String modId);

    /** False when the player's client doesn't have the mod (or an incompatible version). */
    boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);

    void send(ServerPlayer player, CustomPacketPayload payload);
}
