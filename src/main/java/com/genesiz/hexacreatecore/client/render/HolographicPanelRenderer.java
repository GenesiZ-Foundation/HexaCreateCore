package com.genesiz.hexacreatecore.client.render;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.virtual.Dialogue3D;
import com.genesiz.hexacreatecore.virtual.DialogueText;
import com.genesiz.hexacreatecore.virtual.DialogueVec3;
import com.genesiz.hexacreatecore.virtual.RawGeometry;
import com.genesiz.hexacreatecore.virtual.SmoothPositionState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import org.joml.Matrix4f;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public class HolographicPanelRenderer {

  @SubscribeEvent
  public static void onRenderLevelStage(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null) return;

    PoseStack poseStack = event.getPoseStack();
    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

    for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
      if (!(entity instanceof Dialogue3D dialogueEntity)) continue;
      if (!dialogueEntity.shouldShowDialogue()) continue;


      poseStack.pushPose();

      var camera = mc.getEntityRenderDispatcher().camera;
      float realTick = mc.getTimer().getGameTimeDeltaTicks();
      float partialTick = mc.getTimer().getRealtimeDeltaTicks() / 20.0f;


      DialogueVec3<?> target_pos = new DialogueVec3<>(
        realTick,
        (Entity & Dialogue3D) dialogueEntity,
        1.0D,
        mc.player.getEyePosition(realTick),
        camera.getPosition(),
        null
      );

      SmoothPositionState position_tracker = dialogueEntity.getDialogueStateTracker();
      position_tracker.update(
        target_pos.targetX(),
        target_pos.targetY(),
        target_pos.targetZ(),
        camera.getYRot(),
        partialTick
      );

      DialogueVec3<?> dialogue_entity_pos = new DialogueVec3<>(
        realTick,
        (Entity & Dialogue3D) dialogueEntity,
        1.0D,
        mc.player.getEyePosition(realTick),
        camera.getPosition(),
        position_tracker
      );

      String text = "Hello adventurer! This is a dynamic speech bubble that wraps text perfectly when it gets way too long.";
      int maxBubbleWidth = 120;
      DialogueText layout = DialogueText.calculate(mc.font, text, maxBubbleWidth);

      poseStack.translate(dialogue_entity_pos.renderX(), dialogue_entity_pos.renderY(), dialogue_entity_pos.renderZ());

      poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-position_tracker.currentYaw - 180.0F));
      poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-camera.getXRot()));

      poseStack.scale(0.015F, -0.015F, 0.015F);

      float rectW = layout.boxWidth();
      float rectH = layout.boxHeight();
      float borderThickness = 4.0f;
      float borderDepth = 4.0f;

      int bg_color = 0xFF000000;
      int border_color = 0xFFFFFFFF;
      Matrix4f matrix = poseStack.last().pose();
      VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());

      RawGeometry.drawRectangle(consumer, matrix, rectW, rectH, bg_color);

      poseStack.pushPose();
      poseStack.translate(0.0f, -(rectH / 2.0f + borderThickness / 2.0f), 0.0f);
      RawGeometry.drawPrism(consumer, poseStack.last().pose(), rectW + (borderThickness * 2.0f), borderThickness, borderDepth, border_color);
      poseStack.popPose();

      // Bottom Border
      poseStack.pushPose();
      poseStack.translate(0.0f, (rectH / 2.0f + borderThickness / 2.0f), 0.0f);
      RawGeometry.drawPrism(consumer, poseStack.last().pose(), rectW + (borderThickness * 2.0f), borderThickness, borderDepth, border_color);
      poseStack.popPose();

      // Left Border
      poseStack.pushPose();
      poseStack.translate(-(rectW / 2.0f + borderThickness / 2.0f), 0.0f, 0.0f);
      RawGeometry.drawPrism(consumer, poseStack.last().pose(), borderThickness, rectH, borderDepth, border_color);
      poseStack.popPose();

      // Right Border
      poseStack.pushPose();
      poseStack.translate((rectW / 2.0f + borderThickness / 2.0f), 0.0f, 0.0f);
      RawGeometry.drawPrism(consumer, poseStack.last().pose(), borderThickness, rectH, borderDepth, border_color);
      poseStack.popPose();

      poseStack.translate(0.0f, 0.0f, 0.5f);

      // Calculate top start line alignment centering text inside the box height
      float textStartY = -(rectH / 2.0f) + 6.0f; 

      for (int i = 0; i < layout.lines().size(); i++) {
        FormattedCharSequence lineText = layout.lines().get(i);
        float textStartX = -(mc.font.width(lineText) / 2.0f); // Center line horizontally

        // Draw the text lines sequentially downward
        mc.font.drawInBatch(
          lineText, 
          textStartX, 
          textStartY + (i * mc.font.lineHeight), 
          0xFFFFFF, // Crisp white text color
          false, 
          poseStack.last().pose(), 
          bufferSource, 
          Font.DisplayMode.NORMAL, 
          0, 
          15728880 // Max light layout parameters
        );
      }

      poseStack.popPose();
    }
  }
}
