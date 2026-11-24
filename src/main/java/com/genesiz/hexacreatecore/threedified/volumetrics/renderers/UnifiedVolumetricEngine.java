package com.genesiz.hexacreatecore.threedified.volumetrics.renderers;

import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.genesiz.hexacreatecore.threedified.volumetrics.dictionaries.BlackHoleVolumetricDictionary;
import com.genesiz.hexacreatecore.threedified.volumetrics.dictionaries.VolumetricDictionary;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class UnifiedVolumetricEngine {

  private UnifiedVolumetricEngine() {}

  public static void renderBoxVolume(
    PoseStack ps,
    MultiBufferSource bufferSource,
    float centerX, float centerY, float centerZ,
    float coreHalfW, float coreHalfH, float coreHalfD,
    float horizonThickness,
    int shellCount,
    float timeSeconds,
    VolumetricDictionary dictionary
  ) {
    renderBoxVolume(
      ps, bufferSource, centerX, centerY, centerZ,
      coreHalfW, coreHalfH, coreHalfD, horizonThickness, shellCount,
      timeSeconds, dictionary, false
    );
  }

  public static void renderBoxVolume(
    PoseStack ps,
    MultiBufferSource bufferSource,
    float centerX, float centerY, float centerZ,
    float coreHalfW, float coreHalfH, float coreHalfD,
    float horizonThickness,
    int shellCount,
    float timeSeconds,
    VolumetricDictionary dictionary,
    boolean spherical
  ) {
    RenderSystem.enableDepthTest();
    RenderSystem.enableCull();

    RenderSystem.disableBlend();
    RenderSystem.depthMask(true);
    renderBoxCoreOnly(ps, bufferSource, centerX, centerY, centerZ, coreHalfW, coreHalfH, coreHalfD, timeSeconds, dictionary, spherical);
    if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
      bs.endBatch(RawGeometry.DIALOGUE_SOLID);
    }

    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.depthMask(false);
    renderBoxHorizonOnly(ps, bufferSource, centerX, centerY, centerZ, coreHalfW, coreHalfH, coreHalfD, horizonThickness, shellCount, timeSeconds, dictionary, spherical);
    if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
      bs.endBatch(RawGeometry.DIALOGUE_SOLID);
    }

    RenderSystem.depthMask(true);
  }

  public static void renderBoxCoreOnly(
    PoseStack ps,
    MultiBufferSource bufferSource,
    float centerX, float centerY, float centerZ,
    float coreHalfW, float coreHalfH, float coreHalfD,
    float timeSeconds,
    VolumetricDictionary dictionary
  ) {
    renderBoxCoreOnly(ps, bufferSource, centerX, centerY, centerZ, coreHalfW, coreHalfH, coreHalfD, timeSeconds, dictionary, false);
  }

  public static void renderBoxCoreOnly(
    PoseStack ps,
    MultiBufferSource bufferSource,
    float centerX, float centerY, float centerZ,
    float coreHalfW, float coreHalfH, float coreHalfD,
    float timeSeconds,
    VolumetricDictionary dictionary,
    boolean spherical
  ) {
    ps.pushPose();
    ps.translate(centerX, centerY, centerZ);

    Matrix4f poseMat = ps.last().pose();
    Matrix3f normMat = ps.last().normal();
    Vector3f viewDir = computeViewDir(poseMat);

    int coreArgb = (dictionary instanceof BlackHoleVolumetricDictionary bh) ? bh.coreColor() : 0xFF020205;
    VertexConsumer coreVc = bufferSource.getBuffer(RawGeometry.DIALOGUE_SOLID);
    drawFeatheredSuperellipsoid(
      coreVc, poseMat, normMat, viewDir,
      coreHalfW * 0.92f, coreHalfH * 0.92f, coreHalfD * 0.92f,
      0.0f, timeSeconds, coreArgb | 0xFF000000, true, spherical, null
    );
    ps.popPose();
  }

  public static void renderBoxHorizonOnly(
    PoseStack ps,
    MultiBufferSource bufferSource,
    float centerX, float centerY, float centerZ,
    float coreHalfW, float coreHalfH, float coreHalfD,
    float horizonThickness,
    int shellCount,
    float timeSeconds,
    VolumetricDictionary dictionary
  ) {
    renderBoxHorizonOnly(ps, bufferSource, centerX, centerY, centerZ, coreHalfW, coreHalfH, coreHalfD, horizonThickness, shellCount, timeSeconds, dictionary, false);
  }

  public static void renderBoxHorizonOnly(
    PoseStack ps,
    MultiBufferSource bufferSource,
    float centerX, float centerY, float centerZ,
    float coreHalfW, float coreHalfH, float coreHalfD,
    float horizonThickness,
    int shellCount,
    float timeSeconds,
    VolumetricDictionary dictionary,
    boolean spherical
  ) {
    ps.pushPose();
    ps.translate(centerX, centerY, centerZ);

    Matrix4f poseMat = ps.last().pose();
    Matrix3f normMat = ps.last().normal();
    Vector3f viewDir = computeViewDir(poseMat);

    int coreArgb = (dictionary instanceof BlackHoleVolumetricDictionary bh) ? bh.coreColor() : 0xFF020205;
    int shells = Math.max(3, shellCount);
    VertexConsumer shellVc = bufferSource.getBuffer(RawGeometry.DIALOGUE_SOLID);

    for (int i = 1; shells >= i; i++) {
      float shellT = (float) i / shells;
      float expand = horizonThickness * shellT;
      float pulse = 1.0f + 0.02f * (float) Math.sin(timeSeconds * 3.6D - shellT * 2.4D);

      float hw = (coreHalfW + expand) * pulse;
      float hh = (coreHalfH + expand) * pulse;
      float hd = (coreHalfD + expand * 0.95f) * pulse;

      drawFeatheredSuperellipsoid(
        shellVc, poseMat, normMat, viewDir,
        hw, hh, hd,
        shellT, timeSeconds, coreArgb, false, spherical, dictionary
      );
    }
    ps.popPose();
  }


  private static Vector3f computeViewDir(Matrix4f poseMat) {
    Vector3f viewDir = new Vector3f(-poseMat.m30(), -poseMat.m31(), -poseMat.m32());
    if (viewDir.lengthSquared() > 1.0e-6f) {
      viewDir.normalize();
    } else {
      viewDir.set(0.0f, 0.0f, 1.0f);
    }
    return viewDir;
  }

  private static final int SE_STACKS = 12;
  private static final int SE_SLICES = 20;
  private static final float[] SE_COS_PHI = new float[SE_STACKS + 1];
  private static final float[] SE_SIN_PHI = new float[SE_STACKS + 1];
  private static final float[] SE_COS_THETA = new float[SE_SLICES + 1];
  private static final float[] SE_SIN_THETA = new float[SE_SLICES + 1];
  private static final float[] SPH_COS_PHI = new float[SE_STACKS + 1];
  private static final float[] SPH_SIN_PHI = new float[SE_STACKS + 1];
  private static final float[] SPH_COS_THETA = new float[SE_SLICES + 1];
  private static final float[] SPH_SIN_THETA = new float[SE_SLICES + 1];

  static {
    final float exp = 0.48f;
    for (int i = 0; SE_STACKS >= i; i++) {
      double phi = -Math.PI * 0.5D + (Math.PI * i) / SE_STACKS;
      SE_COS_PHI[i] = signedPow(Math.cos(phi), exp);
      SE_SIN_PHI[i] = signedPow(Math.sin(phi), exp);
      SPH_COS_PHI[i] = (float) Math.cos(phi);
      SPH_SIN_PHI[i] = (float) Math.sin(phi);
    }
    for (int j = 0; SE_SLICES >= j; j++) {
      double theta = (Math.PI * 2.0D * j) / SE_SLICES;
      SE_COS_THETA[j] = signedPow(Math.cos(theta), exp);
      SE_SIN_THETA[j] = signedPow(Math.sin(theta), exp);
      SPH_COS_THETA[j] = (float) Math.cos(theta);
      SPH_SIN_THETA[j] = (float) Math.sin(theta);
    }
  }

  private static void drawFeatheredSuperellipsoid(
    VertexConsumer vc,
    Matrix4f poseMat,
    Matrix3f normMat,
    Vector3f viewDir,
    float hw, float hh, float hd,
    float shellT,
    float timeSeconds,
    int coreArgb,
    boolean isCore,
    boolean spherical,
    VolumetricDictionary dict
  ) {
    float[] cosPhiArr = spherical ? SPH_COS_PHI : SE_COS_PHI;
    float[] sinPhiArr = spherical ? SPH_SIN_PHI : SE_SIN_PHI;
    float[] cosThetaArr = spherical ? SPH_COS_THETA : SE_COS_THETA;
    float[] sinThetaArr = spherical ? SPH_SIN_THETA : SE_SIN_THETA;

    Vector3f tmpNorm = new Vector3f();
    int stride = SE_SLICES + 1;
    int[] cols = new int[(SE_STACKS + 1) * stride];

    for (int i = 0; SE_STACKS >= i; i++) {
      float cosPhi = cosPhiArr[i];
      float sinPhi = sinPhiArr[i];
      int row = i * stride;
      for (int j = 0; SE_SLICES >= j; j++) {
        float nx = cosPhi * cosThetaArr[j];
        float nz = cosPhi * sinThetaArr[j];
        cols[row + j] = shadeVertex(nx, sinPhi, nz, shellT, timeSeconds, coreArgb, isCore, dict, normMat, viewDir, tmpNorm);
      }
    }

    for (int i = 0; SE_STACKS > i; i++) {
      float cosPhi0 = cosPhiArr[i];
      float sinPhi0 = sinPhiArr[i];
      float cosPhi1 = cosPhiArr[i + 1];
      float sinPhi1 = sinPhiArr[i + 1];
      int r0 = i * stride;
      int r1 = (i + 1) * stride;

      for (int j = 0; SE_SLICES > j; j++) {
        int c00 = cols[r0 + j];
        int c10 = cols[r0 + j + 1];
        int c11 = cols[r1 + j + 1];
        int c01 = cols[r1 + j];
        if (((c00 | c10 | c11 | c01) >>> 24) == 0) continue;

        float cosT0 = cosThetaArr[j];
        float sinT0 = sinThetaArr[j];
        float cosT1 = cosThetaArr[j + 1];
        float sinT1 = sinThetaArr[j + 1];

        RawGeometry.vertex(vc, poseMat, cosPhi0 * cosT0 * hw, sinPhi0 * hh, cosPhi0 * sinT0 * hd, c00);
        RawGeometry.vertex(vc, poseMat, cosPhi1 * cosT0 * hw, sinPhi1 * hh, cosPhi1 * sinT0 * hd, c01);
        RawGeometry.vertex(vc, poseMat, cosPhi1 * cosT1 * hw, sinPhi1 * hh, cosPhi1 * sinT1 * hd, c11);
        RawGeometry.vertex(vc, poseMat, cosPhi0 * cosT1 * hw, sinPhi0 * hh, cosPhi0 * sinT1 * hd, c10);
      }
    }
  }

  private static int shadeVertex(
    float nx, float ny, float nz,
    float shellT, float timeSeconds,
    int coreArgb, boolean isCore,
    VolumetricDictionary dict,
    Matrix3f normMat, Vector3f viewDir, Vector3f tmpNorm
  ) {
    tmpNorm.set(nx, ny, nz);
    normMat.transform(tmpNorm);
    float lenSq = tmpNorm.lengthSquared();
    float signedDot = (lenSq > 1.0e-6f) ? (tmpNorm.dot(viewDir) * Mth.invSqrt(lenSq)) : 1.0f;

    if (-0.22f > signedDot) {
      return 0x00000000;
    }

    float ndotv = Math.abs(signedDot);

    if (isCore) {
      float coreFade = (float) Math.sqrt(ndotv);
      int alpha = (int) (255.0f * Mth.clamp(0.45f + coreFade * 0.55f, 0.0f, 1.0f));
      return (coreArgb & 0x00FFFFFF) | (alpha << 24);
    }

    float rimMask = Mth.clamp(1.0f - (ndotv * 1.48f), 0.0f, 1.0f);
    if (0.004f >= rimMask) {
      return 0x00000000;
    }

    float shellFade = (rimMask * (0.82f + 0.18f * rimMask)) * (1.0f - shellT * 0.72f);
    int sampled = dict.sampleShellArgb(nx, ny, nz, shellT, timeSeconds);
    int baseAlpha = (sampled >>> 24) & 0xFF;
    int featheredAlpha = (int) (baseAlpha * Mth.clamp(shellFade * 1.35f, 0.0f, 1.0f));
    return (sampled & 0x00FFFFFF) | (featheredAlpha << 24);
  }

  private static float signedPow(double val, float exp) {
    return (float) (Math.signum(val) * Math.pow(Math.abs(val), exp));
  }

  private static final int CROWN_SEGS = 32;
  private static final float[] CROWN_SX = new float[CROWN_SEGS + 1];
  private static final float[] CROWN_SY = new float[CROWN_SEGS + 1];
  private static final float[] CROWN_ANG = new float[CROWN_SEGS + 1];

  static {
    final float sqExp = 0.46f;
    for (int i = 0; CROWN_SEGS >= i; i++) {
      double a = (i * Math.PI * 2.0D) / CROWN_SEGS;
      CROWN_ANG[i] = (float) a;
      CROWN_SX[i] = signedPow(Math.cos(a), sqExp);
      CROWN_SY[i] = signedPow(Math.sin(a), sqExp);
    }
  }

  public static void renderRiftCrownVolume(
    PoseStack ps,
    MultiBufferSource bufferSource,
    float centerX, float centerY, float centerZ,
    float innerHalfW, float innerHalfH,
    float wallThickness,
    float crownDepth,
    int depthSlices,
    float timeSeconds,
    VolumetricDictionary dictionary
  ) {
    renderRiftCrownVolume(
      ps, bufferSource, centerX, centerY, centerZ,
      innerHalfW, innerHalfH, wallThickness, crownDepth, depthSlices,
      timeSeconds, dictionary, true
    );
  }

  public static void renderRiftCrownVolume(
    PoseStack ps,
    MultiBufferSource bufferSource,
    float centerX, float centerY, float centerZ,
    float innerHalfW, float innerHalfH,
    float wallThickness,
    float crownDepth,
    int depthSlices,
    float timeSeconds,
    VolumetricDictionary dictionary,
    boolean autoFlush
  ) {
    ps.pushPose();
    ps.translate(centerX, centerY, centerZ);

    Matrix4f m = ps.last().pose();
    final int segments = CROWN_SEGS;
    final int slices = Math.max(4, depthSlices);

    if (autoFlush) {
      RenderSystem.enableDepthTest();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableCull();
      RenderSystem.depthMask(false);
    }

    VertexConsumer vc = bufferSource.getBuffer(RawGeometry.DIALOGUE_SOLID);

    float[] wArr = new float[segments + 1];
    for (int i = 0; segments >= i; i++) {
      float a = CROWN_ANG[i];
      wArr[i] = (float) (Math.sin(timeSeconds * 4.2D + a * 4.0D) * 0.55D + Math.cos(timeSeconds * 2.9D - a * 6.0D) * 0.45D);
    }

    for (int zStep = 0; slices > zStep; zStep++) {
      float tZ0 = (float) zStep / slices;
      float tZ1 = (float) (zStep + 1) / slices;

      for (int i = 0; segments > i; i++) {
        float sx0 = CROWN_SX[i];
        float sy0 = CROWN_SY[i];
        float sx1 = CROWN_SX[i + 1];
        float sy1 = CROWN_SY[i + 1];

        float w0 = wArr[i];
        float w1 = wArr[i + 1];

        float z0_a = tZ0 * crownDepth * (0.86f + 0.22f * w0);
        float z0_b = tZ0 * crownDepth * (0.86f + 0.22f * w1);
        float z1_a = tZ1 * crownDepth * (0.86f + 0.22f * w0);
        float z1_b = tZ1 * crownDepth * (0.86f + 0.22f * w1);

        float inW0 = innerHalfW + w0 * 0.014f * tZ0;
        float inH0 = innerHalfH + w0 * 0.014f * tZ0;
        float inW1 = innerHalfW + w1 * 0.014f * tZ0;
        float inH1 = innerHalfH + w1 * 0.014f * tZ0;

        float inW0_next = innerHalfW + w0 * 0.014f * tZ1;
        float inH0_next = innerHalfH + w0 * 0.014f * tZ1;
        float inW1_next = innerHalfW + w1 * 0.014f * tZ1;
        float inH1_next = innerHalfH + w1 * 0.014f * tZ1;

        float thick0 = wallThickness * (1.0f - tZ0 * 0.25f) + w0 * 0.024f * tZ0;
        float thick1 = wallThickness * (1.0f - tZ1 * 0.25f) + w0 * 0.024f * tZ1;
        float thick0_b = wallThickness * (1.0f - tZ0 * 0.25f) + w1 * 0.024f * tZ0;
        float thick1_b = wallThickness * (1.0f - tZ1 * 0.25f) + w1 * 0.024f * tZ1;

        int col0 = dictionary.sampleShellArgb(sx0, sy0, tZ0, tZ0, timeSeconds);
        int col1 = dictionary.sampleShellArgb(sx0, sy0, tZ1, tZ1, timeSeconds);
        int col0_b = dictionary.sampleShellArgb(sx1, sy1, tZ0, tZ0, timeSeconds);
        int col1_b = dictionary.sampleShellArgb(sx1, sy1, tZ1, tZ1, timeSeconds);

        RawGeometry.vertex(vc, m, sx0 * (inW0 + thick0),       sy0 * (inH0 + thick0),       z0_a, col0);
        RawGeometry.vertex(vc, m, sx1 * (inW1 + thick0_b),     sy1 * (inH1 + thick0_b),     z0_b, col0_b);
        RawGeometry.vertex(vc, m, sx1 * (inW1_next + thick1_b), sy1 * (inH1_next + thick1_b), z1_b, col1_b);
        RawGeometry.vertex(vc, m, sx0 * (inW0_next + thick1),   sy0 * (inH0_next + thick1),   z1_a, col1);

        RawGeometry.vertex(vc, m, sx0 * inW0,      sy0 * inH0,      z0_a, col0);
        RawGeometry.vertex(vc, m, sx1 * inW1,      sy1 * inH1,      z0_b, col0_b);
        RawGeometry.vertex(vc, m, sx1 * inW1_next, sy1 * inH1_next, z1_b, col1_b);
        RawGeometry.vertex(vc, m, sx0 * inW0_next, sy0 * inH0_next, z1_a, col1);

        if (zStep == slices - 1) {
          RawGeometry.vertex(vc, m, sx0 * inW0_next,            sy0 * inH0_next,            z1_a, col1);
          RawGeometry.vertex(vc, m, sx1 * inW1_next,            sy1 * inH1_next,            z1_b, col1_b);
          RawGeometry.vertex(vc, m, sx1 * (inW1_next + thick1_b), sy1 * (inH1_next + thick1_b), z1_b, col1_b);
          RawGeometry.vertex(vc, m, sx0 * (inW0_next + thick1),   sy0 * (inH0_next + thick1),   z1_a, col1);
        }
      }
    }

    if (autoFlush) {
      if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
        bs.endBatch(RawGeometry.DIALOGUE_SOLID);
      }
      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
    }
    ps.popPose();
  }

  private static final int[] PERLIN_PERM = new int[256 + 256];
  private static final int OIL_GRID = 24;
  private static final int OIL_STRIDE = OIL_GRID + 1;
  private static final float[] OIL_GRID_Y = new float[OIL_STRIDE * OIL_STRIDE];
  private static final int[] OIL_GRID_COL = new int[OIL_STRIDE * OIL_STRIDE];

  static {
    int[] p = new int[16 * 16];
    for (int i = 0; 256 > i; i++) p[i] = i;
    int seed = 133742;
    for (int i = 255; i > 0; i--) {
      seed = (seed * 1664525) + 1013904223;
      int j = (seed >>> 16) % (i + 1);
      int tmp = p[i];
      p[i] = p[j];
      p[j] = tmp;
    }
    for (int i = 0; 512 > i; i++) {
      PERLIN_PERM[i] = p[i & 255];
    }
  }

  private static float fade(float t) {
    return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
  }

  private static float grad3(int hash, float x, float y, float z) {
    int h = hash & 15;
    float u = (8 > h) ? x : y;
    float v = (4 > h) ? y : ((h == 12 || h == 14) ? x : z);
    return (((h & 1) == 0) ? u : -u) + (((h & 2) == 0) ? v : -v);
  }

  private static float perlin3D(float x, float y, float z) {
    int X = Mth.floor(x) & 255;
    int Y = Mth.floor(y) & 255;
    int Z = Mth.floor(z) & 255;

    float fx = x - Mth.floor(x);
    float fy = y - Mth.floor(y);
    float fz = z - Mth.floor(z);

    float u = fade(fx);
    float v = fade(fy);
    float w = fade(fz);

    int A  = PERLIN_PERM[X] + Y;
    int AA = PERLIN_PERM[A] + Z;
    int AB = PERLIN_PERM[A + 1] + Z;
    int B  = PERLIN_PERM[X + 1] + Y;
    int BA = PERLIN_PERM[B] + Z;
    int BB = PERLIN_PERM[B + 1] + Z;

    float x1 = Mth.lerp(u, grad3(PERLIN_PERM[AA], fx, fy, fz),         grad3(PERLIN_PERM[BA], fx - 1.0f, fy, fz));
    float x2 = Mth.lerp(u, grad3(PERLIN_PERM[AB], fx, fy - 1.0f, fz),  grad3(PERLIN_PERM[BB], fx - 1.0f, fy - 1.0f, fz));
    float y1 = Mth.lerp(v, x1, x2);

    float x3 = Mth.lerp(u, grad3(PERLIN_PERM[AA + 1], fx, fy, fz - 1.0f),        grad3(PERLIN_PERM[BA + 1], fx - 1.0f, fy, fz - 1.0f));
    float x4 = Mth.lerp(u, grad3(PERLIN_PERM[AB + 1], fx, fy - 1.0f, fz - 1.0f), grad3(PERLIN_PERM[BB + 1], fx - 1.0f, fy - 1.0f, fz - 1.0f));
    float y2 = Mth.lerp(v, x3, x4);

    return Mth.clamp(0.5f + 0.5f * Mth.lerp(w, y1, y2), 0.0f, 1.0f);
  }

  private static float fbm3D(float x, float y, float z) {
    float n0 = perlin3D(x, y, z) * 0.54f;
    float n1 = perlin3D(x * 2.07f + 17.3f, y * 2.07f - 9.1f, z * 2.07f + 5.7f) * 0.31f;
    float n2 = perlin3D(x * 4.19f - 11.4f, y * 4.19f + 23.8f, z * 4.19f - 19.2f) * 0.15f;
    return Mth.clamp(n0 + n1 + n2, 0.0f, 1.0f);
  }

  public static void renderOilFireVolume(
    PoseStack ps,
    MultiBufferSource bufferSource,
    float centerX, float centerY, float centerZ,
    float radius,
    float flameHeight,
    int radialRings,
    float timeSeconds,
    VolumetricDictionary dictionary
  ) {
    renderOilFireVolume(
      ps, bufferSource, centerX, centerY, centerZ,
      radius, flameHeight, radialRings, timeSeconds, dictionary, true
    );
  }

  public static void renderOilFireVolume(
    PoseStack ps,
    MultiBufferSource bufferSource,
    float centerX, float centerY, float centerZ,
    float radius,
    float flameHeight,
    int radialRings,
    float timeSeconds,
    VolumetricDictionary dictionary,
    boolean autoFlush
  ) {
    ps.pushPose();
    ps.translate(centerX, centerY, centerZ);

    Matrix4f m = ps.last().pose();
    final int grid = OIL_GRID;
    final int stride = OIL_STRIDE;
    final int layers = 4;
    final float maxSpan = radius * 1.12f;
    final float invGrid = 2.0f / (float) grid;
    final float invRadius = 1.0f / Math.max(0.001f, radius);

    if (autoFlush) {
      RenderSystem.enableDepthTest();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableCull();
      RenderSystem.depthMask(false);
    }

    VertexConsumer vc = bufferSource.getBuffer(RawGeometry.DIALOGUE_SOLID);

    for (int layer = 0; layers > layer; layer++) {
      float layerT = (float) layer / (float) (layers - 1);
      float freq = 1.15f + layerT * 0.95f;
      float scrollSpeed = 1.65f + layerT * 2.15f;
      float seedOffset = layer * 31.7f;
      float timeScrollA = (timeSeconds * scrollSpeed) + seedOffset;
      float timeScrollB = (timeSeconds * (scrollSpeed * 1.3f)) + 7.2f;
      float timeWarp = timeSeconds * 0.7f;
      float layerWeight = (layer == 0) ? 0.95f : (0.74f * (1.0f - layerT * 0.38f));
      float baseOffset = 0.02f + layerT * 0.14f;
      float layerAmp = flameHeight * (0.28f + 0.85f * layerT);
      float cutoutThreshold = 0.18f + layerT * 0.24f;

      for (int gz = 0; stride > gz; gz++) {
        float vz = (gz * invGrid - 1.0f) * maxSpan;
        float nz = vz * invRadius;
        float nz2 = nz * nz;

        for (int gx = 0; stride > gx; gx++) {
          int idx = gz * stride + gx;
          float vx = (gx * invGrid - 1.0f) * maxSpan;
          float nx = vx * invRadius;
          float nx2 = nx * nx;

          // Fast reject corners well outside the rounded square before running any Perlin noise
          if ((nx2 * nx2 + nz2 * nz2) > 1.75f) {
            OIL_GRID_COL[idx] = 0;
            continue;
          }

          float warpX = (perlin3D(vx * 0.85f + 4.1f, timeWarp, vz * 0.85f) - 0.5f) * 0.24f;
          float warpZ = (perlin3D(vx * 0.85f, timeWarp, vz * 0.85f + 13.7f) - 0.5f) * 0.24f;
          float ax = Math.abs(nx + warpX);
          float az = Math.abs(nz + warpZ);
          float ax2 = ax * ax;
          float az2 = az * az;
          float pNorm = (ax2 * ax2 * ax) + (az2 * az2 * az);
          if (pNorm >= 1.0f) {
            OIL_GRID_COL[idx] = 0;
            continue;
          }

          float invNorm = 1.0f - pNorm;
          float edgeMask = invNorm * invNorm;

          float nHeight = fbm3D(vx * freq + seedOffset, timeScrollA, vz * freq - seedOffset);
          float nDetail = perlin3D(
            vx * (freq * 2.3f) - timeSeconds * 1.4f,
            timeScrollB,
            vz * (freq * 2.3f) + timeSeconds * 1.1f
          );

          // Fast algebraic peak sharpening (replaces Math.pow(nHeight, 1.45))
          float spike = nHeight * (0.55f + 0.45f * nHeight) * (0.65f + 0.55f * nDetail);
          float y = (baseOffset + spike * layerAmp) * edgeMask;

          float cutout = (layer == 0)
            ? 1.0f
            : Mth.clamp((nHeight - cutoutThreshold) * 2.6f, 0.0f, 1.0f);
          if (0.004f >= cutout) {
            OIL_GRID_COL[idx] = 0;
            continue;
          }

          float radialDistSq = Mth.clamp(nx2 + nz2, 0.0f, 1.0f);
          float coreDarkening = 0.45f + 0.55f * radialDistSq;
          float localShellT = Mth.clamp(((layerT * 0.58f) + (spike * 0.52f)) * coreDarkening, 0.0f, 1.0f);

          int sampled = dictionary.sampleShellArgb(nx, nz, localShellT, localShellT, timeSeconds);
          int baseAlpha = (sampled >>> 24) & 0xFF;

          float density = Mth.clamp(edgeMask * cutout * layerWeight * (0.58f + 0.62f * nHeight), 0.0f, 1.0f);
          int finalAlpha = Mth.clamp((int) (baseAlpha * density), 0, 255);

          OIL_GRID_Y[idx] = y;
          OIL_GRID_COL[idx] = (sampled & 0x00FFFFFF) | (finalAlpha << 24);
        }
      }

      for (int gz = 0; grid > gz; gz++) {
        float vz0 = (gz * invGrid - 1.0f) * maxSpan;
        float vz1 = ((gz + 1) * invGrid - 1.0f) * maxSpan;

        for (int gx = 0; grid > gx; gx++) {
          int i00 = gz * stride + gx;
          int i10 = i00 + 1;
          int i01 = i00 + stride;
          int i11 = i01 + 1;

          int c00 = OIL_GRID_COL[i00];
          int c10 = OIL_GRID_COL[i10];
          int c11 = OIL_GRID_COL[i11];
          int c01 = OIL_GRID_COL[i01];
          if (((c00 | c10 | c11 | c01) >>> 24) == 0) continue;

          float vx0 = (gx * invGrid - 1.0f) * maxSpan;
          float vx1 = ((gx + 1) * invGrid - 1.0f) * maxSpan;

          RawGeometry.vertex(vc, m, vx0, OIL_GRID_Y[i00], vz0, c00);
          RawGeometry.vertex(vc, m, vx1, OIL_GRID_Y[i10], vz0, c10);
          RawGeometry.vertex(vc, m, vx1, OIL_GRID_Y[i11], vz1, c11);
          RawGeometry.vertex(vc, m, vx0, OIL_GRID_Y[i01], vz1, c01);
        }
      }
    }

    if (autoFlush) {
      if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
        bs.endBatch(RawGeometry.DIALOGUE_SOLID);
      }
      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
    }
    ps.popPose();
  }

  private static int sampleOilPoolColor(
    VolumetricDictionary dictionary,
    float normX, float normZ,
    float radialT, float heightT, float turb, float cutoutMask,
    float timeSeconds
  ) {
    int base = dictionary.sampleShellArgb(normX, normZ, heightT, heightT, timeSeconds);
    int baseAlpha = (base >>> 24) & 0xFF;
    float heightFade = (float) Math.pow(1.0f - heightT, 1.25D);
    float edgeFade = 1.0f - (float) Math.pow(radialT, 2.8D) * 0.78f;
    float density = Mth.clamp(heightFade * edgeFade * cutoutMask * (0.55f + 0.65f * turb), 0.0f, 1.0f);
    int finalAlpha = Mth.clamp((int) (baseAlpha * density), 0, 255);
    return (base & 0x00FFFFFF) | (finalAlpha << 24);
  }



}
