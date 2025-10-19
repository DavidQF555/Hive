package hive.common;

import hive.registration.EntityTypeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod(Hive.ID)
public class Hive {

    public static final String ID = "hive";
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ID, ID),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public Hive(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, ServerConfigs.SPEC);
        addRegistries(context.getModEventBus());
    }

    private void addRegistries(IEventBus bus) {
        EntityTypeRegistry.TYPES.register(bus);
    }

}
