package dev.fantasytruth.abyss;

import dev.fantasytruth.terrain.TerrainField;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

/** First-layer shelves extend inward from a continuous descent along the rock face. */
final class EdgeLandscape {
    enum Cover { MEADOW, FLOWERS, WOODLAND, FERN, WETLAND, SCREE, TRAIL, POOL }

    record Shelf(int depth, int reach, int halfDepth, Cover cover) {}
    record Point(int x, int z) {}

    static final int START = 8;
    static final int END = 1312;
    static final int TRAIL_RADIUS = 409;
    private static final int TURN_DROP = 680;
    private static final double TURN = Math.PI * 2;
    private static final double ENTRY_ANGLE = -2.18;
    private static final TerrainField FIELD = new TerrainField(0x456467654d656164L);

    static final List<Shelf> SHELVES = List.of(
            new Shelf(53, 171, 67, Cover.MEADOW),
            new Shelf(145, 213, 68, Cover.FLOWERS),
            new Shelf(222, 176, 60, Cover.WOODLAND),
            new Shelf(300, 174, 59, Cover.SCREE),
            new Shelf(415, 225, 88, Cover.WETLAND),
            new Shelf(515, 166, 70, Cover.FERN),
            new Shelf(600, 253, 63, Cover.MEADOW),
            new Shelf(700, 196, 74, Cover.WOODLAND),
            new Shelf(794, 236, 76, Cover.FLOWERS),
            new Shelf(890, 193, 71, Cover.WETLAND),
            new Shelf(988, 238, 75, Cover.FERN),
            new Shelf(1081, 161, 70, Cover.SCREE),
            new Shelf(1172, 209, 70, Cover.WOODLAND),
            new Shelf(1267, 190, 74, Cover.MEADOW));

    static Point point(int depth, int radius) {
        double angle = angle(depth);
        double r = radius + (trailRadius(depth) - TRAIL_RADIUS) * Mth.clamp((radius - 373) / 34.0, 0, 1);
        return new Point(AbyssLayer.CENTER_X + (int) Math.round(r * Math.cos(angle)),
                AbyssLayer.CENTER_Z + (int) Math.round(r * Math.sin(angle)));
    }

    static double angle(int depth) {
        return ENTRY_ANGLE + (depth - START) * TURN / TURN_DROP;
    }

    private static double trailRadius(double depth) {
        double a = (depth - START) * TURN / TURN_DROP;
        return 403 + 10 * Math.sin(a * 2.2 + 0.3) + 7 * Math.sin(a * 5.8 - 0.75);
    }

    static List<EdgeTerrain.Span> shelves(int x, int z, double radius, boolean forested) {
        if (radius < 145) {
            var exit = forested ? EdgeBasin.column(x, z, radius) : exit(x, z);
            return exit == null ? List.of() : List.of(exit);
        }
        double angle = Math.atan2(z, x) - ENTRY_ANGLE;
        double revolutions = angle / TURN;
        double depth = START + (revolutions - Math.floor(revolutions)) * TURN_DROP;
        var spans = new ArrayList<EdgeTerrain.Span>(3);
        double broad = FIELD.noise(x, z, 91, 1);
        double detail = FIELD.noise(x, z, 27, 2);
        int end = forested ? EdgeBasin.ENTRY_DEPTH : END;
        for (; depth <= end + 1; depth += TURN_DROP) {
            var span = shelf(x, z, radius, depth, broad, detail, forested);
            if (span != null) spans.add(span);
        }
        var exit = forested ? EdgeBasin.column(x, z, radius) : exit(x, z);
        if (exit != null) {
            if (!spans.isEmpty() && spans.get(spans.size() - 1).bottom() <= exit.top()) {
                var last = spans.remove(spans.size() - 1);
                var upper = last.top() > exit.top() ? last : exit;
                spans.add(new EdgeTerrain.Span(Math.min(last.bottom(), exit.bottom()), upper.top(),
                        forested ? upper.cover() : Cover.TRAIL, Math.max(last.water(), exit.water())));
            } else {
                spans.add(exit);
            }
        }
        return spans;
    }

    private static EdgeTerrain.Span shelf(int x, int z, double radius, double depth, double broad, double detail, boolean forested) {
        Shelf chosen = null;
        double reach = 0;
        for (Shelf shelf : SHELVES) {
            double weight = 1 - Math.abs(depth - shelf.depth) / shelf.halfDepth;
            if (weight <= 0) continue;
            double width = shelf.reach * smooth(weight);
            if (forested) width *= 1.09 + 0.10 * broad;
            if (width > reach) {
                reach = width;
                chosen = shelf;
            }
        }
        double trail = trailRadius(depth);
        double front = trail - 14 - reach + (24 * broad + 11 * detail) * Math.min(1, reach / 38);
        if (radius < front) return null;
        int trailY = -(int) Math.floor(Math.min(depth, END));
        boolean path = Math.abs(radius - trail) <= 8;
        double interior = smooth(Mth.clamp((trail - 8 - radius) / 71, 0, 1));
        double outer = Mth.clamp((radius - trail - 8) / 62, 0, 1);
        double relief = (13 * broad + 5 * detail - 5) * interior + (13 + 9 * broad) * outer;
        if (chosen != null && !path) {
            double terrace = smooth(Math.max(0, 1 - Math.abs(depth - chosen.depth) / chosen.halfDepth));
            relief += (forested ? 0.90 : 0.52) * (depth - chosen.depth) * interior * terrace;
            double cuts = Math.max(0, FIELD.noise(x, z, 43, 3) - 0.22);
            relief -= (chosen.cover == Cover.SCREE ? 27 : 14) * cuts * interior;
            if (chosen.cover == Cover.SCREE) relief += 11 * detail * interior;
            if (forested) {
                double breakLine = radius + 23 * broad + 9 * detail;
                double bench = smooth(Mth.clamp((breakLine - 264) / 31, 0, 1));
                relief += interior * switch (chosen.cover) {
                    case WOODLAND -> 14 * bench + 16 * broad - 7 * detail;
                    case FERN -> -15 * (1 - bench) + 8 * detail;
                    case FLOWERS -> 10 * bench - 7 * Math.abs(detail);
                    case WETLAND -> -9 * (1 - bench);
                    case SCREE -> 12 * bench - 19 * cuts;
                    default -> 11 * broad + 4 * detail;
                };
            }
        }
        int top = Math.min(-2, trailY + (int) Math.round(relief));
        boolean gravel = Math.abs(radius - trail) <= 3.3 + 1.8 * detail;
        Cover cover = gravel ? Cover.TRAIL : chosen == null ? Cover.SCREE : chosen.cover;
        if (!path && chosen != null && radius < 396 && cover == Cover.WOODLAND && detail > 0.38) cover = Cover.FERN;
        if (!path && chosen != null && radius < 396 && cover == Cover.MEADOW && broad > 0.34) cover = Cover.FLOWERS;
        if (!path && cover == Cover.SCREE && broad + 0.5 * detail > -0.12) cover = Cover.MEADOW;
        if (forested && chosen != null && !path && interior > 0.25 && (cover == Cover.MEADOW || cover == Cover.FLOWERS)
                && FIELD.noise(x, z, 62, chosen.depth + 318) > 0.12) cover = Cover.WOODLAND;
        int water = top;
        if (chosen != null && chosen.cover == Cover.WETLAND && radius < 391) {
            Point center = point(chosen.depth, 329);
            double dx = (x - OrthIsland.localX(center.x)) / 36.0;
            double dz = (z - OrthIsland.localZ(center.z)) / 30.0;
            double bayX = dx - (chosen.depth < 600 ? 0.57 : -0.57), bayZ = dz + 0.28;
            double basin = Math.min(dx * dx + dz * dz, 1.9 * bayX * bayX + 2.5 * bayZ * bayZ + 0.14)
                    + 0.24 * FIELD.noise(x, z, 19, 6) + 0.13 * detail;
            if (basin < 1) {
                int level = -chosen.depth - 7;
                if (basin < 0.58) {
                    top = level - 1 - (int) (5 * (1 - basin / 0.58));
                    water = level;
                    cover = Cover.POOL;
                } else {
                    top = Math.max(top, level);
                    water = top;
                }
            }
        }
        int thickness = 12 + (int) (29 * Math.sqrt(Mth.clamp((radius - front) / 116, 0, 1)));
        return new EdgeTerrain.Span(top - thickness, top, cover, water);
    }

    private static EdgeTerrain.Span exit(int x, int z) {
        double direction = angle(END);
        double forward = x * Math.cos(direction) + z * Math.sin(direction);
        double side = Math.abs(z * Math.cos(direction) - x * Math.sin(direction));
        if (forward < 11 || forward > 416 || side > 4 + Math.min(11, forward / 13)) return null;
        int top = -END - (int) Math.floor((416 - forward) * 34 / 405);
        int thickness = 8 + (int) Math.min(27, forward / 8);
        return new EdgeTerrain.Span(top - thickness, top, Cover.TRAIL, top);
    }

    static List<EdgeTerrain.Rock> scarps(int x, int z, double radius) {
        if (radius < 432) return List.of();
        double setback = OrthIsland.SHAFT_RADIUS - OrthRelief.pitDistance(x, z);
        if (setback > 70) return List.of();
        var rock = new ArrayList<EdgeTerrain.Rock>(5);
        double ridge = FIELD.noise(x, z, 103, 211);
        double grain = FIELD.noise(x, z, 29, 212);
        int skew = (int) (38 * ridge + 14 * grain);
        for (int i = 0; i < 8; i++) {
            double spur = FIELD.noise(x, z, 73 + i * 4, 220 + i);
            double reach = 21 + 24 * ridge + 31 * spur;
            if (setback >= reach) continue;
            double weight = smooth(1 - setback / reach);
            int center = -92 - i * 168 + skew + (int) (24 * spur);
            int height = 8 + (int) (60 * weight);
            rock.add(new EdgeTerrain.Rock(center - height, Math.min(-24, center + height / 2), skew));
        }
        return rock;
    }

    static EdgeTerrain.Span at(int x, int z, int depth, boolean forested) {
        EdgeTerrain.Span nearest = null;
        int distance = 60;
        for (var span : EdgeTerrain.column(x, z, true, forested).shelves()) {
            int difference = Math.abs(span.top() + depth);
            if (difference < distance) {
                nearest = span;
                distance = difference;
            }
        }
        return nearest;
    }

    private static double smooth(double x) { return x * x * (3 - 2 * x); }

    private EdgeLandscape() {}
}
