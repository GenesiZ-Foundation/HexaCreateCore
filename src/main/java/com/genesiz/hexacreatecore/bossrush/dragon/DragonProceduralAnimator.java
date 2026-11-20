package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.bossrush.dragon.DragonAnimState.ClientAnimData;
import com.genesiz.hexacreatecore.bossrush.dragon.DragonAnimState.DragonAction;
import com.genesiz.hexacreatecore.bossrush.dragon.DragonAnimState.DragonPoseMode;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.GeoBone;

public final class DragonProceduralAnimator {

  private static final float DEG_TO_RAD = (float) (Math.PI / 180.0D);
  private static final String[] NECK_CHAIN = { "neck2", "neck3", "neck4", "neck5", "head" };
  private static final float[] NECK_WEIGHTS = { 0.14f, 0.19f, 0.23f, 0.24f, 0.20f };
  private static final String[] TAIL_CHAIN = {
    "tail", "tail2", "tail3", "tail4", "tail5", "tail6",
    "tail7", "tail8", "tail9", "tail10", "tail11", "tail12"
  };

  private DragonProceduralAnimator() {}

  public static void applyProceduralRig(MhEnderDragonModel<?> model, EnderDragon dragon, IMhEnderDragon mh, float partialTick) {
    ClientAnimData anim = mh.hexacreatecore$getClientAnimData();
    DragonPoseMode poseMode = mh.hexacreatecore$getPoseMode();
    DragonAction action = mh.hexacreatecore$getActiveAction();
    int actionTick = mh.hexacreatecore$getActionTick();
    int actionDur = Math.max(1, mh.hexacreatecore$getActionDuration());
    float actionProg = Mth.clamp((actionTick + partialTick) / (float) actionDur, 0.0f, 1.0f);

    float bodyYaw = Mth.rotLerp(partialTick, dragon.yBodyRotO, dragon.yBodyRot);
    float entityPitch = Mth.rotLerp(partialTick, dragon.xRotO, dragon.getXRot());
    Vec3 vel = dragon.getDeltaMovement();
    float horizSpeed = (float) Math.hypot(vel.x, vel.z);

    if (!anim.initialized) {
      anim.initialized = true;
      anim.prevBodyYaw = bodyYaw;
    }

    boolean isAirborne = (poseMode == DragonPoseMode.FLY_HOVER || poseMode == DragonPoseMode.FLY_GLIDE);

    // Sample yaw/pitch velocity & tail inertia ring buffer once per game tick (20 Hz)
    if (dragon.tickCount != anim.lastSampleTick) {
      anim.lastSampleTick = dragon.tickCount;
      float rawYawDelta = Mth.wrapDegrees(bodyYaw - anim.prevBodyYaw);
      anim.prevBodyYaw = bodyYaw;
      anim.smoothedYawRate = Mth.lerp(0.25f, anim.smoothedYawRate, rawYawDelta);
      anim.smoothedSpeed = Mth.lerp(0.25f, anim.smoothedSpeed, horizSpeed);

      float targetPitch = (Math.abs(entityPitch) > 0.5f)
        ? Mth.clamp(entityPitch, -75.0f, 75.0f)
        : Mth.clamp((float) (vel.y * 32.0D), -28.0f, 28.0f);
      float targetRoll = Mth.clamp(-anim.smoothedYawRate * 3.8f, -32.0f, 32.0f);
      anim.smoothedPitch = Mth.lerp(0.24f, anim.smoothedPitch, (isAirborne || action == DragonAction.AIR_DASH) ? targetPitch : 0.0f);
      anim.smoothedRoll = Mth.lerp(0.22f, anim.smoothedRoll, isAirborne ? targetRoll : 0.0f);
      anim.pushHistory(anim.smoothedYawRate, anim.smoothedPitch);
    }

    float ageTicks = dragon.tickCount + partialTick;
    float targetGlow = mh.hexacreatecore$isSupercritical() ? 1.0f : 0.0f;
    anim.supercriticalGlow = Mth.lerp(0.08f, anim.supercriticalGlow, targetGlow);

    resetProceduralBones(model, dragon, poseMode, action);
    applyRootAndBodyDynamics(model, dragon, anim, poseMode, action, actionProg, ageTicks, isAirborne);
    applyNeckAndHeadIk(model, dragon, anim, bodyYaw, isAirborne, poseMode, action, actionProg, partialTick);
    applyTailPhysics(model, dragon, anim, ageTicks, isAirborne, poseMode, action, actionProg);
    applyWingKinematics(model, dragon, anim, ageTicks, isAirborne, poseMode, action, actionProg);
    applyLimbIkAndAttacks(model, dragon, anim, poseMode, action, actionProg);
  }

  private static void applyRootAndBodyDynamics(
    MhEnderDragonModel<?> model, EnderDragon dragon, ClientAnimData anim, DragonPoseMode mode,
    DragonAction action, float prog, float ageTicks, boolean isAirborne
  ) {
    GeoBone root = getBone(model, dragon, "root");
    GeoBone body = getBone(model, dragon, "body");
    if (root == null || body == null) return;

    float rootY = 0.0f;
    float rootZ = 0.0f;
    float rootRoll = anim.smoothedRoll * DEG_TO_RAD;
    float rootPitch = anim.smoothedPitch * DEG_TO_RAD;

    float bodyPitch = (float) Math.sin(ageTicks * 0.11f) * 0.035f;
    float bodyYaw = 0.0f;
    float bodyRoll = 0.0f;

    if (isAirborne) {
      rootY += (float) Math.sin(ageTicks * 0.16f) * 2.2f;
    } else if (mode == DragonPoseMode.PILLAR_PERCH) {
      if (action == DragonAction.LIFT_OFF_GROUND) {
        // Hold pillar offset during the 0.0s-0.38s crouch, then blend smoothly to 0 as she leaps off
        float airBlend = Mth.clamp((prog - 0.38f) / 0.45f, 0.0f, 1.0f);
        float keepPerch = 1.0f - airBlend;
        bodyPitch = -1.5f * keepPerch * DEG_TO_RAD;
        rootY += 13.5f * keepPerch;
        rootZ -= 24.0f * keepPerch;
      } else {
        bodyPitch = -1.5f * DEG_TO_RAD;
        rootY += 13.5f;
        rootZ -= 24.0f;
      }
    } else if (mode == DragonPoseMode.TOPPLED) {
      rootY -= 11.0f;
      rootRoll += 72.0f * DEG_TO_RAD;
    }

    switch (action) {
      case SAFI_ROAR -> {
        if (0.24f > prog) {
          float t = prog / 0.24f;
          rootY -= Mth.sin(t * Mth.HALF_PI) * 5.5f;
          bodyPitch -= 14.0f * DEG_TO_RAD * t;
        } else if (0.76f > prog) {
          bodyPitch += (float) Math.sin(ageTicks * 1.9f) * 2.4f * DEG_TO_RAD;
          bodyRoll += (float) Math.cos(ageTicks * 1.5f) * 1.8f * DEG_TO_RAD;
        } else {
          float t = (prog - 0.76f) / 0.24f;
          rootY -= (float) Math.sin(t * Math.PI) * 3.5f;
        }
      }
      case CLAW_LEFT -> {
        float swing = swingCurve(prog, 0.35f, 0.55f);
        bodyYaw += swing * 24.0f * DEG_TO_RAD;
        bodyRoll -= swing * 10.0f * DEG_TO_RAD;
      }
      case CLAW_RIGHT -> {
        float swing = swingCurve(prog, 0.35f, 0.55f);
        bodyYaw -= swing * 24.0f * DEG_TO_RAD;
        bodyRoll += swing * 10.0f * DEG_TO_RAD;
      }
      case DOUBLE_CLAW -> {
        float first = swingCurve(prog, 0.22f, 0.42f);
        float second = swingCurve(prog, 0.55f, 0.75f);
        bodyYaw += (first - second) * 26.0f * DEG_TO_RAD;
        rootZ -= (first + second) * 6.0f;
      }
      case FORWARD_STOMP -> {
        if (prog >= 0.55f) {
          float slam = Mth.sin(((prog - 0.55f) / 0.45f) * Mth.PI);
          rootY -= slam * 4.5f;
          bodyPitch -= slam * 18.0f * DEG_TO_RAD;
        }
      }
      case BITE_QUICK, BITE_LUNGE -> {
        float snap = swingCurve(prog, 0.30f, 0.52f);
        rootZ -= snap * (action == DragonAction.BITE_LUNGE ? 10.0f : 4.5f);
        bodyPitch -= snap * 10.0f * DEG_TO_RAD;
      }
      case TAIL_SWIPE_LR, TAIL_SWIPE_RL -> {
        float dir = (action == DragonAction.TAIL_SWIPE_LR) ? 1.0f : -1.0f;
        float whip = swingCurve(prog, 0.30f, 0.65f);
        bodyYaw += dir * whip * 48.0f * DEG_TO_RAD;
      }
      case AIR_STOMP -> {
        if (prog >= 0.40f) {
          float drop = Mth.clamp((prog - 0.40f) / 0.35f, 0.0f, 1.0f);
          rootY -= drop * 10.0f;
          rootPitch -= (1.0f - drop) * 35.0f * DEG_TO_RAD;
        }
      }
      default -> {}
    }

    root.setPosY(root.getPosY() + rootY);
    root.setPosZ(root.getPosZ() + rootZ);
    root.setRotX(root.getRotX() + rootPitch);
    root.setRotZ(root.getRotZ() + rootRoll);

    body.setRotX(body.getRotX() + bodyPitch);
    body.setRotY(body.getRotY() + bodyYaw);
    body.setRotZ(body.getRotZ() + bodyRoll);
  }

  private static void applyNeckAndHeadIk(
    MhEnderDragonModel<?> model, EnderDragon dragon, ClientAnimData anim,
    float bodyYaw, boolean isAirborne, DragonPoseMode poseMode,
    DragonAction action, float prog, float partialTick
  ) {
    float targetIkWeight = 0.0f;

    boolean lockNeckForAttack = (action == DragonAction.SAFI_ROAR
      || action == DragonAction.FIREBALL_SHORT
      || action == DragonAction.AIR_DASH
      || isNovaAnim(action)
      || action == DragonAction.BREATH_SWIPE_90
      || action == DragonAction.DESPERATION_SPIN_CW
      || action == DragonAction.DESPERATION_SPIN_CCW);

    boolean isPerching = (poseMode == DragonPoseMode.PILLAR_PERCH);
    float maxCone = isPerching ? 170.0f : 110.0f;
    float maxYawClamp = isPerching ? 135.0f : 78.0f;
    Player targetPlayer = findNearestFrontPlayer(dragon, bodyYaw, 48.0D, maxCone, partialTick);

    if (!lockNeckForAttack && targetPlayer != null) {
      Vec3 eyePos = dragon.getPosition(partialTick).add(0.0D, 2.8D, 0.0D);
      Vec3 playerEye = targetPlayer.getEyePosition(partialTick);
      double dx = playerEye.x - eyePos.x;
      double dy = playerEye.y - eyePos.y;
      double dz = playerEye.z - eyePos.z;
      double horizDist = Math.max(0.1D, Math.hypot(dx, dz));

      float worldYawToPlayer = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0f;
      float relYaw = Mth.wrapDegrees(worldYawToPlayer - bodyYaw);
      float relPitch = (float) (Mth.atan2(dy, horizDist) * (180.0D / Math.PI));

      anim.lastDesiredYaw = Mth.clamp(relYaw, -maxYawClamp, maxYawClamp);
      anim.lastDesiredPitch = Mth.clamp(relPitch, -45.0f, 42.0f);
      targetIkWeight = 1.0f;
    }

    if (action == DragonAction.SAFI_ROAR) {
      if (0.24f > prog) {
        anim.lastDesiredPitch = 32.0f * (prog / 0.24f);
        anim.lastDesiredYaw = 0.0f;
      } else if (0.76f > prog) {
        anim.lastDesiredPitch = -58.0f;
        anim.lastDesiredYaw = (float) Math.sin(prog * 28.0f) * 6.0f;
      }
      targetIkWeight = 1.0f;
    } else if (action == DragonAction.BREATH_SWIPE_90) {
      anim.lastDesiredYaw = Mth.lerp(prog, -45.0f, 45.0f);
      anim.lastDesiredPitch = 18.0f;
      targetIkWeight = 1.0f;
    }

    // Smoothly ramp IK weight in/out and apply a sinusoidal S-curve so leaving the cone eases back naturally
    float blendRate = (targetIkWeight > anim.ikBlend) ? 0.06f : 0.035f;
    anim.ikBlend = Mth.lerp(blendRate, anim.ikBlend, targetIkWeight);
    float smoothWeight = Mth.sin(Mth.clamp(anim.ikBlend, 0.0f, 1.0f) * Mth.HALF_PI);

    float desiredYaw = anim.lastDesiredYaw;
    float desiredPitch = anim.lastDesiredPitch;

    for (int i = 0; NECK_CHAIN.length > i; i++) {
      GeoBone bone = getBone(model, dragon, NECK_CHAIN[i]);
      if (bone == null) continue;

      float segWeight = NECK_WEIGHTS[i];
      float segTargetYaw;
      float segTargetPitch;
      float segTargetRoll = 0.0f;

      if (isAirborne && !isPerching && !lockNeckForAttack) {
        int lagTicks = (NECK_CHAIN.length - 1 - i) * 2;
        float trailYaw = -anim.sampleYawHistory(lagTicks) * (1.2f - segWeight);
        float trailPitch = -anim.samplePitchHistory(lagTicks) * 0.45f;
        if (i == NECK_CHAIN.length - 1) {
          segTargetYaw = desiredYaw * 0.65f * smoothWeight;
          segTargetPitch = desiredPitch * 0.65f * smoothWeight - anim.smoothedPitch * 0.4f;
        } else {
          segTargetYaw = (desiredYaw * segWeight * smoothWeight) + trailYaw;
          segTargetPitch = (desiredPitch * segWeight * smoothWeight) + trailPitch;
          segTargetRoll = -anim.smoothedRoll * 0.16f;
        }
      } else {
        segTargetYaw = desiredYaw * segWeight * smoothWeight;
        segTargetPitch = desiredPitch * segWeight * smoothWeight;
      }

      float stiffness = (i == NECK_CHAIN.length - 1) ? 0.12f : (0.07f + i * 0.012f);
      anim.neckYaw[i] = Mth.lerp(stiffness, anim.neckYaw[i], segTargetYaw);
      anim.neckPitch[i] = Mth.lerp(stiffness, anim.neckPitch[i], segTargetPitch);
      anim.neckRoll[i] = Mth.lerp(stiffness, anim.neckRoll[i], segTargetRoll);

      bone.setRotY(bone.getRotY() - (anim.neckYaw[i] * DEG_TO_RAD));
      bone.setRotX(bone.getRotX() + (anim.neckPitch[i] * DEG_TO_RAD));
      bone.setRotZ(bone.getRotZ() + (anim.neckRoll[i] * DEG_TO_RAD));
    }
  }

  private static void applyTailPhysics(
    MhEnderDragonModel<?> model, EnderDragon dragon, ClientAnimData anim, float ageTicks,
    boolean isAirborne, DragonPoseMode mode, DragonAction action, float prog
  ) {
    if (isNovaAnim(action)) return;
    float whipWave = 0.0f;
    if (action == DragonAction.TAIL_SWIPE_LR || action == DragonAction.TAIL_SWIPE_RL) {
      float dir = (action == DragonAction.TAIL_SWIPE_LR) ? 1.0f : -1.0f;
      whipWave = dir * swingCurve(prog, 0.25f, 0.70f) * 14.0f;
    }

    boolean perched = (mode == DragonPoseMode.PILLAR_PERCH);
    float inertiaScale = isAirborne ? 1.0f : (perched ? 0.12f : 0.35f);

    for (int i = 0; TAIL_CHAIN.length > i; i++) {
      GeoBone seg = getBone(model, dragon, TAIL_CHAIN[i]);
      if (seg == null) continue;

      int delayTicks = Math.min(ClientAnimData.TAIL_HISTORY - 1, (i + 1) * 2);
      float delayedYaw = -anim.sampleYawHistory(delayTicks) * (0.45f + i * 0.05f) * inertiaScale;
      float delayedPitch = -anim.samplePitchHistory(delayTicks) * 0.18f * inertiaScale;

      float idleWaveFreq = isAirborne ? 0.16f : 0.08f;
      float idleAmp = isAirborne ? 2.4f : 1.0f;
      float phase = i * 0.45f;

      float waveYaw = (float) Math.sin(ageTicks * idleWaveFreq - phase) * idleAmp;
      float wavePitch = (float) Math.cos(ageTicks * (idleWaveFreq * 0.85f) - phase) * (idleAmp * 0.65f);
      float tailWhipSeg = whipWave * (0.65f + i * 0.05f);

      // Positive rotX drapes the base of the tail gently downward over the pillar edge without curling upward
      float perchDrape = perched ? (i < 5 ? 2.4f : -1.2f) : 0.0f;

      seg.setRotY(seg.getRotY() + (delayedYaw + waveYaw + tailWhipSeg) * DEG_TO_RAD);
      seg.setRotX(seg.getRotX() + (delayedPitch + wavePitch + perchDrape) * DEG_TO_RAD);
    }
  }

  private static void applyWingKinematics(
    MhEnderDragonModel<?> model, EnderDragon dragon, ClientAnimData anim, float ageTicks,
    boolean isAirborne, DragonPoseMode mode, DragonAction action, float prog
  ) {
    if (action == DragonAction.AIR_DASH || isNovaAnim(action)) {
      return;
    }
    GeoBone wingL = getBone(model, dragon, "wing");
    GeoBone tipL = getBone(model, dragon, "wingtip");
    GeoBone wingR = getBone(model, dragon, "wing1");
    GeoBone tipR = getBone(model, dragon, "wingtip1");
    if (wingL == null || tipL == null || wingR == null || tipR == null) return;
    boolean grounded = (mode == DragonPoseMode.GROUND_IDLE || mode == DragonPoseMode.GROUND_WALK);
    if (mode == DragonPoseMode.PILLAR_PERCH) {
      if (action == DragonAction.LIFT_OFF_GROUND || action == DragonAction.FIREBALL_SHORT) {
        return; // Let fireball_volley and lift_off_ground wing keyframes play cleanly
      }
      float breatheWing = (float) Math.sin(ageTicks * 0.11f) * 3.5f;
      wingL.setRotZ(wingL.getRotZ() - ((24.0f + breatheWing) * DEG_TO_RAD));
      wingL.setRotY(wingL.getRotY() + (18.0f * DEG_TO_RAD));
      tipL.setRotZ(tipL.getRotZ() + (28.0f * DEG_TO_RAD));
      wingR.setRotZ(wingR.getRotZ() + ((24.0f + breatheWing) * DEG_TO_RAD));
      wingR.setRotY(wingR.getRotY() - (18.0f * DEG_TO_RAD));
      tipR.setRotZ(tipR.getRotZ() - (28.0f * DEG_TO_RAD));
      return;
    }

    if (action == DragonAction.SAFI_ROAR) {
      if (0.24f > prog) {
        float tuck = prog / 0.24f;
        wingL.setRotZ(wingL.getRotZ() + (35.0f * tuck * DEG_TO_RAD));
        wingR.setRotZ(wingR.getRotZ() - (35.0f * tuck * DEG_TO_RAD));
      } else if (0.76f > prog) {
        float tremor = (float) Math.sin(ageTicks * 1.7f) * 5.0f;
        wingL.setRotZ(wingL.getRotZ() - ((28.0f + tremor) * DEG_TO_RAD));
        wingR.setRotZ(wingR.getRotZ() + ((28.0f + tremor) * DEG_TO_RAD));
        tipL.setRotZ(tipL.getRotZ() + (16.0f * DEG_TO_RAD));
        tipR.setRotZ(tipR.getRotZ() - (16.0f * DEG_TO_RAD));
      }
      return;
    }

    if (action == DragonAction.WING_FLAP_FORWARD || action == DragonAction.WING_FLAP_LEFT || action == DragonAction.WING_FLAP_RIGHT) {
      float gust = swingCurve(prog, 0.25f, 0.60f);
      float leftMult = (action == DragonAction.WING_FLAP_RIGHT) ? 0.2f : 1.0f;
      float rightMult = (action == DragonAction.WING_FLAP_LEFT) ? 0.2f : 1.0f;
      wingL.setRotZ(wingL.getRotZ() - (gust * 46.0f * leftMult * DEG_TO_RAD));
      wingL.setRotY(wingL.getRotY() - (gust * 28.0f * leftMult * DEG_TO_RAD));
      wingR.setRotZ(wingR.getRotZ() + (gust * 46.0f * rightMult * DEG_TO_RAD));
      wingR.setRotY(wingR.getRotY() + (gust * 28.0f * rightMult * DEG_TO_RAD));
      return;
    }

    if (action == DragonAction.AIR_DASH) {
      return; // Let pre_charge_flight wing keyframes play cleanly
    }

    if (isAirborne) {
      float flapFreq = (mode == DragonPoseMode.FLY_GLIDE && anim.smoothedSpeed > 0.45f) ? 0.24f : 0.32f;
      float flapAmp = (mode == DragonPoseMode.FLY_GLIDE && anim.smoothedSpeed > 0.45f) ? 30.0f : 38.0f;
      float phase = ageTicks * flapFreq;
      float mainStroke = (float) Math.sin(phase) * flapAmp;
      float tipLagStroke = (float) Math.sin(phase - 0.85f) * (flapAmp * 0.85f);
      float sweepStroke = (float) Math.cos(phase) * 10.0f;
      wingL.setRotZ(wingL.getRotZ() + (mainStroke * DEG_TO_RAD));
      wingL.setRotY(wingL.getRotY() + (sweepStroke * DEG_TO_RAD));
      tipL.setRotZ(tipL.getRotZ() + (tipLagStroke * DEG_TO_RAD));
      wingR.setRotZ(wingR.getRotZ() - (mainStroke * DEG_TO_RAD));
      wingR.setRotY(wingR.getRotY() - (sweepStroke * DEG_TO_RAD));
      tipR.setRotZ(tipR.getRotZ() - (tipLagStroke * DEG_TO_RAD));
    } else if (grounded) {
      float idleFlutter = (float) Math.sin(ageTicks * 0.11f) * 2.0f * DEG_TO_RAD;
      wingL.setRotZ(wingL.getRotZ() + idleFlutter);
      wingR.setRotZ(wingR.getRotZ() - idleFlutter);
    }
  }

  private static void applyLimbIkAndAttacks(
    MhEnderDragonModel<?> model, EnderDragon dragon, ClientAnimData anim,
    DragonPoseMode mode, DragonAction action, float prog
  ) {
    boolean grounded = (mode == DragonPoseMode.GROUND_IDLE || mode == DragonPoseMode.GROUND_WALK);
    if (grounded && (mode == DragonPoseMode.GROUND_WALK || anim.smoothedSpeed > 0.04f)) {
      anim.gaitCycle += Math.max(0.18f, anim.smoothedSpeed * 1.4f);
      float strideA = (float) Math.sin(anim.gaitCycle) * 22.0f * DEG_TO_RAD;
      float liftA = Math.max(0.0f, (float) Math.cos(anim.gaitCycle)) * 18.0f * DEG_TO_RAD;
      float strideB = (float) Math.sin(anim.gaitCycle + Math.PI) * 22.0f * DEG_TO_RAD;
      float liftB = Math.max(0.0f, (float) Math.cos(anim.gaitCycle + Math.PI)) * 18.0f * DEG_TO_RAD;
      applyLegStep(model, dragon, "frontleg", "frontlegtip", "frontfoot", strideA, liftA);
      applyLegStep(model, dragon, "rearleg1", "rearlegtip1", "rearfoot1", strideA * 0.85f, liftA * 0.85f);
      applyLegStep(model, dragon, "frontleg1", "frontlegtip1", "frontfoot1", strideB, liftB);
      applyLegStep(model, dragon, "rearleg", "rearlegtip", "rearfoot", strideB * 0.85f, liftB * 0.85f);
    }
    if (grounded) {
      adaptFootToGround(model, dragon, "frontleg", "frontfoot", -1.2D, 1.8D);
      adaptFootToGround(model, dragon, "frontleg1", "frontfoot1", 1.2D, 1.8D);
      adaptFootToGround(model, dragon, "rearleg", "rearfoot", -1.5D, -1.8D);
      adaptFootToGround(model, dragon, "rearleg1", "rearfoot1", 1.5D, -1.8D);
    }
    switch (action) {
      case CLAW_LEFT -> {
        float swipe = swingCurve(prog, 0.32f, 0.58f);
        GeoBone fl = getBone(model, dragon, "frontleg");
        GeoBone flTip = getBone(model, dragon, "frontlegtip");
        if (fl != null) {
          fl.setRotX(fl.getRotX() - (swipe * 55.0f * DEG_TO_RAD));
          fl.setRotZ(fl.getRotZ() + (swipe * 38.0f * DEG_TO_RAD));
        }
        if (flTip != null) flTip.setRotX(flTip.getRotX() + (swipe * 42.0f * DEG_TO_RAD));
      }
      case CLAW_RIGHT -> {
        float swipe = swingCurve(prog, 0.32f, 0.58f);
        GeoBone fr = getBone(model, dragon, "frontleg1");
        GeoBone frTip = getBone(model, dragon, "frontlegtip1");
        if (fr != null) {
          fr.setRotX(fr.getRotX() - (swipe * 55.0f * DEG_TO_RAD));
          fr.setRotZ(fr.getRotZ() - (swipe * 38.0f * DEG_TO_RAD));
        }
        if (frTip != null) frTip.setRotX(frTip.getRotX() + (swipe * 42.0f * DEG_TO_RAD));
      }
      case DOUBLE_CLAW -> {
        float sL = swingCurve(prog, 0.20f, 0.44f);
        float sR = swingCurve(prog, 0.52f, 0.76f);
        GeoBone fl = getBone(model, dragon, "frontleg");
        GeoBone fr = getBone(model, dragon, "frontleg1");
        if (fl != null) fl.setRotX(fl.getRotX() - (sL * 62.0f * DEG_TO_RAD));
        if (fr != null) fr.setRotX(fr.getRotX() - (sR * 62.0f * DEG_TO_RAD));
      }
      case BACK_LEG_LEFT -> {
        float sweep = swingCurve(prog, 0.30f, 0.60f);
        GeoBone rl = getBone(model, dragon, "rearleg");
        if (rl != null) {
          rl.setRotX(rl.getRotX() + (sweep * 45.0f * DEG_TO_RAD));
          rl.setRotZ(rl.getRotZ() + (sweep * 32.0f * DEG_TO_RAD));
        }
      }
      case BACK_LEG_RIGHT -> {
        float sweep = swingCurve(prog, 0.30f, 0.60f);
        GeoBone rr = getBone(model, dragon, "rearleg1");
        if (rr != null) {
          rr.setRotX(rr.getRotX() + (sweep * 45.0f * DEG_TO_RAD));
          rr.setRotZ(rr.getRotZ() - (sweep * 32.0f * DEG_TO_RAD));
        }
      }
      default -> {}
    }
  }
  private static void applyLegStep(MhEnderDragonModel<?> model, EnderDragon dragon, String upperName, String lowerName, String footName, float strideRad, float liftRad) {
    GeoBone upper = getBone(model, dragon, upperName);
    GeoBone lower = getBone(model, dragon, lowerName);
    GeoBone foot = getBone(model, dragon, footName);
    if (upper != null) upper.setRotX(upper.getRotX() + strideRad - liftRad);
    if (lower != null) lower.setRotX(lower.getRotX() + liftRad * 1.4f);
    if (foot != null) foot.setRotX(foot.getRotX() - strideRad * 0.5f - liftRad * 0.4f);
  }
  private static void adaptFootToGround(MhEnderDragonModel<?> model, EnderDragon dragon, String legName, String footName, double localX, double localZ) {
    Level level = dragon.level();
    if (level == null) return;
    float yawRad = dragon.yBodyRot * DEG_TO_RAD;
    double wx = dragon.getX() + (localX * Math.cos(yawRad) - localZ * Math.sin(yawRad));
    double wz = dragon.getZ() + (localX * Math.sin(yawRad) + localZ * Math.cos(yawRad));
    BlockPos basePos = BlockPos.containing(wx, dragon.getY(), wz);
    double groundY = dragon.getY();
    for (int dy = 2; dy >= -3; dy--) {
      BlockPos check = basePos.offset(0, dy, 0);
      if (!level.getBlockState(check).isAir()) {
        groundY = check.getY() + 1.0D;
        break;
      }
    }
    float heightDiff = Mth.clamp((float) (groundY - dragon.getY()), -1.5f, 1.5f);
    if (Math.abs(heightDiff) > 0.05f) {
      GeoBone leg = getBone(model, dragon, legName);
      GeoBone foot = getBone(model, dragon, footName);
      if (leg != null) leg.setRotX(leg.getRotX() - (heightDiff * 0.25f));
      if (foot != null) foot.setRotX(foot.getRotX() + (heightDiff * 0.25f));
    }
  }
  private static float swingCurve(float prog, float activeStart, float activeEnd) {
    if (activeStart > prog) {
      float t = prog / Math.max(0.01f, activeStart);
      return -0.35f * Mth.sin(t * Mth.HALF_PI);
    } else if (activeEnd >= prog) {
      float t = (prog - activeStart) / Math.max(0.01f, activeEnd - activeStart);
      return Mth.lerp(t * t * (3.0f - 2.0f * t), -0.35f, 1.0f);
    } else {
      float t = (prog - activeEnd) / Math.max(0.01f, 1.0f - activeEnd);
      return 1.0f - (t * t * (3.0f - 2.0f * t));
    }
  }
  private static Player findNearestFrontPlayer(EnderDragon dragon, float bodyYaw, double maxRange, float maxConeDeg, float partialTick) {
    Level level = dragon.level();
    if (level == null) return null;
    Player best = null;
    double bestDistSq = maxRange * maxRange;
    for (Player p : level.players()) {
      if (!p.isAlive() || p.isSpectator()) continue;
      double distSq = dragon.distanceToSqr(p);
      if (distSq > bestDistSq) continue;
      Vec3 pPos = p.getPosition(partialTick);
      double dx = pPos.x - dragon.getX();
      double dz = pPos.z - dragon.getZ();
      float yawToPlayer = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0f;
      float relYaw = Math.abs(Mth.wrapDegrees(yawToPlayer - bodyYaw));
      if (maxConeDeg >= relYaw) {
        bestDistSq = distSq;
        best = p;
      }
    }
    return best;
  }

  private static GeoBone getBone(MhEnderDragonModel<?> model, EnderDragon dragon, String name) {
    return model.getLiveBone(name);
  }

  private static void resetProceduralBones(MhEnderDragonModel<?> model, EnderDragon dragon, DragonPoseMode mode, DragonAction action) {
    resetBoneToBind(getBone(model, dragon, "root"));
    if (action != DragonAction.FIREBALL_SHORT && action != DragonAction.AIR_DASH && !isNovaAnim(action)) {
      for (String name : NECK_CHAIN) {
        resetBoneToBind(getBone(model, dragon, name));
      }
    }
    if (!isNovaAnim(action)) {
      for (String name : TAIL_CHAIN) {
        resetBoneToBind(getBone(model, dragon, name));
      }
    }
    boolean grounded = (mode == DragonPoseMode.GROUND_IDLE || mode == DragonPoseMode.GROUND_WALK);
    if (!grounded && action != DragonAction.LIFT_OFF_GROUND && action != DragonAction.AIR_DASH && action != DragonAction.FIREBALL_SHORT && !isNovaAnim(action)) {
      resetBoneToBind(getBone(model, dragon, "wing"));
      resetBoneToBind(getBone(model, dragon, "wingtip"));
      resetBoneToBind(getBone(model, dragon, "wing1"));
      resetBoneToBind(getBone(model, dragon, "wingtip1"));
    }
  }

  private static void resetBoneToBind(GeoBone bone) {
    if (bone == null) return;
    var snap = bone.getInitialSnapshot();
    if (snap == null) return;
    bone.setPosX(snap.getOffsetX());
    bone.setPosY(snap.getOffsetY());
    bone.setPosZ(snap.getOffsetZ());
    bone.setRotX(snap.getRotX());
    bone.setRotY(snap.getRotY());
    bone.setRotZ(snap.getRotZ());
  }

  private static boolean isNovaAnim(DragonAction action) {
    return action == DragonAction.NOVA_PRE_CHARGE
    || action == DragonAction.NOVA_CHARGE
    || action == DragonAction.NOVA_END_CHARGE;
  }
}
