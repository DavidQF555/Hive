package hive.common.events;

import hive.common.Hive;
import hive.common.rl.RewardState;
import hive.common.world.HiveMind;
import hive.common.world.entities.DroidEntity;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@EventBusSubscriber(modid = Hive.ID, bus = EventBusSubscriber.Bus.GAME)
public final class RewardEventHandler {

    private RewardEventHandler() {
    }

    @SubscribeEvent
    public static void postLivingDamage(LivingDamageEvent.Post event) {
        if (!event.getEntity().level().isClientSide()) {
            double damageDealt = 0;
            double damageTaken = 0;
            Entity source = event.getSource().getEntity();
            if (source instanceof DroidEntity) {
                damageDealt = event.getNewDamage();
            }
            if (event.getEntity() instanceof DroidEntity) {
                damageTaken = event.getNewDamage();
            }
            if (damageDealt != 0 || damageTaken != 0) {
                HiveMind hive = HiveMind.getOrCreate(event.getEntity().getServer());
                hive.addReward(new RewardState(damageDealt, 0, 0, damageTaken));
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            Entity source = event.getSource().getEntity();
            if (source instanceof DroidEntity) {
                HiveMind hive = HiveMind.getOrCreate(source.getServer());
                hive.addReward(new RewardState(0, 1, 0, 0));
            }
            if (event.getEntity() instanceof DroidEntity) {
                HiveMind hive = HiveMind.getOrCreate(event.getEntity().getServer());
                hive.addReward(new RewardState(0, 0, 1, 0));
            }
        }
    }

}
