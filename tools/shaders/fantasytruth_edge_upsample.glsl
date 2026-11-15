#ifndef FANTASYTRUTH_EDGE_UPSAMPLE
#define FANTASYTRUTH_EDGE_UPSAMPLE

vec4 ftUpsampleVolume(sampler2D fog, sampler2D metadata, vec2 uv, float depth) {
    vec2 extent = vec2(textureSize(fog, 0)) * VL_RENDER_RESOLUTION;
    vec2 samplePos = uv * extent - 0.5;
    ivec2 base = ivec2(floor(samplePos));
    ivec2 last = max(ivec2(ceil(extent)) - 1, ivec2(0));
    vec2 fraction = fract(samplePos);
    vec4 color = vec4(0.0);
    float total = 0.0;
    for (int i = 0; i < 4; ++i) {
        ivec2 offset = ivec2(i & 1, i >> 1);
        ivec2 coord = clamp(base + offset, ivec2(0), last);
        vec2 sampleInfo = texelFetch(metadata, coord, 0).gb;
        float agreement = sampleInfo.y * (1.0 - smoothstep(0.003, 0.018, abs(sampleInfo.x - depth)));
        vec2 weights = mix(1.0 - fraction, fraction, vec2(offset));
        float weight = agreement * (weights.x * weights.y + 0.001);
        color += texelFetch(fog, coord, 0) * weight;
        total += weight;
    }
    return total > 0.0001 ? color / total : vec4(0.0, 0.0, 0.0, 1.0);
}

#endif
