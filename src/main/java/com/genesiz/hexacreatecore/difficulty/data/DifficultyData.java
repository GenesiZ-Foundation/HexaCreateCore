package com.genesiz.hexacreatecore.difficulty.data;

import java.util.List;
import java.util.Map;

import com.genesiz.hexacreatecore.bossrush.data.BossHealthBarData.HealthBarStyle;

public final class DifficultyData {

  public enum DifficultyTier {
    NORMAL("normal", "Normal", 0, 0xFFAAAAAA),
    ETHEREAL("ethereal", "Ethereal", 10, 0xFF21B7D1),
    ABSOLUTION("absolution", "Absolution", 15, 0xFFB233FF),
    GENESIS("genesis", "Genesis", 20, 0xFFFF1E56);

    public final String id;
    public final String displayName;
    public final int riposteMaxCharges;
    public final int accentColor;

    DifficultyTier(String id, String displayName, int riposteMaxCharges, int accentColor) {
      this.id = id;
      this.displayName = displayName;
      this.riposteMaxCharges = riposteMaxCharges;
      this.accentColor = accentColor;
    }

    public static DifficultyTier fromId(String id) {
      for (DifficultyTier t : values()) {
        if (t.id.equalsIgnoreCase(id)) return t;
      }
      return NORMAL;
    }
  }

  public record MobEffectEntry(
    String effectId,
    int durationTicks,
    int amplifier,
    float chance
  ) {}

  public record EntityOverride(
    double hpMultiplier,
    double damageMultiplier,
    List<MobEffectEntry> spawnBuffs,
    List<MobEffectEntry> onHitDebuffs,
    List<String> attackCallbacks
  ) {}

  public record DifficultyProfile(
    String id,
    List<String> description,
    double globalHpMultiplier,
    double globalDamageMultiplier,
    HealthBarStyle gaugeStyle,
    List<MobEffectEntry> globalSpawnBuffs,
    List<MobEffectEntry> globalOnHitDebuffs,
    List<String> globalAttackCallbacks,
    Map<String, EntityOverride> entityOverrides
  ) {
    public static HealthBarStyle defaultGauge(int accent) {
      return new HealthBarStyle(
        "", accent, 0xCC110E1A, List.of(0xFF1A1626, accent, 0xFFFFFFFF),
        List.of(0.15f, 0.65f, 0.95f), 0.5f, -15.0f, "right", 1.5f, false, List.of()
      );
    }

    public static DifficultyProfile empty(String id) {
      DifficultyTier tier = DifficultyTier.fromId(id);
      return new DifficultyProfile(id, List.of(), 1.0D, 1.0D, defaultGauge(tier.accentColor), List.of(), List.of(), List.of(), Map.of());
    }
  }
}

