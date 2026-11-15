package dev.fantasytruth.abyss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A freshwater animal that can leave the water and defends itself when provoked. */
public final class Ottobas extends PathfinderMob {
    public Ottobas(EntityType<? extends Ottobas> type, Level level) { super(type, level); }

    static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 42).add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.ATTACK_DAMAGE, 8).add(Attributes.KNOCKBACK_RESISTANCE, 0.55)
                .add(Attributes.FOLLOW_RANGE, 18);
    }

    @Override protected PathNavigation createNavigation(Level level) { return new AmphibiousPathNavigation(this, level); }

    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(3, new RandomStrollGoal(this, 0.86, 55));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 10));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override public boolean canBreatheUnderwater() { return true; }

    @Override public void travel(Vec3 input) {
        if (isInWater() && isEffectiveAi()) {
            // Vanilla movement still handles the ledge and land transitions.
            if (getNavigation().isDone()) setDeltaMovement(getDeltaMovement().add(0, 0.025, 0));
            else if (getMoveControl().hasWanted() && getMoveControl().getWantedY() > getY() + 0.5)
                setDeltaMovement(getDeltaMovement().add(0, 0.06, 0));
        }
        super.travel(input);
    }
}
