package dev.fantasytruth.abyss;

import dev.fantasytruth.terrain.TerrainField;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The forest entrance uses the same cliffs on both sides of the dimension boundary. */
final class ForestMouth {
    static final int RADIUS = 258;
    static final int TRACKING_RADIUS = 142;
    static final int OUTLET_TRACKING_RADIUS = 54;
    static final int OFFSET = AbyssLayer.FOREST.entrance - EdgeTerrain.EXIT;
    static final int BOTTOM = EdgeTerrain.EXIT - 63;
    static final int SEAM_ROOF = EdgeTerrain.EXIT + 9;
    static final int PREVIEW_RADIUS = 248;
    private static final int PREVIEW_TOP = EdgeTerrain.EXIT - 8;
    private static final TerrainField FIELD = new TerrainField(0x466f726573745269L);
    private static final BlockState CANOPY = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    private static final BlockState SCRUB = Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);

    static boolean opening(int x, int z) {
        double squared = (double) x * x + (double) z * z;
        if (squared < 40 * 40) return true;
        return squared < TRACKING_RADIUS * TRACKING_RADIUS && entranceEdge(x, z) < 0;
    }

    static double throat(double angle) {
        return 73 + 17 * Math.sin(2 * angle + 0.63) + 12 * Math.cos(3 * angle - 0.45)
                + 6 * Math.sin(5 * angle + 0.12);
    }

    static double entranceEdge(int x, int z) {
        double wx = (x - 7 + 14 * FIELD.noise(x, z, 79, 8)) * 0.88;
        double wz = (z - 5 + x * 0.18 + 16 * FIELD.noise(x, z, 54, 9)) * 1.15;
        return Math.hypot(wx, wz) - throat(Math.atan2(wz, wx));
    }

    static double roofEdge(int x, int z) {
        return entranceEdge(x, z) - 57 - 22 * FIELD.noise(x, z, 102, 10);
    }

    static double outletDistance(int x, int z) {
        double wx = (x - 4 + 6 * FIELD.noise(x, z, 42, 11)) * 0.77;
        double wz = (z + x * 0.21 + 5 * FIELD.noise(x, z, 34, 12)) * 1.34;
        return Math.hypot(wx, wz) + 3 * FIELD.noise(x, z, 15, 13);
    }

    static boolean outlet(int x, int z) {
        return outletDistance(x, z) < 26;
    }

    static List<EdgeTerrain.Span> shelves(int x, int z, double radius) {
        if (radius >= RADIUS) return List.of(EdgeBasin.column(x, z, radius, true));
        if (radius < 28) return List.of();
        double angle = Math.atan2(z, x);
        double broad = FIELD.noise(x, z, 67, 1);
        double detail = FIELD.noise(x, z, 23, 2);
        double breaks = FIELD.noise(x, z, 39, 3);
        double innerEdge = radius < TRACKING_RADIUS + 25 ? entranceEdge(x, z) : radius - 95;
        boolean open = radius < 40 || radius < TRACKING_RADIUS && innerEdge < 0;
        double outside = smooth(Mth.clamp((radius - 208) / (RADIUS - 208.0), 0, 1));
        double highTongue = crest(angle + broad * 0.07, 6, 0.35);
        double lowTongue = crest(angle - 0.12 + detail * 0.05, 8, -0.9);
        double shoulder = 165 + 27 * Math.sin(2 * angle + 0.7) + 22 * Math.sin(3 * angle - 0.3)
                + 28 * broad + 15 * breaks;
        double upperEdge = shoulder - 86 * highTongue;
        double middleEdge = radius - innerEdge + 49 - 68 * lowTongue + 19 * detail;
        var spans = new ArrayList<EdgeTerrain.Span>(3);

        if (radius >= upperEdge) {
            double rise = smooth(Mth.clamp((radius - upperEdge) / 77, 0, 1));
            double upland = -1247 + 14 * broad + 11 * detail;
            int top = (int) Math.round(upland - (1 - rise) * (26 + 22 * highTongue));
            int thick = 13 + (int) (rise * 56 + highTongue * 11);
            int bottom = top - thick;
            if (open) bottom = Math.max(bottom, SEAM_ROOF);
            if (outside > 0) {
                var existing = EdgeBasin.column(x, z, radius, true);
                top = (int) Math.round(Mth.lerp(outside, top, existing.top()));
                bottom = (int) Math.round(Mth.lerp(outside, bottom, existing.bottom()));
            }
            add(spans, bottom, top, breaks < -0.39 ? EdgeLandscape.Cover.FERN : EdgeLandscape.Cover.WOODLAND);
        }
        if (radius >= middleEdge) {
            double support = smooth(Mth.clamp((radius - middleEdge) / 55, 0, 1));
            int top = -1321 + (int) (13 * support + 10 * broad + 9 * detail + 8 * lowTongue);
            int bottom = top - 11 - (int) (31 * support + 6 * lowTongue);
            if (open) bottom = Math.max(bottom, SEAM_ROOF);
            add(spans, bottom, top, EdgeLandscape.Cover.FERN);
        }
        if (!open) {
            double setback = smooth(Mth.clamp(innerEdge / 83, 0, 1));
            int top = -1343 + (int) (setback * 38 + 10 * broad + 7 * detail);
            int bottom = EdgeTerrain.EXIT - (int) (10 + setback * (43 + 12 * broad) + 6 * detail);
            add(spans, Math.max(BOTTOM, Math.min(bottom, top - 7)), top, EdgeLandscape.Cover.PIT_MOSS);
        }
        return spans;
    }

    private static void add(List<EdgeTerrain.Span> spans, int bottom, int top, EdgeLandscape.Cover cover) {
        if (bottom > top) return;
        if (!spans.isEmpty()) {
            var upper = spans.get(spans.size() - 1);
            if (upper.bottom() <= top + 1) {
                var coverAtTop = top > upper.top() ? cover : upper.cover();
                int surface = Math.max(top, upper.top());
                spans.set(spans.size() - 1, new EdgeTerrain.Span(Math.min(bottom, upper.bottom()),
                        surface, coverAtTop, surface));
                return;
            }
        }
        spans.add(new EdgeTerrain.Span(bottom, top, cover, top));
    }

    private static double crest(double angle, int count, double phase) {
        double ridge = Mth.clamp((Math.cos(angle * count + phase + 0.63 * Math.sin(3 * angle)) + 0.19) / 1.19, 0, 1);
        return smooth(ridge);
    }

    static int inletAttachment(List<EdgeTerrain.Span> spans) {
        if (spans.isEmpty()) return ForestTerrain.MIN_Y;
        var lowest = spans.get(spans.size() - 1);
        int bottom = Math.max(BOTTOM, lowest.bottom());
        int height = bottom + OFFSET;
        return height <= 820 && lowest.top() - bottom >= 8 ? height : ForestTerrain.MIN_Y;
    }

    static BlockState rock(List<EdgeTerrain.Span> spans, int y) {
        for (var span : spans) {
            if (y > span.top() || y < span.bottom()) continue;
            return EdgeTerrain.shelfMaterial(span, y, EdgeTerrain.Layout.FOREST_MOUTH);
        }
        return AbyssShape.AIR;
    }

    static void grow(EdgeExploration.Painter p) {
        // Plant overlapping low crowns on the rock tongues, including the inaccessible lower shelves.
        for (int z = p.z0; z < p.z0 + 16; z++) {
            for (int x = p.x0; x < p.x0 + 16; x++) {
                int dx = OrthIsland.localX(x), dz = OrthIsland.localZ(z);
                double radius = Math.hypot(dx, dz);
                if (radius < 31 || radius >= 222) continue;
                double thicket = FIELD.noise(dx, dz, 16, 32);
                double detail = FIELD.noise(dx, dz, 7, 31);
                for (var span : shelves(dx, dz, radius)) {
                    int y = span.top();
                    if (y < SEAM_ROOF + 2 || !p.get(x, y, z).is(Blocks.MOSS_BLOCK)) continue;
                    double density = thicket + detail * 0.60;
                    int height = (int) (13 * Mth.clamp(density + 0.20, 0, 0.72));
                    if (height < 2) continue;
                    var leaves = thicket > 0.08 ? CANOPY : SCRUB;
                    int roll = EdgeExploration.hash(x, y, z);
                    if (height > 4 && (roll & 31) == 0) {
                        for (int dy = 1; dy <= height - 2; dy++) {
                            p.grow(x, y + dy, z, Blocks.JUNGLE_WOOD.defaultBlockState());
                        }
                    }
                    // Keep the scrub connected even where there is no separate trunk.
                    for (int dy = 1; dy <= height; dy++) p.grow(x, y + dy, z, leaves);
                    if (density > 0.20 && (roll & 15) == 1 && p.get(x, span.bottom(), z).blocksMotion()) {
                        for (int dy = 1; dy <= 4; dy++) {
                            int rootY = span.bottom() - dy;
                            if (rootY < SEAM_ROOF && opening(dx, dz)) break;
                            if (!p.get(x, rootY, z).isAir()) break;
                            p.grow(x, rootY, z, Blocks.MANGROVE_ROOTS.defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    static void preview(ChunkAccess chunk, ForestTerrain terrain) {
        int low = Math.max(chunk.getMinBuildHeight() + 1, EdgeTerrain.MIN_Y + 2);
        if (low > PREVIEW_TOP) return;
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        var columns = new boolean[256];
        boolean any = false;
        var pos = new BlockPos.MutableBlockPos();
        for (int z = z0; z < z0 + 16; z++) for (int x = x0; x < x0 + 16; x++) {
            // Stop the overlap at the same irregular shelf rim, not at a chunk boundary.
            int dx = OrthIsland.localX(x), dz = OrthIsland.localZ(z);
            double r = Math.hypot(dx, dz) + FIELD.noise(dx, dz, 55, 81) * 12;
            if (r >= PREVIEW_RADIUS - 14) continue;
            columns[((z - z0) << 4) + x - x0] = true;
            any = true;
            var column = terrain.column(x, z);
            for (int y = low; y <= PREVIEW_TOP; y++) {
                var state = terrain.block(column, y + OFFSET);
                var target = pos.set(x, y, z);
                if (!chunk.getBlockState(target).equals(state)) chunk.setBlockState(target, state, false);
            }
        }
        // The forest uses normal world coordinates. Only the write height is translated.
        if (any) ForestGrowth.previewCanopy(
                new ForestGrowth.Writer(chunk, -OFFSET, low + OFFSET, PREVIEW_TOP + OFFSET, columns), terrain);
    }

    private static double smooth(double v) { return v * v * (3 - 2 * v); }

    private ForestMouth() {}
}
