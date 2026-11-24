package com.genesiz.hexacreatecore.create.wonderum.wormhole;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.BlackHoleDictionary;
import com.genesiz.hexacreatecore.threedified.shaders.hooks.PortalShaderHook;
import com.genesiz.hexacreatecore.threedified.volumetrics.dictionaries.BlackHoleVolumetricDictionary;
import com.genesiz.hexacreatecore.threedified.volumetrics.dictionaries.SiftRiftVolumetricDictionary;
import com.genesiz.hexacreatecore.threedified.volumetrics.hooks.VolumetricHook;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class WormholeClientRenderer {

  private static final long EPOCH_NANOS = System.nanoTime();
  private static List<WormholeSyncPayload.SyncNode> activeNodes = new ArrayList<>();
  private static WormholeSyncPayload.SyncNode pendingNode = null;
  private static final java.util.Map<String, Long> SPAWN_TIMES = new java.util.concurrent.ConcurrentHashMap<>();
  private static final long SPAWN_ANIM_MS = 550L;


  private WormholeClientRenderer() {}

  public static void updateSyncState(List<WormholeSyncPayload.SyncNode> nodes, WormholeSyncPayload.SyncNode pending) {
    activeNodes = nodes;
    pendingNode = pending;
  }

  public static boolean hasPendingAnchor() {
    return pendingNode != null;
  }
  private record VisibleWormhole(BlockPos pos, Direction face, float discSize, float crownDepth, float scale) {}

  @SubscribeEvent
  public static void onRenderWorld(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    if (activeNodes.isEmpty() && pendingNode == null) {
      SPAWN_TIMES.clear();
      return;
    }

    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;

    Vec3 cam = mc.getEntityRenderDispatcher().camera.getPosition();
    PoseStack ps = event.getPoseStack();
    MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
    float timeSec = (float) ((System.nanoTime() - EPOCH_NANOS) * 1.0e-9D);

    long nowMs = System.currentTimeMillis();
    java.util.Set<String> visibleKeys = new java.util.HashSet<>();
    List<VisibleWormhole> batch = new ArrayList<>(activeNodes.size() + 1);

    for (WormholeSyncPayload.SyncNode node : activeNodes) {
      String key = "active_" + node.pos().asLong() + "_" + node.face().getName();
      visibleKeys.add(key);
      float scale = computeRubberBandScale(key, nowMs);
      if (scale > 0.001f && isWithinRange(cam, node.pos(), node.face())) {
        batch.add(new VisibleWormhole(node.pos(), node.face(), 1.05f, 0.36f, scale));
      }
    }

    if (pendingNode != null) {
      String key = "pending_" + pendingNode.pos().asLong() + "_" + pendingNode.face().getName();
      visibleKeys.add(key);
      float scale = computeRubberBandScale(key, nowMs);
      if (scale > 0.001f && isWithinRange(cam, pendingNode.pos(), pendingNode.face())) {
        batch.add(new VisibleWormhole(pendingNode.pos(), pendingNode.face(), 0.72f, 0.22f, scale));
      }
    }

    SPAWN_TIMES.keySet().removeIf(k -> !visibleKeys.contains(k));
    if (batch.isEmpty()) return;

    // Pass 1: All accretion disks in 1 batched draw call
    RenderSystem.enableDepthTest();
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.disableCull();
    RenderSystem.depthMask(false);
    for (VisibleWormhole wh : batch) {
      pushWormholePose(ps, cam, wh);
      ps.translate(0.0f, 0.0f, 0.05f);
      PortalShaderHook.bind(BlackHoleDictionary.SILVER_SINGULARITY)
        .size(wh.discSize() * 0.88f, wh.discSize() * 0.88f)
        .resolution(32, 7)
        .render(ps, buf, timeSec);
      ps.popPose();
    }
    buf.endBatch(RawGeometry.DIALOGUE_SOLID);

    // Pass 2: All pitch-black 3D singularity cores in 1 batched draw call
    RenderSystem.disableBlend();
    RenderSystem.enableCull();
    RenderSystem.depthMask(true);
    for (VisibleWormhole wh : batch) {
      pushWormholePose(ps, cam, wh);
      float coreHalf = wh.discSize() * 0.59f;
      VolumetricHook.bind(BlackHoleVolumetricDictionary.SILVER_SINGULARITY)
        .at(0.0f, 0.0f, 0.04f)
        .coreSize(coreHalf, coreHalf, 0.11f)
        .renderCoreOnly(ps, buf, timeSec);
      ps.popPose();
    }
    buf.endBatch(RawGeometry.DIALOGUE_SOLID);

    // Pass 3: All translucent photon horizons + Sift-Rift crowns in 1 batched draw call
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.depthMask(false);
    for (VisibleWormhole wh : batch) {
      pushWormholePose(ps, cam, wh);
      float coreHalf = wh.discSize() * 0.59f;
      VolumetricHook.bind(BlackHoleVolumetricDictionary.SILVER_SINGULARITY)
        .at(0.0f, 0.0f, 0.04f)
        .coreSize(coreHalf, coreHalf, 0.11f)
        .eventHorizon(0.095f, 4)
        .renderHorizonOnly(ps, buf, timeSec);

      RenderSystem.disableCull();
      VolumetricHook.bind(SiftRiftVolumetricDictionary.WHITE_RIFT)
        .at(0.0f, 0.0f, 0.045f)
        .coreSize(coreHalf * 0.84f, coreHalf * 0.84f, 0.0f)
        .autoFlush(false)
        .renderCrown(ps, buf, 0.072f, -wh.crownDepth(), 5, timeSec);
      ps.popPose();
    }
    buf.endBatch(RawGeometry.DIALOGUE_SOLID);

    RenderSystem.depthMask(true);
    RenderSystem.enableCull();
  }

  private static boolean isWithinRange(Vec3 cam, BlockPos pos, Direction face) {
    double fx = pos.getX() + 0.5D + face.getStepX() * 0.506D;
    double fy = pos.getY() + 0.5D + face.getStepY() * 0.506D;
    double fz = pos.getZ() + 0.5D + face.getStepZ() * 0.506D;
    return 96.0D * 96.0D >= cam.distanceToSqr(fx, fy, fz);
  }

  private static void pushWormholePose(PoseStack ps, Vec3 cam, VisibleWormhole wh) {
    double fx = wh.pos().getX() + 0.5D + wh.face().getStepX() * 0.506D;
    double fy = wh.pos().getY() + 0.5D + wh.face().getStepY() * 0.506D;
    double fz = wh.pos().getZ() + 0.5D + wh.face().getStepZ() * 0.506D;
    ps.pushPose();
    ps.translate(fx - cam.x, fy - cam.y, fz - cam.z);
    orientToFace(ps, wh.face());
    ps.scale(wh.scale(), wh.scale(), wh.scale());
  }

  private static float computeRubberBandScale(String key, long nowMs) {
    long bornMs = SPAWN_TIMES.computeIfAbsent(key, k -> nowMs);
    float t = Math.min(1.0f, Math.max(0.0f, (nowMs - bornMs) / (float) SPAWN_ANIM_MS));
    if (t >= 1.0f) return 1.0f;

    // easeOutBack rubber-band overshoot (0.0 -> ~1.18 -> 1.0)
    float c1 = 2.4f;
    float c3 = c1 + 1.0f;
    float u = t - 1.0f;
    return 1.0f + c3 * (u * u * u) + c1 * (u * u);
  }


  private static void renderFaceWormhole(
    PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam,
    BlockPos pos, Direction face, float discSize, float crownDepth, float timeSec, float spawnScale
  ) {
    if (spawnScale <= 0.001f) return;

    double fx = pos.getX() + 0.5D + face.getStepX() * 0.506D;
    double fy = pos.getY() + 0.5D + face.getStepY() * 0.506D;
    double fz = pos.getZ() + 0.5D + face.getStepZ() * 0.506D;
    if (cam.distanceToSqr(fx, fy, fz) > 96.0D * 96.0D) return;

    ps.pushPose();
    ps.translate(fx - cam.x, fy - cam.y, fz - cam.z);
    orientToFace(ps, face);
    ps.scale(spawnScale, spawnScale, spawnScale);

    // 1. Face-aligned square relativistic accretion halo at z = 0.05f
    ps.pushPose();
    ps.translate(0.0f, 0.0f, 0.05f);
    PortalShaderHook.bind(BlackHoleDictionary.SILVER_SINGULARITY)
      .size(discSize * 0.88f, discSize * 0.88f)
      .resolution(36, 8)
      .flushBatch()
      .render(ps, buf, timeSec);
    ps.popPose();

    float coreHalf = discSize * 0.59f;
    // 2. 3D Pitch-Black Superellipsoid Core + Rim-Lit Silver Photon Horizon protruding cleanly at z = 0.04f
    VolumetricHook.bind(BlackHoleVolumetricDictionary.SILVER_SINGULARITY)
      .at(0.0f, 0.0f, 0.04f)
      .coreSize(coreHalf, coreHalf, 0.11f)
      .eventHorizon(0.095f, 5)
      .render(ps, buf, timeSec);

    // 3. Sift-Rift translucent white wobbling crown shifted back (-0.065f) to sit flush with the event horizon
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.disableCull();
    RenderSystem.depthMask(false);

    VolumetricHook.bind(SiftRiftVolumetricDictionary.WHITE_RIFT)
      .at(0.0f, 0.0f, 0.045f)
      .coreSize(coreHalf * 0.915f, coreHalf * 0.915f, 0.0f)
      .renderCrown(ps, buf, 0.095f, -crownDepth, 6, timeSec);
    buf.endBatch(RawGeometry.DIALOGUE_SOLID);

    RenderSystem.depthMask(true);
    RenderSystem.enableCull();
    ps.popPose();
  }

  private static void orientToFace(PoseStack ps, Direction face) {
    switch (face) {
      case SOUTH -> {}
      case NORTH -> ps.mulPose(Axis.YP.rotationDegrees(180.0f));
      case EAST  -> ps.mulPose(Axis.YP.rotationDegrees(90.0f));
      case WEST  -> ps.mulPose(Axis.YP.rotationDegrees(-90.0f));
      case UP    -> ps.mulPose(Axis.XP.rotationDegrees(-90.0f));
      case DOWN  -> ps.mulPose(Axis.XP.rotationDegrees(90.0f));
    }
  }

  private static void drawSiftRiftCrown(
    VertexConsumer vc, Matrix4f m,
    float halfSize, float maxDepth, float zStart,
    float timeSec, int peakOpacity, int endOpacity
  ) {
    final int segments = 40;

    for (int i = 0; segments > i; i++) {
      float t0 = (float) (i * Math.PI * 2.0D / segments);
      float t1 = (float) ((i + 1) * Math.PI * 2.0D / segments);

      float c0 = (float) Math.cos(t0);
      float s0 = (float) Math.sin(t0);
      float sqX0 = (float) (Math.signum(c0) * Math.pow(Math.abs(c0), 0.32D));
      float sqY0 = (float) (Math.signum(s0) * Math.pow(Math.abs(s0), 0.32D));

      float c1 = (float) Math.cos(t1);
      float s1 = (float) Math.sin(t1);
      float sqX1 = (float) (Math.signum(c1) * Math.pow(Math.abs(c1), 0.32D));
      float sqY1 = (float) (Math.signum(s1) * Math.pow(Math.abs(s1), 0.32D));

      float wave0 = (float) (Math.sin(timeSec * 4.4D + t0 * 4.0D) * 0.55D + Math.cos(timeSec * 3.1D - t0 * 6.0D) * 0.45D);
      float wave1 = (float) (Math.sin(timeSec * 4.4D + t1 * 4.0D) * 0.55D + Math.cos(timeSec * 3.1D - t1 * 6.0D) * 0.45D);

      float rIn0 = halfSize + wave0 * 0.016f;
      float rIn1 = halfSize + wave1 * 0.016f;

      float rMid0 = halfSize * 1.03f + wave0 * 0.022f;
      float rMid1 = halfSize * 1.03f + wave1 * 0.022f;

      float rOut0 = halfSize * 1.12f + wave0 * 0.036f;
      float rOut1 = halfSize * 1.12f + wave1 * 0.036f;

      float zMid0 = zStart - 0.032f;
      float zMid1 = zStart - 0.032f;

      float zOut0 = zStart - maxDepth * (0.82f + 0.25f * wave0);
      float zOut1 = zStart - maxDepth * (0.82f + 0.25f * wave1);

      // Band 1: Smooth 0 -> peakOpacity ramp right out of the event horizon
      RawGeometry.vertex(vc, m, sqX0 * rIn0,  sqY0 * rIn0,  zStart, 255, 255, 255, 0);
      RawGeometry.vertex(vc, m, sqX1 * rIn1,  sqY1 * rIn1,  zStart, 255, 255, 255, 0);
      RawGeometry.vertex(vc, m, sqX1 * rMid1, sqY1 * rMid1, zMid1,  255, 255, 255, peakOpacity);
      RawGeometry.vertex(vc, m, sqX0 * rMid0, sqY0 * rMid0, zMid0,  255, 255, 255, peakOpacity);

      // Band 2: Outer Sift-rift extrusion (peakOpacity -> endOpacity)
      RawGeometry.vertex(vc, m, sqX0 * rMid0, sqY0 * rMid0, zMid0,  255, 255, 255, peakOpacity);
      RawGeometry.vertex(vc, m, sqX1 * rMid1, sqY1 * rMid1, zMid1,  255, 255, 255, peakOpacity);
      RawGeometry.vertex(vc, m, sqX1 * rOut1, sqY1 * rOut1, zOut1,  255, 255, 255, endOpacity);
      RawGeometry.vertex(vc, m, sqX0 * rOut0, sqY0 * rOut0, zOut0,  255, 255, 255, endOpacity);
    }
  }
}
