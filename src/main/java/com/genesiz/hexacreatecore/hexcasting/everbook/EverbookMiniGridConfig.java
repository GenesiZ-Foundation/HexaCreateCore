package com.genesiz.hexacreatecore.hexcasting.everbook;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EverbookMiniGridConfig {

  public static int pointColor = 0xFFFFD700;
  public static int pointHoverColor = 0xFF10B981;
  public static int pointActiveColor = 0xFF34D399;
  public static int lineColor = 0xFF10B981;
  public static int outlineColor = 0xFF4A0416;
  public static float lineSize = 0.011f;
  public static float outlineSizeMultiplier = 1.95f;
  public static double wobbleIntensity = 0.015D;

  private static boolean loaded = false;

  private EverbookMiniGridConfig() {}

  public static void reload() {
    loaded = false;
    ensureLoaded();
  }

  public static void ensureLoaded() {
    if (loaded) return;
    loaded = true;
    try {
      Path cfgPath = FMLPaths.CONFIGDIR.get().resolve("hexacreatecore").resolve("everbook_minigrid.json");
      if (!Files.exists(cfgPath)) {
        Files.createDirectories(cfgPath.getParent());
        JsonObject def = new JsonObject();
        def.addProperty("point_color", "0xFFFFD700");
        def.addProperty("point_hover_color_hex", "0xFF10B981");
        def.addProperty("line_color", "0xFF10B981");
        def.addProperty("outline_color", "0xFF4A0416");
        def.addProperty("line_size", 0.011f);
        def.addProperty("outline_size", 1.95f);
        def.addProperty("wobble_intensity", 0.015D);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(cfgPath, gson.toJson(def), StandardCharsets.UTF_8);
        return;
      }

      JsonObject obj = JsonParser.parseString(Files.readString(cfgPath, StandardCharsets.UTF_8)).getAsJsonObject();
      if (obj.has("point_color")) pointColor = parseHex(obj.get("point_color").getAsString(), pointColor);
      if (obj.has("point_hover_color_hex")) {
        pointHoverColor = parseHex(obj.get("point_hover_color_hex").getAsString(), pointHoverColor);
        pointActiveColor = pointHoverColor;
      }
      if (obj.has("line_color")) lineColor = parseHex(obj.get("line_color").getAsString(), lineColor);
      if (obj.has("outline_color")) outlineColor = parseHex(obj.get("outline_color").getAsString(), outlineColor);
      if (obj.has("line_size")) lineSize = obj.get("line_size").getAsFloat();
      if (obj.has("outline_size")) outlineSizeMultiplier = obj.get("outline_size").getAsFloat();
      if (obj.has("wobble_intensity")) wobbleIntensity = obj.get("wobble_intensity").getAsDouble();
    } catch (Exception ignored) {}
  }

  private static int parseHex(String raw, int fallback) {
    try {
      String clean = raw.replace("#", "").trim();
      if (clean.startsWith("0x") || clean.startsWith("0X")) clean = clean.substring(2);
      return (int) Long.parseLong(clean, 16);
    } catch (Exception e) {
      return fallback;
    }
  }
}
