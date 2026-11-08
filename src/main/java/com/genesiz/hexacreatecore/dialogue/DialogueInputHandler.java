package com.genesiz.hexacreatecore.dialogue;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = "hexacreatecore", value = Dist.CLIENT)
public final class DialogueInputHandler {
  private static boolean wasUseDown = false;

  @SubscribeEvent
  public static void onClientTick(ClientTickEvent.Pre event) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) {
      wasUseDown = false;
      return;
    }

    boolean isUseDown = mc.options.keyUse.isDown();
    // Rising edge: fires ONLY on the first tick right-click is pressed
    if (isUseDown && !wasUseDown) {
      handleRightClick(mc);
    }
    wasUseDown = isUseDown;
  }

  @SubscribeEvent
  public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;

    if (event.isAttack()) {
      if (handleLeftClick(mc)) {
        event.setSwingHand(false);
        event.setCanceled(true);
      }
    } else if (event.isUseItem()) {
      // Cancel all vanilla repeat-use ticks while hovering over any 3D dialogue button
      if (isHoveringAnyOption(mc)) {
        event.setSwingHand(false);
        event.setCanceled(true);
      }
    }
  }

  @SubscribeEvent
  public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
    if (event.getLevel().isClientSide && isHoveringAnyOption(Minecraft.getInstance())) {
      event.setCanceled(true);
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
