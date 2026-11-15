package dev.fantasytruth.abyss;

import java.util.Arrays;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Fills the authored land and deep sea without sampling three-dimensional vanilla noise. */
public final class OrthTerrain {
    private static final int COLUMN_COUNT = 256;
    private static final int SEA_SURFACE = OrthIsland.SEA - 1;
    private static final int SHAFT_FLOOR = -435;
    private static final int PREVIEW_BOTTOM = -224;
    static final int SHARED_BOTTOM = -480;

    public static boolean supports(ChunkAccess chunk) {
        if (!(chunk instanceof ProtoChunk) || chunk.isUpgrading()) return false;
        int x = OrthIsland.localX(chunk.getPos().getMinBlockX());
        int z = OrthIsland.localZ(chunk.getPos().getMinBlockZ());
        int edgeX = Math.max(Math.abs(x), Math.abs(x + 15));
        int edgeZ = Math.max(Math.abs(z), Math.abs(z + 15));
        return OrthIsland.authored(edgeX, edgeZ);
    }

    public static ChunkAccess fill(ChunkAccess chunk, boolean terraced, boolean vertical, boolean abyssRock) {
        return fill(chunk, terraced, vertical, abyssRock, false, false);
    }

    public static ChunkAccess fill(ChunkAccess chunk, boolean terraced, boolean vertical, boolean abyssRock, boolean wallTerraces, boolean wildEdge) {
        var oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        var worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState deep = Blocks.DEEPSLATE.defaultBlockState();
        BlockState islandRock = abyssRock ? OrthBlocks.STONE.get().defaultBlockState() : stone;
        BlockState water = Blocks.WATER.defaultBlockState();
        int[] floor = new int[COLUMN_COUNT];
        boolean[] ocean = new boolean[COLUMN_COUNT];
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        int highest = Integer.MIN_VALUE;
        boolean allOcean = true;
        boolean allIsland = true, anyIsland = false;
        boolean[] island = new boolean[COLUMN_COUNT];
        int x0 = OrthIsland.localX(chunk.getPos().getMinBlockX());
        int z0 = OrthIsland.localZ(chunk.getPos().getMinBlockZ());
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int i = (lz << 4) | lx;
                int x = x0 + lx, z = z0 + lz;
                int surface = OrthIsland.elevation(x, z, terraced);
                island[i] = abyssRock && surface >= OrthIsland.SEA;
                allIsland &= island[i];
                anyIsland |= island[i];
                int top = floor(x, z, surface, terraced, vertical);
                floor[i] = top;
                ocean[i] = top <= SEA_SURFACE && top == surface;
                allOcean &= ocean[i];
                minimum = Math.min(minimum, top);
                maximum = Math.max(maximum, top);
                int visibleTop = ocean[i] ? SEA_SURFACE : top;
                highest = Math.max(highest, visibleTop);
                oceanFloor.update(lx, top, lz, stone);
                worldSurface.update(lx, visibleTop, lz, ocean[i] ? water : stone);
            }
        }

        LevelChunkSection[] sections = chunk.getSections();
        int bottom = chunk.getMinBuildHeight();
        for (int index = 0, low = bottom; index < sections.length && low <= highest; index++, low += 16) {
            int high = low + 15;
            if (index > 0 && high <= minimum - 4 && (allIsland || !anyIsland)) {
                replace(sections, index, allIsland ? islandRock : high < 0 ? deep : stone);
            } else if (allOcean && low > maximum && high <= SEA_SURFACE) {
                replace(sections, index, Blocks.WATER.defaultBlockState());
            } else {
                fillMixed(sections[index], low, high, bottom, floor, ocean, island, islandRock);
            }
        }
        if (vertical && abyssRock) shareUpperShelves(chunk, x0, z0, wallTerraces, wildEdge);
        return chunk;
    }

    private static void shareUpperShelves(ChunkAccess chunk, int x0, int z0, boolean wallTerraces, boolean wildEdge) {
        // Project the upper ledges from the same terrain profile used in the Edge.
        if (Math.hypot(x0 + 8, z0 + 8) > 610) return;
        var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        var layout = wildEdge ? EdgeTerrain.Layout.WILD_EDGE
                : wallTerraces ? EdgeTerrain.Layout.WALL_TERRACES : EdgeTerrain.Layout.MOSS_PIT;
        int lower = wallTerraces ? SHARED_BOTTOM : PREVIEW_BOTTOM;
        for (int z = z0; z < z0 + 16; z++) {
            for (int x = x0; x < x0 + 16; x++) {
                if (!OrthIsland.shaftColumn(x, z)) continue;
                int wx = x + AbyssLayer.CENTER_X, wz = z + AbyssLayer.CENTER_Z;
                var column = EdgeTerrain.column(wx, wz, layout);
                for (var rock : column.rocks()) {
                    for (int y = Math.max(lower, rock.bottom()); y <= rock.top(); y++) {
                        chunk.setBlockState(pos.set(wx, y, wz), EdgeTerrain.scarpMaterial(rock, y, layout), false);
                    }
                }
                for (var span : column.shelves()) {
                    for (int y = Math.max(lower, span.bottom()); y <= span.water(); y++) {
                        var state = y <= span.top() ? EdgeTerrain.shelfMaterial(span, y, layout)
                                : Blocks.WATER.defaultBlockState();
                        chunk.setBlockState(pos.set(wx, y, wz), state, false);
                    }
                }
            }
        }
    }

    private static void replace(LevelChunkSection[] sections, int index, BlockState state) {
        // The palette's resize listener belongs to the container that created it.
        var blocks = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, state,
                PalettedContainer.Strategy.SECTION_STATES);
        sections[index] = new LevelChunkSection(blocks, sections[index].getBiomes());
    }

    private static void fillMixed(LevelChunkSection section, int low, int high, int bottom,
                                  int[] floor, boolean[] ocean, boolean[] island, BlockState islandRock) {
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState deep = Blocks.DEEPSLATE.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState gravel = Blocks.GRAVEL.defaultBlockState();
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int i = (lz << 4) | lx;
                int surface = floor[i];
                int top = ocean[i] ? SEA_SURFACE : surface;
                for (int y = low; y <= high && y <= top; y++) {
                    BlockState state;
                    if (y == bottom) state = bedrock;
                    else if (y > surface) state = water;
                    else if (y >= surface - 3 && ocean[i]) state = gravel;
                    else if (y == surface && y > OrthIsland.SEA) state = grass;
                    else if (y >= surface - 3 && y > OrthIsland.SEA - 3) state = dirt;
                    else state = island[i] ? islandRock : y < 0 ? deep : stone;
                    section.setBlockState(lx, y - low, lz, state, false);
                }
            }
        }
    }

    static int floor(int x, int z, int surface, boolean terraced, boolean vertical) {
        if (vertical) return OrthIsland.shaftColumn(x, z) ? -505 : surface;
        int edge = terraced ? 690 : 600;
        if (Math.abs(x) >= edge || Math.abs(z) >= edge) return surface;
        return (terraced ? Shaft.TERRACED : Shaft.LEGACY).surface(surface, OrthIsland.pitDistance(x, z, terraced));
    }

    private static final class Shaft {
        private static final Shaft LEGACY = new Shaft(false);
        private static final Shaft TERRACED = new Shaft(true);
        private static final int BAND = 16;
        private final double[] radii = new double[AbyssLayer.MAX_Y - SHAFT_FLOOR];
        private final double[] bandMinimum = new double[(radii.length + BAND - 1) / BAND];

        private Shaft(boolean terraced) {
            Arrays.fill(bandMinimum, Double.POSITIVE_INFINITY);
            for (int i = 0; i < radii.length; i++) {
                radii[i] = OrthIsland.pitRadius(i + SHAFT_FLOOR, terraced);
                bandMinimum[i / BAND] = Math.min(bandMinimum[i / BAND], radii[i]);
            }
        }

        int surface(int surface, double distance) {
            int i = surface - SHAFT_FLOOR;
            while (i > 0 && distance < radii[i]) {
                // An entire band can be skipped even where the ledges make the wall non-monotonic.
                int band = i / BAND;
                i = distance < bandMinimum[band] ? Math.max(0, band * BAND - 1) : i - 1;
            }
            return i + SHAFT_FLOOR;
        }
    }

    private OrthTerrain() {}
}
