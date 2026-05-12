package hive.common.world.entities.ai.movement;

import hive.common.world.Physics;
import hive.common.world.entities.DroidEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidType;

import java.util.Optional;

public class DroidMoveControl extends MoveControl {

    // numerical tolerance for comparisons
    private static final double ERROR = 1E-7;
    // velocity tolerance in blocks/tick for starting jumps
    private static final double JUMP_ERROR = 0.1;
    // slope to target above which SWIMMING rises or sinks
    private static final double SWIM_SLOPE_THRESHOLD = 0.1;
    // probability of triggering a surface jump in WADE, mirrors AmphibiousPathNavigation behavior
    private static final float WADE_JUMP_CHANCE = 0.8f;
    private final DroidEntity mob;
    private DroidOperation operation = DroidOperation.WAIT;
    private boolean stuck, jumpFluid;

    public DroidMoveControl(DroidEntity mob) {
        super(mob);
        this.mob = mob;
    }

    @Override
    public void tick() {
        double max = this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (operation == DroidOperation.WAIT) {
            setPose(Pose.STANDING);
            setDeltaMovement(0, 0, max, 1); // friction is negligible here because target speed is 0
            return;
        }
        double dX = wantedX - mob.getX();
        double dY = wantedY - mob.getY();
        double dZ = wantedZ - mob.getZ();
        float rot = rotlerp(mob.getYRot(), (float) (Mth.atan2(dZ, dX) * 180 / (float) Math.PI) - 90, 90);
        double len = Math.sqrt(dX * dX + dZ * dZ);
        double dist = Math.sqrt(dX * dX + dZ * dZ + dY * dY);
        if (dist < ERROR) {
            setOperation(DroidOperation.WAIT);
        }
        if (operation == DroidOperation.START_JUMP) {
            if (mob.onGround()) {
                if (isInFluid()) {
                    setOperation(DroidOperation.WADE);
                } else {
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
                }
            } else {
                setOperation(DroidOperation.IN_AIR);
            }
        }
        if (this.operation == DroidOperation.IN_AIR) {
            if (isInFluid()) {
                setOperation(DroidOperation.WADE);
            } else if (mob.onGround()) {
                setOperation(DroidOperation.WAIT);
            } else {
                Optional<Double> t = getLandingTime();
                if (t.isEmpty()) {
                    setOperation(DroidOperation.WAIT);
                    setStuck(true);
                } else {
                    setPose(Pose.STANDING);
                    mob.setYRot(rot);
                    setDeltaMovement(dX / Physics.Constants.AIR_FRICTION / t.get(), dZ / Physics.Constants.AIR_FRICTION / t.get(), max, Physics.Constants.AIR_FRICTION);
                }
            }
        }
        double speed = Math.min(len, max);
        if (operation == DroidOperation.WADE) {
            if (!isInFluid()) {
                if (mob.onGround()) {
                    setOperation(DroidOperation.MOVE_TO);
                } else {
                    setOperation(DroidOperation.IN_AIR);
                }
            } else {
                setPose(Pose.STANDING);
                FluidType fluid = mob.level().getFluidState(mob.blockPosition()).getFluidType();
                if (mob.isUnderWater()) {
                    jumpFluid = false;
                }
                if (dY < 0) {
                    mob.sinkInFluid(fluid);
                    jumpFluid = false;
                } else if (jumpFluid) {
                    if (mob.level().getRandom().nextFloat() < WADE_JUMP_CHANCE) {
                        mob.getJumpControl().jump();
                    }
                } else if (mob.isUnderWater()) {
                    if (dY > 0) {
                        mob.getJumpControl().jump();
                    }
                } else if (mob.getFluidTypeHeight(fluid) > mob.getFluidJumpThreshold()) {
                    jumpFluid = true;
                }
                double slow = Physics.getFluidFriction(
                        false,
                        mob.getWaterSlowDown(),
                        mob.hasEffect(MobEffects.DOLPHINS_GRACE),
                        Math.min(1, EnchantmentHelper.getDepthStrider(mob) / 3.0),
                        mob.onGround()
                );
                double tX = dX * speed / len / slow;
                double tZ = dZ * speed / len / slow;
                mob.setYRot(rot);
                setDeltaMovement(tX, tZ, max, slow);
            }
        }
        if (this.operation == DroidOperation.MOVE_TO) {
            if (isInFluid()) {
                setOperation(DroidOperation.WADE);
            } else if (!mob.onGround()) {
                setOperation(DroidOperation.IN_AIR);
            } else {
                setPose(Pose.STANDING);
                BlockPos below = mob.getBlockPosBelowThatAffectsMyMovement();
                float friction = mob.level().getBlockState(below).getFriction(mob.level(), below, mob) * Physics.Constants.AIR_FRICTION;
                double terminal = max * Physics.Constants.GROUND_WALK_SPEED_MULTIPLIER;
                double targetSpeed = Math.min(len, terminal);
                double tX = dX * targetSpeed / len;
                double tZ = dZ * targetSpeed / len;
                mob.setYRot(rot);
                setDeltaMovement(tX, tZ, max, friction);
            }
        }
        if (operation == DroidOperation.SWIMMING) {
            if (!mob.isInWater()) {
                setOperation(mob.onGround() ? DroidOperation.MOVE_TO : DroidOperation.IN_AIR);
            } else {
                setPose(Pose.SWIMMING);
                float targetPitch = len < ERROR ? 0 : (float) (-Math.atan2(dY, len) * 180 / Math.PI);
                mob.setXRot(targetPitch);
                FluidType fluid = mob.level().getFluidState(mob.blockPosition()).getFluidType();
                double slope = len < ERROR ? Math.signum(dY) : dY / len;
                if (slope > SWIM_SLOPE_THRESHOLD) {
                    mob.getJumpControl().jump();
                } else if (slope < -SWIM_SLOPE_THRESHOLD) {
                    mob.sinkInFluid(fluid);
                }
                mob.setYRot(rot);
                mob.setSpeed((float) max);
                mob.setZza(1);
                mob.setXxa(0);
            }
        }
    }

    private void setPose(Pose pose) {
        if (mob.getPose() != pose) {
            mob.setPose(pose);
        }
    }

    private void setDeltaMovement(double cX, double cZ, double speed, double friction) {
        mob.setSpeed((float) speed);
        double rot = mob.getYRot() * Math.PI / 180;
        double dX = -Math.sin(rot);
        double dZ = Math.cos(rot);
        Vec3 delta = mob.getDeltaMovement();
        double changeDX = (cX / friction - delta.x()) / speed;
        double changeDZ = (cZ / friction - delta.z()) / speed;
        double zza = changeDX * dX + changeDZ * dZ;
        double sX = changeDX - zza * dX;
        double sZ = changeDZ - zza * dZ;
        double xxa = Math.sqrt(sX * sX + sZ * sZ);
        double side = -changeDX * sZ + changeDZ * sX;
        if (side < 0) {
            xxa *= -1;
        }
        if (!mob.onGround()) {
            zza /= Physics.Constants.FLY_MULTIPLIER;
            xxa /= Physics.Constants.FLY_MULTIPLIER;
        }
        if (Math.abs(zza) < ERROR || !Double.isFinite(zza)) {
            mob.setZza(0);
        } else {
            mob.setZza((float) Mth.clamp(zza, -1, 1));
        }
        if (Math.abs(xxa) < ERROR || !Double.isFinite(xxa)) {
            mob.setXxa(0);
        } else {
            mob.setXxa((float) Mth.clamp(xxa, -1, 1));
        }
    }

    public boolean isStuck() {
        return stuck;
    }

    public void setStuck(boolean stuck) {
        this.stuck = stuck;
    }

    public boolean shouldSprint() {
        if (operation == DroidOperation.WADE) {
            return false;
        }
        if (operation == DroidOperation.WAIT) {
            // stay sprinting through wait state if still moving
            return mob.getNavigation().isInProgress();
        }
        return true;
    }

    protected void setOperation(DroidOperation operation) {
        this.operation = operation;
    }

    protected boolean isInFluid() {
        return mob.isInSwimmableFluid() && (!canJumpFluid() || mob.isUnderWater());
    }

    protected boolean canJump(double tX, double tZ) {
        Vec3 speed = mob.getDeltaMovement();
        return Math.abs(tX - speed.x()) < JUMP_ERROR && Math.abs(tZ - speed.z()) < JUMP_ERROR;
    }

    public void jumpTowards(double x, double y, double z, double speed) {
        if (operation == DroidOperation.WAIT || operation == DroidOperation.MOVE_TO) {
            setWantedPosition(x, y, z, speed);
            setOperation(DroidOperation.START_JUMP);
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

    private Optional<Double> getLandingTime() {
        return Physics.getLandingTime(-mob.getEffectiveGravity(), this.wantedY - mob.getY(), mob.getDeltaMovement().y());
    }

    private Optional<Double> getJumpLandingTime() {
        return Physics.getLandingTime(-mob.getEffectiveGravity(), wantedY - mob.getY(), 0.42 + mob.getJumpBoostPower());
    }

    protected boolean canJumpFluid() {
        return mob.level().getFluidState(mob.blockPosition()).getHeight(mob.level(), mob.blockPosition()) <= mob.getFluidJumpThreshold();
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
        WADE(),
        SWIMMING()
    }

}
