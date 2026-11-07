package com.genesiz.hexacreatecore.client.render;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.virtual.Dialogue3D;
import com.genesiz.hexacreatecore.virtual.DialoguePlaybackState;
import com.genesiz.hexacreatecore.virtual.DialogueTransformState;
import com.genesiz.hexacreatecore.virtual.RawGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public class HolographicPanelRenderer {
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

    for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
      if (!(entity instanceof Dialogue3D dialogueEntity)) continue;

      boolean isShowing = dialogueEntity.shouldShowDialogue();
      dialogueEntity.getDialoguePlayback().setShowing(isShowing);
      if (!isShowing) continue;

      poseStack.pushPose();


      DialogueTransformState transform = dialogueEntity.getDialogueTransform();
      DialoguePlaybackState playback = dialogueEntity.getDialoguePlayback();

      String text = "Hello adventurer! This is a dynamic speech bubble that wraps text perfectly when it gets way too long.";
      int maxBubbleWidth = 120;

      transform.update(
        entity,
        realTick,
        1.0D,
        mc.player.getEyePosition(realTick),
        camera.getPosition(),
        camera.getYRot(),
        partialTick
      );

      playback.update(
        mc.font,
        text,
        maxBubbleWidth,
        partialTick,
        25.0f,
        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value()
      );

      poseStack.translate(transform.renderX, transform.renderY, transform.renderZ);

      poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-transform.currentYaw - 180.0F));
      poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-camera.getXRot()));

      poseStack.scale(0.015F, -0.015F, 0.015F);

      //RawGeometry.drawBorderedPanel(poseStack, consumer, rectW, rectH, borderThickness, borderDepth, bg_color, border_color);
      if (lines == null) {
        lines = mc.font.split(Component.literal("Yes, forge it"), 100);
      }

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
        0xFF000000,
        0xFFFFFFFF,
        playback,
        true,
        false
      );

      float buttonY = (playback.boxHeight * 0.5f) + 18.0f;
      RawGeometry.drawDialogueNode(
        poseStack,
        bufferSource,
        mc.font,
        0.0f,
        buttonY,
        2.0f,
        90.0f,
        20.0f,
        3.0f,
        4.0f,
        0xFF111111,
        0xFFFFAA00,
        lines,
        true
      );

      poseStack.popPose();
    }
  }
}
