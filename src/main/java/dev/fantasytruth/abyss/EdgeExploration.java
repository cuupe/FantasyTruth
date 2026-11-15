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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Chunk-local placement keeps ledges, trees and landmarks deterministic across chunk borders. */
final class EdgeExploration {
    record Site(int x, int y, int z) {}
    record Landmark(String name, int depth, int radius, Kind kind) {
        EdgeLandscape.Point point() { return EdgeLandscape.point(depth, radius); }
    }
    enum Kind { CAMP, BURIAL, ARK, CAVE, MILL, FOSSIL_GROVE }

    static final List<Landmark> LANDMARKS = List.of(
            new Landmark("upper_camp", 53, 366, Kind.CAMP),
            new Landmark("root_hollow", 145, 447, Kind.CAVE),
            new Landmark("fossil_grove", 171, 374, Kind.FOSSIL_GROVE),
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

    static List<Landmark> landmarks(EdgeTerrain.Layout layout) {
        return layout.mossPit ? LANDMARKS.subList(0, LANDMARKS.size() - 1) : LANDMARKS;
    }

    static void place(ChunkAccess chunk, EdgeTerrain.Layout layout) {
        if (!AbyssLayer.passage(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ(), 626)) return;
        int bottom = chunk.getMinBuildHeight() == EdgeTerrain.MIN_Y ? EdgeTerrain.MIN_Y + 1 : OrthTerrain.SHARED_BOTTOM;
        var painter = new Painter(chunk, 0, bottom, layout);
        EdgeVegetation.understory(painter, layout);
        var features = switch (layout) {
            case MOSS_PIT -> Plan.MossPit.FEATURES;
            case ERODED -> Plan.Eroded.FEATURES;
            case FOREST_MOUTH -> Plan.ForestEntrance.FEATURES;
            case WALL_TERRACES -> Plan.WallTerraces.FEATURES;
            case WILD_EDGE -> Plan.WildEdge.FEATURES;
            case FORESTED -> Plan.Forested.FEATURES;
            default -> Plan.Expanded.FEATURES;
        };
        for (var feature : features.getOrDefault(chunk.getPos().toLong(), List.of())) feature.place(painter);
        if (layout.forestMouth()) {
            if (AbyssLayer.passage(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ(), 234)) {
                ForestMouth.grow(painter);
            }
        } else if (layout.mossPit && AbyssLayer.passage(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ(), 80)) {
            EdgeBasin.growEntrance(painter);
        }
    }

    private static final class Plan {
        private static final class Expanded { static final Map<Long, List<Feature>> FEATURES = build(EdgeTerrain.Layout.EXPANDED); }
        private static final class Forested { static final Map<Long, List<Feature>> FEATURES = build(EdgeTerrain.Layout.FORESTED); }
        private static final class MossPit { static final Map<Long, List<Feature>> FEATURES = build(EdgeTerrain.Layout.MOSS_PIT); }
        private static final class Eroded { static final Map<Long, List<Feature>> FEATURES = build(EdgeTerrain.Layout.ERODED); }
        private static final class ForestEntrance { static final Map<Long, List<Feature>> FEATURES = build(EdgeTerrain.Layout.FOREST_MOUTH); }
        private static final class WallTerraces { static final Map<Long, List<Feature>> FEATURES = build(EdgeTerrain.Layout.WALL_TERRACES); }
        private static final class WildEdge { static final Map<Long, List<Feature>> FEATURES = build(EdgeTerrain.Layout.WILD_EDGE); }

        private static Map<Long, List<Feature>> build(EdgeTerrain.Layout layout) {
            boolean forested = layout.forested;
            Map<Long, List<Feature>> map = new HashMap<>();
            int limit = forested ? 448 : 400;
            int spacing = forested ? 15 : 17;
            for (int z = -limit; z <= limit; z += spacing) {
                for (int x = -limit; x <= limit; x += spacing) {
                    int key = hash(x, 0, z);
                    int bx = AbyssLayer.CENTER_X + x + Math.floorMod(key, 13) - 6;
                    int bz = AbyssLayer.CENTER_Z + z + Math.floorMod(key >>> 8, 13) - 6;
                    if (!AbyssLayer.passage(bx, bz, forested ? 444 : 368)) continue;
                    for (var span : EdgeTerrain.column(bx, bz, layout).shelves()) {
                        int roll = hash(bx, span.top(), bz);
                        if (!EdgeVegetation.treeHabitat(span.cover(), roll, forested) || nearLandmark(bx, span.top(), bz)
                                || (forested && ((!layout.mossPit && EdgeBasin.nearRoute(bx, span.top(), bz, 11))
                                || span.top() > -1215 && !AbyssLayer.passage(bx, bz, 369)))) continue;
                        var site = new Site(bx, span.top() + 1, bz);
                        int kind = EdgeVegetation.treeKind(span.cover(), roll);
                        add(map, site, 36, p -> EdgeVegetation.tree(p, site, kind, roll));
                    }
                }
            }
            for (int depth : new int[]{145, 515, 700, 988, 1172}) {
                var point = EdgeLandscape.point(depth, 266);
                var span = EdgeLandscape.at(point.x(), point.z(), depth, layout);
                if (span == null) continue;
                var site = new Site(point.x(), span.top() + 1, point.z());
                add(map, site, 49, p -> EdgeVegetation.tree(p, site, EdgeVegetation.ELDER, hash(site.x, site.y, site.z)));
            }
            if (forested) forestDetails(map, layout);
            if (layout.wallTerraces()) {
                EdgeWall.plan((site, extent, feature) -> add(map, site, extent, feature::accept), layout);
                if (layout.wildEdge()) EdgeRuins.plan((site, extent, feature) -> add(map, site, extent, feature::accept), layout);
                upperGroves(map, layout);
            }
            int trailEnd = forested ? EdgeBasin.ENTRY_DEPTH : EdgeLandscape.END;
            for (int depth = 18; depth < trailEnd; depth += layout.wildEdge() ? 184 : 44) {
                var point = EdgeLandscape.point(depth, 422);
                var span = EdgeLandscape.at(point.x(), point.z(), depth, layout);
                if (span == null) continue;
                var site = new Site(point.x(), span.top() + 1, point.z());
                add(map, site, 2, p -> marker(p, site));
            }
            for (int depth : new int[]{135, 402, 426, 590, 884, 910, 1248}) if (!layout.mossPit || depth < 1240) waterfall(map, depth, layout);
            if (!layout.wildEdge()) for (var landmark : landmarks(layout)) branchPath(map, landmark, layout);
            for (var landmark : landmarks(layout)) {
                var point = landmark.point();
                var ground = landmark.kind == Kind.CAVE ? EdgeLandscape.point(landmark.depth, 413) : point;
                var span = EdgeLandscape.at(ground.x(), ground.z(), landmark.depth, layout);
                if (span == null) continue;
                var site = new Site(point.x(), span.top() + 1, point.z());
                add(map, site, landmark.kind == Kind.ARK ? 58 : 37, p -> landmark(p, site, landmark, layout));
            }
            if (!layout.mossPit) {
                var bottom = forested ? EdgeBasin.point(14) : EdgeLandscape.point(EdgeLandscape.END, 14);
                add(map, new Site(bottom.x(), -1345, bottom.z()), 4, p -> exit(p, bottom));
            }
            map.replaceAll((chunk, list) -> List.copyOf(list));
            return Map.copyOf(map);
        }

        private static void upperGroves(Map<Long, List<Feature>> map, EdgeTerrain.Layout layout) {
            for (int z = -372; z <= 384; z += 13) {
                for (int x = -372; x <= 384; x += 13) {
                    int key = hash(x, 92, z);
                    int bx = AbyssLayer.CENTER_X + x + Math.floorMod(key, 11) - 5;
                    int bz = AbyssLayer.CENTER_Z + z + Math.floorMod(key >>> 10, 11) - 5;
                    if (!AbyssLayer.passage(bx, bz, 367)) continue;
                    for (var span : EdgeTerrain.column(bx, bz, layout).shelves()) {
                        int y = span.top() + 1;
                        int roll = hash(bx, y ^ 0x5319, bz);
                        if (y < OrthTerrain.SHARED_BOTTOM - 40 || span.water() > span.top()
                                || !EdgeVegetation.treeHabitat(span.cover(), roll, true) || nearLandmark(bx, y, bz)) continue;
                        var site = new Site(bx, y, bz);
                        int kind = EdgeVegetation.treeKind(span.cover(), roll);
                        add(map, site, 27, p -> EdgeVegetation.tree(p, site, kind, roll));
                    }
                }
            }
        }

        private static void forestDetails(Map<Long, List<Feature>> map, EdgeTerrain.Layout layout) {
            for (int depth : new int[]{170, 250, 480, 681, 826, 969, 1125}) {
                int radius = switch (depth) {
                    case 480 -> 312;
                    case 1125 -> 349;
                    default -> 284;
                };
                var point = EdgeLandscape.point(depth, radius);
                var span = EdgeLandscape.at(point.x(), point.z(), depth, layout);
                if (span == null || span.water() > span.top() || nearLandmark(point.x(), span.top(), point.z())) continue;
                var site = new Site(point.x(), span.top() + 1, point.z());
                add(map, site, 42, p -> EdgeVegetation.fallenTree(p, site, depth, layout));
            }
            for (int i = 0; i < 10; i++) {
                double angle = 0.48 + i * Math.PI / 5;
                int radius = i % 2 == 0 ? 260 : 322;
                int x = AbyssLayer.CENTER_X + (int) Math.round(radius * Math.cos(angle));
                int z = AbyssLayer.CENTER_Z + (int) Math.round(radius * Math.sin(angle));
                var span = EdgeLandscape.at(x, z, EdgeBasin.ENTRY_DEPTH, layout);
                if (span == null || !layout.mossPit && EdgeBasin.nearRoute(x, span.top(), z, 40)) continue;
                var site = new Site(x, span.top() + 1, z);
                if (i % 3 == 0) add(map, site, 42, p -> EdgeVegetation.fallenTree(p, site, EdgeBasin.ENTRY_DEPTH, layout));
                else add(map, site, 49, p -> EdgeVegetation.tree(p, site, EdgeVegetation.ELDER, hash(x, site.y, z)));
            }
            if (layout.mossPit) return;
            for (int radius : new int[]{345, 281, 212, 153, 109, 68, 29}) {
                var point = EdgeBasin.point(radius);
                var next = EdgeBasin.point(radius + 6);
                double dx = next.x() - point.x(), dz = next.z() - point.z();
                double scale = 8 / Math.max(1, Math.hypot(dx, dz));
                int x = point.x() - (int) Math.round(dz * scale);
                int z = point.z() + (int) Math.round(dx * scale);
                var span = EdgeLandscape.at(x, z, 1240 + Math.max(0, 171 - radius) * 2 / 3, layout);
                if (span == null) continue;
                var site = new Site(x, span.top() + 1, z);
                add(map, site, 2, p -> marker(p, site));
            }
        }

        private static void branchPath(Map<Long, List<Feature>> map, Landmark landmark, EdgeTerrain.Layout layout) {
            if (landmark.kind == Kind.CAVE || landmark.kind == Kind.FOSSIL_GROVE) return;
            for (int radius = landmark.radius + 4; radius < 405; radius += 3) {
                var point = EdgeLandscape.point(landmark.depth, radius);
                var site = new Site(point.x(), -landmark.depth, point.z());
                add(map, site, 2, p -> {
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            if (dx * dx + dz * dz > 5) continue;
                            int x = site.x + dx, z = site.z + dz;
                            if (x < p.x0 || x > p.x0 + 15 || z < p.z0 || z > p.z0 + 15) continue;
                            var surface = EdgeLandscape.at(x, z, landmark.depth, layout);
                            if (surface == null || surface.water() > surface.top()) continue;
                            int y = surface.top();
                            p.set(x, y, z, Blocks.COARSE_DIRT.defaultBlockState());
                            p.column(x, z, y + 1, y + 2, Blocks.AIR.defaultBlockState());
                        }
                    }
                });
            }
        }

        private static void waterfall(Map<Long, List<Feature>> map, int depth, EdgeTerrain.Layout layout) {
            BlockState falling = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
            for (int offset = -1; offset <= 1; offset++) {
                int level = depth + offset;
                for (int r = 380; r >= 150; r--) {
                    var lip = EdgeLandscape.point(level, r);
                    var bank = EdgeLandscape.at(lip.x(), lip.z(), level, layout);
                    if (bank == null) continue;
                    var point = EdgeLandscape.point(level, r - 2);
                    if (EdgeLandscape.at(point.x(), point.z(), level, layout) != null) continue;
                    var column = EdgeTerrain.column(point.x(), point.z(), layout);
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

    private static void landmark(Painter p, Site s, Landmark landmark, EdgeTerrain.Layout layout) {
        switch (landmark.kind) {
            case CAMP -> camp(p, s, landmark.depth);
            case BURIAL -> burial(p, s);
            case ARK -> ark(p, s, landmark);
            case CAVE -> cave(p, s, landmark.depth);
            case MILL -> mill(p, s);
            case FOSSIL_GROVE -> fossilGrove(p, s, landmark, layout);
        }
    }

    private static void fossilGrove(Painter p, Site s, Landmark landmark, EdgeTerrain.Layout layout) {
        var wall = Direction.getNearest(s.x - AbyssLayer.CENTER_X, 0, s.z - AbyssLayer.CENTER_Z);
        var tangential = wall.getClockWise();
        var chalk = OrthBlocks.CHALK.get().defaultBlockState();
        var stone = Blocks.TUFF.defaultBlockState();
        var fossil = OrthBlocks.FOSSIL_OUTCROP.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, wall.getOpposite());
        var root = OrthBlocks.FOSSIL_ROOT.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, wall.getOpposite());
        var bole = ForestGrowth.Palette.block("petrified_log");
        for (int along = -23; along <= 23; along++) {
            for (int inset = -16; inset <= 20; inset++) {
                int x = s.x + tangential.getStepX() * along + wall.getStepX() * inset;
                int z = s.z + tangential.getStepZ() * along + wall.getStepZ() * inset;
                if (x < p.x0 || x > p.x0 + 15 || z < p.z0 || z > p.z0 + 15) continue;
                double radial = Math.hypot(along * 0.78, inset) + (hash(x, s.y, z) & 3) * 0.65;
                if (radial > 20) continue;
                var ground = EdgeLandscape.at(x, z, landmark.depth, layout);
                if (ground == null || ground.water() > ground.top() || !p.inside(x, ground.top() + 1, z)) continue;
                int y = ground.top();
                if (!p.get(x, y, z).blocksMotion()) continue;
                int roll = hash(x, y, z) & 63;
                int lip = 10 + (int) (2.0 * Math.sin(along * 0.4 + 0.5));
                if (inset >= lip && inset <= lip + 3) {
                    int rise = Math.max(2, 8 - Math.abs(along) / 4 + roll % 4 - (inset - lip));
                    for (int dy = 0; dy <= rise; dy++) {
                        var rock = (dy + along + roll % 4) % 6 == 0 ? chalk : stone;
                        if (inset == lip && dy > 1 && (along + dy * 3) % 7 == 0)
                            rock = (roll & 4) == 0 ? fossil : root;
                        p.wood(x, y + dy, z, rock);
                    }
                    continue;
                }
                if ((roll & 7) < 3) p.set(x, y, z, roll % 3 == 0 ? chalk : Blocks.COARSE_DIRT.defaultBlockState());
                if (roll == 4 && inset < lip) p.wood(x, y + 1, z, OrthBlocks.FOSSIL_STUMP.get().defaultBlockState());
                if ((Math.abs(along + 12) < 2 && Math.abs(inset + 5) < 2)
                        || (Math.abs(along - 11) < 2 && Math.abs(inset - 4) < 2)
                        || (Math.abs(along - 3) < 2 && Math.abs(inset + 12) < 2)) {
                    int height = 4 + Math.floorMod(along / 4 + inset / 3, 6) - (roll & 1);
                    for (int dy = 1; dy <= height; dy++) p.wood(x, y + dy, z, bole);
                    p.wood(x, y + height + 1, z, OrthBlocks.FOSSIL_STUMP.get().defaultBlockState());
                }
            }
        }
    }

    private static void marker(Painter p, Site s) {
        p.set(s.x, s.y, s.z, Blocks.COBBLESTONE_WALL.defaultBlockState());
        p.set(s.x, s.y + 1, s.z, Blocks.LANTERN.defaultBlockState());
    }

    private static void camp(Painter p, Site s, int depth) {
        int x = s.x, y = s.y, z = s.z;
        p.box(x, y - 2, z, 10, 2, 7, Blocks.SPRUCE_PLANKS.defaultBlockState());
        p.box(x, y, z, 10, 9, 7, Blocks.AIR.defaultBlockState());
        BlockState timber = Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState();
        BlockState plank = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState infill = (depth < 650 ? Blocks.PACKED_MUD : Blocks.MOSSY_COBBLESTONE).defaultBlockState();
        for (int dx : new int[]{-9, -2, 8}) {
            for (int dz : new int[]{-6, 5}) {
                pierFoot(p, x + dx, y - 2, z + dz, 14);
                p.column(x + dx, z + dz, y, y + 5, timber);
            }
        }
        for (int dx = -9; dx <= -2; dx++) {
            for (int dz = -6; dz <= 5; dz++) {
                if (dx == -9 || dx == -2 || dz == -6 || dz == 5) {
                    p.column(x + dx, z + dz, y, y + 4, dz % 4 == 0 ? timber : infill);
                }
                p.set(x + dx, y + 5, z + dz, timber);
            }
        }
        BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        p.set(x - 2, y, z + 1, door);
        p.set(x - 2, y + 1, z + 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        p.set(x - 6, y + 2, z + 5, Blocks.BROWN_STAINED_GLASS.defaultBlockState());
        for (int dz = -7; dz <= 6; dz++) {
            int roof = y + 6 + (7 - Math.abs(dz)) / 2;
            BlockState tile = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,
                    dz < 0 ? Direction.SOUTH : Direction.NORTH);
            for (int dx = -10; dx <= -1; dx++) {
                p.set(x + dx, roof - 1, z + dz, plank);
                p.set(x + dx, roof, z + dz, dz == 0 ? Blocks.DARK_OAK_SLAB.defaultBlockState() : tile);
            }
            for (int dx = 0; dx <= 9; dx++) {
                int lean = y + 5 + (9 - dx) / 4;
                BlockState cloth = ((depth / 40 + dz) & 3) == 0 ? Blocks.WHITE_WOOL.defaultBlockState()
                        : (depth < 650 ? Blocks.BROWN_WOOL : Blocks.GREEN_WOOL).defaultBlockState();
                p.set(x + dx, lean, z + dz, cloth);
            }
        }
        for (int dx = -1; dx <= 8; dx++) {
            for (int side : new int[]{-6, 5}) p.set(x + dx, y + 5, z + side, timber);
        }
        p.set(x - 7, y, z - 3, ForestGrowth.Palette.block("camp_bedroll"));
        p.set(x - 5, y, z - 3, ForestGrowth.Palette.block("camp_bedroll"));
        p.set(x + 4, y, z - 4, Blocks.CRAFTING_TABLE.defaultBlockState());
        p.set(x + 5, y, z - 4, Blocks.SMOKER.defaultBlockState());
        p.set(x + 6, y, z - 4, Blocks.STONECUTTER.defaultBlockState());
        p.set(x + 3, y, z + 5, Blocks.LANTERN.defaultBlockState());
        p.barrel(x + 7, y, z - 3, "edge_supplies");
        p.barrel(x - 7, y, z + 2, "edge_supplies");
    }

    private static void pierFoot(Painter p, int x, int y, int z, int search) {
        if (!p.inside(x, y, z)) return;
        int ground = p.shelfBelow(x, y, z, y - search);
        p.column(x, z, ground == EdgeTerrain.NO_SHELF ? y - search : Math.min(ground, y - 1), y,
                Blocks.MOSSY_COBBLESTONE.defaultBlockState());
    }

    private static void burial(Painter p, Site s) {
        int x = s.x, y = s.y, z = s.z;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState worn = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        p.box(x, y - 9, z, 15, 9, 13, Blocks.TUFF.defaultBlockState());
        p.box(x, y - 5, z, 10, 16, 9, air);
        for (int dy = -6; dy <= 12; dy++) {
            int radius = dy <= 1 ? 13 : Math.max(6, 13 - dy / 2);
            p.ring(x, y + dy, z, radius, radius - 3, brick, worn, 4);
        }
        for (int dx : new int[]{-7, 7}) for (int dz : new int[]{-9, 9}) {
            pierFoot(p, x + dx, y - 9, z + dz, 22);
            p.column(x + dx, z + dz, y - 8, y - 2, worn);
        }
        p.disc(x, y - 6, z, 12, brick);
        p.disc(x, y + 12, z, 7, worn);
        p.box(x, y - 5, z, 7, 11, 6, air);
        for (int side : new int[]{-1, 1}) {
            for (int run = 0; run <= 13; run++) {
                int bx = x + side * (run + 5);
                int floor = y - 6 + Math.min(5, run / 2);
                for (int dz = -2; dz <= 2; dz++) {
                    p.column(bx, z + dz, floor - 2, floor, worn);
                    p.column(bx, z + dz, floor + 1, floor + 4, air);
                }
                if (run == 8) {
                    p.column(bx, z - 3, floor, floor + 6, brick);
                    p.column(bx, z + 3, floor, floor + 6, brick);
                    for (int dz = -3; dz <= 3; dz++) p.set(bx, floor + 6, z + dz, brick);
                }
            }
            for (int dx : new int[]{-5, 0, 5}) {
                int bz = z + side * 5;
                p.set(x + dx, y - 5, bz, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
                p.set(x + dx, y - 4, bz, ForestGrowth.Palette.block("praying_skeleton"));
                p.column(x + dx + 1, bz + side * 2, y - 5, y + 4, worn);
                p.set(x + dx + 1, y - 4, bz + side, Blocks.LANTERN.defaultBlockState());
                p.set(x + dx + 1, y - 5, bz + side, brick);
            }
        }
        p.barrel(x, y - 5, z + 8, "edge_relics");
        p.set(x, y - 5, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
    }

    private static void arkStation(Painter p, Site s) {
        int x = s.x, y = s.y + 2, z = s.z;
        BlockState deck = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState beam = Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState();
        p.box(x + 3, y - 1, z + 1, 9, 2, 5, deck);
        p.box(x + 3, y + 1, z + 1, 9, 3, 5, Blocks.AIR.defaultBlockState());
        for (int dx : new int[]{-6, 12}) {
            for (int dz : new int[]{-4, 6}) {
                p.column(x + dx, z + dz, y - 5, y + 4, beam);
                p.set(x + dx, y + 5, z + dz, Blocks.LANTERN.defaultBlockState());
            }
        }
        for (int dx = -7; dx <= 12; dx++) {
            for (int dz = -9; dz <= -3; dz++) {
                int top = y + 5 + (4 - Math.abs(dz + 6)) / 2;
                p.set(x + dx, top, z + dz, ((dx & 3) == 0 ? Blocks.WHITE_WOOL : Blocks.BROWN_WOOL).defaultBlockState());
            }
            p.set(x + dx, y + 4, z - 4, beam);
        }
        for (int dx : new int[]{-6, -1, 6, 12}) p.column(x + dx, z - 8, y - 4, y + 5, beam);
        for (int dx = -5; dx <= 12; dx++) p.set(x + dx, y + 1, z + 6, Blocks.SPRUCE_FENCE.defaultBlockState());
        p.barrel(x - 4, y + 1, z - 2, "edge_relics");
        p.set(x, y + 1, z - 3, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        p.set(x + 10, y + 1, z - 3, Blocks.GRINDSTONE.defaultBlockState());
        if (p.layout.mossPit) {
            BlockPos bell = OrthDescent.arkBell();
            int end = bell.getX() - 3;
            int height = bell.getY() - 1 - y;
            for (int step = 0; step <= height; step++) {
                int bx = end - height + step;
                for (int dz = -1; dz <= 1; dz++) {
                    int bz = bell.getZ() + dz;
                    p.column(bx, bz, y - 1, y + step, deck);
                    p.column(bx, bz, y + step + 1, y + step + 3, Blocks.AIR.defaultBlockState());
                }
            }
            // Cribwork below the bell deck connects the landing to the rock.
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                int bx = bell.getX() + dx, bz = bell.getZ() + dz;
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) p.column(bx, bz, y - 2, bell.getY() - 2, beam);
            }
        }
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
        arkStation(p, s);
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
                    int x = s.x - 8 + i / 3, z = s.z + i;
                    if (!p.inside(x, floor + 1, z)) continue;
                    int ceiling = floor + 7;
                    while (ceiling < floor + 15 && p.get(x, ceiling, z).isAir()) ceiling++;
                    if (p.get(x, ceiling, z).blocksMotion()) {
                        p.column(x, z, floor + 1 + Math.abs(i) / 3, ceiling - 1, root);
                    }
                }
                p.hang(s.x + 3, floor + 9, s.z + 2, Blocks.SPORE_BLOSSOM.defaultBlockState());
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
        p.box(s.x, s.y - 4, s.z, 8, 4, 8, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        for (int x : new int[]{-5, 5}) for (int z : new int[]{-5, 5}) {
            pierFoot(p, s.x + x, s.y - 4, s.z + z, 14);
            p.column(s.x + x, s.z + z, s.y - 1, s.y + 4, Blocks.STONE_BRICKS.defaultBlockState());
        }
        p.box(s.x, s.y, s.z, 8, 6, 8, Blocks.AIR.defaultBlockState());
        for (int y = 0; y <= 22; y++) {
            int r = 7 - y / 9;
            p.ring(s.x, s.y + y, s.z, r, r - 1, Blocks.COBBLESTONE.defaultBlockState(),
                    Blocks.STONE_BRICKS.defaultBlockState(), 4);
        }
        p.box(s.x, s.y, s.z - 6, 1, 3, 2, Blocks.AIR.defaultBlockState());
        for (int h = 0; h < 8; h++) p.disc(s.x, s.y + 23 + h, s.z, 7 - h / 2,
                (h < 2 ? Blocks.DARK_OAK_PLANKS : Blocks.WEATHERED_CUT_COPPER).defaultBlockState());
        for (int h = 7; h <= 20; h++) {
            if (h % 8 > 2) continue;
            p.set(s.x + 5, s.y + h, s.z, Blocks.BROWN_STAINED_GLASS.defaultBlockState());
            p.set(s.x - 5, s.y + h, s.z, Blocks.BROWN_STAINED_GLASS.defaultBlockState());
        }
        p.set(s.x + 1, s.y, s.z - 2, Blocks.GRINDSTONE.defaultBlockState());
        p.set(s.x - 3, s.y, s.z - 2, Blocks.STONECUTTER.defaultBlockState());
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
        private final int yOffset;
        private final int bottom;
        private final EdgeTerrain.Layout layout;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        Painter(ChunkAccess chunk) {
            this(chunk, 0);
        }

        Painter(ChunkAccess chunk, int yOffset) {
            this(chunk, yOffset, EdgeTerrain.MIN_Y + 1);
        }

        Painter(ChunkAccess chunk, int yOffset, int bottom) {
            this(chunk, yOffset, bottom, EdgeTerrain.Layout.WILD_EDGE);
        }

        Painter(ChunkAccess chunk, int yOffset, int bottom, EdgeTerrain.Layout layout) {
            this.chunk = chunk;
            this.yOffset = yOffset;
            this.bottom = bottom;
            this.layout = layout;
            x0 = chunk.getPos().getMinBlockX();
            z0 = chunk.getPos().getMinBlockZ();
        }

        boolean inside(int x, int y, int z) {
            return x >= x0 && x <= x0 + 15 && z >= z0 && z <= z0 + 15 && y >= bottom && y < 0 && !chunk.isOutsideBuildHeight(y + yOffset);
        }

        BlockState get(int x, int y, int z) { return chunk.getBlockState(pos.set(x, y + yOffset, z)); }

        void set(int x, int y, int z, BlockState state) {
            if (inside(x, y, z)) chunk.setBlockState(pos.set(x, y + yOffset, z), state, false);
        }

        void grow(int x, int y, int z, BlockState state) {
            if (inside(x, y, z) && get(x, y, z).isAir()) set(x, y, z, state);
        }

        void hang(int x, int y, int z, BlockState state) {
            if (inside(x, y, z) && hasOverheadSupport(x, y, z)) grow(x, y, z, state);
        }

        boolean hasOverheadSupport(int x, int y, int z) {
            var anchor = get(x, y + 1, z);
            return anchor.blocksMotion() || anchor.is(net.minecraft.tags.BlockTags.LEAVES);
        }

        int shelfBelow(int x, int top, int z, int low) {
            var column = EdgeTerrain.column(x, z, layout);
            if (!column.opening()) return top;
            for (int y = top; y >= low; y--) {
                var rock = EdgeTerrain.block(column, y, layout);
                if (rock.blocksMotion()) return y;
            }
            return EdgeTerrain.NO_SHELF;
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
            for (int y = Math.max(lo, bottom); y <= hi; y++) set(x, y, z, state);
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
            // The Overworld preview shows the barrel but the Edge owns its generated loot.
            if (bottom == OrthTerrain.SHARED_BOTTOM) return;
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
