package dev.fantasytruth.terrain;

/** World-space topography and climate shared by the surface, biomes and decoration. */
public final class AtlasField {
    public enum Biome {
        SHELF_SEA, ESTUARY, FLOODPLAIN, STEPPE, DUNE_SEA, RAINSHADOW_SCRUB, MISTWOOD, MONSOON_FOREST,
        BOREAL_WOODLAND, ALPINE_HEATH, GLACIAL_CIRQUE, KARST_DEPTHS, CLOUD_BARRENS
    }

    public record Column(int height, Biome biome, double slope, double channel) {}

    private final long seed;
    private final AtlasProfile profile;
    private final TerrainField noise;
    private final AtlasRunoff runoff;

    public AtlasField(long seed, AtlasProfile profile) {
        this.seed = seed;
        this.profile = profile;
        noise = new TerrainField(seed);
        runoff = profile.runoffStrength() > 0 ? new AtlasRunoff(profile, this::macro,
                (x, z) -> 0.95 + 0.55 * noise.noise(x, z, 1250, 30)) : null;
    }

    public long seed() { return seed; }

    public AtlasProfile profile() { return profile; }

    private double macro(double x, double z) {
        AtlasProfile.Shape shape = profile.shape();
        if (shape.summitLimit() == 0) {
            return profile.seaLevel() + profile.relief() * (noise.height(x, z) - TerrainField.SEA_LEVEL);
        }
        double land = noise.landmass(x, z) + shape.landBias();
        double base = land * 26 + 86 * smooth(0, 0.78, land) - 98 * smooth(0, 0.72, -land);
        if (land < 0.10 || shape.mountainFrequency() == 0) return profile.seaLevel() + profile.relief() * base;

        double province = noise.noise(x, z, 1980, 32);
        double start = 0.92 - 1.55 * shape.mountainFrequency();
        double uplift = smooth(start, start + 0.40, province) * smooth(0.10, 0.60, land);
        if (uplift == 0) return profile.seaLevel() + profile.relief() * base;
        double ridge = noise.noise(x + 150 * noise.noise(x, z, 590, 33), z, 960, 34);
        return profile.seaLevel() + profile.relief() * (base + uplift * (45 + 380 * smooth(-0.50, 0.80, ridge)));
    }

    public Column column(int x, int z) {
        double sx = x / profile.horizontalScale();
        double sz = z / profile.horizontalScale();
        double base = macro(sx, sz);
        double west = macro(sx - 21, sz);
        double east = macro(sx + 21, sz);
        double south = macro(sx, sz - 21);
        double north = macro(sx, sz + 21);
        double dx = (east - west) / 42;
        double dz = (north - south) / 42;
        double slope = Math.hypot(dx, dz);
        double normalized = 31 / Math.sqrt(0.09 + slope * slope);
        double flowX = dx * normalized;
        double flowZ = dz * normalized;
        double gully = 0.50 * noise.noise(sx, sz, 142, 21)
                + 0.25 * noise.noise(sx - flowX, sz - flowZ, 142, 21)
                + 0.25 * noise.noise(sx + flowX, sz + flowZ, 142, 21);
        double channel = 1 - smooth(0.02, profile.ravineWidth(), Math.abs(gully));
        double incision = profile.ravineDepth() * channel * (0.12 + 0.88 * smooth(0.04, 0.85, slope));
        if (runoff != null) {
            double coast = smooth(profile.seaLevel() - 2, profile.seaLevel() + 12, base);
            double drainage = coast > 0 ? runoff.channel(sx, sz) * coast : 0;
            incision += profile.ravineDepth() * profile.runoffStrength() * drainage;
            channel = Math.max(channel, drainage);
        }

        double hills = profile.foothillDetail() * (17 * noise.noise(sx, sz, 92, 22)
                + 7 * noise.noise(sx, sz, 42, 23) + 2.8 * noise.noise(sx, sz, 17, 24));
        double exposed = smooth(0.18, 0.95, slope) * smooth(-0.25, 0.60, noise.noise(sx, sz, 440, 25));
        double strata = base + hills - incision + 10 * noise.noise(sx, sz, 85, 26);
        double ledges = 2.6 * Math.sin(strata * 0.22) + 1.6 * Math.sin(strata * 0.58)
                + 3.2 * noise.noise(sx, sz, 11, 27);
        double surface = base + hills - incision + profile.rockDetail() * exposed * ledges;
        int height = (int) Math.floor(softSummit(surface));

        double elevation = height - profile.seaLevel();
        double temperature = 0.82 * noise.noise(sx, sz, 2450, 28) - Math.max(0, elevation) / 630
                + profile.temperatureOffset();
        double moisture = 0.73 * noise.noise(sx, sz, 1850, 29) + 0.20 * channel
                - 0.29 * smooth(15, 155, macro(sx - 260, sz) - base) + profile.moistureOffset();
        return new Column(height, classify(elevation, slope, channel, temperature, moisture), slope, channel);
    }

    private double softSummit(double height) {
        int limit = profile.shape().summitLimit();
        double above = height - profile.seaLevel();
        double shoulder = limit * 0.67;
        if (limit == 0 || above <= shoulder) return height;
        double remaining = limit - shoulder;
        return profile.seaLevel() + shoulder - remaining * Math.expm1(-(above - shoulder) / remaining);
    }

    public Biome biomeAt(Column column, long y) {
        if (y < column.height() - 64L) {
            return Biome.KARST_DEPTHS;
        }
        if (y > Math.max(column.height(), profile.seaLevel()) + 112L) {
            return Biome.CLOUD_BARRENS;
        }
        return column.biome();
    }

    private static Biome classify(double elevation, double slope, double channel, double temperature, double moisture) {
        if (elevation < -11) return Biome.SHELF_SEA;
        if (elevation < 6) return Biome.ESTUARY;
        if (temperature < -0.83 && elevation > 145) return Biome.GLACIAL_CIRQUE;
        if (temperature < -0.46 && elevation > 100) return Biome.ALPINE_HEATH;
        if (channel > 0.78 && slope < 0.18 && elevation < 135 && moisture > -0.16) return Biome.FLOODPLAIN;
        if (temperature < -0.25) return Biome.BOREAL_WOODLAND;
        if (moisture < -0.44 && temperature > 0.20) return Biome.DUNE_SEA;
        if (moisture < -0.22) return Biome.RAINSHADOW_SCRUB;
        if (moisture > 0.29 && temperature > 0.20) return Biome.MONSOON_FOREST;
        if (moisture > 0.06) return Biome.MISTWOOD;
        return Biome.STEPPE;
    }

    private static double smooth(double a, double b, double t) {
        double unit = Math.max(0, Math.min(1, (t - a) / (b - a)));
        return unit * unit * (3 - 2 * unit);
    }

    public static long hash(long seed, long x, long y, long z) {
        long h = seed ^ x * 0x632BE59BD9B4E019L ^ y * 0x9E3779B97F4A7C15L ^ z * 0x9E3779B185EBCA87L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    public static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }
}
