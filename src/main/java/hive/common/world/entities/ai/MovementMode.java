package hive.common.world.entities.ai;

import net.minecraft.world.entity.Pose;

public enum MovementMode {
    WALK(Pose.STANDING),
    JUMP(Pose.STANDING),
    SWIM(Pose.SWIMMING);

    public static final int COUNT = values().length;

    public final Pose pose;

    MovementMode(Pose pose) {
        this.pose = pose;
    }
}
