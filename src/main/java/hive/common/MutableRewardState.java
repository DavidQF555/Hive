package hive.common;

import hive.common.rl.RewardState;

public class MutableRewardState {

    private double damage;
    private int kills;

    public void addDamage(double damage) {
        this.damage += damage;
    }

    public void addKills(int kills) {
        this.kills += kills;
    }

    public void reset() {
        damage = 0;
        kills = 0;
    }

    public RewardState immutable() {
        return new RewardState(damage, kills);
    }

}
