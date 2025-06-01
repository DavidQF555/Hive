package hive.common.world.entities.ai;

import hive.common.world.Physics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class DroidMoveControl extends MoveControl {

    public DroidMoveControl(Mob mob) {
        super(mob);
    }

    //JUMPING == FLOATING
    @Override
    public void tick() {
        double dX = wantedX - mob.getX();
        double dZ = wantedZ - mob.getZ();
        float rot = rotlerp(this.mob.getYRot(), (float) (Mth.atan2(dZ, dX) * 180 / (float) Math.PI) - 90, 90);
        double len = Math.sqrt(dX * dX + dZ * dZ);
        double max = this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (len < 2.5000003E-7F) {
            operation = Operation.WAIT;
        }
        if (this.operation == MoveControl.Operation.MOVE_TO) {
            if (!mob.onGround()) {
                operation = Operation.JUMPING;
            } else {
                mob.setYRot(rot);
                double speed = Math.min(len, max);
                setDeltaMovement(dX * speed / len, dZ * speed / len, max);
            }
        }
        if (this.operation == MoveControl.Operation.JUMPING) {
            if (mob.onGround()) {
                operation = Operation.WAIT;
            } else {
                Optional<Double> t = getLandingTime();
                if (t.isEmpty()) {
                    operation = Operation.WAIT;
                } else {
                    mob.setYRot(rot);
                    setDeltaMovement(dX / t.get(), dZ / t.get(), max);
                }
            }
        }
        if (operation == Operation.WAIT) {
            // TODO: should ensure delta movement is 0 instead of just setting impulse
            mob.setZza(0);
            mob.setXxa(0);
        }
    }

    private void setDeltaMovement(double cX, double cZ, double speed) {
        double dX = wantedX - mob.getX();
        double dZ = wantedZ - mob.getZ();
        double len = Math.sqrt(dX * dX + dZ * dZ);
        mob.setSpeed((float) speed);
        Vec3 delta = mob.getDeltaMovement();
        double changeDX = (cX - delta.x()) / speed;
        double changeDZ = (cZ - delta.z()) / speed;
        double dot = changeDX * dX + changeDZ * dZ;
        double zza = dot / len;
        mob.setZza((float) Mth.clamp(zza, -1, 1));
        double sX = changeDX - zza / len * dX;
        double sZ = changeDZ - zza / len * dZ;
        double xxa = Math.sqrt(sX * sX + sZ * sZ);
        double side = -changeDX * sZ + changeDZ * sX;
        if (side < 0) {
            xxa *= -1;
        }
        mob.setXxa((float) Mth.clamp(xxa, -1, 1));
    }

    public void jump() {
        mob.getJumpControl().jump();
        operation = Operation.JUMPING;
    }

    public boolean isJumping() {
        return operation == Operation.JUMPING;
    }

    private Optional<Double> getLandingTime() {
        return Physics.getLandingTime(-mob.getAttributeValue(Attributes.GRAVITY), this.wantedY - mob.getY(), mob.getDeltaMovement().y());
    }

}
