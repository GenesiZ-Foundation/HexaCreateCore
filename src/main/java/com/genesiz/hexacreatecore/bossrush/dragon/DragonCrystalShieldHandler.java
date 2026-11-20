package com.genesiz.hexacreatecore.bossrush.dragon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

@EventBusSubscriber(modid = "hexacreatecore")
public final class DragonCrystalShieldHandler {

  private DragonCrystalShieldHandler() {}

  @SubscribeEvent
  public static void onAttackCrystal(AttackEntityEvent event) {
    if (!(event.getTarget() instanceof EndCrystal crystal)) return;
    if (!(crystal.level() instanceof ServerLevel level)) return;

    boolean novaShieldActive = level.getEntitiesOfClass(
      EnderDragon.class,
      AABB.ofSize(crystal.position(), 140.0D, 110.0D, 140.0D)
    ).stream().anyMatch(d -> {
      if (!d.isAlive() || !(d instanceof IMhEnderDragon mh)) return false;
      DragonAnimState.DragonAction act = mh.hexacreatecore$getActiveAction();
      return act == DragonAnimState.DragonAction.NOVA_PRE_CHARGE
        || act == DragonAnimState.DragonAction.NOVA_CHARGE
        || (act == DragonAnimState.DragonAction.NOVA_END_CHARGE && 80 > mh.hexacreatecore$getActionTick());
    });

    if (novaShieldActive || crystal.isInvulnerable()) {
      event.setCanceled(true);
      level.playSound(null, crystal.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.2f, 1.35f);
      level.sendParticles(ParticleTypes.WITCH, crystal.getX(), crystal.getY() + 1.0D, crystal.getZ(), 10, 0.45D, 0.45D, 0.45D, 0.05D);
    }
  }
}
