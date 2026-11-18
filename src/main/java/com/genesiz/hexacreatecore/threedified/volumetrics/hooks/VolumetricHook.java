package com.genesiz.hexacreatecore.threedified.volumetrics.hooks;

import com.genesiz.hexacreatecore.threedified.volumetrics.dictionaries.VolumetricDictionary;
import com.genesiz.hexacreatecore.threedified.volumetrics.renderers.UnifiedVolumetricEngine;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

public final class VolumetricHook {

  private final VolumetricDictionary dictionary;
  private float centerX = 0.0f;
  private float centerY = 0.0f;
  private float centerZ = 0.0f;
  private float coreHalfW = 0.32f;
  private float coreHalfH = 0.32f;
  private float coreHalfD = 0.14f;
  private float horizonThickness = 0.11f;
  private int shellCount = 4;

  private VolumetricHook(VolumetricDictionary dictionary) {
    this.dictionary = dictionary;
  }

  public static VolumetricHook bind(VolumetricDictionary dictionary) {
    return new VolumetricHook(dictionary);
  }

  public VolumetricHook at(float x, float y, float z) {
    this.centerX = x;
    this.centerY = y;
    this.centerZ = z;
    return this;
  }

  public VolumetricHook coreSize(float halfW, float halfH, float halfD) {
    this.coreHalfW = halfW;
    this.coreHalfH = halfH;
    this.coreHalfD = halfD;
    return this;
  }

  public VolumetricHook eventHorizon(float thickness, int shellCount) {
    this.horizonThickness = thickness;
    this.shellCount = shellCount;
    return this;
  }

  public void render(PoseStack poseStack, MultiBufferSource bufferSource, float timeSeconds) {
    UnifiedVolumetricEngine.renderBoxVolume(
      poseStack, bufferSource,
      centerX, centerY, centerZ,
      coreHalfW, coreHalfH, coreHalfD,
      horizonThickness, shellCount,
      timeSeconds, dictionary
    );
  }

  public void renderCrown(
    PoseStack poseStack, MultiBufferSource bufferSource,
    float wallThickness, float crownDepth, int slices, float timeSeconds
  ) {
    UnifiedVolumetricEngine.renderRiftCrownVolume(
      poseStack, bufferSource,
      centerX, centerY, centerZ,
      coreHalfW, coreHalfH,
      wallThickness, crownDepth, slices,
      timeSeconds, dictionary
    );
  }

}

