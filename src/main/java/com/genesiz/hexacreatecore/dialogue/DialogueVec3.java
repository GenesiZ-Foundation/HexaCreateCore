/*
 * THIS PART OF THE CODE IS MEANT FOR DEVELOPERS
 * */
package com.genesiz.hexacreatecore.dialogue;

import com.genesiz.hexacreatecore.decorators.annotations.Overload;

import net.minecraft.world.entity.Entity;

public record DialogueVec3<T extends Entity &Dialogue3D>(float partialTick, T entity, double orbit , net.minecraft.world.phys.Vec3 facing, net.minecraft.world.phys.Vec3 camera, SmoothPositionState state) {

  @Overload
  public DialogueVec3(float partialTick, T entity, net.minecraft.world.phys.Vec3 facing, net.minecraft.world.phys.Vec3 camera, SmoothPositionState state) {
    this(partialTick, entity, 0.0, facing, camera, state);
  }

  @Overload
  public DialogueVec3(float partialTick, T entity, net.minecraft.world.phys.Vec3 facing, net.minecraft.world.phys.Vec3 camera) {
    this(partialTick, entity, 0.0, facing, camera, null);
  }

  public double entPosX(){
    return net.minecraft.util.Mth.lerp(partialTick, entity.xo, entity.getX());
  }
  public double entPosY(){
    return net.minecraft.util.Mth.lerp(partialTick, entity.yo, entity.getY());
  }
  public double entPosZ(){
    return net.minecraft.util.Mth.lerp(partialTick, entity.zo, entity.getZ());
  }

  private double horDist() {
    double dx = facing.x - entPosX();
    double dz = facing.z - entPosZ();
    return Math.sqrt(dx * dx + dz * dz);
  }

  public double targetX() {
    if (horDist() > 0.001D) return entPosX() + ((facing.x - entPosX()) / horDist()) * orbit;
    return entPosX();}

  public double targetZ() {
    if (horDist() > 0.001D) return entPosZ() + ((facing.z - entPosZ()) / horDist()) * orbit;
    return entPosZ();
  }

  public double targetY() {
    return entPosY() + entity.getBbHeight() + 0.3D;
  }

  public double renderX() {
    return (state != null ? state.currentX : targetX()) - camera.x;
  }

  public double renderY() {
    return (state != null ? state.currentY : targetY()) - camera.y;
  }

  public double renderZ() {
    return (state != null ? state.currentZ : targetZ()) - camera.z;
  }
}
