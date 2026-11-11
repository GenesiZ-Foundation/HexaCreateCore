package com.genesiz.hexacreatecore.hexcasting;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.InputEvent;

@EventBusSubscriber(modid = "hexacreatecore", value = Dist.CLIENT)
public final class HexCastingGridInterceptor {

  /**
   * Intercepts Hexcasting flat screen creations and boots up the 3D hexagonal cage.
   * Never toggles off if already active so right-clicking with the staff can draw lines.
   */
  @SubscribeEvent
  public static void onScreenOpening(ScreenEvent.Opening event) {
    String screenClassName = event.getScreen().getClass().getName();
    
    if (screenClassName.contains("at.petrak.hexcasting.client.gui") || screenClassName.contains("PatternCastingScreen")) {
      event.setCanceled(true);

      if (HexagonalGridManager.isActive()) return;

      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) return;

      HexagonalGridManager.activateCastingGrid(mc.player);
      mc.level.playSeededSound(null, mc.player.getX(), mc.player.getY(), mc.player.getZ(), 
          SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.0f, mc.level.getRandom().nextLong());
    }
  }

  /**
   * Exits 3D casting mode cleanly when pressing ESC or the Inventory key.
   */
  @SubscribeEvent
  public static void onKeyInput(InputEvent.Key event) {
    if (!HexagonalGridManager.isActive()) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.screen != null || mc.player == null || mc.level == null) return;

    boolean isExitKey = event.getKey() == InputConstants.KEY_ESCAPE
        || mc.options.keyInventory.matches(event.getKey(), event.getScanCode());

    if (isExitKey && event.getAction() == InputConstants.PRESS) {
      HexagonalGridManager.deactivateGrid();
      mc.level.playSeededSound(null, mc.player.getX(), mc.player.getY(), mc.player.getZ(), 
          SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.6f, mc.level.getRandom().nextLong());
    }
  }
}
