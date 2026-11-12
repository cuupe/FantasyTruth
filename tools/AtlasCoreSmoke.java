import dev.fantasytruth.atlas.Altitude;
import dev.fantasytruth.terrain.AtlasCaves;
import dev.fantasytruth.terrain.AtlasField;
import dev.fantasytruth.terrain.AtlasProfile;
import dev.fantasytruth.terrain.AtlasSpawn;
import dev.fantasytruth.terrain.TerrainField;
import java.util.EnumSet;

class AtlasCoreSmoke {
    public static void main(String[] args) {
        long[] checks = {-Altitude.LIMIT, -1_000_000_000_000L, -897, -896, -1, 0, 895, 896,
                1_000_000_000_000L, Altitude.LIMIT};
        for (long y : checks) {
            Altitude p = Altitude.at(y);
            require(p.worldY() == y && p.localY() >= -Altitude.HALF && p.localY() < Altitude.HALF, "signed altitude " + y);
        }
        require(Altitude.at(896).page() == 1 && Altitude.at(-897).page() == -1, "symmetric page boundaries");
        var a = new AtlasCaves(72019, -16, -1_000_000_000_000L, 0, 1);
        var b = new AtlasCaves(72019, -16, -1_000_000_000_000L, 0, 1);
        require(a.density(15, 15, 15) == b.density(15, 15, 15), "absolute cavern determinism");

        AtlasField field = new AtlasField(72019, AtlasProfile.DEFAULT);
        TerrainField old = new TerrainField(72019);
        AtlasField raised = new AtlasField(72019, new AtlasProfile(4062, 1, 1, 1, 1, 36, 0.25, 1,
                0, 0, 0.85, 144, 0.65));
        require(raised.column(-837, 1203).height() - field.column(-837, 1203).height() == 4000,
                "profile can move the same terrain through multiple pages");
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        double details = 0;
        double oldDetails = 0;
        int count = 0;
        var biomes = EnumSet.noneOf(AtlasField.Biome.class);
        for (int z = -6000; z <= 6000; z += 48) {
            for (int x = -6000; x <= 6000; x += 48) {
                var c = field.column(x, z);
                min = Math.min(min, c.height());
                max = Math.max(max, c.height());
                biomes.add(c.biome());
                details += Math.abs(field.column(x - 8, z).height() + field.column(x + 8, z).height() - 2 * c.height());
                oldDetails += Math.abs(old.height(x - 8, z) + old.height(x + 8, z) - 2 * old.height(x, z));
                count++;
            }
        }
        require(max - min > 800, "large relief");
        require(biomes.size() >= 9, "surface biome diversity");
        require(details > oldDetails * 2, "additional local detail");
        System.out.printf("surface %d..%d, %d biomes in %d samples, 8-block curvature %.2f vs %.2f%n",
                min, max, biomes.size(), count, details / count, oldDetails / count);
        verifyRunoff();
        verifyBalancedTerrain();
        verifyCaveMask();
    }

    private static void verifyBalancedTerrain() {
        long[] seeds = {0, 1, -1, 17, 1337, 72019, 123456789, 987654321};
        int count = 0, oldLand = 0, newLand = 0, oldPeaks = 0, newPeaks = 0, oceanOrigins = 0;
        int oldMax = Integer.MIN_VALUE, newMax = Integer.MIN_VALUE;
        var standard = AtlasProfile.DEFAULT.withShape(AtlasProfile.Shape.BALANCED);
        for (long seed : seeds) {
            var legacy = new AtlasField(seed, AtlasProfile.DEFAULT);
            var balanced = new AtlasField(seed, standard);
            if (balanced.column(8, 8).height() < standard.seaLevel()) oceanOrigins++;
            var spawn = AtlasSpawn.find(balanced, 0, 0);
            require(spawn != null && spawn.drySamples() >= 7 && spawn.surface() >= standard.seaLevel() + 4,
                    "inland spawn for seed " + seed);
            for (int z = -6144; z <= 6144; z += 128) {
                for (int x = -6144; x <= 6144; x += 128) {
                    int before = legacy.column(x, z).height();
                    int after = balanced.column(x, z).height();
                    count++;
                    if (before >= standard.seaLevel()) oldLand++;
                    if (after >= standard.seaLevel()) newLand++;
                    if (before > standard.seaLevel() + 300) oldPeaks++;
                    if (after > standard.seaLevel() + 300) newPeaks++;
                    oldMax = Math.max(oldMax, before);
                    newMax = Math.max(newMax, after);
                    require(after <= standard.seaLevel() + standard.shape().summitLimit(), "summit limit");
                }
            }
        }
        require(newLand > oldLand && newPeaks * 3 < oldPeaks && oceanOrigins > 0, "balanced terrain distribution");
        System.out.printf(java.util.Locale.ROOT, "balanced: %d samples, land %.1f%% -> %.1f%%, above sea+300 %.2f%% -> %.2f%%, max Y %d -> %d; %d/%d dry spawns (%d ocean origins)%n",
                count, 100.0 * oldLand / count, 100.0 * newLand / count, 100.0 * oldPeaks / count, 100.0 * newPeaks / count,
                oldMax, newMax, seeds.length, seeds.length, oceanOrigins);
    }

    private static void verifyCaveMask() {
        for (long y : new long[]{-1_000_000_000_000L, -416, -16, 0, 320, 1_000_000_000_000L}) {
            for (double strength : new double[]{0.8, 1.0, 2.0}) {
                var caves = new AtlasCaves(72019, -48, y, 32, strength);
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        for (int dy = 0; dy < 16; dy++) {
                            require(caves.isOpen(x, dy, z) == (caves.density(x, dy, z) > 0.008), "cave mask equivalence");
                        }
                    }
                }
            }
        }
    }

    private static void verifyRunoff() {
        var dry = new AtlasProfile(62, 1, 1, 1, 0, 36, 0.25, 1, 0, 0, 0.85, 144, 0.65);
        var wet = new AtlasProfile(62, 1, 1, 1, 0, 36, 0.25, 1, 0, 0, 0.85, 144, 0.65, 1.4, 48, 14);
        var baseline = new AtlasField(72019, dry);
        var runoff = new AtlasField(72019, wet);
        var reordered = new AtlasField(72019, wet);
        reordered.column(2800, -3000);
        int eroded = 0;
        int deepest = 0;
        for (int z = -1800; z <= 1800; z += 27) {
            for (int x = -1800; x <= 1800; x += 27) {
                int delta = baseline.column(x, z).height() - runoff.column(x, z).height();
                require(delta >= 0, "runoff only incises when rock ledges are disabled");
                if (delta > 0) eroded++;
                deepest = Math.max(deepest, delta);
            }
            for (int x : new int[]{-1152, 0, 1152}) {
                require(runoff.column(x, z).equals(reordered.column(x, z)), "deterministic tile cache");
                require(Math.abs(runoff.column(x, z).height() - runoff.column(x - 1, z).height()) <= 18,
                        "drainage tile seam slope");
            }
        }
        require(eroded > 100 && deepest > 12, "runoff cuts a visible drainage network");
        System.out.printf("runoff changed %d sampled columns, maximum incision %d blocks%n", eroded, deepest);
    }

    private static void require(boolean passed, String label) {
        if (!passed) throw new AssertionError(label);
    }
}
