package redero.localweather.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

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
}
