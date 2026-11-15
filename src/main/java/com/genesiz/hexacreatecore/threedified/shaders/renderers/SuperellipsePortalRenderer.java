package com.genesiz.hexacreatecore.threedified.shaders.renderers;

import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.ShaderDictionary;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

public final class SuperellipsePortalRenderer {

  private SuperellipsePortalRenderer() {}

  public static void render(
    VertexConsumer vertexConsumer, Matrix4f poseMatrix,
    float centerX, float centerY, float halfWidth, float halfHeight,
    float timeSeconds, int angularSegments, int radialRings,
    ShaderDictionary dictionary
  ) {
    int segs = Math.max(36, angularSegments);
    int rings = Math.max(6, radialRings);

    for (int segment = 0; segs > segment; segment++) {
      double angle0 = (Math.PI * 2.0D * segment) / segs;
      double angle1 = (Math.PI * 2.0D * (segment + 1)) / segs;

      float tear0 = 1.11f + 0.032f * (float) Math.sin((angle0 * 9.0D) - (timeSeconds * 3.8f));
      float tear1 = 1.11f + 0.032f * (float) Math.sin((angle1 * 9.0D) - (timeSeconds * 3.8f));

      for (int ring = 0; rings > ring; ring++) {
        float tInner = (float) ring / rings;
        float tOuter = (float) (ring + 1) / rings;

        float[] p00 = evalSuperellipse(centerX, centerY, halfWidth, halfHeight, angle0, tInner * tear0, timeSeconds);
        float[] p10 = evalSuperellipse(centerX, centerY, halfWidth, halfHeight, angle1, tInner * tear1, timeSeconds);
        float[] p11 = evalSuperellipse(centerX, centerY, halfWidth, halfHeight, angle1, tOuter * tear1, timeSeconds);
        float[] p01 = evalSuperellipse(centerX, centerY, halfWidth, halfHeight, angle0, tOuter * tear0, timeSeconds);

        RawGeometry.vertex(vertexConsumer, poseMatrix, p00[0], p00[1], 0.0f, dictionary.sampleArgb(p00[0], p00[1], angle0, tInner, timeSeconds));
        RawGeometry.vertex(vertexConsumer, poseMatrix, p10[0], p10[1], 0.0f, dictionary.sampleArgb(p10[0], p10[1], angle1, tInner, timeSeconds));
        RawGeometry.vertex(vertexConsumer, poseMatrix, p11[0], p11[1], 0.0f, dictionary.sampleArgb(p11[0], p11[1], angle1, tOuter, timeSeconds));
        RawGeometry.vertex(vertexConsumer, poseMatrix, p01[0], p01[1], 0.0f, dictionary.sampleArgb(p01[0], p01[1], angle0, tOuter, timeSeconds));
      }
    }
  }

  private static float[] evalSuperellipse(float cx, float cy, float halfW, float halfH, double angle, float scaleMult, float timeSec) {
    float wobble = 1.0f
    + (float) Math.sin((angle * 4.0D) + (timeSec * 1.9f)) * 0.038f
    + (float) Math.cos((angle * 7.0D) - (timeSec * 2.5f)) * 0.022f
    + (float) Math.sin((angle * 11.0D) + (timeSec * 3.2f)) * 0.012f;
    double cos = Math.cos(angle), sin = Math.sin(angle);
    double norm = Math.pow(Math.pow(Math.abs(cos), 4.5D) + Math.pow(Math.abs(sin), 4.5D), 1.0D / 4.5D);
    return new float[]{
      cx + (float) ((cos / norm) * halfW * wobble * scaleMult),
      cy + (float) ((sin / norm) * halfH * wobble * scaleMult)
    };
  }
}

