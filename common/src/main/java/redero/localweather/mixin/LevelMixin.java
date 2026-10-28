package redero.localweather.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import redero.localweather.weather.LocalWeatherApi;
import redero.localweather.weather.WeatherView;

@Mixin(Level.class)
public abstract class LevelMixin {
    /** isRainingAt asks the storm cells over that column instead of the global rain level. */
    @WrapOperation(method = "isRainingAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isRaining()Z"))
    private boolean localweather$rainingHere(Level level, Operation<Boolean> original, @Local(argsOnly = true) BlockPos pos) {
        WeatherView view = LocalWeatherApi.view(level);
        if (view == null) return original.call(level);
        // Above the cloud base it's dry (server rule; clients get the world's cloud height synced).
        if (level instanceof ServerLevel serverLevel && LocalWeatherApi.aboveCloudBase(serverLevel, pos.getY())) return false;
        return view.rainAt(pos.getX() + 0.5, pos.getZ() + 0.5) > LocalWeatherApi.RAIN_THRESHOLD;
    }

    /** On the client, rain and thunder levels (sky, fog, particles, sound) follow the weather at the camera. */
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void localweather$cameraRain(float partialTick, CallbackInfoReturnable<Float> cir) {
        Level level = (Level) (Object) this;
        if (!level.isClientSide()) return;
        float rain = LocalWeatherApi.client().rainLevel(level, partialTick);
        if (!Float.isNaN(rain)) cir.setReturnValue(rain);
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void localweather$cameraThunder(float partialTick, CallbackInfoReturnable<Float> cir) {
        Level level = (Level) (Object) this;
        if (!level.isClientSide()) return;
        float thunder = LocalWeatherApi.client().thunderLevel(level, partialTick);
        if (!Float.isNaN(thunder)) cir.setReturnValue(thunder);
    }
}
