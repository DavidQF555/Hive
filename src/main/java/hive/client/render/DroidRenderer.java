package hive.client.render;

import hive.common.Hive;
import hive.common.world.entities.DroidEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.ResourceLocation;

public class DroidRenderer extends HumanoidMobRenderer<DroidEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Hive.ID, "textures/entity/droid.png");

    public DroidRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
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
