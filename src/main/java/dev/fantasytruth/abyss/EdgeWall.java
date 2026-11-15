package dev.fantasytruth.abyss;

import dev.fantasytruth.abyss.EdgeExploration.Painter;
import dev.fantasytruth.abyss.EdgeExploration.Site;
import dev.fantasytruth.abyss.EdgeLandscape.Cover;
import dev.fantasytruth.terrain.TerrainField;
import java.util.function.Consumer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Rock-foot terrain and exploration sites on the wallward side of the main trail. */
final class EdgeWall {
    private static final TerrainField FIELD = new TerrainField(0x4564676557616c6cL);


    static EdgeTerrain.Span slope(int x, int z, double distance, double detail, int base, boolean wild) {
        double blend = smooth(Mth.clamp((distance - 9) / 29, 0, 1));
        double patch = FIELD.noise(x, z, 48, 1);
        double grain = FIELD.noise(x, z, 20, 2);
        double ravine = 1 - smooth(Mth.clamp(Math.abs(FIELD.noise(x + 19 * detail, z - 23 * patch, 61, 3) - 0.08) * 5, 0, 1));
        double rib = smooth(Mth.clamp((FIELD.noise(x, z, 33, 4) - 0.04) * 2.6, 0, 1));
        double relief = 27 * patch + 13 * grain + 28 * rib - 24 * ravine - 11;
        int top = Math.min(-3, base + (int) Math.round(blend * relief));
        Cover cover;
        if (distance < 20) cover = detail + patch > 0.04 ? Cover.FERN : Cover.MEADOW;
        else if (rib > 0.69 && grain > 0.13) cover = Cover.SCREE;
        else if (ravine > 0.66 && patch < 0.18) cover = Cover.WETLAND;
        else if (patch + detail * 0.38 < -0.34) cover = Cover.FLOWERS;
        else cover = grain > 0.16 - 0.4 * patch ? Cover.FERN : Cover.WOODLAND;

        if (wild && cover == Cover.SCREE && grain < 0.28) cover = Cover.FERN;
        int thickness = 27 + (int) (14 * blend + 18 * rib);
        return new EdgeTerrain.Span(top - thickness, top, cover, top);
    }


    static double innerRelief(int x, int z, double radius, double trail) {
        double edge = smooth(Mth.clamp((radius - 350) / 21, 0, 1));
        double tread = smooth(Mth.clamp((trail - radius - 9) / 18, 0, 1));
        return edge * tread * (13 * FIELD.noise(x, z, 41, 10) + 8 * FIELD.noise(x, z, 16, 11));
    }

    @FunctionalInterface
    interface Index {
        void add(Site site, int extent, Consumer<Painter> feature);
    }

    static void plan(Index index, EdgeTerrain.Layout layout) {
        for (int depth = 29; depth < 1223; depth += 11) {
            int roll = EdgeExploration.hash(depth, 411, 0);
            for (int band = 0; band < 3; band++) {
                int landmarkDepth = depth + Math.floorMod(roll >>> (band * 5), 13) - 6;
                int radius = 443 + 30 * band + Math.floorMod(roll >>> (band * 6 + 4), 19) - 9;
                var point = EdgeLandscape.point(landmarkDepth, radius);
                var span = EdgeLandscape.at(point.x(), point.z(), landmarkDepth, layout);
                if (span == null || span.cover() == Cover.TRAIL || span.water() > span.top()
                        || EdgeExploration.nearLandmark(point.x(), span.top(), point.z())) continue;
                var site = new Site(point.x(), span.top() + 1, point.z());
                int seed = EdgeExploration.hash(site.x(), site.y(), site.z());
                if ((seed & 15) == 0 || span.cover() == Cover.SCREE) {
                    index.add(site, 10, p -> boulders(p, site, seed, layout));
                } else if (!steep(site, landmarkDepth, layout) || (seed & 3) == 1 && clear(site, layout)) {
                    int tree = (seed & 31) == 1 ? EdgeVegetation.ELDER : ((seed & 3) == 1 ? 2 : seed & 1);
                    index.add(site, tree == EdgeVegetation.ELDER ? 39 : 27,
                            p -> EdgeVegetation.tree(p, site, tree, seed));
                } else {
                    index.add(site, 8, p -> thicket(p, site, seed, layout));
                }
            }
        }

        for (int depth : new int[]{96, 180, 263, 365, 479, 571, 654, 748, 838, 951, 1034, 1126, 1203}) {
            for (int radius : new int[]{455, 478, 442}) {
                var point = EdgeLandscape.point(depth, radius);
                var span = EdgeLandscape.at(point.x(), point.z(), depth, layout);
                if (span == null || span.cover() == Cover.TRAIL || span.water() > span.top()
                        || EdgeExploration.nearLandmark(point.x(), span.top(), point.z())) continue;
                var site = new Site(point.x(), span.top() + 1, point.z());
                if (steep(site, depth, layout)) continue;
                int type = Math.floorMod(depth, 3);
                index.add(site, 10, p -> outpost(p, site, type));
                break;
            }
        }
    }

    static void growScarps(Painter p, int x, int z, EdgeTerrain.Column column) {
        if (column.rocks().isEmpty()) return;
        var stone = OrthBlocks.STONE.get().defaultBlockState();
        double moisture = FIELD.noise(x, z, 32, 25);
        for (var ledge : column.rocks()) {
            int top = ledge.top();
            if (p.get(x, top, z) != stone || !p.get(x, top + 1, z).isAir()) continue;
            int roll = EdgeExploration.hash(x, top, z) & 63;
            if (moisture + ((roll & 7) - 3) * 0.033 > -0.09) {
                var moss = (roll == 0 ? OrthBlocks.GLOW_MOSS.get() : Blocks.MOSS_BLOCK).defaultBlockState();
                p.set(x, top, z, moss);
                if (roll < 15) p.grow(x, top + 1, z, (roll < 5 ? Blocks.FERN : Blocks.MOSS_CARPET).defaultBlockState());
            } else if (roll < 6) {
                p.set(x, top, z, Blocks.TUFF.defaultBlockState());
            }
            if (roll < 11 && moisture > 0.04 && p.get(x, ledge.bottom(), z) == stone) {
                p.hang(x, ledge.bottom() - 1, z, Blocks.HANGING_ROOTS.defaultBlockState());
            }
        }
    }

    private static boolean clear(Site site, EdgeTerrain.Layout layout) {
        var column = EdgeTerrain.column(site.x(), site.z(), layout);
        for (var rock : column.rocks()) {
            if (rock.top() >= site.y() && rock.bottom() < site.y() + 19) return false;
        }
        return true;
    }

    private static boolean steep(Site site, int depth, EdgeTerrain.Layout layout) {
        for (int direction : new int[]{-3, 3}) {
            var across = EdgeLandscape.at(site.x() + direction, site.z(), depth, layout);
            var along = EdgeLandscape.at(site.x(), site.z() + direction, depth, layout);
            if (across == null || along == null || Math.abs(across.top() + 1 - site.y()) > 5
                    || Math.abs(along.top() + 1 - site.y()) > 5) return true;
        }
        return false;
    }

    private static void thicket(Painter p, Site site, int seed, EdgeTerrain.Layout layout) {
        int x = site.x(), z = site.z();
        for (int bz = Math.max(p.z0, z - 8); bz <= Math.min(p.z0 + 15, z + 8); bz++) {
            for (int bx = Math.max(p.x0, x - 8); bx <= Math.min(p.x0 + 15, x + 8); bx++) {
                int dx = bx - x, dz = bz - z;
                int roll = EdgeExploration.hash(bx, seed, bz);
                if (dx * dx + dz * dz > 39 + (roll & 31)) continue;
                var ground = EdgeLandscape.at(bx, bz, 1 - site.y(), layout);
                if (ground == null || ground.cover() == Cover.TRAIL || ground.water() > ground.top()) continue;
                int h = 1 + (int) (3 * Math.max(0, FIELD.noise(bx, bz, 7, seed) + 0.55));
                for (int dy = 1; dy <= h; dy++) {
                    p.grow(bx, ground.top() + dy, bz, (roll & 31) == 0 ? Plants.BLOSSOMS : Plants.LEAVES);
                }
            }
        }
    }

    private static final class Plants {
        static final BlockState LEAVES = Blocks.AZALEA_LEAVES.defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, true);
        static final BlockState BLOSSOMS = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, true);
    }

    private static void boulders(Painter p, Site site, int seed, EdgeTerrain.Layout layout) {
        int x = site.x(), z = site.z(), y = site.y() - 1;
        for (int i = 0; i < 4; i++) {
            int roll = EdgeExploration.hash(x + i * 7, seed, z);
            int bx = x + Math.floorMod(roll, 11) - 5;
            int bz = z + Math.floorMod(roll >>> 11, 11) - 5;
            int r = 2 + (roll >>> 21 & 3);
            var ground = EdgeLandscape.at(bx, bz, -y, layout);
            if (ground == null || ground.cover() == Cover.TRAIL || Math.abs(ground.top() - y) > 10) continue;
            p.ellipsoid(bx, ground.top() - 1, bz, r, r + (roll >>> 24 & 1), Math.max(2, r - 1),
                    (i == 1 ? Blocks.MOSSY_COBBLESTONE : Blocks.TUFF).defaultBlockState());
        }
    }

    private static void outpost(Painter p, Site site, int type) {
        int x = site.x(), y = site.y(), z = site.z();
        var rock = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        var spruce = Blocks.SPRUCE_PLANKS.defaultBlockState();
        p.box(x, y - 3, z, 5, 2, 4, rock);
        p.box(x, y - 1, z, 5, 1, 4, type == 0 ? spruce : rock);
        p.box(x, y, z, 5, 4, 4, Blocks.AIR.defaultBlockState());
        if (type == 0) {
            for (int dx : new int[]{-4, 4}) for (int dz : new int[]{-3, 3}) {
                p.column(x + dx, z + dz, y, y + 3, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            }
            for (int dz = -3; dz <= 3; dz++) {
                p.box(x, y + 5 - Math.abs(dz) / 2, z + dz, 5, 1, 0, Blocks.BROWN_WOOL.defaultBlockState());
            }
            p.set(x - 3, y, z, Blocks.CRAFTING_TABLE.defaultBlockState());
        } else {
            for (int dx : new int[]{-4, 4}) for (int dz : new int[]{-3, 3}) {
                p.column(x + dx, z + dz, y, y + (dx == dz + 1 ? 4 : 2), rock);
            }
            p.box(x, y, z - 4, 3, 1, 0, Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
            p.set(x + 2, y, z - 2, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        }
        p.set(x, y, z + 3, Blocks.LANTERN.defaultBlockState());
        p.barrel(x + 3, y, z - 2, type == 0 ? "edge_supplies" : "edge_relics");
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    private EdgeWall() {}
}
