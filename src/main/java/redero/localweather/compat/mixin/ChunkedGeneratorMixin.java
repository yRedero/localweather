package redero.localweather.compat.mixin;

import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.qendolin.betterclouds.clouds.ChunkedGenerator;
import com.qendolin.betterclouds.clouds.Debug;
import com.qendolin.betterclouds.config.Config;

import redero.localweather.client.ClientWeather;
import redero.localweather.client.betterclouds.BetterCloudsCompat;

@Mixin(value = ChunkedGenerator.class, remap = false)
public abstract class ChunkedGeneratorMixin {
    @Shadow
    private boolean queueCacheClear;
    @Shadow
    private double originX;
    @Shadow
    private double originZ;

    /** Size the cloud buffer for what far clouds can actually hold instead of every grid point. */
    @ModifyReturnValue(method = "calcBufferSize", at = @At("RETURN"))
    private static int localweather$bufferSize(int size, Config options) {
        int doubling = options.bottomSparsity > 0.0F ? 2 : 1;
        return BetterCloudsCompat.bufferSize(options, size / doubling);
    }

    /** Rebuild the clouds, cache included, when storms have visibly moved or changed strength. */
    @Inject(method = "update", at = @At("HEAD"))
    private void localweather$rebuildForStorms(Vector3d camera, long cloudTicks, int rendererTicks, float tickDelta,
                                               Config options, float cloudiness, CallbackInfo ci) {
        if (BetterCloudsCompat.fieldChanged(camera.x, camera.z, originX, originZ, options.blockDistance())) {
            queueCacheClear = true;
            Debug.generatorForceUpdate = true;
        }
    }

    /**
     * Better Clouds moves its clouds along its own random path. With local weather the clouds ride the storm
     * wind instead, so storms keep their place among the clouds and don't need rebuilding as they travel.
     */
    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lcom/qendolin/betterclouds/clouds/RandomPath;getPathX(JFD)D"))
    private double localweather$windPathX(long cloudTicks, float delta, double speed, Operation<Double> original,
                                          @Local(argsOnly = true, ordinal = 0) float tickDelta) {
        ClientWeather weather = ClientWeather.INSTANCE;
        return weather.active() ? weather.windOffsetX(tickDelta) : original.call(cloudTicks, delta, speed);
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lcom/qendolin/betterclouds/clouds/RandomPath;getPathZ(JFD)D"))
    private double localweather$windPathZ(long cloudTicks, float delta, double speed, Operation<Double> original,
                                          @Local(argsOnly = true, ordinal = 0) float tickDelta) {
        ClientWeather weather = ClientWeather.INSTANCE;
        return weather.active() ? weather.windOffsetZ(tickDelta) : original.call(cloudTicks, delta, speed);
    }
}
