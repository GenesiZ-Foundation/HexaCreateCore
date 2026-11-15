package com.genesiz.hexacreatecore.threedified.shaders.dictionaries;

import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

public final class AuroraRimDictionary implements ShaderDictionary {

  public static final AuroraRimDictionary COSMIC = new AuroraRimDictionary(0xCCC026D3, 0xCC34D399, 0.74f, 0.90f);
  public static final AuroraRimDictionary EMERALD = new AuroraRimDictionary(0xCC34D399, 0xCC6EE7B7, 0.74f, 0.90f);
  public static final AuroraRimDictionary CRIMSON = new AuroraRimDictionary(0xCCFF1E56, 0xCCFF6B8B, 0.74f, 0.90f);

  private final int primaryRim, secondaryRim;
  private final float innerStartT, peakT;

  public AuroraRimDictionary(int primaryRim, int secondaryRim, float innerStartT, float peakT) {
    this.primaryRim = primaryRim;
    this.secondaryRim = secondaryRim;
    this.innerStartT = innerStartT;
    this.peakT = peakT;
  }

  @Override
  public int sampleArgb(float localX, float localY, double angleRad, float radialT, float timeSeconds) {
    if (innerStartT >= radialT) return 0x00000000;
    float wave = 0.5f + 0.5f * (float) Math.sin((angleRad * 3.0D) + (timeSeconds * 2.4f));
    int peakColor = ShaderDictionary.lerpArgb(primaryRim, secondaryRim, wave);

    float intensity = (peakT >= radialT)
    ? Mth.clamp((radialT - innerStartT) / (peakT - innerStartT), 0.0f, 1.0f)
    : Mth.clamp((1.0f - radialT) / (1.0f - peakT), 0.0f, 1.0f);
    float smoothAlpha = intensity * intensity;
    return (peakColor & 0x00FFFFFF) | ((int) (FastColor.ARGB32.alpha(peakColor) * smoothAlpha) << 24);
  }
}

