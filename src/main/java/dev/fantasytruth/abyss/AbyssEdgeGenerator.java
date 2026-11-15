package dev.fantasytruth.abyss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** The Edge of the Abyss occupies its own dimension at the same absolute coordinates. */
public final class AbyssEdgeGenerator extends ChunkGenerator {
    public static final int ENTRANCE = 0;
    public static final int EXIT = EdgeTerrain.EXIT;
    public static final Codec<AbyssEdgeGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(AbyssEdgeGenerator::getBiomeSource),
            Codec.BOOL.optionalFieldOf("expanded_edge", false).forGetter(AbyssEdgeGenerator::expanded),
            Codec.BOOL.optionalFieldOf("forested_edge", false).forGetter(AbyssEdgeGenerator::forested),
            Codec.BOOL.optionalFieldOf("moss_pit", false).forGetter(AbyssEdgeGenerator::mossPit),
            Codec.BOOL.optionalFieldOf("eroded_edge", false).forGetter(AbyssEdgeGenerator::eroded),
            Codec.BOOL.optionalFieldOf("forest_mouth", false).forGetter(AbyssEdgeGenerator::forestMouth),
            Codec.BOOL.optionalFieldOf("wall_terraces", false).forGetter(generator -> generator.layout.wallTerraces()),
            Codec.BOOL.optionalFieldOf("wild_edge", false).forGetter(AbyssEdgeGenerator::wildEdge)
    ).apply(instance, AbyssEdgeGenerator::new));
    private static final Set<Heightmap.Types> HEIGHTMAPS = Set.of(
            Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG);

    private final EdgeTerrain.Layout layout;
    private volatile EdgeCaves.Plan cavePlan;

    EdgeCaves.Plan caves(long seed) {
        var plan = cavePlan;
        if (plan != null && plan.seed() == seed) return plan;
        synchronized (this) {
            plan = cavePlan;
            if (plan == null || plan.seed() != seed) cavePlan = plan = EdgeCaves.plan(seed, layout);
            return plan;
        }
    }

    private AbyssEdgeGenerator(BiomeSource biomes, boolean expanded, boolean forested, boolean mossPit, boolean eroded, boolean forestMouth, boolean wallTerraces, boolean wildEdge) {
        super(biomes);
        this.layout = !expanded ? EdgeTerrain.Layout.LEGACY : !forested ? EdgeTerrain.Layout.EXPANDED
                : !mossPit ? EdgeTerrain.Layout.FORESTED
                : forestMouth && wallTerraces && wildEdge ? EdgeTerrain.Layout.WILD_EDGE
                : forestMouth && wallTerraces ? EdgeTerrain.Layout.WALL_TERRACES : forestMouth ? EdgeTerrain.Layout.FOREST_MOUTH
                : eroded ? EdgeTerrain.Layout.ERODED : EdgeTerrain.Layout.MOSS_PIT;
    }

    boolean expanded() { return layout != EdgeTerrain.Layout.LEGACY; }
    boolean forested() { return layout.forested; }
    boolean mossPit() { return layout.mossPit; }
    boolean eroded() { return layout.eroded(); }
    boolean forestMouth() { return layout.forestMouth(); }
    boolean wildEdge() { return layout.wildEdge(); }
    EdgeTerrain.Layout layout() { return layout; }
    @Override protected Codec<? extends ChunkGenerator> codec() { return CODEC; }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState state,
                                                       StructureManager structures, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> fill(chunk), executor);
    }

    private ChunkAccess fill(ChunkAccess chunk) {
        var columns = new EdgeTerrain.Column[256];
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        int wallColumns = 0;
        int solidBottom = Integer.MAX_VALUE;
        int top = EdgeTerrain.MIN_Y;
        for (int i = 0; i < columns.length; i++) {
            var column = EdgeTerrain.column(x0 + (i & 15), z0 + (i >> 4), layout);
            columns[i] = column;
            if (!column.opening()) wallColumns++;
            solidBottom = Math.min(solidBottom, column.bottom());
            top = Math.max(top, column.top());
        }
        LevelChunkSection[] sections = chunk.getSections();
        boolean allWall = wallColumns == columns.length;
        BitSet occupied = allWall ? null : occupiedSections(chunk, columns, sections.length);
        BlockState stone = layout.mossPit ? EdgeTerrain.wall(-1, layout) : null;
        for (int i = 0, low = chunk.getMinBuildHeight(); i < sections.length && low <= top; i++, low += 16) {
            if (occupied != null && !occupied.get(i)) continue;
            int high = low + 15;
            if (i > 0 && (high < 0 && allWall || high <= solidBottom - 4)) {
                replaceSection(sections, i, stone == null ? EdgeTerrain.wall(low + 8, layout) : stone);
            } else if (stone != null && high < 0 && i > 0 && wallColumns > 128) {
                // Columns outside the shaft already contain the correct rock.
                fillRockSection(replaceSection(sections, i, stone), low, columns, stone);
            } else {
                fillSection(sections[i], low, high, columns);
            }
        }
        fillHeightmaps(chunk, columns);
        return chunk;
    }

    private static LevelChunkSection replaceSection(LevelChunkSection[] sections, int index, BlockState state) {
        var blocks = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, state, PalettedContainer.Strategy.SECTION_STATES);
        return sections[index] = new LevelChunkSection(blocks, sections[index].getBiomes());
    }

    private void fillRockSection(LevelChunkSection section, int low, EdgeTerrain.Column[] columns, BlockState stone) {
        for (int i = 0; i < columns.length; i++) {
            var column = columns[i];
            if (!column.opening()) continue;
            int x = i & 15, z = i >> 4;
            for (int dy = 0; dy < 16; dy++) {
                BlockState state = EdgeTerrain.block(column, low + dy, layout);
                if (state != stone) section.setBlockState(x, dy, z, state, false);
            }
        }
    }

    private static BitSet occupiedSections(ChunkAccess chunk, EdgeTerrain.Column[] columns, int sections) {
        var occupied = new BitSet(sections);
        int min = chunk.getMinBuildHeight();
        for (var column : columns) {
            if (!column.opening()) {
                markSections(occupied, min, sections, min, column.top());
            } else {
                markSections(occupied, min, sections, min, column.bottom());
                for (var rock : column.rocks()) markSections(occupied, min, sections, rock.bottom(), rock.top());
                for (var shelf : column.shelves()) markSections(occupied, min, sections, shelf.bottom(), shelf.water());
            }
        }
        return occupied;
    }

    private static void markSections(BitSet occupied, int min, int sections, int low, int high) {
        int start = Mth.clamp((low - min) >> 4, 0, sections);
        int end = Mth.clamp(((high - min) >> 4) + 1, 0, sections);
        if (start < end) occupied.set(start, end);
    }

    private static void fillHeightmaps(ChunkAccess chunk, EdgeTerrain.Column[] columns) {
        int min = chunk.getMinBuildHeight();
        if (min != EdgeTerrain.MIN_Y || chunk.getMaxBuildHeight() != EdgeTerrain.MAX_Y) {
            Heightmap.primeHeightmaps(chunk, HEIGHTMAPS);
            return;
        }
        int bits = Mth.ceillog2(chunk.getHeight() + 1);
        var surface = new SimpleBitStorage(bits, columns.length);
        var floor = new SimpleBitStorage(bits, columns.length);
        for (int i = 0; i < columns.length; i++) {
            var column = columns[i];
            surface.set(i, column.top() + 1 - min);
            int ground = column.opening() ? column.solidTop() : Math.max(-1, column.upper());
            floor.set(i, ground + 1 - min);
        }
        chunk.setHeightmap(Heightmap.Types.WORLD_SURFACE_WG, surface.getRaw());
        chunk.setHeightmap(Heightmap.Types.OCEAN_FLOOR_WG, floor.getRaw());
    }

    private void fillSection(LevelChunkSection section, int low, int high, EdgeTerrain.Column[] columns) {
        for (int i = 0; i < columns.length; i++) {
            var column = columns[i];
            int x = i & 15, z = i >> 4;
            if (!column.opening()) {
                for (int y = low; y <= high && y <= column.top(); y++) {
                    section.setBlockState(x, y - low, z, EdgeTerrain.block(column, y, layout), false);
                }
                continue;
            }
            for (int y = low; y <= high && y <= column.bottom(); y++) {
                section.setBlockState(x, y - low, z, EdgeTerrain.block(column, y, layout), false);
            }
            for (var rock : column.rocks()) {
                for (int y = Math.max(low, rock.bottom()); y <= Math.min(high, rock.top()); y++) {
                    section.setBlockState(x, y - low, z, EdgeTerrain.scarpMaterial(rock, y, layout), false);
                }
            }
            for (var span : column.shelves()) {
                for (int y = Math.max(low, span.bottom()); y <= Math.min(high, span.top()); y++) {
                    section.setBlockState(x, y - low, z, EdgeTerrain.shelfMaterial(span, y, layout), false);
                }
                for (int y = Math.max(low, span.top() + 1); y <= Math.min(high, span.water()); y++) {
                    section.setBlockState(x, y - low, z, Blocks.WATER.defaultBlockState(), false);
                }
            }
        }
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState state, ChunkAccess chunk) {
        if (AbyssLayer.passage(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ(), 656)) {
            OrthCity.generateSurface(chunk, true, true, forested(), mossPit(), layout);
        }
        if (expanded()) {
            buildExpeditionFeatures(chunk, region.getSeed(), true);
        } else {
            EdgeFeatures.place(chunk);
        }
        if (forestMouth() && AbyssLayer.passage(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ(),
                ForestMouth.PREVIEW_RADIUS)) {
            var forest = region.getLevel().getServer().getLevel(AbyssLayer.FOREST.key);
            if (forest != null && forest.getChunkSource().getGenerator() instanceof AbyssChunkGenerator next && next.openForest()) {
                ForestMouth.preview(chunk, next.forest(forest.getChunkSource().randomState()));
            }
        }
    }

    void buildExpeditionFeatures(ChunkAccess chunk, long seed, boolean edge) {
        EdgeRelics.place(chunk, seed, layout);
        EdgeExploration.place(chunk, layout);
        if (eroded() && EdgeCaves.nearShaft(chunk.getPos())) {
            caves(seed).place(chunk);
            EdgeOres.place(chunk, seed, layout);
        }
        if (mossPit()) OrthDescent.build(chunk, edge, layout);
        if (eroded() && EdgeCaves.nearShaft(chunk.getPos())) EdgeVegetation.trimUnsupportedRoots(chunk);
    }

    @Override public void createStructures(RegistryAccess registries, ChunkGeneratorStructureState state,
                                           StructureManager structures, ChunkAccess chunk, StructureTemplateManager templates) {}
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        if (expanded() && AbyssLayer.passage(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ(),
                forested() ? 475 : 383)) {
            EdgeVegetation.placeVanillaTrees(level, chunk, this);
        }
        if (layout.wallTerraces() && AbyssLayer.passage(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ(), 656)) {
            OrthCity.generateDetails(chunk, true, true);
        }
    }
    @Override public void applyCarvers(WorldGenRegion region, long seed, RandomState state, BiomeManager biomes,
                                       StructureManager structures, ChunkAccess chunk, GenerationStep.Carving stage) {}
    @Override public void spawnOriginalMobs(WorldGenRegion region) {}

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState state) {
        var column = EdgeTerrain.column(x, z, layout);
        if (column.opening()) {
            return (type.isOpaque().test(Blocks.WATER.defaultBlockState()) ? column.top() : column.solidTop()) + 1;
        }
        return type.isOpaque().test(Blocks.WATER.defaultBlockState()) ? column.top() + 1 : Math.max(-1, column.upper()) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState state) {
        var column = EdgeTerrain.column(x, z, layout);
        var blocks = new BlockState[height.getHeight()];
        for (int i = 0; i < blocks.length; i++) blocks[i] = EdgeTerrain.block(column, height.getMinBuildHeight() + i, layout);
        return new NoiseColumn(height.getMinBuildHeight(), blocks);
    }

    @Override public int getGenDepth() { return EdgeTerrain.MAX_Y - EdgeTerrain.MIN_Y; }
    @Override public int getMinY() { return EdgeTerrain.MIN_Y; }
    @Override public int getSeaLevel() { return OrthIsland.SEA; }
    @Override public void addDebugScreenInfo(List<String> lines, RandomState state, BlockPos pos) {
        lines.add("Abyss L1 · depth " + Math.max(0, -pos.getY()) + " / " + -EXIT);
    }
}
