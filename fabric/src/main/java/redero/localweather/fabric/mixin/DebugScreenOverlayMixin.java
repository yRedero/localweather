package redero.localweather.fabric.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import redero.localweather.client.LocalWeatherClient;

/** Adds the Local Weather lines to the left side of the F3 screen (NeoForge has an event for this). */
@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    @ModifyReturnValue(method = "getGameInformation", at = @At("RETURN"))
    private List<String> localweather$addWeather(List<String> lines) {
        LocalWeatherClient.addDebugText(lines);
        return lines;
    }
}
