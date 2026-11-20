package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.bossrush.dragon.DragonAnimState.DragonAction;
import com.genesiz.hexacreatecore.bossrush.dragon.DragonAnimState.DragonPoseMode;
import com.genesiz.hexacreatecore.bossrush.dragon.DragonArenaController.ArenaState;
import com.genesiz.hexacreatecore.bossrush.dragon.DragonSpatialSelector.SpatialContext;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EnderDragonMhBrain {

  private static final class BrainMemory {
    int phase = 1;
    boolean phase1MidNovaDone = false;
    boolean phase3Nova20Done = false;
    boolean phase3Ground15Done = false;
    boolean phase3Nova7Done = false;
    boolean desperationTriggered = false;
    int desperationStage = 0;
    int stageTimer = 0;

    boolean isAirborne = true;
    int decisionCooldown = 40;
    int consecutiveTurns = 0;
    int p3ShieldTicks = 0;

    EndCrystal latchedCrystal = null;
    int pillarLatchStage = 0;
    int latchTimer = 0;
    Vec3 chargeTarget = null;
  }


  private static final Map<UUID, BrainMemory> MEMORIES = new HashMap<>();

  private EnderDragonMhBrain() {}

  public static void removeDragon(UUID uuid) {
    MEMORIES.remove(uuid);
  }

  public static void tickServer(ServerLevel level, EnderDragon dragon, IMhEnderDragon mh) {
    if (!dragon.isAlive()) {
      MEMORIES.remove(dragon.getUUID());
      ArenaState state = DragonArenaController.getAllArenas().get(net.minecraft.core.BlockPos.containing(mh.hexacreatecore$getArenaOrigin()));
      if (state != null) {
        state.blackholeActive = false;
      }
      return;
    }

    BrainMemory mem = MEMORIES.computeIfAbsent(dragon.getUUID(), id -> new BrainMemory());
    Vec3 origin = mh.hexacreatecore$getArenaOrigin();
    float hpRatio = dragon.getHealth() / Math.max(1.0f, dragon.getMaxHealth());

    // Advance active action timer (AIR_DASH is driven directly by Stage 3 in tickPhase1)
    DragonAction active = mh.hexacreatecore$getActiveAction();
    if (active != DragonAction.NONE && active != DragonAction.AIR_DASH) {
      int nextTick = mh.hexacreatecore$getActionTick() + 1;
      int dur = mh.hexacreatecore$getActionDuration();
      mh.hexacreatecore$setActionTick(nextTick);
      Player target = findNearestPlayer(level, dragon, 96.0D);
      DragonCombatExecutor.tickActionEffects(level, dragon, active, nextTick, dur, target);

      if (nextTick >= dur) {
        mh.hexacreatecore$triggerAction(DragonAction.NONE, 0);
        mem.decisionCooldown = 14;
        onActionFinished(level, dragon, mh, mem, active);
      }
      return;
    }

    if (mem.p3ShieldTicks > 0) {
      mem.p3ShieldTicks--;
      mh.hexacreatecore$setBarrierShield(mem.p3ShieldTicks > 0);
    }

    // Phase 1: Flying & End Crystals (Invulnerable until all crystals are down)
    if (mem.phase == 1) {
      tickPhase1(level, dragon, mh, mem, origin);
      return;
    }

    // Trigger Phase 3 at 30% HP
    if (mem.phase == 2 && 0.30f >= hpRatio) {
      mem.phase = 3;
      mh.hexacreatecore$setFightPhase(3);
      mh.hexacreatecore$setSupercritical(true);
      mem.isAirborne = true;
      mem.p3ShieldTicks = 400; // 20s shield
      mh.hexacreatecore$setBarrierShield(true);
      mh.hexacreatecore$triggerAction(DragonAction.SAFI_ROAR, 60);
      DragonArenaController.triggerPhase3IslandTransition(level, origin);
      return;
    }

    // Trigger Desperation at 1% HP
    if (mem.phase == 3 && !mem.desperationTriggered && 0.015f >= hpRatio) {
      mem.desperationTriggered = true;
      mem.desperationStage = 1;
      mem.stageTimer = 0;
      mh.hexacreatecore$setBarrierShield(true);
      DragonArenaController.extinguishAllIslandFires(level, origin);
    }

    if (mem.desperationTriggered) {
      tickDesperation(level, dragon, mh, mem, origin);
      return;
    }

    if (mem.phase == 2) {
      tickPhase2(level, dragon, mh, mem, origin);
    } else {
      tickPhase3(level, dragon, mh, mem, origin, hpRatio);
    }
  }

  private static void tickPhase1(ServerLevel level, EnderDragon dragon, IMhEnderDragon mh, BrainMemory mem, Vec3 origin) {
    List<EndCrystal> crystals = level.getEntitiesOfClass(EndCrystal.class, AABB.ofSize(origin, 110.0D, 80.0D, 110.0D));
    int count = crystals.size();
    mh.hexacreatecore$setBarrierShield(count > 0);

    // Mid-phase 5-crystal shield + 20s safe-zone nova
    if (count == 5 && !mem.phase1MidNovaDone) {
      mem.phase1MidNovaDone = true;
      mem.pillarLatchStage = 0;
      for (EndCrystal c : crystals) c.setInvulnerable(true);
      dragon.setPos(origin.x, origin.y + 10.0D, origin.z);
      mh.hexacreatecore$setPoseMode(DragonPoseMode.FLY_HOVER);
      mh.hexacreatecore$triggerAction(DragonAction.NOVA_CHARGE, 400);
      return;
    }

    // All crystals destroyed -> enter Phase 2
    if (count == 0) {
      mem.phase = 2;
      mem.isAirborne = false;
      mem.pillarLatchStage = 0;
      mh.hexacreatecore$setFightPhase(2);
      mh.hexacreatecore$setBarrierShield(false);
      mh.hexacreatecore$setPoseMode(DragonPoseMode.GROUND_IDLE);
      mh.hexacreatecore$triggerAction(DragonAction.SAFI_ROAR, 60);
      return;
    }

    // Stage 1: Dash-Dive toward the top of an obsidian pillar & braking flare
    if (mem.pillarLatchStage == 1) {
      mem.latchTimer++;
      if (mem.latchedCrystal == null || !mem.latchedCrystal.isAlive() || mem.latchTimer > 120) {
        mem.pillarLatchStage = 0;
        mem.latchTimer = 0;
        return;
      }

      net.minecraft.core.BlockPos probe = mem.latchedCrystal.blockPosition().below();
      while (probe.getY() > level.getMinBuildHeight() && level.getBlockState(probe).isAir()) {
        probe = probe.below();
      }
      Vec3 landingTop = new Vec3(mem.latchedCrystal.getX(), probe.getY() + 1.0D, mem.latchedCrystal.getZ());
      double distToCap = dragon.position().distanceTo(landingTop);

      if (distToCap > 7.5D) {
        steerToward(dragon, landingTop.add(0.0D, 1.5D, 0.0D), 1.35D);
        mh.hexacreatecore$setPoseMode(DragonPoseMode.FLY_GLIDE);
      } else {
        steerToward(dragon, landingTop, 0.65D);
        mh.hexacreatecore$setPoseMode(DragonPoseMode.FLY_HOVER);
      }

      if (2.4D >= distToCap) {
        mem.pillarLatchStage = 2;
        mem.latchTimer = 0;
        dragon.setPos(landingTop.x, landingTop.y, landingTop.z);
        dragon.setDeltaMovement(Vec3.ZERO);
        level.playSound(null, dragon.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 2.5f, 0.75f);
        level.playSound(null, dragon.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4f, 0.65f);

        Player target = findNearestPlayer(level, dragon, 96.0D);
        Vec3 lookTarget = (target != null) ? target.position() : origin;
        float perchYaw = (float) (Mth.atan2(lookTarget.z - landingTop.z, lookTarget.x - landingTop.x) * (180.0D / Math.PI)) - 90.0f;
        dragon.setYRot(perchYaw);
        dragon.yBodyRot = perchYaw;
        mh.hexacreatecore$setPoseMode(DragonPoseMode.PILLAR_PERCH);
        mh.hexacreatecore$triggerAction(DragonAction.FIREBALL_SHORT, 60);
      }
      return;
    }

    // Stage 2: Perched on top of the pillar -> runs fireball_volley, then triggers lift_off_ground
    if (mem.pillarLatchStage == 2) {
      dragon.setDeltaMovement(Vec3.ZERO);
      mh.hexacreatecore$setPoseMode(DragonPoseMode.PILLAR_PERCH);
      mh.hexacreatecore$triggerAction(DragonAction.LIFT_OFF_GROUND, 20);
      return;
    }

    // Stage 3: Aerial Charge (0-0.25s stop -> 0.25-1.5s turn -> 1.5s+ dash to target)
    if (mem.pillarLatchStage == 3) {
      mem.latchTimer++;
      Player target = findNearestPlayer(level, dragon, 96.0D);

      if (5 >= mem.latchTimer) {
        // 0.0s - 0.25s (ticks 1-5): Brake to a mid-air halt
        dragon.setDeltaMovement(Vec3.ZERO);
        mh.hexacreatecore$setPoseMode(DragonPoseMode.FLY_HOVER);
        mh.hexacreatecore$setActionTick(mem.latchTimer);
      } else if (32 >= mem.latchTimer) {
        // 0.25s - 1.60s (ticks 6-32): Turn yaw & pitch body toward player's live position
        dragon.setDeltaMovement(Vec3.ZERO);
        mh.hexacreatecore$setPoseMode(DragonPoseMode.FLY_HOVER);
        mh.hexacreatecore$setActionTick(mem.latchTimer);
        if (target != null) {
          Vec3 playerPos = target.position().add(0.0D, 1.0D, 0.0D);
          Vec3 toPlayer = playerPos.subtract(dragon.position());
          Vec3 diveDir = (toPlayer.lengthSqr() > 0.01D) ? toPlayer.normalize() : Vec3.directionFromRotation(0.0f, dragon.getYRot());
          mem.chargeTarget = playerPos.add(diveDir.scale(30.0D));

          float desiredYaw = (float) (Mth.atan2(toPlayer.z, toPlayer.x) * (180.0D / Math.PI)) - 90.0f;
          float desiredPitch = Mth.clamp((float) (Mth.atan2(toPlayer.y, Math.hypot(toPlayer.x, toPlayer.z)) * (180.0D / Math.PI)), -75.0f, 75.0f);

          dragon.setYRot(Mth.rotLerp(0.22f, dragon.getYRot(), desiredYaw));
          dragon.setXRot(Mth.rotLerp(0.22f, dragon.getXRot(), desiredPitch));
          dragon.yBodyRot = dragon.getYRot();
        }
      } else {
        // 1.60s+ (tick 33+): Launch dash through player & 30 blocks past while keeping body aimed
        if (mem.chargeTarget == null) {
          mem.pillarLatchStage = 0;
          dragon.setXRot(0.0f);
          mh.hexacreatecore$triggerAction(DragonAction.NONE, 0);
          return;
        }

        if (45 >= mem.latchTimer) {
          mh.hexacreatecore$setActionTick(mem.latchTimer);
        } else {
          if (mh.hexacreatecore$getActiveAction() != DragonAction.NONE) {
            mh.hexacreatecore$triggerAction(DragonAction.NONE, 0);
          }
          mh.hexacreatecore$setPoseMode(DragonPoseMode.FLY_GLIDE);
        }

        if (mem.latchTimer == 33) {
          level.playSound(null, dragon.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0f, 1.15f);
          level.playSound(null, dragon.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 3.0f, 0.75f);
        }

        Vec3 toEnd = mem.chargeTarget.subtract(dragon.position());
        if (toEnd.lengthSqr() > 0.05D) {
          float dashPitch = Mth.clamp((float) (Mth.atan2(toEnd.y, Math.hypot(toEnd.x, toEnd.z)) * (180.0D / Math.PI)), -75.0f, 75.0f);
          dragon.setXRot(Mth.rotLerp(0.25f, dragon.getXRot(), dashPitch));
        }

        steerToward(dragon, mem.chargeTarget, 3.50D);

        // Hitbox knockback if player is in the dive path
        for (Player p : level.getEntitiesOfClass(Player.class, AABB.ofSize(dragon.position(), 6.5D, 5.5D, 6.5D))) {
          if (p.isAlive() && !p.isCreative() && !p.isSpectator()) {
            Vec3 knock = dragon.getDeltaMovement().normalize().scale(1.8D).add(0.0D, 0.55D, 0.0D);
            p.hurt(level.damageSources().mobAttack(dragon), 16.0f);
            p.push(knock.x, knock.y, knock.z);
            p.hurtMarked = true;
          }
        }

        // Arrived 30 blocks past target (or 65-tick safety cap) -> level out & return to normal flying
        if (4.0D >= dragon.position().distanceTo(mem.chargeTarget) || mem.latchTimer > 65) {
          mem.pillarLatchStage = 0;
          dragon.setXRot(0.0f);
          mh.hexacreatecore$triggerAction(DragonAction.NONE, 0);
          mh.hexacreatecore$setPoseMode(DragonPoseMode.FLY_GLIDE);
          mem.decisionCooldown = 130 + dragon.getRandom().nextInt(90);
        }
      }
      return;
    }

    // Default Phase 1: High 32-block radius circular orbit (origin.y + 34)
    double highestCrystalY = origin.y + 38.0D;
    for (EndCrystal c : crystals) {
      if (c.getY() > highestCrystalY) {
        highestCrystalY = c.getY();
      }
    }

    double curAngle = Mth.atan2(dragon.getZ() - origin.z, dragon.getX() - origin.x);
    double leadAngle = curAngle + 0.30D;
    double targetX = origin.x + Math.cos(leadAngle) * 32.0D;
    double targetZ = origin.z + Math.sin(leadAngle) * 32.0D;
    double targetY = highestCrystalY + 12.0D + Math.sin(dragon.tickCount * 0.05D) * 2.0D;

    steerToward(dragon, new Vec3(targetX, targetY, targetZ), 0.90D);
    mh.hexacreatecore$setPoseMode(DragonPoseMode.FLY_GLIDE);

    if (--mem.decisionCooldown <= 0) {
      Player target = findNearestPlayer(level, dragon, 80.0D);
      if (target != null && dragon.getRandom().nextFloat() < 0.40f) {
        mem.pillarLatchStage = 3;
        mem.latchTimer = 0;
        mem.chargeTarget = target.position().add(0.0D, 1.0D, 0.0D);
        mh.hexacreatecore$triggerAction(DragonAction.AIR_DASH, 65);
      } else {
        mem.latchedCrystal = crystals.get(dragon.getRandom().nextInt(crystals.size()));
        mem.pillarLatchStage = 1;
        mem.latchTimer = 0;
      }
    }
  }

  private static void tickPhase2(ServerLevel level, EnderDragon dragon, IMhEnderDragon mh, BrainMemory mem, Vec3 origin) {
    Player target = findNearestPlayer(level, dragon, 64.0D);
    if (target == null) return;

    if (mem.isAirborne) {
      mh.hexacreatecore$setPoseMode(DragonPoseMode.FLY_HOVER);
      steerToward(dragon, target.position().add(0.0D, 11.0D, 0.0D), 0.55D);
      if (--mem.decisionCooldown <= 0) {
        SpatialContext ctx = DragonSpatialSelector.analyzeTarget(dragon, target);
        DragonAction next = DragonSpatialSelector.selectPhase2AirMove(ctx, dragon.getRandom());
        mh.hexacreatecore$triggerAction(next, next.defaultDurationTicks);
      }
      return;
    }

    // Grounded Monster Hunter Spatial Ring + 4-Cone AI
    SpatialContext ctx = DragonSpatialSelector.analyzeTarget(dragon, target);
    if (mem.decisionCooldown > 0) {
      mem.decisionCooldown--;
      if (ctx.ring() == DragonSpatialSelector.RangeRing.LONG) {
        mh.hexacreatecore$setPoseMode(DragonPoseMode.GROUND_WALK);
        steerToward(dragon, target.position(), 0.35D);
      } else {
        mh.hexacreatecore$setPoseMode(DragonPoseMode.GROUND_IDLE);
        dragon.setDeltaMovement(Vec3.ZERO);
      }
      return;
    }

    DragonAction chosen = DragonSpatialSelector.selectPhase2GroundMove(ctx, mem.consecutiveTurns, dragon.getRandom());
    if (chosen == DragonAction.NONE) {
      // 90-degree reposition pivot toward player
      float turnStep = ctx.isLeftHalf() ? -90.0f : 90.0f;
      dragon.setYRot(Mth.wrapDegrees(dragon.getYRot() + turnStep));
      dragon.yBodyRot = dragon.getYRot();
      mem.consecutiveTurns++;
      mem.decisionCooldown = 12;
    } else {
      mem.consecutiveTurns = 0;
      mh.hexacreatecore$triggerAction(chosen, chosen.defaultDurationTicks);
    }
  }

  private static void tickPhase3(ServerLevel level, EnderDragon dragon, IMhEnderDragon mh, BrainMemory mem, Vec3 origin, float hpRatio) {
    Vec3 skyCenter = origin.add(0.0D, 106.0D, 0.0D);

    if (0.20f >= hpRatio && !mem.phase3Nova20Done) {
      mem.phase3Nova20Done = true;
      mem.isAirborne = true;
      DragonArenaController.igniteNextSkyIsland(level, origin);
      mh.hexacreatecore$triggerAction(DragonAction.BREATH_SWIPE_90, 100);
      return;
    }
    if (0.15f >= hpRatio && !mem.phase3Ground15Done) {
      mem.phase3Ground15Done = true;
      mem.isAirborne = false;
      Vec3 safeIsland = DragonArenaController.getUnburnedIslandTop(origin);
      dragon.setPos(safeIsland.x, safeIsland.y + 1.0D, safeIsland.z);
      mh.hexacreatecore$setPoseMode(DragonPoseMode.GROUND_IDLE);
      mh.hexacreatecore$triggerAction(DragonAction.AIR_STOMP, 28);
      return;
    }
    if (0.07f >= hpRatio && !mem.phase3Nova7Done) {
      mem.phase3Nova7Done = true;
      mem.isAirborne = true;
      DragonArenaController.igniteNextSkyIsland(level, origin);
      mh.hexacreatecore$triggerAction(DragonAction.BREATH_SWIPE_90, 100);
      return;
    }

    if (!mem.isAirborne) {
      tickPhase2(level, dragon, mh, mem, DragonArenaController.getUnburnedIslandTop(origin));
      return;
    }

    mh.hexacreatecore$setPoseMode(DragonPoseMode.FLY_HOVER);
    steerToward(dragon, skyCenter, 0.65D);

    if (--mem.decisionCooldown <= 0) {
      Player target = findNearestPlayer(level, dragon, 96.0D);
      boolean nearEdge = target != null && DragonArenaController.isPlayerNearIslandEdge(origin, target.position());
      DragonAction next = DragonSpatialSelector.selectPhase3FlightMove(nearEdge, dragon.getRandom());
      mh.hexacreatecore$triggerAction(next, next.defaultDurationTicks);
    }
  }

  private static void tickDesperation(ServerLevel level, EnderDragon dragon, IMhEnderDragon mh, BrainMemory mem, Vec3 origin) {
    Vec3 skyCenter = origin.add(0.0D, 108.0D, 0.0D);
    mem.stageTimer++;
    switch (mem.desperationStage) {
      case 1 -> {
        // Skyfall fireball barrage across all 3 islands (5 seconds)
        steerToward(dragon, skyCenter, 0.7D);
        if (mem.stageTimer % 6 == 0) {
          DragonArenaController.rainSkyfallFireballs(level, dragon, origin);
        }
        if (mem.stageTimer >= 100) {
          mem.desperationStage = 2;
          mem.stageTimer = 0;
          mh.hexacreatecore$triggerAction(DragonAction.DESPERATION_SPIN_CW, 80);
        }
      }
      case 2 -> {
        mem.desperationStage = 3;
        mh.hexacreatecore$triggerAction(DragonAction.DESPERATION_SPIN_CCW, 80);
      }
      case 3 -> {
        // Blackhole collapse & perimeter exploding stars
        DragonArenaController.detonatePerimeterStars(level, dragon, origin);
        mem.desperationStage = 4;
        mem.stageTimer = 0;
        mh.hexacreatecore$triggerAction(DragonAction.DESPERATION_FINAL_BEAM, 200);
      }
      case 4 -> {
        // Final 10s judgment beam completed -> collapse into cosmetic blackhole & finish fight
        level.playSound(null, dragon.blockPosition(), SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 4.0f, 0.75f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, dragon.getX(), dragon.getY(), dragon.getZ(), 4, 2.0D, 2.0D, 2.0D, 0.0D);
        DragonArenaController.cleanupTemporaryArena(level, origin);
        dragon.setHealth(0.0f);
        dragon.die(level.damageSources().genericKill());
        MEMORIES.remove(dragon.getUUID());
      }
    }
  }

  private static void onActionFinished(ServerLevel level, EnderDragon dragon, IMhEnderDragon mh, BrainMemory mem, DragonAction finished) {
    if (finished == DragonAction.NOVA_CHARGE) {
      // Unlock 5-crystal shields after 20s safe-zone nova & check safe radius directly under dragon
      Vec3 origin = mh.hexacreatecore$getArenaOrigin();
      for (EndCrystal c : level.getEntitiesOfClass(EndCrystal.class, AABB.ofSize(origin, 110.0D, 80.0D, 110.0D))) {
        c.setInvulnerable(false);
      }
      for (Player p : level.players()) {
        if (p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceToSqr(dragon.getX(), origin.y, dragon.getZ()) > 42.0D) {
          p.hurt(level.damageSources().magic(), 36.0f);
        }
      }
      mh.hexacreatecore$triggerAction(DragonAction.NOVA_DETONATE, 24);
    } else if (finished == DragonAction.FIREBALL_CARPET || finished == DragonAction.FLYING_BREATH_45) {
      mem.isAirborne = true;
    } else if (finished == DragonAction.AIR_DASH || finished == DragonAction.AIR_STOMP) {
      mem.isAirborne = false;
    } else if (finished == DragonAction.FIREBALL_SHORT) {
      if (mem.phase == 1 || mem.pillarLatchStage == 2) {
        mh.hexacreatecore$triggerAction(DragonAction.LIFT_OFF_GROUND, 20);
      } else {
        mem.pillarLatchStage = 0;
        mem.decisionCooldown = 140 + dragon.getRandom().nextInt(100);
      }
    } else if (finished == DragonAction.LIFT_OFF_GROUND) {
      mem.pillarLatchStage = 0;
      mem.isAirborne = true;
      mh.hexacreatecore$setPoseMode(DragonPoseMode.FLY_GLIDE);
      mem.decisionCooldown = 140 + dragon.getRandom().nextInt(100);
    }
  }

  private static void steerToward(EnderDragon dragon, Vec3 target, double speed) {
    dragon.noPhysics = true;
    Vec3 diff = target.subtract(dragon.position());
    double dist = diff.length();
    if (0.15D > dist) return;

    Vec3 step = diff.scale(Math.min(speed, dist) / dist);
    dragon.setDeltaMovement(step);
    dragon.move(net.minecraft.world.entity.MoverType.SELF, step);

    double horiz = Math.hypot(diff.x, diff.z);
    if (horiz > 0.05D) {
      float desiredYaw = (float) (Mth.atan2(diff.z, diff.x) * (180.0D / Math.PI)) - 90.0f;
      dragon.setYRot(Mth.rotLerp(0.18f, dragon.getYRot(), desiredYaw));
      dragon.yBodyRot = dragon.getYRot();
    }
  }

  private static Player findNearestPlayer(ServerLevel level, EnderDragon dragon, double range) {
    Player best = null;
    double bestSq = range * range;
    for (Player p : level.players()) {
      if (!p.isAlive() || p.isSpectator()) continue;
      double dSq = dragon.distanceToSqr(p);
      if (bestSq > dSq) {
        bestSq = dSq;
        best = p;
      }
    }
    return best;
  }
}
