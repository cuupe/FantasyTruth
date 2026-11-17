#version 150

uniform vec4 FogColor;
uniform vec4 Clip;
uniform float TerrainLight;

in vec3 position;
in vec4 terrainColor;
out vec4 fragColor;

void main() {
    vec2 relative = abs(position.xz - Clip.xy);
    if (max(relative.x, relative.y) < Clip.z) discard;
    float distance = length(position);
    if (distance >= Clip.w) discard;
    float haze = smoothstep(Clip.z * 0.75, Clip.w, distance);
    fragColor = vec4(mix(terrainColor.rgb * TerrainLight, FogColor.rgb, haze), 1.0);
}
