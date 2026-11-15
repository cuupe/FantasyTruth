package dev.fantasytruth.abyss;

import dev.fantasytruth.FantasyTruth;
import dev.fantasytruth.abyss.EdgeExploration.Painter;
import dev.fantasytruth.abyss.EdgeExploration.Site;
import dev.fantasytruth.terrain.TerrainField;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.feature.TreeFeature;

final class EdgeVegetation {
    static final int ELDER = 5;
    private static final TerrainField FIELD = new TerrainField(0x4c31466c6f7261L);
    private static final Block[] WHITE_FLOWERS = {Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.LILY_OF_THE_VALLEY};
    private static final Block[] FIELD_FLOWERS = {Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.POPPY};

    static void placeVanillaTrees(WorldGenLevel level, ChunkAccess chunk, AbyssEdgeGenerator generator) {
        var registry = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        var layout = generator.layout();
        boolean forested = layout.forested;
        int step = forested ? 5 : 8;
        for (int dz = 0; dz + step <= 16; dz += step) {
            for (int dx = 0; dx + step <= 16; dx += step) {
                int salt = EdgeExploration.hash(x0 + dx, 470, z0 + dz);
                int x = x0 + dx + (forested ? Math.floorMod(salt, step) : salt & 7);
                int z = z0 + dz + (forested ? Math.floorMod(salt >>> 7, step) : salt >>> 7 & 7);
                if (!AbyssLayer.passage(x, z, forested ? 456 : 367)) continue;
                for (var span : EdgeTerrain.column(x, z, layout).shelves()) {
                    int y = span.top() + 1;
                    // Vanilla features can cross chunk bounds. The overlap uses only chunk-clipped trees.
                    if (layout.wallTerraces() && y >= OrthTerrain.SHARED_BOTTOM - 40) continue;
                    if (forested && ((!layout.mossPit && EdgeBasin.nearRoute(x, y, z, 10))
                            || y > -1215 && !AbyssLayer.passage(x, z, 369))) continue;
                    int roll = EdgeExploration.hash(x, y, z);
                    double patch = FIELD.noise(x, z, 57, Math.floorDiv(y, 64));
                    int density = switch (span.cover()) {
                        case WOODLAND -> forested ? 91 : 57;
                        case FERN -> forested ? 73 : 43;
                        case WETLAND -> forested ? 48 : 29;
                        case MEADOW -> forested ? 21 : 16;
                        case FLOWERS -> forested ? 16 : 12;
                        default -> 0;
                    };
                    if (density == 0 || Math.floorMod(roll, 100) >= density + 28 * patch
                            || EdgeExploration.nearLandmark(x, y, z)) continue;
                    var origin = new BlockPos(x, y, z);
                    if (!TreeFeature.validTreePos(level, origin)) continue;
                    var tree = switch (span.cover()) {
                        case FERN -> (roll & 3) == 0 ? TreeFeatures.AZALEA_TREE : TreeFeatures.OAK;
                        case WETLAND -> (roll & 1) == 0 ? TreeFeatures.BIRCH : TreeFeatures.OAK;
                        case FLOWERS -> TreeFeatures.OAK_BEES_005;
                        default -> forested && span.cover() == EdgeLandscape.Cover.WOODLAND && (y < -1215 || patch > 0.35)
                                ? ((roll & 3) == 0 ? TreeFeatures.SPRUCE : TreeFeatures.FANCY_OAK)
                                : switch (roll & 7) {
                            case 0, 1 -> TreeFeatures.BIRCH;
                            case 2, 3 -> TreeFeatures.FANCY_OAK;
                            default -> TreeFeatures.OAK;
                        };
                    };
                    var feature = registry.get(tree);
                    if (feature != null) feature.place(level, generator, RandomSource.create(roll), origin);
                }
            }
        }
    }

    static void understory(Painter p, EdgeTerrain.Layout layout) {
        boolean forested = layout.forested;
        boolean eroded = layout.eroded();
        var flora = eroded ? EdgeFlora.INSTANCE : null;
        for (int x = p.x0; x < p.x0 + 16; x++) {
            for (int z = p.z0; z < p.z0 + 16; z++) {
                if (!AbyssLayer.passage(x, z, layout.wallTerraces() ? 566 : forested ? 477 : 398)) continue;
                var column = EdgeTerrain.column(x, z, layout);
                if (layout.wallTerraces()) EdgeWall.growScarps(p, x, z, column);
                for (var span : column.shelves()) {
                    int y = span.top() + 1;
                    int hash = EdgeExploration.hash(x, y, z);
                    int chance = Math.floorMod(hash, 100);
                    if (span.cover() == EdgeLandscape.Cover.TRAIL || chance > 92) continue;
                    double patch = FIELD.noise(x, z, 15, Math.floorDiv(y, 80));
                    if (flora != null && growEdgeFlora(p, span, flora, x, y, z, chance, patch)) continue;
                    switch (span.cover()) {
                        case MEADOW -> {
                            if (patch > 0.16 && chance < 11) plant(p, x, y, z, FIELD_FLOWERS[Math.floorMod(hash >>> 8, FIELD_FLOWERS.length)]);
                            else if (chance < 26 + 18 * patch) plant(p, x, y, z, Blocks.GRASS);
                            else if (chance < 34 && patch > 0) tall(p, x, y, z, Blocks.TALL_GRASS);
                        }
                        case FLOWERS -> {
                            if (patch > -0.06 && chance < 29) plant(p, x, y, z, WHITE_FLOWERS[Math.floorMod(hash >>> 8, WHITE_FLOWERS.length)]);
                            else if (chance < 44) plant(p, x, y, z, Blocks.GRASS);
                            else if (chance < 48 && patch > 0.2) tall(p, x, y, z, Blocks.LILAC);
                        }
                        case WOODLAND -> {
                            if (chance < 17 + 10 * patch + (forested ? 9 : 0)) plant(p, x, y, z, Blocks.FERN);
                            else if (chance < 21) plant(p, x, y, z, Blocks.BROWN_MUSHROOM);
                            else if (chance < 30 && patch > 0.05) plant(p, x, y, z, Blocks.MOSS_CARPET);
                            else if (chance == 92 && patch > 0.21) plant(p, x, y, z, Blocks.AZALEA);
                        }
                        case FERN -> {
                            if (chance < 19 + 12 * patch) plant(p, x, y, z, Blocks.FERN);
                            else if (chance < 32 && patch > 0) tall(p, x, y, z, Blocks.LARGE_FERN);
                            else if (chance < 43) plant(p, x, y, z, Blocks.MOSS_CARPET);
                            else if (chance == 60 && patch > 0.22) plant(p, x, y, z, Blocks.FLOWERING_AZALEA);
                        }
                        case PIT_MOSS -> {
                            if (chance < 49 + 18 * patch) plant(p, x, y, z, Blocks.MOSS_CARPET);
                            else if (chance < 65) plant(p, x, y, z, Blocks.FERN);
                            else if (chance < 68 && patch > 0.1) tall(p, x, y, z, Blocks.LARGE_FERN);
                        }
                        case WETLAND -> {
                            if (chance < 28) plant(p, x, y, z, Blocks.FERN);
                            else if (chance < 39 && patch > 0) plant(p, x, y, z, Blocks.MOSS_CARPET);
                            else if (chance == 60) p.set(x, y - 1, z, OrthBlocks.GLOW_MOSS.get().defaultBlockState());
                        }
                        case POOL -> {
                            if (chance < 15 && p.get(x, y, z).is(Blocks.WATER)) {
                                p.set(x, y, z, Blocks.SEAGRASS.defaultBlockState());
                            }
                            if (chance < 18 && span.water() - span.top() <= 3) {
                                plant(p, x, span.water() + 1, z, Blocks.LILY_PAD);
                            }
                        }
                        case SCREE -> {
                            if (chance < 8) plant(p, x, y, z, Blocks.DEAD_BUSH);
                            else if (chance == 31 && patch > 0.15) {
                                p.ellipsoid(x, y - 1, z, 2, 2, 2, Blocks.TUFF.defaultBlockState());
                            }
                        }
                        case TRAIL -> {}
                    }
                    if (patch > 0.12 && chance < 12) {
                        p.hang(x, span.bottom() - 1, z, (chance == 0 ? Blocks.SPORE_BLOSSOM : Blocks.HANGING_ROOTS).defaultBlockState());
                    }
                }
            }
        }
    }

    private static boolean growEdgeFlora(Painter p, EdgeTerrain.Span span, EdgeFlora flora, int x, int y, int z,
                                         int chance, double patch) {
        switch (span.cover()) {
            case MEADOW, FLOWERS -> {
                if (patch > -0.08 && chance < 13) {
                    plant(p, x, y, z, flora.grass);
                    return true;
                }
            }
            case WOODLAND, FERN -> {
                if (patch > 0.18 && chance < 12) {
                    plant(p, x, y, z, flora.mushroom);
                    return true;
                }
                if (patch < -0.25 && chance < 8) {
                    p.set(x, y - 1, z, OrthBlocks.GLOW_MOSS.get().defaultBlockState());
                    plant(p, x, y, z, Blocks.MOSS_CARPET);
                    return true;
                }
            }
            case WETLAND, POOL -> {
                if (chance < 13 && (span.cover() == EdgeLandscape.Cover.POOL
                        ? span.water() == y : patch > 0.08)) {
                    plant(p, x, span.water() + 1, z, flora.reeds);
                    return true;
                }
            }
            default -> {}
        }
        if (patch > 0.22 && chance < 9 && p.get(x, span.bottom(), z).is(OrthBlocks.STONE.get())) {
            p.hang(x, span.bottom() - 1, z, flora.roots.defaultBlockState());
        }
        return false;
    }

    static void trimUnsupportedRoots(ChunkAccess chunk) {
        var painter = new Painter(chunk, 0,
                chunk.getMinBuildHeight() == EdgeTerrain.MIN_Y ? EdgeTerrain.MIN_Y + 1 : OrthTerrain.SHARED_BOTTOM);
        var roots = EdgeFlora.INSTANCE.roots;
        for (int i = 0; i < chunk.getSectionsCount(); i++) {
            int bottom = chunk.getMinBuildHeight() + 16 * i;
            if (bottom >= 0) break;
            if (!chunk.getSection(i).maybeHas(state -> state.is(roots) || state.is(Blocks.HANGING_ROOTS)
                    || state.is(Blocks.SPORE_BLOSSOM))) continue;
            for (int y = bottom + 15; y >= bottom; y--) {
                for (int z = painter.z0; z < painter.z0 + 16; z++) {
                    for (int x = painter.x0; x < painter.x0 + 16; x++) {
                        if (!painter.inside(x, y, z)) continue;
                        var plant = painter.get(x, y, z);
                        if (!plant.is(roots) && !plant.is(Blocks.HANGING_ROOTS) && !plant.is(Blocks.SPORE_BLOSSOM)) continue;
                        if (!painter.hasOverheadSupport(x, y, z)) painter.set(x, y, z, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    private record EdgeFlora(Block grass, Block mushroom, Block reeds, Block roots) {
        private static final EdgeFlora INSTANCE = new EdgeFlora(block("abyss_grass_tuft"), block("glow_shroom"),
                block("abyss_reeds"), block("hanging_roots"));

        private static Block block(String name) {
            return BuiltInRegistries.BLOCK.get(new ResourceLocation(FantasyTruth.ID, name));
        }
    }

    private static void plant(Painter p, int x, int y, int z, Block block) { p.grow(x, y, z, block.defaultBlockState()); }

    private static void tall(Painter p, int x, int y, int z, Block block) {
        if (!p.get(x, y, z).isAir() || !p.get(x, y + 1, z).isAir()) return;
        p.grow(x, y, z, block.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
        p.grow(x, y + 1, z, block.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
    }

    static boolean treeHabitat(EdgeLandscape.Cover cover, int hash, boolean forested) {
        int roll = Math.floorMod(hash, 100);
        return roll < switch (cover) {
            case WOODLAND -> forested ? 64 : 43;
            case FERN -> forested ? 46 : 29;
            case WETLAND -> forested ? 24 : 15;
            case MEADOW -> 12;
            case FLOWERS -> 8;
            case SCREE -> 9;
            case TRAIL, POOL, PIT_MOSS -> 0;
        };
    }

    static void fallenTree(Painter p, Site s, int depth, EdgeTerrain.Layout layout) {
        int seed = EdgeExploration.hash(s.x(), s.y(), s.z());
        double radial = Math.atan2(OrthIsland.localZ(s.z()), OrthIsland.localX(s.x()));
        double heading = radial + ((seed & 1) == 0 ? 1 : -1) * (1.3 + (seed >>> 3 & 7) * 0.065);
        double dx = Math.cos(heading), dz = Math.sin(heading);
        int length = 24 + (seed >>> 8 & 15);
        int stumpHeight = 7 + (seed >>> 15 & 7);
        BlockState upright = OrthBlocks.PETRIFIED_LOG.get().defaultBlockState();
        BlockState fallen = upright.setValue(RotatedPillarBlock.AXIS,
                Math.abs(dx) >= Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z);
        limb(p, new Site(s.x(), s.y() - 2, s.z()), new Site(s.x() - 2, s.y() + stumpHeight, s.z() + 1), 4, 2, upright);
        for (int arm = 0; arm < 5; arm++) {
            root(p, s, heading + arm * Math.PI * 2 / 5, 5 + (seed >>> arm & 3), -2, 3, fallen);
        }
        int center = s.y() + 2;
        for (int i = 4; i <= length; i++) {
            int x = s.x() + (int) Math.round(i * dx + Math.sin(i * 0.19) * dz * 2);
            int z = s.z() + (int) Math.round(i * dz - Math.sin(i * 0.19) * dx * 2);
            var ground = EdgeLandscape.at(x, z, depth, layout);
            if (ground == null || ground.water() > ground.top() || ground.cover() == EdgeLandscape.Cover.TRAIL) break;
            int r = i < 15 ? 3 : i < 28 ? 2 : 1;
            center = Mth.clamp(ground.top() + r, center - 1, center + 1);
            for (int side = -r; side <= r; side++) {
                int bx = x - (int) Math.round(side * dz), bz = z + (int) Math.round(side * dx);
                int cap = (int) Math.sqrt(r * r - side * side);
                for (int y = -cap; y <= cap; y++) p.wood(bx, center + y, bz, fallen);
                if ((EdgeExploration.hash(bx, center, bz) & 7) < 5 && p.inside(bx, center + cap, bz)
                        && p.get(bx, center + cap, bz).is(fallen.getBlock())) {
                    p.grow(bx, center + cap + 1, bz, Blocks.MOSS_CARPET.defaultBlockState());
                }
            }
            if ((i == 11 || i == 21) && length > i + 4) {
                var branch = new Site(x, center, z);
                var splinter = offset(branch, heading + (i == 11 ? 0.8 : -1.1), 6, 4);
                limb(p, branch, splinter, 1, 0, fallen);
            }
        }
    }

    static int treeKind(EdgeLandscape.Cover cover, int hash) {
        int roll = hash >>> 9 & 7;
        return switch (cover) {
            case SCREE -> roll < 3 ? 2 : 4;
            case WETLAND -> roll < 3 ? 3 : roll < 6 ? 0 : 1;
            case FERN, WOODLAND -> roll < 3 ? 0 : roll < 6 ? 1 : 4;
            default -> roll < 3 ? 3 : roll < 6 ? 0 : 2;
        };
    }

    static void tree(Painter p, Site s, int kind, int hash) {
        var rng = new SplittableRandom(hash);
        boolean elder = kind == ELDER;
        int height = switch (kind) {
            case 0 -> rng.nextInt(14, 25);
            case 1 -> rng.nextInt(20, 33);
            case 2 -> rng.nextInt(12, 26);
            case 3 -> rng.nextInt(9, 16);
            case 4 -> rng.nextInt(6, 12);
            default -> rng.nextInt(43, 54);
        };
        int width = elder ? 5 : kind == 1 ? 3 : kind >= 3 ? 1 : 2;
        BlockState bark = switch (kind) {
            case 0, 4 -> Blocks.OAK_LOG.defaultBlockState();
            case 1 -> Blocks.DARK_OAK_LOG.defaultBlockState();
            case 3 -> Blocks.BIRCH_LOG.defaultBlockState();
            default -> OrthBlocks.PETRIFIED_LOG.get().defaultBlockState();
        };
        BlockState foliage = switch (kind) {
            case 0 -> leaves(Blocks.OAK_LEAVES);
            case 1 -> leaves(Blocks.DARK_OAK_LEAVES);
            case 3 -> leaves(Blocks.BIRCH_LEAVES);
            case 4 -> leaves(Blocks.FLOWERING_AZALEA_LEAVES);
            default -> leaves(Blocks.AZALEA_LEAVES);
        };
        double start = rng.nextDouble(0, Math.PI * 2);
        int leanX = (int) Math.round((elder ? 9 : 5) * Math.cos(start));
        int leanZ = (int) Math.round((elder ? 9 : 5) * Math.sin(start));
        int bendX = (int) Math.round(width * Math.sin(start));
        int bendZ = -(int) Math.round(width * Math.cos(start));
        Site fork = new Site(s.x() + bendX, s.y() + height / 3, s.z() + bendZ);
        Site crown = new Site(s.x() + leanX, s.y() + height, s.z() + leanZ);
        limb(p, new Site(s.x(), s.y() - 3, s.z()), fork, width, Math.max(1, width - 1), bark);
        limb(p, fork, crown, Math.max(1, width - 1), 0, bark);
        int roots = elder ? 8 : 4 + (hash & 1);
        for (int i = 0; i < roots; i++) {
            double angle = start + i * Math.PI * 2 / roots + rng.nextDouble(-0.27, 0.27);
            int length = width + rng.nextInt(3, elder ? 13 : 7);
            int drop = -2 - rng.nextInt(3);
            root(p, s, angle, length, drop, width, bark);
        }
        int branches = elder ? 9 : 4 + (hash >>> 4 & 3);
        for (int i = 0; i < branches; i++) {
            double angle = start + i * 2.399963 + rng.nextDouble(-0.45, 0.45);
            double f = 0.46 + i * 0.43 / (branches - 1);
            Site base = mix(fork, crown, f);
            int reach = elder ? rng.nextInt(11, 22) : rng.nextInt(4, Math.max(7, height / 2));
            Site elbow = offset(base, angle, reach * 2 / 3, rng.nextInt(1, 5));
            Site tip = offset(elbow, angle + rng.nextDouble(-0.55, 0.55), reach / 3, rng.nextInt(2, 7));
            limb(p, base, elbow, elder ? 2 : 1, 0, bark);
            limb(p, elbow, tip, 0, 0, bark);
            if (kind == 2 && i % 3 != 0) continue;
            int radius = elder ? rng.nextInt(5, 9) : rng.nextInt(3, kind == 1 ? 8 : 6);
            canopy(p, tip, radius, Math.max(2, radius / 2), foliage, hash + i * 31);
            Site twig = offset(elbow, angle + (i % 2 == 0 ? 0.9 : -0.9), Math.max(2, reach / 3), 2);
            limb(p, elbow, twig, 0, 0, bark);
            canopy(p, twig, Math.max(2, radius - 2), 2, foliage, hash - i * 21);
            if ((i & 3) == 1) {
                p.hang(tip.x(), tip.y() - 1, tip.z(), Blocks.HANGING_ROOTS.defaultBlockState());
            }
        }
        if (kind != 2) canopy(p, crown, elder ? 9 : 4 + width, elder ? 4 : 3, foliage, hash);
    }

    private static BlockState leaves(Block block) {
        return block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    private static Site offset(Site start, double angle, int length, int rise) {
        return new Site(start.x() + (int) Math.round(length * Math.cos(angle)), start.y() + rise,
                start.z() + (int) Math.round(length * Math.sin(angle)));
    }

    private static Site mix(Site from, Site to, double t) {
        return new Site((int) Math.round(Mth.lerp(t, from.x(), to.x())),
                (int) Math.round(Mth.lerp(t, from.y(), to.y())), (int) Math.round(Mth.lerp(t, from.z(), to.z())));
    }

    private static void root(Painter p, Site base, double angle, int length, int drop, int width, BlockState bark) {
        Site end = null;
        for (int reach = length; reach >= 3 && end == null; reach -= 2) {
            Site target = offset(base, angle, reach, drop);
            int ground = p.shelfBelow(target.x(), target.y() + 7, target.z(), base.y() - 9);
            if (ground != EdgeTerrain.NO_SHELF) end = new Site(target.x(), ground, target.z());
        }
        if (end != null) limb(p, new Site(base.x(), base.y() + Math.min(2, width - 1), base.z()),
                end, Math.max(0, width - 2), 0, bark);
    }

    private static void limb(Painter p, Site from, Site to, int start, int end, BlockState bark) {
        int dx = Math.abs(from.x() - to.x()), dy = Math.abs(from.y() - to.y()), dz = Math.abs(from.z() - to.z());
        int steps = Math.max(1, Math.max(dx, Math.max(dy, dz)) * 2);
        Direction.Axis axis = dy >= dx && dy >= dz ? Direction.Axis.Y : dx >= dz ? Direction.Axis.X : Direction.Axis.Z;
        BlockState state = bark.setValue(RotatedPillarBlock.AXIS, axis);
        Site previous = from;
        for (int j = 0; j <= steps; j++) {
            double t = j / (double) steps;
            Site s = mix(from, to, t);
            int r = (int) Math.round(Mth.lerp(t, start, end));
            p.wood(s.x(), previous.y(), previous.z(), state);
            p.wood(s.x(), s.y(), previous.z(), state);
            p.wood(s.x(), s.y(), s.z(), state);
            previous = s;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z <= r * r + 1) {
                        p.wood(s.x() + x, s.y(), s.z() + z, state);
                    }
                }
            }
        }
    }

    private static void canopy(Painter p, Site s, int radius, int height, BlockState leaves, int salt) {
        for (int x = Math.max(p.x0, s.x() - radius); x <= Math.min(p.x0 + 15, s.x() + radius); x++) {
            for (int z = Math.max(p.z0, s.z() - radius); z <= Math.min(p.z0 + 15, s.z() + radius); z++) {
                double dx = (x - s.x()) / (double) radius, dz = (z - s.z()) / (double) radius;
                for (int y = s.y() - height; y <= s.y() + height; y++) {
                    double dy = (y - s.y()) / (double) height;
                    double shape = dx * dx + dz * dz * 1.18 + dy * dy;
                    int grain = EdgeExploration.hash(x + salt, y, z) & 15;
                    if (shape < 0.61 || shape < 1.06 && grain > 3 + shape * 5) p.grow(x, y, z, leaves);
                }
            }
        }
    }

    private EdgeVegetation() {}
}
