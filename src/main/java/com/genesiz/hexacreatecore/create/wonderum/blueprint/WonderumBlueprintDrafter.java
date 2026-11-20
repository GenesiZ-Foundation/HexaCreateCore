package com.genesiz.hexacreatecore.create.wonderum.blueprint;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.item.ModItems;
import com.genesiz.hexacreatecore.threedified.hologram.BlueprintReloadListener;
import com.genesiz.hexacreatecore.threedified.hologram.MultiblockBlueprintData;
import com.genesiz.hexacreatecore.threedified.hologram.MultiblockHologramRenderer;
import com.genesiz.hexacreatecore.threedified.network.HologramProjectionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class WonderumBlueprintDrafter {

  private static int selectedIndex = 0;
  private static String selectedStructureId = null;

  public static final net.minecraft.client.KeyMapping ROTATE_HOLOGRAM_KEY = new net.minecraft.client.KeyMapping(
    "key.hexacreatecore.rotate_hologram",
    com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
    org.lwjgl.glfw.GLFW.GLFW_KEY_R,
    "key.categories.hexacreatecore"
  );

  public static final net.minecraft.client.KeyMapping MIRROR_HOLOGRAM_KEY = new net.minecraft.client.KeyMapping(
    "key.hexacreatecore.mirror_hologram",
    com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
    org.lwjgl.glfw.GLFW.GLFW_KEY_M,
    "key.categories.hexacreatecore"
  );

  public static final net.minecraft.client.KeyMapping OPEN_PALETTE_KEY = new net.minecraft.client.KeyMapping(
    "key.hexacreatecore.open_draft_palette",
    com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
    org.lwjgl.glfw.GLFW.GLFW_KEY_B,
    "key.categories.hexacreatecore"
  );

  @EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
  public static final class ModBusKeys {
    @SubscribeEvent
    public static void onRegisterKeys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
      event.register(ROTATE_HOLOGRAM_KEY);
      event.register(MIRROR_HOLOGRAM_KEY);
      event.register(OPEN_PALETTE_KEY);
    }
  }

  private WonderumBlueprintDrafter() {}

  public record GhostTarget(BlockPos worldPos, BlockState expectedState) {}

  @SubscribeEvent(priority = EventPriority.NORMAL)
  public static void onSneakRightClickAir(PlayerInteractEvent.RightClickItem event) {
    if (!event.getLevel().isClientSide) return;
    if (event.getHand() != InteractionHand.MAIN_HAND) return;
    if (!event.getItemStack().is(ModItems.WONDERUM.get())) return;
    if (!event.getEntity().isShiftKeyDown()) return;

    List<String> wonderumIds = BlueprintReloadListener.getWonderumBlueprintIds();
    if (wonderumIds.isEmpty()) {
      com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud.show(
        "§b[Wonderum] §7No blueprints found in §fblueprints/structures/wonderum/"
      );
      return;
    }

    selectedIndex = (selectedIndex + 1) % wonderumIds.size();
    selectedStructureId = wonderumIds.get(selectedIndex);

    WonderumDraftingSession.setDraftingModeActive(false);
    MultiblockHologramRenderer.setProjection(new HologramProjectionPayload(selectedStructureId, BlockPos.ZERO, -1));
    event.getEntity().playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.85f, 1.35f);
    com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud.show(
      "§b[Wonderum] §fBlueprint Selected: §e" + selectedStructureId + " §8(Right-click block to anchor)"
    );
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.SUCCESS);
  }


  @SubscribeEvent
  public static void onClientTick(ClientTickEvent.Post event) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null || mc.screen != null) return;

    boolean holdingWonderum = mc.player.getMainHandItem().is(ModItems.WONDERUM.get());
    boolean hasHologram = MultiblockHologramRenderer.isPendingPlacement() || MultiblockHologramRenderer.getActiveStructureId() != null;

    if (holdingWonderum) {
      while (OPEN_PALETTE_KEY.consumeClick()) {
        mc.setScreen(new WonderumPaletteScreen());
      }
    } else {
      while (OPEN_PALETTE_KEY.consumeClick()) {}
    }

    if (holdingWonderum && hasHologram) {
      while (ROTATE_HOLOGRAM_KEY.consumeClick()) {
        MultiblockHologramRenderer.cycleRotation();
        mc.player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6f, 1.35f);
        com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud.show(
          "§b[Wonderum] §fHologram Orientation §8— §e" + MultiblockHologramRenderer.formatOrientationLabel()
        );
      }

      while (MIRROR_HOLOGRAM_KEY.consumeClick()) {
        MultiblockHologramRenderer.cycleMirror();
        mc.player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6f, 1.15f);
        com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud.show(
          "§b[Wonderum] §fHologram Orientation §8— §e" + MultiblockHologramRenderer.formatOrientationLabel()
        );
      }
    } else {
      while (ROTATE_HOLOGRAM_KEY.consumeClick()) {}
      while (MIRROR_HOLOGRAM_KEY.consumeClick()) {}
    }

    if (!holdingWonderum) return;
    if (MultiblockHologramRenderer.getActiveStructureId() == null) return;

    GhostTarget target = raycastGhostBlock(mc.level, mc.player, 10.0D);
    if (target != null) {
      Component blockName = target.expectedState().getBlock().getName();
      com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud.show(
        "§b[Draft] §fTarget: §e" + blockName.getString() + " §8(Right-click to place)",
        0xFF21B7D1,
        12
      );
    }
  }

  private static boolean tryDraftGhostBlock(Level level, Player player) {
    GhostTarget target = raycastGhostBlock(level, player, 10.0D);
    if (target == null) return false;

    BlockState expected = target.expectedState();
    Item neededItem = expected.getBlock().asItem();
    if (!player.getAbilities().instabuild && !hasItemInInventory(player, neededItem)) {
      com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud.show(
        "§c[Draft] §fMissing: §e" + expected.getBlock().getName().getString()
      );
      return true;
    }

    int blockStateId = net.minecraft.world.level.block.Block.getId(expected);
    PacketDistributor.sendToServer(new WonderumDraftBlockPayload(target.worldPos(), blockStateId));

    SoundType sound = expected.getSoundType(level, target.worldPos(), player);
    player.playNotifySound(sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0f) * 0.5f, sound.getPitch() * 0.9f);
    return true;
  }

  private static boolean hasItemInInventory(Player player, Item item) {
    for (int i = 0; player.getInventory().getContainerSize() > i; i++) {
      ItemStack stack = player.getInventory().getItem(i);
      if (!stack.isEmpty() && stack.is(item)) return true;
    }
    return false;
  }

  public static GhostTarget raycastGhostBlock(Level level, Player player, double reach) {
    String structureId = MultiblockHologramRenderer.getActiveStructureId();
    BlockPos anchor = MultiblockHologramRenderer.getAnchorPos();
    if (structureId == null || anchor == null) return null;

    MultiblockBlueprintData bp = BlueprintReloadListener.getBlueprint(structureId);
    if (bp == null) return null;

    MultiblockBlueprintData.TransformedBlueprint tb = MultiblockHologramRenderer.getTransformedBlueprint(level, anchor, bp);
    Vec3 eye = player.getEyePosition(1.0f);
    Vec3 look = player.getViewVector(1.0f);
    int total = tb.blockCount();
    int[] offX = tb.offsetX();
    int[] offY = tb.offsetY();
    int[] offZ = tb.offsetZ();
    BlockState[] states = tb.states();

    GhostTarget best = null;
    double bestDistSq = reach * reach;

    for (int i = 0; total > i; i++) {
      BlockPos wp = anchor.offset(offX[i], offY[i], offZ[i]);
      BlockState worldState = level.getBlockState(wp);
      if (worldState.is(states[i].getBlock())) continue;

      net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(wp);
      if (box.clip(eye, eye.add(look.scale(reach))).isPresent()) {
        double distSq = eye.distanceToSqr(Vec3.atCenterOf(wp));
        if (bestDistSq > distSq) {
          bestDistSq = distSq;
          best = new GhostTarget(wp, states[i]);
        }
      }
    }
    return best;
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void onUseOrAttackInput(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null || mc.screen != null) return;
    if (!mc.player.getMainHandItem().is(ModItems.WONDERUM.get())) return;

    // 1. LEFT-CLICK:
    if (event.isAttack()) {
      // 1A. SNEAK + LEFT-CLICK: Stop blueprint selection / clear active hologram projection
      if (mc.player.isShiftKeyDown()
        && (MultiblockHologramRenderer.isPendingPlacement() || MultiblockHologramRenderer.getActiveStructureId() != null)) {
        selectedStructureId = null;
        MultiblockHologramRenderer.setProjection(new HologramProjectionPayload("", BlockPos.ZERO, 0));
        mc.player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7f, 1.1f);
        com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud.show("§b[Wonderum] §eBlueprint selection cancelled");
        event.setSwingHand(true);
        event.setCanceled(true);
        return;
      }

      // 1B. Erase any targeted Pseudo-Creative Draft Ghost Block
      if (!WonderumDraftingSession.getBlocks().isEmpty()) {
        BlockPos hitGhost = raycastDraftedGhost(mc.player, 10.0D);
        if (hitGhost != null) {
          WonderumDraftingSession.getBlocks().remove(hitGhost);
          mc.player.playNotifySound(SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.65f, 1.2f);
          event.setSwingHand(true);
          event.setCanceled(true);
          return;
        }
      }
    }

    if (!event.isUseItem()) return;

    // 2. RIGHT-CLICK (Not Sneaking):
    if (mc.player.isShiftKeyDown()) return;

    if (mc.options.keySprint.isDown()
    && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr
    && bhr.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
      var be = mc.level.getBlockEntity(bhr.getBlockPos());
      if (be != null && isCreateKinetic(be)) {
        PacketDistributor.sendToServer(new com.genesiz.hexacreatecore.create.wonderum.WonderumKineticImpulsePayload(bhr.getBlockPos()));
        event.setSwingHand(true);
        event.setCanceled(true);
        return;
      }
    }

    // 2A. Orbiting 3D Drafting Menu buttons/sliders
    if (WonderumDraftingMenuRenderer.handleMenuRightClick(mc)) {
      event.setSwingHand(true);
      event.setCanceled(true);
      return;
    }

    // 2B. Anchor a pending Wonderum blueprint hologram (works up to 12 blocks away on any block, or 3 blocks ahead in air)
    if (MultiblockHologramRenderer.isPendingPlacement()) {
      String idToAnchor = (selectedStructureId != null)
        ? selectedStructureId
        : MultiblockHologramRenderer.getActiveStructureId();

      if (idToAnchor == null) {
        List<String> ids = BlueprintReloadListener.getWonderumBlueprintIds();
        if (!ids.isEmpty()) {
          idToAnchor = ids.get(Math.floorMod(selectedIndex, ids.size()));
          selectedStructureId = idToAnchor;
        }
      }

      if (idToAnchor != null) {
        net.minecraft.world.phys.HitResult hit = mc.player.pick(12.0D, 1.0f, false);
        BlockPos anchor;
        if (hit instanceof net.minecraft.world.phys.BlockHitResult bhr && bhr.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
          anchor = bhr.getBlockPos().relative(bhr.getDirection());
        } else {
          Vec3 ahead = mc.player.getEyePosition(1.0f).add(mc.player.getViewVector(1.0f).scale(3.0D));
          anchor = BlockPos.containing(ahead);
        }

        MultiblockHologramRenderer.setProjection(new HologramProjectionPayload(idToAnchor, anchor, 72000));
        mc.player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.75f, 1.4f);
        com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud.show(
          "§b[Wonderum] §fHologram Anchored: §a" + idToAnchor + " §8(" + MultiblockHologramRenderer.formatOrientationLabel() + ")"
        );
        event.setSwingHand(true);
        event.setCanceled(true);
        return;
      }
    }

    // 2C. Pseudo-creative drafting mode (place new intangible ghost block)
    if (WonderumDraftingSession.isDraftingActive() && MultiblockHologramRenderer.getActiveStructureId() == null) {
      if (tryPlaceDraftGhost(mc.level, mc.player)) {
        event.setSwingHand(true);
        event.setCanceled(true);
        return;
      }
    }

    // 2D. Survival/Creative auto-drafting onto an already-anchored hologram
    if (MultiblockHologramRenderer.getActiveStructureId() != null) {
      if (tryDraftGhostBlock(mc.level, mc.player)) {
        event.setSwingHand(true);
        event.setCanceled(true);
      }
    }
  }

  private static boolean isCreateKinetic(net.minecraft.world.level.block.entity.BlockEntity be) {
    for (Class<?> c = be.getClass(); c != null; c = c.getSuperclass()) {
      if (c.getName().equals("com.simibubi.create.content.kinetics.base.KineticBlockEntity")) return true;
    }
    return false;
  }

  private static boolean tryPlaceDraftGhost(Level level, Player player) {
    double reach = 8.0D;
    Vec3 eye = player.getEyePosition(1.0f);
    Vec3 look = player.getViewVector(1.0f);
    Vec3 end = eye.add(look.scale(reach));

    // Check distance to closest existing draft ghost block
    net.minecraft.world.phys.BlockHitResult ghostHit = raycastDraftedGhostFace(player, reach);
    double ghostDistSq = (ghostHit != null) ? eye.distanceToSqr(ghostHit.getLocation()) : Double.MAX_VALUE;

    // Check distance to closest real world block
    net.minecraft.world.phys.BlockHitResult worldHit = level.clip(new net.minecraft.world.level.ClipContext(
      eye, end,
      net.minecraft.world.level.ClipContext.Block.OUTLINE,
      net.minecraft.world.level.ClipContext.Fluid.NONE,
      player
    ));
    double worldDistSq = (worldHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK)
    ? eye.distanceToSqr(worldHit.getLocation())
    : Double.MAX_VALUE;

    net.minecraft.world.phys.BlockHitResult chosenHit;
    if (ghostDistSq == Double.MAX_VALUE && worldDistSq == Double.MAX_VALUE) {
      return false;
    } else if (ghostDistSq < worldDistSq) {
      chosenHit = ghostHit;
    } else {
      chosenHit = worldHit;
    }

    BlockPos clickedPos = chosenHit.getBlockPos();
    net.minecraft.core.Direction face = chosenHit.getDirection();
    BlockPos placePos = clickedPos.relative(face);

    // Prevent placing inside a solid real block or overwriting an existing ghost block
    if (!level.getBlockState(placePos).canBeReplaced() || WonderumDraftingSession.getBlocks().containsKey(placePos)) {
      return false;
    }

    net.minecraft.world.level.block.Block brush = WonderumDraftingSession.getSelectedBrushBlock();
    net.minecraft.world.item.context.BlockPlaceContext ctx = new net.minecraft.world.item.context.BlockPlaceContext(
      player,
      InteractionHand.MAIN_HAND,
      new ItemStack(brush),
      chosenHit
    );
    BlockState placedState = brush.getStateForPlacement(ctx);
    if (placedState == null) placedState = brush.defaultBlockState();

    WonderumDraftingSession.getBlocks().put(placePos.immutable(), placedState);
    player.playNotifySound(SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.75f, 1.3f);
    return true;
  }

  public static BlockPos raycastDraftedGhost(Player player, double reach) {
    net.minecraft.world.phys.BlockHitResult hit = raycastDraftedGhostFace(player, reach);
    return (hit != null) ? hit.getBlockPos() : null;
  }

  public static net.minecraft.world.phys.BlockHitResult raycastDraftedGhostFace(Player player, double reach) {
    Vec3 eye = player.getEyePosition(1.0f);
    Vec3 end = eye.add(player.getViewVector(1.0f).scale(reach));
    net.minecraft.world.phys.BlockHitResult closest = null;
    double bestDistSq = reach * reach;

    for (BlockPos bp : WonderumDraftingSession.getBlocks().keySet()) {
      net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(bp);
      java.util.Optional<Vec3> hitOpt = box.clip(eye, end);
      if (hitOpt.isPresent()) {
        Vec3 hitVec = hitOpt.get();
        double distSq = eye.distanceToSqr(hitVec);
        if (bestDistSq > distSq) {
          bestDistSq = distSq;
          net.minecraft.core.Direction face = getHitFace(bp, hitVec);
          closest = new net.minecraft.world.phys.BlockHitResult(hitVec, face, bp, false);
        }
      }
    }
    return closest;
  }

  private static net.minecraft.core.Direction getHitFace(BlockPos bp, Vec3 hit) {
    double dx = hit.x - (bp.getX() + 0.5D);
    double dy = hit.y - (bp.getY() + 0.5D);
    double dz = hit.z - (bp.getZ() + 0.5D);
    double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
    if (ax >= ay && ax >= az) {
      return dx >= 0.0D ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST;
    } else if (ay >= ax && ay >= az) {
      return dy >= 0.0D ? net.minecraft.core.Direction.UP : net.minecraft.core.Direction.DOWN;
    } else {
      return dz >= 0.0D ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH;
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void onMouseScroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null || mc.screen != null) return;
    if (!mc.player.getMainHandItem().is(ModItems.WONDERUM.get())) return;
    if (!mc.player.isShiftKeyDown()) return;

    List<String> wonderumIds = BlueprintReloadListener.getWonderumBlueprintIds();
    if (wonderumIds.isEmpty()) {
      com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud.show(
        "§b[Wonderum] §7No blueprints found in §fblueprints/structures/wonderum/"
      );
      event.setCanceled(true);
      return;
    }

    int dir = (event.getScrollDeltaY() > 0.0D) ? -1 : 1;
    selectedIndex = Math.floorMod(selectedIndex + dir, wonderumIds.size());
    selectedStructureId = wonderumIds.get(selectedIndex);

    WonderumDraftingSession.setDraftingModeActive(false);
    MultiblockHologramRenderer.setProjection(new HologramProjectionPayload(selectedStructureId, BlockPos.ZERO, -1));
    mc.player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75f, 1.35f);
    event.setCanceled(true);
  }

  public static int getSelectedIndex() {
    return selectedIndex;
  }
}

