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
    render(
      vertexConsumer, poseMatrix, centerX, centerY, halfWidth, halfHeight,
      timeSeconds, angularSegments, radialRings, false, dictionary
    );
  }

  public static void render(
    VertexConsumer vertexConsumer, Matrix4f poseMatrix,
    float centerX, float centerY, float halfWidth, float halfHeight,
    float timeSeconds, int angularSegments, int radialRings,
    boolean circular, ShaderDictionary dictionary
  ) {
    int segs = Math.max(28, angularSegments);
    int rings = Math.max(5, radialRings);
    int ringStride = rings + 1;

    float[] gridX = new float[(segs + 1) * ringStride];
    float[] gridY = new float[(segs + 1) * ringStride];
    int[] gridCol = new int[(segs + 1) * ringStride];

    for (int s = 0; segs >= s; s++) {
      double angle = (Math.PI * 2.0D * s) / segs;
      float tear = circular ? 1.0f : (1.11f + 0.032f * (float) Math.sin((angle * 9.0D) - (timeSeconds * 3.8f)));
      float wobble = circular ? 1.0f : (
        1.0f
        + (float) Math.sin((angle * 4.0D) + (timeSeconds * 1.9f)) * 0.038f
        + (float) Math.cos((angle * 7.0D) - (timeSeconds * 2.5f)) * 0.022f
        + (float) Math.sin((angle * 11.0D) + (timeSeconds * 3.2f)) * 0.012f
      );

      double cos = Math.cos(angle);
      double sin = Math.sin(angle);
      double norm = 1.0D;
      if (!circular) {
        double ac = Math.abs(cos);
        double as = Math.abs(sin);
        double ac2 = ac * ac;
        double as2 = as * as;
        norm = Math.pow((ac2 * ac2 * Math.sqrt(ac)) + (as2 * as2 * Math.sqrt(as)), 0.22222222D);
      }

      float rayX = (float) ((cos / norm) * halfWidth * wobble * tear);
      float rayY = (float) ((sin / norm) * halfHeight * wobble * tear);

      int rowBase = s * ringStride;
      for (int r = 0; rings >= r; r++) {
        float t = (float) r / rings;
        float vx = centerX + rayX * t;
        float vy = centerY + rayY * t;
        int idx = rowBase + r;
        gridX[idx] = vx;
        gridY[idx] = vy;
        gridCol[idx] = dictionary.sampleArgb(vx, vy, angle, t, timeSeconds);
      }
    }

    for (int s = 0; segs > s; s++) {
      int r0 = s * ringStride;
      int r1 = (s + 1) * ringStride;
      for (int r = 0; rings > r; r++) {
        int i00 = r0 + r;
        int i10 = r1 + r;
        int i11 = i10 + 1;
        int i01 = i00 + 1;

        RawGeometry.vertex(vertexConsumer, poseMatrix, gridX[i00], gridY[i00], 0.0f, gridCol[i00]);
        RawGeometry.vertex(vertexConsumer, poseMatrix, gridX[i10], gridY[i10], 0.0f, gridCol[i10]);
        RawGeometry.vertex(vertexConsumer, poseMatrix, gridX[i11], gridY[i11], 0.0f, gridCol[i11]);
        RawGeometry.vertex(vertexConsumer, poseMatrix, gridX[i01], gridY[i01], 0.0f, gridCol[i01]);
      }
    }
  }
}
