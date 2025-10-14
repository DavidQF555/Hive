package hive.common;

import hive.registration.EntityTypeRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(Hive.ID)
public class Hive {

    public static final String ID = "hive";

    public Hive(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ServerConfigs.SPEC);
        addRegistries(bus);
    }

    private void addRegistries(IEventBus bus) {
        EntityTypeRegistry.TYPES.register(bus);
    }

}
