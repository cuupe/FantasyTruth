package dev.fantasytruth.cube;

import dev.fantasytruth.terrain.TerrainField;

/** Generates immutable surface occupancy. The game preview adds caves and surface rules separately. */
public final class CubeTerrain {
    public enum Material { AIR, WATER, ROCK }

    @FunctionalInterface
    public interface Cube {
        Material sample(int x, int y, int z);
    }

    private static final int SIDE = CubePos.SIDE;
    private static final Cube AIR = (x, y, z) -> Material.AIR;
    private static final Cube ROCK = (x, y, z) -> Material.ROCK;
    private final TerrainField field;

    public CubeTerrain(TerrainField field) {
        this.field = field;
    }

    public Cube generate(CubePos pos) {
        long bottom = pos.y() * SIDE;
        if (bottom > TerrainField.MAX_SURFACE) {
            return AIR;
        }
        if (bottom + SIDE - 1 <= TerrainField.MIN_SURFACE) {
            return ROCK;
        }

        short[] surface = new short[SIDE * SIDE];
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int z = 0; z < SIDE; z++) {
            for (int x = 0; x < SIDE; x++) {
                int height = (int) Math.floor(field.height(pos.x() * SIDE + x, pos.z() * SIDE + z));
                surface[z * SIDE + x] = (short) height;
                min = Math.min(min, height);
                max = Math.max(max, height);
            }
        }
        if (bottom + SIDE - 1 <= min) {
            return ROCK;
        }
        if (bottom > max && bottom >= TerrainField.SEA_LEVEL) {
            return AIR;
        }
        return (x, y, z) -> {
            long blockY = bottom + y;
            if (blockY <= surface[z * SIDE + x]) {
                return Material.ROCK;
            }
            return blockY < TerrainField.SEA_LEVEL ? Material.WATER : Material.AIR;
        };
    }
}
