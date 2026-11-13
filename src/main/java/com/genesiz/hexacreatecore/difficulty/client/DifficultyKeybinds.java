package com.genesiz.hexacreatecore.difficulty.client;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.difficulty.network.DifficultyPackets;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class DifficultyKeybinds {
  public static final KeyMapping PARRY_KEY = new KeyMapping(
    "key.hexacreatecore.parry", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.hexacreatecore"
  );
  public static final KeyMapping UNLEASH_KEY = new KeyMapping(
    "key.hexacreatecore.unleash_riposte", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.hexacreatecore"
  );
  public static final KeyMapping SELECTOR_KEY = new KeyMapping(
    "key.hexacreatecore.difficulty_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.hexacreatecore"
  );

  private static boolean wasParryDown = false;

  @EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
  public static final class ModEvents {
    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
      event.register(PARRY_KEY);
      event.register(UNLEASH_KEY);
      event.register(SELECTOR_KEY);
    }
  }

  @EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
  public static final class GameEvents {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.screen != null) return;

      while (SELECTOR_KEY.consumeClick()) {
        DifficultyClientState.selectorOpen = !DifficultyClientState.selectorOpen;
        if (DifficultyClientState.selectorOpen) {
          DifficultyClientState.selectorBaseYaw = mc.player.getYRot();
        }
      }

      while (UNLEASH_KEY.consumeClick()) {
        PacketDistributor.sendToServer(new DifficultyPackets.ActionPayload("UNLEASH_RIPOSTE", "", 0));
      }

      boolean parryDown = PARRY_KEY.isDown();
      if (parryDown && !wasParryDown) {
        DifficultyClientState.onParryKeyPressed();
      } else if (!parryDown && wasParryDown) {
        DifficultyClientState.onParryKeyReleased();
      }
      wasParryDown = parryDown;
      DifficultyClientState.tickParryAutoFail();
    }

    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
      if (!DifficultyClientState.selectorOpen || event.getAction() != GLFW.GLFW_PRESS) return;
      if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
        if (!DifficultyClientState.hoveredTierId.isEmpty()) {
          PacketDistributor.sendToServer(
            new DifficultyPackets.ActionPayload("SELECT_TIER", DifficultyClientState.hoveredTierId, 0)
          );
          DifficultyClientState.selectorOpen = false;
          event.setCanceled(true);
        }
      } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
        DifficultyClientState.selectorOpen = false;
        event.setCanceled(true);
      }
    }
  }
}
