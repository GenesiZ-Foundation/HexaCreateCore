package com.genesiz.hexacreatecore.threedified.text;

import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

import java.util.List;

public record TextEffectStyle(
  List<Integer> colors,
  List<Float> stops,
  float scrollSpeed,
  float waveAmplitude,
  float waveFrequency,
  BackdropMode backdropMode,
  int glowColor,
  int secondaryGlowColor,
  boolean sparkles,
  boolean rainbowStroke,
  boolean hasPrismaticOutline,
  float gradientAngleDeg
) {
  public TextEffectStyle(
    List<Integer> colors, List<Float> stops, float scrollSpeed,
    float waveAmplitude, float waveFrequency, BackdropMode backdropMode,
    int glowColor, int secondaryGlowColor, boolean sparkles
  ) {
    this(colors, stops, scrollSpeed, waveAmplitude, waveFrequency, backdropMode, glowColor, secondaryGlowColor, sparkles, false, false, 0.0f);
  }

  public TextEffectStyle(
    List<Integer> colors, List<Float> stops, float scrollSpeed,
    float waveAmplitude, float waveFrequency, BackdropMode backdropMode,
    int glowColor, int secondaryGlowColor, boolean sparkles, boolean rainbowStroke
  ) {
    this(colors, stops, scrollSpeed, waveAmplitude, waveFrequency, backdropMode, glowColor, secondaryGlowColor, sparkles, rainbowStroke, false, 0.0f);
  }

  public TextEffectStyle(
    List<Integer> colors, List<Float> stops, float scrollSpeed,
    float waveAmplitude, float waveFrequency, BackdropMode backdropMode,
    int glowColor, int secondaryGlowColor, boolean sparkles, boolean rainbowStroke, boolean hasPrismaticOutline
  ) {
    this(colors, stops, scrollSpeed, waveAmplitude, waveFrequency, backdropMode, glowColor, secondaryGlowColor, sparkles, rainbowStroke, hasPrismaticOutline, 0.0f);
  }

  public enum BackdropMode {
    NONE,
    GLOW_BLOOM,
    CHROMATIC_GLITCH,
    PRISMATIC_OUTLINE,
    SPECTRUM_STROKE
  }

  public static TextEffectStyle prismaticOutline() {
    return new TextEffectStyle(
      List.of(0xFF0E081C, 0xFF28124D, 0xFF0E081C),
      List.of(0.0f, 0.5f, 1.0f),
      2.2f, 0.35f, 0.25f,
      BackdropMode.PRISMATIC_OUTLINE, 0xFF00F5FF, 0xFFFF2A6D, true
    );
  }

  public static TextEffectStyle outline(int hexRgb) {
    int base = ensureOpaque(hexRgb);
    int darkCore = shade(base, 0.16f);
    int midCore = shade(base, 0.32f);
    int brightEdge = tintWhite(base, 0.65f);
    return new TextEffectStyle(
      List.of(darkCore, midCore, darkCore),
      List.of(0.0f, 0.5f, 1.0f),
      2.0f, 0.35f, 0.25f,
      BackdropMode.PRISMATIC_OUTLINE, base, brightEdge, false
    );
  }
  public static TextEffectStyle outline(String hex) { return outline(parseHex(hex)); }


  /** Iridescent chromatic-aberration glitch shader (default rainbow palette) */
  public static TextEffectStyle glitch() {
    return new TextEffectStyle(
      List.of(0xFF38BDF8, 0xFFF472B6, 0xFFA3E635, 0xFFC084FC, 0xFF38BDF8),
      List.of(0.0f, 0.25f, 0.5f, 0.75f, 1.0f),
      2.4f, 1.1f, 0.45f,
      BackdropMode.CHROMATIC_GLITCH, 0x88FF2A6D, 0x8800F5FF, false
    );
  }

  /** Chromatic-aberration glitch shader tinted around a single hexadecimal color (e.g. 0xFF21B7D1 or "#21B7D1") */
  public static TextEffectStyle glitch(int hexRgb) {
    int base = ensureOpaque(hexRgb);
    int dark = shade(base, 0.45f);
    int bright = tintWhite(base, 0.55f);
    return new TextEffectStyle(
      List.of(dark, base, bright, base, dark),
      List.of(0.0f, 0.3f, 0.5f, 0.7f, 1.0f),
      2.4f, 1.1f, 0.45f,
      BackdropMode.CHROMATIC_GLITCH, 0x88FF2A6D, 0x8800F5FF, false
    );
  }

  /** Soft breathing back-glow + smooth gradient derived from any single hexadecimal color */
  public static TextEffectStyle aurora(int hexRgb) {
    int base = ensureOpaque(hexRgb);
    int deep = shade(base, 0.55f);
    int highlight = tintWhite(base, 0.65f);
    int primaryGlow = withAlpha(base, 0x77);
    int outerGlow = withAlpha(highlight, 0x3A);
    return new TextEffectStyle(
      List.of(deep, base, highlight, base, deep),
      List.of(0.0f, 0.3f, 0.5f, 0.75f, 1.0f),
      1.4f, 0.45f, 0.3f,
      BackdropMode.GLOW_BLOOM, primaryGlow, outerGlow, false
    );
  }

  /** Soft back-glow + gradient + occasional twinkling 4-point star glints from any hexadecimal color */
  public static TextEffectStyle starly(int hexRgb) {
    int base = ensureOpaque(hexRgb);
    int deep = shade(base, 0.50f);
    int highlight = tintWhite(base, 0.70f);
    int primaryGlow = withAlpha(base, 0x70);
    int outerGlow = withAlpha(highlight, 0x35);
    return new TextEffectStyle(
      List.of(deep, base, highlight, base, deep),
      List.of(0.0f, 0.32f, 0.52f, 0.76f, 1.0f),
      1.6f, 0.4f, 0.25f,
      BackdropMode.GLOW_BLOOM, primaryGlow, outerGlow, true
    );
  }

  public static TextEffectStyle glitch(String hex) { return glitch(parseHex(hex)); }
  public static TextEffectStyle aurora(String hex) { return aurora(parseHex(hex)); }
  public static TextEffectStyle starly(String hex) { return starly(parseHex(hex)); }

  public static int parseHex(String hex) {
    if (hex == null || hex.isEmpty()) return 0xFFFFFFFF;
    String clean = hex.startsWith("#") ? hex.substring(1) : hex;
    if (clean.startsWith("0x") || clean.startsWith("0X")) clean = clean.substring(2);
    long val = Long.parseLong(clean, 16);
    if (clean.length() == 6) val |= 0xFF000000L;
    return (int) val;
  }

  private static int ensureOpaque(int rgb) {
    return ((rgb >>> 24) == 0) ? (0xFF000000 | (rgb & 0x00FFFFFF)) : rgb;
  }

  private static int withAlpha(int argb, int alpha) {
    return FastColor.ARGB32.color(alpha & 0xFF, FastColor.ARGB32.red(argb), FastColor.ARGB32.green(argb), FastColor.ARGB32.blue(argb));
  }

  private static int shade(int argb, float factor) {
    int r = Math.round(FastColor.ARGB32.red(argb) * factor);
    int g = Math.round(FastColor.ARGB32.green(argb) * factor);
    int b = Math.round(FastColor.ARGB32.blue(argb) * factor);
    return FastColor.ARGB32.color(255, r, g, b);
  }

  private static int tintWhite(int argb, float amount) {
    int r = Math.round(Mth.lerp(amount, FastColor.ARGB32.red(argb), 255.0f));
    int g = Math.round(Mth.lerp(amount, FastColor.ARGB32.green(argb), 255.0f));
    int b = Math.round(Mth.lerp(amount, FastColor.ARGB32.blue(argb), 255.0f));
    return FastColor.ARGB32.color(255, r, g, b);
  }

  public int sampleColor(float u, float timeSec) {
    return sampleColor(u, 0.5f, timeSec);
  }

  public int sampleColor(float u, float v, float timeSec) {
    if (colors == null || colors.isEmpty()) return 0xFFFFFFFF;
    if (colors.size() == 1) return colors.get(0);

    float coord = u;
    if (Math.abs(gradientAngleDeg) > 0.01f) {
      double rad = Math.toRadians(gradientAngleDeg);
      coord = (float) ((u * Math.cos(rad)) + (v * Math.sin(rad)));
    }

    float shifted = (coord - (timeSec * scrollSpeed * 0.25f)) % 1.0f;
    if (0.0f > shifted) shifted += 1.0f;

    int count = colors.size();
    for (int i = 0; (count - 1) > i; i++) {
      float s0 = (stops != null && stops.size() > i) ? stops.get(i) : (i / (float) (count - 1));
      float s1 = (stops != null && stops.size() > (i + 1)) ? stops.get(i + 1) : ((i + 1) / (float) (count - 1));
      if (shifted >= s0 && s1 >= shifted) {
        float localT = (s1 > s0) ? ((shifted - s0) / (s1 - s0)) : 0.0f;
        return lerpArgb(colors.get(i), colors.get(i + 1), localT);
      }
    }
    return colors.get(count - 1);
  }


  public static int lerpArgb(int c0, int c1, float t) {
    t = Mth.clamp(t, 0.0f, 1.0f);
    int a = Math.round(Mth.lerp(t, FastColor.ARGB32.alpha(c0), FastColor.ARGB32.alpha(c1)));
    int r = Math.round(Mth.lerp(t, FastColor.ARGB32.red(c0), FastColor.ARGB32.red(c1)));
    int g = Math.round(Mth.lerp(t, FastColor.ARGB32.green(c0), FastColor.ARGB32.green(c1)));
    int b = Math.round(Mth.lerp(t, FastColor.ARGB32.blue(c0), FastColor.ARGB32.blue(c1)));
    return FastColor.ARGB32.color(a, r, g, b);
  }

  /** Two-color Aurora gradient + soft glow (e.g. White + HexCasting Purple) */
  public static TextEffectStyle aurora(int primaryRgb, int secondaryRgb) {
    int c1 = ensureOpaque(primaryRgb);
    int c2 = ensureOpaque(secondaryRgb);
    int primaryGlow = withAlpha(c2, 0x77);
    int outerGlow = withAlpha(c1, 0x3A);
    return new TextEffectStyle(
      List.of(c1, c2, tintWhite(c2, 0.55f), c2, c1),
      List.of(0.0f, 0.3f, 0.5f, 0.75f, 1.0f),
      1.4f, 0.35f, 0.28f,
      BackdropMode.GLOW_BLOOM, primaryGlow, outerGlow, false
    );
  }

  /** Custom core + animated edge outline (e.g. Gold + White for Raven's Mind) */
  public static TextEffectStyle outline(int coreRgb, int edgeRgb) {
    int core = ensureOpaque(coreRgb);
    int edge = ensureOpaque(edgeRgb);
    return new TextEffectStyle(
      List.of(shade(core, 0.75f), core, tintWhite(core, 0.35f), core, shade(core, 0.75f)),
      List.of(0.0f, 0.3f, 0.5f, 0.7f, 1.0f),
      2.0f, 0.3f, 0.25f,
      BackdropMode.PRISMATIC_OUTLINE, core, edge, false
    );
  }

  /** Radiant pearlescent white core surrounded by a fast-cycling full-spectrum rainbow outline */
  public static TextEffectStyle spectrum() {
    return new TextEffectStyle(
      List.of(0xFFFFFFFF, 0xFFFEF9C3, 0xFFE0F2FE, 0xFFFCE7F3, 0xFFFFFFFF),
      List.of(0.0f, 0.25f, 0.5f, 0.75f, 1.0f),
      2.6f, 0.45f, 0.30f,
      BackdropMode.SPECTRUM_STROKE, 0xFFFF2A6D, 0xFF00F5FF, true
    );
  }

  public static int sampleSpectrum(float phase) {
    float p = phase % 1.0f;
    if (0.0f > p) p += 1.0f;
    int[] stops = { 0xFFFF2A54, 0xFFFFA500, 0xFFFACC15, 0xFF4ADE80, 0xFF00F5FF, 0xFFC084FC, 0xFFFF2A54 };
    float scaled = p * (stops.length - 1);
    int idx = Math.min(stops.length - 2, (int) scaled);
    return lerpArgb(stops[idx], stops[idx + 1], scaled - idx);
  }

}
