package com.genesiz.hexacreatecore.threedified;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.threedified.network.ThreeDifiedNotifyPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class ThreeDifiedNotificationHud {

  private static final long SLIDE_MS = 260L;

  private static String activeMessage = "";
  private static int activeBorderColor = 0xFF00FFAA;
  private static long startMs = 0L;
  private static long durationMs = 2500L;

  private static String cachedText = "";
  private static List<FormattedCharSequence> cachedLines = List.of();
  private static float cachedWidth = 0.0f;
  private static float cachedHeight = 0.0f;

  private ThreeDifiedNotificationHud() {}

  public static void show(String message) {
    show(message, inferBorderColor(message), 50);
  }

  public static void show(String message, int borderColor, int durationTicks) {
    if (message == null || message.isEmpty()) return;
    long now = System.currentTimeMillis();
    long nextDurationMs = Math.max(500L, durationTicks * 50L);

    // If the same message is continuously refreshed (e.g. looking at a ghost block), keep it smoothly open without re-triggering slide-in
    if (message.equals(activeMessage) && startMs > 0L && (now - startMs) < (durationMs - SLIDE_MS)) {
      activeBorderColor = borderColor;
      startMs = now - SLIDE_MS;
      durationMs = nextDurationMs + SLIDE_MS;
      return;
    }

    activeMessage = message;
    activeBorderColor = borderColor;
    startMs = now;
    durationMs = nextDurationMs;
  }

  public static void sendTo(ServerPlayer player, String message) {
    sendTo(player, message, inferBorderColor(message), 60);
  }

  public static void sendTo(ServerPlayer player, String message, int borderColor, int durationTicks) {
    PacketDistributor.sendToPlayer(player, new ThreeDifiedNotifyPayload(message, borderColor, durationTicks));
  }

  private static int inferBorderColor(String msg) {
    if (msg.contains("§c") || msg.contains("§4")) return 0xFFFF1E56;
    if (msg.contains("§d")) return 0xFFB233FF;
    if (msg.contains("§b")) return 0xFF21B7D1;
    if (msg.contains("§a")) return 0xFF10B981;
    return 0xFF00FFAA;
  }

  @SubscribeEvent
  public static void onRenderGui(RenderGuiLayerEvent.Post event) {
    if (!event.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR)) return;
    if (startMs <= 0L || activeMessage.isEmpty()) return;

    long now = System.currentTimeMillis();
    long elapsedMs = now - startMs;
    if (elapsedMs >= durationMs) {
      activeMessage = "";
      startMs = 0L;
      return;
    }

    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null) return;
    Font font = mc.font;

    if (!activeMessage.equals(cachedText)) {
      cachedText = activeMessage;
      cachedLines = font.split(Component.literal(activeMessage), 280);
      int longest = 0;
      for (FormattedCharSequence line : cachedLines) {
        longest = Math.max(longest, font.width(line));
      }
      cachedWidth = longest + 18.0f;
      cachedHeight = (cachedLines.size() * font.lineHeight) + 12.0f;
    }

    int screenW = mc.getWindow().getGuiScaledWidth();
    int screenH = mc.getWindow().getGuiScaledHeight();

    long remainingMs = durationMs - elapsedMs;
    float slide = 1.0f;
    if (SLIDE_MS > elapsedMs) {
      float t = (float) elapsedMs / (float) SLIDE_MS;
      slide = (float) Math.sin(t * (Math.PI / 2.0D));
    } else if (SLIDE_MS > remainingMs) {
      float t = (float) remainingMs / (float) SLIDE_MS;
      slide = (float) Math.sin(t * (Math.PI / 2.0D));
    }

    float centerX = screenW * 0.5f;
    float targetY = screenH - 54.0f - (cachedHeight * 0.5f);
    float offscreenY = screenH + (cachedHeight * 0.5f) + 10.0f;
    float centerY = Mth.lerp(slide, offscreenY, targetY);

    PoseStack poseStack = event.getGuiGraphics().pose();
    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();

    RawGeometry.drawDialogueNode(
      poseStack,
      bufferSource,
      font,
      centerX,
      centerY,
      0.0f,
      cachedWidth,
      cachedHeight,
      3.0f,
      3.0f,
      0xDD0D0814,
      activeBorderColor,
      cachedLines,
      true
    );

    bufferSource.endBatch(RawGeometry.DIALOGUE_SOLID);
    bufferSource.endBatch();
  }
}
