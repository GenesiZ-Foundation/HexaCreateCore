package com.genesiz.hexacreatecore.dialogue.hologram;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.util.*;

public final class BlueprintReloadListener extends SimpleJsonResourceReloadListener {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final Map<String, MultiblockBlueprintData> BLUEPRINTS = new HashMap<>();
  private static boolean hasLoadedOnce = false;

  public BlueprintReloadListener() {
    super(GSON, "blueprints"); // Scans assets/<namespace>/blueprints/*.json
  }

  public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
    event.registerReloadListener(new BlueprintReloadListener());
  }

  public static MultiblockBlueprintData getBlueprint(String structureId) {
    if (!hasLoadedOnce) {
      forceLoadFromClientResources();
    }
    return BLUEPRINTS.get(structureId);
  }

  private static void forceLoadFromClientResources() {
    hasLoadedOnce = true;
    var rm = Minecraft.getInstance().getResourceManager();
    var resources = rm.listResources("blueprints", loc -> loc.getPath().endsWith(".json"));
    for (var entry : resources.entrySet()) {
      try (var reader = entry.getValue().openAsReader()) {
        JsonElement json = GsonHelper.fromJson(GSON, reader, JsonElement.class);
        if (json != null) {
          parseAndStore(json);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
    BLUEPRINTS.clear();
    hasLoadedOnce = true;
    for (JsonElement element : objects.values()) {
      parseAndStore(element);
    }
  }

  private static void parseAndStore(JsonElement element) {
    JsonObject root = GsonHelper.convertToJsonObject(element, "blueprint");
    String structureId = GsonHelper.getAsString(root, "structure_id");

    JsonObject legendObj = GsonHelper.getAsJsonObject(root, "legend");
    Map<Character, BlockState> legendMap = new HashMap<>();
    for (Map.Entry<String, JsonElement> entry : legendObj.entrySet()) {
      String key = entry.getKey();
      if (key.isEmpty()) continue;
      ResourceLocation blockId = ResourceLocation.parse(GsonHelper.convertToString(entry.getValue(), "block_id"));
      Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(Blocks.AIR);
      if (block != Blocks.AIR) {
        legendMap.put(key.charAt(0), block.defaultBlockState());
      }
    }

    JsonArray layersArray = GsonHelper.getAsJsonArray(root, "layers");
    List<Integer> listX = new ArrayList<>();
    List<Integer> listY = new ArrayList<>();
    List<Integer> listZ = new ArrayList<>();
    List<BlockState> listStates = new ArrayList<>();

    for (int y = 0; layersArray.size() > y; y++) {
      JsonArray rowsArray = GsonHelper.convertToJsonArray(layersArray.get(y), "layer");
      for (int z = 0; rowsArray.size() > z; z++) {
        String row = GsonHelper.convertToString(rowsArray.get(z), "row");
        for (int x = 0; row.length() > x; x++) {
          char symbol = row.charAt(x);
          if (symbol == ' ') continue;
          BlockState state = legendMap.get(symbol);
          if (state != null) {
            listX.add(x);
            listY.add(y);
            listZ.add(z);
            listStates.add(state);
          }
        }
      }
    }

    int count = listStates.size();
    int[] arrX = new int[count];
    int[] arrY = new int[count];
    int[] arrZ = new int[count];
    BlockState[] arrStates = new BlockState[count];

    for (int i = 0; count > i; i++) {
      arrX[i] = listX.get(i);
      arrY[i] = listY.get(i);
      arrZ[i] = listZ.get(i);
      arrStates[i] = listStates.get(i);
    }

    BLUEPRINTS.put(structureId, new MultiblockBlueprintData(structureId, arrX, arrY, arrZ, arrStates));

  }
}
