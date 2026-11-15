package dev.fantasytruth.abyss;

import dev.fantasytruth.abyss.EdgeExploration.Painter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Bounded rock-cut passages are planned once per world seed and clipped at chunk borders. */
final class EdgeCaves {
    private static final int LIMIT = 660;


    record Plan(long seed, Map<Long, List<Cave>> byChunk) {
        void place(ChunkAccess chunk) {
            var caves = byChunk.get(chunk.getPos().toLong());
            if (caves == null) return;
            var painter = new Painter(chunk);
            for (var cave : caves) cave.carve(painter);
            for (var cave : caves) cave.decorate(painter);
        }
    }

    static boolean nearShaft(ChunkPos pos) {
        return AbyssLayer.passage(pos.getMiddleBlockX(), pos.getMiddleBlockZ(), LIMIT);
    }

    static Plan plan(long seed, EdgeTerrain.Layout layout) {
        var random = new SplittableRandom(seed ^ 0x4564676543617665L);
        var byChunk = new HashMap<Long, List<Cave>>();
        var entrances = new ArrayList<Hollow>();
        for (int band = layout.wallTerraces() ? -1 : 0; band < 5; band++) {
            int placed = 0;
            for (int attempt = 0; attempt < 96 && placed < 3; attempt++) {
                int depth = 298 + band * 178 + random.nextInt(170);
                int radius = 420 + random.nextInt(10);
                var cave = survey(depth, radius, placed == 0, random, entrances, layout.wallTerraces() ? layout : EdgeTerrain.Layout.ERODED);
                if (cave == null) continue;
                index(byChunk, cave);
                entrances.add(cave.hollows.get(0));
                placed++;
            }
        }
        byChunk.replaceAll((key, caves) -> List.copyOf(caves));
        return new Plan(seed, Map.copyOf(byChunk));
    }

    private static Cave survey(int depth, int entrance, boolean mine, SplittableRandom random,
                               List<Hollow> entrances, EdgeTerrain.Layout layout) {
        double angle = EdgeLandscape.angle(depth);
        double dx = Math.cos(angle), dz = Math.sin(angle);
        // The two ladder approaches run along the north and south walls.
        if (Math.abs(dx) < 0.29) return null;
        int x = worldX(dx * entrance), z = worldZ(dz * entrance);
        var deck = EdgeLandscape.at(x, z, depth, layout);
        if (deck == null || deck.water() > deck.top() || Math.abs(deck.top() + depth) > 23) return null;
        int floor = deck.top();
        for (var other : entrances) {
            if (Math.abs(floor - other.floor) < 38 && Math.hypot(x - other.x, z - other.z) < 115) return null;
        }

        int wall = entrance + 8;
        while (wall < 565 && OrthIsland.shaftColumn((int) Math.round(dx * wall), (int) Math.round(dz * wall))) wall += 4;
        if (wall >= 562) return null;
        for (int radius = entrance; radius <= wall + 30; radius += 5) {
            if (EdgeExploration.nearLandmark(worldX(dx * radius), floor, worldZ(dz * radius))) return null;
        }
        var hollows = new ArrayList<Hollow>();
        int end = wall + random.nextInt(36, 51);
        double bend = random.nextDouble(-8, 8);
        int rise = mine ? 2 : random.nextInt(-4, 7);
        for (int radius = entrance + 2; radius < end; radius += 4) {
            double progress = Mth.clamp((radius - wall) / (double) (end - wall), 0, 1);
            int y = floor + (int) Math.round(Math.min(2, (radius - entrance - 2) / 8.0) + progress * rise);
            double offset = bend * progress * progress;
            int width = mine ? 4 : random.nextInt(4, 7);
            int height = mine ? 5 : random.nextInt(5, 9);
            hollows.add(at(dx, dz, radius, offset, y, width, height));
        }
        var room = at(dx, dz, end, bend, floor + 2 + rise, mine ? 8 : 13, mine ? 8 : 15);
        hollows.add(room);
        int branch = random.nextBoolean() ? 1 : -1;
        int branches = mine ? 2 : 1 + random.nextInt(2);
        for (int arm = 0; arm < branches; arm++) {
            int sign = arm == 0 ? branch : -branch;
            int length = random.nextInt(18, 30);
            int grade = arm == 0 ? 1 : -1;
            for (int step = 4; step <= length; step += 4) {
                int drift = (int) (step * (0.36 + 0.16 * Math.sin(step * 0.21)));
                hollows.add(at(dx, dz, end + drift, bend + sign * step, room.floor + grade * (step / 7),
                        mine ? 4 : 5 + step / 10, mine ? 5 : 7 + step / 9));
            }
        }
        return new Cave(dx, dz, floor, entrance, wall, mine, room, List.copyOf(hollows), random.nextInt());
    }

    private static Hollow at(double dx, double dz, double radius, double offset, int floor, int width, int height) {
        return new Hollow(worldX(dx * radius - dz * offset), worldZ(dz * radius + dx * offset), floor, width, height);
    }

    private static int worldX(double x) { return AbyssLayer.CENTER_X + (int) Math.round(x); }
    private static int worldZ(double z) { return AbyssLayer.CENTER_Z + (int) Math.round(z); }

    private static void index(Map<Long, List<Cave>> byChunk, Cave cave) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = minX, maxZ = maxX;
        for (var hollow : cave.hollows) {
            int padding = hollow == cave.room ? 10 : 1;
            minX = Math.min(minX, hollow.x - hollow.width - padding);
            maxX = Math.max(maxX, hollow.x + hollow.width + padding);
            minZ = Math.min(minZ, hollow.z - hollow.width - padding);
            maxZ = Math.max(maxZ, hollow.z + hollow.width + padding);
        }
        for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
            for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                byChunk.computeIfAbsent(ChunkPos.asLong(cx, cz), key -> new ArrayList<>()).add(cave);
            }
        }
    }

    record Cave(double dx, double dz, int floor, int entrance, int wall, boolean mine, Hollow room, List<Hollow> hollows, int salt) {
        void carve(Painter p) {
            BlockState air = Blocks.CAVE_AIR.defaultBlockState();
            for (var hollow : hollows) {
                for (int x = Math.max(p.x0, hollow.x - hollow.width); x <= Math.min(p.x0 + 15, hollow.x + hollow.width); x++) {
                    for (int z = Math.max(p.z0, hollow.z - hollow.width); z <= Math.min(p.z0 + 15, hollow.z + hollow.width); z++) {
                        int ox = x - hollow.x, oz = z - hollow.z;
                        double radius = (ox * ox + oz * oz) / (double) (hollow.width * hollow.width);
                        int rough = EdgeExploration.hash(x, hollow.floor, z);
                        if (radius > (mine ? 0.95 : 0.89 + (rough & 7) * 0.016)) continue;
                        int height = 2 + (int) ((hollow.height - 2) * Math.sqrt(1 - radius));
                        if (!mine && radius > 0.32 && (rough & 7) == 3) height--;
                        for (int y = hollow.floor + 1; y <= hollow.floor + height; y++) {
                            var previous = p.get(x, y, z);
                            if (!previous.isAir() && previous.getFluidState().isEmpty()) p.set(x, y, z, air);
                        }
                    }
                }
            }
        }

        boolean silkfangDen() { return !mine && (salt & 3) != 0; }

        void decorate(Painter p) {
            var stone = OrthBlocks.STONE.get().defaultBlockState();
            var air = Blocks.CAVE_AIR.defaultBlockState();
            // Give each tunnel a continuous walkable lip even where the outer face overhangs.
            int mouthX = worldX(dx * entrance), mouthZ = worldZ(dz * entrance);
            int lipX = worldX(dx * (wall + 4)), lipZ = worldZ(dz * (wall + 4));
            boolean nearLip = p.x0 <= Math.max(mouthX, lipX) + 3 && p.x0 + 15 >= Math.min(mouthX, lipX) - 3
                    && p.z0 <= Math.max(mouthZ, lipZ) + 3 && p.z0 + 15 >= Math.min(mouthZ, lipZ) - 3;
            for (int radius = entrance; nearLip && radius <= wall + 4; radius++) {
                int y = floor + Mth.clamp((radius - entrance - 2) / 8, 0, 2);
                for (int side = -2; side <= 2; side++) {
                    var deck = at(dx, dz, radius, side, y, 0, 0);
                    if (!p.inside(deck.x, y, deck.z)) continue;
                    for (int dy = -2; dy <= 0; dy++) p.grow(deck.x, y + dy, deck.z, stone);
                    for (int dy = 1; dy <= 3; dy++) {
                        var state = p.get(deck.x, y + dy, deck.z);
                        if (!state.isAir() && state.getFluidState().isEmpty()) {
                            p.set(deck.x, y + dy, deck.z, air);
                        }
                    }
                }
            }
            if (mine) {
                var wood = Blocks.SPRUCE_LOG.defaultBlockState();
                var beam = wood.setValue(RotatedPillarBlock.AXIS, Math.abs(dx) > Math.abs(dz) ? Direction.Axis.Z : Direction.Axis.X);
                for (int radius = wall + 3; radius <= wall + 20; radius += 8) {
                    var frameCenter = at(dx, dz, radius, 0, floor + 2, 0, 0);
                    for (int side = -2; side <= 2; side++) {
                        var frame = new Hollow(frameCenter.x + (beam.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X ? side : 0),
                                frameCenter.z + (beam.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Z ? side : 0),
                                frameCenter.floor, 0, 0);
                        if (!p.inside(frame.x, frame.floor, frame.z)) continue;
                        if (Math.abs(side) == 2) {
                            int support = frame.floor;
                            while (support >= frame.floor - 4 && p.get(frame.x, support, frame.z).isAir()) support--;
                            if (support >= frame.floor - 4 && p.get(frame.x, support, frame.z).blocksMotion()) {
                                for (int y = support + 1; y <= frame.floor + 3; y++) p.grow(frame.x, y, frame.z, wood);
                            }
                        }
                        p.grow(frame.x, frame.floor + 4, frame.z, beam);
                    }
                }
            }
            var moss = OrthBlocks.GLOW_MOSS.get().defaultBlockState();
            var veins = new SplittableRandom(salt);
            for (int n = 0; n < 3; n++) {
                double angle = veins.nextDouble(Math.PI * 2);
                int x = room.x + (int) Math.round((room.width - 1) * Math.cos(angle));
                int z = room.z + (int) Math.round((room.width - 1) * Math.sin(angle));
                var ore = n == 2 && (salt & 3) == 0 ? OrthBlocks.RELIC_ORE.get()
                        : room.floor < -840 ? (n == 1 ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.DEEPSLATE_IRON_ORE)
                        : (n == 1 ? Blocks.COPPER_ORE : Blocks.IRON_ORE);
                EdgeOres.vein(p, x, room.floor - 1 + n, z, ore, veins);
            }
            for (int z = Math.max(p.z0, room.z - room.width); z <= Math.min(p.z0 + 15, room.z + room.width); z++) {
                for (int x = Math.max(p.x0, room.x - room.width); x <= Math.min(p.x0 + 15, room.x + room.width); x++) {
                    int hash = EdgeExploration.hash(x, salt, z);
                    if ((hash & 7) != 0 || !p.get(x, room.floor + 1, z).isAir()
                            || p.get(x, room.floor, z) != stone) continue;
                    if ((hash & 24) == 0 && !mine) p.set(x, room.floor, z, moss);
                }
            }
            if (silkfangDen()) webbing(p);
            if (mine && p.inside(room.x, room.floor + 1, room.z)
                    && p.get(room.x, room.floor + 1, room.z).isAir()
                    && !p.get(room.x, room.floor, room.z).isAir()) {
                p.barrel(room.x, room.floor + 1, room.z, "edge_supplies");
            }
        }
        private void webbing(Painter p) {
            var web = Blocks.COBWEB.defaultBlockState();
            for (int x = Math.max(p.x0, room.x - 5); x <= Math.min(p.x0 + 15, room.x + 5); x++) {
                for (int z = Math.max(p.z0, room.z - 5); z <= Math.min(p.z0 + 15, room.z + 5); z++) {
                    int dx = x - room.x, dz = z - room.z;
                    int distance = dx * dx + dz * dz;
                    if (distance < 14 || distance > 32 || (EdgeExploration.hash(x, salt, z) & 3) == 0) continue;
                    for (int y = room.floor + 4; y < room.floor + room.height; y++) {
                        if (!p.get(x, y + 1, z).blocksMotion() || !p.get(x, y, z).isAir()) continue;
                        p.grow(x, y, z, web);
                        if ((EdgeExploration.hash(x, y, z) & 3) == 0) p.grow(x, y - 1, z, web);
                        break;
                    }
                }
            }
        }
    }

    record Hollow(int x, int z, int floor, int width, int height) {}

    private EdgeCaves() {}
}
