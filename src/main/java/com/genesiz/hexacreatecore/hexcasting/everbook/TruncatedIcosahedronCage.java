package com.genesiz.hexacreatecore.hexcasting.everbook;

import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class TruncatedIcosahedronCage {

  public static final float CAGE_RADIUS = 5.25f;

  private static final List<Vec3> VERTICES = new ArrayList<>(60);
  private static final List<int[]> EDGES = new ArrayList<>(90);
  private static final List<Vec3> PENTAGON_NORMALS = new ArrayList<>(12);
  private static final List<Vec3> HEXAGON_NORMALS = new ArrayList<>(20);

  private static final List<List<Vec3>> FACES = new ArrayList<>(32);
  private record FacePlane(Vec3 normal, double unitInradius) {}
  private static final List<FacePlane> FACE_PLANES = new ArrayList<>(32);
  private static final double PLANE_EPSILON = 1.0e-3D;

  private static final double PENTAGON_INRADIUS_RATIO = 0.93417235896D;
  private static final double HEXAGON_INRADIUS_RATIO = 0.98224694688D;


  static {
    double phi = (1.0D + Math.sqrt(5.0D)) * 0.5D;
    double norm = Math.sqrt(10.0D + 9.0D * phi);

    // 60 vertices: even permutations of (0, +-1, +-3phi), (+-1, +-(2+phi), +-2phi), (+-phi, +-2, +-(2phi+1))
    addEvenPermutations(0.0D, 1.0D, 3.0D * phi, norm);
    addEvenPermutations(1.0D, 2.0D + phi, 2.0D * phi, norm);
    addEvenPermutations(phi, 2.0D, 2.0D * phi + 1.0D, norm);

    // 90 edges: pairs separated by normalized edge length (~0.40355)
    for (int i = 0; VERTICES.size() > i; i++) {
      for (int j = i + 1; VERTICES.size() > j; j++) {
        double dist = VERTICES.get(i).distanceTo(VERTICES.get(j));
        if (dist > 0.38D && 0.43D > dist) {
          EDGES.add(new int[]{ i, j });
        }
      }
    }

    // 12 Pentagon face normals (icosahedron vertices)
    double icoNorm = Math.sqrt(1.0D + phi * phi);
    addNormalPermutations(PENTAGON_NORMALS, 0.0D, 1.0D, phi, icoNorm);

    // 20 Hexagon face normals (dodecahedron vertices)
    double dodNorm = Math.sqrt(3.0D);
    for (int sx : new int[]{ -1, 1 }) {
      for (int sy : new int[]{ -1, 1 }) {
        for (int sz : new int[]{ -1, 1 }) {
          HEXAGON_NORMALS.add(new Vec3(sx / dodNorm, sy / dodNorm, sz / dodNorm));
        }
      }
    }
    addNormalPermutations(HEXAGON_NORMALS, 0.0D, phi, 1.0D / phi, dodNorm);
    buildOrderedFaces(PENTAGON_NORMALS);
    buildOrderedFaces(HEXAGON_NORMALS);
  }

  private TruncatedIcosahedronCage() {}

  private static void addEvenPermutations(double a, double b, double c, double norm) {
    for (int sa : (a == 0.0D ? new int[]{ 1 } : new int[]{ -1, 1 })) {
      for (int sb : (b == 0.0D ? new int[]{ 1 } : new int[]{ -1, 1 })) {
        for (int sc : (c == 0.0D ? new int[]{ 1 } : new int[]{ -1, 1 })) {
          double x = (a * sa) / norm, y = (b * sb) / norm, z = (c * sc) / norm;
          VERTICES.add(new Vec3(x, y, z));
          VERTICES.add(new Vec3(y, z, x));
          VERTICES.add(new Vec3(z, x, y));
        }
      }
    }
  }

  private static void addNormalPermutations(List<Vec3> target, double a, double b, double c, double norm) {
    for (int sb : new int[]{ -1, 1 }) {
      for (int sc : new int[]{ -1, 1 }) {
        double y = (b * sb) / norm, z = (c * sc) / norm;
        target.add(new Vec3(0.0D, y, z));
        target.add(new Vec3(y, z, 0.0D));
        target.add(new Vec3(z, 0.0D, y));
      }
    }
  }

  public static boolean isOutsideCage(Vec3 playerEyePos, Vec3 cageCenter, float radius) {
    Vec3 rel = playerEyePos.subtract(cageCenter);
    for (FacePlane plane : FACE_PLANES) {
      if (rel.dot(plane.normal()) > radius * plane.unitInradius()) {
        return true;
      }
    }
    return false;
  }

  public static void renderCage(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 camPos, Vec3 cageCenter, float radius, float timeSeconds) {
    poseStack.pushPose();
    poseStack.translate(cageCenter.x - camPos.x, cageCenter.y - camPos.y, cageCenter.z - camPos.z);

    // 1. Cosmic Ocean Nebula shader across all 32 polyhedron faces (12 pentagons + 20 hexagons, no aurora rim)
    com.genesiz.hexacreatecore.threedified.shaders.hooks.PolyhedronFaceShaderHook
      .bind(com.genesiz.hexacreatecore.threedified.shaders.dictionaries.CosmicOceanDictionary.DEFAULT)
      .opacity(0.38f)
      .rings(3)
      .renderFaces(poseStack, bufferSource, FACES, radius, timeSeconds);

    // 2. Dark-nebula vertex nodes at the 60 corners
    for (int i = 0; VERTICES.size() > i; i++) {
      Vec3 v = VERTICES.get(i);
      float pulse = 0.5f + 0.5f * (float) Math.sin((timeSeconds * 2.4f) + (i * 0.35f));
      int nodeCol = (pulse > 0.5f) ? 0xCC7E22CE : 0xCC10B981;
      RawGeometry.drawArcaneNodePrism(
        poseStack, bufferSource,
        (float) (v.x * radius), (float) (v.y * radius), (float) (v.z * radius),
        0.045f, nodeCol
      );
    }

    // 3. Wireframe edges of the 12 pentagons & 20 hexagons
    VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
    Matrix4f mat = poseStack.last().pose();
    float wave = 0.5f + 0.5f * (float) Math.sin(timeSeconds * 2.0f);
    int r = Mth.lerpInt(wave, 88, 16);
    int g = Mth.lerpInt(wave, 28, 185);
    int b = Mth.lerpInt(wave, 135, 129);

    for (int[] edge : EDGES) {
      Vec3 p0 = VERTICES.get(edge[0]);
      Vec3 p1 = VERTICES.get(edge[1]);
      lineConsumer.addVertex(mat, (float) (p0.x * radius), (float) (p0.y * radius), (float) (p0.z * radius))
        .setColor(r, g, b, 195).setNormal(0.0f, 1.0f, 0.0f);
      lineConsumer.addVertex(mat, (float) (p1.x * radius), (float) (p1.y * radius), (float) (p1.z * radius))
        .setColor(r, g, b, 195).setNormal(0.0f, 1.0f, 0.0f);
    }

    poseStack.popPose();
  }

  private static void buildOrderedFaces(List<Vec3> normals) {
    for (Vec3 normal : normals) {
      double maxDot = -Double.MAX_VALUE;
      for (Vec3 v : VERTICES) {
        double d = v.dot(normal);
        if (d > maxDot) maxDot = d;
      }

      List<Vec3> faceVerts = new ArrayList<>(6);
      for (Vec3 v : VERTICES) {
        if (Math.abs(v.dot(normal) - maxDot) < PLANE_EPSILON) {
          faceVerts.add(v);
        }
      }
      if (faceVerts.size() < 3) continue;

      Vec3 center = Vec3.ZERO;
      for (Vec3 v : faceVerts) center = center.add(v);
      final Vec3 faceCenter = center.scale(1.0D / faceVerts.size());

      Vec3 tangentU = faceVerts.get(0).subtract(faceCenter).normalize();
      Vec3 tangentV = normal.cross(tangentU).normalize();

      faceVerts.sort((a, b) -> {
        Vec3 da = a.subtract(faceCenter);
        Vec3 db = b.subtract(faceCenter);
        double angA = Math.atan2(da.dot(tangentV), da.dot(tangentU));
        double angB = Math.atan2(db.dot(tangentV), db.dot(tangentU));
        return Double.compare(angA, angB);
      });

      FACE_PLANES.add(new FacePlane(normal, maxDot));
      FACES.add(List.copyOf(faceVerts));
    }
  }
}
