package com.genesiz.hexacreatecore.threedified.shaders.renderers;

import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.ShaderDictionary;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public final class VolumetricSingularityRenderer {

  private VolumetricSingularityRenderer() {}

  public static void render(
    PoseStack ps,
    VertexConsumer vc,
    float centerX,
    float centerY,
    float centerZ,
    float halfWidth,
    float halfHeight,
    float halfDepth,
    float timeSeconds,
    int angularSegments,
    int radialRings,
    ShaderDictionary dictionary
  ) {
    int segs = Math.max(16, angularSegments);
    int rings = Math.max(6, radialRings);

    // 1. Suspended 3D Dark Singularity Core Cube (Pixel Gun 3D inspired rotating dark-matter core)
    float coreSize = Math.min(halfWidth, Math.min(halfHeight, halfDepth)) * 0.68f;
    ps.pushPose();
    ps.translate(centerX, centerY, centerZ);
    ps.mulPose(Axis.YP.rotationDegrees((timeSeconds * 48.0f) % 360.0f));
    ps.mulPose(Axis.XP.rotationDegrees((timeSeconds * 31.0f) % 360.0f));
    ps.mulPose(Axis.ZP.rotationDegrees((timeSeconds * 19.0f) % 360.0f));
    RawGeometry.drawPrism(vc, ps.last().pose(), coreSize, coreSize, coreSize, 0xFF020205);
    ps.popPose();

    // 2. 3D Volumetric Event Horizon & Lensed Accretion Shell (evaluated across 3D superellipse volume)
    Matrix4f m = ps.last().pose();
    float exponent = 0.42f; // Rounded-square Minecraft superellipse profile

    for (int r = 0; rings > r; r++) {
      float t0 = (float) r / rings;
      float t1 = (float) (r + 1) / rings;

      // Dome profile: wraps around the 3D core at t=0 and tapers smoothly to z=centerZ at t=1
      float zDome0 = halfDepth * (float) Math.sqrt(Math.max(0.0f, 1.0f - (t0 * t0)));
      float zDome1 = halfDepth * (float) Math.sqrt(Math.max(0.0f, 1.0f - (t1 * t1)));

      for (int s = 0; segs > s; s++) {
        double a0 = (s * Math.PI * 2.0D) / segs;
        double a1 = ((s + 1) * Math.PI * 2.0D) / segs;

        float sx0 = superellipseAxis(a0, exponent);
        float sy0 = superellipseAxis(a0 - Math.PI * 0.5D, exponent);
        float sx1 = superellipseAxis(a1, exponent);
        float sy1 = superellipseAxis(a1 - Math.PI * 0.5D, exponent);

        float x00 = centerX + sx0 * halfWidth * t0;
        float y00 = centerY + sy0 * halfHeight * t0;
        float x10 = centerX + sx1 * halfWidth * t0;
        float y10 = centerY + sy1 * halfHeight * t0;

        float x01 = centerX + sx0 * halfWidth * t1;
        float y01 = centerY + sy0 * halfHeight * t1;
        float x11 = centerX + sx1 * halfWidth * t1;
        float y11 = centerY + sy1 * halfHeight * t1;

        int col00 = dictionary.sampleArgb(sx0 * t0, sy0 * t0, a0, t0, timeSeconds);
        int col10 = dictionary.sampleArgb(sx1 * t0, sy1 * t0, a1, t0, timeSeconds);
        int col01 = dictionary.sampleArgb(sx0 * t1, sy0 * t1, a0, t1, timeSeconds);
        int col11 = dictionary.sampleArgb(sx1 * t1, sy1 * t1, a1, t1, timeSeconds);

        // Front 3D Event Horizon Hemisphere (+Z)
        RawGeometry.vertex(vc, m, x00, y00, centerZ + zDome0, col00);
        RawGeometry.vertex(vc, m, x10, y10, centerZ + zDome0, col10);
        RawGeometry.vertex(vc, m, x11, y11, centerZ + zDome1, col11);
        RawGeometry.vertex(vc, m, x01, y01, centerZ + zDome1, col01);

        // Back 3D Event Horizon Hemisphere (-Z)
        RawGeometry.vertex(vc, m, x10, y10, centerZ - zDome0, col10);
        RawGeometry.vertex(vc, m, x00, y00, centerZ - zDome0, col00);
        RawGeometry.vertex(vc, m, x01, y01, centerZ - zDome1, col01);
        RawGeometry.vertex(vc, m, x11, y11, centerZ - zDome1, col11);
      }
    }
  }

  private static float superellipseAxis(double angleRad, float exponent) {
    double c = Math.cos(angleRad);
    return (float) (Math.signum(c) * Math.pow(Math.abs(c), exponent));
  }
}
