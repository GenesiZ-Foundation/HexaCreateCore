package com.genesiz.hexacreatecore.dialogue;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class DialogueRaycaster {
  // Reusable scratch objects (zero per-frame heap allocations)
  private static final Matrix4f INV_MATRIX = new Matrix4f();
  private static final Vector4f LOCAL_ORIGIN = new Vector4f();
  private static final Vector4f LOCAL_DIR = new Vector4f();

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
}
