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

  private WormholeClientRenderer() {}

  public static void updateSyncState(List<WormholeSyncPayload.SyncNode> nodes, WormholeSyncPayload.SyncNode pending) {
    activeNodes = nodes;
    pendingNode = pending;
  }

  public static boolean hasPendingAnchor() {
    return pendingNode != null;
  }

  @SubscribeEvent
  public static void onRenderWorld(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    if (activeNodes.isEmpty() && pendingNode == null) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;

    Vec3 cam = mc.getEntityRenderDispatcher().camera.getPosition();
    PoseStack ps = event.getPoseStack();
    MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
    float timeSec = (float) ((System.nanoTime() - EPOCH_NANOS) * 1.0e-9D);

    for (WormholeSyncPayload.SyncNode node : activeNodes) {
      renderFaceWormhole(ps, buf, cam, node.pos(), node.face(), 1.05f, 0.36f, timeSec);
    }

    if (pendingNode != null) {
      renderFaceWormhole(ps, buf, cam, pendingNode.pos(), pendingNode.face(), 0.72f, 0.22f, timeSec);
    }
  }

  private static void renderFaceWormhole(
    PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam,
    BlockPos pos, Direction face, float discSize, float crownDepth, float timeSec
  ) {
    double fx = pos.getX() + 0.5D + face.getStepX() * 0.506D;
    double fy = pos.getY() + 0.5D + face.getStepY() * 0.506D;
    double fz = pos.getZ() + 0.5D + face.getStepZ() * 0.506D;
    if (cam.distanceToSqr(fx, fy, fz) > 96.0D * 96.0D) return;

    ps.pushPose();
    ps.translate(fx - cam.x, fy - cam.y, fz - cam.z);
    orientToFace(ps, face);


    // 1. Face-aligned square relativistic accretion halo at z = 0.0f
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

    // 3. Sift-Rift translucent white wobbling crown hugging the 3D core's perimeter
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.disableCull();
    RenderSystem.depthMask(false);

    //VertexConsumer vc = buf.getBuffer(RawGeometry.DIALOGUE_SOLID);
    //float crownHalf = coreHalf + 0.075f;
    // 3. True 3D Volumetric Sift-Rift Crown wrapping the black-hole perimeter (inner wall, outer wall, and front 3D cap)
    VolumetricHook.bind(SiftRiftVolumetricDictionary.WHITE_RIFT)
      .at(0.0f, 0.0f, -0.005f)
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
