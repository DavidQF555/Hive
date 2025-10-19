package hive.common.world.entities.ai;

import hive.common.world.entities.DroidEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class DroidPathNavigation extends GroundPathNavigation {

    public static final int JUMP_WIDTH = 5;
    public static final int FLUID_JUMP_WIDTH = 1;
    private static final double FLUID_JUMP_HEIGHT = 1.0 / 9;
    private static final BlockPos.MutableBlockPos MUTABLE = new BlockPos.MutableBlockPos();
    private final DroidEntity mob;

    public DroidPathNavigation(DroidEntity entity, Level world) {
        super(entity, world);
        mob = entity;
        setCanFloat(true);
    }

    private boolean isJump(BlockGetter world, double step, @Nullable Node prev, Node next) {
        if (prev == null) {
            return false;
        }
        if (Math.abs(prev.x - next.x) >= 2 || Math.abs(prev.z - next.z) >= 2) {
            return true;
        }
        double prevHeight = WalkNodeEvaluator.getFloorLevel(world, MUTABLE.set(prev.x, prev.y, prev.z));
        double nextHeight = WalkNodeEvaluator.getFloorLevel(world, MUTABLE.set(next.x, next.y, next.z));
        return nextHeight - prevHeight > step;
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
        if (!isDone()) {
            if (mob.getMoveControl() instanceof DroidMoveControl control && control.isStuck()) {
                stop();
                control.setStuck(false);
            } else {
                Node prev = path.getPreviousNode();
                Node node = path.getNextNode();
                if (isJump(mob.level(), mob.maxUpStep(), prev, node)) {
                    if (mob.getMoveControl() instanceof DroidMoveControl control) {
                        Vec3 target = this.path.getNextEntityPos(mob);
                        control.jumpTowards(target.x(), getGroundY(target), target.z(), speedModifier);
                    }
                }
            }
        }
    }

    @Override
    protected boolean shouldTargetNextNodeInDirection(Vec3 start) {
        return super.shouldTargetNextNodeInDirection(start) && !DroidPathfinder.isJump(path.getNextNode(), path.getNode(path.getNextNodeIndex() + 1));
    }

}
