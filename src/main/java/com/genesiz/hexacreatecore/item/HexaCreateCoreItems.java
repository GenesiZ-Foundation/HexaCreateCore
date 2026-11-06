package com.genesiz.hexacreatecore.item;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.entity.ModEntities;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class HexaCreateCoreItems {
  public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HexaCreateCore.MODID);
  public static final DeferredItem<Item> DEBUGGER = ITEMS.register("debugger", ()-> new Item(new Item.Properties()));
  public static final DeferredItem<Item> CURSED_BLACKSMITH_SPAWN_EGG = ITEMS.register("cursed_blacksmith_spawn_egg", ()-> new DeferredSpawnEggItem(
    ModEntities.CURSED_BLACKSMITH,
    0x45413d,
    0x271b45,
    new Item.Properties()
  ));

  public static void register(IEventBus eventBus) {
    ITEMS.register(eventBus);
  }
}
