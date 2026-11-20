package com.genesiz.hexacreatecore.bossrush.dragon;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class MhEnderDragonRenderer<T extends EnderDragon & IMhEnderDragon> extends GeoEntityRenderer<T> {

  public MhEnderDragonRenderer(EntityRendererProvider.Context renderManager) {
    super(renderManager, new MhEnderDragonModel<>());
    this.shadowRadius = 2.5f;
  }

  @SuppressWarnings("unchecked")
  public static EntityRenderer<EnderDragon> create(EntityRendererProvider.Context ctx) {
    return (EntityRenderer<EnderDragon>) (EntityRenderer<?>) new MhEnderDragonRenderer<>(ctx);
  }

  @Override
  public void render(
    T entity,
    float entityYaw,
    float partialTick,
    PoseStack poseStack,
    MultiBufferSource bufferSource,
    int packedLight
  ) {
    super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
  }
}
