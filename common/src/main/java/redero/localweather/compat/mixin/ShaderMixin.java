package redero.localweather.compat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.qendolin.betterclouds.clouds.shaders.Shader;
import com.qendolin.betterclouds.compat.GLCompat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import redero.localweather.client.betterclouds.BetterCloudsShaderPatch;

@Mixin(value = Shader.class, remap = false)
public abstract class ShaderMixin {
    @WrapOperation(method = "compileShader", at = @At(value = "INVOKE",
            target = "Lcom/qendolin/betterclouds/compat/GLCompat;shaderSource(ILjava/lang/String;)V"))
    private void localweather$patchSource(GLCompat glCompat, int id, String source, Operation<Void> original,
                                          @Local(argsOnly = true) ResourceLocation resource,
                                          @Local(argsOnly = true) ResourceManager resources) {
        original.call(glCompat, id, BetterCloudsShaderPatch.patch(resource, source, resources));
    }
}
