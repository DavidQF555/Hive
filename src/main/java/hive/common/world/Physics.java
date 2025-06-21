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

    public static double getHeightFromDistance(double gravity, double dY, double dX, double x) {
        if (dX == 0) {
            return 0;
        }
        return gravity * x * x / dX / dX / 2 + dY * x / dX;
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

}
