package dev.fantasytruth.abyss;

import dev.fantasytruth.terrain.TerrainField;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

/** First-layer shelves extend inward from a continuous descent along the rock face. */
final class EdgeLandscape {
    enum Cover { MEADOW, FLOWERS, WOODLAND, FERN, WETLAND, SCREE, TRAIL, POOL, PIT_MOSS }

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

    static List<EdgeTerrain.Span> shelves(int x, int z, double radius, EdgeTerrain.Layout layout) {
        boolean forested = layout.forested;
        boolean forestMouth = layout.forestMouth();
        if (forestMouth && radius < 105) return ForestMouth.shelves(x, z, radius);
        if (radius < (layout.eroded() ? 105 : 145)) {
            var exit = forested ? EdgeBasin.column(x, z, radius, layout.mossPit) : exit(x, z);
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
            var span = shelf(x, z, radius, depth, broad, detail, layout);
            if (span != null) spans.add(span);
        }
        if (layout.wildEdge() && radius > 245 && radius < 490) {
            addHangingBenches(spans, x, z, radius, broad, detail);
        }
        if (forestMouth && radius < ForestMouth.RADIUS) {
            for (var lip : ForestMouth.shelves(x, z, radius)) append(spans, lip, true);
        } else {
            var exit = forested ? EdgeBasin.column(x, z, radius, layout.mossPit) : exit(x, z);
            if (exit != null) append(spans, exit, forested);
        }
        return spans;
    }

    private static void append(List<EdgeTerrain.Span> spans, EdgeTerrain.Span next, boolean forested) {
        if (!spans.isEmpty() && spans.get(spans.size() - 1).bottom() <= next.top()) {
            var last = spans.remove(spans.size() - 1);
            var upper = last.top() > next.top() ? last : next;
            spans.add(new EdgeTerrain.Span(Math.min(last.bottom(), next.bottom()), upper.top(),
                    forested ? upper.cover() : Cover.TRAIL, Math.max(last.water(), next.water())));
        } else {
            spans.add(next);
        }
    }

    private static void addHangingBenches(List<EdgeTerrain.Span> spans, int x, int z, double radius,
                                          double broad, double detail) {
        // Separate uplands link some of the larger shelves without repeating their spiral grade.
        double fold = FIELD.noise(x, z, 82, 270);
        double taper = Mth.clamp((radius - 245) / 42, 0, 1) * Mth.clamp((490 - radius) / 27, 0, 1);
        if (fold * taper < 0.24) return;
        double angle = Math.atan2(z, x);
        int top = -172 - (int) (147 * (1 + Math.sin(angle + broad * 0.28))) + (int) (24 * detail);
        for (int band = 0; band < 2; band++, top -= 634) {
            int bottom = top - 9 - (int) (36 * (fold * taper - 0.24));
            int index = 0;
            while (index < spans.size() && spans.get(index).top() > top) index++;
            if (index > 0 && spans.get(index - 1).bottom() < top + 12
                    || index < spans.size() && spans.get(index).water() > bottom - 10) continue;
            spans.add(index, new EdgeTerrain.Span(bottom, top, detail < -0.12 ? Cover.FERN : Cover.WOODLAND, top));
        }
    }

    private static EdgeTerrain.Span shelf(int x, int z, double radius, double depth, double broad, double detail,
                                          EdgeTerrain.Layout layout) {
        boolean forested = layout.forested;
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
        double erosion = 0, fold = 0, valley = 0, ridge = 0;
        if (layout.eroded() && chosen != null && depth > 286 && depth < 1200 && radius < 393) {
            double inset = smooth(Mth.clamp((393 - radius) / 35, 0, 1));
            erosion = inset * smooth(Mth.clamp(Math.min(depth - 286, 1200 - depth) / 32, 0, 1));
            double across = (depth - chosen.depth) / chosen.halfDepth;
            fold = FIELD.noise(x, z, 63, 247);
            ridge = smooth(Mth.clamp((1 - Math.abs(across + 0.16 + 0.3 * fold)) * 1.35, 0, 1));
            valley = 1 - smooth(Mth.clamp(Math.abs(FIELD.noise(x, z, 50, 251) + 0.19) * 4.5, 0, 1));
            front -= erosion * (ridge * (69 + 31 * broad) + 27 * fold - 24 * valley);
        }
        boolean wild = layout.wildEdge();
        double bypass = layout.eroded() && !wild ? bypassRadius(depth, trail) : trail;
        if (wild) {
            double shoulder = smooth(Mth.clamp((trail + 32 - radius) / 110, 0, 1));
            front += shoulder * (33 * FIELD.noise(x, z, 49, 264) + 11 * detail);
        }
        if (bypass < trail) front = Math.min(front, bypass - 14);
        if (radius < front) return null;
        int trailY = -(int) Math.floor(Math.min(depth, END));
        boolean path = !wild && Math.abs(radius - trail) <= 8;
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
        if (erosion > 0) {
            double shape = smooth(Mth.clamp((Math.abs(depth - chosen.depth) - 3) / 15, 0, 1));
            double bench = smooth(Mth.clamp((radius + 28 * fold - 246) / 34, 0, 1));
            relief += erosion * shape * (27 * bench - 20 * (1 - bench) + 20 * fold - 17 * valley);
        }
        if (layout.wallTerraces() && depth > 25 && depth < 1210 && radius < trail - 9 && radius > 350) {
            relief += EdgeWall.innerRelief(x, z, radius, trail);
        }
        if (bypass < trail) relief *= smooth(Mth.clamp((Math.abs(radius - bypass) - 3) / 12, 0, 1));
        if (wild) {
            double startSlope = smooth(Mth.clamp((87 - depth) / 78, 0, 1));
            double upland = FIELD.noise(x + 22 * detail, z, 61, 258);
            double gullies = 1 - smooth(Mth.clamp(Math.abs(FIELD.noise(x, z - 30 * broad, 39, 259) - 0.13) * 5, 0, 1));
            relief += 15 * upland + 9 * detail - 13 * gullies;
            relief -= startSlope * (22 + 11 * Math.abs(broad));
        }
        int top = Math.min(-2, trailY + (int) Math.round(relief));
        double lane = radius - trail;
        boolean gravel = wild ? Math.abs(lane - 7 * broad) < 1.25 + detail * 0.4
                && FIELD.noise(x, z, 31, 262) > 0.66
                : Math.abs(lane) <= 3.3 + 1.8 * detail || (bypass < trail && Math.abs(radius - bypass) <= 2.8);
        Cover cover = gravel ? Cover.TRAIL : chosen == null ? Cover.SCREE : chosen.cover;
        if (!path && chosen != null && radius < 396 && cover == Cover.WOODLAND && detail > 0.38) cover = Cover.FERN;
        if (!path && chosen != null && radius < 396 && cover == Cover.MEADOW && broad > 0.34) cover = Cover.FLOWERS;
        if (!path && cover == Cover.SCREE && broad + 0.5 * detail > -0.12) cover = Cover.MEADOW;
        if (forested && chosen != null && !path && interior > 0.25 && (cover == Cover.MEADOW || cover == Cover.FLOWERS)
                && FIELD.noise(x, z, 62, chosen.depth + 318) > 0.12) cover = Cover.WOODLAND;
        if (erosion > 0.4 && cover != Cover.TRAIL) {
            if (valley > 0.82 && fold < 0.25) cover = Cover.FERN;
            else if (fold + detail * 0.45 > 0.42) cover = Cover.SCREE;
            else if ((cover == Cover.MEADOW || cover == Cover.FLOWERS) && fold < -0.28) cover = Cover.WOODLAND;
        }
        if (!wild && erosion > 0 && switch (chosen.depth) {
            case 300, 515, 700, 794, 988, 1172 -> true;
            default -> false;
        }) {
            double side = (depth - chosen.depth) * TURN * radius / TURN_DROP;
            double bend = 11 * Math.sin((393 - radius) / 22);
            if (radius > front + 5 && Math.abs(side - bend) < 2.8 && radius < trail - 8) cover = Cover.TRAIL;
        }
        if (wild && !gravel) {
            double habitat = broad + 0.6 * detail;
            if (depth < 91 && radius > trail - 28 || cover == Cover.SCREE && habitat > -0.23) {
                cover = habitat > 0.19 ? Cover.WOODLAND : habitat < -0.27 ? Cover.FLOWERS : Cover.FERN;
            } else if (cover == Cover.WOODLAND && habitat < -0.29) cover = Cover.FERN;
        }
        if (layout.wallTerraces() && radius > trail + 9 && (wild || depth > 19)) {
            return EdgeWall.slope(x, z, radius - trail, detail, top, wild);
        }
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
        thickness += (int) (erosion * ridge * (12 + 22 * Math.max(0, fold + broad)));
        return new EdgeTerrain.Span(top - thickness, top, cover, water);
    }

    // The first and last blocks of a bypass meet the main trail at the same height.
    private static double bypassRadius(double depth, double trail) {
        int start = depth < 385 ? 330 : depth < 580 ? 533 : depth < 773 ? 725 : 1007;
        double progress = (depth - start) / 45;
        if (progress <= 0 || progress >= 1) return trail;
        double turn = Math.sin(Math.PI * progress);
        return trail - 63 * turn * turn;
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

    static List<EdgeTerrain.Rock> scarps(int x, int z, double radius, double setback, EdgeTerrain.Layout layout) {
        boolean terraces = layout.wallTerraces();
        if (radius < 432 || setback > (terraces ? 84 : 70)) return List.of();
        var rock = new ArrayList<EdgeTerrain.Rock>(5);
        double ridge = FIELD.noise(x, z, 103, 211);
        double grain = FIELD.noise(x, z, 29, 212);
        int skew = (int) (38 * ridge + 14 * grain);
        int bands = terraces ? 12 : 8;
        for (int i = 0; i < bands; i++) {
            double spur = FIELD.noise(x, z, 73 + i * 4, 220 + i);
            double reach = terraces ? 32 + 22 * ridge + 29 * spur + 12 * grain : 21 + 24 * ridge + 31 * spur;
            if (setback >= reach) continue;
            double weight = smooth(1 - setback / reach);
            int center = -92 - i * (terraces ? 111 : 168) + skew + (int) (24 * spur);
            int height = 8 + (int) ((terraces ? 51 : 60) * weight);
            rock.add(new EdgeTerrain.Rock(center - height, Math.min(-24, center + height / 2), skew));
        }
        return rock;
    }

    static EdgeTerrain.Span at(int x, int z, int depth, EdgeTerrain.Layout layout) {
        EdgeTerrain.Span nearest = null;
        int distance = 60;
        for (var span : EdgeTerrain.column(x, z, layout).shelves()) {
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
