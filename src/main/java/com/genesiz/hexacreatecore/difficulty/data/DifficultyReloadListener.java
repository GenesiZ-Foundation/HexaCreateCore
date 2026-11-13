package com.genesiz.hexacreatecore.difficulty.data;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.difficulty.data.DifficultyData.DifficultyProfile;
import com.genesiz.hexacreatecore.difficulty.data.DifficultyData.EntityOverride;
import com.genesiz.hexacreatecore.difficulty.data.DifficultyData.MobEffectEntry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
public final class DifficultyReloadListener extends SimpleJsonResourceReloadListener {
  private static final Gson GSON = new GsonBuilder().create();
  private static final Map<String, DifficultyProfile> PROFILES = new HashMap<>();
  private static String rawGaugeBarsJson = "{}";
  private static String rawParrySoundsJson = "{}";

  public DifficultyReloadListener() {
    super(GSON, "difficulty");
  }

  @SubscribeEvent
  public static void onAddReloadListeners(AddReloadListenerEvent event) {
    event.addListener(new DifficultyReloadListener());
  }

  public static DifficultyProfile getProfile(String tierId) {
    return PROFILES.getOrDefault(tierId.toLowerCase(), DifficultyProfile.empty(tierId));
  }

  @Override
  protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
    PROFILES.clear();
    JsonObject combinedGauges = new JsonObject();

    for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
      ResourceLocation loc = entry.getKey();
      if (!HexaCreateCore.MODID.equals(loc.getNamespace()) || !entry.getValue().isJsonObject()) continue;

      String tierId = loc.getPath().toLowerCase();
      JsonObject root = entry.getValue().getAsJsonObject();

      if ("parry_sounds".equals(tierId)) {
        rawParrySoundsJson = root.toString();
        HexaCreateCore.LOGGER.info("Loaded custom parry_sounds.json configuration.");
        continue;
      }

      List<String> desc = parseStringList(root, "description");
      double globalHp = root.has("hp_multiplier") ? root.get("hp_multiplier").getAsDouble() : 1.0D;
      double globalDmg = root.has("damage_multiplier") ? root.get("damage_multiplier").getAsDouble() : 1.0D;

      DifficultyData.DifficultyTier tierEnum = DifficultyData.DifficultyTier.fromId(tierId);
      com.genesiz.hexacreatecore.bossrush.data.BossHealthBarData.HealthBarStyle gaugeStyle = DifficultyProfile.defaultGauge(tierEnum.accentColor);
      if (root.has("gauge_bar") && root.get("gauge_bar").isJsonObject()) {
        JsonObject wrapper = new JsonObject();
        wrapper.add(tierId, root.getAsJsonObject("gauge_bar"));
        combinedGauges.add(tierId, root.getAsJsonObject("gauge_bar"));
        var parsedMap = com.genesiz.hexacreatecore.bossrush.data.BossRushReloadListener.parseHealthBarsMap(wrapper);
        if (parsedMap.containsKey(tierId)) {
          gaugeStyle = parsedMap.get(tierId);
        }
      }

      List<MobEffectEntry> spawnBuffs = parseEffects(root, "spawn_buffs");
      List<MobEffectEntry> hitDebuffs = parseEffects(root, "on_hit_debuffs");
      List<String> callbacks = parseStringList(root, "attack_callbacks");

      Map<String, EntityOverride> overrides = new HashMap<>();
      if (root.has("entities") && root.get("entities").isJsonObject()) {
        JsonObject ents = root.getAsJsonObject("entities");
        for (Map.Entry<String, JsonElement> e : ents.entrySet()) {
          if (!e.getValue().isJsonObject()) continue;
          JsonObject eObj = e.getValue().getAsJsonObject();
          double eHp = eObj.has("hp_multiplier") ? eObj.get("hp_multiplier").getAsDouble() : globalHp;
          double eDmg = eObj.has("damage_multiplier") ? eObj.get("damage_multiplier").getAsDouble() : globalDmg;
          overrides.put(e.getKey(), new EntityOverride(
            eHp, eDmg,
            parseEffects(eObj, "spawn_buffs"),
            parseEffects(eObj, "on_hit_debuffs"),
            parseStringList(eObj, "attack_callbacks")
          ));
        }
      }

      PROFILES.put(tierId, new DifficultyProfile(tierId, desc, globalHp, globalDmg, gaugeStyle, spawnBuffs, hitDebuffs, callbacks, overrides));
      HexaCreateCore.LOGGER.info("Loaded difficulty profile '{}' ({} entity overrides).", tierId, overrides.size());
    }

    rawGaugeBarsJson = combinedGauges.toString();
  }

  private static List<MobEffectEntry> parseEffects(JsonObject obj, String key) {
    List<MobEffectEntry> list = new ArrayList<>();
    if (!obj.has(key) || !obj.get(key).isJsonArray()) return list;
    for (JsonElement el : obj.getAsJsonArray(key)) {
      if (!el.isJsonObject()) continue;
      JsonObject eo = el.getAsJsonObject();
      String id = eo.has("effect") ? eo.get("effect").getAsString() : "";
      int dur = eo.has("duration") ? eo.get("duration").getAsInt() : 100;
      int amp = eo.has("amplifier") ? eo.get("amplifier").getAsInt() : 0;
      float chance = eo.has("chance") ? eo.get("chance").getAsFloat() : 1.0f;
      if (!id.isEmpty()) list.add(new MobEffectEntry(id, dur, amp, chance));
    }
    return list;
  }

  private static List<String> parseStringList(JsonObject obj, String key) {
    List<String> list = new ArrayList<>();
    if (!obj.has(key) || !obj.get(key).isJsonArray()) return list;
    for (JsonElement el : obj.getAsJsonArray(key)) {
      list.add(el.getAsString());
    }
    return list;
  }

  public static String getRawGaugeBarsJson() { return rawGaugeBarsJson;}
  public static String getRawParrySoundsJson() { return rawParrySoundsJson; }
}
