package com.genesiz.hexacreatecore.hexcasting.everbook;

import at.petrak.hexcasting.api.casting.math.HexDir;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.FastColor;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class EverbookRiftMath {

  public record HitResult(
    int patternIndex,
    boolean inscribeButtonHovered,
    boolean evalButtonHovered,
    int miniGridNodeIndex,
    int totalRows,
    int autoScrollDir
  ) {}

  public record Point2D(float x, float y) {}
  private record AxialStep(int dq, int dr) {}

  public static final float RIFT_HALF_W = 1.70f;
  public static final float RIFT_HALF_H = 1.12f;
  public static final float CELL_SIZE = 0.28f;
  public static final int COLS_PER_ROW = 9;
  public static final float PATTERN_TOP_Y = 0.68f;

  public static final int MINI_COLS = 8;
  public static final int MINI_ROWS = 9;
  public static final float MINI_CENTER_X = 2.25f;
  public static final float MINI_CENTER_Y = 0.0f;
  public static final float MINI_STEP = 0.11f;

  private static final List<AxialStep> AXIAL_STEPS = List.of(
    new AxialStep(1, -1),
    new AxialStep(1, 0),
    new AxialStep(0, 1),
    new AxialStep(-1, 1),
    new AxialStep(-1, 0),
    new AxialStep(0, -1)
  );

  private EverbookRiftMath() {}

  public static Point2D getMiniNodePoint(int rowIndex, int colIndex) {
    float rowShift = (rowIndex % 2 == 1) ? (MINI_STEP * 0.5f) : 0.0f;
    float posX = MINI_CENTER_X + ((colIndex - (MINI_COLS - 1) * 0.5f) * MINI_STEP) + rowShift;
    float posY = MINI_CENTER_Y + (((MINI_ROWS - 1) * 0.5f - rowIndex) * (MINI_STEP * 0.866f));
    return new Point2D(posX, posY);
  }

  public static float[] getMiniNodeLocalPos(int rowIndex, int colIndex) {
    Point2D pt = getMiniNodePoint(rowIndex, colIndex);
    return new float[]{ pt.x(), pt.y() };
  }


  public static void drawPatternStrokes(PoseStack poseStack, VertexConsumer quadConsumer, float centerX, float centerY, float depthZ, float scale, String angles, String startDirName, boolean isSelected, boolean isHovered, int introspectionDepth, float timeSeconds) {
    if (angles == null || angles.isEmpty()) return;
    HexDir startDir;
    try { startDir = HexDir.valueOf(startDirName); } catch (Exception ignored) { startDir = HexDir.EAST; }

    // Exact 60-degree screen-space unit vectors matching HexDir enum order:
    // NORTH_EAST (60°), EAST (0°), SOUTH_EAST (-60°), SOUTH_WEST (-120°), WEST (180°), NORTH_WEST (120°)
    double[] dirAnglesRad = {
      Math.toRadians(60.0D),
      Math.toRadians(0.0D),
      Math.toRadians(-60.0D),
      Math.toRadians(-120.0D),
      Math.toRadians(180.0D),
      Math.toRadians(120.0D)
    };

    List<Point2D> points = new ArrayList<>(angles.length() + 2);
    float curX = 0.0f, curY = 0.0f;
    points.add(new Point2D(curX, curY));

    int dirIdx = startDir.ordinal();
    curX += (float) Math.cos(dirAnglesRad[dirIdx]);
    curY += (float) Math.sin(dirAnglesRad[dirIdx]);
    points.add(new Point2D(curX, curY));

    for (int i = 0; angles.length() > i; i++) {
      int turn = switch (angles.charAt(i)) {
        case 'w' -> 0; // straight
        case 'e' -> 1; // 60° right (clockwise)
        case 'd' -> 2; // 120° right
        case 's' -> 3; // 180° back
        case 'a' -> 4; // 120° left (counter-clockwise)
        default -> 5;  // 'q': 60° left (counter-clockwise)
      };
      dirIdx = Math.floorMod(dirIdx + turn, 6);
      curX += (float) Math.cos(dirAnglesRad[dirIdx]);
      curY += (float) Math.sin(dirAnglesRad[dirIdx]);
      points.add(new Point2D(curX, curY));
    }

    float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
    for (Point2D point : points) {
      minX = Math.min(minX, point.x()); maxX = Math.max(maxX, point.x());
      minY = Math.min(minY, point.y()); maxY = Math.max(maxY, point.y());
    }

    float span = Math.max(1.0f, Math.max(maxX - minX, maxY - minY));
    float midX = (minX + maxX) * 0.5f, midY = (minY + maxY) * 0.5f;
    float normScale = (scale * 2.0f) / span;
    float halfThick = isSelected ? 0.0115f : 0.0095f;

    Matrix4f matrix = poseStack.last().pose();
    for (int i = 0; points.size() - 1 > i; i++) {
      Point2D p0 = points.get(i), p1 = points.get(i + 1);
      float x0 = centerX + (p0.x() - midX) * normScale, y0 = centerY + (p0.y() - midY) * normScale;
      float x1 = centerX + (p1.x() - midX) * normScale, y1 = centerY + (p1.y() - midY) * normScale;

      float dx = x1 - x0, dy = y1 - y0;
      float len = (float) Math.hypot(dx, dy);
      if (len < 1.0e-5f) continue;
      float nx = (-dy / len) * halfThick, ny = (dx / len) * halfThick;

      float wave0 = 0.5f + 0.5f * (float) Math.sin((i * 1.4f) - (timeSeconds * 4.2f) + (centerX * 3.0f));
      float wave1 = 0.5f + 0.5f * (float) Math.sin(((i + 1) * 1.4f) - (timeSeconds * 4.2f) + (centerX * 3.0f));

      int depthPrimary = switch (Math.floorMod(introspectionDepth, 3)) {
        case 1 -> 0xFF00F5FF; // Introspection depth 1: Bright Cyan -> Genesis Red
        case 2 -> 0xFF2563EB; // Introspection depth 2: Deep Blue -> Genesis Red
        default -> 0xFFA855F7; // Depth 0 / 3+: Purple -> Genesis Red
      };

      int col0 = isSelected ? lerpColor(0xFF10B981, 0xFF6EE7B7, wave0)
        : (isHovered ? lerpColor(0xFFFFD700, 0xFFFF1E56, wave0) : lerpColor(depthPrimary, 0xFFFF1E56, wave0));
      int col1 = isSelected ? lerpColor(0xFF10B981, 0xFF6EE7B7, wave1)
        : (isHovered ? lerpColor(0xFFFFD700, 0xFFFF1E56, wave1) : lerpColor(depthPrimary, 0xFFFF1E56, wave1));

      addQuadVertex(quadConsumer, matrix, x0 - nx, y0 - ny, depthZ, col0);
      addQuadVertex(quadConsumer, matrix, x1 - nx, y1 - ny, depthZ, col1);
      addQuadVertex(quadConsumer, matrix, x1 + nx, y1 + ny, depthZ, col1);
      addQuadVertex(quadConsumer, matrix, x0 + nx, y0 + ny, depthZ, col0);
    }
  }


  public static void compileMiniGridStroke(List<Integer> path) {
    if (path == null || path.size() < 2) return;
    StringBuilder anglesBuilder = new StringBuilder();
    HexDir previousDir = null, initialDir = HexDir.EAST;

    for (int i = 0; path.size() - 1 > i; i++) {
      int nodeA = path.get(i), nodeB = path.get(i + 1);
      Point2D ptA = getMiniNodePoint(nodeA / MINI_COLS, nodeA % MINI_COLS);
      Point2D ptB = getMiniNodePoint(nodeB / MINI_COLS, nodeB % MINI_COLS);

      // Snap exact visual screen-space angle (in degrees) to HexDir!
      double deg = Math.toDegrees(Math.atan2(ptB.y() - ptA.y(), ptB.x() - ptA.x()));
      if (0.0D > deg) deg += 360.0D;
      int sector = (int) Math.round(deg / 60.0D) % 6;
      HexDir stepDir = switch (sector) {
        case 0 -> HexDir.EAST;       // 0°
        case 1 -> HexDir.NORTH_EAST; // 60°
        case 2 -> HexDir.NORTH_WEST; // 120°
        case 3 -> HexDir.WEST;       // 180°
        case 4 -> HexDir.SOUTH_WEST; // 240°
        default -> HexDir.SOUTH_EAST;// 300°
      };

      if (i == 0) initialDir = stepDir;
      else if (previousDir != null) {
        int turnDiff = Math.floorMod(stepDir.ordinal() - previousDir.ordinal(), 6);
        anglesBuilder.append(switch (turnDiff) { case 0 -> 'w'; case 1 -> 'e'; case 2 -> 'd'; case 3 -> 's'; case 4 -> 'a'; default -> 'q'; });
      }
      previousDir = stepDir;
    }
    EverbookSessionState.appendDrawnPattern(anglesBuilder.toString(), initialDir.name(), "Sigil");
  }


  public static HitResult raycastRiftWorkspace(Vec3 eyePos, Vec3 lookVec, Vec3 anchorPos, float yaw, float pitch, List<EverbookData.PatternEntry> entries, float scrollOffset) {
    int totalRows = Math.max(1, (int) Math.ceil(entries.size() / (double) COLS_PER_ROW));
    Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(-yaw - 180.0f)).rotateX((float) Math.toRadians(-pitch));
    Quaternionf invRotation = new Quaternionf(rotation).conjugate();

    Vector3f localOrigin = new Vector3f((float) (eyePos.x - anchorPos.x), (float) (eyePos.y - anchorPos.y), (float) (eyePos.z - anchorPos.z));
    invRotation.transform(localOrigin);
    Vector3f localDir = new Vector3f((float) lookVec.x, (float) lookVec.y, (float) lookVec.z);
    invRotation.transform(localDir);

    if (Math.abs(localDir.z) < 1.0e-5f) return new HitResult(-1, false, false, -1, totalRows, 0);
    float hitDistance = -localOrigin.z / localDir.z;
    if (hitDistance <= 0.0f) return new HitResult(-1, false, false, -1, totalRows, 0);

    float hitX = localOrigin.x + (localDir.x * hitDistance);
    float hitY = localOrigin.y + (localDir.y * hitDistance);
    boolean inscribeHovered = Math.abs(hitX - (-2.42f)) < 0.44f && Math.abs(hitY - 0.72f) < 0.15f;
    boolean evalHovered = Math.abs(hitX - (-2.42f)) < 0.44f && Math.abs(hitY - 0.36f) < 0.15f;

    int hitMiniNode = -1;
    float bestMiniDistSq = 0.055f * 0.055f;
    for (int row = 0; MINI_ROWS > row; row++) {
      for (int col = 0; MINI_COLS > col; col++) {
        Point2D nodePos = getMiniNodePoint(row, col);
        float dx = hitX - nodePos.x();
        float dy = hitY - nodePos.y();
        float distSq = dx * dx + dy * dy;
        if (bestMiniDistSq > distSq) {
          bestMiniDistSq = distSq;
          hitMiniNode = (row * MINI_COLS) + col;
        }
      }
    }

    int autoScroll = (Math.abs(hitX) < RIFT_HALF_W) ? ((hitY > 0.78f) ? -1 : ((-0.82f > hitY) ? 1 : 0)) : 0;
    int hitPattern = -1;
    float startX = -((COLS_PER_ROW - 1) * CELL_SIZE) * 0.5f;
    for (int i = 0; entries.size() > i; i++) {
      float visibleRow = (i / COLS_PER_ROW) - scrollOffset;
      if (-0.6f > visibleRow || visibleRow > 5.2f) continue;
      float cellX = startX + ((i % COLS_PER_ROW) * CELL_SIZE);
      float cellY = PATTERN_TOP_Y - (visibleRow * CELL_SIZE);
      if (Math.abs(hitX - cellX) < (CELL_SIZE * 0.46f) && Math.abs(hitY - cellY) < (CELL_SIZE * 0.46f)) {
        hitPattern = i;
        break;
      }
    }
    return new HitResult(hitPattern, inscribeHovered, evalHovered, hitMiniNode, totalRows, autoScroll);
  }

  public static void drawBoldAbsolutionPattern(PoseStack poseStack, VertexConsumer quadConsumer, float centerX, float centerY, float depthZ, float scale, String angles, String startDirName, boolean isSelected, boolean isHovered, float timeSeconds) {
    if (angles == null || angles.isEmpty()) return;
    HexDir startDir;
    try { startDir = HexDir.valueOf(startDirName); } catch (Exception ignored) { startDir = HexDir.EAST; }

    List<Point2D> points = new ArrayList<>(angles.length() + 2);
    float axialQ = 0.0f, axialR = 0.0f;
    points.add(new Point2D(0.0f, 0.0f));
    int currentOrdinal = startDir.ordinal();
    AxialStep firstStep = AXIAL_STEPS.get(currentOrdinal);
    axialQ += firstStep.dq(); axialR += firstStep.dr();
    points.add(new Point2D((float) (axialQ + axialR * 0.5D), (float) (-axialR * 0.8660254D)));

    for (int i = 0; angles.length() > i; i++) {
      int turnOffset = switch (angles.charAt(i)) { case 'w' -> 0; case 'e' -> 1; case 'd' -> 2; case 's' -> 3; case 'a' -> 4; default -> 5; };
      currentOrdinal = Math.floorMod(currentOrdinal + turnOffset, 6);
      AxialStep step = AXIAL_STEPS.get(currentOrdinal);
      axialQ += step.dq(); axialR += step.dr();
      points.add(new Point2D((float) (axialQ + axialR * 0.5D), (float) (-axialR * 0.8660254D)));
    }

    float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
    for (Point2D pt : points) {
      minX = Math.min(minX, pt.x()); maxX = Math.max(maxX, pt.x());
      minY = Math.min(minY, pt.y()); maxY = Math.max(maxY, pt.y());
    }
    float span = Math.max(1.0f, Math.max(maxX - minX, maxY - minY));
    float midX = (minX + maxX) * 0.5f, midY = (minY + maxY) * 0.5f;
    float normScale = (scale * 2.0f) / span;

    // Outer glow color: Green when selected, warm gold when hovered, deep purple-crimson normally
    int glowArgb = isSelected ? 0x8810B981 : (isHovered ? 0x77FFD700 : 0x557E22CE);
    Matrix4f matrix = poseStack.last().pose();

    for (int i = 0; points.size() - 1 > i; i++) {
      Point2D p0 = points.get(i), p1 = points.get(i + 1);
      float x0 = centerX + (p0.x() - midX) * normScale, y0 = centerY + (p0.y() - midY) * normScale;
      float x1 = centerX + (p1.x() - midX) * normScale, y1 = centerY + (p1.y() - midY) * normScale;

      float wave0 = 0.5f + 0.5f * (float) Math.sin((i * 1.1f) - (timeSeconds * 4.2f));
      float wave1 = 0.5f + 0.5f * (float) Math.sin(((i + 1) * 1.1f) - (timeSeconds * 4.2f));
      int core0 = lerpColor(0xFFA855F7, 0xFFFF4D79, wave0); // Purple -> Genesis Light Red
      int core1 = lerpColor(0xFFA855F7, 0xFFFF4D79, wave1);

      emitRibbonSegment(quadConsumer, matrix, x0, y0, x1, y1, depthZ, 0.022f, glowArgb, glowArgb);
      emitRibbonSegment(quadConsumer, matrix, x0, y0, x1, y1, depthZ + 0.002f, 0.011f, core0, core1);
    }
  }

  private static void emitRibbonSegment(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, float halfThick, int c0, int c1) {
    float dx = x1 - x0, dy = y1 - y0;
    float len = (float) Math.hypot(dx, dy);
    if (len < 1.0e-5f) return;
    float nx = (-dy / len) * halfThick, ny = (dx / len) * halfThick;
    vc.addVertex(m, x0 - nx, y0 - ny, z).setColor(FastColor.ARGB32.red(c0), FastColor.ARGB32.green(c0), FastColor.ARGB32.blue(c0), FastColor.ARGB32.alpha(c0));
    vc.addVertex(m, x0 + nx, y0 + ny, z).setColor(FastColor.ARGB32.red(c0), FastColor.ARGB32.green(c0), FastColor.ARGB32.blue(c0), FastColor.ARGB32.alpha(c0));
    vc.addVertex(m, x1 + nx, y1 + ny, z).setColor(FastColor.ARGB32.red(c1), FastColor.ARGB32.green(c1), FastColor.ARGB32.blue(c1), FastColor.ARGB32.alpha(c1));
    vc.addVertex(m, x1 - nx, y1 - ny, z).setColor(FastColor.ARGB32.red(c1), FastColor.ARGB32.green(c1), FastColor.ARGB32.blue(c1), FastColor.ARGB32.alpha(c1));
  }

  private static void addQuadVertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, int argb) {
    vc.addVertex(m, x, y, z).setColor(FastColor.ARGB32.red(argb), FastColor.ARGB32.green(argb), FastColor.ARGB32.blue(argb), FastColor.ARGB32.alpha(argb));
  }

  private static int lerpColor(int c0, int c1, float t) {
    int a = (int) (FastColor.ARGB32.alpha(c0) + (FastColor.ARGB32.alpha(c1) - FastColor.ARGB32.alpha(c0)) * t);
    int r = (int) (FastColor.ARGB32.red(c0) + (FastColor.ARGB32.red(c1) - FastColor.ARGB32.red(c0)) * t);
    int g = (int) (FastColor.ARGB32.green(c0) + (FastColor.ARGB32.green(c1) - FastColor.ARGB32.green(c0)) * t);
    int b = (int) (FastColor.ARGB32.blue(c0) + (FastColor.ARGB32.blue(c1) - FastColor.ARGB32.blue(c0)) * t);
    return FastColor.ARGB32.color(a, r, g, b);
  }

}
