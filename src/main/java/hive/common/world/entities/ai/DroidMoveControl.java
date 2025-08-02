package hive.common.world.entities.ai;

import hive.common.world.Physics;
import hive.common.world.entities.DroidEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class DroidMoveControl extends MoveControl {

    private static final double ERROR = 1E-7;
    private static final double JUMP_ERROR = 0.1;
    private static final int JUMP_CAP = 5;
    private DroidOperation operation = DroidOperation.WAIT;
    private int jumpDelay;

    public DroidMoveControl(Mob mob) {
        super(mob);
    }

    @Override
    public void tick() {
        double max = this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (operation == DroidOperation.WAIT) {
            setDeltaMovement(0, 0, max);
            return;
        }
        double dX = wantedX - mob.getX();
        double dY = wantedY - mob.getY();
        double dZ = wantedZ - mob.getZ();
        float rot = rotlerp(mob.getYRot(), (float) (Mth.atan2(dZ, dX) * 180 / (float) Math.PI) - 90, 90);
        double dist = Math.sqrt(dX * dX + dZ * dZ + dY * dY);
        if (dist < ERROR) {
            setOperation(DroidOperation.WAIT);
        }
        if (operation == DroidOperation.START_JUMP) {
            if (isInSwimmableFluid()) {
                setOperation(DroidOperation.MOVE_TO);
            } else if (!mob.onGround()) {
                setOperation(DroidOperation.IN_AIR);
            } else {
                Optional<Double> t = getJumpLandingTime();
                if (t.isEmpty()) {
                    setOperation(DroidOperation.WAIT);
                } else {
                    double tX = dX / t.get();
                    double tZ = dZ / t.get();
                    mob.setYRot(rot);
                    setDeltaMovement(tX, tZ, max);
                    if (++jumpDelay > JUMP_CAP || canJump(tX, tZ)) {
                        mob.getJumpControl().jump();
                    }
                }
            }
        }
        if (this.operation == DroidOperation.IN_AIR) {
            if (mob.onGround()) {
                setOperation(DroidOperation.WAIT);
            } else {
                Optional<Double> t = getLandingTime();
                if (t.isEmpty()) {
                    setOperation(DroidOperation.WAIT);
                } else {
                    mob.setYRot(rot);
                    setDeltaMovement(dX / t.get(), dZ / t.get(), max);
                }
            }
        }
        if (this.operation == DroidOperation.MOVE_TO) {
            if (!mob.onGround() && !isInSwimmableFluid()) {
                setOperation(DroidOperation.IN_AIR);
            } else {
                double len = Math.sqrt(dX * dX + dZ * dZ);
                double speed = Math.min(len, max);
                BlockPos below = mob.getBlockPosBelowThatAffectsMyMovement();
                float friction = mob.level().getBlockState(below).getFriction(mob.level(), below, mob);
                double tX = dX * speed / len / friction;
                double tZ = dZ * speed / len / friction;
                mob.setYRot(rot);
                setDeltaMovement(tX, tZ, max);
            }
        }
    }

    private void setDeltaMovement(double cX, double cZ, double speed) {
        mob.setSpeed((float) speed);
        double rot = mob.getYRot() * Math.PI / 180;
        double dX = -Math.sin(rot);
        double dZ = Math.cos(rot);
        Vec3 delta = mob.getDeltaMovement();
        double changeDX = (cX - delta.x()) / speed;
        double changeDZ = (cZ - delta.z()) / speed;
        double zza = changeDX * dX + changeDZ * dZ;
        double sX = changeDX - zza * dX;
        double sZ = changeDZ - zza * dZ;
        double xxa = Math.sqrt(sX * sX + sZ * sZ);
        double side = -changeDX * sZ + changeDZ * sX;
        if (side < 0) {
            xxa *= -1;
        }
        if (!mob.onGround()) {
            zza /= DroidEntity.FLY_MULTIPLIER;
            xxa /= DroidEntity.FLY_MULTIPLIER;
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

    protected void setOperation(DroidOperation operation) {
        if (operation != this.operation) {
            this.operation = operation;
            mob.setSprinting(operation != DroidOperation.WAIT);
        }
    }

    protected boolean canJump(double tX, double tZ) {
        Vec3 speed = mob.getDeltaMovement();
        double xSpeed = speed.x();
        double zSpeed = speed.z();
        if (mob.isSprinting()) {
            double rot = mob.getYRot() * Math.PI / 180;
            xSpeed -= Math.sin(rot) * DroidEntity.JUMP_BOOST;
            zSpeed += Math.cos(rot) * DroidEntity.JUMP_BOOST;
        }
        return Math.abs(tX - xSpeed) < JUMP_ERROR && Math.abs(tZ - zSpeed) < JUMP_ERROR;
    }

    public void jumpTowards(double x, double y, double z, double speed) {
        if (operation != DroidOperation.START_JUMP && operation != DroidOperation.IN_AIR) {
            jumpDelay = 0;
            setWantedPosition(x, y, z, speed);
            setOperation(DroidOperation.START_JUMP);
        }
    }

    private Optional<Double> getLandingTime() {
        return Physics.getLandingTime(-mob.getEffectiveGravity(), this.wantedY - mob.getY(), mob.getDeltaMovement().y());
    }

    private Optional<Double> getJumpLandingTime() {
        return Physics.getLandingTime(-mob.getEffectiveGravity(), wantedY - mob.getY(), mob.getAttributeValue(Attributes.JUMP_STRENGTH) + mob.getJumpBoostPower());
    }

    protected boolean isInSwimmableFluid() {
        return mob.isInFluidType((fluidType, height) -> mob.canSwimInFluidType(fluidType));
    }

    @Override
    public boolean hasWanted() {
        return operation != DroidOperation.WAIT;
    }

    @Override
    public void setWantedPosition(double x, double y, double z, double speed) {
        super.setWantedPosition(x, y, z, speed);
        if (this.operation != DroidOperation.IN_AIR && operation != DroidOperation.START_JUMP) {
            setOperation(DroidOperation.MOVE_TO);
        }
    }

    protected enum DroidOperation {
        WAIT(),
        MOVE_TO(),
        IN_AIR(),
        START_JUMP()
    }

}
