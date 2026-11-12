#version 150

uniform vec3 Forward;
uniform vec3 Left;
uniform vec3 Up;
uniform vec2 ViewScale;
uniform vec3 LightDirection;
uniform vec3 CameraOffset;
uniform vec3 UpperColor;
uniform vec3 HorizonColor;
uniform vec3 LowerColor;
uniform float CloudStrength;
uniform float ApertureStrength;
uniform float SurfaceOnly;

in vec2 ScreenPosition;
out vec4 fragColor;

float cell(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 w = f * f * (3.0 - 2.0 * f);
    return mix(mix(cell(i), cell(i + vec2(1.0, 0.0)), w.x),
               mix(cell(i + vec2(0.0, 1.0)), cell(i + vec2(1.0)), w.x), w.y);
}

void main() {
    vec2 screen = ScreenPosition / ViewScale;
    vec3 ray = normalize(Forward - screen.x * Left + screen.y * Up);
    float down = max(-ray.y, 0.0);
    float up = max(ray.y, 0.0);
    vec3 color = mix(HorizonColor, LowerColor, smoothstep(0.0, 1.0, down));
    color = mix(color, UpperColor, smoothstep(0.0, 1.0, up) * 0.65);

    // Offset the cloud field with the camera's world position so it stays in the shaft as the view turns.
    vec2 mist = ray.xz * 3.8 + vec2(ray.y * 1.7, -ray.y * 2.4) + CameraOffset.xz;
    float density = 0.68 * noise(mist) + 0.32 * noise(mist * 2.34 + CameraOffset.y);
    float cloud = smoothstep(0.34, 0.75, density) * smoothstep(0.02, 0.75, down);
    color = mix(color, mix(HorizonColor, UpperColor, 0.68), cloud * CloudStrength);

    float opening = pow(max(dot(ray, LightDirection), 0.0), 7.5);
    float light = opening * (0.28 + 0.72 * density) * ApertureStrength;
    color = mix(color, UpperColor, light);
    float opacity = mix(1.0, smoothstep(0.0, 0.54, down), SurfaceOnly);
    fragColor = vec4(color, opacity);
}
