package com.genesiz.hexacreatecore;

import com.genesiz.hexacreatecore.entity.ModEntities;
import com.genesiz.hexacreatecore.entity.client.CursedBlacksmithRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = HexaCreateCore.MODID, dist = Dist.CLIENT)

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public class HexaCreateCoreClient {

  public HexaCreateCoreClient(ModContainer container) {
    container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
  }

  @SubscribeEvent
  static void onClientSetup(FMLClientSetupEvent event) {
    EntityRenderers.register(ModEntities.CURSED_BLACKSMITH.get(), CursedBlacksmithRenderer::new);

    event.enqueueWork(() -> {
      com.genesiz.hexacreatecore.compat.WitherReincarnatedCompat.enforceApiBoundMusicConfig();
    });
  }

  @SubscribeEvent
  public static void onClientTick(ClientTickEvent.Pre event) {
    Minecraft minecraft = Minecraft.getInstance();

    if (minecraft.level == null || minecraft.player == null) return;
  }
}
