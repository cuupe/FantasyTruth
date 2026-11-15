package dev.fantasytruth.abyss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;

/** Spawn on loaded forest floors and banks, including in previously generated chunks. */
final class ForestFauna {
    static void tick(ServerPlayer player, ServerLevel level, AbyssChunkGenerator generator) {
        if (player.getBlockY() > ForestTerrain.INVERTED + 8 || (player.tickCount + player.getId()) % 83 != 0
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        var local = player.getBoundingBox().inflate(52, 23, 52);
        boolean group = level.getDifficulty() != Difficulty.PEACEFUL && level.getEntitiesOfClass(Inbyo.class, local).isEmpty();
        boolean ottobas = level.getEntitiesOfClass(Ottobas.class, local).isEmpty();
        boolean rohana = level.getEntitiesOfClass(Rohana.class, local).size() < 4;
        if (!group && !ottobas && !rohana) return;
        var terrain = generator.forest(level.getChunkSource().randomState());
        var rng = player.getRandom();
        for (int i = 0; i < 14 && (group || ottobas || rohana); i++) {
            double angle = rng.nextDouble() * Math.PI * 2, distance = 8 + rng.nextInt(30);
            var probe = BlockPos.containing(player.getX() + distance * Math.cos(angle), player.getY(),
                    player.getZ() + distance * Math.sin(angle));
            if (!level.hasChunkAt(probe) || AbyssLayer.passage(probe.getX(), probe.getZ(), 55)
                    || probe.distToLowCornerSqr(ForestTerrain.CAMP_X, probe.getY(), ForestTerrain.CAMP_Z) < 42 * 42) continue;
            var c = terrain.column(probe.getX(), probe.getZ());
            if (c.chute()) continue;
            int springWater = ForestSpring.waterLine(terrain, probe.getX(), probe.getZ());
            int floor = Math.max(c.floor(), springWater);
            if (group && c.ledgeTop() < ForestTerrain.INVERTED && Math.abs(c.ledgeTop() - probe.getY()) <= 19
                    && c.ledgeTop() > floor + 4) {
                group = !pack(level, probe.atY(c.ledgeTop() + 1));
            }
            if (Math.abs(floor - probe.getY()) > 20) continue;
            if (group && (c.damp() || rng.nextBoolean())) group = !pack(level, probe.atY(floor + 1));
            if (!ottobas && !rohana) continue;
            int water = Math.max(c.floorWater(), springWater);
            if (water < c.floor() - 2) continue;
            var surface = probe.atY(water);
            if (!wet(level, surface)) continue;
            if (rohana && spawn(level, AbyssEntities.ROHANA.get(), surface.above(2))) {
                rohana = false;
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    var next = surface.relative(direction, 2);
                    if (wet(level, next)) spawn(level, AbyssEntities.ROHANA.get(), next.above(1));
                }
            }
            var shallows = surface.below();
            if (ottobas && wet(level, shallows) && wet(level, shallows.below())) {
                ottobas = !spawn(level, AbyssEntities.OTTOBAS.get(), shallows);
            }
        }
    }

    private static boolean wet(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getFluidState(pos).is(FluidTags.WATER);
    }

    private static boolean pack(ServerLevel level, BlockPos first) {
        if (!onLand(level, first) || !spawn(level, AbyssEntities.INBYO.get(), first)) return false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            var side = first.relative(direction, 3);
            for (int dy = -2; dy <= 2; dy++) {
                var spot = side.above(dy);
                if (onLand(level, spot) && spawn(level, AbyssEntities.INBYO.get(), spot)) break;
            }
        }
        return true;
    }

    private static boolean onLand(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private static <T extends Mob> boolean spawn(ServerLevel level, EntityType<T> type, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.hasChunksAt(pos.offset(-2, 0, -2), pos.offset(2, 0, 2))) return false;
        var mob = type.create(level);
        if (mob == null) return false;
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        if (!level.noCollision(mob, mob.getBoundingBox())) return false;
        return level.addFreshEntity(mob);
    }

    private ForestFauna() {}
}
