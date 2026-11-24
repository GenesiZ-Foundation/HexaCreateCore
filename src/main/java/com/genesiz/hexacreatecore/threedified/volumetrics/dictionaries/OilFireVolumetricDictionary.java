package com.genesiz.hexacreatecore.threedified.volumetrics.dictionaries;

import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.ShaderDictionary;
import net.minecraft.util.Mth;

public final class OilFireVolumetricDictionary implements VolumetricDictionary {

  // Matches the dragon boss-bar palette: #140A1F (dark middle/base) -> #6A1B9A (mid) -> #D580FF (bright flame tips)
  public static final OilFireVolumetricDictionary DRAGON_PLASMA = new OilFireVolumetricDictionary(
    0xFF140A1F, // Deep dark void-purple middle/base (#140A1F)
    0xFF6A1B9A, // Rich royal-purple mid plasma (#6A1B9A)
    0xFFD580FF, // Bright lavender-magenta flame crests (#D580FF)
    0.90f
  );

  private final int darkBase;
  private final int midPurple;
  private final int brightTip;
  private final float opacityScale;

  public OilFireVolumetricDictionary(
    int darkBase,
    int midPurple,
    int brightTip,
    float opacityScale
  ) {
    this.darkBase = darkBase;
    this.midPurple = midPurple;
    this.brightTip = brightTip;
    this.opacityScale = opacityScale;
  }

  public OilFireVolumetricDictionary withOpacity(float scale) {
    return new OilFireVolumetricDictionary(
      this.darkBase,
      this.midPurple,
      this.brightTip,
      Mth.clamp(scale, 0.0f, 1.0f)
    );
  }

  @Override
  public int sampleShellArgb(float normX, float normY, float normZ, float shellT, float timeSeconds) {
    float lick = 0.5f + 0.5f * Mth.sin((normX * 6.5f) - (normY * 6.5f) - (timeSeconds * 7.5f) + (shellT * 4.5f));
    float t = Mth.clamp(shellT + (lick - 0.5f) * 0.14f, 0.0f, 1.0f);

    int rgb;
    if (0.52f >= t) {
      // Middle & lower layers: #140A1F -> #6A1B9A
      rgb = ShaderDictionary.lerpArgb(darkBase, midPurple, t / 0.52f);
    } else {
      // Rising upper tongues & crests: #6A1B9A -> #D580FF
      rgb = ShaderDictionary.lerpArgb(midPurple, brightTip, (t - 0.52f) / 0.48f);
    }

    int alpha = Mth.clamp((int) (225.0f * opacityScale), 0, 255);
    return (rgb & 0x00FFFFFF) | (alpha << 24);
  }
}
