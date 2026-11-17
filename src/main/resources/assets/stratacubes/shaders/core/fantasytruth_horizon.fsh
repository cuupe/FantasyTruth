#version 150

uniform sampler2D Blocks;
uniform vec4 StoneUV;
uniform vec4 SandUV;
uniform vec4 LeavesUV;
uniform vec4 GrassUV;
uniform vec4 WoodUV;
uniform vec4 PlankUV;
uniform vec4 SoilUV;
uniform vec4 HorizonFog;
uniform vec3 Clip;
uniform vec4 Tile;
uniform float TerrainLight;

in vec3 position;
in vec3 tilePoint;
in vec3 terrainColor;
flat in int material;
flat in int seam;
out vec4 fragColor;

float grain(vec2 point) {
    vec2 p = mod(floor(point), 64.0) * 0.1031;
    p += dot(p, p.yx + 33.33);
    return fract((p.x + p.y) * p.x);
}

bool covered(int mask, vec2 point) {
    ivec2 cell = clamp(ivec2(floor(4.0 * point / Tile.z)), ivec2(0), ivec2(3));
    return (mask & (1 << (4 * cell.y + cell.x))) != 0;
}

void main() {
    vec3 dx = dFdx(tilePoint);
    vec3 dy = dFdy(tilePoint);
    vec2 relative = abs(position.xz);
    float edge = max(relative.x, relative.y);
    float inner = max(0.0, Clip.y - 62.0);
    float distance = length(position);
    if (edge <= inner || distance >= Clip.z) discard;

    int coverage = int(Tile.w);
    if (seam != 0) {
        vec2 outward = seam == 1 ? vec2(-0.25, 0) : seam == 2 ? vec2(0.25, 0)
                : seam == 3 ? vec2(0, -0.25) : vec2(0, 0.25);
        if (coverage == 65535 || !covered(coverage, tilePoint.xz - outward)
                || covered(coverage, tilePoint.xz + outward)) discard;
    } else if (coverage != 65535 && !covered(coverage, tilePoint.xz)) discard;

    vec3 local = terrainColor * TerrainLight;
    if (material != 0) {
        vec3 normal = abs(cross(dx, dy));
        vec2 uv = tilePoint.xz;
        vec2 du = dx.xz, dv = dy.xz;
        if (normal.x > max(normal.y, normal.z)) {
            uv = tilePoint.zy;
            du = dx.zy;
            dv = dy.zy;
        } else if (normal.z > max(normal.x, normal.y)) {
            uv = tilePoint.xy;
            du = dx.xy;
            dv = dy.xy;
        }
        float pixels = max(length(du), length(dv));
        if (pixels < 7.0) {
            vec4 sprite = material == 7 ? SoilUV : material == 6 ? PlankUV : material == 5 ? WoodUV
                    : material == 4 ? GrassUV : material == 3 ? LeavesUV : material == 2 ? SandUV : StoneUV;
            vec2 size = sprite.zw - sprite.xy;
            vec2 atlasUV = sprite.xy + (0.04 + 0.92 * fract(uv)) * size;
            vec4 texel = textureGrad(Blocks, atlasUV, du * size * 0.92, dv * size * 0.92);
            float tex = dot(texel.rgb, vec3(0.299, 0.587, 0.114));
            float average = material == 2 ? 0.82 : material == 6 ? 0.62
                    : material == 5 || material == 7 ? 0.34 : material == 3 ? 0.48 : 0.50;
            float strength = 1.0 - smoothstep(0.85, 4.0, pixels);
            local *= 1.0 + (tex - average) * strength * texel.a * 0.92;
        }
        if (pixels < 11.0) {
            local *= 1.0 + (grain(uv * 0.125) - 0.5) * (1.0 - smoothstep(4.0, 11.0, pixels)) * 0.16;
        }
    }

    float visibility = max(330.0, Clip.z * 0.33);
    float opticalDepth = max(0.0, distance - Clip.y * 0.66) / visibility;
    float haze = 1.0 - 1.0 / (1.0 + opticalDepth * (0.48 + opticalDepth));
    // The coarse and vanilla passes overlap. Fade underneath the real chunk silhouette.
    float nearFade = smoothstep(inner, inner + min(53.0, (Clip.z - inner) * 0.48), edge);
    float farFade = 1.0 - smoothstep(Clip.z * 0.84, Clip.z, distance);
    fragColor = vec4(mix(local, HorizonFog.rgb, haze), nearFade * farFade);
}
