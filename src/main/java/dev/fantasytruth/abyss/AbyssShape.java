package dev.fantasytruth.abyss;

import dev.fantasytruth.terrain.AtlasField;
import dev.fantasytruth.terrain.TerrainField;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Shared field for the terrain, base-height queries, and cross-chunk formations. */
final class AbyssShape {
    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState TUFF = Blocks.TUFF.defaultBlockState();
    private static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
    private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState TERRACOTTA = Blocks.TERRACOTTA.defaultBlockState();
    private static final BlockState BASALT = Blocks.SMOOTH_BASALT.defaultBlockState();

    record Column(double radius, double angle, double warp, int roof, int phase) {}
    private record Slice(double progress, double blend, double radius, double ledge) {}

    final AbyssLayer layer;
    final long seed;
    private final TerrainField noise;
    private final Slice[] slices = new Slice[AbyssLayer.MAX_Y - AbyssLayer.MIN_Y];

    AbyssShape(AbyssLayer layer, long seed) {
        this.layer = layer;
        this.seed = seed;
        this.noise = new TerrainField(seed ^ (0x9e3779b97f4a7c15L * (layer.ordinal() + 1)));
        for (int y = AbyssLayer.MIN_Y; y < AbyssLayer.MAX_Y; y++) {
            double progress = Math.max(0, Math.min(1, (layer.entrance - y) / (double) (layer.entrance - layer.exit)));
            double blend = Math.min(1, Math.min(layer.entrance - y, y - layer.exit) / 42.0);
            blend = Math.max(0, blend);
            double radius = 64 + (layer.width - 64) * Math.pow(Math.sin(progress * Math.PI), 0.68);
            double ledge = switch (layer) {
                case EDGE -> 14 * Math.cos(y * 0.092) + 5 * Math.cos(y * 0.21);
                case FOREST -> 23 * Math.sin(y * 0.055) + 8 * Math.cos(y * 0.13);
                case FAULT -> 5 * Math.cos(y * 0.067) + 4 * Math.cos(y * 0.19);
                case GOBLETS -> 24 * Math.cos(y * 0.049) + 9 * Math.cos(y * 0.13);
                case SEA -> 9 * Math.cos(y * 0.041);
                case CAPITAL -> 20 * Math.cos(y * 0.062) + 8 * Math.cos(y * 0.14);
                case MAELSTROM -> 14 * Math.cos(y * 0.055);
            };
            slices[y - AbyssLayer.MIN_Y] = new Slice(progress, blend, radius, ledge);
        }
    }

    Column column(int x, int z) {
        double dx = x - AbyssLayer.CENTER_X;
        double dz = z - AbyssLayer.CENTER_Z;
        double radius = Math.hypot(dx, dz);
        double angle = Math.atan2(dz, dx);
        double warp = 18 * noise.noise(x, z, 58, 71) + 8 * noise.noise(x, z, 17, 72)
                + 10 * Math.sin(7 * angle) + 5 * Math.cos(15 * angle);
        int roof = layer.entrance + 60 + (int) (18 * noise.noise(x, z, 93, 73));
        int phase = Math.floorMod(AtlasField.hash(seed, x >> 3, 0, z >> 3), 23);
        return new Column(radius, angle, warp, roof, phase);
    }

    BlockState block(Column column, int y) {
        if (y > column.roof) return AIR;
        if (y <= AbyssLayer.MIN_Y + 1) return BEDROCK;
        if (layer == AbyssLayer.MAELSTROM && y < layer.exit + column.warp * 0.65) return BASALT;
        if (layer != AbyssLayer.MAELSTROM && y < layer.exit - 80) return DEEPSLATE;

        double r = column.radius;
        if (r <= AbyssLayer.PASSAGE_RADIUS + 4) return AIR;
        Slice slice = slices[y - AbyssLayer.MIN_Y];
        double wall = slice.radius + slice.blend * (column.warp + slice.ledge);
        if (layer == AbyssLayer.FAULT && r < layer.width + 36) {
            wall += slice.blend * (12 * Math.sin(column.angle * 11 + y * 0.012)
                    + 5 * Math.sin(column.angle * 19 - y * 0.027));
        } else if (layer == AbyssLayer.MAELSTROM && r < layer.width + 40) {
            wall += slice.blend * 28 * Math.cos(column.angle * 3 - slice.progress * 19);
        }

        // The rock collar keeps the fifth-layer basin separate from the central descent.
        if (layer == AbyssLayer.SEA && y >= 326 && y <= 410 && r <= 39) return CALCITE;
        if (r < wall) {
            if (layer == AbyssLayer.SEA && y >= 326 && y <= 401) return y <= 341 ? CALCITE : WATER;
            return AIR;
        }
        int band = Math.floorMod(y + column.phase, 46);
        return switch (layer) {
            case EDGE -> band < 6 ? MOSS : band < 17 ? ANDESITE : STONE;
            case FOREST -> band < 13 ? MOSS : band < 23 ? TUFF : STONE;
            case FAULT -> band < 5 ? CALCITE : band < 21 ? ANDESITE : STONE;
            case GOBLETS -> band < 10 ? MOSS : band < 22 ? CALCITE : TUFF;
            case SEA -> band < 16 ? CALCITE : band < 23 ? Blocks.PACKED_ICE.defaultBlockState() : DEEPSLATE;
            case CAPITAL -> band < 11 ? CALCITE : band < 26 ? TERRACOTTA : ANDESITE;
            case MAELSTROM -> band < 8 ? CALCITE : band < 23 ? BASALT : DEEPSLATE;
        };
    }

    int anchor(Column column, int nearY, boolean inverted) {
        int direction = inverted ? -1 : 1;
        for (int y = nearY + 37; y >= nearY - 37; y--) {
            if (block(column, y) == AIR && block(column, y - direction).blocksMotion()
                    && block(column, y + direction * 5) == AIR) return y;
        }
        return Integer.MIN_VALUE;
    }
}
