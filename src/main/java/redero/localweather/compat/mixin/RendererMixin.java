package redero.localweather.compat.mixin;

import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.qendolin.betterclouds.clouds.Renderer;
import com.qendolin.betterclouds.clouds.Resources;
import com.qendolin.betterclouds.clouds.fog.FogProvider;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import redero.localweather.client.ClientWeather;
import redero.localweather.client.LocalWeatherClientConfig;
import redero.localweather.client.betterclouds.BetterCloudsCompat;
import redero.localweather.client.betterclouds.BetterCloudsShaderPatch;

@Mixin(value = Renderer.class, remap = false)
public abstract class RendererMixin {
    /** The patched coverage shader needs the game's near and far plane to tell what terrain hides a cloud. */
    @Inject(method = {"drawCloudsWithFrustumCulling", "drawCloudsWithoutFrustumCulling"}, at = @At("HEAD"))
    private void localweather$coverageDepthRange(CallbackInfo ci) {
        setDepthRange("lw_depth_range");
        setResolution();
    }

    /**
     * With a lower cloud resolution Better Clouds leaves the viewport at its smaller cloud target for the
     * full-screen shading pass, which then only covers part of the screen. Draw it over the whole screen; the
     * patched shader reads the cloud textures at scaled coordinates.
     */
    @Inject(method = "drawShading", at = @At("HEAD"))
    private void localweather$fullScreenShading(float tickDelta, FogProvider.Fog fog, CallbackInfo ci) {
        if (!BetterCloudsShaderPatch.active()) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        Resources res = ((Renderer) (Object) this).resources();
        if (res.fboWidth() != main.width || res.fboHeight() != main.height) {
            GlStateManager._viewport(0, 0, main.width, main.height);
        }
    }

    private void setResolution() {
        if (!BetterCloudsShaderPatch.active()) return;
        int program = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int location = GL20.glGetUniformLocation(program, "lw_resolution");
        if (location < 0) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        Resources res = ((Renderer) (Object) this).resources();
        GL20.glUniform2f(location, res.fboWidth() / (float) main.width, res.fboHeight() / (float) main.height);
    }

    private static void setDepthRange(String uniform) {
        if (!BetterCloudsShaderPatch.active()) return;
        int program = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int location = GL20.glGetUniformLocation(program, uniform);
        if (location >= 0) {
            GL20.glUniform2f(location, GameRenderer.PROJECTION_Z_NEAR, Minecraft.getInstance().gameRenderer.getDepthFar());
        }
    }

    /** Draws clouds at a lower resolution when configured: their cost is mostly per pixel. */
    @ModifyReturnValue(method = {"scaledFramebufferWidth", "scaledFramebufferHeight"}, at = @At("RETURN"))
    private int localweather$cloudResolution(int size) {
        if (!LocalWeatherClientConfig.SPEC.isLoaded()) return size;
        return Math.max(1, (int) (size * LocalWeatherClientConfig.CLOUD_RESOLUTION.get()));
    }

    /** Lifts every cloud by the configured offset, e.g. above Tectonic's mountains. */
    @WrapOperation(method = "prepare", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getCloudHeight()F"))
    private float localweather$cloudHeight(DimensionSpecialEffects effects, Operation<Float> original) {
        float height = original.call(effects);
        if (Float.isNaN(height)) return height;
        return height + ClientWeather.INSTANCE.cloudHeightOffset();
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
        setDepthRange("u_depth_range");
        setResolution();
    }
}
