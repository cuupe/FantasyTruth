package dev.fantasytruth.abyss;

import dev.fantasytruth.abyss.ForestGrowth.Writer;
import dev.fantasytruth.terrain.AtlasField;
import java.util.SplittableRandom;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Remains of the ancient wind-catching mills, plus relic deposits below the forest. */
final class ForestSites {
    private static final int CELL = 352;
    private static final int REACH = 26;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState CRACKED = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState LOG = Blocks.STRIPPED_DARK_OAK_WOOD.defaultBlockState();
    private static final BlockState PLANK = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final BlockState CLOTH = Blocks.LIGHT_GRAY_WOOL.defaultBlockState();

    static void place(Writer p, ForestTerrain terrain) {
        int mx = ForestTerrain.FIRST_MILL_X, mz = ForestTerrain.FIRST_MILL_Z;
        if (p.intersects(mx, mz, REACH)) mill(p, mx, terrain.column(mx, mz).shelfTop(), mz, 3);
        for (int gz = Math.floorDiv(p.z - REACH, CELL); gz <= Math.floorDiv(p.z + 15 + REACH, CELL); gz++) {
            for (int gx = Math.floorDiv(p.x - REACH, CELL); gx <= Math.floorDiv(p.x + 15 + REACH, CELL); gx++) {
                var rng = new SplittableRandom(AtlasField.hash(terrain.seed, gx, 354, gz));
                int x = gx * CELL + rng.nextInt(48, CELL - 48);
                int z = gz * CELL + rng.nextInt(48, CELL - 48);
                if (!p.intersects(x, z, REACH) || AbyssLayer.passage(x, z, 315)) continue;
                var c = terrain.column(x, z);
                int top = c.shelfTop();
                boolean clearance = c.roofTop() < ForestTerrain.MIN_Y || c.roofBottom() - top > 54;
                if (top > 270 && clearance && gentle(terrain, x, z, top)) {
                    mill(p, x, top, z, rng.nextInt(3, 5));
                }
                if (c.ledgeBottom() - c.floor() > 40 && c.ledgeBottom() < ForestTerrain.INVERTED) {
                    relicBed(p, terrain, x, z);
                }
            }
        }
    }

    private static boolean gentle(ForestTerrain terrain, int x, int z, int y) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (Math.abs(terrain.column(x + side.getStepX() * 9, z + side.getStepZ() * 9).shelfTop() - y) > 11) {
                return false;
            }
        }
        return true;
    }

    private static void mill(Writer p, int x, int y, int z, int sails) {
        for (int h = -13; h <= 23; h++) {
            int radius = h < 1 ? 9 : h < 16 ? 6 : 5;
            p.disc(x, y + h, z, radius, radius, h % 7 == 6 ? CRACKED : STONE, true);
            if (h >= 2 && h <= 21 && h != 11) p.disc(x, y + h, z, radius - 2, radius - 2, AIR, true);
        }
        for (int dx = -1; dx <= 1; dx++) for (int h = 1; h <= 4; h++) for (int dz = 3; dz <= 7; dz++) {
            p.set(x + dx, y + h, z + dz, AIR);
        }
        for (int dx = -2; dx <= 2; dx++) {
            p.set(x + dx, y, z + 6, STONE);
            p.set(x + dx, y + 1, z + 5, STONE);
        }
        p.barrel(x + 2, y + 2, z - 2, "edge_supplies");
        for (int h = 2; h <= 19; h++) p.set(x - 3, y + h, z, LOG);
        p.ladder(x - 2, y + 2, y + 19, z, Direction.EAST);
        for (int dx : new int[]{-4, 4}) for (int dz : new int[]{-4, 4}) {
            for (int h = -3; h <= 13; h++) p.set(x + dx, y + h, z + dz, h == 11 ? PLANK : STONE);
            for (int h = -4; h <= 4; h++) p.set(x + dx + Integer.signum(dx), y + h, z + dz, CRACKED);
        }
        for (int dx : new int[]{-5, 5}) {
            for (int h = 13; h <= 16; h++) {
                p.set(x + dx, y + h, z, Blocks.BROWN_STAINED_GLASS.defaultBlockState());
                p.set(x + dx - Integer.signum(dx), y + h, z, AIR);
            }
        }
        for (int h = 0; h < 8; h++) {
            int radius = Math.max(2, 7 - (h + 1) / 2);
            p.disc(x, y + 23 + h, z, radius, radius, h > 4
                    ? Blocks.OXIDIZED_CUT_COPPER.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState(), true);
        }
        p.set(x, y + 31, z, Blocks.LIGHTNING_ROD.defaultBlockState());
        p.set(x + 3, y + 12, z - 1, Blocks.GRINDSTONE.defaultBlockState());
        p.set(x + 3, y + 12, z + 2, Blocks.LANTERN.defaultBlockState());
        p.set(x - 2, y + 11, z + 1, AIR);
        p.limb(x, y + 17, z - 2, x, y + 17, z - 8, 2, LOG);
        for (int h = -2; h <= 2; h++) for (int dx = -2; dx <= 2; dx++) {
            if (dx * dx + h * h <= 5) p.set(x + dx, y + 17 + h, z - 9, Blocks.WAXED_CUT_COPPER.defaultBlockState());
        }
        for (int blade = 0; blade < sails; blade++) {
            double angle = 0.34 + blade * Math.PI / 2;
            double sx = Math.cos(angle), sy = Math.sin(angle);
            int tipX = x + (int) Math.round(20 * sx), tipY = y + 17 + (int) Math.round(20 * sy);
            p.limb(x, y + 17, z - 8, tipX, tipY, z - 8, 1, LOG);
            int length = blade == 1 ? 12 : 19;
            for (int u = 6; u <= length; u++) for (int v = 0; v <= 5 - (u - 6) / 6; v++) {
                if (blade == 1 && u + v > 14) continue;
                int px = x + (int) Math.round(u * sx - v * sy);
                int py = y + 17 + (int) Math.round(u * sy + v * sx);
                boolean seam = u % 5 == 0 || v == 5 - (u - 6) / 6;
                p.grow(px, py, z - 8, seam ? Blocks.SPRUCE_FENCE.defaultBlockState() : CLOTH);
            }
        }
    }

    private static void relicBed(Writer p, ForestTerrain terrain, int x, int z) {
        for (int dz = -8; dz <= 8; dz++) for (int dx = -8; dx <= 8; dx++) {
            if (dx * dx + dz * dz > 68 || !p.intersects(x + dx, z + dz, 0)) continue;
            int y = terrain.column(x + dx, z + dz).floor();
            int roll = (int) AtlasField.hash(terrain.seed, x + dx, 922, z + dz);
            if ((roll & 7) < 2) p.replace(x + dx, y, z + dz, Blocks.MOSS_BLOCK,
                    (roll & 8) == 0 ? ForestGrowth.Palette.MOSS : Blocks.TUFF.defaultBlockState());
            if ((roll & 31) == 20) p.grow(x + dx, y + 1, z + dz, ForestGrowth.Palette.block("relic_debris"));
        }
        int y = terrain.column(x, z).floor();
        p.barrel(x, y, z, "forest_relic_bed");
    }

    private ForestSites() {}
}
