package com.genesiz.hexacreatecore.difficulty.client;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.difficulty.data.DifficultyData.DifficultyTier;
import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.genesiz.hexacreatecore.threedified.text.TextEffectStyle;
import com.genesiz.hexacreatecore.threedified.text.ThreeDifiedTextRenderer;
import com.genesiz.hexacreatecore.threedified.threedishapes_hooks.ThreeDiShapeHooks;
import com.genesiz.hexacreatecore.threedified.threedishapes_hooks.UiShapeTarget;
import net.minecraft.network.chat.Style;
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
  private static Vec3 smoothedRingCenter = null;

  private static final TextEffectStyle ETHEREAL_TITLE_STYLE = new TextEffectStyle(
    List.of(0xFF38BDF8, 0xFFBAE6FD, 0xFFFFFFFF, 0xFF38BDF8),
    List.of(0.0f, 0.35f, 0.7f, 1.0f),
    3.2f, 0.45f, 0.32f,
    TextEffectStyle.BackdropMode.GLOW_BLOOM,
    0x7738BDF8, 0x44E0F2FE, false, false, false
  );

  private static final TextEffectStyle ABSOLUTION_TITLE_STYLE = new TextEffectStyle(
    List.of(0xFF140724, 0xFF581C87, 0xFF9333EA, 0xFF140724),
    List.of(0.0f, 0.35f, 0.7f, 1.0f),
    2.0f, 0.70f, 0.35f,
    TextEffectStyle.BackdropMode.GLOW_BLOOM,
    0x889333EA, 0x55E9D5FF, false, false, true
  );

  private static final TextEffectStyle GENESIS_TITLE_STYLE = new TextEffectStyle(
    List.of(0xFFFF1E56, 0xFF8B0000, 0xFFDC2626, 0xFFFF1E56),
    List.of(0.0f, 0.35f, 0.7f, 1.0f),
    2.4f, 0.95f, 0.40f,
    TextEffectStyle.BackdropMode.CHROMATIC_GLITCH,
    0x99FF1E56, 0x99660011, false, true, false
  );

  private static final TextEffectStyle ETHEREAL_DESC_STYLE = new TextEffectStyle(
    List.of(0xFF21B7D1, 0xFFBAE6FD, 0xFFFFFFFF, 0xFF21B7D1),
    List.of(0.0f, 0.35f, 0.7f, 1.0f),
    1.6f, 0.0f, 0.25f,
    TextEffectStyle.BackdropMode.NONE,
    0, 0, false, false, false
  );

  // Absolution Description: Same Purple/Dark-Violet gradient as title, zero wobble
  private static final TextEffectStyle ABSOLUTION_DESC_STYLE = new TextEffectStyle(
    List.of(0xFF3B0764, 0xFF7E22CE, 0xFFD8B4FE, 0xFF7E22CE, 0xFF3B0764),
    List.of(0.0f, 0.25f, 0.5f, 0.75f, 1.0f),
    1.8f, 0.0f, 0.25f,
    TextEffectStyle.BackdropMode.NONE,
    0, 0, false, false, false
  );

  // Genesis Description: Crimson & Blood-Red Chromatic Glitch (4th image style) with tiny readable wobble (0.25f)
  private static final TextEffectStyle GENESIS_DESC_STYLE = new TextEffectStyle(
    List.of(0xFFFF1E56, 0xFF8B0000, 0xFFDC2626, 0xFFFF1E56),
    List.of(0.0f, 0.35f, 0.7f, 1.0f),
    2.2f, 0.25f, 0.35f,
    TextEffectStyle.BackdropMode.CHROMATIC_GLITCH,
    0x88FF1E56, 0x88660011, true, false, false
  );


  @SubscribeEvent
  public static void onRenderWorld(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    if (!DifficultyClientState.selectorOpen) {
      smoothedRingCenter = null;
      return;
    }

    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null) {
      smoothedRingCenter = null;
      return;
    }

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

    float dt = mc.getTimer().getRealtimeDeltaTicks() / 20.0f;
    double linearAlpha = Math.min(1.0D, dt * 4.5D);
    double sineEase = Math.sin(linearAlpha * (Math.PI / 2.0D));

    if (smoothedRingCenter == null || smoothedRingCenter.distanceToSqr(playerEye) > 100.0D) {
      smoothedRingCenter = playerEye;
    } else {
      smoothedRingCenter = smoothedRingCenter.lerp(playerEye, sineEase);
    }

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

      Vec3 nodeWorldPos = new Vec3(smoothedRingCenter.x + dx, smoothedRingCenter.y + 0.1D, smoothedRingCenter.z + dz);
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

      float faceYaw = (float) Math.toDegrees(Math.atan2(smoothedRingCenter.x - nodeWorldPos.x, smoothedRingCenter.z - nodeWorldPos.z)) + 180.0f;
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

      ThreeDiShapeHooks.renderAttachedShapes(
        poseStack, bufferSource, UiShapeTarget.DIFFICULTY_CARD, tier.id,
        0.0f, 0.0f, 0.0f, titleW, titleH
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

      float boxGap = (tier == DifficultyTier.GENESIS) ? 42.0f : 8.0f;
      float descH = (descLines.size() * font.lineHeight) + 12.0f;
      float descCenterY = -(titleH * 0.5f) - boxGap - (descH * 0.5f);

      RawGeometry.drawDialogueNode(
        poseStack, bufferSource, font,
        0.0f, descCenterY, 0.0f,
        (float) maxDescW, descH, 3.0f, 3.0f,
        0xCC0D0B14, tier.accentColor, List.of(), false
      );

      ThreeDiShapeHooks.renderAttachedShapes(
        poseStack, bufferSource, UiShapeTarget.DIFFICULTY_DESCRIPTION, tier.id,
        0.0f, descCenterY, 0.0f, (float) maxDescW, descH
      );

      // Draw crisp front-facing text 3.5 units in front of the extruded box face (depth = 3.0)
      poseStack.pushPose();
      poseStack.translate(0.0f, 0.0f, 3.5f);

      String prefix = isSelected ? "✦ " : "";
      String tierTitle = prefix + tier.displayName;
      String suffix = isHovered ? " [Right-Click]" : "";

      TextEffectStyle titleStyle = switch (tier) {
        case ETHEREAL -> ETHEREAL_TITLE_STYLE;
        case ABSOLUTION -> ABSOLUTION_TITLE_STYLE;
        case GENESIS -> GENESIS_TITLE_STYLE;
        default -> ETHEREAL_TITLE_STYLE;
      };

      var boldTierComp = Component.literal(tierTitle).withStyle(Style.EMPTY.withBold(true));
      var boldSuffixComp = Component.literal(suffix).withStyle(Style.EMPTY.withBold(true));
      float tierW = font.width(boldTierComp);
      float suffixW = font.width(boldSuffixComp);
      float titleX = -(tierW + suffixW) * 0.5f;
      float titleY = -font.lineHeight * 0.5f;

      ThreeDifiedTextRenderer.draw3DBold(
        poseStack, bufferSource, font, tierTitle, titleX, titleY, titleStyle
      );
      if (!suffix.isEmpty()) {
        font.drawInBatch(
          boldSuffixComp, titleX + tierW, titleY, 0xFFFFFFFF, false,
          poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT
        );
      }

      TextEffectStyle descStyle = switch (tier) {
        case ETHEREAL -> ETHEREAL_DESC_STYLE;
        case ABSOLUTION -> ABSOLUTION_DESC_STYLE;
        case GENESIS -> GENESIS_DESC_STYLE;
        default -> ETHEREAL_DESC_STYLE;
      };

      float startY = descCenterY - (descLines.size() * font.lineHeight * 0.5f);
      for (int l = 0; descLines.size() > l; l++) {
        FormattedCharSequence lineSeq = descLines.get(l);
        StringBuilder sb = new StringBuilder();
        lineSeq.accept((idx, s, cp) -> {
          sb.appendCodePoint(cp);
          return true;
        });
        String plainLine = sb.toString();
        float lineX = -font.width(plainLine) * 0.5f;
        ThreeDifiedTextRenderer.draw3D(
          poseStack, bufferSource, font, plainLine, lineX, startY + (l * font.lineHeight), descStyle
        );
      }

      poseStack.popPose();
      poseStack.popPose();
    }
    DifficultyClientState.hoveredTierId = bestHover;
  }
}
