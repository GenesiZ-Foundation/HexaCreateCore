package com.genesiz.hexacreatecore.threedified.data;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.util.*;

public final class DialogueReloadListener extends SimpleJsonResourceReloadListener {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final Map<ResourceLocation, DialogueTreeData> LOADED_TREES = new HashMap<>();
  private static boolean hasLoadedOnce = false;

  public DialogueReloadListener() {
    super(GSON, "dialogues");
  }

  public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
    event.registerReloadListener(new DialogueReloadListener());
  }

  public static DialogueTreeData getDialogueForEntity(ResourceLocation entityTypeId) {
    if (!hasLoadedOnce) {
      forceLoadFromClientResources();
    }
    return LOADED_TREES.get(entityTypeId);
  }

  private static void forceLoadFromClientResources() {
    hasLoadedOnce = true;
    var rm = Minecraft.getInstance().getResourceManager();
    var resources = rm.listResources("dialogues", loc -> loc.getPath().endsWith(".json"));
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
    LOADED_TREES.clear();
    hasLoadedOnce = true;
    for (JsonElement element : objects.values()) {
      parseAndStore(element);
    }
  }

  private static void parseAndStore(JsonElement element) {
    JsonObject root = GsonHelper.convertToJsonObject(element, "dialogue");

    ResourceLocation npcId = ResourceLocation.parse(GsonHelper.getAsString(root, "npc_id"));
    ResourceLocation soundId = ResourceLocation.parse(GsonHelper.getAsString(root, "blip_sound", "minecraft:ui.button.click"));

    float cps = GsonHelper.getAsFloat(root, "chars_per_second", 25.0f);
    int maxWidth = GsonHelper.getAsInt(root, "max_width", 120);
    String entryNode = GsonHelper.getAsString(root, "entry_node", "start");

    int treeBg = parseArgb(root, "bg_color", 0xFF000000);
    int treeBorder = parseArgb(root, "border_color", 0xFFFFFFFF);
    int btnBg = parseArgb(root, "button_bg_color", 0xFF111111);
    int btnBorder = parseArgb(root, "button_border_color", 0xFFFFAA00);
    int btnHover = parseArgb(root, "button_hover_color", 0xFF00FFAA);

    JsonObject nodesObj = GsonHelper.getAsJsonObject(root, "nodes");
    Map<String, DialogueTreeData.DialogueStepData> parsedNodes = new HashMap<>();

    for (Map.Entry<String, JsonElement> nodeEntry : nodesObj.entrySet()) {
      JsonObject stepObj = GsonHelper.convertToJsonObject(nodeEntry.getValue(), "node");
      String text = GsonHelper.getAsString(stepObj, "text");
      int stepBg = parseArgb(stepObj, "bg_color", treeBg);
      int stepBorder = parseArgb(stepObj, "border_color", treeBorder);

      List<DialogueTreeData.DialogueOptionData> options = new ArrayList<>();
      if (stepObj.has("options")) {
        JsonArray optArray = GsonHelper.getAsJsonArray(stepObj, "options");
        for (JsonElement optElem : optArray) {
          JsonObject optObj = GsonHelper.convertToJsonObject(optElem, "option");
          int optBg = parseArgb(optObj, "bg_color", btnBg);
          int optBorder = parseArgb(optObj, "border_color", btnBorder);
          int optHover = parseArgb(optObj, "hover_color", btnHover);

          options.add(new DialogueTreeData.DialogueOptionData(
            GsonHelper.getAsString(optObj, "label"),
            GsonHelper.getAsString(optObj, "next_node", "close"),
            GsonHelper.getAsString(optObj, "action_id", ""),
            GsonHelper.getAsString(optObj, "right_click_label", ""),
            optBg,
            optBorder,
            optHover
          ));
        }
      }
      parsedNodes.put(nodeEntry.getKey(), new DialogueTreeData.DialogueStepData(text, stepBg, stepBorder, List.copyOf(options)));
    }

    LOADED_TREES.put(npcId, new DialogueTreeData(
      npcId, soundId, cps, maxWidth,
      treeBg, treeBorder, btnBg, btnBorder, btnHover,
      entryNode, Map.copyOf(parsedNodes)
    ));
  }

  private static int parseArgb(JsonObject obj, String key, int fallback) {
    if (!obj.has(key)) return fallback;
    String raw = GsonHelper.getAsString(obj, key).trim();
    if (raw.startsWith("#")) raw = raw.substring(1);
    else if (raw.startsWith("0x") || raw.startsWith("0X")) raw = raw.substring(2);

    try {
      long parsed = Long.parseLong(raw, 16);
      if (raw.length() == 6) {
        parsed |= 0xFF000000L;
      }
      return (int) parsed;
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
