package com.genesiz.hexacreatecore.threedified.shaders.hooks;

import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.CompositeDictionary;
import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.ShaderDictionary;
import com.genesiz.hexacreatecore.threedified.shaders.renderers.SuperellipsePortalRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

public final class PortalShaderHook {

  private final ShaderDictionary dictionary;
  private float centerX = 0.0f;
  private float centerY = 0.0f;
  private float halfWidth = 1.0f;
  private float halfHeight = 0.65f;
  private int angularSegments = 64;
  private int radialRings = 12;
  private boolean flushAfterRender = false;

  private PortalShaderHook(ShaderDictionary dictionary) {
    this.dictionary = dictionary;
  }

  public static PortalShaderHook bind(ShaderDictionary... dictionaries) {
    ShaderDictionary active = (dictionaries.length == 1) ? dictionaries[0] : new CompositeDictionary(dictionaries);
    return new PortalShaderHook(active);
  }

  public PortalShaderHook at(float centerX, float centerY) {
    this.centerX = centerX;
    this.centerY = centerY;
    return this;
  }

  public PortalShaderHook size(float halfWidth, float halfHeight) {
    this.halfWidth = halfWidth;
    this.halfHeight = halfHeight;
    return this;
  }

  public PortalShaderHook resolution(int angularSegments, int radialRings) {
    this.angularSegments = angularSegments;
    this.radialRings = radialRings;
    return this;
  }

  public PortalShaderHook flushBatch() {
    this.flushAfterRender = true;
    return this;
  }

  public void render(PoseStack poseStack, MultiBufferSource bufferSource, float timeSeconds) {
    SuperellipsePortalRenderer.render(
      bufferSource.getBuffer(RawGeometry.DIALOGUE_SOLID),
      poseStack.last().pose(),
      centerX, centerY, halfWidth, halfHeight,
      timeSeconds, angularSegments, radialRings,
      dictionary
    );
    if (flushAfterRender && bufferSource instanceof MultiBufferSource.BufferSource buffered) {
      buffered.endBatch(RawGeometry.DIALOGUE_SOLID);
    }
  }
}

