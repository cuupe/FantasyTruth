package dev.fantasytruth.abyss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class Silkfang extends Spider {
    public final AnimationState strikeAnimation = new AnimationState();
    private BlockPos den;
    private int venomWindup;
    private int cooldown;
    private int attackTick;
    private Vec3 venomAim = Vec3.ZERO;

    public Silkfang(EntityType<? extends Spider> type, Level level) { super(type, level); }

    @Override protected void registerGoals() {
        // The vanilla spider's roaming and target goals would pull it out of its web territory.
    }

    static AttributeSupplier.Builder attributes() {
        return createMonsterAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.MOVEMENT_SPEED, 0.39)
                .add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.FOLLOW_RANGE, 14);
    }

    void occupy(BlockPos position) { den = position.immutable(); }

    @Override protected void customServerAiStep() {
        if (den == null) den = blockPosition();
        if (cooldown > 0) cooldown--;
        Vec3 home = Vec3.atBottomCenterOf(den);
        if (tickCount % 7 == 0) {
            var player = level().getNearestPlayer(this, 13);
            setTarget(player != null && canAttack(player) && player.distanceToSqr(home) < 44
                    && (hasLineOfSight(player) || player.getDeltaMovement().horizontalDistanceSqr() > 0.045) ? player : null);
        }
        var target = getTarget();
        if (target == null || target.distanceToSqr(home) >= 64 || !target.isAlive() || !canAttack(target)) {
            venomWindup = 0;
            setTarget(null);
            if (tickCount % 12 == 0 && distanceToSqr(home) > 2) getNavigation().moveTo(home.x, home.y, home.z, 1.0);
            return;
        }
        if (distanceToSqr(home) > 100) {
            setTarget(null);
            getNavigation().moveTo(home.x, home.y, home.z, 1.25);
            return;
        }
        engage(target);
    }

    private void engage(LivingEntity target) {
        double distance = distanceToSqr(target);
        getLookControl().setLookAt(target, 45, 35);
        if (venomWindup > 0) {
            getNavigation().stop();
            if (--venomWindup == 0) {
                if (distance <= 92 && hasLineOfSight(target)) {
                    var delta = venomAim.subtract(getEyePosition());
                    var world = (ServerLevel) level();
                    for (double step = 0.15; step <= 1; step += 0.15) {
                        var at = getEyePosition().add(delta.scale(step));
                        world.sendParticles(ParticleTypes.SPIT, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                    }
                    if (target.getEyePosition().distanceToSqr(venomAim) <= 2.4) {
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 72, 2));
                        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 55));
                    }
                }
                cooldown = 45;
            }
            return;
        }
        if (cooldown == 0 && hasLineOfSight(target)) {
            if (distance < 3.8) {
                doHurtTarget(target);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
                level().broadcastEntityEvent(this, (byte) 4);
                cooldown = 22;
                return;
            }
            if (distance < 80) {
                venomWindup = 11;
                venomAim = target.getEyePosition().add(target.getDeltaMovement().scale(4));
                playSound(SoundEvents.SPIDER_AMBIENT, 0.6F, 0.65F);
                level().broadcastEntityEvent(this, (byte) 4);
                return;
            }
        }
        if (tickCount % 8 == 0) getNavigation().moveTo(target, 1.20);
    }

    @Override public void handleEntityEvent(byte event) {
        if (event == 4) {
            strikeAnimation.start(tickCount);
            attackTick = tickCount;
        } else super.handleEntityEvent(event);
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide() && tickCount - attackTick > 23) strikeAnimation.stop();
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (den != null) tag.putLong("Den", den.asLong());
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Den")) den = BlockPos.of(tag.getLong("Den"));
    }
}
