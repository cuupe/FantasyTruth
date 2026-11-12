#ifndef FANTASYTRUTH_EDGE_VOLUME
#define FANTASYTRUTH_EDGE_VOLUME

float ftShaft(vec3 p) {
    float rim = 1.0 - smoothstep(444.0, 508.0, length(p.xz - vec2(8.0)));
    return rim * (1.0 - smoothstep(-40.0, 12.0, p.y)) * smoothstep(-1510.0, -1350.0, p.y);
}

float ftHash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float ftNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(ftHash(i), ftHash(i + vec2(1.0, 0.0)), f.x),
               mix(ftHash(i + vec2(0.0, 1.0)), ftHash(i + vec2(1.0)), f.x), f.y);
}

float ftCloudHeight(int band) {
    if (band == 0) return -165.0;
    if (band == 1) return -440.0;
    if (band == 2) return -740.0;
    return -1060.0;
}

float ftOpening(vec3 p) {
    // Project towards the lit side of the opening to keep the shafts diagonal in world space.
    vec2 projected = p.xz + max(0.0, 95.0 - p.y) * vec2(-0.25, 0.17);
    float rim = length(projected - vec2(8.0));
    float streak = ftNoise(vec2((projected.x + projected.y * 0.64) * 0.021, projected.y * 0.004));
    return (1.0 - smoothstep(290.0, 473.0, rim)) * mix(0.32, 1.0, smoothstep(0.23, 0.72, streak));
}

float ftShadow(vec3 position) {
    vec3 p = toShadowSpaceProjected(position);
    #ifdef DISTORT_SHADOWMAP
        p.xy *= calcDistort(p.xy);
    #endif
    float border = max(abs(p.x), abs(p.y));
    if (border >= 0.99) return 1.0;
    p = p * vec3(0.5, 0.5, 0.5 / 6.0) + 0.5;
    #ifdef TRANSLUCENT_COLORED_SHADOWS
        float lit = shadow2D(shadowtex0, p).x;
    #else
        float lit = shadow2D(shadow, p).x;
    #endif
    return mix(lit, 1.0, smoothstep(0.9, 0.99, border));
}

vec4 ftEdgeVolume(vec3 viewPosition, vec3 sun, vec3 ambient, float weight) {
    vec3 local = gbufferModelViewInverse[3].xyz;
    vec3 origin = cameraPosition + local;
    vec3 direction = mat3(gbufferModelViewInverse) * viewPosition;
    float distanceToScene = length(direction);
    if (distanceToScene < 0.01) return vec4(0.0, 0.0, 0.0, 1.0);
    direction /= distanceToScene;
    float end = min(distanceToScene, 1680.0);

    vec3 color = vec3(0.0);
    float transmittance = 1.0;
    vec3 cloudAmbient = vec3(0.38, 0.49, 0.60) + ambient * 0.045;
    vec3 cloudSun = vec3(0.31, 0.26, 0.17) + sun * 0.012;
    for (int layer = 0; layer < 4; ++layer) {
        int band = direction.y < 0.0 ? layer : 3 - layer;
        float center = ftCloudHeight(band);
        float bottom = center - 12.0, top = center + 12.0;
        float a = 0.0, b = end;
        if (abs(direction.y) < 0.001) {
            if (origin.y < bottom || origin.y > top) continue;
        } else {
            float hit0 = (bottom - origin.y) / direction.y;
            float hit1 = (top - origin.y) / direction.y;
            a = max(0.0, min(hit0, hit1));
            b = min(end, max(hit0, hit1));
        }
        if (b <= a) continue;
        float span = min(b - a, 66.0);
        for (int sampleIndex = 0; sampleIndex < 2; ++sampleIndex) {
            float t = a + (float(sampleIndex) + 0.5) * span * 0.5;
            vec3 point = origin + direction * t;
            float inside = ftShaft(point);
            if (inside <= 0.001) continue;
            vec2 uv = point.xz * 0.012 + vec2(float(band) * 5.31, float(band) * 1.83);
            float cells = 0.72 * ftNoise(uv) + 0.28 * ftNoise(uv * 2.7);
            float patches = smoothstep(0.48, 0.69, cells);
            float softEdge = 1.0 - smoothstep(5.0, 12.0, abs(point.y - center));
            float opacity = 1.0 - exp(-0.044 * span * 0.5 * patches * softEdge * inside * weight);
            if (opacity <= 0.001) continue;
            float light = ftOpening(point) * mix(0.22, 1.0, ftShadow(local + direction * t));
            color += transmittance * opacity * (cloudAmbient + cloudSun * light);
            transmittance *= 1.0 - opacity;
        }
    }

    // Clear air has no blanket fog. A few shadowed samples add the visible sun shafts.
    vec3 beam = vec3(0.0);
    float stepLength = min(end, 680.0) / 4.0;
    for (int i = 0; i < 4; ++i) {
        float t = (float(i) + 0.5) * stepLength;
        vec3 point = origin + direction * t;
        float exposure = ftShaft(point) * ftOpening(point);
        if (exposure <= 0.001) continue;
        float light = ftShadow(local + direction * t);
        beam += vec3(0.95, 0.83, 0.59) * (0.00037 * stepLength * exposure * light);
    }
    vec3 reflectedSky = vec3(0.37, 0.53, 0.65) * (0.085 * smoothstep(80.0, 380.0, end) * weight);
    return vec4(color + (reflectedSky + beam * weight) * mix(0.7, 1.0, transmittance), transmittance);
}

#endif
