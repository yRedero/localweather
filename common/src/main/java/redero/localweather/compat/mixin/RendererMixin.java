package redero.localweather.compat.mixin;

import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
import com.qendolin.betterclouds.config.Config;
import com.qendolin.betterclouds.config.ConfigManager;
import com.qendolin.betterclouds.clouds.fog.FogProvider;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import redero.localweather.client.ClientWeather;
import redero.localweather.client.LightningFlashes;
import redero.localweather.client.LocalWeatherClientConfig;
import redero.localweather.client.betterclouds.BetterCloudsCompat;
import redero.localweather.client.betterclouds.BetterCloudsShaderPatch;
import redero.localweather.client.betterclouds.VoxyDepth;
import redero.localweather.client.betterclouds.LodDepthDump;
import redero.localweather.server.DevScene;

@Mixin(value = Renderer.class, remap = false)
public abstract class RendererMixin {
    /** The patched coverage shader needs the game's near and far plane to tell what terrain hides a cloud. */
    @Inject(method = {"drawCloudsWithFrustumCulling", "drawCloudsWithoutFrustumCulling"}, at = @At("HEAD"))
    private void localweather$coverageDepthRange(CallbackInfo ci) {
        setDepthRange("lw_depth_range");
        setResolution();
        bindLodDepth();
    }

    /** Texture unit for Voxy's depth; Better Clouds' coverage pass uses units 0, 5 and 6. */
    private static final int LOD_UNIT = 7;

    /** Gives the coverage shader Voxy's depth, so far Voxy terrain hides the clouds behind it. */
    private void bindLodDepth() {
        if (!BetterCloudsShaderPatch.active()) return;
        int program = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int info = GL20.glGetUniformLocation(program, "lw_lod_info");
        if (info < 0) return;
        VoxyDepth.Snapshot voxy = VoxyDepth.current();
        if (voxy == null) {
            GL20.glUniform2f(info, 0.0F, 0.0F);
            return;
        }
        int previousUnit = GL20.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + LOD_UNIT);
        GlStateManager._bindTexture(voxy.texture());
        GlStateManager._activeTexture(previousUnit);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "lw_lod_depth"), LOD_UNIT);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program, "lw_lod_inv_proj"), false,
                    voxy.inverseProjection().get(stack.mallocFloat(16)));
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        GL20.glUniform2f(GL20.glGetUniformLocation(program, "lw_screen_inv"), 1.0F / main.width, 1.0F / main.height);
        GL20.glUniform2f(info, 1.0F, voxy.zeroToOne() ? 1.0F : 0.0F);
        if (DevScene.STRAFE) LodDepthDump.maybeDump(voxy);
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
        setFlashes();
    }

    @Shadow
    private float cloudsHeight;

    private final float[] localweather$flashes = new float[LightningFlashes.MAX * 4];

    /** Gives the shading shader the current lightning flashes, relative to the camera. */
    private void setFlashes() {
        if (!BetterCloudsShaderPatch.active()) return;
        int program = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int countLocation = GL20.glGetUniformLocation(program, "lw_flash_count");
        if (countLocation < 0) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);
        int count = LightningFlashes.write(camera.x, camera.z, partialTick, localweather$flashes);
        GL20.glUniform1i(countLocation, count);
        if (count == 0) return;
        GL20.glUniform4fv(GL20.glGetUniformLocation(program, "lw_flashes"), localweather$flashes);
        Config config = ConfigManager.instance();
        // A little into the layer, so the glow sits where the clouds are thickest.
        GL20.glUniform1f(GL20.glGetUniformLocation(program, "lw_cloud_y"),
                (float) (cloudsHeight + config.yOffset + config.yRange * 0.3 - camera.y));
    }
}
