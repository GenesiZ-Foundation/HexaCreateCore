package com.genesiz.hexacreatecore.mixins;

import com.genesiz.hexacreatecore.hexcasting.HexagonalGridManager;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

  @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
  private void onTurnPlayerHead(CallbackInfo ci) {
    if (HexagonalGridManager.isActive()) {
      MouseHandler mouse = (MouseHandler) (Object) this;

      double accumulatedX = mouse.getXVelocity();
      double accumulatedY = mouse.getYVelocity();

      // Send look updates straight to our independent tracking arrays
      HexagonalGridManager.handleMouseTurn(accumulatedX, accumulatedY);

      ci.cancel(); // Completely block the mouse from spinning your player's head model!
    }
  }
}
