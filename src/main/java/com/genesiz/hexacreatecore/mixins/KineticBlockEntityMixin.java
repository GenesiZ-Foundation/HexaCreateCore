package com.genesiz.hexacreatecore.mixins;

import com.genesiz.hexacreatecore.create.wonderum.wormhole.WormholeKineticBridge;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class KineticBlockEntityMixin extends BlockEntity {

  protected KineticBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
  }

  @Inject(method = "getGeneratedSpeed", at = @At("HEAD"), cancellable = true)
  private void hexacreatecore$wormholeGeneratedSpeed(CallbackInfoReturnable<Float> cir) {
    WormholeKineticBridge.DrivenState state = WormholeKineticBridge.getDriven(this.level, this.worldPosition);
    if (state != null) {
      cir.setReturnValue(state.speed());
    }
  }

  @Inject(method = "isSource", at = @At("HEAD"), cancellable = true)
  private void hexacreatecore$wormholeIsSource(CallbackInfoReturnable<Boolean> cir) {
    WormholeKineticBridge.DrivenState state = WormholeKineticBridge.getDriven(this.level, this.worldPosition);
    if (state != null && Math.abs(state.speed()) > 0.01f) {
      cir.setReturnValue(true);
    }
  }

  @Inject(method = "calculateAddedStressCapacity", at = @At("HEAD"), cancellable = true)
  private void hexacreatecore$wormholeAddedCapacity(CallbackInfoReturnable<Float> cir) {
    WormholeKineticBridge.DrivenState state = WormholeKineticBridge.getDriven(this.level, this.worldPosition);
    if (state != null && Math.abs(state.speed()) > 0.01f) {
      cir.setReturnValue(state.perRpmCapacity());
    }
  }

  @Inject(method = "getTheoreticalSpeed", at = @At("HEAD"), cancellable = true)
  private void hexacreatecore$wormholeTheoreticalSpeed(CallbackInfoReturnable<Float> cir) {
    WormholeKineticBridge.DrivenState state = WormholeKineticBridge.getDriven(this.level, this.worldPosition);
    if (state != null) {
      cir.setReturnValue(state.speed());
    }
  }
}
