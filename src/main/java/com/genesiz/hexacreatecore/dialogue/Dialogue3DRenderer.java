/*
 * THIS PART OF THE CODE IS MEANT FOR DEVELOPERS
 * */
package com.genesiz.hexacreatecore.dialogue;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

// DEFAULT RENDERER
@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public class Dialogue3DRenderer {
  private static List<FormattedCharSequence> lines = null;

  @SubscribeEvent
  public static void onRenderLevelStage(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null) return;

    PoseStack poseStack = event.getPoseStack();
    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

    var camera = mc.getEntityRenderDispatcher().camera;
    float realTick = mc.getTimer().getGameTimeDeltaTicks();
    float partialTick = mc.getTimer().getRealtimeDeltaTicks() / 20.0f;
    org.joml.Vector3f lookDir = camera.getLookVector();

    for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
      if (!(entity instanceof Dialogue3D dialogueEntity)) continue;
      if (dialogueEntity.hasCustomRenderer()) continue;

      DialoguePlaybackState playback = dialogueEntity.getDialoguePlayback();
      boolean isShowing = dialogueEntity.shouldShowDialogue();
      playback.setShowing(isShowing);
      if (!isShowing || playback.isForceClosed()) continue;

      DialogueTransformState transform = dialogueEntity.getDialogueTransform();
      transform.update(
        entity,
        realTick,
        1.0D,
        mc.player.getEyePosition(realTick),
        camera.getPosition(),
        camera.getYRot(),
        partialTick
      );
      playback.updateFromEntity(mc.font, entity, partialTick);


      poseStack.pushPose();
      poseStack.translate(transform.renderX, transform.renderY, transform.renderZ);

      poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-transform.currentYaw - 180.0F));
      poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-camera.getXRot()));

      poseStack.scale(0.015F, -0.015F, 0.015F);

      RawGeometry.drawDialogueNode(
        poseStack,
        bufferSource,
        mc.font,
        0.0f,
        0.0f,
        0.0f,
        playback.boxWidth,
        playback.boxHeight,
        4.0f,
        4.0f,
        playback.currentBgColor,
        playback.currentBorderColor,
        playback,
        true,
        false
      );

      int newHoveredIndex = -1;
      float currentY = (playback.boxHeight * 0.5f) + 8.0f;
      for (int i = 0; playback.options.size() > i; i++) {
        DialogueNode button = playback.options.get(i);
        float buttonCenterY = currentY + (button.height * 0.5f);

        boolean isHovered = DialogueRaycaster.isLookingAtNode(
          poseStack.last().pose(),
          lookDir,
          0.0f, buttonCenterY, 2.0f,
          button.baseWidth, button.baseHeight,
          5.0D
        );

        if (isHovered) {
          newHoveredIndex = i;
        }

        boolean isHeld = isHovered && (mc.options.keyAttack.isDown() || mc.options.keyUse.isDown());
        button.updatePressScale(isHeld, 0.85f, partialTick);

        float buttonZ = isHeld ? 1.0f : (isHovered ? 5.0f : 2.0f);
        int borderColor = isHovered ? button.hoverBorderColor : button.borderColor;

        RawGeometry.drawDialogueNode(
          poseStack, bufferSource, mc.font,
          0.0f, buttonCenterY, buttonZ,
          button.width, button.height,
          3.0f, 4.0f,
          button.bgColor, borderColor,
          button.lines, true
        );

        playback.hoveredOptionIndex = newHoveredIndex;
        currentY += button.height + 10.0f;
      }

      poseStack.popPose();
    }
  }
}
