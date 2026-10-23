package redero.localweather.compat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.qendolin.betterclouds.clouds.Renderer;

import redero.localweather.client.betterclouds.BetterCloudsCompat;

@Mixin(value = Renderer.class, remap = false)
public abstract class RendererMixin {
    /**
     * Better Clouds culls cloud chunks with vanilla's frustum, which ends at the camera's far plane (4x the
     * render distance). Far clouds live beyond it, so skip chunk culling; the GPU still drops what's off screen.
     */
    @ModifyExpressionValue(method = "drawCoverage", at = @At(value = "FIELD",
            target = "Lcom/qendolin/betterclouds/config/Config;useFrustumCulling:Z"))
    private boolean localweather$keepFarChunks(boolean useFrustumCulling) {
        return useFrustumCulling && !BetterCloudsCompat.farCloudsActive();
    }
}
