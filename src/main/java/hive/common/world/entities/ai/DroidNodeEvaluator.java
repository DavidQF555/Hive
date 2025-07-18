package hive.common.world.entities.ai;

import hive.common.world.Physics;
import hive.common.world.entities.DroidEntity;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
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
    private static final IntegerAABB.Mutable BOUNDS = new IntegerAABB.Mutable();
    private static final Node[] CACHE = new Node[Direction.Plane.HORIZONTAL.length()];
    private final Object2BooleanMap<IntegerAABB> jumpCollisions = new Object2BooleanOpenHashMap<>();
    private final double speedFactor;
    private final boolean assumeSprinting;
    private double jumpXZSpeed, jumpYSpeed, gravity, jumpHeight;

    public DroidNodeEvaluator(double speedFactor, boolean assumeSprinting) {
        this.speedFactor = speedFactor;
        this.assumeSprinting = assumeSprinting;
    }

    @Override
    public void prepare(PathNavigationRegion region, Mob mob) {
        super.prepare(region, mob);
        jumpXZSpeed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * speedFactor;
        if (assumeSprinting || mob.isSprinting()) {
            jumpXZSpeed += DroidEntity.JUMP_BOOST;
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

    protected boolean hasJumpCollisions(IntegerAABB bounds) {
        if (jumpCollisions.containsKey(bounds)) {
            return jumpCollisions.getBoolean(bounds);
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    pos.set(x, y, z);
                    if (!currentContext.level().getBlockState(pos).isEmpty()) {
                        jumpCollisions.put(bounds.immutable(), true);
                        return true;
                    }
                }
            }
        }
        jumpCollisions.put(bounds.immutable(), false);
        return false;
    }

    protected int addLongJumps(Node[] arr, Node start, double floor, int i) {
        int y = Mth.floor(floor);
        for (int x = -JUMP; x <= JUMP; x++) {
            for (int z = -JUMP; z <= JUMP; z++) {
                if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                    continue;
                }
                Node node = findLongJumpNode(start.x + x, y, start.z + z, floor);
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
        int steps = Mth.ceil(len / Math.min(entityDepth, entityWidth));
        dX /= steps;
        dZ /= steps;
        double dT = Physics.getLandingTime(gravity, to.y - floor, jumpYSpeed).map(t -> t / steps).orElseThrow();
        for (int i = 0; i < steps; i++) {
            int minX = Mth.floor(start.x + dX * i);
            int maxX = Mth.ceil(start.x + dX * i) + entityWidth - 1;
            if (dX < 0) {
                minX = Math.min(start.x + 1, minX);
                maxX = Math.min(start.x + 1, maxX);
            } else if (dX > 0) {
                minX = Math.max(start.x - 1, minX);
                maxX = Math.max(start.x - 1, maxX);
            }
            int minZ = Mth.floor(start.z + dZ * i);
            int maxZ = Mth.ceil(start.z + dZ * i) + entityDepth - 1;
            if (dZ < 0) {
                minZ = Math.min(start.z + 1, minZ);
                maxZ = Math.min(start.z + 1, maxZ);
            } else if (dZ > 0) {
                minZ = Math.max(start.z - 1, minZ);
                maxZ = Math.max(start.z - 1, maxZ);
            }
            if (minX > maxX || minZ > maxZ) {
                continue;
            }
            double t1 = dT * i;
            double t2 = dT * (i + 1);
            int minY = Mth.floor(floor + Physics.getMinHeight(gravity, jumpYSpeed, t1, t2));
            int maxY = Mth.ceil(floor + Physics.getMaxHeight(gravity, jumpYSpeed, t1, t2)) + entityHeight - 1;
            BOUNDS.set(minX, minY, minZ, maxX, maxY, maxZ);
            if (hasJumpCollisions(BOUNDS)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    protected Node findLongJumpNode(int x, int y, int z, double floor) {
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
        if (!isAmphibious() && type == PathType.WATER && !canFloat()) {
            return tryFindFirstNonWaterBelow(x, y, z, node);
        } else if (type == PathType.OPEN) {
            return tryFindFirstGroundNodeBelow(x, y, z);
        } else if (doesBlockHavePartialCollision(type) && node == null) {
            return getClosedNode(x, y, z, type);
        }
        return node;
    }

    protected boolean canJumpFrom(PathType type) {
        return mob.getPathfindingMalus(type) >= 0 && type != PathType.STICKY_HONEY;
    }

    @Override
    public int getNeighbors(Node[] arr, Node start) {
        int i = 0;
        double floor = getFloorLevel(new BlockPos(start.x, start.y, start.z));
        PathType type = getCachedPathType(start.x, start.y, start.z);
        int step = 0;
        if (canJumpFrom(type)) {
            step = Mth.floor(Math.max(jumpHeight, mob.maxUpStep()));
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Node node = findAcceptedNode(start.x + direction.getStepX(), start.y, start.z + direction.getStepZ(), step, floor, direction, type);
            CACHE[direction.get2DDataValue()] = node;
            if (isNeighborValid(node, start)) {
                arr[i++] = node;
            }
        }
        for (Direction dir1 : Direction.Plane.HORIZONTAL) {
            Direction dir2 = dir1.getClockWise();
            if (isDiagonalValid(start, CACHE[dir1.get2DDataValue()], CACHE[dir2.get2DDataValue()])) {
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
            i = addLongJumps(arr, start, floor, i);
        }
        return i;
    }
}
