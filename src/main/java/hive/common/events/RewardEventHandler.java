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
        Entity source = event.getSource().getEntity();
        if (source instanceof DroidEntity && !source.level().isClientSide()) {
            HiveMind hive = HiveMind.getOrCreate(source.getServer());
            hive.addReward(new RewardState(event.getNewDamage(), 0));
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity source = event.getSource().getEntity();
        if (source instanceof DroidEntity && !source.level().isClientSide()) {
            HiveMind hive = HiveMind.getOrCreate(source.getServer());
            hive.addReward(new RewardState(0, 1));
        }
    }

}
