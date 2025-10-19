package hive.registration;

import hive.common.Hive;
import hive.common.world.entities.DroidEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.UnaryOperator;

@Mod.EventBusSubscriber(modid = Hive.ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EntityTypeRegistry {

    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, Hive.ID);

    public static final RegistryObject<EntityType<DroidEntity>> DROID = register("droid", MobCategory.MONSTER, DroidEntity::new, type -> type
            .sized(0.6f, 1.8f)
            .clientTrackingRange(8)
    );

    private EntityTypeRegistry() {
    }

    private static <T extends Entity> RegistryObject<EntityType<T>> register(String name, MobCategory category, EntityType.EntityFactory<T> factory, UnaryOperator<EntityType.Builder<T>> type) {
        return TYPES.register(name, () -> type.apply(EntityType.Builder.of(factory, category)).build(name));
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(DROID.get(), DroidEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onRegisterSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(DROID.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.AND);
    }

}
