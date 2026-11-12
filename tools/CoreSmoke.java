import dev.fantasytruth.cube.CubePos;
import dev.fantasytruth.cube.CubeTerrain;
import dev.fantasytruth.cube.CubeWindow;
import dev.fantasytruth.terrain.TerrainField;
import java.util.Arrays;

class CoreSmoke {
    public static void main(String[] args) {
        var field = new TerrainField(72019);
        var terrain = new CubeTerrain(field);
        var negative = CubePos.atBlock(-1, -17, -16);
        require(negative.equals(new CubePos(-1, -2, -1)), "Negative cube position");
        require(CubePos.local(-17) == 15 && CubePos.local(-16) == 0, "Negative local position");
        require(terrain.generate(CubePos.atBlock(0, Long.MIN_VALUE, 0)).sample(0, 0, 0)
                == CubeTerrain.Material.ROCK, "Extreme depth");
        require(terrain.generate(CubePos.atBlock(0, Long.MAX_VALUE, 0)).sample(0, 15, 0)
                == CubeTerrain.Material.AIR, "Extreme height");

        var window = new CubeWindow(terrain, 2, 1);
        window.moveTo(negative);
        require(window.generatePending(1) == 1 && window.getLoaded(negative) != null, "Center loads first");
        require(window.generatePending(100) == 74 && window.loadedCount() == 75, "Bounded residency");
        window.moveTo(new CubePos(0, -2, -1));
        require(window.loadedCount() == 60, "Keep overlapping cubes");
        require(window.generatePending(100) == 15, "Generate only the new strip");
        var high = CubePos.atBlock(-1, 1_000_000_000_000L, -16);
        window.moveTo(high);
        require(window.loadedCount() == 0 && window.generatePending(2) == 2 && window.getLoaded(high) != null,
                "Vertical jump does not generate intermediate cubes");

        checkSlope(field);
    }

    private static void checkSlope(TerrainField field) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double[] interior = new double[512 * 512];
        double[] edges = new double[interior.length];
        int index = 0;
        long started = System.nanoTime();
        for (int z = -4096; z < 4096; z += 16) {
            for (int x = -4096; x < 4096; x += 16) {
                double left = field.height(x - 1, z + 5);
                double center = field.height(x, z + 5);
                double right = field.height(x + 1, z + 5);
                min = Math.min(min, center);
                max = Math.max(max, center);
                edges[index] = Math.abs(center - left);
                interior[index++] = Math.abs(right - center);
            }
        }
        double elapsed = (System.nanoTime() - started) / 1e6;
        Arrays.sort(interior);
        Arrays.sort(edges);
        double p99Inside = interior[(int) (0.99 * interior.length)];
        double p99Edge = edges[(int) (0.99 * edges.length)];
        require(max - min > 800, "Large vertical relief");
        require(p99Edge < 3.5 && Math.abs(p99Inside - p99Edge) < 0.1, "No chunk-boundary slope spike");
        require(new TerrainField(72019).height(-129, 33) == field.height(-129, 33), "Seed is repeatable");
        require(new TerrainField(72020).height(-129, 33) != field.height(-129, 33), "Seeds vary");
        System.out.printf("Surface %.1f..%.1f, p99 steps %.2f inside / %.2f across edges, max %.2f; %d samples in %.1f ms%n",
                min, max, p99Inside, p99Edge, edges[edges.length - 1], index * 3, elapsed);
    }

    private static void require(boolean passed, String check) {
        if (!passed) {
            throw new AssertionError(check);
        }
    }
}
