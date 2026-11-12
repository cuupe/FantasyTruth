package dev.fantasytruth.abyss;

import dev.fantasytruth.terrain.TerrainField;
import net.minecraft.util.Mth;

/** The forest basin forms the last terrace, above a broken-sided funnel into the second layer. */
final class EdgeBasin {
    static final int ENTRY_DEPTH = 1240;
    private static final int INNER_RADIUS = 11;
    private static final TerrainField FIELD = new TerrainField(0x4564676542617369L);

    static EdgeTerrain.Span column(int x, int z, double radius) {
        if (radius < INNER_RADIUS) return null;
        double angle = Math.atan2(z, x);
        double distance = routeDistance(angle, radius);
        double route = radius <= 437 ? 1 - smooth(Mth.clamp((distance - 3) / 21, 0, 1)) : 0;
        double broad = FIELD.noise(x, z, 91, 11);
        double detail = FIELD.noise(x, z, 31, 12);
        double rim = 147 + 23 * Math.sin(3 * angle + 0.85) + 16 * Math.cos(6 * angle - 1.1) + 13 * broad;
        double throat = INNER_RADIUS + (2.5 + 3 * Math.sin(5 * angle + 1.2)) * (1 - route);
        if (radius < throat) return null;
        double descent = Mth.clamp((rim - radius) / (rim - INNER_RADIUS), 0, 1);
        double benches = 12 * smooth(Mth.clamp((radius + 35 * broad - 250) / 87, 0, 1));
        double folds = (15 * broad + 8 * detail) * (1 - 0.65 * descent);
        double ribs = 11 * Math.cos(angle * 9 + broad * 2) * Math.sin(descent * Math.PI);
        double terrain = -1246 + benches + folds + ribs - 105 * Math.pow(descent, 1.18);
        int y = (int) Math.round(Mth.lerp(route, terrain, routeHeight(radius)));
        int thickness = 22 + (int) (62 * smooth(Mth.clamp((radius - 35) / 265, 0, 1)));
        EdgeLandscape.Cover cover;
        if (route > 0.96) cover = EdgeLandscape.Cover.TRAIL;
        else if (radius < 46 || descent > 0.45 && ribs < -1) cover = EdgeLandscape.Cover.SCREE;
        else if (broad < -0.35 && radius > 190) cover = EdgeLandscape.Cover.MEADOW;
        else if (detail > 0.23 || descent > 0.43) cover = EdgeLandscape.Cover.FERN;
        else cover = EdgeLandscape.Cover.WOODLAND;
        return new EdgeTerrain.Span(y - thickness, y, cover, y);
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
