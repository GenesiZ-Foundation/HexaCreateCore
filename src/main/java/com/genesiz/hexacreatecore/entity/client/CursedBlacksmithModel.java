package com.genesiz.hexacreatecore.entity.client;

import net.minecraft.resources.ResourceLocation;
import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.entity.custom.CursedBlacksmithEntity;

import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class CursedBlacksmithModel extends GeoModel<CursedBlacksmithEntity>{

  @Override
  public ResourceLocation getAnimationResource(CursedBlacksmithEntity model) {
    return ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "animations/entity/cursed_blacksmith.animation.json");
  }

  @Override
  public ResourceLocation getModelResource(CursedBlacksmithEntity model) {
    return ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "geo/entity/cursed_blacksmith.geo.json");
  }

  @Override
  public ResourceLocation getTextureResource(CursedBlacksmithEntity model) {
    return ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "textures/entity/cursed_blacksmith.png");
  }

  @Override
  public void setCustomAnimations(CursedBlacksmithEntity animatable, long instanceId, AnimationState<CursedBlacksmithEntity> animationState) {
    super.setCustomAnimations(animatable, instanceId, animationState);
    GeoBone head = getAnimationProcessor().getBone("head");

    if (head != null) {
      EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);

      float pitch = entityData.headPitch() * ((float) Math.PI / 180F);
      float yaw = entityData.netHeadYaw() * ((float) Math.PI / 180F);

      // Optional: Clamp the head yaw to roughly 75 degrees max left/right 
      // so the neck doesn't realistically snap backwards
      yaw = net.minecraft.util.Mth.clamp(yaw, -1.3f, 1.3f);

      head.setRotX(pitch);
      head.setRotY(yaw);
    }
  }
}
