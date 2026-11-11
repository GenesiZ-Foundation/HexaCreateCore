package com.genesiz.hexacreatecore.hexcasting;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

@EventBusSubscriber(modid = "hexacreatecore", value = Dist.CLIENT)
public final class ClientRenderSubscriber {

  @SubscribeEvent
  public static void onRenderLevelStage(RenderLevelStageEvent event) {
    if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || !HexagonalGridManager.isActive()) return;

      Vec3 projectCameraPos = mc.gameRenderer.getMainCamera().getPosition();

      // Pass 1: Render the solid 3D cell-shaded purple pattern trace lines
      HexCastingGeometry.render3DCellShadedLines(
        event.getPoseStack(),
        HexagonalGridManager.getPersistentLines(),
        HexagonalGridManager.getActiveDrawingPath(),
        HexagonalGridManager.getActiveNodes(),
        projectCameraPos,
        HexagonalGridManager.getPointColorRaw()
      );

      // Compute active 2D horizontal cylinder shell distance parameters matching our tick handler
      double deltaX = projectCameraPos.x - HexagonalGridManager.getCenterAnchorPoint().x;
      double deltaZ = projectCameraPos.z - HexagonalGridManager.getCenterAnchorPoint().z;
      double cameraHorizontalDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

      // 2.0D is your defined HEX_RADIUS layout boundary threshold parameter
      int activeShellIndex = (int) (cameraHorizontalDist / 2.0D); 

      MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

      // =========================================================================
      // SINGLE VALID HUD PASS: Invokes your verified vertical multi-box geometry panel layout!
      // DELETED: The stray call to HexCastingHudGeometry has been completely removed to purge the cyan box!
      HexCastingGeometry.render3DStackHUD(
        event.getPoseStack(),
        bufferSource,
        HexagonalGridManager.getCenterAnchorPoint(),
        projectCameraPos,
        HexagonalGridManager.getFreeCamYaw(),
        HexagonalGridManager.getFreeCamPitch(),
        activeShellIndex,
        HexagonalGridManager.getStackStrings(),
        HexagonalGridManager.getRavensmindString(),
        HexagonalGridManager.getScrollIndex()
      );
      // =========================================================================

      // Command the buffer source to immediately submit your custom quads straight to shader layers
      bufferSource.endBatch(com.genesiz.hexacreatecore.dialogue.RawGeometry.DIALOGUE_SOLID);
      bufferSource.endBatch(); 
    }
  }
}
