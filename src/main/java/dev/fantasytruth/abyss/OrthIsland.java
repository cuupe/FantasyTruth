package dev.fantasytruth.abyss;

import dev.fantasytruth.terrain.TerrainField;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/** The authored island and its ocean basin use coordinates relative to the Abyss. */
public final class OrthIsland {
    private static final ResourceKey<NoiseGeneratorSettings> SETTINGS = ResourceKey.create(
            Registries.NOISE_SETTINGS, new ResourceLocation("stratacubes", "expedition_orth"));

    private static final ResourceKey<NoiseGeneratorSettings> TERRACED_SETTINGS = ResourceKey.create(
            Registries.NOISE_SETTINGS, new ResourceLocation("stratacubes", "expedition_orth_terraced"));

    private static final ResourceKey<NoiseGeneratorSettings> SHAFT_SETTINGS = ResourceKey.create(
            Registries.NOISE_SETTINGS, new ResourceLocation("stratacubes", "expedition_orth_shaft"));
    static final int SHAFT_RADIUS = 499;

    public static final int SEA = 78;
    public static final int OCEAN_RADIUS = 4352;
    private static final int BLEND_RADIUS = 5120;
    private static final int GRID_RADIUS = 1664;
    private static final int GRID_STEP = 4;
    private static final int GRID_SIDE = 1 + GRID_RADIUS * 2 / GRID_STEP;
    private static final TerrainField FIELD = new TerrainField(0x4f525448L);

    public static boolean enabled(ChunkGenerator generator) {
        return generator instanceof NoiseBasedChunkGenerator noise
                && (noise.generatorSettings().is(SETTINGS) || noise.generatorSettings().is(TERRACED_SETTINGS) || noise.generatorSettings().is(SHAFT_SETTINGS));
    }

    public static boolean terraced(ChunkGenerator generator) {
        return generator instanceof NoiseBasedChunkGenerator noise
                && (noise.generatorSettings().is(TERRACED_SETTINGS) || noise.generatorSettings().is(SHAFT_SETTINGS));
    }

    public static boolean vertical(ChunkGenerator generator) {
        return generator instanceof NoiseBasedChunkGenerator noise && noise.generatorSettings().is(SHAFT_SETTINGS);
    }

    public static boolean inShaft(double x, double z) {
        return shaftColumn(localX(Mth.floor(x)), localZ(Mth.floor(z)));
    }

    static boolean shaftColumn(int x, int z) {
        return Math.abs(x) < 650 && Math.abs(z) < 650 && OrthRelief.pitDistance(x, z) < SHAFT_RADIUS;
    }

    public static int localX(int x) { return x - AbyssLayer.CENTER_X; }
    public static int localZ(int z) { return z - AbyssLayer.CENTER_Z; }

    public static boolean authored(int x, int z) {
        return Math.abs(x) < OCEAN_RADIUS && Math.abs(z) < OCEAN_RADIUS
                && x * x + z * z < OCEAN_RADIUS * OCEAN_RADIUS;
    }

    public static double distance(int x, int z) {
        return Math.sqrt((double) x * x + (double) z * z);
    }

    static double woodland(int x, int z) {
        return FIELD.noise(x, z, 196, 12) + 0.4 * FIELD.noise(x, z, 61, 13);
    }

    public static int elevation(int x, int z) {
        if (Math.abs(x) >= GRID_RADIUS || Math.abs(z) >= GRID_RADIUS) return oceanFloor(x, z);
        return Mth.floor(sample(Profiles.HEIGHT, x, z));
    }

    public static int elevation(int x, int z, boolean terraced) {
        return terraced ? OrthRelief.elevation(x, z) : elevation(x, z);
    }

    static double pitDistance(int x, int z, boolean terraced) {
        return terraced ? OrthRelief.pitDistance(x, z) : pitDistance(x, z);
    }

    static double pitDistance(int x, int z) {
        if (Math.abs(x) >= 600 || Math.abs(z) >= 600) return distance(x, z);
        return sample(Profiles.PIT, x, z);
    }

    static double pitRadius(int y) {
        double ledges = 6 * Math.sin(y * 0.066) + 4 * Math.sin(y * 0.14);
        if (y >= 257) return 486 + ledges;
        if (y >= 82) return Mth.lerp((y - 82) / 175.0, 318, 486) + ledges;
        if (y >= -208) return Mth.lerp((y + 208) / 290.0, 53, 318) + ledges;
        return 53 + ledges;
    }

    static double pitRadius(int y, boolean terraced) {
        return terraced ? OrthRelief.pitRadius(y) : pitRadius(y);
    }

    public static boolean dry(int worldX, int y, int worldZ, boolean terraced, boolean vertical) {
        int x = localX(worldX);
        int z = localZ(worldZ);
        if (vertical) return shaftColumn(x, z);
        int edge = terraced ? 660 : 540;
        return Math.abs(x) < edge && Math.abs(z) < edge && pitDistance(x, z, terraced) < pitRadius(y, terraced) + 24;
    }

    static double density(DensityFunction terrain, DensityFunction.FunctionContext point, boolean terraced, boolean vertical) {
        int x = localX(point.blockX());
        int z = localZ(point.blockZ());
        int y = point.blockY();
        if (Math.abs(x) >= BLEND_RADIUS || Math.abs(z) >= BLEND_RADIUS || y <= (vertical ? -512 : -480)) {
            return terrain.compute(point);
        }
        int radiusSquared = x * x + z * z;
        if (radiusSquared >= BLEND_RADIUS * BLEND_RADIUS) return terrain.compute(point);
        boolean inBasin = radiusSquared <= OCEAN_RADIUS * OCEAN_RADIUS;
        if (inBasin && y >= (terraced ? 832 : 384)) return -1;
        double surface = (elevation(x, z, terraced) + 0.5 - y) / 22;
        int pitEdge = terraced ? 650 : 570;
        double opening = vertical ? SHAFT_RADIUS : pitRadius(y, terraced);
        double bottom = vertical ? -505 : -435;
        double island = radiusSquared < pitEdge * pitEdge
                ? Math.min(surface, Math.max((pitDistance(x, z, terraced) - opening) / 12, (bottom - y) / 18.0))
                : surface;
        if (vertical && inBasin && shaftColumn(x, z)) return island;
        // Keep the deep vanilla rock and ore system below the ocean shelf.
        if ((inBasin && y >= -270) || (radiusSquared < 70 * 70 && y > -450)) return island;
        double strength = (inBasin ? 1 : 1 - smooth(OCEAN_RADIUS, BLEND_RADIUS, Math.sqrt(radiusSquared)))
                * smooth(-480, -270, y);
        return Mth.lerp(strength, terrain.compute(point), island);
    }

    static int oceanFloor(int x, int z) {
        return Mth.floor(-32 + 7 * Math.sin(x * 0.0042 + z * 0.002)
                + 5 * Math.sin(z * 0.0067 - x * 0.003));
    }

    private static double smooth(double from, double to, double value) {
        double t = Mth.clamp((value - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float[] profile(boolean pit) {
        float[] result = new float[GRID_SIDE * GRID_SIDE];
        for (int iz = 0; iz < GRID_SIDE; iz++) {
            int z = iz * GRID_STEP - GRID_RADIUS;
            for (int ix = 0; ix < GRID_SIDE; ix++) {
                int x = ix * GRID_STEP - GRID_RADIUS;
                double r = distance(x, z);
                if (pit) {
                    double angle = Math.atan2(z, x);
                    double lobes = 22 * Math.sin(angle * 5 + 0.7) + 14 * Math.cos(angle * 8);
                    result[iz * GRID_SIDE + ix] = (float) (r + smooth(85, 260, r)
                            * (lobes + 18 * FIELD.noise(x, z, 97, 1)));
                    continue;
                }
                double theta = Math.atan2(z, x);
                double shore = 1450 + 52 * Math.sin(3 * theta + 0.9)
                        + 34 * Math.cos(7 * theta) + 72 * FIELD.noise(x, z, 340, 2);
                double inland = shore - r;
                double cliff = 42 * smooth(-16, 113, inland);
                double rise = 157 * smooth(20, 785, inland);
                double summit = 46 * (1 - smooth(570, 855, r)) * (0.55 - 0.45 * Math.sin(theta));
                double relief = 27 * FIELD.noise(x, z, 285, 3) + 13 * FIELD.noise(x, z, 98, 4)
                        + 4 * FIELD.noise(x, z, 24, 5);
                double slope = smooth(815, 1080, r) * smooth(85, 180, inland);
                double gully = 27 * (1 - smooth(0.015, 0.22, Math.abs(FIELD.noise(x, z, 225, 8))));
                double ridge = 34 * smooth(0.08, 0.67, FIELD.noise(x, z, 202, 9));
                double highland = SEA + cliff + rise + summit + relief + slope * (ridge - gully);
                double terrace = Math.floor(highland / 9) * 9;
                double bench = terrace + 9 * smooth(0.23, 0.77, (highland - terrace) / 9);
                highland = Mth.lerp(0.58 * (1 - smooth(875, 1100, r)), highland, bench);
                double island = SEA + inland * 0.35;
                island = Mth.lerp(smooth(8, 100, inland), island, highland);
                double floor = Mth.lerp(smooth(-160, 0, inland), oceanFloor(x, z), island);
                result[iz * GRID_SIDE + ix] = (float) floor;
            }
        }
        return result;
    }

    private static double sample(float[] grid, int x, int z) {
        int gx = x + GRID_RADIUS;
        int gz = z + GRID_RADIUS;
        int index = gz / GRID_STEP * GRID_SIDE + gx / GRID_STEP;
        double tx = gx % GRID_STEP / (double) GRID_STEP;
        double tz = gz % GRID_STEP / (double) GRID_STEP;
        return Mth.lerp(tz, Mth.lerp(tx, grid[index], grid[index + 1]),
                Mth.lerp(tx, grid[index + GRID_SIDE], grid[index + GRID_SIDE + 1]));
    }

    private static final class Profiles {
        static final float[] HEIGHT = profile(false);
        static final float[] PIT = profile(true);
    }

    private OrthIsland() {}
}
