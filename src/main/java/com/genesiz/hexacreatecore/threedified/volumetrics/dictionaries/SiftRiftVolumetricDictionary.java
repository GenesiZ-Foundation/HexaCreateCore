package com.genesiz.hexacreatecore.threedified.volumetrics.dictionaries;

import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.ShaderDictionary;
import net.minecraft.util.Mth;

public final class SiftRiftVolumetricDictionary implements VolumetricDictionary {

  public static final SiftRiftVolumetricDictionary WHITE_RIFT = new SiftRiftVolumetricDictionary(
    0xFFF8FAFC, // Crisp rift-white inner edge
    0xFFCBD5E1  // Cool silver-mist outer edge
  );

  private final int innerWhite;
  private final int outerMist;

  public SiftRiftVolumetricDictionary(int innerWhite, int outerMist) {
    this.innerWhite = innerWhite;
    this.outerMist = outerMist;
  }

  @Override
  public int sampleShellArgb(float normX, float normY, float normZ, float shellT, float timeSeconds) {
    // shellT = axial extrusion progress from block face (0.0) to outer rift tip (1.0)
    double angle = Math.atan2(normY, normX);
    float shimmer = 0.5f + 0.5f * (float) Math.sin((angle * 4.0D) - (timeSeconds * 3.8D) + (shellT * 5.0D));

    int rgb = ShaderDictionary.lerpArgb(innerWhite, outerMist, shellT * (1.0f - shimmer * 0.35f));

    // Smoothly ramp from 0% right at the block face seam (0.0..0.12), peak at ~80% (204), and fade to ~20% (51) at outer tip
    float rampIn = Mth.clamp(shellT / 0.14f, 0.0f, 1.0f);
    float fadeOut = Mth.lerp(shellT, 204.0f, 46.0f);
    int alpha = (int) (fadeOut * rampIn * (0.85f + 0.15f * shimmer));
    return (rgb & 0x00FFFFFF) | (Mth.clamp(alpha, 0, 255) << 24);
  }
}
