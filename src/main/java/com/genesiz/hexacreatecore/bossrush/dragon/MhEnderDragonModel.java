package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.HexaCreateCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public final class MhEnderDragonModel<T extends EnderDragon & IMhEnderDragon> extends GeoModel<T> {

  private static final ResourceLocation MODEL_LOC =
    ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "geo/entity/extreme_ender_dragon.geo.json");
  private static final ResourceLocation TEXTURE_LOC =
    ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "textures/entity/dragon.png");
  private static final ResourceLocation ANIM_LOC =
    ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "animations/entity/extreme_ender_dragon.animation.json");

  @Override
  public ResourceLocation getModelResource(T animatable) {
    return MODEL_LOC;
  }

  @Override
  public ResourceLocation getTextureResource(T animatable) {
    return TEXTURE_LOC;
  }

  @Override
  public ResourceLocation getAnimationResource(T animatable) {
    return ANIM_LOC;
  }

  @Override
  public void setCustomAnimations(T dragon, long instanceId, AnimationState<T> animationState) {
    super.setCustomAnimations(dragon, instanceId, animationState);
    DragonProceduralAnimator.applyProceduralRig(this, dragon, dragon, animationState.getPartialTick());
  }

  public software.bernie.geckolib.cache.object.GeoBone getLiveBone(String name) {
    software.bernie.geckolib.cache.object.GeoBone bone = getAnimationProcessor().getBone(name);
    if (bone != null) return bone;
    var baked = getBakedModel(MODEL_LOC);
    return baked != null ? baked.getBone(name).orElse(null) : null;
  }
}
