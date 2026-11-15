package dev.fantasytruth.abyss;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Builds the same upper Edge structures in the Overworld's terrain overlap. */
public final class OrthSeam {
    public static void decorate(WorldGenRegion region, ChunkAccess chunk) {
        if (!AbyssLayer.passage(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ(), 660)) return;
        var edge = region.getLevel().getServer().getLevel(AbyssLayer.EDGE.key);
        if (edge != null && edge.getChunkSource().getGenerator() instanceof AbyssEdgeGenerator generator
                && generator.layout().wallTerraces()) {
            generator.buildExpeditionFeatures(chunk, region.getSeed(), false);
        }
    }

    private OrthSeam() {}
}
