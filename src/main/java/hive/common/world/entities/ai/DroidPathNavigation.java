package hive.common.world.entities.ai;

import hive.common.world.entities.DroidEntity;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

public class DroidPathNavigation extends GroundPathNavigation {

    public static final int JUMP_WIDTH = 5;

    public DroidPathNavigation(DroidEntity entity, Level world) {
        super(entity, world);
    }

    @Override
    protected PathFinder createPathFinder(int max) {
        nodeEvaluator = new DroidNodeEvaluator(1, true, JUMP_WIDTH);
        return new DroidPathfinder(this.nodeEvaluator, max);
    }

    @Override
    public void tick() {
        super.tick();
        if (!isDone()) {
            Node prev = path.getPreviousNode();
            Node node = path.getNextNode();
            if (DroidPathfinder.isJump(prev, node) && mob.getMoveControl() instanceof DroidMoveControl control) {
                Vec3 target = this.path.getNextEntityPos(mob);
                control.jumpTowards(target.x(), getGroundY(target), target.z(), speedModifier);
            }
        }
    }

    @Override
    protected boolean shouldTargetNextNodeInDirection(Vec3 start) {
        return super.shouldTargetNextNodeInDirection(start) && !DroidPathfinder.isJump(path.getNextNode(), path.getNode(path.getNextNodeIndex() + 1));
    }

}
