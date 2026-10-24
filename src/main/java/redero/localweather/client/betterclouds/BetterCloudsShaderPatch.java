package redero.localweather.client.betterclouds;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import redero.localweather.LocalWeather;

/**
 * Text patches for Better Clouds' coverage and shading shaders, so each cloud can carry a size, a storm
 * darkness and a "flat" flag. They travel in the cloud's y coordinate, see {@link CloudCode}.
 * <p>
 * Clouds past the camera's far plane all get depth 1.0 under Better Clouds' depth clamp, so they can't be
 * sorted and flicker through each other. The coverage pass therefore writes its own depth with a far range
 * of {@code LW_FAR}, and the shading pass converts it back to the game's depth when it copies it out.
 * <p>
 * All three shaders are patched or none: a partial patch would put clouds in the wrong place.
 */
public final class BetterCloudsShaderPatch {
    private static final String COVERAGE_VSH = "shaders/core/betterclouds_coverage.vsh";
    private static final String COVERAGE_FSH = "shaders/core/betterclouds_coverage.fsh";
    private static final String SHADING_FSH = "shaders/core/betterclouds_shading.fsh";
    /** Near and far plane of the clouds' own depth. Clouds closer than a few blocks are faded out anyway. */
    private static final String DEPTH_RANGE = "const float LW_NEAR = 4.0; const float LW_FAR = 16384.0;";

    private static final String[][] COVERAGE = {
            {"layout(location = 0) in vec3 in_pos;", "layout(location = 0) in vec3 lw_in_pos;"},
            {"void main() {", """
                    void main() {
                        // Local Weather: decode size, darkness and flat flag from the cloud's y coordinate.
                        float lw_code = floor((lw_in_pos.y + 1024.0) / 2048.0);
                        vec3 in_pos = vec3(lw_in_pos.x, lw_in_pos.y - lw_code * 2048.0, lw_in_pos.z);
                        float lw_size = (1.0 + mod(lw_code, 4.0)) * (1.0 + mod(floor(lw_code / 64.0), 2.0));
                        float lw_dark = mod(floor(lw_code / 4.0), 8.0);
                        float lw_flat = mod(floor(lw_code / 32.0), 2.0);"""},
            // Better Clouds shrinks and flattens clouds towards the edge of its range; far storms must keep their height.
            {"vec3 cloudPos = in_pos; // in world space but anchored to the chunk grid", """
                    if (lw_code > 0.5) scaleFalloff = 1.0;
                        vec3 cloudPos = in_pos; // in world space but anchored to the chunk grid"""},
            {"vec3 scale = SIZE * dynScale * scaleFalloff;", """
                    vec3 scale = SIZE * dynScale * scaleFalloff;
                        scale *= lw_size;
                        scale.y *= mix(1.0, 0.2, lw_flat);"""},
            // Storm clouds are dark from below but sunlit from above: a cloud below the camera loses most of its
            // darkness. Judged by height rather than view angle, so far clouds near the horizon count too.
            // The clouds' own long-range depth, so clouds past the game's far plane still sort. Set per vertex rather
            // than per fragment (gl_FragDepth), which would stop the GPU from skipping hidden fragments early.
            {"    gl_Position = u_mvp_matrix * vec4(vertexPos, 1.0);\n#endif", """
                        gl_Position = u_mvp_matrix * vec4(vertexPos, 1.0);
                    #endif
                        {
                            %s
                            gl_Position.z = (LW_FAR + LW_NEAR) / (LW_FAR - LW_NEAR) * gl_Position.w - 2.0 * LW_FAR * LW_NEAR / (LW_FAR - LW_NEAR);
                        }""".formatted(DEPTH_RANGE)},
            {"pass_color.b = texture(u_noise_texture, localWorldPosition.xz / 1024.0).g;", """
                    float lw_seen_dark = floor(lw_dark * mix(1.0, 0.25, smoothstep(0.0, -48.0, localWorldPosition.y)) + 0.5);
                        pass_color.b = (lw_seen_dark + 0.05 + 0.85 * texture(u_noise_texture, localWorldPosition.xz / 1024.0).g) / 8.0;"""},
    };

    private static final String[][] COVERAGE_DEPTH = {
            {"uniform sampler2D u_depth_texture;", """
                    uniform sampler2D u_depth_texture;
                    // Local Weather: the game's near and far plane, and cloud resolution / screen resolution, set by RendererMixin.
                    uniform vec2 lw_depth_range;
                    uniform vec2 lw_resolution;"""},
            // With a lower cloud resolution this pass runs on a smaller target; the depth textures are full size.
            {"void main() {", """
                    void main() {
                        vec2 lw_res = lw_resolution.x > 0.0 ? lw_resolution : vec2(1.0);"""},
            {"ivec2(gl_FragCoord.xy)", "ivec2(gl_FragCoord.xy / lw_res)"},
            // Terrain hides clouds by comparing view distances rather than depths: past the game's far plane the
            // cloud's depth is clamped and would lose against anything. Terrain depth near or past the far plane
            // comes from LOD mods (Voxy, Distant Horizons) with their own projection and can't be compared; it
            // doesn't hide clouds, which sit above that terrain in all but rare cases.
            {"if(min(gl_FragCoord.z, 1.0) > depth) discard;", """
                    // gl_FragCoord.z holds the clouds' own long-range depth (set in the vertex shader); terrain
                        // tests need the game's depth, rebuilt here from the view distance.
                        float lw_w = 1.0 / gl_FragCoord.w;
                        float lw_gn = lw_depth_range.x;
                        float lw_gf = lw_depth_range.y;
                        if (depth < 1.0 && lw_gf > lw_gn) {
                            float lw_game_depth = clamp(((lw_gf + lw_gn) - 2.0 * lw_gf * lw_gn / lw_w) / (lw_gf - lw_gn) * 0.5 + 0.5, 0.0, 1.0);
                            float lw_scene = 2.0 * lw_gf * lw_gn / ((lw_gf + lw_gn) - (2.0 * depth - 1.0) * (lw_gf - lw_gn));
                            bool lw_trusted = lw_scene < 0.9 * lw_gf;
                            if (lw_trusted && lw_w > lw_scene) discard;
                            if (!lw_trusted && lw_w < 0.9 * lw_gf && lw_game_depth > depth) discard;
                        }"""},
    };

    private static final String[][] SHADING = {
            {"gl_FragDepth = texelFetch(u_depth_texture, ivec2(gl_FragCoord.xy), 0).r;", """
                    float lw_d = texelFetch(u_depth_texture, ivec2(gl_FragCoord.xy), 0).r;
                        // Local Weather: back from the clouds' long depth range to the game's (u_depth_range = near, far).
                        %s
                        float lw_z = 2.0 * LW_FAR * LW_NEAR / ((LW_FAR + LW_NEAR) - (2.0 * lw_d - 1.0) * (LW_FAR - LW_NEAR));
                        float lw_gn = u_depth_range.x;
                        float lw_gf = u_depth_range.y;
                        // Clouds past the far plane would land exactly on it. LOD mods (Voxy) draw their far terrain after
                        // the clouds, with its depth capped just in front of the far plane, so it would paint over every
                        // far cloud. Cap cloud depth a few steps (of a 24-bit buffer) in front of that instead.
                        gl_FragDepth = lw_d >= 1.0 || lw_gf <= lw_gn ? lw_d
                            : min(((lw_gf + lw_gn) - 2.0 * lw_gf * lw_gn / lw_z) / (lw_gf - lw_gn) * 0.5 + 0.5, 1.0 - 4.0 / 16777215.0);""".formatted(DEPTH_RANGE)},
            {"vec3 cloudData = texelFetch(u_data_texture, ivec2(gl_FragCoord), 0).rgb;", """
                    vec3 cloudData = texelFetch(u_data_texture, ivec2(gl_FragCoord), 0).rgb;
                        // Local Weather: the blue channel holds 8 darkness steps with the colour noise inside each step.
                        float lw_dark = floor(cloudData.b * 8.0) / 7.0;
                        cloudData.b = clamp((fract(cloudData.b * 8.0) - 0.05) / 0.85, 0.0, 1.0);"""},
            {"color_lumi *= u_color_grading.x;", """
                    color_lumi *= mix(1.0, 0.22, min(lw_dark, 1.0));
                        color_lumi *= u_color_grading.x;"""},
            // Storm clouds are dense: less see-through than Better Clouds' fair-weather clouds.
            {"out_color.a *= u_opacity.y;", """
                    out_color.a = mix(out_color.a, sqrt(out_color.a), 0.6 * min(lw_dark, 1.0));
                        out_color.a *= u_opacity.y;"""},
            // Lower cloud resolution: this full-screen pass reads the smaller cloud textures at scaled coordinates.
            {"uniform sampler2D u_light_texture;", """
                    uniform sampler2D u_light_texture;
                    // Local Weather: cloud resolution / screen resolution, set by RendererMixin.
                    uniform vec2 lw_resolution;"""},
            {"ivec2(gl_FragCoord.xy)", "lw_px"},
            {"ivec2(gl_FragCoord)", "lw_px"},
            {"void main() {", """
                    void main() {
                        ivec2 lw_px = ivec2(gl_FragCoord.xy * (lw_resolution.x > 0.0 ? lw_resolution : vec2(1.0)));"""},
    };

    private static volatile boolean active;

    private BetterCloudsShaderPatch() {
    }

    /** True once the current shaders carry the patch, so the generator may encode clouds. */
    public static boolean active() {
        return active;
    }

    /** Called with each Better Clouds shader source right before it is compiled. */
    public static String patch(ResourceLocation id, String source, ResourceManager resources) {
        String[][] edits;
        if (id.getPath().equals(COVERAGE_VSH)) {
            edits = COVERAGE;
        } else if (id.getPath().equals(COVERAGE_FSH)) {
            edits = COVERAGE_DEPTH;
        } else if (id.getPath().equals(SHADING_FSH)) {
            edits = SHADING;
        } else {
            return source;
        }
        boolean ok = anchorsPresent(resources, COVERAGE_VSH, COVERAGE) && anchorsPresent(resources, COVERAGE_FSH, COVERAGE_DEPTH)
                && anchorsPresent(resources, SHADING_FSH, SHADING);
        active = ok;
        if (!ok) {
            LocalWeather.LOGGER.warn("Better Clouds shaders changed, storm cloud sizes and darkness are disabled");
            return source;
        }
        source = source.replace("\r\n", "\n");
        for (String[] edit : edits) {
            source = source.replace(edit[0], edit[1]);
        }
        return source;
    }

    private static boolean anchorsPresent(ResourceManager resources, String path, String[][] edits) {
        Optional<Resource> resource = resources.getResource(ResourceLocation.fromNamespaceAndPath("betterclouds", path));
        if (resource.isEmpty()) return false;
        try (InputStream in = resource.get().open()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            for (String[] edit : edits) {
                if (!text.contains(edit[0])) return false;
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
