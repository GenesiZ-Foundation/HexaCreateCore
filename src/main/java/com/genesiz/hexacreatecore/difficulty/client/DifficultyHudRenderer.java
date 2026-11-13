package com.genesiz.hexacreatecore.difficulty.client;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.bossrush.data.BossHealthBarData.HealthBarStyle;
import com.genesiz.hexacreatecore.difficulty.data.DifficultyData.DifficultyTier;
import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.List;
import java.util.Random;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class DifficultyHudRenderer {
  private static final Random SHAKE_RNG = new Random();
  private static boolean hasCamHistory = false;
  private static float lastYaw = 0.0f;
  private static float lastPitch = 0.0f;
  private static float lagX = 0.0f;
  private static float lagY = 0.0f;

  @SubscribeEvent
  public static void onRenderHud(RenderGuiLayerEvent.Post event) {
    if (!event.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR)) return;
    DifficultyTier tier = DifficultyClientState.activeTier;
    if (tier == DifficultyTier.NORMAL) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null) return;

    GuiGraphics gui = event.getGuiGraphics();
    Font font = mc.font;
    int screenW = mc.getWindow().getGuiScaledWidth();
    int screenH = mc.getWindow().getGuiScaledHeight();
    long now = System.currentTimeMillis();

    // 1. Top-Left Riposte Gauge (shorter 96px bar, zero text, gradient shader + shake when full/primed)
    int maxCharges = Math.max(1, tier.riposteMaxCharges);
    float fillRatio = Math.min(1.0f, DifficultyClientState.riposteCharges / (float) maxCharges);
    boolean isFull = DifficultyClientState.riposteCharges >= maxCharges;
    boolean isPrimed = DifficultyClientState.ripostePrimed;

    float shakeAmp = isPrimed ? 2.4f : (isFull ? 1.2f : 0.0f);
    float shakeX = (shakeAmp > 0.0f) ? ((SHAKE_RNG.nextFloat() * 2.0f - 1.0f) * shakeAmp) : 0.0f;
    float shakeY = (shakeAmp > 0.0f) ? ((SHAKE_RNG.nextFloat() * 2.0f - 1.0f) * shakeAmp) : 0.0f;

    float curYaw = mc.player.getYRot();
    float curPitch = mc.player.getXRot();
    if (!hasCamHistory) {
      lastYaw = curYaw;
      lastPitch = curPitch;
      hasCamHistory = true;
    } else {
      float dYaw = Mth.wrapDegrees(curYaw - lastYaw);
      float dPitch = curPitch - lastPitch;
      lastYaw = curYaw;
      lastPitch = curPitch;
      lagX = Mth.clamp(lagX - (dYaw * 0.28f), -10.0f, 10.0f);
      lagY = Mth.clamp(lagY - (dPitch * 0.28f), -8.0f, 8.0f);
      float dt = mc.getTimer().getRealtimeDeltaTicks() / 20.0f;
      float ease = (float) Math.sin(Math.min(1.0D, dt * 3.5D) * (Math.PI / 2.0D));
      lagX = Mth.lerp(ease, lagX, 0.0f);
      lagY = Mth.lerp(ease, lagY, 0.0f);
    }

    double timeSec = (now % 600000L) / 1000.0D;
    float bobX = (float) Math.sin(timeSec * 1.6D) * 1.5f;
    float bobY = (float) Math.cos(timeSec * 2.1D) * 1.8f;

    HealthBarStyle gStyle = DifficultyClientState.getActiveGaugeStyle();
    float gaugeW = 96.0f;
    float gaugeH = 11.0f;
    float gaugeCenterX = 12.0f + (gaugeW * 0.5f) + lagX + bobX + shakeX;
    float gaugeCenterY = 12.0f + (gaugeH * 0.5f) + lagY + bobY + shakeY;

    // 2. Compact 2x Smaller ADOFAI Rhythm Parry Bar (80px x 7px) centered below crosshair
    boolean showParryBar = DifficultyClientState.isHoldingParry
    || DifficultyClientState.isInParryFlow()
    || (DifficultyClientState.resultDisplayUntilMs > now);

    float barW = 80.0f;
    float barH = 7.0f;
    float parryCenterX = screenW * 0.5f;
    float parryCenterY = (screenH * 0.5f) + 20.0f;

    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();

    RawGeometry.drawDialogueNode(
      gui.pose(), bufferSource, font,
      gaugeCenterX, gaugeCenterY, 0.0f,
      gaugeW, gaugeH, 2.0f, 2.0f,
      gStyle.backgroundColor(), gStyle.borderColor(), List.of(), false
    );

    if (showParryBar) {
      RawGeometry.drawDialogueNode(
        gui.pose(), bufferSource, font,
        parryCenterX, parryCenterY, 0.0f,
        barW + 4.0f, barH + 4.0f, 1.5f, 1.5f,
        0xDD0D0B14, 0x00000000, List.of(), false
      );
    }

    bufferSource.endBatch(RawGeometry.DIALOGUE_SOLID);
    bufferSource.endBatch();
    RenderSystem.disableDepthTest();

    // Render Quantized Gradient Riposte Gauge Fill (using same shader math as BossHealthBarRenderer)
    float pad = 2.5f;
    float innerLeft = gaugeCenterX - (gaugeW * 0.5f) + pad;
    float innerTop = gaugeCenterY - (gaugeH * 0.5f) + pad;
    float innerW = gaugeW - (pad * 2.0f);
    float innerH = gaugeH - (pad * 2.0f);
    int fillW = Math.round(innerW * fillRatio);

    if (fillW > 0) {
      float cellSize = Mth.lerp(gStyle.pixelDensity(), 6.0f, 1.0f);
      int cols = Math.max(1, Math.round(innerW / cellSize));
      int rows = Math.max(1, Math.round(innerH / cellSize));

      double rad = Math.toRadians(gStyle.colorAngle());
      double dirX = Math.cos(rad);
      double dirY = Math.sin(rad);
      double maxProj = Math.max(0.0001D, Math.abs(dirX) + Math.abs(dirY));
      int clipRight = Math.round(innerLeft) + fillW;

      for (int c = 0; cols > c; c++) {
        int ix0 = Math.round(innerLeft + (c * (innerW / cols)));
        int ix1 = Math.min(clipRight, Math.round(innerLeft + ((c + 1) * (innerW / cols))));
        if (ix0 >= ix1) break;
        float u = (c + 0.5f) / (float) cols;

        for (int r = 0; rows > r; r++) {
          int iy0 = Math.round(innerTop + (r * (innerH / rows)));
          int iy1 = Math.round(innerTop + ((r + 1) * (innerH / rows)));
          if (iy0 >= iy1) continue;
          float v = (r + 0.5f) / (float) rows;
          double proj = ((u - 0.5D) * dirX + (v - 0.5D) * dirY) / maxProj + 0.5D;
          gui.fill(ix0, iy0, ix1, iy1, gStyle.sampleGradient((float) proj));
        }
      }
    }

    // Render Compact 2x Smaller ADOFAI Parry Bar + Cubic-Bezier Cyan Line
    if (showParryBar) {
      int bx0 = Math.round(parryCenterX - (barW * 0.5f));
      int by0 = Math.round(parryCenterY - (barH * 0.5f));
      int by1 = Math.round(parryCenterY + (barH * 0.5f));

      drawZone(gui, bx0, by0, by1, barW, 0.00f, 0.10f, 0xFFDC2626);
      drawZone(gui, bx0, by0, by1, barW, 0.10f, 0.30f, 0xFFF97316);
      drawZone(gui, bx0, by0, by1, barW, 0.30f, 0.45f, 0xFF22C55E);
      drawZone(gui, bx0, by0, by1, barW, 0.45f, 0.55f, 0xFFFFFFFF);
      drawZone(gui, bx0, by0, by1, barW, 0.55f, 0.70f, 0xFF22C55E);
      drawZone(gui, bx0, by0, by1, barW, 0.70f, 0.90f, 0xFFF97316);
      drawZone(gui, bx0, by0, by1, barW, 0.90f, 1.00f, 0xFFDC2626);

      float cursor = DifficultyClientState.getSweepProgress();
      if (cursor >= 0.0f) {
        int cx = bx0 + Math.round(barW * cursor);
        gui.fill(cx - 1, by0 - 2, cx + 1, by1 + 2, 0xFF00F5FF);
      }
    }
  }

  private static void drawZone(GuiGraphics gui, int bx0, int by0, int by1, float barW, float startPct, float endPct, int color) {
    int x0 = bx0 + Math.round(barW * startPct);
    int x1 = bx0 + Math.round(barW * endPct);
    if (x1 > x0) gui.fill(x0, by0, x1, by1, color);
  }
}
