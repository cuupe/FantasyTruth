package dev.fantasytruth.terrain;

/** A continuous, world-coordinate relief field shared by column and cube generators. */
public final class TerrainField {
    public static final int SEA_LEVEL = 62;
    public static final int MIN_SURFACE = -46;
    public static final int MAX_SURFACE = 1007;
    private final long seed;

    public TerrainField(long seed) {
        this.seed = seed;
    }

    public double landmass(double x, double z) {
        double warpedX = x + 205 * noise(x, z, 1320, 1);
        double warpedZ = z + 205 * noise(x, z, 1320, 2);
        return 0.79 * noise(warpedX, warpedZ, 1880, 3)
                + 0.21 * noise(warpedX, warpedZ, 730, 4);
    }

    public double height(double x, double z) {
        double land = landmass(x, z);
        double province = noise(x, z, 2140, 5);
        double uplift = smooth(-0.04, 0.37, land) * smooth(-0.34, 0.47, province);
        double ridgeX = x + 155 * noise(x, z, 820, 6);
        double ridgeZ = z + 155 * noise(x, z, 820, 7);
        double ridge = noise(ridgeX, ridgeZ, 1100, 8);
        double ridgeProfile = smooth(-0.62, 0.61, ridge);
        double relief = uplift * (72 + 660 * ridgeProfile);

        // Valleys have broad shoulders and a narrower thalweg. Both follow the
        // same warped field so their transitions survive changes in sampling scale.
        double channelDistance = Math.abs(noise(ridgeX, ridgeZ, 630, 9));
        double valley = (12 + 96 * uplift) * (1 - smooth(0.025, 0.42, channelDistance));
        double incision = (4 + 24 * uplift) * (1 - smooth(0, 0.12, channelDistance));
        double foothills = (9 + 19 * uplift) * noise(x, z, 240, 10);
        double fineRelief = (3 + 3 * uplift) * noise(x, z, 58, 11);

        // Expose scarps selectively on high ridges. A bounded, continuous lip
        // gives steep rock faces without using discontinuities or chunk-local RNG.
        double hardRock = uplift * smooth(0.30, 0.80, province) * smooth(0.12, 0.55, ridge);
        double ledge = 29 * hardRock * smooth(0.02, 0.095, noise(ridgeX, ridgeZ, 138, 12));
        double oceanBasin = 120 * smooth(0.05, 0.66, -land);
        double elevation = SEA_LEVEL + 150 * land + relief + ledge + foothills + fineRelief
                - oceanBasin - valley - incision;
        // Preserve an open ocean above the vanilla deep-lava aquifer threshold.
        double waterDepth = SEA_LEVEL - MIN_SURFACE;
        return elevation >= SEA_LEVEL ? elevation
                : SEA_LEVEL + waterDepth * Math.tanh((elevation - SEA_LEVEL) / waterDepth);
    }

    public double noise(double x, double z, double wavelength, long channel) {
        double gridX = x / wavelength;
        double gridZ = z / wavelength;
        long cellX = (long) Math.floor(gridX);
        long cellZ = (long) Math.floor(gridZ);
        double blendX = fade(gridX - cellX);
        double blendZ = fade(gridZ - cellZ);
        long salt = seed ^ channel * 0x9E3779B97F4A7C15L;
        double north = lerp(value(cellX, cellZ, salt), value(cellX + 1, cellZ, salt), blendX);
        double south = lerp(value(cellX, cellZ + 1, salt), value(cellX + 1, cellZ + 1, salt), blendX);
        return lerp(north, south, blendZ);
    }

    private static double value(long x, long z, long salt) {
        long hash = x * 0x632BE59BD9B4E019L ^ z * 0x9E3779B185EBCA87L ^ salt;
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        return ((hash ^ (hash >>> 31)) >>> 11) * 0x1.0p-52 - 1;
    }

    private static double smooth(double low, double high, double value) {
        return fade(Math.max(0, Math.min(1, (value - low) / (high - low))));
    }

    private static double fade(double t) {
        return t * t * t * (t * (6 * t - 15) + 10);
    }

    private static double lerp(double from, double to, double t) {
        return from + t * (to - from);
    }
}
