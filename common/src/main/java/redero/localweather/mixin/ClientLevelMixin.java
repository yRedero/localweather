package redero.localweather.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import redero.localweather.client.ClientWeather;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    /** The sky greys over as a storm's clouds build up, before any rain falls. */
    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private Vec3 localweather$gloomySky(Vec3 color, Vec3 pos, float partialTick) {
        return ClientWeather.gloomy(color, ClientWeather.INSTANCE.gloom(partialTick));
    }

    /**
     * Sky colour, light level and cloud colour use the sky's slow, averaged weather instead of the rain at the
     * camera, so passing under a small dark cloud doesn't make them flicker.
     */
    @WrapOperation(method = {"getSkyDarken", "getSkyColor", "getCloudColor"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private float localweather$skyRain(ClientLevel level, float partialTick, Operation<Float> original) {
        float rain = ClientWeather.INSTANCE.skyRainLevel(partialTick);
        return Float.isNaN(rain) ? original.call(level, partialTick) : rain;
    }

    @WrapOperation(method = {"getSkyDarken", "getSkyColor", "getCloudColor"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getThunderLevel(F)F"))
    private float localweather$skyThunder(ClientLevel level, float partialTick, Operation<Float> original) {
        float thunder = ClientWeather.INSTANCE.skyThunderLevel(partialTick);
        return Float.isNaN(thunder) ? original.call(level, partialTick) : thunder;
    }
}
