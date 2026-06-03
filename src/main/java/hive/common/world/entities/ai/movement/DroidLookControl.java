package hive.common.world.entities.ai.movement;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;

public class DroidLookControl extends LookControl {

    // max head rotation per tick for the smooth track toward target
    private static final float HEAD_YAW_SPEED = 30;
    private static final float HEAD_PITCH_SPEED = 30;

    public DroidLookControl(Mob mob) {
        super(mob);
    }

    @Override
    public void tick() {
        if (mob.isVisuallySwimming()) {
            mob.setYHeadRot(mob.getYRot());
        } else {
            LivingEntity target = mob.getTarget();
            if (target != null && target.isAlive()) {
                setLookAt(target, HEAD_YAW_SPEED, HEAD_PITCH_SPEED);
            }
            super.tick();
        }
    }

}
