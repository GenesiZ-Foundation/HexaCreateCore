package com.genesiz.hexacreatecore.mixins;

import com.genesiz.hexacreatecore.hexcasting.HexagonalGridManager;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

  @Shadow private Vec3 position;
  @Shadow private float xRot;
  @Shadow private float yRot;
  @Shadow private boolean initialized;
  @Shadow private BlockGetter level;
  @Shadow private Entity entity;

  @Inject(method = "setup", at = @At("HEAD"), cancellable = true)
  private void onSetupHead(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
    if (HexagonalGridManager.isActive()) {
      this.initialized = true;
      this.level = level;
      this.entity = entity;

      this.position = HexagonalGridManager.getLerpedCameraPos(partialTick);
      this.yRot = HexagonalGridManager.getLerpedCameraYaw(partialTick);
      this.xRot = HexagonalGridManager.getLerpedCameraPitch(partialTick);

      Camera cameraInstance = (Camera) (Object) this;

      try {
        java.lang.reflect.Method setRotationMethod = Camera.class.getDeclaredMethod("setRotation", float.class, float.class);
        setRotationMethod.setAccessible(true);
        setRotationMethod.invoke(cameraInstance, this.yRot, this.xRot);
      } catch (Exception e) {
        e.printStackTrace();
      }

      ci.cancel(); 
    }
  }

  @Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
  private void onIsDetachedHead(CallbackInfoReturnable<Boolean> cir) {
    if (HexagonalGridManager.isActive()) {
      cir.setReturnValue(true); // Tells the renderer to draw the player body model!
    }
  }
}

