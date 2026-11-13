package com.genesiz.hexacreatecore.threedified.text;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public final class ThreeDifiedTextRenderer {

  private ThreeDifiedTextRenderer() {}

  /** Renders shader-styled text in 2D GUI / Tooltips with glow bloom, chromatic glitch, and sparkles */
  public static void draw2D(GuiGraphics gui, Font font, String text, float x, float y, TextEffectStyle style) {
    if (text == null || text.isEmpty()) return;
    float timeSec = (System.currentTimeMillis() % 600000L) / 1000.0f;
    int totalW = Math.max(1, font.width(text));
    int lineH = font.lineHeight;

    // 1. Back-layer Aura / Glow Bloom
    if (style.backdropMode() == TextEffectStyle.BackdropMode.GLOW_BLOOM) {
      float pulse = 0.85f + 0.15f * (float) Math.sin(timeSec * 4.5f);
      int outerGlow = scaleAlpha(style.secondaryGlowColor(), pulse);
      int innerGlow = scaleAlpha(style.glowColor(), pulse);
      int x0 = Math.round(x);
      int y0 = Math.round(y);
      gui.fill(x0 - 4, y0 - 2, x0 + totalW + 4, y0 + lineH + 2, outerGlow);
      gui.fill(x0 - 2, y0 - 1, x0 + totalW + 2, y0 + lineH + 1, innerGlow);
    }

    // 2. Per-character animated gradient + chromatic glitch / prismatic outline + wave motion
    float cursorX = x;
    boolean isOutline = style.backdropMode() == TextEffectStyle.BackdropMode.PRISMATIC_OUTLINE;

    for (int i = 0; text.length() > i; i++) {
      String ch = String.valueOf(text.charAt(i));
      int cw = font.width(ch);
      float u = ((cursorX - x) + (cw * 0.5f)) / (float) totalW;
      float waveY = (style.waveAmplitude() > 0.0f)
        ? (float) Math.sin((timeSec * 5.0f) + (i * style.waveFrequency())) * style.waveAmplitude()
        : 0.0f;

      var boldCh = Component.literal(ch).withStyle(Style.EMPTY.withBold(true));
      int cx = Math.round(cursorX);
      int cy = Math.round(y + waveY);

      if (style.backdropMode() == TextEffectStyle.BackdropMode.CHROMATIC_GLITCH) {
        float jitterX = (float) Math.sin((timeSec * 23.0f) + (i * 1.7f)) * 1.3f;
        float jitterY = (float) Math.cos((timeSec * 19.0f) + (i * 2.3f)) * 0.9f;
        gui.drawString(font, ch, Math.round(cursorX + jitterX), Math.round(y + waveY + jitterY), style.glowColor(), false);
        gui.drawString(font, ch, Math.round(cursorX - jitterX), Math.round(y + waveY - jitterY), style.secondaryGlowColor(), false);
      } else if (isOutline) {
        float shift = ((u * 1.5f) - (timeSec * style.scrollSpeed() * 0.35f)) % 1.0f;
        if (0.0f > shift) shift += 1.0f;
        float wave = 0.5f + 0.5f * (float) Math.sin(shift * Math.PI * 2.0D);
        int edgeCol = TextEffectStyle.lerpArgb(style.glowColor(), style.secondaryGlowColor(), wave);
        gui.drawString(font, boldCh, cx - 1, cy, edgeCol, false);
        gui.drawString(font, boldCh, cx + 1, cy, edgeCol, false);
        gui.drawString(font, boldCh, cx, cy - 1, edgeCol, false);
        gui.drawString(font, boldCh, cx, cy + 1, edgeCol, false);
      }

      int charColor = style.sampleColor(u, timeSec);
      gui.drawString(font, boldCh, cx, cy, charColor, !isOutline);
      cursorX += cw;
    }

    // 3. Twinkling 4-point star sparkles across the text bounds
    if (style.sparkles()) {
      for (int s = 0; 4 > s; s++) {
        float phase = ((timeSec * 1.3f) + (s * 0.27f)) % 1.0f;
        float twinkle = (float) Math.sin(phase * Math.PI);
        if (0.15f > twinkle) continue;
        float sx = x + (((s * 37) % 100) / 100.0f) * totalW;
        float sy = y + (((s * 53 + (int) (timeSec * 3.0f)) % 100) / 100.0f) * lineH;
        int sparkCol = scaleAlpha(0xFFFFFFFF, twinkle);
        int ix = Math.round(sx);
        int iy = Math.round(sy);
        gui.fill(ix - 2, iy, ix + 3, iy + 1, sparkCol);
        gui.fill(ix, iy - 2, ix + 1, iy + 3, sparkCol);
      }
    }
  }


  /** Renders shader-styled text in 3D world space (PoseStack + MultiBufferSource) */
  public static void draw3D(PoseStack poseStack, MultiBufferSource bufferSource, Font font, String text, float x, float y, TextEffectStyle style) {
    if (text == null || text.isEmpty()) return;
    float timeSec = (System.currentTimeMillis() % 600000L) / 1000.0f;
    int totalW = Math.max(1, font.width(text));
    float cursorX = x;
    boolean isOutline = style.backdropMode() == TextEffectStyle.BackdropMode.PRISMATIC_OUTLINE;

    for (int i = 0; text.length() > i; i++) {
      String ch = String.valueOf(text.charAt(i));
      int cw = font.width(ch);
      float u = ((cursorX - x) + (cw * 0.5f)) / (float) totalW;
      float waveY = (style.waveAmplitude() > 0.0f)
        ? (float) Math.sin((timeSec * 5.0f) + (i * style.waveFrequency())) * style.waveAmplitude()
        : 0.0f;

      var boldCh = Component.literal(ch).withStyle(Style.EMPTY.withBold(true));

      if (style.backdropMode() == TextEffectStyle.BackdropMode.CHROMATIC_GLITCH) {
        float jx = (float) Math.sin((timeSec * 23.0f) + (i * 1.7f)) * 1.2f;
        font.drawInBatch(boldCh, cursorX + jx, y + waveY, style.glowColor(), false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(boldCh, cursorX - jx, y + waveY, style.secondaryGlowColor(), false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
      } else if (isOutline) {
        float shift = ((u * 1.5f) - (timeSec * style.scrollSpeed() * 0.35f)) % 1.0f;
        if (0.0f > shift) shift += 1.0f;
        float wave = 0.5f + 0.5f * (float) Math.sin(shift * Math.PI * 2.0D);
        int edgeCol = TextEffectStyle.lerpArgb(style.glowColor(), style.secondaryGlowColor(), wave);
        poseStack.pushPose();
        poseStack.translate(0.0f, 0.0f, -0.05f);
        font.drawInBatch(boldCh, cursorX - 1.0f, y + waveY, edgeCol, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(boldCh, cursorX + 1.0f, y + waveY, edgeCol, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(boldCh, cursorX, y + waveY - 1.0f, edgeCol, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(boldCh, cursorX, y + waveY + 1.0f, edgeCol, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
      }

      int charColor = style.sampleColor(u, timeSec);
      font.drawInBatch(boldCh, cursorX, y + waveY, charColor, !isOutline, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
      cursorX += cw;
    }
  }

  private static int scaleAlpha(int argb, float factor) {
    int a = Math.round(((argb >>> 24) & 0xFF) * factor);
    return (a << 24) | (argb & 0x00FFFFFF);
  }
}
