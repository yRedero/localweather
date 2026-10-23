package redero.localweather.compat.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.qendolin.betterclouds.clouds.Buffer;
import com.qendolin.betterclouds.clouds.ChunkedGenerator;
import com.qendolin.betterclouds.clouds.Sampler;
import com.qendolin.betterclouds.config.Config;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.phys.AABB;
import redero.localweather.LocalWeather;
import redero.localweather.client.betterclouds.CloudGenContext;
import redero.localweather.server.DevScene;

/**
 * Hooks Better Clouds' generator task: local cloudiness per point, level of detail, stacked storm clouds and
 * the high layer. All decisions live in {@link CloudGenContext}; this class only forwards.
 */
@Mixin(targets = "com.qendolin.betterclouds.clouds.ChunkedGenerator$Task", remap = false)
public abstract class ChunkedGeneratorTaskMixin {
    @Unique
    @Nullable
    private CloudGenContext localweather$context;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void localweather$capture(int chunkX, int chunkZ, Config options, float distance, float cloudiness,
                                      ChunkedGenerator generator, CallbackInfo ci) {
        localweather$context = CloudGenContext.capture(generator.originX(), generator.originZ(), options, cloudiness);
    }

    @Inject(method = "run", at = @At("RETURN"))
    private void localweather$devStats(CallbackInfo ci) {
        if (DevScene.ENABLED && localweather$context != null) {
            LocalWeather.LOGGER.info("Dev scene clouds: {}", localweather$context.stats());
        }
    }

    /** Cached chunks would keep the level of detail and storms they were generated with. */
    @ModifyExpressionValue(method = "run", at = @At(value = "FIELD",
            target = "Lcom/qendolin/betterclouds/config/Config;useSamplerCaching:Z"))
    private boolean localweather$noCache(boolean useSamplerCaching) {
        return useSamplerCaching && localweather$context == null;
    }

    @WrapOperation(method = "genSamplePoints", at = @At(value = "INVOKE",
            target = "Lcom/qendolin/betterclouds/clouds/Sampler;sample(IIFFF)F"))
    private float localweather$sample(Sampler sampler, int x, int z, float cloudiness, float fuzziness, float scale,
                                      Operation<Float> original) {
        CloudGenContext context = localweather$context;
        if (context == null) return original.call(sampler, x, z, cloudiness, fuzziness, scale);
        return context.sample(x, z, cloudiness, c -> original.call(sampler, x, z, c, fuzziness, scale));
    }

    @WrapOperation(method = "genSamplePoints", at = @At(value = "NEW", target = "net/minecraft/world/phys/AABB"))
    private AABB localweather$point(double x1, double y1, double z1, double x2, double y2, double z2,
                                    Operation<AABB> original) {
        CloudGenContext context = localweather$context;
        if (context == null) return original.call(x1, y1, z1, x2, y2, z2);
        return context.point(x1, y1, z1);
    }

    @WrapOperation(method = "genSamplePoints", at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/objects/ObjectArrayList;add(Ljava/lang/Object;)Z"))
    private boolean localweather$addStacked(ObjectArrayList<Object> points, Object point, Operation<Boolean> original) {
        boolean added = original.call(points, point);
        CloudGenContext context = localweather$context;
        if (context != null) context.flushExtras(extra -> original.call(points, extra));
        return added;
    }

    /** Uploads the encoded y, and never writes past the end of Better Clouds' buffer. */
    @WrapOperation(method = "run", at = @At(value = "INVOKE",
            target = "Lcom/qendolin/betterclouds/clouds/Buffer;put(FFF)V"))
    private void localweather$upload(Buffer buffer, float x, float y, float z, Operation<Void> original,
                                     @Local AABB point) {
        if (localweather$context == null) {
            original.call(buffer, x, y, z);
            return;
        }
        if (((BufferAccessor) buffer).localweather$writeBuffer().remaining() < 3) return;
        original.call(buffer, x, CloudGenContext.uploadY(point), z);
    }
}
