package com.genesiz.hexacreatecore.threedified.threedishapes_renderers;

import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.genesiz.hexacreatecore.threedified.threedishapes_factory.ShapeBlueprint;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FastColor;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ThreeDiShapeRendererRegistry {

  private static final Map<String, IShapeRenderer> RENDERERS = new ConcurrentHashMap<>();

  static {
    register("corner_brackets", ThreeDiShapeRendererRegistry::renderCornerBrackets);
    register("floating_crystal", ThreeDiShapeRendererRegistry::renderFloatingCrystal);
    register("arcane_ring", ThreeDiShapeRendererRegistry::renderArcaneRing);
    register("side_wings", ThreeDiShapeRendererRegistry::renderSideWings);
    register("kingdom_crown", ThreeDiShapeRendererRegistry::renderKingdomCrown);
    register("interlaced_chains", ThreeDiShapeRendererRegistry::renderInterlacedChains);
  }

  private ThreeDiShapeRendererRegistry() {}

  public static void register(String id, IShapeRenderer renderer) {
    RENDERERS.put(id, renderer);
  }

  public static void renderBlueprint(PoseStack poseStack, MultiBufferSource bufferSource, ShapeBlueprint bp, float boxW, float boxH, float timeSec) {
    IShapeRenderer renderer = RENDERERS.get(bp.rendererId());
    if (renderer != null) {
      renderer.render(poseStack, bufferSource, bp, boxW, boxH, timeSec);
    }
  }

  private static void renderCornerBrackets(PoseStack ps, MultiBufferSource buf, ShapeBlueprint bp, float boxW, float boxH, float timeSec) {
    VertexConsumer vc = buf.getBuffer(RawGeometry.DIALOGUE_SOLID);
    float hw = boxW * 0.5f;
    float hh = boxH * 0.5f;
    float len = bp.size();
    float thick = Math.max(1.5f, bp.size() * 0.22f);
    float d = bp.depth();

    int[] signsX = { -1, 1, -1, 1 };
    int[] signsY = { -1, -1, 1, 1 };
    for (int c = 0; 4 > c; c++) {
      float sx = signsX[c];
      float sy = signsY[c];
      float cx = sx * (hw + 1.0f);
      float cy = sy * (hh + 1.0f);

      ps.pushPose();
      ps.translate(cx + bp.offsetX(), cy + bp.offsetY(), bp.offsetZ());
      Matrix4f mat = ps.last().pose();
      // Horizontal arm
      drawExtrudedBox(vc, mat, 0.0f, 0.0f, -sx * len, -sy * thick, d, bp.primaryColor(), bp.secondaryColor());
      // Vertical arm
      drawExtrudedBox(vc, mat, 0.0f, 0.0f, -sx * thick, -sy * len, d, bp.primaryColor(), bp.secondaryColor());
      ps.popPose();
    }
  }

  private static void renderFloatingCrystal(PoseStack ps, MultiBufferSource buf, ShapeBlueprint bp, float boxW, float boxH, float timeSec) {
    VertexConsumer vc = buf.getBuffer(RawGeometry.DIALOGUE_SOLID);
    float ax = (bp.anchor().normX * boxW) + bp.offsetX();
    float ay = (bp.anchor().normY * boxH) + bp.offsetY() + (float) Math.sin(timeSec * 3.2f) * 1.5f;

    ps.pushPose();
    ps.translate(ax, ay, bp.offsetZ());
    ps.mulPose(Axis.YP.rotationDegrees((timeSec * bp.spinSpeedDegPerSec()) % 360.0f));
    Matrix4f mat = ps.last().pose();

    float r = bp.size() * 0.5f;
    float h = bp.size() * 0.85f;
    int sides = Math.max(3, bp.segments());
    for (int i = 0; sides > i; i++) {
      double a0 = (Math.PI * 2.0D * i) / sides;
      double a1 = (Math.PI * 2.0D * (i + 1)) / sides;
      float x0 = (float) Math.cos(a0) * r;
      float z0 = (float) Math.sin(a0) * r;
      float x1 = (float) Math.cos(a1) * r;
      float z1 = (float) Math.sin(a1) * r;

      // Upper pyramid facet
      drawTri(vc, mat, 0.0f, -h, 0.0f, x0, 0.0f, z0, x1, 0.0f, z1, bp.primaryColor());
      // Lower pyramid facet
      drawTri(vc, mat, 0.0f, h, 0.0f, x1, 0.0f, z1, x0, 0.0f, z0, bp.secondaryColor());
    }
    ps.popPose();
  }

  private static void renderArcaneRing(PoseStack ps, MultiBufferSource buf, ShapeBlueprint bp, float boxW, float boxH, float timeSec) {
    VertexConsumer vc = buf.getBuffer(RawGeometry.DIALOGUE_SOLID);
    ps.pushPose();
    ps.translate(bp.offsetX(), bp.offsetY(), bp.offsetZ());
    ps.mulPose(Axis.ZP.rotationDegrees((timeSec * bp.spinSpeedDegPerSec()) % 360.0f));
    Matrix4f mat = ps.last().pose();

    float outerR = bp.size();
    float innerR = Math.max(2.0f, outerR - Math.max(2.0f, bp.depth()));
    int segs = bp.segments();
    for (int i = 0; segs > i; i++) {
      if (i % 2 == 1) continue; // Segmented rune gaps
      double a0 = (Math.PI * 2.0D * i) / segs;
      double a1 = (Math.PI * 2.0D * (i + 0.85D)) / segs;
      float ox0 = (float) Math.cos(a0) * outerR, oy0 = (float) Math.sin(a0) * outerR;
      float ox1 = (float) Math.cos(a1) * outerR, oy1 = (float) Math.sin(a1) * outerR;
      float ix0 = (float) Math.cos(a0) * innerR, iy0 = (float) Math.sin(a0) * innerR;
      float ix1 = (float) Math.cos(a1) * innerR, iy1 = (float) Math.sin(a1) * innerR;

      drawQuad(vc, mat, ix0, iy0, 0.0f, ox0, oy0, 0.0f, ox1, oy1, 0.0f, ix1, iy1, 0.0f, bp.primaryColor(), bp.secondaryColor());
    }
    ps.popPose();
  }

  private static void renderSideWings(PoseStack ps, MultiBufferSource buf, ShapeBlueprint bp, float boxW, float boxH, float timeSec) {
    VertexConsumer vc = buf.getBuffer(RawGeometry.DIALOGUE_SOLID);
    float hw = boxW * 0.5f;
    float span = bp.size();
    float wingH = Math.min(boxH * 0.85f, span * 0.9f);

    for (int side : new int[]{ -1, 1 }) {
      ps.pushPose();
      ps.translate(side * (hw + 1.5f) + bp.offsetX(), bp.offsetY(), bp.offsetZ());
      Matrix4f mat = ps.last().pose();
      // Upper swept blade
      drawTri(vc, mat, 0.0f, -wingH * 0.5f, 0.0f, side * span, -wingH * 0.85f, bp.depth(), side * (span * 0.65f), 0.0f, 0.0f, bp.primaryColor());
      // Lower swept blade
      drawTri(vc, mat, 0.0f, wingH * 0.5f, 0.0f, side * (span * 0.65f), 0.0f, 0.0f, side * span, wingH * 0.85f, bp.depth(), bp.secondaryColor());
      ps.popPose();
    }
  }

  private static void drawQuad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, int c0, int c1) {
    addV(vc, m, x0, y0, z0, c0);
    addV(vc, m, x1, y1, z1, c0);
    addV(vc, m, x2, y2, z2, c1);
    addV(vc, m, x3, y3, z3, c1);
  }

  private static void drawTri(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, int col) {
    addV(vc, m, x0, y0, z0, col);
    addV(vc, m, x1, y1, z1, col);
    addV(vc, m, x2, y2, z2, col);
    addV(vc, m, x2, y2, z2, col);
  }

  private static void addV(VertexConsumer vc, Matrix4f m, float x, float y, float z, int argb) {
    vc.addVertex(m, x, y, z).setColor(
      FastColor.ARGB32.red(argb), FastColor.ARGB32.green(argb), FastColor.ARGB32.blue(argb), FastColor.ARGB32.alpha(argb)
    );
  }

  private static void renderKingdomCrown(PoseStack ps, MultiBufferSource buf, ShapeBlueprint bp, float boxW, float boxH, float timeSec) {
    VertexConsumer vc = buf.getBuffer(RawGeometry.DIALOGUE_SOLID);
    float ax = (bp.anchor().normX * boxW) + bp.offsetX();
    float ay = (bp.anchor().normY * boxH) + bp.offsetY();

    ps.pushPose();
    ps.translate(ax, ay, 0.0f);

    int blackstoneDeep = 0xFF17131A;
    int blackstoneMid = 0xFF2B2532;
    int blackstoneLight = 0xFF484052;
    int eyeGold = 0xFFFFD700;
    int eyeBright = 0xFFFFF59D;

    float halfW = bp.size() * 0.48f;
    float totalH = bp.size() * 0.34f;
    float d = 3.2f;

    // 1. Five Stepped Pixel-Art Blackstone Spires (clean open silhouette — no rectangular backplate)
    float[] spireX = { -halfW * 0.80f, -halfW * 0.43f, 0.0f, halfW * 0.43f, halfW * 0.80f };
    float[] spireHalfW = { 2.6f, 3.6f, 4.8f, 3.6f, 2.6f };
    float[] spireTopY = { -totalH * 0.66f, -totalH * 0.86f, -totalH * 1.05f, -totalH * 0.86f, -totalH * 0.66f };
    float bandTopY = -totalH * 0.34f;

    Matrix4f spireMat = ps.last().pose();
    for (int s = 0; 5 > s; s++) {
      float sx = spireX[s];
      float sw = spireHalfW[s];
      float topY = spireTopY[s];
      float midY = bandTopY + ((topY - bandTopY) * 0.62f);

      drawExtrudedBox(vc, spireMat, sx - sw, midY, sx + sw, bandTopY + 0.2f, d, blackstoneMid, blackstoneLight);
      float tipW = sw * 0.55f;
      drawExtrudedBox(vc, spireMat, sx - tipW, topY, sx + tipW, midY + 0.4f, d, blackstoneLight, blackstoneMid);
    }

    // 2. Lower Blackstone Brow Band (offset in Z so front/back never Z-fight with spires)
    ps.pushPose();
    ps.translate(0.0f, 0.0f, -0.15f);
    Matrix4f bandMat = ps.last().pose();
    drawExtrudedBox(vc, bandMat, -halfW, bandTopY, halfW, 0.0f, d + 0.30f, blackstoneDeep, blackstoneMid);
    drawExtrudedBox(vc, bandMat, -halfW - 0.5f, bandTopY - 1.2f, halfW + 0.5f, bandTopY + 0.6f, d + 0.45f, blackstoneLight, blackstoneMid);
    ps.popPose();

    // 3. Glowing Golden Crown Eyes
    float eyeY0 = -totalH * 0.22f;
    float eyeY1 = -totalH * 0.08f;
    ps.pushPose();
    ps.translate(0.0f, 0.0f, d + 0.22f);
    Matrix4f eyeMat = ps.last().pose();
    drawExtrudedBox(vc, eyeMat, -halfW * 0.62f, eyeY0, -halfW * 0.28f, eyeY1, 0.35f, eyeGold, eyeBright);
    drawExtrudedBox(vc, eyeMat, halfW * 0.28f, eyeY0, halfW * 0.62f, eyeY1, 0.35f, eyeGold, eyeBright);
    ps.popPose();

    // 4. Five Graduated Genesis Crimson & Blood-Red 3D Diamond Gems across the front
    float[] gemScale = { 0.55f, 0.82f, 1.20f, 0.82f, 0.55f };
    float[] gemY = { -totalH * 0.44f, -totalH * 0.48f, -totalH * 0.52f, -totalH * 0.48f, -totalH * 0.44f };
    float pulse = 0.85f + 0.15f * (float) Math.sin(timeSec * 4.0f);
    int gemBright = scaleColor(0xFFFF1E56, pulse);
    int gemBlood = 0xFF7F0019;

    Matrix4f gemMat = ps.last().pose();
    for (int g = 0; 5 > g; g++) {
      float gx = spireX[g], gy = gemY[g];
      float gw = totalH * 0.16f * gemScale[g];
      float gh = totalH * 0.24f * gemScale[g];
      float gz = d + 1.3f;

      drawTri(vc, gemMat, gx, gy - gh, gz, gx - gw, gy, d + 0.2f, gx, gy, gz + 0.6f, gemBright);
      drawTri(vc, gemMat, gx, gy - gh, gz, gx, gy, gz + 0.6f, gx + gw, gy, d + 0.2f, gemBlood);
      drawTri(vc, gemMat, gx - gw, gy, d + 0.2f, gx, gy + gh, gz, gx, gy, gz + 0.6f, gemBlood);
      drawTri(vc, gemMat, gx, gy, gz + 0.6f, gx, gy + gh, gz, gx + gw, gy, d + 0.2f, gemBright);
    }

    ps.popPose();
  }

  private static void renderInterlacedChains(PoseStack ps, MultiBufferSource buf, ShapeBlueprint bp, float boxW, float boxH, float timeSec) {
    VertexConsumer vc = buf.getBuffer(RawGeometry.DIALOGUE_SOLID);
    ps.pushPose();
    ps.translate(bp.offsetX(), bp.offsetY(), 1.5f); // Centered at the mid-depth of the 3.0f box

    float pad = bp.size() + 4.5f;
    float halfW = (boxW * 0.5f) + pad;
    float halfH = (boxH * 0.5f) + pad;
    float cornerR = Math.min(18.0f, Math.min(halfW, halfH) * 0.45f);
    float straightW = Math.max(0.0f, (halfW - cornerR) * 2.0f);
    float straightH = Math.max(0.0f, (halfH - cornerR) * 2.0f);
    float arcLen = (float) (Math.PI * 0.5D * cornerR);
    float perimeter = (2.0f * straightW) + (2.0f * straightH) + (4.0f * arcLen);

    float linkSize = bp.depth();
    float linkPitch = linkSize * 2.05f;
    int linkCount = Math.max(20, Math.round(perimeter / linkPitch));
    float actualPitch = perimeter / linkCount;

    // DNA double-helix parameters: 8 full twists around the box, spinning smoothly over time
    double helixTwists = 8.0D;
    double spinPhase = timeSec * Math.toRadians(bp.spinSpeedDegPerSec() * 3.5D);
    float helixRadius = linkSize * 1.95f;

    int brightCol = bp.primaryColor();
    int darkCol = bp.secondaryColor();
    float linkDepth = 1.8f;

    for (int strand = 0; 2 > strand; strand++) {
      double strandPhaseOffset = strand * Math.PI; // 180 degrees apart like DNA strands

      for (int i = 0; linkCount > i; i++) {
        float dist = i * actualPitch;
        float[] pt = sampleRoundedRect(dist, halfW, halfH, cornerR, straightW, straightH, arcLen);
        float px = pt[0], py = pt[1], nx = pt[2], ny = pt[3], tangentDeg = pt[4];

        // True 3D DNA helix: spirals outward/inward (normal) AND forward/backward (Z) around the perimeter
        double helixAngle = ((Math.PI * 2.0D * helixTwists * i) / linkCount) + strandPhaseOffset + spinPhase;
        float radialOffset = (float) Math.cos(helixAngle) * helixRadius;
        float zOffset = (float) Math.sin(helixAngle) * helixRadius;

        ps.pushPose();
        ps.translate(px + (nx * radialOffset), py + (ny * radialOffset), zOffset);
        ps.mulPose(Axis.ZP.rotationDegrees(tangentDeg));
        Matrix4f m = ps.last().pose();

        float halfL = actualPitch * 0.68f;
        float halfThick = linkSize * 0.72f;
        float bar = Math.max(1.0f, linkSize * 0.35f);

        if (i % 2 == 0) {
          drawExtrudedBox(vc, m, -halfL, -halfThick, halfL, -halfThick + bar, linkDepth, brightCol, darkCol);
          drawExtrudedBox(vc, m, -halfL, halfThick - bar, halfL, halfThick, linkDepth, darkCol, brightCol);
          drawExtrudedBox(vc, m, -halfL, -halfThick, -halfL + bar, halfThick, linkDepth, brightCol, darkCol);
          drawExtrudedBox(vc, m, halfL - bar, -halfThick, halfL, halfThick, linkDepth, darkCol, brightCol);
        } else {
          float edgeHalf = bar * 0.60f;
          drawExtrudedBox(vc, m, -halfL * 1.12f, -edgeHalf, halfL * 1.12f, edgeHalf, linkDepth * 1.2f, brightCol, darkCol);
        }

        ps.popPose();
      }
    }

    ps.popPose();
  }


  private static void drawExtrudedBox(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float d, int c0, int c1) {
    float minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
    float minY = Math.min(y0, y1), maxY = Math.max(y0, y1);
    // Front & Back
    drawQuad(vc, m, minX, minY, d, maxX, minY, d, maxX, maxY, d, minX, maxY, d, c0, c1);
    drawQuad(vc, m, maxX, minY, 0, minX, minY, 0, minX, maxY, 0, maxX, maxY, 0, c1, c0);
    // Top & Bottom
    drawQuad(vc, m, minX, minY, 0, minX, minY, d, maxX, minY, d, maxX, minY, 0, c1, c0);
    drawQuad(vc, m, minX, maxY, 0, maxX, maxY, 0, maxX, maxY, d, minX, maxY, d, c1, c0);
    // Left & Right end-caps (essential for F5 side angles!)
    drawQuad(vc, m, minX, minY, 0, minX, maxY, 0, minX, maxY, d, minX, minY, d, c0, c1);
    drawQuad(vc, m, maxX, minY, 0, maxX, minY, d, maxX, maxY, d, maxX, maxY, 0, c0, c1);
  }


  /** Returns [x, y, outwardNormalX, outwardNormalY, tangentAngleDeg] along a rounded-rectangle perimeter */
  private static float[] sampleRoundedRect(float d, float hw, float hh, float r, float sw, float sh, float arc) {
    float innerW = hw - r;
    float innerH = hh - r;
    // 1. Top edge (left -> right)
    if (sw > d) {
      return new float[]{ -innerW + d, -hh, 0.0f, -1.0f, 0.0f };
    }
    d -= sw;
    // 2. Top-right corner
    if (arc > d) {
      double a = -Math.PI * 0.5D + (d / r);
      float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
      return new float[]{ innerW + ca * r, -innerH + sa * r, ca, sa, (float) Math.toDegrees(a + Math.PI * 0.5D) };
    }
    d -= arc;
    // 3. Right edge (top -> bottom)
    if (sh > d) {
      return new float[]{ hw, -innerH + d, 1.0f, 0.0f, 90.0f };
    }
    d -= sh;
    // 4. Bottom-right corner
    if (arc > d) {
      double a = (d / r);
      float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
      return new float[]{ innerW + ca * r, innerH + sa * r, ca, sa, (float) Math.toDegrees(a + Math.PI * 0.5D) };
    }
    d -= arc;
    // 5. Bottom edge (right -> left)
    if (sw > d) {
      return new float[]{ innerW - d, hh, 0.0f, 1.0f, 180.0f };
    }
    d -= sw;
    // 6. Bottom-left corner
    if (arc > d) {
      double a = Math.PI * 0.5D + (d / r);
      float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
      return new float[]{ -innerW + ca * r, innerH + sa * r, ca, sa, (float) Math.toDegrees(a + Math.PI * 0.5D) };
    }
    d -= arc;
    // 7. Left edge (bottom -> top)
    if (sh > d) {
      return new float[]{ -hw, innerH - d, -1.0f, 0.0f, 270.0f };
    }
    d -= sh;
    // 8. Top-left corner
    double a = Math.PI + (d / r);
    float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
    return new float[]{ -innerW + ca * r, -innerH + sa * r, ca, sa, (float) Math.toDegrees(a + Math.PI * 0.5D) };
  }

  private static int scaleColor(int argb, float factor) {
    int a = FastColor.ARGB32.alpha(argb);
    int r = Math.min(255, Math.round(FastColor.ARGB32.red(argb) * factor));
    int g = Math.min(255, Math.round(FastColor.ARGB32.green(argb) * factor));
    int b = Math.min(255, Math.round(FastColor.ARGB32.blue(argb) * factor));
    return FastColor.ARGB32.color(a, r, g, b);
  }
}

