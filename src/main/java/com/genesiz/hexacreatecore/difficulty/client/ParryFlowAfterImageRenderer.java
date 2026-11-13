package com.genesiz.hexacreatecore.difficulty.client;
import com.genesiz.hexacreatecore.HexaCreateCore;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayDeque;
import java.util.Deque;

@SuppressWarnings({"rawtypes", "unchecked"})
@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class ParryFlowAfterImageRenderer {
  private record PoseSnapshot(
    long timestampMs, Vec3 pos, float bodyYaw, float headYaw,
    float headPitch, float walkPos, float walkSpeed, boolean crouching
  ) {}

  private static final Deque HISTORY = new ArrayDeque();
  private static final float[] OPACITIES = { 0.80f, 0.60f, 0.40f, 0.20f };
  private static final long[] SAMPLE_AGE_MS = { 45L, 90L, 135L, 180L };
  private static long lastCaptureMs = 0L;

  @SubscribeEvent
  public static void onRenderAfterImages(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
    Minecraft mc = Minecraft.getInstance();
    if (!(mc.player instanceof AbstractClientPlayer player)) return;
    if (!DifficultyClientState.isInParryFlow()) {
      if (!HISTORY.isEmpty()) HISTORY.clear();
      return;
    }

    long now = System.currentTimeMillis();
    float pt = event.getPartialTick().getGameTimeDeltaPartialTick(true);

    if ((now - lastCaptureMs) >= 16L) {
      lastCaptureMs = now;
      HISTORY.addFirst(new PoseSnapshot(
        now, player.getPosition(pt),
        Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot),
        Mth.rotLerp(pt, player.yHeadRotO, player.yHeadRot),
        Mth.lerp(pt, player.xRotO, player.getXRot()),
        player.walkAnimation.position(pt),
        player.walkAnimation.speed(pt),
        player.isCrouching()
      ));

      while (HISTORY.size() > 24) HISTORY.removeLast();
    }

    if (HISTORY.isEmpty()) return;
    if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer playerRenderer)) return;

    var model = playerRenderer.getModel();
    Vec3 camPos = event.getCamera().getPosition();
    PoseStack poseStack = event.getPoseStack();
    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();

    for (int i = 0; 4 > i; i++) {

      PoseSnapshot snap = findSnapshotAtAge(now - SAMPLE_AGE_MS[i]);

      if (snap == null) continue;
      if (mc.options.getCameraType().isFirstPerson() && 0.04D > snap.pos.distanceToSqr(player.getPosition(pt))) continue;

      poseStack.pushPose();
      poseStack.translate(snap.pos.x - camPos.x, snap.pos.y - camPos.y, snap.pos.z - camPos.z);
      poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - snap.bodyYaw));
      poseStack.scale(-0.9375f, -0.9375f, 0.9375f);
      poseStack.translate(0.0D, -1.501D, 0.0D);

      model.crouching = snap.crouching;
      model.young = false;
      model.setupAnim(player, snap.walkPos, snap.walkSpeed, player.tickCount + pt, snap.headYaw - snap.bodyYaw, snap.headPitch);

      int alpha = Math.round(OPACITIES[i] * 255.0f);
      int argb = FastColor.ARGB32.color(alpha, 76, 232, 255);
      VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));

      model.renderToBuffer(poseStack, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, argb);
      poseStack.popPose();
    }

    bufferSource.endBatch();
  }

  private static PoseSnapshot findSnapshotAtAge(long targetTimeMs) {
    PoseSnapshot closest = null;
    long bestDiff = Long.MAX_VALUE;
    for (Object obj : HISTORY) {
      PoseSnapshot snap = (PoseSnapshot) obj;
      long diff = Math.abs(snap.timestampMs - targetTimeMs);
      if (bestDiff > diff) {
        bestDiff = diff;
        closest = snap;
      }
    }
    return closest;
  }
}
