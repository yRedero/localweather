package redero.localweather.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import redero.localweather.server.WeatherSavedData;
import redero.localweather.server.WeatherSimulator;
import redero.localweather.weather.LocalWeatherApi;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    private ServerLevel localweather$self() {
        return (ServerLevel) (Object) this;
    }

    /** The storm simulation replaces vanilla's global rain and thunder timers. */
    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void localweather$simulate(CallbackInfo ci) {
        ServerLevel level = localweather$self();
        if (!LocalWeatherApi.managed(level)) return;
        WeatherSimulator.tick(level);
        ci.cancel();
    }

    /** Lightning: let every chunk try, the thunder check below decides. */
    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isRaining()Z"))
    private boolean localweather$chunkMayRain(ServerLevel level, Operation<Boolean> original) {
        return LocalWeatherApi.managed(level) || original.call(level);
    }

    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isThundering()Z"))
    private boolean localweather$thunderingOverChunk(ServerLevel level, Operation<Boolean> original,
                                                     @Local(argsOnly = true) LevelChunk chunk) {
        if (!LocalWeatherApi.managed(level)) return original.call(level);
        ChunkPos pos = chunk.getPos();
        return WeatherSavedData.get(level).thunderAt(pos.getMiddleBlockX(), pos.getMiddleBlockZ()) > LocalWeatherApi.THUNDER_THRESHOLD;
    }

    /** Snow layers and cauldrons fill only under a storm. Ice still forms in clear weather, as in vanilla. */
    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isRaining()Z"))
    private boolean localweather$precipitatingHere(ServerLevel level, Operation<Boolean> original,
                                                   @Local(argsOnly = true) BlockPos pos) {
        if (!LocalWeatherApi.managed(level)) return original.call(level);
        // Mountain tops above the cloud base get no snow and their cauldrons stay empty.
        if (LocalWeatherApi.aboveCloudBase(level, level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()))) return false;
        return WeatherSavedData.get(level).rainAt(pos.getX() + 0.5, pos.getZ() + 0.5) > LocalWeatherApi.RAIN_THRESHOLD;
    }

    /** Sleeping clears storms over the players instead of the whole dimension. */
    @Inject(method = "resetWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void localweather$clearOverSleepers(CallbackInfo ci) {
        ServerLevel level = localweather$self();
        if (!LocalWeatherApi.managed(level)) return;
        WeatherSimulator.clearOverPlayers(level);
        ci.cancel();
    }

    /** /weather turns into local storms. Vanilla's flags still get set, so removing the mod leaves sane weather. */
    @Inject(method = "setWeatherParameters", at = @At("HEAD"))
    private void localweather$weatherCommand(int clearTime, int weatherTime, boolean isRaining, boolean isThundering, CallbackInfo ci) {
        ServerLevel level = localweather$self();
        if (!LocalWeatherApi.managed(level)) return;
        WeatherSimulator.applyWeatherCommand(level, weatherTime, isRaining, isThundering);
    }
}
