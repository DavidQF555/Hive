package hive.common.world.entities.ai.movement;

import hive.common.world.entities.DroidEntity;
import hive.common.world.entities.ai.pathfinding.DroidNodeEvaluator;
import hive.common.world.entities.ai.pathfinding.DroidPathfinder;
import hive.common.world.entities.ai.pathfinding.ModedNode;
import hive.common.world.entities.ai.pathfinding.MovementMode;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

public class DroidPathNavigation extends GroundPathNavigation {

    public static final int JUMP_WIDTH = 5;
    public static final int FLUID_JUMP_WIDTH = 1;
    private static final double FLUID_JUMP_HEIGHT = 1.0 / 9;
    private final DroidEntity mob;

    public DroidPathNavigation(DroidEntity entity, Level world) {
        super(entity, world);
        mob = entity;
        setCanFloat(true);
    }

    @Override
    protected PathFinder createPathFinder(int max) {
        nodeEvaluator = new DroidNodeEvaluator(1, true, JUMP_WIDTH, FLUID_JUMP_WIDTH, FLUID_JUMP_HEIGHT);
        return new DroidPathfinder(this.nodeEvaluator, max, 1, true);
    }

    @Override
    protected double getGroundY(Vec3 pos) {
        BlockPos block = BlockPos.containing(pos);
        BlockState state = level.getBlockState(block);
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            FluidState above = level.getBlockState(block.above()).getFluidState();
            float height = fluid.getHeight(level, block);
            if (above.getType().isSame(fluid.getType())) {
                return pos.y() + 0.5;
            }
            if (height > mob.getFluidJumpThreshold()) {
                return pos.y() + height;
            }
        }
        BlockState below = level.getBlockState(block.below());
        if (!below.isAir() && below.getFluidState().isEmpty()) {
            return WalkNodeEvaluator.getFloorLevel(level, block);
        } else {
            return pos.y();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (isDone() || !(mob.getMoveControl() instanceof DroidMoveControl control)) {
            return;
        }
        if (control.isStuck()) {
            stop();
            control.setStuck(false);
            return;
        }
        Node node = path.getNextNode();
        Vec3 target = getNodeTargetPos(node);
        double tx = target.x();
        double tz = target.z();
        switch (ModedNode.modeOf(node)) {
            case JUMP -> control.jumpTowards(tx, getGroundY(target), tz, speedModifier);
            case SWIM -> control.swimTo(tx, getSwimY(Mth.floor(target.y())), tz, speedModifier);
            case WALK -> control.walkTo(tx, getGroundY(target), tz, speedModifier);
        }
    }

    protected double getSwimY(int y) {
        return y + (1 - mob.getDimensions(MovementMode.SWIM.pose).height()) / 2;
    }

    protected Vec3 getNodeTargetPos(Node node) {
        EntityDimensions dims = mob.getDimensions(ModedNode.modeOf(node).pose);
        double offset = ((int) (dims.width() + 1)) / 2.0;
        return new Vec3(node.x + offset, node.y, node.z + offset);
    }

    @Override
    protected boolean shouldTargetNextNodeInDirection(Vec3 start) {
        return super.shouldTargetNextNodeInDirection(start)
                && ModedNode.modeOf(path.getNode(path.getNextNodeIndex() + 1)) != MovementMode.JUMP;
    }

    // corner-cutting through fluid (mirrors AmphibiousPathNavigation)
    @Override
    protected boolean canMoveDirectly(Vec3 from, Vec3 to) {
        return mob.isInLiquid() && isClearForMovementBetween(mob, from, to, false);
    }

}
