package redero.localweather.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import redero.localweather.client.ClientWeather;

/**
 * Thick cloud hides the sun, moon and stars: vanilla fades them by the rain level, which here is raised to how
 * overcast the weather regions are. Optional (require 0): a mod drawing its own sky just skips it.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @WrapOperation(method = "renderSky", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private float localweather$cloudsHideSky(ClientLevel level, float partialTick, Operation<Float> original) {
        return Math.max(original.call(level, partialTick), ClientWeather.INSTANCE.skyHidden(partialTick));
    }
}
