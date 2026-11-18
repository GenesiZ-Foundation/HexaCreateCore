package com.genesiz.hexacreatecore.create.wonderum.blueprint;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud;
import com.genesiz.hexacreatecore.threedified.hologram.BlueprintReloadListener;
import com.genesiz.hexacreatecore.threedified.hologram.MultiblockBlueprintData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WonderumDraftingSession {

  public enum ColorTarget {
    BG("Background Color", "bg_color"),
    BORDER("Border Color", "border_color"),
    PROGRESS("Progress Color", "progress_color");

    public final String buttonLabel;
    public final String jsonKey;

    ColorTarget(String buttonLabel, String jsonKey) {
      this.buttonLabel = buttonLabel;
      this.jsonKey = jsonKey;
    }
  }

  private static final Map<BlockPos, BlockState> DRAFT_BLOCKS = new LinkedHashMap<>();
  private static Block selectedBrushBlock = Blocks.COBBLESTONE;
  private static boolean draftingModeActive = false;

  private static String structureName = "";
  private static boolean awaitingChatName = false;

  // Default colors matching your JSON schema
  private static int bgColor = 0xDD0D0814;
  private static int borderColor = 0xFFFF6600;
  private static int progressColor = 0xBB21B7D1;

  private static boolean colorPickerOpen = false;
  private static ColorTarget activeColorTarget = ColorTarget.BG;

  // HSVA state synced to the active ColorTarget
  private static float hue = 0.75f;
  private static float saturation = 0.60f;
  private static float value = 0.08f;
  private static float alpha = 0.87f;

  private WonderumDraftingSession() {}

  public static Map<BlockPos, BlockState> getBlocks() { return DRAFT_BLOCKS; }
  public static boolean isDraftingActive() { return draftingModeActive || !DRAFT_BLOCKS.isEmpty(); }
  public static void setDraftingModeActive(boolean active) { draftingModeActive = active; }

  public static Block getSelectedBrushBlock() { return selectedBrushBlock; }
  public static void setSelectedBrushBlock(Block block) {
    if (block != null && block != Blocks.AIR) {
      selectedBrushBlock = block;
      draftingModeActive = true;
    }
  }

  public static String getStructureName() { return structureName; }
  public static void setStructureName(String name) { structureName = (name != null) ? name.trim() : ""; }

  public static boolean isAwaitingChatName() { return awaitingChatName; }
  public static void setAwaitingChatName(boolean awaiting) { awaitingChatName = awaiting; }

  public static int getBgColor() { return bgColor; }
  public static int getBorderColor() { return borderColor; }
  public static int getProgressColor() { return progressColor; }

  public static boolean isColorPickerOpen() { return colorPickerOpen; }
  public static void setColorPickerOpen(boolean open) {
    colorPickerOpen = open;
    if (open) loadHsvaFromTarget();
  }

  public static ColorTarget getActiveColorTarget() { return activeColorTarget; }
  public static void setActiveColorTarget(ColorTarget target) {
    activeColorTarget = target;
    loadHsvaFromTarget();
  }

  public static float getHue() { return hue; }
  public static float getSaturation() { return saturation; }
  public static float getValue() { return value; }
  public static float getAlpha() { return alpha; }

  public static void updateHsva(float h, float s, float v, float a) {
    hue = Math.max(0.0f, Math.min(1.0f, h));
    saturation = Math.max(0.0f, Math.min(1.0f, s));
    value = Math.max(0.0f, Math.min(1.0f, v));
    alpha = Math.max(0.0f, Math.min(1.0f, a));

    int rgb = java.awt.Color.HSBtoRGB(hue, saturation, value) & 0x00FFFFFF;
    int ai = Math.round(alpha * 255.0f) & 0xFF;
    int argb = (ai << 24) | rgb;

    switch (activeColorTarget) {
      case BG -> bgColor = argb;
      case BORDER -> borderColor = argb;
      case PROGRESS -> progressColor = argb;
    }
  }

  private static void loadHsvaFromTarget() {
    int argb = switch (activeColorTarget) {
      case BG -> bgColor;
      case BORDER -> borderColor;
      case PROGRESS -> progressColor;
    };
    alpha = ((argb >>> 24) & 0xFF) / 255.0f;
    int r = (argb >>> 16) & 0xFF;
    int g = (argb >>> 8) & 0xFF;
    int b = argb & 0xFF;
    float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
    hue = hsb[0];
    saturation = hsb[1];
    value = hsb[2];
  }

  public static void dismantle() {
    DRAFT_BLOCKS.clear();
    draftingModeActive = false;
    colorPickerOpen = false;
    awaitingChatName = false;
    ThreeDifiedNotificationHud.show("§b[Wonderum] §eDraft hologram dismantled.");
  }

  public static String toSmallSnakeCase(String raw) {
    if (raw == null || raw.isBlank()) return "unnamed_blueprint";
    String cleaned = raw.trim()
      .toLowerCase(Locale.ROOT)
      .replaceAll("[^a-z0-9]+", "_")
      .replaceAll("^_+|_+$", "");
    return cleaned.isEmpty() ? "unnamed_blueprint" : cleaned;
  }

  public static String formatArgbHex(int argb) {
    return String.format("#%08X", argb);
  }

  public static void saveBlueprintJson() {
    if (DRAFT_BLOCKS.isEmpty()) {
      ThreeDifiedNotificationHud.show("§c[Wonderum] §fCannot save an empty hologram!");
      return;
    }

    String snakeId = toSmallSnakeCase(structureName);

    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
    for (BlockPos pos : DRAFT_BLOCKS.keySet()) {
      if (minX > pos.getX()) minX = pos.getX();
      if (minY > pos.getY()) minY = pos.getY();
      if (minZ > pos.getZ()) minZ = pos.getZ();
      if (pos.getX() > maxX) maxX = pos.getX();
      if (pos.getY() > maxY) maxY = pos.getY();
      if (pos.getZ() > maxZ) maxZ = pos.getZ();
    }

    int sizeX = (maxX - minX) + 1;
    int sizeY = (maxY - minY) + 1;
    int sizeZ = (maxZ - minZ) + 1;

    // Assign single-character legend symbols (A-Z, a-z, 0-9)
    String symbolPool = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    Map<ResourceLocation, Character> blockToSymbol = new LinkedHashMap<>();
    JsonObject legendJson = new JsonObject();
    int symbolCursor = 0;

    for (BlockState state : DRAFT_BLOCKS.values()) {
      ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
      if (!blockToSymbol.containsKey(blockId)) {
        char sym = (symbolPool.length() > symbolCursor) ? symbolPool.charAt(symbolCursor++) : '#';
        blockToSymbol.put(blockId, sym);
        legendJson.addProperty(String.valueOf(sym), blockId.toString());
      }
    }

    // Build 3D ASCII layers array [y][z][x]
    JsonArray layersArray = new JsonArray();
    List<Integer> arrX = new ArrayList<>();
    List<Integer> arrY = new ArrayList<>();
    List<Integer> arrZ = new ArrayList<>();
    List<BlockState> arrStates = new ArrayList<>();

    for (int y = 0; sizeY > y; y++) {
      JsonArray rowsArray = new JsonArray();
      for (int z = 0; sizeZ > z; z++) {
        StringBuilder rowStr = new StringBuilder(sizeX);
        for (int x = 0; sizeX > x; x++) {
          BlockPos wp = new BlockPos(minX + x, minY + y, minZ + z);
          BlockState st = DRAFT_BLOCKS.get(wp);
          if (st == null || st.isAir()) {
            rowStr.append(' ');
          } else {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
            rowStr.append(blockToSymbol.getOrDefault(id, 'C'));
            arrX.add(x);
            arrY.add(y);
            arrZ.add(z);
            arrStates.add(st);
          }
        }
        rowsArray.add(rowStr.toString());
      }
      layersArray.add(rowsArray);
    }

    JsonObject root = new JsonObject();
    root.addProperty("structure_id", snakeId);
    root.addProperty("bg_color", formatArgbHex(bgColor));
    root.addProperty("border_color", formatArgbHex(borderColor));
    root.addProperty("progress_color", String.format("%08X", progressColor));
    root.add("legend", legendJson);
    root.add("layers", layersArray);

    try {
      Path exportDir = FMLPaths.CONFIGDIR.get().resolve("hexacreatecore").resolve("exported_blueprints");
      Files.createDirectories(exportDir);
      Path outFile = exportDir.resolve(snakeId + ".json");
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      Files.writeString(outFile, gson.toJson(root), StandardCharsets.UTF_8);

      // Hot-register directly into BlueprintReloadListener so it is immediately usable in Wonderum!
      int count = arrStates.size();
      int[] ox = new int[count], oy = new int[count], oz = new int[count];
      BlockState[] stArr = new BlockState[count];
      for (int i = 0; count > i; i++) {
        ox[i] = arrX.get(i);
        oy[i] = arrY.get(i);
        oz[i] = arrZ.get(i);
        stArr[i] = arrStates.get(i);
      }
      BlueprintReloadListener.registerRuntimeBlueprint(
        new MultiblockBlueprintData(snakeId, bgColor, borderColor, progressColor, ox, oy, oz, stArr),
        true
      );

      ThreeDifiedNotificationHud.show("§b[Wonderum] §aSaved §e" + snakeId + ".json §7(" + count + " blocks)");
    } catch (Exception e) {
      HexaCreateCore.LOGGER.error("Failed exporting Wonderum blueprint JSON", e);
      ThreeDifiedNotificationHud.show("§c[Wonderum] §fFailed to save blueprint JSON!");
    }
  }
}
