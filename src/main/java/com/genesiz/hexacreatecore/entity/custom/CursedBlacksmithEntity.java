package com.genesiz.hexacreatecore.entity.custom;

import com.genesiz.hexacreatecore.virtual.Dialogue3D;
import com.genesiz.hexacreatecore.virtual.SmoothPositionState;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class CursedBlacksmithEntity extends Monster implements GeoEntity,Dialogue3D {
  private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
  private static final EntityDataAccessor<Integer> DIALOGUE_TICKS = 
  SynchedEntityData.defineId(CursedBlacksmithEntity.class, EntityDataSerializers.INT);

  private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
  private final SmoothPositionState dialogueState = new SmoothPositionState();

  public CursedBlacksmithEntity(EntityType<? extends Monster> type, Level level) {
    super(type, level);
  }

  public static AttributeSupplier.Builder createAttributes() {
    return Mob.createMobAttributes()
      .add(Attributes.MAX_HEALTH, 7046.0D)
      .add(Attributes.MOVEMENT_SPEED, 0.0D)
      .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
    ;
  }

  @Override
  protected void registerGoals() {
    super.registerGoals();
    this.goalSelector.addGoal(0, new FloatGoal(this));
    this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8));
  }

  public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    controllers.add(new AnimationController<CursedBlacksmithEntity>(this,"cursed_blacksmith_controller", 5, state -> {
      return state.setAndContinue(IDLE);
    }));
  }

  @Override
  protected BodyRotationControl createBodyControl() {
    return new BodyRotationControl(this) {
      @Override
      public void clientTick() {
      }
    };
  }

  @Override
  public void setYBodyRot(float bodyRot) {
    super.setYBodyRot(this.getYRot());
  }

  @Override
  public void aiStep() {
    float fixedSpawnDirection = this.getYRot();
    super.aiStep();
    this.setYRot(fixedSpawnDirection);
    this.yRotO = fixedSpawnDirection;
  }

  @Override
  public InteractionResult mobInteract(Player player, InteractionHand hand) {
    if (hand == InteractionHand.MAIN_HAND) {
      if (!this.level().isClientSide) {
        // Sets dialogue state window active for 100 ticks (5 seconds)
        this.entityData.set(DIALOGUE_TICKS, 100);
      }
      return InteractionResult.sidedSuccess(this.level().isClientSide);
    }
    return super.mobInteract(player, hand);
  }

  @Override
  public void tick() {
    super.tick();
    // Count down dialogue timers safely on server & client ticks
    int activeTicks = this.entityData.get(DIALOGUE_TICKS);
    if (activeTicks > 0) {
      this.entityData.set(DIALOGUE_TICKS, activeTicks - 1);
    }
  }

  public boolean shouldShowDialogue() {
    return this.entityData.get(DIALOGUE_TICKS) > 0;
  }

  @Override
  public AnimatableInstanceCache getAnimatableInstanceCache() {
    return this.cache;
  }
  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(DIALOGUE_TICKS, 0); 
  }

  @Override
  public SmoothPositionState getDialogueStateTracker() {
    return this.dialogueState;
  }
}
