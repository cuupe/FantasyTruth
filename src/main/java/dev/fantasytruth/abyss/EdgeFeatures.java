package dev.fantasytruth.abyss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Features are indexed by chunk so canopies and waterfalls agree across chunk borders. */
final class EdgeFeatures {
    private record Site(int x, int y, int z) {}
    private interface Feature { void build(Painter painter); }

    static void place(ChunkAccess chunk) {
        if (!AbyssLayer.passage(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ(), 630)) return;
        var features = Plan.BY_CHUNK.get(chunk.getPos().toLong());
        if (features == null) return;
        Painter painter = new Painter(chunk);
        for (Feature feature : features) feature.build(painter);
    }

    private static final class Plan {
        static final Map<Long, List<Feature>> BY_CHUNK = create();

        private static Map<Long, List<Feature>> create() {
            Map<Long, List<Feature>> map = new HashMap<>();
            for (int shelf = 0; shelf < EdgeTerrain.SHELVES.size(); shelf++) {
                var deck = EdgeTerrain.SHELVES.get(shelf);
                for (int z = -448; z <= 448; z += 57) {
                    for (int x = -448; x <= 448; x += 57) {
                        int hash = hash(x + shelf * 113, z);
                        int bx = AbyssLayer.CENTER_X + x + (hash & 15) - 7;
                        int bz = AbyssLayer.CENTER_Z + z + (hash >>> 4 & 15) - 7;
                        int ground = EdgeTerrain.ground(shelf, bx, bz);
                        if (ground == EdgeTerrain.NO_SHELF || (hash >>> 9 & 7) > 4) continue;
                        if (EdgeTerrain.ground(shelf, bx + 7, bz) == EdgeTerrain.NO_SHELF
                                || EdgeTerrain.ground(shelf, bx, bz + 7) == EdgeTerrain.NO_SHELF) continue;
                        var site = new Site(bx, ground + 1, bz);
                        int size = 19 + (hash >>> 14 & 31);
                        if (Math.abs(Math.IEEEremainder(Math.atan2(z, x) - deck.angle(), Math.PI * 2)) < 0.09
                                && AbyssLayer.passage(bx, bz, 366)) continue;
                        add(map, bx, bz, 33, p -> tree(p, site, size, hash));
                    }
                }
            }
            var elder = onShelf(0, -2.42, 314);
            add(map, elder.x, elder.z, 52, p -> tree(p, elder, 74, 12171));
            var mill = onShelf(0, -1.99, 354);
            add(map, mill.x, mill.z, 32, p -> windmill(p, mill));
            var camp = onShelf(0, -2.20, 264);
            add(map, camp.x, camp.z, 32, p -> camp(p, camp));
            var lowMill = onShelf(3, -0.56, 327);
            add(map, lowMill.x, lowMill.z, 32, p -> windmill(p, lowMill));
            waterfall(map, 0, -2.08);
            waterfall(map, 1, 0.42);
            waterfall(map, 2, 2.51);
            waterfall(map, 3, -0.83);
            waterfall(map, 5, 1.62);
            waterfall(map, 7, 2.53);
            map.replaceAll((pos, list) -> List.copyOf(list));
            return Map.copyOf(map);
        }

        private static Site onShelf(int shelf, double angle, int radius) {
            for (int r = radius; r <= 460; r += 3) {
                int x = x(angle, r), z = z(angle, r);
                int y = EdgeTerrain.ground(shelf, x, z);
                if (y != EdgeTerrain.NO_SHELF) return new Site(x, y + 1, z);
            }
            throw new IllegalStateException("Edge landmark lacks a shelf: " + shelf + " at " + angle);
        }

        private static void waterfall(Map<Long, List<Feature>> map, int shelf, double angle) {
            for (int lane = -2; lane <= 2; lane++) {
                double turn = angle + lane * 0.004;
                for (int r = 438; r > 130; r--) {
                    int x = x(turn, r), z = z(turn, r);
                    if (EdgeTerrain.ground(shelf, x, z) != EdgeTerrain.NO_SHELF) continue;
                    int lip = EdgeTerrain.ground(shelf, x(turn, r + 5), z(turn, r + 5));
                    if (lip == EdgeTerrain.NO_SHELF) continue;
                    var column = EdgeTerrain.column(x, z);
                    if (!column.opening()) continue;
                    int bottom = column.bottom() + 1;
                    for (var span : column.shelves()) {
                        if (span.top() < lip) bottom = Math.max(bottom, span.top() + 1);
                    }
                    int end = bottom;
                    BlockState fall = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
                    add(map, x, z, 0, p -> p.column(x, z, end, lip, fall));
                    break;
                }
            }
        }

        private static int x(double angle, double r) { return AbyssLayer.CENTER_X + (int) Math.round(Math.cos(angle) * r); }
        private static int z(double angle, double r) { return AbyssLayer.CENTER_Z + (int) Math.round(Math.sin(angle) * r); }

        private static void add(Map<Long, List<Feature>> map, int x, int z, int extent, Feature feature) {
            for (int cz = (z - extent) >> 4; cz <= (z + extent) >> 4; cz++) {
                for (int cx = (x - extent) >> 4; cx <= (x + extent) >> 4; cx++) {
                    map.computeIfAbsent(ChunkPos.asLong(cx, cz), pos -> new ArrayList<>()).add(feature);
                }
            }
        }
    }

    private static void tree(Painter p, Site site, int height, int hash) {
        int x = site.x, y = site.y, z = site.z;
        BlockState trunk = OrthBlocks.PETRIFIED_LOG.get().defaultBlockState();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockState moss = OrthBlocks.ABYSS_MOSS.get().defaultBlockState();
        int base = height >= 60 ? 7 : 2 + height / 18;
        for (int j = -5; j < height; j++) {
            int radius = Math.max(1, base - Math.max(j, 0) * base / height);
            p.disc(x, y + j, z, radius, trunk);
        }
        p.disc(x, y - 1, z, base + 2, moss);
        p.set(x + base + 1, y, z, OrthBlocks.GLOW_MOSS.get().defaultBlockState());
        for (int arm = 0; arm < 5; arm++) {
            double angle = arm * Math.PI * 2 / 5 + (hash & 7) * 0.14;
            int reach = height / 5 + 5 + (hash >>> (arm * 3) & 3);
            int branchY = y + height - 11 - (hash >>> (arm * 3 + 1) & 7);
            int bx = x, bz = z;
            for (int j = 0; j <= reach; j++) {
                bx = x + (int) Math.round(j * Math.cos(angle));
                bz = z + (int) Math.round(j * Math.sin(angle));
                p.disc(bx, branchY + j / 3, bz, j < reach / 2 ? 2 : 1, trunk);
            }
            crown(p, bx, branchY + reach / 3 + 3, bz, base + 5, leaves);
        }
        crown(p, x, y + height, z, base + 8, leaves);
    }

    private static void crown(Painter p, int x, int y, int z, int radius, BlockState leaves) {
        int depth = radius / 2;
        for (int j = -depth; j <= depth; j++) {
            int width = (int) Math.round(radius * Math.sqrt(1 - j * j / (double) ((depth + 1) * (depth + 1))));
            p.disc(x, y + j, z, width, leaves);
        }
    }

    private static void windmill(Painter p, Site site) {
        int x = site.x, y = site.y, z = site.z;
        BlockState stone = OrthBlocks.MILL_STONE.get().defaultBlockState();
        BlockState slate = OrthBlocks.SLATE.get().defaultBlockState();
        for (int j = -9; j < 35; j++) {
            int width = 11 - Math.max(j, 0) / 12;
            p.ring(x, y + j, z, width, j < 0 || j % 8 == 0 ? 0 : width - 2, stone);
        }
        for (int j = 0; j < 11; j++) p.disc(x, y + 35 + j, z, 11 - j, slate);
        p.column(x, z - 10, y + 1, y + 4, Blocks.AIR.defaultBlockState());
        for (int wing = 0; wing < 4; wing++) {
            double angle = Math.PI * 0.25 + wing * Math.PI * 0.5;
            for (int reach = 0; reach < 24; reach++) {
                int bx = x + (int) Math.round(reach * Math.cos(angle));
                int by = y + 28 + (int) Math.round(reach * Math.sin(angle));
                p.set(bx, by, z - 12, Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState());
                if (reach > 4) {
                    for (int span = 1; span <= 3; span++) {
                        p.set(bx - (int) Math.round(span * Math.sin(angle)),
                                by + (int) Math.round(span * Math.cos(angle)), z - 12,
                                OrthBlocks.SAIL.get().defaultBlockState());
                    }
                }
            }
        }
    }

    private static void camp(Painter p, Site site) {
        int x = site.x, y = site.y, z = site.z;
        BlockState plank = OrthBlocks.PLANK.get().defaultBlockState();
        for (int dz = -9; dz <= 9; dz++) {
            for (int dx = -13; dx <= 13; dx++) {
                p.column(x + dx, z + dz, y - 5, y - 1, plank);
            }
        }
        for (int offset : new int[]{-7, 7}) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dx = -4; dx <= 4; dx++) {
                    p.set(x + offset + dx, y + 5 - Math.abs(dx), z + dz, Blocks.BROWN_WOOL.defaultBlockState());
                }
            }
            p.set(x + offset, y, z + 3, OrthBlocks.CAMP_CRATE.get().defaultBlockState());
        }
        p.set(x, y, z, Blocks.LANTERN.defaultBlockState());
        p.set(x + 3, y, z + 3, OrthBlocks.CAMP_CRATE.get().defaultBlockState());
    }

    private static int hash(int x, int z) {
        int h = x * 0x632be5ab ^ z * 0x85157af5;
        h ^= h >>> 16;
        h *= 0x7feb352d;
        return h ^ (h >>> 15);
    }

    private static final class Painter {
        private final ChunkAccess chunk;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private final int x0, x1, z0, z1;

        Painter(ChunkAccess chunk) {
            this.chunk = chunk;
            x0 = chunk.getPos().getMinBlockX();
            z0 = chunk.getPos().getMinBlockZ();
            x1 = x0 + 15;
            z1 = z0 + 15;
        }

        void set(int x, int y, int z, BlockState state) { column(x, z, y, y, state); }

        void column(int x, int z, int lo, int hi, BlockState state) {
            if (x < x0 || x > x1 || z < z0 || z > z1) return;
            for (int y = lo; y <= hi; y++) chunk.setBlockState(pos.set(x, y, z), state, false);
        }

        void disc(int x, int y, int z, int r, BlockState state) { ring(x, y, z, r, 0, state); }

        void ring(int x, int y, int z, int r, int inner, BlockState state) {
            for (int bz = Math.max(z0, z - r); bz <= Math.min(z1, z + r); bz++) {
                for (int bx = Math.max(x0, x - r); bx <= Math.min(x1, x + r); bx++) {
                    int dx = bx - x, dz = bz - z, squared = dx * dx + dz * dz;
                    if (squared >= inner * inner && squared <= r * r) set(bx, y, bz, state);
                }
            }
        }
    }

    private EdgeFeatures() {}
}
