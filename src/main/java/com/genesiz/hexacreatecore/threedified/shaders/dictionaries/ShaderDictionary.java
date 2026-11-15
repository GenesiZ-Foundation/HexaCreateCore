package com.genesiz.hexacreatecore.threedified.shaders.dictionaries;

import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

@FunctionalInterface
public interface ShaderDictionary {
  int sampleArgb(float localX, float localY, double angleRad, float radialT, float timeSeconds);

  default ShaderDictionary then(ShaderDictionary overlay) {
    return new CompositeDictionary(this, overlay);
  }

  static int lerpArgb(int c0, int c1, float t) {
    return FastColor.ARGB32.color(
      Mth.lerpInt(t, FastColor.ARGB32.alpha(c0), FastColor.ARGB32.alpha(c1)),
      Mth.lerpInt(t, FastColor.ARGB32.red(c0), FastColor.ARGB32.red(c1)),
      Mth.lerpInt(t, FastColor.ARGB32.green(c0), FastColor.ARGB32.green(c1)),
      Mth.lerpInt(t, FastColor.ARGB32.blue(c0), FastColor.ARGB32.blue(c1))
    );
  }
}

