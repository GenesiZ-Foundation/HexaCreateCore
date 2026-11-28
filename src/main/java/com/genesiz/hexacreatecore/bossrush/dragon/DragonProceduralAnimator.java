package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.bossrush.dragon.DragonAnimState.ClientAnimData;
import com.genesiz.hexacreatecore.bossrush.dragon.DragonAnimState.DragonAction;
import com.genesiz.hexacreatecore.bossrush.dragon.DragonAnimState.DragonPoseMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DragonProceduralAnimator {

  private static final float DEG_TO_RAD = (float) (Math.PI / 180.0D);

  // Only hardcoded physical constants: foot heights in Blockbench model units (16 units = 1 block)
  private static final float FRONT_FOOT_HEIGHT_UNITS = 4.0f;
  private static final float REAR_FOOT_HEIGHT_UNITS  = 6.0f;

  // Body pivot [X0, Y20, Z8] vs true geometric torso center [X0, Y8, Z24]
  private static final float BODY_PIVOT_Y = 20.0f;
  private static final float BODY_PIVOT_Z = 8.0f;
  private static final float BODY_CENTER_OFFSET_Y = 8.0f - BODY_PIVOT_Y; // -12 units
  private static final float BODY_CENTER_OFFSET_Z = 24.0f - BODY_PIVOT_Z; // +16 units

  private static final String[] NECK_CHAIN = { "neck2", "neck3", "neck4", "neck5", "head" };
  private static final float[] NECK_WEIGHTS = { 0.14f, 0.19f, 0.23f, 0.24f, 0.20f };
  private static final String[] TAIL_CHAIN = {
    "tail", "tail2", "tail3", "tail4", "tail5", "tail6",
    "tail7", "tail8", "tail9", "tail10", "tail11", "tail12"
  };

  private static final Map<UUID, ClientAnimData> CLIENT_DATA = new HashMap<>();
  private static final Map<UUID, SmoothedLegState> LEG_STATES = new ConcurrentHashMap<>();

  private DragonProceduralAnimator() {}

  public static void applyProceduralRig(MhEnderDragonModel<?> model, EnderDragon dragon, IMhEnderDragon mh, float partialTick) {
    if (dragon == null || mh == null) {
      return;
    }

    ClientAnimData anim = CLIENT_DATA.computeIfAbsent(dragon.getUUID(), id -> new ClientAnimData());
    DragonPoseMode poseMode = mh.hexacreatecore$getPoseMode();
    DragonAction action = mh.hexacreatecore$getActiveAction();
    int actionTick = mh.hexacreatecore$getActionTick();
    int actionDur = Math.max(1, mh.hexacreatecore$getActionDuration());
    float actionProg = (action == DragonAction.NONE)
    ? 0.0f
    : Mth.clamp((actionTick + partialTick) / actionDur, 0.0f, 1.0f);

    Vec3 vel = dragon.getDeltaMovement();
    float horizSpeed = (float) Math.hypot(vel.x, vel.z);
    boolean isAirborne = (poseMode == DragonPoseMode.FLY_HOVER
    || poseMode == DragonPoseMode.FLY_GLIDE
    || dragon.getY() > mh.hexacreatecore$getArenaOrigin().y + 1.5D);

    // Seamless 90-degree turn handoff on the client: on the exact frame TURN_*_90 / CLAW_* exits to NONE,
    // snap yBodyRot & yBodyRotO immediately if the server's rotation packet hasn't caught up yet.
    float renderedBodyYaw = Mth.rotLerp(partialTick, dragon.yBodyRotO, dragon.yBodyRot);
    anim.oneFrameYawFixDeg = 0.0f;


    if (anim.prevAction != action) {
      if (anim.prevAction == DragonAction.TURN_LEFT_90 || anim.prevAction == DragonAction.CLAW_LEFT
        || anim.prevAction == DragonAction.TURN_RIGHT_90 || anim.prevAction == DragonAction.CLAW_RIGHT) {
        float turnSign = (anim.prevAction == DragonAction.TURN_LEFT_90 || anim.prevAction == DragonAction.CLAW_LEFT) ? -90.0f : 90.0f;
        float expectedYaw = Mth.wrapDegrees(anim.turnStartYaw + turnSign);
        float residualYaw = Mth.wrapDegrees(renderedBodyYaw - expectedYaw);

        // PoseStack already used renderedBodyYaw for THIS frame; cancel residualYaw on root so visual angle stays exact
        if (Math.abs(residualYaw) > 1.0f) {
          anim.oneFrameYawFixDeg = -residualYaw;
          dragon.setYRot(expectedYaw);
          dragon.yRotO = expectedYaw;
          dragon.yBodyRot = expectedYaw;
          dragon.yBodyRotO = expectedYaw;
          dragon.yHeadRot = expectedYaw;
          dragon.yHeadRotO = expectedYaw;
        }
        anim.prevBodyYaw = expectedYaw;
      }
      if (action == DragonAction.TURN_LEFT_90 || action == DragonAction.CLAW_LEFT
        || action == DragonAction.TURN_RIGHT_90 || action == DragonAction.CLAW_RIGHT) {
        anim.turnStartYaw = renderedBodyYaw;
      }
      anim.prevAction = action;
    }

    float bodyYaw = Mth.rotLerp(partialTick, dragon.yBodyRotO, dragon.yBodyRot);
    float entityPitch = Mth.lerp(partialTick, dragon.xRotO, dragon.getXRot());

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

    if (Math.abs(anim.oneFrameYawFixDeg) > 0.01f) {
      GeoBone rootFix = getBone(model, dragon, "root");
      if (rootFix != null) {
        rootFix.setRotY(rootFix.getRotY() - (anim.oneFrameYawFixDeg * DEG_TO_RAD));
      }
    }


    if (isFullKeyframeAnim(poseMode, action)) {
      GeoBone root = getBone(model, dragon, "root");
      if (root != null) {
        if (isNovaAnim(action)) {
          root.setRotX(0.0f);
          root.setRotY(0.0f);
          root.setRotZ(0.0f);
        }
        if (action == DragonAction.LANDING) {
          SmoothedLegState legState = LEG_STATES.get(dragon.getUUID());
          if (legState != null) {
            legState.hasStartSnapshot = false;
            legState.smoothedBodyPitch = 0.0f;
            legState.smoothedRootLiftUnits = STANDING_ROOT_LIFT_UNITS;
          }
        } else if (poseMode == DragonPoseMode.GROUND_IDLE || poseMode == DragonPoseMode.GROUND_WALK) {
          solveGroundedBodyAndLegsFromShoulders(model, dragon, poseMode, action, actionProg, 0.0f, 0.0f);
        }
      }
      applyTailPhysics(model, dragon, anim, bodyYaw, ageTicks, isAirborne, poseMode, action, actionProg);
      return;
    }

    applyRootAndBodyDynamics(model, dragon, anim, poseMode, action, actionProg, ageTicks, isAirborne);
    applyNeckAndHeadIk(model, dragon, anim, bodyYaw, isAirborne, poseMode, action, actionProg, partialTick);
    applyTailPhysics(model, dragon, anim, ageTicks, isAirborne, poseMode, action, actionProg);
    applyWingKinematics(model, dragon, anim, ageTicks, isAirborne, poseMode, action, actionProg);
    applyLimbIkAndAttacks(model, dragon, anim, poseMode, action, actionProg);
  }

  /**
   * 5-Step Shoulder-Raycast Ground & Limb IK Solver (Zero Side Tilt):
   * 1) Reads current animation position & rotation of frontleg, frontleg1, rearleg, rearleg1.
   * 2) Raycasts straight down from each of those 4 shoulder/hip pivots to the ground.
   * 3) Computes body pitch around body pivot [0, 20, 8] (true center) from front vs rear heights.
   * 4) Rotates each leg's 2 limbs individually so each foot pivot sits right above ground (4u front, 6u rear).
   * 5) Rotates each foot bone so it is perpendicular to the ground.
   */
  private static final float STANDING_ROOT_LIFT_UNITS = 28.5f;

  /**
  Computes a leg pivot's current model-space [X, Y, Z] before our procedural pitch is applied.
  */
  private static float[] transformPointInBody(
    GeoBone limb,
    float bodyPivY, float bodyPivZ,
    float bodyPosY, float bodyPosZ, float bodyRotX,
    float rootY, float rootZ
  ) {
    float lx = limb.getPivotX() + limb.getPosX();
    float ly = limb.getPivotY() + limb.getPosY();
    float lz = limb.getPivotZ() + limb.getPosZ();
    float dy = ly - bodyPivY;
    float dz = lz - bodyPivZ;
    float cos = Mth.cos(bodyRotX);
    float sin = Mth.sin(bodyRotX);
    float worldModelY = rootY + bodyPosY + bodyPivY + (dy * cos) - (dz * sin);
    float worldModelZ = rootZ + bodyPosZ + bodyPivZ + (dy * sin) + (dz * cos);
    return new float[]{ lx, worldModelY, worldModelZ };
  }

  private static float computeRotatedModelY(
    GeoBone limb,
    float bodyPivY, float bodyPivZ,
    float bodyPosY, float bodyRotX, float rootY
  ) {
    float ly = limb.getPivotY() + limb.getPosY();
    float lz = limb.getPivotZ() + limb.getPosZ();
    float dy = ly - bodyPivY;
    float dz = lz - bodyPivZ;
    return rootY + bodyPosY + bodyPivY + (dy * Mth.cos(bodyRotX)) - (dz * Mth.sin(bodyRotX));
  }

  private static float computeRotatedModelZ(
    GeoBone limb,
    float bodyPivY, float bodyPivZ,
    float bodyPosZ, float bodyRotX, float rootZ
  ) {
    float ly = limb.getPivotY() + limb.getPosY();
    float lz = limb.getPivotZ() + limb.getPosZ();
    float dy = ly - bodyPivY;
    float dz = lz - bodyPivZ;
    return rootZ + bodyPosZ + bodyPivZ + (dy * Mth.sin(bodyRotX)) + (dz * Mth.cos(bodyRotX));
  }

  private static float wrapRadians(float angle) {
    return Mth.wrapDegrees(angle / DEG_TO_RAD) * DEG_TO_RAD;
  }

  private static double raycastDownFromModelPoint(EnderDragon dragon, float modelX, float modelY, float modelZ) {
    Level level = dragon.level();
    if (level == null) return Double.NaN;

    float rad = (180.0f - dragon.yBodyRot) * DEG_TO_RAD;
    double cos = Math.cos(rad);
    double sin = Math.sin(rad);

    double centerOffsetX = modelX / 16.0D;
    double centerOffsetZ = modelZ / 16.0D;
    double shoulderWorldY = dragon.getY() + (modelY / 16.0D);

    // 6 model units wide/deep = +/- 3.0 model units (+/- 0.1875 blocks) half-extent
    final double halfRect = 8.0D / 16.0D;

    double bestSurfaceY = Double.NEGATIVE_INFINITY;

    for (int sample = 0; sample < 5; sample++) {
      double dx = (sample == 0) ? 0.0D : ((sample == 1 || sample == 2) ? halfRect : -halfRect);
      double dz = (sample == 0) ? 0.0D : ((sample == 1 || sample == 3) ? halfRect : -halfRect);

      double ox = centerOffsetX + dx;
      double oz = centerOffsetZ + dz;

      double wx = dragon.getX() + (ox * cos) + (oz * sin);
      double wz = dragon.getZ() - (ox * sin) + (oz * cos);

      double colY = sampleSingleColumnFromShoulder(level, dragon, wx, shoulderWorldY, wz);
      if (!Double.isNaN(colY) && colY > bestSurfaceY) {
        bestSurfaceY = colY;
      }
    }

    return (bestSurfaceY == Double.NEGATIVE_INFINITY) ? Double.NaN : bestSurfaceY;
  }

  private static double sampleSingleColumnFromShoulder(
    Level level, EnderDragon dragon, double wx, double shoulderWorldY, double wz
  ) {
    BlockPos atShoulder = BlockPos.containing(wx, shoulderWorldY, wz);
    if (!level.getBlockState(atShoulder).getCollisionShape(level, atShoulder).isEmpty()) {
      BlockPos cursor = atShoulder;
      for (int up = 0; up < 6; up++) {
        if (level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
          BlockPos below = cursor.below();
          return below.getY() + level.getBlockState(below).getCollisionShape(level, below).max(Direction.Axis.Y);
        }
        cursor = cursor.above();
      }
    }

    Vec3 start = new Vec3(wx, shoulderWorldY + 0.25D, wz);
    Vec3 end   = new Vec3(wx, shoulderWorldY - 6.00D, wz);
    BlockHitResult hit = level.clip(
      new ClipContext(
        start, end,
        ClipContext.Block.COLLIDER,
        ClipContext.Fluid.NONE,
        dragon
      )
    );
    return (hit.getType() != HitResult.Type.MISS) ? hit.getLocation().y : Double.NaN;
  }

  /**
  Solves an individual leg's 2 limbs (upper + tip) from its live bone pivots so the foot pivot sits
  right above the ground (4 units = 0.25b for front feet, 6 units = 0.375b for rear feet), then
  rotates the foot bone to be perpendicular to the vertical ground raycast.
  */

  private static void solveGroundedBodyAndLegsFromShoulders(
    MhEnderDragonModel<?> model, EnderDragon dragon,
    DragonPoseMode poseMode, DragonAction action, float prog,
    float gaitLiftNormA, float gaitLiftNormB
  ) {
    GeoBone root   = getBone(model, dragon, "root");
    GeoBone body   = getBone(model, dragon, "body");
    GeoBone frontR = getBone(model, dragon, "frontleg");
    GeoBone frontL = getBone(model, dragon, "frontleg1");
    GeoBone rearR  = getBone(model, dragon, "rearleg");
    GeoBone rearL  = getBone(model, dragon, "rearleg1");
    GeoBone fTipR  = getBone(model, dragon, "frontlegtip");
    GeoBone fTipL  = getBone(model, dragon, "frontlegtip1");
    GeoBone rTipR  = getBone(model, dragon, "rearlegtip");
    GeoBone rTipL  = getBone(model, dragon, "rearlegtip1");
    GeoBone fFootR = getBone(model, dragon, "frontfoot");
    GeoBone fFootL = getBone(model, dragon, "frontfoot1");
    GeoBone rFootR = getBone(model, dragon, "rearfoot");
    GeoBone rFootL = getBone(model, dragon, "rearfoot1");
    if (root == null || body == null || frontR == null || frontL == null || rearR == null || rearL == null
      || fTipR == null || fTipL == null || rTipR == null || rTipL == null
      || fFootR == null || fFootL == null || rFootR == null || rFootL == null) {
      return;
    }

    SmoothedLegState state = LEG_STATES.computeIfAbsent(dragon.getUUID(), id -> new SmoothedLegState());

    // Capture keyframe 0 (starting pose) whenever the animation or pose mode changes
    if (!state.hasStartSnapshot || state.lastPoseMode != poseMode || state.lastAction != action) {
      state.hasStartSnapshot = true;
      state.lastPoseMode = poseMode;
      state.lastAction = action;

      state.startRootPivY = root.getPivotY();
      state.startRootPosY = root.getPosY();
      state.startRootPivZ = root.getPivotZ();
      state.startRootPosZ = root.getPosZ();

      state.startBodyPivY = (body.getPivotY() != 0.0f) ? body.getPivotY() : BODY_PIVOT_Y;
      state.startBodyPivZ = (body.getPivotZ() != 0.0f) ? body.getPivotZ() : BODY_PIVOT_Z;
      state.startBodyPosY = body.getPosY();
      state.startBodyPosZ = body.getPosZ();
      state.startBodyRotX = body.getRotX();

      state.startFR.capture(frontR, fTipR, fFootR);
      state.startFL.capture(frontL, fTipL, fFootL);
      state.startRR.capture(rearR,  rTipR, rFootR);
      state.startRL.capture(rearL,  rTipL, rFootL);
    }

    // 1) Compute shoulder/hip positions from the cached keyframe-0 starting pose (unaffected by mid-clip bobbing)
    float flatRootY = state.startRootPivY + state.startRootPosY + STANDING_ROOT_LIFT_UNITS;
    float rootBaseZ = state.startRootPivZ + state.startRootPosZ;

    float frX = state.startFR.upperPivX + state.startFR.upperPosX;
    float frY = computeStartModelY(state.startFR, state.startBodyPivY, state.startBodyPivZ, state.startBodyPosY, state.startBodyRotX, flatRootY);
    float frZ = computeStartModelZ(state.startFR, state.startBodyPivY, state.startBodyPivZ, state.startBodyPosZ, state.startBodyRotX, rootBaseZ);

    float flX = state.startFL.upperPivX + state.startFL.upperPosX;
    float flY = computeStartModelY(state.startFL, state.startBodyPivY, state.startBodyPivZ, state.startBodyPosY, state.startBodyRotX, flatRootY);
    float flZ = computeStartModelZ(state.startFL, state.startBodyPivY, state.startBodyPivZ, state.startBodyPosZ, state.startBodyRotX, rootBaseZ);

    float rrX = state.startRR.upperPivX + state.startRR.upperPosX;
    float rrY = computeStartModelY(state.startRR, state.startBodyPivY, state.startBodyPivZ, state.startBodyPosY, state.startBodyRotX, flatRootY);
    float rrZ = computeStartModelZ(state.startRR, state.startBodyPivY, state.startBodyPivZ, state.startBodyPosZ, state.startBodyRotX, rootBaseZ);

    float rlX = state.startRL.upperPivX + state.startRL.upperPosX;
    float rlY = computeStartModelY(state.startRL, state.startBodyPivY, state.startBodyPivZ, state.startBodyPosY, state.startBodyRotX, flatRootY);
    float rlZ = computeStartModelZ(state.startRL, state.startBodyPivY, state.startBodyPivZ, state.startBodyPosZ, state.startBodyRotX, rootBaseZ);

    // 2) Raycast straight down from the 4 starting-pose shoulder/hip pivots
    double rawGroundFR = raycastDownFromModelPoint(dragon, frX, frY, frZ);
    double rawGroundFL = raycastDownFromModelPoint(dragon, flX, flY, flZ);
    double rawGroundRR = raycastDownFromModelPoint(dragon, rrX, rrY, rrZ);
    double rawGroundRL = raycastDownFromModelPoint(dragon, rlX, rlY, rlZ);

    float fallbackGround = (float) dragon.getY();
    float gFR = Double.isNaN(rawGroundFR) ? fallbackGround : (float) rawGroundFR;
    float gFL = Double.isNaN(rawGroundFL) ? fallbackGround : (float) rawGroundFL;
    float gRR = Double.isNaN(rawGroundRR) ? fallbackGround : (float) rawGroundRR;
    float gRL = Double.isNaN(rawGroundRL) ? fallbackGround : (float) rawGroundRL;

    state.groundY_FR = Float.isNaN(state.groundY_FR) ? gFR : Mth.lerp(0.32f, state.groundY_FR, gFR);
    state.groundY_FL = Float.isNaN(state.groundY_FL) ? gFL : Mth.lerp(0.32f, state.groundY_FL, gFL);
    state.groundY_RR = Float.isNaN(state.groundY_RR) ? gRR : Mth.lerp(0.32f, state.groundY_RR, gRR);
    state.groundY_RL = Float.isNaN(state.groundY_RL) ? gRL : Mth.lerp(0.32f, state.groundY_RL, gRL);

    // 3) Calculate body pitch angle (accounting for every leg + front-leg reach limit)
    float frontAvgY = (state.groundY_FR + state.groundY_FL) * 0.5f;
    float rearAvgY  = (state.groundY_RR + state.groundY_RL) * 0.5f;
    float heightDiffBlocks = frontAvgY - rearAvgY;

    float targetBodyPitch = (float) Math.atan2(heightDiffBlocks, 4.0f);

    float lowestFrontGroundY = Math.min(state.groundY_FR, state.groundY_FL);
    float frontFootHeightBlocks = FRONT_FOOT_HEIGHT_UNITS / 16.0f;
    float frontMaxReachBlocks = 2.05f;

    float testAvgRelBlocks = ((frontAvgY + rearAvgY) * 0.5f) - (float) dragon.getY();
    float testSin = Mth.sin(targetBodyPitch);
    float testCos = Mth.cos(targetBodyPitch);
    float testCenterShift = (BODY_CENTER_OFFSET_Y * (testCos - 1.0f)) - (BODY_CENTER_OFFSET_Z * testSin);
    float testRootY = state.startRootPivY + state.startRootPosY + STANDING_ROOT_LIFT_UNITS + (testAvgRelBlocks * 16.0f) - testCenterShift;

    float testShoulderY_FR = (float) dragon.getY() + computeStartModelY(state.startFR, state.startBodyPivY, state.startBodyPivZ, state.startBodyPosY, state.startBodyRotX + targetBodyPitch, testRootY) / 16.0f;
    float testShoulderY_FL = (float) dragon.getY() + computeStartModelY(state.startFL, state.startBodyPivY, state.startBodyPivZ, state.startBodyPosY, state.startBodyRotX + targetBodyPitch, testRootY) / 16.0f;
    float lowestShoulderY = Math.min(testShoulderY_FR, testShoulderY_FL);

    float requiredFrontReach = lowestShoulderY - (lowestFrontGroundY + frontFootHeightBlocks);
    if (requiredFrontReach > frontMaxReachBlocks) {
      float extraDropBlocks = requiredFrontReach - frontMaxReachBlocks;
      float extraNoseDownPitch = (float) Math.asin(Mth.clamp(extraDropBlocks / 2.6f, 0.0f, 0.75f));
      targetBodyPitch -= extraNoseDownPitch;
    }

    if (!Float.isFinite(targetBodyPitch)) targetBodyPitch = 0.0f;
    targetBodyPitch = Mth.clamp(targetBodyPitch, -52.0f * DEG_TO_RAD, 45.0f * DEG_TO_RAD);
    state.smoothedBodyPitch = Mth.lerp(0.24f, state.smoothedBodyPitch, targetBodyPitch);

    float avgGroundRelBlocks = ((frontAvgY + rearAvgY) * 0.5f) - (float) dragon.getY();
    float sinP = Mth.sin(state.smoothedBodyPitch);
    float cosP = Mth.cos(state.smoothedBodyPitch);
    float centerPitchShiftUnits = (BODY_CENTER_OFFSET_Y * (cosP - 1.0f)) - (BODY_CENTER_OFFSET_Z * sinP);
    float targetRootLiftUnits = Mth.clamp(
      STANDING_ROOT_LIFT_UNITS + (avgGroundRelBlocks * 16.0f) - centerPitchShiftUnits,
      STANDING_ROOT_LIFT_UNITS - 32.0f,
      STANDING_ROOT_LIFT_UNITS + 32.0f
    );
    state.smoothedRootLiftUnits = Mth.lerp(0.24f, state.smoothedRootLiftUnits, targetRootLiftUnits);

    root.setPosY(root.getPosY() + state.smoothedRootLiftUnits);
    float clampedFinalBodyRotX = Mth.clamp(body.getRotX() + state.smoothedBodyPitch, -65.0f * DEG_TO_RAD, 65.0f * DEG_TO_RAD);
    body.setRotX(clampedFinalBodyRotX);
    body.setRotZ(0.0f);
    root.setRotZ(0.0f);

    float finalStartRootY = state.startRootPivY + state.startRootPosY + state.smoothedRootLiftUnits;
    float finalStartBodyRotX = state.startBodyRotX + state.smoothedBodyPitch;
    float shoulderWorldY_FR = (float) dragon.getY() + computeStartModelY(state.startFR, state.startBodyPivY, state.startBodyPivZ, state.startBodyPosY, finalStartBodyRotX, finalStartRootY) / 16.0f;
    float shoulderWorldY_FL = (float) dragon.getY() + computeStartModelY(state.startFL, state.startBodyPivY, state.startBodyPivZ, state.startBodyPosY, finalStartBodyRotX, finalStartRootY) / 16.0f;
    float shoulderWorldY_RR = (float) dragon.getY() + computeStartModelY(state.startRR, state.startBodyPivY, state.startBodyPivZ, state.startBodyPosY, finalStartBodyRotX, finalStartRootY) / 16.0f;
    float shoulderWorldY_RL = (float) dragon.getY() + computeStartModelY(state.startRL, state.startBodyPivY, state.startBodyPivZ, state.startBodyPosY, finalStartBodyRotX, finalStartRootY) / 16.0f;

    // 4) Solve all 4 legs individually (4u front foot height, 6u rear foot height)
    float wFR = computeLegGroundIkWeight(action, prog, true,  true,  gaitLiftNormA);
    float wFL = computeLegGroundIkWeight(action, prog, true,  false, gaitLiftNormB);
    float wRR = computeLegGroundIkWeight(action, prog, false, true,  gaitLiftNormB * 0.85f);
    float wRL = computeLegGroundIkWeight(action, prog, false, false, gaitLiftNormA * 0.85f);

    solveSingleLegIk(model, dragon, state.startFR, "frontleg",  "frontlegtip",  "frontfoot",  shoulderWorldY_FR, state.groundY_FR, state.smoothedBodyPitch, wFR, true);
    solveSingleLegIk(model, dragon, state.startFL, "frontleg1", "frontlegtip1", "frontfoot1", shoulderWorldY_FL, state.groundY_FL, state.smoothedBodyPitch, wFL, true);
    solveSingleLegIk(model, dragon, state.startRR, "rearleg",   "rearlegtip",   "rearfoot",   shoulderWorldY_RR, state.groundY_RR, state.smoothedBodyPitch, wRR, false);
    solveSingleLegIk(model, dragon, state.startRL, "rearleg1",  "rearlegtip1",  "rearfoot1",  shoulderWorldY_RL, state.groundY_RL, state.smoothedBodyPitch, wRL, false);
  }

  /**
   * Computes the live vertical reach (in blocks) from upper pivot to foot pivot in the current animation.
   */
  private static float computeAnimatedVerticalReachBlocks(GeoBone upper, GeoBone tip, GeoBone foot) {
    float d1y = (tip.getPivotY() + tip.getPosY()) - (upper.getPivotY() + upper.getPosY());
    float d1z = (tip.getPivotZ() + tip.getPosZ()) - (upper.getPivotZ() + upper.getPosZ());
    float r1 = upper.getRotX();
    float v1y = (d1y * Mth.cos(r1)) - (d1z * Mth.sin(r1));

    float d2y = (foot.getPivotY() + foot.getPosY()) - (tip.getPivotY() + tip.getPosY());
    float d2z = (foot.getPivotZ() + foot.getPosZ()) - (tip.getPivotZ() + tip.getPosZ());
    float r2 = r1 + tip.getRotX();
    float v2y = (d2y * Mth.cos(r2)) - (d2z * Mth.sin(r2));

    return Math.max(0.45f, Math.abs(v1y + v2y) / 16.0f);
  }

  private static float computeStartModelY(
    LimbStartPose limb,
    float bodyPivY, float bodyPivZ,
    float bodyPosY, float bodyRotX, float rootY
  ) {
    float ly = limb.upperPivY + limb.upperPosY;
    float lz = limb.upperPivZ + limb.upperPosZ;
    float dy = ly - bodyPivY;
    float dz = lz - bodyPivZ;
    return rootY + bodyPosY + bodyPivY + (dy * Mth.cos(bodyRotX)) - (dz * Mth.sin(bodyRotX));
  }

  private static float computeStartModelZ(
    LimbStartPose limb,
    float bodyPivY, float bodyPivZ,
    float bodyPosZ, float bodyRotX, float rootZ
  ) {
    float ly = limb.upperPivY + limb.upperPosY;
    float lz = limb.upperPivZ + limb.upperPosZ;
    float dy = ly - bodyPivY;
    float dz = lz - bodyPivZ;
    return rootZ + bodyPosZ + bodyPivZ + (dy * Mth.sin(bodyRotX)) + (dz * Mth.cos(bodyRotX));
  }

  private static float evalAnkleRelY(float d1y, float d1z, float d2y, float d2z, float r1, float r2) {
    float y1 = (d1y * Mth.cos(r1)) - (d1z * Mth.sin(r1));
    float rTotal = r1 + r2;
    float y2 = (d2y * Mth.cos(rTotal)) - (d2z * Mth.sin(rTotal));
    return y1 + y2;
  }

  private static void solveSingleLegIk(
    MhEnderDragonModel<?> model, EnderDragon dragon,
    LimbStartPose startPose,
    String upperName, String tipName, String footName,
    float shoulderWorldY, float groundWorldY, float bodyPitchDelta,
    float ikWeight, boolean isFrontLeg
  ) {
    if (ikWeight <= 0.005f) return;

    GeoBone upper = getBone(model, dragon, upperName);
    GeoBone tip   = getBone(model, dragon, tipName);
    GeoBone foot  = getBone(model, dragon, footName);
    if (upper == null || tip == null || foot == null) return;

    float hipPivY = startPose.upperPivY + startPose.upperPosY;
    float hipPivZ = startPose.upperPivZ + startPose.upperPosZ;

    // Link vectors from keyframe-0 starting pose (in model units)
    float d1y = (startPose.tipPivY + startPose.tipPosY) - hipPivY;
    float d1z = (startPose.tipPivZ + startPose.tipPosZ) - hipPivZ;
    float d2y = (startPose.footPivY + startPose.footPosY) - (startPose.tipPivY + startPose.tipPosY);
    float d2z = (startPose.footPivZ + startPose.footPosZ) - (startPose.tipPivZ + startPose.tipPosZ);

    // Keyframe-0 ankle offset relative to hip in upright world-aligned space
    float startLocalY = evalAnkleLocalY(d1y, d1z, d2y, d2z, startPose.upperRotX, startPose.tipRotX);
    float startLocalZ = evalAnkleLocalZ(d1y, d1z, d2y, d2z, startPose.upperRotX, startPose.tipRotX);

    // Target ankle offset in world-aligned space:
    // - Y = ground + footHeightUnits (6u rear, 4u front)
    // - Z = exact same horizontal Z as keyframe 0
    float footHeightUnits = isFrontLeg ? FRONT_FOOT_HEIGHT_UNITS : REAR_FOOT_HEIGHT_UNITS;
    float targetWorldRelY = Mth.lerp(ikWeight, startLocalY, ((groundWorldY - shoulderWorldY) * 16.0f) + footHeightUnits);
    float targetWorldRelZ = startLocalZ;

    float cosB = Mth.cos(bodyPitchDelta);
    float sinB = Mth.sin(bodyPitchDelta);

    // 2x2 damped Gauss-Newton solver locking BOTH world-aligned Y and world-aligned Z
    float r1 = startPose.upperRotX;
    float r2 = startPose.tipRotX;
    final float eps = 0.002f;
    final float damping = 0.25f;

    for (int iter = 0; iter < 16; iter++) {
      float curLY = evalAnkleLocalY(d1y, d1z, d2y, d2z, r1, r2);
      float curLZ = evalAnkleLocalZ(d1y, d1z, d2y, d2z, r1, r2);
      float curWY = (curLY * cosB) - (curLZ * sinB);
      float curWZ = (curLY * sinB) + (curLZ * cosB);

      float errY = targetWorldRelY - curWY;
      float errZ = targetWorldRelZ - curWZ;
      if ((errY * errY + errZ * errZ) < 0.0025f) break;

      // Partial derivatives wrt r1
      float ly1 = evalAnkleLocalY(d1y, d1z, d2y, d2z, r1 + eps, r2);
      float lz1 = evalAnkleLocalZ(d1y, d1z, d2y, d2z, r1 + eps, r2);
      float jY1 = (((ly1 * cosB) - (lz1 * sinB)) - curWY) / eps;
      float jZ1 = (((ly1 * sinB) + (lz1 * cosB)) - curWZ) / eps;

      // Partial derivatives wrt r2
      float ly2 = evalAnkleLocalY(d1y, d1z, d2y, d2z, r1, r2 + eps);
      float lz2 = evalAnkleLocalZ(d1y, d1z, d2y, d2z, r1, r2 + eps);
      float jY2 = (((ly2 * cosB) - (lz2 * sinB)) - curWY) / eps;
      float jZ2 = (((ly2 * sinB) + (lz2 * cosB)) - curWZ) / eps;

      // Solve (J^T J + damping * I) * delta = J^T * err
      float a11 = (jY1 * jY1) + (jZ1 * jZ1) + damping;
      float a12 = (jY1 * jY2) + (jZ1 * jZ2);
      float a22 = (jY2 * jY2) + (jZ2 * jZ2) + damping;
      float b1  = (jY1 * errY) + (jZ1 * errZ);
      float b2  = (jY2 * errY) + (jZ2 * errZ);

      float det = (a11 * a22) - (a12 * a12);
      if (Math.abs(det) < 1e-5f) break;

      float dr1 = ((a22 * b1) - (a12 * b2)) / det;
      float dr2 = ((a11 * b2) - (a12 * b1)) / det;

      r1 = Mth.clamp(r1 + dr1, startPose.upperRotX - 1.25f, startPose.upperRotX + 1.25f);
      r2 = Mth.clamp(r2 + dr2, startPose.tipRotX - 1.45f, startPose.tipRotX + 1.45f);
    }

    // Apply solved rotation deltas additively onto the live animation frame
    float deltaR1 = (r1 - startPose.upperRotX) * ikWeight;
    float deltaR2 = (r2 - startPose.tipRotX) * ikWeight;

    upper.setRotX(upper.getRotX() + deltaR1);
    tip.setRotX(tip.getRotX() + deltaR2);

    // Keep foot perpendicular to the straight-down ground raycast
    float deltaFootRotX = -(bodyPitchDelta + deltaR1 + deltaR2);
    foot.setRotX(foot.getRotX() + deltaFootRotX);
    foot.setRotZ(0.0f);
  }

  private static float evalAnkleLocalY(float d1y, float d1z, float d2y, float d2z, float r1, float r2) {
    float y1 = (d1y * Mth.cos(r1)) - (d1z * Mth.sin(r1));
    float rTotal = r1 + r2;
    float y2 = (d2y * Mth.cos(rTotal)) - (d2z * Mth.sin(rTotal));
    return y1 + y2;
  }

  private static float evalAnkleLocalZ(float d1y, float d1z, float d2y, float d2z, float r1, float r2) {
    float z1 = (d1y * Mth.sin(r1)) + (d1z * Mth.cos(r1));
    float rTotal = r1 + r2;
    float z2 = (d2y * Mth.sin(rTotal)) + (d2z * Mth.cos(rTotal));
    return z1 + z2;
  }

  private static final class LimbStartPose {
    float upperPosX, upperPosY, upperPosZ;
    float upperPivX, upperPivY, upperPivZ;
    float tipPivY, tipPivZ, tipPosY, tipPosZ;
    float footPivY, footPivZ, footPosY, footPosZ;
    float upperRotX, tipRotX, footRotX;

    void capture(GeoBone upper, GeoBone tip, GeoBone foot) {
      this.upperPosX = upper.getPosX();
      this.upperPosY = upper.getPosY();
      this.upperPosZ = upper.getPosZ();
      this.upperPivX = upper.getPivotX();
      this.upperPivY = upper.getPivotY();
      this.upperPivZ = upper.getPivotZ();

      this.tipPivY = tip.getPivotY();
      this.tipPivZ = tip.getPivotZ();
      this.tipPosY = tip.getPosY();
      this.tipPosZ = tip.getPosZ();

      this.footPivY = foot.getPivotY();
      this.footPivZ = foot.getPivotZ();
      this.footPosY = foot.getPosY();
      this.footPosZ = foot.getPosZ();

      this.upperRotX = upper.getRotX();
      this.tipRotX   = tip.getRotX();
      this.footRotX  = foot.getRotX();
    }
  }

  private static final class SmoothedLegState {
    DragonPoseMode lastPoseMode = null;
    DragonAction lastAction = null;
    boolean hasStartSnapshot = false;

    float startRootPivY, startRootPosY, startRootPivZ, startRootPosZ;
    float startBodyPivY, startBodyPivZ, startBodyPosY, startBodyPosZ, startBodyRotX;

    final LimbStartPose startFR = new LimbStartPose();
    final LimbStartPose startFL = new LimbStartPose();
    final LimbStartPose startRR = new LimbStartPose();
    final LimbStartPose startRL = new LimbStartPose();

    float groundY_FR = Float.NaN;
    float groundY_FL = Float.NaN;
    float groundY_RR = Float.NaN;
    float groundY_RL = Float.NaN;
    float smoothedBodyPitch = 0.0f;
    float smoothedRootLiftUnits = 0.0f;
  }

  private static void applyLimbIkAndAttacks(
    MhEnderDragonModel<?> model, EnderDragon dragon, ClientAnimData anim,
    DragonPoseMode mode, DragonAction action, float prog
  ) {
    boolean grounded = (action != DragonAction.LANDING)
      && (mode == DragonPoseMode.GROUND_IDLE || mode == DragonPoseMode.GROUND_WALK);

    boolean walking = grounded && (mode == DragonPoseMode.GROUND_WALK || anim.smoothedSpeed > 0.02f);
    float strideA = 0.0f, liftA = 0.0f, strideB = 0.0f, liftB = 0.0f;
    if (walking) {
      strideA = (float) Math.sin(anim.gaitCycle) * 20.0f * DEG_TO_RAD;
      liftA = Math.max(0.0f, (float) Math.cos(anim.gaitCycle)) * 16.0f * DEG_TO_RAD;
      strideB = (float) Math.sin(anim.gaitCycle + Math.PI) * 20.0f * DEG_TO_RAD;
      liftB = Math.max(0.0f, (float) Math.cos(anim.gaitCycle + Math.PI)) * 16.0f * DEG_TO_RAD;
    }
    if (grounded) {
      float normLiftA = Mth.clamp(liftA / (16.0f * DEG_TO_RAD), 0.0f, 1.0f);
      float normLiftB = Mth.clamp(liftB / (16.0f * DEG_TO_RAD), 0.0f, 1.0f);
      solveGroundedBodyAndLegsFromShoulders(model, dragon, mode, action, prog, normLiftA, normLiftB);
    }
    if (walking) {
      applyLegStep(model, dragon, "frontleg",  "frontlegtip",  "frontfoot",  strideA, liftA);
      applyLegStep(model, dragon, "rearleg1",  "rearlegtip1",  "rearfoot1",  strideA * 0.85f, liftA * 0.85f);
      applyLegStep(model, dragon, "frontleg1", "frontlegtip1", "frontfoot1", strideB, liftB);
      applyLegStep(model, dragon, "rearleg",   "rearlegtip",   "rearfoot",   strideB * 0.85f, liftB * 0.85f);
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
  private static float computeLegGroundIkWeight(
    DragonAction action, float prog, boolean isFront, boolean isRightSide, float gaitLiftNorm
  ) {
    float weight = 1.0f - (gaitLiftNorm * 0.75f);
    float attackBlend = 0.0f;
    switch (action) {
      case CLAW_LEFT -> {
        if (isFront && isRightSide) attackBlend = Math.abs(swingCurve(prog, 0.32f, 0.58f));
      }
      case CLAW_RIGHT -> {
        if (isFront && !isRightSide) attackBlend = Math.abs(swingCurve(prog, 0.32f, 0.58f));
      }
      case DOUBLE_CLAW -> {
        if (isFront) {
          attackBlend = isRightSide
          ? Math.abs(swingCurve(prog, 0.20f, 0.44f))
          : Math.abs(swingCurve(prog, 0.52f, 0.76f));
        }
      }
      case BACK_LEG_LEFT -> {
        if (!isFront && isRightSide) attackBlend = Math.abs(swingCurve(prog, 0.30f, 0.60f));
      }
      case BACK_LEG_RIGHT -> {
        if (!isFront && !isRightSide) attackBlend = Math.abs(swingCurve(prog, 0.30f, 0.60f));
      }
      case SHIELD_STOMP_CLUSTER, SHIELD_STOMP_SPREAD -> {
        if (isFront) attackBlend = Mth.sin(Mth.clamp(prog, 0.0f, 1.0f) * Mth.PI);
      }
      default -> {}
    }
    return Mth.clamp(weight * (1.0f - attackBlend), 0.0f, 1.0f);
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
      case SHIELD_STOMP_CLUSTER, SHIELD_STOMP_SPREAD -> {
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
        return;
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

  private static void applyLegStep(MhEnderDragonModel<?> model, EnderDragon dragon, String upperName, String lowerName, String footName, float strideRad, float liftRad) {
    GeoBone upper = getBone(model, dragon, upperName);
    GeoBone lower = getBone(model, dragon, lowerName);
    GeoBone foot = getBone(model, dragon, footName);
    if (upper != null) upper.setRotX(upper.getRotX() + strideRad - liftRad);
    if (lower != null) lower.setRotX(lower.getRotX() + liftRad * 1.4f);
    if (foot != null) foot.setRotX(foot.getRotX() - strideRad * 0.5f - liftRad * 0.4f);
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
    boolean fullKeyed = isFullKeyframeAnim(mode, action);

    if (action != DragonAction.FIREBALL_SHORT && action != DragonAction.AIR_DASH && !fullKeyed) {
      for (String name : NECK_CHAIN) {
        resetBoneToBind(getBone(model, dragon, name));
      }
    }

    for (String name : TAIL_CHAIN) {
      resetBoneToBind(getBone(model, dragon, name));
    }

    if (action != DragonAction.LIFT_OFF_GROUND && action != DragonAction.AIR_DASH && action != DragonAction.FIREBALL_SHORT && !fullKeyed) {
      resetBoneToBind(getBone(model, dragon, "wing"));
      resetBoneToBind(getBone(model, dragon, "wingtip"));
      resetBoneToBind(getBone(model, dragon, "wing1"));
      resetBoneToBind(getBone(model, dragon, "wingtip1"));
    }
    for (String name : new String[]{ "frontleg", "frontleg1", "rearleg", "rearleg1" }) {
      GeoBone upper = getBone(model, dragon, name);
      if (upper != null && upper.getInitialSnapshot() != null) {
        upper.setPosY(upper.getInitialSnapshot().getOffsetY());
      }
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
  private static boolean isFullKeyframeAnim(DragonPoseMode mode, DragonAction action) {
    if (isNovaAnim(action)) return true;
    if (action == DragonAction.LANDING
    || action == DragonAction.CUSTOM_CLIP
    || action == DragonAction.SAFI_ROAR
    || action == DragonAction.TURN_LEFT_90
    || action == DragonAction.TURN_RIGHT_90
    || action == DragonAction.CLAW_LEFT
    || action == DragonAction.CLAW_RIGHT
    || action == DragonAction.DOUBLE_CLAW
    || action == DragonAction.SHIELD_STOMP_CLUSTER
    || action == DragonAction.SHIELD_STOMP_SPREAD) {
      return true;
    }
    return action == DragonAction.NONE
    && (mode == DragonPoseMode.GROUND_IDLE || mode == DragonPoseMode.GROUND_WALK);
  }

  private static final Map<UUID, TailChainState> TAIL_STATES = new ConcurrentHashMap<>();

  private static final class TailChainState {
    float lastAgeTicks = Float.NaN;
    final float[] yawDeg   = new float[TAIL_CHAIN.length];
    final float[] pitchDeg = new float[TAIL_CHAIN.length];
    final float[] yawVel   = new float[TAIL_CHAIN.length];
    final float[] pitchVel = new float[TAIL_CHAIN.length];
    float smoothedGroundFloorPitchDeg = 0.0f;
  }

  private static void applyTailPhysics(
    MhEnderDragonModel<?> model, EnderDragon dragon, ClientAnimData anim,
    float bodyYaw, float ageTicks,
    boolean isAirborne, DragonPoseMode mode, DragonAction action, float prog
  ) {
    TailChainState tail = TAIL_STATES.computeIfAbsent(dragon.getUUID(), id -> new TailChainState());
    SmoothedLegState legState = LEG_STATES.get(dragon.getUUID());

    // 1) Local lateral drift, forward speed, and live body bone twist
    Vec3 vel = dragon.getDeltaMovement();
    float rad = (180.0f - bodyYaw) * DEG_TO_RAD;
    double cos = Math.cos(rad);
    double sin = Math.sin(rad);
    float localLateralSpeed = (float) ((vel.x * cos) - (vel.z * sin));
    float forwardSpeed      = (float) Math.hypot(vel.x, vel.z);

    GeoBone bodyBone = getBone(model, dragon, "body");
    float bodyAnimYawDeg   = (bodyBone != null) ? (bodyBone.getRotY() / DEG_TO_RAD) : 0.0f;
    float bodyAnimPitchDeg = (bodyBone != null) ? (bodyBone.getRotX() / DEG_TO_RAD) : 0.0f;

    // 2) Terrain clearance check under mid-tail and tail-tip when grounded
    float targetFloorLiftDeg = 0.0f;
    if (!isAirborne && mode != DragonPoseMode.PILLAR_PERCH) {
      float bodyPitchRad = (legState != null) ? legState.smoothedBodyPitch : 0.0f;
      float rootLiftBlocks = (legState != null) ? (legState.smoothedRootLiftUnits / 16.0f) : (STANDING_ROOT_LIFT_UNITS / 16.0f);
      float baseTailY = (float) dragon.getY() + rootLiftBlocks + 0.85f + (2.5f * Mth.sin(bodyPitchRad));

      double midWX = dragon.getX() + (6.5D * sin);
      double midWZ = dragon.getZ() + (6.5D * cos);
      double tipWX = dragon.getX() + (10.5D * sin);
      double tipWZ = dragon.getZ() + (10.5D * cos);

      double midGround = sampleSingleColumnFromShoulder(dragon.level(), dragon, midWX, baseTailY + 1.5D, midWZ);
      double tipGround = sampleSingleColumnFromShoulder(dragon.level(), dragon, tipWX, baseTailY + 1.5D, tipWZ);

      double highestTailObstacle = Double.NEGATIVE_INFINITY;
      if (!Double.isNaN(midGround)) highestTailObstacle = Math.max(highestTailObstacle, midGround + 0.35D);
      if (!Double.isNaN(tipGround)) highestTailObstacle = Math.max(highestTailObstacle, tipGround + 0.35D);

      if (highestTailObstacle > Double.NEGATIVE_INFINITY) {
        float predictedTipY = baseTailY + (8.0f * Mth.sin(bodyPitchRad));
        float penetrationBlocks = (float) (highestTailObstacle - predictedTipY);
        if (penetrationBlocks > -0.25f) {
          targetFloorLiftDeg = -Mth.clamp((penetrationBlocks + 0.25f) * 7.5f, 0.0f, 34.0f);
        }
      }
    }
    tail.smoothedGroundFloorPitchDeg = Mth.lerp(0.18f, tail.smoothedGroundFloorPitchDeg, targetFloorLiftDeg);

    // 3) Evaluate the 4-second (80-tick) base 'tail' keyframe wiggle
    float wiggleXDeg = evalTailWiggleXDeg(ageTicks);
    float wiggleYDeg = evalTailWiggleYDeg(ageTicks);
    float wiggleZDeg = evalTailWiggleZDeg(ageTicks);

    // 4) Independent continuous sub-stepped spring-damper loop
    float dtTicks = Float.isNaN(tail.lastAgeTicks) ? 0.25f : Mth.clamp(ageTicks - tail.lastAgeTicks, 0.0f, 2.0f);
    tail.lastAgeTicks = ageTicks;

    if (dtTicks > 0.0001f) {
      boolean perched = (mode == DragonPoseMode.PILLAR_PERCH);
      float whipInput = 0.0f;
      if (action == DragonAction.TAIL_SWIPE_LR || action == DragonAction.TAIL_SWIPE_RL) {
        float dir = (action == DragonAction.TAIL_SWIPE_LR) ? 1.0f : -1.0f;
        whipInput = dir * swingCurve(prog, 0.25f, 0.70f) * 26.0f;
      }

      float turnImpulse = Mth.clamp(-anim.smoothedYawRate * 1.45f, -24.0f, 24.0f);
      float driftImpulse = Mth.clamp(-localLateralSpeed * 28.0f, -18.0f, 18.0f);
      float bodyTwistLag = Mth.clamp(-bodyAnimYawDeg * 0.35f, -16.0f, 16.0f);
      float rootTargetYaw = turnImpulse + driftImpulse + bodyTwistLag + whipInput;

      float velPitchImpulse = isAirborne
        ? Mth.clamp(-anim.smoothedPitch * 0.35f, -14.0f, 14.0f)
        : Mth.clamp((float) (-vel.y * 18.0D) - (bodyAnimPitchDeg * 0.20f), -10.0f, 10.0f);
      float speedStreamline = Mth.clamp(forwardSpeed * 12.0f, 0.0f, 6.0f);

      final float stepSize = 0.25f;
      int steps = Math.max(1, Mth.ceil(dtTicks / stepSize));
      float subDt = dtTicks / steps;

      for (int s = 0; s < steps; s++) {
        for (int i = 0; i < TAIL_CHAIN.length; i++) {
          float frac = (float) i / (TAIL_CHAIN.length - 1);

          float prevYaw   = (i == 1) ? (tail.yawDeg[0] + wiggleYDeg)   : (i > 1 ? tail.yawDeg[i - 1] : 0.0f);
          float prevPitch = (i == 1) ? (tail.pitchDeg[0] + wiggleXDeg) : (i > 1 ? tail.pitchDeg[i - 1] : 0.0f);

          float leadYaw = (i == 0) ? rootTargetYaw : (prevYaw * 0.92f);
          float leadPitch = (i == 0)
            ? (velPitchImpulse - speedStreamline)
            : (prevPitch * 0.90f);

          float stiffness = Mth.lerp(frac, 0.26f, 0.14f) * subDt;
          float damping   = (float) Math.pow(Mth.lerp(frac, 0.68f, 0.74f), subDt);

          float swayFreq = isAirborne ? 0.18f : (0.09f + forwardSpeed * 0.35f);
          float swayAmp  = (isAirborne ? 2.6f : (1.1f + forwardSpeed * 5.0f)) * (0.45f + frac * 0.85f);
          float phase    = i * 0.48f;

          float organicYaw   = (float) Math.sin(ageTicks * swayFreq - phase) * swayAmp;
          float organicPitch = (float) Math.cos(ageTicks * (swayFreq * 0.85f) - phase) * (swayAmp * 0.55f);

          float baseDrape = perched
            ? (i < 5 ? 2.6f : -1.4f)
            : (isAirborne ? -0.35f : Mth.lerp(frac, 1.35f, -1.10f));
          float floorAvoidSeg = (tail.smoothedGroundFloorPitchDeg / TAIL_CHAIN.length) * (1.35f - frac * 0.55f);

          float targetY = leadYaw + organicYaw;
          float targetP = leadPitch + organicPitch + baseDrape + floorAvoidSeg;

          tail.yawVel[i]   = (tail.yawVel[i] + (targetY - tail.yawDeg[i]) * stiffness) * damping;
          tail.pitchVel[i] = (tail.pitchVel[i] + (targetP - tail.pitchDeg[i]) * stiffness) * damping;

          tail.yawDeg[i]   = Mth.clamp(tail.yawDeg[i] + tail.yawVel[i] * subDt, -22.0f, 22.0f);
          tail.pitchDeg[i] = Mth.clamp(tail.pitchDeg[i] + tail.pitchVel[i] * subDt, -18.0f, 18.0f);
        }
      }
    }

    // 5) Apply solved angles to tail .. tail12 (with 4-second keyframe wiggle on base 'tail')
    for (int i = 0; i < TAIL_CHAIN.length; i++) {
      GeoBone seg = getBone(model, dragon, TAIL_CHAIN[i]);
      if (seg == null) continue;
      float extraX = (i == 0) ? wiggleXDeg : 0.0f;
      float extraY = (i == 0) ? wiggleYDeg : 0.0f;
      float extraZ = (i == 0) ? wiggleZDeg : 0.0f;

      seg.setRotX(seg.getRotX() + ((tail.pitchDeg[i] + extraX) * DEG_TO_RAD));
      seg.setRotY(seg.getRotY() + ((tail.yawDeg[i] + extraY) * DEG_TO_RAD));
      seg.setRotZ(seg.getRotZ() + (extraZ * DEG_TO_RAD));
    }
  }

  private static float evalTailWiggleXDeg(float ageTicks) {
    float tSec = (ageTicks % 80.0f) / 20.0f;
    return -5.0f * Mth.cos((float) Math.PI * tSec);
  }

  private static float evalTailWiggleYDeg(float ageTicks) {
    float tSec = (ageTicks % 80.0f) / 20.0f;
    return 5.0f * Mth.cos((float) (Math.PI * 0.5D) * tSec);
  }

  private static float evalTailWiggleZDeg(float ageTicks) {
    float tSec = (ageTicks % 80.0f) / 20.0f;
    return -2.5f - (2.5f * Mth.cos((float) Math.PI * tSec));
  }

}
