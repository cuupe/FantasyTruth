package dev.fantasytruth.abyss;

import dev.fantasytruth.terrain.AtlasField;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

final class AbyssFormations {
    private static final BlockState LEAVES = Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    private static final BlockState BLOSSOMS = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    private static final BlockState LOG = Blocks.JUNGLE_LOG.defaultBlockState();
    private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
    private static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
    private static final BlockState TUFF = Blocks.TUFF.defaultBlockState();
    private static final BlockState BRICKS = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState GLOW = Blocks.SHROOMLIGHT.defaultBlockState();

    static void place(ChunkAccess chunk, AbyssShape shape) {
        if (shape.layer != AbyssLayer.EDGE && shape.layer != AbyssLayer.FOREST
                && shape.layer != AbyssLayer.GOBLETS && shape.layer != AbyssLayer.CAPITAL) return;
        boolean grove = shape.layer == AbyssLayer.EDGE || shape.layer == AbyssLayer.FOREST;
        int spacing = grove ? 19 : 37;
        int reach = grove ? 8 : 17;
        Writer writer = new Writer(chunk);
        for (int gz = Math.floorDiv(writer.z - reach, spacing); gz <= Math.floorDiv(writer.z + 15 + reach, spacing); gz++) {
            for (int gx = Math.floorDiv(writer.x - reach, spacing); gx <= Math.floorDiv(writer.x + 15 + reach, spacing); gx++) {
                var random = new SplittableRandom(AtlasField.hash(shape.seed, gx, 913, gz));
                int x = gx * spacing + random.nextInt(spacing);
                int z = gz * spacing + random.nextInt(spacing);
                var column = shape.column(x, z);
                if (column.radius() < 44 || column.radius() > shape.layer.width + 55) continue;
                int bands = grove ? 7 : 5;
                for (int band = 1; band < bands; band++) {
                    int y = shape.layer.entrance - band * (shape.layer.entrance - shape.layer.exit) / bands;
                    y += random.nextInt(-26, 27);
                    boolean inverted = shape.layer == AbyssLayer.FOREST && random.nextDouble() < 0.68;
                    int base = shape.anchor(column, y, inverted);
                    if (base == Integer.MIN_VALUE) continue;
                    if (grove) tree(writer, x, base, z, inverted ? -1 : 1, random);
                    else if (shape.layer == AbyssLayer.GOBLETS) goblet(writer, x, base, z, random);
                    else ruin(writer, x, base, z, random);
                }
            }
        }
    }

    private static void tree(Writer writer, int x, int y, int z, int direction, SplittableRandom random) {
        int height = random.nextInt(11, 24);
        int radius = random.nextInt(4, 8);
        for (int h = 0; h <= height; h++) writer.put(x, y + h * direction, z, LOG);
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int h = -3; h <= 4; h++) {
                    if (dx * dx + dz * dz + 2 * h * h > radius * radius + random.nextInt(-3, 5)) continue;
                    BlockState foliage = random.nextInt(32) == 0 ? BLOSSOMS : LEAVES;
                    writer.put(x + dx, y + (height + h) * direction, z + dz, foliage);
                }
                if (dx * dx + dz * dz < radius * radius && random.nextInt(7) == 0) {
                    int hanging = random.nextInt(1, 5);
                    for (int i = 0; i < hanging; i++) {
                        writer.put(x + dx, y + height * direction - i, z + dz,
                                i + 1 == hanging ? GLOW : Blocks.MANGROVE_ROOTS.defaultBlockState());
                    }
                }
            }
        }
        if (direction > 0) {
            for (int i = 0; i < 10; i++) {
                int dx = random.nextInt(-5, 6);
                int dz = random.nextInt(-5, 6);
                BlockState plant = random.nextBoolean() ? Blocks.FERN.defaultBlockState() : Blocks.MOSS_CARPET.defaultBlockState();
                if (writer.at(x + dx, y - 1, z + dz).blocksMotion()) {
                    writer.put(x + dx, y, z + dz, plant);
                }
            }
        }
    }

    private static void goblet(Writer writer, int x, int y, int z, SplittableRandom random) {
        int radius = random.nextInt(10, 17);
        int stalk = random.nextInt(19, 43);
        for (int h = 0; h < stalk + 11; h++) {
            double outer = h < stalk ? 2.5 + 3.5 * Math.exp(-h / 5.0) : 3.0 + (radius - 3) * (h - stalk) / 10.0;
            double inner = h < stalk + 3 ? 0 : Math.max(0, outer - 2.3);
            int bound = (int) Math.ceil(outer);
            for (int dz = -bound; dz <= bound; dz++) {
                for (int dx = -bound; dx <= bound; dx++) {
                    double r = Math.hypot(dx, dz);
                    if (r > outer || r < inner) continue;
                    BlockState state = h >= stalk + 8 ? MOSS : h >= stalk ? CALCITE : TUFF;
                    writer.put(x + dx, y + h, z + dz, state);
                }
            }
        }
        writer.put(x, y + stalk + 3, z, GLOW);
    }

    private static void ruin(Writer writer, int x, int y, int z, SplittableRandom random) {
        int height = random.nextInt(16, 45);
        int base = random.nextInt(4, 9);
        for (int h = 0; h < height; h++) {
            int radius = Math.max(2, base - h / 10);
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius && h % 10 > 1) continue;
                    if (h % 10 >= 3 && h % 10 <= 6 && (dx == 0 || dz == 0)) continue;
                    writer.put(x + dx, y + h, z + dz, h % 10 == 0 ? CALCITE : BRICKS);
                }
            }
        }
    }

    private static final class Writer {
        private final ChunkAccess chunk;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private final int x;
        private final int z;

        Writer(ChunkAccess chunk) {
            this.chunk = chunk;
            this.x = chunk.getPos().getMinBlockX();
            this.z = chunk.getPos().getMinBlockZ();
        }

        private boolean contains(int x, int y, int z) {
            return x >= this.x && x < this.x + 16 && z >= this.z && z < this.z + 16 && !chunk.isOutsideBuildHeight(y);
        }

        BlockState at(int x, int y, int z) {
            return contains(x, y, z) ? chunk.getBlockState(pos.set(x, y, z)) : AbyssShape.AIR;
        }

        void put(int x, int y, int z, BlockState state) {
            if (contains(x, y, z) && at(x, y, z).isAir()) chunk.setBlockState(pos.set(x, y, z), state, false);
        }
    }

    private AbyssFormations() {}
}
