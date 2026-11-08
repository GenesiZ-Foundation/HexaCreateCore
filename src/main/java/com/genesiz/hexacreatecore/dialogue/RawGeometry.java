/*
 * THIS PART OF THE CODE IS MEANT FOR DEVELOPERS
 * */
package com.genesiz.hexacreatecore.dialogue;

import com.genesiz.hexacreatecore.decorators.annotations.Overload;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

import org.joml.Matrix4f;

public final class RawGeometry {
  private RawGeometry() {} // Prevent instantiation
  public static final RenderType DIALOGUE_SOLID = RenderType.create(
    "dialogue_solid",
    DefaultVertexFormat.POSITION_COLOR,
    VertexFormat.Mode.QUADS,
    256,
    false,
    true,
    RenderType.CompositeState.builder()
    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
    .setCullState(RenderStateShard.NO_CULL)
    .createCompositeState(false)
  );


  public static void drawRectangle(VertexConsumer consumer, Matrix4f matrix, float width, float height, int hexColor) {
    float halfW = width / 2.0f;
    float halfH = height / 2.0f;

    int a = (hexColor >> 24) & 0xFF;
    int r = (hexColor >> 16) & 0xFF;
    int g = (hexColor >> 8) & 0xFF;
    int b = hexColor & 0xFF;

    // Counter-clockwise quad mapping
    vertex(consumer, matrix,  halfW,  halfH, 0.0f, r, g, b, a);
    vertex(consumer, matrix, -halfW,  halfH, 0.0f, r, g, b, a);
    vertex(consumer, matrix, -halfW, -halfH, 0.0f, r, g, b, a);
    vertex(consumer, matrix,  halfW, -halfH, 0.0f, r, g, b, a);
  }

  public static void drawPrism(VertexConsumer consumer, Matrix4f matrix, float width, float height, float depth, int hexColor) {
    float hW = width / 2.0f;
    float hH = height / 2.0f;
    float hD = depth / 2.0f;

    int a = (hexColor >> 24) & 0xFF;
    int r = (hexColor >> 16) & 0xFF;
    int g = (hexColor >> 8) & 0xFF;
    int b = hexColor & 0xFF;

    // Front Face
    vertex(consumer, matrix,  hW,  hH,  hD, r, g, b, a);
    vertex(consumer, matrix, -hW,  hH,  hD, r, g, b, a);
    vertex(consumer, matrix, -hW, -hH,  hD, r, g, b, a);
    vertex(consumer, matrix,  hW, -hH,  hD, r, g, b, a);

    // Back Face
    vertex(consumer, matrix,  hW, -hH, -hD, r, g, b, a);
    vertex(consumer, matrix, -hW, -hH, -hD, r, g, b, a);
    vertex(consumer, matrix, -hW,  hH, -hD, r, g, b, a);
    vertex(consumer, matrix,  hW,  hH, -hD, r, g, b, a);

    // Top Face
    vertex(consumer, matrix,  hW,  hH, -hD, r, g, b, a);
    vertex(consumer, matrix, -hW,  hH, -hD, r, g, b, a);
    vertex(consumer, matrix, -hW,  hH,  hD, r, g, b, a);
    vertex(consumer, matrix,  hW,  hH,  hD, r, g, b, a);

    // Bottom Face
    vertex(consumer, matrix,  hW, -hH,  hD, r, g, b, a);
    vertex(consumer, matrix, -hW, -hH,  hD, r, g, b, a);
    vertex(consumer, matrix, -hW, -hH, -hD, r, g, b, a);
    vertex(consumer, matrix,  hW, -hH, -hD, r, g, b, a);

    // Right Face
    vertex(consumer, matrix,  hW,  hH, -hD, r, g, b, a);
    vertex(consumer, matrix,  hW,  hH,  hD, r, g, b, a);
    vertex(consumer, matrix,  hW, -hH,  hD, r, g, b, a);
    vertex(consumer, matrix,  hW, -hH, -hD, r, g, b, a);

    // Left Face
    vertex(consumer, matrix, -hW,  hH,  hD, r, g, b, a);
    vertex(consumer, matrix, -hW,  hH, -hD, r, g, b, a);
    vertex(consumer, matrix, -hW, -hH, -hD, r, g, b, a);
    vertex(consumer, matrix, -hW, -hH,  hD, r, g, b, a);
  }

  public static void drawBorderedPanel(
      PoseStack poseStack,
      VertexConsumer consumer,
      float width,
      float height,
      float borderThickness,
      float borderDepth,
      int bgColor,
      int borderColor
  ) {
    drawRectangle(consumer, poseStack.last().pose(), width, height, bgColor);

    float halfW = width * 0.5f;
    float halfH = height * 0.5f;
    float halfBorder = borderThickness * 0.5f;
    float horizSpan = width + (borderThickness * 2.0f);

    poseStack.pushPose();
    poseStack.translate(0.0f, -(halfH + halfBorder), 0.0f);
    drawPrism(consumer, poseStack.last().pose(), horizSpan, borderThickness, borderDepth, borderColor);
    poseStack.popPose();

    poseStack.pushPose();
    poseStack.translate(0.0f, (halfH + halfBorder), 0.0f);
    drawPrism(consumer, poseStack.last().pose(), horizSpan, borderThickness, borderDepth, borderColor);
    poseStack.popPose();

    poseStack.pushPose();
    poseStack.translate(-(halfW + halfBorder), 0.0f, 0.0f);
    drawPrism(consumer, poseStack.last().pose(), borderThickness, height, borderDepth, borderColor);
    poseStack.popPose();

    poseStack.pushPose();
    poseStack.translate((halfW + halfBorder), 0.0f, 0.0f);
    drawPrism(consumer, poseStack.last().pose(), borderThickness, height, borderDepth, borderColor);
    poseStack.popPose();
  }

  public static void drawDialogueNode(
    PoseStack poseStack,
    MultiBufferSource bufferSource,
    Font font,
    float localX,
    float localY,
    float localZ,
    float width,
    float height,
    float borderThickness,
    float borderDepth,
    int bgColor,
    int borderColor,
    // Different here
    List<FormattedCharSequence> lines,
    boolean centerText
  ) {
    poseStack.pushPose();
    poseStack.translate(localX, localY, localZ);

    VertexConsumer consumer = bufferSource.getBuffer(DIALOGUE_SOLID);
    drawBorderedPanel(poseStack, consumer, width, height, borderThickness, borderDepth, bgColor, borderColor);

    if (lines != null && !lines.isEmpty()) {
      poseStack.translate(0.0f, 0.0f, 0.5f);
      float textStartY = -(height * 0.5f) + 6.0f;
      Matrix4f textMatrix = poseStack.last().pose();

      for (int i = 0; i < lines.size(); i++) {
        FormattedCharSequence line = lines.get(i);
        float textStartX = centerText
          ? -(font.width(line) * 0.5f)
          : -(width * 0.5f) + 8.0f;

        font.drawInBatch(
          line,
          textStartX,
          textStartY + (i * font.lineHeight),
          0xFFFFFF,
          false,
          textMatrix,
          bufferSource,
          Font.DisplayMode.NORMAL,
          0,
          15728880
        );
      }
    }

    poseStack.popPose();
  }

  @Overload
  public static void drawDialogueNode(
    PoseStack poseStack,
    MultiBufferSource bufferSource,
    Font font,
    float localX,
    float localY,
    float localZ,
    float width,
    float height,
    float borderThickness,
    float borderDepth,
    int bgColor,
    int borderColor,
    // Different here
    DialoguePlaybackState playback,
    boolean typewriter,
    boolean centerText
  ) {
    List<FormattedCharSequence> linesToDraw = typewriter
    ? playback.getVisibleLines()
    : playback.getFullLines();

    drawDialogueNode(
      poseStack,
      bufferSource,
      font,
      localX,
      localY,
      localZ,
      width,
      height,
      borderThickness,
      borderDepth,
      bgColor,
      borderColor,
      linesToDraw,
      centerText
    );
  }

  private static void vertex(VertexConsumer c, Matrix4f m, float x, float y, float z, int r, int g, int b, int a) {
    c.addVertex(m, x, y, z).setColor(r, g, b, a);
  }
}
