package hive.registration;

import hive.common.Hive;
import hive.common.world.entities.DroidEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

@EventBusSubscriber(modid = Hive.ID)
public final class EntityTypeRegistry {

    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, Hive.ID);

    public static final DeferredHolder<EntityType<?>, EntityType<DroidEntity>> DROID = register("droid", MobCategory.MONSTER, DroidEntity::new, type -> type.sized(0.6f, 1.8f).eyeHeight(1.62f));

    private EntityTypeRegistry() {
    }

    private static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(String name, MobCategory category, EntityType.EntityFactory<T> factory, UnaryOperator<EntityType.Builder<T>> type) {
        return TYPES.register(name, () -> type.apply(EntityType.Builder.of(factory, category)).build(name));
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(DROID.get(), DroidEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(DROID.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.AND);
    }

}
