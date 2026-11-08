package com.genesiz.hexacreatecore.entity;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.entity.custom.CursedBlacksmithEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModEntities {
  public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, HexaCreateCore.MODID);
  public static final ResourceKey<EntityType<?>> CURSED_BLACKSMITH_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "cursed_blacksmith"));

  public static final DeferredHolder<EntityType<?>, EntityType<CursedBlacksmithEntity>> CURSED_BLACKSMITH = 
  ENTITY_TYPES.register("cursed_blacksmith", () -> 
    EntityType.Builder.of(CursedBlacksmithEntity::new, MobCategory.MONSTER)
    .sized(1.5f, 1.5f)
    .build("cursed_blacksmith")
  );

  public static void register (IEventBus eventBus) {
    ENTITY_TYPES.register(eventBus);
  }
}
