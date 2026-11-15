package dev.fantasytruth.abyss;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Sparse deposits replace existing rock in the first-layer ledges and scarps. */
final class EdgeRelics {
    static void place(ChunkAccess chunk, long seed, EdgeTerrain.Layout layout) {
        var chunkPos = chunk.getPos();
        if (!AbyssLayer.passage(chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ(), 570)) return;
        var random = RandomSource.create(seed ^ EdgeExploration.hash(chunkPos.x, 0x7461, chunkPos.z));
        if (random.nextInt(4) != 0) return;

        int x0 = chunkPos.getMinBlockX(), z0 = chunkPos.getMinBlockZ();
        var pos = new BlockPos.MutableBlockPos();
        BlockState ore = OrthBlocks.RELIC_ORE.get().defaultBlockState();
        for (int attempt = 0; attempt < 14; attempt++) {
            int x = x0 + 2 + random.nextInt(12), z = z0 + 2 + random.nextInt(12);
            var column = EdgeTerrain.column(x, z, layout);
            if (!column.opening() || column.rocks().isEmpty() && column.shelves().isEmpty()) continue;
            int top;
            if (!column.rocks().isEmpty()) {
                top = column.rocks().get(random.nextInt(column.rocks().size())).top();
            } else {
                var shelf = column.shelves().get(random.nextInt(column.shelves().size()));
                if (shelf.cover() == EdgeLandscape.Cover.TRAIL || shelf.cover() == EdgeLandscape.Cover.POOL
                        || shelf.cover() == EdgeLandscape.Cover.PIT_MOSS || shelf.top() < -1320) continue;
                top = shelf.top();
            }
            int y = top - 4 - random.nextInt(8);
            if (!hostRock(chunk.getBlockState(pos.set(x, y, z)))) continue;
            boolean exposed = chunk.getBlockState(pos.set(x - 1, y, z)).isAir()
                    || chunk.getBlockState(pos.set(x + 1, y, z)).isAir()
                    || chunk.getBlockState(pos.set(x, y, z - 1)).isAir()
                    || chunk.getBlockState(pos.set(x, y, z + 1)).isAir();
            if (!exposed && attempt < 10) continue;
            chunk.setBlockState(pos.set(x, y, z), ore, false);
            if (hostRock(chunk.getBlockState(pos.set(x, y - 1, z)))) chunk.setBlockState(pos, ore, false);
            return;
        }
    }

    static boolean hostRock(BlockState state) {
        return state.is(OrthBlocks.STONE.get()) || state.is(OrthBlocks.CHALK.get())
                || state.is(Blocks.ANDESITE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.TUFF);
    }

    private EdgeRelics() {}
}
