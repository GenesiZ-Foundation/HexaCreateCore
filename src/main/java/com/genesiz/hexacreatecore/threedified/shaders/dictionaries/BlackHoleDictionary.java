package com.genesiz.hexacreatecore.threedified.shaders.dictionaries;

import net.minecraft.util.Mth;

public final class BlackHoleDictionary implements ShaderDictionary {

  public static final BlackHoleDictionary SILVER_SINGULARITY = new BlackHoleDictionary(
    0xFF020205, // Pitch-black event horizon core
    0xFFF8FAFC, // Pure silver-white photon ring peak
    0xFF94A3B8, // Metallic slate-gray mid accretion
    0xFF334155  // Deep steel outer lensing halo
  );

  private final int singularityBlack;
  private final int photonSilver;
  private final int accretionGray;
  private final int outerSteel;

  public BlackHoleDictionary(int singularityBlack, int photonSilver, int accretionGray, int outerSteel) {
    this.singularityBlack = singularityBlack;
    this.photonSilver = photonSilver;
    this.accretionGray = accretionGray;
    this.outerSteel = outerSteel;
  }

  @Override
  public int sampleArgb(float localX, float localY, double angleRad, float radialT, float timeSeconds) {
    float horizonRadius = 0.42f;
    float photonPeak = 0.50f;

    // 1. Inside Schwarzschild Event Horizon: absolute pitch-black core
    if (horizonRadius >= radialT) {
      return singularityBlack;
    }

    // 2. Relativistic Doppler beaming + gravitational lensing swirl around the mass
    double spinAngle = angleRad - (timeSeconds * 2.6D);
    double lensingWarp = (horizonRadius * horizonRadius) / Math.max(0.08D, radialT - (horizonRadius * 0.65D));
    double warpedPhase = spinAngle + (lensingWarp * 3.4D);

    float doppler = 0.65f + 0.35f * (float) Math.cos(angleRad - 0.6D);
    float filamentA = 0.5f + 0.5f * (float) Math.sin((warpedPhase * 2.0D) + (radialT * 10.0f));
    float filamentB = 0.5f + 0.5f * (float) Math.cos((warpedPhase * 3.0D) - (timeSeconds * 1.9f));

    // 3. Sharp Silver Photon Sphere Ring just outside the black horizon
    if (photonPeak >= radialT) {
      float ringT = Mth.clamp((radialT - horizonRadius) / (photonPeak - horizonRadius), 0.0f, 1.0f);
      int ringColor = ShaderDictionary.lerpArgb(singularityBlack, photonSilver, ringT * ringT * doppler);
      return ringColor | 0xFF000000;
    }

    // 4. Outer Silver -> Gray Lensed Accretion Disk fading smoothly to the edge
    float diskT = Mth.clamp((radialT - photonPeak) / (1.0f - photonPeak), 0.0f, 1.0f);
    int diskBase = ShaderDictionary.lerpArgb(photonSilver, accretionGray, Math.min(1.0f, diskT * 1.45f));
    int diskSwirl = ShaderDictionary.lerpArgb(diskBase, outerSteel, (1.0f - filamentA * 0.65f) * diskT);
    diskSwirl = ShaderDictionary.lerpArgb(diskSwirl, photonSilver, filamentB * (1.0f - diskT) * 0.45f * doppler);

    float outerFade = (diskT > 0.55f) ? Mth.clamp((1.0f - diskT) / 0.45f, 0.0f, 1.0f) : 1.0f;
    return (diskSwirl & 0x00FFFFFF) | ((int) (255 * outerFade) << 24);
  }
}

