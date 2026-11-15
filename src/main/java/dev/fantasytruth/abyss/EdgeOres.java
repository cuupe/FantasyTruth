package dev.fantasytruth.abyss;

import dev.fantasytruth.abyss.EdgeExploration.Painter;
import dev.fantasytruth.terrain.AtlasField;
import java.util.SplittableRandom;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Seeded veins cross chunk boundaries and replace only the Edge's host rock. */
final class EdgeOres {
    private static final int CELL = 24, STRATUM = 64, REACH = 10;
    private static final int LOW = -1344;
    private static final Block[] SHALLOW = {Blocks.COAL_ORE, Blocks.COPPER_ORE, Blocks.IRON_ORE, Blocks.COAL_ORE};
    private static final Block[] MID = {Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.COAL_ORE, Blocks.GOLD_ORE};
    private static final Block[] DEEP = {Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.DEEPSLATE_GOLD_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DEEPSLATE_LAPIS_ORE};

    static void place(ChunkAccess chunk, long seed, EdgeTerrain.Layout layout) {
        if (!AbyssLayer.passage(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ(), 572)) return;
        var p = new Painter(chunk, 0, chunk.getMinBuildHeight() == EdgeTerrain.MIN_Y ? LOW : OrthTerrain.SHARED_BOTTOM);
        int low = Math.max(LOW, chunk.getMinBuildHeight());
        for (int gz = Math.floorDiv(p.z0 - REACH, CELL); gz <= Math.floorDiv(p.z0 + 15 + REACH, CELL); gz++) {
            for (int gx = Math.floorDiv(p.x0 - REACH, CELL); gx <= Math.floorDiv(p.x0 + 15 + REACH, CELL); gx++) {
                for (int gy = Math.floorDiv(low - REACH, STRATUM); gy < 0; gy++) {
                    var rng = new SplittableRandom(AtlasField.hash(seed ^ 0x456467654f726573L, gx, gy, gz));
                    int x = gx * CELL + rng.nextInt(CELL), z = gz * CELL + rng.nextInt(CELL);
                    int y = gy * STRATUM + rng.nextInt(STRATUM);
                    if (x + REACH < p.x0 || x - REACH > p.x0 + 15 || z + REACH < p.z0 || z - REACH > p.z0 + 15
                            || !AbyssLayer.passage(x, z, 561) || AbyssLayer.passage(x, z, 117)
                            || y < LOW || y > -21 || !EdgeRelics.hostRock(EdgeTerrain.block(EdgeTerrain.column(x, z, layout), y, layout))) {
                        continue;
                    }
                    var palette = y > -420 ? SHALLOW : y > -850 ? MID : DEEP;
                    vein(p, x, y, z, palette[rng.nextInt(palette.length)], rng);
                }
            }
        }
    }

    static void vein(Painter p, int x, int y, int z, Block ore, SplittableRandom rng) {
        var state = ore.defaultBlockState();
        double angle = rng.nextDouble(Math.PI * 2);
        int dx = (int) Math.round(7 * Math.cos(angle)), dz = (int) Math.round(7 * Math.sin(angle));
        int tilt = rng.nextInt(-4, 5);
        for (int step = -3; step <= 3; step++) {
            int cx = x + Math.round(dx * step / 3F), cz = z + Math.round(dz * step / 3F);
            int cy = y + Math.round(tilt * step / 3F);
            int r = Math.abs(step) < 2 ? 3 : 2;
            for (int bz = Math.max(p.z0, cz - r); bz <= Math.min(p.z0 + 15, cz + r); bz++) {
                for (int bx = Math.max(p.x0, cx - r); bx <= Math.min(p.x0 + 15, cx + r); bx++) {
                    int distance = (bx - cx) * (bx - cx) + (bz - cz) * (bz - cz);
                    for (int by = cy - 1; by <= cy + 1; by++) {
                        int grain = EdgeExploration.hash(bx, by, bz);
                        if (distance + 3 * Math.abs(by - cy) > r * r - 2 + (grain & 3)) continue;
                        if (p.inside(bx, by, bz) && EdgeRelics.hostRock(p.get(bx, by, bz))) p.set(bx, by, bz, state);
                    }
                }
            }
        }
    }

    private EdgeOres() {}
}
