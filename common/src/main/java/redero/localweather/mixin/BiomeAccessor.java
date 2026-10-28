package redero.localweather.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.biome.Biome;

/** The biome's own climate (vanilla keeps it private), for how readily storms form over it. */
@Mixin(Biome.class)
public interface BiomeAccessor {
    @Accessor("climateSettings")
    Biome.ClimateSettings localweather$climateSettings();
}
