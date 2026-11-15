package com.genesiz.hexacreatecore.threedified.shaders.dictionaries;

import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

public final class CosmicOceanDictionary implements ShaderDictionary {

  public static final CosmicOceanDictionary DEFAULT = new CosmicOceanDictionary(0xD8090414, 0xD84C1D95, 0xD886198F, 0xD80D9488, 0x00000000, 0.0f);
  public static final CosmicOceanDictionary EMERALD_TINT = new CosmicOceanDictionary(0xD8090414, 0xD84C1D95, 0xD886198F, 0xD80D9488, 0xCC10B981, 0.35f);
  public static final CosmicOceanDictionary CRIMSON_TINT = new CosmicOceanDictionary(0xD8090414, 0xD84C1D95, 0xD886198F, 0xD80D9488, 0xCCFF1E56, 0.45f);

  private final int voidBase, violetMid, magentaCurrent, tealCurrent, tintColor;
  private final float tintStrength;

  public CosmicOceanDictionary(int voidBase, int violetMid, int magentaCurrent, int tealCurrent, int tintColor, float tintStrength) {
    this.voidBase = voidBase;
    this.violetMid = violetMid;
    this.magentaCurrent = magentaCurrent;
    this.tealCurrent = tealCurrent;
    this.tintColor = tintColor;
    this.tintStrength = tintStrength;
  }

  @Override
  public int sampleArgb(float localX, float localY, double angleRad, float radialT, float timeSeconds) {
    float driftOne = 0.5f + 0.5f * (float) Math.sin((localX * 2.6f + localY * 1.9f) + (timeSeconds * 1.45f));
    float driftTwo = 0.5f + 0.5f * (float) Math.cos((-localX * 2.2f + localY * 2.8f) - (timeSeconds * 1.15f));
    float driftThree = 0.5f + 0.5f * (float) Math.sin((localX * 1.5f - localY * 3.3f) + (timeSeconds * 1.85f));

    int base = ShaderDictionary.lerpArgb(voidBase, violetMid, (driftOne * 0.42f) + (driftTwo * 0.35f) + (driftThree * 0.23f));
    int color = ShaderDictionary.lerpArgb(base, ShaderDictionary.lerpArgb(magentaCurrent, tealCurrent, driftTwo), driftThree * 0.48f);
    if (tintStrength > 0.001f) color = ShaderDictionary.lerpArgb(color, tintColor, tintStrength);

    float edgeFade = (radialT > 0.86f) ? Mth.clamp((1.0f - radialT) / 0.14f, 0.0f, 1.0f) : 1.0f;
    return (color & 0x00FFFFFF) | ((int) (FastColor.ARGB32.alpha(color) * edgeFade) << 24);
  }
}

