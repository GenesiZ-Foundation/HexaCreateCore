package com.genesiz.hexacreatecore.bossrush.dragon;

import net.minecraft.util.Mth;

public final class DragonAnimState {

  public enum DragonPoseMode {
    GROUND_IDLE(0),
    GROUND_WALK(1),
    FLY_HOVER(2),
    FLY_GLIDE(3),
    REAR_UP(4),
    PILLAR_PERCH(5),
    TOPPLED(6);

    public final int id;
    DragonPoseMode(int id) { this.id = id; }

    public static DragonPoseMode byId(int id) {
      for (DragonPoseMode m : values()) if (m.id == id) return m;
      return FLY_HOVER;
    }
  }

  public enum DragonAction {
    NONE(0, 0),
    SAFI_ROAR(1, 150),
    CLAW_LEFT(2, 30),
    CLAW_RIGHT(3, 30),
    DOUBLE_CLAW(4, 65),
    SHIELD_STOMP_CLUSTER(5, 75),
    WING_FLAP_FORWARD(6, 20),
    WING_FLAP_LEFT(7, 20),
    WING_FLAP_RIGHT(8, 20),
    BITE_QUICK(9, 20),
    BITE_LUNGE(10, 28),
    FIREBALL_SHORT(11, 30),
    FIREBALL_CARPET(12, 44),
    BREATH_SWIPE_90(13, 48),
    FLYING_BREATH_45(14, 44),
    BACK_LEG_LEFT(15, 24),
    BACK_LEG_RIGHT(16, 24),
    TAIL_SWIPE_LR(17, 28),
    TAIL_SWIPE_RL(18, 28),
    AIR_DASH(19, 26),
    AIR_CHARGED_FIREBALL(20, 36),
    AIR_STOMP(21, 28),
    NOVA_CHARGE(22, 400),
    NOVA_DETONATE(23, 24),
    LASER_TRIPLE(24, 48),
    MULTIDIR_LASER(25, 60),
    LASER_SWIPE(26, 44),
    DESPERATION_SPIN_CW(27, 80),
    DESPERATION_SPIN_CCW(28, 80),
    DESPERATION_FINAL_BEAM(29, 200),
    LIFT_OFF_GROUND(30, 20),
    NOVA_PRE_CHARGE(31, 40),
    NOVA_END_CHARGE(32, 125),
    LANDING(33, 20),
    TURN_LEFT_90(34, 30),
    TURN_RIGHT_90(35, 30),
    SHIELD_STOMP_SPREAD(36, 75),
    CUSTOM_CLIP(37, 40);

    public final int id;
    public final int defaultDurationTicks;

    DragonAction(int id, int defaultDurationTicks) {
      this.id = id;
      this.defaultDurationTicks = defaultDurationTicks;
    }

    public static DragonAction byId(int id) {
      for (DragonAction a : values()) if (a.id == id) return a;
      return NONE;
    }
  }

  /** Client-side smoothed IK & spring-damper state per EnderDragon instance */
  public static final class ClientAnimData {
    public float wingCycle = 0.0f;
    public float gaitCycle = 0.0f;
    public float smoothedSpeed = 0.0f;
    public float smoothedYawRate = 0.0f;
    public float smoothedPitch = 0.0f;
    public float smoothedRoll = 0.0f;
    public float prevBodyYaw = 0.0f;
    public boolean initialized = false;

    // 5-bone neck IK smoothed rotations (neck2, neck3, neck4, neck5, head)
    public final float[] neckYaw = new float[5];
    public final float[] neckPitch = new float[5];
    public final float[] neckRoll = new float[5];
    public float ikBlend = 0.0f;
    public float mouthOpenBlend = 0.0f;
    public float supercriticalGlow = 0.0f;

    // 12-bone tail inertia history ring buffer
    public static final int TAIL_HISTORY = 32;
    public final float[] yawHistory = new float[TAIL_HISTORY];
    public final float[] pitchHistory = new float[TAIL_HISTORY];
    public int historyCursor = 0;
    public int lastSampleTick = -1;

    public float lastDesiredYaw = 0.0f;
    public float lastDesiredPitch = 0.0f;

    public float footOffsetFR = 0.0f;
    public float footOffsetFL = 0.0f;
    public float footOffsetRR = 0.0f;
    public float footOffsetRL = 0.0f;




    public void pushHistory(float yawDelta, float pitchVal) {
      yawHistory[historyCursor] = Mth.clamp(yawDelta, -18.0f, 18.0f);
      pitchHistory[historyCursor] = Mth.clamp(pitchVal, -25.0f, 25.0f);
      historyCursor = (historyCursor + 1) % TAIL_HISTORY;
    }

    public float sampleYawHistory(int ticksAgo) {
      int idx = ((historyCursor - 1 - ticksAgo) % TAIL_HISTORY + TAIL_HISTORY) % TAIL_HISTORY;
      return yawHistory[idx];
    }

    public float samplePitchHistory(int ticksAgo) {
      int idx = ((historyCursor - 1 - ticksAgo) % TAIL_HISTORY + TAIL_HISTORY) % TAIL_HISTORY;
      return pitchHistory[idx];
    }
  }

  private DragonAnimState() {}
}
