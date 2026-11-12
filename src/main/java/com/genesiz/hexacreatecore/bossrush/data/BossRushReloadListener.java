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
    activeSequence = null;
    CUSTOM_EFFECTS.clear();

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
      }
    }
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
}
