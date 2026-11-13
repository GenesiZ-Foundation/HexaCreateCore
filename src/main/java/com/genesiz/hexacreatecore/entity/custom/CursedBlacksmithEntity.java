package com.genesiz.hexacreatecore.entity.custom;

import com.genesiz.hexacreatecore.animations.EntityAnimationStateMachine;
import com.genesiz.hexacreatecore.components.IProceduralAnimConfig;
import com.genesiz.hexacreatecore.threedified.ThreeDified;
import com.genesiz.hexacreatecore.threedified.DialoguePlaybackState;
import com.genesiz.hexacreatecore.threedified.DialogueTransformState;

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
import software.bernie.geckolib.util.GeckoLibUtil;

public class CursedBlacksmithEntity extends Monster implements GeoEntity, ThreeDified, IProceduralAnimConfig {
  private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
  
  public static final EntityDataAccessor<Integer> DIALOGUE_TICKS =
      SynchedEntityData.defineId(CursedBlacksmithEntity.class, EntityDataSerializers.INT);

  private final DialogueTransformState dialogueTransform = new DialogueTransformState();
  private final DialoguePlaybackState dialoguePlayback = new DialoguePlaybackState();

  // Pseudo-ECS Component attachment
  private final EntityAnimationStateMachine animationComponent;

  public CursedBlacksmithEntity(EntityType<? extends Monster> type, Level level) {
    super(type, level);

    this.animationComponent = new EntityAnimationStateMachine(() -> 
        this.entityData.get(DIALOGUE_TICKS) > 0 && !this.dialoguePlayback.isForceClosed()
    );
    this.animationComponent.onInit(this);
  }

  public static AttributeSupplier.Builder createAttributes() {
    return Mob.createMobAttributes()
      .add(Attributes.MAX_HEALTH, 7046.0D)
      .add(Attributes.MOVEMENT_SPEED, 0.0D)
      .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
  }

  @Override
  protected void registerGoals() {
    super.registerGoals();
    this.goalSelector.addGoal(0, new FloatGoal(this));
    this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8));
  }

  // --- IProceduralAnimConfig Declarative Abstract Configuration Mappings ---

  @Override
  public int getMinRandomInterval() {
    return 80;  // 4 seconds minimum between random secondary behaviors
  }

  @Override
  public int getMaxRandomInterval() {
    return 180; // 9 seconds maximum between random secondary behaviors
  }

  @Override
  public String getContinuousAmbientAnim() {
    return "breathing"; // The generic continuous loop
  }

  @Override
  public String getRandomActionAnim() {
    return "blink"; // The generic randomized one-shot clip
  }

  // --- GeckoLib Multi-Controller Layer Setup ---

  @Override
  public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    // LAYER 1: Core Sequence System (Idle -> Talking -> Custom overrides)
    controllers.add(new AnimationController<>(this, "cursed_blacksmith_controller", 5, 
        this.animationComponent::handleAnimationState
    ));

    // LAYER 2: Universal Ambient & Random Action Layer
    controllers.add(new AnimationController<>(this, "cursed_blacksmith_overlay", 4, 
        this.animationComponent::handleOverlayAnimationState
    ));
  }

  @Override
  protected BodyRotationControl createBodyControl() {
    return new BodyRotationControl(this) {
      @Override
      public void clientTick() {}
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
  public void tick() {
    super.tick();

    if (!this.level().isClientSide) {
      int activeTicks = this.entityData.get(DIALOGUE_TICKS);
      if (activeTicks > 0) {
        this.entityData.set(DIALOGUE_TICKS, activeTicks - 1);
      }
    }

    this.animationComponent.onTick();
  }

  @Override
  public InteractionResult mobInteract(Player player, InteractionHand hand) {
    if (hand != InteractionHand.MAIN_HAND) return super.mobInteract(player, hand);

    if (this.level().isClientSide) {
      if (this.shouldShowDialogue() && this.dialoguePlayback.hoveredOptionIndex != -1) {
        return InteractionResult.SUCCESS;
      }
      if (!this.shouldShowDialogue() || this.dialoguePlayback.isForceClosed()) {
        this.dialoguePlayback.resetTypewriter();
      }
    } else {
      this.entityData.set(DIALOGUE_TICKS, 100);
    }
    return InteractionResult.sidedSuccess(this.level().isClientSide);
  }

  @Override
  public AnimatableInstanceCache getAnimatableInstanceCache() {
    return this.cache;
  }

  @Override
  public DialogueTransformState getDialogueTransform() {
    return this.dialogueTransform;
  }

  @Override
  public DialoguePlaybackState getDialoguePlayback() {
    return this.dialoguePlayback;
  }

  @Override
  public boolean shouldShowDialogue() {
    return this.entityData.get(DIALOGUE_TICKS) > 0;
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(DIALOGUE_TICKS, 0);
  }
}
