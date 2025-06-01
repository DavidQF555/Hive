package hive.common.world.entities.ai;

import hive.common.world.entities.DroidEntity;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;

public class DroidPathNavigation extends GroundPathNavigation {

    public DroidPathNavigation(DroidEntity entity, Level world) {
        super(entity, world);
    }

    @Override
    protected PathFinder createPathFinder(int max) {
        nodeEvaluator = new DroidNodeEvaluator(1);
        return new DroidPathfinder(this.nodeEvaluator, max);
    }

    @Override
    public void tick() {
        super.tick();
        if (!isDone()) {
            Node prev = path.getPreviousNode();
            Node node = path.getNextNode();
            if (DroidPathfinder.isJump(prev, node) && mob.getMoveControl() instanceof DroidMoveControl control && !control.isJumping()) {
                control.jump();
            }
        }
    }

}
