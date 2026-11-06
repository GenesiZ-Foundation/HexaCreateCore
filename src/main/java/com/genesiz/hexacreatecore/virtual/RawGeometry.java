/*
 * THIS PART OF THE CODE IS MEANT FOR DEVELOPERS
 * */
package com.genesiz.hexacreatecore.virtual;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

public final class RawGeometry {
  private RawGeometry() {} // Prevent instantiation

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

  private static void vertex(VertexConsumer c, Matrix4f m, float x, float y, float z, int r, int g, int b, int a) {
    c.addVertex(m, x, y, z).setColor(r, g, b, a);
  }
}
