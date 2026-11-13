package com.genesiz.hexacreatecore.difficulty.client;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.difficulty.data.DifficultyData.DifficultyTier;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class DifficultyAtmosphereController {

  private static DifficultyTier activeEffectTier = DifficultyTier.NORMAL;
  private static long effectStartMs = 0L;

  private DifficultyAtmosphereController() {}

  public static void trigger(DifficultyTier tier) {
    if (tier == null || tier == DifficultyTier.NORMAL) {
      activeEffectTier = DifficultyTier.NORMAL;
      effectStartMs = 0L;
      return;
    }
    activeEffectTier = tier;
    effectStartMs = System.currentTimeMillis();
  }

  private record AtmosphereFrame(
    float activeAlpha,
    int skyRgb,
    int fogRgb,
    float fogDistanceScale, // 1.0f = normal, 0.15f = 15% of normal/DistantHorizons distance
    float veinVignetteAlpha
  ) {}

  private static AtmosphereFrame sampleFrame() {
    if (activeEffectTier == DifficultyTier.NORMAL || effectStartMs <= 0L) {
      return new AtmosphereFrame(0.0f, 0, 0, 1.0f, 0.0f);
    }

    float sec = (System.currentTimeMillis() - effectStartMs) / 1000.0f;
    if (sec < 0.0f) return new AtmosphereFrame(0.0f, 0, 0, 1.0f, 0.0f);

    if (activeEffectTier == DifficultyTier.ETHEREAL) {
      // 2s fade in -> 2s hold -> 1s fade out (5.0s total)
      if (sec >= 5.0f) {
        activeEffectTier = DifficultyTier.NORMAL;
        return new AtmosphereFrame(0.0f, 0, 0, 1.0f, 0.0f);
      }
      float a = (2.0f > sec) ? (sec / 2.0f) : ((4.0f > sec) ? 1.0f : (1.0f - ((sec - 4.0f) / 1.0f)));
      a = smoothStep(Mth.clamp(a, 0.0f, 1.0f));
      return new AtmosphereFrame(a, 0x38BDF8, 0x00E5FF, Mth.lerp(a, 1.0f, 0.38f), 0.0f);
    }

    if (activeEffectTier == DifficultyTier.ABSOLUTION) {
      // 3s fade in -> 2s hold -> 1s pale out (6.0s total)
      if (sec >= 6.0f) {
        activeEffectTier = DifficultyTier.NORMAL;
        return new AtmosphereFrame(0.0f, 0, 0, 1.0f, 0.0f);
      }
      float a = (3.0f > sec) ? (sec / 3.0f) : ((5.0f > sec) ? 1.0f : (1.0f - ((sec - 5.0f) / 1.0f)));
      a = smoothStep(Mth.clamp(a, 0.0f, 1.0f));
      return new AtmosphereFrame(a, 0x2A0845, 0x09080C, Mth.lerp(a, 1.0f, 0.28f), 0.0f);
    }

    if (activeEffectTier == DifficultyTier.GENESIS) {
      // 0..10s: normal -> blood red sky + bright Genesis red close fog
      // 10..20s: blood red sky -> pitch black + bright red fog -> blood red
      // 20..25s: sky/fog recover + Heart Attack vein vignette (1s fade-in, 3s heartbeat pulse, 1s fade-out)
      if (sec >= 25.0f) {
        activeEffectTier = DifficultyTier.NORMAL;
        return new AtmosphereFrame(0.0f, 0, 0, 1.0f, 0.0f);
      }

      if (10.0f > sec) {
        float t = smoothStep(sec / 10.0f);
        return new AtmosphereFrame(t, 0x7F0019, 0xFF1E56, Mth.lerp(t, 1.0f, 0.18f), 0.0f);
      } else if (20.0f > sec) {
        float t = smoothStep((sec - 10.0f) / 10.0f);
        int sky = lerpRgb(0x7F0019, 0x030002, t);
        int fog = lerpRgb(0xFF1E56, 0x660011, t);
        return new AtmosphereFrame(1.0f, sky, fog, Mth.lerp(t, 0.18f, 0.14f), 0.0f);
      } else {
        float sub = sec - 20.0f; // 0.0 -> 5.0s
        float envAlpha = 1.0f - smoothStep(sub / 5.0f);

        float veinBase;
        if (1.0f > sub) {
          veinBase = sub; // 1s fade in
        } else if (4.0f > sub) {
          // 3s violent double-heartbeat pulse ("lub-dub" every 1.0s)
          float beatPhase = (sub - 1.0f) % 1.0f;
          float lub = (float) Math.exp(-Math.pow((beatPhase - 0.12f) / 0.08f, 2.0D));
          float dub = (float) Math.exp(-Math.pow((beatPhase - 0.32f) / 0.08f, 2.0D)) * 0.85f;
          veinBase = Mth.clamp(0.62f + 0.38f * Math.max(lub, dub), 0.0f, 1.0f);
        } else {
          veinBase = 1.0f - (sub - 4.0f); // 1s fade out
        }
        return new AtmosphereFrame(envAlpha, 0x030002, 0x660011, Mth.lerp(envAlpha, 1.0f, 0.14f), Mth.clamp(veinBase, 0.0f, 1.0f));
      }
    }

    return new AtmosphereFrame(0.0f, 0, 0, 1.0f, 0.0f);
  }

  /** Called by ClientLevelMixin to tint the sky dome color smoothly */
  public static Vec3 applySkyColorTint(Vec3 baseSky) {
    AtmosphereFrame f = sampleFrame();
    if (0.001f >= f.activeAlpha) return baseSky;
    float tr = ((f.skyRgb >> 16) & 0xFF) / 255.0f;
    float tg = ((f.skyRgb >> 8) & 0xFF) / 255.0f;
    float tb = (f.skyRgb & 0xFF) / 255.0f;
    return new Vec3(
      Mth.lerp(f.activeAlpha, baseSky.x, tr),
      Mth.lerp(f.activeAlpha, baseSky.y, tg),
      Mth.lerp(f.activeAlpha, baseSky.z, tb)
    );
  }

  @SubscribeEvent
  public static void onFogColor(ViewportEvent.ComputeFogColor event) {
    AtmosphereFrame f = sampleFrame();
    if (0.001f >= f.activeAlpha) return;
    float tr = ((f.fogRgb >> 16) & 0xFF) / 255.0f;
    float tg = ((f.fogRgb >> 8) & 0xFF) / 255.0f;
    float tb = (f.fogRgb & 0xFF) / 255.0f;
    event.setRed(Mth.lerp(f.activeAlpha, event.getRed(), tr));
    event.setGreen(Mth.lerp(f.activeAlpha, event.getGreen(), tg));
    event.setBlue(Mth.lerp(f.activeAlpha, event.getBlue(), tb));
  }

  @SubscribeEvent
  public static void onRenderFog(ViewportEvent.RenderFog event) {
    AtmosphereFrame f = sampleFrame();
    if (0.001f >= f.activeAlpha || f.fogDistanceScale >= 0.999f) return;

    // Percentage-based fog compression (scales proportionally with any render distance / Distant Horizons)
    float near = event.getNearPlaneDistance() * f.fogDistanceScale;
    float far = Math.max(6.0f, event.getFarPlaneDistance() * f.fogDistanceScale);
    event.setNearPlaneDistance(near);
    event.setFarPlaneDistance(far);
    event.setCanceled(true);
  }

  @SubscribeEvent
  public static void onRenderHeartAttackVignette(RenderGuiEvent.Post event) {
    AtmosphereFrame f = sampleFrame();
    if (0.005f >= f.veinVignetteAlpha) return;

    Minecraft mc = Minecraft.getInstance();
    int w = mc.getWindow().getGuiScaledWidth();
    int h = mc.getWindow().getGuiScaledHeight();
    float alpha = f.veinVignetteAlpha;

    GuiGraphics gui = event.getGuiGraphics();
    PoseStack ps = gui.pose();
    ps.pushPose();
    ps.translate(0.0f, 0.0f, 350.0f);

    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.disableDepthTest();
    RenderSystem.setShader(GameRenderer::getPositionColorShader);

    Tesselator tess = Tesselator.getInstance();
    BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    Matrix4f mat = ps.last().pose();

    int edgeAlpha = Math.round(215 * alpha);
    float borderX = w * 0.24f;
    float borderY = h * 0.24f;

    // 1. Deep Crimson Screen-Edge Vignette Frame (Top, Bottom, Left, Right)
    addGradientQuad(buf, mat, 0, 0, w, 0, w, borderY, 0, borderY, 0x66000D, edgeAlpha, 0x330005, 0);
    addGradientQuad(buf, mat, 0, h - borderY, w, h - borderY, w, h, 0, h, 0x330005, 0, 0x66000D, edgeAlpha);
    addGradientQuad(buf, mat, 0, 0, borderX, 0, borderX, h, 0, h, 0x66000D, edgeAlpha, 0x330005, 0);
    addGradientQuad(buf, mat, w - borderX, 0, w, 0, w, h, w - borderX, h, 0x330005, 0, 0x66000D, edgeAlpha);

    // 2. Branching Blood-Red Capillary Tendrils creeping inward from screen corners & edges
    int tendrilCount = 36;
    for (int i = 0; tendrilCount > i; i++) {
      double angle = (Math.PI * 2.0D * i) / tendrilCount;
      float cos = (float) Math.cos(angle);
      float sin = (float) Math.sin(angle);

      // Anchor on screen border
      float sx = (w * 0.5f) + cos * (w * 0.52f);
      float sy = (h * 0.5f) + sin * (h * 0.52f);
      float reach = (0.22f + 0.14f * (float) Math.sin(i * 2.7D)) * alpha;

      float curX = sx;
      float curY = sy;
      for (int seg = 0; 5 > seg; seg++) {
        float jitter = (float) Math.sin((i * 1.9D) + (seg * 2.3D)) * 12.0f;
        float nextX = curX - (cos * w * (reach / 5.0f)) - (sin * jitter);
        float nextY = curY - (sin * h * (reach / 5.0f)) + (cos * jitter);
        float thick = (5.5f - seg * 0.9f);
        float px = -sin * thick * 0.5f;
        float py = cos * thick * 0.5f;

        int a0 = Math.max(0, Math.round((230 - seg * 40) * alpha));
        int a1 = Math.max(0, Math.round((190 - seg * 42) * alpha));
        addGradientQuad(buf, mat, curX - px, curY - py, curX + px, curY + py, nextX + px * 0.5f, nextY + py * 0.5f, nextX - px * 0.5f, nextY - py * 0.5f, 0xB8001F, a0, 0xFF1E56, a1);

        curX = nextX;
        curY = nextY;
      }
    }

    BufferUploader.drawWithShader(buf.buildOrThrow());
    RenderSystem.enableDepthTest();
    ps.popPose();
  }

  private static void addGradientQuad(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int rgb0, int a0, int rgb1, int a1) {
    int r0 = (rgb0 >> 16) & 0xFF, g0 = (rgb0 >> 8) & 0xFF, bl0 = rgb0 & 0xFF;
    int r1 = (rgb1 >> 16) & 0xFF, g1 = (rgb1 >> 8) & 0xFF, bl1 = rgb1 & 0xFF;
    b.addVertex(m, x0, y0, 0).setColor(r0, g0, bl0, a0);
    b.addVertex(m, x1, y1, 0).setColor(r0, g0, bl0, a0);
    b.addVertex(m, x2, y2, 0).setColor(r1, g1, bl1, a1);
    b.addVertex(m, x3, y3, 0).setColor(r1, g1, bl1, a1);
  }

  private static float smoothStep(float x) {
    return x * x * (3.0f - 2.0f * x);
  }

  private static int lerpRgb(int c0, int c1, float t) {
    int r = Mth.lerpInt(t, (c0 >> 16) & 0xFF, (c1 >> 16) & 0xFF);
    int g = Mth.lerpInt(t, (c0 >> 8) & 0xFF, (c1 >> 8) & 0xFF);
    int b = Mth.lerpInt(t, c0 & 0xFF, c1 & 0xFF);
    return (r << 16) | (g << 8) | b;
  }
}
