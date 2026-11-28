package com.genesiz.hexacreatecore.threedified;

import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class ThreedifiedLod {

  private ThreedifiedLod() {}

  /**
   * Returns a [0.0 .. 1.0] detail factor based on projected screen size (radius / distance).
   * Returns 0.0f when the volume is sub-pixel or far beyond useful visual range (caller can skip rendering).
   */
  public static float computeScreenLod(Matrix4f poseMat, float localX, float localY, float localZ, float worldRadius) {
    float wx = poseMat.m00() * localX + poseMat.m10() * localY + poseMat.m20() * localZ + poseMat.m30();
    float wy = poseMat.m01() * localX + poseMat.m11() * localY + poseMat.m21() * localZ + poseMat.m31();
    float wz = poseMat.m02() * localX + poseMat.m12() * localY + poseMat.m22() * localZ + poseMat.m32();

    // Account for PoseStack scale
    float sxSq = poseMat.m00() * poseMat.m00() + poseMat.m01() * poseMat.m01() + poseMat.m02() * poseMat.m02();
    float scaledRadius = Math.max(0.05f, worldRadius * (float) Math.sqrt(Math.max(1.0e-4f, sxSq)));

    float distSq = (wx * wx) + (wy * wy) + (wz * wz);
    if (distSq <= (scaledRadius * scaledRadius * 4.0f)) {
      return 1.0f; // Inside or right next to camera
    }

    float invDist = Mth.invSqrt(distSq);
    float angularSize = scaledRadius * invDist; // ~tan(halfAngle)

    // Sub-pixel / tiny speck cutoff (~< 2px on 1080p) -> cull completely
    if (angularSize < 0.0022f) {
      return 0.0f;
    }

    // Smoothly ramps from 0.0 (at 0.0022 rad) to 1.0 (at >= 0.085 rad, e.g. 1.7m radius at 20m distance)
    float t = Mth.clamp((angularSize - 0.0022f) / (0.085f - 0.0022f), 0.0f, 1.0f);
    return Mth.sqrt(t);
  }

  public static int scaleSegments(int baseCount, int minCount, float lod) {
    if (lod >= 0.98f) return baseCount;
    return Math.max(minCount, Math.round(Mth.lerp(lod, (float) minCount, (float) baseCount)));
  }

  /** Returns grid step stride (1 = full res, 2 = quarter quads, 3 = ninth quads). */
  public static int gridStep(float lod) {
    if (lod >= 0.62f) return 1;
    if (lod >= 0.28f) return 2;
    return 3;
  }

  public static float viewDot(Matrix4f poseMat, float localX, float localY, float localZ, Vector3f localOffset) {
    float wx = poseMat.m00() * localX + poseMat.m10() * localY + poseMat.m20() * localZ + poseMat.m30();
    float wy = poseMat.m01() * localX + poseMat.m11() * localY + poseMat.m21() * localZ + poseMat.m31();
    float wz = poseMat.m02() * localX + poseMat.m12() * localY + poseMat.m22() * localZ + poseMat.m32();
    return (wx * localOffset.x) + (wy * localOffset.y) + (wz * localOffset.z);
  }
}

