package redero.localweather;

/**
 * Switches for development and testing, set as system properties (-Dlocalweather.x=...; the Gradle runs set them
 * from -P switches, see README "Develop"). All off in normal play. They work in the release jar too, so tests can
 * run against a real launcher profile.
 */
public final class DevFlags {
    /** Dev scene: true, inside, above, ground or strafe (see DevScene); null when off. */
    public static final String DEV_SCENE = System.getProperty("localweather.devscene");
    /** Ticks the dev scene waits before its first shot. */
    public static final int WARMUP_TICKS = Integer.getInteger("localweather.warmup", 300);
    /** Blocks per tick the strafe scene walks sideways. */
    public static final double STRAFE_SPEED = Double.parseDouble(System.getProperty("localweather.strafeSpeed", "0.2"));
    /** Paint each cloud pixel by how it was tested against terrain (see BetterCloudsShaderPatch). */
    public static final boolean DEBUG_OCCLUSION = Boolean.getBoolean("localweather.debugOcclusion");
    /** Leave Better Clouds' shaders alone, to compare against. */
    public static final boolean NO_SHADER_PATCH = Boolean.getBoolean("localweather.noShaderPatch");
    /** Thunderstorms flash 8x as often, with a visible bolt every time. */
    public static final boolean LIGHTNING_TEST = Boolean.getBoolean("localweather.lightningTest");
    /** A cumulus heap in nearly every possible place. */
    public static final boolean CUMULUS_TEST = Boolean.getBoolean("localweather.cumulusTest");

    private DevFlags() {
    }
}
