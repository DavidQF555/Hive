package hive.client.render;

import hive.common.Hive;
import hive.common.world.entities.DroidEntity;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.ResourceLocation;

public class DroidRenderer extends HumanoidMobRenderer<DroidEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Hive.ID, "textures/entity/droid.png");

    public DroidRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        addLayer(
                new HumanoidArmorLayer<>(
                        this,
                        new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                        new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                        context.getEquipmentRenderer()
                )
        );
        addLayer(new CustomHeadLayer<>(this, context.getModelSet()));
        addLayer(new WingsLayer<>(this, context.getModelSet(), context.getEquipmentRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(HumanoidRenderState p_368654_) {
        return TEXTURE;
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

}
