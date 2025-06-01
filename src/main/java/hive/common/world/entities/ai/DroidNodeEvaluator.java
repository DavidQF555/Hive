package hive.common.world.entities.ai;

import hive.common.world.Physics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class DroidNodeEvaluator extends WalkNodeEvaluator {

    private static final int JUMP = 4;
    private final Node[] cache = new Node[Direction.Plane.HORIZONTAL.length()];
    private final double speedFactor;
    private double jumpXZSpeed, jumpYSpeed, gravity;

    public DroidNodeEvaluator(double speedFactor) {
        this.speedFactor = speedFactor;
    }

    @Override
    public void prepare(PathNavigationRegion region, Mob mob) {
        super.prepare(region, mob);
        jumpXZSpeed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * speedFactor;
        if (mob.isSprinting()) {
            jumpXZSpeed += 0.2;
        }
        jumpYSpeed = mob.getAttributeValue(Attributes.JUMP_STRENGTH);
        gravity = -mob.getAttributeValue(Attributes.GRAVITY);
    }

    protected int addJumps(Node[] arr, Node start, Direction dir, int step, double floor, PathType type, int i) {
        for (int j = 2; j <= JUMP; j++) {
            Node node = findAcceptedNode(start.x + dir.getStepX() * j, start.y, start.z + dir.getStepZ() * j, step, floor, dir, type);
            if (node != null && isNeighborValid(node, start) && canJumpPosition(start, node, floor) && canJumpCollision(start, node, floor)) {
                arr[i++] = node;
            }
        }
        return i;
    }

    protected boolean canJumpPosition(Node start, Node to, double floor) {
        double distH = start.distanceToXZ(to) - 1;
        if (distH > JUMP) {
            return false;
        }
        double diff = to.y - floor;
        return Physics.getLandingTime(gravity, diff, jumpYSpeed)
                .map(time -> time * jumpXZSpeed > distH)
                .orElse(false);
    }

    protected boolean canJumpCollision(Node start, Node to, double floor) {
        double dX = to.x - start.x;
        double dZ = to.z - start.z;
        double len = Math.sqrt(dX * dX + dZ * dZ);
        int steps = Mth.ceil(len / (entityDepth * entityWidth));
        dX /= steps;
        dZ /= steps;
        for (int i = 1; i < steps; i++) {
            int x = Mth.floor(start.x + dX * i);
            int z = Mth.floor(start.z + dZ * i);
            int y = Mth.floor(floor + Physics.getHeightFromDistance(gravity, jumpYSpeed, jumpXZSpeed, len * i / steps));
            AABB bounds = new AABB(x, y, z, x + entityWidth, y + entityHeight + 1, z + entityDepth);
            if (hasCollisions(bounds)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    @Override
    protected Node findAcceptedNode(int x, int y, int z, int step, double floor, Direction dir, PathType path) {
        Node node = null;
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        double d0 = this.getFloorLevel(blockpos$mutableblockpos.set(x, y, z));
        if (d0 - floor > this.getMobJumpHeight()) {
            return null;
        } else {
            PathType pathtype = this.getCachedPathType(x, y, z);
            float f = this.mob.getPathfindingMalus(pathtype);
            if (f >= 0) {
                node = this.getNodeAndUpdateCostToMax(x, y, z, pathtype, f);
            }

            if (pathtype == PathType.WALKABLE || this.isAmphibious() && pathtype == PathType.WATER) {
                return node;
            }

            if ((node == null || node.costMalus < 0.0F)
                    && step > 0
                    && (pathtype != PathType.FENCE || this.canWalkOverFences())
                    && pathtype != PathType.UNPASSABLE_RAIL
                    && pathtype != PathType.TRAPDOOR
                    && pathtype != PathType.POWDER_SNOW) {
                return this.tryJumpOn(x, y, z, step, floor, dir, path, blockpos$mutableblockpos);
            } else if (!this.isAmphibious() && pathtype == PathType.WATER && !this.canFloat()) {
                return this.tryFindFirstNonWaterBelow(x, y, z, node);
            } else if (pathtype == PathType.OPEN) {
                return this.tryFindFirstGroundNodeBelow(x, y, z);
            } else if (doesBlockHavePartialCollision(pathtype) && node == null) {
                return this.getClosedNode(x, y, z, pathtype);
            }
            return null;
        }
    }


    @Override
    public int getNeighbors(Node[] arr, Node start) {
        int i = 0;
        double floor = getFloorLevel(new BlockPos(start.x, start.y, start.z));
        PathType up = getCachedPathType(start.x, start.y + 1, start.z);
        PathType type = getCachedPathType(start.x, start.y, start.z);
        int step = 0;
        if (mob.getPathfindingMalus(up) >= 0 && type != PathType.STICKY_HONEY) {
            step = Math.max(1, Mth.floor(mob.maxUpStep()));
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Node node = findAcceptedNode(start.x + direction.getStepX(), start.y, start.z + direction.getStepZ(), step, floor, direction, type);
            cache[direction.get2DDataValue()] = node;
            if (isNeighborValid(node, start)) {
                arr[i++] = node;
            }
            if (step > 0) {
                i = addJumps(arr, start, direction, step, floor, type, i);
            }
        }
        for (Direction dir1 : Direction.Plane.HORIZONTAL) {
            Direction dir2 = dir1.getClockWise();
            if (isDiagonalValid(start, cache[dir1.get2DDataValue()], cache[dir2.get2DDataValue()])) {
                Node node = findAcceptedNode(
                        start.x + dir1.getStepX() + dir2.getStepX(),
                        start.y,
                        start.z + dir1.getStepZ() + dir2.getStepZ(),
                        step,
                        floor,
                        dir1,
                        type
                );
                if (isDiagonalValid(node)) {
                    arr[i++] = node;
                }
            }
        }
        for (Direction direction : Direction.Plane.VERTICAL) {
            Node node = findAcceptedNode(start.x, start.y + direction.getStepY(), start.z, step, floor, direction, type);
            if (isNeighborValid(node, start)) {
                arr[i++] = node;
            }
        }
        return i;
    }
}
