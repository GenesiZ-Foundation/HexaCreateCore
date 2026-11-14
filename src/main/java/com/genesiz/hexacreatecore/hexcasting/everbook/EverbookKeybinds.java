package com.genesiz.hexacreatecore.hexcasting.everbook;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class EverbookKeybinds {

  public static final KeyMapping DELETE_IOTA_KEY = new KeyMapping(
    "key.hexacreatecore.delete_iota",
    InputConstants.Type.KEYSYM,
    GLFW.GLFW_KEY_DELETE,
    "key.categories.hexacreatecore"
  );

  private EverbookKeybinds() {}

  @SubscribeEvent
  public static void registerKeys(RegisterKeyMappingsEvent event) {
    event.register(DELETE_IOTA_KEY);
  }
}
