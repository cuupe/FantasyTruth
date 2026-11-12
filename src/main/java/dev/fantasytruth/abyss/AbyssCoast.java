package dev.fantasytruth.abyss;

import dev.fantasytruth.terrain.TerrainField;
import net.minecraft.util.Mth;

/** An outer foothill, an inner catchment and a narrow, deep passage. */
final class AbyssCoast {
    static final int REACH = 528;
    private static final int SEA = 78;
    private static final int STEP = 4;
    private static final int SIDE = REACH * 2 / STEP + 1;
    private static final double DENSITY_SCALE = 28;

    static double shape(double vanilla, int x, int y, int z) {
        if (x <= -REACH || x >= REACH || z <= -REACH || z >= REACH) return vanilla;
        int distanceSquared = x * x + z * z;
        if (distanceSquared >= REACH * REACH) return vanilla;
        double radius = Math.sqrt(distanceSquared);

        // A submerged apron starts below the shelf and feathers into existing terrain.
        double influence = (1 - smooth(400, REACH, radius)) * smooth(-180, -112, y);
        double apron = (sample(Profiles.APRON, x, z) - y) / DENSITY_SCALE;
        double raised = vanilla + influence * roundedPositive(apron - vanilla);

        // The upper walls are a catchment with shallow ravines. The steep rock face
        // belongs to the narrow well below it, away from the coast.
        double catchment = (sample(Profiles.BOWL, x, z) - y) / DENSITY_SCALE;
        double cut = 1 - smooth(102, 187, radius);
        double surface = raised - cut * roundedPositive(raised - catchment);
        if (radius >= 50) return surface;

        double angle = Math.atan2(z, x);
        double edge = 30 + 2.7 * Math.sin(5 * angle) + 1.6 * Math.sin(9 * angle + y * 0.038);
        double shaft = Math.max((radius - edge) / 10, (-460 - y) / 12.0);
        return surface - (1 - smooth(39, 50, radius)) * roundedPositive(surface - shaft);
    }

    private static double roundedPositive(double value) {
        if (value <= 0) return 0;
        double transition = 0.075;
        return value >= transition ? value - transition * 0.5 : value * value / (2 * transition);
    }

    private static double smooth(double low, double high, double value) {
        double t = Mth.clamp((value - low) / (high - low), 0, 1);
        return t * t * t * (t * (6 * t - 15) + 10);
    }

    private static double sample(float[] values, int x, int z) {
        int gx = x + REACH;
        int gz = z + REACH;
        int i = (gz / STEP) * SIDE + gx / STEP;
        double tx = (gx % STEP) / (double) STEP;
        double tz = (gz % STEP) / (double) STEP;
        return Mth.lerp(tz, Mth.lerp(tx, values[i], values[i + 1]),
                Mth.lerp(tx, values[i + SIDE], values[i + SIDE + 1]));
    }

    /** The fixed entrance can share a small, immutable horizontal sample grid. */
    private static final class Profiles {
        static final float[] APRON = new float[SIDE * SIDE];
        static final float[] BOWL = new float[SIDE * SIDE];

        static {
            TerrainField noise = new TerrainField(0x5a9077ab155L);
            for (int iz = 0; iz < SIDE; iz++) {
                for (int ix = 0; ix < SIDE; ix++) {
                    int x = ix * STEP - REACH;
                    int z = iz * STEP - REACH;
                    double dx = x + 20 * noise.noise(x, z, 173, 1);
                    double dz = z + 20 * noise.noise(x, z, 173, 2);
                    double radius = Math.hypot(dx, dz);
                    double shoulder = 1 - smooth(68, 476, radius);
                    double foothills = 12 * noise.noise(x, z, 134, 3) + 5.5 * noise.noise(x, z, 47, 4)
                            + 1.8 * noise.noise(x, z, 19, 5);
                    int i = iz * SIDE + ix;
                    APRON[i] = (float) (SEA - 97 + 161 * shoulder + foothills * smooth(0, 0.48, shoulder));
                    BOWL[i] = (float) (SEA + 12 + 67 * smooth(29, 137, radius)
                            + 7 * noise.noise(x, z, 72, 6) + 2.8 * noise.noise(x, z, 23, 7));
                }
            }
        }
    }

    private AbyssCoast() {}
}
