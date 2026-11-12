package com.genesiz.hexacreatecore.mixins;

import com.genesiz.hexacreatecore.bossrush.client.BossRushClientHud;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

  @Inject(method = "play", at = @At("HEAD"), cancellable = true)
  private void hexacreatecore$blockExternalBossMusic(SoundInstance sound, CallbackInfo ci) {
    if (BossRushClientHud.shouldBlockExternalSound(sound)) {
      ci.cancel();
    }
  }
}
