package com.genesiz.hexacreatecore.bossrush.client;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.bossrush.data.BossHealthBarData.HealthBarStyle;
import com.genesiz.hexacreatecore.bossrush.data.BossRushReloadListener;
import com.genesiz.hexacreatecore.bossrush.network.BossRushSyncPayload;
import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class BossRushClientHud {
  private static boolean bossRushActive = false;
  private static int activeBossEntityId = -1;
  private static String currentMusicId = "";
  private static final Map<String, HealthBarStyle> CLIENT_HEALTH_BARS = new HashMap<>();

  private static String phaseName = "";
  private static String fullDescription = "";
  private static int revealedChars = 0;
  private static long bannerStartMs = 0L;
  private static final long BANNER_DURATION_MS = 7000L;
  private static final long BANNER_SLIDE_MS = 750L;

  private static String musicTitle = "";
  private static long musicToastStartMs = 0L;
  private static final long MUSIC_TOAST_DURATION_MS = 5000L;
  private static final long MUSIC_TOAST_SLIDE_MS = 750L;
  private static SoundInstance activeMusicInstance = null;

  private static String statusBanner = "";
  private static int statusBannerTicks = 0;

  private static boolean hasCameraHistory = false;
  private static float lastCamYaw = 0.0f;
  private static float lastCamPitch = 0.0f;
  private static float lagOffsetX = 0.0f;
  private static float lagOffsetY = 0.0f;

  public static boolean isBossRushActive() {
    return bossRushActive;
  }

  private static LivingEntity resolveDisplayedBoss(Minecraft mc) {
    if (mc.level == null || mc.player == null || CLIENT_HEALTH_BARS.isEmpty()) return null;

    if (bossRushActive && activeBossEntityId != -1) {
      Entity target = mc.level.getEntity(activeBossEntityId);
      if (target instanceof LivingEntity boss && !boss.isRemoved() && boss.isAlive()) {
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType());
        if (CLIENT_HEALTH_BARS.containsKey(typeId.toString())) {
          return boss;
        }
      }
    }

    LivingEntity nearestGlobalBoss = null;
    double bestDistSq = 48.0D * 48.0D;

    for (Entity entity : mc.level.entitiesForRendering()) {
      if (!(entity instanceof LivingEntity living) || living == mc.player || !living.isAlive()) continue;
      ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
      HealthBarStyle style = CLIENT_HEALTH_BARS.get(typeId.toString());
      if (style != null && !style.bossRushOnly()) {
        double distSq = mc.player.distanceToSqr(living);
        if (bestDistSq > distSq) {
          bestDistSq = distSq;
          nearestGlobalBoss = living;
        }
      }
    }

    return nearestGlobalBoss;
  }

  public static boolean shouldBlockExternalSound(SoundInstance sound) {
    if (!bossRushActive || sound == null || sound == activeMusicInstance) return false;

    ResourceLocation loc = sound.getLocation();
    if (HexaCreateCore.MODID.equals(loc.getNamespace())) return false;

    SoundSource source = sound.getSource();
    String ns = loc.getNamespace().toLowerCase();
    String path = loc.getPath().toLowerCase();

    if (source == SoundSource.MUSIC || source == SoundSource.RECORDS) return true;
    if (sound.isLooping() || sound.getAttenuation() == SoundInstance.Attenuation.NONE) return true;
    if (path.contains("music") || path.contains("theme") || path.contains("bgm") || path.contains("ost")) return true;
    if (ns.contains("wither") && (path.contains("boss") || path.contains("fight") || path.contains("phase"))) return true;

    return false;
  }

  public static void handleSyncPacket(BossRushSyncPayload payload) {
    HexaCreateCore.LOGGER.info("[HealthBarDebug] Client received healthBarsJson: {}", payload.healthBarsJson());

    Minecraft mc = Minecraft.getInstance();

    if (payload.healthBarsJson() != null && !payload.healthBarsJson().isEmpty() && !"{}".equals(payload.healthBarsJson())) {
      try {
        JsonObject parsed = JsonParser.parseString(payload.healthBarsJson()).getAsJsonObject();
        CLIENT_HEALTH_BARS.clear();
        CLIENT_HEALTH_BARS.putAll(BossRushReloadListener.parseHealthBarsMap(parsed));
      } catch (Exception e) {
        HexaCreateCore.LOGGER.error("Failed parsing synced health_bars.json on client", e);
      }
    }

    if (!payload.totemItemId().isEmpty() && mc.gameRenderer != null) {
      ResourceLocation itemLoc = ResourceLocation.tryParse(payload.totemItemId());
      if (itemLoc != null && BuiltInRegistries.ITEM.containsKey(itemLoc)) {
        Item item = BuiltInRegistries.ITEM.get(itemLoc);
        mc.gameRenderer.displayItemActivation(new ItemStack(item));
        if (mc.player != null && mc.particleEngine != null) {
          mc.particleEngine.createTrackingEmitter(mc.player, ParticleTypes.TOTEM_OF_UNDYING, 30);
        }
      }
    }

    if (!payload.totemSoundId().isEmpty() && mc.player != null) {
      ResourceLocation soundLoc = payload.totemSoundId().contains(":")
      ? ResourceLocation.tryParse(payload.totemSoundId())
      : ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, payload.totemSoundId());
      if (soundLoc != null) {
        SoundEvent se = BuiltInRegistries.SOUND_EVENT.containsKey(soundLoc)
        ? BuiltInRegistries.SOUND_EVENT.get(soundLoc)
        : SoundEvent.createVariableRangeEvent(soundLoc);
        mc.player.playSound(se, 1.0f, 1.0f);
      }
    }

    if (!payload.statusBanner().isEmpty()) {
      statusBanner = payload.statusBanner();
      statusBannerTicks = 80;
    }

    bossRushActive = payload.active();

    if (!payload.active()) {
      activeBossEntityId = -1;
      currentMusicId = "";
      phaseName = "";
      fullDescription = "";
      bannerStartMs = 0L;

      if (!payload.musicId().isEmpty()) {
        playBossRushMusic(payload.musicId(), false);
        musicTitle = payload.musicTitle().isEmpty() ? payload.musicId() : payload.musicTitle();
        musicToastStartMs = System.currentTimeMillis();
      } else {
        stopActiveMusic();
        musicToastStartMs = 0L;
      }
      return;
    }

    if (payload.activeBossEntityId() != -1) {
      activeBossEntityId = payload.activeBossEntityId();
    }

    if (!payload.phaseName().isEmpty()) {
      phaseName = payload.phaseName();
      fullDescription = payload.phaseDescription();
      revealedChars = 0;
      bannerStartMs = System.currentTimeMillis();
    }

    if (!payload.musicId().isEmpty()) {
      playBossRushMusic(payload.musicId(), true);
      musicTitle = payload.musicTitle().isEmpty() ? payload.musicId() : payload.musicTitle();
      musicToastStartMs = System.currentTimeMillis();
    }
  }

  private static void playBossRushMusic(String musicName) {
    playBossRushMusic(musicName, true);
  }

  private static void playBossRushMusic(String musicName, boolean loop) {
    stopActiveMusic();
    currentMusicId = loop ? musicName : "";
    Minecraft mc = Minecraft.getInstance();

    mc.getSoundManager().stop(null, SoundSource.MUSIC);
    mc.getSoundManager().stop(null, SoundSource.RECORDS);

    ResourceLocation soundLoc = ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "boss_rush.music." + musicName);
    SoundEvent event = SoundEvent.createVariableRangeEvent(soundLoc);

    activeMusicInstance = new SimpleSoundInstance(
      event.getLocation(), SoundSource.MASTER, 1.0f, 1.0f,
      SoundInstance.createUnseededRandom(), loop, 0,
      SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true
    );
    mc.getSoundManager().play(activeMusicInstance);
  }

  public static void stopActiveMusic() {
    if (activeMusicInstance != null) {
      Minecraft.getInstance().getSoundManager().stop(activeMusicInstance);
      activeMusicInstance = null;
    }
  }

  @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
  public static void onRenderVanillaBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
    if (hasActiveCustomBossBar()) {
      event.setCanceled(true);
    }
  }

  @SubscribeEvent
  public static void onPlaySound(PlaySoundEvent event) {
    if (shouldBlockExternalSound(event.getSound())) {
      event.setSound(null);
    }
  }

  @SubscribeEvent
  public static void onClientTick(ClientTickEvent.Post event) {
    Minecraft mc = Minecraft.getInstance();

    if (bannerStartMs > 0L && (BANNER_DURATION_MS > (System.currentTimeMillis() - bannerStartMs))) {
      if (fullDescription.length() > revealedChars) {
        revealedChars = Math.min(fullDescription.length(), revealedChars + 2);
        if (mc.player != null && revealedChars % 4 == 0) {
          mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.15f, 1.8f);
        }
      }
    }

    if (statusBannerTicks > 0) statusBannerTicks--;

    if (bossRushActive && mc.player != null) {
      if (!currentMusicId.isEmpty() && mc.player.tickCount % 20 == 0) {
        if (activeMusicInstance == null || !mc.getSoundManager().isActive(activeMusicInstance)) {
          playBossRushMusic(currentMusicId);
        }
      }

      if (mc.player.tickCount % 10 == 0) {
        try {
          Object soundEngine = null;
          for (Field f : mc.getSoundManager().getClass().getDeclaredFields()) {
            if (f.getType().getSimpleName().equals("SoundEngine")) {
              f.setAccessible(true);
              soundEngine = f.get(mc.getSoundManager());
              break;
            }
          }
          if (soundEngine != null) {
            for (Field f : soundEngine.getClass().getDeclaredFields()) {
              if (Map.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                Object mapObj = f.get(soundEngine);
                if (mapObj instanceof Map<?, ?> map) {
                  for (Object key : new ArrayList<>(map.keySet())) {
                    if (key instanceof SoundInstance si && si != activeMusicInstance && shouldBlockExternalSound(si)) {
                      mc.getSoundManager().stop(si);
                    }
                  }
                }
              }
            }
          }
        } catch (Throwable ignored) {}
      }
    }
  }


  @SubscribeEvent
  public static void onRenderGui(RenderGuiLayerEvent.Post event) {
    if (!event.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR)) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null) return;

    long nowMs = System.currentTimeMillis();
    long bannerElapsedMs = (bannerStartMs > 0L) ? (nowMs - bannerStartMs) : Long.MAX_VALUE;
    boolean isBannerVisible = (bannerStartMs > 0L) && (BANNER_DURATION_MS > bannerElapsedMs);

    long musicElapsedMs = (musicToastStartMs > 0L) ? (nowMs - musicToastStartMs) : Long.MAX_VALUE;
    boolean isMusicToastVisible = (musicToastStartMs > 0L) && (MUSIC_TOAST_DURATION_MS > musicElapsedMs) && !musicTitle.isEmpty();

    LivingEntity displayedBoss = resolveDisplayedBoss(mc);
    boolean anyVisible = (displayedBoss != null) || isBannerVisible || isMusicToastVisible || (statusBannerTicks > 0);
    if (!anyVisible) {
      hasCameraHistory = false;
      lagOffsetX = 0.0f;
      lagOffsetY = 0.0f;
      return;
    }

    float currentYaw = mc.player.getYRot();
    float currentPitch = mc.player.getXRot();
    if (!hasCameraHistory) {
      lastCamYaw = currentYaw;
      lastCamPitch = currentPitch;
      hasCameraHistory = true;
    } else {
      float deltaYaw = Mth.wrapDegrees(currentYaw - lastCamYaw);
      float deltaPitch = currentPitch - lastCamPitch;
      lastCamYaw = currentYaw;
      lastCamPitch = currentPitch;

      lagOffsetX = Mth.clamp(lagOffsetX - (deltaYaw * 0.45f), -24.0f, 24.0f);
      lagOffsetY = Mth.clamp(lagOffsetY - (deltaPitch * 0.45f), -16.0f, 16.0f);

      float dt = mc.getTimer().getRealtimeDeltaTicks() / 20.0f;
      double linearAlpha = Math.min(1.0D, dt * 3.0D);
      float sineEase = (float) Math.sin(linearAlpha * (Math.PI / 2.0D));

      lagOffsetX = Mth.lerp(sineEase, lagOffsetX, 0.0f);
      lagOffsetY = Mth.lerp(sineEase, lagOffsetY, 0.0f);
    }

    Font font = mc.font;
    int screenWidth = mc.getWindow().getGuiScaledWidth();
    int screenHeight = mc.getWindow().getGuiScaledHeight();
    float screenCenterX = screenWidth * 0.5f;

    PoseStack poseStack = event.getGuiGraphics().pose();
    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();

    if (displayedBoss != null) {
      ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(displayedBoss.getType());
      HealthBarStyle barStyle = CLIENT_HEALTH_BARS.get(typeId.toString());
      if (barStyle != null) {
        BossHealthBarRenderer.renderCustomBossBar(
          event.getGuiGraphics(), bufferSource, font, screenWidth, displayedBoss, barStyle, lagOffsetX, lagOffsetY
        );
      }
    }

    if (isBannerVisible && !phaseName.isEmpty()) {
      String styledName = "§6§l" + phaseName;
      int maxTitleWidth = Math.min(260, screenWidth - 40);
      List<FormattedCharSequence> lines = font.split(Component.literal(styledName), maxTitleWidth - 20);

      int widestTitle = 0;
      for (FormattedCharSequence seq : lines) {
        widestTitle = Math.max(widestTitle, font.width(seq));
      }

      float boxWidth = widestTitle + 24.0f;
      float boxHeight = (lines.size() * font.lineHeight) + 12.0f;

      long bannerRemainingMs = BANNER_DURATION_MS - bannerElapsedMs;
      float slideProgress = 1.0f;
      if (BANNER_SLIDE_MS > bannerElapsedMs) {
        float t = (float) bannerElapsedMs / (float) BANNER_SLIDE_MS;
        slideProgress = (float) Math.sin(t * (Math.PI / 2.0D));
      } else if (BANNER_SLIDE_MS > bannerRemainingMs) {
        float t = (float) bannerRemainingMs / (float) BANNER_SLIDE_MS;
        slideProgress = (float) Math.sin(t * (Math.PI / 2.0D));
      }

      float targetCenterY = 48.0f + (boxHeight * 0.5f);
      float offscreenCenterY = -(boxHeight * 0.5f) - 10.0f;
      float centerX = screenCenterX + lagOffsetX;
      float centerY = Mth.lerp(slideProgress, offscreenCenterY, targetCenterY) + lagOffsetY;

      RawGeometry.drawDialogueNode(
        poseStack, bufferSource, font,
        centerX, centerY, 0.0f,
        boxWidth, boxHeight, 3.0f, 3.0f,
        0xCC110E1A, 0xFFFFAA00, lines, true
      );
    }

    if (isMusicToastVisible) {
      String styledMusic = "§b♪ §f" + musicTitle;
      List<FormattedCharSequence> lines = font.split(Component.literal(styledMusic), 220);

      int widestMusic = 0;
      for (FormattedCharSequence seq : lines) {
        widestMusic = Math.max(widestMusic, font.width(seq));
      }

      float boxWidth = widestMusic + 22.0f;
      float boxHeight = (lines.size() * font.lineHeight) + 12.0f;

      long remainingMs = MUSIC_TOAST_DURATION_MS - musicElapsedMs;
      float slideProgress = 1.0f;
      if (MUSIC_TOAST_SLIDE_MS > musicElapsedMs) {
        float t = (float) musicElapsedMs / (float) MUSIC_TOAST_SLIDE_MS;
        slideProgress = (float) Math.sin(t * (Math.PI / 2.0D));
      } else if (MUSIC_TOAST_SLIDE_MS > remainingMs) {
        float t = (float) remainingMs / (float) MUSIC_TOAST_SLIDE_MS;
        slideProgress = (float) Math.sin(t * (Math.PI / 2.0D));
      }

      float targetCenterX = screenWidth - (boxWidth * 0.5f) - 10.0f;
      float offscreenCenterX = screenWidth + (boxWidth * 0.5f) + 10.0f;
      float centerX = Mth.lerp(slideProgress, offscreenCenterX, targetCenterX) + (lagOffsetX * 0.5f);
      float centerY = 12.0f + (boxHeight * 0.5f) + (lagOffsetY * 0.5f);

      RawGeometry.drawDialogueNode(
        poseStack, bufferSource, font,
        centerX, centerY, 0.0f,
        boxWidth, boxHeight, 3.0f, 3.0f,
        0xCC110E1A, 0xFF21B7D1, lines, true
      );
    }

    if (statusBannerTicks > 0 && !statusBanner.isEmpty()) {
      List<FormattedCharSequence> lines = font.split(Component.literal(statusBanner), 240);
      int widestStatus = 0;
      for (FormattedCharSequence seq : lines) {
        widestStatus = Math.max(widestStatus, font.width(seq));
      }

      float boxWidth = widestStatus + 24.0f;
      float boxHeight = (lines.size() * font.lineHeight) + 12.0f;
      float centerX = screenCenterX + lagOffsetX;
      float centerY = 48.0f + (boxHeight * 0.5f) + lagOffsetY;

      RawGeometry.drawDialogueNode(
        poseStack, bufferSource, font,
        centerX, centerY, 0.0f,
        boxWidth, boxHeight, 3.0f, 3.0f,
        0xCC110E1A, 0xFFB233FF, lines, true
      );
    }

    if (isBannerVisible && revealedChars > 0) {
      String rawVisible = fullDescription.substring(0, revealedChars);
      String styledDesc = "§e" + rawVisible;
      int maxBoxWidth = Math.min(260, screenWidth - 40);
      List<FormattedCharSequence> descLines = font.split(Component.literal(styledDesc), maxBoxWidth - 20);

      int widestLine = 0;
      for (FormattedCharSequence seq : descLines) {
        widestLine = Math.max(widestLine, font.width(seq));
      }

      float descBoxWidth = widestLine + 24.0f;
      float descBoxHeight = (descLines.size() * font.lineHeight) + 12.0f;
      float descCenterX = screenCenterX + lagOffsetX;
      float descCenterY = (screenHeight - 50.0f - (descBoxHeight * 0.5f)) + lagOffsetY;

      RawGeometry.drawDialogueNode(
        poseStack, bufferSource, font,
        descCenterX, descCenterY, 0.0f,
        descBoxWidth, descBoxHeight, 3.0f, 3.0f,
        0xCC110E1A, 0xFF8A4FFF, descLines, true
      );
    }

    bufferSource.endBatch(RawGeometry.DIALOGUE_SOLID);
    bufferSource.endBatch();
  }

  @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
  public static void onPreRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
    if (!hasActiveCustomBossBar()) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.player != null && mc.player.tickCount % 60 == 0) {
      HexaCreateCore.LOGGER.info("[BossBarLayerProbe] Active GuiLayer: {}", event.getName());
    }

    ResourceLocation layerName = event.getName();
    String ns = layerName.getNamespace().toLowerCase();
    String path = layerName.getPath().toLowerCase();

    if (HexaCreateCore.MODID.equals(ns) || "minecraft".equals(ns) || "jade".equals(ns)) {
      if (VanillaGuiLayers.BOSS_OVERLAY.equals(layerName)) {
        event.setCanceled(true);
      }
      return;
    }

    // Cancel any external mod layer related to boss bars / custom boss HUDs
    if (ns.contains("legendary") || ns.contains("bosses") || ns.contains("wither")
    || path.contains("boss") || path.contains("health_bar") || path.contains("bossbar")) {
      event.setCanceled(true);
    }
  }

  public static boolean hasActiveCustomBossBar() {
    return resolveDisplayedBoss(Minecraft.getInstance()) != null;
  }

}
