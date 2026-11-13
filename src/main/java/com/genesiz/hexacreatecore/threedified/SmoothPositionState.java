/*
 * THIS PART OF THE CODE IS MEANT FOR DEVELOPERS
 * */
package com.genesiz.hexacreatecore.threedified;

import net.minecraft.sounds.SoundEvent;

public class SmoothPositionState {
  public double currentX, currentY, currentZ;
  public float currentYaw;
  private boolean initialized = false;

  public float revealedChars = 0.0f;
  private String lastText = "";
  private int lastTicks = 0;

  public void onTicksSynced(int newTicks) {
    if (newTicks > this.lastTicks) {
      this.revealedChars = 0.0f;
    }
    this.lastTicks = newTicks;
  }

  public void resetTypewriter() {
    this.revealedChars = 0.0f;
  }

  public void update(double targetX, double targetY, double targetZ, float targetYaw, float deltaSeconds, String currentText, float charsPerSecond, SoundEvent blipSound) {
    if (!currentText.equals(this.lastText)) {
      this.lastText = currentText;
      this.revealedChars = 0.0f;
    }

    if (this.revealedChars < currentText.length()) {
      //this.revealedChars = Math.min(currentText.length(), this.revealedChars + (charsPerSecond * deltaSeconds));
      float nextChars = Math.min(currentText.length(), this.revealedChars + (charsPerSecond * deltaSeconds));

      if ((int) nextChars > (int) this.revealedChars && blipSound != null) {
        char revealedChar = currentText.charAt((int) nextChars - 1);
        if (!Character.isWhitespace(revealedChar)) {
          float pitch = 0.95f + (float) (Math.random() * 0.1f);
          net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(blipSound, pitch, 0.5f)
          );
        }
      }

      this.revealedChars = nextChars;
    }

    if (!initialized) {
      this.currentX = targetX;
      this.currentY = targetY;
      this.currentZ = targetZ;
      this.currentYaw = targetYaw;
      this.initialized = true;
      return;
    }

    float positionSmoothing = 12.0f;
    float posFactor = 1.0f - (float) Math.exp(-positionSmoothing * deltaSeconds);
    this.currentX += (targetX - this.currentX) * posFactor;
    this.currentY += (targetY - this.currentY) * posFactor;
    this.currentZ += (targetZ - this.currentZ) * posFactor;

    // :TODO: change smoothing through a setter function
    float rotationSmoothing = 5.0f; 
    float rotFactor = 1.0f - (float) Math.exp(-rotationSmoothing * deltaSeconds);

    float yawDelta = targetYaw - this.currentYaw;
    while (yawDelta < -180.0F) yawDelta += 360.0F;
    while (yawDelta >= 180.0F) yawDelta -= 360.0F;

    this.currentYaw += yawDelta * rotFactor;
  }
}

