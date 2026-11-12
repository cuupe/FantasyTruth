package dev.fantasytruth.terrain;

/** Chooses an inland spawn area without generating any chunks during the search. */
public final class AtlasSpawn {
    public record Site(int x, int z, int surface, int drySamples) {}
    private record Candidate(Site site, double score) {}
    private static final int STEP = 128;
    private static final int MAX_RADIUS = 16_384;

    public static Site find(AtlasField terrain, int originX, int originZ) {
        int centerX = (originX & ~15) + 8;
        int centerZ = (originZ & ~15) + 8;
        Candidate best = null;
        for (int radius = 0; radius <= MAX_RADIUS; radius += STEP) {
            Candidate ring = null;
            for (int offset = -radius; offset <= radius; offset += STEP) {
                ring = better(ring, inspect(terrain, centerX + offset, centerZ - radius));
                if (radius == 0) continue;
                ring = better(ring, inspect(terrain, centerX + offset, centerZ + radius));
                if (Math.abs(offset) < radius) {
                    ring = better(ring, inspect(terrain, centerX - radius, centerZ + offset));
                    ring = better(ring, inspect(terrain, centerX + radius, centerZ + offset));
                }
            }
            if (ring != null && ring.site.drySamples() >= 7 && ring.score >= 0) return ring.site;
            best = better(best, ring);
        }
        return best == null ? null : best.site;
    }

    private static Candidate inspect(AtlasField terrain, int x, int z) {
        int sea = terrain.profile().seaLevel();
        AtlasField.Column column = terrain.column(x, z);
        int elevation = column.height() - sea;
        if (elevation < 4 || column.slope() > 0.40) return null;

        int localMin = column.height();
        int localMax = column.height();
        for (int dz = -7; dz <= 7; dz += 14) {
            for (int dx = -7; dx <= 7; dx += 14) {
                int height = terrain.column(x + dx, z + dz).height();
                localMin = Math.min(localMin, height);
                localMax = Math.max(localMax, height);
            }
        }
        if (localMin < sea + 2 || localMax - localMin > 10) return null;

        int dry = 0;
        for (int dz = -96; dz <= 96; dz += 96) {
            for (int dx = -96; dx <= 96; dx += 96) {
                if (dx == 0 && dz == 0) continue;
                if (terrain.column(x + dx, z + dz).height() >= sea + 4) dry++;
            }
        }
        double score = dry * 5 - Math.abs(elevation - 36) * 0.22 - column.slope() * 42
                - (localMax - localMin) * 1.4;
        return new Candidate(new Site(x, z, column.height(), dry), score);
    }

    private static Candidate better(Candidate previous, Candidate next) {
        return next != null && (previous == null || next.score > previous.score) ? next : previous;
    }

    private AtlasSpawn() {}
}
