/*
 * THIS PART OF THE CODE IS MEANT FOR DEVELOPERS
 * */
package com.genesiz.hexacreatecore.dialogue;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// LEFT/RIGHT CLICK HANDLER
@EventBusSubscriber(modid = "hexacreatecore", value = Dist.CLIENT)
public final class DialogueInputHandler {
  private static boolean wasUseDown = false;
  private static boolean consumedUseThisTick = false;

  @SubscribeEvent
  public static void onClientTick(ClientTickEvent.Pre event) {
    consumedUseThisTick = false;
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null || mc.screen != null) {
      wasUseDown = false;
      return;
    }

    boolean isUseDown = mc.options.keyUse.isDown();
    if (isUseDown && !wasUseDown) {
      for (Entity entity : mc.level.entitiesForRendering()) {
        if (entity instanceof Dialogue3D dialogueEntity && dialogueEntity.shouldShowDialogue()) {
          if (dialogueEntity.getDialoguePlayback().triggerRightClick(entity, mc.player)) {
            consumedUseThisTick = true;
            break;
          }
        }
      }
    }
    wasUseDown = isUseDown;
  }

  @SubscribeEvent
  public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;

    // Left-click -> triggerLeftClick (inspect label)
    if (event.isAttack()) {
      for (Entity entity : mc.level.entitiesForRendering()) {
        if (entity instanceof Dialogue3D dialogueEntity && dialogueEntity.shouldShowDialogue()) {
          if (dialogueEntity.getDialoguePlayback().triggerLeftClick(entity, mc.player)) {
            event.setSwingHand(true);
            event.setCanceled(true);
            return;
          }
        }
      }
    }

    // Right-click -> if onClientTick already handled a button click this tick, cancel vanilla mobInteract!
    if (event.isUseItem()) {
      if (consumedUseThisTick) {
        event.setSwingHand(true);
        event.setCanceled(true);
        return;
      }
      for (Entity entity : mc.level.entitiesForRendering()) {
        if (entity instanceof Dialogue3D dialogueEntity && dialogueEntity.shouldShowDialogue()) {
          if (dialogueEntity.getDialoguePlayback().triggerRightClick(entity, mc.player)) {
            wasUseDown = true;
            consumedUseThisTick = true;
            event.setSwingHand(true);
            event.setCanceled(true);
            return;
          }
        }
      }
    }
  }

  @SubscribeEvent
  public static void onEntityInteract(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
    if (!event.getLevel().isClientSide) return;
    if (event.getEntity() instanceof Dialogue3D dialogueEntity && dialogueEntity.shouldShowDialogue()) {
      if (dialogueEntity.getDialoguePlayback().hoveredOptionIndex != -1) {
        event.setCanceled(true);
      }
    }
  }

  @SubscribeEvent
  public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
    if (event.getLevel().isClientSide && isHoveringAnyOption(Minecraft.getInstance())) {
      event.setCanceled(true);
    }
  }

  private static boolean isHoveringAnyOption(Minecraft mc) {
    for (Entity entity : mc.level.entitiesForRendering()) {
      if (!(entity instanceof Dialogue3D dialogueEntity) || !dialogueEntity.shouldShowDialogue()) continue;
      if (dialogueEntity.getDialoguePlayback().hoveredOptionIndex >= 0) return true;
    }
    return false;
  }

  private static boolean handleLeftClick(Minecraft mc) {
    for (Entity entity : mc.level.entitiesForRendering()) {
      if (!(entity instanceof Dialogue3D dialogueEntity) || !dialogueEntity.shouldShowDialogue()) continue;
      if (dialogueEntity.getDialoguePlayback().triggerLeftClick(entity, mc.player)) return true;
    }
    return false;
  }

  private static boolean handleRightClick(Minecraft mc) {
    for (Entity entity : mc.level.entitiesForRendering()) {
      if (!(entity instanceof Dialogue3D dialogueEntity) || !dialogueEntity.shouldShowDialogue()) continue;
      if (dialogueEntity.getDialoguePlayback().triggerRightClick(entity, mc.player)) return true;
    }
    return false;
  }
}
