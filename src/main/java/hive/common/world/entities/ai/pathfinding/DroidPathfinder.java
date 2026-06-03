package hive.common.world.entities.ai.pathfinding;

import hive.common.world.Physics;
import hive.common.world.entities.ai.movement.DroidPathNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DroidPathfinder extends PathFinder {

    private static final int SIZE = DroidNodeEvaluator.getMinCacheSize(DroidPathNavigation.JUMP_WIDTH, DroidPathNavigation.FLUID_JUMP_WIDTH);
    private final boolean assumeSprinting;
    private final double speedFactor;
    private double ySpeed, gravity;
    private float groundWalkSpeed, wadeSpeed, swimSpeed;

    public DroidPathfinder(NodeEvaluator eval, int max, double speedFactor, boolean assumeSprinting) {
        super(eval, max);
        this.speedFactor = speedFactor;
        this.assumeSprinting = assumeSprinting;
        neighbors = new Node[SIZE];
    }

    private static boolean hasLineOfSight(Mob mob, Node from, Node to) {
        Vec3 start = new Vec3(from.x + 0.5, from.y + 0.5, from.z + 0.5);
        Vec3 end = new Vec3(to.x + 0.5, to.y + 0.5, to.z + 0.5);
        return mob.level()
                .clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob))
                .getType() == HitResult.Type.MISS;
    }

    @Nullable
    @Override
    public Path findPath(PathNavigationRegion region, Mob mob, Set<BlockPos> targets, float maxDist, int reachedDist, float nodeFactor) {
        ySpeed = Physics.Constants.JUMP_POWER + mob.getJumpBoostPower();
        gravity = -mob.getAttributeValue(ForgeMod.ENTITY_GRAVITY.get());
        boolean dolphinsGrace = mob.hasEffect(MobEffects.DOLPHINS_GRACE);
        double waterEfficiency = Math.min(1, EnchantmentHelper.getDepthStrider(mob) / 3.0);
        double swimAttr = mob.getAttributeValue(ForgeMod.SWIM_SPEED.get());
        double max = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * speedFactor;
        double fluidAccel = Physics.getSwimSpeedMultiplier(max, waterEfficiency, false) * swimAttr;
        wadeSpeed = (float) Physics.getTerminalSpeed(fluidAccel, Physics.getFluidFriction(false, mob.getWaterSlowDown(), dolphinsGrace, waterEfficiency, false));
        swimSpeed = (float) Physics.getTerminalSpeed(fluidAccel, Physics.getFluidFriction(true, mob.getWaterSlowDown(), dolphinsGrace, waterEfficiency, false));
        groundWalkSpeed = (float) (max * Physics.Constants.GROUND_WALK_SPEED_MULTIPLIER);
        if (assumeSprinting || mob.isSprinting()) {
            groundWalkSpeed *= Physics.Constants.SPRINT_MULTIPLIER;
        }
        Path path = super.findPath(region, mob, targets, maxDist, reachedDist, nodeFactor);
        if (path != null) {
            path = trimSwimSegments(path, mob);
            path = trimJumpToWalkTransitions(path);
        }
        return path;
    }

    private Path trimJumpToWalkTransitions(Path path) {
        int n = path.getNodeCount();
        if (n < 2) {
            return path;
        }
        List<Node> kept = new ArrayList<>(n);
        int i = 0;
        while (i < n) {
            Node cur = path.getNode(i);
            kept.add(cur);
            if (i + 1 < n
                    && ModedNode.modeOf(cur) == MovementMode.JUMP
                    && ModedNode.modeOf(path.getNode(i + 1)) == MovementMode.WALK
                    && cur.x == path.getNode(i + 1).x
                    && cur.y == path.getNode(i + 1).y
                    && cur.z == path.getNode(i + 1).z) {
                i++;
            }
            i++;
        }
        return new Path(kept, path.getTarget(), path.canReach());
    }

    private Path trimSwimSegments(Path path, Mob mob) {
        int n = path.getNodeCount();
        if (n < 3) {
            return path;
        }
        List<Node> kept = new ArrayList<>(n);
        kept.add(path.getNode(0));
        int start = 0;
        while (start < n - 1) {
            int next = start + 1;
            while (next + 1 < n
                    && ModedNode.modeOf(path.getNode(start)) == MovementMode.SWIM
                    && ModedNode.modeOf(path.getNode(next)) == MovementMode.SWIM
                    && ModedNode.modeOf(path.getNode(next + 1)) == MovementMode.SWIM
                    && hasLineOfSight(mob, path.getNode(start), path.getNode(next + 1))) {
                next++;
            }
            kept.add(path.getNode(next));
            start = next;
        }
        return new Path(kept, path.getTarget(), path.canReach());
    }

    // walk cost is always XZ distance, other costs are tuned to be proportional to the expected travel time
    // this is so that block malus is still about the same impact as in vanilla
    @Override
    protected float distance(Node n1, Node n2) {
        // transition cost
        if (n1.x == n2.x && n1.y == n2.y && n1.z == n2.z) {
            MovementMode from = ModedNode.modeOf(n1);
            MovementMode to = ModedNode.modeOf(n2);
            if (from != to) {
                ModeTransition t = ModeTransition.find(from, to);
                if (t != null) {
                    return t.costTicks * groundWalkSpeed;
                }
            }
        }
        MovementMode toMode = ModedNode.modeOf(n2);
        float dist = n1.distanceToXZ(n2);
        // swim
        if (toMode == MovementMode.SWIM) {
            return n1.distanceTo(n2) * groundWalkSpeed / swimSpeed;
        }
        // jump
        else if (toMode == MovementMode.JUMP) {
            return Physics.getLandingTime(gravity, n2.y - n1.y, ySpeed)
                    .map(t -> (float) (t * groundWalkSpeed))
                    .orElseGet(() -> n1.distanceTo(n2));
        } else if (n2.y < n1.y) {
            // sink
            if (n1.type == BlockPathTypes.WATER && n2.type == BlockPathTypes.WATER) {
                return n1.distanceTo(n2) * groundWalkSpeed / wadeSpeed;
            }
            // fall
            return Physics.getLandingTime(gravity, n2.y - n1.y, 0)
                    .map(t -> (float) (t * groundWalkSpeed))
                    .orElseGet(() -> n1.distanceTo(n2));
        }
        // wade
        else if (n1.type == BlockPathTypes.WATER || n2.type == BlockPathTypes.WATER) {
            return dist * groundWalkSpeed / wadeSpeed;
        }
        // walk
        return dist;
    }

}
