package hive.common.world;

import java.util.Optional;

public final class Physics {

    private Physics() {
    }

    public static Optional<Double> getLandingTime(double gravity, double distance, double dY) {
        double disc = dY * dY + 2 * gravity * distance;
        if (Double.isNaN(disc) || disc < 0 || gravity == 0) {
            return Optional.empty();
        }
        double sqrt = Math.sqrt(disc);
        double max = Math.max((sqrt - dY) / gravity, (-sqrt - dY) / gravity);
        if (max <= 0) {
            return Optional.empty();
        }
        return Optional.of(max);
    }

    public static double getHeight(double gravity, double dY) {
        if (gravity == 0) {
            return 0;
        }
        return dY * dY / gravity / -2;
    }

    public static double getMaxHeight(double gravity, double dY, double t1, double t2) {
        if (gravity == 0) {
            return 0;
        }
        double y1 = gravity * t1 * t1 / 2 + dY * t1;
        double y2 = gravity * t2 * t2 / 2 + dY * t2;
        double max = Math.max(y1, y2);
        if (gravity < 0) {
            double t3 = dY / gravity / -2;
            if (t1 < t3 && t2 > t3) {
                return Math.max(max, gravity * t3 * t3 / 2 + dY * t3);
            }
        }
        return max;
    }

    public static double getMinHeight(double gravity, double dY, double t1, double t2) {
        if (gravity == 0) {
            return 0;
        }
        double y1 = gravity * t1 * t1 / 2 + dY * t1;
        double y2 = gravity * t2 * t2 / 2 + dY * t2;
        double min = Math.min(y1, y2);
        if (gravity > 0) {
            double t3 = dY / gravity / -2;
            if (t1 < t3 && t2 > t3) {
                return Math.min(min, gravity * t3 * t3 / 2 + dY * t3);
            }
        }
        return min;
    }

    // mirrors LivingEntity.travelInFluid friction selection
    public static double getFluidFriction(boolean swimming, float waterSlowDown, boolean dolphinsGrace, double waterEfficiency, boolean onGround) {
        if (dolphinsGrace) {
            return Constants.DOLPHINS_GRACE_FRICTION;
        }
        double slow = swimming ? Constants.SWIM_SPRINT_FRICTION : waterSlowDown;
        double eff = onGround ? waterEfficiency : waterEfficiency * 0.5;
        if (eff > 0) {
            slow += (Constants.WATER_EFFICIENCY_TARGET_FRICTION - slow) * eff;
        }
        return slow;
    }

    public static double getFluidTerminalSpeed(double friction) {
        if (friction <= 0 || friction >= 1) {
            return 0;
        }
        return Constants.WATER_ACCEL * friction / (1 - friction);
    }

    public static final class Constants {

        // invulnerable ticks after being hurt, mirrors LivingEntity.hurtServer
        public static final int INVULNERABLE_TICKS = 10;
        // entity jump power defined in LivingEntity.getJumpPower
        public static final float JUMP_POWER = 0.42f;
        // constant in LivingEntity.travelInFluid water branch.
        public static final float WATER_ACCEL = 0.02f;
        // horizontal friction multiplier when sprinting in water
        // constant in LivingEntity.travelInFluid.
        public static final float SWIM_SPRINT_FRICTION = 0.9f;
        // friction override when DOLPHINS_GRACE is active in LivingEntity.travelInFluid
        public static final float DOLPHINS_GRACE_FRICTION = 0.96f;
        // target friction in WATER_MOVEMENT_EFFICIENCY mix in LivingEntity.travelInFluid
        public static final float WATER_EFFICIENCY_TARGET_FRICTION = 0.54600006f;
        // constant in Entity.travelInAir as always applied friction
        public static final float AIR_FRICTION = 0.91f;
        // ground-walk accel coefficient inside LivingEntity.getFrictionInfluencedSpeed
        public static final float GROUND_ACCEL_K = 0.21600002f;
        // queried per-tick via Block.getFriction() in Entity.travelInAir. Uses the default
        public static final float DEFAULT_BLOCK_FRICTION = 0.6f;
        // sprinting move speed multiplier
        public static final float SPRINT_MULTIPLIER = 1.3f;
        // horizontal boost added in the facing direction by LivingEntity.jumpFromGround when sprinting
        public static final double JUMP_BOOST = 0.2;
        // airborne speed scaler used in place of vanilla's flat getFlyingSpeed
        // player returns 0.02 walking, 0.026 sprinting, multiplying MOVEMENT_SPEED by this scaler reproduces those player numbers automatically
        public static final float FLY_MULTIPLIER = 0.2f;
        // both ground and air friction applied
        public static final float GROUND_FRICTION = DEFAULT_BLOCK_FRICTION * AIR_FRICTION;
        public static final float GROUND_ACCEL_PER_MOVE_SPEED = GROUND_ACCEL_K / (DEFAULT_BLOCK_FRICTION * DEFAULT_BLOCK_FRICTION * DEFAULT_BLOCK_FRICTION);

        // derived ground terminal speed multiplier (per unit of MOVEMENT_SPEED)
        public static final float GROUND_WALK_SPEED_MULTIPLIER = GROUND_ACCEL_PER_MOVE_SPEED * GROUND_FRICTION / (1 - GROUND_FRICTION);

        private Constants() {
        }

    }

}
