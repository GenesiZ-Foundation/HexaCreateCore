package com.genesiz.hexacreatecore.mixins;

import com.genesiz.hexacreatecore.hexcasting.HexagonalGridManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

  /**
     * Intercepts the engine-level visibility dispatcher pass.
     * If our 3D casting room is active and the game is deciding whether to draw the local player,
     * we forcefully override the check to return true, bypassing first-person cloaking completely!
     */
  @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
  private void onShouldRenderEntity(Entity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
    if (HexagonalGridManager.isActive()) {
      Minecraft mc = Minecraft.getInstance();
      // If the entity being processed for rendering matches our actual local player instance, force it visible!
      if (entity == mc.player) {
        cir.setReturnValue(true);
      }
    }
  }
}
