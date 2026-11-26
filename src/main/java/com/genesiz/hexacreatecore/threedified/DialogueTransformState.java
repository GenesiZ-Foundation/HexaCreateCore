/*
 * THIS PART OF THE CODE IS MEANT FOR DEVELOPERS
 * */
package com.genesiz.hexacreatecore.threedified;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/* FULL BREAKDOWN:
 *
 * -> Smoothly tracks and interpolates an entity's 3D dialogue anchor position & facing yaw
 *
 * [.update()] computes target position with orbit offset toward the viewer, exponentially smooths
 *   currentX/Y/Z and currentYaw, and writes camera-relative coords into renderX/Y/Z
 *
 * */
public class DialogueTransformState {
  public   double   currentX, currentY, currentZ;
  public   float    currentYaw;
  private  boolean  initialized = false;

  // Reusable output fields (avoids returning new Vec3 or record allocations)
  public   double   renderX, renderY, renderZ;

  public void update(Entity entity, float partialTick, double orbit, Vec3 facing, Vec3 camera, float targetYaw, float deltaSeconds) {
    double entX = Mth.lerp(partialTick, entity.xo, entity.getX());
    double entY = Mth.lerp(partialTick, entity.yo, entity.getY());
    double entZ = Mth.lerp(partialTick, entity.zo, entity.getZ());

    double dx = facing.x - entX;
    double dz = facing.z - entZ;
    double horDistSq = dx * dx + dz * dz;

    double targetX = entX;
    double targetZ = entZ;
    double targetY = entY + entity.getBbHeight() + 0.3D;

    if (horDistSq > 0.000001D) {
      double invDist = orbit / Math.sqrt(horDistSq);
      targetX += dx * invDist;
      targetZ += dz * invDist;
    }

    if (!initialized) {
      this.currentX = targetX;
      this.currentY = targetY;
      this.currentZ = targetZ;
      this.currentYaw = targetYaw;
      this.initialized = true;
    } else {
      float posFactor = 1.0f - (float) Math.exp(-12.0f * deltaSeconds);
      this.currentX += (targetX - this.currentX) * posFactor;
      this.currentY += (targetY - this.currentY) * posFactor;
      this.currentZ += (targetZ - this.currentZ) * posFactor;

      float rotFactor = 1.0f - (float) Math.exp(-5.0f * deltaSeconds);
      float yawDelta = Mth.wrapDegrees(targetYaw - this.currentYaw);
      this.currentYaw += yawDelta * rotFactor;
    }

    this.renderX = this.currentX - camera.x;
    this.renderY = this.currentY - camera.y;
    this.renderZ = this.currentZ - camera.z;
  }
}
