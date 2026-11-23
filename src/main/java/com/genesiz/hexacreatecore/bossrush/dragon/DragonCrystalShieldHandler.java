package com.genesiz.hexacreatecore.bossrush.dragon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

@EventBusSubscriber(modid = "hexacreatecore")
public final class DragonCrystalShieldHandler {

  private DragonCrystalShieldHandler() {}

  public static boolean isCrystalShielded(ServerLevel level, EndCrystal crystal) {
    if (crystal.isInvulnerable()) return true;
    return level.getEntitiesOfClass(
      EnderDragon.class,
      AABB.ofSize(crystal.position(), 140.0D, 110.0D, 140.0D)
    ).stream().anyMatch(d -> {
      if (!d.isAlive() || !(d instanceof IMhEnderDragon mh)) return false;
      DragonAnimState.DragonAction act = mh.hexacreatecore$getActiveAction();
      return act == DragonAnimState.DragonAction.NOVA_PRE_CHARGE
        || act == DragonAnimState.DragonAction.NOVA_CHARGE
        || (act == DragonAnimState.DragonAction.NOVA_END_CHARGE && 80 > mh.hexacreatecore$getActionTick());
    });
  }

  private static void playShieldFeedback(ServerLevel level, EndCrystal crystal) {
    level.playSound(null, crystal.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.2f, 1.35f);
    level.sendParticles(ParticleTypes.WITCH, crystal.getX(), crystal.getY() + 1.0D, crystal.getZ(), 10, 0.45D, 0.45D, 0.45D, 0.05D);
  }

  @SubscribeEvent
  public static void onAttackCrystal(AttackEntityEvent event) {
    if (!(event.getTarget() instanceof EndCrystal crystal)) return;
    if (!(crystal.level() instanceof ServerLevel level)) return;

    if (isCrystalShielded(level, crystal)) {
      event.setCanceled(true);
      playShieldFeedback(level, crystal);
    }
  }

  @SubscribeEvent
  public static void onProjectileImpactCrystal(ProjectileImpactEvent event) {
    if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit)) return;
    if (!(entityHit.getEntity() instanceof EndCrystal crystal)) return;
    if (!(crystal.level() instanceof ServerLevel level)) return;

    if (isCrystalShielded(level, crystal)) {
      event.setCanceled(true);
      playShieldFeedback(level, crystal);
    }
  }

  @SubscribeEvent
  public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
    if (!(event.getLevel() instanceof ServerLevel level)) return;
    event.getAffectedEntities().removeIf(entity ->
      entity instanceof EndCrystal crystal && isCrystalShielded(level, crystal)
    );
  }

  @SubscribeEvent
  public static void onAttackDragonPart(AttackEntityEvent event) {
    if (event.getTarget() instanceof DragonMultipartCollider.BonePart part) {
      if (part.getParent() instanceof IMhEnderDragon mh && mh.hexacreatecore$hasBarrierShield()) {
        event.setCanceled(true);
      }
    }
  }

}
