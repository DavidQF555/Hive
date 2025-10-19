package hive.common.world.packets;

import hive.common.Hive;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = Hive.ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PacketRegistry {

    private static int index = 0;

    private PacketRegistry() {
    }

    @SubscribeEvent
    public static void onFMLCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> DebugPathEffectPacket.register(index++));
    }

}
