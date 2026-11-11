package com.genesiz.hexacreatecore.hexcasting;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

@EventBusSubscriber(modid = "hexacreatecore", value = Dist.CLIENT)
public final class HexCastingClientEvents {

  @SubscribeEvent
  public static void onMovementInput(MovementInputUpdateEvent event) {
    if (!HexagonalGridManager.isActive()) return;

    // Direct movement translation update pass onto our active spatial tracking frame
    HexagonalGridManager.handleFreeCamMovement(event.getInput());

    // Secure physical layer: wipe character movement impulses to lock player entity position in the world
    event.getInput().forwardImpulse = 0.0f;
    event.getInput().leftImpulse = 0.0f;
    event.getInput().up = false;
    event.getInput().down = false;
    event.getInput().left = false;
    event.getInput().right = false;
    event.getInput().jumping = false;
    event.getInput().shiftKeyDown = false;
  }

  @SubscribeEvent
  public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
    if (!HexagonalGridManager.isActive()) return;
    HexagonalGridManager.handleStackScroll((float) event.getScrollDeltaY());
    event.setCanceled(true);
  }
}

