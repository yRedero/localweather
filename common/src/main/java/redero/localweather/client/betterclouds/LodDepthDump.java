package redero.localweather.client.betterclouds;

import java.io.File;
import java.nio.FloatBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import redero.localweather.LocalWeather;

/** Dev only: saves Voxy's depth texture, as the cloud shader sees it, to run/screenshots/lod_depth.png. */
public final class LodDepthDump {
    private static int frames;
    private static boolean done;

    private LodDepthDump() {
    }

    public static void maybeDump(VoxyDepth.Snapshot voxy) {
        if (done || ++frames < 600) return;
        done = true;
        int tex = voxy.texture();
        int w = GL45.glGetTextureLevelParameteri(tex, 0, GL11.GL_TEXTURE_WIDTH);
        int h = GL45.glGetTextureLevelParameteri(tex, 0, GL11.GL_TEXTURE_HEIGHT);
        int format = GL45.glGetTextureLevelParameteri(tex, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
        int minFilter = GL45.glGetTextureParameteri(tex, GL11.GL_TEXTURE_MIN_FILTER);
        int compare = GL45.glGetTextureParameteri(tex, GL14.GL_TEXTURE_COMPARE_MODE);
        LocalWeather.LOGGER.info("LOD depth: texture {} {}x{} format {} minFilter {} compareMode {} zeroToOne {} invProj {}",
                tex, w, h, format, minFilter, compare, voxy.zeroToOne(), voxy.inverseProjection());
        if (w <= 0 || h <= 0) return;
        FloatBuffer buf = MemoryUtil.memAllocFloat(w * h);
        try {
            GL45.glGetTextureImage(tex, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, buf);
            float min = 1, max = 0;
            int ones = 0, zeros = 0;
            for (int i = 0; i < w * h; i++) {
                float d = buf.get(i);
                if (d >= 1.0F) ones++; else if (d <= 0.0F) zeros++;
                else { min = Math.min(min, d); max = Math.max(max, d); }
            }
            LocalWeather.LOGGER.info("LOD depth values: {} at 1, {} at 0, others {}..{}", ones, zeros, min, max);
            try (NativeImage image = new NativeImage(w, h, false)) {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        float d = buf.get(y * w + x);
                        int v;
                        int abgr;
                        if (d >= 1.0F) abgr = 0xFFFF0000; // blue: cleared / sky
                        else if (d <= 0.0F) abgr = 0xFF0000FF; // red: zero
                        else {
                            // Linear view distance via the inverse projection, shown on a log scale up to 16 km.
                            org.joml.Vector4f v4 = new org.joml.Vector4f((x + 0.5F) / w * 2 - 1, (y + 0.5F) / h * 2 - 1,
                                    voxy.zeroToOne() ? d : d * 2 - 1, 1).mul(voxy.inverseProjection());
                            float dist = -v4.z / v4.w;
                            v = (int) Math.max(0, Math.min(255, Math.log(Math.max(dist, 1)) / Math.log(16000) * 255));
                            abgr = 0xFF000000 | v << 16 | v << 8 | v;
                        }
                        image.setPixelRGBA(x, h - 1 - y, abgr);
                    }
                }
                File out = new File(new File(Minecraft.getInstance().gameDirectory, "screenshots"), "lod_depth.png");
                out.getParentFile().mkdirs();
                image.writeToFile(out);
                LocalWeather.LOGGER.info("LOD depth saved to {}", out);
            }
        } catch (Exception e) {
            LocalWeather.LOGGER.warn("LOD depth dump failed", e);
        } finally {
            MemoryUtil.memFree(buf);
        }
    }
}
