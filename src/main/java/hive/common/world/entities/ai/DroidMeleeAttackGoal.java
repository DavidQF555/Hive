package hive.common.world.entities.ai;

import hive.common.world.entities.DroidEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class DroidMeleeAttackGoal extends Goal {

    private static final double PREDICTION_DAMPING = 0.5;
    private static final int PREDICTION_ITERATIONS = 3;
    private static final double REPATH_DISTANCE_SQ = 4.0;
    private static final double PREDICTION_MAX_DISTANCE = 16.0;
    private static final int INTERCEPT_SCAN_STEPS = 4;
    private static final int SNAP_DOWN_BLOCKS = 4;
    private final DroidEntity mob;
    private final double speedModifier;
    private final boolean followTargetEvenIfNotSeen;
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
        if (target == null || !target.isAlive()) return false;
        return mob.isWithinRestriction(target.blockPosition());
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (target instanceof Player player && (player.isSpectator() || player.isCreative())) return false;
        if (!followTargetEvenIfNotSeen) return !mob.getNavigation().isDone();
        return mob.isWithinRestriction(target.blockPosition());
    }

    @Override
    public void start() {
        mob.setAggressive(true);
        ticksUntilNextPathRecalculation = 0;
        ticksUntilNextAttack = 0;
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
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);

        if (ticksUntilNextAttack == 0 && mob.isWithinMeleeAttackRange(target) && mob.getSensing().hasLineOfSight(target)) {
            ticksUntilNextAttack = 20;
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

        Vec3 lastSafe = targetPos;
        for (int i = 1; i <= INTERCEPT_SCAN_STEPS; i++) {
            double t = (double) i / INTERCEPT_SCAN_STEPS;
            double cx = targetPos.x() + (raw.x() - targetPos.x()) * t;
            double cz = targetPos.z() + (raw.z() - targetPos.z()) * t;
            Vec3 candidate = new Vec3(cx, snapY(cx, targetPos.y(), cz), cz);
            if (isSafeIntercept(target, candidate)) {
                lastSafe = candidate;
            } else {
                break;
            }
        }
        return lastSafe;
    }

    private Vec3 predictFinalIntercept(LivingEntity target) {
        Vec3 mobPos = mob.position();
        Vec3 targetPos = target.position();
        Vec3 targetVel = target.getDeltaMovement();
        double mobSpeed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * speedModifier;

        if (mobSpeed <= 0) return targetPos;

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

    private double snapY(double x, double fromY, double z) {
        BlockPos start = BlockPos.containing(x, fromY, z);
        for (int i = 0; i <= SNAP_DOWN_BLOCKS; i++) {
            BlockPos check = start.below(i);
            BlockState state = mob.level().getBlockState(check);
            if (state.blocksMotion()) {
                return check.getY() + 1.0;
            }
        }
        return fromY;
    }

    private boolean isSafeIntercept(LivingEntity target, Vec3 predicted) {
        Vec3 offset = predicted.subtract(target.position());
        AABB predictedBounds = target.getBoundingBox().move(offset);
        return mob.level().noCollision(target, predictedBounds);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

}