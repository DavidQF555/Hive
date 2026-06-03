package hive.common.world.entities.ai.movement;

import hive.common.world.Physics;
import hive.common.world.entities.DroidEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class DroidMoveControl extends MoveControl {

    // numerical tolerance for comparisons
    private static final double ERROR = 1E-7;
    // velocity tolerance in blocks/tick for starting jumps
    private static final double JUMP_ERROR = 0.1;
    private static final double ROTATION_ERROR = 0.1;
    // slope to target above which SWIMMING rises or sinks
    private static final double SWIM_SLOPE_THRESHOLD = 0.1;
    // probability of triggering a surface jump in WADE, mirrors AmphibiousPathNavigation behavior
    private static final float WADE_JUMP_CHANCE = 0.8f;
    private static final float ATTACK_TURN_RATE_DEGREES = 30;
    private final DroidEntity mob;
    private DroidOperation operation = DroidOperation.WAIT;
    private boolean stuck;
    private @Nullable LivingEntity attackTarget;

    public DroidMoveControl(DroidEntity mob) {
        super(mob);
        this.mob = mob;
    }

    @Override
    public void tick() {
        double max = this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (operation == DroidOperation.WAIT) {
            setPose(Pose.STANDING);
            handleBodyRotation(mob.getYRot());
            setDeltaMovement(0, 0, max, 1); // friction is negligible here because target speed is 0
            return;
        }
        double dX = wantedX - mob.getX();
        double dY = wantedY - mob.getY();
        double dZ = wantedZ - mob.getZ();
        double len = Math.sqrt(dX * dX + dZ * dZ);
        double dist = Math.sqrt(dX * dX + dZ * dZ + dY * dY);
        float rot;
        if (len < ROTATION_ERROR) {
            rot = mob.getYRot();
        } else {
            rot = rotlerp(mob.getYRot(), (float) (Mth.atan2(dZ, dX) * 180 / (float) Math.PI) - 90, 90);
        }
        if (dist < ERROR) {
            setOperation(DroidOperation.WAIT);
        }
        if (operation == DroidOperation.START_JUMP) {
            if (mob.isInFluidType()) {
                setOperation(DroidOperation.FLUID_LAUNCH);
            } else if (mob.onGround()) {
                Optional<Double> t = getJumpLandingTime();
                if (t.isEmpty()) {
                    setOperation(DroidOperation.WAIT);
                    setStuck(true);
                } else {
                    setPose(Pose.STANDING);
                    BlockPos below = mob.getBlockPosBelowThatAffectsMyMovement();
                    float friction = mob.level().getBlockState(below).getFriction(mob.level(), below, mob) * Physics.Constants.AIR_FRICTION;
                    double tX = dX / Physics.Constants.AIR_FRICTION / t.get();
                    double tZ = dZ / Physics.Constants.AIR_FRICTION / t.get();
                    if (mob.isSprinting()) {
                        // lower requirements when sprinting because there is boost
                        double adjX = tX + Math.sin(rot * Math.PI / 180) * Physics.Constants.JUMP_BOOST;
                        double adjZ = tZ - Math.cos(rot * Math.PI / 180) * Physics.Constants.JUMP_BOOST;
                        // ensure sign doesn't change
                        tX = (tX > 0 && adjX < 0) || (tX < 0 && adjX > 0) ? 0 : adjX;
                        tZ = (tZ > 0 && adjZ < 0) || (tZ < 0 && adjZ > 0) ? 0 : adjZ;
                    }
                    mob.setYRot(rot);
                    setDeltaMovement(tX, tZ, max, friction);
                    if (mob.noJumpDelay <= 0 && canJump(tX, tZ)) {
                        mob.getJumpControl().jump();
                    }
                }
            } else {
                setOperation(DroidOperation.IN_AIR);
            }
        }
        if (operation == DroidOperation.FLUID_LAUNCH) {
            if (!mob.isInFluidType()) {
                setOperation(mob.onGround() ? DroidOperation.MOVE_TO : DroidOperation.IN_AIR);
            } else {
                setPose(Pose.STANDING);
                mob.getJumpControl().jump();
                double friction = Physics.getFluidFriction(
                        false,
                        mob.getWaterSlowDown(),
                        mob.hasEffect(MobEffects.DOLPHINS_GRACE),
                        mob.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY),
                        mob.onGround()
                );
                double fluidSpeed = Math.min(len, max);
                double tX = len < ERROR ? 0 : dX * fluidSpeed / len / friction;
                double tZ = len < ERROR ? 0 : dZ * fluidSpeed / len / friction;
                mob.setYRot(rot);
                setDeltaMovement(tX, tZ, max, friction);
            }
        }
        if (this.operation == DroidOperation.IN_AIR) {
            if (mob.isInFluidType()) {
                setOperation(DroidOperation.WADE);
            } else if (mob.onGround()) {
                setOperation(DroidOperation.MOVE_TO);
            } else {
                Optional<Double> t = getLandingTime();
                if (t.isEmpty()) {
                    setOperation(DroidOperation.WAIT);
                    setStuck(true);
                } else {
                    setPose(Pose.STANDING);
                    handleBodyRotation(rot);
                    setDeltaMovement(dX / Physics.Constants.AIR_FRICTION / t.get(), dZ / Physics.Constants.AIR_FRICTION / t.get(), max, Physics.Constants.AIR_FRICTION);
                }
            }
        }
        double speed = Math.min(len, max);
        if (operation == DroidOperation.WADE) {
            if (!mob.isInFluidType()) {
                if (mob.onGround()) {
                    setOperation(DroidOperation.MOVE_TO);
                } else {
                    setOperation(DroidOperation.IN_AIR);
                }
            } else {
                setPose(Pose.STANDING);
                FluidType fluid = mob.getMaxHeightFluidType();
                if (dY < 0) {
                    mob.sinkInFluid(fluid);
                } else if (mob.isUnderWater()) {
                    if (dY > 0) {
                        mob.getJumpControl().jump();
                    }
                } else if (mob.getFluidTypeHeight(fluid) > mob.getFluidJumpThreshold()
                        && mob.level().getRandom().nextFloat() < WADE_JUMP_CHANCE) {
                    mob.getJumpControl().jump();
                }
                double friction = Physics.getFluidFriction(
                        false,
                        mob.getWaterSlowDown(),
                        mob.hasEffect(MobEffects.DOLPHINS_GRACE),
                        mob.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY),
                        mob.onGround()
                );
                double tX = len < ERROR ? 0 : dX * speed / len / friction;
                double tZ = len < ERROR ? 0 : dZ * speed / len / friction;
                handleBodyRotation(rot);
                setDeltaMovement(tX, tZ, max, friction);
            }
        }
        if (this.operation == DroidOperation.MOVE_TO) {
            if (mob.isInFluidType()) {
                setOperation(DroidOperation.WADE);
            } else if (!mob.onGround()) {
                setOperation(DroidOperation.IN_AIR);
            } else {
                setPose(Pose.STANDING);
                BlockPos below = mob.getBlockPosBelowThatAffectsMyMovement();
                float blockFriction = mob.level().getBlockState(below).getFriction(mob.level(), below, mob);
                double terminal = Physics.getTerminalGroundSpeed(max, blockFriction);
                double targetSpeed = Math.min(len, terminal);
                double tX = dX * targetSpeed / len;
                double tZ = dZ * targetSpeed / len;
                handleBodyRotation(rot);
                setDeltaMovement(tX, tZ, max, blockFriction * Physics.Constants.AIR_FRICTION);
            }
        }
        if (operation == DroidOperation.SWIMMING) {
            if (!mob.isInWater()) {
                setOperation(mob.onGround() ? DroidOperation.MOVE_TO : DroidOperation.IN_AIR);
            } else {
                setPose(Pose.SWIMMING);
                if (handleSwimRotation(dY, len, dist, rot)) {
                    mob.setZza(0);
                    mob.setXxa(0);
                } else {
                    double waterEff = mob.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);
                    double friction = Physics.getFluidFriction(
                            true,
                            mob.getWaterSlowDown(),
                            mob.hasEffect(MobEffects.DOLPHINS_GRACE),
                            waterEff,
                            mob.onGround()
                    );
                    double swimSpeed = Physics.getSwimSpeedMultiplier(max, waterEff, mob.onGround()) * mob.getAttributeValue(NeoForgeMod.SWIM_SPEED);
                    FluidType fluid = mob.level().getFluidState(mob.blockPosition()).getFluidType();
                    double slope = len < ERROR ? Math.signum(dY) : dY / len;
                    if (slope > SWIM_SLOPE_THRESHOLD) {
                        mob.getJumpControl().jump();
                    } else if (slope < -SWIM_SLOPE_THRESHOLD) {
                        mob.sinkInFluid(fluid);
                    }
                    double terminal = Physics.getTerminalSpeed(swimSpeed, friction);
                    double targetSpeed = Math.min(len, terminal);
                    double tX = len < ERROR ? 0 : dX * targetSpeed / len;
                    double tZ = len < ERROR ? 0 : dZ * targetSpeed / len;
                    setDeltaMovement(tX, tZ, max, friction, swimSpeed);
                }
            }
        }
    }

    private void setPose(Pose pose) {
        if (mob.getPose() != pose) {
            mob.setPose(pose);
        }
    }

    public void setAttackTarget(@Nullable LivingEntity target) {
        this.attackTarget = target;
    }

    private void handleBodyRotation(float rot) {
        if (shouldRotateBodyForAttack()) {
            rotateBodyTowardTarget(attackTarget);
        } else {
            mob.setYRot(rot);
        }
    }

    // returns whether the droid is turning for an attack
    private boolean handleSwimRotation(double dY, double len, double dist, float rot) {
        if (dist >= ROTATION_ERROR) {
            mob.setXRot((float) (-Math.atan2(dY, len) * 180 / Math.PI));
        }
        if (shouldRotateBodyForAttack()) {
            rotateBodyTowardTarget(attackTarget);
            return true;
        }
        mob.setYRot(rot);
        return false;
    }

    private boolean shouldRotateBodyForAttack() {
        if (attackTarget == null || !attackTarget.isAlive()) {
            return false;
        }
        double dx = attackTarget.getX() - mob.getX();
        double dz = attackTarget.getZ() - mob.getZ();
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float bodyDelta = Math.abs(Mth.wrapDegrees(targetYaw - mob.yBodyRot));
        float bodyAlignmentTolerance = mob.getMaxHeadYRot() + DroidEntity.ATTACK_FACING_THRESHOLD_DEGREES;
        if (bodyDelta <= bodyAlignmentTolerance) {
            return false;
        }
        if (!mob.onGround() && !mob.isInWater()) {
            return false;
        }
        int ticksToAlign = (int) Math.ceil((bodyDelta - bodyAlignmentTolerance) / ATTACK_TURN_RATE_DEGREES);
        long ticksUntilAttack = Math.max(0, mob.getNextAttackTick() - mob.level().getGameTime());
        return ticksUntilAttack <= ticksToAlign;
    }

    private void rotateBodyTowardTarget(LivingEntity target) {
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float delta = Mth.wrapDegrees(targetYaw - mob.getYRot());
        float step = Mth.clamp(delta, -ATTACK_TURN_RATE_DEGREES, ATTACK_TURN_RATE_DEGREES);
        mob.setYRot(mob.getYRot() + step);
    }

    private void setDeltaMovement(double cX, double cZ, double speed, double friction) {
        double impulseScale = speed;
        if (!mob.onGround()) {
            impulseScale *= Physics.Constants.FLY_MULTIPLIER;
        }
        setDeltaMovement(cX, cZ, speed, friction, impulseScale);
    }

    private void setDeltaMovement(double cX, double cZ, double speed, double friction, double impulseScale) {
        mob.setSpeed((float) speed);
        double rot = mob.getYRot() * Math.PI / 180;
        double dX = -Math.sin(rot);
        double dZ = Math.cos(rot);
        Vec3 delta = mob.getDeltaMovement();
        double changeDX = (cX / friction - delta.x()) / impulseScale;
        double changeDZ = (cZ / friction - delta.z()) / impulseScale;
        double zza = changeDX * dX + changeDZ * dZ;
        double sX = changeDX - zza * dX;
        double sZ = changeDZ - zza * dZ;
        double xxa = Math.sqrt(sX * sX + sZ * sZ);
        double side = -changeDX * sZ + changeDZ * sX;
        if (side < 0) {
            xxa *= -1;
        }
        double scale = getScale(zza, xxa);
        zza *= scale;
        xxa *= scale;
        if (Math.abs(zza) < ERROR || !Double.isFinite(zza)) {
            mob.setZza(0);
        } else {
            mob.setZza((float) zza);
        }
        if (Math.abs(xxa) < ERROR || !Double.isFinite(xxa)) {
            mob.setXxa(0);
        } else {
            mob.setXxa((float) xxa);
        }
    }

    private double getScale(double zza, double xxa) {
        double scale = 1;
        if (zza > 1) {
            scale = Math.min(scale, 1 / zza);
        } else if (zza < -1) {
            scale = Math.min(scale, -1 / zza);
        }
        if (xxa > 1) {
            scale = Math.min(scale, 1 / xxa);
        } else if (xxa < -1) {
            scale = Math.min(scale, -1 / xxa);
        }
        return scale;
    }

    public boolean isStuck() {
        return stuck;
    }

    public void setStuck(boolean stuck) {
        this.stuck = stuck;
    }

    protected void setOperation(DroidOperation operation) {
        this.operation = operation;
    }

    protected boolean canJump(double tX, double tZ) {
        Vec3 speed = mob.getDeltaMovement();
        return Math.abs(tX - speed.x()) < JUMP_ERROR && Math.abs(tZ - speed.z()) < JUMP_ERROR;
    }

    public void jumpTowards(double x, double y, double z, double speed) {
        if (operation == DroidOperation.WAIT || operation == DroidOperation.MOVE_TO || operation == DroidOperation.WADE) {
            setWantedPosition(x, y, z, speed);
            setOperation(mob.isInFluidType() ? DroidOperation.FLUID_LAUNCH : DroidOperation.START_JUMP);
        }
    }

    public void swimTo(double x, double y, double z, double speed) {
        setWantedPosition(x, y, z, speed);
        setOperation(DroidOperation.SWIMMING);
    }

    public void walkTo(double x, double y, double z, double speed) {
        setWantedPosition(x, y, z, speed);
        if (operation == DroidOperation.SWIMMING) {
            setOperation(DroidOperation.MOVE_TO);
        }
    }

    public void stop() {
        if (operation == DroidOperation.MOVE_TO) {
            setOperation(DroidOperation.WAIT);
        }
    }

    private Optional<Double> getLandingTime() {
        return Physics.getLandingTime(-mob.getEffectiveGravity(), this.wantedY - mob.getY(), mob.getDeltaMovement().y());
    }

    private Optional<Double> getJumpLandingTime() {
        return Physics.getLandingTime(-mob.getEffectiveGravity(), wantedY - mob.getY(), mob.getAttributeValue(Attributes.JUMP_STRENGTH) + mob.getJumpBoostPower());
    }

    @Override
    public boolean hasWanted() {
        return operation != DroidOperation.WAIT;
    }

    @Override
    public void setWantedPosition(double x, double y, double z, double speed) {
        super.setWantedPosition(x, y, z, speed);
        if (operation == DroidOperation.WAIT) {
            setOperation(DroidOperation.MOVE_TO);
        }
    }

    protected enum DroidOperation {
        WAIT(),
        MOVE_TO(),
        IN_AIR(),
        START_JUMP(),
        FLUID_LAUNCH(),
        WADE(),
        SWIMMING()
    }

}
