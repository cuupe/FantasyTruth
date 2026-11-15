package dev.fantasytruth.abyss;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class CrimsonSplitjaw extends EdgeFlyer {
    private int attack;
    private int recovery;
    private int nextScan;
    private Vec3 strikePoint = Vec3.ZERO;

    public CrimsonSplitjaw(EntityType<? extends CrimsonSplitjaw> type, Level level) { super(type, level); }

    static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 82).add(Attributes.ATTACK_DAMAGE, 11)
                .add(Attributes.FOLLOW_RANGE, 47).add(Attributes.KNOCKBACK_RESISTANCE, 0.65);
    }

    @Override protected boolean shouldDespawnInPeaceful() { return true; }

    @Override protected void customServerAiStep() {
        var target = getTarget();
        Vec3 roost = home();
        if (target != null && (!target.isAlive() || !canAttack(target) || target.distanceToSqr(roost) > 3600)) {
            setTarget(null);
            target = null;
        }
        if (target == null && tickCount >= nextScan && getHealth() > 18) {
            nextScan = tickCount + 12;
            var player = level().getNearestPlayer(this, 42);
            if (player != null && canAttack(player) && hasLineOfSight(player)) target = player;
            else target = level().getEntitiesOfClass(Hammerbeak.class, getBoundingBox().inflate(25), this::hasLineOfSight)
                    .stream().min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            setTarget(target);
        }
        if (recovery > 0) recovery--;
        if (target == null || getHealth() <= 18) {
            attack = 0;
            action(CRUISE);
            double phase = tickCount * 0.018;
            if (target != null) {
                var away = position().subtract(target.position()).normalize();
                steer(position().add(away.scale(17)).add(0, 5, 0), 0.62, false);
            } else steer(roost.add(Math.cos(phase) * 13, 5 + Math.sin(phase * 2) * 4, Math.sin(phase) * 13), 0.37, false);
            return;
        }
        hunt(target);
    }

    private void hunt(LivingEntity target) {
        var center = target.position().add(0, target.getBbHeight() * 0.55, 0);
        if (attack == 0 && recovery == 0 && distanceToSqr(target) < 325 && hasLineOfSight(target)) {
            attack = 34;
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 1.8F, 1.45F);
        }
        if (attack > 20) {
            action(WINDUP);
            strikePoint = center.add(target.getDeltaMovement().scale(7));
            steer(strikePoint, 0.12, false);
            attack--;
            return;
        }
        if (attack > 0) {
            action(STRIKE);
            attack--;
            Vec3 course = strikePoint.subtract(position());
            if (!clear(course.normalize(), 2.2) || course.lengthSqr() < 1.8) {
                attack = 0;
            } else {
                steer(strikePoint, 1.03, true);
                if (distanceToSqr(target) < 9.5 && hasLineOfSight(target)) {
                    if (doHurtTarget(target)) target.knockback(0.65, getX() - target.getX(), getZ() - target.getZ());
                    attack = 0;
                }
            }
            if (attack == 0) recovery = 43;
            return;
        }
        action(CRUISE);
        double phase = tickCount * 0.046 + getId();
        double radius = recovery > 12 ? 11 : 6;
        steer(center.add(radius * Math.cos(phase), recovery > 12 ? 7 : 3, radius * Math.sin(phase)), 0.46, false);
    }
}
