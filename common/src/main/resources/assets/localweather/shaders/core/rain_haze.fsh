#version 150

// Rain haze as fog in the air: every pixel's view ray is stepped through the rain below the cloud base, out to the
// terrain behind it (the game's depth nearby, Voxy's far away). The rain comes from a small map of the storms around
// the camera (red: rain, green: thunder), so the haze follows their real outlines with no surfaces or edges.
uniform sampler2D SceneDepth;
uniform sampler2D LodDepth;
uniform sampler2D RainMap;
uniform vec2 ScreenSize;
// The game's near and far plane.
uniform vec2 DepthRange;
// x: Voxy's depth is bound, y: it is stored 0..1.
uniform vec2 LodInfo;
uniform mat4 LodInvProj;
// Camera axes; Right and Up are scaled by the half-size of the view, so Look + ndc.x * Right + ndc.y * Up is the ray
// through a pixel, one unit deep along Look.
uniform vec3 Look;
uniform vec3 Right;
uniform vec3 Up;
uniform float CamY;
uniform float CloudBase;
// The map's corner relative to the camera (x, z) and its size in blocks.
uniform vec2 MapOrigin;
uniform float MapSize;
uniform float MaxDist;
uniform vec3 HazeColor;
// Extinction per block in full rain.
uniform float Density;
uniform float Fade;

in vec2 ndc;

out vec4 fragColor;

const int STEPS = 20;

// Interleaved gradient noise: a different start per pixel, so the steps don't show as bands.
float dither(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec3 ray = Look + ndc.x * Right + ndc.y * Up;
    float rayLength = length(ray);
    vec3 dir = ray / rayLength;

    float depth = texture(SceneDepth, uv).r;
    float near = DepthRange.x;
    float far = DepthRange.y;
    float end = MaxDist;
    float viewZ = depth < 1.0 ? 2.0 * near * far / ((far + near) - (2.0 * depth - 1.0) * (far - near)) : far;
    if (viewZ < 0.9 * far) {
        end = viewZ * rayLength;
    } else if (LodInfo.x > 0.5) {
        float lod = texture(LodDepth, uv).r;
        if (lod > 0.0 && lod < 1.0) {
            vec4 view = LodInvProj * vec4(uv * 2.0 - 1.0, LodInfo.y > 0.5 ? lod : lod * 2.0 - 1.0, 1.0);
            float terrain = -view.z / view.w;
            if (terrain > 0.0) end = terrain * rayLength;
        }
    }
    end = min(end, MaxDist);

    float transmittance = 1.0;
    float start = dither(gl_FragCoord.xy);
    float previous = 0.0;
    for (int i = 1; i <= STEPS; i++) {
        // Denser steps near the camera, where the haze changes fastest on screen.
        float f = (float(i) - start) / float(STEPS);
        float t = end * f * f;
        float dt = t - previous;
        previous = t;
        vec3 p = dir * t;
        float below = 1.0 - smoothstep(CloudBase - 30.0, CloudBase + 5.0, CamY + p.y);
        vec2 mapUv = (p.xz - MapOrigin) / MapSize;
        float inside = smoothstep(0.0, 0.04, mapUv.x) * smoothstep(0.0, 0.04, mapUv.y)
                * smoothstep(1.0, 0.96, mapUv.x) * smoothstep(1.0, 0.96, mapUv.y);
        vec2 weather = texture(RainMap, mapUv).rg;
        float sigma = Density * weather.r * (1.0 + 0.8 * weather.g) * below * inside;
        transmittance *= exp(-sigma * dt);
    }
    float alpha = min(1.0 - transmittance, 0.85) * Fade;
    fragColor = vec4(HazeColor * alpha, alpha);
}
