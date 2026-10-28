package redero.localweather.compat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.qendolin.betterclouds.config.Config;

import redero.localweather.client.LocalWeatherClientConfig;
import redero.localweather.client.betterclouds.BetterCloudsCompat;

@Mixin(value = Config.class, remap = false)
public abstract class ConfigMixin {
    /**
     * Better Clouds caps clouds at 4x the render distance. Its renderer already draws past the camera's far
     * plane (depth clamp), so only the generation and fog distance need to grow. Far clouds need the shader
     * patch for their bigger size, otherwise they would be a sparse dotted ring.
     */
    @ModifyReturnValue(method = "blockDistance", at = @At("RETURN"))
    private int localweather$farClouds(int distance) {
        if (!BetterCloudsCompat.farCloudsActive()) return distance;
        return Math.max(distance, LocalWeatherClientConfig.CLOUD_DISTANCE.get());
    }
}
