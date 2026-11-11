package com.genesiz.hexacreatecore.mixins;

import com.genesiz.hexacreatecore.hexcasting.HexagonalGridManager;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

  @Unique private static float hexacreate$frozenYaw = 0.0f;
  @Unique private static float hexacreate$frozenPitch = 0.0f;

  /**
     * Intercepts the entry of sendPosition() to substitute our locked body orientation angles,
     * ensuring your character stands perfectly motionless to other players on the server world!
     */
  @Inject(method = "sendPosition", at = @At("HEAD"))
  private void onSendPositionHead(CallbackInfo ci) {
    if (HexagonalGridManager.isActive()) {
      LocalPlayer player = (LocalPlayer) (Object) this;

      // Capture initial angles on first activation pass
      if (hexacreate$frozenYaw == 0.0f && hexacreate$frozenPitch == 0.0f) {
        hexacreate$frozenYaw = player.getYRot();
        hexacreate$frozenPitch = player.getXRot();
      }

      // Temporarily trick the outgoing packet pipeline into sending the locked coordinates
      player.setYRot(hexacreate$frozenYaw);
      player.setXRot(hexacreate$frozenPitch);
    } else {
      // Flush trackers completely when the arcane room is deactivated
      hexacreate$frozenYaw = 0.0f;
      hexacreate$frozenPitch = 0.0f;
    }
  }
}
