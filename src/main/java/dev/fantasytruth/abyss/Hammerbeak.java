package dev.fantasytruth.abyss;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class Hammerbeak extends EdgeFlyer {
    private int alarm;
    private int cooldown;
    private int strike;
    private ItemEntity shiny;

    public Hammerbeak(EntityType<? extends Hammerbeak> type, Level level) {
        super(type, level);
        setDropChance(EquipmentSlot.MAINHAND, 1);
    }

    static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 14).add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.FOLLOW_RANGE, 26).add(Attributes.MOVEMENT_SPEED, 0.28);
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide() && source.getEntity() instanceof LivingEntity attacker && canAttack(attacker)) {
            defend(attacker);
            for (var bird : level().getEntitiesOfClass(Hammerbeak.class, getBoundingBox().inflate(19, 10, 19))) {
                if (bird.canAttack(attacker)) bird.defend(attacker);
            }
        }
        return hit;
    }

    private void defend(LivingEntity attacker) {
        if (getTarget() != attacker) {
            setTarget(attacker);
            cooldown = 9 + random.nextInt(28);
        }
        alarm = 220;
    }

    @Override protected void customServerAiStep() {
        if (cooldown > 0) cooldown--;
        if (alarm > 0) alarm--;
        if (tickCount % 40 == 0 && getTarget() == null) {
            level().getEntitiesOfClass(CrimsonSplitjaw.class, getBoundingBox().inflate(18), jaw ->
                    jaw.getHealth() < jaw.getMaxHealth() * 0.32F && hasLineOfSight(jaw))
                    .stream().findFirst().ifPresent(this::defend);
        }
        var target = getTarget();
        Vec3 roost = home();
        if (target != null && (alarm == 0 || !target.isAlive() || !canAttack(target) || target.distanceToSqr(roost) > 920)) {
            target = null;
            setTarget(null);
        }
        if (target == null) {
            action(CRUISE);
            forage(roost);
            return;
        }
        if (getHealth() <= 4) {
            action(CRUISE);
            Vec3 away = position().subtract(target.position()).normalize();
            steer(position().add(away.scale(9)).add(0, 4, 0), 0.49, false);
            return;
        }
        if (cooldown == 0 && strike == 0 && distanceToSqr(target) < 115 && hasLineOfSight(target)) strike = 15;
        if (strike > 0) {
            strike--;
            action(STRIKE);
            steer(target.position().add(0, target.getBbHeight() * 0.55, 0), 0.56, false);
            if (distanceToSqr(target) < 3.2 && hasLineOfSight(target)) {
                doHurtTarget(target);
                playSound(SoundEvents.WOOD_HIT, 0.8F, 1.4F);
                strike = 0;
            }
            if (strike == 0) cooldown = 22 + random.nextInt(22);
        } else {
            action(CRUISE);
            double orbit = tickCount * 0.053 + getId() * 2.4;
            steer(target.position().add(5.5 * Math.cos(orbit), 2.8, 5.5 * Math.sin(orbit)), 0.34, false);
        }
    }

    private void forage(Vec3 roost) {
        if (!getMainHandItem().isEmpty()) {
            if (distanceToSqr(roost) < 2) {
                spawnAtLocation(getMainHandItem().copy());
                setItemSlot(EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
                cooldown = 200;
            } else steer(roost, 0.25, false);
            return;
        }
        if (!level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) shiny = null;
        else if (cooldown == 0 && tickCount % 40 == 0) {
            shiny = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(9), item ->
                    item.isAlive() && item.distanceToSqr(roost) < 144
                    && (item.getItem().is(ItemTags.BEACON_PAYMENT_ITEMS) || item.getItem().is(Items.AMETHYST_SHARD)))
                    .stream().filter(this::hasLineOfSight).findFirst().orElse(null);
        }
        if (shiny != null && shiny.isAlive()) {
            steer(shiny.position().add(0, 0.4, 0), 0.23, false);
            if (distanceToSqr(shiny) < 1.6) {
                var stack = shiny.getItem().copy();
                setItemSlot(EquipmentSlot.MAINHAND, stack.split(1));
                if (stack.isEmpty()) shiny.discard();
                else shiny.setItem(stack);
                shiny = null;
            }
            return;
        }
        double phase = tickCount * 0.031 + getId();
        steer(roost.add(Math.cos(phase) * 5, 1 + Math.sin(phase) * 0.8, Math.sin(phase) * 5), 0.22, false);
    }
}
