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

}
