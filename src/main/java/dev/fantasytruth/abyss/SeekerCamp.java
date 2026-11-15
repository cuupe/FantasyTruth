package dev.fantasytruth.abyss;

import dev.fantasytruth.abyss.ForestGrowth.Writer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The lookout is built into the tapered crown of the inverted Giant Porta Tree. */
final class SeekerCamp {
    private static final int X = ForestTerrain.CAMP_X, Z = ForestTerrain.CAMP_Z, Y = ForestTerrain.CAMP_Y;
    private static final int HOIST_X = X - 47, HOIST_Z = Z + 5;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState BARK = Blocks.DARK_OAK_WOOD.defaultBlockState();
    private static final BlockState FLOOR = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final BlockState TRIM = Blocks.STRIPPED_DARK_OAK_WOOD.defaultBlockState();
    private static final BlockState RAIL = Blocks.DARK_OAK_FENCE.defaultBlockState();
    private static final BlockState METAL = Blocks.CUT_COPPER.defaultBlockState();
    private static final BlockState CAGE = Blocks.IRON_BARS.defaultBlockState();
    private static final BlockState LEAVES = ForestGrowth.leaves(Blocks.DARK_OAK_LEAVES);
    private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
    private static final BlockState WINDOW = Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
    private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState();
    private static final BlockState LIGHT = Blocks.LANTERN.defaultBlockState();

    static void place(Writer p) {
        trunk(p);
        rooms(p);
        dock(p);
    }

    private static void trunk(Writer p) {
        if (p.intersects(X + 6, Z + 3, 36)) {
            for (int y = Y + 19; y <= ForestTerrain.PORTA_ANCHOR + 22; y++) {
                double root = ForestTerrain.smooth(Y + 36, ForestTerrain.PORTA_ANCHOR, y);
                int r = (int) (12 + 8 * root + 1.6 * Math.sin(y * 0.088));
                p.disc(X + (int) (root * 9), y, Z + (int) (root * 4), r, r - 2, BARK, false);
            }
        }
        for (int i = 0; i < 9; i++) {
            double angle = i * 2.399963;
            int x = X + (int) ((28 + i % 4 * 4) * Math.cos(angle));
            int z = Z + (int) ((28 + i % 4 * 4) * Math.sin(angle));
            int y = Y - 44 - i % 4 * 5;
            p.limb(X, Y - 27, Z, x, y + 3, z, 4, BARK);
            for (int h = -8; h <= 9; h++) {
                int r = 15 - Math.abs(h);
                p.disc(x, y + h, z, r, r - 3, h < -5 ? MOSS : LEAVES, false);
            }
            p.hang(x, y - 9, z, 8 + i, i % 3 == 1);
        }
        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI / 3 + 0.24;
            int x = X + (int) (52 * Math.cos(angle));
            int z = Z + (int) (49 * Math.sin(angle));
            p.limb(X + 9, -280 + i * 7, Z + 4, x, ForestTerrain.PORTA_ANCHOR + 24, z, 4, BARK);
        }
    }

    private static void rooms(Writer p) {
        for (int z = Math.max(p.z, Z - 35); z <= Math.min(p.z + 15, Z + 35); z++) {
            for (int x = Math.max(p.x, X - 37); x <= Math.min(p.x + 15, X + 37); x++) {
                double dx = x - X, dz = (z - Z) * 1.10;
                double distance = Math.hypot(dx, dz);
                double angle = Math.atan2(dz, dx);
                double rib = (1 + Math.sin(11 * angle + 0.6 * Math.sin(3 * angle))) * 0.8;
                for (int dy = -40; dy <= 28; dy++) {
                    double radius = 30 + rib;
                    if (dy > 11) radius -= 16 * ForestTerrain.smooth(11, 31, dy);
                    if (dy < -12) radius -= 20 * ForestTerrain.smooth(13, 42, -dy);
                    if (distance > radius) {
                        if (dy == 0 && distance < 36 || dy == -12 && distance < 33) p.set(x, Y + dy, z, FLOOR);
                        if (dy == 1 && distance >= 35 && distance < 36 || dy == -11 && distance >= 32 && distance < 33) {
                            p.set(x, Y + dy, z, RAIL);
                        }
                        continue;
                    }
                    boolean core = distance < radius - 2.2;
                    if (dy >= 12) {
                        double cap = 24 + 4 * (1 - distance / 30);
                        if (dy < cap && core && dy <= 22) {
                            p.set(x, Y + dy, z, AIR);
                        } else {
                            boolean green = Math.cos(angle * 7 + distance * 0.28) + Math.sin(angle * 3 - distance * 0.18) > -0.35;
                            p.set(x, Y + dy, z, dy > 21 && green ? MOSS : dy > 21
                                    ? Blocks.OXIDIZED_CUT_COPPER.defaultBlockState() : TRIM);
                        }
                    } else if (dy == 0 || dy == -12 || dy == -24) {
                        p.set(x, Y + dy, z, core ? FLOOR : TRIM);
                    } else if (core && dy > -24) {
                        boolean lamp = dy == 6 && distance >= radius - 3.7
                                && Math.cos(14 * angle + 0.45 * Math.sin(3 * angle)) > 0.92;
                        p.set(x, Y + dy, z, lamp ? Blocks.SHROOMLIGHT.defaultBlockState() : AIR);
                    } else if (dy > 0 && dy < 11) {
                        double frame = Math.cos(14 * angle + 0.45 * Math.sin(3 * angle));
                        boolean pane = dy >= 2 && dy <= 8 && frame > (dy == 8 ? 0.975 : 0.88);
                        var wall = dy == 1 || dy == 5 || dy == 10 || frame < -0.93 ? TRIM : BARK;
                        if (pane && dy != 5) wall = WINDOW;
                        p.set(x, Y + dy, z, wall);
                    } else {
                        boolean pane = dy >= -20 && dy <= -16 && Math.cos(10 * angle + 1) > 0.91;
                        p.set(x, Y + dy, z, pane ? WINDOW : BARK);
                    }
                }
            }
        }
        furnishGalleries(p);
        telescope(p, X - 12, Z - 18, Y - 12, 24);
        telescope(p, X + 12, Z - 19, Y - 15, 20);
        // The backed ladder connects the three inhabited decks.
        for (int y = Y - 23; y <= Y + 2; y++) {
            p.set(X + 2, y, Z - 4, BARK);
            p.set(X + 2, y, Z - 3, AIR);
        }
        p.ladder(X + 2, Y - 23, Y + 2, Z - 3, Direction.SOUTH);
        for (int dx = -15; dx <= 15; dx++) {
            p.set(X + dx, Y + 1, Z + 13, dx % 6 == 0 ? Blocks.SMOKER.defaultBlockState() : FLOOR);
            if (dx % 5 == 0) p.set(X + dx, Y + 2, Z + 13, LIGHT);
        }
        for (int dz = -11; dz <= -7; dz++) for (int dx = -15; dx <= -6; dx++) {
            p.set(X + dx, Y + 1, Z + dz, FLOOR);
            if (dz == -7 && dx % 3 == 0) p.set(X + dx, Y + 2, Z + dz, Blocks.FLOWER_POT.defaultBlockState());
        }
        for (int dz = 3; dz <= 12; dz += 3) {
            p.set(X + 15, Y + 1, Z - dz, ForestGrowth.Palette.block("camp_bedroll"));
            p.barrel(X + 17, Y + 1, Z - dz, "edge_supplies");
        }

        p.disc(X - 10, Y - 11, Z + 3, 8, 7, Blocks.SMOOTH_STONE.defaultBlockState(), true);
        p.disc(X - 10, Y - 11, Z + 3, 7, 6, Blocks.WATER.defaultBlockState(), true);
        for (int dx = 3; dx <= 13; dx += 2) p.barrel(X + dx, Y - 23, Z + 8, "edge_relics");
        for (int dx : new int[]{-12, 0, 12}) {
            p.set(X + dx, Y + 1, Z + 1, LIGHT);
            p.set(X + dx, Y - 11, Z - 2, LIGHT);
            p.set(X + dx, Y - 23, Z + 2, LIGHT);
        }
    }

    private static void furnishGalleries(Writer p) {
        // Partitions preserve the west-to-east arrival route along the south side.
        for (int dx = -21; dx <= 21; dx++) {
            for (int h = 1; h <= 8; h++) {
                boolean door = Math.abs(dx - 5) <= 2 || Math.abs(dx + 13) <= 1;
                if (door && h <= 4) continue;
                p.set(X + dx, Y + h, Z - 6, h == 8 || dx % 6 == 0 ? TRIM : FLOOR);
            }
        }
        for (int dz = -17; dz <= -7; dz++) {
            for (int h = 1; h <= 8; h++) p.set(X - 3, Y + h, Z + dz, h == 8 ? TRIM : FLOOR);
        }
        for (int dx = -20; dx <= 18; dx++) {
            if (dx % 6 == 0) {
                p.set(X + dx, Y + 3, Z - 5, LIGHT.setValue(LanternBlock.HANGING, true));
                p.set(X + dx, Y + 4, Z - 5, TRIM);
            }
        }
        for (int dx = 1; dx <= 18; dx++) {
            p.set(X + dx, Y + 1, Z - 16, dx % 5 == 0 ? Blocks.CARTOGRAPHY_TABLE.defaultBlockState()
                    : Blocks.BOOKSHELF.defaultBlockState());
            if (dx % 3 != 0) p.set(X + dx, Y + 2, Z - 16, Blocks.BOOKSHELF.defaultBlockState());
        }
        p.set(X + 5, Y, Z - 11, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        p.set(X + 8, Y + 1, Z - 10, Blocks.LECTERN.defaultBlockState());
        p.set(X + 9, Y + 1, Z - 10, Blocks.POTTED_FERN.defaultBlockState());
        p.set(X + 9, Y, Z - 10, TRIM);
        for (int bx : new int[]{X - 16, X - 6, X + 11}) {
            for (int dz = 2; dz <= 4; dz++) {
                p.set(bx, Y + 1, Z + dz, Blocks.DARK_OAK_FENCE.defaultBlockState());
                p.set(bx, Y + 2, Z + dz, Blocks.DARK_OAK_SLAB.defaultBlockState());
                p.set(bx - 1, Y + 1, Z + dz, Blocks.SPRUCE_STAIRS.defaultBlockState());
                p.set(bx + 1, Y + 1, Z + dz, Blocks.SPRUCE_STAIRS.defaultBlockState());
            }
        }
        for (int dz = 2; dz <= 12; dz++) for (int h = 1; h <= 6; h++) {
            if (dz == 6 || dz == 7 || dz == 8) continue;
            p.set(X, Y - 12 + h, Z + dz, h == 6 ? TRIM : FLOOR);
        }
        for (int dx = -16; dx <= -4; dx += 4) p.set(X + dx, Y - 11, Z + 12, Blocks.LANTERN.defaultBlockState());
        for (int dz = -9; dz <= -5; dz += 2) {
            p.set(X - 7, Y - 23, Z + dz, Blocks.CRAFTING_TABLE.defaultBlockState());
            p.set(X - 9, Y - 23, Z + dz, Blocks.BARREL.defaultBlockState());
        }
    }

    private static void telescope(Writer p, int x, int z, int y, int length) {
        if (!p.intersects(x, z - length / 2, length / 2 + 5)) return;
        p.limb(x, Y - 27, z - 3, x, y - 1, z - 5, 2, BARK);
        for (int step = 0; step <= length; step++) {
            int centerY = y - step / 3;
            int radius = step < 4 || step > length - 4 ? 4 : 3;
            BlockState rim = step % 6 == 0 || step == length ? METAL : Blocks.WEATHERED_CUT_COPPER.defaultBlockState();
            for (int dx = -radius; dx <= radius; dx++) for (int dy = -radius; dy <= radius; dy++) {
                int distance = dx * dx + dy * dy;
                if (distance > radius * radius) continue;
                if (distance >= (radius - 1) * (radius - 1)) p.set(x + dx, centerY + dy, z - step, rim);
                else if (step == length) p.set(x + dx, centerY + dy, z - step, Blocks.CYAN_STAINED_GLASS.defaultBlockState());
                else if (step == length - 1) p.set(x + dx, centerY + dy, z - step, Blocks.TINTED_GLASS.defaultBlockState());
            }
        }
    }

    private static void dock(Writer p) {
        // The side gallery reaches the winch housing and its suspended passenger cage.
        for (int x = HOIST_X - 1; x <= X - 24; x++) for (int dz = -3; dz <= 3; dz++) {
            int z = HOIST_Z + dz;
            p.set(x, Y, z, FLOOR);
            for (int h = 1; h <= 5; h++) {
                p.set(x, Y + h, z, h == 1 && Math.abs(dz) == 3 && x < X - 29 ? RAIL : AIR);
            }
        }
        p.limb(X - 21, Y + 12, Z + 4, HOIST_X, Y + 14, HOIST_Z, 3, BARK);
        for (int side : new int[]{-1, 1}) {
            int railZ = HOIST_Z + 4 * side;
            p.limb(X - 21, Y - 14, railZ, HOIST_X, Y - 2, railZ, 2, BARK);
            for (int x = HOIST_X; x < X - 30; x += 7) {
                for (int h = 1; h <= 6; h++) p.set(x, Y + h, railZ, TRIM);
                p.set(x, Y + 7, railZ, METAL);
                p.set(x, Y + 4, railZ - side, LIGHT.setValue(LanternBlock.HANGING, true));
                p.set(x, Y + 5, railZ - side, TRIM);
            }
        }
        for (int h = 9; h <= 23; h++) {
            int radius = h < 17 ? 9 : 24 - h + 2;
            boolean housing = h > 9 && h < 16;
            p.disc(HOIST_X, Y + h, HOIST_Z, radius, radius, housing ? BARK : METAL, true);
            if (housing) {
                p.disc(HOIST_X, Y + h, HOIST_Z, radius - 2, radius - 2, AIR, true);
                if (h >= 11 && h <= 14) {
                    for (int offset = -1; offset <= 1; offset++) {
                        p.set(HOIST_X + offset, Y + h, HOIST_Z - radius, WINDOW);
                        p.set(HOIST_X + offset, Y + h, HOIST_Z - radius + 1, WINDOW);
                    }
                }
            }
        }
        p.set(HOIST_X, Y + 24, HOIST_Z, LIGHT);
        for (int y = Y + 8; y >= Y - 7; y--) p.set(HOIST_X, y, HOIST_Z, CHAIN);

        int base = Y - 15;
        for (int dz = -3; dz <= 3; dz++) for (int dx = -3; dx <= 3; dx++) {
            boolean rim = Math.abs(dx) == 3 || Math.abs(dz) == 3;
            boolean door = (dz == 3 || dz == -3) && Math.abs(dx) <= 1;
            p.set(HOIST_X + dx, base, HOIST_Z + dz, FLOOR);
            p.set(HOIST_X + dx, base + 7, HOIST_Z + dz, METAL);
            if (rim) for (int y = base + 1; y < base + 7; y++) {
                if (!door || y >= base + 5) p.set(HOIST_X + dx, y, HOIST_Z + dz, CAGE);
            }
        }
        for (int y = ForestTerrain.CAMP_LANDING; y <= Y + 1; y++) p.set(HOIST_X + 4, y, HOIST_Z, TRIM);
        p.ladder(HOIST_X + 3, ForestTerrain.CAMP_LANDING + 1, Y + 1, HOIST_Z, Direction.WEST);
        for (int y = ForestTerrain.CAMP_LANDING + 1; y < Y; y++) {
            p.set(HOIST_X + 3, y, HOIST_Z + 1, AIR);
        }
        for (int x = X - 64; x <= HOIST_X + 4; x++) for (int z = HOIST_Z - 3; z <= HOIST_Z + 12; z++) {
            p.set(x, ForestTerrain.CAMP_LANDING, z, FLOOR);
            for (int h = 1; h <= 3; h++) p.set(x, ForestTerrain.CAMP_LANDING + h, z, AIR);
            if (z == HOIST_Z - 3 || z == HOIST_Z + 12) p.set(x, ForestTerrain.CAMP_LANDING + 1, z, RAIL);
        }
        // The ladder exits through the bridge, with a two-block-high path into the gallery.
        p.ladder(HOIST_X + 3, Y, Y + 1, HOIST_Z, Direction.WEST);
        p.set(HOIST_X + 2, Y + 1, HOIST_Z + 2, LIGHT);
        ForestExpedition.placeCampStations(p);
    }

    private SeekerCamp() {}
}
