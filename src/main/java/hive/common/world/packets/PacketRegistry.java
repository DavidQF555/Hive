package hive.common.world.packets;

import hive.common.Hive;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Hive.ID, bus = EventBusSubscriber.Bus.MOD)
public final class PacketRegistry {

    public static final CustomPacketPayload.Type<PathEffectPacket> PATH = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Hive.ID, "path"));

    private PacketRegistry() {
    }

    @SubscribeEvent
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(PATH, PathEffectPacket.CODEC, PathEffectPacket::handle);
    }

}
