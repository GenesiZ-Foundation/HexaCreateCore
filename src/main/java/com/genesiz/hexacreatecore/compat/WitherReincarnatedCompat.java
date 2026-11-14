package com.genesiz.hexacreatecore.compat;

import com.genesiz.hexacreatecore.HexaCreateCore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class WitherReincarnatedCompat {

  private static boolean enforcing = false;

  private WitherReincarnatedCompat() {}

  @SubscribeEvent
  public static void onModConfigLoad(ModConfigEvent.Loading event) {
    if ("witherreincarnated".equals(event.getConfig().getModId())) {
      enforceApiBoundMusicConfig();
    }
  }

  @SubscribeEvent
  public static void onModConfigReload(ModConfigEvent.Reloading event) {
    if ("witherreincarnated".equals(event.getConfig().getModId())) {
      enforceApiBoundMusicConfig();
    }
  }

  @SuppressWarnings("unchecked")
  public static void enforceApiBoundMusicConfig() {
    if (enforcing || !ModList.get().isLoaded("witherreincarnated")) return;
    enforcing = true;
    try {
      Class<?> cfgClass = Class.forName("com.alexander.witherreincarnated.config.WRClientConfig");
      ModConfigSpec.ConfigValue<Double> musicVol = (ModConfigSpec.ConfigValue<Double>) cfgClass.getField("music_volume").get(null);
      ModConfigSpec.ConfigValue<Boolean> oneTrack = (ModConfigSpec.ConfigValue<Boolean>) cfgClass.getField("only_play_one_music_track").get(null);
      ModConfigSpec spec = (ModConfigSpec) cfgClass.getField("SPEC").get(null);

      boolean changed = false;
      if (musicVol.get() != 0.0D) {
        musicVol.set(0.0D);
        changed = true;
      }
      if (oneTrack.get()) {
        oneTrack.set(false);
        changed = true;
      }
      if (changed) {
        spec.save();
      }

      annotateTomlFile();
      HexaCreateCore.LOGGER.info("[HexaCreateCore] Bound Wither: Reincarnated client music_volume = 0.0 (API bound).");
    } catch (Exception e) {
      HexaCreateCore.LOGGER.warn("[HexaCreateCore] Failed to bind Wither: Reincarnated client config", e);
    } finally {
      enforcing = false;
    }
  }

  private static void annotateTomlFile() {
    try {
      Path tomlPath = FMLPaths.CONFIGDIR.get().resolve("witherreincarnated-client.toml");
      if (!Files.exists(tomlPath)) return;

      String content = Files.readString(tomlPath, StandardCharsets.UTF_8);
      String tag = "# [API bound by HexaCreateCore: Managed by Boss Rush Audio Engine]";
      if (!content.contains(tag)) {
        content = content.replace("music_volume =", tag + "\nmusic_volume =");
        content = content.replace("only_play_one_music_track =", tag + "\nonly_play_one_music_track =");
        Files.writeString(tomlPath, content, StandardCharsets.UTF_8);
      }
    } catch (Exception ignored) {
    }
  }

  @EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
  public static final class ClientGuard {
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onScreenClose(net.neoforged.neoforge.client.event.ScreenEvent.Closing event) {
      enforceApiBoundMusicConfig();
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
      if ((++tickCounter % 20) == 0) {
        enforceApiBoundMusicConfig();
      }
    }
  }

}
