package com.genesiz.hexacreatecore.hexcasting;

import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import java.util.List;

public final class HexCastingGeometry {
  private static Vec3 smoothedHudPos = null;
  private static float smoothedHudYaw = 0.0f;

  public static void render3DCellShadedLines(PoseStack poseStack, List<List<Integer>> persistentLines, List<Integer> activePath, List<ArcaneNode3D> gridNodes, Vec3 cameraPos, int baseColorHex) {
    if (gridNodes.isEmpty()) return;
    boolean hasActiveLine = (activePath != null && activePath.size() >= 2);
    boolean hasPersistentLines = (persistentLines != null && !persistentLines.isEmpty());
    if (!hasActiveLine && !hasPersistentLines) return;

    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.setShader(GameRenderer::getPositionColorShader);
    Tesselator tesselator = Tesselator.getInstance();
    float coreThickness = 0.0225f;

    GlStateManager._enableCull();
    GL11.glCullFace(GL11.GL_FRONT);
    BufferBuilder outlineBuffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    renderScenePrisms(poseStack, outlineBuffer, persistentLines, activePath, gridNodes, cameraPos, coreThickness * 2.0f, 0xFF000000);
    BufferUploader.drawWithShader(outlineBuffer.buildOrThrow());

    GL11.glCullFace(GL11.GL_BACK);
    BufferBuilder coreBuffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    renderScenePrisms(poseStack, coreBuffer, persistentLines, activePath, gridNodes, cameraPos, coreThickness, 0xFFB233FF);
    BufferUploader.drawWithShader(coreBuffer.buildOrThrow());
    RenderSystem.disableBlend();
  }

  public static void render3DStackHUD(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 center, Vec3 cameraPos, float camYaw, float camPitch, int activeShellIndex, List<String> stackStrings, String ravensmind, int scrollIndex) {
    if (center == null || !HexagonalGridManager.isActive()) {
      smoothedHudPos = null;
      return;
    }

    List<String> allItems = HexagonalGridManager.getStackStrings();
    String activeRavensmind = HexagonalGridManager.getRavensmindString();
    boolean hasRavensmind = (activeRavensmind != null && !activeRavensmind.isEmpty());

    if (allItems.isEmpty() && !hasRavensmind) {
      smoothedHudPos = null;
      return;
    }

    Minecraft mc = Minecraft.getInstance();
    Font font = mc.font;

    int startIndex = Math.max(0, Math.min(scrollIndex, Math.max(0, allItems.size() - 10)));
    int endIndex = Math.min(allItems.size(), startIndex + 10);

    double orbitRadius = 2.0D * (activeShellIndex + 2);
    double yawRad = Math.toRadians(camYaw);
    double lookX = -Math.sin(yawRad);
    double lookZ = Math.cos(yawRad);

    double relX = cameraPos.x - center.x;
    double relZ = cameraPos.z - center.z;
    double b = relX * lookX + relZ * lookZ;
    double c = (relX * relX + relZ * relZ) - (orbitRadius * orbitRadius);
    double discriminant = (b * b) - c;

    double goalX;
    double goalZ;
    if (discriminant >= 0.0D) {
      double t = -b + Math.sqrt(discriminant);
      goalX = cameraPos.x + (lookX * t);
      goalZ = cameraPos.z + (lookZ * t);
    } else {
      goalX = center.x + (lookX * orbitRadius);
      goalZ = center.z + (lookZ * orbitRadius);
    }

    double sineBob = Math.sin(System.currentTimeMillis() / 900.0D) * 0.06D;
    double goalY = center.y + 4.50D + sineBob;
    Vec3 goalPos = new Vec3(goalX, goalY, goalZ);

    float dt = mc.getTimer().getRealtimeDeltaTicks() / 20.0f;
    double linearAlpha = Math.min(1.0D, dt * 2.5D);
    double sineEase = Math.sin(linearAlpha * (Math.PI / 2.0D));

    if (smoothedHudPos == null || smoothedHudPos.distanceToSqr(goalPos) > 100.0D) {
      smoothedHudPos = goalPos;
      smoothedHudYaw = camYaw;
    } else {
      smoothedHudPos = smoothedHudPos.lerp(goalPos, sineEase);
      float yawDiff = net.minecraft.util.Mth.wrapDegrees(camYaw - smoothedHudYaw);
      smoothedHudYaw += yawDiff * (float) sineEase;
    }

    poseStack.pushPose();
    poseStack.translate(smoothedHudPos.x - cameraPos.x, smoothedHudPos.y - cameraPos.y, smoothedHudPos.z - cameraPos.z);
    poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-smoothedHudYaw - 180.0F));
    poseStack.scale(0.010F, -0.010F, 0.010F);

    float currentPanelLocalY = 0.0f;
    int fixedBoxWidth = 150;

    // 1. Pinned Raven's Mind box at the very top of the floating list
    if (hasRavensmind) {
      String ravenLabel = "§6[Raven's Mind] §f" + activeRavensmind;
      List<FormattedCharSequence> ravenLines = font.split(Component.literal(ravenLabel), fixedBoxWidth - 16);
      float ravenHeight = (ravenLines.size() * font.lineHeight) + 12.0f;

      poseStack.pushPose();
      poseStack.translate(0.0f, currentPanelLocalY, 0.0f);

      RawGeometry.drawDialogueNode(
        poseStack, bufferSource, font,
        0.0f, 0.0f, 0.0f,
        fixedBoxWidth, ravenHeight, 3.0f, 3.0f,
        0x1A241808, 0x1AFFD700, ravenLines, false
      );

      poseStack.popPose();
      currentPanelLocalY += ravenHeight + 10.0f;
    }

    // 2. Scrollable 10-slot stack boxes below Raven's Mind
    for (int i = startIndex; i < endIndex; i++) {
      String label = "§e[" + i + "] §f" + allItems.get(i);
      List<FormattedCharSequence> formattedLines = font.split(Component.literal(label), fixedBoxWidth - 16);
      float boxHeight = (formattedLines.size() * font.lineHeight) + 12.0f;

      poseStack.pushPose();
      poseStack.translate(0.0f, currentPanelLocalY, 0.0f);

      RawGeometry.drawDialogueNode(
        poseStack, bufferSource, font,
        0.0f, 0.0f, 0.0f,
        fixedBoxWidth, boxHeight, 3.0f, 3.0f,
        0x1A110E1A, 0x1A5A2E85, formattedLines, false
      );

      poseStack.popPose();
      currentPanelLocalY += boxHeight + 8.0f;
    }

    poseStack.popPose();
  }


  private static void renderScenePrisms(PoseStack poseStack, VertexConsumer consumer, List<List<Integer>> persistentLines, List<Integer> activePath, List<ArcaneNode3D> gridNodes, Vec3 cameraPos, float thickness, int colorHex) {
    drawSingleLineStream(poseStack, consumer, activePath, gridNodes, cameraPos, thickness, colorHex);
    for (List<Integer> completeLine : persistentLines) {
      drawSingleLineStream(poseStack, consumer, completeLine, gridNodes, cameraPos, thickness, colorHex);
    }
  }

  private static void drawSingleLineStream(PoseStack poseStack, VertexConsumer consumer, List<Integer> path, List<ArcaneNode3D> gridNodes, Vec3 cameraPos, float thickness, int colorHex) {
    if (path == null || path.size() < 2) return;
    for (int i = 0; path.size() - 1 > i; i++) {
      ArcaneNode3D startNode = gridNodes.get(path.get(i));
      ArcaneNode3D endNode = gridNodes.get(path.get(i + 1));
      if (startNode == null || endNode == null) continue;

      Vec3 start = startNode.worldPos(); Vec3 end = endNode.worldPos();
      Vec3 midpoint = new Vec3((start.x + end.x) / 2.0D, (start.y + end.y) / 2.0D, (start.z + end.z) / 2.0D);

      double dx = end.x - start.x; double dy = end.y - start.y; double dz = end.z - start.z;
      float lineLength = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (lineLength < 0.001f) continue;

      poseStack.pushPose();
      poseStack.translate(midpoint.x - cameraPos.x, midpoint.y - cameraPos.y, midpoint.z - cameraPos.z);
      poseStack.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0f, 0.0f, 1.0f), new Vector3f((float)(dx / lineLength), (float)(dy / lineLength), (float)(dz / lineLength))));
      RawGeometry.drawPrism(consumer, poseStack.last().pose(), thickness, thickness, lineLength, colorHex);
      poseStack.popPose();
    }
  }
}
