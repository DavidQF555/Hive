package hive.common.world.packets;

import hive.common.Hive;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Hive.ID, bus = EventBusSubscriber.Bus.MOD)
public final class PacketRegistry {

    public static final CustomPacketPayload.Type<DebugPathEffectPacket> DEBUG_PATH = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Hive.ID, "debug_path"));

    private PacketRegistry() {
    }

    @SubscribeEvent
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(DEBUG_PATH, DebugPathEffectPacket.CODEC, DebugPathEffectPacket::handle);
    }

}
