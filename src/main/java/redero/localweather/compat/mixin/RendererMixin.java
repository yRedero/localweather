package redero.localweather.compat.mixin;

import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qendolin.betterclouds.clouds.Renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import redero.localweather.client.LocalWeatherClientConfig;
import redero.localweather.client.betterclouds.BetterCloudsCompat;
import redero.localweather.client.betterclouds.BetterCloudsShaderPatch;

@Mixin(value = Renderer.class, remap = false)
public abstract class RendererMixin {
    /** Lifts every cloud by the configured offset, e.g. above Tectonic's mountains. */
    @WrapOperation(method = "prepare", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getCloudHeight()F"))
    private float localweather$cloudHeight(DimensionSpecialEffects effects, Operation<Float> original) {
        float height = original.call(effects);
        if (Float.isNaN(height) || !LocalWeatherClientConfig.SPEC.isLoaded()) return height;
        return height + LocalWeatherClientConfig.CLOUD_HEIGHT_OFFSET.get();
    }

    /** Our clouds can be up to 4x Better Clouds' size, so chunk boxes need this much extra margin. */
    private static final double CLOUD_MARGIN = 32.0;

    /**
     * Better Clouds culls cloud chunks with vanilla's frustum, which also cuts everything past the camera's far
     * plane (4x the render distance), where far clouds live. Its side planes all meet at the camera, so pulling
     * a box towards the camera doesn't change whether it's inside them: far boxes are scaled in before testing.
     */
    @WrapOperation(method = "drawCloudsWithFrustumCulling", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/culling/Frustum;isVisible(Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean localweather$cullWithoutFarPlane(Frustum frustum, AABB box, Operation<Boolean> original) {
        if (!BetterCloudsCompat.farCloudsActive()) return original.call(frustum, box);
        FrustumAccessor camera = (FrustumAccessor) frustum;
        double cx = camera.localweather$camX();
        double cy = camera.localweather$camY();
        double cz = camera.localweather$camZ();
        box = box.inflate(CLOUD_MARGIN);
        double dx = Math.max(Math.abs(box.minX - cx), Math.abs(box.maxX - cx));
        double dy = Math.max(Math.abs(box.minY - cy), Math.abs(box.maxY - cy));
        double dz = Math.max(Math.abs(box.minZ - cz), Math.abs(box.maxZ - cz));
        double farthest = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double limit = 0.5 * Minecraft.getInstance().gameRenderer.getDepthFar();
        if (farthest > limit) {
            double s = limit / farthest;
            box = new AABB(cx + (box.minX - cx) * s, cy + (box.minY - cy) * s, cz + (box.minZ - cz) * s,
                    cx + (box.maxX - cx) * s, cy + (box.maxY - cy) * s, cz + (box.maxZ - cz) * s);
        }
        return original.call(frustum, box);
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
