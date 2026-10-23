package redero.localweather.compat.mixin;

import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.qendolin.betterclouds.clouds.ChunkedGenerator;
import com.qendolin.betterclouds.clouds.Debug;
import com.qendolin.betterclouds.config.Config;

import redero.localweather.client.betterclouds.BetterCloudsCompat;

@Mixin(value = ChunkedGenerator.class, remap = false)
public abstract class ChunkedGeneratorMixin {
    @Shadow
    private boolean queueCacheClear;
    @Shadow
    private double originX;
    @Shadow
    private double originZ;

    /** Rebuild the clouds, cache included, when storms have visibly moved or changed strength. */
    @Inject(method = "update", at = @At("HEAD"))
    private void localweather$rebuildForStorms(Vector3d camera, long cloudTicks, int rendererTicks, float tickDelta,
                                               Config options, float cloudiness, CallbackInfo ci) {
        if (BetterCloudsCompat.fieldChanged(camera.x, camera.z, originX, originZ, options.blockDistance())) {
            queueCacheClear = true;
            Debug.generatorForceUpdate = true;
        }
    }
}
