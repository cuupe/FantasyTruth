package dev.fantasytruth.abyss;

import dev.fantasytruth.abyss.ForestGrowth.Writer;
import dev.fantasytruth.terrain.AtlasField;
import java.util.SplittableRandom;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;

/** Staggered, overlapping stands of broadleaf trees cover the habitable terraces and valley floor. */
final class ForestCanopy {
    private static final int CELL = 16;
    private static final int REACH = 23;
    private static final BlockState JUNGLE = Blocks.JUNGLE_WOOD.defaultBlockState();
    private static final BlockState DARK_OAK = Blocks.DARK_OAK_WOOD.defaultBlockState();
    private static final BlockState[] CROWNS = {ForestGrowth.leaves(Blocks.JUNGLE_LEAVES),
            ForestGrowth.leaves(Blocks.DARK_OAK_LEAVES), ForestGrowth.leaves(Blocks.AZALEA_LEAVES)};

    static void place(Writer p, ForestTerrain terrain) {
        for (int cz = Math.floorDiv(p.z - REACH, CELL); cz <= Math.floorDiv(p.z + 15 + REACH, CELL); cz++) {
            for (int cx = Math.floorDiv(p.x - REACH, CELL); cx <= Math.floorDiv(p.x + 15 + REACH, CELL); cx++) {
                var random = new SplittableRandom(AtlasField.hash(terrain.seed, cx, 924, cz));
                int x = CELL * cx + random.nextInt(3, 14), z = CELL * cz + random.nextInt(3, 14);
                if (!p.intersects(x, z, REACH)) continue;
                var column = terrain.column(x, z);
                int variety = random.nextInt(8);
                int overhead = column.ledgeBottom() > column.floor() ? column.ledgeBottom() : AbyssLayer.MAX_Y;
                if (column.shelfBottom() > column.floor()) overhead = Math.min(overhead, column.shelfBottom());
                if (column.roofBottom() > column.floor()) overhead = Math.min(overhead, column.roofBottom());
                if (!column.chute() && column.floorWater() == column.floor() && overhead - column.floor() >= 23
                        && !lowerLandmark(x, z) && !ForestSpring.clearsFloor(terrain, x, z)
                        && (column.damp() || variety != 0)) {
                    int height = Math.min(overhead - column.floor() - 7,
                            variety == 7 ? random.nextInt(39, 65) : random.nextInt(21, 39));
                    if (height >= 15) tree(p, x, column.floor(), z, height, variety, random);
                }
                int top = column.shelfTop();
                if (top <= column.floor() + 12 || column.water() > top || upperLandmark(x, z) || variety == 0) continue;
                int roof = column.roofBottom();
                int height = Math.min(roof > top ? roof - top - 6 : 72, random.nextInt(25, 57));
                if (height >= 20) tree(p, x, top, z, height, variety, random);
            }
        }
        understory(p, terrain);
    }

    private static void understory(Writer p, ForestTerrain terrain) {
        for (int z = p.z + 1; z < p.z + 15; z += 3) for (int x = p.x + 1; x < p.x + 15; x += 3) {
            var c = terrain.column(x, z);
            if (c.chute() || c.floorWater() != c.floor() || lowerLandmark(x, z)) continue;
            int roll = (int) AtlasField.hash(terrain.seed, x, 413, z) & 31;
            if (roll < (c.damp() ? 9 : 15)) continue;
            int y = c.floor();
            if (roll >= 24) p.replace(x, y, z, Blocks.MOSS_BLOCK, Blocks.PODZOL.defaultBlockState());
            if (roll >= 28) {
                p.grow(x, y + 1, z, Blocks.AZALEA.defaultBlockState());
            } else if (roll >= 19) {
                var fern = Blocks.LARGE_FERN.defaultBlockState();
                p.tallPlant(x, y + 1, z, fern, fern.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
            } else {
                p.grow(x, y + 1, z, Blocks.FERN.defaultBlockState());
            }
        }
    }

    private static boolean lowerLandmark(int x, int z) {
        return close(x, z, ForestTerrain.CAMP_X - 41, ForestTerrain.CAMP_Z + 10, 28)
                || close(x, z, ForestTerrain.FALL_X, ForestTerrain.FALL_Z, 15)
                || close(x, z, ForestLandmarks.POND_X, ForestLandmarks.POND_Z, 31)
                || close(x, z, ForestLandmarks.DIG_X, ForestLandmarks.DIG_Z + 9, 19)
                || close(x, z, ForestLandmarks.FALL_X, ForestLandmarks.FALL_Z, 20);
    }

    private static boolean upperLandmark(int x, int z) {
        return close(x, z, ForestTerrain.FIRST_MILL_X, ForestTerrain.FIRST_MILL_Z, 31)
                || close(x, z, ForestLandmarks.NEST_X, ForestLandmarks.NEST_Z, 14)
                || AbyssLayer.passage(x, z, 36);
    }

    private static boolean close(int x, int z, int tx, int tz, int radius) {
        int dx = x - tx, dz = z - tz;
        return Math.abs(dx) < radius && Math.abs(dz) < radius && dx * dx + dz * dz < radius * radius;
    }

    private static void tree(Writer p, int x, int floor, int z, int height, int variety, SplittableRandom random) {
        var wood = variety % 3 == 0 ? DARK_OAK : JUNGLE;
        var leaf = CROWNS[variety % CROWNS.length];
        int top = floor + height;
        int tx = x + random.nextInt(-3, 4), tz = z + random.nextInt(-3, 4);
        int width = height > 39 ? 2 : 1;
        p.disc(x, floor + 1, z, width + 1, width + 1, wood, false);
        p.limb(x, floor + 1, z, x, floor + height / 2, z, width, wood);
        p.limb(x, floor + height / 2, z, tx, top, tz, 1, wood);
        double phase = random.nextDouble() * Math.PI * 2;
        int span = height >= 39 ? 9 : height >= 25 ? 7 : 5;
        crown(p, tx, top - 1, tz, span, span - 1, leaf);
        for (int i = 0; i < 4; i++) {
            double angle = phase + 2.399963 * i;
            int reach = span + random.nextInt(-2, 3);
            int bx = tx + (int) Math.round(Math.cos(angle) * reach);
            int bz = tz + (int) Math.round(Math.sin(angle) * reach);
            int by = top - random.nextInt(3, Math.min(12, height - 5));
            p.limb(x, Math.max(floor + 6, by - 7), z, bx, by, bz, 1, wood);
            crown(p, bx, by, bz, span - 1, span - 2, i == 0 ? CROWNS[(variety + 1) % 3] : leaf);
            if (i == 2) p.hang(bx, by - 4, bz, random.nextInt(3, 8), variety == 7);
        }
    }

    private static void crown(Writer p, int x, int y, int z, int rx, int rz, BlockState leaf) {
        for (int dy = -3; dy <= 4; dy++) {
            int taper = Math.abs(dy) / 2 + (dy == 4 ? 2 : 0);
            p.disc(x, y + dy, z, Math.max(2, rx - taper), Math.max(2, rz - taper), leaf, false);
        }
    }

    private ForestCanopy() {}
}
