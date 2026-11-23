#version 150

// Local Weather's far rain veils: plain coloured quads that also pass their view distance on.
in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out float viewDepth;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    vertexColor = Color;
    viewDepth = -view.z;
}
