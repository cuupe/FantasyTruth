#ifndef FANTASYTRUTH_EDGE_BOUNDS
#define FANTASYTRUTH_EDGE_BOUNDS

float ftDaylight(float sunHeight) {
    return smoothstep(-0.15, 0.29, sunHeight);
}

#ifdef FANTASYTRUTH_MOUTH
uniform int bedrockLevel;

bool ftMouthCamera(vec3 eye) {
    return bedrockLevel == -512 && eye.y <= 620.0 && eye.y >= -32.0
        && distance(eye.xz, vec2(8.0)) <= 690.0;
}

float ftMouthWeight(vec3 eye, vec3 ray, float surfaceDistance) {
    if (!ftMouthCamera(eye) || ray.y > -0.12) return 0.0;
    float entry = max(0.0, eye.y - 155.0) / -ray.y;
    vec2 atMouth = eye.xz + ray.xz * entry;
    float inside = 1.0 - smoothstep(420.0, 503.0, length(atMouth - vec2(8.0)));
    return inside * smoothstep(0.0, 25.0, surfaceDistance - entry)
                  * smoothstep(0.12, 0.40, -ray.y);
}
#endif

float ftShaft(vec3 point) {
    float rim = 1.0 - smoothstep(444.0, 508.0, length(point.xz - vec2(8.0)));
    return rim * (1.0 - smoothstep(-40.0, 12.0, point.y)) * smoothstep(-1510.0, -1350.0, point.y);
}

#endif
