package dev.fantasytruth.abyss;

import dev.fantasytruth.terrain.TerrainField;
import net.minecraft.util.Mth;

/** Outer sea cliffs, a broken watershed ridge, and the inward-facing city slopes. */
final class OrthRelief {
    static final int GRID_RADIUS = 1792;
    private static final int STEP = 4;
    private static final int SIDE = 1 + GRID_RADIUS * 2 / STEP;
    private static final double TURN = 2 * Math.PI;
    private static final TerrainField FIELD = new TerrainField(0x4f5254482d52494dL);
    // East, southeast, south, southwest, west, northwest, north, northeast.
    private static final int[] SHORE = {1490, 1410, 1300, 1540, 1510, 1480, 1550, 1470};
    private static final int[] RIDGE = {708, 564, 431, 502, 583, 628, 652, 734};
    private static final int[] LIP = {331, 266, 201, 234, 277, 314, 300, 346};
    private static final int[] CREST = {1130, 1090, 1030, 1130, 1100, 1125, 1160, 1175};
    private static final double[] RAVINES = {0.063, 0.25, 0.409, 0.569, 0.827, 0.938};

    static int elevation(int x, int z) {
        if (Math.abs(x) >= GRID_RADIUS || Math.abs(z) >= GRID_RADIUS) return OrthIsland.oceanFloor(x, z);
        return Mth.floor(sample(Profiles.HEIGHT, x, z));
    }

    static double pitDistance(int x, int z) {
        if (Math.abs(x) >= 690 || Math.abs(z) >= 690) return OrthIsland.distance(x, z);
        return sample(Profiles.PIT, x, z);
    }

    static double pitRadius(int y) {
        double beds = 5 * Math.sin(y * 0.048) + 3 * Math.sin(y * 0.124);
        if (y >= 142) return 499 + beds;
        if (y >= -75) return Mth.lerp((y + 75) / 217.0, 449, 499) + beds;
        if (y >= -224) return Mth.lerp((y + 224) / 149.0, 305, 449) + beds;
        if (y >= -383) return Mth.lerp((y + 383) / 159.0, 52, 305) + beds;
        return 52 + beds;
    }

    static Point at(double turn, double terrace) {
        double theta = TURN * turn;
        double r = terrace - terraceOffset(theta);
        return new Point((int) Math.round(Math.cos(theta) * r), (int) Math.round(Math.sin(theta) * r));
    }

    static double townDistance(int x, int z) {
        return OrthIsland.distance(x, z) + terraceOffset(Math.atan2(z, x));
    }

    static double turn(int x, int z) {
        double turn = Math.atan2(z, x) / TURN;
        return turn - Math.floor(turn);
    }

    static double ravine(double turn) {
        turn -= Math.floor(turn);
        double depth = 0;
        for (double axis : RAVINES) {
            double distance = Math.abs(axis - turn);
            distance = Math.min(distance, 1 - distance);
            depth = Math.max(depth, 1 - smooth(0.002, 0.019, distance));
        }
        return depth;
    }

    private static double terraceOffset(double theta) {
        return 30 * Math.sin(theta * 3 + 0.54) + 17 * Math.cos(theta * 5 - 0.7)
                + 10 * Math.sin(theta * 9 + 1.8);
    }

    private static double scarp(double angle, double r, int x, int z) {
        double ridgeRibs = Math.abs(Math.sin(angle * 23 + 0.65 * FIELD.noise(x, z, 128, 3)));
        double inland = smooth(550, 1040, r);
        return (9 + 24 * inland) * ridgeRibs + 18 * FIELD.noise(x, z, 118, 4)
                + 6 * FIELD.noise(x, z, 37, 5);
    }

    private static float[] profile(boolean pit) {
        float[] result = new float[SIDE * SIDE];
        for (int iz = 0; iz < SIDE; iz++) {
            int z = iz * STEP - GRID_RADIUS;
            for (int ix = 0; ix < SIDE; ix++) {
                int x = ix * STEP - GRID_RADIUS;
                double r = OrthIsland.distance(x, z);
                double theta = Math.atan2(z, x);
                int index = iz * SIDE + ix;
                if (pit) {
                    if (r >= 740) {
                        result[index] = (float) r;
                        continue;
                    }
                    double cliffs = 17 * Math.sin(13 * theta + 0.8 * FIELD.noise(x, z, 140, 1))
                            + 10 * Math.cos(21 * theta) + 11 * FIELD.noise(x, z, 46, 2);
                    result[index] = (float) (r + smooth(95, 380, r) * (terraceOffset(theta) + cliffs));
                    continue;
                }

                double turn = theta / TURN;
                turn -= Math.floor(turn);
                double inlet = Math.exp(-Math.pow((turn - 0.25) / 0.032, 2));
                double shore = sector(SHORE, turn) + 38 * Math.sin(theta * 7 + 0.9)
                        + 48 * FIELD.noise(x, z, 360, 6) + 13 * FIELD.noise(x, z, 80, 7);
                double landward = shore - r;
                int seabed = OrthIsland.oceanFloor(x, z);
                if (landward <= 0) {
                    result[index] = (float) Mth.lerp(smooth(-136, 0, landward), seabed, OrthIsland.SEA);
                    continue;
                }

                double q = r + terraceOffset(theta);
                double crest = sector(CREST, turn) + 28 * Math.sin(4 * theta - 0.8);
                double crestY = sector(RIDGE, turn) + 24 * Math.sin(5 * theta + 0.8);
                double lipY = sector(LIP, turn) + 17 * Math.sin(theta * 7 - 0.9);
                double drainage = ravine(turn);
                double ribs = scarp(theta, r, x, z);
                double valley = 66 * drainage * smooth(510, 910, q);
                double height;
                if (q <= crest) {
                    double slopeWindow = smooth(472, 614, q) * (1 - smooth(crest - 110, crest, q));
                    double folds = 33 * FIELD.noise(x, z, 181, 8) + 15 * FIELD.noise(x, z, 72, 9);
                    double warpedSlope = q + folds * slopeWindow;
                    double rise = smooth(447, crest + 20, warpedSlope);
                    double hillside = slopeWindow * (9 * FIELD.noise(x, z, 88, 10) + 3.2 * FIELD.noise(x, z, 24, 11));
                    height = Mth.lerp(rise, lipY, crestY) + ribs * (1 - 0.58 * slopeWindow) + hillside - valley;
                    height -= 83 * inlet * smooth(710, 1040, q);
                } else {
                    // The outer face rises from the sea almost at once. The south inlet breaks that wall.
                    double width = Math.max(70, shore + terraceOffset(theta) - crest);
                    double bank = Mth.clamp(landward / width, 0, 1);
                    double cliff = 0.55 * smooth(17, 115, landward) + 0.45 * smooth(0.12, 1, bank);
                    double outer = Mth.lerp(inlet * 0.93, cliff, Math.pow(bank, 2.8));
                    height = OrthIsland.SEA + (crestY - OrthIsland.SEA - valley - 83 * inlet) * outer
                            + ribs * smooth(18, 85, landward);
                    height = Mth.lerp(smooth(0, 20, landward), OrthIsland.SEA + 0.55 * landward, height);
                }
                result[index] = (float) height;
            }
        }
        return result;
    }

    private static double sector(int[] values, double turn) {
        double position = turn * values.length;
        int index = (int) position;
        return Mth.lerp(smooth(0, 1, position - index), values[index], values[(index + 1) % values.length]);
    }

    private static double smooth(double from, double to, double value) {
        double t = Mth.clamp((value - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static double sample(float[] grid, int x, int z) {
        int gx = x + GRID_RADIUS, gz = z + GRID_RADIUS;
        int i = gz / STEP * SIDE + gx / STEP;
        double tx = gx % STEP / (double) STEP;
        double tz = gz % STEP / (double) STEP;
        return Mth.lerp(tz, Mth.lerp(tx, grid[i], grid[i + 1]),
                Mth.lerp(tx, grid[i + SIDE], grid[i + SIDE + 1]));
    }

    record Point(int x, int z) {}

    private static final class Profiles {
        static final float[] HEIGHT = profile(false);
        static final float[] PIT = profile(true);
    }

    private OrthRelief() {}
}

