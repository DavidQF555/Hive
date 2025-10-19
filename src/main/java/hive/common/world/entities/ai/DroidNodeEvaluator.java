package hive.common.world.entities.ai;

import hive.common.world.Physics;
import hive.common.world.entities.DroidEntity;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;

public class DroidNodeEvaluator extends WalkNodeEvaluator {

    private static final IntegerAABB.Mutable BOUNDS = new IntegerAABB.Mutable();
    private static final BlockPos.MutableBlockPos MUTABLE = new BlockPos.MutableBlockPos();
    private static final Node[] CACHE = new Node[Direction.Plane.HORIZONTAL.stream().toList().size()];
    private final Object2BooleanMap<IntegerAABB> jumpCollisions = new Object2BooleanOpenHashMap<>();
    private final double speedFactor, fluidJumpHeight;
    private final boolean assumeSprinting;
    private final int jumpWidth, fluidJumpWidth;
    private double jumpXZSpeed, jumpYSpeed, gravity, jumpHeight, maxStep;

    public DroidNodeEvaluator(double speedFactor, boolean assumeSprinting, int jumpWidth, int fluidJumpWidth, double fluidJumpHeight) {
        this.speedFactor = speedFactor;
        this.assumeSprinting = assumeSprinting;
        this.jumpWidth = jumpWidth;
        this.fluidJumpWidth = fluidJumpWidth;
        this.fluidJumpHeight = fluidJumpHeight;
    }

    public static int getMinCacheSize(int jumpWidth, int fluidJumpWidth) {
        int jumpNodes = (jumpWidth * 2 + 1) * (jumpWidth * 2 + 1);
        int fluidNodes = (fluidJumpWidth * 2 + 1) * (fluidJumpWidth * 2 + 1);
        return 9 + Math.max(jumpNodes, fluidNodes);
    }

    @Override
    public void prepare(PathNavigationRegion region, Mob mob) {
        super.prepare(region, mob);
        jumpXZSpeed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * speedFactor;
        jumpYSpeed = 0.42 + mob.getJumpBoostPower();
        gravity = -mob.getAttributeValue(ForgeMod.ENTITY_GRAVITY.get());
        jumpHeight = Physics.getHeight(gravity, jumpYSpeed);
        maxStep = mob.getStepHeight();
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

    @Override
    public Node getStart() {
        return getStartNode(mob.blockPosition());
    }

    @Nullable
    protected Node getJumpNode(Node start, int x, int z, double floor, boolean canSprint) {
        double y = floor + getMobJumpHeight();
        Node node = tryFindFirstGroundNode(x, z, y - mob.getMaxFallDistance(), y, maxStep, false);
        if (isNeighborValid(node, start) && canJump(start, node, floor, canSprint)) {
            return node;
        }
        return null;
    }

    @Nullable
    protected Node getCloseJumpNode(Node start, int x, int z, double floor, boolean canSprint) {
        double maxY = floor + getMobJumpHeight();
        double minY = Math.max(floor + maxStep, maxY - mob.getMaxFallDistance());
        Node node = tryFindFirstGroundNode(x, z, minY, maxY, maxStep, false);
        if (isNeighborValid(node, start) && canJump(start, node, floor, canSprint)) {
            return node;
        }
        return null;
    }

    @Nullable
    protected Node getWalkNode(Node start, int x, int z, double floor) {
        Node node = tryFindFirstGroundNode(x, z, floor - mob.getMaxFallDistance(), floor, maxStep, true);
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
        double maxY = Math.min(floor + getMobJumpHeight(), start.y + entityHeight);
        Node node = tryFindFirstGroundNode(start.x, start.z, floor, maxY, maxStep, true);
        if (isNeighborValid(node, start)) {
            return node;
        }
        return null;
    }

    @Nullable
    protected Node getDownNode(Node start, double floor) {
        Node node = tryFindFirstGroundNode(start.x, start.z, mob.level().getMinBuildHeight(), floor, 0, true);
        if (isNeighborValid(node, start)) {
            return node;
        }
        return null;
    }

    @Override
    protected double getFloorLevel(BlockPos pos) {
        if (level.getFluidState(pos).is(FluidTags.WATER)) {
            return pos.getY();
        }
        return getFloorLevel(level, pos);
    }

    @Nullable
    protected Node tryFindFirstGroundNode(int x, int z, double minY, double maxY, double step, boolean stopOnFirst) {
        int min = Math.max(mob.level().getMinBuildHeight(), Mth.floor(minY));
        for (int i = Math.min(mob.level().getMaxBuildHeight() - 1, Mth.ceil(maxY + step)); i >= min; i--) {
            double floor = getFloorLevel(MUTABLE.set(x, i, z));
            if (floor >= maxY + step) {
                continue;
            }
            if (floor < minY) {
                break;
            }
            BlockPathTypes path = getCachedBlockType(mob, x, i, z);
            float malus = mob.getPathfindingMalus(path);
            if (path != BlockPathTypes.OPEN) {
                if (malus >= 0) {
                    return getNodeAndUpdateCostToMax(x, i, z, path, malus);
                } else if (stopOnFirst && floor < maxY) {
                    return getBlockedNode(x, i, z);
                }
            }
        }
        return null;
    }

    protected double getMobJumpHeight() {
        return jumpHeight;
    }

    protected boolean hasJumpCollisions(IntegerAABB bounds, @Nullable IntegerAABB exclude) {
        if (bounds.isEmpty()) {
            return false;
        }
        boolean set = exclude == null || !bounds.intersects(exclude);
        if (jumpCollisions.containsKey(bounds)) {
            if (!jumpCollisions.getBoolean(bounds)) {
                return false;
            } else if (set) {
                return true;
            }
        }
        for (int x = bounds.minX; x < bounds.maxX; x++) {
            for (int y = bounds.minY; y < bounds.maxY; y++) {
                for (int z = bounds.minZ; z < bounds.maxZ; z++) {
                    if (exclude != null && exclude.intersects(x, y, z)) {
                        continue;
                    }
                    MUTABLE.set(x, y, z);
                    if (!level.getBlockState(MUTABLE).getCollisionShape(level, MUTABLE).isEmpty()) {
                        jumpCollisions.put(bounds.immutable(), true);
                        return true;
                    }
                }
            }
        }
        if (set) {
            jumpCollisions.put(bounds.immutable(), false);
        }
        return false;
    }

    @Nullable
    protected Node getFluidNode(Node start, int x, int y, int z) {
        BlockPathTypes path = getCachedBlockType(mob, x, y, z);
        Node node = getNodeAndUpdateCostToMax(x, y, z, path, mob.getPathfindingMalus(path));
        if (isNeighborValid(node, start)) {
            return node;
        }
        return null;
    }

    protected int addFluidNodes(Node[] arr, Node start, int i, double fluidHeight) {
        // fluid jump nodes
        for (int x = start.x - fluidJumpWidth; x <= start.x + fluidJumpWidth; x++) {
            for (int z = start.z - fluidJumpWidth; z <= start.z + fluidJumpWidth; z++) {
                if (x != start.x || z != start.z) {
                    Node node = tryFindFirstGroundNode(x, z, fluidHeight - mob.getMaxFallDistance() + fluidJumpHeight, fluidHeight + fluidJumpHeight, maxStep, false);
                    if (isNeighborValid(node, start) && canJump(start, node, fluidHeight, false)) {
                        arr[i++] = node;
                    }
                }
            }
        }
        // fluid up node
        if (start.y + 1 < level.getMaxBuildHeight()) {
            Node up = getFluidNode(start, start.x, start.y + 1, start.z);
            if (up != null) {
                arr[i++] = up;
            }
        }
        return i;
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

    protected boolean canJump(Node start, Node to, double floor, boolean canSprint) {
        double distH = start.distanceToXZ(to) - 1;
        double toFloor = getFloorLevel(MUTABLE.set(to.x, to.y, to.z));
        double diff = toFloor - floor;
        // TODO not completely accurate, XZ speed is different if starting in water
        return Physics.getLandingTime(gravity, diff, jumpYSpeed)
                .map(time -> time * getJumpXZSpeed(canSprint) > distH && canJumpCollision(start, to, floor, time))
                .orElse(false);
    }

    // can try to make more elegant
    protected boolean canJumpCollision(Node start, Node to, double floor, double time) {
        double dX = to.x - start.x;
        double dZ = to.z - start.z;
        IntegerAABB bounds = new IntegerAABB(start.x, start.y, start.z, start.x + entityWidth, start.y + entityHeight, start.z + entityDepth);
        double len = Math.sqrt(dX * dX + dZ * dZ);
        int steps = Mth.ceil(len / Math.min(entityDepth, entityWidth));
        dX /= steps;
        dZ /= steps;
        double dT = time / steps;
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
            int maxY = Mth.ceil(floor + Physics.getMaxHeight(gravity, jumpYSpeed, t1, t2)) + entityHeight;
            BOUNDS.set(minX, minY, minZ, maxX, maxY, maxZ);
            if (hasJumpCollisions(BOUNDS, bounds)) {
                return false;
            }
        }
        return true;
    }

    protected boolean canJumpIn(BlockPathTypes type) {
        return type != BlockPathTypes.STICKY_HONEY;
    }

    protected boolean canJumpOn(BlockPathTypes type) {
        return type == BlockPathTypes.BLOCKED || type == BlockPathTypes.FENCE || type == BlockPathTypes.LEAVES;
    }

    protected int addHorizontal(Node[] arr, Node start, double floor, int i) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Node node = getWalkNode(start, start.x + direction.getStepX(), start.z + direction.getStepZ(), floor);
            if (node != null) {
                arr[i++] = node;
            }
            CACHE[direction.get2DDataValue()] = node;
        }
        for (Direction dir1 : Direction.Plane.HORIZONTAL) {
            Direction dir2 = dir1.getClockWise();
            Node node = getWalkNode(start, start.x + dir1.getStepX() + dir2.getStepX(), start.z + dir1.getStepZ() + dir2.getStepZ(), floor);
            if (isDiagonalValid(start, CACHE[dir1.get2DDataValue()], CACHE[dir2.get2DDataValue()], node)) {
                arr[i++] = node;
            }
        }
        return i;
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockGetter world, int x, int y, int z) {
        BlockPathTypes path = getBlockPathTypeRaw(world, MUTABLE.set(x, y, z));
        if (path == BlockPathTypes.OPEN && y >= level.getMinBuildHeight() + 1) {
            return switch (getBlockPathTypeRaw(world, MUTABLE.set(x, y - 1, z))) {
                case OPEN, WATER, LAVA, WALKABLE -> BlockPathTypes.OPEN;
                case DAMAGE_FIRE -> BlockPathTypes.DAMAGE_FIRE;
                case DAMAGE_OTHER -> BlockPathTypes.DAMAGE_OTHER;
                case STICKY_HONEY -> BlockPathTypes.STICKY_HONEY;
                case POWDER_SNOW -> BlockPathTypes.DANGER_POWDER_SNOW;
                case DAMAGE_CAUTIOUS -> BlockPathTypes.DAMAGE_CAUTIOUS;
                default -> checkNeighbourBlocks(world, MUTABLE.set(x, y, z), BlockPathTypes.WALKABLE);
            };
        } else if (path == BlockPathTypes.STICKY_HONEY) {
            return BlockPathTypes.BLOCKED;
        } else {
            return path;
        }
    }

    @Override
    public int getNeighbors(Node[] arr, Node start) {
        BlockPos pos = new BlockPos(start.x, start.y, start.z);
        int i = 0;
        double floor = getFloorLevel(pos);
        BlockPathTypes type = getCachedBlockType(mob, start.x, start.y, start.z);
        BlockPathTypes downType = getCachedBlockType(mob, start.x, start.y - 1, start.z);
        FluidState fluid = level.getFluidState(pos);
        double fluidHeight = fluid.getHeight(level, pos);
        boolean canJump = canJumpIn(type) && canJumpOn(downType) && fluidHeight <= mob.getFluidJumpThreshold();
        Node down = getDownNode(start, floor);
        if (down != null) {
            arr[i++] = down;
        }
        i = addHorizontal(arr, start, floor, i);
        if (canJump) {
            if (getMobJumpHeight() >= 1) {
                Node up = getUpNode(start, floor);
                if (up != null) {
                    arr[i++] = up;
                }
                boolean canSprint = fluid.getFluidType().isAir();
                i = addJumps(arr, start, floor, i, canSprint);
            }
        } else if (!fluid.getFluidType().isAir()) {
            i = addFluidNodes(arr, start, i, start.y + fluidHeight);
        }
        return i;
    }
}
