package dev.fantasytruth.abyss;

import dev.fantasytruth.abyss.ForestGrowth.Writer;
import dev.fantasytruth.terrain.AtlasField;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;

/** Clip templates to each generating chunk and tie the approaches into the local terrain. */
final class ForestLandmarks {
    static final int NEST_X = -142, NEST_Z = -89;
    static final int CROSS_X = 40, CROSS_Z = 213, CROSS_Y = -396;
    static final int FALL_X = -29, FALL_Z = 8;
    static final int POND_X = 143, POND_Z = 226;
    static final int DIG_X = 226, DIG_Z = 103;
    private static final int NEST_CELL = 410, NEST_REACH = 19;
    private static final BlockState STONE = Blocks.TUFF.defaultBlockState();
    private static final BlockState SOIL = Blocks.ROOTED_DIRT.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private record Piece(int x, int y, int z, BlockState state) {}

    // Resolve the bundled block palette only after the block registry is available.
    private static final class Templates {
        static final List<Piece> NEST = read("weeper_nest");
        static final List<Piece> CROSSING = read("inverted_traverse");
        static final List<Piece> WATERFALL = read("heavens_waterfall");
    }

    static void place(Writer p, ForestTerrain terrain) {
        if (p.intersects(NEST_X, NEST_Z, NEST_REACH)) nest(p, terrain, NEST_X, NEST_Z);
        if (p.intersects(CROSS_X + 17, CROSS_Z + 8, 30)) crossing(p, terrain);
        if (p.intersects(FALL_X, FALL_Z, 19)) waterfall(p, terrain);
        if (p.intersects(POND_X, POND_Z, 33)) pond(p, terrain);
        if (p.intersects(DIG_X, DIG_Z, 22)) expeditionDig(p, terrain);
        for (int gz = Math.floorDiv(p.z - NEST_REACH, NEST_CELL);
             gz <= Math.floorDiv(p.z + 15 + NEST_REACH, NEST_CELL); gz++) {
            for (int gx = Math.floorDiv(p.x - NEST_REACH, NEST_CELL);
                 gx <= Math.floorDiv(p.x + 15 + NEST_REACH, NEST_CELL); gx++) {
                var rng = new SplittableRandom(AtlasField.hash(terrain.seed, gx, 493, gz));
                int x = gx * NEST_CELL + rng.nextInt(32, NEST_CELL - 32);
                int z = gz * NEST_CELL + rng.nextInt(32, NEST_CELL - 32);
                if (!p.intersects(x, z, NEST_REACH) || AbyssLayer.passage(x, z, 320)) continue;
                var column = terrain.column(x, z);
                if (column.shelfTop() >= 300 && column.shelfTop() <= 605) nest(p, terrain, x, z);
            }
        }
    }

    private static void nest(Writer p, ForestTerrain terrain, int x, int z) {
        int base = terrain.column(x, z).shelfTop() - 3;
        for (int bz = Math.max(p.z, z - 10); bz <= Math.min(p.z + 15, z + 12); bz++) {
            for (int bx = Math.max(p.x, x - 10); bx <= Math.min(p.x + 15, x + 10); bx++) {
                if (Math.hypot(bx - x, (bz - z) * 0.9) > 10) continue;
                var column = terrain.column(bx, bz);
                for (int y = Math.max(base - 22, column.shelfTop()); y <= base + 5; y++) p.set(bx, y, bz, STONE);
                for (int y = base + 11; y <= base + 20; y++) p.set(bx, y, bz, AIR);
            }
        }
        paste(p, Templates.NEST, x - 12, base, z - 13);
        // Clear the occupied air around the bowl, expose the approach and hide the anchor inside the nest.
        for (int bz = z + 3; bz <= z + 10; bz++) for (int bx = x - 1; bx <= x + 1; bx++) {
            p.set(bx, base + 10, bz, SOIL);
            for (int y = base + 11; y <= base + 14; y++) p.set(bx, y, bz, AIR);
        }
        p.spawner(x, base + 10, z, "stratacubes:corpse_weeper");
        p.set(x, base + 11, z, Blocks.MANGROVE_ROOTS.defaultBlockState());
        p.barrel(x + 5, base + 11, z + 2, "forest_nest");
    }

    private static void crossing(Writer p, ForestTerrain terrain) {
        paste(p, Templates.CROSSING, CROSS_X, CROSS_Y, CROSS_Z);
        for (int step = 5; step <= 31; step++) {
            int x = CROSS_X + step;
            int z = CROSS_Z + 8 + (int) Math.round(2 * Math.sin((step - 6) * 0.22));
            int y = CROSS_Y + 11 + Mth.clamp((step - 10) / 5, 0, 3);
            for (int side = -1; side <= 1; side++) {
                p.set(x, y, z + side, Blocks.DARK_OAK_WOOD.defaultBlockState());
                for (int h = 1; h <= 3; h++) p.set(x, y + h, z + side, AIR);
            }
        }
        for (int end : new int[]{5, 30}) {
            int x = CROSS_X + end, z = CROSS_Z + 8 + (int) Math.round(2 * Math.sin((end - 6) * 0.22));
            int y = CROSS_Y + (end == 5 ? 11 : 14);
            for (int bx = x - 2; bx <= x + 2; bx++) for (int bz = z - 2; bz <= z + 2; bz++) {
                var floor = terrain.column(bx, bz);
                for (int by = Math.max(floor.floor(), y - 10); by < y; by++) p.set(bx, by, bz, STONE);
                p.set(bx, y, bz, SOIL);
                for (int h = 1; h <= 3; h++) p.set(bx, y + h, bz, AIR);
            }
        }
        for (int x : new int[]{52, 57, 63}) {
            int z = CROSS_Z + 6 + (int) Math.round(2 * Math.sin((x - CROSS_X - 6) * 0.22));
            int ceiling = terrain.column(x, z).ledgeBottom();
            if (ceiling <= CROSS_Y + 21) continue;
            for (int y = CROSS_Y + 19; y <= ceiling; y++) p.grow(x, y, z, ForestGrowth.Palette.TRUNK);
        }
        crossingLadder(p, terrain, CROSS_X + 4, CROSS_Z + 7, CROSS_Y + 11, Direction.WEST);
        crossingLadder(p, terrain, CROSS_X + 32, CROSS_Z + 7, CROSS_Y + 14, Direction.EAST);
    }

    private static void crossingLadder(Writer p, ForestTerrain terrain, int x, int z, int top, Direction facing) {
        int bottom = terrain.column(x, z).floor() + 1;
        for (int y = bottom; y <= top; y++) {
            p.set(x - facing.getStepX(), y, z, Blocks.DARK_OAK_WOOD.defaultBlockState());
        }
        p.ladder(x, bottom, top, z, facing);
        for (int h = 1; h <= 3; h++) p.set(x, top + h, z, AIR);
    }

    private static void waterfall(Writer p, ForestTerrain terrain) {
        int x = FALL_X, y = terrain.exitY() + 2, z = FALL_Z;
        paste(p, Templates.WATERFALL, x - 15, y, z - 17);
        for (int bz = Math.max(p.z, z - 8); bz <= Math.min(p.z + 15, z + 8); bz++) {
            for (int bx = Math.max(p.x, x - 11); bx <= Math.min(p.x + 15, x + 11); bx++) {
                if (Math.hypot(bx - x, bz - z) > 10) continue;
                for (int by = terrain.column(bx, bz).floor() + 1; by < y + 4; by++) p.set(bx, by, bz, STONE);
            }
        }
        // Overlooking ledge joins the waterfall to the existing central descent.
        for (int bx = x + 9; bx <= -13; bx++) for (int bz = z - 2; bz <= z + 2; bz++) {
            int top = y + 4 - (bx - x - 9) / 3;
            p.set(bx, top, bz, ForestGrowth.Palette.MOSS);
            for (int by = terrain.column(bx, bz).floor() + 1; by < top; by++) p.set(bx, by, bz, STONE);
            for (int dy = 1; dy <= 3; dy++) p.set(bx, top + dy, bz, AIR);
        }
    }

    private static void pond(Writer p, ForestTerrain terrain) {
        int level = pondLevel(terrain);
        for (int z = Math.max(p.z, POND_Z - 30); z <= Math.min(p.z + 15, POND_Z + 30); z++) {
            for (int x = Math.max(p.x, POND_X - 30); x <= Math.min(p.x + 15, POND_X + 30); x++) {
                double r = Math.hypot(x - POND_X, 1.22 * (z - POND_Z)) + (AtlasField.hash(terrain.seed, x, 87, z) & 3) * 0.55;
                if (r > 29) continue;
                int floor = terrain.column(x, z).floor();
                int bed = r < 21 ? level - 3 - (int) (4 * (1 - r / 21))
                        : (int) Mth.lerp(ForestTerrain.smooth(21, 29, r), level + 1, floor);
                for (int y = Math.min(bed, floor); y <= bed; y++) p.set(x, y, z, r < 21 ? STONE : SOIL);
                for (int y = bed + 1; y <= Math.max(floor + 2, level); y++) {
                    p.set(x, y, z, r < 21 && y <= level ? Blocks.WATER.defaultBlockState() : AIR);
                }
            }
        }
        p.spawner(POND_X, level - 4, POND_Z, "stratacubes:demonfish");
        p.barrel(POND_X + 20, level + 2, POND_Z, "forest_relic_bed");
        p.set(POND_X + 20, level + 1, POND_Z, SOIL);
    }

    private static int pondLevel(ForestTerrain terrain) { return terrain.column(POND_X, POND_Z).floor() + 2; }

    private static void expeditionDig(Writer p, ForestTerrain terrain) {
        int y = terrain.column(DIG_X, DIG_Z).floor() - 25;
        BlockState masonry = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        for (int dz = -16; dz <= 18; dz++) for (int dx = -12; dx <= 12; dx++) {
            int x = DIG_X + dx, z = DIG_Z + dz;
            if (!p.intersects(x, z, 0)) continue;
            double chamber = Math.hypot(dx * 1.3, dz + 5);
            if (chamber < 12) {
                int height = 5 + (int) (9 * Math.sqrt(1 - chamber / 12));
                p.set(x, y, z, (dx + dz) % 5 == 0 ? masonry : STONE);
                for (int h = 1; h <= height; h++) p.set(x, y + h, z, AIR);
            }
            // The pentagonal mouth is visible from the excavation approach.
            if (dz < 6 || dz > 18) continue;
            int width = Math.abs(dx);
            if (width > 4) continue;
            int top = terrain.column(x, z).floor();
            int step = y + Math.min(Math.max(0, top - y), (dz - 6) * 2 / 3);
            p.set(x, step, z, STONE);
            for (int h = 1; h <= 3; h++) p.set(x, step + h, z, AIR);
        }
        int shaftX = DIG_X + 1, shaftZ = DIG_Z + 17;
        int shaftTop = terrain.column(shaftX, shaftZ).floor();
        int shaftBottom = Math.min(y + 7, shaftTop) + 1;
        for (int by = shaftBottom; by <= shaftTop + 1; by++) {
            p.set(shaftX + 1, by, shaftZ, masonry);
            p.set(shaftX, by, shaftZ + 1, AIR);
        }
        p.ladder(shaftX, shaftBottom, shaftTop + 1, shaftZ, Direction.WEST);
        // An excavation headframe and tool shed make the ladder visible from the forest floor.
        BlockState timber = Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState();
        BlockState deck = Blocks.SPRUCE_PLANKS.defaultBlockState();
        int platformZ = DIG_Z + 17;
        for (int dz = -3; dz <= 4; dz++) for (int dx = -6; dx <= 6; dx++) {
            int x = DIG_X + dx, z = platformZ + dz;
            if (!p.intersects(x, z, 0)) continue;
            if (dx == 1 && dz == 0) continue;
            int ground = terrain.column(x, z).floor();
            for (int by = Math.min(shaftTop - 2, Math.max(ground, shaftTop - 10)); by < shaftTop; by++) {
                p.set(x, by, z, STONE);
            }
            p.set(x, shaftTop, z, deck);
            for (int h = 1; h <= 4; h++) p.set(x, shaftTop + h, z, AIR);
            if (dz == 4 && Math.abs(dx) >= 4) p.set(x, shaftTop + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
        }
        for (int dx : new int[]{-4, 5}) for (int dz : new int[]{-2, 2}) {
            for (int h = 1; h <= 7; h++) p.set(DIG_X + dx, shaftTop + h, platformZ + dz, timber);
        }
        for (int h = 1; h <= 3; h++) p.set(shaftX + 1, shaftTop + h, shaftZ, timber);
        for (int dx = -5; dx <= 6; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int rise = (3 - Math.abs(dz)) / 2;
                p.set(DIG_X + dx, shaftTop + 7 + rise, platformZ + dz,
                        (Math.abs(dx) < 3 ? Blocks.DARK_OAK_PLANKS : Blocks.BROWN_WOOL).defaultBlockState());
            }
        }
        p.set(shaftX, shaftTop + 5, shaftZ, Blocks.CHAIN.defaultBlockState());
        p.set(shaftX, shaftTop + 6, shaftZ, Blocks.CHAIN.defaultBlockState());
        p.set(DIG_X + 5, shaftTop + 1, platformZ, Blocks.STONECUTTER.defaultBlockState());
        p.set(DIG_X - 4, shaftTop + 1, platformZ + 1, Blocks.LANTERN.defaultBlockState());
        p.barrel(DIG_X - 5, shaftTop + 1, platformZ - 1, "edge_supplies");
        for (int dx = -5; dx <= 5; dx++) {
            int lintel = 8 - Math.abs(dx) / 2;
            for (int dy = 0; dy <= 8; dy++) {
                if (Math.abs(dx) == 5 || dy == 0 || dy == lintel) {
                    p.set(DIG_X + dx, y + dy, DIG_Z + 6, masonry);
                } else if (dy < lintel) p.set(DIG_X + dx, y + dy, DIG_Z + 6, AIR);
            }
        }
        for (int dy = 9; dy <= 15; dy++) p.set(DIG_X, y + dy, DIG_Z - 5, Blocks.CHAIN.defaultBlockState());
        p.disc(DIG_X, y + 8, DIG_Z - 5, 2, 2, Blocks.WAXED_WEATHERED_CUT_COPPER.defaultBlockState(), true);
        p.set(DIG_X, y + 7, DIG_Z - 5, Blocks.SOUL_LANTERN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        p.barrel(DIG_X + 5, y + 1, DIG_Z - 5, "forest_joint_dig");
        p.barrel(DIG_X - 5, y + 1, DIG_Z - 5, "forest_relic_bed");
    }

    private static void paste(Writer p, List<Piece> pieces, int x, int y, int z) {
        for (var piece : pieces) p.set(x + piece.x, y + piece.y, z + piece.z, piece.state);
    }

    private static List<Piece> read(String name) {
        String path = "/data/stratacubes/structures/forest/" + name + ".nbt";
        try (var stream = ForestLandmarks.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing structure " + path);
            var template = NbtIo.readCompressed(stream);
            var palette = new ArrayList<BlockState>();
            for (var value : template.getList("palette", Tag.TAG_COMPOUND)) {
                palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), (net.minecraft.nbt.CompoundTag) value));
            }
            var pieces = new ArrayList<Piece>();
            for (var value : template.getList("blocks", Tag.TAG_COMPOUND)) {
                var block = (net.minecraft.nbt.CompoundTag) value;
                var pos = block.getList("pos", Tag.TAG_INT);
                pieces.add(new Piece(pos.getInt(0), pos.getInt(1), pos.getInt(2), palette.get(block.getInt("state"))));
            }
            return List.copyOf(pieces);
        } catch (IOException error) {
            throw new IllegalStateException("Could not read " + path, error);
        }
    }

    private ForestLandmarks() {}
}
