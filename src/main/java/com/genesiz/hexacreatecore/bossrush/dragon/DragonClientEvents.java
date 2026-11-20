package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.HexaCreateCore;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class DragonClientEvents {

  private DragonClientEvents() {}

  @SubscribeEvent
  public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
    HexaCreateCore.LOGGER.info("[DragonClientEvents] Registering MhEnderDragonRenderer for ENDER_DRAGON");
    event.registerEntityRenderer(EntityType.ENDER_DRAGON, MhEnderDragonRenderer::create);
  }
}
