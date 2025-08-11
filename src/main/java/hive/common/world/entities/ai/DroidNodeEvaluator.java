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
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jetbrains.annotations.Nullable;

public class DroidNodeEvaluator extends WalkNodeEvaluator {

    private static final IntegerAABB.Mutable BOUNDS = new IntegerAABB.Mutable();
    private static final Node[] CACHE = new Node[Direction.Plane.HORIZONTAL.length()];
    private final Object2BooleanMap<IntegerAABB> jumpCollisions = new Object2BooleanOpenHashMap<>();
    private final double speedFactor;
    private final boolean assumeSprinting;
    private final int jumpWidth;
    private double jumpXZSpeed, jumpYSpeed, gravity, jumpHeight;

    public DroidNodeEvaluator(double speedFactor, boolean assumeSprinting, int jumpWidth) {
        this.speedFactor = speedFactor;
        this.assumeSprinting = assumeSprinting;
        this.jumpWidth = jumpWidth;
    }

    public static int getMinCacheSize(int width) {
        return 9 + (width * 2 + 1) * (width * 2 + 1);
    }

    @Override
    public void prepare(PathNavigationRegion region, Mob mob) {
        super.prepare(region, mob);
        jumpXZSpeed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * speedFactor;
        jumpYSpeed = mob.getAttributeValue(Attributes.JUMP_STRENGTH) + mob.getJumpBoostPower();
        gravity = -mob.getAttributeValue(Attributes.GRAVITY);
        jumpHeight = Physics.getHeight(gravity, jumpYSpeed);
    }

    protected double getJumpXZSpeed(boolean canSprint) {
        if (canSprint && (assumeSprinting || mob.isSprinting())) {
            return jumpXZSpeed + DroidEntity.JUMP_BOOST;
        }
        return jumpXZSpeed;
    }

    @Override
    public void done() {
        super.done();
        jumpCollisions.clear();
    }

    @Nullable
    protected Node getJumpNode(Node start, int x, int z, double floor, boolean canSprint) {
        int y = Mth.floor(floor + getMobJumpHeight());
        Node node = tryFindFirstGroundNode(x, y, z, mob.getMaxFallDistance(), false);
        if (isNeighborValid(node, start) && canJumpPosition(start, node, floor, canSprint) && canJumpCollision(start, node, floor)) {
            return node;
        }
        return null;
    }

    @Nullable
    protected Node getCloseJumpNode(Node start, int x, int z, double floor, boolean canSprint) {
        int y = Mth.floor(floor + getMobJumpHeight());
        int dif = Math.min(y - start.y - 1, mob.getMaxFallDistance());
        Node node = tryFindFirstGroundNode(x, y, z, dif, false);
        if (isNeighborValid(node, start) && canJumpPosition(start, node, floor, canSprint) && canJumpCollision(start, node, floor)) {
            return node;
        }
        return null;
    }

    @Nullable
    protected Node getWalkNode(Node start, int x, int z) {
        Node node = tryFindFirstGroundNode(x, start.y, z, mob.getMaxFallDistance(), true);
        if (isNeighborValid(node, start)) {
            return node;
        }
        return null;
    }

    @Override
    protected boolean isNeighborValid(@Nullable Node target, Node start) {
        return target != null && !target.closed && target.costMalus >= 0;
    }

    @Nullable
    protected Node getUpNode(Node start, double floor) {
        int startY = Math.min(Mth.floor(floor + getMobJumpHeight()), start.y + entityHeight);
        int dif = startY - start.y - 1;
        Node node = tryFindFirstGroundNode(start.x, startY, start.z, dif, true);
        if (isNeighborValid(node, start)) {
            return node;
        }
        return null;
    }

    @Nullable
    protected Node getDownNode(Node start) {
        int dif = start.y - mob.level().getMinY() - 1;
        Node node = tryFindFirstGroundNode(start.x, start.y - 1, start.z, dif, true);
        if (isNeighborValid(node, start)) {
            return node;
        }
        return null;
    }

    protected Node tryFindFirstGroundNode(int x, int y, int z, int maxDown, boolean stopOnFirst) {
        int min = Math.max(mob.level().getMinY(), y - maxDown);
        for (int i = Math.min(mob.level().getMaxY(), y); i >= min; i--) {
            PathType path = getCachedPathType(x, i, z);
            float malus = mob.getPathfindingMalus(path);
            if (path != PathType.OPEN) {
                if (malus >= 0) {
                    return getNodeAndUpdateCostToMax(x, i, z, path, malus);
                } else if (stopOnFirst) {
                    return getBlockedNode(x, i, z);
                }
            }
        }
        return getBlockedNode(x, y, z);
    }

    @Override
    protected double getMobJumpHeight() {
        return Math.max(jumpHeight, mob.maxUpStep());
    }

    protected boolean hasJumpCollisions(IntegerAABB bounds) {
        if (bounds.isEmpty()) {
            return false;
        }
        if (jumpCollisions.containsKey(bounds)) {
            return jumpCollisions.getBoolean(bounds);
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX; x < bounds.maxX; x++) {
            for (int y = bounds.minY; y < bounds.maxY; y++) {
                for (int z = bounds.minZ; z < bounds.maxZ; z++) {
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

    protected int addJumps(Node[] arr, Node start, double floor, int i, boolean canSprint) {
        for (int x = -jumpWidth; x <= jumpWidth; x++) {
            for (int z = -jumpWidth; z <= jumpWidth; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                Node node;
                if (Math.abs(x) == 1 || Math.abs(z) == 1) {
                    node = getCloseJumpNode(start, start.x + x, start.z + z, floor, canSprint);
                } else {
                    node = getJumpNode(start, start.x + x, start.z + z, floor, canSprint);
                }
                if (node != null) {
                    arr[i++] = node;
                }
            }
        }
        return i;
    }

    protected boolean canJumpPosition(Node start, Node to, double floor, boolean canSprint) {
        double distH = start.distanceToXZ(to) - 1;
        double toFloor = getFloorLevel(new BlockPos(to.x, to.y, to.z));
        double diff = toFloor - floor;
        return Physics.getLandingTime(gravity, diff, jumpYSpeed)
                .map(time -> time * getJumpXZSpeed(canSprint) > distH)
                .orElse(false);
    }

    // can try to make more elegant
    protected boolean canJumpCollision(Node start, Node to, double floor) {
        double dX = to.x - start.x;
        double dZ = to.z - start.z;
        double toFloor = getFloorLevel(new BlockPos(to.x, to.y, to.z));
        IntegerAABB bounds = new IntegerAABB(start.x, start.y, start.z, start.x + entityWidth, start.y + entityHeight, start.z + entityDepth);
        double len = Math.sqrt(dX * dX + dZ * dZ);
        int steps = Mth.ceil(len / Math.min(entityDepth, entityWidth));
        dX /= steps;
        dZ /= steps;
        double dT = Physics.getLandingTime(gravity, toFloor - floor, jumpYSpeed).map(t -> t / steps).orElseThrow();
        for (int i = 0; i < steps; i++) {
            int minX = Mth.floor(start.x + dX * i);
            int maxX = Mth.ceil(start.x + dX * i) + entityWidth;
            if (dX < 0) {
                minX = Math.min(start.x + 1, minX);
                maxX = Math.min(start.x + 2, maxX);
            } else if (dX > 0) {
                minX = Math.max(start.x - 1, minX);
                maxX = Math.max(start.x, maxX);
            }
            int minZ = Mth.floor(start.z + dZ * i);
            int maxZ = Mth.ceil(start.z + dZ * i) + entityDepth;
            if (dZ < 0) {
                minZ = Math.min(start.z + 1, minZ);
                maxZ = Math.min(start.z + 2, maxZ);
            } else if (dZ > 0) {
                minZ = Math.max(start.z - 1, minZ);
                maxZ = Math.max(start.z, maxZ);
            }
            double t1 = dT * i;
            double t2 = dT * (i + 1);
            int minY = Mth.floor(floor + Physics.getMinHeight(gravity, jumpYSpeed, t1, t2));
            int maxY = Mth.ceil(floor + Physics.getMaxHeight(gravity, jumpYSpeed, t1, t2)) + entityHeight - 1;
            BOUNDS.set(minX, minY, minZ, maxX, maxY, maxZ);
            for (IntegerAABB bound : BOUNDS.truncate(bounds)) {
                if (bound != null && hasJumpCollisions(bound)) {
                    return false;
                }
            }
        }
        return true;
    }

    protected boolean canJumpIn(PathType type) {
        return type != PathType.STICKY_HONEY;
    }

    protected boolean canJumpOn(PathType type) {
        return type == PathType.BLOCKED || type == PathType.FENCE || type == PathType.LEAVES;
    }

    protected int addWalks(Node[] arr, Node start, int i) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Node node = getWalkNode(start, start.x + direction.getStepX(), start.z + direction.getStepZ());
            if (node != null) {
                arr[i++] = node;
            }
            CACHE[direction.get2DDataValue()] = node;
        }
        for (Direction dir1 : Direction.Plane.HORIZONTAL) {
            Direction dir2 = dir1.getClockWise();
            if (isDiagonalValid(start, CACHE[dir1.get2DDataValue()], CACHE[dir2.get2DDataValue()])) {
                Node node = getWalkNode(start, start.x + dir1.getStepX() + dir2.getStepX(), start.z + dir1.getStepZ() + dir2.getStepZ());
                if (isDiagonalValid(node)) {
                    arr[i++] = node;
                }
            }
        }
        return i;
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        PathType path = context.getPathTypeFromState(x, y, z);
        if (path == PathType.OPEN && y >= context.level().getMinY() + 1) {
            return switch (context.getPathTypeFromState(x, y - 1, z)) {
                case OPEN, WATER, LAVA, WALKABLE -> PathType.OPEN;
                case DAMAGE_FIRE -> PathType.DAMAGE_FIRE;
                case DAMAGE_OTHER -> PathType.DAMAGE_OTHER;
                case STICKY_HONEY -> PathType.STICKY_HONEY;
                case POWDER_SNOW -> PathType.DANGER_POWDER_SNOW;
                case DAMAGE_CAUTIOUS -> PathType.DAMAGE_CAUTIOUS;
                case TRAPDOOR -> PathType.DANGER_TRAPDOOR;
                default -> checkNeighbourBlocks(context, x, y, z, PathType.WALKABLE);
            };
        } else if (path == PathType.STICKY_HONEY) {
            return PathType.BLOCKED;
        } else {
            return path;
        }
    }

    @Override
    public int getNeighbors(Node[] arr, Node start) {
        BlockPos pos = new BlockPos(start.x, start.y, start.z);
        int i = 0;
        double floor = getFloorLevel(pos);
        PathType type = getCachedPathType(start.x, start.y, start.z);
        PathType downType = getCachedPathType(start.x, start.y - 1, start.z);
        FluidState fluid = currentContext.level().getFluidState(pos);
        boolean canJump = canJumpIn(type) && canJumpOn(downType) && fluid.getHeight(currentContext.level(), pos) <= mob.getFluidJumpThreshold();
        Node down = getDownNode(start);
        if (down != null) {
            arr[i++] = down;
        }
        i = addWalks(arr, start, i);
        if (canJump && getMobJumpHeight() >= 1) {
            Node up = getUpNode(start, floor);
            if (up != null) {
                arr[i++] = up;
            }
            boolean canSprint = fluid.getFluidType().isAir();
            i = addJumps(arr, start, floor, i, canSprint);
        }
        return i;
    }
}
