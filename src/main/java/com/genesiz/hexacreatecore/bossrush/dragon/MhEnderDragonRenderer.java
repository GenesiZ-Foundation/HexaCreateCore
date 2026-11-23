package com.genesiz.hexacreatecore.bossrush.dragon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class MhEnderDragonRenderer<T extends EnderDragon & IMhEnderDragon> extends GeoEntityRenderer<T> {

  private static final String[] TRACKED_BONES = {
    "body", "head", "neck", "neck5",
    "rearleg", "rearleg1", "frontleg", "frontleg1",
    "rearfoot", "rearfoot1", "frontfoot", "frontfoot1",
    "tail", "tail6", "tail12"
  };

  private boolean renderingAfterImage = false;
  private int afterImageAlpha = 153; // 60% of 255

  public MhEnderDragonRenderer(EntityRendererProvider.Context renderManager) {
    super(renderManager, new MhEnderDragonModel<>());
    this.shadowRadius = 2.5f;
  }

  @SuppressWarnings("unchecked")
  public static EntityRenderer<EnderDragon> create(EntityRendererProvider.Context ctx) {
    return (EntityRenderer<EnderDragon>) (EntityRenderer<?>) new MhEnderDragonRenderer<>(ctx);
  }

  @Override
  public RenderType getRenderType(
    T animatable,
    ResourceLocation texture,
    @Nullable MultiBufferSource bufferSource,
    float partialTick
  ) {
    if (this.renderingAfterImage) {
      return RenderType.entityTranslucent(texture);
    }
    return super.getRenderType(animatable, texture, bufferSource, partialTick);
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
    var baked = this.model.getBakedModel(this.model.getModelResource(entity));
    if (baked != null) {
      for (String name : TRACKED_BONES) {
        baked.getBone(name).ifPresent(b -> b.setTrackingMatrices(true));
      }
    }

    super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

    if (baked != null) {
      DragonMultipartCollider.captureRenderedWorldBones(entity, baked);
    }

    // 3x Purple Translucent After-Image during end_roar (1.0s -> 6.0s)
    if (entity.hexacreatecore$getActiveAction() == DragonAnimState.DragonAction.SAFI_ROAR && baked != null) {
      float elapsedSec = (entity.hexacreatecore$getActionTick() + partialTick) / 20.0f;
      if (elapsedSec >= 1.0f && 6.0f >= elapsedSec) {
        float ghostScale;
        float opacity;

        if (2.0f > elapsedSec) {
          // 1.0s -> 2.0s: Ease-in growth from 1x to 3x
          float t = Mth.clamp((elapsedSec - 1.0f) / 1.0f, 0.0f, 1.0f);
          float easeIn = t * t * t;
          ghostScale = Mth.lerp(easeIn, 1.0f, 3.0f);
          opacity = 0.60f * Mth.clamp(t * 2.0f, 0.0f, 1.0f);
        } else if (4.75f > elapsedSec) {
          // 2.0s -> 4.75s: Hold at 3x scale & 60% opacity during roar
          ghostScale = 3.0f;
          opacity = 0.60f;
        } else {
          // 4.75s -> 6.0s: Ease-in shrink from 3x back to 1x
          float t = Mth.clamp((elapsedSec - 4.75f) / 1.25f, 0.0f, 1.0f);
          float easeIn = t * t * t;
          ghostScale = Mth.lerp(easeIn, 3.0f, 1.0f);
          opacity = 0.60f * (1.0f - easeIn);
        }

        if (opacity > 0.01f) {
          renderPurpleAfterImage(baked, entity, partialTick, poseStack, bufferSource, ghostScale, opacity);
        }
      }
    }
  }

  private void renderPurpleAfterImage(
    BakedGeoModel baked,
    T entity,
    float partialTick,
    PoseStack poseStack,
    MultiBufferSource bufferSource,
    float scale,
    float opacity
  ) {
    this.renderingAfterImage = true;
    this.afterImageAlpha = Mth.clamp((int) (opacity * 255.0f), 0, 255);
    this.animatable = entity;

    // Bright Ender Dragon eye magenta (#E066FF = 224, 102, 255)
    int r = 224;
    int g = 102;
    int b = 255;

    MultiBufferSource purpleTintedSource = renderType -> {
      VertexConsumer delegate = bufferSource.getBuffer(renderType);
      return new PurpleTintVertexConsumer(delegate, r, g, b, this.afterImageAlpha);
    };

    poseStack.pushPose();
    poseStack.translate(0.0D, 2.35D, 0.0D);
    poseStack.scale(scale, scale, scale);
    poseStack.translate(0.0D, -2.35D, 0.0D);

    try {
      this.actuallyRender(
        poseStack,
        entity,
        baked,
        RenderType.entityTranslucent(this.getTextureLocation(entity)),
        purpleTintedSource,
        purpleTintedSource.getBuffer(RenderType.entityTranslucent(this.getTextureLocation(entity))),
        true,
        partialTick,
        15728880,
        OverlayTexture.NO_OVERLAY,
        ((this.afterImageAlpha & 0xFF) << 24) | (r << 16) | (g << 8) | b
      );
    } finally {
      poseStack.popPose();
      this.animatable = null;
      this.renderingAfterImage = false;
    }
  }


  private static final class PurpleTintVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final int r, g, b, a;

    PurpleTintVertexConsumer(VertexConsumer delegate, int r, int g, int b, int a) {
      this.delegate = delegate;
      this.r = r;
      this.g = g;
      this.b = b;
      this.a = a;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
      return this.delegate.addVertex(x, y, z);
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
      return this.delegate.setColor(this.r, this.g, this.b, this.a);
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
      return this.delegate.setUv(u, v);
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
      return this.delegate.setUv1(u, v);
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
      return this.delegate.setUv2(u, v);
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
      return this.delegate.setNormal(x, y, z);
    }
  }
}
