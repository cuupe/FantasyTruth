package dev.fantasytruth.abyss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Small noctilucent insect with a short flight radius around its pool. */
public final class Rohana extends PathfinderMob {
    private Vec3 home;

    public Rohana(EntityType<? extends Rohana> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 3).add(Attributes.MOVEMENT_SPEED, 0.19);
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (home == null) home = position();
        double turn = (tickCount + getId() * 13) * 0.045;
        Vec3 goal = home.add(2.7 * Math.cos(turn), 0.65 * Math.sin(turn * 1.6), 2.7 * Math.sin(turn));
        Vec3 course = goal.subtract(position());
        Vec3 motion = getDeltaMovement().lerp(course.normalize().scale(0.16), 0.16);
        if (level().noCollision(this, getBoundingBox().move(motion))) setDeltaMovement(motion);
        else setDeltaMovement(home.subtract(position()).normalize().scale(0.10));
        if (motion.horizontalDistanceSqr() > 0.002) {
            setYRot(Mth.approachDegrees(getYRot(), (float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90, 12));
            yBodyRot = yHeadRot = getYRot();
        }
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) {
            tag.putDouble("HomeX", home.x);
            tag.putDouble("HomeY", home.y);
            tag.putDouble("HomeZ", home.z);
        }
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeY")) home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
    }
}
