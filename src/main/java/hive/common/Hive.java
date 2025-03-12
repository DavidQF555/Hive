package hive.common;

import hive.registration.EntityTypeRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Hive.ID)
public class Hive {

    public static final String ID = "hive";

    public Hive(IEventBus bus, ModContainer container) {
        addRegistries(bus);
    }

    private void addRegistries(IEventBus bus) {
        EntityTypeRegistry.TYPES.register(bus);
    }

}
