/*
 * THIS PART OF THE CODE IS MEANT FOR DEVELOPERS
 * */
package com.genesiz.hexacreatecore.threedified.text;

import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/* FULL BREAKDOWN:
 *
 * -> Renders animated TextEffectStyle strings in both 2D GUI screens and 3D world space
 *
 * [.draw2D()] is self explanatory
 * [.draw2DBold()] is self explanatory
 * [.draw3D()] is self explanatory
 * [.draw3DBold()] is self explanatory
 *
 * */
public final class ThreeDifiedTextRenderer {

  private ThreeDifiedTextRenderer() {}

  public static void draw2D(GuiGraphics gui, Font font, String text, float x, float y, TextEffectStyle style) {
    draw2DInternal(gui, font, text, x, y, style, false);
  }

  public static void draw2DBold(GuiGraphics gui, Font font, String text, float x, float y, TextEffectStyle style) {
    draw2DInternal(gui, font, text, x, y, style, true);
  }

  public static void draw3D(PoseStack poseStack, MultiBufferSource bufferSource, Font font, String text, float x, float y, TextEffectStyle style) {
    draw3DInternal(poseStack, bufferSource, font, text, x, y, style, false);
  }

  public static void draw3DBold(PoseStack poseStack, MultiBufferSource bufferSource, Font font, String text, float x, float y, TextEffectStyle style) {
    draw3DInternal(poseStack, bufferSource, font, text, x, y, style, true);
  }

  public static void draw3D(PoseStack poseStack, MultiBufferSource bufferSource, Font font, String text, float x, float y, TextEffectStyle style, Style baseStyle) {
    draw3DInternal(poseStack, bufferSource, font, text, x, y, style, false, baseStyle);
  }

  public static void draw3DBold(PoseStack poseStack, MultiBufferSource bufferSource, Font font, String text, float x, float y, TextEffectStyle style, Style baseStyle) {
    draw3DInternal(poseStack, bufferSource, font, text, x, y, style, true, baseStyle);
  }

  private static void draw3DInternal(PoseStack poseStack, MultiBufferSource bufferSource, Font font, String text, float x, float y, TextEffectStyle style, boolean bold) {
    draw3DInternal(poseStack, bufferSource, font, text, x, y, style, bold, Style.EMPTY);
  }

  private static void draw2DInternal(GuiGraphics gui, Font font, String text, float x, float y, TextEffectStyle style, boolean bold) {
    if (text == null || text.isEmpty()) return;
    float timeSec = (System.currentTimeMillis() % 600000L) / 1000.0f;
    int totalW = Math.max(1, font.width(bold ? Component.literal(text).withStyle(Style.EMPTY.withBold(true)) : Component.literal(text)));
    int lineH = font.lineHeight;

    boolean isPrismatic = style.backdropMode() == TextEffectStyle.BackdropMode.PRISMATIC_OUTLINE;
    boolean isSpectrum = style.backdropMode() == TextEffectStyle.BackdropMode.SPECTRUM_STROKE || style.rainbowStroke();
    boolean hasStroke = isPrismatic || isSpectrum;

    if (style.backdropMode() == TextEffectStyle.BackdropMode.GLOW_BLOOM) {
      float pulse = 0.85f + 0.15f * (float) Math.sin(timeSec * 4.5f);
      int outerGlow = scaleAlpha(style.secondaryGlowColor(), pulse);
      int innerGlow = scaleAlpha(style.glowColor(), pulse);
      int x0 = Math.round(x);
      int y0 = Math.round(y);
      gui.fill(x0 - 4, y0 - 2, x0 + totalW + 4, y0 + lineH + 2, outerGlow);
      gui.fill(x0 - 2, y0 - 1, x0 + totalW + 2, y0 + lineH + 1, innerGlow);
    }

    float cursorX = x;

    for (int i = 0; text.length() > i; i++) {
      String ch = String.valueOf(text.charAt(i));
      var styledCh = bold ? Component.literal(ch).withStyle(Style.EMPTY.withBold(true)) : Component.literal(ch);
      int cw = font.width(styledCh);
      float u = ((cursorX - x) + (cw * 0.5f)) / (float) totalW;
      float waveY = (style.waveAmplitude() > 0.0f)
        ? (float) Math.sin((timeSec * 5.0f) + (i * style.waveFrequency())) * style.waveAmplitude()
        : 0.0f;

      int cx = Math.round(cursorX);
      int cy = Math.round(y + waveY);

      if (style.backdropMode() == TextEffectStyle.BackdropMode.CHROMATIC_GLITCH) {
        float jitterX = (float) Math.sin((timeSec * 23.0f) + (i * 1.7f)) * 1.3f;
        float jitterY = (float) Math.cos((timeSec * 19.0f) + (i * 2.3f)) * 0.9f;
        gui.drawString(font, styledCh, Math.round(cursorX + jitterX), Math.round(y + waveY + jitterY), style.glowColor(), false);
        gui.drawString(font, styledCh, Math.round(cursorX - jitterX), Math.round(y + waveY - jitterY), style.secondaryGlowColor(), false);
      }

      if (hasStroke) {
        float shift = ((u * 1.4f) - (timeSec * style.scrollSpeed() * 0.35f)) % 1.0f;
        if (0.0f > shift) shift += 1.0f;
        int edgeCol = isSpectrum
          ? TextEffectStyle.sampleSpectrum(shift)
          : TextEffectStyle.lerpArgb(style.glowColor(), style.secondaryGlowColor(), 0.5f + 0.5f * (float) Math.sin(shift * Math.PI * 2.0D));
        gui.drawString(font, styledCh, cx - 1, cy, edgeCol, false);
        gui.drawString(font, styledCh, cx + 1, cy, edgeCol, false);
        gui.drawString(font, styledCh, cx, cy - 1, edgeCol, false);
        gui.drawString(font, styledCh, cx, cy + 1, edgeCol, false);
      }


      int charColor = style.sampleColor(u, timeSec);
      gui.drawString(font, styledCh, cx, cy, charColor, !hasStroke);
      cursorX += cw;
    }

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

private static void draw3DInternal(
    PoseStack poseStack, MultiBufferSource bufferSource, Font font,
    String text, float x, float y, TextEffectStyle style, boolean bold, Style baseStyle
  ) {
    if (text == null || text.isEmpty()) return;
    float timeSec = (System.currentTimeMillis() % 600000L) / 1000.0f;
    Style effectiveBase = (baseStyle == null ? Style.EMPTY : baseStyle)
      .withBold(bold || (baseStyle != null && baseStyle.isBold()));

    Component fullComp = Component.literal(text).withStyle(effectiveBase);
    int totalW = Math.max(1, font.width(fullComp));
    int lineH = font.lineHeight;

    if (style.backdropMode() == TextEffectStyle.BackdropMode.GLOW_BLOOM) {
      float pulse = 0.85f + 0.15f * (float) Math.sin(timeSec * 4.5f);
      int outerGlow = scaleAlpha(style.secondaryGlowColor(), pulse);
      int innerGlow = scaleAlpha(style.glowColor(), pulse);
      float cx = x + (totalW * 0.5f);
      float cy = y + (lineH * 0.5f);
      RawGeometry.drawDialogueNode(
        poseStack, bufferSource, font,
        cx, cy, -0.04f, totalW + 8.0f, lineH + 4.0f, 0.0f, 0.0f,
        outerGlow, 0x00000000, java.util.List.of(), false
      );
      RawGeometry.drawDialogueNode(
        poseStack, bufferSource, font,
        cx, cy, -0.06f, totalW + 4.0f, lineH + 2.0f, 0.0f, 0.0f,
        innerGlow, 0x00000000, java.util.List.of(), false
      );
    }

    boolean isPrismatic = style.backdropMode() == TextEffectStyle.BackdropMode.PRISMATIC_OUTLINE;
    boolean isSpectrum = style.backdropMode() == TextEffectStyle.BackdropMode.SPECTRUM_STROKE || style.rainbowStroke();
    boolean hasStroke = isPrismatic || isSpectrum;
    boolean dropShadow = !hasStroke && !effectiveBase.isObfuscated();

    float cursorX = x;
    var matrix = poseStack.last().pose();

    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      String ch = String.valueOf(c);
      Component styledCh = Component.literal(ch).withStyle(effectiveBase);
      int cw = Math.max(1, font.width(styledCh));

      float u = ((cursorX - x) + (cw * 0.5f)) / (float) totalW;
      float waveY = (style.waveAmplitude() > 0.0f)
        ? (float) Math.sin((timeSec * 5.0f) + (i * style.waveFrequency())) * style.waveAmplitude()
        : 0.0f;
      float drawY = y + waveY;

      if (style.backdropMode() == TextEffectStyle.BackdropMode.CHROMATIC_GLITCH) {
        float jx = (float) Math.sin((timeSec * 23.0f) + (i * 1.7f)) * 1.2f;
        float jy = (float) Math.cos((timeSec * 19.0f) + (i * 2.3f)) * 0.8f;
        font.drawInBatch(styledCh, cursorX + jx, drawY + jy, style.glowColor(), false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(styledCh, cursorX - jx, drawY - jy, style.secondaryGlowColor(), false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
      }

      if (hasStroke) {
        float shift = ((u * 1.4f) - (timeSec * style.scrollSpeed() * 0.35f)) % 1.0f;
        if (shift < 0.0f) shift += 1.0f;
        int edgeCol = isSpectrum
          ? TextEffectStyle.sampleSpectrum(shift)
          : TextEffectStyle.lerpArgb(style.glowColor(), style.secondaryGlowColor(), 0.5f + 0.5f * (float) Math.sin(shift * Math.PI * 2.0D));
        float off = bold ? 0.9f : 0.75f;
        poseStack.pushPose();
        poseStack.translate(0.0f, 0.0f, -0.08f);
        var strokeMat = poseStack.last().pose();
        font.drawInBatch(styledCh, cursorX - off, drawY, edgeCol, false, strokeMat, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(styledCh, cursorX + off, drawY, edgeCol, false, strokeMat, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(styledCh, cursorX, drawY - off, edgeCol, false, strokeMat, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(styledCh, cursorX, drawY + off, edgeCol, false, strokeMat, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
      }

      float v = 0.5f + ((i % 2 == 0) ? -0.25f : 0.25f);
      int charColor = style.sampleColor(u, v, timeSec);
      font.drawInBatch(styledCh, cursorX, drawY, charColor, dropShadow, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);

      cursorX += cw;
    }
  }

  private static int scaleAlpha(int argb, float factor) {
    int a = Math.round(((argb >>> 24) & 0xFF) * factor);
    return net.minecraft.util.FastColor.ARGB32.color(
      a,
      net.minecraft.util.FastColor.ARGB32.red(argb),
      net.minecraft.util.FastColor.ARGB32.green(argb),
      net.minecraft.util.FastColor.ARGB32.blue(argb)
    );
  }
}
