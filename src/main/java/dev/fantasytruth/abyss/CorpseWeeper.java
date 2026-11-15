package dev.fantasytruth.abyss;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Nest-bound predator. It calls from cover before sweeping down across the canopy. */
public final class CorpseWeeper extends Monster {
    private static final EntityDataAccessor<Boolean> SWOOP = SynchedEntityData.defineId(CorpseWeeper.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(CorpseWeeper.class, EntityDataSerializers.BOOLEAN);
    public final net.minecraft.world.entity.AnimationState attackAnimation = new net.minecraft.world.entity.AnimationState();
    private BlockPos roost;
    private int delay;
    private int sweep;

    public CorpseWeeper(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 10;
    }

    static AttributeSupplier.Builder attributes() {
        return createMonsterAttributes().add(Attributes.MAX_HEALTH, 34).add(Attributes.FOLLOW_RANGE, 42)
                .add(Attributes.ATTACK_DAMAGE, 6).add(Attributes.MOVEMENT_SPEED, 0.28);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SWOOP, false);
        entityData.define(FLYING, false);
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide()) attackAnimation.animateWhen(entityData.get(SWOOP), tickCount);
    }
    public boolean flying() { return entityData.get(FLYING); }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (roost == null) roost = blockPosition();
        Vec3 home = Vec3.atBottomCenterOf(roost);
        if (tickCount % 8 == 1 || getTarget() == null) {
            Player nearby = level().getNearestPlayer(this, 42);
            setTarget(nearby != null && canAttack(nearby) && nearby.distanceToSqr(home) < 2800 ? nearby : null);
        }
        LivingEntity target = getTarget();
        if (delay > 0) delay--;
        if (sweep > 0) sweep--;
        if (target != null && delay == 0 && sweep == 0 && distanceTo(target) < 26 && hasLineOfSight(target)) {
            sweep = 28;
            delay = 78;
        }
        boolean attack = sweep > 0;
        entityData.set(SWOOP, attack);

        if (target != null && !attack && tickCount % 115 == 15) {
            // Corpses' calls are represented by a vanilla distressed villager, played at the bird's hiding place.
            playSound(SoundEvents.VILLAGER_HURT, 1.3F, 0.83F + 0.17F * random.nextFloat());
        }
        if (target == null && distanceToSqr(home) < 5) {
            setNoGravity(false);
            entityData.set(FLYING, !onGround());
            return;
        }
        setNoGravity(true);
        entityData.set(FLYING, true);
        double phase = tickCount * 0.039 + getId();
        Vec3 goal = home;
        if (target != null) {
            goal = attack ? target.position().add(0, 0.65, 0)
                    : home.add(Math.cos(phase) * 10, 5 + 2 * Math.sin(phase), Math.sin(phase) * 10);
            if (attack && distanceToSqr(target) < 4.8 && hasLineOfSight(target)) {
                doHurtTarget(target);
                sweep = 0;
            }
        }
        Vec3 course = goal.subtract(position());
        if (horizontalCollision) course = new Vec3(-course.z, Math.max(2, course.y), course.x);
        Vec3 velocity = course.normalize().scale(attack ? 0.47 : 0.27);
        setDeltaMovement(getDeltaMovement().scale(0.62).add(velocity.scale(0.38)));
        if (course.horizontalDistanceSqr() > 0.01) {
            float yaw = (float) (Mth.atan2(course.z, course.x) * Mth.RAD_TO_DEG) - 90;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 19));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
        fallDistance = 0;
    }

    @Override protected SoundEvent getHurtSound(DamageSource damage) { return SoundEvents.PHANTOM_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.PHANTOM_DEATH; }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (roost != null) tag.putLong("Roost", roost.asLong());
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Roost")) roost = BlockPos.of(tag.getLong("Roost"));
    }
}
