package redero.localweather.compat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.culling.Frustum;

@Mixin(Frustum.class)
public interface FrustumAccessor {
    @Accessor("camX")
    double localweather$camX();

    @Accessor("camY")
    double localweather$camY();

    @Accessor("camZ")
    double localweather$camZ();
}
