package com.genesiz.hexacreatecore.virtual;

public class SmoothPositionState {
  public double currentX, currentY, currentZ;
  public float currentYaw;
  private boolean initialized = false;

  public void update(double targetX, double targetY, double targetZ, float targetYaw, float deltaSeconds) {
    if (!initialized) {
      this.currentX = targetX;
      this.currentY = targetY;
      this.currentZ = targetZ;
      this.currentYaw = targetYaw; // Snap immediately on instantiation
      this.initialized = true;
      return;
    }

    float positionSmoothing = 12.0f;
    float posFactor = 1.0f - (float) Math.exp(-positionSmoothing * deltaSeconds);
    this.currentX += (targetX - this.currentX) * posFactor;
    this.currentY += (targetY - this.currentY) * posFactor;
    this.currentZ += (targetZ - this.currentZ) * posFactor;

    // --- EXPONENTIAL EASE-OUT FOR ROTATION YAW ---
    // A slower speed factor (e.g., 5.0f instead of 12.0f) forces the panel 
    // to lag slightly behind the player's movement, exposing the 3D prisms
    float rotationSmoothing = 5.0f; 
    float rotFactor = 1.0f - (float) Math.exp(-rotationSmoothing * deltaSeconds);

    // Standardize the delta tracking across the -180 to 180 wrapping thresholds
    float yawDelta = targetYaw - this.currentYaw;
    while (yawDelta < -180.0F) yawDelta += 360.0F;
    while (yawDelta >= 180.0F) yawDelta -= 360.0F;

    this.currentYaw += yawDelta * rotFactor;
  }
}

