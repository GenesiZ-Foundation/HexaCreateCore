package com.genesiz.hexacreatecore.bossrush.client;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.bossrush.network.BossRushSyncPayload;
import com.genesiz.hexacreatecore.dialogue.RawGeometry;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.List;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class BossRushClientHud {
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

  // Sine-eased camera inertia state (matching HexCastingGeometry.render3DStackHUD)
  private static boolean hasCameraHistory = false;
  private static float lastCamYaw = 0.0f;
  private static float lastCamPitch = 0.0f;
  private static float lagOffsetX = 0.0f;
  private static float lagOffsetY = 0.0f;

  public static void handleSyncPacket(BossRushSyncPayload payload) {
    Minecraft mc = Minecraft.getInstance();

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

    if (!payload.active()) {
      stopActiveMusic();
      phaseName = "";
      fullDescription = "";
      bannerStartMs = 0L;
      musicToastStartMs = 0L;
      return;
    }

    phaseName = payload.phaseName();
    fullDescription = payload.phaseDescription();
    revealedChars = 0;
    bannerStartMs = System.currentTimeMillis();


    if (!payload.musicId().isEmpty()) {
      playBossRushMusic(payload.musicId());
      musicTitle = payload.musicTitle().isEmpty() ? payload.musicId() : payload.musicTitle();
      musicToastStartMs = System.currentTimeMillis();
    }
  }

  private static void playBossRushMusic(String musicName) {
    stopActiveMusic();
    Minecraft mc = Minecraft.getInstance();
    ResourceLocation soundLoc = ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "boss_rush.music." + musicName);
    SoundEvent event = SoundEvent.createVariableRangeEvent(soundLoc);

    activeMusicInstance = new SimpleSoundInstance(
      event.getLocation(),
      SoundSource.RECORDS,
      1.0f,
      1.0f,
      SoundInstance.createUnseededRandom(),
      true,
      0,
      SoundInstance.Attenuation.NONE,
      0.0D, 0.0D, 0.0D,
      true
    );
    mc.getSoundManager().play(activeMusicInstance);
  }

  public static void stopActiveMusic() {
    if (activeMusicInstance != null) {
      Minecraft.getInstance().getSoundManager().stop(activeMusicInstance);
      activeMusicInstance = null;
    }
  }

  @SubscribeEvent
  public static void onClientTick(ClientTickEvent.Post event) {
    if (bannerStartMs > 0L && (BANNER_DURATION_MS > (System.currentTimeMillis() - bannerStartMs))) {
      if (fullDescription.length() > revealedChars) {
        revealedChars = Math.min(fullDescription.length(), revealedChars + 2);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && revealedChars % 4 == 0) {
          mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.15f, 1.8f);
        }
      }
    }

    if (statusBannerTicks > 0) statusBannerTicks--;
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

    boolean anyVisible = isBannerVisible || isMusicToastVisible || (statusBannerTicks > 0);

    if (!anyVisible) {
      hasCameraHistory = false;
      lagOffsetX = 0.0f;
      lagOffsetY = 0.0f;
      return;
    }

    // Sine-eased camera inertia matching HexCastingGeometry.render3DStackHUD
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

    // 1. Top-Center Phase Title centered directly below Jade's HUD
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

      float targetCenterY = 36.0f + (boxHeight * 0.5f);
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


    // 2. Top-Right Sliding Music Toast in Dialogue3D box
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

    // 3. Status Banner (Victory / Arena Exit / Death) centered directly below Jade's HUD
    if (statusBannerTicks > 0 && !statusBanner.isEmpty()) {
      List<FormattedCharSequence> lines = font.split(Component.literal(statusBanner), 240);
      int widestStatus = 0;
      for (FormattedCharSequence seq : lines) {
        widestStatus = Math.max(widestStatus, font.width(seq));
      }

      float boxWidth = widestStatus + 24.0f;
      float boxHeight = (lines.size() * font.lineHeight) + 12.0f;
      float centerX = screenCenterX + lagOffsetX;
      float centerY = 36.0f + (boxHeight * 0.5f) + lagOffsetY;

      RawGeometry.drawDialogueNode(
        poseStack, bufferSource, font,
        centerX, centerY, 0.0f,
        boxWidth, boxHeight, 3.0f, 3.0f,
        0xCC110E1A, 0xFFB233FF, lines, true
      );
    }

    // 4. Bottom Typewriter Description centered directly above the XP Bar
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
}
