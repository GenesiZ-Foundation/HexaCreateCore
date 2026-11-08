package com.genesiz.hexacreatecore;

import org.slf4j.Logger;

import com.genesiz.hexacreatecore.datagen.ModModelProvider;
import com.genesiz.hexacreatecore.dialogue.data.DialogueReloadListener;
import com.genesiz.hexacreatecore.dialogue.hologram.BlueprintReloadListener;
import com.genesiz.hexacreatecore.dialogue.network.DialogueNetworking;
import com.genesiz.hexacreatecore.entity.ModEntities;
import com.genesiz.hexacreatecore.entity.custom.CursedBlacksmithEntity;
import com.genesiz.hexacreatecore.item.HexaCreateCoreItems;
import com.mojang.logging.LogUtils;
import com.genesiz.hexacreatecore.item.ModCreativeModeTabs;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.fml.ModContainer;

@Mod(HexaCreateCore.MODID)
public class HexaCreateCore {
  public static final String MODID = "hexacreatecore";
  public static final Logger LOGGER = LogUtils.getLogger();

  static {
    DialogueNetworking.registerAction("hexacreatecore:start_forging", (serverPlayer, npcEntity) -> {
      if (npcEntity instanceof CursedBlacksmithEntity) {
        serverPlayer.giveExperienceLevels(1);
      }
    });

    // EXAMPLE SHOW IN WORLD AT COORDINATES
    //DialogueNetworking.registerAction("hexacreatecore:show_tutorial", (serverPlayer, npcEntity) -> {
    //  BlockPos anchor = npcEntity.blockPosition().offset(3, 0, 0);
    //  net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
    //    serverPlayer,
    //    new HologramProjectionPayload("cobble_frame", anchor, 600)
    //  );
    //});
    DialogueNetworking.registerAction("hexacreatecore:show_tutorial", (serverPlayer, npcEntity) -> {
      DialogueNetworking.queueHologramPlacement(serverPlayer, "cobble_frame", 600);
    });

  }

  public HexaCreateCore(IEventBus modEventBus, ModContainer modContainer) {
    modEventBus.addListener(this::addCreative);
    modEventBus.addListener(this::gatherData);

    ModCreativeModeTabs.register(modEventBus);
    HexaCreateCoreItems.register(modEventBus);
    ModEntities.register(modEventBus);

    if (FMLEnvironment.dist.isClient()) {
      modEventBus.addListener(DialogueReloadListener::onRegisterClientReloadListeners);
      modEventBus.addListener(BlueprintReloadListener::onRegisterClientReloadListeners);
    }
  }

  private void gatherData(final GatherDataEvent event) {
    DataGenerator generator = event.getGenerator();
    PackOutput packOutput = generator.getPackOutput();
    ExistingFileHelper existingFileHelper = event.getExistingFileHelper();

    generator.addProvider(event.includeClient(), new ModModelProvider(packOutput, existingFileHelper));
  }

  private void addCreative(BuildCreativeModeTabContentsEvent event) {
  }
}
