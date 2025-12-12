package hive.common.world;

import hive.common.Hive;
import hive.common.MutableRewardState;
import hive.common.rl.DecisionModel;
import hive.common.rl.DecisionModelInput;
import hive.common.rl.DecisionState;
import hive.common.rl.RewardState;
import hive.common.rl.TrainingData;
import hive.common.world.entities.DroidEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class HiveMind extends SavedData {

    private static final String NAME = Hive.ID + "_HiveMind";
    private final DecisionModel model = new DecisionModel();
    private final List<TrainingData> training = new ArrayList<>();
    private final MutableRewardState reward = new MutableRewardState();

    public HiveMind() {
    }

    public HiveMind(CompoundTag tag, HolderLookup.Provider lookup) {
        if (tag.contains("Model", Tag.TAG_COMPOUND)) {
            model.deserializeNBT(lookup, tag.getCompound("Model"));
        }
    }

    public static HiveMind getOrCreate(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(new Factory<>(HiveMind::new, HiveMind::new), NAME);
    }

    public TrainingData evaluate(ServerLevel world, DroidEntity entity) {
        double health = entity.getHealth();
        double armor = entity.getArmorValue();
        double damage = entity.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double range = entity.getAttributeValue(Attributes.FOLLOW_RANGE);
        AABB bounds = AABB.ofSize(entity.position(), range * 2, range * 2, range * 2);
        List<DroidEntity> droids = world.getEntitiesOfClass(DroidEntity.class, bounds);
        int nearbyDroids = droids.size();
        double droidHealth = droids.stream()
                .mapToDouble(LivingEntity::getHealth)
                .sum();
        double avgDroidDamage = droids.stream()
                .mapToDouble(e -> e.getAttributeValue(Attributes.ATTACK_DAMAGE))
                .average()
                .orElse(0);
        double avgDroidArmor = droids.stream()
                .mapToDouble(e -> e.getAttributeValue(Attributes.ARMOR))
                .average()
                .orElse(0);
        TargetingConditions conditions = TargetingConditions.forCombat().ignoreLineOfSight();
        List<Player> players = world.getNearbyPlayers(conditions, entity, bounds);
        int nearbyPlayers = players.size();
        double playerHealth = players.stream()
                .mapToDouble(LivingEntity::getHealth)
                .sum();
        double minPlayerHealth = players.stream()
                .mapToDouble(LivingEntity::getHealth)
                .min()
                .orElse(0);
        double avgPlayerArmor = players.stream()
                .mapToDouble(e -> e.getAttributeValue(Attributes.ARMOR))
                .average()
                .orElse(0);
        DecisionModelInput input = new DecisionModelInput(
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
        );
        DecisionState decision = evaluate(input);
        return new TrainingData(input, decision);
    }

    protected DecisionState evaluate(DecisionModelInput input) {
        return model.evaluate(input);
    }

    public void addTrainingData(DecisionModelInput input, DecisionState decision) {
        training.add(new TrainingData(input, decision));
    }

    public void addReward(RewardState reward) {
        this.reward.addDamageDealt(reward.damageDealt());
        this.reward.addKills(reward.kills());
        this.reward.addDamageTaken(reward.damageTaken());
        this.reward.addDeaths(reward.deaths());
    }

    public void consume() {
        model.train(training, reward.immutable());
        reward.reset();
        training.clear();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        CompoundTag model = this.model.serializeNBT(lookup);
        tag.put("Model", model);
        return tag;
    }

}
