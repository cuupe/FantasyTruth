package dev.fantasytruth.client;

import dev.fantasytruth.atlas.AtlasChunkGenerator;
import dev.fantasytruth.terrain.AtlasField;
import dev.fantasytruth.terrain.AtlasProfile;

final class AtlasStudioPreview {
    static final int SIZE = 144;
    private static final int[] BIOMES = {0x206994, 0xa1b58b, 0x6ba786, 0xb1b85d, 0xd9b16c, 0xae8957,
            0x3c8a72, 0x2a7b48, 0x557d6c, 0x8b9a78, 0xc7dfed, 0x515c6b, 0xc3daf0};

    record Data(int[] height, int[] biomes, int low, int high, long seed) {}

    static Data sample(AtlasProfile profile, long seed, int centerX, int centerZ, int span) {
        AtlasField terrain = new AtlasField(AtlasChunkGenerator.terrainSeed(seed), profile);
        int[] height = new int[SIZE * SIZE];
        int[] biomes = new int[height.length];
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        double step = span / (double) SIZE;
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int wx = (int) Math.floor(centerX + (x + 0.5 - SIZE / 2.0) * step);
                int wz = (int) Math.floor(centerZ + (z + 0.5 - SIZE / 2.0) * step);
                AtlasField.Column column = terrain.column(wx, wz);
                int index = x + z * SIZE;
                height[index] = column.height();
                biomes[index] = BIOMES[column.biome().ordinal()];
                low = Math.min(low, column.height());
                high = Math.max(high, column.height());
            }
        }
        int[] relief = new int[height.length];
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int i = x + z * SIZE;
                int y = height[i];
                double dx = (height[Math.min(SIZE - 1, x + 1) + z * SIZE]
                        - height[Math.max(0, x - 1) + z * SIZE]) / (2 * step);
                double dz = (height[x + Math.min(SIZE - 1, z + 1) * SIZE]
                        - height[x + Math.max(0, z - 1) * SIZE]) / (2 * step);
                double shade = Math.max(0.5, Math.min(1.38, 0.94 + (dx + dz) * 0.62));
                if (y < profile.seaLevel()) {
                    relief[i] = rgba(mix(0x1e456b, 0x519cb2,
                            clamp(1 - (profile.seaLevel() - y) / 150.0)), 1);
                } else {
                    double altitude = clamp((y - profile.seaLevel()) / Math.max(500.0, high - profile.seaLevel()));
                    int land = altitude < 0.60 ? mix(0x579274, 0xa8a577, altitude / 0.60)
                            : mix(0xa8a577, 0xe3edf6, (altitude - 0.60) / 0.40);
                    relief[i] = rgba(land, shade);
                }
                biomes[i] = rgba(biomes[i], 0.85 + 0.15 * shade);
            }
        }
        return new Data(relief, biomes, low, high, seed);
    }

    private static double clamp(double x) { return Math.max(0, Math.min(1, x)); }

    private static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int rgba(int rgb, double shade) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * shade));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * shade));
        int b = Math.min(255, (int) ((rgb & 255) * shade));
        return 0xff000000 | (b << 16) | (g << 8) | r;
    }

    private AtlasStudioPreview() {}
}
