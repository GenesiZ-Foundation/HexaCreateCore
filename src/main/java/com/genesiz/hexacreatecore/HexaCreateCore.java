package com.genesiz.hexacreatecore;

import org.slf4j.Logger;

import com.genesiz.hexacreatecore.datagen.ModModelProvider;
import com.genesiz.hexacreatecore.entity.ModEntities;
import com.genesiz.hexacreatecore.item.HexaCreateCoreItems;
import com.mojang.logging.LogUtils;
import com.genesiz.hexacreatecore.item.ModCreativeModeTabs;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
//import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
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
