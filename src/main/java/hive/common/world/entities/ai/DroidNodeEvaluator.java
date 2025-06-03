package hive.common.world.entities.ai;

import hive.common.world.Physics;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jetbrains.annotations.Nullable;

public class DroidNodeEvaluator extends WalkNodeEvaluator {

    private static final int JUMP = 4;
    private final Node[] cache = new Node[Direction.Plane.HORIZONTAL.length()];
    private final Long2BooleanMap jumpCollisions = new Long2BooleanOpenHashMap();
    private final double speedFactor;
    private double jumpXZSpeed, jumpYSpeed, gravity, jumpHeight;

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
        jumpHeight = Physics.getHeight(gravity, jumpYSpeed);
    }

    @Override
    public void done() {
        super.done();
        jumpCollisions.clear();
    }

    @Override
    protected double getMobJumpHeight() {
        return Math.max(jumpHeight, mob.maxUpStep());
    }

    protected boolean hasJumpCollisions(int x, int y, int z) {
        return jumpCollisions.computeIfAbsent(BlockPos.asLong(x, y, z), hash -> {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int dX = 0; dX < entityWidth; dX++) {
                for (int dY = 0; dY < entityHeight; dY++) {
                    for (int dZ = 0; dZ < entityDepth; dZ++) {
                        pos.set(x + dX, y + dY, z + dZ);
                        if (!currentContext.level().getBlockState(pos).isEmpty()) {
                            return true;
                        }
                    }
                }
            }
            return false;
        });
    }

    protected int addJumps(Node[] arr, Node start, double floor, int i) {
        int y = Mth.floor(floor + jumpHeight);
        for (int x = -JUMP; x <= JUMP; x++) {
            for (int z = -JUMP; z <= JUMP; z++) {
                if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                    continue;
                }
                Node node = findAcceptedJumpNode(start.x + x, y, start.z + z, floor);
                if (node != null && isNeighborValid(node, start) && canJumpPosition(start, node, floor) && canJumpCollision(start, node, floor)) {
                    arr[i++] = node;
                }
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
            if (hasJumpCollisions(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    protected Node findAcceptedJumpNode(int x, int y, int z, double floor) {
        Node node = null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double level = getFloorLevel(pos.set(x, y, z));
        if (level - floor > getMobJumpHeight()) {
            return null;
        }
        PathType type = getCachedPathType(x, y, z);
        float cost = mob.getPathfindingMalus(type);
        if (cost >= 0) {
            node = getNodeAndUpdateCostToMax(x, y, z, type, cost);
        }
        if (!this.isAmphibious() && type == PathType.WATER && !canFloat()) {
            return this.tryFindFirstNonWaterBelow(x, y, z, node);
        } else if (type == PathType.OPEN) {
            return this.tryFindFirstGroundNodeBelow(x, y, z);
        } else if (doesBlockHavePartialCollision(type) && node == null) {
            return this.getClosedNode(x, y, z, type);
        }
        return node;
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
        if (step > 0) {
            i = addJumps(arr, start, floor, i);
        }
        return i;
    }
}
