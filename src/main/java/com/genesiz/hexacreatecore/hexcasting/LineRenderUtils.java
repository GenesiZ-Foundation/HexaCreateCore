package com.genesiz.hexacreatecore.hexcasting;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class LineRenderUtils {
  // Draws a solid 3D billboarded rectangle between two world vectors facing the camera view lens
  public static void draw3DLineRectangle(PoseStack poseStack, VertexConsumer buffer, Vec3 start, Vec3 end, Vec3 cameraPos, float thickness, int red, int green, int blue, int alpha) {
    Vec3 lineDirection = end.subtract(start);
    Vec3 cameraToLineStart = start.subtract(cameraPos);

    // Generate a perpendicular cross-product vector matching the camera view to billboard the quad face
    Vec3 crossVector = lineDirection.cross(cameraToLineStart).normalize().scale(thickness * 0.5D);
    if (crossVector.lengthSqr() < 0.0001D) return;

    Matrix4f matrixMatrix = poseStack.last().pose();

    Vector3f p1 = new Vector3f((float)(start.x + crossVector.x - cameraPos.x), (float)(start.y + crossVector.y - cameraPos.y), (float)(start.z + crossVector.z - cameraPos.z));
    Vector3f p2 = new Vector3f((float)(start.x - crossVector.x - cameraPos.x), (float)(start.y - crossVector.y - cameraPos.y), (float)(start.z - crossVector.z - cameraPos.z));
    Vector3f p3 = new Vector3f((float)(end.x - crossVector.x - cameraPos.x), (float)(end.y - crossVector.y - cameraPos.y), (float)(end.z - crossVector.z - cameraPos.z));
    Vector3f p4 = new Vector3f((float)(end.x + crossVector.x - cameraPos.x), (float)(end.y + crossVector.y - cameraPos.y), (float)(end.z + crossVector.z - cameraPos.z));

    // Inject the vertex primitives cleanly using direct quad layouts
    buffer.addVertex(matrixMatrix, p1.x, p1.y, p1.z).setColor(red, green, blue, alpha);
    buffer.addVertex(matrixMatrix, p2.x, p2.y, p2.z).setColor(red, green, blue, alpha);
    buffer.addVertex(matrixMatrix, p3.x, p3.y, p3.z).setColor(red, green, blue, alpha);
    buffer.addVertex(matrixMatrix, p4.x, p4.y, p4.z).setColor(red, green, blue, alpha);
  }
}
