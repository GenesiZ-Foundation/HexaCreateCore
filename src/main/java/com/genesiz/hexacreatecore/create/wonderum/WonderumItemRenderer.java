package com.genesiz.hexacreatecore.create.wonderum;

import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.BlackHoleDictionary;
import com.genesiz.hexacreatecore.threedified.shaders.hooks.PortalShaderHook;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

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

    // 2. Align with the item display transform and translate to the hollow chamber [1.0, 14.5, 1.0] / 16.0
    poseStack.pushPose();
    poseStack.translate(0.5f, 0.5f, 0.5f);
    baseModel.applyTransform(displayContext, poseStack, leftHand);
    poseStack.translate(-0.5f, -0.5f, -0.5f);
    poseStack.translate(1.0f / 16.0f, 14.5f / 16.0f, 1.0f / 16.0f);

    // 3. Billboard the silver-and-black singularity shader toward the camera
    if (displayContext != ItemDisplayContext.GUI) {
      Quaternionf inversePoseRot = new Quaternionf();
      poseStack.last().pose().getNormalizedRotation(inversePoseRot).conjugate();
      poseStack.mulPose(inversePoseRot);
      poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
    }

    float timeSec = (float) ((System.nanoTime() - EPOCH_NANOS) * 1.0e-9D);
    PortalShaderHook.bind(BlackHoleDictionary.SILVER_SINGULARITY)
      .size(0.115f, 0.115f)
      .resolution(40, 8)
      .render(poseStack, bufferSource, timeSec);

    poseStack.popPose();
  }
}

