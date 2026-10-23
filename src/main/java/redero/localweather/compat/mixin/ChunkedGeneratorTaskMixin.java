package redero.localweather.compat.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qendolin.betterclouds.clouds.ChunkedGenerator;
import com.qendolin.betterclouds.clouds.Sampler;
import com.qendolin.betterclouds.config.Config;

import redero.localweather.client.betterclouds.LocalCloudField;

/** Each generator task samples cloudiness per point from the storm cells instead of one global value. */
@Mixin(targets = "com.qendolin.betterclouds.clouds.ChunkedGenerator$Task", remap = false)
public abstract class ChunkedGeneratorTaskMixin {
    @Unique
    @Nullable
    private LocalCloudField localweather$field;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void localweather$captureStorms(int chunkX, int chunkZ, Config options, float distance, float cloudiness,
                                            ChunkedGenerator generator, CallbackInfo ci) {
        localweather$field = LocalCloudField.capture(generator.originX(), generator.originZ(), cloudiness, options.blockDistance());
    }

    @WrapOperation(method = "genSamplePoints", at = @At(value = "INVOKE",
            target = "Lcom/qendolin/betterclouds/clouds/Sampler;sample(IIFFF)F"))
    private float localweather$localCloudiness(Sampler sampler, int x, int z, float cloudiness, float fuzziness, float scale,
                                               Operation<Float> original) {
        LocalCloudField field = localweather$field;
        return original.call(sampler, x, z, field != null ? field.cloudinessAt(x, z) : cloudiness, fuzziness, scale);
    }
}
