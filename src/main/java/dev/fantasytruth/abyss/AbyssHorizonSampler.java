package dev.fantasytruth.abyss;

import dev.fantasytruth.horizon.HorizonTile;
import dev.fantasytruth.horizon.HorizonTile.Span;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import static dev.fantasytruth.horizon.HorizonTile.GRASS;
import static dev.fantasytruth.horizon.HorizonTile.STONE;
import static dev.fantasytruth.horizon.HorizonTile.WATER;

/** Uses the same immutable terrain profiles as near generation, without opening chunks. */
public record AbyssHorizonSampler(ChunkGenerator generator, RandomState random, LevelHeightAccessor height) {
    public static AbyssHorizonSampler of(ServerLevel level) {
        var source = level.getChunkSource();
        var generator = source.getGenerator();
        if (!(generator instanceof AbyssEdgeGenerator || generator instanceof AbyssChunkGenerator
                || level.dimension() == Level.OVERWORLD && generator instanceof NoiseBasedChunkGenerator)) return null;
        return new AbyssHorizonSampler(generator, source.randomState(),
                LevelHeightAccessor.create(level.getMinBuildHeight(), level.getHeight()));
    }

    public HorizonTile.Data sample(HorizonTile tile) {
        int step = tile.step();
        var data = new ArrayList<List<Span>>(HorizonTile.SAMPLES * HorizonTile.SAMPLES);
        for (int z = 0; z < HorizonTile.SAMPLES; z++) {
            for (int x = 0; x < HorizonTile.SAMPLES; x++) {
                data.add(column(tile.x() + x * step, tile.z() + z * step, step));
            }
        }
        return new HorizonTile.Data(data);
    }

    private List<Span> column(int x, int z, int step) {
        var spans = new ArrayList<Span>(6);
        if (generator instanceof AbyssEdgeGenerator edge) {
            appendEdge(spans, EdgeTerrain.column(x, z, edge.layout()), height.getMinBuildHeight(), height.getMaxBuildHeight());
        } else if (generator instanceof AbyssChunkGenerator abyss) {
            if (abyss.openForest()) appendForest(spans, abyss.forest(random).column(x, z));
            else appendRock(spans, abyss.shape(random), x, z, Math.max(4, step / 8));
        } else {
            appendSurface(spans, x, z);
        }
        spans.sort(Comparator.comparingInt(Span::bottom));
        if (spans.size() <= HorizonTile.MAX_SPANS) return spans;
        // Preserve the thickest shelves and roof when many thin spans share a column.
        spans.sort(Comparator.comparingInt((Span span) -> span.top() - span.bottom()).reversed());
        var visible = new ArrayList<>(spans.subList(0, HorizonTile.MAX_SPANS));
        visible.sort(Comparator.comparingInt(Span::bottom));
        return visible;
    }

    private void appendSurface(List<Span> spans, int x, int z) {
        int localX = OrthIsland.localX(x), localZ = OrthIsland.localZ(z);
        boolean orth = OrthIsland.enabled(generator), natural = OrthIsland.natural(generator);
        int sea = generator.getSeaLevel();
        if (orth && (natural ? OrthIsland.siteCore(localX, localZ) : OrthIsland.authored(localX, localZ))) {
            int surface = OrthIsland.elevation(localX, localZ, OrthIsland.terraced(generator));
            int floor = OrthTerrain.floor(localX, localZ, surface, OrthIsland.terraced(generator), OrthIsland.vertical(generator));
            if (OrthIsland.vertical(generator) && OrthIsland.shaftColumn(localX, localZ)) {
                add(spans, height.getMinBuildHeight(), floor + 1, STONE);
                var source = (OrthBiomeSource) generator.getBiomeSource();
                var layout = source.wildEdge() ? EdgeTerrain.Layout.WILD_EDGE
                        : source.wallTerraces() ? EdgeTerrain.Layout.WALL_TERRACES : EdgeTerrain.Layout.MOSS_PIT;
                appendEdge(spans, EdgeTerrain.column(x, z, layout), OrthTerrain.SHARED_BOTTOM, 0);
            } else {
                int roof = OrthIsland.terraced(generator) ? OrthCity.distantRoof(x, z, floor) : floor;
                int color = roof > floor ? 0x805b44 : floor < sea ? WATER : floor > 720 ? STONE
                        : OrthIsland.woodland(localX, localZ) > 0.15 ? 0x4e6941 : GRASS;
                add(spans, height.getMinBuildHeight(), Math.max(sea, roof + 1), color);
            }
            return;
        }
        double weight = natural ? OrthIsland.siteWeight(localX, localZ) : 0;
        int top = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, random);
        int floor = top == sea ? generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, random) - 1 : top - 1;
        boolean water = floor + 1 < top;
        if (weight > 0) {
            int coast = OrthTerrain.coastFloor(x, z, floor);
            water = coast < sea;
            top = Math.max(coast + 1, sea);
        }
        int color = WATER;
        if (!water) {
            var biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(top),
                    QuartPos.fromBlock(z), random.sampler()).value();
            color = top > 160 && biome.coldEnoughToSnow(new net.minecraft.core.BlockPos(x, top, z))
                    ? 0xb9c4cb : biome.getGrassColor(x, z) & 0xffffff;
        }
        add(spans, height.getMinBuildHeight(), top, color);
    }

    private void appendEdge(List<Span> spans, EdgeTerrain.Column column, int min, int max) {
        if (!column.opening()) {
            add(spans, min, Math.min(max, column.top() + 1), column.top() > column.upper() ? WATER : STONE);
            return;
        }
        add(spans, min, Math.min(max, column.bottom() + 1), STONE);
        for (var rock : column.rocks()) {
            add(spans, Math.max(min, rock.bottom()), Math.min(max, rock.top() + 1), STONE);
        }
        for (var shelf : column.shelves()) {
            int color = shelf.water() > shelf.top() ? WATER : switch (shelf.cover()) {
                case MEADOW, FLOWERS, FERN, WOODLAND, WETLAND -> GRASS;
                default -> STONE;
            };
            add(spans, Math.max(min, shelf.bottom()), Math.min(max, shelf.water() + 1), color);
        }
    }

    private void appendForest(List<Span> spans, ForestTerrain.Column column) {
        int minimum = height.getMinBuildHeight();
        add(spans, minimum, column.floorWater() + 1, column.floorWater() > column.floor() ? WATER : 0x3c603b);
        if (column.shelfTop() >= minimum) {
            add(spans, column.shelfBottom(), Math.max(column.shelfTop(), column.water()) + 1,
                    column.water() > column.shelfTop() ? WATER : 0x4b7040);
        }
        if (column.ledgeTop() >= minimum) add(spans, column.ledgeBottom(), column.ledgeTop() + 1, 0x415a3e);
        if (column.roofTop() >= minimum) add(spans, column.roofBottom(), column.roofTop() + 1, STONE);
        for (var shelf : column.inlet()) {
            add(spans, shelf.bottom(), shelf.water() + 1, shelf.water() > shelf.top() ? WATER : GRASS);
        }
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

    private void add(List<Span> spans, int bottom, int top, int color) {
        int from = Mth.clamp(bottom, height.getMinBuildHeight(), height.getMaxBuildHeight());
        int to = Mth.clamp(top, height.getMinBuildHeight(), height.getMaxBuildHeight());
        if (to > from) spans.add(new Span(from, to, color));
    }
}
