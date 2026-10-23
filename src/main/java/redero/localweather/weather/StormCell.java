package redero.localweather.weather;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * One storm system: a disc of rain that drifts with the wind and fades in and out over its lifetime.
 * Mutated only by the server simulator; clients get fresh copies and extrapolate them with {@code aheadTicks}.
 */
public final class StormCell {
    /** Bends every storm's outline so it isn't a circle. Shared by rain, lightning and clouds, so they agree. */
    private static final SimplexNoise SHAPE_NOISE = new SimplexNoise(new LegacyRandomSource(0x5704L));
    /** How far the outline is pushed in or out, as a share of the radius. */
    private static final double WARP = 0.25;
    /** Overcast reaches this much further than the rain. */
    private static final float CLOUD_REACH = 1.35F;

    public final int id;
    public final CellType type;
    public double x, z;
    /** Blocks per tick. */
    public float vx, vz;
    public float radius;
    public float intensity;
    public float thunder;
    /** How fast this cell rides the wind compared to other cells. */
    public float windFactor;
    public int age;
    public int lifetime;

    public StormCell(int id, CellType type, double x, double z, float radius, float intensity, float thunder,
                     float windFactor, int lifetime) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.z = z;
        this.radius = radius;
        this.intensity = intensity;
        this.thunder = thunder;
        this.windFactor = windFactor;
        this.lifetime = lifetime;
    }

    public StormCell copy() {
        StormCell c = new StormCell(id, type, x, z, radius, intensity, thunder, windFactor, lifetime);
        c.vx = vx;
        c.vz = vz;
        c.age = age;
        return c;
    }

    /** 0..1 strength from the forming and dissipating stages. */
    public float envelope(int aheadTicks) {
        float f = (age + aheadTicks) / (float) lifetime;
        if (f >= 1.0F) return 0.0F;
        if (f < 0.15F) return smooth(f / 0.15F);
        if (f > 0.8F) return smooth((1.0F - f) / 0.2F);
        return 1.0F;
    }

    public Stage stage() {
        float f = age / (float) lifetime;
        return f < 0.15F ? Stage.FORMING : f > 0.8F ? Stage.DISSIPATING : Stage.MATURE;
    }

    public double centerX(int aheadTicks) {
        return x + vx * aheadTicks;
    }

    public double centerZ(int aheadTicks) {
        return z + vz * aheadTicks;
    }

    /** Distance from the centre in radii, measured through the outline noise. */
    private double relativeDistance(double px, double pz, int aheadTicks) {
        double dx = px - centerX(aheadTicks);
        double dz = pz - centerZ(aheadTicks);
        double reach = radius * CLOUD_REACH * (1.0 + WARP);
        if (dx * dx + dz * dz > reach * reach) return Double.MAX_VALUE;
        double warp = WARP * radius;
        dx += warp * SHAPE_NOISE.getValue(px / 900.0, pz / 900.0);
        dz += warp * SHAPE_NOISE.getValue(px / 900.0 + 37.0, pz / 900.0 + 37.0);
        return Math.sqrt(dx * dx + dz * dz) / radius;
    }

    /** Rain strength at a point: flat core out to 55% of the radius, soft edge after that. */
    public float rainAt(double px, double pz, int aheadTicks) {
        double d = relativeDistance(px, pz, aheadTicks);
        if (d >= 1.0) return 0.0F;
        return intensity * envelope(aheadTicks) * (1.0F - smoothstep(0.55F, 1.0F, (float) d));
    }

    /** Cloud cover: the overcast reaches past the rain, so a storm looks bigger than the area it rains on. */
    public float cloudAt(double px, double pz, int aheadTicks) {
        double d = relativeDistance(px, pz, aheadTicks) / CLOUD_REACH;
        if (d >= 1.0) return 0.0F;
        return intensity * envelope(aheadTicks) * (1.0F - smoothstep(0.45F, 1.0F, (float) d));
    }

    /** Thunder is concentrated in a tighter core than the rain. */
    public float thunderAt(double px, double pz, int aheadTicks) {
        if (thunder <= 0.0F) return 0.0F;
        double d = relativeDistance(px, pz, aheadTicks);
        if (d >= 0.7) return 0.0F;
        return thunder * envelope(aheadTicks) * (1.0F - smoothstep(0.3F, 0.7F, (float) d));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("type", type.name());
        tag.putDouble("x", x);
        tag.putDouble("z", z);
        tag.putFloat("radius", radius);
        tag.putFloat("intensity", intensity);
        tag.putFloat("thunder", thunder);
        tag.putFloat("wind_factor", windFactor);
        tag.putInt("age", age);
        tag.putInt("lifetime", lifetime);
        return tag;
    }

    public static StormCell load(CompoundTag tag) {
        CellType type;
        try {
            type = CellType.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException e) {
            type = CellType.RAIN;
        }
        StormCell c = new StormCell(tag.getInt("id"), type, tag.getDouble("x"), tag.getDouble("z"),
                tag.getFloat("radius"), tag.getFloat("intensity"), tag.getFloat("thunder"),
                tag.getFloat("wind_factor"), Math.max(1, tag.getInt("lifetime")));
        c.age = tag.getInt("age");
        return c;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(id);
        buf.writeEnum(type);
        buf.writeDouble(x);
        buf.writeDouble(z);
        buf.writeFloat(vx);
        buf.writeFloat(vz);
        buf.writeFloat(radius);
        buf.writeFloat(intensity);
        buf.writeFloat(thunder);
        buf.writeVarInt(age);
        buf.writeVarInt(lifetime);
    }

    public static StormCell read(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        CellType type = buf.readEnum(CellType.class);
        double x = buf.readDouble();
        double z = buf.readDouble();
        float vx = buf.readFloat();
        float vz = buf.readFloat();
        float radius = buf.readFloat();
        float intensity = buf.readFloat();
        float thunder = buf.readFloat();
        int age = buf.readVarInt();
        int lifetime = buf.readVarInt();
        StormCell c = new StormCell(id, type, x, z, radius, intensity, thunder, 1.0F, Math.max(1, lifetime));
        c.vx = vx;
        c.vz = vz;
        c.age = age;
        return c;
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        return smooth(Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F));
    }

    public enum Stage { FORMING, MATURE, DISSIPATING }
}
