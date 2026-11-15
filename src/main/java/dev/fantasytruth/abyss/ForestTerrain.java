package dev.fantasytruth.abyss;

import dev.fantasytruth.terrain.TerrainField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;

/** Height intervals leave large open volumes between the canopy, suspended strata and ravine floor. */
final class ForestTerrain {
    static final int MIN_Y = -768;
    static final int EXIT = AbyssLayer.FOREST.entrance - 1250;
    static final int INVERTED = 130;
    static final int GROVE_X = 64, GROVE_Z = 217;
    static final int PORTA_ANCHOR = -155;
    static final int CAMP_LANDING = -488;
    private static final int CROSSING_FLOOR = -414;
    static final int CAMP_Y = AbyssLayer.FOREST.entrance - 1190;
    static final int CAMP_X = 173, CAMP_Z = 137;
    static final int FALL_X = 103, FALL_Z = 115;
    static final int FALL_BOTTOM = -548, FALL_TOP = -137;
    static final int FIRST_MILL_X = 157, FIRST_MILL_Z = -58;
    private static final int INLET_BOTTOM = ForestMouth.BOTTOM + ForestMouth.OFFSET;
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
    private static final int NO_SPAN = MIN_Y - 1;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.TUFF.defaultBlockState();
    private static final BlockState DEEP = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState ROOTS = Blocks.ROOTED_DIRT.defaultBlockState();

    record Column(int floor, int floorWater, int shelfBottom, int shelfTop, int water, int ledgeBottom, int ledgeTop,
                  int roofBottom, int roofTop, List<EdgeTerrain.Span> inlet, boolean chute, boolean damp) {
        int roofAttachment() {
            if (roofBottom == NO_SPAN) return NO_SPAN;
            int contact = roofBottom;
            for (var span : inlet) {
                int top = span.top() + ForestMouth.OFFSET;
                int bottom = span.bottom() + ForestMouth.OFFSET;
                if (top + 1 >= contact && bottom < contact) contact = bottom;
            }
            for (var span : inlet) {
                int top = span.top() + ForestMouth.OFFSET;
                if (top < contact && top >= contact - 28) return NO_SPAN;
            }
            return contact;
        }

        int inletAttachment() {
            int anchor = ForestMouth.inletAttachment(inlet);
            return anchor > shelfTop + 44 ? anchor : NO_SPAN;
        }

        int top() {
            int inletTop = inlet.isEmpty() ? NO_SPAN : inlet.get(0).top() + ForestMouth.OFFSET;
            return Math.max(inletTop, Math.max(roofTop, Math.max(water, Math.max(floorWater, ledgeTop))));
        }
    }

    final long seed;
    final boolean deep;
    private final int minY;
    private final int exit;
    private final TerrainField field;
    final ForestSpring.Site spring;

    ForestTerrain(long seed, boolean deep) {
        this.seed = seed;
        this.deep = deep;
        this.minY = deep ? ForestCoordinates.DEEP_MIN_Y : MIN_Y;
        this.exit = deep ? ForestCoordinates.DEEP_EXIT : EXIT;
        field = new TerrainField(seed ^ 0x4c32466f72657374L);
        spring = ForestSpring.chooseFirst(this);
    }

    int exitY() { return exit; }

    Column column(int x, int z) {
        int dx = OrthIsland.localX(x), dz = OrthIsland.localZ(z);
        double radius = Math.hypot(dx, dz);
        double wx = x + 131 * field.noise(x, z, 630, 1), wz = z + 131 * field.noise(x, z, 630, 2);
        double province = field.noise(wx, wz, 690, 3);
        double terrace = field.noise(wx, wz, 195, 4);
        double grain = field.noise(x, z, 41, 5);
        double ravineField = Math.abs(field.noise(wx, wz, 270, 6));
        double cut = 1 - smooth(0.018, 0.35, ravineField);
        double cavern = field.noise(wx, wz, 332, 7);
        boolean damp = field.noise(x, z, 93, 8) + 0.45 * grain > -0.10;
        double outlet = radius;
        if (radius < (deep ? 258 : 125)) {
            outlet = Mth.lerp(deep ? smooth(166, 258, radius) : 0, ForestMouth.outletDistance(dx, dz), radius);
        }
        boolean chute = outlet < 26;
        double camp = Math.hypot(x - CAMP_X, z - CAMP_Z);

        // A branching deep valley joins the camp basin and the passage to the Great Fault.
        double slope = field.noise(wx, wz, 113, 14);
        double footpath = smooth(0.08, 0.29, ravineField);
        double hillside = smooth(-0.04, 0.44, slope) * footpath;
        int floor = (int) (-387 + 55 * province + 26 * terrace + 5 * grain + 68 * hillside - 53 * cut);
        double basin = Math.hypot(dx * 0.83, (dz - 90) * 0.70);
        double riverbed = 0;
        if (deep) {
            double upland = field.noise(wx, wz, 1170, 22);
            double ridge = smooth(0.16, 0.51, field.noise(wx, wz, 254, 23));
            double channel = Math.abs(field.noise(wx, wz, 372, 24));
            riverbed = (1 - smooth(0.022, 0.165, channel)) * (1 - smooth(0.12, 0.52, upland));
            double benches = smooth(-0.3, 0.38, terrace);
            floor = (int) (-904 + 188 * upland + 82 * benches + 91 * ridge + 30 * slope + 12 * grain);
            floor = (int) Mth.lerp(smooth(0.28, 0.72, riverbed), floor, -967 + 10 * upland + 3 * grain);

            floor = (int) Mth.lerp(1 - smooth(32, 206, outlet), floor, -1157 + 16 * terrace + 6 * grain);
            floor = (int) Mth.lerp(1 - smooth(40, 183, camp), floor, CAMP_Y - 97 + 11 * terrace + 6 * grain);
        } else {
            floor = (int) Mth.lerp(smooth(39, 310, basin), -498 + 11 * terrace + 3 * grain, floor);
            floor = (int) Mth.lerp(smooth(35, 90, camp), CAMP_Y - 97 + 6 * grain, floor);
        }
        double grove = Math.hypot(x - GROVE_X, (z - GROVE_Z) * 0.89);
        if (grove < 104) {
            double hollow = 1 - smooth(21, 104, grove);
            floor = (int) Mth.lerp(hollow, -471 + 30 * hillside + 9 * terrace, floor);
        }
        double landing = Math.hypot((x - 118) * 0.70, (z - 150) * 0.86);
        floor = (int) Mth.lerp(smooth(10, 70, landing), CAMP_LANDING - 1, floor);
        double approach = Math.hypot((x - 45) * 0.6, (z - 239) * 0.85);
        floor = (int) Mth.lerp(smooth(9, 43, approach), CROSSING_FLOOR, floor);
        if (outlet < 101) {
            int gorge = exit - 82 + (int) (84 * smooth(4, 26, outlet) + (deep ? 171 : 104) * smooth(26, 101, outlet)
                    + 7 * grain * smooth(29, 71, outlet));
            floor = Math.min(floor, chute ? Math.min(gorge, exit - 6) : gorge);
        }
        floor = Mth.clamp(floor, exit - 133, -150);

        // The upper forest is a traversable landmass cut by ravines. Seeded buttresses thicken its underside.
        double buttress = smooth(0.14, 0.72, cavern) * (1 - smooth(0.66, 0.98, cut));
        int shelfTop = (int) (525 + 67 * province + 42 * terrace + 8 * grain - 124 * cut
                - 75 * smooth(0.65, 1.0, cut));
        int thickness = (int) (29 + 48 * buttress + 13 * Math.max(0, terrace));
        if (deep) {
            double escarpment = smooth(-0.42, 0.23, field.noise(wx, wz, 390, 27));
            double gap = smooth(0.57, 0.92, cut) * smooth(0.08, 0.42, cavern);
            shelfTop = (int) Mth.lerp(gap, 476 + 99 * province + 64 * escarpment + 16 * grain - 138 * cut, floor);
            thickness = (int) (46 + 64 * buttress + 40 * escarpment);
        }
        int shelfBottom = shelfTop - thickness;

        // A long, winding cleft slopes from the entrance bank to the lower forest.
        if (dx >= 38 && dx <= 1970) {
            double riverZ = descentZ(dx);
            double width = (deep ? 37 : 21) + 22 * smooth(0.5, 1.0, cut);
            double distance = Math.abs(z - riverZ);
            double access = 1 - smooth(width, width + 99, distance);
            access *= smooth(38, 88, dx) * (1 - smooth(1800, 1970, dx));
            double descent = deep ? Mth.clamp((dx - 65) / 1725.0, 0, 1) : smooth(70, 1780, dx);
            int grade = (int) Mth.lerp(descent, 556, floor + 4);
            shelfTop = (int) Mth.lerp(access, shelfTop, grade + 3 * grain);
            shelfBottom = Math.min(shelfBottom, shelfTop - 14);
        }

        // Only shelves already near the floor merge into rising foothills.
        if (deep && shelfTop - floor < 124) {
            shelfBottom = Math.min(shelfBottom, floor - 2);
        }

        // Under the canopy, interlocking stone overhangs form the upside-down forest.
        int ledgeBottom = NO_SPAN, ledgeTop = NO_SPAN;
        double groveRoof = 1 - smooth(24, 104, grove);
        double overhang = smooth(-0.38, 0.31, cavern + 0.58 * terrace - 0.21 * cut + 0.65 * groveRoof);
        if (deep) overhang *= 0.27 + 0.73 * Math.max(groveRoof, smooth(0.28, 0.67, cavern));
        int deepCeiling = (int) ((deep ? -332 + 220 * groveRoof : -109) + 76 * province + 41 * terrace + 9 * grain - 14 * groveRoof);
        if (radius > 68 && overhang > (deep ? 0.23 : 0.055)
                && shelfBottom > deepCeiling + 30 && (!deep || deepCeiling > floor + 120)) {
            double flutes = 0.64 * field.noise(x, z, 77, 9) + 0.36 * grain;
            ledgeTop = deepCeiling;
            double spur = smooth(0.37, 0.80, slope + 0.25 * flutes) * smooth(0.50, 0.91, overhang);
            ledgeBottom = deepCeiling - (int) (24 + overhang * (81 + 33 * flutes) + 58 * spur);
            ledgeBottom = Math.max(floor + 90, ledgeBottom);
        }
        double porta = Math.hypot((x - CAMP_X - 9) * 0.86, (z - CAMP_Z - 4) * 1.14);
        if (porta < 69) {
            double crown = 1 - smooth(17, 69, porta);
            ledgeTop = Math.max(ledgeTop, PORTA_ANCHOR + (int) (125 * crown + 17 * terrace));
            int solidUnderside = Math.min(ledgeTop - 10, PORTA_ANCHOR + (int) (58 * (1 - crown) + 5 * grain));
            ledgeBottom = ledgeBottom == NO_SPAN ? solidUnderside : Math.min(ledgeBottom, solidUnderside);
        }
        // Where the descending shelves meet the valley floor, their layers merge.
        if (shelfTop <= floor) {
            shelfTop = shelfBottom = NO_SPAN;
        }

        // The rising water follows a cleft past the camp bridge.
        double waterfall = Math.hypot(x - FALL_X, z - FALL_Z);
        if (deep && waterfall < 42) {
            floor = (int) Mth.lerp(1 - smooth(10, 42, waterfall), floor, FALL_BOTTOM - 10 + 3 * grain);
        }
        if (waterfall < 13) {
            if (!deep) floor = Math.min(floor, FALL_BOTTOM - 10);
            if (ledgeTop <= FALL_TOP + 10) ledgeTop = ledgeBottom = NO_SPAN;
            else if (ledgeBottom < FALL_TOP + 10) {
                ledgeBottom = FALL_TOP + 10;
                ledgeTop = Math.max(ledgeTop, ledgeBottom + 7);
            }
        }
        // The entrance pool breaks the initial fall.
        int water = shelfTop;
        double pool = Math.hypot(dx * 0.91, dz * 1.16) + 8 * grain;
        if (pool < 94) {
            int bank = (int) Mth.lerp(smooth(32, 93, pool), 563 + 4 * grain, shelfTop);
            if (pool < 29) bank = 558 - (int) (9 * (1 - pool / 29));
            shelfTop = bank;
            shelfBottom = bank - (int) (25 + 29 * (1 - smooth(27, 94, pool)));
            water = pool < 29 ? 564 : bank;
        }
        double mill = Math.hypot(x - FIRST_MILL_X, z - FIRST_MILL_Z);
        if (mill < 49) {
            shelfTop = (int) Mth.lerp(smooth(14, 49, mill), 553 + grain, shelfTop);
            shelfBottom = Math.min(shelfBottom, shelfTop - 26);
            water = shelfTop;
        }
        int roofTop = NO_SPAN, roofBottom = NO_SPAN;
        double roofEdge = radius < 236 ? ForestMouth.roofEdge(dx, dz) : radius - 175;
        if (roofEdge >= 0) {
            double shoulder = smooth(0, 75, roofEdge);
            double ribs = smooth(0.05, 0.72, cavern + 0.42 * terrace);
            double bevel = 88 * (1 - smooth(0, 42, roofEdge));
            roofBottom = (int) (Mth.lerp(shoulder, 871, 822) + bevel + 34 * province + 22 * terrace
                    + 12 * grain - 39 * ribs * shoulder);
            roofTop = AbyssLayer.MAX_Y - 1;
        }
        List<EdgeTerrain.Span> inlet = List.of();
        if (radius < ForestMouth.RADIUS) {
            inlet = ForestMouth.shelves(dx, dz, radius);
        }
        // Continue the first-layer ledges into the rock above the opening.
        if (radius > 174 && radius < 315) {
            var upper = EdgeTerrain.column(x, z, EdgeTerrain.Layout.WILD_EDGE).shelves();
            int apertureTop = inlet.isEmpty() ? EdgeTerrain.EXIT : inlet.get(0).top();
            for (var span : upper) {
                if (span.bottom() <= apertureTop + 12 || span.top() >= -1080 || span.top() <= -1230) continue;
                int bottom = span.bottom();
                if (roofTop != NO_SPAN) {
                    bottom = Math.min(bottom, (int) Mth.lerp(smooth(223, 251, radius),
                            bottom, roofBottom - ForestMouth.OFFSET));
                }
                var projection = new ArrayList<EdgeTerrain.Span>(inlet.size() + 1);
                projection.add(new EdgeTerrain.Span(bottom, span.top(), span.cover(), span.water()));
                projection.addAll(inlet);
                inlet = List.copyOf(projection);
                break;
            }
        }
        if (roofTop != NO_SPAN && !inlet.isEmpty()) {
            int upperBank = inlet.get(0).top() + ForestMouth.OFFSET;
            if (upperBank >= 789 && upperBank < roofBottom && roofBottom - upperBank <= 43) {
                roofBottom = upperBank + 1;
            }
        }
        int floorWater = deep && riverbed > 0.59 && floor > -995 && floor < -953 ? -953 : floor;
        return new Column(floor, floorWater, shelfBottom, shelfTop, water, ledgeBottom, ledgeTop, roofBottom, roofTop, inlet, chute, damp);
    }

    static double descentZ(int dx) {
        double t = Math.max(0, dx);
        return AbyssLayer.CENTER_Z + 73 * Math.sin(t / 161) - 64 * Math.sin(t / 327);
    }

    BlockState block(Column c, int y) {
        if (y == minY) return Blocks.BEDROCK.defaultBlockState();
        if (y <= c.floor) {
            if (!c.chute && y == c.floor) return c.floorWater > c.floor ? Blocks.CLAY.defaultBlockState() : MOSS;
            if (deep && !c.chute && c.floorWater == c.floor && y >= c.floor - 3) return ROOTS;
            return y < -576 ? DEEP : STONE;
        }
        if (y <= c.floorWater) return WATER;
        if (c.roofTop != NO_SPAN && y >= c.roofBottom + 8 && y <= c.roofTop) return STONE;
        if (y >= INLET_BOTTOM && !c.inlet.isEmpty()) {
            BlockState shared = ForestMouth.rock(c.inlet, y - ForestMouth.OFFSET);
            if (!shared.isAir()) return shared;
        }
        if (y > c.shelfTop && y <= c.water) return WATER;
        if (y >= c.shelfBottom && y <= c.shelfTop) {
            if (y == c.shelfTop || y == c.shelfBottom && c.damp) return MOSS;
            if (y > c.shelfTop - 3) return ROOTS;
            return STONE;
        }
        if (y >= c.ledgeBottom && y <= c.ledgeTop) {
            if (y == c.ledgeTop || y == c.ledgeBottom && c.damp) return MOSS;
            if (y < c.ledgeBottom + 8) return c.damp ? DEEP : ANDESITE;
            return STONE;
        }
        if (y >= c.roofBottom && y <= c.roofTop) {
            if (y == c.roofBottom && c.damp) return MOSS;
            return y < c.roofBottom + 8 ? ANDESITE : STONE;
        }
        return AIR;
    }

    ChunkAccess fill(ChunkAccess chunk) {
        int x = chunk.getPos().getMinBlockX(), z = chunk.getPos().getMinBlockZ();
        var columns = new Column[256];
        var occupied = new BitSet(chunk.getSectionsCount());
        int min = chunk.getMinBuildHeight(), sections = chunk.getSectionsCount(), floor = Integer.MAX_VALUE;
        int fullRoof = min;
        for (int i = 0; i < columns.length; i++) {
            Column c = column(x + (i & 15), z + (i >> 4));
            columns[i] = c;
            floor = Math.min(floor, c.floor);
            fullRoof = c.roofTop == NO_SPAN ? Integer.MAX_VALUE : Math.max(fullRoof, c.roofBottom + 8);
            mark(occupied, sections, min, min, c.floorWater);
            mark(occupied, sections, min, c.shelfBottom, c.water);
            mark(occupied, sections, min, c.roofBottom, c.roofTop);
            mark(occupied, sections, min, c.ledgeBottom, c.ledgeTop);
            for (var span : c.inlet) mark(occupied, sections, min, span.bottom() + ForestMouth.OFFSET, span.top() + ForestMouth.OFFSET);
        }
        int bits = Mth.ceillog2(chunk.getHeight() + 1);
        var heights = new SimpleBitStorage(bits, columns.length);
        var floorHeights = new SimpleBitStorage(bits, columns.length);
        for (int i = 0; i < columns.length; i++) {
            var column = columns[i];
            int top = column.top();
            heights.set(i, Mth.clamp(top + 1 - min, 0, chunk.getHeight()));
            int solid = top == column.water ? column.shelfTop : top == column.floorWater ? column.floor : top;
            floorHeights.set(i, Mth.clamp(solid + 1 - min, 0, chunk.getHeight()));
        }
        for (int i = occupied.nextSetBit(0); i >= 0; i = occupied.nextSetBit(i + 1)) {
            int low = min + i * 16, high = low + 15;
            var section = chunk.getSection(i);
            if (low >= fullRoof || low > minY && high < floor - (deep ? 3 : 0) && (high < -576 || low >= -576)) {
                var states = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY,
                        high < -576 ? DEEP : STONE, PalettedContainer.Strategy.SECTION_STATES);
                chunk.getSections()[i] = new LevelChunkSection(states, section.getBiomes());
                continue;
            }
            var states = section.getStates();
            for (int cell = 0; cell < columns.length; cell++) {
                Column c = columns[cell];
                for (int y = low; y <= high; y++) {
                    var state = block(c, y);
                    if (state != AIR) states.getAndSetUnchecked(cell & 15, y - low, cell >> 4, state);
                }
            }
            section.recalcBlockCounts();
        }
        chunk.setHeightmap(Heightmap.Types.WORLD_SURFACE_WG, heights.getRaw());
        chunk.setHeightmap(Heightmap.Types.OCEAN_FLOOR_WG, floorHeights.getRaw());
        return chunk;
    }

    private static void mark(BitSet set, int sections, int min, int bottom, int top) {
        int from = Mth.clamp((bottom - min) >> 4, 0, sections);
        int end = Mth.clamp(((top - min) >> 4) + 1, 0, sections);
        if (from < end) set.set(from, end);
    }

    NoiseColumn baseColumn(int x, int z, LevelHeightAccessor height) {
        var column = column(x, z);
        var blocks = new BlockState[height.getHeight()];
        Arrays.setAll(blocks, i -> block(column, i + height.getMinBuildHeight()));
        return new NoiseColumn(height.getMinBuildHeight(), blocks);
    }

    static double smooth(double min, double max, double value) {
        double t = Mth.clamp((value - min) / (max - min), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
