package com.genesiz.hexacreatecore.item;

import java.util.function.Supplier;

import com.genesiz.hexacreatecore.HexaCreateCore;

import net.neoforged.bus.api.IEventBus;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;;

public class ModCreativeModeTabs {
  public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HexaCreateCore.MODID);

  public static final Supplier<CreativeModeTab> HEXACREATECORE_TAB = CREATIVE_MODE_TAB.register("hexacreatecore_item_tab",
  () -> CreativeModeTab
    .builder()
    .icon(
    ()-> new ItemStack(HexaCreateCoreItems.DEBUGGER.get()))
    .title(Component.translatable("creativetab.hexacreatecore.mod_items"))
    .displayItems(
    (ItemDisplayParameters, output) -> {
        output.accept(HexaCreateCoreItems.DEBUGGER);
        output.accept(HexaCreateCoreItems.CURSED_BLACKSMITH_SPAWN_EGG);


    }).build()
  );

  public static void register(IEventBus eventBus) {
    CREATIVE_MODE_TAB.register(eventBus);
  }
}
