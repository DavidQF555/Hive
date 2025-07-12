package hive.common.world.entities.ai;

import hive.common.world.Physics;
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
    private DroidOperation operation = DroidOperation.WAIT;

    public DroidMoveControl(Mob mob) {
        super(mob);
    }

    @Override
    public void tick() {
        double dX = wantedX - mob.getX();
        double dZ = wantedZ - mob.getZ();
        float rot = rotlerp(mob.getYRot(), (float) (Mth.atan2(dZ, dX) * 180 / (float) Math.PI) - 90, 90);
        double len = Math.sqrt(dX * dX + dZ * dZ);
        double max = this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (operation != DroidOperation.WAIT && len < ERROR) {
            operation = DroidOperation.WAIT;
            mob.setSprinting(false);
        }
        if (this.operation == DroidOperation.MOVE_TO) {
            if (!mob.onGround()) {
                operation = DroidOperation.IN_AIR;
                mob.setSprinting(true);
            } else {
                double speed = Math.min(len, max);
                BlockPos below = mob.getBlockPosBelowThatAffectsMyMovement();
                float friction = mob.level().getBlockState(below).getFriction(mob.level(), below, mob);
                double tX = dX * speed / len / friction;
                double tZ = dZ * speed / len / friction;
                mob.setYRot(rot);
                setDeltaMovement(tX, tZ, max);
            }
        }
        if (operation == DroidOperation.START_JUMP) {
            if (!mob.onGround()) {
                operation = DroidOperation.IN_AIR;
                mob.setSprinting(true);
            } else {
                Optional<Double> t = getJumpLandingTime();
                if (t.isEmpty()) {
                    operation = DroidOperation.WAIT;
                    mob.setSprinting(false);
                } else {
                    double tX = dX / t.get();
                    double tZ = dZ / t.get();
                    mob.setYRot(rot);
                    setDeltaMovement(tX, tZ, max);
                    if (canJump(tX, tZ)) {
                        mob.getJumpControl().jump();
                    }
                }
            }
        }
        if (this.operation == DroidOperation.IN_AIR) {
            if (mob.onGround()) {
                operation = DroidOperation.WAIT;
                mob.setSprinting(false);
            } else {
                Optional<Double> t = getLandingTime();
                if (t.isEmpty()) {
                    operation = DroidOperation.WAIT;
                    mob.setSprinting(false);
                } else {
                    mob.setYRot(rot);
                    setDeltaMovement(dX / t.get(), dZ / t.get(), max);
                }
            }
        }
        if (operation == DroidOperation.WAIT) {
            setDeltaMovement(0, 0, max);
        }
    }

    protected boolean canJump(double tX, double tZ) {
        Vec3 speed = mob.getDeltaMovement();
        double xSpeed = speed.x();
        double zSpeed = speed.z();
        if (mob.isSprinting()) {
            double rot = mob.getYRot() * Math.PI / 180;
            xSpeed -= Math.sin(rot) * 0.2;
            zSpeed += Math.cos(rot) * 0.2;
        }
        return Math.abs(tX - xSpeed) < JUMP_ERROR && Math.abs(tZ - zSpeed) < JUMP_ERROR;
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
            zza *= 4;
            xxa *= 4;
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

    public void jumpTowards(double x, double y, double z, double speed) {
        if (operation != DroidOperation.IN_AIR) {
            setWantedPosition(x, y, z, speed);
            mob.setSprinting(true);
            operation = DroidOperation.START_JUMP;
        }
    }

    private Optional<Double> getLandingTime() {
        return Physics.getLandingTime(-mob.getEffectiveGravity(), this.wantedY - mob.getY(), mob.getDeltaMovement().y());
    }

    private Optional<Double> getJumpLandingTime() {
        return Physics.getLandingTime(-mob.getEffectiveGravity(), wantedY - mob.getY(), mob.getAttributeValue(Attributes.JUMP_STRENGTH));
    }

    @Override
    public boolean hasWanted() {
        return operation != DroidOperation.WAIT;
    }

    @Override
    public void setWantedPosition(double x, double y, double z, double speed) {
        super.setWantedPosition(x, y, z, speed);
        if (this.operation != DroidOperation.IN_AIR) {
            mob.setSprinting(true);
            this.operation = DroidOperation.MOVE_TO;
        }
    }

    private enum DroidOperation {
        WAIT(),
        MOVE_TO(),
        IN_AIR(),
        START_JUMP()
    }

}
