package dev.fantasytruth.cube;

public record CubePos(int x, long y, int z) {
    public static final int SIDE = 16;
    static final int MIN_XZ = Math.floorDiv(Integer.MIN_VALUE, SIDE);
    static final int MAX_XZ = Math.floorDiv(Integer.MAX_VALUE, SIDE);
    static final long MIN_Y = Math.floorDiv(Long.MIN_VALUE, SIDE);
    static final long MAX_Y = Math.floorDiv(Long.MAX_VALUE, SIDE);

    public CubePos {
        if (!valid(x, y, z)) {
            throw new IllegalArgumentException("Cube position outside signed block coordinate range");
        }
    }

    public static CubePos atBlock(int x, long y, int z) {
        return new CubePos(Math.floorDiv(x, SIDE), Math.floorDiv(y, SIDE), Math.floorDiv(z, SIDE));
    }

    public static int local(long blockCoordinate) {
        return Math.floorMod(blockCoordinate, SIDE);
    }

    static boolean valid(int x, long y, int z) {
        return x >= MIN_XZ && x <= MAX_XZ && y >= MIN_Y && y <= MAX_Y && z >= MIN_XZ && z <= MAX_XZ;
    }
}
