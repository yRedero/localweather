#version 150

// Local Weather's rain haze: one quad over the whole screen.
in vec3 Position;

out vec2 ndc;

void main() {
    ndc = Position.xy;
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
