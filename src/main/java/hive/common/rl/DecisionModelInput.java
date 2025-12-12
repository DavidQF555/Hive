package hive.common.rl;

public record DecisionModelInput(double[] arr) {

    public DecisionModelInput(
            double nearbyDroids,
            double health,
            double armor,
            double damage,
            double droidHealth,
            double avgDroidDamage,
            double avgDroidArmor,
            double nearbyPlayers,
            double playerHealth,
            double minPlayerHealth,
            double avgPlayerArmor
    ) {
        this(
                new double[]{
                        nearbyDroids,
                        health,
                        armor,
                        damage,
                        droidHealth,
                        avgDroidDamage,
                        avgDroidArmor,
                        nearbyPlayers,
                        playerHealth,
                        minPlayerHealth,
                        avgPlayerArmor
                }
        );
    }

}
