package com.genesiz.hexacreatecore.components;

import net.minecraft.util.RandomSource;

public interface IProceduralAnimConfig {
  /**
   * The minimum tick interval before triggering the random overlay animation.
   */
  int getMinRandomInterval();

  /**
   * The maximum tick interval before triggering the random overlay animation.
   */
  int getMaxRandomInterval();

  /**
   * The name of the continuous baseline looping animation (e.g., "breathing", "hovering").
   */
  default String getContinuousAmbientAnim() {
    return "breathing";
  }

  /**
   * The name of the rare, one-shot action animation to play at intervals (e.g., "scratching", "blink").
   */
  default String getRandomActionAnim() {
    return "blink";
  }

  /**
   * Computes the next randomized threshold block window dynamically.
   */
  default int getNextRandomTicks(RandomSource random) {
    int min = getMinRandomInterval();
    int max = getMaxRandomInterval();
    if (min >= max) return min;
    return min + random.nextInt(max - min + 1);
  }
}
