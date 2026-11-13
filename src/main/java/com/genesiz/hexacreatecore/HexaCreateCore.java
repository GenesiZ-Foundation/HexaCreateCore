package com.genesiz.hexacreatecore;

import org.slf4j.Logger;
import com.genesiz.hexacreatecore.datagen.ModModelProvider;
import com.genesiz.hexacreatecore.threedified.data.DialogueReloadListener;
import com.genesiz.hexacreatecore.threedified.hologram.BlueprintReloadListener;
import com.genesiz.hexacreatecore.entity.ModEntities;
import com.genesiz.hexacreatecore.item.HexaCreateCoreItems;
import com.genesiz.hexacreatecore.item.ModCreativeModeTabs;
import com.genesiz.hexacreatecore.registry.ModEffects;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.fml.ModContainer;

@Mod(HexaCreateCore.MODID)
public class HexaCreateCore {
  public static final String MODID = "hexacreatecore";
  public static final Logger LOGGER = LogUtils.getLogger();

  public HexaCreateCore(IEventBus modEventBus, ModContainer modContainer) {
    modEventBus.addListener(this::addCreative);
    modEventBus.addListener(this::gatherData);

    ModCreativeModeTabs.register(modEventBus);
    HexaCreateCoreItems.register(modEventBus);
    ModEntities.register(modEventBus);
    ModEffects.register(modEventBus);

    if (FMLEnvironment.dist.isClient()) {
      modEventBus.addListener(DialogueReloadListener::onRegisterClientReloadListeners);
      modEventBus.addListener(BlueprintReloadListener::onRegisterClientReloadListeners);
      modEventBus.addListener(com.genesiz.hexacreatecore.animations.AnimationSequenceReloadListener::onRegisterClientReloadListeners);
      modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> {
        event.enqueueWork(com.genesiz.hexacreatecore.hexcasting.HexagonalGridManager::loadExternalGridConfig);
      });
    }
  }

  private void addCreative(BuildCreativeModeTabContentsEvent event) {
    // Hook for appending items directly to creative tabs dynamically
  }

  private void gatherData(final GatherDataEvent event) {
    event.getGenerator().addProvider(
      event.includeClient(), 
      new ModModelProvider(event.getGenerator().getPackOutput(), event.getExistingFileHelper())
    );
  }
}
