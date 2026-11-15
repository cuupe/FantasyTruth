#ifndef FANTASYTRUTH_EDGE_VIEW
#define FANTASYTRUTH_EDGE_VIEW

float ftCaveWeight(vec3 point) {
    return (1.0 - smoothstep(482.0, 516.0, length(point.xz - vec2(8.0))))
           * (1.0 - smoothstep(-12.0, 6.0, point.y))
           * smoothstep(-1470.0, -1360.0, point.y);
}

vec3 ftCaveBackground(vec3 sky, vec3 camera, vec3 ray, float daylight) {
    float weight = ftCaveWeight(camera);
    if (weight <= 0.0) return sky;
    vec2 offset = camera.xz - vec2(8.0);
    float mouthDistance = max(0.0, 155.0 - camera.y) / max(ray.y, 0.0001);
    float mouthRadius = length(offset + ray.xz * mouthDistance);
    float throughMouth = smoothstep(0.0, 0.04, ray.y)
                        * (1.0 - smoothstep(442.0, 504.0, mouthRadius));
    float down = 1.0 - smoothstep(-0.78, -0.10, ray.y);
    float depth = 1.0 - smoothstep(-1320.0, -250.0, camera.y);
    vec3 air = mix(vec3(0.26, 0.36, 0.41), vec3(0.12, 0.22, 0.21), 0.60 * depth + 0.36 * down);
    vec3 night = mix(vec3(0.004, 0.007, 0.013), vec3(0.002, 0.004, 0.007), down);
    return mix(sky, mix(night, air, daylight), weight * (1.0 - throughMouth));
}

vec3 ftCaveFill(vec3 indirect, vec3 position, float skylight, float daylight) {
    float weight = ftCaveWeight(position);
    vec3 bounce = vec3(0.19, 0.23, 0.19) * mix(0.61, 1.0, skylight);
    return mix(indirect, max(indirect, bounce * mix(0.025, 1.0, daylight)), weight);
}

#endif
