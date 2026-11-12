package dev.fantasytruth.abyss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Chunk-local placement keeps ledges, trees and landmarks deterministic across chunk borders. */
final class EdgeExploration {
    record Site(int x, int y, int z) {}
    record Landmark(String name, int depth, int radius, Kind kind) {
        EdgeLandscape.Point point() { return EdgeLandscape.point(depth, radius); }
    }
    enum Kind { CAMP, BURIAL, ARK, CAVE, MILL }

    static final List<Landmark> LANDMARKS = List.of(
            new Landmark("upper_camp", 53, 366, Kind.CAMP),
            new Landmark("root_hollow", 145, 447, Kind.CAVE),
            new Landmark("mill", 222, 344, Kind.MILL),
            new Landmark("burial", 300, 347, Kind.BURIAL),
            new Landmark("spring_shelter", 436, 448, Kind.CAVE),
            new Landmark("fern_camp", 515, 350, Kind.CAMP),
            new Landmark("ark", 600, 169, Kind.ARK),
            new Landmark("woodland_hollow", 700, 443, Kind.CAVE),
            new Landmark("lower_camp", 794, 349, Kind.CAMP),
            new Landmark("water_hollow", 906, 444, Kind.CAVE),
            new Landmark("fern_shelter", 988, 350, Kind.CAMP),
            new Landmark("chalk_hollow", 1081, 445, Kind.CAVE),
            new Landmark("deep_camp", 1172, 350, Kind.CAMP),
            new Landmark("last_camp", 1267, 348, Kind.CAMP));

    private interface Feature { void place(Painter painter); }

    static void place(ChunkAccess chunk, boolean forested) {
        if (!AbyssLayer.passage(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ(), 626)) return;
        var painter = new Painter(chunk);
        EdgeVegetation.understory(painter, forested);
        var features = forested ? Plan.Forested.FEATURES : Plan.Expanded.FEATURES;
        for (var feature : features.getOrDefault(chunk.getPos().toLong(), List.of())) feature.place(painter);
    }

    private static final class Plan {
        private static final class Expanded { static final Map<Long, List<Feature>> FEATURES = build(false); }
        private static final class Forested { static final Map<Long, List<Feature>> FEATURES = build(true); }

        private static Map<Long, List<Feature>> build(boolean forested) {
            Map<Long, List<Feature>> map = new HashMap<>();
            int limit = forested ? 448 : 400;
            int spacing = forested ? 15 : 17;
            for (int z = -limit; z <= limit; z += spacing) {
                for (int x = -limit; x <= limit; x += spacing) {
                    int key = hash(x, 0, z);
                    int bx = AbyssLayer.CENTER_X + x + Math.floorMod(key, 13) - 6;
                    int bz = AbyssLayer.CENTER_Z + z + Math.floorMod(key >>> 8, 13) - 6;
                    if (!AbyssLayer.passage(bx, bz, forested ? 444 : 368)) continue;
                    for (var span : EdgeTerrain.column(bx, bz, true, forested).shelves()) {
                        int roll = hash(bx, span.top(), bz);
                        if (!EdgeVegetation.treeHabitat(span.cover(), roll, forested) || nearLandmark(bx, span.top(), bz)
                                || (forested && (EdgeBasin.nearRoute(bx, span.top(), bz, 11)
                                || span.top() > -1215 && !AbyssLayer.passage(bx, bz, 369)))) continue;
                        var site = new Site(bx, span.top() + 1, bz);
                        int kind = EdgeVegetation.treeKind(span.cover(), roll);
                        add(map, site, 36, p -> EdgeVegetation.tree(p, site, kind, roll));
                    }
                }
            }
            for (int depth : new int[]{145, 515, 700, 988, 1172}) {
                var point = EdgeLandscape.point(depth, 266);
                var span = EdgeLandscape.at(point.x(), point.z(), depth, forested);
                if (span == null) continue;
                var site = new Site(point.x(), span.top() + 1, point.z());
                add(map, site, 49, p -> EdgeVegetation.tree(p, site, EdgeVegetation.ELDER, hash(site.x, site.y, site.z)));
            }
            if (forested) forestDetails(map);
            int trailEnd = forested ? EdgeBasin.ENTRY_DEPTH : EdgeLandscape.END;
            for (int depth = 18; depth < trailEnd; depth += 44) {
                var point = EdgeLandscape.point(depth, 422);
                var span = EdgeLandscape.at(point.x(), point.z(), depth, forested);
                if (span == null) continue;
                var site = new Site(point.x(), span.top() + 1, point.z());
                add(map, site, 2, p -> marker(p, site));
            }
            for (int depth : new int[]{135, 402, 426, 590, 884, 910, 1248}) waterfall(map, depth, forested);
            for (var landmark : LANDMARKS) branchPath(map, landmark, forested);
            for (var landmark : LANDMARKS) {
                var point = landmark.point();
                var ground = landmark.kind == Kind.CAVE ? EdgeLandscape.point(landmark.depth, 413) : point;
                var span = EdgeLandscape.at(ground.x(), ground.z(), landmark.depth, forested);
                if (span == null) continue;
                var site = new Site(point.x(), span.top() + 1, point.z());
                add(map, site, landmark.kind == Kind.ARK ? 58 : 37, p -> landmark(p, site, landmark));
            }
            var bottom = forested ? EdgeBasin.point(14) : EdgeLandscape.point(EdgeLandscape.END, 14);
            add(map, new Site(bottom.x(), -1345, bottom.z()), 4, p -> exit(p, bottom));
            map.replaceAll((chunk, list) -> List.copyOf(list));
            return Map.copyOf(map);
        }

        private static void forestDetails(Map<Long, List<Feature>> map) {
            for (int depth : new int[]{170, 250, 480, 681, 826, 969, 1125}) {
                var point = EdgeLandscape.point(depth, 284);
                var span = EdgeLandscape.at(point.x(), point.z(), depth, true);
                if (span == null || span.water() > span.top() || nearLandmark(point.x(), span.top(), point.z())) continue;
                var site = new Site(point.x(), span.top() + 1, point.z());
                add(map, site, 42, p -> EdgeVegetation.fallenTree(p, site, depth));
            }
            for (int i = 0; i < 10; i++) {
                double angle = 0.48 + i * Math.PI / 5;
                int radius = i % 2 == 0 ? 260 : 322;
                int x = AbyssLayer.CENTER_X + (int) Math.round(radius * Math.cos(angle));
                int z = AbyssLayer.CENTER_Z + (int) Math.round(radius * Math.sin(angle));
                var span = EdgeLandscape.at(x, z, EdgeBasin.ENTRY_DEPTH, true);
                if (span == null || EdgeBasin.nearRoute(x, span.top(), z, 40)) continue;
                var site = new Site(x, span.top() + 1, z);
                if (i % 3 == 0) add(map, site, 42, p -> EdgeVegetation.fallenTree(p, site, EdgeBasin.ENTRY_DEPTH));
                else add(map, site, 49, p -> EdgeVegetation.tree(p, site, EdgeVegetation.ELDER, hash(x, site.y, z)));
            }
            for (int radius : new int[]{345, 281, 212, 153, 109, 68, 29}) {
                var point = EdgeBasin.point(radius);
                var next = EdgeBasin.point(radius + 6);
                double dx = next.x() - point.x(), dz = next.z() - point.z();
                double scale = 8 / Math.max(1, Math.hypot(dx, dz));
                int x = point.x() - (int) Math.round(dz * scale);
                int z = point.z() + (int) Math.round(dx * scale);
                var span = EdgeLandscape.at(x, z, 1240 + Math.max(0, 171 - radius) * 2 / 3, true);
                if (span == null) continue;
                var site = new Site(x, span.top() + 1, z);
                add(map, site, 2, p -> marker(p, site));
            }
        }

        private static void branchPath(Map<Long, List<Feature>> map, Landmark landmark, boolean forested) {
            if (landmark.kind == Kind.CAVE) return;
            for (int radius = landmark.radius + 4; radius < 405; radius += 3) {
                var point = EdgeLandscape.point(landmark.depth, radius);
                var site = new Site(point.x(), -landmark.depth, point.z());
                add(map, site, 2, p -> {
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            if (dx * dx + dz * dz > 5) continue;
                            int x = site.x + dx, z = site.z + dz;
                            if (x < p.x0 || x > p.x0 + 15 || z < p.z0 || z > p.z0 + 15) continue;
                            var surface = EdgeLandscape.at(x, z, landmark.depth, forested);
                            if (surface == null || surface.water() > surface.top()) continue;
                            int y = surface.top();
                            p.set(x, y, z, Blocks.COARSE_DIRT.defaultBlockState());
                            p.column(x, z, y + 1, y + 2, Blocks.AIR.defaultBlockState());
                        }
                    }
                });
            }
        }

        private static void waterfall(Map<Long, List<Feature>> map, int depth, boolean forested) {
            BlockState falling = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
            for (int offset = -1; offset <= 1; offset++) {
                int level = depth + offset;
                for (int r = 380; r >= 150; r--) {
                    var lip = EdgeLandscape.point(level, r);
                    var bank = EdgeLandscape.at(lip.x(), lip.z(), level, forested);
                    if (bank == null) continue;
                    var point = EdgeLandscape.point(level, r - 2);
                    if (EdgeLandscape.at(point.x(), point.z(), level, forested) != null) continue;
                    var column = EdgeTerrain.column(point.x(), point.z(), true, forested);
                    int base = column.bottom();
                    for (var span : column.shelves()) if (span.top() < bank.top()) base = Math.max(base, span.water());
                    int foot = base + 1;
                    var site = new Site(point.x(), bank.top(), point.z());
                    add(map, site, 0, p -> p.column(site.x, site.z, foot, site.y, falling));
                    break;
                }
            }
        }

        private static void add(Map<Long, List<Feature>> map, Site site, int extent, Feature feature) {
            for (int cz = (site.z - extent) >> 4; cz <= (site.z + extent) >> 4; cz++) {
                for (int cx = (site.x - extent) >> 4; cx <= (site.x + extent) >> 4; cx++) {
                    map.computeIfAbsent(ChunkPos.asLong(cx, cz), pos -> new ArrayList<>()).add(feature);
                }
            }
        }
    }

    static boolean nearLandmark(int x, int y, int z) {
        int radius = (int) OrthIsland.distance(OrthIsland.localX(x), OrthIsland.localZ(z));
        for (var landmark : LANDMARKS) {
            if (Math.abs(y + landmark.depth) >= 45) continue;
            var point = landmark.point();
            if (Math.hypot(x - point.x(), z - point.z()) < 37) return true;
            if (radius > landmark.radius && radius < 404) {
                var trail = EdgeLandscape.point(landmark.depth, radius);
                if (Math.hypot(x - trail.x(), z - trail.z()) < 10) return true;
            }
        }
        return false;
    }

    private static void landmark(Painter p, Site s, Landmark landmark) {
        switch (landmark.kind) {
            case CAMP -> camp(p, s, landmark.depth);
            case BURIAL -> burial(p, s);
            case ARK -> ark(p, s, landmark);
            case CAVE -> cave(p, s, landmark.depth);
            case MILL -> mill(p, s);
        }
    }

    private static void marker(Painter p, Site s) {
        p.set(s.x, s.y, s.z, Blocks.COBBLESTONE_WALL.defaultBlockState());
        p.set(s.x, s.y + 1, s.z, Blocks.LANTERN.defaultBlockState());
    }

    private static void camp(Painter p, Site s, int depth) {
        p.box(s.x, s.y - 4, s.z, 8, 3, 6, Blocks.COBBLESTONE.defaultBlockState());
        p.box(s.x, s.y - 1, s.z, 8, 1, 6, Blocks.SPRUCE_PLANKS.defaultBlockState());
        p.box(s.x, s.y, s.z, 8, 5, 6, Blocks.AIR.defaultBlockState());
        for (int x : new int[]{-6, 6}) {
            for (int z : new int[]{-4, 4}) {
                p.column(s.x + x, s.z + z, s.y, s.y + 4, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            }
        }
        for (int x = -7; x <= 7; x++) {
            var cloth = (x + depth / 40) % 3 == 0 ? Blocks.WHITE_WOOL : Blocks.BROWN_WOOL;
            for (int z = -5; z <= 4; z++) p.set(s.x + x, s.y + 5 - Math.abs(x) / 3, s.z + z, cloth.defaultBlockState());
        }
        p.set(s.x - 4, s.y, s.z - 3, Blocks.CRAFTING_TABLE.defaultBlockState());
        p.set(s.x - 2, s.y, s.z - 3, Blocks.FURNACE.defaultBlockState());
        p.set(s.x, s.y, s.z + 5, Blocks.LANTERN.defaultBlockState());
        p.barrel(s.x + 4, s.y, s.z - 3, "edge_supplies");
    }

    private static void burial(Painter p, Site s) {
        p.box(s.x, s.y - 5, s.z, 13, 5, 11, Blocks.TUFF.defaultBlockState());
        p.box(s.x, s.y, s.z, 11, 7, 10, Blocks.AIR.defaultBlockState());
        for (int y = 0; y < 21; y++) {
            int width = 10 - y / 8;
            p.ring(s.x, s.y + y, s.z, width, width - 2, Blocks.STONE_BRICKS.defaultBlockState(),
                    Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), 5);
        }
        for (int y = 0; y < 5; y++) p.box(s.x, s.y + y, s.z - 8, 2, 1, 4, Blocks.AIR.defaultBlockState());
        p.disc(s.x, s.y + 21, s.z, 8, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
        p.disc(s.x, s.y + 22, s.z, 6, Blocks.TUFF.defaultBlockState());
        for (int x = -5; x <= 5; x += 5) {
            for (int z : new int[]{-3, 3}) {
                p.set(s.x + x, s.y, s.z + z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
                p.set(s.x + x, s.y + 1, s.z + z, Blocks.BONE_BLOCK.defaultBlockState());
                p.set(s.x + x, s.y + 2, s.z + z, Blocks.SKELETON_SKULL.defaultBlockState());
            }
        }
        p.set(s.x, s.y, s.z - 5, Blocks.LANTERN.defaultBlockState());
        p.barrel(s.x + 3, s.y, s.z + 5, "edge_relics");
    }

    private static void ark(Painter p, Site s, Landmark landmark) {
        var stem = EdgeLandscape.point(landmark.depth, landmark.radius + 47);
        double ax = stem.x() - s.x, az = stem.z() - s.z;
        double length = Math.hypot(ax, az);
        ax /= length;
        az /= length;
        for (int x = p.x0; x <= p.x0 + 15; x++) {
            for (int z = p.z0; z <= p.z0 + 15; z++) {
                double along = (x - s.x) * ax + (z - s.z) * az;
                double across = (z - s.z) * ax - (x - s.x) * az;
                double side = Math.abs(across - 3 * Math.sin(along * 0.11) - 2 * Math.sin(along * 0.24));
                if (along < -47 || along > 46) continue;
                double edge = (along < -10 ? 23 + (along + 10) * 0.55 : 23 - Math.max(0, along) * 0.36)
                        + 2.3 * Math.cos(along * 0.18) + 1.6 * Math.sin(along * 0.46);
                int grain = hash(x, 0, z) & 3;
                if (side > edge + grain - 1) continue;
                int y = arkTop(s, along, across, x, z);
                int thickness = 6 + (int) Math.min(31, (along + 47) * 0.50) - (int) (side * 0.16);
                int vein = (int) (3 * Math.sin(x * 0.15 + z * 0.20));
                for (int by = y - thickness; by < y; by++) {
                    var material = Math.floorMod(by + grain + vein, 13) < 3 ? Blocks.CALCITE : Blocks.TUFF;
                    p.set(x, by, z, material.defaultBlockState());
                }
                int plant = Math.floorMod(hash(x, y, z), 100);
                boolean turf = side < edge - 2 + 2 * Math.sin(along * 0.32 + across * 0.27);
                p.set(x, y, z, (turf ? Blocks.GRASS_BLOCK : plant < 62 ? Blocks.TUFF : Blocks.CALCITE).defaultBlockState());
                for (int clear = 1; clear <= 3; clear++) p.set(x, y + clear, z, Blocks.AIR.defaultBlockState());
                if (turf && plant < 36) {
                    var vegetation = plant < 26 ? Blocks.GRASS : plant < 31 ? Blocks.OXEYE_DAISY : Blocks.FERN;
                    p.grow(x, y + 1, z, vegetation.defaultBlockState());
                }
            }
        }
        p.barrel(s.x, arkTop(s, 0, 0, s.x, s.z) + 1, s.z, "edge_relics");
        var tip = EdgeLandscape.point(landmark.depth, landmark.radius - 32);
        double dx = tip.x() - s.x, dz = tip.z() - s.z;
        marker(p, new Site(tip.x(), arkTop(s, dx * ax + dz * az, dz * ax - dx * az, tip.x(), tip.z()) + 1, tip.z()));
    }

    private static int arkTop(Site s, double along, double across, int x, int z) {
        return s.y - 1 + (int) Math.floor((along + 47) / 26 + 0.82 * Math.sin(along * 0.17 + across * 0.18)
                + 0.46 * Math.sin(x * 0.31 - z * 0.22));
    }

    private static void cave(Painter p, Site s, int depth) {
        var entrance = EdgeLandscape.point(depth, 413);
        int floor = -depth;
        p.ellipsoid(s.x, floor + 5, s.z, 16, 10, 14, Blocks.TUFF.defaultBlockState());
        p.ellipsoid(s.x, floor + 5, s.z, 13, 7, 11, Blocks.AIR.defaultBlockState());
        p.box(s.x, floor - 3, s.z, 10, 3, 8, Blocks.MOSS_BLOCK.defaultBlockState());
        for (int i = 0; i <= 35; i++) {
            int x = entrance.x() + (s.x - entrance.x()) * i / 35;
            int z = entrance.z() + (s.z - entrance.z()) * i / 35;
            p.box(x, floor, z, 2, 4, 2, Blocks.AIR.defaultBlockState());
            p.disc(x, floor - 1, z, 2, Blocks.COBBLESTONE.defaultBlockState());
        }
        p.set(s.x + 5, floor, s.z, Blocks.LANTERN.defaultBlockState());
        p.set(s.x - 6, floor, s.z - 4, Blocks.MOSS_CARPET.defaultBlockState());
        switch (depth) {
            case 145 -> {
                var root = OrthBlocks.PETRIFIED_LOG.get().defaultBlockState();
                for (int i = -6; i <= 4; i++) {
                    p.column(s.x - 8 + i / 3, s.z + i, floor + 1 + Math.abs(i) / 3, floor + 7, root);
                }
                p.set(s.x + 3, floor + 9, s.z + 2, Blocks.SPORE_BLOSSOM.defaultBlockState());
            }
            case 436, 906 -> {
                p.disc(s.x - 5, floor - 2, s.z + 1, 4, Blocks.CLAY.defaultBlockState());
                p.disc(s.x - 5, floor - 1, s.z + 1, 4, Blocks.CLAY.defaultBlockState());
                p.disc(s.x - 5, floor - 1, s.z + 1, 3, Blocks.WATER.defaultBlockState());
                p.set(s.x - 9, floor - 1, s.z + 1, OrthBlocks.GLOW_MOSS.get().defaultBlockState());
                for (int i = -4; i <= 4; i += 2) {
                    p.set(s.x + i, floor + 8, s.z + 3, Blocks.DRIPSTONE_BLOCK.defaultBlockState());
                    p.set(s.x + i, floor + 7, s.z + 3, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN));
                }
            }
            case 700 -> {
                p.set(s.x - 4, floor, s.z + 4, Blocks.CRAFTING_TABLE.defaultBlockState());
                p.set(s.x - 2, floor, s.z + 4, Blocks.FURNACE.defaultBlockState());
                p.column(s.x - 5, s.z + 5, floor, floor + 5, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            }
            default -> {
                p.ellipsoid(s.x - 7, floor + 1, s.z + 4, 3, 2, 3, Blocks.CALCITE.defaultBlockState());
                p.ellipsoid(s.x + 7, floor + 1, s.z - 4, 2, 2, 3, Blocks.CALCITE.defaultBlockState());
                p.set(s.x - 3, floor, s.z + 4, Blocks.AMETHYST_BLOCK.defaultBlockState());
            }
        }
        if (depth == 145 || depth >= 900) {
            BlockState ore = OrthBlocks.RELIC_ORE.get().defaultBlockState();
            p.set(s.x - 8, floor - 1, s.z - 3, ore);
            p.set(s.x - 8, floor - 2, s.z - 3, ore);
        }
        p.barrel(s.x + 5, floor, s.z + 4, depth < 800 ? "edge_supplies" : "edge_relics");
    }

    private static void mill(Painter p, Site s) {
        p.box(s.x, s.y - 5, s.z, 8, 5, 8, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        p.box(s.x, s.y, s.z, 8, 6, 8, Blocks.AIR.defaultBlockState());
        for (int y = 0; y <= 22; y++) {
            int r = 7 - y / 9;
            p.ring(s.x, s.y + y, s.z, r, r - 1, Blocks.COBBLESTONE.defaultBlockState(),
                    Blocks.STONE_BRICKS.defaultBlockState(), 4);
        }
        p.box(s.x, s.y, s.z - 6, 1, 3, 2, Blocks.AIR.defaultBlockState());
        for (int y = 0; y < 7; y++) p.disc(s.x, s.y + 23 + y, s.z, 7 - y, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        for (int arm : new int[]{-1, 1}) {
            for (int d = 0; d <= 13; d++) {
                p.set(s.x + d * arm, s.y + 17 + d * arm, s.z - 8, Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState());
                p.set(s.x + d * arm, s.y + 17 - d * arm, s.z - 8, Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState());
                if (d > 3) {
                    p.set(s.x + d * arm, s.y + 17 + d * arm + 1, s.z - 8, Blocks.WHITE_WOOL.defaultBlockState());
                    p.set(s.x + d * arm, s.y + 17 - d * arm - 1, s.z - 8, Blocks.WHITE_WOOL.defaultBlockState());
                }
            }
        }
        p.set(s.x, s.y, s.z, Blocks.LANTERN.defaultBlockState());
        p.barrel(s.x + 2, s.y, s.z + 2, "edge_supplies");
    }

    private static void exit(Painter p, EdgeLandscape.Point point) {
        int x = point.x(), z = point.z();
        Direction outward = Math.abs(x - AbyssLayer.CENTER_X) > Math.abs(z - AbyssLayer.CENTER_Z)
                ? (x > AbyssLayer.CENTER_X ? Direction.EAST : Direction.WEST)
                : (z > AbyssLayer.CENTER_Z ? Direction.SOUTH : Direction.NORTH);
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, outward.getOpposite());
        p.column(x, z, EdgeTerrain.EXIT - 5, EdgeTerrain.EXIT + 7, ladder);
        p.column(x + outward.getStepX(), z + outward.getStepZ(), EdgeTerrain.EXIT - 5, EdgeTerrain.EXIT + 7,
                Blocks.COBBLESTONE.defaultBlockState());
    }

    static int hash(int x, int y, int z) {
        int h = x * 0x632be5ab ^ y * 0x9e3779b9 ^ z * 0x85157af5;
        h ^= h >>> 16;
        h *= 0x7feb352d;
        return h ^ (h >>> 15);
    }

    static final class Painter {
        final int x0, z0;
        private final ChunkAccess chunk;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        Painter(ChunkAccess chunk) {
            this.chunk = chunk;
            x0 = chunk.getPos().getMinBlockX();
            z0 = chunk.getPos().getMinBlockZ();
        }

        private boolean inside(int x, int y, int z) {
            return x >= x0 && x <= x0 + 15 && z >= z0 && z <= z0 + 15 && y > EdgeTerrain.MIN_Y && y < 0;
        }

        BlockState get(int x, int y, int z) { return chunk.getBlockState(pos.set(x, y, z)); }

        void set(int x, int y, int z, BlockState state) {
            if (inside(x, y, z)) chunk.setBlockState(pos.set(x, y, z), state, false);
        }

        void grow(int x, int y, int z, BlockState state) {
            if (inside(x, y, z) && get(x, y, z).isAir()) set(x, y, z, state);
        }

        void wood(int x, int y, int z, BlockState state) {
            if (!inside(x, y, z)) return;
            var current = get(x, y, z);
            if (current.canBeReplaced() || current.is(net.minecraft.tags.BlockTags.LEAVES)
                    || current.is(Blocks.GRASS_BLOCK) || current.is(Blocks.ROOTED_DIRT) || current.is(Blocks.PODZOL)
                    || current.is(Blocks.MOSS_BLOCK)) set(x, y, z, state);
        }

        void column(int x, int z, int lo, int hi, BlockState state) {
            if (x < x0 || x > x0 + 15 || z < z0 || z > z0 + 15) return;
            for (int y = lo; y <= hi; y++) set(x, y, z, state);
        }

        void box(int x, int y, int z, int rx, int h, int rz, BlockState state) {
            for (int bx = Math.max(x0, x - rx); bx <= Math.min(x0 + 15, x + rx); bx++) {
                for (int bz = Math.max(z0, z - rz); bz <= Math.min(z0 + 15, z + rz); bz++) {
                    column(bx, bz, y, y + h - 1, state);
                }
            }
        }

        void disc(int x, int y, int z, int radius, BlockState state) {
            ring(x, y, z, radius, 0, state, state, 1);
        }

        void ring(int x, int y, int z, int r, int inner, BlockState stone, BlockState alternate, int chance) {
            for (int bx = Math.max(x0, x - r); bx <= Math.min(x0 + 15, x + r); bx++) {
                for (int bz = Math.max(z0, z - r); bz <= Math.min(z0 + 15, z + r); bz++) {
                    int dx = bx - x, dz = bz - z, distance = dx * dx + dz * dz;
                    if (distance >= inner * inner && distance <= r * r) {
                        set(bx, y, bz, Math.floorMod(hash(bx, y, bz), chance) == 0 ? alternate : stone);
                    }
                }
            }
        }

        void ellipsoid(int x, int y, int z, int rx, int ry, int rz, BlockState state) {
            for (int bx = Math.max(x0, x - rx); bx <= Math.min(x0 + 15, x + rx); bx++) {
                for (int bz = Math.max(z0, z - rz); bz <= Math.min(z0 + 15, z + rz); bz++) {
                    double dx = (bx - x) / (double) rx, dz = (bz - z) / (double) rz;
                    double span = 1 - dx * dx - dz * dz;
                    if (span < 0) continue;
                    int half = (int) (ry * Math.sqrt(span));
                    column(bx, bz, y - half, y + half, state);
                }
            }
        }

        void barrel(int x, int y, int z, String loot) {
            if (!inside(x, y, z)) return;
            set(x, y, z, Blocks.BARREL.defaultBlockState());
            var tag = new CompoundTag();
            tag.putString("id", "minecraft:barrel");
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            tag.putString("LootTable", "stratacubes:chests/" + loot);
            tag.putLong("LootTableSeed", hash(x, y, z));
            chunk.setBlockEntityNbt(tag);
        }
    }

    private EdgeExploration() {}
}
