package hive.common.world.entities.ai.pathfinding;

import hive.common.world.Physics;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

public class DroidNodeEvaluator extends WalkNodeEvaluator {

    // 3 x 3 x 3 excluding self
    private static final int SWIM_NEIGHBOR_COUNT = 3 * 3 * 3 - 1;
    // 4 cardinal + 4 diagonal + 1 self step
    private static final int WALK_NEIGHBOR_BASELINE = 9;
    private static final IntegerAABB.Mutable BOUNDS = new IntegerAABB.Mutable();
    private static final BlockPos.MutableBlockPos MUTABLE = new BlockPos.MutableBlockPos();
    private static final Node[] CACHE = new Node[Direction.Plane.HORIZONTAL.length()];
    private static final Node[][] SWIM_LEVEL_CACHE = new Node[3][Direction.Plane.HORIZONTAL.length()];
    private final Long2ObjectMap<ModedNode> modedNodes = new Long2ObjectOpenHashMap<>();
    private final Object2BooleanMap<IntegerAABB> jumpCollisions = new Object2BooleanOpenHashMap<>();
    private final Long2ObjectMap<PathType> swimPathTypeCache = new Long2ObjectOpenHashMap<>();
    private final Long2BooleanMap submergedCache = new Long2BooleanOpenHashMap();
    private final double speedFactor, fluidJumpHeight;
    private final boolean assumeSprinting;
    private final int jumpWidth, fluidJumpWidth;
    private double jumpXZSpeed, jumpYSpeed, gravity, jumpHeight, maxStep;
    private int swimWidth, swimHeight, swimDepth;

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
        return Math.max(SWIM_NEIGHBOR_COUNT, WALK_NEIGHBOR_BASELINE + Math.max(jumpNodes, fluidNodes));
    }

    @Override
    public void prepare(PathNavigationRegion region, Mob mob) {
        super.prepare(region, mob);
        jumpXZSpeed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * speedFactor * Physics.Constants.GROUND_WALK_SPEED_MULTIPLIER;
        jumpYSpeed = mob.getAttributeValue(Attributes.JUMP_STRENGTH) + mob.getJumpBoostPower();
        gravity = -mob.getAttributeValue(Attributes.GRAVITY);
        jumpHeight = Physics.getHeight(gravity, jumpYSpeed);
        maxStep = mob.maxUpStep();
        EntityDimensions swim = mob.getDimensions(MovementMode.SWIM.pose);
        swimWidth = Mth.floor(swim.width() + 1f);
        swimHeight = Mth.floor(swim.height() + 1f);
        swimDepth = Mth.floor(swim.width() + 1f);
    }

    protected double getJumpXZSpeed(boolean canSprint) {
        if (canSprint && (assumeSprinting || mob.isSprinting())) {
            return jumpXZSpeed + Physics.Constants.JUMP_BOOST;
        }
        return jumpXZSpeed;
    }

    @Override
    public void done() {
        super.done();
        modedNodes.clear();
        jumpCollisions.clear();
        swimPathTypeCache.clear();
        submergedCache.clear();
    }

    protected PathType getCachedSwimPathType(int x, int y, int z) {
        return swimPathTypeCache.computeIfAbsent(BlockPos.asLong(x, y, z), l -> getSwimPathTypeOfMob(currentContext, x, y, z));
    }

    protected PathType getSwimPathTypeOfMob(PathfindingContext context, int x, int y, int z) {
        Set<PathType> set = getPathTypeWithinSwimBB(context, x, y, z);
        PathType worst = PathType.BLOCKED;
        for (PathType type : set) {
            if (mob.getPathfindingMalus(type) < 0) {
                return type;
            }
            if (mob.getPathfindingMalus(type) >= mob.getPathfindingMalus(worst)) {
                worst = type;
            }
        }
        return swimWidth <= 1
                && worst != PathType.OPEN
                && mob.getPathfindingMalus(worst) == 0
                && getPathType(context, x, y, z) == PathType.OPEN
                ? PathType.OPEN
                : worst;
    }

    // mirrors WalkNodeEvaluator.getPathTypeWithinMobBB but iterates the swim hitbox
    public Set<PathType> getPathTypeWithinSwimBB(PathfindingContext context, int x, int y, int z) {
        EnumSet<PathType> set = EnumSet.noneOf(PathType.class);
        for (int i = 0; i < swimWidth; i++) {
            for (int j = 0; j < swimHeight; j++) {
                for (int k = 0; k < swimDepth; k++) {
                    set.add(getPathType(context, i + x, j + y, k + z));
                }
            }
        }
        return set;
    }

    @Override
    public ModedNode getStart() {
        BlockPos pos = mob.blockPosition();
        MovementMode mode = getStartMode(pos);
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (mode == MovementMode.WALK && mob.onGround()) {
            y = Mth.floor(mob.getY() + 0.5);
        }

        // mirrors WalkNodeEvaluator.getStart fallback to the four hitbox box corners
        if (!canStartAt(x, y, z, mode)) {
            AABB box = mob.getBoundingBox();
            int minX = Mth.floor(box.minX);
            int maxX = Mth.floor(box.maxX);
            int minZ = Mth.floor(box.minZ);
            int maxZ = Mth.floor(box.maxZ);
            if (canStartAt(minX, y, minZ, mode)) {
                x = minX;
                z = minZ;
            } else if (canStartAt(minX, y, maxZ, mode)) {
                x = minX;
                z = maxZ;
            } else if (canStartAt(maxX, y, minZ, mode)) {
                x = maxX;
                z = minZ;
            } else if (canStartAt(maxX, y, maxZ, mode)) {
                x = maxX;
                z = maxZ;
            }
        }
        ModedNode node = getNode(x, y, z, mode);
        node.type = mode == MovementMode.SWIM
                ? getCachedSwimPathType(node.x, node.y, node.z)
                : getCachedPathType(node.x, node.y, node.z);
        node.costMalus = mob.getPathfindingMalus(node.type);
        return node;
    }

    protected boolean canStartAt(int x, int y, int z, MovementMode mode) {
        PathType type = mode == MovementMode.SWIM ? getCachedSwimPathType(x, y, z) : getCachedPathType(x, y, z);
        return type != PathType.OPEN && mob.getPathfindingMalus(type) >= 0;
    }

    protected ModedNode getNode(int x, int y, int z, MovementMode mode) {
        return modedNodes.computeIfAbsent(ModedNode.hash(x, y, z, mode), id -> new ModedNode(x, y, z, mode));
    }

    @Override
    protected Node getNode(int x, int y, int z) {
        return getNode(x, y, z, MovementMode.WALK);
    }

    protected ModedNode getBlockedNode(int x, int y, int z, MovementMode mode) {
        ModedNode node = getNode(x, y, z, mode);
        node.type = PathType.BLOCKED;
        node.costMalus = -1;
        return node;
    }

    protected ModedNode getNodeAndUpdateCostToMax(int x, int y, int z, MovementMode mode, PathType path, float malus) {
        ModedNode node = getNode(x, y, z, mode);
        node.costMalus = Math.max(node.costMalus, malus);
        node.type = path;
        return node;
    }

    private MovementMode getStartMode(BlockPos pos) {
        if (isSubmerged(pos.getX(), pos.getY(), pos.getZ())) {
            return MovementMode.SWIM;
        }
        return MovementMode.WALK;
    }

    private boolean isSubmerged(int x, int y, int z) {
        return submergedCache.computeIfAbsent(BlockPos.asLong(x, y, z), l -> computeSubmerged(x, y, z));
    }

    private boolean computeSubmerged(int x, int y, int z) {
        for (int bx = 0; bx < entityWidth; bx++) {
            for (int by = 0; by < entityHeight; by++) {
                for (int bz = 0; bz < entityDepth; bz++) {
                    if (!canSwim(x + bx, y + by, z + bz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean canSwim(int x, int y, int z) {
        return mob.canSwimInFluidType(currentContext.level().getFluidState(MUTABLE.set(x, y, z)).getFluidType());
    }

    @Nullable
    protected ModedNode getJumpNode(Node start, int x, int z, double floor, boolean canSprint) {
        double y = floor + getMobJumpHeight();
        ModedNode node = tryFindFirstGroundNode(x, z, y - mob.getMaxFallDistance(), y, maxStep, false, MovementMode.JUMP);
        if (isNeighborValid(node, start) && canJump(start, node, floor, canSprint)) {
            return node;
        }
        return null;
    }

    @Nullable
    protected ModedNode getWalkNode(Node start, int x, int z, double floor, MovementMode mode) {
        ModedNode node = tryFindFirstGroundNode(x, z, floor - mob.getMaxFallDistance(), floor, maxStep, true, mode);
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
    protected ModedNode getUpNode(Node start, double floor, MovementMode mode) {
        double maxY = Math.min(floor + getMobJumpHeight(), start.y + entityHeight);
        ModedNode node = tryFindFirstGroundNode(start.x, start.z, floor, maxY, maxStep, true, mode);
        if (isNeighborValid(node, start)) {
            return node;
        }
        return null;
    }

    @Nullable
    protected ModedNode getDownNode(Node start, double floor, MovementMode mode) {
        ModedNode node = tryFindFirstGroundNode(start.x, start.z, mob.level().getMinBuildHeight(), floor, 0, true, mode);
        if (isNeighborValid(node, start)) {
            return node;
        }
        return null;
    }

    @Override
    protected double getFloorLevel(BlockPos pos) {
        BlockGetter world = currentContext.level();
        if (mob.canSwimInFluidType(world.getFluidState(pos).getFluidType())) {
            return pos.getY();
        }
        return getFloorLevel(world, pos);
    }

    @Nullable
    protected ModedNode tryFindFirstGroundNode(int x, int z, double minY, double maxY, double step, boolean stopOnFirst, MovementMode mode) {
        int min = Math.max(mob.level().getMinBuildHeight(), Mth.floor(minY));
        for (int i = Math.min(mob.level().getMaxBuildHeight() - 1, Mth.ceil(maxY + step)); i >= min; i--) {
            double floor = getFloorLevel(MUTABLE.set(x, i, z));
            if (floor >= maxY + step) {
                continue;
            }
            if (floor < minY) {
                break;
            }
            PathType path = getCachedPathType(x, i, z);
            float malus = mob.getPathfindingMalus(path);
            if (path != PathType.OPEN) {
                if (malus >= 0) {
                    return getNodeAndUpdateCostToMax(x, i, z, mode, path, malus);
                } else if (stopOnFirst && floor < maxY) {
                    return getBlockedNode(x, i, z, mode);
                }
            }
        }
        return null;
    }

    @Override
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
                    if (!currentContext.level().getBlockState(MUTABLE).getCollisionShape(currentContext.level(), MUTABLE).isEmpty()) {
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

    protected int addFluidNodes(Node[] arr, Node start, int i, double fluidHeight) {
        // jump arcs out of water surface
        for (int x = start.x - fluidJumpWidth; x <= start.x + fluidJumpWidth; x++) {
            for (int z = start.z - fluidJumpWidth; z <= start.z + fluidJumpWidth; z++) {
                if (x != start.x || z != start.z) {
                    ModedNode node = tryFindFirstGroundNode(x, z, fluidHeight - mob.getMaxFallDistance() + fluidJumpHeight, fluidHeight + fluidJumpHeight, maxStep, false, MovementMode.JUMP);
                    if (isNeighborValid(node, start) && canJump(start, node, fluidHeight, false)) {
                        arr[i++] = node;
                    }
                }
            }
        }
        return i;
    }

    private boolean canChangeModes(int x, int y, int z, MovementMode mode) {
        if (mode == MovementMode.SWIM) {
            if (!isSubmerged(x, y, z)) {
                return false;
            }
            return mob.getPathfindingMalus(getCachedSwimPathType(x, y, z)) >= 0;
        }
        return mob.getPathfindingMalus(getCachedPathType(x, y, z)) >= 0;
    }

    protected int addJumps(Node[] arr, Node start, double floor, int i, boolean canSprint) {
        for (int x = -jumpWidth; x <= jumpWidth; x++) {
            for (int z = -jumpWidth; z <= jumpWidth; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                ModedNode node = getJumpNode(start, start.x + x, start.z + z, floor, canSprint);
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

    protected boolean canJumpIn(PathType type) {
        return type != PathType.STICKY_HONEY;
    }

    protected boolean canJumpOn(PathType type) {
        return type == PathType.BLOCKED || type == PathType.FENCE || type == PathType.LEAVES;
    }

    protected int addHorizontal(Node[] arr, Node start, double floor, int i, MovementMode mode) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            ModedNode node = getWalkNode(start, start.x + direction.getStepX(), start.z + direction.getStepZ(), floor, mode);
            if (node != null) {
                arr[i++] = node;
            }
            CACHE[direction.get2DDataValue()] = node;
        }
        for (Direction dir1 : Direction.Plane.HORIZONTAL) {
            Direction dir2 = dir1.getClockWise();
            if (isDiagonalValid(start, CACHE[dir1.get2DDataValue()], CACHE[dir2.get2DDataValue()])) {
                ModedNode node = getWalkNode(start, start.x + dir1.getStepX() + dir2.getStepX(), start.z + dir1.getStepZ() + dir2.getStepZ(), floor, mode);
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
        if (path == PathType.OPEN && y >= context.level().getMinBuildHeight() + 1) {
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
        MovementMode startMode = ModedNode.modeOf(start);
        int i = switch (startMode) {
            case WALK -> addWalkNeighbors(arr, start);
            case SWIM -> addSwimNeighbors(arr, start);
            case JUMP -> 0; // jump always transitions to walk first
        };
        i = addTransitionNeighbors(arr, start, startMode, i);
        return i;
    }

    private int addWalkNeighbors(Node[] arr, Node start) {
        int i = 0;
        BlockPos pos = new BlockPos(start.x, start.y, start.z);
        double floor = getFloorLevel(pos);
        PathType type = getCachedPathType(start.x, start.y, start.z);
        PathType downType = getCachedPathType(start.x, start.y - 1, start.z);
        FluidState fluid = currentContext.level().getFluidState(pos);
        double fluidHeight = fluid.getHeight(currentContext.level(), pos);
        boolean canJump = canJumpIn(type) && canJumpOn(downType) && fluidHeight <= mob.getFluidJumpThreshold();
        ModedNode down = getDownNode(start, floor, MovementMode.WALK);
        if (down != null) {
            arr[i++] = down;
        }
        i = addHorizontal(arr, start, floor, i, MovementMode.WALK);
        if (canJump) {
            if (getMobJumpHeight() >= 1) {
                ModedNode up = getUpNode(start, floor, MovementMode.WALK);
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

    private int addSwimNeighbors(Node[] arr, Node start) {
        int i = 0;
        ModedNode down = getSwimNode(start.x, start.y - 1, start.z);
        ModedNode up = getSwimNode(start.x, start.y + 1, start.z);
        if (isNeighborValid(down, start)) {
            arr[i++] = down;
        }
        if (isNeighborValid(up, start)) {
            arr[i++] = up;
        }
        boolean[] gates = {down != null, true, up != null};
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int idx = d.get2DDataValue();
            int nx = start.x + d.getStepX();
            int nz = start.z + d.getStepZ();
            for (int dy = -1; dy <= 1; dy++) {
                ModedNode node = getSwimNode(nx, start.y + dy, nz);
                SWIM_LEVEL_CACHE[dy + 1][idx] = node;
                if (gates[dy + 1] && isNeighborValid(node, start)) {
                    arr[i++] = node;
                }
            }
        }
        for (Direction dir1 : Direction.Plane.HORIZONTAL) {
            Direction dir2 = dir1.getClockWise();
            int idx1 = dir1.get2DDataValue();
            int idx2 = dir2.get2DDataValue();
            int dx = dir1.getStepX() + dir2.getStepX();
            int dz = dir1.getStepZ() + dir2.getStepZ();
            for (int dy = -1; dy <= 1; dy++) {
                if (gates[dy + 1]
                        && SWIM_LEVEL_CACHE[dy + 1][idx1] != null
                        && SWIM_LEVEL_CACHE[dy + 1][idx2] != null) {
                    ModedNode diag = getSwimNode(start.x + dx, start.y + dy, start.z + dz);
                    if (isNeighborValid(diag, start)) {
                        arr[i++] = diag;
                    }
                }
            }
        }
        return i;
    }

    @Nullable
    private ModedNode getSwimNode(int x, int y, int z) {
        if (!canSwim(x, y, z)) {
            return null;
        }
        PathType path = getCachedSwimPathType(x, y, z);
        float malus = mob.getPathfindingMalus(path);
        if (malus < 0) {
            return null;
        }
        return getNodeAndUpdateCostToMax(x, y, z, MovementMode.SWIM, path, malus);
    }

    // in-place transition to other modes
    private int addTransitionNeighbors(Node[] arr, Node start, MovementMode from, int i) {
        for (ModeTransition t : ModeTransition.fromMode(from)) {
            if (!canChangeModes(start.x, start.y, start.z, t.to)) {
                continue;
            }
            PathType path = t.to == MovementMode.SWIM
                    ? getCachedSwimPathType(start.x, start.y, start.z)
                    : getCachedPathType(start.x, start.y, start.z);
            float malus = mob.getPathfindingMalus(path);
            if (malus < 0) {
                continue;
            }
            ModedNode node = getNodeAndUpdateCostToMax(start.x, start.y, start.z, t.to, path, malus);
            if (isNeighborValid(node, start)) {
                arr[i++] = node;
            }
        }
        return i;
    }
}
