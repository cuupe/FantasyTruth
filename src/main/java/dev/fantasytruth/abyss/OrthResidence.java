package dev.fantasytruth.abyss;

import dev.fantasytruth.abyss.OrthCity.Painter;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Furnishes standard Orth lots in the same local coordinates as their frontage. */
final class OrthResidence {
    private static final BlockState BEAM = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()
            .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
    private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    private static final BlockState SPRUCE_SEAT = Blocks.SPRUCE_STAIRS.defaultBlockState();
    private static final BlockState SHELF = Blocks.SPRUCE_SLAB.defaultBlockState()
            .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);

    private static final Block[] BEDS = { Blocks.BLUE_BED, Blocks.RED_BED, Blocks.YELLOW_BED, Blocks.GREEN_BED };
    private static final Block[] RUGS = { Blocks.BLUE_CARPET, Blocks.RED_CARPET, Blocks.BROWN_CARPET, Blocks.GREEN_CARPET };
    private static final Block[] BORDERS = { Blocks.LIGHT_BLUE_CARPET, Blocks.ORANGE_CARPET, Blocks.WHITE_CARPET, Blocks.LIME_CARPET };
    private static final Block[] HANGINGS = { Blocks.BLUE_WALL_BANNER, Blocks.RED_WALL_BANNER,
            Blocks.YELLOW_WALL_BANNER, Blocks.GREEN_WALL_BANNER };
    private static final Block[] BENCHES = { Blocks.CARTOGRAPHY_TABLE, Blocks.LOOM, Blocks.STONECUTTER, Blocks.SMITHING_TABLE };

    static void furnish(Painter p, int x, int y, int z, int width, int depth, int floors, int style) {
        p.fill(x - width + 1, y + 5 * floors, z - depth + 1,
                x + width - 1, y + 5 * floors, z + depth - 1, Blocks.SPRUCE_PLANKS);
        for (int floor = 0; floor < floors; floor++) {
            int base = y + 5 * floor;
            for (int dz : new int[]{-depth + 1, 0, depth - 1}) {
                for (int dx = -width + 1; dx < width; dx++) {
                    p.column(x + dx, z + dz, base + 4, base + 4, BEAM);
                }
            }
            p.column(x, z, base + 3, base + 3, LANTERN);
            p.column(x + 2, z - depth + 1, base + 3, base + 3, LANTERN);
            p.column(x + 2, z + depth - 1, base + 3, base + 3, LANTERN);
            p.fill(x, base + 1, z - depth + 1, x, base + 1, z, RUGS[style]);

            if (width >= 5 && depth >= 5) {
                furnishedRug(p, x, base, z, width >= 7 ? 2 : 1, depth >= 6 ? 2 : 1, style);
            }
            if (depth >= 5) {
                furnishWestWall(p, x - width + 1, base, z, depth, floor, floors, style);
            }
            if (depth >= 5) {
                p.furnish(x + width - 1, base + 3, z - 2, HANGINGS[style].defaultBlockState()
                        .setValue(WallBannerBlock.FACING, Direction.WEST));
            }
            if (floor == 0) {
                kitchen(p, x, base, z, width, depth, floors == 1, style);
            } else if (floor + 1 == floors) {
                bedroom(p, x, base, z, width, depth, style);
            } else {
                workshop(p, x, base, z, width, depth, style);
            }
        }
        int west = x - width + 1;
        if (floors > 1) {
            p.furnish(west, y + 1, z + 1, Blocks.BARREL);
            p.furnish(west, y + 1, z + 2, Blocks.BARREL);
            p.furnish(west, y + 2, z + 2, Blocks.BARREL);
            p.column(west + 1, z + 2, y + 1, y + 1, SHELF);
            p.set(west + 1, y + 2, z + 2, Blocks.FLOWER_POT);
        }
        if (floors > 2) {
            chest(p, west + 1, y + 6, z - 2, Direction.NORTH);
            p.furnish(west + 1, y + 6, z - 1, Blocks.BARREL);
        }
        for (int floor = 0; floor + 1 < floors; floor++) {
            stairs(p, west, y + 5 * floor, z, floor, floor + 2 < floors);
        }
    }

    private static void furnishedRug(Painter p, int x, int y, int z, int width, int depth, int style) {
        p.fill(x - width, y + 1, z - depth, x + width, y + 1, z + depth, BORDERS[style]);
        p.fill(x - width + 1, y + 1, z - depth + 1, x + width - 1, y + 1, z + depth - 1, RUGS[style]);
    }

    private static void furnishWestWall(Painter p, int x, int y, int z, int depth, int floor, int floors, int style) {
        int front = z - depth + 1, rear = z + depth - 1;
        if (floor == 0) {
            p.furnish(x, y + 1, front, Blocks.BARREL);
            p.set(x, y + 2, front, Blocks.FLOWER_POT);
            p.column(x + 1, front, y + 1, y + 1, SPRUCE_SEAT.setValue(StairBlock.FACING, Direction.NORTH));
            p.furnish(x, y + 1, rear, Blocks.BARREL);
            p.set(x + 1, y + 1, rear, Blocks.SPRUCE_PLANKS);
            p.set(x + 1, y + 2, rear, Blocks.POTTED_DANDELION);
            p.column(x, rear, y + 2, y + 3, Blocks.BOOKSHELF);
            p.column(x + 2, rear, y + 1, y + 1, SHELF);
            p.set(x + 2, y + 2, rear, Blocks.CANDLE);
        } else if (floor + 1 == floors) {
            p.column(x, rear, y + 1, y + 3, Blocks.BOOKSHELF);
            p.column(x + 1, rear, y + 1, y + 2, Blocks.BOOKSHELF);
            p.set(x + 2, y + 1, rear, Blocks.SPRUCE_PLANKS);
            p.set(x + 2, y + 2, rear, Blocks.POTTED_ALLIUM);
            p.fill(x, y + 1, rear - 1, x + 2, y + 1, rear - 1, RUGS[style]);
            p.column(x + 1, front, y + 1, y + 1, SPRUCE_SEAT.setValue(StairBlock.FACING, Direction.NORTH));
            p.furnish(x, y + 1, front, Blocks.BARREL);
        } else {
            p.furnish(x, y + 1, front, Blocks.BARREL);
            p.furnish(x, y + 2, front, Blocks.BARREL);
            chest(p, x + 1, y + 1, front, Direction.SOUTH);
            p.set(x, y + 1, rear, Blocks.SMITHING_TABLE);
            p.furnish(x + 1, y + 1, rear, Blocks.BARREL);
            p.furnish(x + 1, y + 2, rear, Blocks.BARREL);
            p.column(x + 2, rear, y + 1, y + 1, SHELF);
            p.set(x + 2, y + 2, rear, Blocks.CANDLE);
        }
    }

    private static void kitchen(Painter p, int x, int y, int z, int width, int depth, boolean singleFloor, int style) {
        int rear = z + depth - 1, front = z - depth + 1, east = x + width - 1;
        Block appliance = (style & 1) == 0 ? Blocks.SMOKER : Blocks.FURNACE;
        p.furnish(east, y + 1, rear, appliance.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        p.furnish(east - 1, y + 1, rear, Blocks.BARREL);
        p.set(east - 2, y + 1, rear, Blocks.CRAFTING_TABLE);
        p.column(east, rear, y + 2, y + 3, Blocks.BRICKS);
        p.furnish(east - 1, y + 2, rear, Blocks.BARREL);
        p.column(east - 2, rear, y + 3, y + 3, SHELF);
        p.column(east, rear - 2, y + 1, y + 1, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(BlockStateProperties.LEVEL_CAULDRON, 2));
        diningTable(p, x + 2, y, z, width, depth);
        if (width >= 6) {
            sittingArea(p, x - 3, y, z);
            if (depth >= 6) {
                p.column(x - 2, z + 2, y + 1, y + 1, SPRUCE_SEAT.setValue(StairBlock.FACING, Direction.SOUTH));
                p.column(x - width + 1, front + 1, y + 1, y + 2, Blocks.BOOKSHELF);
                p.column(x + 2, z + 2, y + 1, y + 1, SHELF);
                p.set(x + 2, y + 2, z + 2, Blocks.POTTED_DANDELION);
                p.furnish(x + 3, y + 1, z + 2, Blocks.BARREL);
            }
        }

        if (singleFloor) {
            bed(p, east, y + 1, front + 1, Direction.NORTH, BEDS[style]);
            chest(p, east - 1, y + 1, front, Direction.SOUTH);
            p.set(east - 2, y + 1, front, Blocks.BOOKSHELF);
        } else {
            p.furnish(east, y + 1, front, Blocks.BARREL);
            p.set(east - 1, y + 1, front, BENCHES[style]);
            p.set(east, y + 2, front, Blocks.POTTED_FERN);
        }

        if (width >= 5) {
            p.column(east - 3, rear, y + 1, y + 1, SHELF);
            p.set(east - 3, y + 2, rear, Blocks.POTTED_DANDELION);
            for (int offset = 2; offset <= depth - 4; offset++) {
                int bz = z + offset;
                if (offset == 2) p.furnish(east, y + 1, bz, Blocks.BARREL);
                else p.column(east, bz, y + 1, y + 1, SHELF);
            }
            if (depth >= 6) {
                p.column(east, z + 2, y + 3, y + 3, SHELF);
                p.set(east, y + 2, z + 2, Blocks.CANDLE);
            }
        }
        if (width >= 6) {
            p.column(east, z - 1, y + 1, y + 1, SHELF);
            p.set(east, y + 2, z - 1, Blocks.FLOWER_POT);
        }
    }

    private static void bedroom(Painter p, int x, int y, int z, int width, int depth, int style) {
        int rear = z + depth - 1, front = z - depth + 1, east = x + width - 1;
        bed(p, east, y + 1, rear - 1, Direction.SOUTH, BEDS[style]);
        if (width >= 6) {
            bed(p, east - 1, y + 1, rear - 1, Direction.SOUTH, BEDS[style]);
            chest(p, east - 2, y + 1, rear, Direction.NORTH);
        } else {
            chest(p, east - 1, y + 1, rear, Direction.NORTH);
        }

        p.column(east, front, y + 1, y + 2, Blocks.BOOKSHELF);
        p.furnish(east - 1, y + 1, front, Blocks.BARREL);
        p.furnish(east - 2, y + 1, front, Blocks.LECTERN.defaultBlockState()
                .setValue(LecternBlock.FACING, Direction.SOUTH));
        p.column(east - 2, front + 2, y + 1, y + 1, SPRUCE_SEAT.setValue(StairBlock.FACING, Direction.SOUTH));
        p.fill(east - 1, y + 1, rear - 2, east, y + 1, rear - 2, RUGS[style]);

        int dresser = east - (width >= 6 ? 3 : 2);
        p.furnish(dresser, y + 1, rear, Blocks.BARREL);
        p.set(dresser, y + 2, rear, Blocks.LANTERN);
        if (depth >= 5) {
            // The trunk and shelf sit on the window side of the reading desk.
            p.column(east, front + 1, y + 1, y + 1, SHELF);
            p.set(east, y + 2, front + 1, Blocks.POTTED_BIRCH_SAPLING);
            p.column(east, front + 2, y + 1, y + 2, Blocks.BOOKSHELF);
        }
        if (depth >= 6 && width >= 5) {
            p.fill(x + 3, y + 1, z + 1, east, y + 1, z + 1, Blocks.DARK_OAK_PLANKS);
            p.fill(x + 3, y + 2, z + 1, east, y + 2, z + 1, Blocks.DARK_OAK_FENCE);
            p.fill(x + 3, y + 3, z + 1, east, y + 3, z + 1, Blocks.DARK_OAK_SLAB);
        }
        if (width >= 6) {
            sittingArea(p, x - (width == 6 ? 2 : 3), y, z);
            if (depth >= 6) {
                p.furnish(east - 2, y + 1, z - 1, Blocks.BARREL);
                p.column(east - 1, z - 1, y + 1, y + 1, SHELF);
                p.set(east - 1, y + 2, z - 1, Blocks.FLOWER_POT);
                p.column(east, z - 1, y + 1, y + 2, Blocks.BOOKSHELF);
                p.column(x - width + 1, rear - 1, y + 1, y + 1, SHELF);
                p.set(x - width + 1, y + 2, rear - 1, Blocks.CANDLE);
            }
        }
    }

    private static void workshop(Painter p, int x, int y, int z, int width, int depth, int style) {
        int east = x + width - 1, rear = z + depth - 1, front = z - depth + 1;
        p.set(east, y + 1, rear, BENCHES[style]);
        p.set(east - 1, y + 1, rear, Blocks.CRAFTING_TABLE);
        p.furnish(east - 2, y + 1, rear, Blocks.BARREL);
        p.column(east, front, y + 1, y + 2, Blocks.BOOKSHELF);
        p.column(east - 1, front, y + 1, y + 1, Blocks.GRINDSTONE.defaultBlockState()
                .setValue(GrindstoneBlock.FACE, AttachFace.FLOOR)
                .setValue(GrindstoneBlock.FACING, Direction.SOUTH));
        chest(p, east - 2, y + 1, front, Direction.SOUTH);

        if (width >= 5) {
            for (int dz = front - z + 1; dz < rear - z - 2; dz++) {
                p.column(east, z + dz, y + 1, y + 1, SHELF);
            }
            p.furnish(east, y + 1, rear - 2, Blocks.BLAST_FURNACE.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
            if (depth >= 5) p.set(east, y + 2, z + 1, Blocks.CANDLE);
            if (depth >= 6) p.column(east, z + 2, y + 3, y + 3, SHELF);
        }
        if (width >= 6) {
            p.set(x + 2, y + 1, z - 1, Blocks.CRAFTING_TABLE);
            p.set(x + 2, y + 1, z, Blocks.SPRUCE_PLANKS);
            p.set(x + 2, y + 2, z, Blocks.FLOWER_POT);
            p.furnish(x + 2, y + 1, z + 1, Blocks.BARREL);
            if (depth >= 5) {
                p.column(x + 3, z - 1, y + 1, y + 1, SHELF);
                p.set(x + 3, y + 2, z - 1, Blocks.CANDLE);
                p.column(x + 3, z, y + 1, y + 1, SHELF);
                p.column(x + 3, z + 1, y + 1, y + 1, SHELF);
            }
            int seat = x - (width == 6 ? 2 : 3);
            p.column(seat, z, y + 1, y + 1, SHELF);
            p.set(seat, y + 2, z, Blocks.CANDLE);
            p.column(seat, z + 1, y + 1, y + 1, SPRUCE_SEAT.setValue(StairBlock.FACING, Direction.SOUTH));
        }
    }

    private static void diningTable(Painter p, int x, int y, int z, int width, int depth) {
        int length = depth >= 6 ? 3 : depth == 5 ? 2 : 1;
        int start = z - (length == 3 ? 1 : 0);
        int columns = width >= 7 && depth >= 5 ? 2 : 1;
        for (int i = 0; i < length; i++) {
            int at = start + i;
            for (int dx = 0; dx < columns; dx++) {
                if (dx == 1 && i == length - 1) {
                    p.column(x + dx, at, y + 1, y + 1, SHELF);
                    p.set(x + dx, y + 2, at, Blocks.CAKE);
                } else {
                    p.set(x + dx, y + 1, at, Blocks.SPRUCE_FENCE);
                    p.set(x + dx, y + 2, at, Blocks.SPRUCE_PRESSURE_PLATE);
                }
            }
            p.column(x - 1, at, y + 1, y + 1, SPRUCE_SEAT.setValue(StairBlock.FACING, Direction.WEST));
            p.column(x + columns, at, y + 1, y + 1, SPRUCE_SEAT.setValue(StairBlock.FACING, Direction.EAST));
        }
    }

    private static void sittingArea(Painter p, int x, int y, int z) {
        for (int dz = -1; dz <= 1; dz++) {
            p.column(x, z + dz, y + 1, y + 1, SPRUCE_SEAT.setValue(StairBlock.FACING, Direction.WEST));
        }
        p.column(x + 1, z, y + 1, y + 1, SHELF);
        p.set(x + 1, y + 2, z, Blocks.CANDLE);
    }

    private static void bed(Painter p, int x, int y, int z, Direction facing, Block block) {
        BlockState foot = block.defaultBlockState().setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.FOOT);
        p.furnish(x, y, z, foot);
        p.furnish(x + facing.getStepX(), y, z + facing.getStepZ(), foot.setValue(BedBlock.PART, BedPart.HEAD));
    }

    private static void chest(Painter p, int x, int y, int z, Direction facing) {
        p.furnish(x, y, z, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
    }

    private static void stairs(Painter p, int west, int y, int z, int floor, boolean continues) {
        boolean southbound = (floor & 1) == 0;
        int x = southbound ? west : west + 1;
        Direction facing = southbound ? Direction.SOUTH : Direction.NORTH;
        BlockState stair = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
        for (int step = 1; step <= 5; step++) {
            int bz = z + (step - 3) * facing.getStepZ();
            p.column(x, bz, y + step, y + step, stair);
            p.column(x, bz, y + step + 1, y + step + 3, Blocks.AIR);
        }
        // Leave the end landings open so players can turn into the next flight.
        int guard = southbound && !continues ? west + 1 : west + 2;
        for (int dz = -1; dz <= 1; dz++) {
            p.set(guard, y + 6, z + dz, Blocks.SPRUCE_FENCE);
            if (!southbound) p.set(west, y + 6, z + dz, Blocks.SPRUCE_FENCE);
        }
    }

    private OrthResidence() {}
}
