package hive.common.world.entities.ai;

import hive.common.world.Physics;
import hive.common.world.entities.DroidEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class DroidMeleeAttackGoal extends Goal {

    // damping factor on the smoothed target velocity for predictions
    private static final double PREDICTION_DAMPING = 0.5;
    // iteration count for the calculating the estimate
    private static final int PREDICTION_ITERATIONS = 3;
    // upper bound on how far ahead of the target the predicted intercept may be placed
    private static final double PREDICTION_MAX_DISTANCE = 8;
    // max blocks to scan downward from the extrapolated pos when finding ground for the intercept
    private static final int SNAP_DOWN_BLOCKS = 4;
    // buffer size for target position samples
    private static final int VELOCITY_SAMPLE_WINDOW = 3;
    // minimum samples needed before getSmoothedVelocity returns a non-zero estimate
    private static final int MIN_VELOCITY_SAMPLES = 2;
    // ticks before re-running pathfinding, same as MeleeAttackGoal
    private static final int REPATH_DELAY_BASE = 4;
    private static final int REPATH_DELAY_RANDOM = 7;
    // ticks per second to convert the ATTACK_SPEED attribute into a tick cooldown
    private static final int TICKS_PER_SECOND = 20;
    private final DroidEntity mob;
    private final double speedModifier;
    private final boolean followTargetEvenIfNotSeen;
    private final Vec3[] positionHistory = new Vec3[VELOCITY_SAMPLE_WINDOW];
    private int historyCount;
    private int historyIndex;
    private int ticksUntilNextPathRecalculation;

    public DroidMeleeAttackGoal(DroidEntity mob, double speedModifier, boolean followTargetEvenIfNotSeen) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.followTargetEvenIfNotSeen = followTargetEvenIfNotSeen;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        return mob.isWithinRestriction(target.blockPosition());
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player player && (player.isSpectator() || player.isCreative())) {
            return false;
        }
        if (!followTargetEvenIfNotSeen) {
            return !mob.getNavigation().isDone();
        }
        return mob.isWithinRestriction(target.blockPosition());
    }

    @Override
    public void start() {
        mob.setAggressive(true);
        ticksUntilNextPathRecalculation = 0;
        historyCount = 0;
        historyIndex = 0;
    }

    @Override
    public void stop() {
        LivingEntity target = mob.getTarget();
        if (target instanceof Player player && (player.isSpectator() || player.isCreative())) {
            mob.setTarget(null);
        }
        mob.setAggressive(false);
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }

        recordTargetPosition(target);

        mob.getLookControl().setLookAt(target, 30, 30);

        long now = mob.level().getGameTime();
        if (now >= mob.getNextAttackTick() && target.invulnerableTime <= Physics.Constants.INVULNERABLE_TICKS && mob.isWithinMeleeAttackRange(target) && mob.getSensing().hasLineOfSight(target)) {
            mob.setNextAttackTick(now + getAttackCooldownTicks());
            mob.swing(InteractionHand.MAIN_HAND);
            if (mob.level() instanceof ServerLevel serverLevel) {
                mob.doHurtTarget(serverLevel, target);
            }
        }

        if (--ticksUntilNextPathRecalculation <= 0) {
            ticksUntilNextPathRecalculation = REPATH_DELAY_BASE + mob.getRandom().nextInt(REPATH_DELAY_RANDOM);
            if (!mob.isPassenger()) {
                Vec3 predicted = findSafeIntercept(target);
                mob.getNavigation().moveTo(predicted.x(), predicted.y(), predicted.z(), speedModifier);
            }
        }
    }

    private void recordTargetPosition(LivingEntity target) {
        positionHistory[historyIndex] = target.position();
        historyIndex = (historyIndex + 1) % VELOCITY_SAMPLE_WINDOW;
        if (historyCount < VELOCITY_SAMPLE_WINDOW) historyCount++;
    }

    private Vec3 getSmoothedVelocity() {
        if (historyCount < MIN_VELOCITY_SAMPLES) {
            return Vec3.ZERO;
        }
        int newest = (historyIndex - 1 + VELOCITY_SAMPLE_WINDOW) % VELOCITY_SAMPLE_WINDOW;
        int oldest = (historyIndex - historyCount + VELOCITY_SAMPLE_WINDOW) % VELOCITY_SAMPLE_WINDOW;
        return positionHistory[newest].subtract(positionHistory[oldest]).scale(1.0 / (historyCount - 1));
    }

    private int getAttackCooldownTicks() {
        double speed = mob.getAttributeValue(Attributes.ATTACK_SPEED);
        return speed > 0 ? (int) Math.ceil((double) TICKS_PER_SECOND / speed) : TICKS_PER_SECOND;
    }

    private Vec3 findSafeIntercept(LivingEntity target) {
        Vec3 targetPos = target.position();
        Vec3 raw = predictFinalIntercept(target);

        Vec3 diff = raw.subtract(targetPos);
        if (diff.lengthSqr() > PREDICTION_MAX_DISTANCE * PREDICTION_MAX_DISTANCE) {
            diff = diff.normalize().scale(PREDICTION_MAX_DISTANCE);
        }

        BlockPos.MutableBlockPos lastSafe = target.blockPosition().mutable();
        Level level = mob.level();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int steps = Math.max(1, (int) Math.ceil(diff.length()));

        // default attributes to 0 when not present for target
        AttributeInstance jumpAttr = target.getAttribute(Attributes.JUMP_STRENGTH);
        AttributeInstance gravityAttr = target.getAttribute(Attributes.GRAVITY);
        double jumpYSpeed = (jumpAttr != null ? jumpAttr.getValue() : 0) + target.getJumpBoostPower();
        double gravity = gravityAttr != null ? gravityAttr.getValue() : 0;
        int jumpHeight = Math.max(1, (int) Math.floor(Physics.getHeight(-gravity, jumpYSpeed)));
        int prevX = 0;
        int prevZ = 0;
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            BlockPos extrapolated = BlockPos.containing(targetPos.add(diff.scale(t)));
            if (i != 1 && extrapolated.getX() == prevX && extrapolated.getZ() == prevZ) {
                continue;
            }
            prevX = extrapolated.getX();
            prevZ = extrapolated.getZ();
            if (!mob.level().getFluidState(extrapolated).isEmpty()) {
                lastSafe.set(extrapolated);
            } else {
                int y = snapY(extrapolated.getX(), lastSafe.getY() + jumpHeight, extrapolated.getZ(), level, cursor);
                lastSafe.set(extrapolated.getX(), y, extrapolated.getZ());
            }
        }
        return Vec3.atBottomCenterOf(lastSafe);
    }

    private Vec3 predictFinalIntercept(LivingEntity target) {
        Vec3 mobPos = mob.position();
        Vec3 targetPos = target.position();
        Vec3 targetVel = getSmoothedVelocity();
        double mobSpeed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * speedModifier;

        if (mobSpeed <= 0) {
            return targetPos;
        }

        Vec3 dampedVel = new Vec3(
                targetVel.x() * PREDICTION_DAMPING,
                targetVel.y() * PREDICTION_DAMPING,
                targetVel.z() * PREDICTION_DAMPING
        );

        Vec3 estimated = targetPos;
        for (int i = 0; i < PREDICTION_ITERATIONS; i++) {
            double travelTicks = estimated.distanceTo(mobPos) / mobSpeed;
            estimated = new Vec3(
                    targetPos.x() + dampedVel.x() * travelTicks,
                    targetPos.y() + dampedVel.y() * travelTicks,
                    targetPos.z() + dampedVel.z() * travelTicks
            );
        }
        return estimated;
    }

    private int snapY(int x, int fromY, int z, Level level, BlockPos.MutableBlockPos cursor) {
        for (int i = 0; i <= SNAP_DOWN_BLOCKS; i++) {
            int y = fromY - i;
            BlockState below = level.getBlockState(cursor.set(x, y - 1, z));
            if (below.getFluidState().isEmpty()) {
                continue;
            }
            if (!level.getBlockState(cursor.set(x, y, z)).getCollisionShape(level, cursor).isEmpty()) {
                continue;
            }
            return y;
        }
        return fromY;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

}
