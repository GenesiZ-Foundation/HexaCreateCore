package com.genesiz.hexacreatecore.difficulty.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class EnderDecayEffect extends MobEffect {

  public EnderDecayEffect() {
    super(MobEffectCategory.HARMFUL, 0x9333EA);
  }

  @Override
  public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    // 1 HP every 0.2s (4 ticks)
    return duration % 4 == 0;
  }

  @Override
  public boolean applyEffectTick(LivingEntity entity, int amplifier) {
    if (entity.level().isClientSide() || !entity.isAlive()) return true;
    if (entity instanceof Player p && (p.isCreative() || p.isSpectator())) return true;

    float drain = 1.0f + amplifier;
    float nextHp = entity.getHealth() - drain;
    if (nextHp > 0.0f) {
      entity.setHealth(nextHp);
      entity.hurtTime = 0;
      entity.hurtDuration = 0;
      entity.hurtMarked = false;
    } else {
      entity.setHealth(0.0f);
      entity.die(entity.level().damageSources().magic());
    }
    return true;
  }
}

