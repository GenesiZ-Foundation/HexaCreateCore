package com.genesiz.hexacreatecore.mixins;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.bossrush.client.BossRushClientHud;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

  private static Field volumeField;

  @Inject(method = "play", at = @At("HEAD"), cancellable = true)
  private void hexacreatecore$interceptPlay(SoundInstance sound, CallbackInfo ci) {
    if (shouldCancelOrSanitize(sound)) {
      ci.cancel();
    }
  }

  @Inject(method = "queueTickingSound", at = @At("HEAD"), cancellable = true)
  private void hexacreatecore$interceptTicking(TickableSoundInstance sound, CallbackInfo ci) {
    if (shouldCancelOrSanitize(sound)) {
      ci.cancel();
    }
  }

  private static boolean shouldCancelOrSanitize(SoundInstance sound) {
    if (sound == null) return false;
    ResourceLocation loc = sound.getLocation();
    if (loc == null) return false;
    if (HexaCreateCore.MODID.equals(loc.getNamespace())) return false;

    String ns = loc.getNamespace().toLowerCase();
    String path = loc.getPath().toLowerCase();
    SoundSource src = sound.getSource();

    // 1. Nuclear Boss Rush music & Wither: Reincarnated loop/music blocker
    if (BossRushClientHud.isBossRushActive()) {
      if (src == SoundSource.MUSIC || src == SoundSource.RECORDS) return true;
      if (path.contains("music") || path.contains("theme") || path.contains("bgm") || path.contains("ost")) return true;
      if ("witherreincarnated".equals(ns) && (path.contains("wither_intro") || path.contains("wither_outro")
      || path.contains("wither_finale") || path.endsWith("music.wither") || path.contains("loop"))) {
        return true;
      }
    }

    // 2. Always sanitize Wither: Reincarnated's extreme volume=10.0f looping instances so OpenAL/SoundPhysics never mutes 3D audio
    if ("witherreincarnated".equals(ns) && sound instanceof AbstractSoundInstance abs) {
      try {
        if (volumeField == null) {
          volumeField = AbstractSoundInstance.class.getDeclaredField("volume");
          volumeField.setAccessible(true);
        }
        float v = volumeField.getFloat(abs);
        if (v > 1.25f) {
          volumeField.setFloat(abs, 1.0f);
        }
      } catch (Exception ignored) {
      }
    }

    return false;
  }
}
