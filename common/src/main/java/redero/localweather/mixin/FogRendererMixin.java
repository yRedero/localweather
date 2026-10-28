package redero.localweather.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import redero.localweather.client.ClientWeather;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    /** Fog colour follows the sky's slow, averaged weather, like the sky itself (see ClientLevelMixin). */
    @WrapOperation(method = "setupColor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private static float localweather$skyRain(ClientLevel level, float partialTick, Operation<Float> original) {
        float rain = ClientWeather.INSTANCE.skyRainLevel(partialTick);
        return Float.isNaN(rain) ? original.call(level, partialTick) : rain;
    }

    @WrapOperation(method = "setupColor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getThunderLevel(F)F"))
    private static float localweather$skyThunder(ClientLevel level, float partialTick, Operation<Float> original) {
        float thunder = ClientWeather.INSTANCE.skyThunderLevel(partialTick);
        return Float.isNaN(thunder) ? original.call(level, partialTick) : thunder;
    }

    /** Fog greys over with the sky, so the horizon matches it. */
    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void localweather$gloomyFog(Camera camera, float partialTick, ClientLevel level, int renderDistance,
                                               float bossColorModifier, CallbackInfo ci) {
        if (camera.getFluidInCamera() != FogType.NONE) return;
        float gloom = ClientWeather.INSTANCE.gloom(partialTick);
        if (gloom <= 0.0F) return;
        Vec3 fog = ClientWeather.gloomy(new Vec3(fogRed, fogGreen, fogBlue), gloom);
        fogRed = (float) fog.x;
        fogGreen = (float) fog.y;
        fogBlue = (float) fog.z;
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
    }
}
