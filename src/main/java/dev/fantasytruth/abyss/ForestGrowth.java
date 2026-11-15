package dev.fantasytruth.abyss;

import dev.fantasytruth.terrain.AtlasField;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Deterministic, chunk-clipped vegetation and structures at the scale of the forest. */
final class ForestGrowth {
    private static final int SPACING = 48, REACH = 50;
    private static final int CURRENT_SPACING = 272, CURRENT_WIDTH = 3;
    private record Spring(int x, int z, int top) {}
    private record GroveTree(int x, int z, int variant) {}
    private record RimTree(int x, int y, int z) {}

    private enum Hanging {
        ROOF, INLET, SHELF, LEDGE;

        int bottom(ForestTerrain.Column c) {
            return switch (this) {
                case ROOF -> c.roofAttachment();
                case INLET -> c.inletAttachment();
                case SHELF -> c.shelfBottom();
                case LEDGE -> c.ledgeBottom();
            };
        }

        int top(ForestTerrain.Column c) {
            return switch (this) {
                case ROOF, INLET -> c.roofTop();
                case SHELF -> c.shelfTop();
                case LEDGE -> c.ledgeTop();
            };
        }
    }

    private static final List<GroveTree> RIM_TREES = rimTrees();
    private static final List<GroveTree> INLET_TREES = inletTrees();

    private static final BlockState LEAVES = leaves(Blocks.JUNGLE_LEAVES);
    private static final BlockState ROOT = Blocks.MANGROVE_ROOTS.defaultBlockState();
    private static final BlockState LOG = Blocks.DARK_OAK_WOOD.defaultBlockState();

    static void place(ChunkAccess chunk, ForestTerrain terrain) {
        var p = new Writer(chunk);
        canopy(p, terrain);
        groundCover(p, terrain);
        if (p.intersects(ForestTerrain.FALL_X, ForestTerrain.FALL_Z, 6)) reverseFall(p);
        springs(p, terrain);
        ForestSites.place(p, terrain);
        ForestLandmarks.place(p, terrain);
        ForestSpring.place(p, terrain);
        if (p.intersects(ForestTerrain.CAMP_X, ForestTerrain.CAMP_Z, 84)) SeekerCamp.place(p);
    }

    static void previewCanopy(Writer p, ForestTerrain terrain) {
        canopy(p, terrain);
        groundCover(p, terrain);
    }

    private static void canopy(Writer p, ForestTerrain terrain) {
        for (int gz = Math.floorDiv(p.z - REACH, SPACING); gz <= Math.floorDiv(p.z + 15 + REACH, SPACING); gz++) {
            for (int gx = Math.floorDiv(p.x - REACH, SPACING); gx <= Math.floorDiv(p.x + 15 + REACH, SPACING); gx++) {
                var rng = new SplittableRandom(AtlasField.hash(terrain.seed, gx, 205, gz));
                int x = gx * SPACING + rng.nextInt(8, SPACING - 8);
                int z = gz * SPACING + rng.nextInt(8, SPACING - 8);
                if (!p.intersects(x, z, REACH) || AbyssLayer.passage(x, z, 63)) continue;
                var c = terrain.column(x, z);
                double millDistance = Math.hypot(x - ForestTerrain.FIRST_MILL_X, z - ForestTerrain.FIRST_MILL_Z);
                if (!terrain.deep && c.shelfTop() > 160 && millDistance > 35 && anchored(terrain, x, z, c)) {
                    int roof = c.roofTop() < ForestTerrain.MIN_Y ? 697 : c.roofBottom();
                    int height = Math.min(roof - c.shelfTop() - 10, rng.nextInt(46, 124));
                    if (height >= 25) tree(p, x, c.shelfTop(), z, height, 1, true, rng, terrain, null);
                }
                if (Math.hypot(x - ForestTerrain.CAMP_X, z - ForestTerrain.CAMP_Z) < 61
                        || Math.hypot(x - ForestTerrain.FALL_X, z - ForestTerrain.FALL_Z) < 24) continue;
                int ceiling = c.ledgeBottom();
                if (ceiling > ForestTerrain.MIN_Y && ceiling - c.floor() > 85
                        && anchorBelow(terrain, x, z, ceiling, Hanging.LEDGE)) {
                    int drop = ceiling - c.floor();
                    int height = Math.min(drop - 12, (int) (drop * rng.nextDouble(0.59, 0.86)));
                    tree(p, x, ceiling, z, height, -1, false, rng, terrain, Hanging.LEDGE);
                } else if (!terrain.deep && rng.nextInt(3) == 0 && !ForestSpring.clearsFloor(terrain, x, z)) {
                    int overhead = ceiling <= ForestTerrain.MIN_Y ? c.shelfBottom() : ceiling;
                    int height = Math.min(overhead - c.floor() - 12, rng.nextInt(36, 98));
                    if (height >= 25) tree(p, x, c.floor(), z, height, 1, false, rng, terrain, null);
                }
                int highDrop = c.shelfBottom() - Math.max(c.ledgeTop(), c.floor());
                if (c.shelfBottom() > 145 && highDrop > 121 && rng.nextInt(4) < (terrain.deep ? 1 : 2)
                        && anchorBelow(terrain, x, z, c.shelfBottom(), Hanging.SHELF)) {
                    int height = Math.min(highDrop - 17, rng.nextInt(89, 174));
                    tree(p, x, c.shelfBottom(), z, height, -1, false, rng, terrain, Hanging.SHELF);
                }
            }
        }
        for (var grove : RIM_TREES) {
            if (terrain.deep && grove.variant() % 3 != 0 || !p.intersects(grove.x(), grove.z(), REACH + 30)) continue;
            var site = rimTree(terrain, grove);
            if (site == null || !p.intersects(site.x(), site.z(), REACH)) continue;
            var rng = new SplittableRandom(AtlasField.hash(terrain.seed, grove.x(), 811, grove.z()));
            int height = Math.max(site.y() - rng.nextInt(720, 765), rng.nextInt(78, 119) + grove.variant());
            tree(p, site.x(), site.y(), site.z(), height, -1, false, rng, terrain, Hanging.ROOF);
        }
        for (var grove : INLET_TREES) {
            if (!p.intersects(grove.x(), grove.z(), REACH)) continue;
            int anchor = terrain.column(grove.x(), grove.z()).inletAttachment();
            if (anchor <= ForestTerrain.MIN_Y) continue;
            var rng = new SplittableRandom(AtlasField.hash(terrain.seed, grove.x(), 812, grove.z()));
            int height = Math.max(anchor - rng.nextInt(688, 703), rng.nextInt(62, 87));
            tree(p, grove.x(), anchor, grove.z(), height, -1, false, rng, terrain, Hanging.INLET);
        }
        if (terrain.deep) ForestCanopy.place(p, terrain);
    }

    private static List<GroveTree> rimTrees() {
        var trees = new ArrayList<GroveTree>();
        for (int ring = 0; ring < 2; ring++) for (int i = 0; i < 15; i++) {
            double angle = i * (Math.PI * 2 / 15) + ring * 0.27 + 0.13 * Math.sin(i * 2.37);
            double cos = Math.cos(angle), sin = Math.sin(angle);
            for (int radius = 88; radius <= 241; radius += 3) {
                int dx = (int) Math.round(radius * cos), dz = (int) Math.round(radius * sin);
                if (ForestMouth.opening(dx, dz) || ForestMouth.roofEdge(dx, dz) < 17 + 43 * ring + i % 4 * 4) continue;
                trees.add(new GroveTree(dx + AbyssLayer.CENTER_X, dz + AbyssLayer.CENTER_Z, (i % 4) * 6 + ring * 9));
                break;
            }
        }
        return List.copyOf(trees);
    }

    private static List<GroveTree> inletTrees() {
        var trees = new ArrayList<GroveTree>();
        for (int i = 0; i < 11; i++) {
            double angle = Math.PI * 2 * i / 11 + 0.17 + 0.16 * Math.sin(i * 1.87);
            for (int radius = 72; radius <= 163; radius += 3) {
                int x = (int) Math.round(radius * Math.cos(angle)), z = (int) Math.round(radius * Math.sin(angle));
                if (ForestMouth.entranceEdge(x, z) < 20) continue;
                var ledges = ForestMouth.shelves(x, z, Math.hypot(x, z));
                if (ForestMouth.inletAttachment(ledges) <= ForestTerrain.MIN_Y) continue;
                trees.add(new GroveTree(x + AbyssLayer.CENTER_X, z + AbyssLayer.CENTER_Z, i));
                break;
            }
        }
        return List.copyOf(trees);
    }

    private static RimTree rimTree(ForestTerrain terrain, GroveTree grove) {
        int dx = OrthIsland.localX(grove.x()), dz = OrthIsland.localZ(grove.z());
        double radius = Math.hypot(dx, dz);
        for (int offset = 0; offset <= 30; offset += 5) {
            int x = grove.x() + (int) Math.round(dx * offset / radius);
            int z = grove.z() + (int) Math.round(dz * offset / radius);
            int anchor = terrain.column(x, z).roofAttachment();
            if (anchor >= 753) return new RimTree(x, anchor, z);
        }
        return null;
    }

    private static boolean anchorBelow(ForestTerrain terrain, int x, int z, int ceiling, Hanging surface) {
        int supported = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            var neighbor = terrain.column(x + side.getStepX() * 4, z + side.getStepZ() * 4);
            if (surface.bottom(neighbor) <= ceiling + 9 && surface.top(neighbor) >= ceiling) supported++;
        }
        return supported >= 2;
    }

    private static boolean anchored(ForestTerrain terrain, int x, int z, ForestTerrain.Column center) {
        if (center.shelfTop() <= ForestTerrain.MIN_Y) return false;
        if (center.water() > center.shelfTop()) return false;
        int supported = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            var neighbor = terrain.column(x + side.getStepX() * 5, z + side.getStepZ() * 5);
            if (neighbor.shelfTop() >= center.shelfBottom() && neighbor.shelfBottom() <= center.shelfTop()) supported++;
        }
        return supported >= 3;
    }

    private static void tree(Writer p, int x, int y, int z, int height, int direction, boolean parasol,
                             SplittableRandom rng, ForestTerrain terrain, Hanging hanging) {
        int radius = parasol ? rng.nextInt(21, 34) : rng.nextInt(12, 20);
        int stem = direction < 0 && height >= 140 ? 7 : height >= 100 ? 5 : height >= 58 ? 4 : 3;
        double angle = Math.atan2(AbyssLayer.CENTER_Z - z, AbyssLayer.CENTER_X - x);
        if (direction < 0) angle += hanging == Hanging.ROOF ? rng.nextDouble(-0.52, 0.52) : rng.nextDouble(-1.5, 1.5);
        int bend = direction < 0 ? rng.nextInt(10, 17) : 8;
        int leanX = (int) Math.round(Math.cos(angle) * bend), leanZ = (int) Math.round(Math.sin(angle) * bend);
        int previousX = x, previousZ = z;
        for (int h = 1; h <= height; h++) {
            double t = h / (double) height;
            int r = Math.max(1, (int) (stem * (1.0 - t * 0.77) + (h < 12 ? (12 - h) * 0.21 : 0)));
            int bx = x + (int) (leanX * t * t), bz = z + (int) (leanZ * t * t), by = y + direction * h;
            p.disc(bx, by, bz, r, r, Palette.TRUNK, false);
            if (bx != previousX || bz != previousZ) {
                p.grow(previousX, by, previousZ, Palette.TRUNK);
                p.grow(bx, by, previousZ, Palette.TRUNK);
            }
            previousX = bx;
            previousZ = bz;
        }
        int tipX = x + leanX, tipZ = z + leanZ, tipY = y + height * direction;
        if (parasol) {
            for (int h = -3; h <= 4; h++) {
                int r = radius - Math.abs(h) * 3;
                var cover = h == -3 ? ROOT : h > 0 ? LEAVES : Palette.DISC_LEAVES;
                p.disc(tipX, tipY + h, tipZ, r, r - 3, cover, false);
            }
            // The lower branch and its leaf clusters point inwards even where the canopy hides the sky.
            int bx = x + leanX * 3, bz = z + leanZ * 3;
            int by = y + height / 2;
            p.limb(x + leanX / 9, y + height / 3, z + leanZ / 9, bx, by, bz, 2, LOG);
            for (int h = -2; h <= 2; h++) p.disc(bx, by + h, bz, 8 - Math.abs(h), 6 - Math.abs(h), Palette.DISC_LEAVES, false);
            p.hang(bx, by - 3, bz, 6, false);
            for (int step = 2; step <= 4; step++) {
                int ax = x + leanX * step / 2, az = z + leanZ * step / 2;
                int ay = y + height / 3 + height * step / 36;
                for (int h = 2; h >= -2; h--) {
                    int base = ay + h;
                    if (p.attachAmagiri(ax, base, az)) break;
                }
            }
        } else {
            if (hanging != null) {
                for (int dy = 0; dy < 7; dy++) {
                    int w = 10 - dy;
                    p.disc(x, y - dy, z, w, w - 1, dy < 2 ? ROOT : LOG, false);
                }
                for (int dy = -5; dy <= 7; dy++) {
                    int w = 13 - Math.abs(dy);
                    p.disc(tipX, tipY + dy, tipZ, w, w - 1, Palette.DEEP_LEAVES, false);
                }
                p.hang(tipX, tipY - 6, tipZ, 12, true);
            }
            for (int b = 0; b < 5; b++) {
                double a = angle + b * 2.39996;
                int r = radius / 2 + rng.nextInt(2, direction < 0 ? 14 : 9);
                int bx = tipX + (int) (r * Math.cos(a)), bz = tipZ + (int) (r * Math.sin(a));
                int by = tipY - direction * rng.nextInt(3, 21);
                int base = by - direction * 13;
                double lean = (base - y) / (double) (height * direction);
                p.limb(x + (int) (leanX * lean * lean), base, z + (int) (leanZ * lean * lean), bx, by, bz, 2, LOG);
                int crown = direction < 0 ? rng.nextInt(9, 14) : 7;
                for (int h = -4; h <= 4; h++) {
                    int width = crown - Math.abs(h);
                    var cover = direction < 0 && (b & 1) == 0 ? Palette.DEEP_LEAVES : LEAVES;
                    p.disc(bx, by + h, bz, width, width - 1, cover, false);
                }
                int vineLength = rng.nextInt(6, direction < 0 ? 20 : 13);
                p.hang(bx, by - 5, bz, vineLength, (b & 1) == 0);
            }
        }
        for (int i = 0; i < 5; i++) {
            double a = angle + i * 1.2566;
            int rx = x + (int) (12 * Math.cos(a)), rz = z + (int) (12 * Math.sin(a));
            if (!p.intersects(rx, rz, 15)) continue;
            var anchor = terrain.column(rx, rz);
            int soil = hanging != null ? hanging.bottom(anchor) : parasol ? anchor.shelfTop() : anchor.floor();
            if (Math.abs(soil - y) > 10) continue;
            p.limb(x, y + 9 * direction, z, rx, soil, rz, 2, ROOT);
        }
    }

    private static void groundCover(Writer p, ForestTerrain terrain) {
        for (int z = p.z; z < p.z + 16; z += 2) for (int x = p.x; x < p.x + 16; x += 2) {
            int hash = (int) AtlasField.hash(terrain.seed, x, 49, z);
            if ((hash & 3) == 0) continue;
            var c = terrain.column(x, z);
            if (!c.chute() && c.floorWater() == c.floor()) {
                if (c.damp() && (hash & 15) == 1) {
                    p.replace(x, c.floor(), z, Blocks.MOSS_BLOCK, Palette.MOSS);
                    p.replace(x + 1, c.floor(), z, Blocks.MOSS_BLOCK, Palette.MOSS);
                    p.replace(x, c.floor(), z + 1, Blocks.MOSS_BLOCK, Palette.MOSS);
                }
                p.plant(x, c.floor() + 1, z, c.damp() && (hash & 7) < 4 ? Palette.GLOW : Blocks.FERN.defaultBlockState());
            }
            if (c.ledgeBottom() > ForestTerrain.MIN_Y) {
                if (c.damp() && (hash & 7) < 3) {
                    p.hang(x, c.ledgeBottom() - 1, z, 5 + (hash >>> 9 & 15), (hash & 16) == 0);
                }
                if ((hash & 15) == 1) p.plant(x, c.ledgeTop() + 1, z, Palette.FORTUNE);
            }
            if (c.shelfTop() < ForestTerrain.MIN_Y || c.water() > c.shelfTop()) continue;
            BlockState plant = c.damp() && (hash & 15) < 5 ? Palette.GLOW
                    : (hash & 7) < 6 ? AmagiriBlock.towardCenter(x, z) : Blocks.FERN.defaultBlockState();
            p.plant(x, c.shelfTop() + 1, z, plant);
            if (c.shelfBottom() < ForestTerrain.INVERTED && (hash & 7) < (c.damp() ? 5 : 2)) {
                p.hang(x, c.shelfBottom() - 1, z, 5 + (hash >>> 9 & 15), (hash & 16) == 0);
            }
        }
    }

    // Use the updraft material for the water ribbon so vanilla fluid ticks cannot pull it down.
    private static void reverseFall(Writer p) {
        for (int y = ForestTerrain.FALL_BOTTOM; y <= ForestTerrain.FALL_TOP; y++) {
            int x = fallX(y), z = fallZ(y);
            int width = y > ForestTerrain.FALL_TOP - 16 ? 2 : 4;
            p.disc(x, y, z, width, 2, Palette.CURRENT, false);
            if (y == ForestTerrain.FALL_BOTTOM) continue;
            int previousX = fallX(y - 1), previousZ = fallZ(y - 1);
            if (previousX != x || previousZ != z) {
                p.grow(previousX, y, previousZ, Palette.CURRENT);
                p.grow(x, y, previousZ, Palette.CURRENT);
            }
        }
    }

    private static void springs(Writer p, ForestTerrain terrain) {
        // Regional springs terminate under a shelf where spray disperses into the cavern.
        for (int gz = Math.floorDiv(p.z - CURRENT_WIDTH, CURRENT_SPACING);
             gz <= Math.floorDiv(p.z + 15 + CURRENT_WIDTH, CURRENT_SPACING); gz++) {
            for (int gx = Math.floorDiv(p.x - CURRENT_WIDTH, CURRENT_SPACING);
                 gx <= Math.floorDiv(p.x + 15 + CURRENT_WIDTH, CURRENT_SPACING); gx++) {
                var rng = new SplittableRandom(AtlasField.hash(terrain.seed, gx, 723, gz));
                int x = gx * CURRENT_SPACING + rng.nextInt(25, CURRENT_SPACING - 25);
                int z = gz * CURRENT_SPACING + rng.nextInt(25, CURRENT_SPACING - 25);
                if (AbyssLayer.passage(x, z, 405) || !p.intersects(x, z, CURRENT_WIDTH)) continue;
                var c = terrain.column(x, z);
                int top = c.ledgeBottom() - 5;
                if (top - c.floor() < 130) continue;
                poolAndCurrent(p, terrain, new Spring(x, z, top));
            }
        }
    }

    private static void poolAndCurrent(Writer p, ForestTerrain terrain, Spring spring) {
        for (int z = Math.max(p.z, spring.z - CURRENT_WIDTH); z <= Math.min(p.z + 15, spring.z + CURRENT_WIDTH); z++) {
            for (int x = Math.max(p.x, spring.x - CURRENT_WIDTH); x <= Math.min(p.x + 15, spring.x + CURRENT_WIDTH); x++) {
                int dx = x - spring.x, dz = z - spring.z;
                int radial = dx * dx + dz * dz;
                if (radial > CURRENT_WIDTH * CURRENT_WIDTH) continue;
                var c = terrain.column(x, z);
                if (radial <= 5) {
                    int top = Math.min(spring.top - radial / 2, c.ledgeBottom() - 2);
                    for (int y = c.floor() + 1; y <= top; y++) p.grow(x, y, z, Palette.CURRENT);
                }
                p.replace(x, c.floor(), z, Blocks.MOSS_BLOCK, Palette.MOSS);
            }
        }
    }

    private static int fallX(int y) { return ForestTerrain.FALL_X + (int) (3 * Math.sin(y * 0.024)); }
    private static int fallZ(int y) { return ForestTerrain.FALL_Z + (int) (2 * Math.cos(y * 0.031)); }

    static void tickCurrent(ServerPlayer player) {
        int y = player.getBlockY();
        BlockPos pos = player.blockPosition();
        if (!player.serverLevel().getBlockState(pos).is(Palette.CURRENT.getBlock())) {
            if (!inCurrent(player.getX(), y, player.getZ())) return;
            pos = new BlockPos(fallX(y), y, fallZ(y));
            if (!player.serverLevel().getBlockState(pos).is(Palette.CURRENT.getBlock())) return;
        }
        var speed = player.getDeltaMovement();
        if (!player.isShiftKeyDown()) {
            player.setDeltaMovement(speed.x, Math.min(0.48, Math.max(0.14, speed.y + 0.11)), speed.z);
            player.hurtMarked = true;
        }
        player.fallDistance = 0;
        if (player.tickCount % 4 == 0) {
            player.serverLevel().sendParticles(ParticleTypes.CLOUD,
                    player.getX(), y + 0.5, player.getZ(), 5, 1.2, 0.8, 1.2, 0.09);
        }
    }

    private static boolean inCurrent(double x, int y, double z) {
        return y >= ForestTerrain.FALL_BOTTOM && y <= ForestTerrain.FALL_TOP
                && Math.abs(x - fallX(y) - 0.5) < 4 && Math.abs(z - fallZ(y) - 0.5) < 4;
    }

    static BlockState leaves(Block block) {
        return block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    static final class Palette {
        static final BlockState CURRENT = block("l2_updraft");
        static final BlockState DISC_LEAVES = leaves(Blocks.AZALEA_LEAVES);
        static final BlockState DEEP_LEAVES = leaves(Blocks.DARK_OAK_LEAVES);
        static final BlockState MOSS = block("glow_moss");
        static final BlockState TRUNK = block("l2_giant_trunk");
        static final BlockState VINE = block("l2_upside_vine");
        static final BlockState FUNGUS = block("l2_fungus_cap");
        static final BlockState FORTUNE = block("eternal_fortune");
        static final BlockState GLOW = block("glow_shroom");

        static BlockState block(String id) {
            return BuiltInRegistries.BLOCK.get(new ResourceLocation("stratacubes", id)).defaultBlockState();
        }
    }

    static final class Writer {
        private final ChunkAccess chunk;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final int x, z;
        private final int offset, lowest, highest;
        private final boolean[] previewColumns;
        Writer(ChunkAccess chunk) {
            this(chunk, 0, chunk.getMinBuildHeight(), chunk.getMaxBuildHeight() - 1, null);
        }

        Writer(ChunkAccess chunk, int offset, int lowest, int highest, boolean[] previewColumns) {
            this.chunk = chunk;
            this.offset = offset;
            this.lowest = lowest;
            this.highest = highest;
            this.previewColumns = previewColumns;
            x = chunk.getPos().getMinBlockX();
            z = chunk.getPos().getMinBlockZ();
        }

        private boolean includesY(int y) { return y >= lowest && y <= highest; }
        private BlockPos.MutableBlockPos locate(int x, int y, int z) { return pos.set(x, y + offset, z); }

        boolean intersects(int bx, int bz, int r) { return bx + r >= x && bx - r <= x + 15 && bz + r >= z && bz - r <= z + 15; }
        boolean inside(int bx, int y, int bz) {
            return bx >= x && bx <= x + 15 && bz >= z && bz <= z + 15 && includesY(y)
                    && (previewColumns == null || previewColumns[((bz - z) << 4) + bx - x]);
        }

        void set(int bx, int y, int bz, BlockState state) {
            if (inside(bx, y, bz)) chunk.setBlockState(locate(bx, y, bz), state, false);
        }

        void tallPlant(int x, int y, int z, BlockState lower, BlockState upper) {
            if (!inside(x, y + 1, z) || !chunk.getBlockState(locate(x, y, z)).isAir()
                    || !chunk.getBlockState(locate(x, y + 1, z)).isAir()) return;
            set(x, y, z, lower);
            set(x, y + 1, z, upper);
        }

        void grow(int bx, int y, int bz, BlockState state) {
            if (inside(bx, y, bz) && chunk.getBlockState(locate(bx, y, bz)).isAir()) chunk.setBlockState(pos, state, false);
        }

        void replace(int x, int y, int z, Block previous, BlockState state) {
            if (inside(x, y, z) && chunk.getBlockState(locate(x, y, z)).is(previous)) chunk.setBlockState(pos, state, false);
        }

        boolean attachAmagiri(int x, int y, int z) {
            if (!inside(x, y + 1, z) || !chunk.getBlockState(locate(x, y, z)).is(Blocks.DARK_OAK_WOOD)) return false;
            grow(x, y + 1, z, AmagiriBlock.towardCenter(x, z));
            return true;
        }

        void plant(int x, int y, int z, BlockState state) {
            if (!inside(x, y, z) || !chunk.getBlockState(locate(x, y - 1, z)).is(Blocks.MOSS_BLOCK)) return;
            grow(x, y, z, state);
        }

        void hang(int x, int y, int z, int length, boolean glowing) {
            if (!inside(x, y, z)) return;
            var anchor = chunk.getBlockState(locate(x, y + 1, z));
            if (!anchor.blocksMotion() && !anchor.is(BlockTags.LEAVES)) return;
            for (int dy = 0; dy < length && inside(x, y - dy, z); dy++) {
                var state = chunk.getBlockState(locate(x, y - dy, z));
                if (!state.isAir()) break;
                chunk.setBlockState(pos, glowing && dy == length - 1 ? Palette.FUNGUS : Palette.VINE, false);
            }
        }

        void disc(int bx, int y, int bz, int rx, int rz, BlockState state, boolean force) {
            if (!includesY(y)) return;
            for (int nz = Math.max(z, bz - rz); nz <= Math.min(z + 15, bz + rz); nz++) {
                for (int nx = Math.max(x, bx - rx); nx <= Math.min(x + 15, bx + rx); nx++) {
                    double dx = (nx - bx) / (double) rx, dz = (nz - bz) / (double) rz;
                    if (dx * dx + dz * dz > 1) continue;
                    if (force) set(nx, y, nz, state);
                    else grow(nx, y, nz, state);
                }
            }
        }

        void limb(int x, int y, int z, int tx, int ty, int tz, int radius, BlockState state) {
            int steps = Math.max(1, Math.max(Math.abs(ty - y), Math.max(Math.abs(tx - x), Math.abs(tz - z))));
            for (int i = 0; i <= steps; i++) {
                disc(x + (tx - x) * i / steps, y + (ty - y) * i / steps, z + (tz - z) * i / steps, radius, radius, state, false);
            }
        }

        void spawner(int x, int y, int z, String entity) {
            if (!inside(x, y, z)) return;
            set(x, y, z, Blocks.SPAWNER.defaultBlockState());
            var tag = new CompoundTag();
            tag.putString("id", "minecraft:mob_spawner");
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            tag.putShort("Delay", (short) 30);
            tag.putShort("MinSpawnDelay", (short) 400);
            tag.putShort("MaxSpawnDelay", (short) 700);
            tag.putShort("SpawnCount", (short) 2);
            tag.putShort("MaxNearbyEntities", (short) 3);
            tag.putShort("RequiredPlayerRange", (short) 30);
            tag.putShort("SpawnRange", (short) 5);
            var spawn = new CompoundTag();
            var entry = new CompoundTag();
            entry.putString("id", entity);
            spawn.put("entity", entry);
            var range = new CompoundTag();
            range.putInt("min_inclusive", 0);
            range.putInt("max_inclusive", 15);
            var light = new CompoundTag();
            light.put("sky_light_limit", range);
            light.put("block_light_limit", range.copy());
            spawn.put("custom_spawn_rules", light);
            tag.put("SpawnData", spawn);
            chunk.setBlockEntityNbt(tag);
        }

        void journal(int x, int y, int z) {
            if (!inside(x, y, z)) return;
            set(x, y, z, Blocks.LECTERN.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LecternBlock.HAS_BOOK, true));
            var pages = new net.minecraft.nbt.ListTag();
            for (int page = 1; page <= 3; page++) {
                var text = net.minecraft.network.chat.Component.translatable("book.stratacubes.forest." + page);
                pages.add(net.minecraft.nbt.StringTag.valueOf(net.minecraft.network.chat.Component.Serializer.toJson(text)));
            }
            var notes = new CompoundTag();
            notes.putString("title", "Seeker Camp");
            notes.putString("author", "Seeker Camp");
            notes.put("pages", pages);
            var book = new CompoundTag();
            book.putString("id", "minecraft:written_book");
            book.putByte("Count", (byte) 1);
            book.put("tag", notes);
            var tag = new CompoundTag();
            tag.putString("id", "minecraft:lectern");
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            tag.put("Book", book);
            chunk.setBlockEntityNbt(tag);
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
            chunk.setBlockEntityNbt(tag);
        }

        void ladder(int bx, int from, int to, int bz, Direction facing) {
            var ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing);
            for (int y = from; y <= to; y++) set(bx, y, bz, ladder);
        }
    }

    private ForestGrowth() {}
}
