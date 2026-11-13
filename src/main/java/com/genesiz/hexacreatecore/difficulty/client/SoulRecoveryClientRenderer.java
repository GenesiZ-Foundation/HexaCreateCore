package com.genesiz.hexacreatecore.difficulty.client;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.difficulty.network.DifficultyPackets.SoulActionPayload;
import com.genesiz.hexacreatecore.difficulty.network.DifficultyPackets.SoulSyncPayload;
import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.genesiz.hexacreatecore.threedified.text.TextEffectStyle;
import com.genesiz.hexacreatecore.threedified.text.ThreeDifiedTextRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class SoulRecoveryClientRenderer {

  public static boolean inSoulForm = false;
  public static Vec3 bodyPos = Vec3.ZERO;
  public static Vec3 launchPos = Vec3.ZERO;
  public static int remainingTicks = 0;
  public static int maxTicks = 240;
  public static int fractureStacks = 0;

  private static Vec3 soulCamPos = Vec3.ZERO;
  private static Vec3 prevSoulCamPos = Vec3.ZERO;
  private static Vec3 soulVel = Vec3.ZERO;
  private static int launchSlideTicks = 0;
  private static int wallHitCooldownTicks = 0;
  private static long wallPenaltyBannerUntilMs = 0L;
  private static boolean reclaimSent = false;

  private static float inputForward = 0.0f;
  private static float inputStrafe = 0.0f;
  private static boolean inputJump = false;
  private static boolean inputSneak = false;

  private static final TextEffectStyle SOUL_ALERT_STYLE = TextEffectStyle.glitch(0xFF21B7D1);
  private static final TextEffectStyle WALL_PENALTY_STYLE = TextEffectStyle.glitch(0xFFFF1E56);
  private static final TextEffectStyle BODY_BEACON_STYLE = TextEffectStyle.spectrum();

  private SoulRecoveryClientRenderer() {}

  public static Vec3 getLerpedSoulCamPos(float pt) {
    return new Vec3(
      Mth.lerp(pt, prevSoulCamPos.x, soulCamPos.x),
      Mth.lerp(pt, prevSoulCamPos.y, soulCamPos.y),
      Mth.lerp(pt, prevSoulCamPos.z, soulCamPos.z)
    );
  }

  public static void handleSoulSync(SoulSyncPayload payload) {
    boolean wasInSoul = inSoulForm;
    inSoulForm = payload.inSoulForm();
    bodyPos = new Vec3(payload.bodyX(), payload.bodyY(), payload.bodyZ());
    launchPos = new Vec3(payload.launchX(), payload.launchY(), payload.launchZ());
    maxTicks = Math.max(1, payload.maxTicks());
    fractureStacks = payload.fractureStacks();

    if (inSoulForm && !wasInSoul) {
      Vec3 startEye = bodyPos.add(0.0D, 1.62D, 0.0D);
      soulCamPos = startEye;
      prevSoulCamPos = startEye;
      soulVel = Vec3.ZERO;
      launchSlideTicks = 0;
      wallHitCooldownTicks = 0;
      reclaimSent = false;
      remainingTicks = payload.remainingTicks();
    } else if (inSoulForm) {
      remainingTicks = Math.min(remainingTicks, payload.remainingTicks());
    } else {
      reclaimSent = false;
    }
  }

  @SubscribeEvent
  public static void onMovementInput(MovementInputUpdateEvent event) {
    if (!inSoulForm) return;
    var input = event.getInput();
    inputForward = input.forwardImpulse;
    inputStrafe = input.leftImpulse;
    inputJump = input.jumping;
    inputSneak = input.shiftKeyDown;

    input.forwardImpulse = 0.0f;
    input.leftImpulse = 0.0f;
    input.jumping = false;
    input.shiftKeyDown = true;
  }

  @SubscribeEvent
  public static void onClientTick(ClientTickEvent.Post event) {
    if (!inSoulForm) return;
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null || mc.level == null) return;

    prevSoulCamPos = soulCamPos;
    if (wallHitCooldownTicks > 0) wallHitCooldownTicks--;

    if (7 > launchSlideTicks) {
      launchSlideTicks++;
      float t = launchSlideTicks / 7.0f;
      double sineEase = Math.sin(t * (Math.PI / 2.0D));
      Vec3 startEye = bodyPos.add(0.0D, 1.62D, 0.0D);
      soulCamPos = startEye.lerp(launchPos, sineEase);
    } else {
      float yawRad = (float) Math.toRadians(mc.player.getYRot());
      float pitchRad = (float) Math.toRadians(mc.player.getXRot());
      double cosP = Math.cos(pitchRad);
      Vec3 forward = new Vec3(-Math.sin(yawRad) * cosP, -Math.sin(pitchRad), Math.cos(yawRad) * cosP);
      Vec3 right = new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad));

      double vert = (inputJump ? 1.0D : 0.0D) - (inputSneak ? 1.0D : 0.0D);
      Vec3 wishDir = forward.scale(inputForward).add(right.scale(inputStrafe)).add(0.0D, vert, 0.0D);
      if (wishDir.lengthSqr() > 1.0D) wishDir = wishDir.normalize();

      double speed = mc.options.keySprint.isDown() ? 0.95D : 0.65D;
      soulVel = soulVel.scale(0.68D).add(wishDir.scale(speed * 0.35D));

      boolean hitWall = false;
      Vec3 nextPos = soulCamPos;

      Vec3 tryX = new Vec3(nextPos.x + soulVel.x, nextPos.y, nextPos.z);
      if (isSolidBlockAt(mc, tryX)) {
        hitWall = true;
        soulVel = new Vec3(-soulVel.x * 0.4D, soulVel.y, soulVel.z);
      } else {
        nextPos = tryX;
      }

      Vec3 tryY = new Vec3(nextPos.x, nextPos.y + soulVel.y, nextPos.z);
      if (isSolidBlockAt(mc, tryY)) {
        hitWall = true;
        soulVel = new Vec3(soulVel.x, -soulVel.y * 0.4D, soulVel.z);
      } else {
        nextPos = tryY;
      }

      Vec3 tryZ = new Vec3(nextPos.x, nextPos.y, nextPos.z + soulVel.z);
      if (isSolidBlockAt(mc, tryZ)) {
        hitWall = true;
        soulVel = new Vec3(soulVel.x, soulVel.y, -soulVel.z * 0.4D);
      } else {
        nextPos = tryZ;
      }

      soulCamPos = nextPos;

      if (hitWall && wallHitCooldownTicks <= 0) {
        wallHitCooldownTicks = 12;
        remainingTicks = Math.max(0, remainingTicks - 40);
        wallPenaltyBannerUntilMs = System.currentTimeMillis() + 900L;
        PacketDistributor.sendToServer(new SoulActionPayload("WALL_HIT"));
      }
    }

    Vec3 bodyCenter = bodyPos.add(0.0D, 1.0D, 0.0D);
    if (!reclaimSent && launchSlideTicks >= 7 && soulCamPos.distanceToSqr(bodyCenter) <= 2.72D) {
      reclaimSent = true;
      PacketDistributor.sendToServer(new SoulActionPayload("RECLAIM_BODY"));
      return;
    }

    remainingTicks--;
    if (!reclaimSent && remainingTicks <= 0) {
      reclaimSent = true;
      PacketDistributor.sendToServer(new SoulActionPayload("EXPIRED"));
    }
  }

  private static boolean isSolidBlockAt(Minecraft mc, Vec3 pos) {
    BlockPos bp = BlockPos.containing(pos);
    return mc.level.getBlockState(bp).isSuffocating(mc.level, bp);
  }

  @SubscribeEvent
  public static void onRenderWorld(RenderLevelStageEvent event) {
    if (!inSoulForm) return;
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null) return;

    Vec3 camPos = event.getCamera().getPosition();
    PoseStack poseStack = event.getPoseStack();
    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
    int accent = DifficultyClientState.activeTier.accentColor;

    poseStack.pushPose();
    poseStack.translate(bodyPos.x - camPos.x, bodyPos.y - camPos.y + 1.95D, bodyPos.z - camPos.z);

    float faceYaw = (float) Math.toDegrees(Math.atan2(camPos.x - bodyPos.x, camPos.z - bodyPos.z)) + 180.0f;
    poseStack.mulPose(Axis.YP.rotationDegrees(faceYaw));
    poseStack.scale(-0.015f, -0.015f, -0.015f);

    String marker = "✦ RECLAIM YOUR BODY ✦";
    var boldMarker = Component.literal(marker).withStyle(Style.EMPTY.withBold(true));
    float mw = mc.font.width(boldMarker) + 24.0f;
    float mh = mc.font.lineHeight + 10.0f;
    RawGeometry.drawDialogueNode(
      poseStack, bufferSource, mc.font,
      0.0f, 0.0f, 0.0f, mw, mh, 3.0f, 3.0f,
      0xDD110E1A, accent, List.of(), false
    );
    poseStack.translate(0.0f, 0.0f, 3.5f);
    ThreeDifiedTextRenderer.draw3DBold(
      poseStack, bufferSource, mc.font, marker, -mc.font.width(boldMarker) * 0.5f, -mc.font.lineHeight * 0.5f, BODY_BEACON_STYLE
    );
    poseStack.popPose();
  }

  @SubscribeEvent
  public static void onRenderGui(RenderGuiEvent.Post event) {
    if (!inSoulForm) return;

    Minecraft mc = Minecraft.getInstance();
    Font font = mc.font;
    int screenW = mc.getWindow().getGuiScaledWidth();
    float centerX = screenW * 0.5f;

    boolean showWallPenalty = System.currentTimeMillis() < wallPenaltyBannerUntilMs;
    float secLeft = Math.max(0.0f, remainingTicks / 20.0f);
    String alert = showWallPenalty
    ? String.format("WALL IMPACT (-2.0s) — RETURN TO BODY (%.1fs)", secLeft)
    : String.format("SOUL SEVERED — FLY TO YOUR BODY (%.1fs) | WALLS COST -2.0s", secLeft);
    var boldAlert = Component.literal(alert).withStyle(Style.EMPTY.withBold(true));

    float boxW = font.width(boldAlert) + 28.0f;
    float boxH = font.lineHeight + 16.0f;
    float centerY = 38.0f;

    PoseStack poseStack = event.getGuiGraphics().pose();
    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
    int accent = showWallPenalty ? 0xFFFF1E56 : DifficultyClientState.activeTier.accentColor;

    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();

    RawGeometry.drawDialogueNode(
      poseStack, bufferSource, font,
      centerX, centerY, 0.0f, boxW, boxH, 3.0f, 3.0f,
      0xDD110E1A, accent, List.of(), true
    );

    bufferSource.endBatch(RawGeometry.DIALOGUE_SOLID);
    bufferSource.endBatch();
    RenderSystem.disableDepthTest();

    ThreeDifiedTextRenderer.draw2DBold(
      event.getGuiGraphics(), font, alert, centerX - (font.width(boldAlert) * 0.5f), centerY - (font.lineHeight * 0.5f) - 2.0f,
      showWallPenalty ? WALL_PENALTY_STYLE : SOUL_ALERT_STYLE
    );

    float pct = Mth.clamp((float) remainingTicks / (float) maxTicks, 0.0f, 1.0f);
    int barX0 = Math.round(centerX - (boxW * 0.5f) + 6.0f);
    int barX1 = Math.round(centerX + (boxW * 0.5f) - 6.0f);
    int barY = Math.round(centerY + (boxH * 0.5f) - 5.0f);
    int fillX = barX0 + Math.round((barX1 - barX0) * pct);
    event.getGuiGraphics().fill(barX0, barY, barX1, barY + 2, 0xFF1A1429);
    event.getGuiGraphics().fill(barX0, barY, fillX, barY + 2, accent);
  }
}
