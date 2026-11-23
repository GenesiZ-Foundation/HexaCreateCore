package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.bossrush.dragon.DragonAnimState.DragonAction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DragonCombatExecutor {
  private static final java.util.Set<java.util.UUID> TRUE_KILL_BYPASS = java.util.concurrent.ConcurrentHashMap.newKeySet();

  public static boolean isTrueKillBypassed(Player player) {
    return TRUE_KILL_BYPASS.contains(player.getUUID())
    || player.getPersistentData().getBoolean("hexacreatecore:bypass_soul_death");
  }

  public static void forceInstantKill(ServerLevel level, Player player) {
    forceInstantKill(level, player, null, "%s body and soul got turned into stardust");
  }


  public static void forceInstantKill(
    ServerLevel level,
    Player player,
    @org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity attacker,
    String messageFormat
  ) {
    TRUE_KILL_BYPASS.add(player.getUUID());
    player.getPersistentData().putBoolean("hexacreatecore:bypass_soul_death", true);
    try {
      if (Float.isNaN(player.getHealth())) {
        player.setHealth(player.getMaxHealth());
      }
      player.removeAllEffects();
      player.setInvulnerable(false);
      net.minecraft.world.damagesource.DamageSource trueKillSource =
        new net.minecraft.world.damagesource.DamageSource(
          level.damageSources().genericKill().typeHolder(),
          attacker,
          attacker
        ) {
          @Override
          public net.minecraft.network.chat.Component getLocalizedDeathMessage(
            net.minecraft.world.entity.LivingEntity victim
          ) {
            return net.minecraft.network.chat.Component.literal(
              String.format(messageFormat, victim.getDisplayName().getString())
            );
          }
        };

      player.hurt(trueKillSource, 1_000_000.0f);
      if (player.isAlive() || Float.isNaN(player.getHealth())) {
        player.setHealth(0.0f);
        player.die(trueKillSource);
      }
    } finally {
      TRUE_KILL_BYPASS.remove(player.getUUID());
      player.getPersistentData().remove("hexacreatecore:bypass_soul_death");
    }
  }

  private DragonCombatExecutor() {}

  public static void tickActionEffects(ServerLevel level, EnderDragon dragon, DragonAction action, int tick, int dur, Player target) {
    float prog = (float) tick / (float) Math.max(1, dur);
    Vec3 forward = Vec3.directionFromRotation(0.0f, dragon.getYRot()).normalize();
    Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);

    switch (action) {
      case DOUBLE_CLAW -> {
        if (tick == 11 || tick == 23) {
          double sideSign = (tick == 11) ? -1.0D : 1.0D;
          dragon.setDeltaMovement(forward.scale(0.75D));
          Vec3 hitCenter = dragon.position().add(forward.scale(4.8D)).add(right.scale(sideSign * 2.5D));
          damagePlayersInBox(level, dragon, hitCenter, 4.2D, 13.0f, forward.scale(0.95D));
        }
      }
      case FORWARD_STOMP -> {
        if (tick == 19) {
          level.playSound(null, dragon.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.2f, 0.75f);
          Vec3 slamPos = dragon.position().add(forward.scale(4.5D));
          level.sendParticles(ParticleTypes.EXPLOSION, slamPos.x, slamPos.y + 0.5D, slamPos.z, 4, 1.5D, 0.3D, 1.5D, 0.0D);
          damagePlayersInBox(level, dragon, slamPos, 5.5D, 18.0f, forward.scale(1.1D).add(0.0D, 0.55D, 0.0D));
        }
      }
      case WING_FLAP_FORWARD, WING_FLAP_LEFT, WING_FLAP_RIGHT -> {
        if (tick == 8) {
          Vec3 pushDir = (action == DragonAction.WING_FLAP_FORWARD) ? forward
          : (action == DragonAction.WING_FLAP_LEFT) ? right.scale(-1.0D) : right;
          level.playSound(null, dragon.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 2.5f, 0.85f);
          applyRadialKnockback(level, dragon.position().add(pushDir.scale(3.0D)), 9.5D, 1.65D, 4.0f);
        }
      }
      case BITE_QUICK, BITE_LUNGE -> {
        int hitFrame = (action == DragonAction.BITE_QUICK) ? 8 : 12;
        if (tick == hitFrame) {
          if (action == DragonAction.BITE_LUNGE) dragon.setDeltaMovement(forward.scale(1.15D));
          Vec3 jawPos = dragon.position().add(forward.scale(action == DragonAction.BITE_LUNGE ? 6.0D : 4.5D)).add(0.0D, 1.5D, 0.0D);
          damagePlayersInBox(level, dragon, jawPos, 3.6D, action == DragonAction.BITE_LUNGE ? 17.0f : 12.0f, forward.scale(0.85D));
        }
      }
      case FIREBALL_SHORT -> {
        if (target != null) {
          Vec3 aimBase = target.position().add(0.0D, 1.0D, 0.0D);
          if (tick == 20 || tick == 28) {
            spawnDragonFireball(level, dragon, aimBase, 1.0f);
          } else if (tick == 50) {
            spawnFireballGrid3x3(level, dragon, aimBase);
          }
        }
      }
      case FIREBALL_CARPET, AIR_CHARGED_FIREBALL -> {
        if (target != null && (tick == 12 || (action == DragonAction.FIREBALL_CARPET && tick >= 14 && 34 >= tick && tick % 4 == 0))) {
          spawnDragonFireball(level, dragon, target.position().add(0.0D, 1.0D, 0.0D), action == DragonAction.AIR_CHARGED_FIREBALL ? 2.0f : 1.0f);
        }
      }
      case BREATH_SWIPE_90, FLYING_BREATH_45 -> {
        if (tick >= 10 && 40 >= tick) {
          float sweepAngle = dragon.getYRot() + Mth.lerp(prog, -45.0f, 45.0f);
          Vec3 beamDir = Vec3.directionFromRotation(22.0f, sweepAngle);
          traceBreathCone(level, dragon.position().add(forward.scale(4.0D)).add(0.0D, 2.5D, 0.0D), beamDir, 18.0D, 8.0f);
        }
      }
      case BACK_LEG_LEFT, BACK_LEG_RIGHT -> {
        if (tick == 10) {
          double sideSign = (action == DragonAction.BACK_LEG_LEFT) ? -1.0D : 1.0D;
          Vec3 legPos = dragon.position().subtract(forward.scale(3.0D)).add(right.scale(sideSign * 3.5D));
          damagePlayersInBox(level, dragon, legPos, 4.0D, 13.0f, right.scale(sideSign * 1.1D));
        }
      }
      case TAIL_SWIPE_LR, TAIL_SWIPE_RL -> {
        if (tick >= 10 && 18 >= tick) {
          double sweepProg = (tick - 10) / 8.0D;
          double angleOffset = (action == DragonAction.TAIL_SWIPE_LR) ? Mth.lerp(sweepProg, -90.0D, 90.0D) : Mth.lerp(sweepProg, 90.0D, -90.0D);
          Vec3 tailDir = Vec3.directionFromRotation(0.0f, dragon.getYRot() + 180.0f + (float) angleOffset);
          Vec3 tailPos = dragon.position().add(tailDir.scale(6.5D));
          damagePlayersInBox(level, dragon, tailPos, 3.8D, 15.0f, tailDir.scale(1.25D).add(0.0D, 0.4D, 0.0D));
        }
      }
      case AIR_STOMP -> {
        if (tick == 18) {
          level.playSound(null, dragon.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.60f);
          level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, dragon.getX(), dragon.getY(), dragon.getZ(), 2, 1.0D, 0.2D, 1.0D, 0.0D);
          applyRadialKnockback(level, dragon.position(), 12.0D, 1.5D, 16.0f);
        }
      }
      case LASER_TRIPLE -> {
        if (target != null && (tick == 14 || tick == 26 || tick == 38)) {
          Vec3 headPos = dragon.position().add(forward.scale(4.5D)).add(0.0D, 2.5D, 0.0D);
          Vec3 dir = target.getEyePosition().subtract(headPos).normalize();
          firePrecisionLaser(level, dragon, headPos, dir, 65.0D, 16.0f);
        }
      }
      case MULTIDIR_LASER -> {
        if (tick == 25 || tick == 42) {
          Vec3 origin = dragon.position().add(0.0D, 2.0D, 0.0D);
          if (target != null) {
            firePrecisionLaser(level, dragon, origin, target.getEyePosition().subtract(origin).normalize(), 65.0D, 14.0f);
          }
          for (int i = 0; 20 > i; i++) {
            float yaw = (360.0f / 20.0f) * i + (tick * 7.0f);
            float pitch = 15.0f + (i % 4) * 14.0f;
            firePrecisionLaser(level, dragon, origin, Vec3.directionFromRotation(pitch, yaw), 65.0D, 12.0f);
          }
        }
      }
      case DESPERATION_SPIN_CW, DESPERATION_SPIN_CCW -> {
        if (tick >= 10) {
          float dirMult = (action == DragonAction.DESPERATION_SPIN_CW) ? 1.0f : -1.0f;
          float spinYaw = dragon.getYRot() + (dirMult * prog * 360.0f);
          Vec3 headPos = dragon.position().add(0.0D, 2.0D, 0.0D);
          firePrecisionLaser(level, dragon, headPos, Vec3.directionFromRotation(18.0f, spinYaw), 65.0D, 18.0f);
        }
      }
      case LIFT_OFF_GROUND -> {
        if (tick == 8) {
          level.playSound(null, dragon.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 3.2f, 0.72f);
          level.sendParticles(ParticleTypes.CLOUD, dragon.getX(), dragon.getY(), dragon.getZ(), 28, 2.2D, 0.4D, 2.2D, 0.12D);
        }
        if (tick >= 8 && 17 >= tick) {
          dragon.noPhysics = true;
          dragon.setDeltaMovement(0.0D, 1.0D, 0.0D);
          dragon.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0.0D, 1.0D, 0.0D));
        }
      }
      case NOVA_PRE_CHARGE -> {
        // Every 1.0s (tick 1, 21): zombie_villager.converted @ pitch 0.5, 200-block range
        if (tick % 20 == 1) {
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.MASTER, 12.5f, 0.5f);
        }
        if (tick % 10 == 0) {
          emitNovaFloorBreath(level, dragon);
        }
        if (tick % 2 == 0) {
          stepNovaGalaxyWaves(level, dragon, tick);
        }
      }
      case NOVA_CHARGE -> {
        // Every 1.0s (tick 1, 21, ...): zombie_villager.converted @ pitch 0.5, 200-block range
        if (tick % 20 == 1) {
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.MASTER, 12.5f, 0.5f);
        }
        if (tick % 10 == 0) {
          emitNovaFloorBreath(level, dragon);
        }
        if (tick % 2 == 0) {
          stepNovaGalaxyWaves(level, dragon, 40 + tick);
        }
      }
      case NOVA_END_CHARGE -> {
        if (80 > tick) {
          // Every 1.0s (tick 1 = 4s left, 21 = 3s left, 41 = 2s left, 61 = 1s left)
          if (tick % 20 == 1) {
            level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
              SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.MASTER, 12.5f, 0.5f);

            float sonicPitch = switch (tick) {
              case 1 -> 1.0f;  // 4s left
              case 21 -> 0.8f; // 3s left
              case 41 -> 0.6f; // 2s left
              default -> 0.2f; // 1s left (tick 61)
            };
            level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
              SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.MASTER, 12.5f, sonicPitch);

            if (tick == 61) {
              level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                  net.minecraft.resources.ResourceLocation.withDefaultNamespace("item.trident.return")
                ), SoundSource.MASTER, 12.5f, 0.8f);
            }
          }
          if (tick % 10 == 0) {
            emitNovaFloorBreath(level, dragon);
          }
          if (tick % 2 == 0) {
            stepNovaGalaxyWaves(level, dragon, 320 + tick);
          }
        } else if (tick == 80) {
          // Exact 4.0s mark: Unlock 5-crystal shields & apply true instant-kill safe-zone check
          Vec3 origin = (dragon instanceof IMhEnderDragon mh) ? mh.hexacreatecore$getArenaOrigin() : dragon.position().subtract(0.0D, 20.0D, 0.0D);
          for (net.minecraft.world.entity.boss.enderdragon.EndCrystal c : level.getEntitiesOfClass(
            net.minecraft.world.entity.boss.enderdragon.EndCrystal.class,
            net.minecraft.world.phys.AABB.ofSize(origin, 110.0D, 80.0D, 110.0D)
          )) {
            c.setInvulnerable(false);
          }
          for (Player p : level.players()) {
            if (p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceToSqr(dragon.getX(), origin.y, dragon.getZ()) > 42.0D) {
              forceInstantKill(level, p, dragon, "%s was obliterated by the Ender Dragon's blackhole collapse");
            }
          }
          // Detonation sound & particle layer
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            SoundEvents.WARDEN_DEATH, SoundSource.MASTER, 12.5f, 0.3f);
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 12.5f, 0.8f);
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
              net.minecraft.resources.ResourceLocation.withDefaultNamespace("item.trident.return")
            ), SoundSource.MASTER, 12.5f, 0.5f);
          level.playSound(null, dragon.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.5f, 0.70f);
          level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, dragon.getX(), dragon.getY(), dragon.getZ(), 500, 28.0D, 4.0D, 28.0D, 0.25D);
        }
      }

      case LANDING -> {
        // 20 ticks (1.0s): 0.0-0.5s (t 1..10) = -2 blocks, 0.5-0.75s (t 11..15) = -1 block, 0.75-1.0s (t 16..20) = -1 block
        dragon.setPos(dragon.getX(), dragon.getY() - 0.20D, dragon.getZ());
        dragon.setDeltaMovement(Vec3.ZERO);
        if (tick == 1) {
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            SoundEvents.ENDER_DRAGON_FLAP, SoundSource.MASTER, 2.0f, 0.90f);
        } else if (tick == 20) {
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            SoundEvents.ANVIL_LAND, SoundSource.MASTER, 1.4f, 0.65f);
        }
      }
      case SAFI_ROAR -> {
        // end_roar (6.0s = 120 ticks) with 2x louder layered roar audio & front knockback
        if (tick == 5) {
          // 0.25s: Push players away if in front melee circle + flap + warden sonic_charge buffer
          for (Player p : level.players()) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
            Vec3 toP = p.position().subtract(dragon.position());
            double dist = Math.hypot(toP.x, toP.z);
            if (10.5D >= dist && dist > 0.01D) {
              Vec3 dirXZ = new Vec3(toP.x, 0.0D, toP.z).normalize();
              if (dirXZ.dot(forward) > 0.25D) {
                Vec3 push = dirXZ.scale(1.85D).add(0.0D, 0.45D, 0.0D);
                p.push(push.x, push.y, push.z);
                p.hurtMarked = true;
              }
            }
          }
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            SoundEvents.ENDER_DRAGON_FLAP, SoundSource.MASTER, 4.0f, 1.2f);
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.MASTER, 2.0f, 1.0f);
        } else if (tick == 10) {
          // 0.50s
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            SoundEvents.ENDER_DRAGON_GROWL, SoundSource.MASTER, 4.0f, 1.0f);
        } else if (tick == 17) {
          // 0.83s
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
              net.minecraft.resources.ResourceLocation.withDefaultNamespace("item.brush.brushing.sand.complete")
            ), SoundSource.MASTER, 2.0f, 0.1f);
        } else if (tick == 40) {
          // 2.00s: Massive layered roar + particle shockwave
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            SoundEvents.ENDER_DRAGON_GROWL, SoundSource.MASTER, 4.0f, 0.5f);
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            SoundEvents.WARDEN_DEATH, SoundSource.MASTER, 4.0f, 0.5f);
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            SoundEvents.ENDERMAN_DEATH, SoundSource.MASTER, 1.0f, 0.5f);
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            SoundEvents.ENDERMAN_SCREAM, SoundSource.MASTER, 2.0f, 0.2f);
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            SoundEvents.WITHER_DEATH, SoundSource.MASTER, 2.0f, 0.6f);

          double dx = dragon.getX();
          double dy = dragon.getY();
          double dz = dragon.getZ();
          level.sendParticles(ParticleTypes.END_ROD, dx, dy, dz, 250, 1.0D, 1.0D, 1.0D, 2.0D);
          level.sendParticles(ParticleTypes.DRAGON_BREATH, dx, dy, dz, 250, 1.0D, 1.0D, 1.0D, 1.0D);
          level.sendParticles(ParticleTypes.WITCH, dx, dy, dz, 500, 8.0D, 1.0D, 8.0D, 1.0D);
        } else if (tick == 82) {
          // 4.08s
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            SoundEvents.ENDER_DRAGON_FLAP, SoundSource.MASTER, 4.0f, 1.2f);
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            SoundEvents.ENDER_DRAGON_AMBIENT, SoundSource.MASTER, 4.0f, 0.8f);
        }
      }
      case TURN_LEFT_90, TURN_RIGHT_90, CLAW_LEFT, CLAW_RIGHT -> {
        // Active bite/sweep hitbox on head & neck between 0.25s (tick 5) and 0.79s (tick 16)
        if (tick == 6) {
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            SoundEvents.ENDER_DRAGON_FLAP, SoundSource.MASTER, 1.4f, 1.15f);
          level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
            SoundEvents.ENDER_DRAGON_GROWL, SoundSource.MASTER, 1.2f, 1.35f);
        }
        if (tick >= 5 && 16 >= tick) {
          DragonMultipartCollider.PartRig rig = DragonMultipartCollider.getOrCreateRig(dragon);
          net.minecraft.world.phys.AABB headBox = rig.head.getBoundingBox().inflate(1.15D, 1.25D, 1.15D);
          net.minecraft.world.phys.AABB neckBox = rig.neckSpan.getBoundingBox().inflate(0.95D, 1.10D, 0.95D);

          boolean isLeft = (action == DragonAction.TURN_LEFT_90 || action == DragonAction.CLAW_LEFT);
          float sweepAngleDeg = dragon.getYRot() + (isLeft ? -45.0f : 45.0f);
          Vec3 sweepPush = Vec3.directionFromRotation(0.0f, sweepAngleDeg).normalize().scale(1.35D).add(0.0D, 0.35D, 0.0D);

          for (Player p : level.players()) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
            net.minecraft.world.phys.AABB pBox = p.getBoundingBox();
            if (pBox.intersects(headBox) || pBox.intersects(neckBox)) {
              if (p.hurt(level.damageSources().mobAttack(dragon), 27.0f)) {
                p.push(sweepPush.x, sweepPush.y, sweepPush.z);
                p.hurtMarked = true;
              }
            }
          }
        }
      }
      default -> {}
    }
  }

  public static void spawnFireballGrid3x3(ServerLevel level, EnderDragon dragon, Vec3 targetCenter) {
    Vec3 toTarget = targetCenter.subtract(dragon.position()).normalize();
    Vec3 right = new Vec3(-toTarget.z, 0.0D, toTarget.x).normalize();
    Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
    double spacing = 2.6D;

    for (int row = -1; 1 >= row; row++) {
      for (int col = -1; 1 >= col; col++) {
        Vec3 gridAim = targetCenter.add(right.scale(col * spacing)).add(up.scale(row * spacing));
        spawnDragonFireball(level, dragon, gridAim, 1.0f);
      }
    }
  }

  public static void spawnDragonFireball(ServerLevel level, EnderDragon dragon, Vec3 targetPos, float scale) {
    float bodyYaw = dragon.getYRot();
    Vec3 bodyForward = Vec3.directionFromRotation(0.0f, bodyYaw).normalize();

    boolean perched = (dragon instanceof IMhEnderDragon mh
      && mh.hexacreatecore$getPoseMode() == DragonAnimState.DragonPoseMode.PILLAR_PERCH);

    Vec3 neckRoot = dragon.position().add(bodyForward.scale(perched ? 3.9D : 2.4D));

    double toTargetX = targetPos.x - neckRoot.x;
    double toTargetZ = targetPos.z - neckRoot.z;
    float worldYawToTarget = (float) (Mth.atan2(toTargetZ, toTargetX) * (180.0D / Math.PI)) - 90.0f;
    float clampedRelYaw = Mth.clamp(Mth.wrapDegrees(worldYawToTarget - bodyYaw), -115.0f, 115.0f);
    float headYaw = bodyYaw + clampedRelYaw;

    Vec3 headForward = Vec3.directionFromRotation(0.0f, headYaw).normalize();
    double neckReach = perched ? 2.4D : 3.2D;
    double mouthYOffset = perched ? -1.45D : 0.35D;

    Vec3 mouth = neckRoot.add(headForward.scale(neckReach)).add(0.0D, mouthYOffset, 0.0D);
    Vec3 dir = targetPos.subtract(mouth).normalize();

    DragonFireball fb = new DragonFireball(level, dragon, dir.scale(5.6D));
    fb.accelerationPower = 0.4D;
    fb.setDeltaMovement(dir.scale(3.6D));
    fb.setPos(mouth.x, mouth.y, mouth.z);
    level.addFreshEntity(fb);
  }

  public static void firePrecisionLaser(ServerLevel level, EnderDragon dragon, Vec3 start, Vec3 dir, double maxLen, float damage) {
    level.playSound(null, dragon.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.2f, 1.35f);
    for (double d = 1.0D; maxLen >= d; d += 1.5D) {
      Vec3 pt = start.add(dir.scale(d));
      level.sendParticles(ParticleTypes.END_ROD, pt.x, pt.y, pt.z, 2, 0.05D, 0.05D, 0.05D, 0.0D);
      damagePlayersInBox(level, dragon, pt, 1.4D, damage, dir.scale(0.9D));
    }
  }

  private static void traceBreathCone(ServerLevel level, Vec3 start, Vec3 dir, double len, float damage) {
    for (double d = 1.5D; len >= d; d += 2.0D) {
      Vec3 pt = start.add(dir.scale(d));
      level.sendParticles(ParticleTypes.DRAGON_BREATH, pt.x, pt.y, pt.z, 5, 0.6D, 0.4D, 0.6D, 0.02D);
      for (Player p : level.getEntitiesOfClass(Player.class, AABB.ofSize(pt, 3.2D, 3.2D, 3.2D))) {
        if (p.isAlive() && !p.isCreative() && !p.isSpectator()) {
          p.hurt(level.damageSources().dragonBreath(), damage);
        }
      }
    }
  }

  private static void damagePlayersInBox(ServerLevel level, EnderDragon dragon, Vec3 center, double radius, float damage, Vec3 knockback) {
    AABB box = AABB.ofSize(center, radius * 2.0D, radius * 1.8D, radius * 2.0D);
    for (Player p : level.getEntitiesOfClass(Player.class, box)) {
      if (p.isAlive() && !p.isCreative() && !p.isSpectator()) {
        p.hurt(level.damageSources().mobAttack(dragon), damage);
        p.push(knockback.x, Math.max(0.25D, knockback.y), knockback.z);
        p.hurtMarked = true;
      }
    }
  }

  private static void applyRadialKnockback(ServerLevel level, Vec3 center, double radius, double strength, float damage) {
    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(center, radius * 2.0D, 10.0D, radius * 2.0D))) {
      if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
      if (e instanceof EnderDragon) continue;

      Vec3 diff = e.position().subtract(center);
      double dist = Math.max(0.5D, Math.hypot(diff.x, diff.z));
      if (radius >= dist) {
        Vec3 push = new Vec3(diff.x / dist, 0.0D, diff.z / dist).scale(strength);
        e.push(push.x, 0.45D, push.z);
        e.hurtMarked = true;
        if (damage > 0.0f && e instanceof Player p) {
          p.hurt(level.damageSources().magic(), damage);
        }
      }
    }
  }

  private static void spawnHorizontalRing(ServerLevel level, Vec3 center, double radius, int count) {
    for (int i = 0; count > i; i++) {
      double a = (2.0D * Math.PI * i) / count;
      level.sendParticles(ParticleTypes.END_ROD, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }
  }

  private static void emitNovaFloorBreath(ServerLevel level, EnderDragon dragon) {
    double dX = dragon.getX();
    double dY = dragon.getY();
    double dZ = dragon.getZ();
    int maxGroundY = (int) Math.floor(dY) - 1;
    int minScanY = Math.max(level.getMinBuildHeight(), maxGroundY - 48);

    // 2x2 block grid DRAGON_BREATH under the dragon (200-block perimeter, emitted within 20 blocks of each player)
    for (Player p : level.players()) {
      if (!(p instanceof net.minecraft.server.level.ServerPlayer sp) || !sp.isAlive() || sp.isSpectator()) continue;

      int pX = Mth.floor(sp.getX());
      int pZ = Mth.floor(sp.getZ());
      int startX = ((pX - 20) >> 1) << 1;
      int endX = pX + 20;
      int startZ = ((pZ - 20) >> 1) << 1;
      int endZ = pZ + 20;

      net.minecraft.core.BlockPos.MutableBlockPos cursor = new net.minecraft.core.BlockPos.MutableBlockPos();
      for (int gx = startX; endX >= gx; gx += 2) {
        double dxPlayer = (gx + 0.5D) - sp.getX();
        double dxDragon = (gx + 0.5D) - dX;
        if (Math.abs(dxDragon) > 200.0D) continue;

        for (int gz = startZ; endZ >= gz; gz += 2) {
          double dzPlayer = (gz + 0.5D) - sp.getZ();
          double dzDragon = (gz + 0.5D) - dZ;

          if ((dxPlayer * dxPlayer + dzPlayer * dzPlayer) > 20.0D * 20.0D) continue;
          if ((dxDragon * dxDragon + dzDragon * dzDragon) > 200.0D * 200.0D) continue;

          for (int y = Math.min(level.getMaxBuildHeight() - 1, maxGroundY); y >= minScanY; y--) {
            cursor.set(gx, y, gz);
            if (!level.getBlockState(cursor).isAir()) {
              level.sendParticles(sp, ParticleTypes.DRAGON_BREATH, true, gx + 0.5D, y + 1.08D, gz + 0.5D, 1, 0.18D, 0.04D, 0.18D, 0.015D);
              break;
            }
          }
        }
      }
    }
  }

    private static void stepNovaGalaxyWaves(ServerLevel level, EnderDragon dragon, int currentTick) {
    double dX = dragon.getX();
    double dY = dragon.getY() + 2.5D;
    double dZ = dragon.getZ();

    // New wave spawns every 0.5s (10 ticks) and takes 2.0s (40 ticks) to reach the dragon
    int latestPulse = currentTick / 10;
    int earliestPulse = Math.max(1, (currentTick - 40) / 10);
    int arms = 10;

    for (int pulseIndex = earliestPulse; latestPulse >= pulseIndex; pulseIndex++) {
      int ageTicks = currentTick - (pulseIndex * 10);
      if (0 > ageTicks || ageTicks > 40) continue;

      double t = ageTicks / 40.0D;
      double tNext = Math.min(1.0D, (ageTicks + 2) / 40.0D);

      double yawRad = Math.toRadians(pulseIndex * 15.0D);
      double pitchRad = Math.toRadians(pulseIndex * 10.0D);
      double cosPitch = Math.cos(pitchRad);
      double sinPitch = Math.sin(pitchRad);

      for (int arm = 0; arms > arm; arm++) {
        double baseArmAngle = (Math.PI * 2.0D * arm) / arms;

        Vec3 cur = evalGalaxyPoint(dX, dY, dZ, baseArmAngle, yawRad, cosPitch, sinPitch, t);
        Vec3 next = evalGalaxyPoint(dX, dY, dZ, baseArmAngle, yawRad, cosPitch, sinPitch, tNext);

        // Velocity per tick toward the next spiral step so particles stream smoothly over 2.0s
        double vx = (next.x - cur.x) * 0.5D;
        double vy = (next.y - cur.y) * 0.5D;
        double vz = (next.z - cur.z) * 0.5D;

        for (Player p : level.players()) {
          if (p instanceof net.minecraft.server.level.ServerPlayer sp && 180.0D * 180.0D >= sp.distanceToSqr(cur.x, cur.y, cur.z)) {
            level.sendParticles(sp, ParticleTypes.END_ROD, true, cur.x, cur.y, cur.z, 0, vx, vy, vz, 1.0D);
          }
        }
      }
    }
  }

  private static Vec3 evalGalaxyPoint(
    double cx, double cy, double cz,
    double baseArmAngle, double yawRad, double cosPitch, double sinPitch, double t
  ) {
    double radius = Mth.lerp(t, 56.0D, 2.5D);
    double spiralTheta = baseArmAngle + (t * Math.PI * 2.25D) + yawRad;

    double lx = Math.cos(spiralTheta) * radius;
    double ly = Math.sin(t * Math.PI) * 1.8D;
    double lz = Math.sin(spiralTheta) * radius;

    double ry = (ly * cosPitch) - (lz * sinPitch);
    double rz = (ly * sinPitch) + (lz * cosPitch);
    return new Vec3(cx + lx, cy + ry, cz + rz);
  }

}
