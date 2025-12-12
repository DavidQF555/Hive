package hive.common;

import hive.common.rl.RewardState;

public class MutableRewardState {

    private double damageDealt, damageTaken;
    private int kills, deaths;

    public void addDamageDealt(double damage) {
        this.damageDealt += damage;
    }

    public void addKills(int kills) {
        this.kills += kills;
    }

    public void addDamageTaken(double damage) {
        this.damageTaken += damage;
    }

    public void addDeaths(int deaths) {
        this.deaths += deaths;
    }

    public void reset() {
        damageDealt = 0;
        damageTaken = 0;
        kills = 0;
        deaths = 0;
    }

    public RewardState immutable() {
        return new RewardState(damageDealt, kills, deaths, damageTaken);
    }

}
