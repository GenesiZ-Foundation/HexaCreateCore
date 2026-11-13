package com.genesiz.hexacreatecore.difficulty.client;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.difficulty.data.DifficultyData.DifficultyTier;
import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class Difficulty3DRingRenderer {

  @SubscribeEvent
  public static void onRenderWorld(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    if (!DifficultyClientState.selectorOpen) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null) return;

    List<DifficultyTier> tiers = new ArrayList<>();
    tiers.add(DifficultyTier.ETHEREAL);
    tiers.add(DifficultyTier.ABSOLUTION);
    if (DifficultyClientState.genesisUnlocked) {
      tiers.add(DifficultyTier.GENESIS);
    }

    int count = tiers.size();
    double stepAngleRad = (Math.PI * 2.0D) / count;
    double baseRad = Math.toRadians(DifficultyClientState.selectorBaseYaw);
    double radius = 2.8D;

    Vec3 camPos = event.getCamera().getPosition();
    Vec3 playerEye = mc.player.getEyePosition(event.getPartialTick().getGameTimeDeltaPartialTick(true));
    Vec3 lookVec = mc.player.getViewVector(1.0f).normalize();

    PoseStack poseStack = event.getPoseStack();
    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
    Font font = mc.font;

    String bestHover = "";
    double bestDot = 0.90D;

    for (int i = 0; count > i; i++) {
      DifficultyTier tier = tiers.get(i);
      double angle = baseRad + (i * stepAngleRad);
      double dx = -Math.sin(angle) * radius;
      double dz = Math.cos(angle) * radius;

      Vec3 nodeWorldPos = new Vec3(playerEye.x + dx, playerEye.y + 0.1D, playerEye.z + dz);
      Vec3 toNode = nodeWorldPos.subtract(playerEye).normalize();
      double dot = lookVec.dot(toNode);
      if (dot > bestDot) {
        bestDot = dot;
        bestHover = tier.id;
      }

      boolean isHovered = tier.id.equals(DifficultyClientState.hoveredTierId);
      boolean isSelected = tier == DifficultyClientState.activeTier;

      poseStack.pushPose();
      poseStack.translate(nodeWorldPos.x - camPos.x, nodeWorldPos.y - camPos.y, nodeWorldPos.z - camPos.z);

      float faceYaw = (float) Math.toDegrees(Math.atan2(playerEye.x - nodeWorldPos.x, playerEye.z - nodeWorldPos.z)) + 180.0f;
      poseStack.mulPose(Axis.YP.rotationDegrees(faceYaw));

      float scale = isHovered ? 0.0145f : 0.0125f;
      poseStack.scale(-scale, -scale, -scale);

      int borderColor = isHovered ? 0xFFFFFFFF : (isSelected ? 0xFFFFD700 : tier.accentColor);
      int bgColor = isHovered ? 0xEE1A1429 : 0xCC110E1A;

      // 1. Difficulty Name Box at eye level
      String title = (isSelected ? "✦ " : "") + tier.displayName + (isHovered ? " [Right-Click]" : "");
      FormattedCharSequence titleSeq = Component.literal("§l" + title).getVisualOrderText();
      float titleW = Math.max(110.0f, font.width(titleSeq) + 26.0f);
      float titleH = font.lineHeight + 12.0f;

      RawGeometry.drawDialogueNode(
        poseStack, bufferSource, font,
        0.0f, 0.0f, 0.0f,
        titleW, titleH, 3.0f, 3.0f,
        bgColor, borderColor, List.of(), false
      );

      // 2. List of Changes floating directly above the Difficulty Name Box
      List<String> changes = DifficultyClientState.TIER_DESCRIPTIONS.getOrDefault(tier.id, List.of("No description configured."));
      List<FormattedCharSequence> descLines = new ArrayList<>();
      int maxDescW = 140;
      for (String line : changes) {
        for (FormattedCharSequence seq : font.split(Component.literal("§7• §f" + line), 210)) {
          descLines.add(seq);
          maxDescW = Math.max(maxDescW, font.width(seq) + 22);
        }
      }

      float descH = (descLines.size() * font.lineHeight) + 12.0f;
      float descCenterY = -(titleH * 0.5f) - 8.0f - (descH * 0.5f);

      RawGeometry.drawDialogueNode(
        poseStack, bufferSource, font,
        0.0f, descCenterY, 0.0f,
      (float) maxDescW, descH, 3.0f, 3.0f,
        0xCC0D0B14, tier.accentColor, List.of(), false
      );

      // Draw crisp front-facing text 3.5 units in front of the extruded box face (depth = 3.0)
      poseStack.pushPose();
      poseStack.translate(0.0f, 0.0f, 3.5f);

      float titleX = -font.width(titleSeq) * 0.5f;
      float titleY = -font.lineHeight * 0.5f;
      font.drawInBatch(titleSeq, titleX, titleY, 0xFFFFFFFF, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);

      float startY = descCenterY - (descLines.size() * font.lineHeight * 0.5f);
      for (int l = 0; descLines.size() > l; l++) {
        FormattedCharSequence lineSeq = descLines.get(l);
        float lineX = -font.width(lineSeq) * 0.5f;
        font.drawInBatch(lineSeq, lineX, startY + (l * font.lineHeight), 0xFFFFFFFF, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
      }

      poseStack.popPose();
      poseStack.popPose();
    }

    DifficultyClientState.hoveredTierId = bestHover;
    bufferSource.endBatch(RawGeometry.DIALOGUE_SOLID);
    bufferSource.endBatch();
  }
}
