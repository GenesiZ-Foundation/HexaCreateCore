package com.genesiz.hexacreatecore.mixins;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("deprecation")
@Mixin(ItemProperties.class)
public abstract class ItemPropertiesMixin {

  @Shadow @Final @Mutable
  private static Map<Item, Map<ResourceLocation, ItemPropertyFunction>> PROPERTIES;

  @Inject(method = "<clinit>", at = @At("TAIL"))
  private static void hexacreatecore$makePropertiesThreadSafe(CallbackInfo ci) {
    PROPERTIES = Collections.synchronizedMap(new HashMap<>(PROPERTIES));
  }
}
