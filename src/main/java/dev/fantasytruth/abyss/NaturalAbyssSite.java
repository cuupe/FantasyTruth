package dev.fantasytruth.abyss;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.ticks.ProtoChunkTicks;

/** A translation from the authored Abyss coordinates to a native Overworld structure start. */
public record NaturalAbyssSite(int offsetX, int offsetZ) {
    public static final ResourceLocation OVERWORLD_TYPE = new ResourceLocation("stratacubes", "extended_overworld");
    public static final ResourceLocation STRUCTURE_ID = new ResourceLocation("minecraft", "abyss");
    private static final ResourceLocation STRUCTURE_SET_ID = new ResourceLocation("stratacubes", "abyss");
    private static final int LOOKOUT_CHUNK_Z = -48;
    private static final int RADIUS = OrthIsland.SITE_RADIUS + 16;
    private static final Set<Heightmap.Types> HEIGHTMAPS = Set.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG);

    static NaturalAbyssSite at(ChunkPos start) {
        return new NaturalAbyssSite(start.getMinBlockX(), (start.z - LOOKOUT_CHUNK_Z) << 4);
    }

    ChunkPos local(ChunkPos chunk) {
        return new ChunkPos(chunk.x - (offsetX >> 4), chunk.z - (offsetZ >> 4));
    }

    BlockPos world(BlockPos position) { return position.offset(offsetX, 0, offsetZ); }

    boolean inShaft(double x, double z) { return OrthIsland.inShaft(x - offsetX, z - offsetZ); }

    boolean core(ChunkPos chunk) {
        ChunkPos own = local(chunk);
        int x = OrthIsland.localX(own.getMinBlockX()), z = OrthIsland.localZ(own.getMinBlockZ());
        return OrthIsland.siteCore(Math.max(Math.abs(x), Math.abs(x + 15)), Math.max(Math.abs(z), Math.abs(z + 15)));
    }

    static BlockPos lookout(ChunkPos start) {
        int height = OrthIsland.elevation(0, LOOKOUT_CHUNK_Z << 4, true) + 1;
        return new BlockPos(start.getMiddleBlockX(), height, start.getMiddleBlockZ());
    }

    public static boolean extended(ServerLevel level) {
        return level.dimension() == Level.OVERWORLD && level.dimensionTypeId().location().equals(OVERWORLD_TYPE);
    }

    static boolean vanillaGenerator(ChunkGenerator generator, LevelHeightAccessor height) {
        return height.getMinBuildHeight() == -512 && height.getMaxBuildHeight() == 1024
                && generator instanceof NoiseBasedChunkGenerator noise && noise.generatorSettings().is(NoiseGeneratorSettings.OVERWORLD)
                && !(generator.getBiomeSource() instanceof OrthBiomeSource);
    }

    public static Distribution distribution(ServerLevel level) {
        var source = level.getChunkSource();
        if (!extended(level) || !level.structureManager().shouldGenerateStructures()
                || !vanillaGenerator(source.getGenerator(), level)) return null;
        var registries = level.registryAccess();
        var set = registries.registryOrThrow(Registries.STRUCTURE_SET).get(STRUCTURE_SET_ID);
        var structure = registries.registryOrThrow(Registries.STRUCTURE).get(STRUCTURE_ID);
        if (set == null || !(set.placement() instanceof RandomSpreadStructurePlacement placement)
                || !(structure instanceof NaturalAbyssStructure)) return null;
        return new Distribution(source.getGeneratorState(), source.getGenerator(), structure, placement);
    }

    public static NaturalAbyssSite find(ServerLevel level, int x, int z) {
        var sites = distribution(level);
        return sites == null ? null : sites.near(x, z);
    }

    public record Distribution(ChunkGeneratorStructureState state, ChunkGenerator generator,
                               Structure structure, RandomSpreadStructurePlacement placement) {
        public NaturalAbyssSite near(int x, int z) {
            int spacing = placement.spacing();
            int anchorZ = z + (LOOKOUT_CHUNK_Z << 4);
            int firstX = Math.floorDiv((x - RADIUS) >> 4, spacing);
            int lastX = Math.floorDiv((x + RADIUS) >> 4, spacing);
            int firstZ = Math.floorDiv((anchorZ - RADIUS) >> 4, spacing);
            int lastZ = Math.floorDiv((anchorZ + RADIUS) >> 4, spacing);
            for (int rz = firstZ; rz <= lastZ; rz++) {
                for (int rx = firstX; rx <= lastX; rx++) {
                    ChunkPos start = placement.getPotentialStructureChunk(state.getLevelSeed(), rx * spacing, rz * spacing);
                    var site = at(start);
                    int dx = x - site.offsetX - AbyssLayer.CENTER_X, dz = z - site.offsetZ - AbyssLayer.CENTER_Z;
                    if ((long) dx * dx + (long) dz * dz <= (long) RADIUS * RADIUS
                            && placement.isStructureChunk(state, start.x, start.z) && biomeAllows(start)) return site;
                }
            }
            return null;
        }

        private boolean biomeAllows(ChunkPos start) {
            var pos = lookout(start);
            var biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(pos.getX()),
                    QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()), state.randomState().sampler());
            return structure.biomes().contains(biome);
        }
    }

    /** Run the layout in its canonical coordinate space and transfer the generated blocks and block-entity NBT. */
    public void build(ServerLevel level, ChunkAccess target, int sea) {
        if (!(target instanceof ProtoChunk) || target.isUpgrading()) return;
        if (!core(target.getPos())) {
            OrthTerrain.blendCoast(target, sea, offsetX, offsetZ);
            return;
        }

        var sections = new LevelChunkSection[target.getSectionsCount()];
        for (int i = 0; i < sections.length; i++) {
            var blocks = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(),
                    PalettedContainer.Strategy.SECTION_STATES);
            sections[i] = new LevelChunkSection(blocks, target.getSection(i).getBiomes());
        }
        RegistryAccess registries = level.registryAccess();
        var local = new ProtoChunk(local(target.getPos()), UpgradeData.EMPTY, sections, new ProtoChunkTicks<>(),
                new ProtoChunkTicks<>(), target, registries.registryOrThrow(Registries.BIOME), null);
        local.setStatus(target.getStatus());
        OrthTerrain.fill(local, true, true, true, true, true, sea - 1);
        OrthCity.generateSurface(local, true, true, true, true, true, true);
        var edge = level.getServer().getLevel(AbyssLayer.EDGE.key);
        if (edge != null && edge.getChunkSource().getGenerator() instanceof AbyssEdgeGenerator generator
                && AbyssLayer.passage(local.getPos().getMiddleBlockX(), local.getPos().getMiddleBlockZ(), 660)) {
            generator.buildExpeditionFeatures(local, level.getSeed(), false);
        }
        OrthCity.generateDetails(local, true, true);
        System.arraycopy(local.getSections(), 0, target.getSections(), 0, sections.length);
        for (var type : HEIGHTMAPS) target.setHeightmap(type, local.getOrCreateHeightmapUnprimed(type).getRawData());
        for (var pos : local.getBlockEntitiesPos()) {
            var tag = local.getBlockEntityNbtForSaving(pos);
            if (tag == null) continue;
            tag.putInt("x", pos.getX() + offsetX);
            tag.putInt("z", pos.getZ() + offsetZ);
            target.setBlockEntityNbt(tag);
        }
    }
}
