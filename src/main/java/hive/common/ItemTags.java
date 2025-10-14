package hive.common;

import hive.common.world.entities.DroidEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;

import java.util.EnumMap;
import java.util.Map;

public final class ItemTags {

    public static final Map<EquipmentSlot, TagKey<Item>> DROID_EQUIPMENT = new EnumMap<>(EquipmentSlot.class);

    static {
        for (EquipmentSlot slot : DroidEntity.EQUIPMENT_POPULATION_ORDER) {
            String key = "droid/" + slot.getName();
            DROID_EQUIPMENT.put(slot, TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Hive.ID, key)));
        }
    }

    private ItemTags() {
    }

}
