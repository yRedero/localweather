package redero.localweather.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.world.entity.LightningBolt;
import redero.localweather.client.RainShafts;

/** Real lightning is drawn as Local Weather's bolt (see LightningFlashes#bolt), so vanilla's is skipped then. */
@Mixin(LightningBoltRenderer.class)
public abstract class LightningBoltRendererMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/LightningBolt;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void localweather$ownBolt(LightningBolt bolt, float yaw, float partialTick, PoseStack poseStack,
                                      MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (RainShafts.drawsBolts()) ci.cancel();
    }
}
