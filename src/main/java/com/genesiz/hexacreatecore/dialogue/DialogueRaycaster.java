package com.genesiz.hexacreatecore.dialogue;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class DialogueRaycaster {
  // Reusable scratch objects (zero per-frame heap allocations)
  private static final Matrix4f INV_MATRIX = new Matrix4f();
  private static final Vector4f LOCAL_ORIGIN = new Vector4f();
  private static final Vector4f LOCAL_DIR = new Vector4f();

  private static final Vector3f SCRATCH_CENTER = new Vector3f();
  private static final Vector3f SCRATCH_NORMAL = new Vector3f();
  private static final Vector3f SCRATCH_HIT = new Vector3f();
  private static final Matrix4f SCRATCH_INV = new Matrix4f();

  private DialogueRaycaster() {}

  public static boolean isLookingAtNode(
    Matrix4f canvasPose,
    Vector3f cameraLookDir,
    float localX,
    float localY,
    float localZ,
    float width,
    float height,
    double maxReachBlocks
  ) {
    // Invert the canvas model matrix to transform the world ray into local UI pixel space
    canvasPose.invert(INV_MATRIX);

    LOCAL_ORIGIN.set(0.0f, 0.0f, 0.0f, 1.0f).mul(INV_MATRIX);
    LOCAL_DIR.set(cameraLookDir.x, cameraLookDir.y, cameraLookDir.z, 0.0f).mul(INV_MATRIX);

    if (Math.abs(LOCAL_DIR.z) < 1e-6f) return false; // Parallel to panel

    float t = (localZ - LOCAL_ORIGIN.z) / LOCAL_DIR.z;
    if (t < 0.0f) return false; // Behind the player's camera

    float hitX = LOCAL_ORIGIN.x + LOCAL_DIR.x * t;
    float hitY = LOCAL_ORIGIN.y + LOCAL_DIR.y * t;

    float halfW = width * 0.5f;
    float halfH = height * 0.5f;

    boolean insideBounds = Math.abs(hitX - localX) <= halfW && Math.abs(hitY - localY) <= halfH;
    if (!insideBounds) return false;

    double worldDistSq = (t * t) * (LOCAL_DIR.x * LOCAL_DIR.x + LOCAL_DIR.y * LOCAL_DIR.y + LOCAL_DIR.z * LOCAL_DIR.z);
    return (worldDistSq * 0.000225D) <= (maxReachBlocks * maxReachBlocks);
  }

  public static boolean isLookingAtNode(
    Matrix4f canvasMatrix,
    float rayOriginX, float rayOriginY, float rayOriginZ,
    Vector3f lookDir,
    float localCenterX, float localCenterY, float localCenterZ,
    float width, float height,
    double maxReach
  ) {
    // 1. Transform node center and normal into camera-relative world space
    SCRATCH_CENTER.set(localCenterX, localCenterY, localCenterZ);
    canvasMatrix.transformPosition(SCRATCH_CENTER);

    SCRATCH_NORMAL.set(0.0f, 0.0f, 1.0f);
    canvasMatrix.transformDirection(SCRATCH_NORMAL).normalize();

    // 2. Ray-plane intersection starting from the PLAYER'S EYES (not the F5 camera!)
    float denom = SCRATCH_NORMAL.dot(lookDir);
    if (Math.abs(denom) < 1e-5f) return false;

    float toPlaneX = SCRATCH_CENTER.x - rayOriginX;
    float toPlaneY = SCRATCH_CENTER.y - rayOriginY;
    float toPlaneZ = SCRATCH_CENTER.z - rayOriginZ;

    float t = (toPlaneX * SCRATCH_NORMAL.x + toPlaneY * SCRATCH_NORMAL.y + toPlaneZ * SCRATCH_NORMAL.z) / denom;

    // 3. Reject if the button is BEHIND the player's eyes (t <= 0) or beyond 5-block player reach!
    if (0.0f >= t || t > maxReach) return false;

    // 4. Compute hit point in camera-relative world space and transform into local button space
    SCRATCH_HIT.set(
      rayOriginX + lookDir.x * t,
      rayOriginY + lookDir.y * t,
      rayOriginZ + lookDir.z * t
    );

    canvasMatrix.invert(SCRATCH_INV);
    SCRATCH_INV.transformPosition(SCRATCH_HIT);

    float halfW = width * 0.5f;
    float halfH = height * 0.5f;
    return Math.abs(SCRATCH_HIT.x - localCenterX) <= halfW
    && Math.abs(SCRATCH_HIT.y - localCenterY) <= halfH;
  }
}
