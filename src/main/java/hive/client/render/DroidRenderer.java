package hive.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import hive.common.Hive;
import hive.common.world.entities.DroidEntity;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class DroidRenderer extends HumanoidMobRenderer<DroidEntity, HumanoidModel<DroidEntity>> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Hive.ID, "textures/entity/droid.png");

    public DroidRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        addLayer(
                new HumanoidArmorLayer<>(
                        this,
                        new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                        new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                        context.getModelManager()
                )
        );
        addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        addLayer(new ElytraLayer<>(this, context.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(DroidEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void setupRotations(DroidEntity entity, PoseStack pose, float pAgeInTicks, float pRotationYaw, float pPartialTicks) {
        super.setupRotations(entity, pose, pAgeInTicks, pRotationYaw, pPartialTicks);
        float swimAmount = entity.getSwimAmount(pPartialTicks);
        if (swimAmount > 0) {
            float tilt = Mth.lerp(swimAmount, 0, -90 - entity.getXRot());
            pose.mulPose(Axis.XP.rotationDegrees(tilt));
            if (entity.isVisuallySwimming()) {
                pose.translate(0, -1f, 0.3f);
            }
        }
    }

}
