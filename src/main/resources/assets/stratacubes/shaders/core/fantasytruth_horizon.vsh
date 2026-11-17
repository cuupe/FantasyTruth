#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Origin;

out vec3 position;
out vec4 terrainColor;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    // Reserve the last portion of the depth buffer for distant terrain. Near chunks keep priority.
    gl_Position.z = gl_Position.w - 0.015;
    position = Position + Origin;
    terrainColor = Color;
}
