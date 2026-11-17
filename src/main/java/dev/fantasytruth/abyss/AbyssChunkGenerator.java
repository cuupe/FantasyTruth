package dev.fantasytruth.abyss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class AbyssChunkGenerator extends ChunkGenerator {
    public static final Codec<AbyssChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(AbyssChunkGenerator::getBiomeSource),
            Codec.intRange(1, 7).fieldOf("layer").forGetter(generator -> generator.layer.ordinal() + 1),
            Codec.BOOL.optionalFieldOf("forest_mouth", false).forGetter(generator -> generator.forestMouth),
            Codec.BOOL.optionalFieldOf("open_forest", false).forGetter(AbyssChunkGenerator::openForest),
            Codec.BOOL.optionalFieldOf("deep_forest", false).forGetter(AbyssChunkGenerator::deepForest)
    ).apply(instance, AbyssChunkGenerator::new));

    private static final ResourceLocation SEED = new ResourceLocation("stratacubes", "abyss");
    private static final Set<Heightmap.Types> HEIGHTMAPS = Set.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG);
    private record Binding(RandomState random, AbyssShape shape) {}

    private final AbyssLayer layer;
    private final boolean forestMouth;
    private final boolean openForest;
    private final boolean deepForest;
    private volatile Binding binding;
    private volatile ForestBinding forestBinding;

    private record ForestBinding(RandomState random, ForestTerrain terrain) {}

    private AbyssChunkGenerator(BiomeSource biomes, int layer, boolean forestMouth, boolean openForest, boolean deepForest) {
        super(biomes);
        this.layer = AbyssLayer.numbered(layer);
        this.forestMouth = this.layer == AbyssLayer.FOREST && forestMouth;
        this.openForest = this.forestMouth && openForest;
        this.deepForest = this.openForest && deepForest;
    }

    AbyssLayer layer() { return layer; }
    boolean openForest() { return openForest; }
    boolean deepForest() { return deepForest; }
    int exitY() { return deepForest ? ForestCoordinates.DEEP_EXIT : openForest ? ForestTerrain.EXIT : layer.exit; }

    ForestTerrain forest(RandomState state) {
        var current = forestBinding;
        if (current != null && current.random == state) return current.terrain;
        long seed = state.getOrCreateRandomFactory(SEED).at(layer.ordinal(), 0, 0).nextLong();
        var terrain = new ForestTerrain(seed, deepForest);
        forestBinding = new ForestBinding(state, terrain);
        return terrain;
    }

    AbyssShape shape(RandomState state) {
        Binding current = binding;
        if (current != null && current.random == state) return current.shape;
        long seed = state.getOrCreateRandomFactory(SEED).at(layer.ordinal(), 0, 0).nextLong();
        AbyssShape generated = new AbyssShape(layer, seed, forestMouth);
        binding = new Binding(state, generated);
        return generated;
    }

    @Override protected Codec<? extends ChunkGenerator> codec() { return CODEC; }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState state,
                                                       StructureManager structures, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> openForest ? forest(state).fill(chunk) : fill(chunk, shape(state)), executor);
    }

    private static ChunkAccess fill(ChunkAccess chunk, AbyssShape shape) {
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        var columns = new AbyssShape.Column[256];
        int roof = AbyssLayer.MIN_Y;
        for (int i = 0; i < columns.length; i++) {
            columns[i] = shape.column(x0 + (i & 15), z0 + (i >> 4));
            roof = Math.max(roof, columns[i].roof());
        }
        for (int index = 0; index < chunk.getSectionsCount(); index++) {
            int y0 = chunk.getMinBuildHeight() + index * 16;
            if (y0 > roof) break;
            var section = chunk.getSection(index);
            section.acquire();
            try {
                var states = section.getStates();
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState block = shape.block(columns[x + z * 16], y0 + y);
                            if (block != AbyssShape.AIR) states.getAndSetUnchecked(x, y, z, block);
                        }
                    }
                }
                section.recalcBlockCounts();
            } finally {
                section.release();
            }
        }
        Heightmap.primeHeightmaps(chunk, HEIGHTMAPS);
        return chunk;
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState state, ChunkAccess chunk) {
        if (openForest) ForestGrowth.place(chunk, forest(state));
        else AbyssFormations.place(chunk, shape(state));
        if (forestMouth && AbyssLayer.passage(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ(), 234)) {
            ForestMouth.grow(new EdgeExploration.Painter(chunk, ForestMouth.OFFSET));
        }
        // The open forest seeds its heightmaps in fill; surface writes update them in ProtoChunk.
        if (!openForest) Heightmap.primeHeightmaps(chunk, HEIGHTMAPS);
    }

    @Override public void createStructures(RegistryAccess registries, ChunkGeneratorStructureState state,
                                           StructureManager structures, ChunkAccess chunk, StructureTemplateManager templates) {}
    @Override public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk,
                                                StructureManager structures) {}
    @Override public void applyCarvers(WorldGenRegion region, long seed, RandomState state, BiomeManager biomes,
                                       StructureManager structures, ChunkAccess chunk, GenerationStep.Carving stage) {}
    @Override public void spawnOriginalMobs(WorldGenRegion region) {}

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState state) {
        if (openForest) {
            var terrain = forest(state);
            var column = terrain.column(x, z);
            for (int y = Math.min(column.top(), height.getMaxBuildHeight() - 1); y >= height.getMinBuildHeight(); y--) {
                if (type.isOpaque().test(terrain.block(column, y))) return y + 1;
            }
            return height.getMinBuildHeight();
        }
        var shape = shape(state);
        var column = shape.column(x, z);
        for (int y = Math.min(column.roof(), height.getMaxBuildHeight() - 1); y >= height.getMinBuildHeight(); y--) {
            if (type.isOpaque().test(shape.block(column, y))) return y + 1;
        }
        return height.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState state) {
        if (openForest) return forest(state).baseColumn(x, z, height);
        var shape = shape(state);
        var column = shape.column(x, z);
        var blocks = new BlockState[height.getHeight()];
        for (int i = 0; i < blocks.length; i++) blocks[i] = shape.block(column, height.getMinBuildHeight() + i);
        return new NoiseColumn(height.getMinBuildHeight(), blocks);
    }

    @Override public int getGenDepth() { return AbyssLayer.MAX_Y - getMinY(); }
    @Override public int getMinY() { return deepForest ? ForestCoordinates.DEEP_MIN_Y : openForest ? ForestTerrain.MIN_Y : AbyssLayer.MIN_Y; }
    @Override public int getSeaLevel() { return layer == AbyssLayer.SEA ? 402 : AbyssLayer.MIN_Y; }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState state, BlockPos pos) {
        if (openForest) {
            lines.add("Abyss L2 · depth " + ForestCoordinates.depth(pos.getY(), exitY()) + " m | Y "
                    + pos.getY() + " | passage Y " + exitY());
            return;
        }
        lines.add("Abyss L" + (layer.ordinal() + 1) + " " + layer.id + " · descent "
                + Math.max(0, layer.entrance - pos.getY()) + " / " + (layer.entrance - exitY()));
    }
}
