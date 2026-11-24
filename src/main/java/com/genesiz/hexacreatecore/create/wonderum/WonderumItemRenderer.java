package com.genesiz.hexacreatecore.create.wonderum;

import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.BlackHoleDictionary;
import com.genesiz.hexacreatecore.threedified.shaders.hooks.PortalShaderHook;
import com.genesiz.hexacreatecore.threedified.volumetrics.dictionaries.BlackHoleVolumetricDictionary;
import com.genesiz.hexacreatecore.threedified.volumetrics.hooks.VolumetricHook;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class WonderumItemRenderer extends BlockEntityWithoutLevelRenderer {

  public static final WonderumItemRenderer INSTANCE = new WonderumItemRenderer();
  private static final long EPOCH_NANOS = System.nanoTime();

  private WonderumItemRenderer() {
    super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
  }

  @Override
  public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
    Minecraft mc = Minecraft.getInstance();
    ItemRenderer itemRenderer = mc.getItemRenderer();
    BakedModel baseModel = mc.getModelManager().getModel(WonderumClientEvents.WONDERUM_BASE_MODEL);
    boolean leftHand = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;

    // 1. Render static Wonderum hammer model
    poseStack.pushPose();
    poseStack.translate(0.5f, 0.5f, 0.5f);
    itemRenderer.render(stack, displayContext, leftHand, poseStack, bufferSource, packedLight, packedOverlay, baseModel);
    poseStack.popPose();

    // 2. Compute chamber center in current view/item space
    poseStack.pushPose();
    poseStack.translate(0.5f, 0.5f, 0.5f);
    baseModel.applyTransform(displayContext, poseStack, leftHand);
    poseStack.translate(-0.5f, -0.5f, -0.5f);
    poseStack.translate(1.0f / 16.0f, 14.5f / 16.0f, 0.15f / 16.0f);

    if (displayContext != ItemDisplayContext.GUI) {
      // Extract exact translation & uniform scale, then replace rotation with pure camera-conjugated world orientation
      Matrix4f m = poseStack.last().pose();
      Vector3f viewTranslation = new Vector3f(m.m30(), m.m31(), m.m32());
      float uniformScale = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02());

      Quaternionf worldCamRot = new Quaternionf(mc.getEntityRenderDispatcher().cameraOrientation()).conjugate();
      m.identity()
        .translate(viewTranslation)
        .rotate(worldCamRot)
        .scale(uniformScale);
      poseStack.last().normal().identity().rotate(worldCamRot);
    }

    // 3. Tilt the black hole + accretion disk to a fixed 20-degree angle in world space
    poseStack.mulPose(Axis.ZP.rotationDegrees(10.0f));
    poseStack.mulPose(Axis.XP.rotationDegrees(80.0f));

    float timeSec = (float) ((System.nanoTime() - EPOCH_NANOS) * 1.0e-9D);

    // 4. Render circular relativistic accretion disk matched to the 3D core's horizon
    PortalShaderHook.bind(BlackHoleDictionary.SILVER_SINGULARITY)
      .size(0.168f, 0.168f)
      .resolution(36, 7)
      .circular(true)
      .flushBatch()
      .render(poseStack, bufferSource, timeSec);

    // 5. Render 3D spherical pitch-black singularity core + rim-lit silver photon horizon
    VolumetricHook.bind(BlackHoleVolumetricDictionary.SILVER_SINGULARITY)
      .at(0.0f, 0.0f, 0.0f)
      .coreSize(0.072f, 0.072f, 0.072f)
      .eventHorizon(0.022f, 4)
      .spherical(true)
      .render(poseStack, bufferSource, timeSec);

    poseStack.popPose();
  }
}
