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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
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
  private static int bannerDisplayTicks = 0;

  private static String musicTitle = "";
  private static int musicToastTicks = 0;
  private static SoundInstance activeMusicInstance = null;

  private static String statusBanner = "";
  private static int statusBannerTicks = 0;

  public static void handleSyncPacket(BossRushSyncPayload payload) {
    Minecraft mc = Minecraft.getInstance();

    if (!payload.totemItemId().isEmpty() && mc.gameRenderer != null) {
      ResourceLocation itemLoc = ResourceLocation.tryParse(payload.totemItemId());
      if (itemLoc != null && BuiltInRegistries.ITEM.containsKey(itemLoc)) {
        Item item = BuiltInRegistries.ITEM.get(itemLoc);
        mc.gameRenderer.displayItemActivation(new ItemStack(item));
        if (mc.player != null && mc.particleEngine != null) {
          mc.particleEngine.createTrackingEmitter(mc.player, net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, 30);
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
      bannerDisplayTicks = 0;
      return;
    }

    phaseName = payload.phaseName();
    fullDescription = payload.phaseDescription();
    revealedChars = 0;
    bannerDisplayTicks = 140;

    if (!payload.musicId().isEmpty()) {
      playBossRushMusic(payload.musicId());
      musicTitle = payload.musicTitle().isEmpty() ? payload.musicId() : payload.musicTitle();
      musicToastTicks = 100;
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
    if (bannerDisplayTicks > 0) {
      bannerDisplayTicks--;
      if (revealedChars < fullDescription.length()) {
        revealedChars = Math.min(fullDescription.length(), revealedChars + 2);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && revealedChars % 4 == 0) {
          mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.15f, 1.8f);
        }
      }
    }

    if (musicToastTicks > 0) musicToastTicks--;
    if (statusBannerTicks > 0) statusBannerTicks--;
  }

  @SubscribeEvent
  public static void onRenderGui(RenderGuiLayerEvent.Post event) {
    if (!event.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR)) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null) return;

    Font font = mc.font;
    int screenWidth = mc.getWindow().getGuiScaledWidth();
    int screenHeight = mc.getWindow().getGuiScaledHeight();
    PoseStack poseStack = event.getGuiGraphics().pose();
    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();

    if (bannerDisplayTicks > 0 && !phaseName.isEmpty()) {
      String styledName = "§6§l" + phaseName;
      int textWidth = font.width(styledName);
      float boxWidth = Math.max(140.0f, textWidth + 28.0f);
      float boxHeight = font.lineHeight + 14.0f;
      float boxX = (screenWidth - boxWidth) / 2.0f;
      float boxY = 14.0f;

      List<FormattedCharSequence> lines = font.split(Component.literal(styledName), (int) boxWidth - 16);
      RawGeometry.drawDialogueNode(
        poseStack, bufferSource, font,
        boxX, boxY, 0.0f,
        boxWidth, boxHeight, 3.0f, 3.0f,
        0xCC110E1A, 0xFFFFAA00, lines, false
      );
    }

    if (musicToastTicks > 0 && !musicTitle.isEmpty()) {
      String styledMusic = "§b♪ §f" + musicTitle;
      int textWidth = font.width(styledMusic);
      float boxWidth = textWidth + 24.0f;
      float boxHeight = font.lineHeight + 12.0f;
      float boxX = screenWidth - boxWidth - 12.0f;
      float boxY = 12.0f;

      List<FormattedCharSequence> lines = font.split(Component.literal(styledMusic), (int) boxWidth - 12);
      RawGeometry.drawDialogueNode(
        poseStack, bufferSource, font,
        boxX, boxY, 0.0f,
        boxWidth, boxHeight, 3.0f, 3.0f,
        0xCC110E1A, 0xFF21B7D1, lines, false
      );
    }

    if (statusBannerTicks > 0 && !statusBanner.isEmpty()) {
      int textWidth = font.width(statusBanner);
      float boxWidth = Math.max(150.0f, textWidth + 28.0f);
      float boxHeight = font.lineHeight + 14.0f;
      float boxX = (screenWidth - boxWidth) / 2.0f;
      float boxY = 14.0f;

      List<FormattedCharSequence> lines = font.split(Component.literal(statusBanner), (int) boxWidth - 16);
      RawGeometry.drawDialogueNode(
        poseStack, bufferSource, font,
        boxX, boxY, 0.0f,
        boxWidth, boxHeight, 3.0f, 3.0f,
        0xCC110E1A, 0xFFB233FF, lines, false
      );
    }

    bufferSource.endBatch(RawGeometry.DIALOGUE_SOLID);
    bufferSource.endBatch();

    if (bannerDisplayTicks > 0 && revealedChars > 0) {
      String visibleDesc = "§e" + fullDescription.substring(0, revealedChars);
      int descWidth = font.width(visibleDesc);
      int descX = (screenWidth - descWidth) / 2;
      int descY = screenHeight - 54;
      event.getGuiGraphics().drawString(font, visibleDesc, descX, descY, 0xFFFFFF, true);
    }
  }
}
