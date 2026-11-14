package com.genesiz.hexacreatecore.hexcasting.everbook;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.hexcasting.HexagonalGridManager;
import com.genesiz.hexacreatecore.item.custom.EverbookItem;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class EverbookSessionState {

  private static boolean active = false;
  private static boolean riftLocked = false; // false = Soul/Casting mode (follows player), true = Static Rift Edit Mode
  private static boolean usingOffhand = false;

  private static EverbookData.BookState bookState = new EverbookData.BookState(0, new ArrayList<>(List.of(new EverbookData.BookPage("Chapter I", new ArrayList<>()))));
  private static final Set<String> SELECTED_IDS = new LinkedHashSet<>();
  private static final List<EverbookData.PatternEntry> CLIPBOARD = new ArrayList<>();

  // Rift world-space anchor
  private static Vec3 riftAnchorPos = Vec3.ZERO;
  private static float riftYaw = 0.0f;
  private static float riftPitch = 0.0f;

  // Scroll & interactive hit-testing state
  private static float scrollRowOffset = 0.0f;
  private static int totalWrappedRows = 1;
  private static int hoveredPatternIdx = -1;
  private static boolean draggingSelected = false;
  private static boolean sweepSelecting = false;
  private static boolean evalHovered = false;

  // Half-size flat mini-grid (right side of Rift in Rift Mode)
  private static final List<Integer> MINI_GRID_PATH = new ArrayList<>();
  private static int hoveredMiniNode = -1;

  // Left-side Dry-Run Evaluation Result
  private static boolean hasEvalResult = false;
  private static boolean evalSuccess = false;
  private static boolean evalCollapsed = false;
  private static int evalRecursions = 0;
  private static String evalSummary = "Ready";
  private static List<String> evalStackLines = List.of();

  private static Vec3 cageCenterPos = Vec3.ZERO;
  private static int riftOverreachTicks = 0;
  private static long lastRiftPenaltyTick = 0L;
  private static boolean sweepDeselecting = false;

  public static Vec3 getCageCenterPos() { return cageCenterPos; }

  private EverbookSessionState() {}

  public static boolean isActive() { return active; }
  public static boolean isRiftMode() { return active && riftLocked; }
  public static Vec3 getRiftAnchorPos() { return riftAnchorPos; }
  public static float getRiftYaw() { return riftYaw; }
  public static float getRiftPitch() { return riftPitch; }
  public static float getScrollRowOffset() { return scrollRowOffset; }
  public static int getHoveredPatternIdx() { return hoveredPatternIdx; }
  public static Set<String> getSelectedIds() { return SELECTED_IDS; }
  public static boolean isEvalHovered() { return evalHovered; }
  public static List<Integer> getMiniGridPath() { return MINI_GRID_PATH; }
  public static int getHoveredMiniNode() { return hoveredMiniNode; }
  public static boolean hasEvalResult() { return hasEvalResult; }
  public static boolean isEvalSuccess() { return evalSuccess; }
  public static boolean isEvalCollapsed() { return evalCollapsed; }
  public static int getEvalRecursions() { return evalRecursions; }
  public static String getEvalSummary() { return evalSummary; }
  public static List<String> getEvalStackLines() { return evalStackLines; }
  private static String draggedAnchorId = null;

  public static List<EverbookData.PatternEntry> getCurrentEntries() {
    return bookState.currentPage().entries();
  }

  public static void activateForPlayer(Player player, ItemStack stack, InteractionHand hand) {
    active = true;
    riftLocked = false;
    usingOffhand = (hand == InteractionHand.OFF_HAND);
    bookState = EverbookData.read(stack);
    SELECTED_IDS.clear();
    MINI_GRID_PATH.clear();
    scrollRowOffset = 0.0f;
    hasEvalResult = false;
    updateRiftFollowCamera();
  }

  public static void deactivate() {
    if (active) {
      syncToServer("SAVE");
    }
    active = false;
    riftLocked = false;
    SELECTED_IDS.clear();
    MINI_GRID_PATH.clear();
    draggingSelected = false;
    sweepSelecting = false;
  }

  public static void appendDrawnPattern(String angles, String startDir, String label) {
    if (!active) return;
    List<EverbookData.PatternEntry> entries = getCurrentEntries();
    entries.add(new EverbookData.PatternEntry(
      UUID.randomUUID().toString(), angles, startDir, label, 0.0f, 0.0f, entries.size()
    ));
    // Auto-scroll to bottom when adding new patterns
    scrollRowOffset = Math.max(0.0f, totalWrappedRows - 4.0f);
    syncToServer("SAVE");
  }

  public static void triggerEvaluate() {
    List<EverbookData.PatternEntry> all = getCurrentEntries();
    if (all.isEmpty()) return;

    List<EverbookData.PatternEntry> toEval = new ArrayList<>();
    if (!SELECTED_IDS.isEmpty()) {
      for (EverbookData.PatternEntry e : all) {
        if (SELECTED_IDS.contains(e.id())) toEval.add(e);
      }
    } else {
      toEval.addAll(all);
    }
    PacketDistributor.sendToServer(EverbookEvalPayload.request(toEval));
  }

  public static void onEvaluationResult(boolean success, boolean collapsed, int recursions, String summary, List<String> stack) {
    hasEvalResult = true;
    evalSuccess = success;
    evalCollapsed = collapsed;
    evalRecursions = recursions;
    evalSummary = summary;
    evalStackLines = stack;
  }

  private static void syncToServer(String action) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player != null) {
      ItemStack held = mc.player.getItemInHand(usingOffhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
      if (held.getItem() instanceof EverbookItem) {
        EverbookData.write(held, bookState);
      }
      PacketDistributor.sendToServer(new EverbookSyncPayload(usingOffhand, action, bookState.toNbt()));
    }
  }

  private static void updateRiftFollowCamera() {
    Vec3 camPos = HexagonalGridManager.getFreeCamPos();
    float yaw = HexagonalGridManager.getFreeCamYaw();
    float pitch = HexagonalGridManager.getFreeCamPitch();
    Vec3 look = Vec3.directionFromRotation(pitch, yaw);
    Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
    // Follows player smoothly on the left-forward side like the 3D stack
    riftAnchorPos = camPos.add(look.scale(2.15D)).add(right.scale(-0.85D));
    riftYaw = yaw + 18.0f;
    riftPitch = pitch * 0.45f;
  }

  @SubscribeEvent
  public static void onClientTick(ClientTickEvent.Pre event) {
    if (!HexagonalGridManager.isActive()) {
      if (active) deactivate();
      return;
    }
    if (!active) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null || mc.level == null) return;
    if (!riftLocked) return;

    Vec3 eye = HexagonalGridManager.getFreeCamPos();

    // 3D Truncated Icosahedron Containment Penalty (restricts X, Y, and Z movement)
    if (TruncatedIcosahedronCage.isOutsideCage(eye, riftAnchorPos, TruncatedIcosahedronCage.CAGE_RADIUS)) {
      riftOverreachTicks++;
      long gameTime = mc.level.getGameTime();
      if (gameTime - lastRiftPenaltyTick >= 20L) {
        lastRiftPenaltyTick = gameTime;
        String penaltyCmd = (riftOverreachTicks >= 200)
        ? "hexacreatecore:overreach_penalty;wither"
        : "hexacreatecore:overreach_penalty;normal";
        PacketDistributor.sendToServer(
          new com.genesiz.hexacreatecore.threedified.network.DialogueActionPayload(mc.player.getId(), penaltyCmd, false)
        );
      }
    } else {
      riftOverreachTicks = 0;
    }

    Vec3 look = Vec3.directionFromRotation(HexagonalGridManager.getFreeCamPitch(), HexagonalGridManager.getFreeCamYaw());
    EverbookRiftMath.HitResult hit = EverbookRiftMath.raycastRiftWorkspace(eye, look, riftAnchorPos, riftYaw, riftPitch, getCurrentEntries(), scrollRowOffset);

    hoveredPatternIdx = hit.patternIndex();
    evalHovered = hit.evalButtonHovered();
    hoveredMiniNode = hit.miniGridNodeIndex();
    totalWrappedRows = Math.max(1, hit.totalRows());

    boolean sprintHeld = mc.options.keySprint.isDown();
    long windowHandle = mc.getWindow().getWindow();
    boolean rightHeld = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    boolean leftHeld = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

    if (sweepDeselecting && !sprintHeld && leftHeld && hoveredPatternIdx >= 0 && getCurrentEntries().size() > hoveredPatternIdx) {
      SELECTED_IDS.remove(getCurrentEntries().get(hoveredPatternIdx).id());
    }
    if (!leftHeld) {
      sweepDeselecting = false;
    }

    if (sweepSelecting && sprintHeld && rightHeld && hoveredPatternIdx >= 0 && getCurrentEntries().size() > hoveredPatternIdx) {
      SELECTED_IDS.add(getCurrentEntries().get(hoveredPatternIdx).id());
    }
    if (!rightHeld) {
      sweepSelecting = false;
      if (draggingSelected) {
        draggingSelected = false;
        syncToServer("SAVE");
      }
      if (!MINI_GRID_PATH.isEmpty()) {
        if (MINI_GRID_PATH.size() >= 2) {
          EverbookRiftMath.compileMiniGridStroke(MINI_GRID_PATH);
        }
        MINI_GRID_PATH.clear();
      }
    }

    if (draggingSelected && rightHeld) {
      if (hit.autoScrollDir() != 0) {
        float maxScroll = Math.max(0.0f, totalWrappedRows - 4.0f);
        scrollRowOffset = Mth.clamp(scrollRowOffset + (hit.autoScrollDir() * 0.14f), 0.0f, maxScroll);
      }
      if (hoveredPatternIdx >= 0 && getCurrentEntries().size() > hoveredPatternIdx) {
        shiftSelectedBlockTo(hoveredPatternIdx);
      }
    }

    if (!sprintHeld && !draggingSelected && rightHeld && hoveredMiniNode >= 0) {
      int sz = MINI_GRID_PATH.size();
      if (sz >= 2 && MINI_GRID_PATH.get(sz - 2) == hoveredMiniNode) {
        MINI_GRID_PATH.remove(sz - 1);
      } else if (MINI_GRID_PATH.isEmpty() || MINI_GRID_PATH.get(sz - 1) != hoveredMiniNode) {
        MINI_GRID_PATH.add(hoveredMiniNode);
        mc.level.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(),
          SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.PLAYERS, 0.6f, 1.1f + (MINI_GRID_PATH.size() * 0.05f));
      }
    }
  }


  @SubscribeEvent
  public static void onMouseButton(InputEvent.MouseButton.Pre event) {
    if (!active || !HexagonalGridManager.isActive()) return;
    Minecraft mc = Minecraft.getInstance();
    if (mc.screen != null || mc.player == null || mc.level == null) return;

    boolean sprintHeld = mc.options.keySprint.isDown();

    if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == InputConstants.PRESS && sprintHeld) {
      riftLocked = !riftLocked;
      draggingSelected = false;
      sweepSelecting = false;
      MINI_GRID_PATH.clear();
      riftOverreachTicks = 0;
      if (riftLocked) {
        cageCenterPos = HexagonalGridManager.getFreeCamPos();
      }
      mc.level.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(),
        SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.8f, riftLocked ? 1.25f : 0.85f);
      event.setCanceled(true);
      return;
    }

    if (!riftLocked) return;

    if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == InputConstants.PRESS && evalHovered) {
      triggerEvaluate();
      mc.level.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(),
        SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 1.35f);
      event.setCanceled(true);
      return;
    }

    if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == InputConstants.PRESS && !sprintHeld) {
      List<EverbookData.PatternEntry> entries = getCurrentEntries();
      if (hoveredPatternIdx >= 0 && entries.size() > hoveredPatternIdx) {
        String clickedId = entries.get(hoveredPatternIdx).id();
        SELECTED_IDS.remove(clickedId);
        sweepDeselecting = true;
      } else {
        SELECTED_IDS.clear();
        sweepDeselecting = true;
      }
      event.setCanceled(true);
      return;
    }

    if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == InputConstants.PRESS) {
      List<EverbookData.PatternEntry> entries = getCurrentEntries();
      if (sprintHeld) {
        // Start sweep multi-select even if the swipe begins in empty space between patterns!
        sweepSelecting = true;
        if (hoveredPatternIdx >= 0 && entries.size() > hoveredPatternIdx) {
          String clickedId = entries.get(hoveredPatternIdx).id();
          if (SELECTED_IDS.contains(clickedId)) {
            SELECTED_IDS.remove(clickedId);
          } else {
            SELECTED_IDS.add(clickedId);
          }
        } else {
          SELECTED_IDS.clear();
        }
        event.setCanceled(true);
      } else if (hoveredPatternIdx >= 0 && entries.size() > hoveredPatternIdx) {
        String clickedId = entries.get(hoveredPatternIdx).id();
        if (SELECTED_IDS.contains(clickedId)) {
          draggingSelected = true;
          draggedAnchorId = clickedId;
          event.setCanceled(true);
        }
      }
    }
  }


  @SubscribeEvent
  public static void onKeyInput(InputEvent.Key event) {
    if (!active || !riftLocked || event.getAction() != InputConstants.PRESS) return;
    Minecraft mc = Minecraft.getInstance();
    if (mc.screen != null) return;

    int key = event.getKey();
    boolean ctrl = (event.getModifiers() & GLFW.GLFW_MOD_CONTROL) != 0;

    // Evaluate non-destructively (R or Enter)
    if (key == GLFW.GLFW_KEY_R || key == GLFW.GLFW_KEY_ENTER) {
      triggerEvaluate();
    }
    // Batch Delete via configurable KeyMapping (never touches Backspace!)
    else if (EverbookKeybinds.DELETE_IOTA_KEY.matches(event.getKey(), event.getScanCode()) && !SELECTED_IDS.isEmpty()) {
      getCurrentEntries().removeIf(e -> SELECTED_IDS.contains(e.id()));
      SELECTED_IDS.clear();
      syncToServer("SAVE");
    }
    // Batch Copy (Ctrl+C)
    else if (ctrl && key == GLFW.GLFW_KEY_C && !SELECTED_IDS.isEmpty()) {
      CLIPBOARD.clear();
      for (EverbookData.PatternEntry e : getCurrentEntries()) {
        if (SELECTED_IDS.contains(e.id())) CLIPBOARD.add(e);
      }
    }
    // Batch Paste (Ctrl+V)
    else if (ctrl && key == GLFW.GLFW_KEY_V && !CLIPBOARD.isEmpty()) {
      List<EverbookData.PatternEntry> entries = getCurrentEntries();
      SELECTED_IDS.clear();
      for (EverbookData.PatternEntry c : CLIPBOARD) {
        EverbookData.PatternEntry copy = c.duplicateWithOffset(0, 0, entries.size());
        entries.add(copy);
        SELECTED_IDS.add(copy.id());
      }
      syncToServer("SAVE");
    }
  }


  public static void handleRiftScroll(float delta) {
    float maxScroll = Math.max(0.0f, totalWrappedRows - 4.0f);
    scrollRowOffset = Mth.clamp(scrollRowOffset - (delta * 0.65f), 0.0f, maxScroll);
  }

  public static void syncLockedTransform(Vec3 pos, float yaw, float pitch) {
    riftAnchorPos = pos;
    riftYaw = yaw;
    riftPitch = pitch;
  }

  private static void shiftSelectedBlockTo(int targetIdx) {
    List<EverbookData.PatternEntry> entries = getCurrentEntries();
    if (SELECTED_IDS.isEmpty() || 0 > targetIdx || targetIdx >= entries.size()) return;
    if (SELECTED_IDS.contains(entries.get(targetIdx).id())) return;

    int anchorIdx = -1;
    for (int i = 0; entries.size() > i; i++) {
      String id = entries.get(i).id();
      if (id.equals(draggedAnchorId) || (anchorIdx == -1 && SELECTED_IDS.contains(id))) {
        anchorIdx = i;
        if (id.equals(draggedAnchorId)) break;
      }
    }
    boolean movingRight = targetIdx > anchorIdx;

    List<EverbookData.PatternEntry> selected = new ArrayList<>();
    List<EverbookData.PatternEntry> remaining = new ArrayList<>();
    int insertAt = 0;

    for (int i = 0; entries.size() > i; i++) {
      EverbookData.PatternEntry entry = entries.get(i);
      if (SELECTED_IDS.contains(entry.id())) {
        selected.add(entry);
      } else {
        if (movingRight ? (targetIdx >= i) : (targetIdx > i)) {
          insertAt++;
        }
        remaining.add(entry);
      }
    }
    insertAt = Mth.clamp(insertAt, 0, remaining.size());
    remaining.addAll(insertAt, selected);
    entries.clear();
    entries.addAll(remaining);
  }
}
