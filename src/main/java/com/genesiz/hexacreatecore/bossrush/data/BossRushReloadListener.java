package com.genesiz.hexacreatecore.bossrush.data;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.bossrush.data.BossRushData.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = HexaCreateCore.MODID)
public final class BossRushReloadListener extends SimpleJsonResourceReloadListener {
  private static final Gson GSON = new Gson();
  private static SequenceConfig activeSequence = null;
  private static final Map<String, CustomEffectScript> CUSTOM_EFFECTS = new HashMap<>();
  private static final Map<String, BossHealthBarData.HealthBarStyle> HEALTH_BAR_STYLES = new HashMap<>();
  private static String rawHealthBarsJson = "{}";


  public BossRushReloadListener() {
    super(GSON, "boss_rush");
  }

  public static SequenceConfig getSequence() {
    return activeSequence;
  }

  public static CustomEffectScript getCustomEffect(String id) {
    return CUSTOM_EFFECTS.get(id);
  }

  @SubscribeEvent
  public static void onAddReloadListeners(AddReloadListenerEvent event) {
    event.addListener(new BossRushReloadListener());
  }

  @Override
  protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
    HexaCreateCore.LOGGER.info("[HealthBarDebug] Reload keys found: {}", objects.keySet());

    activeSequence = null;
    CUSTOM_EFFECTS.clear();
    HEALTH_BAR_STYLES.clear();
    rawHealthBarsJson = "{}";

    for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
      ResourceLocation loc = entry.getKey();
      if (!HexaCreateCore.MODID.equals(loc.getNamespace()) || !entry.getValue().isJsonObject()) continue;

      JsonObject root = entry.getValue().getAsJsonObject();
      String path = loc.getPath();

      if ("sequence".equals(path)) {
        activeSequence = parseSequence(root);
        HexaCreateCore.LOGGER.info("Loaded Boss Rush sequence with {} phases (AoE: {} blocks).",
          activeSequence.phases().size(), activeSequence.areaOfEffect());
      } else if (path.startsWith("effects/")) {
        String effectId = path.substring("effects/".length());
        CUSTOM_EFFECTS.put(effectId, parseEffectScript(root));
      } else if ("health_bars".equals(path)) {
        rawHealthBarsJson = root.toString();
        HEALTH_BAR_STYLES.putAll(parseHealthBarsMap(root));
        HexaCreateCore.LOGGER.info("Loaded {} custom Boss Rush health bar styles.", HEALTH_BAR_STYLES.size());
      }
    }
  }

  public static Map<String, BossHealthBarData.HealthBarStyle> parseHealthBarsMap(JsonObject root) {
    Map<String, BossHealthBarData.HealthBarStyle> map = new HashMap<>();
    for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
      if (!entry.getValue().isJsonObject()) continue;
      JsonObject obj = entry.getValue().getAsJsonObject();

      String displayName = obj.has("display_name") ? obj.get("display_name").getAsString() : "";
      int border = BossHealthBarData.HealthBarStyle.parseHexColor(
        obj.has("border_color") ? obj.get("border_color").getAsString() : "#FFAA00", 0xFFFFAA00
      );
      int bg = BossHealthBarData.HealthBarStyle.parseHexColor(
        obj.has("background_color") ? obj.get("background_color").getAsString() : "#CC110E1A", 0xCC110E1A
      );

      List<Integer> colors = new ArrayList<>();
      if (obj.has("bar_color") && obj.get("bar_color").isJsonArray()) {
        for (JsonElement c : obj.getAsJsonArray("bar_color")) {
          colors.add(BossHealthBarData.HealthBarStyle.parseHexColor(c.getAsString(), 0xFFFFFFFF));
        }
      }
      if (colors.isEmpty()) colors.add(0xFFFF3355);

      List<Float> positions = new ArrayList<>();
      if (obj.has("bar_color_pos") && obj.get("bar_color_pos").isJsonArray()) {
        for (JsonElement p : obj.getAsJsonArray("bar_color_pos")) {
          positions.add(p.getAsFloat());
        }
      }

      float pixelDensity = obj.has("pixel_density") ? net.minecraft.util.Mth.clamp(obj.get("pixel_density").getAsFloat(), 0.05f, 1.0f) : 0.5f;
      float colorAngle = obj.has("color_angle") ? net.minecraft.util.Mth.clamp(obj.get("color_angle").getAsFloat(), -90.0f, 90.0f) : 0.0f;
      String moveDir = obj.has("move_direction") ? obj.get("move_direction").getAsString() : "none";
      float moveSpeed = obj.has("move_speed") ? net.minecraft.util.Mth.clamp(obj.get("move_speed").getAsFloat(), 0.0f, 20.0f) : 0.0f;
      boolean bossRushOnly = !obj.has("boss_rush_only") || obj.get("boss_rush_only").getAsBoolean();

      List<BossHealthBarData.HpPhaseOverride> hpPhases = new ArrayList<>();
      if (obj.has("hp_phases") && obj.get("hp_phases").isJsonArray()) {
        for (JsonElement phaseElem : obj.getAsJsonArray("hp_phases")) {
          if (!phaseElem.isJsonObject()) continue;
          JsonObject pObj = phaseElem.getAsJsonObject();
          float belowHp = pObj.has("below_hp") ? pObj.get("below_hp").getAsFloat() : 100.0f;
          String pName = pObj.has("display_name") ? pObj.get("display_name").getAsString() : displayName;
          int pBorder = BossHealthBarData.HealthBarStyle.parseHexColor(
            pObj.has("border_color") ? pObj.get("border_color").getAsString() : "#FF1E56", border
          );
          int pBg = BossHealthBarData.HealthBarStyle.parseHexColor(
            pObj.has("background_color") ? pObj.get("background_color").getAsString() : "#17060C", bg
          );

          List<Integer> pColors = new ArrayList<>();
          if (pObj.has("bar_color") && pObj.get("bar_color").isJsonArray()) {
            for (JsonElement c : pObj.getAsJsonArray("bar_color")) {
              pColors.add(BossHealthBarData.HealthBarStyle.parseHexColor(c.getAsString(), 0xFFFFFFFF));
            }
          }
          if (pColors.isEmpty()) pColors.addAll(colors);

          List<Float> pPositions = new ArrayList<>();
          if (pObj.has("bar_color_pos") && pObj.get("bar_color_pos").isJsonArray()) {
            for (JsonElement p : pObj.getAsJsonArray("bar_color_pos")) {
              pPositions.add(p.getAsFloat());
            }
          }

          float pDensity = pObj.has("pixel_density") ? net.minecraft.util.Mth.clamp(pObj.get("pixel_density").getAsFloat(), 0.05f, 1.0f) : pixelDensity;
          float pAngle = pObj.has("color_angle") ? net.minecraft.util.Mth.clamp(pObj.get("color_angle").getAsFloat(), -90.0f, 90.0f) : colorAngle;
          String pDir = pObj.has("move_direction") ? pObj.get("move_direction").getAsString() : moveDir;
          float pSpeed = pObj.has("move_speed") ? net.minecraft.util.Mth.clamp(pObj.get("move_speed").getAsFloat(), 0.0f, 20.0f) : moveSpeed;

          hpPhases.add(new BossHealthBarData.HpPhaseOverride(belowHp, pName, pBorder, pBg, pColors, pPositions, pDensity, pAngle, pDir, pSpeed));
        }
      }

      map.put(entry.getKey(), new BossHealthBarData.HealthBarStyle(
        displayName, border, bg, colors, positions, pixelDensity, colorAngle, moveDir, moveSpeed, bossRushOnly, hpPhases
      ));
    }
    return map;
  }


  private static SequenceConfig parseSequence(JsonObject root) {
    String onTrigger = root.has("on_trigger") ? root.get("on_trigger").getAsString() : "";
    String onTriggerSound = root.has("on_trigger_sound") ? root.get("on_trigger_sound").getAsString() : "minecraft:item.totem.use";
    int areaOfEffect = root.has("area_of_effect") ? root.get("area_of_effect").getAsInt() : 64;

    List<PhaseConfig> phases = new ArrayList<>();
    if (root.has("phases") && root.get("phases").isJsonArray()) {
      for (JsonElement elem : root.getAsJsonArray("phases")) {
        if (!elem.isJsonObject()) continue;
        JsonObject p = elem.getAsJsonObject();

        String id = p.has("id") ? p.get("id").getAsString() : "phase_" + phases.size();
        String name = p.has("name") ? p.get("name").getAsString() : id;
        String desc = p.has("description") ? p.get("description").getAsString() : "";
        String music = p.has("music") ? p.get("music").getAsString() : "";
        String musicTitle = p.has("music_title") ? p.get("music_title").getAsString() : music;
        String onCompletion = p.has("on_completion") ? p.get("on_completion").getAsString() : "";
        String onCompletionSound = p.has("on_completion_sound") ? p.get("on_completion_sound").getAsString() : "minecraft:item.totem.use";

        List<String> bosses = new ArrayList<>();
        if (p.has("bosses") && p.get("bosses").isJsonArray()) {
          JsonArray bossArr = p.getAsJsonArray("bosses");
          for (JsonElement b : bossArr) {
            bosses.add(b.getAsString());
          }
        }

        String onStart = "";
        String onEnd = "";
        if (p.has("effects") && p.get("effects").isJsonObject()) {
          JsonObject eff = p.getAsJsonObject("effects");
          if (eff.has("on_start")) onStart = eff.get("on_start").getAsString();
          if (eff.has("on_end")) onEnd = eff.get("on_end").getAsString();
        }

        phases.add(new PhaseConfig(id, name, desc, music, musicTitle, onCompletion, onCompletionSound, bosses, new PhaseEffects(onStart, onEnd)));
      }
    }

    return new SequenceConfig(onTrigger, onTriggerSound, areaOfEffect, phases);
  }

  private static CustomEffectScript parseEffectScript(JsonObject obj) {
    String particle = obj.has("particle") ? obj.get("particle").getAsString() : "minecraft:enchant";
    String sound = obj.has("sound") ? obj.get("sound").getAsString() : "";
    int count = obj.has("count") ? obj.get("count").getAsInt() : 60;
    double radius = obj.has("radius") ? obj.get("radius").getAsDouble() : 3.5D;
    boolean lightning = obj.has("lightning") && obj.get("lightning").getAsBoolean();
    return new CustomEffectScript(particle, sound, count, radius, lightning);
  }
  
  public static Map<String, BossHealthBarData.HealthBarStyle> getHealthBarStyles() {
    return HEALTH_BAR_STYLES;
  }

  public static String getRawHealthBarsJson() {
    return rawHealthBarsJson;
  }
}
