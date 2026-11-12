package dev.fantasytruth.terrain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;

/** Bounded downhill accumulation. Tile halos include every possible contributing path. */
final class AtlasRunoff {
    private static final int TILE = 24;
    private static final int PAD = 2;
    private static final int STORED = TILE + 2 * PAD;
    private static final double DIAGONAL = Math.sqrt(2);

    private record TileKey(int x, int z) {}
    private record Tile(float[] flow, byte[] dx, byte[] dz) {}
    private record CachedTile(int x, int z, Tile tile) {}

    private final int spacing;
    private final int steps;
    private final int seaLevel;
    private final double width;
    private final DoubleBinaryOperator height;
    private final DoubleBinaryOperator rain;
    private final Map<TileKey, Tile> tiles = new LinkedHashMap<>(32, 0.75f, true);
    private volatile CachedTile lastTile;

    AtlasRunoff(AtlasProfile profile, DoubleBinaryOperator height, DoubleBinaryOperator rain) {
        spacing = profile.runoffSpacing();
        steps = profile.runoffSteps();
        seaLevel = profile.seaLevel();
        width = Math.max(0.065, profile.ravineWidth() * 0.72);
        this.height = height;
        this.rain = rain;
    }

    double channel(double x, double z) {
        double cellX = x / spacing;
        double cellZ = z / spacing;
        int gx = (int) Math.floor(cellX);
        int gz = (int) Math.floor(cellZ);
        Tile tile = tile(Math.floorDiv(gx, TILE), Math.floorDiv(gz, TILE));
        int localX = Math.floorMod(gx, TILE) + PAD;
        int localZ = Math.floorMod(gz, TILE) + PAD;
        double result = 0;
        for (int dz = -1; dz <= 2; dz++) {
            for (int dx = -1; dx <= 2; dx++) {
                int index = localX + dx + (localZ + dz) * STORED;
                int vx = tile.dx[index];
                int vz = tile.dz[index];
                double strength = smooth(2.8, 7 + steps * 0.28, tile.flow[index]);
                if (strength == 0 || (vx == 0 && vz == 0)) continue;
                double px = cellX - (gx + dx);
                double pz = cellZ - (gz + dz);
                double t = Math.max(0, Math.min(1, (px * vx + pz * vz) / (vx * vx + vz * vz)));
                double distance = Math.hypot(px - vx * t, pz - vz * t);
                double channel = strength * (1 - smooth(width * 0.25, width, distance));
                result = Math.max(result, channel);
            }
        }
        return result;
    }

    private Tile tile(int x, int z) {
        CachedTile last = lastTile;
        if (last != null && last.x == x && last.z == z) return last.tile;
        synchronized (tiles) {
            TileKey key = new TileKey(x, z);
            Tile result = tiles.get(key);
            if (result == null) {
                result = generate(key);
                if (tiles.size() >= 64) tiles.remove(tiles.keySet().iterator().next());
                tiles.put(key, result);
            }
            lastTile = new CachedTile(x, z, result);
            return result;
        }
    }

    private Tile generate(TileKey key) {
        int halo = steps + 1;
        int side = STORED + 2 * halo;
        int size = side * side;
        int gx0 = key.x * TILE - PAD - halo;
        int gz0 = key.z * TILE - PAD - halo;
        double[] elevations = new double[size];
        double[] water = new double[size];
        int[] downstream = new int[size];
        java.util.Arrays.fill(downstream, -1);
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                double worldX = (gx0 + x) * (double) spacing;
                double worldZ = (gz0 + z) * (double) spacing;
                int i = x + z * side;
                elevations[i] = height.applyAsDouble(worldX, worldZ);
                water[i] = elevations[i] > seaLevel ? rain.applyAsDouble(worldX, worldZ) : 0;
            }
        }
        for (int z = 1; z < side - 1; z++) {
            for (int x = 1; x < side - 1; x++) {
                int i = x + z * side;
                if (elevations[i] <= seaLevel) continue;
                double steepest = 0;
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) continue;
                        int neighbor = i + dx + dz * side;
                        double slope = (elevations[i] - elevations[neighbor]) / (dx != 0 && dz != 0 ? DIAGONAL : 1);
                        if (slope > steepest) {
                            downstream[i] = neighbor;
                            steepest = slope;
                        }
                    }
                }
            }
        }
        double[] total = water.clone();
        double[] next = new double[size];
        for (int step = 0; step < steps; step++) {
            java.util.Arrays.fill(next, 0);
            for (int i = 0; i < size; i++) {
                if (downstream[i] >= 0) next[downstream[i]] += water[i] * 0.94;
            }
            for (int i = 0; i < size; i++) total[i] += next[i];
            double[] swap = water;
            water = next;
            next = swap;
        }
        Tile output = new Tile(new float[STORED * STORED], new byte[STORED * STORED], new byte[STORED * STORED]);
        for (int z = 0; z < STORED; z++) {
            for (int x = 0; x < STORED; x++) {
                int index = (z + halo) * side + x + halo;
                int out = z * STORED + x;
                output.flow[out] = (float) total[index];
                int to = downstream[index];
                if (to >= 0) {
                    output.dx[out] = (byte) (to % side - index % side);
                    output.dz[out] = (byte) (to / side - index / side);
                }
            }
        }
        return output;
    }

    private static double smooth(double low, double high, double x) {
        double t = Math.max(0, Math.min(1, (x - low) / (high - low)));
        return t * t * (3 - 2 * t);
    }
}
