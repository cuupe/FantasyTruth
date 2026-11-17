package dev.fantasytruth.abyss;

import dev.fantasytruth.horizon.HorizonTile;
import dev.fantasytruth.horizon.HorizonChunkData;
import dev.fantasytruth.horizon.HorizonTile.Layer;
import dev.fantasytruth.horizon.HorizonTile.Span;
import dev.fantasytruth.horizon.HorizonTile.Texture;
import dev.fantasytruth.terrain.AtlasField;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import static dev.fantasytruth.horizon.HorizonTile.GRASS;
import static dev.fantasytruth.horizon.HorizonTile.STONE;
import static dev.fantasytruth.horizon.HorizonTile.WATER;

/** Uses the same immutable terrain profiles as near generation, without opening chunks. */
public final class AbyssHorizonSampler {
    private static final int CACHED_COLUMNS = 12288;
    private final ChunkGenerator generator;
    private final RandomState random;
    private final LevelHeightAccessor height;
    private final NaturalAbyssSite.Distribution sites;
    private final long seed;
    // A column on the world-aligned lattice is shared by adjacent tiles and successive detail levels.
    private final ColumnCache<List<Span>> surfaceColumns = new ColumnCache<>();
    private final ColumnCache<ForestTerrain.Column> forestColumns = new ColumnCache<>();
    private final boolean forest;

    private AbyssHorizonSampler(ChunkGenerator generator, RandomState random, LevelHeightAccessor height,
                                NaturalAbyssSite.Distribution sites, long seed) {
        this.generator = generator;
        this.random = random;
        this.height = height;
        this.sites = sites;
        this.seed = seed;
        forest = generator instanceof AbyssChunkGenerator abyss && abyss.openForest();
    }

    public static AbyssHorizonSampler of(ServerLevel level) {
        var source = level.getChunkSource();
        var generator = source.getGenerator();
        if (!(generator instanceof AbyssEdgeGenerator || generator instanceof AbyssChunkGenerator
                || level.dimension() == Level.OVERWORLD && generator instanceof NoiseBasedChunkGenerator)) return null;
        return new AbyssHorizonSampler(generator, source.randomState(),
                LevelHeightAccessor.create(level.getMinBuildHeight(), level.getHeight()), NaturalAbyssSite.distribution(level), level.getSeed());
    }

    /** Copy only already-loaded columns. The sampler thread never accesses a mutable chunk. */
    public Map<Long, List<Span>> captureLoaded(ServerLevel level, HorizonTile tile) {
        if (tile.step() > 8 || !fine(tile) || !forest && level.dimension() != Level.OVERWORLD || OrthIsland.enabled(generator)
                || sites != null && sites.near(tile.x() + tile.size() / 2, tile.z() + tile.size() / 2) != null) return Map.of();
        var source = level.getChunkSource();
        var chunks = new HashMap<Long, LevelChunk>();
        var samples = new HashMap<Long, List<Span>>();
        var pos = new BlockPos.MutableBlockPos();
        int step = tile.step();
        for (int z = tile.z() - step; z <= tile.z() + tile.size() + step; z += step) {
            for (int x = tile.x() - step; x <= tile.x() + tile.size() + step; x += step) {
                int cx = x >> 4, cz = z >> 4;
                long key = ChunkPos.asLong(cx, cz);
                if (!chunks.containsKey(key)) chunks.put(key, source.getChunkNow(cx, cz));
                var chunk = chunks.get(key);
                if (chunk == null) continue;
                samples.put(ChunkPos.asLong(x, z), observedColumn(chunk, pos, x, z));
            }
        }
        return samples;
    }

    public HorizonChunkData observe(LevelChunk chunk) {
        var chunkPos = chunk.getPos();
        if (!forest && (!(generator instanceof NoiseBasedChunkGenerator) || OrthIsland.enabled(generator)
                || sites != null && sites.near(chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ()) != null)) return null;
        var columns = new ArrayList<List<Span>>(HorizonChunkData.WIDTH * HorizonChunkData.WIDTH);
        var pos = new BlockPos.MutableBlockPos();
        int step = HorizonChunkData.STEP;
        for (int z = 0; z < 16; z += step) {
            for (int x = 0; x < 16; x += step) {
                columns.add(observedColumn(chunk, pos, chunkPos.getMinBlockX() + x, chunkPos.getMinBlockZ() + z));
            }
        }
        return new HorizonChunkData(columns);
    }

    private List<Span> observedColumn(LevelChunk chunk, BlockPos.MutableBlockPos pos, int x, int z) {
        var spans = new ArrayList<Span>(4);
        if (forest && generator instanceof AbyssChunkGenerator abyss) {
            var profile = forestProfile(abyss, x, z);
            int separation = profile.shelfTop() - profile.floor();
            captureGrowth(chunk, pos, spans, x, z, profile.floor(), separation > 32 ? Math.min(60, separation) : 60);
            if (separation > 32) captureGrowth(chunk, pos, spans, x, z, profile.shelfTop(), 46);
        } else captureSurface(chunk, pos, spans, x, z);
        return List.copyOf(limit(spans));
    }

    private void captureSurface(LevelChunk chunk, BlockPos.MutableBlockPos pos, List<Span> spans, int x, int z) {
        int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15);
        if (top < height.getMinBuildHeight()) return;
        var block = chunk.getBlockState(pos.set(x, top, z));
        var biome = chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(top), QuartPos.fromBlock(z)).value();
        if (block.getFluidState().is(FluidTags.WATER)) {
            int bed = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15);
            add(spans, height.getMinBuildHeight(), bed + 1, HorizonTile.snapshotColor(0x77735d, Texture.SAND));
            addWater(spans, top + 1, waterTint(biome.getWaterColor()));
            return;
        }
        captureRuns(chunk, pos, spans, x, z, Math.max(height.getMinBuildHeight(), top - 35), top, biome, false);
    }

    private void captureGrowth(LevelChunk chunk, BlockPos.MutableBlockPos pos, List<Span> spans,
                               int x, int z, int surface, int reach) {
        if (surface < height.getMinBuildHeight()) return;
        var biome = chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(surface), QuartPos.fromBlock(z)).value();
        captureRuns(chunk, pos, spans, x, z, surface + 1, Math.min(height.getMaxBuildHeight() - 1, surface + reach), biome, true);
    }

    private void captureRuns(LevelChunk chunk, BlockPos.MutableBlockPos pos, List<Span> spans,
                             int x, int z, int bottom, int top, Biome biome, boolean growth) {
        int previous = 0, start = bottom;
        for (int y = bottom; y <= top; y++) {
            var block = chunk.getBlockState(pos.set(x, y, z));
            int color = blockColor(block, chunk, pos, biome, growth);
            if (color == previous) continue;
            if (previous != 0) add(spans, start, y, previous);
            start = !growth && y == bottom && !HorizonTile.isWaterColor(color) ? height.getMinBuildHeight() : y;
            previous = color;
        }
        if (previous != 0) add(spans, start, top + 1, previous);
    }

    private static int blockColor(BlockState block, LevelChunk chunk, BlockPos pos, Biome biome, boolean growth) {
        if (block.is(BlockTags.LEAVES)) return HorizonTile.snapshotColor(foliageTint(biome.getFoliageColor()), Texture.LEAVES);
        if (block.is(BlockTags.LOGS)) return HorizonTile.snapshotColor(0x655039, Texture.WOOD);
        if (block.is(BlockTags.PLANKS)) return HorizonTile.snapshotColor(tint(block.getMapColor(chunk, pos).col, 0.9, 0.89, 0.85), Texture.PLANKS);
        if (growth || block.isAir()) return 0;
        if (block.getFluidState().is(FluidTags.WATER)) return waterTint(biome.getWaterColor());
        if (!block.blocksMotion()) return 0;
        if (block.is(Blocks.GRASS_BLOCK) || block.is(Blocks.MOSS_BLOCK)) {
            return HorizonTile.snapshotColor(grassTint(biome.getGrassColor(pos.getX(), pos.getZ())), Texture.GRASS);
        }
        var texture = block.is(BlockTags.SAND) || block.is(Blocks.SANDSTONE) || block.is(Blocks.RED_SANDSTONE)
                ? Texture.SAND : block.is(BlockTags.DIRT) ? Texture.SOIL : Texture.STONE;
        return HorizonTile.snapshotColor(tint(block.getMapColor(chunk, pos).col, 0.9, 0.89, 0.85), texture);
    }

    private boolean fine(HorizonTile tile) {
        return tile.step() <= 16 && (forest || generator instanceof NoiseBasedChunkGenerator);
    }

    public HorizonTile.Data sample(HorizonTile tile, Map<Long, List<Span>> captured, Map<Long, List<Span>> archived) {
        int step = tile.step();
        boolean fine = fine(tile);
        int border = fine ? 1 : 0;
        int samples = tile.cells() + 1 + 2 * border;
        var data = new ArrayList<List<Span>>(samples * samples);
        for (int z = 0; z < samples; z++) {
            for (int x = 0; x < samples; x++) {
                int wx = tile.x() + (x - border) * step, wz = tile.z() + (z - border) * step;
                long key = ChunkPos.asLong(wx, wz);
                var existing = captured.get(key);
                if (existing == null) existing = archived.get(key);
                if (existing == null) data.add(column(wx, wz, step));
                else if (!forest) data.add(existing);
                else {
                    var spans = new ArrayList<Span>();
                    appendForest(spans, forestProfile((AbyssChunkGenerator) generator, wx, wz), wx, wz, step, false);
                    spans.addAll(existing);
                    spans.sort(Comparator.comparingInt(Span::bottom));
                    data.add(limit(spans));
                }
            }
        }
        return new HorizonTile.Data(samples, forest, fine, data);
    }

    private List<Span> column(int x, int z, int step) {
        long key = ChunkPos.asLong(x, z);
        if (generator instanceof NoiseBasedChunkGenerator) {
            var cached = surfaceColumns.get(key);
            if (cached != null) return cached;
            var surface = new ArrayList<Span>(4);
            appendSurface(surface, x, z);
            surface.sort(Comparator.comparingInt(Span::bottom));
            var result = limit(surface);
            surfaceColumns.put(key, result);
            return result;
        }
        var spans = new ArrayList<Span>(6);
        if (generator instanceof AbyssEdgeGenerator edge) {
            appendEdge(spans, EdgeTerrain.column(x, z, edge.layout()), height.getMinBuildHeight(), height.getMaxBuildHeight());
        } else if (generator instanceof AbyssChunkGenerator abyss) {
            if (forest) {
                appendForest(spans, forestProfile(abyss, x, z), x, z, step, true);
            }
            else appendRock(spans, abyss.shape(random), x, z, Math.max(4, step / 8));
        }
        spans.sort(Comparator.comparingInt(Span::bottom));
        return limit(spans);
    }

    private ForestTerrain.Column forestProfile(AbyssChunkGenerator abyss, int x, int z) {
        long key = ChunkPos.asLong(x, z);
        var profile = forestColumns.get(key);
        if (profile == null) {
            profile = abyss.forest(random).column(x, z);
            forestColumns.put(key, profile);
        }
        return profile;
    }

    private static final class ColumnCache<T> {
        private final Long2ObjectLinkedOpenHashMap<T> entries = new Long2ObjectLinkedOpenHashMap<>(256);

        synchronized T get(long key) {
            return entries.getAndMoveToLast(key);
        }

        synchronized void put(long key, T value) {
            entries.putAndMoveToLast(key, value);
            if (entries.size() > CACHED_COLUMNS) entries.removeFirst();
        }
    }

    private static List<Span> limit(List<Span> spans) {
        if (spans.size() <= HorizonTile.MAX_SPANS) return spans;
        // Preserve the thickest shelves and roof when many thin spans share a column.
        spans.sort(Comparator.comparingInt((Span span) -> span.top() - span.bottom()).reversed());
        var visible = new ArrayList<>(spans.subList(0, HorizonTile.MAX_SPANS));
        visible.sort(Comparator.comparingInt(Span::bottom));
        return visible;
    }

    private void appendSurface(List<Span> spans, int x, int z) {
        var site = sites == null ? null : sites.near(x, z);
        int canonicalX = site == null ? x : x - site.offsetX(), canonicalZ = site == null ? z : z - site.offsetZ();
        int localX = OrthIsland.localX(canonicalX), localZ = OrthIsland.localZ(canonicalZ);
        boolean legacy = OrthIsland.enabled(generator), legacySite = OrthIsland.natural(generator);
        boolean core = site != null ? site.core(new ChunkPos(x >> 4, z >> 4))
                : legacy && (legacySite ? OrthIsland.siteCore(localX, localZ) : OrthIsland.authored(localX, localZ));
        int sea = generator.getSeaLevel();
        if (core) {
            boolean terraced = site != null || OrthIsland.terraced(generator);
            boolean vertical = site != null || OrthIsland.vertical(generator);
            int surface = OrthIsland.elevation(localX, localZ, terraced);
            int floor = OrthTerrain.floor(localX, localZ, surface, terraced, vertical);
            if (vertical && OrthIsland.shaftColumn(localX, localZ)) {
                add(spans, height.getMinBuildHeight(), floor + 1, STONE);
                var source = generator.getBiomeSource() instanceof OrthBiomeSource orth ? orth : null;
                var layout = site != null || source != null && source.wildEdge() ? EdgeTerrain.Layout.WILD_EDGE
                        : source != null && source.wallTerraces() ? EdgeTerrain.Layout.WALL_TERRACES : EdgeTerrain.Layout.MOSS_PIT;
                appendEdge(spans, EdgeTerrain.column(canonicalX, canonicalZ, layout), OrthTerrain.SHARED_BOTTOM, 0);
            } else {
                int roof = terraced ? OrthCity.distantRoof(canonicalX, canonicalZ, floor) : floor;
                int color = roof > floor ? 0x805b44 : floor < sea ? 0x66664f : floor > 720 ? STONE
                        : OrthIsland.woodland(localX, localZ) > 0.15 ? 0x365633 : GRASS;
                add(spans, height.getMinBuildHeight(), roof + 1, color);
                if (roof < sea - 1) addWater(spans, sea, WATER);
                else if (roof == floor && floor < 720 && OrthIsland.woodland(localX, localZ) > 0.30) {
                    canopy(spans, roof + 1, roof + 13, 0x355632, true);
                }
            }
            return;
        }
        double weight = site != null || legacySite ? OrthIsland.siteWeight(localX, localZ) : 0;
        int solidTop = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, random);
        if (weight > 0) solidTop = OrthTerrain.coastFloor(canonicalX, canonicalZ, solidTop - 1) + 1;
        var biomeAtSurface = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(Math.max(solidTop, sea)),
                QuartPos.fromBlock(z), random.sampler());
        var biome = biomeAtSurface.value();
        boolean submerged = solidTop < sea;
        int color;
        if (solidTop > 114 && biome.coldEnoughToSnow(new BlockPos(x, solidTop, z))) color = 0xb1bec6;
        else if (submerged || biomeAtSurface.is(BiomeTags.IS_BEACH)) color = 0x77735d;
        else if (biome.getBaseTemperature() >= 1.5f && !biome.hasPrecipitation()) color = 0xb1a17b;
        else color = grassTint(biome.getGrassColor(x, z));
        add(spans, height.getMinBuildHeight(), solidTop, color);
        if (submerged) addWater(spans, sea, waterTint(biome.getWaterColor()));
        else if (color != 0xb1bec6 && (biomeAtSurface.is(BiomeTags.IS_FOREST) || biomeAtSurface.is(BiomeTags.IS_JUNGLE)
                || biomeAtSurface.is(BiomeTags.IS_TAIGA))) {
            int roll = (int) AtlasField.hash(seed, x, 691, z) & 15;
            canopy(spans, solidTop, solidTop + 6 + (roll & 7), foliageTint(biome.getFoliageColor()), roll >= 6);
        }
    }

    private static int grassTint(int color) { return tint(color, 0.60, 0.57, 0.52); }
    private static int foliageTint(int color) { return tint(color, 0.40, 0.42, 0.38); }

    private static int waterTint(int color) { return HorizonTile.waterColor(tint(color, 0.57, 0.58, 0.59)); }

    private static int tint(int rgb, double red, double green, double blue) {
        return (int) ((rgb >> 16 & 255) * red) << 16
                | (int) ((rgb >> 8 & 255) * green) << 8 | (int) ((rgb & 255) * blue);
    }

    private void appendEdge(List<Span> spans, EdgeTerrain.Column column, int min, int max) {
        if (!column.opening()) {
            add(spans, min, Math.min(max, column.upper() + 1), STONE);
            if (column.top() > column.upper() && column.top() < max) addWater(spans, column.top() + 1, WATER);
            return;
        }
        add(spans, min, Math.min(max, column.bottom() + 1), STONE);
        for (var rock : column.rocks()) {
            add(spans, Math.max(min, rock.bottom()), Math.min(max, rock.top() + 1), HorizonTile.layerColor(STONE, Layer.SCARP));
        }
        for (var shelf : column.shelves()) {
            int color = switch (shelf.cover()) {
                case MEADOW, FLOWERS, FERN, WOODLAND, WETLAND -> GRASS;
                default -> STONE;
            };
            add(spans, Math.max(min, shelf.bottom()), Math.min(max, shelf.top() + 1), HorizonTile.layerColor(color, Layer.SHELF));
            if (shelf.water() > shelf.top() && shelf.water() < max) addWater(spans, shelf.water() + 1, WATER);
        }
    }

    private void appendForest(List<Span> spans, ForestTerrain.Column column, int x, int z, int step, boolean approximateCanopy) {
        int minimum = height.getMinBuildHeight();
        add(spans, minimum, column.floor() + 1, 0x3c603b);
        if (column.floorWater() > column.floor()) addWater(spans, column.floorWater() + 1, WATER);
        if (column.shelfTop() >= minimum) {
            add(spans, column.shelfBottom(), column.shelfTop() + 1, HorizonTile.layerColor(0x4b7040, Layer.SHELF));
            if (column.water() > column.shelfTop()) addWater(spans, column.water() + 1, WATER);
        }
        if (column.ledgeTop() >= minimum) add(spans, column.ledgeBottom(), column.ledgeTop() + 1, HorizonTile.layerColor(0x415a3e, Layer.LEDGE));
        if (column.roofTop() >= minimum) add(spans, column.roofBottom(), column.roofTop() + 1, HorizonTile.layerColor(STONE, Layer.CEILING));
        for (var shelf : column.inlet()) {
            int offset = ForestMouth.OFFSET;
            Layer layer = shelf.top() > -1230 ? Layer.INLET_RIM : shelf.top() >= -1298 ? Layer.INLET_UPPER
                    : shelf.cover() == EdgeLandscape.Cover.PIT_MOSS ? Layer.INLET_LOWER : Layer.INLET_MIDDLE;
            add(spans, shelf.bottom() + offset, shelf.top() + offset + 1, HorizonTile.layerColor(GRASS, layer));
            if (shelf.water() > shelf.top()) addWater(spans, shelf.water() + offset + 1, WATER);
        }
        boolean cover = step > 16;
        if (!approximateCanopy || column.chute() || !cover && ((x & 15) != 0 || (z & 15) != 0)) return;
        int variant = (int) (AtlasField.hash(seed, x, 924, z) & 15);
        if (!cover && variant > 11) return;
        int tone = column.damp() ? 0x355a38 : 0x47673a;
        if (column.floorWater() <= column.floor()) {
            int top = column.floor() + 23 + variant * 2;
            if (column.shelfBottom() > column.floor()) top = Math.min(top, column.shelfBottom() - 5);
            if (column.ledgeBottom() > column.floor()) top = Math.min(top, column.ledgeBottom() - 5);
            if (top > column.floor() + 14) canopy(spans, column.floor() + 1, top, tone, cover);
        }
        if (column.shelfTop() >= minimum && column.water() <= column.shelfTop()) {
            int top = column.shelfTop() + 21 + variant * 2;
            if (column.roofBottom() > column.shelfTop()) top = Math.min(top, column.roofBottom() - 5);
            if (top > column.shelfTop() + 15) canopy(spans, column.shelfTop() + 1, top, tone, cover);
        }
    }

    private void canopy(List<Span> spans, int bottom, int top, int color, boolean cover) {
        add(spans, bottom, top, HorizonTile.layerColor(color, cover ? Layer.CANOPY_COVER : Layer.CANOPY));
    }

    private void appendRock(List<Span> spans, AbyssShape shape, int x, int z, int stride) {
        var column = shape.column(x, z);
        int start = height.getMinBuildHeight();
        boolean solid = false;
        int top = Math.min(height.getMaxBuildHeight(), column.roof() + 1);
        if (column.entrance() == null && column.radius() > shape.layer.width + 120) {
            add(spans, start, top, STONE);
            return;
        }
        for (int y = start; y < top; y += stride) {
            boolean occupied = !shape.block(column, y).isAir();
            if (occupied && !solid) start = y;
            else if (!occupied && solid) add(spans, start, y, STONE);
            solid = occupied;
        }
        if (solid) add(spans, start, top, STONE);
    }

    private void addWater(List<Span> spans, int y, int color) {
        add(spans, y - 1, y, color);
    }

    private void add(List<Span> spans, int bottom, int top, int color) {
        int from = Mth.clamp(bottom, height.getMinBuildHeight(), height.getMaxBuildHeight());
        int to = Mth.clamp(top, height.getMinBuildHeight(), height.getMaxBuildHeight());
        if (to > from) spans.add(new Span(from, to, color));
    }
}
