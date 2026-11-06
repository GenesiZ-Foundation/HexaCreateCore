package com.genesiz.hexacreatecore.event;

import com.genesiz.hexacreatecore.entity.ModEntities;
import com.genesiz.hexacreatecore.entity.custom.CursedBlacksmithEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber
public class ModEvents {

  @SubscribeEvent
  public static void registerAttributes(EntityAttributeCreationEvent event) {
    event.put(ModEntities.CURSED_BLACKSMITH.get(), CursedBlacksmithEntity.createMobAttributes().build());
  }
}
