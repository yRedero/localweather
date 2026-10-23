package redero.localweather.client.betterclouds;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * A Better Clouds cloud point that also carries a code for the patched shader: bits 0-1 size (1x to 4x),
 * bits 2-4 darkness (0-7), bit 5 flat. The code is added to y in steps of 2048 when the point is uploaded.
 * The box itself spans every cloud stacked on this point, so Better Clouds' frustum culling keeps towers.
 */
public final class CloudCode extends AABB {
    private static final float STEP = 2048.0F;

    public final float renderY;
    public final int code;

    public CloudCode(double x, double y, double z, double minY, double maxY, int code) {
        super(x, Math.min(y, minY), z, x, Math.max(y, maxY), z);
        this.renderY = (float) y;
        this.code = code;
    }

    /** @param size 1 to 4, @param darkness 0 to 1 */
    public static int encode(int size, float darkness, boolean flat) {
        int dark = Mth.clamp(Math.round(darkness * 7.0F), 0, 7);
        return (Mth.clamp(size, 1, 4) - 1) | dark << 2 | (flat ? 1 << 5 : 0);
    }

    public float encodedY() {
        return renderY + code * STEP;
    }
}
