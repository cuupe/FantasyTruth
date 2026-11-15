package dev.fantasytruth.abyss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/** Orth's districts are built a chunk at a time from a shared, immutable site plan. */
public final class OrthCity {
    private static final int LOT = 22;
    private static final int ORPHANAGE_X = -650, ORPHANAGE_Z = -118;
    private static final int GUILD_X = 642, GUILD_Z = -74;
    private static final int GATE_Z = -530;
    private static final int PIER_Z = 114;
    private static final int SPAWN_Z = -648;

    public static int spawnZ(boolean terraced) {
        return terraced ? OrthTownLayout.SPAWN_Z : SPAWN_Z;
    }

    public static boolean authored(ChunkPos chunk) {
        return OrthIsland.authored(OrthIsland.localX(chunk.getMiddleBlockX()),
                OrthIsland.localZ(chunk.getMiddleBlockZ()));
    }

    public static void generateSurface(ChunkAccess chunk, boolean terraced, boolean vertical) {
        generateSurface(chunk, terraced, vertical, false, false);
    }

    public static void generateSurface(ChunkAccess chunk, boolean terraced, boolean vertical, boolean forested, boolean abyssRock) {
        generateSurface(chunk, terraced, vertical, forested, abyssRock, false, false);
    }

    public static void generateSurface(ChunkAccess chunk, boolean terraced, boolean vertical, boolean forested,
                                       boolean abyssRock, boolean wallTerraces, boolean wildEdge) {
        generateSurface(chunk, terraced, vertical, forested, abyssRock,
                wildEdge ? EdgeTerrain.Layout.WILD_EDGE
                        : wallTerraces ? EdgeTerrain.Layout.WALL_TERRACES : EdgeTerrain.Layout.LEGACY);
    }

    static void generateSurface(ChunkAccess chunk, boolean terraced, boolean vertical, boolean forested, boolean abyssRock,
                                EdgeTerrain.Layout edgeLayout) {
        Painter p = new Painter(chunk, terraced, vertical, forested, abyssRock, edgeLayout, 0, 0, Direction.NORTH);
        if (Math.abs(p.x0) > 1740 || Math.abs(p.z0) > 1740) return;
        if (terraced) {
            if (OrthIsland.distance(p.x0 + 8, p.z0 + 8) > 1740) return;
            sculpt(p);
            plantTrees(p);
            if (vertical && abyssRock && chunk.getMinBuildHeight() != EdgeTerrain.MIN_Y && !edgeLayout.wallTerraces()) {
                plantShaftShelves(p);
            }
            for (Piece piece : TerracePlan.pieces(vertical).getOrDefault(chunk.getPos().toLong(), List.of())) {
                if (piece.surface != null) piece.surface.build(p);
            }
            if (vertical && abyssRock && chunk.getMinBuildHeight() != EdgeTerrain.MIN_Y && !edgeLayout.wallTerraces()) {
                OrthDescent.build(chunk, false, edgeLayout);
            }
            return;
        }
        for (int z = p.z0; z <= p.z1; z++) {
            for (int x = p.x0; x <= p.x1; x++) {
                int y = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x - p.x0, z - p.z0);
                double radius = OrthIsland.distance(x, z);
                if (radius < 550 && OrthIsland.pitDistance(x, z) < 510) {
                    if (y > -220) {
                        boolean moss = y > 160 && OrthIsland.woodland(x, z) > 0.43;
                        p.column(x, z, y - 3, y, ((y >> 3) & 3) == 1 ? OrthBlocks.CHALK.get() : OrthBlocks.STONE.get());
                        if (moss) p.set(x, y, z, Blocks.MOSS_BLOCK);
                        if (moss && (hash(x, z) & 15) == 0) p.set(x, y + 1, z, Blocks.MOSS_CARPET);
                    }
                    continue;
                }
                if (y <= OrthIsland.SEA || y > 400) continue;
                if (street(x, z, radius)) {
                    p.column(x, z, y - 1, y, OrthBlocks.COBBLE.get());
                    if (Math.floorMod(x, LOT) == 3 && Math.floorMod(z, LOT) == 3
                            && Math.floorMod(x + z - 6, LOT * 3) == 0) {
                        p.column(x, z, y + 1, y + 3, Blocks.SPRUCE_FENCE);
                        p.set(x, y + 4, z, Blocks.LANTERN);
                    }
                } else if (y < OrthIsland.SEA + 8) {
                    p.set(x, y, z, (hash(x >> 2, z >> 2) & 7) < 2 ? Blocks.SAND : Blocks.GRAVEL);
                } else if (p.block(x, y, z).is(Blocks.GRASS_BLOCK)) {
                    plantGroundCover(p, x, y, z, radius);
                }
            }
        }
        plantTrees(p);
        for (Piece piece : Plan.Legacy.PIECES.getOrDefault(chunk.getPos().toLong(), List.of())) {
            if (piece.surface != null) piece.surface.build(p);
        }
    }

    private static void plantShaftShelves(Painter p) {
        if (Math.hypot(p.x0 + 8, p.z0 + 8) > 575) return;
        for (int z = p.z0 - 6; z <= p.z1 + 6; z++) {
            for (int x = p.x0 - 6; x <= p.x1 + 6; x++) {
                int seed = hash(x, z);
                boolean tree = (seed & 63) == 0;
                boolean local = x >= p.x0 && x <= p.x1 && z >= p.z0 && z <= p.z1;
                if ((!tree && !local) || !OrthIsland.shaftColumn(x, z)) continue;
                int wx = x + AbyssLayer.CENTER_X, wz = z + AbyssLayer.CENTER_Z;
                for (var span : EdgeTerrain.column(wx, wz, EdgeTerrain.Layout.MOSS_PIT).shelves()) {
                    int y = span.top();
                    if (y >= -7 || y < -205 || span.water() > y) continue;
                    boolean green = switch (span.cover()) {
                        case MEADOW, FLOWERS, FERN, WOODLAND, WETLAND -> true;
                        default -> false;
                    };
                    if (!green) continue;
                    if (tree && OrthIsland.woodland(x, z) > -0.25) {
                        p.canopyTree(x, y + 1, z);
                    } else if (local && (seed & 3) == 1 && p.block(x, y + 1, z).isAir()) {
                        p.set(x, y + 1, z, (seed & 31) == 1 ? Blocks.OXEYE_DAISY : Blocks.FERN);
                    }
                }
            }
        }
    }

    public static void generateDetails(ChunkAccess chunk, boolean terraced, boolean vertical) {
        List<Piece> pieces = (terraced ? TerracePlan.pieces(vertical) : Plan.Legacy.PIECES).get(chunk.getPos().toLong());
        if (pieces == null) return;
        Painter p = new Painter(chunk, terraced, vertical);
        for (Piece piece : pieces) {
            if (piece.details != null) piece.details.build(p);
        }
    }

    private static void sculpt(Painter p) {
        int[] heights = new int[18 * 18];
        for (int z = 0; z < 18; z++) {
            for (int x = 0; x < 18; x++) {
                int bx = p.x0 + x - 1, bz = p.z0 + z - 1;
                heights[z * 18 + x] = OrthTerrain.floor(bx, bz, p.elevation(bx, bz), true, p.vertical);
            }
        }
        for (int z = p.z0; z <= p.z1; z++) {
            for (int x = p.x0; x <= p.x1; x++) {
                int i = (z - p.z0 + 1) * 18 + x - p.x0 + 1;
                int y = heights[i];
                int low = Math.min(Math.min(heights[i - 1], heights[i + 1]),
                        Math.min(heights[i - 18], heights[i + 18]));
                boolean shaft = OrthRelief.pitDistance(x, z) < (p.vertical ? 535 : 512);
                if (p.abyssRock && y > -505 && (shaft || y - low >= 7 && y >= OrthIsland.SEA)) {
                    int bottom = Math.max(y - 47, Math.min(y - 3, low));
                    p.column(x, z, bottom, y, OrthBlocks.STONE.get());
                    if (y > OrthIsland.SEA + 6 && y - low < 11 && OrthIsland.woodland(x, z) > 0.24) {
                        p.set(x, y, z, Blocks.MOSS_BLOCK);
                        plantGroundCover(p, x, y, z, OrthIsland.distance(x, z));
                    }
                    continue;
                }
                if (p.vertical && !shaft && y >= OrthIsland.SEA + 6) {
                    // Keep a turf cap and a vegetated soil face on the island's exterior slopes.
                    if (y - low >= 3) {
                        int bottom = Math.max(OrthIsland.SEA + 5, low);
                        boolean moss = OrthIsland.woodland(x, z) > -0.12;
                        p.column(x, z, bottom, y - 1, moss ? Blocks.MOSS_BLOCK : Blocks.ROOTED_DIRT);
                    }
                    p.set(x, y, z, Blocks.GRASS_BLOCK);
                    plantGroundCover(p, x, y, z, OrthIsland.distance(x, z));
                } else if (y > (p.vertical ? -505 : -380) && (y - low >= 3 || shaft)) {
                    int bottom = Math.max(y - 47, Math.min(y - 3, low));
                    int shift = (int) (13 * OrthIsland.woodland(x, z) + 5 * Math.sin(OrthRelief.turn(x, z) * 28));
                    for (int bed = bottom; bed <= y;) {
                        int height = Math.min(y, (Math.floorDiv(bed + shift, 11) + 1) * 11 - shift - 1);
                        Block rock = switch (Math.floorMod(Math.floorDiv(bed + shift, 11), 6)) {
                            case 1 -> OrthBlocks.CHALK.get();
                            case 2, 4 -> Blocks.ANDESITE;
                            case 3 -> Blocks.TUFF;
                            default -> OrthBlocks.STONE.get();
                        };
                        p.column(x, z, bed, height, rock);
                        bed = height + 1;
                    }
                    if (y > 135 && y - low < 6 && OrthIsland.woodland(x, z) > 0.28) {
                        p.set(x, y, z, Blocks.MOSS_BLOCK);
                    }
                } else if (y >= OrthIsland.SEA && y < OrthIsland.SEA + 10) {
                    p.set(x, y, z, (hash(x >> 2, z >> 2) & 7) < 4 ? Blocks.GRAVEL : Blocks.SAND);
                } else if (y >= OrthIsland.SEA + 10 && p.block(x, y, z).is(Blocks.GRASS_BLOCK)) {
                    plantGroundCover(p, x, y, z, OrthIsland.distance(x, z));
                }
            }
        }
    }

    private static void plantGroundCover(Painter p, int x, int y, int z, double radius) {
        int h = hash(x, z);
        double forest = OrthIsland.woodland(x, z);
        if (p.terraced) {
            if (forest > 0.28 && (h & 63) == 1) p.set(x, y, z, Blocks.MOSS_BLOCK);
            if ((h & 127) < (p.forested && radius > 840 ? 27 : 12)) {
                Block plant = (h & 511) == 0 ? Blocks.AZURE_BLUET : forest > 0.17 ? Blocks.FERN : Blocks.GRASS;
                p.set(x, y + 1, z, plant);
            }
            return;
        }
        if (radius > 983 && forest > 0.21 && (h & 63) < 6) {
            p.set(x, y, z, (h & 8) == 0 ? Blocks.ROOTED_DIRT : Blocks.PODZOL);
            if ((h & 7) == 0) p.set(x, y + 1, z, Blocks.FERN);
        } else if ((h & 63) < (radius > 983 ? 12 : 5)) {
            Block plant = switch ((h >>> 7) & 7) {
                case 0 -> Blocks.POPPY;
                case 1 -> Blocks.AZURE_BLUET;
                case 2 -> Blocks.FERN;
                default -> Blocks.GRASS;
            };
            p.set(x, y + 1, z, plant);
        }
    }

    private static void plantTrees(Painter p) {
        // A tree can contribute to any chunk within the radius of its crown.
        int spacing = p.forested ? 6 : 10;
        for (int row = Math.floorDiv(p.z0 - 7, spacing); row <= Math.floorDiv(p.z1 + 7, spacing); row++) {
            for (int col = Math.floorDiv(p.x0 - 7, spacing); col <= Math.floorDiv(p.x1 + 7, spacing); col++) {
                int h = hash(col, row);
                int x = col * spacing + 2 + (h & 3);
                int z = row * spacing + 2 + (h >>> 2 & 3);
                double radius = OrthIsland.distance(x, z);
                if (p.terraced ? OrthRelief.pitDistance(x, z) < 515
                        : radius < 529 || street(x, z, radius) || isLot(x, z)) continue;
                int y = p.elevation(x, z);
                if (y < OrthIsland.SEA + 14) continue;
                double forest = OrthIsland.woodland(x, z);
                if (p.terraced) {
                    int slope = p.forested ? 18 : 11;
                    if (Math.abs(p.elevation(x - 3, z) - p.elevation(x + 3, z)) > slope
                            || Math.abs(p.elevation(x, z - 3) - p.elevation(x, z + 3)) > slope) continue;
                    double turn = OrthRelief.turn(x, z);
                    double q = OrthRelief.townDistance(x, z);
                    boolean grove = q > 1040 || OrthRelief.ravine(turn) > 0.26
                            || q > 790 && turn > 0.15 && turn < 0.36;
                    if (p.forested) {
                        boolean woodland = q > 1080 || q > 900 && OrthRelief.ravine(turn) > 0.32
                                || q > 860 && turn > 0.15 && turn < 0.36;
                        if (woodland && forest > -0.41 && (forest > 0.12 || (h & 7) < 5)) {
                            if (y > 485 && (h & 3) != 0 || (h & 15) == 1) p.pine(x, y + 1, z);
                            else p.canopyTree(x, y + 1, z);
                        } else if ((h & 63) < 8) {
                            p.shrub(x, y + 1, z);
                        }
                    } else if (grove && forest > -0.19) {
                        if (y > 510 || (h & 8) == 0) p.pine(x, y + 1, z);
                        else p.tree(x, y + 1, z);
                    } else if ((h & 63) < 11) {
                        p.shrub(x, y + 1, z);
                    }
                } else if (radius > 984 && forest > -0.22 && (forest > 0.17 || (h & 48) == 16)) {
                    if (forest > 0.18 && (h & 64) == 0) p.pine(x, y + 1, z);
                    else p.tree(x, y + 1, z);
                } else if ((h & 63) < (radius > 984 ? 10 : 17)) {
                    p.shrub(x, y + 1, z);
                }
            }
        }
    }

    private static boolean street(int x, int z, double radius) {
        return (radius >= 519 && radius <= 984 && (road(x) || road(z)))
                || (Math.abs(x) <= 5 && z <= GATE_Z && z >= -1020);
    }

    private static boolean road(int value) {
        return Math.floorMod(value + 1, LOT) <= 2;
    }

    private static boolean isLot(int x, int z) {
        return occupied(Math.floorDiv(x, LOT) * LOT + LOT / 2, Math.floorDiv(z, LOT) * LOT + LOT / 2);
    }

    private static boolean occupied(int x, int z) {
        double r = OrthIsland.distance(x, z);
        if (r > 984 || OrthIsland.pitDistance(x, z) < 531) return false;
        if (Math.abs(x - ORPHANAGE_X) < 60 && Math.abs(z - ORPHANAGE_Z) < 65) return false;
        if (Math.abs(x - GUILD_X) < 95 && Math.abs(z - GUILD_Z) < 65) return false;
        if (Math.abs(x) < 62 && z < -495 && z > -710) return false;
        if (x < -430 && x > -630 && Math.abs(z - PIER_Z) < 32) return false;
        if (z > 510 && Math.abs(x) < 55) return false;
        int limit = r < 850 ? 99 : z > 0 ? 82 : 92;
        return Math.floorMod(hash(x, z), 100) < limit;
    }

    private static int hash(int x, int z) {
        int h = x * 0x632be5ab ^ z * 0x85157af5;
        h ^= h >>> 16;
        h *= 0x7feb352d;
        return h ^ h >>> 15;
    }

    private interface Design { void build(Painter p); }
    private record Piece(int x0, int z0, int x1, int z1, Design surface, Design details, Residence residence) {}

    record Residence(int x, int y, int z, int width, int depth, int floors, int style, Direction facing, boolean stilts) {
        BlockPos at(int dx, int dy, int dz) {
            int cos = -facing.getStepZ(), sin = facing.getStepX();
            return new BlockPos(AbyssLayer.CENTER_X + x + cos * dx - sin * dz, y + dy,
                    AbyssLayer.CENTER_Z + z + sin * dx + cos * dz);
        }

        void build(Painter painter) {
            Painter p = painter.rotated(x, z, facing);
            if (!stilts) {
                int front = z - depth - 1;
                p.terrace(x - 3, y, front, x + 3, front, OrthBlocks.COBBLE.get());
                if (Math.floorMod(hash(x, z), 19) == 0) {
                    // A small bell court opens off the approach at street level.
                    p.terrace(x - 4, y, front - 1, x + 1, front, OrthBlocks.COBBLE.get());
                    p.set(x - 3, y + 1, front - 1, Blocks.BELL);
                }
            }
            residence(p, x, y, z, width, depth, floors, style, stilts);
        }
    }

    private static Map<Long, List<Piece>> plan(boolean terraced, boolean vertical) {
        return terraced ? TerracePlan.pieces(vertical) : Plan.Legacy.PIECES;
    }

    static boolean developed(ChunkPos chunk, boolean terraced, boolean vertical) {
        return plan(terraced, vertical).containsKey(chunk.toLong());
    }

    static List<Residence> residences(ChunkPos chunk, boolean terraced, boolean vertical) {
        var pieces = plan(terraced, vertical).get(chunk.toLong());
        if (pieces == null) return List.of();
        var homes = new ArrayList<Residence>();
        for (Piece piece : pieces) {
            var home = piece.residence();
            if (home != null && chunk.equals(new ChunkPos(home.at(0, 1, 3)))) homes.add(home);
        }
        return homes;
    }

    private static final class Plan {
        private static final class Legacy {
            static final Map<Long, List<Piece>> PIECES = create();
        }

        private static Map<Long, List<Piece>> create() {
            Map<Long, List<Piece>> pieces = new HashMap<>();
            int first = Math.floorDiv(-984, LOT) * LOT + LOT / 2;
            for (int z = first; z <= 984; z += LOT) {
                for (int x = first; x <= 984; x += LOT) {
                    if (!occupied(x, z)) continue;
                    int h = hash(x, z);
                    int jitterX = (h >>> 11 & 3) - 1;
                    int jitterZ = (h >>> 13 & 3) - 1;
                    int width = Math.min(5 + (h & 3), 8 - Math.abs(jitterX));
                    int depth = Math.min(5 + (h >>> 2 & 3), 8 - Math.abs(jitterZ));
                    int floors = (z > 470 ? 1 : 2) + (h >>> 5 & 1);
                    int style = (h >>> 8) & 3;
                    int cx = x + jitterX;
                    int cz = z + jitterZ;
                    if (Math.floorMod(h, 37) == 0 && OrthIsland.pitDistance(cx, cz) > 550) {
                        int y = OrthIsland.elevation(cx, cz);
                        add(pieces, cx, cz, 13, 14, p -> mill(p, cx, y, cz));
                    } else {
                        int streetZ = z - LOT / 2 + 1;
                        int frontage = OrthIsland.elevation(cx, streetZ);
                        int frontZ = cz - depth;
                        int localGround = OrthIsland.elevation(cx, frontZ);
                        int rise = Math.min(4, Math.max(-3, localGround - frontage));
                        int y = frontage + rise;
                        var home = new Residence(cx, y, cz, width, depth, floors, style, Direction.NORTH, false);
                        add(pieces, cx, cz, width + 3, depth + 3,
                                p -> houseOutline(p, cx, y, cz, width, depth, floors, style),
                                home::build, home);
                        add(pieces, cx, (streetZ + frontZ - 1) / 2, 1, LOT / 2, null,
                                p -> approach(p, cx, streetZ, frontZ - 1, frontage, y));
                    }
                }
            }

            add(pieces, 0, -586, 53, 127, OrthCity::central);
            add(pieces, ORPHANAGE_X, ORPHANAGE_Z, 52, 49, OrthCity::orphanage);
            add(pieces, GUILD_X, GUILD_Z, 94, 50, OrthCity::guild);
            add(pieces, -507, PIER_Z, 123, 29, OrthCity::pier);
            addWharf(pieces, false, false);
            add(pieces, 0, 1315, 66, 285, OrthCity::harbor);
            pieces.replaceAll((key, list) -> List.copyOf(list));
            return Map.copyOf(pieces);
        }

        private static void addWharf(Map<Long, List<Piece>> pieces, boolean terraced, boolean vertical) {
            for (int x = -156; x <= 156; x += 26) {
                int lip = 510 - x * x / 1000;
                int crest = OrthIsland.elevation(x, lip + 42, terraced);
                for (int tier = 0; tier < 3; tier++) {
                    int y = crest - (terraced ? 28 + tier * 49 : 12 + tier * 17);
                    int z = lip - tier * 32;
                    if (terraced) {
                        int wall = 599;
                        double radius = vertical ? OrthIsland.SHAFT_RADIUS : OrthRelief.pitRadius(y);
                        while (wall > 352 && OrthRelief.pitDistance(x, wall) >= radius) wall--;
                        z = wall - 8 - tier * 7;
                    }
                    var home = new Residence(x, y, z, 8, 9, tier == 0 ? 2 : 1, (x / 26 + 8) % 4, Direction.NORTH, true);
                    add(pieces, x, z + 10, 13, 24, p -> wharf(p, home), null, home);
                }
            }
        }

        private static void add(Map<Long, List<Piece>> pieces, int x, int z, int dx, int dz, Design surface) {
            add(pieces, x, z, dx, dz, surface, null);
        }

        private static void add(Map<Long, List<Piece>> pieces, int x, int z, int dx, int dz,
                                Design surface, Design details) {
            add(pieces, x, z, dx, dz, surface, details, null);
        }

        private static void add(Map<Long, List<Piece>> pieces, int x, int z, int dx, int dz,
                                Design surface, Design details, Residence home) {
            Piece piece = new Piece(x - dx, z - dz, x + dx, z + dz, surface, details, home);
            for (int cx = (piece.x0 + AbyssLayer.CENTER_X) >> 4; cx <= (piece.x1 + AbyssLayer.CENTER_X) >> 4; cx++) {
                for (int cz = (piece.z0 + AbyssLayer.CENTER_Z) >> 4; cz <= (piece.z1 + AbyssLayer.CENTER_Z) >> 4; cz++) {
                    pieces.computeIfAbsent(ChunkPos.asLong(cx, cz), ignored -> new ArrayList<>()).add(piece);
                }
            }
        }
    }

    private static final class TerracePlan {
        private static final class Sloped {
            static final Map<Long, List<Piece>> PIECES = create(false);
        }
        private static final class Vertical {
            static final Map<Long, List<Piece>> PIECES = create(true);
        }

        static Map<Long, List<Piece>> pieces(boolean vertical) {
            return vertical ? Vertical.PIECES : Sloped.PIECES;
        }

        private static Map<Long, List<Piece>> create(boolean vertical) {
            Map<Long, List<Piece>> pieces = new HashMap<>();
            var survey = OrthTownLayout.survey();
            for (var road : survey.roads()) addRoad(pieces, road, false);
            for (var lot : survey.lots()) addRoad(pieces, lot.access(), true);
            for (var lot : survey.lots()) {
                int x = lot.x(), y = lot.y(), z = lot.z();
                if (lot.mill()) {
                    Plan.add(pieces, x, z, 14, 14, p -> mill(p.rotated(x, z, lot.facing()), x, y, z));
                } else {
                    var home = new Residence(x, y, z, lot.width(), lot.depth(), lot.floors(), lot.style(), lot.facing(), false);
                    Plan.add(pieces, x, z, lot.extentX() + 1, lot.extentZ() + 1,
                            p -> houseOutline(p.rotated(x, z, lot.facing()), x, y, z,
                                    lot.width(), lot.depth(), lot.floors(), lot.style()),
                            home::build, home);
                }
            }
            int spawn = OrthTownLayout.SPAWN_Z;
            int gate = OrthTownLayout.GATE_Z;
            Plan.add(pieces, 0, (gate + spawn) / 2, 63, (gate - spawn) / 2 + 50, OrthCity::central);
            Plan.add(pieces, OrthTownLayout.ORPHANAGE.x(), OrthTownLayout.ORPHANAGE.z(), 54, 51, OrthCity::orphanage);
            Plan.add(pieces, OrthTownLayout.GUILD.x(), OrthTownLayout.GUILD.z(), 96, 53, OrthCity::guild);
            Plan.add(pieces, -526, OrthTownLayout.PIER_Z, 158, 32, OrthCity::pier);
            Plan.addWharf(pieces, true, vertical);
            Plan.add(pieces, 0, OrthTownLayout.southShore() + 48, 68, 123, OrthCity::harbor);
            for (double turn : OrthTownLayout.FALLS) {
                for (int lane = -1; lane <= 1; lane++) {
                    addWaterfall(pieces, turn + lane * 0.0003, vertical);
                }
            }
            pieces.replaceAll((key, list) -> List.copyOf(list));
            return Map.copyOf(pieces);
        }

        private static void addRoad(Map<Long, List<Piece>> pieces, OrthTownLayout.Road road, boolean details) {
            var a = road.a();
            var b = road.b();
            int x = (a.x() + b.x()) / 2, z = (a.z() + b.z()) / 2;
            int rx = (Math.abs(a.x() - b.x()) + 1) / 2 + road.width() + 1;
            int rz = (Math.abs(a.z() - b.z()) + 1) / 2 + road.width() + 1;
            Design pave = p -> pave(p, road);
            Plan.add(pieces, x, z, rx, rz, details ? null : pave, details ? pave : null);
        }

        private static void addWaterfall(Map<Long, List<Piece>> pieces, double turn, boolean vertical) {
            for (int radius = 574; radius >= 415; radius--) {
                var at = OrthRelief.at(turn, radius);
                int top = OrthRelief.elevation(at.x(), at.z());
                int floor = OrthTerrain.floor(at.x(), at.z(), top, true, vertical);
                if (top - floor < 92) continue;
                Plan.add(pieces, at.x(), at.z(), 0, 0, p -> {
                    boolean edge = p.chunk.getMinBuildHeight() == EdgeTerrain.MIN_Y;
                    int start = vertical ? 0 : floor + 1;
                    if (vertical && (edge || p.edgeLayout.wallTerraces())) {
                        start = EdgeTerrain.column(at.x() + AbyssLayer.CENTER_X, at.z() + AbyssLayer.CENTER_Z,
                                p.edgeLayout).top() + 1;
                        if (!edge) start = Math.max(start, OrthTerrain.SHARED_BOTTOM);
                    }
                    p.column(at.x(), at.z(), start, top - 2,
                            Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8));
                });
                return;
            }
        }
    }

    private static void pave(Painter p, OrthTownLayout.Road road) {
        var a = road.a();
        var b = road.b();
        int w = road.width();
        int dx = b.x() - a.x(), dz = b.z() - a.z();
        int length = dx * dx + dz * dz;
        if (length == 0) return;
        for (int z = Math.max(p.z0, Math.min(a.z(), b.z()) - w); z <= Math.min(p.z1, Math.max(a.z(), b.z()) + w); z++) {
            for (int x = Math.max(p.x0, Math.min(a.x(), b.x()) - w); x <= Math.min(p.x1, Math.max(a.x(), b.x()) + w); x++) {
                double t = Mth.clamp(((x - a.x()) * dx + (z - a.z()) * dz) / (double) length, 0, 1);
                double offsetX = x - a.x() - t * dx, offsetZ = z - a.z() - t * dz;
                if (offsetX * offsetX + offsetZ * offsetZ > w * w) continue;
                int y = Mth.floor(Mth.lerp(t, a.y(), b.y()));
                int terrain = p.elevation(x, z);
                p.column(x, z, Math.max(y - 27, Math.min(terrain - 2, y - 3)), y, OrthBlocks.COBBLE.get());
                p.column(x, z, y + 1, Math.max(y + 3, Math.min(y + 35, terrain + 4)), Blocks.AIR);
            }
        }
    }

    private static void approach(Painter p, int x, int z0, int z1, int streetY, int doorY) {
        for (int z = z0; z <= z1; z++) {
            int y = streetY + (doorY - streetY) * (z - z0 + 1) / (z1 - z0 + 1);
            p.terrace(x - 1, y, z, x + 1, z, OrthBlocks.COBBLE.get());
        }
    }

    private static Block roofCover(int style) {
        return switch (style) {
            case 0 -> OrthBlocks.SLATE.get();
            case 1 -> Blocks.BRICKS;
            case 2 -> Blocks.MUD_BRICKS;
            default -> Blocks.DARK_OAK_PLANKS;
        };
    }

    private static void houseOutline(Painter p, int x, int y, int z, int w, int d, int floors, int style) {
        int roof = y + floors * 5 + 1;
        Block wall = style == 1 ? OrthBlocks.TIMBER.get() : OrthBlocks.PLASTER.get();
        p.shell(x - w, y - 1, z - d, x + w, y + 4, z + d, OrthBlocks.COBBLE.get());
        p.shell(x - w, y + 5, z - d, x + w, roof - 1, z + d, wall);
        roof(p, x, roof, z, w, d, style, wall);
    }

    private static void house(Painter p, int x, int y, int z, int w, int d, int floors, int style, boolean stilts) {
        house(p, x, y, z, w, d, floors, style, stilts, false);
    }

    private static void residence(Painter p, int x, int y, int z, int w, int d, int floors, int style, boolean stilts) {
        house(p, x, y, z, w, d, floors, style, stilts, true);
    }

    private static void house(Painter p, int x, int y, int z, int w, int d, int floors, int style,
                              boolean stilts, boolean residential) {
        int roof = y + floors * 5 + 1;
        boolean transverseRoof = style == 1 || style == 3;
        int slopeWidth = transverseRoof ? d : w;
        Block frame = style == 3 ? Blocks.DARK_OAK_PLANKS : Blocks.STRIPPED_SPRUCE_WOOD;
        Block wall = stilts ? Blocks.SPRUCE_PLANKS : style == 1 ? OrthBlocks.TIMBER.get() : OrthBlocks.PLASTER.get();
        p.fill(x - w, y + 1, z - d, x + w, roof + slopeWidth + 2, z + d, Blocks.AIR);
        if (stilts) {
            for (int dx : new int[]{-w, w}) {
                for (int dz : new int[]{-d, d}) p.column(x + dx, z + dz, y - 24, y, frame);
            }
        } else {
            for (int bz = Math.max(z - d, p.z0); bz <= Math.min(z + d, p.z1); bz++) {
                for (int bx = Math.max(x - w, p.x0); bx <= Math.min(x + w, p.x1); bx++) {
                    p.column(bx, bz, Math.min(y - 5, p.elevation(bx, bz) - 3), y - 1, OrthBlocks.COBBLE.get());
                }
            }
        }
        for (int floor = 0; floor < floors; floor++) {
            int base = y + floor * 5;
            p.fill(x - w, base, z - d, x + w, base, z + d, OrthBlocks.PLANK.get());
            p.shell(x - w, base + 1, z - d, x + w, base + 4, z + d, floor == 0 && !stilts ? OrthBlocks.COBBLE.get() : wall);
            p.shell(x - w, base + 4, z - d, x + w, base + 4, z + d, frame);
            for (int dx : new int[]{-w, w}) {
                for (int dz : new int[]{-d, d}) p.column(x + dx, z + dz, base + 1, base + 4, frame);
                p.column(x + dx, z, base + 2, base + 3, Blocks.GLASS);
            }
            for (int dx : new int[]{-w + 2, w - 2}) {
                for (int dz : new int[]{-d, d}) {
                    p.column(x + dx, z + dz, base + 2, base + 3, Blocks.GLASS);
                    p.set(x + dx, base + 1, z + dz + Integer.signum(dz), Blocks.SPRUCE_SLAB);
                }
            }
            if (floor > 0) {
                for (int dx : new int[]{-w / 2, 0, w / 2}) {
                    p.column(x + dx, z - d, base + 1, base + 3, frame);
                }
                p.column(x, z + d, base + 2, base + 3, Blocks.GLASS);
            }
            if (residential) continue;
            if (floor + 1 < floors) {
                p.column(x - w + 1, z + d - 2, base + 1, base + 4, Blocks.LADDER.defaultBlockState()
                        .setValue(LadderBlock.FACING, Direction.EAST));
            }
            if (floor > 0) p.column(x - w + 1, z + d - 2, base, base, Blocks.LADDER.defaultBlockState()
                    .setValue(LadderBlock.FACING, Direction.EAST));
            furnishHouse(p, x, base, z, w, d, floor, style);
            p.set(x, base + 1, z + d - 1, Blocks.LANTERN);
        }
        if (residential) OrthResidence.furnish(p, x, y, z, w, d, floors, style);
        p.door(x, y + 1, z - d, Direction.NORTH);
        p.set(x - 2, y + 1, z - d - 1, OrthBlocks.CRATE.get());
        p.set(x + 2, y + 1, z - d - 1, Blocks.COBBLESTONE_WALL);
        p.set(x + 2, y + 2, z - d - 1, Blocks.LANTERN);
        p.fill(x - 1, y + 3, z - d, x + 1, y + 3, z - d, frame);
        for (int side : new int[]{-1, 1}) {
            p.column(x + side, z - d, y + 1, y + 2, style == 0 ? Blocks.STONE_BRICKS : frame);
        }
        if (!stilts && floors >= 2 && w >= 6 && w <= 8 && style != 0) {
            int bayX = x + (style == 1 ? -w + 2 : w - 2);
            p.bayWindow(bayX, y + 5, z - d, wall, frame);
        }
        roof(p, x, roof, z, w, d, style, wall);
        if (transverseRoof) p.set(x - w, roof + 1, z, Blocks.BROWN_STAINED_GLASS);
        else p.set(x, roof + 1, z - d, Blocks.BROWN_STAINED_GLASS);
        int chimneyAlong = transverseRoof ? d - 1 : w - 2;
        int chimneyY = roof + roofRise(slopeWidth, chimneyAlong, style);
        p.column(x + w - 2, z + d - 1, roof - 1, chimneyY + 3, Blocks.BRICKS);
        p.set(x + w - 2, chimneyY + 4, z + d - 1, Blocks.BRICK_SLAB);
    }

    private static void furnishHouse(Painter p, int x, int y, int z, int w, int d, int floor, int style) {
        int right = x + w - 2, rear = z + d - 1;
        if (floor == 0) {
            p.set(right, y + 1, rear, style == 2 ? Blocks.SMOKER : Blocks.CRAFTING_TABLE);
            p.set(right - 1, y + 1, rear, OrthBlocks.CRATE.get());
            p.set(right, y + 2, rear, style == 1 ? Blocks.FLOWER_POT : Blocks.LANTERN);
            p.set(x - w + 1, y + 1, z - d + 2, style == 3 ? Blocks.LOOM : Blocks.BARREL);
            p.set(x - w + 1, y + 1, z - d + 3, Blocks.SPRUCE_STAIRS);
        } else {
            p.column(right, rear, y + 1, y + 2, Blocks.BOOKSHELF);
            p.set(right - 1, y + 1, rear, Blocks.LECTERN);
            p.set(x - w + 1, y + 1, z - d + 2, Blocks.SPRUCE_STAIRS);
            p.set(x - w + 1, y + 1, z - d + 3, Blocks.SPRUCE_SLAB);
            p.fill(x - 1, y + 1, z, x + 1, y + 1, z, style == 1 ? Blocks.RED_CARPET : Blocks.BROWN_CARPET);
        }
    }

    private static int roofRise(int span, int offset, int style) {
        int half = span + 1;
        if (style == 3) return (offset + half) / 2;
        int run = half - Math.abs(offset);
        return style == 2 ? run - run / 3 : style == 1 ? run + Math.max(0, 3 - Math.abs(offset)) / 2 : run;
    }

    private static void roof(Painter p, int x, int roof, int z, int w, int d, int style, Block wall) {
        boolean transverse = style == 1 || style == 3;
        int span = transverse ? d : w;
        Block stairs = switch (style) {
            case 0 -> Blocks.DEEPSLATE_TILE_STAIRS;
            case 1 -> Blocks.BRICK_STAIRS;
            case 2 -> Blocks.MUD_BRICK_STAIRS;
            default -> Blocks.DARK_OAK_STAIRS;
        };
        Block slab = switch (style) {
            case 0 -> Blocks.DEEPSLATE_TILE_SLAB;
            case 1 -> Blocks.BRICK_SLAB;
            case 2 -> Blocks.MUD_BRICK_SLAB;
            default -> Blocks.DARK_OAK_SLAB;
        };
        for (int offset = -span - 1; offset <= span + 1; offset++) {
            int top = roof + roofRise(span, offset, style);
            Direction uphill = transverse ? (offset < 0 || style == 3 ? Direction.SOUTH : Direction.NORTH)
                    : offset < 0 ? Direction.EAST : Direction.WEST;
            int next = offset + (offset < 0 || style == 3 ? 1 : -1);
            boolean incline = next <= span + 1 && (offset != 0 || style == 3)
                    && roofRise(span, next, style) > top - roof;
            BlockState tile = incline ? stairs.defaultBlockState().setValue(StairBlock.FACING, uphill)
                    : slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
            int length = transverse ? w : d;
            for (int along = -length - 1; along <= length + 1; along++) {
                int bx = x + (transverse ? along : offset), bz = z + (transverse ? offset : along);
                p.column(bx, bz, top, top, tile);
                if (incline && Math.abs(along) <= length && Math.abs(offset) <= span) {
                    p.column(bx, bz, top - 1, top - 1, roofCover(style));
                }
                if (Math.abs(along) == length && Math.abs(offset) <= span) {
                    p.column(bx, bz, roof, top - (incline ? 2 : 1), (offset & 3) == 0
                            ? Blocks.STRIPPED_SPRUCE_WOOD : wall);
                }
            }
        }
    }

    private static void central(Painter p) {
        int spawn = spawnZ(p.terraced);
        int gate = p.terraced ? OrthTownLayout.GATE_Z : GATE_Z;
        int y = p.elevation(0, spawn);
        p.terrace(-20, y, spawn - 12, 20, spawn + 13, OrthBlocks.COBBLE.get());
        int gateY = p.elevation(0, gate);
        for (int z = p.terraced ? gate - 15 : spawn + 14; z <= gate + (p.terraced ? 23 : 32); z++) {
            int grade = z < gate - 11 ? p.elevation(0, z) : gateY - Math.max(0, z - gate) / 2;
            p.terrace(-4, grade, z, 4, z, OrthBlocks.COBBLE.get());
        }
        // The gate is a rock-cut passage with dressed stones around the two mouths.
        for (int dz = -11; dz <= 10; dz++) {
            for (int dx = -25; dx <= 25; dx++) {
                int grain = Math.floorMod(hash(dx + gate, dz), 7);
                int top = gateY + 14 + (int) (16 * Math.sqrt(1 - dx * dx / 676.0))
                        + grain - Math.abs(dz) / 3;
                int width = dz < -7 || dz > 6 ? 10 : 8;
                int drop = Math.max(0, dz) / 2;
                int spring = gateY + 12 - drop;
                int bottom = Math.abs(dx) > width ? gateY - drop
                        : spring + (int) Math.sqrt((width + 1) * (width + 1) - dx * dx);
                p.column(dx, gate + dz, bottom, top, grain < 2 ? Blocks.TUFF : OrthBlocks.COBBLE.get());
                if (Math.abs(dx) <= width) p.column(dx, gate + dz, gateY + 1 - drop, bottom - 1, Blocks.AIR);
                if (Math.abs(dz) >= 9 && Math.abs(dx) <= width + 2) {
                    int base = Math.abs(dx) <= width ? bottom : gateY + 1 - drop;
                    p.column(dx, gate + dz, base, Math.min(base + (Math.abs(dx) <= width ? 2 : 19), top),
                            grain < 3 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
                }
                if (grain == 3) p.set(dx, top + 1, gate + dz, Blocks.MOSS_BLOCK);
            }
        }
        for (int side : new int[]{-1, 1}) {
            int bx = side * 14;
            p.fill(bx - 1, gateY, gate - 15, bx + 1, gateY + 10, gate - 9, Blocks.STONE_BRICKS);
            p.fill(bx - 2, gateY + 10, gate - 16, bx + 2, gateY + 10, gate - 12, Blocks.STONE_BRICK_SLAB);
            p.set(bx, gateY + 11, gate - 14, Blocks.LANTERN);
            for (int dz : new int[]{-7, 0, 7}) {
                int floor = gateY - Math.max(0, dz) / 2;
                p.set(side * 4, floor + 1, gate + dz, Blocks.STONE_BRICK_WALL);
                p.set(side * 4, floor + 2, gate + dz, Blocks.LANTERN);
            }
        }

        int station = p.terraced ? gate - 28 : -578;
        house(p, -29, p.elevation(-29, station), station, 12, 11, 3, 1, false);
        house(p, 29, p.elevation(29, station), station, 12, 11, 2, 0, false);
    }

    private static void orphanage(Painter p) {
        int x = p.terraced ? OrthTownLayout.ORPHANAGE.x() : ORPHANAGE_X;
        int z = p.terraced ? OrthTownLayout.ORPHANAGE.z() : ORPHANAGE_Z;
        int y = p.elevation(x, z - 17);
        p.terrace(x - 47, y, z - 29, x + 34, z + 24, OrthBlocks.COBBLE.get());
        house(p, x, y, z, 25, 16, 4, 2, false);
        p.fill(x - 26, y + 21, z - 17, x + 26, y + 49, z + 17, Blocks.AIR);
        for (int dz = -17; dz <= 17; dz++) {
            int rise = (int) (14 * Math.sqrt(1 - dz * dz / 324.0));
            for (int bx = Math.max(x - 26, p.x0); bx <= Math.min(x + 26, p.x1); bx++) {
                int moss = hash(bx / 4, (z + dz) / 3) & 7;
                Block cover = Math.abs(dz) + moss > 13 ? Blocks.MOSS_BLOCK
                        : moss == 1 ? Blocks.WEATHERED_CUT_COPPER : Blocks.OXIDIZED_CUT_COPPER;
                p.column(bx, z + dz, y + 20 + rise, y + 21 + rise, cover);
            }
            for (int end : new int[]{-25, 25}) {
                p.column(x + end, z + dz, y + 21, y + 19 + rise, OrthBlocks.PLASTER.get());
                if (rise > 8 && dz % 7 == 0) p.column(x + end, z + dz, y + 23, y + 26, Blocks.BROWN_STAINED_GLASS);
            }
        }
        int spring = y + 20 + (int) (14 * Math.sqrt(1 - 16 * 16 / 324.0));
        for (int side : new int[]{-1, 1}) {
            p.fill(x - 25, y + 21, z + side * 16, x + 25, spring - 1, z + side * 16, OrthBlocks.PLASTER.get());
            for (int dx : new int[]{-16, 0, 16}) {
                p.fill(x + dx - 2, y + 22, z + side * 16, x + dx + 2, y + 23, z + side * 16,
                        Blocks.BROWN_STAINED_GLASS);
                p.fill(x + dx - 1, y + 24, z + side * 16, x + dx + 1, y + 24, z + side * 16,
                        Blocks.BROWN_STAINED_GLASS);
                for (int bx = -5; bx <= 5; bx++) {
                    int top = y + 23 + (int) (4 * Math.sqrt(1 - bx * bx / 36.0));
                    p.fill(x + dx + bx, top - 1, z + side * 18 - 1, x + dx + bx, top, z + side * 18 + 1,
                            Math.abs(bx) == 5 ? Blocks.OXIDIZED_CUT_COPPER : Blocks.MOSS_BLOCK);
                }
            }
        }
        p.fill(x - 27, y + 1, z - 9, x - 24, y + 5, z - 3, Blocks.AIR);
        tower(p, x - 36, y, z - 6, 8, 45, true);
        p.fill(x - 30, y, z - 8, x - 25, y, z - 4, OrthBlocks.PLANK.get());
        p.fill(x - 30, y + 1, z - 8, x - 25, y + 4, z - 4, Blocks.AIR);
        p.fill(x - 6, y, z - 29, x + 6, y, z - 16, Blocks.STONE_BRICKS);
        for (int side : new int[]{-1, 1}) {
            for (int distance : new int[]{11, 18, 25, 32}) {
                int bx = x + side * distance;
                p.fill(bx - 2, y, z - 26, bx + 2, y, z - 22, Blocks.MOSS_BLOCK);
                if (distance != 18) p.shrub(bx, y + 1, z - 24);
                p.set(bx, y + 1, z - 28, Blocks.STONE_BRICK_WALL);
            }
        }
        p.fill(x - 3, y + 1, z - 16, x + 3, y + 4, z - 16, Blocks.AIR);
        p.fill(x - 4, y + 5, z - 17, x + 4, y + 5, z - 16, Blocks.SMOOTH_SANDSTONE);
        for (int side : new int[]{-1, 1}) {
            for (int dx : new int[]{5, 13, 21}) {
                int bx = x + side * dx;
                p.column(bx, z - 17, y + 1, y + 21, dx == 5 ? Blocks.STRIPPED_SPRUCE_WOOD : OrthBlocks.CHALK.get());
                for (int story = 0; story < 4; story++) {
                    int floor = y + story * 5;
                    p.fill(bx + side, floor + 2, z - 16, bx + side, floor + 3, z - 16, Blocks.BROWN_STAINED_GLASS);
                }
            }
            for (int dx : new int[]{10, 22, 34}) p.tree(x + side * dx, y + 1, z + 21);
        }
        for (int story = 0; story < 3; story++) {
            int floor = y + 5 * story;
            interiorStairs(p, x - 17 - 2 * (story % 2), floor, z - 4);
            p.fill(x + 8, floor + 1, z - 15, x + 8, floor + 4, z + 15, Blocks.SPRUCE_PLANKS);
            p.fill(x + 8, floor + 1, z - 2, x + 8, floor + 3, z + 2, Blocks.AIR);
        }
        for (int dx = -8; dx <= 4; dx += 4) {
            for (int dz = 5; dz <= 11; dz += 3) {
                roomTable(p, x + dx, y, z + dz);
                p.set(x + dx, y + 6, z + dz, Blocks.SPRUCE_STAIRS);
            }
        }
        for (int dx = 11; dx <= 21; dx += 3) {
            p.column(x + dx, z - 11, y + 6, y + 8, Blocks.BOOKSHELF);
            p.set(x + dx, y + 11, z + 12, ForestGrowth.Palette.block("camp_bedroll").getBlock());
        }
    }

    private static void interiorStairs(Painter p, int x, int y, int z) {
        BlockState stairs = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        for (int run = 0; run < 5; run++) {
            for (int side = 0; side < 2; side++) {
                p.column(x + side, z + run, y + run, y + run, stairs);
                p.column(x + side, z + run, y + run + 1, y + run + 3, Blocks.AIR);
            }
        }
    }

    private static void roomTable(Painter p, int x, int y, int z) {
        p.set(x, y + 1, z, Blocks.SPRUCE_FENCE);
        p.column(x, z, y + 2, y + 2, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        p.set(x - 1, y + 1, z, Blocks.SPRUCE_STAIRS);
        p.column(x + 1, z, y + 1, y + 1, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
    }

    private static void guild(Painter p) {
        int x = p.terraced ? OrthTownLayout.GUILD.x() : GUILD_X;
        int z = p.terraced ? OrthTownLayout.GUILD.z() : GUILD_Z;
        int y = p.elevation(x, z);
        p.terrace(x - 51, y, z - 33, x + 47, z + 30, OrthBlocks.COBBLE.get());
        house(p, x + 1, y, z, 17, 18, 4, 0, false);
        tower(p, x - 29, y, z + 9, 10, 43, true);
        tower(p, x + 30, y, z + 8, 12, 56, false);
        tower(p, x - 28, y, z - 18, 9, 57, false);
        tower(p, x + 29, y, z - 19, 9, 38, false);
        p.fill(x - 9, y + 1, z - 17, x + 9, y + 9, z + 6, Blocks.AIR);
        p.fill(x - 5, y + 1, z - 18, x + 5, y + 6, z - 18, Blocks.AIR);
        p.fill(x - 6, y + 7, z - 19, x + 6, y + 7, z - 18, Blocks.POLISHED_ANDESITE);
        p.fill(x - 2, y + 8, z - 18, x + 2, y + 13, z - 18, Blocks.YELLOW_STAINED_GLASS);
        for (int side : new int[]{-1, 1}) {
            for (int i = -24; i <= -12; i += 6) {
                p.column(x + 1 + side * 8, z + i, y + 1, y + 9, OrthBlocks.CHALK.get());
                p.set(x + 1 + side * 7, y + 2, z + i + 1, Blocks.LANTERN);
            }
            p.fill(x + side * 5 - 2, y + 1, z + 2, x + side * 5 + 2, y + 1, z + 4, OrthBlocks.TIMBER.get());
            p.set(x + side * 5, y + 2, z + 3, Blocks.LECTERN);
        }
        for (int story = 0; story < 3; story++) interiorStairs(p, x + ((story & 1) == 0 ? 11 : 14), y + 5 * story, z - 5);
        // High bridges connect occupied floors in the two rear towers to the registry.
        int bridge = y + 20;
        p.fill(x - 29, bridge, z + 7, x + 30, bridge, z + 11, OrthBlocks.PLANK.get());
        p.fill(x - 28, bridge + 1, z + 7, x + 29, bridge + 3, z + 11, Blocks.AIR);
        for (int side : new int[]{7, 11}) {
            p.fill(x - 26, bridge + 1, z + side, x - 17, bridge + 1, z + side, Blocks.SPRUCE_FENCE);
            p.fill(x + 18, bridge + 1, z + side, x + 27, bridge + 1, z + side, Blocks.SPRUCE_FENCE);
        }
        // Keep the internal tower climbing shafts continuous through the bridge deck.
        for (int[] tower : new int[][]{{x - 29, z + 9}, {x + 30, z + 8}}) {
            p.column(tower[0], tower[1] + 2, bridge, bridge + 3, Blocks.SPRUCE_LOG);
            p.column(tower[0], tower[1] + 1, bridge, bridge + 3,
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
        for (int bx = x + 48; bx <= x + 86; bx++) {
            int grade = y + (p.elevation(x + 86, z) - y) * (bx - x - 48) / 38;
            p.fill(bx, grade - 1, z - 3, bx, grade, z + 3, OrthBlocks.CHALK.get());
            if ((bx - x) % 12 == 0) p.fill(bx - 1, grade - 23, z - 2, bx + 1, grade - 2, z + 2, OrthBlocks.CHALK.get());
            for (int dz : new int[]{-3, 3}) p.set(bx, grade + 1, z + dz, Blocks.STONE_BRICK_WALL);
        }
    }

    private static void tower(Painter p, int x, int y, int z, int r, int h, boolean bell) {
        p.disc(x, y, z, r, 0, OrthBlocks.COBBLE.get());
        for (int j = 1; j <= h; j++) {
            int width = r - j / 23;
            p.disc(x, y + j, z, width + (j % 12 == 0 ? 1 : 0), width - 2,
                    j % 12 == 0 ? Blocks.MOSSY_STONE_BRICKS : OrthBlocks.CHALK.get());
            if (j % 6 == 0) p.disc(x, y + j, z, width - 1, 0, OrthBlocks.PLANK.get());
            if (j % 6 >= 2 && j % 6 <= 3) {
                p.fill(x, y + j, z - width, x, y + j, z - width + 2, Blocks.GLASS);
                p.fill(x + width - 2, y + j, z, x + width, y + j, z, Blocks.GLASS);
            }
        }
        p.fill(x - 1, y + 1, z - r, x + 1, y + 4, z - r + 3, Blocks.AIR);
        p.column(x, z + 2, y + 1, y + h, Blocks.SPRUCE_LOG);
        p.column(x, z + 1, y + 1, y + h, Blocks.LADDER.defaultBlockState()
                .setValue(LadderBlock.FACING, Direction.NORTH));
        int topRadius = r - h / 23;
        if (bell) {
            p.disc(x, y + h + 1, z, topRadius + 1, 0, OrthBlocks.SLATE.get());
            for (int side : new int[]{-1, 1}) {
                p.column(x + side * (topRadius - 1), z, y + h + 2, y + h + 8, Blocks.SPRUCE_LOG);
                p.column(x, z + side * (topRadius - 1), y + h + 2, y + h + 8, Blocks.SPRUCE_LOG);
            }
            p.column(x, z + 2, y + h + 1, y + h + 2, Blocks.SPRUCE_LOG);
            p.column(x, z + 1, y + h + 1, y + h + 2, Blocks.LADDER.defaultBlockState()
                    .setValue(LadderBlock.FACING, Direction.NORTH));
            p.set(x, y + h + 2, z, Blocks.BELL);
        }
        int cap = y + h + (bell ? 9 : 1);
        for (int j = topRadius + 1; j >= 1; j--) {
            p.disc(x, cap, z, j, Math.max(0, j - 2), OrthBlocks.SLATE.get());
            p.disc(x, cap + 1, z, j, Math.max(0, j - 2), OrthBlocks.SLATE.get());
            cap += 2;
        }
        p.column(x, z, cap, cap + 3, OrthBlocks.BOLT.get());
    }

    private static void mill(Painter p, int x, int y, int z) {
        tower(p, x, y, z + 4, 6, 24, false);
        int face = z - 4, axle = y + 20;
        p.fill(x, axle, face, x, axle, z + 4, OrthBlocks.BOLT.get());
        for (int arm = 0; arm < 8; arm++) {
            double angle = Math.PI * arm / 4;
            for (int r = 2; r <= 12; r++) {
                int dx = (int) Math.round(Math.cos(angle) * r);
                int dy = (int) Math.round(Math.sin(angle) * r);
                p.set(x + dx, axle + dy, face, Blocks.STRIPPED_SPRUCE_WOOD);
                if (r >= 5) {
                    p.set(x + dx - (int) Math.round(Math.sin(angle)), axle + dy + (int) Math.round(Math.cos(angle)),
                            face, OrthBlocks.SAIL.get());
                }
            }
        }
    }

    private static void pier(Painter p) {
        int z = p.terraced ? OrthTownLayout.PIER_Z : PIER_Z;
        int anchor = p.terraced ? OrthTownLayout.PIER_ANCHOR : -597;
        int hub = p.terraced ? OrthTownLayout.PIER_HUB : -415;
        int y = p.elevation(anchor, z);
        int start = anchor + 23;
        p.terrace(anchor - 25, y, z - 12, anchor + 27, z + 12, OrthBlocks.COBBLE.get());
        for (int side : new int[]{-1, 1}) {
            int bz = z + side * 8;
            p.fill(start - 18, y, bz - 3, start - 2, y, bz + 3, Blocks.SPRUCE_PLANKS);
            p.shell(start - 17, y + 1, bz - 3, start - 3, y + 5, bz + 3, OrthBlocks.PLASTER.get());
            p.fill(start - 18, y + 6, bz - 4, start - 2, y + 6, bz + 4, Blocks.DARK_OAK_SLAB);
            p.fill(start - 18, y + 7, bz - 2, start - 2, y + 7, bz + 2, Blocks.DEEPSLATE_TILES);
            p.door(start - 17, y + 1, bz, Direction.WEST);
            p.set(start - 8, y + 2, bz - side * 3, Blocks.BROWN_STAINED_GLASS);
            p.set(start - 11, y + 1, bz, Blocks.LECTERN);
            p.set(start - 7, y + 1, bz, Blocks.BARREL);
        }
        for (int x = start; x <= hub - 8; x++) {
            int grade = y - (x - start) / 8;
            int bay = (x - start) % 18;
            p.fill(x, grade, z - 4, x, grade, z + 4, OrthBlocks.PIER.get());
            p.fill(x, grade, z - 1, x, grade, z + 1, (x & 31) == 0 ? Blocks.TERRACOTTA : Blocks.RED_TERRACOTTA);
            for (int dz : new int[]{-4, 4}) {
                p.set(x, grade + 1, z + dz, Blocks.IRON_BARS);
                p.set(x, grade - 1 - Math.min(bay, 18 - bay), z + dz, Blocks.DEEPSLATE_TILES);
                p.set(x, grade - 2 - Math.min(bay, 18 - bay), z + dz, Blocks.DEEPSLATE_TILES);
                if (bay == 0) {
                    p.column(x, z + dz, grade - 9, grade + 5, OrthBlocks.BOLT.get());
                    p.set(x, grade + 6, z + dz, Blocks.LANTERN);
                }
            }
        }
        y -= (hub - start) / 8;
        p.disc(hub, y, z, 18, 0, OrthBlocks.PIER.get());
        p.disc(hub, y - 1, z, 18, 13, Blocks.DEEPSLATE_TILES);
        p.disc(hub, y + 1, z, 18, 17, Blocks.IRON_BARS);
        p.fill(hub - 18, y + 1, z - 3, hub - 15, y + 3, z + 3, Blocks.AIR);
        p.fill(hub - 17, y, z - 4, hub - 14, y, z + 4, OrthBlocks.PIER.get());
        p.disc(hub, y + 18, z, 12, 0, OrthBlocks.PIER.get());
        for (int h = 0; h < 18; h++) {
            int radius = h < 7 ? 4 : 4 + (h - 7) / 3;
            p.disc(hub + 2, y + h, z, radius, h == 0 ? 0 : radius - 2, h < 3 || h == 7 || h == 17
                    ? Blocks.CUT_COPPER : h < 8 ? Blocks.STRIPPED_SPRUCE_WOOD : Blocks.POLISHED_DEEPSLATE);
            if (h >= 3 && h <= 6) {
                p.fill(hub + 2 - radius, y + h, z - 1, hub + 2, y + h, z + 1, Blocks.AIR);
                p.fill(hub + 2, y + h, z - radius, hub + 3, y + h, z - 2, Blocks.BROWN_STAINED_GLASS);
            }
            if (h == 1 || h == 2) {
                p.fill(hub + 2 - radius, y + h, z - 1, hub, y + h, z + 1, Blocks.AIR);
            }
        }
        p.set(hub + 2, y + 1, z + 2, Blocks.LANTERN);
        for (int dx : new int[]{-8, 8}) {
            for (int dz : new int[]{-8, 8}) {
                p.column(hub + dx, z + dz, y - 24, y + 21, OrthBlocks.BOLT.get());
                p.set(hub + dx, y + 5, z + dz, Blocks.COPPER_BLOCK);
                for (int h = 0; h < 8; h++) {
                    p.set(hub + dx - Integer.signum(dx) * h, y + 18 - h, z + dz, Blocks.DEEPSLATE_TILES);
                    p.set(hub + dx, y - h, z + dz - Integer.signum(dz) * h, Blocks.DEEPSLATE_TILES);
                }
            }
        }
        for (int h = 0; h < 8; h++) {
            int radius = 13 - h * h / 7;
            p.disc(hub, y + 19 + h, z, radius, Math.max(0, radius - 2),
                    h == 0 ? Blocks.CUT_COPPER : Blocks.WEATHERED_CUT_COPPER);
        }
        p.disc(hub, y + 27, z, 5, 0, Blocks.OXIDIZED_CUT_COPPER);
        p.column(hub + 1, z, y - 24, y + 18, OrthBlocks.CABLE.get());
        p.fill(hub - 4, y - 31, z - 3, hub + 4, y - 31, z + 3, OrthBlocks.PLANK.get());
        p.fill(hub - 4, y - 24, z - 3, hub + 4, y - 24, z + 3, Blocks.CUT_COPPER);
        p.shell(hub - 4, y - 30, z - 3, hub + 4, y - 25, z + 3, Blocks.IRON_BARS);
        p.fill(hub - 4, y - 30, z - 1, hub - 4, y - 27, z + 1, Blocks.AIR);
        p.set(hub + 2, y - 30, z, Blocks.LANTERN);
        p.set(hub + 10, y + 1, z + 4, Blocks.BARREL);
        p.set(hub + 11, y + 1, z + 4, Blocks.GRINDSTONE);
    }

    private static void wharf(Painter p, Residence home) {
        int x = home.x(), y = home.y(), z = home.z();
        home.build(p);
        p.fill(x - 13, y, z - 13, x + 13, y, z - 9, OrthBlocks.PLANK.get());
        p.fill(x - 13, y + 1, z - 13, x + 13, y + 1, z - 13, Blocks.SPRUCE_FENCE);
        p.fill(x - 2, y, z + 10, x + 2, y, z + 34, OrthBlocks.PLANK.get());
    }

    private static void harbor(Painter p) {
        int shore = p.terraced ? OrthTownLayout.southShore() : 1441;
        int first = p.terraced ? shore - 45 : 1000;
        for (int z = first; z <= shore + 144; z++) {
            int y = Math.max(OrthIsland.SEA + 2, p.elevation(0, z));
            if (y > OrthIsland.SEA + 2) p.terrace(-4, y, z, 4, z, OrthBlocks.COBBLE.get());
            else {
                p.fill(-8, y, z, 8, y, z, OrthBlocks.PLANK.get());
                if (z % 12 == 0) {
                    p.column(-7, z, y - 24, y + 1, Blocks.SPRUCE_LOG);
                    p.column(7, z, y - 24, y + 1, Blocks.SPRUCE_LOG);
                }
            }
        }
        int y = OrthIsland.SEA + 2;
        for (int x : new int[]{-30, 30}) {
            int warehouse = shore - 51;
            int warehouseY = Math.max(OrthIsland.SEA + 2, p.elevation(x, warehouse));
            house(p, x, warehouseY, warehouse, 16, 12, 2, 1, warehouseY < OrthIsland.SEA + 8);
        }
        for (int z : new int[]{shore + 63, shore + 111, shore + 143}) {
            p.fill(-40, y, z - 4, 40, y, z + 4, OrthBlocks.PLANK.get());
            for (int x = -38; x <= 38; x += 9) {
                p.column(x, z + 3, y - 20, y + 1, Blocks.SPRUCE_LOG);
                p.set(x, y + 1, z - 1, OrthBlocks.CRATE.get());
            }
        }
    }

    static final class Painter {
        final ChunkAccess chunk;
        final boolean terraced, vertical, forested, abyssRock;
        final EdgeTerrain.Layout edgeLayout;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final int x0, x1, z0, z1;
        private final int pivotX, pivotZ, cos, sin;
        private final Rotation rotation;

        Painter(ChunkAccess chunk, boolean terraced, boolean vertical) {
            this(chunk, terraced, vertical, false, false, EdgeTerrain.Layout.LEGACY, 0, 0, Direction.NORTH);
        }

        private Painter(ChunkAccess chunk, boolean terraced, boolean vertical, boolean forested, boolean abyssRock,
                        EdgeTerrain.Layout edgeLayout, int pivotX, int pivotZ, Direction facing) {
            this.chunk = chunk;
            this.terraced = terraced;
            this.vertical = vertical;
            this.forested = forested;
            this.abyssRock = abyssRock;
            this.edgeLayout = edgeLayout;
            this.pivotX = pivotX;
            this.pivotZ = pivotZ;
            cos = -facing.getStepZ();
            sin = facing.getStepX();
            rotation = switch (facing) {
                case EAST -> Rotation.CLOCKWISE_90;
                case SOUTH -> Rotation.CLOCKWISE_180;
                case WEST -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };
            int x = OrthIsland.localX(chunk.getPos().getMinBlockX()) - pivotX;
            int z = OrthIsland.localZ(chunk.getPos().getMinBlockZ()) - pivotZ;
            int u = pivotX + cos * x + sin * z, v = pivotZ - sin * x + cos * z;
            int endX = u + (cos + sin) * 15, endZ = v + (cos - sin) * 15;
            x0 = Math.min(u, endX);
            x1 = Math.max(u, endX);
            z0 = Math.min(v, endZ);
            z1 = Math.max(v, endZ);
        }

        Painter rotated(int x, int z, Direction facing) {
            return facing == Direction.NORTH ? this : new Painter(chunk, terraced, vertical, forested, abyssRock, edgeLayout, x, z, facing);
        }

        private int worldX(int x, int z) {
            return pivotX + cos * (x - pivotX) - sin * (z - pivotZ);
        }

        private int worldZ(int x, int z) {
            return pivotZ + sin * (x - pivotX) + cos * (z - pivotZ);
        }

        int elevation(int x, int z) {
            return OrthIsland.elevation(worldX(x, z), worldZ(x, z), terraced);
        }

        BlockState block(int x, int y, int z) {
            return chunk.getBlockState(pos.set(worldX(x, z) + AbyssLayer.CENTER_X, y,
                    worldZ(x, z) + AbyssLayer.CENTER_Z));
        }

        void set(int x, int y, int z, Block block) { column(x, z, y, y, block); }

        void furnish(int x, int y, int z, Block block) {
            furnish(x, y, z, block.defaultBlockState());
        }

        void furnish(int x, int y, int z, BlockState state) {
            if (x < x0 || x > x1 || z < z0 || z > z1 || chunk.isOutsideBuildHeight(y)) return;
            column(x, z, y, y, state);
            if (state.getBlock() instanceof EntityBlock factory) {
                BlockPos placed = pos.immutable();
                var entity = factory.newBlockEntity(placed, chunk.getBlockState(placed));
                if (entity != null) chunk.setBlockEntityNbt(entity.saveWithFullMetadata());
            }
        }

        void column(int x, int z, int low, int high, Block block) {
            column(x, z, low, high, block.defaultBlockState());
        }

        void column(int x, int z, int low, int high, BlockState state) {
            if (x < x0 || x > x1 || z < z0 || z > z1) return;
            int bx = worldX(x, z) + AbyssLayer.CENTER_X;
            int bz = worldZ(x, z) + AbyssLayer.CENTER_Z;
            BlockState facing = rotation == Rotation.NONE ? state : state.rotate(rotation);
            for (int y = low; y <= high; y++) chunk.setBlockState(pos.set(bx, y, bz), facing, false);
        }

        void fill(int xa, int ya, int za, int xb, int yb, int zb, Block block) {
            BlockState state = block.defaultBlockState();
            for (int z = Math.max(za, z0); z <= Math.min(zb, z1); z++) {
                for (int x = Math.max(xa, x0); x <= Math.min(xb, x1); x++) column(x, z, ya, yb, state);
            }
        }

        void shell(int xa, int ya, int za, int xb, int yb, int zb, Block block) {
            fill(xa, ya, za, xb, yb, za, block);
            fill(xa, ya, zb, xb, yb, zb, block);
            fill(xa, ya, za + 1, xa, yb, zb - 1, block);
            fill(xb, ya, za + 1, xb, yb, zb - 1, block);
        }

        void terrace(int xa, int y, int za, int xb, int zb, Block block) {
            for (int z = Math.max(za, z0); z <= Math.min(zb, z1); z++) {
                for (int x = Math.max(xa, x0); x <= Math.min(xb, x1); x++) {
                    int terrain = elevation(x, z);
                    column(x, z, Math.min(y, terrain) - 4, y, block);
                    column(x, z, y + 1, Math.max(y + 8, terrain + 12), Blocks.AIR);
                }
            }
        }

        void disc(int x, int y, int z, int radius, int inner, Block block) {
            disc(x, y, z, radius, inner, block.defaultBlockState());
        }

        void disc(int x, int y, int z, int radius, int inner, BlockState state) {
            for (int bz = Math.max(z - radius, z0); bz <= Math.min(z + radius, z1); bz++) {
                for (int bx = Math.max(x - radius, x0); bx <= Math.min(x + radius, x1); bx++) {
                    int dx = bx - x, dz = bz - z, r2 = dx * dx + dz * dz;
                    if (r2 <= radius * radius && r2 >= inner * inner) column(bx, bz, y, y, state);
                }
            }
        }

        void door(int x, int y, int z, Direction direction) {
            BlockState lower = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, direction);
            column(x, z, y, y, lower);
            column(x, z, y + 1, y + 1, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }

        void bayWindow(int x, int y, int z, Block wall, Block frame) {
            fill(x - 2, y, z - 2, x + 2, y, z, frame);
            shell(x - 2, y + 1, z - 2, x + 2, y + 3, z, wall);
            fill(x - 1, y + 1, z, x + 1, y + 3, z, Blocks.AIR);
            fill(x - 1, y + 2, z - 2, x + 1, y + 2, z - 2, Blocks.GLASS);
            fill(x - 2, y + 4, z - 2, x + 2, y + 4, z, OrthBlocks.SLATE.get());
            for (int side : new int[]{-1, 1}) column(x + side * 2, z - 1, y - 2, y, Blocks.SPRUCE_FENCE);
        }

        void shrub(int x, int y, int z) {
            BlockState leaves = Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            disc(x, y, z, 2, 0, leaves);
            if ((hash(x, z) & 1) == 0) set(x, y + 1, z, Blocks.FLOWERING_AZALEA);
        }

        void tree(int x, int y, int z) {
            int h = hash(x, z);
            int height = 5 + (h & 3);
            boolean birch = (h & 16) == 0;
            column(x, z, y, y + height, birch ? Blocks.BIRCH_LOG : Blocks.OAK_LOG);
            BlockState leaves = (birch ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES)
                    .defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            int width = birch ? 3 : 4;
            for (int j = -3; j <= 2; j++) disc(x, y + height + j, z, Math.max(1, width - Math.abs(j)), j < 1 ? 1 : 0, leaves);
        }

        void pine(int x, int y, int z) {
            int height = 9 + (hash(x, z) & 7);
            column(x, z, y, y + height, Blocks.SPRUCE_LOG);
            BlockState leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            for (int j = 3; j <= height + 1; j++) {
                int radius = Math.min(4, (height + 1 - j) / 3 + (j & 1));
                disc(x, y + j, z, radius, j <= height ? 1 : 0, leaves);
            }
        }

        void canopyTree(int x, int y, int z) {
            int h = hash(x, z);
            boolean birch = (h & 7) < 2;
            boolean dark = !birch && (h & 31) > 24;
            int height = 7 + (h >>> 4 & 7);
            Block trunk = birch ? Blocks.BIRCH_LOG : dark ? Blocks.DARK_OAK_LOG : Blocks.OAK_LOG;
            BlockState leaves = (birch ? Blocks.BIRCH_LEAVES : dark ? Blocks.DARK_OAK_LEAVES : Blocks.OAK_LEAVES)
                    .defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            column(x, z, y, y + height, trunk);
            int width = birch ? 3 : 5;
            for (int j = -3; j <= 3; j++) disc(x, y + height + j, z, Math.max(1, width - Math.abs(j)), 0, leaves);
            int side = (h & 128) == 0 ? -1 : 1;
            if (!birch) {
                set(x + side, y + height - 3, z, trunk);
                set(x + side * 2, y + height - 2, z, trunk);
                disc(x + side * 2, y + height - 1, z, 4, 0, leaves);
                disc(x + side * 2, y + height, z, 3, 0, leaves);
            }
        }
    }

    private OrthCity() {}
}
