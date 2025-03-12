package hive.client;

import hive.client.render.DroidRenderer;
import hive.common.Hive;
import hive.registration.EntityTypeRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = Hive.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEventBusSubscriber {

    private ClientEventBusSubscriber() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityTypeRegistry.DROID.get(), DroidRenderer::new);
    }

}
