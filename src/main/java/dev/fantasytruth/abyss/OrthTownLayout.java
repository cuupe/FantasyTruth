package dev.fantasytruth.abyss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

/** Short lanes follow the folds of the inner slope and carry individually surveyed plots. */
final class OrthTownLayout {
    private static final double TURN = 2 * Math.PI;
    static final int SPAWN_Z = OrthRelief.at(0.75, 782).z();
    static final int GATE_Z = OrthRelief.at(0.75, 513).z();
    static final OrthRelief.Point ORPHANAGE = OrthRelief.at(0.54, 817);
    static final OrthRelief.Point GUILD = OrthRelief.at(0.974, 672);
    static final int PIER_Z = 120;
    static final int PIER_ANCHOR = -605;
    static final int PIER_HUB = -394;
    static final double[] FALLS = {0.063, 0.205, 0.409, 0.569, 0.827, 0.938};

    private static final List<District> DISTRICTS = List.of(
            new District(-0.046, 0.127, 920, 151, false),
            new District(0.145, 0.353, 776, 153, true),
            new District(0.365, 0.576, 1015, 127, false),
            new District(0.588, 0.815, 1070, 120, false),
            new District(0.835, 0.927, 949, 154, false)
    );

    static Survey survey() {
        List<Road> roads = new ArrayList<>();
        List<Lane> lanes = new ArrayList<>();
        for (District district : DISTRICTS) surveyDistrict(roads, lanes, district);
        landmarkRoads(roads);
        Map<Long, List<Road>> roadIndex = new HashMap<>();
        for (Road road : roads) {
            int pad = road.width + 16;
            index(roadIndex, road, Math.min(road.a.x, road.b.x) - pad, Math.min(road.a.z, road.b.z) - pad,
                    Math.max(road.a.x, road.b.x) + pad, Math.max(road.a.z, road.b.z) + pad);
        }
        List<Lot> lots = new ArrayList<>();
        Map<Long, List<Lot>> footprints = new HashMap<>();
        for (Lane lane : lanes) plotFrontages(lots, footprints, roadIndex, lane);
        fillCourts(lots, footprints, roadIndex, roads);
        return new Survey(List.copyOf(roads), List.copyOf(lots));
    }

    private static void surveyDistrict(List<Road> roads, List<Lane> lanes, District district) {
        List<Node> centers = new ArrayList<>();
        for (int ring = 0; ring < 5; ring++) {
            double radial = Mth.lerp((ring + 0.23) / 4.5, 546, district.upper);
            if (radial > district.upper) continue;
            int count = Math.max(2, (int) Math.ceil((district.to - district.from) * TURN * radial / district.spacing));
            for (int i = 0; i < count; i++) {
                int seed = hash((int) (district.from * 10000) + ring * 703, i);
                double fraction = (i + 0.48 + (unit(seed) - 0.5) * 0.85) / count;
                double turn = Mth.lerp(fraction, district.from, district.to);
                double radius = radial + 79 * (unit(seed >>> 8) - 0.5);
                if (OrthRelief.ravine(turn) > 0.71) continue;
                var pos = OrthRelief.at(turn, radius);
                Node center = at(pos.x(), pos.z());
                if (!urban(center.x, center.z, district)) continue;
                centers.add(center);
                double tangent = TURN * (turn + 0.25);
                double angle = tangent + 1.08 * (unit(seed >>> 16) - 0.5);
                double half = 49 + 39 * unit(seed >>> 3);
                trace(roads, lanes, district, center, angle, -half, half + (seed & 15), seed);
                for (int branch : new int[]{-1, 1}) {
                    double along = branch * half * (0.23 + 0.22 * unit(seed >>> (branch < 0 ? 5 : 13)));
                    Node fork = curve(center, angle, along, seed);
                    double heading = angle + branch * (0.49 + 0.54 * unit(seed >>> 9));
                    trace(roads, lanes, district, fork, heading, 0, 44 + (seed >>> 17 & 31), seed ^ (branch * 71237));
                }
            }
        }
        for (int i = 1; i < centers.size(); i++) {
            Node from = centers.get(i);
            Node neighbor = null;
            double limit = 177;
            for (int j = 0; j < i; j++) {
                Node candidate = centers.get(j);
                double distance = distance(from, candidate);
                if (distance < 48 || distance >= limit) continue;
                limit = distance;
                neighbor = candidate;
            }
            if (neighbor == null || (hash(from.x, from.z) & 7) > 5) continue;
            int x = (from.x + neighbor.x) / 2 + (hash(from.z, from.x) & 15) - 7;
            int z = (from.z + neighbor.z) / 2 + (hash(from.x, from.z) >>> 4 & 15) - 7;
            if (urban(x, z, district)) gradedRoute(roads, 2, from, at(x, z), neighbor);
        }
    }

    private static void trace(List<Road> roads, List<Lane> lanes, District district, Node center,
                              double angle, double start, double end, int seed) {
        int count = Math.max(1, (int) Math.ceil((end - start) / 8));
        Node last = null;
        List<Road> lane = new ArrayList<>();
        for (int i = 0; i <= count; i++) {
            Node node = curve(center, angle, Mth.lerp(i / (double) count, start, end), seed);
            if (!urban(node.x, node.z, district)) {
                if (!lane.isEmpty()) lanes.add(new Lane(List.copyOf(lane), seed, district.south));
                lane.clear();
                last = null;
                continue;
            }
            if (last != null && distance(last, node) > 0) {
                Road road = new Road(last, node, (seed & 7) < 2 ? 3 : 2);
                roads.add(road);
                lane.add(road);
            }
            last = node;
        }
        if (!lane.isEmpty()) lanes.add(new Lane(List.copyOf(lane), seed, district.south));
    }

    private static Node curve(Node center, double angle, double along, int seed) {
        double bend = Math.sin(along / (24 + (seed & 15))) * ((seed >>> 9 & 15) - 7)
                + along * Math.abs(along) * ((seed >>> 17 & 7) - 3) / 1800.0;
        int x = center.x + (int) Math.round(along * Math.cos(angle) - bend * Math.sin(angle));
        int z = center.z + (int) Math.round(along * Math.sin(angle) + bend * Math.cos(angle));
        return at(x, z);
    }

    private static boolean urban(int x, int z, District district) {
        double distance = OrthRelief.townDistance(x, z);
        if (distance < 518 || distance > district.upper + 21 || OrthRelief.pitDistance(x, z) < 516) return false;
        double turn = OrthRelief.turn(x, z);
        if (district.from < 0 && turn > 0.6) turn--;
        return turn >= district.from - 0.014 && turn <= district.to + 0.014
                && OrthRelief.ravine(turn) < 0.84;
    }

    private static void plotFrontages(List<Lot> lots, Map<Long, List<Lot>> footprints,
                                      Map<Long, List<Road>> roadIndex, Lane lane) {
        for (int side : new int[]{-1, 1}) {
            double next = 4 + (lane.seed & 11) + (side > 0 ? 5 : 0);
            int candidate = 0;
            for (Road road : lane.roads) {
                double length = distance(road.a, road.b);
                while (next <= length) {
                    int h = hash(lane.seed ^ (side * 6151), candidate++);
                    double t = next / length;
                    double dx = (road.b.x - road.a.x) / length, dz = (road.b.z - road.a.z) / length;
                    int rx = (int) Math.round(Mth.lerp(t, road.a.x, road.b.x));
                    int rz = (int) Math.round(Mth.lerp(t, road.a.z, road.b.z));
                    int ry = (int) Math.floor(Mth.lerp(t, road.a.y, road.b.y));
                    int width = 4 + (h >>> 2 & 3), depth = 4 + (h >>> 4 & 3);
                    int offset = road.width + Math.max(width, depth) + 4 + (h >>> 16 & 3);
                    int x = rx + (int) Math.round(-dz * side * offset);
                    int z = rz + (int) Math.round(dx * side * offset);
                    if (!reserved(x, z) && OrthRelief.pitDistance(x, z) >= 521
                            && OrthRelief.ravine(OrthRelief.turn(x, z)) < 0.71) {
                        Direction facing = facing(rx - x, rz - z);
                        int ground = OrthRelief.elevation(x, z);
                        int floor = ry + Mth.clamp(ground - ry, -9, 11) + (h >>> 21 & 1);
                        int floors = 1 + (h >>> 8 & 1) + (!lane.south && (h >>> 9 & 3) != 0 ? 1 : 0);
                        boolean mill = floor > 445 && Math.floorMod(h, 43) == 0;
                        int frontX = x + facing.getStepX() * (depth + 1);
                        int frontZ = z + facing.getStepZ() * (depth + 1);
                        var access = new Road(new Node(rx, ry, rz), new Node(frontX, floor, frontZ), 1);
                        Lot lot = new Lot(x, floor, z, width, depth, floors, (h >>> 13) & 3, facing, mill, access);
                        if (Math.abs(ground - floor) <= 18 && awayFromRoads(roadIndex, lot)
                                && reserveFootprint(footprints, lot)) lots.add(lot);
                    }
                    next += 13 + (h & 7) + (lane.south ? 2 : 0);
                }
                next -= length;
            }
        }
    }

    private static void fillCourts(List<Lot> lots, Map<Long, List<Lot>> footprints,
                                   Map<Long, List<Road>> roadIndex, List<Road> roads) {
        int frontages = lots.size();
        for (int i = 0; i < frontages; i++) {
            Lot parent = lots.get(i);
            int h = hash(parent.x, parent.z);
            if (parent.mill || (h & 15) == 0) continue;
            int fx = parent.facing.getStepX(), fz = parent.facing.getStepZ();
            for (int side : new int[]{(h & 1) == 0 ? 1 : -1, (h & 1) == 0 ? -1 : 1}) {
                int behind = parent.depth + 9 + (h >>> 4 & 3);
                int across = parent.width + 7 + (h >>> 9 & 3);
                int x = parent.x - fx * behind - fz * side * across;
                int z = parent.z - fz * behind + fx * side * across;
                if (reserved(x, z) || OrthRelief.townDistance(x, z) > 1090 || OrthRelief.pitDistance(x, z) < 532
                        || OrthRelief.ravine(OrthRelief.turn(x, z)) > 0.65) continue;
                int ground = OrthRelief.elevation(x, z);
                if (Math.abs(ground - parent.y) > 24) continue;
                int wing = parent.width + 3;
                Node corner = at(parent.x - fz * side * wing, parent.z + fx * side * wing);
                Node front = at(x + fx * 5, z + fz * 5);
                int y = ground + (h >>> 20 & 3) - 1;
                var annex = new Lot(x, y, z, 4 + (h >>> 6 & 1), 4, 1 + (h >>> 12 & 1),
                        (h >>> 15) & 3, parent.facing, false,
                        new Road(corner, new Node(front.x, y, front.z), 1));
                if (awayFromRoads(roadIndex, annex) && reserveFootprint(footprints, annex)) {
                    lots.add(annex);
                    gradedRoute(roads, 1, parent.access.a, corner);
                    break;
                }
            }
        }
    }

    private static boolean awayFromRoads(Map<Long, List<Road>> roads, Lot lot) {
        for (Road road : roads.getOrDefault(ChunkPos.asLong(lot.x >> 5, lot.z >> 5), List.of())) {
            double dx = road.b.x - road.a.x, dz = road.b.z - road.a.z;
            double len2 = dx * dx + dz * dz;
            if (len2 == 0) continue;
            double t = Mth.clamp(((lot.x - road.a.x) * dx + (lot.z - road.a.z) * dz) / len2, 0, 1);
            double x = lot.x - road.a.x - t * dx, z = lot.z - road.a.z - t * dz;
            double horizontal = Math.max(0, Math.abs(x) - lot.extentX());
            double longitudinal = Math.max(0, Math.abs(z) - lot.extentZ());
            if (horizontal * horizontal + longitudinal * longitudinal <= road.width * road.width) return false;
        }
        return true;
    }

    private static boolean reserveFootprint(Map<Long, List<Lot>> footprints, Lot lot) {
        int rx = lot.extentX(), rz = lot.extentZ();
        for (int z = (lot.z - rz) >> 5; z <= (lot.z + rz) >> 5; z++) {
            for (int x = (lot.x - rx) >> 5; x <= (lot.x + rx) >> 5; x++) {
                for (Lot other : footprints.getOrDefault(ChunkPos.asLong(x, z), List.of())) {
                    if (Math.abs(lot.x - other.x) <= rx + other.extentX()
                            && Math.abs(lot.z - other.z) <= rz + other.extentZ()) return false;
                }
            }
        }
        index(footprints, lot, lot.x - rx, lot.z - rz, lot.x + rx, lot.z + rz);
        return true;
    }

    private static <T> void index(Map<Long, List<T>> index, T entry, int minX, int minZ, int maxX, int maxZ) {
        for (int z = minZ >> 5; z <= maxZ >> 5; z++) {
            for (int x = minX >> 5; x <= maxX >> 5; x++) {
                index.computeIfAbsent(ChunkPos.asLong(x, z), ignored -> new ArrayList<>()).add(entry);
            }
        }
    }

    private static void landmarkRoads(List<Road> roads) {
        gradedRoute(roads, 5, at(0, SPAWN_Z), at(-48, SPAWN_Z + 50), at(-42, -667), at(27, -621),
                at(24, GATE_Z - 46), at(0, GATE_Z));
        gradedRoute(roads, 4, node(0.279, 752), node(0.263, 829), node(0.222, 939), node(0.251, 1047),
                node(0.23, 1155), at(0, southShore() - 12));
        gradedRoute(roads, 3, node(0.557, 820), at(ORPHANAGE.x(), ORPHANAGE.z() - 29));
        gradedRoute(roads, 4, node(0.981, 751), at(GUILD.x() + 48, GUILD.z() - 3));
        gradedRoute(roads, 4, node(0.465, 657), at(PIER_ANCHOR - 16, PIER_Z));
    }

    private static boolean reserved(int x, int z) {
        if (Math.abs(x - ORPHANAGE.x()) < 66 && Math.abs(z - ORPHANAGE.z()) < 63) return true;
        if (Math.abs(x - GUILD.x()) < 104 && Math.abs(z - GUILD.z()) < 68) return true;
        if (Math.abs(x) < 65 && z >= SPAWN_Z - 43 && z <= GATE_Z + 32) return true;
        if (x <= PIER_ANCHOR + 52 && x >= PIER_ANCHOR - 78 && Math.abs(z - PIER_Z) < 39) return true;
        return Math.abs(x) < 145 && z > 0 && OrthRelief.townDistance(x, z) < 672;
    }

    private static void gradedRoute(List<Road> roads, int width, Node... nodes) {
        for (int i = 1; i < nodes.length; i++) {
            Node start = nodes[i - 1], end = nodes[i];
            double length = distance(start, end);
            if (length == 0) continue;
            int segments = Math.max(1, (int) Math.ceil(length / 10));
            int curvature = (hash(start.x + end.x, start.z + end.z) & 15) - 7;
            Node a = start;
            for (int j = 1; j <= segments; j++) {
                double t = j / (double) segments;
                double bend = curvature * Math.sin(Math.PI * t);
                int x = (int) Math.round(Mth.lerp(t, start.x, end.x) - bend * (end.z - start.z) / length);
                int z = (int) Math.round(Mth.lerp(t, start.z, end.z) + bend * (end.x - start.x) / length);
                Node b = j == segments ? end : at(x, z);
                if (distance(a, b) > 0) roads.add(new Road(a, b, width));
                a = b;
            }
        }
    }

    static int southShore() { return Coast.SHORE; }

    private static final class Coast {
        static final int SHORE = find();
        private static int find() {
            for (int z = 1610; z >= 1000; z--) if (OrthRelief.elevation(0, z) >= OrthIsland.SEA) return z;
            return 1300;
        }
    }

    private static Node node(double turn, double radius) {
        var p = OrthRelief.at(turn, radius);
        return at(p.x(), p.z());
    }

    private static Node at(int x, int z) {
        return new Node(x, OrthRelief.elevation(x, z), z);
    }

    private static double distance(Node a, Node b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static Direction facing(int x, int z) {
        if (Math.abs(x) >= Math.abs(z)) return x >= 0 ? Direction.EAST : Direction.WEST;
        return z >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static double unit(int bits) { return (bits & 255) / 255.0; }

    private static int hash(int x, int z) {
        int h = (x * 0x632be5ab) ^ (z * 0x85157af5);
        h ^= h >>> 16;
        h *= 0x7feb352d;
        return h ^ (h >>> 15);
    }

    record Node(int x, int y, int z) {}
    record Road(Node a, Node b, int width) {}
    record Survey(List<Road> roads, List<Lot> lots) {}
    record Lot(int x, int y, int z, int width, int depth, int floors, int style, Direction facing, boolean mill, Road access) {
        int extentX() { return mill ? 14 : (facing.getAxis() == Direction.Axis.X ? depth : width) + 2; }
        int extentZ() { return mill ? 14 : (facing.getAxis() == Direction.Axis.X ? width : depth) + 2; }
    }
    private record Lane(List<Road> roads, int seed, boolean south) {}
    private record District(double from, double to, int upper, int spacing, boolean south) {}

    private OrthTownLayout() {}
}
