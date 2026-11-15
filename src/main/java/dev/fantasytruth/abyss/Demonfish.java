package dev.fantasytruth.abyss;

import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;

/** A pond predator that pursues nearby swimmers and stays below the surface. */
public final class Demonfish extends WaterAnimal {
    public final AnimationState attackAnimation = new AnimationState();
    private Vec3 home;
    private int biteDelay;
    private int animatedBite;
    private net.minecraft.world.entity.player.Player prey;

    public Demonfish(EntityType<? extends Demonfish> type, Level level) {
        super(type, level);
    }

    static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 14).add(Attributes.ATTACK_DAMAGE, 4);
    }
    @Override protected boolean shouldDespawnInPeaceful() { return true; }

    @Override public boolean checkSpawnObstruction(LevelReader level) {
        return level.getFluidState(blockPosition()).is(FluidTags.WATER) && super.checkSpawnObstruction(level);
    }

    @Override public void handleEntityEvent(byte event) {
        if (event == 4) {
            attackAnimation.start(tickCount);
            animatedBite = tickCount;
        } else super.handleEntityEvent(event);
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide() && tickCount - animatedBite > 35) attackAnimation.stop();
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (!isInWater()) return;
        if (home == null) home = position();
        if (biteDelay > 0) biteDelay--;
        if (tickCount % 7 == 0) {
            var player = level().getNearestPlayer(this, 11);
            prey = player != null && player.isInWater() && canAttack(player) && hasLineOfSight(player)
                    && player.distanceToSqr(home) < 225 ? player : null;
        }
        boolean chasing = prey != null && prey.isInWater() && canAttack(prey);
        double turn = (tickCount + getId() * 19) * 0.033;
        Vec3 patrol = home.add(5 * Math.cos(turn), 0.65 * Math.sin(turn * 1.7), 5 * Math.sin(turn));
        Vec3 goal = chasing && biteDelay < 17 ? prey.position().add(prey.getDeltaMovement().scale(3))
                : patrol;
        Vec3 course = goal.subtract(position());
        if (!wetAt(goal) || !wetAt(position().add(course.normalize().scale(1.3)))) {
            course = home.subtract(position());
        }
        if (chasing && distanceToSqr(prey) < 2.2 && biteDelay == 0 && hasLineOfSight(prey)) {
            doHurtTarget(prey);
            level().broadcastEntityEvent(this, (byte) 4);
            biteDelay = 31;
        }
        var speed = course.normalize().scale(chasing ? 0.24 : 0.12);
        setDeltaMovement(getDeltaMovement().lerp(speed, 0.30));
        if (course.horizontalDistanceSqr() > 0.01) {
            setYRot(Mth.approachDegrees(getYRot(), (float) (Mth.atan2(course.z, course.x) * Mth.RAD_TO_DEG) - 90, 15));
            yBodyRot = yHeadRot = getYRot();
        }
    }

    private boolean wetAt(Vec3 position) {
        var block = net.minecraft.core.BlockPos.containing(position);
        return level().hasChunkAt(block) && level().getFluidState(block).is(FluidTags.WATER);
    }

    @Override public void travel(Vec3 input) {
        if (isInWater() && isEffectiveAi()) {
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.92));
        } else super.travel(input);
    }
}
