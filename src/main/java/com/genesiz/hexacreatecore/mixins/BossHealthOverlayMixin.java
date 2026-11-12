package com.genesiz.hexacreatecore.mixins;

import com.genesiz.hexacreatecore.bossrush.client.BossRushClientHud;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {

  @Inject(method = "render", at = @At("HEAD"), cancellable = true)
  private void hexacreatecore$hideOverlayWhenCustomBarActive(GuiGraphics guiGraphics, CallbackInfo ci) {
    if (BossRushClientHud.hasActiveCustomBossBar()) {
      ci.cancel();
    }
  }
}
