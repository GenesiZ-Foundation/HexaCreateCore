package com.genesiz.hexacreatecore.mixins;

import com.genesiz.hexacreatecore.bossrush.dragon.DragonAnimState.ClientAnimData;
import com.genesiz.hexacreatecore.bossrush.dragon.DragonAnimState.DragonAction;
import com.genesiz.hexacreatecore.bossrush.dragon.DragonAnimState.DragonPoseMode;
import com.genesiz.hexacreatecore.bossrush.dragon.DragonMultipartCollider;
import com.genesiz.hexacreatecore.bossrush.dragon.EnderDragonMhBrain;
import com.genesiz.hexacreatecore.bossrush.dragon.IMhEnderDragon;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin extends Mob implements IMhEnderDragon {

  @Unique
  private static final EntityDataAccessor<Boolean> HEXA_MH_ENABLED =
  SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.BOOLEAN);
  @Unique
  private static final EntityDataAccessor<Integer> HEXA_POSE_MODE =
  SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.INT);
  @Unique
  private static final EntityDataAccessor<Integer> HEXA_ACTION_ID =
  SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.INT);
  @Unique
  private static final EntityDataAccessor<Integer> HEXA_ACTION_TICK =
  SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.INT);
  @Unique
  private static final EntityDataAccessor<Integer> HEXA_ACTION_DUR =
  SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.INT);
  @Unique
  private static final EntityDataAccessor<Boolean> HEXA_BARRIER_SHIELD =
  SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.BOOLEAN);
  @Unique
  private static final EntityDataAccessor<Boolean> HEXA_SUPERCRITICAL =
  SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.BOOLEAN);
  @Unique
  private static final EntityDataAccessor<Integer> HEXA_FIGHT_PHASE =
  SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.INT);

  @Shadow
  protected abstract boolean reallyHurt(DamageSource source, float amount);
  @Unique
  private final AnimatableInstanceCache hexacreatecore$geoCache = GeckoLibUtil.createInstanceCache(this);
  @Unique
  private final ClientAnimData hexacreatecore$clientAnim = new ClientAnimData();
  @Unique
  private Vec3 hexacreatecore$arenaOrigin = new Vec3(0.0D, 64.0D, 0.0D);
  @Unique
  private boolean hexacreatecore$originInitialized = false;
  @Unique private int hexacreatecore$lastActionTick = 0;

  @Unique
  private static final RawAnimation POSE_NEUTRAL_GROUND = RawAnimation.begin().thenPlayAndHold("neutral_ground");
  @Unique
  private static final RawAnimation POSE_NEUTRAL_FLY = RawAnimation.begin().thenPlayAndHold("neutral_fly");
  @Unique
  private static final RawAnimation POSE_REAR_UP = RawAnimation.begin().thenPlayAndHold("rear_up");
  @Unique
  private static final RawAnimation POSE_AIR_MOVE = RawAnimation.begin().thenLoop("air_move");
  @Unique
  private static final RawAnimation POSE_WINGS_TUCKED = RawAnimation.begin().thenLoop("wings_tucked");
  @Unique
  private static final RawAnimation POSE_MOUTH_OPEN = RawAnimation.begin().thenPlayAndHold("mouth_open");
  @Unique
  private static final RawAnimation POSE_MOUTH_CLOSED = RawAnimation.begin().thenPlayAndHold("mouth_closed");
  @Unique
  private static final RawAnimation ANIM_FIREBALL_VOLLEY = RawAnimation.begin().thenPlay("fireball_volley");
  @Unique
  private static final RawAnimation ANIM_LIFT_OFF_GROUND = RawAnimation.begin().thenPlay("lift_off_ground");
  @Unique
  private DragonAction hexacreatecore$lastStanceAction = DragonAction.NONE;
  @Unique
  private static final RawAnimation ANIM_PRE_CHARGE_FLIGHT = RawAnimation.begin().thenPlay("pre_charge_flight");
  @Unique
  private static final RawAnimation ANIM_PRE_CHARGING_NOVA_1 = RawAnimation.begin().thenPlay("pre_charging_nova_1");
  @Unique
  private static final RawAnimation ANIM_CHARGING_NOVA_1 = RawAnimation.begin().thenLoop("charging_nova_1");
  @Unique
  private static final RawAnimation ANIM_END_CHARGING_NOVA_1 = RawAnimation.begin().thenPlay("end_charging_nova_1");
  @Unique
  private static final RawAnimation ANIM_GROUND_IDLE = RawAnimation.begin().thenLoop("ground_idle");
  @Unique
  private static final RawAnimation ANIM_GROUND_WALK = RawAnimation.begin().thenLoop("ground_walk");
  @Unique
  private static final RawAnimation ANIM_LANDING = RawAnimation.begin().thenPlay("landing");
  @Unique
  private static final RawAnimation ANIM_END_ROAR = RawAnimation.begin().thenPlay("end_roar");
  @Unique
  private static final RawAnimation ANIM_TURN_LEFT_90 = RawAnimation.begin().thenPlay("turn_left_90");
  @Unique
  private static final RawAnimation ANIM_TURN_RIGHT_90 = RawAnimation.begin().thenPlay("turn_right_90");
  @Unique
  private static final RawAnimation ANIM_DOUBLE_WING_HIT = RawAnimation.begin().thenPlay("double_wing_hit");
  @Unique
  private static final RawAnimation ANIM_SHIELD_STOMP = RawAnimation.begin().thenPlay("shield_stomp");
  @Unique
  private static final EntityDataAccessor HEXA_CUSTOM_CLIP =
  SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.STRING);
  @Unique
  private String hexacreatecore$cachedCustomClipName = "";
  @Unique
  private RawAnimation hexacreatecore$cachedCustomClipAnim = null;






  protected EnderDragonMixin(EntityType<? extends Mob> entityType, Level level) {
    super(entityType, level);
  }

  @Inject(method = "defineSynchedData", at = @At("TAIL"))
  private void hexacreatecore$defineSynchedData(SynchedEntityData.Builder builder, CallbackInfo ci) {
    builder.define(HEXA_MH_ENABLED, true);
    builder.define(HEXA_POSE_MODE, DragonPoseMode.FLY_GLIDE.id);
    builder.define(HEXA_ACTION_ID, DragonAction.NONE.id);
    builder.define(HEXA_ACTION_TICK, 0);
    builder.define(HEXA_ACTION_DUR, 0);
    builder.define(HEXA_BARRIER_SHIELD, false);
    builder.define(HEXA_SUPERCRITICAL, false);
    builder.define(HEXA_FIGHT_PHASE, 1);
    builder.define(HEXA_CUSTOM_CLIP, "");
  }

  @Override
  public String hexacreatecore$getCustomClip() {
    return (String) this.entityData.get(HEXA_CUSTOM_CLIP);
  }

  @Override
  public void hexacreatecore$triggerCustomClip(String clipName, int durationTicks) {
    this.entityData.set(HEXA_CUSTOM_CLIP, clipName != null ? clipName : "");
    this.entityData.set(HEXA_ACTION_ID, DragonAction.CUSTOM_CLIP.id);
    this.entityData.set(HEXA_ACTION_TICK, 0);
    this.entityData.set(HEXA_ACTION_DUR, durationTicks > 0 ? durationTicks : DragonAction.CUSTOM_CLIP.defaultDurationTicks);
  }

  @Override
  public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    controllers.add(
      new AnimationController<>(this, "wings_overlay", 5, this::hexacreatecore$wingsPredicate),
      new AnimationController<>(this, "mouth_overlay", 3, this::hexacreatecore$mouthPredicate),
      new AnimationController<>(this, "stance", 3, this::hexacreatecore$stancePredicate)
    );
  }

  @Unique
  private PlayState hexacreatecore$stancePredicate(AnimationState state) {
    DragonPoseMode mode = hexacreatecore$getPoseMode();
    DragonAction action = hexacreatecore$getActiveAction();
    state.getController().setAnimationSpeed(1.0D);

    int actionTick = hexacreatecore$getActionTick();
    if (action != hexacreatecore$lastStanceAction || (action != DragonAction.NONE && actionTick <= 1 && hexacreatecore$lastActionTick > 2)) {
      hexacreatecore$lastStanceAction = action;
      if (action == hexacreatecore$lastStanceAction && action != DragonAction.NONE && actionTick <= 1 && hexacreatecore$lastActionTick > 2) {
        state.getController().forceAnimationReset();
      }
    }
    hexacreatecore$lastActionTick = actionTick;

    if (action == DragonAction.CUSTOM_CLIP) {
      String clip = hexacreatecore$getCustomClip();
      if (!clip.isEmpty()) {
        if (!clip.equals(hexacreatecore$cachedCustomClipName) || hexacreatecore$cachedCustomClipAnim == null) {
          hexacreatecore$cachedCustomClipName = clip;
          hexacreatecore$cachedCustomClipAnim = RawAnimation.begin().thenPlay(clip);
          state.getController().forceAnimationReset();
        }
        return state.setAndContinue(hexacreatecore$cachedCustomClipAnim);
      }
    }

    if (action == DragonAction.FIREBALL_SHORT) {
      return state.setAndContinue(ANIM_FIREBALL_VOLLEY);
    }
    if (action == DragonAction.LIFT_OFF_GROUND) {
      return state.setAndContinue(ANIM_LIFT_OFF_GROUND);
    }
    if (action == DragonAction.AIR_DASH) {
      return state.setAndContinue(ANIM_PRE_CHARGE_FLIGHT);
    }
    if (action == DragonAction.NOVA_PRE_CHARGE) {
      return state.setAndContinue(ANIM_PRE_CHARGING_NOVA_1);
    }
    if (action == DragonAction.NOVA_CHARGE) {
      return state.setAndContinue(ANIM_CHARGING_NOVA_1);
    }
    if (action == DragonAction.NOVA_END_CHARGE) {
      return state.setAndContinue(ANIM_END_CHARGING_NOVA_1);
    }
    if (action == DragonAction.LANDING) {
      return state.setAndContinue(ANIM_LANDING);
    }
    if (action == DragonAction.SAFI_ROAR) {
      state.getController().setAnimationSpeed(0.80D);
      return state.setAndContinue(ANIM_END_ROAR);
    }
    if (action == DragonAction.TURN_LEFT_90 || action == DragonAction.CLAW_LEFT) {
      state.getController().setAnimationSpeed(0.80D);
      return state.setAndContinue(ANIM_TURN_LEFT_90);
    }
    if (action == DragonAction.TURN_RIGHT_90 || action == DragonAction.CLAW_RIGHT) {
      state.getController().setAnimationSpeed(0.80D);
      return state.setAndContinue(ANIM_TURN_RIGHT_90);
    }
    if (action == DragonAction.DOUBLE_CLAW) {
      return state.setAndContinue(ANIM_DOUBLE_WING_HIT);
    }
    if (action == DragonAction.SHIELD_STOMP_CLUSTER || action == DragonAction.SHIELD_STOMP_SPREAD) {
      return state.setAndContinue(ANIM_SHIELD_STOMP);
    }
    if (mode == DragonPoseMode.REAR_UP) {
      return state.setAndContinue(POSE_REAR_UP);
    }

    return switch (mode) {
      case GROUND_IDLE -> state.setAndContinue(ANIM_GROUND_IDLE);
      case GROUND_WALK -> {
        EnderDragon self = (EnderDragon) (Object) this;
        double horizSpeed = Math.hypot(self.getDeltaMovement().x, self.getDeltaMovement().z);
        state.getController().setAnimationSpeed(horizSpeed > 0.22D ? 2.0D : 1.0D);
        yield state.setAndContinue(ANIM_GROUND_WALK);
      }
      case TOPPLED, PILLAR_PERCH -> state.setAndContinue(POSE_NEUTRAL_GROUND);
      case FLY_HOVER -> state.setAndContinue(POSE_NEUTRAL_FLY);
      case FLY_GLIDE -> state.setAndContinue(POSE_AIR_MOVE);
      default -> state.setAndContinue(POSE_NEUTRAL_FLY);
    };
  }

  @Unique
  private PlayState hexacreatecore$wingsPredicate(AnimationState state) {
    DragonPoseMode mode = hexacreatecore$getPoseMode();
    DragonAction action = hexacreatecore$getActiveAction();

    boolean grounded = (mode == DragonPoseMode.GROUND_IDLE || mode == DragonPoseMode.GROUND_WALK);
    boolean flaringWings = (action == DragonAction.SAFI_ROAR
      || action == DragonAction.CUSTOM_CLIP
      || action == DragonAction.DOUBLE_CLAW
      || action == DragonAction.SHIELD_STOMP_CLUSTER
      || action == DragonAction.SHIELD_STOMP_SPREAD
      || action == DragonAction.WING_FLAP_FORWARD
      || action == DragonAction.WING_FLAP_LEFT
      || action == DragonAction.WING_FLAP_RIGHT
      || action == DragonAction.NOVA_CHARGE
      || action == DragonAction.NOVA_DETONATE);

    if (grounded && !flaringWings) {
      return state.setAndContinue(POSE_WINGS_TUCKED);
    }
    return PlayState.STOP;
  }

  @Unique
  private PlayState hexacreatecore$mouthPredicate(AnimationState state) {
    DragonAction action = hexacreatecore$getActiveAction();
    if (action == DragonAction.FIREBALL_SHORT
      || action == DragonAction.CUSTOM_CLIP
      || action == DragonAction.AIR_DASH
      || action == DragonAction.NOVA_PRE_CHARGE
      || action == DragonAction.NOVA_CHARGE
      || action == DragonAction.NOVA_END_CHARGE
      || action == DragonAction.LANDING
      || action == DragonAction.SAFI_ROAR
      || action == DragonAction.TURN_LEFT_90
      || action == DragonAction.TURN_RIGHT_90
      || action == DragonAction.CLAW_LEFT
      || action == DragonAction.CLAW_RIGHT
      || action == DragonAction.DOUBLE_CLAW
      || action == DragonAction.SHIELD_STOMP_CLUSTER
      || action == DragonAction.SHIELD_STOMP_SPREAD) {
      return PlayState.STOP;
    }

    int tick = hexacreatecore$getActionTick();
    int dur = Math.max(1, hexacreatecore$getActionDuration());
    float prog = (float) tick / (float) dur;

    boolean openMouth = switch (action) {
      case SAFI_ROAR -> tick >= 14 && 48 >= tick;
      case BITE_QUICK, BITE_LUNGE -> prog >= 0.25f && 0.65f >= prog;
      case FIREBALL_SHORT, FIREBALL_CARPET, BREATH_SWIPE_90, FLYING_BREATH_45,
      AIR_CHARGED_FIREBALL, NOVA_DETONATE, LASER_TRIPLE, MULTIDIR_LASER,
      LASER_SWIPE, DESPERATION_SPIN_CW, DESPERATION_SPIN_CCW, DESPERATION_FINAL_BEAM -> prog >= 0.15f && 0.88f >= prog;
      default -> false;
    };

    return state.setAndContinue(openMouth ? POSE_MOUTH_OPEN : POSE_MOUTH_CLOSED);
  }

  @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
  private void hexacreatecore$overrideVanillaDragonAi(CallbackInfo ci) {
    if (!hexacreatecore$isMhDragon()) return;
    EnderDragon self = (EnderDragon) (Object) this;
    if (!self.isAlive()) {
      return; // Let vanilla EnderDragon#aiStep run tickDeath() & notify EndDragonFight#setDragonKilled
    }
    ci.cancel();

    self.setNoGravity(true);
    self.xo = self.getX();
    self.yo = self.getY();
    self.zo = self.getZ();
    self.yBodyRotO = self.yBodyRot;
    self.yHeadRotO = self.yHeadRot;
    self.xRotO = self.getXRot();

    if (self.level().isClientSide()) {
      if (this.lerpSteps > 0) {
        this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot, this.lerpXRot);
        this.lerpSteps--;
      }
      self.yBodyRot = self.getYRot();
      self.yHeadRot = self.getYRot();
      DragonMultipartCollider.syncHitboxes(self, this);
      return;
    }

    if (!hexacreatecore$originInitialized) {
      hexacreatecore$originInitialized = true;
      if (self.level().dimension() != Level.END
        && 1.0D > hexacreatecore$arenaOrigin.distanceToSqr(0.0D, 64.0D, 0.0D)) {
        hexacreatecore$arenaOrigin = self.position();
      }
    }

    if (self.level() instanceof ServerLevel sl) {
      try {
        EnderDragonMhBrain.tickServer(sl, self, this);
      } catch (Throwable t) {
        com.genesiz.hexacreatecore.HexaCreateCore.LOGGER.error("EnderDragonMhBrain tick error", t);
      }
    }

    self.yBodyRot = self.getYRot();
    self.yHeadRot = self.getYRot();
    DragonMultipartCollider.syncHitboxes(self, this);
  }


  @Inject(method = "hurt(Lnet/minecraft/world/entity/boss/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
  private void hexacreatecore$guardBarrierShield(
    EnderDragonPart part,
    DamageSource source,
    float amount,
    CallbackInfoReturnable cir
  ) {
    if (hexacreatecore$isMhDragon() && hexacreatecore$hasBarrierShield()) {
      EnderDragon self = (EnderDragon) (Object) this;
      self.level().playSound(null, self.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 1.2f, 1.85f);
      cir.setReturnValue(false);
    }
  }
  @Override
  public AnimatableInstanceCache getAnimatableInstanceCache() {
    return hexacreatecore$geoCache;
  }
  @Override
  public boolean hexacreatecore$isMhDragon() {
    return this.entityData.get(HEXA_MH_ENABLED);
  }
  @Override
  public void hexacreatecore$setMhDragon(boolean enabled, Vec3 arenaOrigin) {
    this.entityData.set(HEXA_MH_ENABLED, enabled);
    if (arenaOrigin != null) this.hexacreatecore$arenaOrigin = arenaOrigin;
  }
  @Override
  public Vec3 hexacreatecore$getArenaOrigin() {
    return this.hexacreatecore$arenaOrigin;
  }
  @Override
  public DragonPoseMode hexacreatecore$getPoseMode() {
    return DragonPoseMode.byId(this.entityData.get(HEXA_POSE_MODE));
  }
  @Override
  public void hexacreatecore$setPoseMode(DragonPoseMode mode) {
    this.entityData.set(HEXA_POSE_MODE, mode.id);
  }
  @Override
  public DragonAction hexacreatecore$getActiveAction() {
    return DragonAction.byId(this.entityData.get(HEXA_ACTION_ID));
  }
  @Override
  public int hexacreatecore$getActionTick() {
    return this.entityData.get(HEXA_ACTION_TICK);
  }
  @Override
  public void hexacreatecore$setActionTick(int tick) {
    this.entityData.set(HEXA_ACTION_TICK, tick);
  }
  @Override
  public int hexacreatecore$getActionDuration() {
    return this.entityData.get(HEXA_ACTION_DUR);
  }
  @Override
  public void hexacreatecore$triggerAction(DragonAction action, int durationTicks) {
    this.entityData.set(HEXA_ACTION_ID, action.id);
    this.entityData.set(HEXA_ACTION_TICK, 0);
    this.entityData.set(HEXA_ACTION_DUR, durationTicks > 0 ? durationTicks : action.defaultDurationTicks);
  }
  @Override
  public boolean hexacreatecore$hasBarrierShield() {
    return this.entityData.get(HEXA_BARRIER_SHIELD);
  }
  @Override
  public void hexacreatecore$setBarrierShield(boolean shielded) {
    this.entityData.set(HEXA_BARRIER_SHIELD, shielded);
  }
  @Override
  public boolean hexacreatecore$isSupercritical() {
    return this.entityData.get(HEXA_SUPERCRITICAL);
  }
  @Override
  public void hexacreatecore$setSupercritical(boolean supercritical) {
    this.entityData.set(HEXA_SUPERCRITICAL, supercritical);
  }
  @Override
  public int hexacreatecore$getFightPhase() {
    return this.entityData.get(HEXA_FIGHT_PHASE);
  }
  @Override
  public void hexacreatecore$setFightPhase(int phase) {
    this.entityData.set(HEXA_FIGHT_PHASE, phase);
  }
  @Override
  public ClientAnimData hexacreatecore$getClientAnimData() {
    return this.hexacreatecore$clientAnim;
  }

  @Inject(method = "createAttributes", at = @At("RETURN"))
  private static void hexacreatecore$setBaseMaxHealth(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
    cir.getReturnValue().add(Attributes.MAX_HEALTH, 1000.0D);
  }

  @Inject(method = "getParts", at = @At("HEAD"), cancellable = true)
  private void hexacreatecore$getBoneParts(CallbackInfoReturnable cir) {
    cir.setReturnValue(DragonMultipartCollider.getOrCreateRig((EnderDragon) (Object) this).all);
  }

  @Override
  public boolean canCollideWith(net.minecraft.world.entity.Entity other) {
    if (other instanceof DragonMultipartCollider.BonePart bp && bp.getParent() == (Object) this) {
      return false;
    }
    return super.canCollideWith(other);
  }

  @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
  private void hexacreatecore$routeBonePartDamage(
    DamageSource source,
    float amount,
    CallbackInfoReturnable<Boolean> cir
  ) {
    if (!hexacreatecore$isMhDragon()) return;
    EnderDragon self = (EnderDragon) (Object) this;

    // Immune to her own dragon breath clouds, fireballs, explosions, and Endermen
    if (source.getEntity() == self
      || source.getDirectEntity() == self
      || source.getEntity() instanceof net.minecraft.world.entity.monster.EnderMan
      || source.getDirectEntity() instanceof net.minecraft.world.entity.monster.EnderMan) {
      cir.setReturnValue(false);
      return;
    }

    if (hexacreatecore$hasBarrierShield()) {
      self.level().playSound(null, self.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 1.2f, 1.85f);
      cir.setReturnValue(false);
      return;
    }

    if (!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
      float maxPerHit = Math.max(1.0f, self.getMaxHealth() * 0.02f);
      float cappedAmount = Math.min(amount, maxPerHit);
      cir.setReturnValue(this.reallyHurt(source, cappedAmount));
    }
  }
}
