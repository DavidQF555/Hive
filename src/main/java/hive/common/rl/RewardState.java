package hive.common.rl;

public record RewardState(
        double damageDealt,
        int kills,
        int deaths,
        double damageTaken
) {
}
