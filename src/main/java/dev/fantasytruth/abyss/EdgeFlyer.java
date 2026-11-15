package dev.fantasytruth.abyss;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Flight steering samples actual collision space, so the narrow Edge ledges do not act like ground paths. */
public abstract class EdgeFlyer extends PathfinderMob {
    private static final EntityDataAccessor<Byte> ACTION = SynchedEntityData.defineId(EdgeFlyer.class, EntityDataSerializers.BYTE);
    static final byte CRUISE = 0, WINDUP = 1, STRIKE = 2;
    private static final double[] TURNS = {0, 0.62, -0.62, 1.23, -1.23, 2.1, -2.1};
    private static final double[] PITCHES = {0, 0.62, -0.50};
    public final AnimationState strikeAnimation = new AnimationState();
    private BlockPos home;
    private Vec3 heading = Vec3.ZERO;

    protected EdgeFlyer(EntityType<? extends EdgeFlyer> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ACTION, CRUISE);
    }

    public byte action() { return entityData.get(ACTION); }
    protected void action(byte next) { entityData.set(ACTION, next); }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide()) strikeAnimation.animateWhen(action() == STRIKE, tickCount);
    }

    Vec3 home() {
        if (home == null) home = blockPosition();
        return Vec3.atBottomCenterOf(home);
    }

    void steer(Vec3 destination, double speed, boolean charge) {
        Vec3 direction = destination.subtract(position()).normalize();
        if (!charge && (tickCount % 4 == 0 || horizontalCollision || heading.lengthSqr() < 0.01)) {
            heading = detour(direction, Math.max(2.2, speed * 7));
        } else if (charge) {
            heading = clear(direction, Math.max(1.6, speed * 2.5)) ? direction : Vec3.ZERO;
        }
        var velocity = getDeltaMovement().scale(0.55).add(heading.scale(speed * 0.45));
        setDeltaMovement(velocity);
        Vec3 facing = action() == WINDUP ? direction : velocity;
        if (facing.horizontalDistanceSqr() > 0.01) {
            float yaw = (float) (Mth.atan2(facing.z, facing.x) * Mth.RAD_TO_DEG) - 90;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 19));
            yBodyRot = yHeadRot = getYRot();
        }
        fallDistance = 0;
    }

    private Vec3 detour(Vec3 direction, double lookahead) {
        if (clear(direction, lookahead)) return direction;
        Vec3 best = Vec3.ZERO;
        double score = -10;
        for (double turn : TURNS) {
            for (double climb : PITCHES) {
                Vec3 horizontal = direction.yRot((float) turn);
                Vec3 option = new Vec3(horizontal.x, horizontal.y + climb, horizontal.z).normalize();
                if (!clear(option, lookahead)) continue;
                double merit = option.dot(direction) + 0.23 * option.dot(heading);
                if (merit > score) {
                    best = option;
                    score = merit;
                }
            }
        }
        return best;
    }

    boolean clear(Vec3 direction, double distance) {
        Vec3 offset = direction.scale(distance);
        Vec3 end = position().add(offset);
        if (!level().hasChunkAt(BlockPos.containing(end)) || !level().noCollision(this, getBoundingBox().move(offset))) return false;
        var start = position().add(0, getBbHeight() * 0.55, 0);
        return level().clip(new ClipContext(start, start.add(offset), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, this)).getType() == HitResult.Type.MISS;
    }

    @Override public void travel(Vec3 input) {
        if (isEffectiveAi()) {
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.89));
        } else super.travel(input);
    }

    @Override public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override public void addAdditionalSaveData(CompoundTag data) {
        super.addAdditionalSaveData(data);
        if (home != null) data.putLong("Roost", home.asLong());
    }

    @Override public void readAdditionalSaveData(CompoundTag data) {
        super.readAdditionalSaveData(data);
        if (data.contains("Roost")) home = BlockPos.of(data.getLong("Roost"));
    }
}
