package redero.localweather.compat.mixin;

import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.qendolin.betterclouds.clouds.Renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import redero.localweather.client.betterclouds.BetterCloudsCompat;
import redero.localweather.client.betterclouds.BetterCloudsShaderPatch;

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

    /**
     * The patched shading shader turns the clouds' far-range depth back into the game's depth when it copies
     * it to the main framebuffer, and needs the game's near and far plane for that. Better Clouds declares
     * u_depth_range but never sets it.
     */
    @Inject(method = "drawShading", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL32;glDrawArrays(III)V"))
    private void localweather$depthRange(CallbackInfo ci) {
        if (!BetterCloudsShaderPatch.active()) return;
        int program = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int location = GL20.glGetUniformLocation(program, "u_depth_range");
        if (location >= 0) {
            GL20.glUniform2f(location, GameRenderer.PROJECTION_Z_NEAR, Minecraft.getInstance().gameRenderer.getDepthFar());
        }
    }
}
