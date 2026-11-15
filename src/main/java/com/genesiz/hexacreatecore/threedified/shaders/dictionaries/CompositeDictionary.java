package com.genesiz.hexacreatecore.threedified.shaders.dictionaries;

import net.minecraft.util.FastColor;

import java.util.List;

public final class CompositeDictionary implements ShaderDictionary {

  public static final ShaderDictionary EVERBOOK_DEFAULT = new CompositeDictionary(CosmicOceanDictionary.DEFAULT, AuroraRimDictionary.COSMIC);
  public static final ShaderDictionary EVERBOOK_EMERALD = new CompositeDictionary(CosmicOceanDictionary.EMERALD_TINT, AuroraRimDictionary.EMERALD);
  public static final ShaderDictionary EVERBOOK_CRIMSON = new CompositeDictionary(CosmicOceanDictionary.CRIMSON_TINT, AuroraRimDictionary.CRIMSON);

  private final List<ShaderDictionary> layers;

  public CompositeDictionary(ShaderDictionary... layers) {
    this.layers = List.of(layers);
  }

  @Override
  public int sampleArgb(float localX, float localY, double angleRad, float radialT, float timeSeconds) {
    int result = 0x00000000;
    for (ShaderDictionary layer : layers) {
      int top = layer.sampleArgb(localX, localY, angleRad, radialT, timeSeconds);
      int topAlpha = FastColor.ARGB32.alpha(top);
      if (topAlpha == 0) continue;
      if (FastColor.ARGB32.alpha(result) == 0) {
        result = top;
      } else {
        result = ShaderDictionary.lerpArgb(result, (top & 0x00FFFFFF) | (Math.max(FastColor.ARGB32.alpha(result), topAlpha) << 24), topAlpha / 255.0f);
      }
    }
    return result;
  }
}

