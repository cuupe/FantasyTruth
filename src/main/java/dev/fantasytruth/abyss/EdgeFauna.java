package dev.fantasytruth.abyss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Populate loaded habitat near explorers, including existing first-layer saves. */
final class EdgeFauna {
    static void tick(ServerPlayer player, ServerLevel level, AbyssEdgeGenerator generator) {
        if (player.isSpectator() || !player.isAlive() || player.getBlockY() > -12 || player.getBlockY() < -1335
                || !AbyssLayer.passage(player.getX(), player.getZ(), 566)
                || (player.tickCount + player.getId()) % 91 != 0
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        var random = player.getRandom();
        var local = player.getBoundingBox().inflate(52, 23, 52);
        int birds = level.getEntitiesOfClass(Hammerbeak.class, local).size();
        if (birds < 3) flock(player, level, generator.layout(), random, 4 - birds);
        if (level.getDifficulty() == Difficulty.PEACEFUL) return;
        if (level.getEntitiesOfClass(Silkfang.class, local).isEmpty()) den(player, level, generator.caves(level.getSeed()));
        int swimmers = level.getEntitiesOfClass(Demonfish.class, local).size();
        if (swimmers < 3) fish(player, level, generator.layout(), random, swimmers < 2);
        if (random.nextInt(36) == 0 && level.getEntitiesOfClass(CrimsonSplitjaw.class, local.inflate(36)).isEmpty()) {
            migrant(player, level, random);
        }
    }

    private static void flock(ServerPlayer player, ServerLevel level, EdgeTerrain.Layout layout, RandomSource random, int count) {
        for (int attempt = 0; attempt < 14; attempt++) {
            BlockPos sample = sample(player, random, 18, 41);
            if (!level.hasChunkAt(sample) || EdgeExploration.nearLandmark(sample.getX(), sample.getY(), sample.getZ())) continue;
            for (var shelf : EdgeTerrain.column(sample.getX(), sample.getZ(), layout).shelves()) {
                if (Math.abs(shelf.top() - sample.getY()) > 23 || shelf.cover() == EdgeLandscape.Cover.POOL
                        || shelf.cover() == EdgeLandscape.Cover.PIT_MOSS || shelf.cover() == EdgeLandscape.Cover.TRAIL) continue;
                var roost = sample.atY(shelf.top() + 3);
                if (!level.getBlockState(roost.below(3)).blocksMotion() || !level.getBlockState(roost).isAir()) continue;
                if (spawn(level, AbyssEntities.HAMMERBEAK.get(), roost, null)) {
                    for (int i = 1; i < count; i++) {
                        spawn(level, AbyssEntities.HAMMERBEAK.get(),
                                roost.offset(random.nextInt(9) - 4, random.nextInt(3), random.nextInt(9) - 4), null);
                    }
                    return;
                }
            }
        }
    }

    private static void den(ServerPlayer player, ServerLevel level, EdgeCaves.Plan plan) {
        var current = player.chunkPosition();
        for (int cz = current.z - 2; cz <= current.z + 2; cz++) for (int cx = current.x - 2; cx <= current.x + 2; cx++) {
            var caves = plan.byChunk().get(ChunkPos.asLong(cx, cz));
            if (caves == null) continue;
            for (var cave : caves) {
                if (!cave.silkfangDen()) continue;
                var room = cave.room();
                var den = new BlockPos(room.x(), room.floor() + 1, room.z());
                double distance = player.distanceToSqr(den.getX() + 0.5, den.getY(), den.getZ() + 0.5);
                if (distance < 196 || distance > 2100 || !level.hasChunkAt(den)
                        || !level.getEntitiesOfClass(Silkfang.class, new AABB(den).inflate(13)).isEmpty()) continue;
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    var perch = den.relative(side, 3);
                    if (level.getBlockState(perch.below()).blocksMotion()
                            && spawn(level, AbyssEntities.SILKFANG.get(), perch, den)) return;
                }
            }
        }
    }

    private static void fish(ServerPlayer player, ServerLevel level, EdgeTerrain.Layout layout, RandomSource random, boolean school) {
        for (int attempt = 0; attempt < 13; attempt++) {
            var sample = sample(player, random, 9, 37);
            if (!level.hasChunkAt(sample)) continue;
            for (var span : EdgeTerrain.column(sample.getX(), sample.getZ(), layout).shelves()) {
                if (span.water() - span.top() < 2 || Math.abs(span.water() - sample.getY()) > 24) continue;
                var water = sample.atY(span.water() - 1);
                if (!level.getFluidState(water).is(FluidTags.WATER)) continue;
                if (spawn(level, AbyssEntities.DEMONFISH.get(), water, null)) {
                    if (school) for (Direction side : Direction.Plane.HORIZONTAL) {
                        if (spawn(level, AbyssEntities.DEMONFISH.get(), water.relative(side, 2), null)) break;
                    }
                    return;
                }
            }
        }
    }

    private static void migrant(ServerPlayer player, ServerLevel level, RandomSource random) {
        for (int attempt = 0; attempt < 8; attempt++) {
            var place = sample(player, random, 28, 49).above(5 + random.nextInt(6));
            if (AbyssLayer.passage(place.getX(), place.getZ(), 96) || !AbyssLayer.passage(place.getX(), place.getZ(), 410)
                    || !level.hasChunkAt(place) || !level.getBlockState(place).isAir()) continue;
            if (spawn(level, AbyssEntities.SPLITJAW.get(), place, null)) return;
        }
    }

    private static BlockPos sample(ServerPlayer player, RandomSource random, int min, int max) {
        double angle = random.nextDouble() * Math.PI * 2;
        double radius = min + random.nextDouble() * (max - min);
        return BlockPos.containing(player.getX() + radius * Math.cos(angle), player.getY(),
                player.getZ() + radius * Math.sin(angle));
    }

    private static <T extends Mob> boolean spawn(ServerLevel level, EntityType<T> type, BlockPos place, BlockPos den) {
        if (!level.hasChunkAt(place) || level.isOutsideBuildHeight(place) || level.getBlockState(place).is(Blocks.COBWEB)) return false;
        T mob = type.create(level);
        if (mob == null) return false;
        mob.moveTo(place.getX() + 0.5, place.getY(), place.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        if (!level.noCollision(mob, mob.getBoundingBox()) || !mob.checkSpawnObstruction(level)) return false;
        if (mob instanceof Silkfang silkfang && den != null) silkfang.occupy(den);
        return level.addFreshEntity(mob);
    }

    private EdgeFauna() {}
}
