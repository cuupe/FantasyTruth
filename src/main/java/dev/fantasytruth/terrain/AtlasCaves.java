package dev.fantasytruth.terrain;

/** Absolute-coordinate cavern field. Adjacent cubes share exactly the same lattice boundary. */
public final class AtlasCaves {
    private static final int STEP = 4;
    private static final int NODES = 5;
    private static final double OPEN_THRESHOLD = 0.008;
    private final double[] nodes = new double[NODES * NODES * NODES];
    private long candidates;
    private long emptyCells;

    public AtlasCaves(long seed, int originX, long originY, int originZ, double strength) {
        for (int y = 0; y < NODES; y++) {
            for (int z = 0; z < NODES; z++) {
                for (int x = 0; x < NODES; x++) {
                    int blockX = originX + x * STEP;
                    long blockY = originY + y * STEP;
                    int blockZ = originZ + z * STEP;
                    double a = Math.abs(value(seed ^ 19, blockX, blockY, blockZ, 63, 37));
                    double b = Math.abs(value(seed ^ 31, blockX, blockY, blockZ, 45, 29));
                    double tubes = 0.16 * strength - Math.max(a, b);
                    double chambers = value(seed ^ 61, blockX, blockY, blockZ, 82, 39) - (0.85 - 0.15 * strength);
                    nodes[index(x, y, z)] = Math.max(tubes, chambers);
                }
            }
        }
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    int openCorners = 0;
                    for (int corner = 0; corner < 8; corner++) {
                        if (nodes[index(x + (corner & 1), y + ((corner >> 1) & 1), z + (corner >> 2))] > OPEN_THRESHOLD) {
                            openCorners++;
                        }
                    }
                    long bit = 1L << (y * 16 + z * 4 + x);
                    if (openCorners > 0) candidates |= bit;
                    if (openCorners == 8) emptyCells |= bit;
                }
            }
        }
    }

    public boolean hasOpenCells() { return candidates != 0; }

    public boolean isOpen(int x, int y, int z) {
        long bit = 1L << ((y >> 2) * 16 + (z >> 2) * 4 + (x >> 2));
        return (candidates & bit) != 0 && ((emptyCells & bit) != 0 || density(x, y, z) > OPEN_THRESHOLD);
    }

    public double density(int x, int y, int z) {
        int ix = x / STEP;
        int iy = y / STEP;
        int iz = z / STEP;
        int i = index(ix, iy, iz);
        double dx = (x % STEP) / (double) STEP;
        double dy = (y % STEP) / (double) STEP;
        double dz = (z % STEP) / (double) STEP;
        double low = layer(i, dx, dz);
        double high = layer(i + NODES * NODES, dx, dz);
        return lerp(low, high, dy);
    }

    private double layer(int i, double dx, double dz) {
        return lerp(lerp(nodes[i], nodes[i + 1], dx), lerp(nodes[i + NODES], nodes[i + NODES + 1], dx), dz);
    }

    private static int index(int x, int y, int z) {
        return (y * NODES + z) * NODES + x;
    }

    private static double value(long seed, int x, long y, int z, int horizontal, int vertical) {
        int ix = Math.floorDiv(x, horizontal);
        long iy = Math.floorDiv(y, vertical);
        int iz = Math.floorDiv(z, horizontal);
        double dx = fade(Math.floorMod(x, horizontal) / (double) horizontal);
        double dy = fade(Math.floorMod(y, vertical) / (double) vertical);
        double dz = fade(Math.floorMod(z, horizontal) / (double) horizontal);
        double low = plane(seed, ix, iy, iz, dx, dz);
        double high = plane(seed, ix, iy + 1, iz, dx, dz);
        return 2 * lerp(low, high, dy) - 1;
    }

    private static double plane(long seed, int x, long y, int z, double dx, double dz) {
        double a = AtlasField.unit(AtlasField.hash(seed, x, y, z));
        double b = AtlasField.unit(AtlasField.hash(seed, x + 1L, y, z));
        double c = AtlasField.unit(AtlasField.hash(seed, x, y, z + 1L));
        double d = AtlasField.unit(AtlasField.hash(seed, x + 1L, y, z + 1L));
        return lerp(lerp(a, b, dx), lerp(c, d, dx), dz);
    }

    private static double fade(double v) { return v * v * (3 - 2 * v); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
