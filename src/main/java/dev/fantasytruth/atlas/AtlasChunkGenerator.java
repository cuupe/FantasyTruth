package dev.fantasytruth.atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.fantasytruth.terrain.AtlasCaves;
import dev.fantasytruth.terrain.AtlasField;
import dev.fantasytruth.terrain.AtlasProfile;
import dev.fantasytruth.terrain.AtlasSpawn;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

public final class AtlasChunkGenerator extends ChunkGenerator {
    public static final Codec<AtlasChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AtlasBiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.source),
            AtlasCodecs.PROFILE.optionalFieldOf("profile", AtlasProfile.DEFAULT).forGetter(generator -> generator.profile),
            Codec.LONG.optionalFieldOf("page", 0L).forGetter(generator -> generator.page)
    ).apply(instance, AtlasChunkGenerator::new));

    private record Binding(RandomState randomState, AtlasField terrain) {}
    private static final ResourceLocation TERRAIN_SEED = new ResourceLocation("stratacubes", "atlas_field");
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState TUFF = Blocks.TUFF.defaultBlockState();
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
    private static final Set<Heightmap.Types> GENERATION_HEIGHTS = Set.of(
            Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG);

    private final AtlasBiomeSource source;
    private final AtlasProfile profile;
    private final long page;
    private final long originY;
    private volatile Binding binding;
    private volatile AtlasContent content = AtlasContent.EMPTY;

    private AtlasChunkGenerator(AtlasBiomeSource source, AtlasProfile profile, long page) {
        super(source.forPage(Math.multiplyExact(page, Altitude.STRIDE)));
        this.source = (AtlasBiomeSource) getBiomeSource();
        this.profile = profile;
        this.page = page;
        this.originY = Math.multiplyExact(page, Altitude.STRIDE);
    }

    AtlasChunkGenerator forPage(long targetPage) {
        AtlasChunkGenerator generator = new AtlasChunkGenerator(source, profile, targetPage);
        generator.content = content;
        return generator;
    }

    void content(AtlasContent content) { this.content = content; }
    long page() { return page; }
    public AtlasProfile profile() { return profile; }

    public ChunkPos initialSpawnChunk(RandomState state, ChunkPos vanilla) {
        AtlasSpawn.Site site = AtlasSpawn.find(field(state), vanilla.getMiddleBlockX(), vanilla.getMiddleBlockZ());
        if (site == null) return vanilla;
        com.mojang.logging.LogUtils.getLogger().info("Atlas spawn area at {}, {}, elevation {}, nearby dry samples {}/8",
                site.x(), site.z(), site.surface(), site.drySamples());
        return new ChunkPos(site.x() >> 4, site.z() >> 4);
    }

    public static long terrainSeed(long worldSeed) {
        return NoiseGeneratorSettings.dummy().getRandomSource().newInstance(worldSeed).forkPositional()
                .fromHashOf(TERRAIN_SEED).forkPositional().at(0, 0, 0).nextLong();
    }

    AtlasLandmarks.Site nearestLandmark(RandomState random, String id, int x, int z) {
        return AtlasLandmarks.nearest(content, field(random), id, x, z);
    }

    AtlasField field(RandomState randomState) {
        Binding cached = binding;
        if (cached != null && cached.randomState() == randomState) return cached.terrain();
        long seed = randomState.getOrCreateRandomFactory(TERRAIN_SEED).at(0, 0, 0).nextLong();
        AtlasField terrain = new AtlasField(seed, profile);
        source.bind(terrain);
        binding = new Binding(randomState, terrain);
        return terrain;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() { return CODEC; }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(Executor executor, RandomState state, Blender blender,
                                                      StructureManager structures, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            AtlasField terrain = field(state);
            AtlasField.Column[] columns = new AtlasField.Column[16];
            int x = chunk.getPos().getMinBlockX();
            int z = chunk.getPos().getMinBlockZ();
            for (int i = 0; i < columns.length; i++) {
                columns[i] = terrain.column(x + (i & 3) * 4, z + (i >> 2) * 4);
            }
            chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> source.at(terrain.biomeAt(
                    columns[(qx & 3) + (qz & 3) * 4], originY + QuartPos.toBlock(qy))), state.sampler());
            return chunk;
        }, executor);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState state,
                                                        StructureManager structures, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> generate(chunk, field(state)), executor);
    }

    private ChunkAccess generate(ChunkAccess chunk, AtlasField terrain) {
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        AtlasField.Column[] columns = new AtlasField.Column[256];
        byte[] lithology = new byte[columns.length];
        int rockTop = Integer.MIN_VALUE;
        for (int i = 0; i < columns.length; i++) {
            columns[i] = terrain.column(x0 + (i & 15), z0 + (i >> 4));
            lithology[i] = (byte) lithology(terrain, x0 + (i & 15), z0 + (i >> 4));
            rockTop = Math.max(rockTop, columns[i].height());
        }
        int top = Math.max(rockTop, profile.seaLevel() - 1);
        BlockState[] shallowRock = new BlockState[256];
        BlockState[] deepRock = new BlockState[256];
        for (int sectionIndex = 0; sectionIndex < chunk.getSectionsCount(); sectionIndex++) {
            long worldY = originY + chunk.getMinBuildHeight() + sectionIndex * 16L;
            if (worldY > top) break;
            LevelChunkSection section = chunk.getSection(sectionIndex);
            AtlasCaves caves = worldY + 12 < rockTop ? caves(terrain, x0, worldY, z0) : null;
            if (worldY <= rockTop) {
                for (int phase = 0; phase < 16; phase++) {
                    for (int y = 0; y < 16; y++) {
                        int index = phase * 16 + y;
                        shallowRock[index] = rock(worldY + y, phase, false);
                        deepRock[index] = rock(worldY + y, phase, true);
                    }
                }
            }
            int waterTop = worldY <= profile.seaLevel() - 16L ? 15
                    : (int) (profile.seaLevel() - 1L - worldY);
            section.acquire();
            try {
                var states = section.getStates();
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int index = x + z * 16;
                        AtlasField.Column column = columns[index];
                        int depthAtBottom = worldY < column.height() - 100000L ? 100000 : (int) (column.height() - worldY);
                        int solidTop = Math.min(15, depthAtBottom);
                        int phase = lithology[index] * 16;
                        for (int y = 0; y <= solidTop; y++) {
                            int depth = depthAtBottom - y;
                            if (depth > 12 && caves != null && caves.isOpen(x, y, z)) continue;
                            BlockState block = depth < 4 ? surface(column, depth)
                                    : (depth > 50 ? deepRock : shallowRock)[phase + y];
                            states.getAndSetUnchecked(x, y, z, block);
                        }
                        for (int y = Math.max(0, solidTop + 1); y <= waterTop; y++) {
                            states.getAndSetUnchecked(x, y, z, WATER);
                        }
                    }
                }
                if (worldY <= rockTop) AtlasDeposits.place(content, terrain, columns, section, x0, worldY, z0);
                section.recalcBlockCounts();
            } finally {
                section.release();
            }
        }
        Heightmap.primeHeightmaps(chunk, GENERATION_HEIGHTS);
        return chunk;
    }

    private AtlasCaves caves(AtlasField terrain, int x, long y, int z) {
        if (profile.caves() == 0) return null;
        AtlasCaves caves = new AtlasCaves(terrain.seed(), x, y, z, profile.caves());
        return caves.hasOpenCells() ? caves : null;
    }

    private BlockState blockAt(AtlasField terrain, AtlasField.Column column, AtlasCaves caves,
                               int x0, int z0, long y0, int x, int y, int z) {
        long altitude = y0 + y;
        if (altitude > column.height()) return altitude < profile.seaLevel() ? WATER : AIR;
        long depth = altitude < column.height() - 100000L ? 100000 : column.height() - altitude;
        if (depth > 12 && caves != null && caves.isOpen(x, y, z)) return AIR;
        if (depth < 4) return surface(column, (int) depth);
        return rock(altitude, lithology(terrain, x0 + x, z0 + z), depth > 50);
    }

    private static int lithology(AtlasField terrain, int x, int z) {
        return (int) (AtlasField.hash(terrain.seed() ^ 18, Math.floorDiv(x, 26), 0, Math.floorDiv(z, 26)) & 15);
    }

    private static BlockState rock(long altitude, int phase, boolean deep) {
        int stratum = Math.floorMod(altitude, 51) + phase;
        if (stratum % 19 < 2) return altitude < -120 ? TUFF : ANDESITE;
        if (stratum % 37 == 3 && deep) return CALCITE;
        return altitude < 0 ? DEEPSLATE : STONE;
    }

    private static BlockState surface(AtlasField.Column column, int depth) {
        return switch (column.biome()) {
            case SHELF_SEA -> Blocks.GRAVEL.defaultBlockState();
            case ESTUARY -> depth < 2 ? Blocks.SAND.defaultBlockState() : Blocks.CLAY.defaultBlockState();
            case DUNE_SEA -> depth < 2 ? Blocks.SAND.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
            case FLOODPLAIN -> depth == 0 ? Blocks.MUD.defaultBlockState() : Blocks.CLAY.defaultBlockState();
            case RAINSHADOW_SCRUB -> depth == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState();
            case GLACIAL_CIRQUE -> depth == 0 && column.slope() < 1.25 ? Blocks.SNOW_BLOCK.defaultBlockState() : STONE;
            case ALPINE_HEATH -> column.slope() > 0.58 ? ANDESITE : Blocks.COARSE_DIRT.defaultBlockState();
            default -> column.slope() > 1.24 ? STONE :
                    (depth == 0 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState());
        };
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState state) {
        AtlasField terrain = field(state);
        AtlasField.Column column = terrain.column(x, z);
        int x0 = x & ~15;
        int z0 = z & ~15;
        BlockState[] blocks = new BlockState[height.getHeight()];
        for (int i = 0; i < blocks.length; i += 16) {
            long worldY = originY + height.getMinBuildHeight() + i;
            AtlasCaves caves = worldY < column.height() ? caves(terrain, x0, worldY, z0) : null;
            for (int y = 0; y < 16 && i + y < blocks.length; y++) {
                blocks[i + y] = blockAt(terrain, column, caves, x0, z0, worldY, x & 15, y, z & 15);
            }
        }
        return new NoiseColumn(height.getMinBuildHeight(), blocks);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState state) {
        AtlasField terrain = field(state);
        AtlasField.Column column = terrain.column(x, z);
        long top = Math.max(column.height(), type.isOpaque().test(WATER) ? profile.seaLevel() - 1 : column.height());
        if (top >= originY + height.getMinBuildHeight() && top < originY + height.getMaxBuildHeight()) {
            return (int) (top - originY) + 1;
        }
        if (top < originY + height.getMinBuildHeight()) return height.getMinBuildHeight();
        NoiseColumn blocks = getBaseColumn(x, z, height, state);
        for (int y = height.getMaxBuildHeight() - 1; y >= height.getMinBuildHeight(); y--) {
            if (type.isOpaque().test(blocks.getBlock(y))) return y + 1;
        }
        return height.getMinBuildHeight();
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState state, ChunkAccess chunk) {
        AtlasField terrain = field(state);
        AtlasDecorations.populate(terrain, chunk, originY);
        AtlasLandmarks.place(content, terrain, chunk, page);
        Heightmap.primeHeightmaps(chunk, GENERATION_HEIGHTS);
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState state, BiomeManager biomes,
                             StructureManager structures, ChunkAccess chunk, GenerationStep.Carving stage) {
        // Cavities are part of the shared absolute-coordinate density in the NOISE stage.
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // Runtime spawning uses the custom biome tables.
    }

    @Override
    public int getGenDepth() { return Altitude.HEIGHT; }
    @Override
    public int getMinY() { return Altitude.MIN_LOCAL; }
    @Override
    public int getSeaLevel() {
        int max = Altitude.MIN_LOCAL + Altitude.HEIGHT - 1;
        long sea = profile.seaLevel();
        if (originY <= sea - max) return max;
        if (originY >= sea - Altitude.MIN_LOCAL) return Altitude.MIN_LOCAL;
        return (int) (sea - originY);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState state, BlockPos pos) {
        AtlasField.Column column = field(state).column(pos.getX(), pos.getZ());
        lines.add("Atlas Y: " + (originY + pos.getY()) + " · ground: " + column.height() + " · " + column.biome());
    }
}
