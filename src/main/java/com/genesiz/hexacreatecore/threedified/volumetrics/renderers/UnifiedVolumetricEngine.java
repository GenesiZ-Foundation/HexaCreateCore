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
    ps.pushPose();
    ps.translate(centerX, centerY, centerZ);

    Matrix4f poseMat = ps.last().pose();
    Matrix3f normMat = ps.last().normal();

    Vector3f viewDir = new Vector3f(-poseMat.m30(), -poseMat.m31(), -poseMat.m32());
    if (viewDir.lengthSquared() > 1.0e-6f) {
      viewDir.normalize();
    } else {
      viewDir.set(0.0f, 0.0f, 1.0f);
    }

    int coreArgb = (dictionary instanceof BlackHoleVolumetricDictionary bh) ? bh.coreColor() : 0xFF020205;
    int shells = Math.max(4, shellCount + 2);

    RenderSystem.enableDepthTest();
    RenderSystem.enableCull();

    // Pass 1: Opaque Pitch-Black 3D Core with depthMask(true) — completely blocks anything behind it (zero Z-fighting)
    RenderSystem.disableBlend();
    RenderSystem.depthMask(true);
    VertexConsumer coreVc = bufferSource.getBuffer(RawGeometry.DIALOGUE_SOLID);
    drawFeatheredSuperellipsoid(
      coreVc, poseMat, normMat, viewDir,
      coreHalfW * 0.92f, coreHalfH * 0.92f, coreHalfD * 0.92f,
      0.0f, timeSeconds, coreArgb | 0xFF000000, true, null
    );
    if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
      bs.endBatch(RawGeometry.DIALOGUE_SOLID);
    }

    // Pass 2: Translucent Rim-Lit 3D Silver Photon Horizon Shells (depthMask=false, front-facing only)
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.depthMask(false);
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
        shellT, timeSeconds, coreArgb, false, dictionary
      );
    }

    if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
      bs.endBatch(RawGeometry.DIALOGUE_SOLID);
    }

    RenderSystem.depthMask(true);
    ps.popPose();
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
    VolumetricDictionary dict
  ) {
    final int stacks = 14;
    final int slices = 24;
    final float exp = 0.48f; // Smooth Minecraft superellipsoid exponent (softly squared, zero sharp 90-deg edges)

    Vector3f tmpNorm = new Vector3f();

    for (int i = 0; stacks > i; i++) {
      double phi0 = -Math.PI * 0.5D + (Math.PI * i) / stacks;
      double phi1 = -Math.PI * 0.5D + (Math.PI * (i + 1)) / stacks;

      float cosPhi0 = signedPow(Math.cos(phi0), exp);
      float sinPhi0 = signedPow(Math.sin(phi0), exp);
      float cosPhi1 = signedPow(Math.cos(phi1), exp);
      float sinPhi1 = signedPow(Math.sin(phi1), exp);

      for (int j = 0; slices > j; j++) {
        double theta0 = (Math.PI * 2.0D * j) / slices;
        double theta1 = (Math.PI * 2.0D * (j + 1)) / slices;

        float cosT0 = signedPow(Math.cos(theta0), exp);
        float sinT0 = signedPow(Math.sin(theta0), exp);
        float cosT1 = signedPow(Math.cos(theta1), exp);
        float sinT1 = signedPow(Math.sin(theta1), exp);

        float nx00 = cosPhi0 * cosT0, ny00 = sinPhi0, nz00 = cosPhi0 * sinT0;
        float nx10 = cosPhi0 * cosT1, ny10 = sinPhi0, nz10 = cosPhi0 * sinT1;
        float nx11 = cosPhi1 * cosT1, ny11 = sinPhi1, nz11 = cosPhi1 * sinT1;
        float nx01 = cosPhi1 * cosT0, ny01 = sinPhi1, nz01 = cosPhi1 * sinT0;

        int c00 = shadeVertex(nx00, ny00, nz00, shellT, timeSeconds, coreArgb, isCore, dict, normMat, viewDir, tmpNorm);
        int c10 = shadeVertex(nx10, ny10, nz10, shellT, timeSeconds, coreArgb, isCore, dict, normMat, viewDir, tmpNorm);
        int c11 = shadeVertex(nx11, ny11, nz11, shellT, timeSeconds, coreArgb, isCore, dict, normMat, viewDir, tmpNorm);
        int c01 = shadeVertex(nx01, ny01, nz01, shellT, timeSeconds, coreArgb, isCore, dict, normMat, viewDir, tmpNorm);

      RawGeometry.vertex(vc, poseMat, nx00 * hw, ny00 * hh, nz00 * hd, c00);
        RawGeometry.vertex(vc, poseMat, nx01 * hw, ny01 * hh, nz01 * hd, c01);
        RawGeometry.vertex(vc, poseMat, nx11 * hw, ny11 * hh, nz11 * hd, c11);
        RawGeometry.vertex(vc, poseMat, nx10 * hw, ny10 * hh, nz10 * hd, c10);
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
    if (tmpNorm.lengthSquared() > 1.0e-6f) {
      tmpNorm.normalize();
      normMat.transform(tmpNorm);
      tmpNorm.normalize();
    }

    // View-angle Fresnel edge feathering: silhouette edges fade smoothly to 0% alpha from any angle
    float ndotv = Math.abs(tmpNorm.dot(viewDir));

    if (isCore) {
      float coreFade = (float) Math.pow(ndotv, 0.65D);
      int alpha = (int) (255.0f * Mth.clamp(0.45f + coreFade * 0.55f, 0.0f, 1.0f));
      return (coreArgb & 0x00FFFFFF) | (alpha << 24);
    }

    // Rim-only Fresnel envelope: transparent over the core center (ndotv ~ 1), bright silver photon glow on the 3D edges, fading at extreme silhouette
    float rimMask = Mth.clamp(1.0f - (ndotv * 1.48f), 0.0f, 1.0f);
    float shellFade = (float) Math.pow(rimMask, 1.25D) * (1.0f - shellT * 0.72f);

    int sampled = dict.sampleShellArgb(nx, ny, nz, shellT, timeSeconds);
    int baseAlpha = (sampled >>> 24) & 0xFF;
    int featheredAlpha = (int) (baseAlpha * Mth.clamp(shellFade * 1.35f, 0.0f, 1.0f));
    return (sampled & 0x00FFFFFF) | (featheredAlpha << 24);
  }

  private static float signedPow(double val, float exp) {
    return (float) (Math.signum(val) * Math.pow(Math.abs(val), exp));
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
    ps.pushPose();
    ps.translate(centerX, centerY, centerZ);

    Matrix4f m = ps.last().pose();
    final int segments = 36;
    final int slices = Math.max(4, depthSlices);
    final float sqExp = 0.34f; // Rounded-square Minecraft silhouette matching the black-hole core

    RenderSystem.enableDepthTest();
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.disableCull();
    RenderSystem.depthMask(false);

    VertexConsumer vc = bufferSource.getBuffer(RawGeometry.DIALOGUE_SOLID);

    for (int zStep = 0; slices > zStep; zStep++) {
      float tZ0 = (float) zStep / slices;
      float tZ1 = (float) (zStep + 1) / slices;

      for (int i = 0; segments > i; i++) {
        double a0 = (i * Math.PI * 2.0D) / segments;
        double a1 = ((i + 1) * Math.PI * 2.0D) / segments;

        float sx0 = signedPow(Math.cos(a0), sqExp);
        float sy0 = signedPow(Math.sin(a0), sqExp);
        float sx1 = signedPow(Math.cos(a1), sqExp);
        float sy1 = signedPow(Math.sin(a1), sqExp);

        float w0 = (float) (Math.sin(timeSeconds * 4.2D + a0 * 4.0D) * 0.55D + Math.cos(timeSeconds * 2.9D - a0 * 6.0D) * 0.45D);
        float w1 = (float) (Math.sin(timeSeconds * 4.2D + a1 * 4.0D) * 0.55D + Math.cos(timeSeconds * 2.9D - a1 * 6.0D) * 0.45D);

        float z0_a = tZ0 * crownDepth * (0.86f + 0.22f * w0);
        float z0_b = tZ0 * crownDepth * (0.86f + 0.22f * w1);
        float z1_a = tZ1 * crownDepth * (0.86f + 0.22f * w0);
        float z1_b = tZ1 * crownDepth * (0.86f + 0.22f * w1);

        // Inner wall hugs the 3D black-hole core; outer wall flares into a thick 3D volumetric collar
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

        // 1. Outer 3D Volumetric Wall
        RawGeometry.vertex(vc, m, sx0 * (inW0 + thick0),     sy0 * (inH0 + thick0),     z0_a, col0);
        RawGeometry.vertex(vc, m, sx1 * (inW1 + thick0_b),   sy1 * (inH1 + thick0_b),   z0_b, col0_b);
        RawGeometry.vertex(vc, m, sx1 * (inW1_next + thick1_b), sy1 * (inH1_next + thick1_b), z1_b, col1_b);
        RawGeometry.vertex(vc, m, sx0 * (inW0_next + thick1),   sy0 * (inH0_next + thick1),   z1_a, col1);

        // 2. Inner 3D Volumetric Wall (gives real 3D thickness around the black hole)
        RawGeometry.vertex(vc, m, sx0 * inW0,      sy0 * inH0,      z0_a, col0);
        RawGeometry.vertex(vc, m, sx1 * inW1,      sy1 * inH1,      z0_b, col0_b);
        RawGeometry.vertex(vc, m, sx1 * inW1_next, sy1 * inH1_next, z1_b, col1_b);
        RawGeometry.vertex(vc, m, sx0 * inW0_next, sy0 * inH0_next, z1_a, col1);

        // 3. Front 3D Rim Cap closing the outer tip of the volumetric collar
        if (zStep == slices - 1) {
          RawGeometry.vertex(vc, m, sx0 * inW0_next,            sy0 * inH0_next,            z1_a, col1);
          RawGeometry.vertex(vc, m, sx1 * inW1_next,            sy1 * inH1_next,            z1_b, col1_b);
          RawGeometry.vertex(vc, m, sx1 * (inW1_next + thick1_b), sy1 * (inH1_next + thick1_b), z1_b, col1_b);
          RawGeometry.vertex(vc, m, sx0 * (inW0_next + thick1),   sy0 * (inH0_next + thick1),   z1_a, col1);
        }
      }
    }

    if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
      bs.endBatch(RawGeometry.DIALOGUE_SOLID);
    }

    RenderSystem.depthMask(true);
    RenderSystem.enableCull();
    ps.popPose();
  }

}
