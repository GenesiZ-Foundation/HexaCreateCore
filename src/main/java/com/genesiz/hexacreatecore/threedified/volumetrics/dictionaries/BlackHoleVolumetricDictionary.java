package com.genesiz.hexacreatecore.threedified.volumetrics.dictionaries;

import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.ShaderDictionary;
import net.minecraft.util.Mth;

public final class BlackHoleVolumetricDictionary implements VolumetricDictionary {

  public static final BlackHoleVolumetricDictionary SILVER_SINGULARITY = new BlackHoleVolumetricDictionary(
    0xFF020205, // Pitch-black 3D singularity core
    0xFFF8FAFC, // Pure silver-white photon horizon
    0xFF94A3B8, // Metallic slate mid-lensing shell
    0xFF475569  // Steel outer dissipation edge
  );

  private final int coreColor;
  private final int photonSilver;
  private final int midSlate;
  private final int outerSteel;

  public BlackHoleVolumetricDictionary(int coreColor, int photonSilver, int midSlate, int outerSteel) {
    this.coreColor = coreColor;
    this.photonSilver = photonSilver;
    this.midSlate = midSlate;
    this.outerSteel = outerSteel;
  }

  public int coreColor() {
    return coreColor;
  }

  @Override
  public int sampleShellArgb(float normX, float normY, float normZ, float shellT, float timeSeconds) {
    double angle = Math.atan2(normY, normX);
    float swirl = 0.5f + 0.5f * (float) Math.sin((angle * 3.0D) - (timeSeconds * 3.2D) + (normZ * 4.0D) + (shellT * 6.0D));
    float filament = 0.5f + 0.5f * (float) Math.cos((angle * 5.0D) + (timeSeconds * 2.1D) - (normY * 3.5D));

    // Inner photon sheath (shellT ~ 0.0 -> 0.35) is crisp silver-white; outer shells fade to slate-steel
    int rgb = (0.35f >= shellT)
    ? ShaderDictionary.lerpArgb(photonSilver, midSlate, (shellT / 0.35f) * (1.0f - swirl * 0.35f))
    : ShaderDictionary.lerpArgb(midSlate, outerSteel, ((shellT - 0.35f) / 0.65f));

    rgb = ShaderDictionary.lerpArgb(rgb, photonSilver, filament * (1.0f - shellT) * 0.42f);

    // Translucent alpha ramp: ~65% right around the dark 3D core, fading smoothly to 0% at the outer edge
    float alphaFactor = Mth.clamp((1.0f - shellT) * (1.0f - shellT * 0.55f), 0.0f, 1.0f);
    int alpha = (int) (168.0f * alphaFactor);
    return (rgb & 0x00FFFFFF) | (alpha << 24);
  }
}

