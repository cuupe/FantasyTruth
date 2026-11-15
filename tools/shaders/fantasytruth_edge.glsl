#ifndef FANTASYTRUTH_EDGE_VOLUME
#define FANTASYTRUTH_EDGE_VOLUME

#include "/lib/fantasytruth_edge_bounds.glsl"

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
    if (band == 3) return -1060.0;
    return -1172.0;
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

float ftBeam(vec3 origin, vec3 direction, vec3 local, float rayEnd, vec2 center, vec2 radii) {
    // Find the part of the view ray inside the shaft's elliptical cross section.
    // This keeps thin shafts continuous even when the visible terrain is far away.
    vec2 tilt = vec2(-0.32, 0.21);
    vec2 aperture = origin.xz + (115.0 - origin.y) * tilt - center;
    vec2 apertureStep = direction.xz - direction.y * tilt;
    vec2 start = vec2(aperture.x + aperture.y * 0.25, aperture.y) / radii;
    vec2 step = vec2(apertureStep.x + apertureStep.y * 0.25, apertureStep.y) / radii;
    float a = dot(step, step);
    float nearT = 0.0, farT = rayEnd;
    if (a > 1e-9) {
        float closestT = -dot(start, step) / a;
        vec2 closest = start + step * closestT;
        float hit = 1.0 - dot(closest, closest);
        if (hit <= 0.0) return 0.0;
        float halfSpan = sqrt(hit / a);
        nearT = max(0.0, closestT - halfSpan);
        farT = min(rayEnd, closestT + halfSpan);
    } else if (dot(start, start) >= 1.0) {
        return 0.0;
    }
    if (farT <= nearT) return 0.0;

    float beam = 0.0;
    float stride = (farT - nearT) / 3.0;
    for (int i = 0; i < 3; ++i) {
        float t = nearT + (float(i) + 0.5) * stride;
        vec3 point = origin + direction * t;
        vec2 crossSection = start + step * t;
        float density = 1.0 - smoothstep(0.06, 1.0, dot(crossSection, crossSection));
        float exposure = ftShaft(point) * smoothstep(-800.0, -250.0, point.y) * density;
        if (exposure > 0.001) beam += stride * exposure * mix(0.26, 1.0, ftShadow(local + direction * t));
    }
    return beam;
}

vec4 ftEdgeVolume(vec3 viewPosition, vec3 sun, vec3 ambient, float weight, float daylight) {
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
    vec3 cloudSun = (vec3(0.31, 0.26, 0.17) + sun * 0.012) * daylight;
    for (int layer = 0; layer < 5; ++layer) {
        #ifdef FANTASYTRUTH_MOUTH
            if (layer > 0 && origin.y >= 165.0) break;
        #endif
        if (transmittance < 0.015) break;
        int band = direction.y < 0.0 ? layer : 4 - layer;
        float center = ftCloudHeight(band);
        float thickness = band == 4 ? 12.0 : 9.0;
        float bottom = center - thickness, top = center + thickness;
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
            vec2 uv = point.xz * (band == 4 ? 0.017 : 0.012) + vec2(float(band) * 5.31, float(band) * 1.83);
            float cells = 0.72 * ftNoise(uv) + 0.28 * ftNoise(uv * 2.7);
            float patches = band == 4 ? smoothstep(0.53, 0.71, cells) : smoothstep(0.54, 0.74, cells);
            float softEdge = 1.0 - smoothstep(thickness * 0.4, thickness, abs(point.y - center));
            float density = band == 4 ? 0.084 : 0.048;
            #ifdef FANTASYTRUTH_MOUTH
                density *= mix(1.0, 0.35, smoothstep(25.0, 165.0, origin.y));
            #endif
            float opacity = 1.0 - exp(-density * span * 0.5 * patches * softEdge * inside * weight);
            if (opacity <= 0.001) continue;
            float light = daylight > 0.001 ? ftOpening(point) * mix(0.22, 1.0, ftShadow(local + direction * t)) : 0.0;
            vec3 fill = band == 4 ? vec3(0.65, 0.72, 0.78) : cloudAmbient;
            color += transmittance * opacity * (mix(vec3(0.005, 0.008, 0.014), fill, daylight) + cloudSun * light);
            transmittance *= 1.0 - opacity;
        }
    }

    // Broken openings in the canopy and rock lip shape parallel midday shafts.
    float beam = 0.0;
    float beamEnd = min(end, 1040.0);
    if (daylight > 0.001 && origin.y + min(0.0, direction.y * beamEnd) < 12.0 &&
        origin.y + max(0.0, direction.y * beamEnd) > -800.0) {
        beam = ftBeam(origin, direction, local, beamEnd, vec2(-131.0, 49.0), vec2(39.0, 23.0));
        beam += ftBeam(origin, direction, local, beamEnd, vec2(-65.0, 17.0), vec2(23.0, 17.0)) * 0.86;
        beam += ftBeam(origin, direction, local, beamEnd, vec2(9.0, -52.0), vec2(31.0, 21.0)) * 0.94;
        beam += ftBeam(origin, direction, local, beamEnd, vec2(96.0, -98.0), vec2(22.0, 36.0)) * 0.79;
        beam += ftBeam(origin, direction, local, beamEnd, vec2(-195.0, -91.0), vec2(46.0, 32.0)) * 0.73;
        beam += ftBeam(origin, direction, local, beamEnd, vec2(-211.0, 156.0), vec2(37.0, 27.0)) * 0.63;
    }
    vec3 scatteredSun = vec3(1.0, 0.84, 0.62) * (1.22 * (1.0 - exp(-0.014 * beam)));
    vec3 reflectedSky = mix(vec3(0.004, 0.007, 0.013), vec3(0.37, 0.53, 0.65), daylight)
        * (0.065 * smoothstep(80.0, 380.0, end) * weight);
    return vec4(color + (reflectedSky + scatteredSun * (weight * daylight)) * mix(0.54, 1.0, transmittance), transmittance);
}

#endif
