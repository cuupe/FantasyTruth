package dev.fantasytruth.abyss;

import dev.fantasytruth.abyss.ForestGrowth.Writer;
import dev.fantasytruth.terrain.AtlasField;
import java.util.SplittableRandom;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/** Clearings in the lower forest, with irregular spring banks and sheltered roots. */
final class ForestSpring {
    private static final int X = 295, Z = -142;
    private static final int REACH = 23, CELL = 320;

    record Site(int x, int z, int water) {}

    static Site chooseFirst(ForestTerrain terrain) {
        for (int distance = 0; distance <= 224; distance += 28) {
            int steps = distance == 0 ? 1 : 12;
            for (int i = 0; i < steps; i++) {
                double angle = (0.18 + i / (double) steps) * Math.PI * 2;
                int x = X + (int) Math.round(distance * Math.cos(angle));
                int z = Z + (int) Math.round(distance * Math.sin(angle));
                if (z > -90 || AbyssLayer.passage(x, z, 248)) continue;
                var c = terrain.column(x, z);
                if (suitable(terrain, x, z, c)) return new Site(x, z, c.floor() + 1);
            }
        }
        return new Site(X, Z, terrain.column(X, Z).floor() + 1);
    }

    private static boolean suitable(ForestTerrain terrain, int x, int z, ForestTerrain.Column center) {
        if (center.chute() || center.floorWater() > center.floor() + 2 || !open(center, center.floor())) return false;
        for (int dz = -12; dz <= 12; dz += 12) for (int dx = -12; dx <= 12; dx += 12) {
            if (dx == 0 && dz == 0) continue;
            var c = terrain.column(x + dx, z + dz);
            if (Math.abs(c.floor() - center.floor()) > 11 || !open(c, c.floor())) return false;
        }
        return true;
    }

    private static boolean open(ForestTerrain.Column column, int y) {
        return (column.shelfTop() <= y || column.shelfBottom() >= y + 28)
                && (column.ledgeTop() <= y || column.ledgeBottom() >= y + 28);
    }

    private static Site regional(ForestTerrain terrain, int x, int z) {
        if (AbyssLayer.passage(x, z, 389)) return null;
        int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
        var random = new SplittableRandom(AtlasField.hash(terrain.seed, cx, 1309, cz));
        if (random.nextInt(3) != 0) return null;
        int sx = cx * CELL + random.nextInt(46, CELL - 46);
        int sz = cz * CELL + random.nextInt(46, CELL - 46);
        if (AbyssLayer.passage(sx, sz, 446) || Math.abs(sx - x) > REACH + 15 || Math.abs(sz - z) > REACH + 15) return null;
        var c = terrain.column(sx, sz);
        if (!c.damp() || c.floor() < terrain.exitY() + 30 || !suitable(terrain, sx, sz, c)) return null;
        return new Site(sx, sz, c.floor() + 1);
    }

    private static double radius(ForestTerrain terrain, Site site, int x, int z) {
        return Math.hypot(x - site.x, (z - site.z) * 1.23) + (AtlasField.hash(terrain.seed, x, 815, z) & 3) * 0.39;
    }

    static boolean clearsFloor(ForestTerrain terrain, int x, int z) {
        var fixed = terrain.spring;
        if (Math.abs(x - fixed.x) <= 19 && Math.abs(z - fixed.z) <= 19 && radius(terrain, fixed, x, z) < 19) return true;
        var site = regional(terrain, x, z);
        return site != null && radius(terrain, site, x, z) < 19;
    }

    static int waterLine(ForestTerrain terrain, int x, int z) {
        if (Math.abs(x - ForestLandmarks.POND_X) <= 22 && Math.abs(z - ForestLandmarks.POND_Z) <= 22) {
            double r = Math.hypot(x - ForestLandmarks.POND_X, (z - ForestLandmarks.POND_Z) * 1.22);
            if (r < 21) return terrain.column(ForestLandmarks.POND_X, ForestLandmarks.POND_Z).floor() + 2;
        }
        var fixed = terrain.spring;
        if (Math.abs(x - fixed.x) <= 13 && Math.abs(z - fixed.z) <= 13 && radius(terrain, fixed, x, z) < 12)
            return fixed.water;
        var site = regional(terrain, x, z);
        return site != null && radius(terrain, site, x, z) < 12 ? site.water : Integer.MIN_VALUE;
    }

    static void place(Writer p, ForestTerrain terrain) {
        var fixed = terrain.spring;
        if (p.intersects(fixed.x, fixed.z, REACH)) place(p, terrain, fixed);
        var site = regional(terrain, p.x, p.z);
        if (site != null && p.intersects(site.x, site.z, REACH)) place(p, terrain, site);
    }

    private static void place(Writer p, ForestTerrain terrain, Site site) {
        int water = site.water;
        var tuff = Blocks.TUFF.defaultBlockState();
        var wetStone = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        var soil = Blocks.ROOTED_DIRT.defaultBlockState();
        var air = Blocks.AIR.defaultBlockState();
        var fresh = Blocks.WATER.defaultBlockState();
        for (int z = Math.max(p.z, site.z - REACH); z <= Math.min(p.z + 15, site.z + REACH); z++) {
            for (int x = Math.max(p.x, site.x - REACH); x <= Math.min(p.x + 15, site.x + REACH); x++) {
                double r = radius(terrain, site, x, z);
                if (r > 19) continue;
                var col = terrain.column(x, z);
                int floor = col.floor();
                if (col.chute() || Math.abs(floor - water) > 13) continue;
                int roll = (int) AtlasField.hash(terrain.seed, x, water, z);
                int bed = r < 12 ? water - 2 - (int) (3 * (1 - r / 12))
                        : (int) (water + 1 + (floor - water - 1) * ForestTerrain.smooth(13, 19, r));
                for (int y = Math.min(floor, bed); y <= bed; y++) p.set(x, y, z, r < 12 ? tuff : (roll & 7) < 3 ? wetStone : soil);
                for (int y = bed + 1; y <= Math.max(bed + 2, Math.max(floor + 2, r < 12 ? water : bed)); y++)
                    p.set(x, y, z, r < 12 && y <= water ? fresh : air);
                if (r >= 12 && (roll & 7) < (r < 16 ? 5 : 2))
                    p.grow(x, bed + 1, z, (roll & 8) == 0 ? OrthBlocks.SPRING_MOSS.get().defaultBlockState()
                            : OrthBlocks.SPRING_SEDGE.get().defaultBlockState());
            }
        }
        // A broken semicircle of buttressed trees leaves one unobstructed approach to the water.
        for (int i = 0; i < 5; i++) {
            double theta = 0.20 + i * 0.98 + (AtlasField.hash(terrain.seed, site.x, i, site.z) & 7) * 0.055;
            int x = site.x + (int) Math.round(19 * Math.cos(theta));
            int z = site.z + (int) Math.round(19 * Math.sin(theta));
            if (!p.intersects(x, z, 10)) continue;
            var col = terrain.column(x, z);
            if (col.chute() || Math.abs(col.floor() - water) > 10 || col.floorWater() > col.floor()) continue;
            tree(p, terrain, x, col.floor(), z, i);
        }
    }

    private static void tree(Writer p, ForestTerrain terrain, int x, int y, int z, int kind) {
        var bark = ForestGrowth.Palette.TRUNK;
        int height = 10 + kind % 3 * 3;
        p.limb(x, y, z, x, y + height, z, 1, bark);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int tx = x + direction.getStepX() * 4, tz = z + direction.getStepZ() * 4;
            var c = terrain.column(tx, tz);
            if (c.floorWater() == c.floor() && Math.abs(c.floor() - y) <= 4
                    && !clearsFloor(terrain, tx, tz)) {
                p.limb(x, y + 2, z, tx, c.floor(), tz, 1, bark);
            }
            if (kind % 2 == direction.get2DDataValue() % 2) {
                p.grow(x + direction.getStepX() * 2, y + 3 + (kind & 1), z + direction.getStepZ() * 2,
                        OrthBlocks.SHELF_FUNGUS.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, direction));
            }
        }
        var leaves = ForestGrowth.leaves(kind % 2 == 0 ? Blocks.JUNGLE_LEAVES : Blocks.DARK_OAK_LEAVES);
        for (int dy = -2; dy <= 2; dy++) p.disc(x, y + height + dy, z, 7 - Math.abs(dy), 6 - Math.abs(dy), leaves, false);
    }

    private ForestSpring() {}
}
