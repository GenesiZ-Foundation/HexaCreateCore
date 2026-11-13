package com.genesiz.hexacreatecore.difficulty;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.difficulty.data.DifficultyData.*;
import com.genesiz.hexacreatecore.difficulty.network.DifficultyPackets.SoulSyncPayload;
import com.genesiz.hexacreatecore.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = HexaCreateCore.MODID)
public final class SoulRecoveryManager {

  private static final int SOUL_DURATION_TICKS = 240; // 12.0s total timer (never resets)
  private static final int WALL_PENALTY_TICKS = 40;   // -2.0s penalty per wall impact
  private static final Map<UUID, ActiveSoul> ACTIVE_SOULS = new ConcurrentHashMap<>();
  private static final java.util.Set<UUID> TRUE_DEATH_PENDING = ConcurrentHashMap.newKeySet();


  private static final class ActiveSoul {
    final Vec3 bodyPos;
    final Vec3 launchPos;
    final float bodyYaw;
    final float bodyPitch;
    final int nextStacks;
    int ticksRemaining;

    ActiveSoul(Vec3 bodyPos, Vec3 launchPos, float bodyYaw, float bodyPitch, int nextStacks, int ticksRemaining) {
      this.bodyPos = bodyPos;
      this.launchPos = launchPos;
      this.bodyYaw = bodyYaw;
      this.bodyPitch = bodyPitch;
      this.nextStacks = nextStacks;
      this.ticksRemaining = ticksRemaining;
    }
  }


  private SoulRecoveryManager() {}

  public static boolean isInSoulForm(UUID uuid) {
    return ACTIVE_SOULS.containsKey(uuid);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void onIncomingDamageWhileSoul(LivingIncomingDamageEvent event) {
    if (event.getEntity() instanceof ServerPlayer player && ACTIVE_SOULS.containsKey(player.getUUID())) {
      event.setCanceled(true);
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void onPlayerFatalDamage(LivingDeathEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    if (player.level().isClientSide()) return;
    if (TRUE_DEATH_PENDING.remove(player.getUUID())) {
      ACTIVE_SOULS.remove(player.getUUID());
      player.setInvulnerable(false);
      player.setPose(Pose.STANDING);
      syncSoul(player, null, 0);
      return;
    }

    ServerLevel level = player.serverLevel();
    DifficultyTier tier = DifficultySavedData.get(level).getActiveTier();
    if (tier == DifficultyTier.NORMAL) return;

    if (ACTIVE_SOULS.containsKey(player.getUUID())) {
      ACTIVE_SOULS.remove(player.getUUID());
      player.setInvulnerable(false);
      syncSoul(player, null, 0);
      return;
    }

    MobEffectInstance existing = player.getEffect(ModEffects.SOUL_FRACTURE);
    int currentStacks = (existing != null) ? (existing.getAmplifier() + 1) : 0;
    if (currentStacks >= 3) {
      return;
    }

    event.setCanceled(true);
    player.setHealth(1.0f);
    player.setInvulnerable(true);
    player.removeAllEffects();
    if (existing != null) {
      player.addEffect(new MobEffectInstance(ModEffects.SOUL_FRACTURE, -1, existing.getAmplifier(), false, true, true));
    }

    Vec3 bodyPos = player.position();
    Vec3 launchPos = findSafeSoulLaunchPos(level, bodyPos, player.getLookAngle());

    ActiveSoul soul = new ActiveSoul(bodyPos, launchPos, player.getYRot(), player.getXRot(), currentStacks + 1, SOUL_DURATION_TICKS);
    ACTIVE_SOULS.put(player.getUUID(), soul);

    level.playSound(null, BlockPos.containing(bodyPos), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.4f, 0.65f);
    level.sendParticles(ParticleTypes.SOUL, bodyPos.x, bodyPos.y + 1.0D, bodyPos.z, 35, 0.4D, 0.7D, 0.4D, 0.08D);
    syncSoul(player, soul, currentStacks);
  }

  public static void handleSoulAction(ServerPlayer player, String action) {
    ActiveSoul soul = ACTIVE_SOULS.get(player.getUUID());
    if (soul == null) return;
    ServerLevel level = player.serverLevel();

    switch (action) {
      case "RECLAIM_BODY" -> reviveAtBody(player, level, soul);
      case "WALL_HIT" -> {
        soul.ticksRemaining = Math.max(0, soul.ticksRemaining - WALL_PENALTY_TICKS);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.2f, 0.6f);
        if (soul.ticksRemaining <= 0) {
          killLostSoul(player, level);
        } else {
          syncSoul(player, soul, soul.nextStacks - 1);
        }
      }
      case "EXPIRED" -> killLostSoul(player, level);
    }
  }

  @SubscribeEvent
  public static void onPlayerTick(PlayerTickEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    if (player.level().isClientSide()) return;

    ActiveSoul soul = ACTIVE_SOULS.get(player.getUUID());
    if (soul == null) return;

    ServerLevel level = player.serverLevel();

    player.setInvulnerable(true);
    player.setPose(Pose.CROUCHING);
    player.setDeltaMovement(Vec3.ZERO);
    if (player.position().distanceToSqr(soul.bodyPos) > 0.05D) {
      player.teleportTo(level, soul.bodyPos.x, soul.bodyPos.y, soul.bodyPos.z, soul.bodyYaw, soul.bodyPitch);
    }

    if (soul.ticksRemaining % 4 == 0) {
      level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, soul.bodyPos.x, soul.bodyPos.y + 0.9D, soul.bodyPos.z, 4, 0.25D, 0.45D, 0.25D, 0.01D);
    }

    soul.ticksRemaining--;
    if (soul.ticksRemaining <= 0) {
      killLostSoul(player, level);
    }
  }

  @SubscribeEvent
  public static void onEffectRemove(MobEffectEvent.Remove event) {
    if (event.getEffect() == ModEffects.SOUL_FRACTURE) {
      event.setCanceled(true);
    }
  }

  @SubscribeEvent
  public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      ActiveSoul soul = ACTIVE_SOULS.remove(player.getUUID());
      if (soul != null) {
        player.setInvulnerable(false);
      }
    }
  }

  private static void reviveAtBody(ServerPlayer player, ServerLevel level, ActiveSoul soul) {
    ACTIVE_SOULS.remove(player.getUUID());
    player.setInvulnerable(false);
    player.setPose(Pose.STANDING);
    player.teleportTo(level, soul.bodyPos.x, soul.bodyPos.y, soul.bodyPos.z, soul.bodyYaw, soul.bodyPitch);
    player.setDeltaMovement(Vec3.ZERO);

    int amp = Math.min(2, soul.nextStacks - 1);
    player.addEffect(new MobEffectInstance(ModEffects.SOUL_FRACTURE, -1, amp, false, true, true));

    float targetHp = Math.max(1.0f, player.getMaxHealth() * 0.40f);
    player.setHealth(targetHp);
    player.invulnerableTime = 50;

    level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.9f, 1.35f);
    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, soul.bodyPos.x, soul.bodyPos.y + 1.0D, soul.bodyPos.z, 40, 0.5D, 0.8D, 0.5D, 0.1D);
    syncSoul(player, null, soul.nextStacks);
  }

  private static void killLostSoul(ServerPlayer player, ServerLevel level) {
    ACTIVE_SOULS.remove(player.getUUID());
    TRUE_DEATH_PENDING.add(player.getUUID());
    player.setInvulnerable(false);
    player.setPose(Pose.STANDING);
    syncSoul(player, null, 0);
    level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 0.6f);
    player.kill();
  }

  private static Vec3 findSafeSoulLaunchPos(ServerLevel level, Vec3 origin, Vec3 lookDir) {
    Vec3 backDir = new Vec3(-lookDir.x, 0.0D, -lookDir.z);
    if (backDir.lengthSqr() < 0.01D) backDir = new Vec3(0.0D, 0.0D, -1.0D);
    backDir = backDir.normalize();

    for (double dist = 12.0D; dist >= 3.0D; dist -= 1.0D) {
      Vec3 candidate = origin.add(backDir.x * dist, 2.8D, backDir.z * dist);
      BlockPos feet = BlockPos.containing(candidate);
      BlockPos eye = feet.above();
      if (!level.getBlockState(feet).isSuffocating(level, feet) && !level.getBlockState(eye).isSuffocating(level, eye)) {
        return candidate;
      }
    }
    return origin.add(0.0D, 3.5D, 0.0D);
  }

  private static void syncSoul(ServerPlayer player, ActiveSoul soul, int stacks) {
    if (soul == null) {
      PacketDistributor.sendToPlayer(player, new SoulSyncPayload(false, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0, SOUL_DURATION_TICKS, stacks));
    } else {
      PacketDistributor.sendToPlayer(player, new SoulSyncPayload(
        true, soul.bodyPos.x, soul.bodyPos.y, soul.bodyPos.z,
        soul.launchPos.x, soul.launchPos.y, soul.launchPos.z,
        soul.ticksRemaining, SOUL_DURATION_TICKS, stacks
      ));
    }
  }
}
