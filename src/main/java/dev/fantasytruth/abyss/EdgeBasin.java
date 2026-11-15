package dev.fantasytruth.abyss;

import dev.fantasytruth.terrain.TerrainField;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;

/** The forest basin forms the last terrace, above a broken-sided funnel into the second layer. */
final class EdgeBasin {
    static final int ENTRY_DEPTH = 1240;
    static final int MOSS_FLOOR = -1332;
    static final int APPROACH_RADIUS = 64;
    private static final int INNER_RADIUS = 11;
    private static final TerrainField FIELD = new TerrainField(0x4564676542617369L);

    static EdgeTerrain.Span column(int x, int z, double radius, boolean mossPit) {
        if (mossPit) return mossPit(x, z, radius);
        if (radius < INNER_RADIUS) return null;
        double angle = Math.atan2(z, x);
        double distance = routeDistance(angle, radius);
        double route = radius <= 437 ? 1 - smooth(Mth.clamp((distance - 3) / 21, 0, 1)) : 0;
        double broad = FIELD.noise(x, z, 91, 11);
        double detail = FIELD.noise(x, z, 31, 12);
        double rim = 147 + 23 * Math.sin(3 * angle + 0.85) + 16 * Math.cos(6 * angle - 1.1) + 13 * broad;
        double throat = 33 + 8 * Math.sin(5 * angle + 1.2) + 5 * Math.cos(8 * angle - 0.7) + 8 * broad;
        if (radius < throat && distance > 5 + 0.17 * radius) return null;
        double descent = Mth.clamp((rim - radius) / (rim - throat + 12), 0, 1);
        double benches = 12 * smooth(Mth.clamp((radius + 35 * broad - 250) / 87, 0, 1));
        double folds = (15 * broad + 8 * detail) * (1 - 0.65 * descent);
        double ribs = 11 * Math.cos(angle * 9 + broad * 2) * Math.sin(descent * Math.PI);
        double terrain = -1246 + benches + folds + ribs - 105 * Math.pow(descent, 1.18);
        int y = (int) Math.round(Mth.lerp(route, terrain, routeHeight(radius)));
        int thickness = 22 + (int) (62 * smooth(Mth.clamp((radius - 35) / 265, 0, 1)));
        EdgeLandscape.Cover cover;
        if (route > 0.96) cover = EdgeLandscape.Cover.TRAIL;
        else if (radius < throat + 6 && detail < 0.14 || descent > 0.53 && ribs < -3 && detail < -0.10)
            cover = EdgeLandscape.Cover.SCREE;
        else if (broad < -0.35 && radius > 190) cover = EdgeLandscape.Cover.MEADOW;
        else if (detail > 0.23 || descent > 0.31) cover = EdgeLandscape.Cover.FERN;
        else cover = EdgeLandscape.Cover.WOODLAND;
        return new EdgeTerrain.Span(y - thickness, y, cover, y);
    }

    private static EdgeTerrain.Span mossPit(int x, int z, double radius) {
        double angle = Math.atan2(z, x);
        if (radius < APPROACH_RADIUS && hole(x, z)) return null;
        double broad = FIELD.noise(x, z, 91, 11);
        double detail = FIELD.noise(x, z, 31, 12);
        double rim = 164 + 23 * Math.sin(3 * angle + 0.85) + 15 * Math.cos(7 * angle - 1.1) + 18 * broad;
        double drop = smooth(Mth.clamp((rim - radius) / 23, 0, 1));
        double inside = smooth(Mth.clamp((rim - 21 - radius) / 57, 0, 1));
        double bench = smooth(Mth.clamp((radius + 35 * broad - 250) / 87, 0, 1));
        double shoulder = -1247 + 10 * bench + 12 * broad + 8 * detail;
        double ledge = shoulder - 67 * drop;
        int y = (int) Math.round(Mth.lerp(inside, ledge, MOSS_FLOOR + 4 * broad + 5 * detail));
        int bottom = Math.min(y - 38, -1445 + (int) (9 * broad));
        var cover = drop > 0.08 ? EdgeLandscape.Cover.PIT_MOSS
                : detail > 0.23 ? EdgeLandscape.Cover.FERN : EdgeLandscape.Cover.WOODLAND;
        return new EdgeTerrain.Span(bottom, y, cover, y);
    }

    static boolean hole(int x, int z) {
        double angle = Math.atan2(z, x);
        double edge = 41 + 8 * Math.sin(3 * angle + 0.45) + 6 * Math.cos(5 * angle + 0.7)
                + 4 * Math.sin(8 * angle - 1) + 2 * Math.cos(13 * angle + 0.4);
        return (double) x * x + (double) z * z < edge * edge;
    }

    static void growEntrance(EdgeExploration.Painter p) {
        var moss = Blocks.MOSS_BLOCK.defaultBlockState();
        var wood = Blocks.JUNGLE_WOOD.defaultBlockState();
        var leaves = Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        var jungleLeaves = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        var dirt = Blocks.ROOTED_DIRT.defaultBlockState();
        var stone = OrthBlocks.STONE.get();
        for (int wx = p.x0; wx < p.x0 + 16; wx++) {
            for (int wz = p.z0; wz < p.z0 + 16; wz++) {
                int x = OrthIsland.localX(wx), z = OrthIsland.localZ(wz);
                double radius = OrthIsland.distance(x, z);
                if (radius < 20 || radius > 62 || hole(x, z)) continue;
                boolean face = touchesHole(x, z, 1);
                var surface = mossPit(x, z, radius);
                double thicket = FIELD.noise(x, z, 14, 17);
                int top = surface.top();
                if (face || touchesHole(x, z, 2)) {
                    // Break the upper rim into shallow notches above the transit plane.
                    double erosion = FIELD.noise(x, z, 11, 30);
                    int cut = Math.max(0, (int) ((erosion + 0.26) * (face ? 10 : 4)));
                    int lip = Math.max(EdgeTerrain.EXIT + 4, top - cut);
                    for (int y = top; y > lip; y--) p.set(wx, y, wz, Blocks.AIR.defaultBlockState());
                    top = lip;
                }
                if (face) {
                    for (int y = top - 1; y >= -1518; y--) {
                        if (!p.get(wx, y, wz).is(stone)) continue;
                        // Offset the growth in both axes every few blocks of depth.
                        // Mix two scales so adjacent faces read as patches instead of vertical bands.
                        double mat = FIELD.noise(x + y * 0.63, z - y * 0.42, 8, 19);
                        double grain = FIELD.noise(z + y * 0.37, x - y * 0.74, 3, 24);
                        double growth = mat + grain * 0.35;
                        p.set(wx, y, wz, growth < -0.36 ? wood : growth < -0.21 ? dirt : moss);
                    }
                }
                boolean nearRim = touchesHole(x, z, 6);
                if (!nearRim || thicket < -0.32) continue;
                int y = top + 1;
                double crown = FIELD.noise(x, z, 6, 28) * 0.58 + thicket;
                int height = 1 + (int) (7 * Mth.clamp(crown + 0.28, 0, 1));
                var canopy = thicket < 0.10 ? jungleLeaves : leaves;
                if ((EdgeExploration.hash(wx, y, wz) & 7) == 0 && height >= 4) p.grow(wx, y, wz, wood);
                for (int dy = 0; dy < height; dy++) {
                    var previous = p.get(wx, y + dy, wz);
                    if (!previous.isAir() && !previous.canBeReplaced()) continue;
                    p.set(wx, y + dy, wz, canopy);
                }
            }
        }
    }

    private static boolean touchesHole(int x, int z, int reach) {
        return hole(x + reach, z) || hole(x - reach, z) || hole(x, z + reach) || hole(x, z - reach);
    }

    static boolean nearRoute(int worldX, int y, int worldZ, int margin) {
        if (y > -1215) return false;
        int x = OrthIsland.localX(worldX), z = OrthIsland.localZ(worldZ);
        double radius = OrthIsland.distance(x, z);
        return radius < 437 && routeDistance(Math.atan2(z, x), radius) < margin;
    }

    static EdgeLandscape.Point point(int radius) {
        double angle = routeAngle(radius);
        return new EdgeLandscape.Point(AbyssLayer.CENTER_X + (int) Math.round(radius * Math.cos(angle)),
                AbyssLayer.CENTER_Z + (int) Math.round(radius * Math.sin(angle)));
    }

    private static double routeDistance(double angle, double radius) {
        return Math.abs(Math.IEEEremainder(angle - routeAngle(radius), Math.PI * 2)) * radius;
    }

    private static double routeAngle(double radius) {
        double t = Mth.clamp((409 - radius) / 395, 0, 1);
        return Mth.lerp(t, EdgeLandscape.angle(ENTRY_DEPTH), EdgeLandscape.angle(EdgeLandscape.END))
                + 0.23 * Math.sin(Math.PI * t);
    }

    private static double routeHeight(double radius) {
        double walk = Mth.clamp((409 - radius) / 238, 0, 1);
        double lower = Mth.clamp((171 - radius) / 157, 0, 1);
        return -ENTRY_DEPTH - 5 * walk - 101 * lower;
    }

    private static double smooth(double x) { return x * x * (3 - 2 * x); }

    private EdgeBasin() {}
}
