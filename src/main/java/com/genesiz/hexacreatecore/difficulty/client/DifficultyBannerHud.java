package com.genesiz.hexacreatecore.difficulty.client;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.difficulty.data.DifficultyData.DifficultyTier;
import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.genesiz.hexacreatecore.threedified.text.TextEffectStyle;
import com.genesiz.hexacreatecore.threedified.text.ThreeDifiedTextRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class DifficultyBannerHud {

  private static final long TOTAL_DURATION_MS = 6700L; // 350ms slide-in + 6000ms stay + 350ms slide-out
  private static final long SLIDE_MS = 350L;

  private static final TextEffectStyle ETHEREAL_STYLE = new TextEffectStyle(
    List.of(0xFF38BDF8, 0xFFBAE6FD, 0xFFFFFFFF, 0xFF38BDF8),
    List.of(0.0f, 0.35f, 0.7f, 1.0f),
    3.2f, 0.45f, 0.32f,
    TextEffectStyle.BackdropMode.GLOW_BLOOM,
    0x7738BDF8, 0x44E0F2FE, false, false, false
  );

  private static final TextEffectStyle ABSOLUTION_STYLE = new TextEffectStyle(
    List.of(0xFF140724, 0xFF581C87, 0xFF9333EA, 0xFF140724),
    List.of(0.0f, 0.35f, 0.7f, 1.0f),
    2.0f, 0.70f, 0.35f,
    TextEffectStyle.BackdropMode.GLOW_BLOOM,
    0x889333EA, 0x55E9D5FF, false, false, true
  );

  private static final TextEffectStyle GENESIS_STYLE = new TextEffectStyle(
    List.of(0xFFFF1E56, 0xFF8B0000, 0xFFDC2626, 0xFFFF1E56),
    List.of(0.0f, 0.35f, 0x7f / 255.0f, 1.0f),
    2.4f, 0.95f, 0.40f,
    TextEffectStyle.BackdropMode.CHROMATIC_GLITCH,
    0x99FF1E56, 0x99660011, false, true, false
  );

  @SubscribeEvent
  public static void onRenderBanner(RenderGuiEvent.Post event) {
    if (DifficultyClientState.difficultyBannerText.isEmpty()) return;

    long elapsed = System.currentTimeMillis() - DifficultyClientState.difficultyBannerStartMs;
    if (elapsed >= TOTAL_DURATION_MS) {
      DifficultyClientState.difficultyBannerText = "";
      return;
    }

    Minecraft mc = Minecraft.getInstance();
    Font font = mc.font;
    int screenWidth = mc.getWindow().getGuiScaledWidth();
    float screenCenterX = screenWidth * 0.5f;

    var boldComp = Component.literal(DifficultyClientState.difficultyBannerText).withStyle(Style.EMPTY.withBold(true));
    int maxTextW = Math.min(280, screenWidth - 40);
    List<FormattedCharSequence> seqs = font.split(boldComp, maxTextW);

    List<String> lines = new ArrayList<>(seqs.size());
    int widest = 0;
    for (FormattedCharSequence seq : seqs) {
      widest = Math.max(widest, font.width(seq));
      StringBuilder sb = new StringBuilder();
      seq.accept((idx, st, cp) -> {
        sb.appendCodePoint(cp);
        return true;
      });
      lines.add(sb.toString());
    }

    float lineStep = font.lineHeight + 4.0f;
    float boxWidth = widest + 26.0f;
    float boxHeight = (lines.size() * lineStep) + 10.0f;

    long remaining = TOTAL_DURATION_MS - elapsed;
    float slideProgress = 1.0f;
    if (SLIDE_MS > elapsed) {
      slideProgress = (float) Math.sin(((float) elapsed / SLIDE_MS) * (Math.PI / 2.0D));
    } else if (SLIDE_MS > remaining) {
      slideProgress = (float) Math.sin(((float) remaining / SLIDE_MS) * (Math.PI / 2.0D));
    }

    float targetCenterY = 48.0f + (boxHeight * 0.5f);
    float offscreenCenterY = -(boxHeight * 0.5f) - 10.0f;
    float centerY = Mth.lerp(slideProgress, offscreenCenterY, targetCenterY);

    DifficultyTier tier = DifficultyClientState.difficultyBannerTier;
    TextEffectStyle textStyle = switch (tier) {
      case ETHEREAL -> ETHEREAL_STYLE;
      case ABSOLUTION -> ABSOLUTION_STYLE;
      case GENESIS -> GENESIS_STYLE;
      default -> ETHEREAL_STYLE;
    };

    PoseStack poseStack = event.getGuiGraphics().pose();
    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();

    RawGeometry.drawDialogueNode(
      poseStack, bufferSource, font,
      screenCenterX, centerY, 0.0f,
      boxWidth, boxHeight, 3.0f, 3.0f,
      0xCC110E1A, tier.accentColor, List.of(), true
    );

    bufferSource.endBatch(RawGeometry.DIALOGUE_SOLID);
    bufferSource.endBatch();
    RenderSystem.disableDepthTest();

    float startY = centerY - (lines.size() * lineStep * 0.5f) + 2.0f;
    for (int i = 0; lines.size() > i; i++) {
      String line = lines.get(i);
      var lineBold = Component.literal(line).withStyle(Style.EMPTY.withBold(true));
      float lx = screenCenterX - (font.width(lineBold) * 0.5f);
      ThreeDifiedTextRenderer.draw2DBold(
        event.getGuiGraphics(), font, line, lx, startY + (i * lineStep), textStyle
      );
    }
  }
}
