package dev.fantasytruth.abyss;

import dev.fantasytruth.terrain.TerrainField;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** A full-depth chamber with a collar shared with the surface and meadow shelves attached to its wall. */
final class EdgeTerrain {
    static final int MIN_Y = -1536;
    static final int MAX_Y = AbyssLayer.MAX_Y;
    static final int EXIT = -1350;
    static final int NO_SHELF = Integer.MIN_VALUE;
    private static final TerrainField FIELD = new TerrainField(0x61627973732d6c31L);

    static final List<Shelf> SHELVES = List.of(
            new Shelf(-2.25, 0.85, 206, -132, 49),
            new Shelf(0.52, 0.79, 252, -224, 47),
            new Shelf(2.34, 1.00, 187, -385, 64),
            new Shelf(-0.62, 0.94, 175, -555, 58),
            new Shelf(-2.52, 1.10, 232, -748, 66),
            new Shelf(1.54, 1.07, 192, -938, 53),
            new Shelf(0.08, 0.98, 153, -1124, 48),
            new Shelf(2.72, 0.83, 142, -1241, 39));

    static Column column(int x, int z) {
        return column(x, z, false);
    }

    static Column column(int x, int z, boolean expanded) {
        return column(x, z, expanded, false);
    }

    static Column column(int x, int z, boolean expanded, boolean forested) {
        int dx = OrthIsland.localX(x), dz = OrthIsland.localZ(z);
        double radius = OrthIsland.distance(dx, dz);
        boolean opening = OrthIsland.shaftColumn(dx, dz);
        int upper = opening ? -1 : OrthRelief.elevation(dx, dz);
        int bottom = opening ? -1462 + (int) (22 * FIELD.noise(dx, dz, 102, 9)) : -1;
        if (!opening) return new Column(upper, false, bottom, List.of());
        if (expanded) return new Column(upper, true, bottom, EdgeLandscape.shelves(dx, dz, radius, forested),
                EdgeLandscape.scarps(dx, dz, radius));
        if (radius < 133) return new Column(upper, true, bottom, List.of());
        var shelves = new ArrayList<Span>(3);
        double angle = Math.atan2(dz, dx);
        for (int i = 0; i < SHELVES.size(); i++) {
            Span span = span(i, dx, dz, radius, angle);
            if (span != null) shelves.add(span);
        }
        return new Column(upper, true, bottom, List.copyOf(shelves));
    }

    static int ground(int index, int x, int z) {
        int dx = OrthIsland.localX(x), dz = OrthIsland.localZ(z);
        if (!OrthIsland.shaftColumn(dx, dz)) return NO_SHELF;
        Span span = span(index, dx, dz, OrthIsland.distance(dx, dz), Math.atan2(dz, dx));
        return span == null ? NO_SHELF : span.top;
    }

    private static Span span(int index, int x, int z, double radius, double angle) {
        Shelf shelf = SHELVES.get(index);
        double diff = Math.abs(Math.IEEEremainder(angle - shelf.angle, Math.PI * 2));
        if (diff > shelf.halfWidth + 0.12) return null;
        double flank = Mth.clamp(diff / shelf.halfWidth, 0, 1.17);
        double front = shelf.front + flank * flank * (463 - shelf.front)
                + 25 * FIELD.noise(x, z, 75, 21 + index) + 9 * FIELD.noise(x, z, 26, 31 + index);
        if (radius < front) return null;
        double reach = Mth.clamp((radius - front) / Math.max(38, 484 - front), 0, 1);
        int top = shelf.y + (int) (24 * reach + 12 * FIELD.noise(x, z, 112, 51 + index)
                + 4 * FIELD.noise(x, z, 31, 71 + index));
        int thickness = 9 + (int) (shelf.thickness * Math.sqrt(reach));
        return new Span(top - thickness, top);
    }

    static BlockState block(Column column, int y) {
        if (y == MIN_Y) return Blocks.BEDROCK.defaultBlockState();
        if (!column.opening) {
            if (y < 0) return wall(y);
            if (y <= column.upper) {
                if (y == column.upper && y >= OrthIsland.SEA) return Blocks.GRASS_BLOCK.defaultBlockState();
                if (y > column.upper - 4) return Blocks.DIRT.defaultBlockState();
                return wall(y);
            }
            return y < OrthIsland.SEA ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        if (y <= column.bottom) {
            return y > column.bottom - 3 ? OrthBlocks.ABYSS_MOSS.get().defaultBlockState() : wall(y);
        }
        for (Span shelf : column.shelves) {
            if (y >= shelf.bottom && y <= shelf.top) return shelfMaterial(shelf, y);
            if (y > shelf.top && y <= shelf.water) return Blocks.WATER.defaultBlockState();
        }
        for (Rock rock : column.rocks) {
            if (y >= rock.bottom && y <= rock.top) return scarpMaterial(rock, y);
        }
        return Blocks.AIR.defaultBlockState();
    }

    static BlockState shelfMaterial(Span span, int y) {
        if (y == span.top) return switch (span.cover) {
            case MEADOW, FLOWERS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case WOODLAND -> Blocks.PODZOL.defaultBlockState();
            case FERN, WETLAND -> Blocks.MOSS_BLOCK.defaultBlockState();
            case SCREE -> Blocks.COARSE_DIRT.defaultBlockState();
            case TRAIL -> Blocks.GRAVEL.defaultBlockState();
            case POOL -> Blocks.CLAY.defaultBlockState();
        };
        if (y > span.top - 4) return Blocks.ROOTED_DIRT.defaultBlockState();
        return wall(y);
    }

    static BlockState wall(int y) {
        if (y < -1120 && Math.floorMod(y, 99) > 58) return Blocks.DEEPSLATE.defaultBlockState();
        if (Math.floorMod(y, 103) < 14) return OrthBlocks.CHALK.get().defaultBlockState();
        if (Math.floorMod(y, 149) > 119) return Blocks.ANDESITE.defaultBlockState();
        return OrthBlocks.STONE.get().defaultBlockState();
    }

    static BlockState scarpMaterial(Rock rock, int y) {
        if (y == rock.top) return Blocks.TUFF.defaultBlockState();
        return wall(y + rock.skew);
    }

    record Shelf(double angle, double halfWidth, int front, int y, int thickness) {}
    record Span(int bottom, int top, EdgeLandscape.Cover cover, int water) {
        Span(int bottom, int top) { this(bottom, top, EdgeLandscape.Cover.MEADOW, top); }
    }
    record Rock(int bottom, int top, int skew) {}
    record Column(int upper, boolean opening, int bottom, List<Span> shelves, List<Rock> rocks) {
        Column(int upper, boolean opening, int bottom, List<Span> shelves) { this(upper, opening, bottom, shelves, List.of()); }

        int top() {
            if (!opening) return Math.max(upper, OrthIsland.SEA - 1);
            return shelves.isEmpty() ? solidTop() : Math.max(solidTop(), shelves.get(0).water);
        }

        int solidTop() {
            int shelf = shelves.isEmpty() ? bottom : shelves.get(0).top;
            return rocks.isEmpty() ? shelf : Math.max(shelf, rocks.get(0).top);
        }
    }

    private EdgeTerrain() {}
}
