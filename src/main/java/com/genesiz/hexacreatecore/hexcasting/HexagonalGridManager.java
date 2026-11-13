package com.genesiz.hexacreatecore.hexcasting;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = "hexacreatecore", value = Dist.CLIENT)
public final class HexagonalGridManager {
  private static final List<ArcaneNode3D> ACTIVE_GRID_NODES = new ArrayList<>();
  private static final List<Integer> ACTIVE_DRAWING_PATH = new ArrayList<>();
  private static final List<List<Integer>> PERSISTENT_SPELL_LINES = new ArrayList<>();
  private static final List<String> STACK_DISPLAY_STRINGS = new ArrayList<>();

  private static Vec3 centerAnchorPoint = null;
  private static boolean isGridActive = false;
  private static String ravensmindString = null;
  private static int stackScrollIndex = 0;

  private static double camX = 0.0D;
  private static double camY = 0.0D;
  private static double camZ = 0.0D;
  private static double prevCamX = 0.0D;
  private static double prevCamY = 0.0D;
  private static double prevCamZ = 0.0D;

  private static float camYaw = 0.0f;
  private static float camPitch = 0.0f;
  private static float prevCamYaw = 0.0f;
  private static float prevCamPitch = 0.0f;

  private static final double FLY_SPEED_MOD = 0.14D;
  private static final double HEX_RADIUS = 2.0D;
  private static final double SELECTION_RADIUS_SQ = 0.09D; 

  private static int pointColorRaw = 0xFF21B7D1;
  private static int pointHoverColorRaw = 0xFFFFFF66;
  private static int lineColorRaw = 0xFFB233FF;
  private static int outlineColorRaw = 0xFF000000;
  private static float configLineSize = 0.0225f;
  private static float configOutlineSizeMultiplier = 2.0f;
  private static double configWobbleIntensity = 0.015D;

  private static int activeShellLayers = 1;
  private static long lastPenaltyTick = 0L;
  private static int penaltyZoneTicks = 0;

  public static Vec3 getFreeCamPos() { return new Vec3(camX, camY, camZ); }
  public static float getFreeCamYaw() { return camYaw; }
  public static float getFreeCamPitch() { return camPitch; }
  public static int getPointColorRaw() { return pointColorRaw; }

  public static int getShellIndex(int nodeId) {
    return (int) Math.floor((-1.0D + Math.sqrt(1.0D + (nodeId / 24.0D))) / 2.0D);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static void activateCastingGrid(Player player) {
    loadExternalGridConfig();

    ACTIVE_GRID_NODES.clear();
    ACTIVE_DRAWING_PATH.clear();
    PERSISTENT_SPELL_LINES.clear();
    STACK_DISPLAY_STRINGS.clear();

    centerAnchorPoint = player.position(); 
    ravensmindString = null;
    stackScrollIndex = 0;
    isGridActive = true;

    camX = player.getX(); camY = player.getEyeY(); camZ = player.getZ();
    prevCamX = camX; prevCamY = camY; prevCamZ = camZ;

    camYaw = player.getYRot(); camPitch = player.getXRot();
    prevCamYaw = camYaw; prevCamPitch = camPitch;

    double gridZoomAttributeValue = 1.0D;
    net.minecraft.resources.ResourceLocation attributeId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hexcasting", "grid_zoom");
    java.util.Optional optionalHolder = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(attributeId);

    if (optionalHolder != null && optionalHolder.isPresent()) {
      net.minecraft.core.Holder zoomAttributeHolder = (net.minecraft.core.Holder) optionalHolder.get();
      if (player.getAttributes().hasAttribute(zoomAttributeHolder)) {
        gridZoomAttributeValue = player.getAttributeValue(zoomAttributeHolder);
      }
    }

    int targetShellLayersCount = (int) Math.ceil(gridZoomAttributeValue);
    if (gridZoomAttributeValue > 1.0D && targetShellLayersCount == 1) targetShellLayersCount = 2;
    if (targetShellLayersCount < 1) targetShellLayersCount = 1;
    activeShellLayers = targetShellLayersCount;

    int nodeId = 0;
    double[] yOffsets = {-0.5D, 0.0D, 0.5D, 1.0D, 1.5D, 2.0D, 2.5D, 3.0D};

    for (int shellIndex = 0; targetShellLayersCount > shellIndex; shellIndex++) {
      double currentShellRadius = HEX_RADIUS * (shellIndex + 1);
      int pointsPerRing = 24 * (shellIndex + 1); 

      for (int rowIndex = 0; yOffsets.length > rowIndex; rowIndex++) {
        double nodeY = centerAnchorPoint.y + yOffsets[rowIndex] + 1.0D;
        double shiftParity = rowIndex % 2;
        double indexOffset = (shiftParity == 1) ? 0.5D : 0.0D;

        for (int i = 0; pointsPerRing > i; i++) {
          double currentStepIndex = i + indexOffset;
          double radiansAngle = (currentStepIndex * 2.0D * Math.PI) / pointsPerRing;

          Vec3 nodePosition = new Vec3(
            centerAnchorPoint.x + (currentShellRadius * Math.cos(radiansAngle)),
            nodeY,
            centerAnchorPoint.z + (currentShellRadius * Math.sin(radiansAngle))
          );

          int structuralColor = (shellIndex % 2 == 0) ? pointColorRaw : 0xFF9433FF;
          ACTIVE_GRID_NODES.add(new ArcaneNode3D(nodeId++, nodePosition, false, structuralColor));
        }
      }
    }
  }

  public static void deactivateGrid() {
    isGridActive = false;
    ACTIVE_GRID_NODES.clear(); ACTIVE_DRAWING_PATH.clear(); PERSISTENT_SPELL_LINES.clear(); STACK_DISPLAY_STRINGS.clear();
    centerAnchorPoint = null; ravensmindString = null; stackScrollIndex = 0;
  }

  public static void handleFreeCamMovement(net.minecraft.client.player.Input input) {
    prevCamX = camX; prevCamY = camY; prevCamZ = camZ;
    prevCamYaw = camYaw; prevCamPitch = camPitch;

    float yawRad = (float) Math.toRadians(camYaw);
    Vec3 forwardVec = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad)).normalize();
    Vec3 rightVec = new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad)).normalize();

    Vec3 moveVector = Vec3.ZERO;
    if (input.up) moveVector = moveVector.add(forwardVec);
    if (input.down) moveVector = moveVector.subtract(forwardVec);
    if (input.left) moveVector = moveVector.add(rightVec);
    if (input.right) moveVector = moveVector.subtract(rightVec);

    if (input.jumping) moveVector = moveVector.add(0.0D, 1.0D, 0.0D);
    if (input.shiftKeyDown) moveVector = moveVector.subtract(0.0D, 1.0D, 0.0D);

    if (moveVector.lengthSqr() > 0.001D) {
      moveVector = moveVector.normalize().scale(FLY_SPEED_MOD);
      camX += moveVector.x; camY += moveVector.y; camZ += moveVector.z;
    }

    if (centerAnchorPoint != null) {
      // Horizontal cylinder clamp: 3 blocks for 1 ring, 5 blocks for 2 rings, 7 blocks for 3 rings, etc.
      double maxHorizontalRadius = (HEX_RADIUS * activeShellLayers) + 1.0D;
      double dx = camX - centerAnchorPoint.x;
      double dz = camZ - centerAnchorPoint.z;
      double horizDist = Math.sqrt((dx * dx) + (dz * dz));

      if (horizDist > maxHorizontalRadius) {
        double scale = maxHorizontalRadius / horizDist;
        camX = centerAnchorPoint.x + (dx * scale);
        camZ = centerAnchorPoint.z + (dz * scale);
      }

      double minY = centerAnchorPoint.y + 0.5D;
      double maxY = centerAnchorPoint.y + 5.0D;
      camY = net.minecraft.util.Mth.clamp(camY, minY, maxY);
    }
  }


  @SubscribeEvent
  public static void onClientTick(ClientTickEvent.Pre event) {
    if (!isGridActive) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null || mc.level == null) return;

    if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
      mc.setScreen(null);
      return;
    }

    Vec3 eyePos = getFreeCamPos();
    float f = camPitch * ((float)Math.PI / 180F);
    float f1 = -camYaw * ((float)Math.PI / 180F);
    float f2 = net.minecraft.util.Mth.cos(f1); float f3 = net.minecraft.util.Mth.sin(f1);
    float f4 = net.minecraft.util.Mth.cos(f); float f5 = net.minecraft.util.Mth.sin(f);
    Vec3 lookVec = new Vec3((double)(f3 * f4), (double)(-f5), (double)(f2 * f4)).normalize();

    double cameraDistanceToCenter = 0.0D;
    if (centerAnchorPoint != null) {
      double deltaX = eyePos.x - centerAnchorPoint.x;
      double deltaZ = eyePos.z - centerAnchorPoint.z;
      cameraDistanceToCenter = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }
    int currentActiveZoneShell = (int) (cameraDistanceToCenter / HEX_RADIUS);

    double outerRingRadius = HEX_RADIUS * activeShellLayers;
    if (cameraDistanceToCenter > outerRingRadius) {
      penaltyZoneTicks++;
      long gameTime = mc.level.getGameTime();
      if (gameTime - lastPenaltyTick >= 20L) {
        lastPenaltyTick = gameTime;
        String actionPayload = (penaltyZoneTicks >= 200)
          ? "hexacreatecore:overreach_penalty;wither"
          : "hexacreatecore:overreach_penalty;normal";

        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
          new com.genesiz.hexacreatecore.threedified.network.DialogueActionPayload(mc.player.getId(), actionPayload, false)
        );
      }
    } else {
      penaltyZoneTicks = 0;
    }


    int highlightedNodeId = -1;
    double closestDistSq = Double.MAX_VALUE;

    for (int i = 0; ACTIVE_GRID_NODES.size() > i; i++) {
      ArcaneNode3D node = ACTIVE_GRID_NODES.get(i);
      int nodeShellIndex = getShellIndex(node.id());
      if (nodeShellIndex != currentActiveZoneShell) continue;

      Vec3 targetVec = node.worldPos().subtract(eyePos);
      double projection = targetVec.dot(lookVec);
      if (0.0D > projection) continue; 

      Vec3 closestPointOnGazeLine = eyePos.add(lookVec.scale(projection));
      double distSq = node.worldPos().distanceToSqr(closestPointOnGazeLine);

      if (SELECTION_RADIUS_SQ > distSq && distSq < closestDistSq) {
        closestDistSq = distSq; highlightedNodeId = i;
      }
    }

    boolean isRightClickHeld = mc.options.keyUse.isDown();

    for (int i = 0; ACTIVE_GRID_NODES.size() > i; i++) {
      ArcaneNode3D node = ACTIVE_GRID_NODES.get(i);
      boolean isCurrentlyHovered = (i == highlightedNodeId);

      int nodeShellIndex = getShellIndex(i);
      int baseShellColor = (nodeShellIndex % 2 == 0) ? pointColorRaw : 0xFF9433FF;
      int targetColor = isCurrentlyHovered ? pointHoverColorRaw : 
    (nodeShellIndex == currentActiveZoneShell ? baseShellColor : (baseShellColor & 0x44FFFFFF));

      if (isCurrentlyHovered && isRightClickHeld) {
        int pathSize = ACTIVE_DRAWING_PATH.size();
        if (pathSize >= 2 && ACTIVE_DRAWING_PATH.get(pathSize - 2) == i) {
          ACTIVE_DRAWING_PATH.remove(pathSize - 1);
          mc.level.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(), 
            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.45f, 0.65f);
        } else if (ACTIVE_DRAWING_PATH.isEmpty() || ACTIVE_DRAWING_PATH.get(pathSize - 1) != i) {
          ACTIVE_DRAWING_PATH.add(i);
          try {
            net.minecraft.resources.ResourceLocation soundId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hexcasting", "casting.pattern.add_segment");
            net.minecraft.sounds.SoundEvent authenticSound = mc.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.SOUND_EVENT).get(soundId);
            float risingPitchMultiplier = 0.8f + Math.min(0.6f, (float) ACTIVE_DRAWING_PATH.size() * 0.05f);
            if (authenticSound != null) {
              mc.level.playSound(mc.player, node.worldPos().x, node.worldPos().y, node.worldPos().z, authenticSound, net.minecraft.sounds.SoundSource.PLAYERS, 0.75f, risingPitchMultiplier);
            }
          } catch (Exception ignored) {}
        }
      }
      ACTIVE_GRID_NODES.set(i, new ArcaneNode3D(node.id(), node.worldPos(), false, targetColor));
    }

    if (!isRightClickHeld && !ACTIVE_DRAWING_PATH.isEmpty()) {
      if (ACTIVE_DRAWING_PATH.size() >= 2) {
        ArrayList<Integer> pathSnapshot = new ArrayList<>(ACTIVE_DRAWING_PATH);
        PERSISTENT_SPELL_LINES.add(pathSnapshot);
        compileAndCast3DPattern(mc.player, pathSnapshot);
      }
      ACTIVE_DRAWING_PATH.clear();
    }
  }

  public static void updateLiveCastingStack(List<String> incomingStack, String incomingRavensmind) {
    STACK_DISPLAY_STRINGS.clear();
    if (incomingStack != null) {
      STACK_DISPLAY_STRINGS.addAll(incomingStack);
    }
    ravensmindString = (incomingRavensmind == null || incomingRavensmind.isEmpty()) ? null : incomingRavensmind;
  }

  public static void compileAndCast3DPattern(Player player, List<Integer> explicitPath) {
    if (explicitPath == null || explicitPath.size() < 2) return;
    java.util.List<at.petrak.hexcasting.api.casting.math.HexDir> directions = new java.util.ArrayList<>();

    for (int i = 0; explicitPath.size() - 1 > i; i++) {
      int startId = explicitPath.get(i); 
      int endId = explicitPath.get(i + 1);

      int startShell = getShellIndex(startId);
      int endShell = getShellIndex(endId);

      int startWidth = 24 * (startShell + 1);
      int endWidth = 24 * (endShell + 1);

      int baseStartId = startId - (96 * startShell * (startShell + 1));
      int baseEndId = endId - (96 * endShell * (endShell + 1));

      int startCol = baseStartId % startWidth; 
      int startRow = baseStartId / startWidth;
      int endCol = baseEndId % endWidth; 
      int endRow = baseEndId / endWidth;

      int maxPerimeter = Math.max(startWidth, endWidth);
      if (startWidth != endWidth) {
        startCol = (int) (startCol * ((double) maxPerimeter / startWidth));
        endCol = (int) (endCol * ((double) maxPerimeter / endWidth));
      }

      int dCol = endCol - startCol; 
      int dRow = endRow - startRow;

      int halfWidth = maxPerimeter / 2;
      if (dCol < -halfWidth) dCol += maxPerimeter;
      if (dCol > halfWidth) dCol -= maxPerimeter;

      at.petrak.hexcasting.api.casting.math.HexDir stepDir = null;
      if (dRow == 0) {
        stepDir = (dCol > 0) ? at.petrak.hexcasting.api.casting.math.HexDir.EAST : at.petrak.hexcasting.api.casting.math.HexDir.WEST;
      } else if (dRow > 0) {
        if (startRow % 2 == 1) {
          stepDir = (dCol > 0) ? at.petrak.hexcasting.api.casting.math.HexDir.NORTH_EAST : at.petrak.hexcasting.api.casting.math.HexDir.NORTH_WEST;
        } else {
          stepDir = (dCol >= 0) ? at.petrak.hexcasting.api.casting.math.HexDir.NORTH_EAST : at.petrak.hexcasting.api.casting.math.HexDir.NORTH_WEST;
        }
      } else {
        if (startRow % 2 == 1) {
          stepDir = (dCol <= 0) ? at.petrak.hexcasting.api.casting.math.HexDir.SOUTH_WEST : at.petrak.hexcasting.api.casting.math.HexDir.SOUTH_EAST;
        } else {
          stepDir = (dCol < 0) ? at.petrak.hexcasting.api.casting.math.HexDir.SOUTH_WEST : at.petrak.hexcasting.api.casting.math.HexDir.SOUTH_EAST;
        }
      }
      if (stepDir != null) directions.add(stepDir);
    }

    if (directions.isEmpty()) return;
    at.petrak.hexcasting.api.casting.math.HexDir compass = directions.get(0);
    StringBuilder signatureBuilder = new StringBuilder();

    for (int i = 1; directions.size() > i; i++) {
      at.petrak.hexcasting.api.casting.math.HexDir nextDir = directions.get(i);
      int turnOrdinal = (nextDir.ordinal() - compass.ordinal() + 6) % 6;
      switch (turnOrdinal) {
        case 0 -> signatureBuilder.append("w"); case 1 -> signatureBuilder.append("e"); case 2 -> signatureBuilder.append("d"); 
        case 3 -> signatureBuilder.append("s"); case 4 -> signatureBuilder.append("a"); case 5 -> signatureBuilder.append("q"); 
      }
      compass = nextDir;
    }

    if (signatureBuilder.length() == 0) signatureBuilder.append("w");
    String signature = signatureBuilder.toString();
    at.petrak.hexcasting.api.casting.math.HexDir startDir = directions.get(0);

    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§d[Arcane Grid] Signature: " + signature + " (" + startDir.name() + ")"), true);

    String unifiedPayloadString = "hexacreatecore:cast_3d_spell;" + startDir.ordinal() + ";" + signature;
    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.genesiz.hexacreatecore.threedified.network.DialogueActionPayload(player.getId(), unifiedPayloadString, false));
  }

  public static void loadExternalGridConfig() {
    java.io.File configFile = new java.io.File(
      net.minecraft.client.Minecraft.getInstance().gameDirectory, 
      "config/hexacreatecore/grid_config.json"
    );

    if (!configFile.exists()) {
      try {
        if (configFile.getParentFile() != null) {
          configFile.getParentFile().mkdirs();
        }
        String defaultJson = """
          {
            "point_color": "0xFF21B7D1",
            "point_hover_color_hex": "0xFFFFFF66",
            "line_color": "0xFFB233FF",
            "outline_color": "0xFF000000",
            "line_size": 0.0225,
            "outline_size": 2.0,
            "wobble_intensity": 0.015
          }
          """;
        java.nio.file.Files.writeString(configFile.toPath(), defaultJson, java.nio.charset.StandardCharsets.UTF_8);
      } catch (Exception writeException) {
        writeException.printStackTrace();
      }
      return;
    }

    try {
      String jsonContent = new String(java.nio.file.Files.readAllBytes(configFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);

      pointColorRaw = parseHexOrDefault(extractJsonKeyValue(jsonContent, "point_color"), 0xFF21B7D1);
      pointHoverColorRaw = parseHexOrDefault(extractJsonKeyValue(jsonContent, "point_hover_color_hex"), 0xFFFFFF66);
      lineColorRaw = parseHexOrDefault(extractJsonKeyValue(jsonContent, "line_color"), 0xFFB233FF);
      outlineColorRaw = parseHexOrDefault(extractJsonKeyValue(jsonContent, "outline_color"), 0xFF000000);

      configLineSize = (float) parseDoubleOrDefault(extractJsonKeyValue(jsonContent, "line_size"), 0.0225D);
      configOutlineSizeMultiplier = (float) parseDoubleOrDefault(extractJsonKeyValue(jsonContent, "outline_size"), 2.0D);
      configWobbleIntensity = parseDoubleOrDefault(extractJsonKeyValue(jsonContent, "wobble_intensity"), 0.015D);
    } catch (Exception loaderCleanException) {
      loaderCleanException.printStackTrace();
    }
  }


  private static String extractJsonKeyValue(String json, String key) {
    String patternTarget = "\"" + key + "\""; if (!json.contains(patternTarget)) return "";
    int offset = json.indexOf(patternTarget) + patternTarget.length();
    int end = json.indexOf("\n", offset); if (end == -1) end = json.indexOf("}", offset);
    return json.substring(offset, end).replace(":", "").trim();
  }

  private static int parseHexOrDefault(String v, int f) {
    try { String c = v.replaceAll("[\"\\s]", ""); if (c.startsWith("0x") || c.startsWith("0X")) c = c.substring(2); return (int) Long.parseLong(c, 16); } catch (Exception e) { return f; }
  }
  private static double parseDoubleOrDefault(String v, double f) { try { return Double.parseDouble(v.replaceAll("[\"\\s]", "")); } catch (Exception e) { return f; } }

  @SubscribeEvent
  public static void onMouseScroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
    if (!isGridActive) return;
    handleStackScroll((float) event.getScrollDeltaY());
    event.setCanceled(true);
  }

  @SubscribeEvent
  public static void onScreenOpening(net.neoforged.neoforge.client.event.ScreenEvent.Opening event) {
    if (!isGridActive) return;

    net.minecraft.client.gui.screens.Screen screen = event.getScreen();
    if (screen instanceof net.minecraft.client.gui.screens.PauseScreen
    || screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
      event.setCanceled(true);
      deactivateGrid();
    }
  }

  @SubscribeEvent
  public static void onRightClickStaff(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
    // Block normal staff right-click and hand-swing animation while in 3D grid mode
    if (isGridActive) {
      event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
      event.setCanceled(true);
      return;
    }

    if (!event.getLevel().isClientSide()) return;

    Player player = event.getEntity();
    if (!player.isShiftKeyDown()) return;

    net.minecraft.resources.ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
    if (itemId != null && "hexcasting".equals(itemId.getNamespace()) && itemId.getPath().contains("staff")) {
      STACK_DISPLAY_STRINGS.clear();
      ravensmindString = null;
      stackScrollIndex = 0;

      // Clear HexCasting's client-side orbiting spiral runes
      try {
        Class<?> stackClass = Class.forName("at.petrak.hexcasting.client.ClientCastingStack");
        for (java.lang.reflect.Method m : stackClass.getDeclaredMethods()) {
          if (m.getName().contains("clear")) {
            m.setAccessible(true);
            if (m.getParameterCount() == 0) {
              m.invoke(null);
            } else if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == java.util.UUID.class) {
              m.invoke(null, player.getUUID());
            }
          }
        }
      } catch (Throwable ignored) {}

      net.neoforged.neoforge.network.PacketDistributor.sendToServer(
        new com.genesiz.hexacreatecore.threedified.network.DialogueActionPayload(player.getId(), "hexacreatecore:clear_3d_stack", false)
      );

      activateCastingGrid(player);
      event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
      event.setCanceled(true);
    }
  }


  public static void handleStackScroll(float delta) { if (delta > 0.0f) { if (stackScrollIndex > 0) stackScrollIndex--; } else if (delta < 0.0f) { int maxScroll = Math.max(0, STACK_DISPLAY_STRINGS.size() - 10); if (maxScroll > stackScrollIndex) stackScrollIndex++; } }
  public static List<ArcaneNode3D> getActiveNodes() { return ACTIVE_GRID_NODES; }
  public static List<Integer> getActiveDrawingPath() { return ACTIVE_DRAWING_PATH; }
  public static List<List<Integer>> getPersistentLines() { return PERSISTENT_SPELL_LINES; }
  public static List<String> getStackStrings() { return STACK_DISPLAY_STRINGS; }
  public static String getRavensmindString() { return ravensmindString; }
  public static int getScrollIndex() { return stackScrollIndex; }
  public static boolean isActive() { return isGridActive; }
  public static void handleMouseTurn(double mx, double my) { prevCamYaw = camYaw; prevCamPitch = camPitch; camYaw += (float) (mx * 0.15F); camPitch = net.minecraft.util.Mth.clamp(camPitch + (float) (my * 0.15F), -90.0F, 90.0F); }
  public static void setFreeCamRotation(float yaw, float pitch) { prevCamYaw = camYaw; prevCamPitch = camPitch; camYaw = yaw; camPitch = pitch; }
  public static Vec3 getLerpedCameraPos(float pt) { return new Vec3(net.minecraft.util.Mth.lerp(pt, prevCamX, camX), net.minecraft.util.Mth.lerp(pt, prevCamY, camY), net.minecraft.util.Mth.lerp(pt, prevCamZ, camZ)); }
  public static float getLerpedCameraYaw(float pt) { return net.minecraft.util.Mth.rotLerp(pt, prevCamYaw, camYaw); }
  public static float getLerpedCameraPitch(float pt) { return net.minecraft.util.Mth.lerp(pt, prevCamPitch, camPitch); }
  public static int getLineColorRaw() { return lineColorRaw; }
  public static int getOutlineColorRaw() { return outlineColorRaw; }
  public static float getConfigLineSize() { return configLineSize; }
  public static float getConfigOutlineSizeMultiplier() { return configOutlineSizeMultiplier; }
  public static Vec3 getCenterAnchorPoint() { return centerAnchorPoint; }
}
