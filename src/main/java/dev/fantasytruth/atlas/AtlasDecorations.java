package dev.fantasytruth.atlas;

import dev.fantasytruth.terrain.AtlasField;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

final class AtlasDecorations {
    private enum Wood {
        OAK(Blocks.OAK_LOG, Blocks.OAK_LEAVES), SPRUCE(Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES),
        JUNGLE(Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES);

        final Block log;
        final Block leaves;
        Wood(Block log, Block leaves) { this.log = log; this.leaves = leaves; }
    }

    static void populate(AtlasField field, ChunkAccess chunk, long originY) {
        if (originY + chunk.getMaxBuildHeight() < field.profile().seaLevel() - 1L
                || originY + chunk.getMinBuildHeight() > 65000) return;
        Writer writer = new Writer(chunk, originY);
        flora(field, writer);
        sites(writer, 11, 12, field.seed() ^ 0x5aa8f7, (x, z, random) -> tree(field, writer, x, z, random));
    }

    private static void flora(AtlasField field, Writer writer) {
        for (int z = writer.z0; z < writer.z0 + 16; z++) {
            for (int x = writer.x0; x < writer.x0 + 16; x++) {
                double roll = AtlasField.unit(AtlasField.hash(field.seed() ^ 74, x, 0, z));
                if (roll > 0.15) continue;
                AtlasField.Column column = field.column(x, z);
                if (column.height() < field.profile().seaLevel() || column.slope() > 0.66) continue;
                Block plant = switch (column.biome()) {
                    case FLOODPLAIN -> Blocks.MOSS_CARPET;
                    case RAINSHADOW_SCRUB -> Blocks.DEAD_BUSH;
                    case MISTWOOD, BOREAL_WOODLAND, MONSOON_FOREST -> Blocks.FERN;
                    case STEPPE -> roll < 0.02 ? Blocks.OXEYE_DAISY : Blocks.GRASS;
                    default -> null;
                };
                if (plant != null) writer.put(x, column.height() + 1, z, plant.defaultBlockState(), true);
            }
        }
    }

    @FunctionalInterface
    private interface Site { void generate(int x, int z, SplittableRandom random); }

    private static void sites(Writer writer, int spacing, int reach, long seed, Site site) {
        for (int gz = Math.floorDiv(writer.z0 - reach, spacing); gz <= Math.floorDiv(writer.z0 + 15 + reach, spacing); gz++) {
            for (int gx = Math.floorDiv(writer.x0 - reach, spacing); gx <= Math.floorDiv(writer.x0 + 15 + reach, spacing); gx++) {
                SplittableRandom random = new SplittableRandom(AtlasField.hash(seed, gx, 0, gz));
                int x = gx * spacing + random.nextInt(spacing);
                int z = gz * spacing + random.nextInt(spacing);
                if (Math.max(writer.x0 - x, x - writer.x0 - 15) <= reach
                        && Math.max(writer.z0 - z, z - writer.z0 - 15) <= reach) site.generate(x, z, random);
            }
        }
    }

    private static void tree(AtlasField field, Writer writer, int x, int z, SplittableRandom random) {
        AtlasField.Column column = field.column(x, z);
        if (column.height() <= field.profile().seaLevel() || column.slope() > 0.63) return;
        double density = switch (column.biome()) {
            case MISTWOOD, MONSOON_FOREST -> 0.95;
            case BOREAL_WOODLAND -> 0.73;
            case FLOODPLAIN -> 0.33;
            case STEPPE -> 0.11;
            case ALPINE_HEATH -> 0.07;
            default -> 0;
        };
        if (random.nextDouble() >= density * field.profile().treeDensity()) return;
        Wood wood = switch (column.biome()) {
            case BOREAL_WOODLAND, ALPINE_HEATH -> Wood.SPRUCE;
            case MONSOON_FOREST -> Wood.JUNGLE;
            default -> Wood.OAK;
        };
        int base = column.height() + 1;
        int height = switch (wood) {
            case OAK -> random.nextInt(7, 13);
            case SPRUCE -> random.nextInt(10, 19);
            case JUNGLE -> random.nextInt(13, 24);
        };
        for (int y = 0; y < height; y++) writer.put(x, base + y, z, wood.log.defaultBlockState(), false);
        int branches = wood == Wood.SPRUCE ? 11 : 6;
        double azimuth = random.nextDouble(2 * Math.PI);
        for (int i = 0; i < branches; i++) {
            double t = i / (double) branches;
            double angle = azimuth + i * 2.3999632297 + random.nextDouble(-0.30, 0.30);
            int origin = base + (int) (height * (0.42 + 0.52 * t));
            double spread = wood == Wood.SPRUCE ? 4.9 * (1 - t) + 1 : 2.5 + 3 * Math.sin(t * Math.PI);
            int endX = x + (int) Math.round(spread * Math.cos(angle));
            int endZ = z + (int) Math.round(spread * Math.sin(angle));
            int endY = origin + (wood == Wood.SPRUCE ? 0 : random.nextInt(1, 4));
            branch(writer, wood, x, origin, z, endX, endY, endZ);
            crown(writer, wood, endX, endY, endZ, wood == Wood.SPRUCE ? 2 : 3, random);
        }
        crown(writer, wood, x, base + height - 1, z, wood == Wood.SPRUCE ? 2 : 3, random);
    }

    private static void branch(Writer writer, Wood wood, int x, int y, int z, int x1, int y1, int z1) {
        int steps = Math.max(Math.max(Math.abs(x1 - x), Math.abs(z1 - z)), Math.abs(y1 - y));
        if (steps == 0) return;
        BlockState vertical = wood.log.defaultBlockState();
        int fromX = x, fromY = y, fromZ = z;
        for (int i = 1; i <= steps; i++) {
            int a = x + Math.round((x1 - x) * i / (float) steps);
            int b = y + Math.round((y1 - y) * i / (float) steps);
            int c = z + Math.round((z1 - z) * i / (float) steps);
            if (a != fromX) writer.put(a, fromY, fromZ, vertical.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), false);
            if (c != fromZ) writer.put(a, fromY, c, vertical.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z), false);
            if (b != fromY) writer.put(a, b, c, vertical, false);
            fromX = a;
            fromY = b;
            fromZ = c;
        }
    }

    private static void crown(Writer writer, Wood wood, int x, int y, int z, int radius, SplittableRandom random) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -1; dy <= 2; dy++) {
                    double boundary = radius * radius + random.nextDouble(-2.5, 1.5);
                    int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (dx * dx + dz * dz + dy * dy * 2 < boundary && distance < 7) {
                        BlockState state = wood.leaves.defaultBlockState().setValue(LeavesBlock.DISTANCE, Math.max(1, distance));
                        writer.put(x + dx, y + dy, z + dz, state, true);
                    }
                }
            }
        }
    }

    static void house(AtlasField field, Writer writer, int x, int z, long floor, int halfX, int halfZ,
                      boolean rotate, AtlasContent.Landmark rule) {
        BlockState stone = rule.wall().defaultBlockState();
        BlockState plank = rule.floor().defaultBlockState();
        BlockState beam = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        for (int a = -halfX - 1; a <= halfX + 1; a++) {
            for (int b = -halfZ - 1; b <= halfZ + 1; b++) {
                int xx = x + (rotate ? b : a);
                int zz = z + (rotate ? a : b);
                boolean inside = Math.abs(a) <= halfX && Math.abs(b) <= halfZ;
                boolean wall = Math.abs(a) == halfX || Math.abs(b) == halfZ;
                long roof = floor + 4 + halfX - Math.min(halfX, Math.abs(a));
                if (inside) {
                    int ground = field.column(xx, zz).height();
                    for (long yy = Math.max(floor - 16, ground + 1L); yy < floor; yy++) writer.put(xx, yy, zz, stone, false);
                    writer.put(xx, floor, zz, plank, false);
                    for (long yy = floor + 1; yy < roof; yy++) {
                        boolean door = a == 0 && b == -halfZ && yy < floor + 3;
                        boolean window = wall && a != 0 && a % 2 == 0 && yy == floor + 2;
                        BlockState material = Blocks.AIR.defaultBlockState();
                        if (wall && !door) {
                            material = window ? Blocks.GLASS_PANE.defaultBlockState()
                                    : ((Math.abs(a) == halfX && Math.abs(b) == halfZ) || yy == floor + 3 ? beam : stone);
                        }
                        writer.put(xx, yy, zz, material, false);
                    }
                }
                Direction face = a >= 0 ? Direction.WEST : Direction.EAST;
                if (rotate) face = a >= 0 ? Direction.NORTH : Direction.SOUTH;
                BlockState roofTile = a == 0 ? plank : Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, face);
                writer.put(xx, roof, zz, roofTile, false);
            }
        }
        int entryX = x + (rotate ? -halfZ - 1 : 0);
        int entryZ = z + (rotate ? 0 : -halfZ - 1);
        int entryGround = field.column(entryX, entryZ).height();
        for (long yy = Math.max(floor - 16, entryGround + 1L); yy <= floor; yy++) writer.put(entryX, yy, entryZ, stone, false);
    }

    static final class Writer {
        final ChunkAccess chunk;
        final long originY;
        final int x0;
        final int z0;
        final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();

        Writer(ChunkAccess chunk, long originY) {
            this.chunk = chunk;
            this.originY = originY;
            x0 = chunk.getPos().getMinBlockX();
            z0 = chunk.getPos().getMinBlockZ();
        }

        void put(int x, long worldY, int z, BlockState block, boolean airOnly) {
            if (x < x0 || x >= x0 + 16 || z < z0 || z >= z0 + 16) return;
            if (worldY < originY + chunk.getMinBuildHeight() || worldY >= originY + chunk.getMaxBuildHeight()) return;
            position.set(x, (int) (worldY - originY), z);
            BlockState old = chunk.getBlockState(position);
            if (airOnly && !old.isAir()) return;
            if (old.hasBlockEntity() && old.getBlock() != block.getBlock()) chunk.removeBlockEntity(position);
            chunk.setBlockState(position, block, false);
        }
    }

    private AtlasDecorations() {}
}
