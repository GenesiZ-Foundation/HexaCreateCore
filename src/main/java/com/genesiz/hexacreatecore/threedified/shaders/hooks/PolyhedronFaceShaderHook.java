package com.genesiz.hexacreatecore.threedified.shaders.hooks;

import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.ShaderDictionary;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FastColor;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public final class PolyhedronFaceShaderHook {

  private final ShaderDictionary dictionary;
  private float alphaMultiplier = 0.36f;
  private int subdivisions = 4;

  private PolyhedronFaceShaderHook(ShaderDictionary dictionary) {
    this.dictionary = dictionary;
  }

  public static PolyhedronFaceShaderHook bind(ShaderDictionary dictionary) {
    return new PolyhedronFaceShaderHook(dictionary);
  }

  public PolyhedronFaceShaderHook opacity(float alphaMultiplier) {
    this.alphaMultiplier = alphaMultiplier;
    return this;
  }

  public PolyhedronFaceShaderHook rings(int subdivisions) {
    this.subdivisions = Math.max(2, subdivisions);
    return this;
  }

  public void renderFaces(PoseStack poseStack, MultiBufferSource bufferSource, List<List<Vec3>> faces, float radius, float timeSeconds) {
    VertexConsumer vc = bufferSource.getBuffer(RawGeometry.DIALOGUE_SOLID);
    Matrix4f mat = poseStack.last().pose();

    for (List<Vec3> face : faces) {
      int sides = face.size();
      if (3 > sides) continue;

      Vec3 center = Vec3.ZERO;
      for (Vec3 v : face) center = center.add(v);
      center = center.scale(1.0D / sides);

      for (int s = 0; sides > s; s++) {
        Vec3 v0 = face.get(s);
        Vec3 v1 = face.get((s + 1) % sides);

        for (int r = 0; subdivisions > r; r++) {
          float t0 = (float) r / subdivisions;
          float t1 = (float) (r + 1) / subdivisions;

          Vec3 p00 = center.lerp(v0, t0).scale(radius);
          Vec3 p10 = center.lerp(v1, t0).scale(radius);
          Vec3 p11 = center.lerp(v1, t1).scale(radius);
          Vec3 p01 = center.lerp(v0, t1).scale(radius);

          RawGeometry.vertex(vc, mat, (float) p00.x, (float) p00.y, (float) p00.z, sampleFaceColor(p00, timeSeconds));
          RawGeometry.vertex(vc, mat, (float) p10.x, (float) p10.y, (float) p10.z, sampleFaceColor(p10, timeSeconds));
          RawGeometry.vertex(vc, mat, (float) p11.x, (float) p11.y, (float) p11.z, sampleFaceColor(p11, timeSeconds));
          RawGeometry.vertex(vc, mat, (float) p01.x, (float) p01.y, (float) p01.z, sampleFaceColor(p01, timeSeconds));
        }
      }
    }
  }

  private int sampleFaceColor(Vec3 p, float timeSeconds) {
    float u = (float) (p.x * 0.55D - p.z * 0.45D);
    float v = (float) (p.y * 0.65D + p.z * 0.35D);
    double sphericalAngle = Math.atan2(p.z, p.x);
    int raw = dictionary.sampleArgb(u, v, sphericalAngle, 0.0f, timeSeconds);
    int scaledAlpha = (int) (FastColor.ARGB32.alpha(raw) * alphaMultiplier);
    return (raw & 0x00FFFFFF) | (scaledAlpha << 24);
  }
}
