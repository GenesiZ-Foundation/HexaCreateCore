package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.bossrush.dragon.DragonAnimState.DragonAction;
import com.genesiz.hexacreatecore.bossrush.dragon.DragonPatternResolver.PatternTreeSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = HexaCreateCore.MODID)
public final class DragonPatternReloadListener extends SimpleJsonResourceReloadListener {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final ResourceLocation KEYS_ID =
    ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "extreme_ender_dragon_keys");
  private static final ResourceLocation PATTERNS_ID =
    ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "extreme_ender_dragon_patterns");

  private static Map<String, DragonAction> actionKeys = Map.of();
  private static PatternTreeSet patternTree = PatternTreeSet.EMPTY;
  private static boolean loadedOnce = false;

  public DragonPatternReloadListener() {
    super(GSON, "dragons");
  }

  @SubscribeEvent
  public static void onAddReloadListeners(AddReloadListenerEvent event) {
    event.addListener(new DragonPatternReloadListener());
  }

  public static PatternTreeSet getPatternTree() {
    if (!loadedOnce) {
      loadFromClasspathFallback();
    }
    return patternTree;
  }

  public static Map<String, DragonAction> getActionKeys() {
    if (!loadedOnce) {
      loadFromClasspathFallback();
    }
    return actionKeys;
  }

  @Override
  protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
    JsonElement keysEl = objects.get(KEYS_ID);
    JsonElement patternsEl = objects.get(PATTERNS_ID);

    Map<String, DragonAction> parsedKeys = new HashMap<>();
    if (keysEl != null && keysEl.isJsonObject()) {
      parsedKeys = parseKeysObject(keysEl.getAsJsonObject());
    }

    PatternTreeSet parsedTree = PatternTreeSet.EMPTY;
    if (patternsEl != null && patternsEl.isJsonObject()) {
      parsedTree = DragonPatternResolver.parsePatternTree(patternsEl.getAsJsonObject(), parsedKeys);
    }

    actionKeys = Map.copyOf(parsedKeys);
    patternTree = parsedTree;
    loadedOnce = true;
    HexaCreateCore.LOGGER.info("Loaded {} dragon action keys and pattern tree.", actionKeys.size());
  }

  private static Map<String, DragonAction> parseKeysObject(JsonObject obj) {
    Map<String, DragonAction> map = new HashMap<>();
    for (var entry : obj.entrySet()) {
      if (!entry.getValue().isJsonPrimitive()) continue;
      String enumName = entry.getValue().getAsString().trim().toUpperCase(java.util.Locale.ROOT);
      try {
        map.put(entry.getKey().trim(), DragonAction.valueOf(enumName));
      } catch (IllegalArgumentException ex) {
        HexaCreateCore.LOGGER.warn("Unknown DragonAction '{}' in extreme_ender_dragon_keys.json", enumName);
      }
    }
    return map;
  }

  private static void loadFromClasspathFallback() {
    loadedOnce = true;
    try (
      var keyStream = DragonPatternReloadListener.class.getResourceAsStream("/data/hexacreatecore/dragons/extreme_ender_dragon_keys.json");
      var patStream = DragonPatternReloadListener.class.getResourceAsStream("/data/hexacreatecore/dragons/extreme_ender_dragon_patterns.json")
    ) {
      if (keyStream != null) {
        JsonObject keyObj = JsonParser.parseReader(new InputStreamReader(keyStream, StandardCharsets.UTF_8)).getAsJsonObject();
        actionKeys = Map.copyOf(parseKeysObject(keyObj));
      }
      if (patStream != null) {
        JsonObject patObj = JsonParser.parseReader(new InputStreamReader(patStream, StandardCharsets.UTF_8)).getAsJsonObject();
        patternTree = DragonPatternResolver.parsePatternTree(patObj, actionKeys);
      }
    } catch (Exception e) {
      HexaCreateCore.LOGGER.error("Failed fallback load for dragon pattern JSONs", e);
    }
  }
}

