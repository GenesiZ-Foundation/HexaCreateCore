package com.genesiz.hexacreatecore.hexcasting.everbook;

import com.genesiz.hexacreatecore.HexaCreateCore;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import punchy.api.PunchyApi;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PunchyEverbookCompat {
  private static final String CLOSED_CLIP = "everbook_player";
  private static final String OPEN_CLIP = "my_animation_2";

  @SubscribeEvent
  public static void onClientSetup(FMLClientSetupEvent event) {
    if (!ModList.get().isLoaded("punchy")) return;

    event.enqueueWork(PunchyEverbookCompat::registerHooks);
  }

  private static void registerHooks() {
    PunchyApi.remapClips(HexaCreateCore.MODID, (clipName, mainHand) -> {
      if (CLOSED_CLIP.equals(clipName)) {
        var player = Minecraft.getInstance().player;
        if (player != null && EverbookPlayerAnimController.isPlayerBookOpen(player)) {
          return OPEN_CLIP;
        }
      }
      return clipName;
    });
  }

  public static boolean isPunchyControllingBook() {
    return ModList.get().isLoaded("punchy") && PunchyApi.isActive();
  }
}
