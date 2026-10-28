package redero.localweather.compat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qendolin.betterclouds.clouds.EffectTintProvider;

import net.minecraft.client.multiplayer.ClientLevel;
import redero.localweather.client.ClientWeather;
import redero.localweather.client.LocalWeatherClientConfig;
import redero.localweather.client.betterclouds.BetterCloudsShaderPatch;

/**
 * Better Clouds greys and darkens every cloud by the rain and thunder at the camera. Storm clouds are
 * darkened one by one now, so under a storm the whole sky would turn dark, clear-weather clouds included.
 * Keep a little of it: a storm overhead still dims the light.
 */
@Mixin(value = EffectTintProvider.class, remap = false)
public abstract class EffectTintProviderMixin {
    @WrapOperation(method = "getCloudsColor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private static float localweather$softRain(ClientLevel level, float partialTick, Operation<Float> original) {
        float rain = original.call(level, partialTick);
        return perCloudDarkness() ? rain * keep() : rain;
    }

    @WrapOperation(method = "getCloudsColor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getThunderLevel(F)F"))
    private static float localweather$softThunder(ClientLevel level, float partialTick, Operation<Float> original) {
        float thunder = original.call(level, partialTick);
        return perCloudDarkness() ? thunder * keep() : thunder;
    }

    private static float keep() {
        return LocalWeatherClientConfig.GLOBAL_TINT.get().floatValue();
    }

    private static boolean perCloudDarkness() {
        return ClientWeather.INSTANCE.active() && BetterCloudsShaderPatch.active()
                && LocalWeatherClientConfig.SPEC.isLoaded() && LocalWeatherClientConfig.STORM_DARKNESS.get();
    }
}
