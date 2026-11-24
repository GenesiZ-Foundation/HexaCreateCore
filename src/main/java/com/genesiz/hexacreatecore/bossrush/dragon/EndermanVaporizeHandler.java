package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.HexaCreateCore;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.EnderMan;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = HexaCreateCore.MODID)
public final class EndermanVaporizeHandler {

  private static final Set<UUID> VAPORIZED_IDS = ConcurrentHashMap.newKeySet();

  private EndermanVaporizeHandler() {}

  public static void vaporizeEnderman(ServerLevel level, EnderMan enderman) {
    if (enderman == null || enderman.isRemoved()) return;
    if (!VAPORIZED_IDS.add(enderman.getUUID())) return;

    double x = enderman.getX();
    double y = enderman.getY();
    double z = enderman.getZ();
    double centerY = y + (enderman.getBbHeight() * 0.5D);

    // Send 1 static purple after-image snapshot to nearby players
    float bodyYaw = enderman.yBodyRot;
    float headYawRel = Mth.wrapDegrees(enderman.yHeadRot - bodyYaw);
    EndermanAfterImagePayload payload = new EndermanAfterImagePayload(
      x, y, z,
      bodyYaw,
      headYawRel,
      enderman.getXRot(),
      enderman.walkAnimation.position(),
      enderman.walkAnimation.speed(),
      enderman.isCreepy()
    );
    for (ServerPlayer sp : level.players()) {
      if (128.0D * 128.0D >= sp.distanceToSqr(x, y, z)) {
        PacketDistributor.sendToPlayer(sp, payload);
      }
    }

    // particle minecraft:soul ~ ~ ~ 0.5 0.5 0.5 0.1 30 normal
    // particle minecraft:dragon_breath ~ ~ ~ 0.5 0.5 0.5 0.1 30 normal
    level.sendParticles(ParticleTypes.SOUL, x, centerY, z, 30, 0.5D, 0.5D, 0.5D, 0.1D);
    level.sendParticles(ParticleTypes.DRAGON_BREATH, x, centerY, z, 30, 0.5D, 0.5D, 0.5D, 0.1D);

    level.playSound(null, x, y, z, SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 1.3f, 0.65f);
    level.playSound(null, x, y, z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.5f, 0.85f);

    // Discard directly so zero items or XP ever drop
    enderman.discard();
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void onEndermanHitByDragon(LivingIncomingDamageEvent event) {
    if (!(event.getEntity() instanceof EnderMan enderman)) return;
    if (!(enderman.level() instanceof ServerLevel level)) return;

    boolean fromDragon = (event.getSource().getEntity() instanceof EnderDragon)
      || (event.getSource().getDirectEntity() instanceof EnderDragon);

    if (fromDragon) {
      event.setCanceled(true);
      vaporizeEnderman(level, enderman);
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void onVaporizedDrops(LivingDropsEvent event) {
    if (VAPORIZED_IDS.contains(event.getEntity().getUUID())) {
      event.setCanceled(true);
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void onVaporizedXp(LivingExperienceDropEvent event) {
    if (VAPORIZED_IDS.remove(event.getEntity().getUUID())) {
      event.setCanceled(true);
    }
  }
}

