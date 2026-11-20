package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.bossrush.dragon.DragonAnimState.DragonAction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public final class DragonSpatialSelector {

  public enum RangeRing { CLOSE, MEDIUM, LONG }
  public enum DirectionCone { FRONT, LEFT, RIGHT, BACK }

  public record SpatialContext(
    RangeRing ring,
    DirectionCone cone,
    boolean isLeftHalf,
    float relYawDeg,
    double distance
  ) {}

  private DragonSpatialSelector() {}

  public static SpatialContext analyzeTarget(EnderDragon dragon, Player target) {
    double dx = target.getX() - dragon.getX();
    double dz = target.getZ() - dragon.getZ();
    double dist = Math.hypot(dx, dz);

    RangeRing ring = (7.5D >= dist) ? RangeRing.CLOSE
    : (17.0D >= dist) ? RangeRing.MEDIUM
    : RangeRing.LONG;

    float worldYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0f;
    float relYaw = Mth.wrapDegrees(worldYaw - dragon.getYRot());
    float absYaw = Math.abs(relYaw);

    DirectionCone cone;
    if (45.0f >= absYaw) {
      cone = DirectionCone.FRONT;
    } else if (135.0f >= absYaw) {
      cone = (0.0f > relYaw) ? DirectionCone.LEFT : DirectionCone.RIGHT;
    } else {
      cone = DirectionCone.BACK;
    }

    return new SpatialContext(ring, cone, 0.0f > relYaw, relYaw, dist);
  }

  public static DragonAction selectPhase2GroundMove(SpatialContext ctx, int consecutiveTurns, RandomSource rng) {
    // Anti-circle-strafe punish if player keeps orbiting during turns
    if (consecutiveTurns >= 2) {
      return switch (ctx.cone()) {
        case LEFT -> DragonAction.WING_FLAP_LEFT;
        case RIGHT -> DragonAction.WING_FLAP_RIGHT;
        case BACK -> ctx.isLeftHalf() ? DragonAction.TAIL_SWIPE_LR : DragonAction.TAIL_SWIPE_RL;
        default -> DragonAction.WING_FLAP_FORWARD;
      };
    }

    List<DragonAction> pool = new ArrayList<>();
    switch (ctx.cone()) {
      case FRONT -> {
        if (ctx.ring() == RangeRing.CLOSE) {
          pool.add(DragonAction.BITE_QUICK);
          pool.add(DragonAction.BITE_LUNGE);
          pool.add(DragonAction.CLAW_LEFT);
          pool.add(DragonAction.CLAW_RIGHT);
          pool.add(DragonAction.WING_FLAP_FORWARD);
          pool.add(DragonAction.FORWARD_STOMP);
        } else if (ctx.ring() == RangeRing.MEDIUM) {
          pool.add(DragonAction.DOUBLE_CLAW);
          pool.add(DragonAction.FORWARD_STOMP);
          pool.add(DragonAction.BITE_LUNGE);
          pool.add(DragonAction.FIREBALL_SHORT);
          pool.add(DragonAction.BREATH_SWIPE_90);
        } else {
          pool.add(DragonAction.FIREBALL_CARPET);
          pool.add(DragonAction.FLYING_BREATH_45);
          pool.add(DragonAction.FIREBALL_SHORT);
        }
      }
      case LEFT -> {
        if (ctx.ring() == RangeRing.CLOSE) {
          pool.add(DragonAction.CLAW_LEFT);
          pool.add(DragonAction.WING_FLAP_LEFT);
        }
        pool.add(DragonAction.NONE); // Signals 90-deg turn left
      }
      case RIGHT -> {
        if (ctx.ring() == RangeRing.CLOSE) {
          pool.add(DragonAction.CLAW_RIGHT);
          pool.add(DragonAction.WING_FLAP_RIGHT);
        }
        pool.add(DragonAction.NONE); // Signals 90-deg turn right
      }
      case BACK -> {
        if (ctx.ring() == RangeRing.CLOSE) {
          pool.add(ctx.isLeftHalf() ? DragonAction.BACK_LEG_LEFT : DragonAction.BACK_LEG_RIGHT);
          pool.add(ctx.isLeftHalf() ? DragonAction.TAIL_SWIPE_LR : DragonAction.TAIL_SWIPE_RL);
        } else if (ctx.ring() == RangeRing.MEDIUM) {
          pool.add(ctx.isLeftHalf() ? DragonAction.TAIL_SWIPE_LR : DragonAction.TAIL_SWIPE_RL);
          pool.add(DragonAction.NONE);
        } else {
          pool.add(DragonAction.NONE);
        }
      }
    }
    return pool.isEmpty() ? DragonAction.NONE : pool.get(rng.nextInt(pool.size()));
  }

  public static DragonAction selectPhase2AirMove(SpatialContext ctx, RandomSource rng) {
    List<DragonAction> pool = List.of(
      DragonAction.AIR_DASH,
      DragonAction.AIR_CHARGED_FIREBALL,
      DragonAction.AIR_STOMP
    );
    return pool.get(rng.nextInt(pool.size()));
  }

  public static DragonAction selectPhase3FlightMove(boolean playerNearEdge, RandomSource rng) {
    if (playerNearEdge && rng.nextFloat() < 0.45f) {
      return DragonAction.WING_FLAP_FORWARD; // Edge wing push toward blackhole
    }
    List<DragonAction> pool = List.of(
      DragonAction.LASER_TRIPLE,
      DragonAction.AIR_DASH,
      DragonAction.BITE_LUNGE,
      DragonAction.FIREBALL_CARPET,
      DragonAction.MULTIDIR_LASER,
      DragonAction.LASER_SWIPE
    );
    return pool.get(rng.nextInt(pool.size()));
  }
}

