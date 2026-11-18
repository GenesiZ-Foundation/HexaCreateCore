package com.genesiz.hexacreatecore.create.wonderum;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.item.ModItems;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class WonderumClientEvents {

  public static final ModelResourceLocation WONDERUM_BASE_MODEL = ModelResourceLocation.standalone(
    ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "item/wonderum_base")
  );

  private WonderumClientEvents() {}

  @SubscribeEvent
  public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
    event.register(WONDERUM_BASE_MODEL);
  }

  @SubscribeEvent
  public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
    event.registerItem(new IClientItemExtensions() {
      @Override
      public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return WonderumItemRenderer.INSTANCE;
      }
    }, ModItems.WONDERUM.get());
  }
}


