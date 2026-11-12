package com.genesiz.hexacreatecore.bossrush.data;

import net.minecraft.util.Mth;
import java.util.List;

public final class BossHealthBarData {

  
  public record HpPhaseOverride(
    float belowHp,
    String displayName,
    int borderColor,
    int backgroundColor,
    List<Integer> barColors,
    List<Float> barColorPositions,
    float pixelDensity,
    float colorAngle,
    String moveDirection,
    float moveSpeed
  ) {}


  public record HealthBarStyle(
    String displayName,
    int borderColor,
    int backgroundColor,
    List<Integer> barColors,
    List<Float> barColorPositions,
    float pixelDensity,
    float colorAngle,
    String moveDirection,
    float moveSpeed,
    boolean bossRushOnly,
    List<HpPhaseOverride> hpPhases
  ) {

    public int sampleGradient(float t) {
      if (barColors.isEmpty()) return 0xFFFFFFFF;
      if (barColors.size() == 1) return barColors.get(0);

      float shiftedT = t;
      boolean moving = moveSpeed > 0.0f && ("left".equalsIgnoreCase(moveDirection) || "right".equalsIgnoreCase(moveDirection));
      if (moving) {
        float timeSec = (System.currentTimeMillis() % 600000L) / 1000.0f;
        float offset = timeSec * (moveSpeed * 0.15f);
        shiftedT = "left".equalsIgnoreCase(moveDirection) ? (t + offset) : (t - offset);
        shiftedT = ((shiftedT % 1.0f) + 1.0f) % 1.0f;
      }

      float clamped = Mth.clamp(shiftedT, 0.0f, 1.0f);
      int count = barColors.size();
      float firstPos = getStopPos(0, count);
      float lastPos = getStopPos(count - 1, count);

      if (moving && (firstPos > clamped || clamped > lastPos)) {
        float wrapSpan = (1.0f - lastPos) + firstPos;
        if (wrapSpan > 0.0001f) {
          float distFromLast = (clamped >= lastPos) ? (clamped - lastPos) : ((1.0f - lastPos) + clamped);
          return lerpArgb(barColors.get(count - 1), barColors.get(0), distFromLast / wrapSpan);
        }
      }

      if (firstPos >= clamped) return barColors.get(0);
      if (clamped >= lastPos) return barColors.get(count - 1);

      for (int i = 0; (count - 1) > i; i++) {
        float p0 = getStopPos(i, count);
        float p1 = getStopPos(i + 1, count);
        if (clamped >= p0 && p1 >= clamped) {
          float span = p1 - p0;
          float localAlpha = (span > 0.0001f) ? ((clamped - p0) / span) : 0.0f;
          return lerpArgb(barColors.get(i), barColors.get(i + 1), localAlpha);
        }
      }
      return barColors.get(count - 1);
    }

    private float getStopPos(int index, int total) {
      if (barColorPositions != null && barColorPositions.size() > index) {
        return Mth.clamp(barColorPositions.get(index), 0.0f, 1.0f);
      }
      return (total > 1) ? ((float) index / (float) (total - 1)) : 0.0f;
    }

    public static int lerpArgb(int c0, int c1, float t) {
      int a0 = (c0 >>> 24) & 0xFF, r0 = (c0 >>> 16) & 0xFF, g0 = (c0 >>> 8) & 0xFF, b0 = c0 & 0xFF;
      int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
      int a = (int) Mth.lerp(t, a0, a1);
      int r = (int) Mth.lerp(t, r0, r1);
      int g = (int) Mth.lerp(t, g0, g1);
      int b = (int) Mth.lerp(t, b0, b1);
      return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int parseHexColor(String hex, int fallback) {
      if (hex == null || hex.isEmpty()) return fallback;
      String clean = hex.trim();
      if (clean.startsWith("#")) clean = clean.substring(1);
      if (clean.startsWith("0x") || clean.startsWith("0X")) clean = clean.substring(2);
      try {
        long val = Long.parseLong(clean, 16);
        return (clean.length() == 6) ? (int) (0xFF000000L | val) : (int) val;
      } catch (NumberFormatException e) {
        return fallback;
      }
    }

      public HealthBarStyle resolveForHp(float currentHp) {
      if (hpPhases == null || hpPhases.isEmpty()) return this;
      HpPhaseOverride best = null;
      for (HpPhaseOverride phase : hpPhases) {
        if (phase.belowHp() >= currentHp) {
          if (best == null || best.belowHp() > phase.belowHp()) {
            best = phase;
          }
        }
      }
      if (best == null) return this;
      String activeName = (best.displayName() != null && !best.displayName().isEmpty()) ? best.displayName() : displayName;
      return new HealthBarStyle(
        activeName,
        best.borderColor(),
        best.backgroundColor(),
        best.barColors(),
        best.barColorPositions(),
        best.pixelDensity(),
        best.colorAngle(),
        best.moveDirection(),
        best.moveSpeed(),
        bossRushOnly,
        List.of()
      );
    }
  }
}
