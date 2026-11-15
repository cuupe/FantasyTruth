#ifndef FANTASYTRUTH_EDGE_DEPTH
#define FANTASYTRUTH_EDGE_DEPTH

bool ftHasDepth(float depth) {
    return depth > 0.0 && depth < 1.0;
}

vec3 ftSurfacePosition(vec2 uv, float vanillaDepth, float lodDepth, out bool hasSurface) {
    bool vanilla = ftHasDepth(vanillaDepth);
    hasSurface = vanilla;
    vec3 surface = vanilla ? toScreenSpace_DH(uv, vanillaDepth, 1.0) : vec3(0.0);
    #ifdef DISTANT_HORIZONS
        if (ftHasDepth(lodDepth)) {
            vec3 lod = toScreenSpace_DH(uv, 1.0, lodDepth);
            if (!vanilla || lod.z > surface.z) surface = lod;
            hasSurface = true;
        }
    #endif
    return surface;
}

// Store the actual fog pass depth, with a separate value for clear pixels.
// The logarithm preserves precision at both ends of the 1,350-block shaft.
float ftVolumeDepth(vec3 position, bool hasSurface) {
    return hasSurface ? min(log2(1.0 + max(0.0, -position.z)) / 16.0, 0.9375) : 1.0;
}

#endif
