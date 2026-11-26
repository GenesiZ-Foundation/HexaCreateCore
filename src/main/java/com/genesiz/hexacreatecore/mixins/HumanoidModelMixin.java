package com.genesiz.hexacreatecore.mixins;

import com.genesiz.hexacreatecore.hexcasting.everbook.EverbookPlayerAnimController;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {

  @Shadow @Final public ModelPart head;
  @Shadow @Final public ModelPart hat;
  @Shadow @Final public ModelPart rightArm;
  @Shadow @Final public ModelPart leftArm;

  @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
  private void hexacreatecore$applyEverbookMapPose(
    T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci
  ) {
    if (!(entity instanceof Player player)) return;

    float partialTick = ageInTicks - (float) player.tickCount;
    float prog = EverbookPlayerAnimController.getPlayerOpenProgress(player, Mth.clamp(partialTick, 0.0f, 1.0f));
    if (0.001f >= prog) return;

    // Smooth cubic ease
    float ease = prog * prog * (3.0f - 2.0f * prog);
    float breath = Mth.sin(ageInTicks * 0.08f) * 0.025f * ease;

    // Two-handed lowered map/book reading pose (overrides Not Enough Animations cleanly)
    float targetRightX = -0.92f + breath;
    float targetRightY = -0.32f;
    float targetRightZ = 0.08f;

    float targetLeftX = -0.92f - breath;
    float targetLeftY = 0.32f;
    float targetLeftZ = -0.08f;

    this.rightArm.xRot = Mth.lerp(ease, this.rightArm.xRot, targetRightX);
    this.rightArm.yRot = Mth.lerp(ease, this.rightArm.yRot, targetRightY);
    this.rightArm.zRot = Mth.lerp(ease, this.rightArm.zRot, targetRightZ);

    this.leftArm.xRot = Mth.lerp(ease, this.leftArm.xRot, targetLeftX);
    this.leftArm.yRot = Mth.lerp(ease, this.leftArm.yRot, targetLeftY);
    this.leftArm.zRot = Mth.lerp(ease, this.leftArm.zRot, targetLeftZ);

    // Subtle downward gaze toward the open pages
    float targetHeadX = Math.max(this.head.xRot, 0.24f);
    this.head.xRot = Mth.lerp(ease, this.head.xRot, targetHeadX);
    this.hat.copyFrom(this.head);
  }
}
