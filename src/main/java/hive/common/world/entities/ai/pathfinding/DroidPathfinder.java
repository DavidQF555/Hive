package hive.common.world.entities.ai.pathfinding;

import hive.common.world.Physics;
import hive.common.world.entities.DroidEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class DroidPathfinder extends PathFinder {

    private static final int SIZE = DroidNodeEvaluator.getMinCacheSize(DroidPathNavigation.JUMP_WIDTH, DroidPathNavigation.FLUID_JUMP_WIDTH);
    private final boolean assumeSprinting;
    private final double speedFactor;
    private double ySpeed, xzSpeed, gravity;

    public DroidPathfinder(NodeEvaluator eval, int max, double speedFactor, boolean assumeSprinting) {
        super(eval, max);
        this.speedFactor = speedFactor;
        this.assumeSprinting = assumeSprinting;
        neighbors = new Node[SIZE];
    }

    public static boolean isJump(@Nullable Node prev, Node next) {
        return prev != null && (next.y > prev.y || Math.abs(prev.x - next.x) >= 2 || Math.abs(prev.z - next.z) >= 2);
    }

    @Nullable
    @Override
    public Path findPath(PathNavigationRegion region, Mob mob, Set<BlockPos> targets, float maxDist, int reachedDist, float nodeFactor) {
        ySpeed = mob.getAttributeValue(Attributes.JUMP_STRENGTH) + mob.getJumpBoostPower();
        gravity = -mob.getAttributeValue(Attributes.GRAVITY);
        xzSpeed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * speedFactor;
        if (assumeSprinting || mob.isSprinting()) {
            xzSpeed += DroidEntity.JUMP_BOOST;
        }
        return super.findPath(region, mob, targets, maxDist, reachedDist, nodeFactor);
    }

    @Override
    protected float distance(Node n1, Node n2) {
        float dist = n1.distanceToXZ(n2);
        if (isJump(n1, n2)) {
            return Physics.getLandingTime(gravity, n2.y - n1.y, ySpeed)
                    .map(t -> (float) (t * xzSpeed))
                    .orElseGet(() -> n1.distanceTo(n2));
        } else if (n2.y < n1.y) {
            return Physics.getLandingTime(gravity, n2.y - n1.y, 0)
                    .map(t -> (float) (t * xzSpeed))
                    .orElseGet(() -> n1.distanceTo(n2));
        }
        return dist;
    }

}
