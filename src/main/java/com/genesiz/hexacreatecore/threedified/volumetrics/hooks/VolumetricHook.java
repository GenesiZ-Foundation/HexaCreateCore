/*
 * THIS PART OF THE CODE IS MEANT FOR DEVELOPERS
 * */
package com.genesiz.hexacreatecore.threedified.volumetrics.hooks;

import com.genesiz.hexacreatecore.threedified.volumetrics.dictionaries.VolumetricDictionary;
import com.genesiz.hexacreatecore.threedified.volumetrics.renderers.UnifiedVolumetricEngine;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/* FULL BREAKDOWN:
 *
 * -> Fluent builder hook for rendering multi-shell 3D volumetric envelopes via UnifiedVolumetricEngine
 *
 * [.bind()] is self explanatory
 * [.at()/.coreSize()/.eventHorizon()/.autoFlush()/.spherical()] fluent configuration setters
 * [.render()/.renderCoreOnly()/.renderHorizonOnly()] renders box or spherical volumetric shells
 * [.renderCrown()] renders a rift crown volumetric envelope
 * [.renderOilFire()] renders a layered oil-fire flame volume
 *
 * */
public final class VolumetricHook {

  private final  VolumetricDictionary  dictionary;
  private        float                 centerX = 0.0f;
  private        float                 centerY = 0.0f;
  private        float                 centerZ = 0.0f;
  private        float                 coreHalfW = 0.32f;
  private        float                 coreHalfH = 0.32f;
  private        float                 coreHalfD = 0.14f;
  private        float                 horizonThickness = 0.11f;
  private        int                   shellCount = 4;
  private        boolean               autoFlush = true;
  private        boolean               spherical = false;

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

  public VolumetricHook autoFlush(boolean autoFlush) {
    this.autoFlush = autoFlush;
    return this;
  }

  public VolumetricHook spherical(boolean spherical) {
    this.spherical = spherical;
    return this;
  }

  public void render(PoseStack poseStack, MultiBufferSource bufferSource, float timeSeconds) {
    UnifiedVolumetricEngine.renderBoxVolume(
      poseStack, bufferSource,
      centerX, centerY, centerZ,
      coreHalfW, coreHalfH, coreHalfD,
      horizonThickness, shellCount,
      timeSeconds, dictionary, spherical
    );
  }

  public void renderCoreOnly(PoseStack poseStack, MultiBufferSource bufferSource, float timeSeconds) {
    UnifiedVolumetricEngine.renderBoxCoreOnly(
      poseStack, bufferSource,
      centerX, centerY, centerZ,
      coreHalfW, coreHalfH, coreHalfD,
      timeSeconds, dictionary, spherical
    );
  }

  public void renderHorizonOnly(PoseStack poseStack, MultiBufferSource bufferSource, float timeSeconds) {
    UnifiedVolumetricEngine.renderBoxHorizonOnly(
      poseStack, bufferSource,
      centerX, centerY, centerZ,
      coreHalfW, coreHalfH, coreHalfD,
      horizonThickness, shellCount,
      timeSeconds, dictionary, spherical
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
      timeSeconds, dictionary, autoFlush
    );
  }

  public void renderOilFire(
    PoseStack poseStack, MultiBufferSource bufferSource,
    float radius, float flameHeight, int radialRings, float timeSeconds
  ) {
    UnifiedVolumetricEngine.renderOilFireVolume(
      poseStack, bufferSource,
      centerX, centerY, centerZ,
      radius, flameHeight, radialRings,
      timeSeconds, dictionary, autoFlush
    );
  }
}

