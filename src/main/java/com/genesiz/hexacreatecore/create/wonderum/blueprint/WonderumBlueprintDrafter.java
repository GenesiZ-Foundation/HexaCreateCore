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
      event.getEntity().displayClientMessage(
        Component.literal("§b[Wonderum] §7No blueprints found in §fblueprints/structures/wonderum/"), true
      );
      return;
    }

    selectedIndex = (selectedIndex + 1) % wonderumIds.size();
    selectedStructureId = wonderumIds.get(selectedIndex);

    MultiblockHologramRenderer.setProjection(new HologramProjectionPayload(selectedStructureId, BlockPos.ZERO, -1));
    event.getEntity().playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.85f, 1.35f);
    event.getEntity().displayClientMessage(
      Component.literal("§b[Wonderum] §fBlueprint Selected: §e" + selectedStructureId + " §8(Right-click block to anchor)"), true
    );
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.SUCCESS);
  }

  @SubscribeEvent(priority = EventPriority.HIGH)
  public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
    if (!event.getLevel().isClientSide) return;
    if (event.getHand() != InteractionHand.MAIN_HAND) return;
    if (!event.getItemStack().is(ModItems.WONDERUM.get())) return;

    // 1. Anchoring a pending Wonderum blueprint hologram
    if (MultiblockHologramRenderer.isPendingPlacement() && selectedStructureId != null) {
      BlockPos anchor = event.getPos().relative(event.getFace() != null ? event.getFace() : net.minecraft.core.Direction.UP);
      MultiblockHologramRenderer.setProjection(new HologramProjectionPayload(selectedStructureId, anchor, 72000));
      event.getEntity().playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.75f, 1.4f);
      event.getEntity().displayClientMessage(
        Component.literal("§b[Wonderum] §fHologram Anchored: §a" + selectedStructureId + " §8(Right-click ghost blocks to draft)"), true
      );
      event.setCanceled(true);
      event.setCancellationResult(InteractionResult.SUCCESS);
      return;
    }

    // 2. Pseudo-creative drafting when clicking directly on/through a projected ghost block
    if (!event.getEntity().isShiftKeyDown() && MultiblockHologramRenderer.getActiveStructureId() != null) {
      if (tryDraftGhostBlock(event.getLevel(), event.getEntity())) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
      }
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGH)
  public static void onRightClickAirDraft(PlayerInteractEvent.RightClickItem event) {
    if (!event.getLevel().isClientSide) return;
    if (event.getHand() != InteractionHand.MAIN_HAND) return;
    if (!event.getItemStack().is(ModItems.WONDERUM.get())) return;
    if (event.getEntity().isShiftKeyDown()) return;
    if (MultiblockHologramRenderer.getActiveStructureId() == null) return;

    if (tryDraftGhostBlock(event.getLevel(), event.getEntity())) {
      event.setCanceled(true);
      event.setCancellationResult(InteractionResult.SUCCESS);
    }
  }

  @SubscribeEvent
  public static void onClientTick(ClientTickEvent.Post event) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;
    if (!mc.player.getMainHandItem().is(ModItems.WONDERUM.get())) return;
    if (MultiblockHologramRenderer.getActiveStructureId() == null) return;

    GhostTarget target = raycastGhostBlock(mc.level, mc.player, 10.0D);
    if (target != null) {
      Component blockName = target.expectedState().getBlock().getName();
      mc.player.displayClientMessage(
        Component.literal("§b[Draft] §fTarget: §e" + blockName.getString() + " §8(Right-click to place)"), true
      );
    }
  }

  private static boolean tryDraftGhostBlock(Level level, Player player) {
    GhostTarget target = raycastGhostBlock(level, player, 10.0D);
    if (target == null) return false;

    BlockState expected = target.expectedState();
    Item neededItem = expected.getBlock().asItem();
    if (!player.getAbilities().instabuild && !hasItemInInventory(player, neededItem)) {
      player.displayClientMessage(
        Component.literal("§c[Draft] §fMissing: §e" + expected.getBlock().getName().getString()), true
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

    Vec3 eye = player.getEyePosition(1.0f);
    Vec3 look = player.getViewVector(1.0f);
    int total = bp.blockCount();
    int[] offX = bp.offsetX();
    int[] offY = bp.offsetY();
    int[] offZ = bp.offsetZ();
    BlockState[] states = bp.states();

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
}

