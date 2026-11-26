/*
 * THIS PART OF THE CODE IS MEANT FOR DEVELOPERS
 * */
package com.genesiz.hexacreatecore.threedified.volumetrics.dictionaries;

/* FULL BREAKDOWN:
 *
 * -> Functional color-sampling interface driving VolumetricHook volumetric shell layers
 *
 * [.sampleShellArgb()] samples the ARGB color at normalized [-1..1] coordinates, shellT [0..1], and timeSeconds
 *
 * */
@FunctionalInterface
public interface VolumetricDictionary {
  /**
   * Samples an ARGB color for a point on a 3D volumetric envelope.
   * @param normX Normalized local X [-1..1]
   * @param normY Normalized local Y [-1..1]
   * @param normZ Normalized local Z [-1..1]
   * @param shellT Normalized shell depth from core surface (0.0) to outer halo edge (1.0)
   * @param timeSeconds Elapsed animation time in seconds
   */
  int sampleShellArgb(float normX, float normY, float normZ, float shellT, float timeSeconds);
}
