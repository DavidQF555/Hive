package hive.common.events;

import hive.common.Hive;
import hive.common.ServerConfigs;
import hive.common.world.HiveMind;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Hive.ID, bus = EventBusSubscriber.Bus.GAME)
public final class TrainingEventHandler {

    private TrainingEventHandler() {
    }

    @SubscribeEvent
    public static void postServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getLevel(Level.OVERWORLD).getGameTime() % ServerConfigs.INSTANCE.trainingPeriod.get() == 0) {
            HiveMind.getOrCreate(event.getServer()).consume();
        }
    }

}
