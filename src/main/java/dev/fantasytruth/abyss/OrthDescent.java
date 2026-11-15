package dev.fantasytruth.abyss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** The approach, ladders and landings use the same coordinates in both dimensions. */
final class OrthDescent {
    record Route(String id, int x, int startZ, int top, int shaftZ, int endZ, int bottom, Direction facing) {
        BlockPos entrance() { return new BlockPos(x, top + 1, startZ); }
    }

    static Route gate() { return Survey.GATE; }
    static Route wharf() { return Survey.WHARF; }

    private static final class Survey {
        static final Route GATE = gateRoute();
        static final Route WHARF = wharfRoute();
    }

    private static Route gateRoute() {
        int start = OrthTownLayout.GATE_Z + 20;
        int y = OrthRelief.elevation(0, OrthTownLayout.GATE_Z) - 10;
        return survey("gate", 0, start, y, 410, 75, Direction.SOUTH);
    }

    private static Route wharfRoute() {
        int x = 104;
        int lip = 510 - x * x / 1000;
        int y = OrthRelief.elevation(x, lip + 42) - 28;
        int wall = 599;
        while (wall > 352 && !OrthIsland.shaftColumn(x, wall)) wall--;
        return survey("wharf", x, wall - 12, y, 387, 390, Direction.NORTH);
    }

    private static Route survey(String id, int x, int start, int y, int target, int depth, Direction facing) {
        int step = facing.getStepZ();
        int shaft = start;
        while (!OrthIsland.shaftColumn(x, shaft) || !OrthIsland.shaftColumn(x, shaft - step)) {
            shaft += step;
            if (Math.abs(shaft) > 650) throw new IllegalStateException("Missing " + id + " shaft");
        }
        shaft += step * 8;
        int end = facing == Direction.SOUTH ? -target : target;
        var layout = EdgeTerrain.Layout.MOSS_PIT;
        var span = EdgeLandscape.at(world(x), world(end), depth, layout);
        while (span == null) {
            end -= step;
            if (!OrthIsland.shaftColumn(x, end)) throw new IllegalStateException("Missing " + id + " landing");
            span = EdgeLandscape.at(world(x), world(end), depth, layout);
        }
        return new Route(id, world(x), world(start), y, world(shaft), world(end), span.top(), facing);
    }

    static BlockPos pierBell() {
        int anchor = OrthTownLayout.PIER_ANCHOR, hub = OrthTownLayout.PIER_HUB, z = OrthTownLayout.PIER_Z;
        int y = OrthRelief.elevation(anchor, z) - (hub - anchor - 23) / 8;
        return new BlockPos(world(hub - 6), y + 1, world(z + 5));
    }

    static BlockPos arkBell() {
        var point = EdgeLandscape.point(600, 169);
        var span = EdgeLandscape.at(point.x(), point.z(), 600, EdgeTerrain.Layout.MOSS_PIT);
        return new BlockPos(point.x() + 6, span.top() + 4, point.z() + 2);
    }

    private static int world(int coordinate) { return AbyssLayer.CENTER_X + coordinate; }

    static void build(ChunkAccess chunk, boolean edge, EdgeTerrain.Layout layout) {
        boolean shared = layout.wallTerraces();
        var p = new Builder(chunk);
        p.route(gate(), edge || shared, layout);
        p.route(wharf(), edge || shared, layout);
        BlockPos bell = edge ? arkBell() : pierBell();
        if (p.contains(bell.getX() - 2, bell.getZ() - 2) || p.contains(bell.getX() + 2, bell.getZ() - 2)
                || p.contains(bell.getX() - 2, bell.getZ() + 2) || p.contains(bell.getX() + 2, bell.getZ() + 2)) {
            // A solid call point also gives the arriving player a clear deck.
            for (int x = bell.getX() - 2; x <= bell.getX() + 2; x++) {
                for (int z = bell.getZ() - 2; z <= bell.getZ() + 2; z++) {
                    p.set(x, bell.getY() - 1, z, Blocks.SPRUCE_PLANKS);
                    for (int y = bell.getY(); y < bell.getY() + 3; y++) p.set(x, y, z, Blocks.AIR);
                }
            }
            p.set(bell.getX(), bell.getY(), bell.getZ(), Blocks.BELL);
        }
    }

    private static final class Builder {
        private final ChunkAccess chunk;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        Builder(ChunkAccess chunk) { this.chunk = chunk; }

        boolean contains(int x, int z) {
            return (x >> 4) == chunk.getPos().x && (z >> 4) == chunk.getPos().z;
        }

        void set(int x, int y, int z, Block block) { set(x, y, z, block.defaultBlockState()); }

        void set(int x, int y, int z, BlockState state) {
            if (contains(x, z)) chunk.setBlockState(pos.set(x, y, z), state, false);
        }

        void route(Route route, boolean edge, EdgeTerrain.Layout layout) {
            int x = route.x, shaft = route.shaftZ, step = route.facing.getStepZ();
            if (chunk.getPos().getMaxBlockZ() < Math.min(route.startZ, Math.min(shaft, route.endZ)) - 1
                    || chunk.getPos().getMinBlockZ() > Math.max(route.startZ, Math.max(shaft, route.endZ)) + 1) return;
            if (chunk.getPos().getMaxBlockX() < x - 2 || chunk.getPos().getMinBlockX() > x + 2) return;
            boolean formal = route.facing == Direction.SOUTH;
            Block deck = formal ? Blocks.MOSSY_STONE_BRICKS : Blocks.SPRUCE_PLANKS;
            Block rail = formal ? Blocks.COBBLESTONE_WALL : Blocks.SPRUCE_FENCE;
            Block support = formal ? OrthBlocks.COBBLE.get() : Blocks.STRIPPED_SPRUCE_LOG;
            for (int z = Math.min(route.startZ, shaft - step); z <= Math.max(route.startZ, shaft - step); z++) {
                if ((z >> 4) != chunk.getPos().z) continue;
                int y = route.top - (formal ? Math.max(0, (z - route.startZ) / 2) : 0);
                deck(x, y, z, deck, rail);
                if (Math.floorMod(z, 8) == 0) set(x + 2, y + 1, z, Blocks.LANTERN);
            }
            int highest = route.top - (formal ? Math.max(0, (shaft - step - route.startZ) / 2) : 0);
            if (edge) {
                for (int z = Math.min(shaft, route.endZ); z <= Math.max(shaft, route.endZ); z++) {
                    if ((z >> 4) != chunk.getPos().z) continue;
                    int floor = route.bottom;
                    if (layout.wildEdge()) {
                        int offset = Math.abs(z - route.endZ);
                        var landing = EdgeLandscape.at(x, route.endZ, -route.bottom, layout);
                        if (landing != null) {
                            int difference = landing.top() - floor;
                            int ramp = Math.abs(z - shaft) / 2;
                            floor += Math.max(-ramp, Math.min(ramp, difference));
                        }
                        if (offset < 9) {
                            landingTerrace(x, floor, z, offset, deck, rail);
                            continue;
                        }
                    }
                    deck(x, floor, z, deck, rail);
                }
                set(x + 2, route.bottom + 1, shaft, Blocks.LANTERN);
            }
            if (contains(x, shaft) || contains(x, shaft - step) || contains(x, shaft + step)) {
                BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, route.facing);
                int low = edge ? route.bottom + 1 : Math.max(-8, route.bottom + 1);
                for (int y = low; y <= highest + 1; y++) {
                    set(x, y, shaft - step, support);
                    set(x, y, shaft, ladder);
                    set(x, y, shaft + step, Blocks.AIR);
                }
                set(x, highest + 1, shaft - step, Blocks.AIR);
                set(x, highest + 1, shaft, Blocks.AIR);
            }
        }

        void landingTerrace(int x, int y, int z, int endDistance, Block deck, Block rail) {
            for (int dx = -2; dx <= 2; dx++) {
                int roll = EdgeExploration.hash(x + dx, y, z) & 7;
                Block floor = endDistance < 2 || (endDistance < 6 && roll < 2)
                        ? Blocks.COARSE_DIRT : roll < 3 ? Blocks.MOSSY_COBBLESTONE : deck;
                set(x + dx, y, z, floor);
                set(x + dx, y - 1, z, Blocks.TUFF);
                set(x + dx, y + 1, z, Math.abs(dx) == 2 && endDistance >= 4 && roll > 2 ? rail : Blocks.AIR);
                set(x + dx, y + 2, z, Blocks.AIR);
            }
        }

        void deck(int x, int y, int z, Block deck, Block rail) {
            for (int dx = -2; dx <= 2; dx++) {
                set(x + dx, y, z, deck);
                set(x + dx, y + 1, z, Math.abs(dx) == 2 ? rail : Blocks.AIR);
                set(x + dx, y + 2, z, Blocks.AIR);
            }
        }
    }

    private OrthDescent() {}
}
