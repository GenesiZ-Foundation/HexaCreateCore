package com.genesiz.hexacreatecore.bossrush.data;

import java.util.List;

public final class BossRushData {
  public record SequenceConfig(
    String onTriggerItem,
    String onTriggerSound,
    int areaOfEffect,
    List<PhaseConfig> phases
  ) {}

  public record PhaseConfig(
    String id,
    String name,
    String description,
    String music,
    String musicTitle,
    String onCompletionItem,
    String onCompletionSound,
    List<String> bosses,
    PhaseEffects effects
  ) {}

  public record PhaseEffects(
    String onStart,
    String onEnd
  ) {}

  public record CustomEffectScript(
    String particle,
    String sound,
    int count,
    double radius,
    boolean lightning
  ) {}
}

