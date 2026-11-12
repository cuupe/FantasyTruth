#version 150

in vec3 Position;
out vec2 ScreenPosition;

void main() {
    ScreenPosition = Position.xy;
    gl_Position = vec4(Position, 1.0);
}
