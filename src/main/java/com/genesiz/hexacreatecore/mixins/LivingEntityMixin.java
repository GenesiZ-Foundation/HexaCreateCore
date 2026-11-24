package com.genesiz.hexacreatecore.mixins;

import com.genesiz.hexacreatecore.registry.ModEffects;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

  @Shadow
  public int hurtTime;

  @Shadow
  public int hurtDuration;

  @Unique
  private int hexacreatecore$lastRealHurtAnimTick = -100;

  @Inject(method = "animateHurt", at = @At("HEAD"))
  private void hexacreatecore$recordRealHurtPacket(float yaw, CallbackInfo ci) {
    LivingEntity self = (LivingEntity) (Object) this;
    this.hexacreatecore$lastRealHurtAnimTick = self.tickCount;
  }

  @Inject(method = "onSyncedDataUpdated", at = @At("TAIL"))
  private void hexacreatecore$suppressEnderDecayBobbing(EntityDataAccessor key, CallbackInfo ci) {
    LivingEntity self = (LivingEntity) (Object) this;
    if (self.level().isClientSide() && self.hasEffect(ModEffects.ENDER_DECAY)) {
      if (self.tickCount - this.hexacreatecore$lastRealHurtAnimTick > 2) {
        this.hurtTime = 0;
        this.hurtDuration = 0;
      }
    }
  }
}

