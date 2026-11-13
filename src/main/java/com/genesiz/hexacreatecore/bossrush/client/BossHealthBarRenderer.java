package com.genesiz.hexacreatecore.bossrush.client;

import com.genesiz.hexacreatecore.bossrush.data.BossHealthBarData.HealthBarStyle;
import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public final class BossHealthBarRenderer {
  private static float smoothedHealthRatio = 1.0f;
  private static int trackedEntityId = -1;

  private BossHealthBarRenderer() {}

  public static void renderCustomBossBar(
    GuiGraphics guiGraphics,
    MultiBufferSource.BufferSource bufferSource,
    Font font,
    int screenWidth,
    LivingEntity boss,
    HealthBarStyle style,
    float lagOffsetX,
    float lagOffsetY
  ) {
    try{
      if (boss == null || !boss.isAlive()) {
        trackedEntityId = -1;
        return;
      }

      float maxHp = Math.max(1.0f, boss.getMaxHealth());
      float currentHp = Mth.clamp(boss.getHealth(), 0.0f, maxHp);

      float hpScale = 1.0f;
      var maxHpAttr = boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
      if (maxHpAttr != null) {
        var mod = maxHpAttr.getModifier(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
          com.genesiz.hexacreatecore.HexaCreateCore.MODID, "difficulty_hp_scale"
        ));
        if (mod != null) {
          hpScale = (float) Math.max(0.1D, 1.0D + mod.amount());
        }
      }

      style = style.resolveForHp(currentHp, hpScale);

      float targetRatio = currentHp / maxHp;

      if (trackedEntityId != boss.getId()) {
        trackedEntityId = boss.getId();
        smoothedHealthRatio = targetRatio;
      } else {
        smoothedHealthRatio = Mth.lerp(0.12f, smoothedHealthRatio, targetRatio);
      }

      float screenCenterX = (screenWidth * 0.5f) + lagOffsetX;

      // 1. Top Box: Entity Nameplate with gradient-colored characters
      String rawName = (style.displayName() != null && !style.displayName().isEmpty())
      ? style.displayName()
      : boss.getDisplayName().getString();
      MutableComponent gradientName = buildGradientText(rawName, style);
      int nameTextWidth = font.width(gradientName);
      float nameBoxWidth = Math.max(96.0f, nameTextWidth + 22.0f);
      float nameBoxHeight = font.lineHeight + 8.0f;
      float nameCenterY = 6.0f + (nameBoxHeight * 0.5f) + lagOffsetY;

      List<FormattedCharSequence> nameLines = List.of(gradientName.getVisualOrderText());
      RawGeometry.drawDialogueNode(
        guiGraphics.pose(), bufferSource, font,
        screenCenterX, nameCenterY, 0.0f,
        nameBoxWidth, nameBoxHeight, 2.5f, 2.5f,
        style.backgroundColor(), style.borderColor(), nameLines, true
      );

      // 2. Second Box: Wide Health Bar Container directly beneath the Nameplate
      float barBoxWidth = Math.min(240.0f, screenWidth - 40.0f);
      float barBoxHeight = 14.0f;
      float barCenterY = (6.0f + nameBoxHeight + 3.0f) + (barBoxHeight * 0.5f) + lagOffsetY;

      RawGeometry.drawDialogueNode(
        guiGraphics.pose(), bufferSource, font,
        screenCenterX, barCenterY, 0.0f,
        barBoxWidth, barBoxHeight, 2.5f, 2.5f,
        style.backgroundColor(), style.borderColor(), List.of(), false
      );

      // Flush ThreeDified geometry before drawing the quantized gradient cells on top
      bufferSource.endBatch(RawGeometry.DIALOGUE_SOLID);
      bufferSource.endBatch();

      // Inner fill bounds inside the border
      float pad = 3.0f;
      float innerLeft = screenCenterX - (barBoxWidth * 0.5f) + pad;
      float innerTop = barCenterY - (barBoxHeight * 0.5f) + pad;
      float innerWidth = barBoxWidth - (pad * 2.0f);
      float innerHeight = barBoxHeight - (pad * 2.0f);

      // Trailing damage drain bar
      int trailWidth = Math.round(innerWidth * smoothedHealthRatio);
      if (trailWidth > 0) {
        guiGraphics.fill(
          Math.round(innerLeft),
          Math.round(innerTop),
          Math.round(innerLeft) + trailWidth,
          Math.round(innerTop + innerHeight),
          0x66FF4455
        );
      }

      // Quantized pixel grid controlled by pixel_density (1.0 = 1px, 0.05 = 8px)
      int fillWidth = Math.round(innerWidth * targetRatio);
      if (fillWidth > 0) {
        float cellSize = Mth.lerp(style.pixelDensity(), 8.0f, 1.0f);
        int cols = Math.max(1, Math.round(innerWidth / cellSize));
        int rows = Math.max(1, Math.round(innerHeight / cellSize));

        double rad = Math.toRadians(style.colorAngle());
        double dirX = Math.cos(rad);
        double dirY = Math.sin(rad);
        double maxProj = Math.abs(dirX) + Math.abs(dirY);
        if (0.0001D > maxProj) maxProj = 1.0D;

        int clipRight = Math.round(innerLeft) + fillWidth;

        for (int c = 0; cols > c; c++) {
          float x0 = innerLeft + (c * (innerWidth / cols));
          float x1 = innerLeft + ((c + 1) * (innerWidth / cols));
          int ix0 = Math.round(x0);
          int ix1 = Math.min(clipRight, Math.round(x1));
          if (ix0 >= ix1) break;

          float u = (c + 0.5f) / (float) cols;

          for (int r = 0; rows > r; r++) {
            float y0 = innerTop + (r * (innerHeight / rows));
            float y1 = innerTop + ((r + 1) * (innerHeight / rows));
            int iy0 = Math.round(y0);
            int iy1 = Math.round(y1);
            if (iy0 >= iy1) continue;

            float v = (r + 0.5f) / (float) rows;

            // Directional linear gradient projection (-90 to +90 degrees)
            double proj = ((u - 0.5D) * dirX + (v - 0.5D) * dirY) / maxProj + 0.5D;
            int color = style.sampleGradient((float) proj);

            // Subtle top bevel highlight & bottom shadow for crisp pixel depth
            if (r == 0 && rows > 2) {
              color = HealthBarStyle.lerpArgb(color, 0xFFFFFFFF, 0.22f);
            } else if (r == rows - 1 && rows > 2) {
              color = HealthBarStyle.lerpArgb(color, 0xFF000000, 0.28f);
            }

            guiGraphics.fill(ix0, iy0, ix1, iy1, color);
          }
        }
      }

      // Centered HP readout inside the bar
      String hpText = Math.round(currentHp) + " / " + Math.round(maxHp);
      int hpTextW = font.width(hpText);
      PoseStack pose = guiGraphics.pose();
      pose.pushPose();
      pose.translate(screenCenterX - (hpTextW * 0.25f), innerTop + (innerHeight * 0.5f) - (font.lineHeight * 0.25f), 5.0f);
      pose.scale(0.5f, 0.5f, 1.0f);
      guiGraphics.drawString(font, hpText, 0, 0, 0xFFFFFFFF, true);
      pose.popPose();
    } catch (Throwable t) {
      com.genesiz.hexacreatecore.HexaCreateCore.LOGGER.error("BossHealthBarRenderer error", t);
    }
  }

  private static MutableComponent buildGradientText(String text, HealthBarStyle style) {
    MutableComponent result = Component.empty();
    int len = text.length();
    for (int i = 0; len > i; i++) {
      float t = (len > 1) ? ((float) i / (float) (len - 1)) : 0.5f;
      int rgb = style.sampleGradient(t) & 0x00FFFFFF;
      result.append(Component.literal(String.valueOf(text.charAt(i)))
        .withStyle(Style.EMPTY.withColor(rgb).withBold(true)));
    }
    return result;
  }
}
