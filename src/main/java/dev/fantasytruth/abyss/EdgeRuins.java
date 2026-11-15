package dev.fantasytruth.abyss;

import dev.fantasytruth.abyss.EdgeExploration.Painter;
import dev.fantasytruth.abyss.EdgeExploration.Site;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/** Eroded masonry marks the entrances to small chambers cut into the shaft wall. */
final class EdgeRuins {
    static void plan(EdgeWall.Index index, EdgeTerrain.Layout layout) {
        for (int depth : new int[]{54, 127, 206, 337, 450, 553, 641, 735, 858, 934, 1049, 1144, 1209}) {
            for (int offset : new int[]{0, 9, -9, 18, -18}) {
                int at = depth + offset;
                double angle = EdgeLandscape.angle(at);
                if (Math.abs(Math.cos(angle)) < 0.25) continue; // Ladder approaches.
                Direction intoWall = Direction.getNearest(Math.cos(angle), 0, Math.sin(angle));
                var point = EdgeLandscape.point(at, 462);
                var span = EdgeLandscape.at(point.x(), point.z(), at, layout);
                if (span == null || span.water() > span.top() || Math.abs(span.top() + at) > 57) continue;
                int x = point.x(), z = point.z(), y = span.top() + 1;
                int dx = intoWall.getStepX(), dz = intoWall.getStepZ();
                int length = 0;
                while (length < 68 && OrthIsland.inShaft(x + dx * length, z + dz * length)) length += 2;
                if (length < 9 || length > 49
                        || EdgeExploration.nearLandmark(x, y, z)
                        || EdgeExploration.nearLandmark(x + dx * length, y, z + dz * length)) continue;
                var site = new Site(x, y, z);
                var wall = new Site(x + dx * (length - 5), y, z + dz * (length - 5));
                int reach = length;
                index.add(wall, 21, p -> chamber(p, wall, intoWall, at));
                index.add(site, reach + 4, p -> approach(p, site, intoWall, reach, at));
                break;
            }
        }
    }

    private static void approach(Painter p, Site site, Direction intoWall, int length, int salt) {
        int dx = intoWall.getStepX(), dz = intoWall.getStepZ();
        for (int along = 0; along < length - 8; along++) {
            for (int side = -2; side <= 2; side++) {
                int x = site.x() + dx * along - dz * side, z = site.z() + dz * along + dx * side;
                int y = site.y();
                if (!p.inside(x, y, z)) continue;
                if (Math.abs(side) == 2 && (EdgeExploration.hash(x, salt, z) & 3) == 0) continue;
                var stone = along < 6 ? Blocks.COARSE_DIRT : Blocks.MOSSY_COBBLESTONE;
                p.set(x, y - 1, z, stone.defaultBlockState());
                p.grow(x, y - 2, z, Blocks.TUFF.defaultBlockState());
                for (int h = 0; h < 3; h++) p.set(x, y + h, z, Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void chamber(Painter p, Site site, Direction intoWall, int salt) {
        int dx = intoWall.getStepX(), dz = intoWall.getStepZ(), y = site.y();
        boolean gallery = (salt & 1) == 0;
        for (int along = -3; along <= 1; along++) {
            for (int side : new int[]{-5, 5}) {
                int x = site.x() + dx * along - dz * side, z = site.z() + dz * along + dx * side;
                for (int h = -6; h <= 6 + (Math.abs(along) & 1); h++) {
                    p.set(x, y + h, z, (h == 0 || h == 5 ? Blocks.CHISELED_STONE_BRICKS
                            : h < 0 ? Blocks.TUFF : Blocks.MOSSY_STONE_BRICKS).defaultBlockState());
                }
            }
        }
        for (int along = -4; along <= 15; along++) {
            int width = along < 5 ? (along < 0 ? 3 : 4) : gallery ? 6 : 4;
            for (int side = -width; side <= width; side++) {
                int x = site.x() + dx * along - dz * side, z = site.z() + dz * along + dx * side;
                if (!p.inside(x, y, z)) continue;
                int roll = EdgeExploration.hash(x, salt, z);
                var lining = (roll & 3) == 0 ? Blocks.CRACKED_STONE_BRICKS : Blocks.MOSSY_STONE_BRICKS;
                p.set(x, y - 1, z, lining.defaultBlockState());
                if (along < 2) p.grow(x, y - 2, z, Blocks.TUFF.defaultBlockState());
                int roof = 3 + (width - Math.abs(side)) / 2;
                if (along >= 0) p.set(x, y + roof, z, lining.defaultBlockState());
                if (along == 0 || along == 4) {
                    p.set(x, y + roof + 1, z, lining.defaultBlockState());
                    if (Math.abs(side) == width) p.grow(x, y + roof + 2, z, Blocks.MOSS_BLOCK.defaultBlockState());
                }
                for (int h = 0; h < roof; h++) {
                    boolean column = Math.abs(side) == width && (along == 0 || along == 4 || along == 10 || along >= 14);
                    var material = column ? lining : Blocks.CAVE_AIR;
                    if (along == 0 && Math.abs(side) == width && h == roof - 1) material = Blocks.CHISELED_STONE_BRICKS;
                    p.set(x, y + h, z, material.defaultBlockState());
                }
            }
        }
        for (int side : new int[]{-3, 3}) {
            int x = site.x() + dx * 8 - dz * side, z = site.z() + dz * 8 + dx * side;
            p.set(x, y, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            p.set(x, y + 1, z, Blocks.SOUL_LANTERN.defaultBlockState());
        }
        for (int along = 6; along <= 13; along += 3) {
            for (int side : new int[]{-4, 4}) {
                int x = site.x() + dx * along - dz * side, z = site.z() + dz * along + dx * side;
                p.set(x, y, z, (gallery ? Blocks.POLISHED_ANDESITE : Blocks.TUFF).defaultBlockState());
                p.set(x, y + 1, z, ForestGrowth.Palette.block(gallery ? "relic_debris" : "bone_pile"));
            }
        }
        for (int side = -3; side <= 3; side++) {
            int x = site.x() + dx * 14 - dz * side, z = site.z() + dz * 14 + dx * side;
            p.set(x, y, z, Blocks.CUT_SANDSTONE.defaultBlockState());
            if (Math.abs(side) >= 2) p.set(x, y + 1, z, Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
        }
        p.barrel(site.x() + dx * 13 - dz * 2, y, site.z() + dz * 13 + dx * 2, "edge_relics");
    }

    private EdgeRuins() {}
}
