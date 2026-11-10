package com.genesiz.hexacreatecore.animations;

import com.genesiz.hexacreatecore.components.IEntityComponent;
import com.genesiz.hexacreatecore.components.IProceduralAnimConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;

import java.util.function.Supplier;

public final class EntityAnimationStateMachine implements IEntityComponent {
  private enum Phase { PRE, LOOP, POST }

  private final Supplier<Boolean> dialogueCondition;
  private Entity entity;
  
  private String currentSequenceId = "idle";
  private Phase currentPhase = Phase.LOOP;
  private String lastPlayedAnimName = "";
  private String entityTypeKey = null;
  private String networkSequenceOverride = null;

  // Universal procedural interval properties
  private int randomActionTimer = 0;
  private int nextRandomActionThreshold = -1;

  public EntityAnimationStateMachine(Supplier<Boolean> dialogueCondition) {
    this.dialogueCondition = dialogueCondition;
  }

  @Override
  public void onInit(Entity entity) {
    this.entity = entity;
  }

  @Override
  public void onTick() {
    // Modular tick updates live here independently
  }

  private String getEntityTypeKey() {
    if (this.entityTypeKey == null && this.entity != null) {
      this.entityTypeKey = BuiltInRegistries.ENTITY_TYPE.getKey(this.entity.getType()).toString();
    }
    return this.entityTypeKey != null ? this.entityTypeKey : "hexacreatecore:cursed_blacksmith";
  }

  public <T extends GeoEntity> PlayState handleAnimationState(AnimationState<T> event) {
    boolean hasDialogue = this.dialogueCondition.get();
    
    // 1. Pure ECS Check: Universal Intercept of explicit server-driven network animation overrides!
    if (event.getController().getCurrentRawAnimation() != null) {
      String activeTrigger = event.getController().getCurrentRawAnimation().getAnimationStages().toString();
      
      if (activeTrigger.startsWith("override:")) {
        this.networkSequenceOverride = activeTrigger.substring(9);
      }
    }

    // Determine baseline target sequences based on runtime interactions
    String targetSequence = hasDialogue ? "talking" : "idle";
    
    if (hasDialogue && this.networkSequenceOverride != null) {
      targetSequence = this.networkSequenceOverride;
    }

    if (!hasDialogue) {
      this.networkSequenceOverride = null;
      targetSequence = "idle";
    }

    // 2. Structural boundary checking: handling transitions out of active sequences
    if (!this.currentSequenceId.equals(targetSequence)) {
      AnimationSequenceData currentData = AnimationSequenceReloadListener.getSequence(getEntityTypeKey(), this.currentSequenceId);
      
      boolean canTransition = true;
      if (currentData != null && currentData.waitForCompletion()) {
        if (this.currentPhase == Phase.PRE || this.currentPhase == Phase.POST) {
          canTransition = event.getController().hasAnimationFinished();
        }
      }

      if (canTransition) {
        if (currentData != null && currentData.postAnim().isPresent() && this.currentPhase != Phase.POST) {
          this.currentPhase = Phase.POST;
        } else {
          this.currentSequenceId = targetSequence;
          AnimationSequenceData nextData = AnimationSequenceReloadListener.getSequence(getEntityTypeKey(), this.currentSequenceId);
          this.currentPhase = (nextData != null && nextData.preAnim().isPresent()) ? Phase.PRE : Phase.LOOP;
          this.lastPlayedAnimName = ""; 
          event.getController().forceAnimationReset();
        }
      }
    }

    // 3. Process output sequence phases to drive execution parameters
    AnimationSequenceData activeData = AnimationSequenceReloadListener.getSequence(getEntityTypeKey(), this.currentSequenceId);
    
    // Action bar debug text channel
    Minecraft mc = Minecraft.getInstance();
    if (mc.player != null) {
      String debugMsg = String.format("§b[Anim Debug] Seq: §e%s §7| Phase: §e%s §7| Data found: %s", 
          this.currentSequenceId, this.currentPhase, (activeData != null ? "§aYES" : "§cNO"));
      mc.player.displayClientMessage(Component.literal(debugMsg), true);
    }

    if (activeData == null) {
      return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
    }

    switch (this.currentPhase) {
      case PRE -> {
        String anim = activeData.preAnim().orElse("");
        boolean isCompleted = event.getController().hasAnimationFinished() || event.getAnimationTick() >= 20.0d;
        
        if (isCompleted && anim.equals(this.lastPlayedAnimName)) {
          this.currentPhase = Phase.LOOP;
          event.getController().forceAnimationReset();
          return PlayState.CONTINUE;
        }
        this.lastPlayedAnimName = anim;
        return event.setAndContinue(RawAnimation.begin().thenPlay(anim));
      }
      case LOOP -> {
        String anim = activeData.loopAnim().orElse("idle");
        this.lastPlayedAnimName = anim;
        return event.setAndContinue(RawAnimation.begin().thenLoop(anim));
      }
      case POST -> {
        String anim = activeData.postAnim().orElse("");
        boolean isCompleted = event.getController().hasAnimationFinished() || event.getAnimationTick() >= 20.0d;
        
        if (isCompleted && anim.equals(this.lastPlayedAnimName)) {
          this.currentPhase = Phase.LOOP;
          event.getController().forceAnimationReset();
          return PlayState.CONTINUE;
        }
        this.lastPlayedAnimName = anim;
        return event.setAndContinue(RawAnimation.begin().thenPlay(anim));
      }
    }

    return PlayState.CONTINUE;
  }

  /**
   * Processes layered multi-controller animations (Generic Actions & Continuous Ambience) 
   * completely independent of the base locomotion/dialogue sequences layer.
   */
  public <T extends GeoEntity> PlayState handleOverlayAnimationState(AnimationState<T> event) {
    // 1. Resolve configuration parameters using the abstract data layers
    IProceduralAnimConfig config = (this.entity instanceof IProceduralAnimConfig c) ? c : new IProceduralAnimConfig() {
      @Override public int getMinRandomInterval() { return 60; }
      @Override public int getMaxRandomInterval() { return 160; }
    };

    String ambientAnim = config.getContinuousAmbientAnim();
    String actionAnim = config.getRandomActionAnim();

    // 2. Instantiate threshold calculations using native entity seed random generation
    if (this.nextRandomActionThreshold == -1 && this.entity != null) {
      this.nextRandomActionThreshold = config.getNextRandomTicks(this.entity.getRandom());
    }

    // 3. Assemble base continuous ambient layer loop path
    RawAnimation overlayAnimStack = RawAnimation.begin().thenLoop(ambientAnim);

    this.randomActionTimer++;
    if (this.randomActionTimer >= this.nextRandomActionThreshold) {
      // Inject secondary high-priority random action clip on top of baseline loop stack parameters
      overlayAnimStack = RawAnimation.begin().thenPlay(actionAnim).thenLoop(ambientAnim);
      
      // Flush trackers and schedule subsequent dynamic threshold windows
      this.randomActionTimer = 0;
      if (this.entity != null) {
        this.nextRandomActionThreshold = config.getNextRandomTicks(this.entity.getRandom());
      }
    }

    return event.setAndContinue(overlayAnimStack);
  }
}
