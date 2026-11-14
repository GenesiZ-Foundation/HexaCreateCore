package com.genesiz.hexacreatecore.hexcasting.everbook;

import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import static com.genesiz.hexacreatecore.hexcasting.everbook.EverbookRiftMath.Point2D;

public final class EverbookNebulaPortal {

  private EverbookNebulaPortal() {}

  public static void renderPortal(VertexConsumer vc, Matrix4f mat, float cx, float cy, float halfW, float halfH, float timeSec, int cols, int rows, boolean emeraldTint, boolean crimsonTint) {
    int angularSegs = Math.max(48, cols * 2);
    int radialRings = Math.max(10, rows / 2);

    for (int s = 0; angularSegs > s; s++) {
      double a0 = (Math.PI * 2.0D * s) / angularSegs;
      double a1 = (Math.PI * 2.0D * (s + 1)) / angularSegs;

      float tear0 = 1.11f + 0.032f * (float) Math.sin((a0 * 9.0D) - (timeSec * 3.8f));
      float tear1 = 1.11f + 0.032f * (float) Math.sin((a1 * 9.0D) - (timeSec * 3.8f));

      for (int ring = 0; radialRings > ring; ring++) {
        float tIn = (float) ring / radialRings;
        float tOut = (float) (ring + 1) / radialRings;

        float r0In = tIn * tear0, r0Out = tOut * tear0;
        float r1In = tIn * tear1, r1Out = tOut * tear1;

        Point2D p00 = evalSuperellipse(cx, cy, halfW, halfH, a0, r0In, timeSec);
        Point2D p10 = evalSuperellipse(cx, cy, halfW, halfH, a1, r1In, timeSec);
        Point2D p11 = evalSuperellipse(cx, cy, halfW, halfH, a1, r1Out, timeSec);
        Point2D p01 = evalSuperellipse(cx, cy, halfW, halfH, a0, r0Out, timeSec);

        RawGeometry.vertex(vc, mat, p00.x(), p00.y(), 0.0f, sampleUnifiedColor(p00.x(), p00.y(), a0, tIn, timeSec, emeraldTint, crimsonTint));
        RawGeometry.vertex(vc, mat, p10.x(), p10.y(), 0.0f, sampleUnifiedColor(p10.x(), p10.y(), a1, tIn, timeSec, emeraldTint, crimsonTint));
        RawGeometry.vertex(vc, mat, p11.x(), p11.y(), 0.0f, sampleUnifiedColor(p11.x(), p11.y(), a1, tOut, timeSec, emeraldTint, crimsonTint));
        RawGeometry.vertex(vc, mat, p01.x(), p01.y(), 0.0f, sampleUnifiedColor(p01.x(), p01.y(), a0, tOut, timeSec, emeraldTint, crimsonTint));
      }
    }
  }

  private static Point2D evalSuperellipse(float cx, float cy, float halfW, float halfH, double angle, float scaleMult, float timeSec) {
    float wobble = 1.0f + (float) Math.sin((angle * 4.0D) + (timeSec * 1.9f)) * 0.038f
    + (float) Math.cos((angle * 7.0D) - (timeSec * 2.5f)) * 0.022f + (float) Math.sin((angle * 11.0D) + (timeSec * 3.2f)) * 0.012f;
    double cos = Math.cos(angle), sin = Math.sin(angle);
    double norm = Math.pow(Math.pow(Math.abs(cos), 4.5D) + Math.pow(Math.abs(sin), 4.5D), 1.0D / 4.5D);
    return new Point2D(cx + (float) ((cos / norm) * halfW * wobble * scaleMult), cy + (float) ((sin / norm) * halfH * wobble * scaleMult));
  }

  private static int sampleUnifiedColor(float x, float y, double angle, float radialT, float timeSec, boolean emeraldTint, boolean crimsonTint) {
    // 1. Interior 3-direction drifting ocean nebula
    float d1 = 0.5f + 0.5f * (float) Math.sin((x * 2.6f + y * 1.9f) + (timeSec * 1.45f));
    float d2 = 0.5f + 0.5f * (float) Math.cos((-x * 2.2f + y * 2.8f) - (timeSec * 1.15f));
    float d3 = 0.5f + 0.5f * (float) Math.sin((x * 1.5f - y * 3.3f) + (timeSec * 1.85f));
    int oceanBase = lerpArgb(0xD8090414, 0xD84C1D95, (d1 * 0.42f) + (d2 * 0.35f) + (d3 * 0.23f));
    int interior = lerpArgb(oceanBase, lerpArgb(0xD886198F, 0xD80D9488, d2), d3 * 0.48f);
    if (emeraldTint) interior = lerpArgb(interior, 0xCC10B981, 0.35f);
    if (crimsonTint) interior = lerpArgb(interior, 0xCCFF1E56, 0.45f);

    // 2. Smooth aurora rim blend near outer edge (radialT 0.76 -> 0.90 -> 1.00)
    float rimWave = 0.5f + 0.5f * (float) Math.sin((angle * 3.0D) + (timeSec * 2.4f));
    int auroraPeak = crimsonTint ? 0xCCFF1E56 : (emeraldTint ? 0xCC34D399 : lerpArgb(0xCCC026D3, 0xCC34D399, rimWave));

    float rimMix = Mth.clamp((radialT - 0.74f) / 0.16f, 0.0f, 1.0f);
    int blended = lerpArgb(interior, auroraPeak, rimMix * rimMix);

    // 3. Smooth outer alpha fade to 0 at radialT = 1.0
    float outerFade = (radialT > 0.88f) ? Mth.clamp((1.0f - radialT) / 0.12f, 0.0f, 1.0f) : 1.0f;
    return (blended & 0x00FFFFFF) | ((int) (FastColor.ARGB32.alpha(blended) * outerFade) << 24);
  }

  private static int lerpArgb(int c0, int c1, float t) {
    return FastColor.ARGB32.color(Mth.lerpInt(t, FastColor.ARGB32.alpha(c0), FastColor.ARGB32.alpha(c1)),
      Mth.lerpInt(t, FastColor.ARGB32.red(c0), FastColor.ARGB32.red(c1)),
      Mth.lerpInt(t, FastColor.ARGB32.green(c0), FastColor.ARGB32.green(c1)),
      Mth.lerpInt(t, FastColor.ARGB32.blue(c0), FastColor.ARGB32.blue(c1)));
  }
}
