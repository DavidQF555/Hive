package hive.common.world.entities.ai;

import hive.common.world.entities.DroidEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class DroidMeleeAttackGoal extends Goal {

    private static final double PREDICTION_DAMPING = 0.5;
    private static final int PREDICTION_ITERATIONS = 3;
    private static final double REPATH_DISTANCE_SQ = 4.0;
    private static final double PREDICTION_MAX_DISTANCE = 16.0;
    private static final int INTERCEPT_SCAN_STEPS = 4;
    private static final int SNAP_DOWN_BLOCKS = 4;
    private static final int VELOCITY_SAMPLE_WINDOW = 3;
    private final DroidEntity mob;
    private final double speedModifier;
    private final boolean followTargetEvenIfNotSeen;
    private final Vec3[] positionHistory = new Vec3[VELOCITY_SAMPLE_WINDOW];
    private int historyCount;
    private int historyIndex;
    private int ticksUntilNextPathRecalculation;
    private int ticksUntilNextAttack;
    private Vec3 lastPathedTargetPos = Vec3.ZERO;

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
        ticksUntilNextAttack = 0;
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
        lastPathedTargetPos = Vec3.ZERO;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }

        recordTargetPosition(target);

        mob.getLookControl().setLookAt(target, 30, 30);
        ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);

        if (ticksUntilNextAttack == 0 && mob.isWithinMeleeAttackRange(target) && mob.getSensing().hasLineOfSight(target)) {
            ticksUntilNextAttack = getAttackCooldownTicks();
            mob.swing(InteractionHand.MAIN_HAND);
            if (mob.level() instanceof ServerLevel serverLevel) {
                mob.doHurtTarget(serverLevel, target);
            }
        }

        boolean drifted = target.position().distanceToSqr(lastPathedTargetPos) > REPATH_DISTANCE_SQ;
        if (--ticksUntilNextPathRecalculation <= 0 || drifted) {
            ticksUntilNextPathRecalculation = 4 + mob.getRandom().nextInt(7);
            if (!mob.isPassenger()) {
                Vec3 predicted = findSafeIntercept(target);
                mob.getNavigation().moveTo(predicted.x(), predicted.y(), predicted.z(), speedModifier);
                lastPathedTargetPos = target.position();
            }
        }
    }

    private void recordTargetPosition(LivingEntity target) {
        positionHistory[historyIndex] = target.position();
        historyIndex = (historyIndex + 1) % VELOCITY_SAMPLE_WINDOW;
        if (historyCount < VELOCITY_SAMPLE_WINDOW) historyCount++;
    }

    private Vec3 getSmoothedVelocity() {
        if (historyCount < 2) {
            return Vec3.ZERO;
        }
        int newest = (historyIndex - 1 + VELOCITY_SAMPLE_WINDOW) % VELOCITY_SAMPLE_WINDOW;
        int oldest = (historyIndex - historyCount + VELOCITY_SAMPLE_WINDOW) % VELOCITY_SAMPLE_WINDOW;
        return positionHistory[newest].subtract(positionHistory[oldest]).scale(1.0 / (historyCount - 1));
    }

    private int getAttackCooldownTicks() {
        double speed = mob.getAttributeValue(Attributes.ATTACK_SPEED);
        return speed > 0 ? (int) Math.ceil(20.0 / speed) : 20;
    }

    private Vec3 findSafeIntercept(LivingEntity target) {
        Vec3 targetPos = target.position();
        Vec3 raw = predictFinalIntercept(target);

        double dx = raw.x() - targetPos.x();
        double dz = raw.z() - targetPos.z();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > PREDICTION_MAX_DISTANCE) {
            double scale = PREDICTION_MAX_DISTANCE / dist;
            raw = new Vec3(targetPos.x() + dx * scale, targetPos.y(), targetPos.z() + dz * scale);
        }

        BlockPos lastSafe = target.blockPosition();
        int baseY = lastSafe.getY();
        Level level = mob.level();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 1; i <= INTERCEPT_SCAN_STEPS; i++) {
            double t = (double) i / INTERCEPT_SCAN_STEPS;
            int ix = Mth.floor(targetPos.x() + (raw.x() - targetPos.x()) * t);
            int iz = Mth.floor(targetPos.z() + (raw.z() - targetPos.z()) * t);
            int iy = snapY(ix, baseY, iz, level, cursor);
            if (iy == Integer.MIN_VALUE) {
                break;
            }
            lastSafe = new BlockPos(ix, iy, iz);
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
                0,
                targetVel.z() * PREDICTION_DAMPING
        );

        Vec3 estimated = targetPos;
        for (int i = 0; i < PREDICTION_ITERATIONS; i++) {
            double dx = estimated.x() - mobPos.x();
            double dz = estimated.z() - mobPos.z();
            double travelTicks = Math.sqrt(dx * dx + dz * dz) / mobSpeed;
            estimated = new Vec3(
                    targetPos.x() + dampedVel.x() * travelTicks,
                    targetPos.y(),
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
