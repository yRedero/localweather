#version 150

// Hides a veil only where terrain really is in front of it, by comparing distances: the veils are mostly past the
// game's far plane, where depths are clamped and can't be compared. Nearby terrain and clouds come from the game's
// depth (a copy of it); terrain past the far plane from Voxy's own depth, as Local Weather's clouds do.
uniform sampler2D SceneDepth;
uniform sampler2D LodDepth;
uniform vec2 ScreenSize;
// The game's near and far plane.
uniform vec2 DepthRange;
// x: Voxy's depth is bound, y: it is stored 0..1.
uniform vec2 LodInfo;
uniform mat4 LodInvProj;

in vec4 vertexColor;
in float viewDepth;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float depth = texture(SceneDepth, uv).r;
    float near = DepthRange.x;
    float far = DepthRange.y;
    float scene = depth < 1.0 ? 2.0 * near * far / ((far + near) - (2.0 * depth - 1.0) * (far - near)) : far;
    if (scene < 0.9 * far) {
        if (scene < viewDepth) discard;
    } else if (LodInfo.x > 0.5) {
        float lod = texture(LodDepth, uv).r;
        if (lod > 0.0 && lod < 1.0) {
            vec4 view = LodInvProj * vec4(uv * 2.0 - 1.0, LodInfo.y > 0.5 ? lod : lod * 2.0 - 1.0, 1.0);
            float terrain = -view.z / view.w;
            if (terrain > 0.0 && terrain < viewDepth) discard;
        }
    }
    fragColor = vertexColor;
}
