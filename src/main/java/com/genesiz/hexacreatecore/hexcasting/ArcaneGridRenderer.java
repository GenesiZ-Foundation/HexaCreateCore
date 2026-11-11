package com.genesiz.hexacreatecore.hexcasting;

import com.genesiz.hexacreatecore.dialogue.RawGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

@EventBusSubscriber(modid = "hexacreatecore", value = Dist.CLIENT)
public final class ArcaneGridRenderer {

  @SubscribeEvent
  public static void onRenderLevelStage(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    if (!HexagonalGridManager.isActive()) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;

    List<ArcaneNode3D> nodes = HexagonalGridManager.getActiveNodes();
    if (nodes.isEmpty()) return;

    PoseStack poseStack = event.getPoseStack();
    Vec3 cam = mc.getEntityRenderDispatcher().camera.getPosition();
    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

    com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();

    // 1. Render Arcane 3D Node Prisms
    for (ArcaneNode3D node : nodes) {
      Vec3 pos = node.worldPos();
      poseStack.pushPose();
      poseStack.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);

      var camera = mc.getEntityRenderDispatcher().camera;
      poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-camera.getYRot() - 180.0F));
      poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-camera.getXRot()));

      float size = node.isActivated() ? 0.12f : 0.06f;
      RawGeometry.drawArcaneNodePrism(poseStack, bufferSource, 0.0f, 0.0f, 0.0f, size, node.nodeColor());
      poseStack.popPose();
    }

    // 2. Render Lines (Both persistent history and current drawing path threads)
    VertexConsumer lineBuffer = bufferSource.getBuffer(RenderType.lines());
    
    // Draw past drawn lines
    for (List<Integer> pastPath : HexagonalGridManager.getPersistentLines()) {
      for (int i = 0; pastPath.size() - 1 > i; i++) {
        renderLineSegment(poseStack, lineBuffer, cam, nodes.get(pastPath.get(i)), nodes.get(pastPath.get(i + 1)), 140);
      }
    }
    // Draw current active line thread
    List<Integer> currentPath = HexagonalGridManager.getActiveDrawingPath();
    for (int i = 0; currentPath.size() - 1 > i; i++) {
      renderLineSegment(poseStack, lineBuffer, cam, nodes.get(currentPath.get(i)), nodes.get(currentPath.get(i + 1)), 255);
    }

    bufferSource.endBatch(RenderType.gui());
    bufferSource.endBatch(RenderType.lines());

    com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
  }

  private static void renderLineSegment(PoseStack poseStack, VertexConsumer buffer, Vec3 cam, ArcaneNode3D start, ArcaneNode3D end, int alpha) {
    if (start == null || end == null) return;
    buffer.addVertex(poseStack.last().pose(), (float)(start.worldPos().x - cam.x), (float)(start.worldPos().y - cam.y), (float)(start.worldPos().z - cam.z)).setColor(34, 183, 209, alpha).setNormal(0.0f, 1.0f, 0.0f);
    buffer.addVertex(poseStack.last().pose(), (float)(end.worldPos().x - cam.x), (float)(end.worldPos().y - cam.y), (float)(end.worldPos().z - cam.z)).setColor(34, 183, 209, alpha).setNormal(0.0f, 1.0f, 0.0f);
  }

  /**
   * Draws a multi-row scrollable stack menu floating gracefully in the air inside the hexagonal cage.
   */

  private static void drawStackRow(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Minecraft mc, String text, float x, float y, float w, float h, int borderColor) {
    List<FormattedCharSequence> splitLines = mc.font.split(Component.literal(text), (int) w - 8);
    RawGeometry.drawDialogueNode(
      poseStack, (net.minecraft.client.renderer.MultiBufferSource) bufferSource, mc.font,
      x, y, 0.0f, w, h, 2.0f, 2.0f,
      0xDD0D0814, borderColor, splitLines, true
    );
  }
}
