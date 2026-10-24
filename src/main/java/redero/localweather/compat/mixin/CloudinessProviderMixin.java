package redero.localweather.compat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.qendolin.betterclouds.clouds.CloudinessProvider;

import net.minecraft.client.multiplayer.ClientLevel;
import redero.localweather.client.ClientWeather;
import redero.localweather.weather.LocalWeatherApi;

/**
 * Better Clouds raises one global cloudiness with the rain at the camera, and rebuilds every cloud each time it
 * moves by 1%. With local weather every cloud gets its own cloudiness from the storms, so keep the weather out of
 * the global value: it then only carries season factors and stays put, instead of popping clouds in and out
 * whenever the camera's rain fades.
 */
@Mixin(value = CloudinessProvider.class, remap = false)
public abstract class CloudinessProviderMixin {
    @ModifyReturnValue(method = "getTrueRainGradient", at = @At("RETURN"))
    private static float localweather$noGlobalRain(float rain, ClientLevel level, float tickDelta) {
        return localWeather(level) ? 0.0F : rain;
    }

    @ModifyReturnValue(method = "getTrueThunderGradient", at = @At("RETURN"))
    private static float localweather$noGlobalThunder(float thunder, ClientLevel level, float tickDelta) {
        return localWeather(level) ? 0.0F : thunder;
    }

    private static boolean localWeather(ClientLevel level) {
        return level != null && ClientWeather.INSTANCE.active() && LocalWeatherApi.managed(level);
    }
}
