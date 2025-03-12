package hive.registration;

import hive.common.Hive;
import hive.common.world.entities.DroidEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

@EventBusSubscriber(modid = Hive.ID, bus = EventBusSubscriber.Bus.MOD)
public final class EntityTypeRegistry {

    public static final DeferredRegister.Entities TYPES = DeferredRegister.createEntities(Hive.ID);

    public static final DeferredHolder<EntityType<?>, EntityType<DroidEntity>> DROID = register("droid", MobCategory.MONSTER, DroidEntity::new, type -> type.sized(1, 2));

    private EntityTypeRegistry() {
    }

    private static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(String name, MobCategory category, EntityType.EntityFactory<T> factory, UnaryOperator<EntityType.Builder<T>> type) {
        return TYPES.registerEntityType(name, factory, category, type);
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(DROID.get(), DroidEntity.createAttributes().build());
    }

}
