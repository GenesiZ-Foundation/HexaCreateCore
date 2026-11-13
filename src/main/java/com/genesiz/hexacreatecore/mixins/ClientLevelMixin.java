package com.genesiz.hexacreatecore.mixins;

import com.genesiz.hexacreatecore.difficulty.client.DifficultyAtmosphereController;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

  @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
  private void onGetSkyColor(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
    cir.setReturnValue(DifficultyAtmosphereController.applySkyColorTint(cir.getReturnValue()));
  }
}
