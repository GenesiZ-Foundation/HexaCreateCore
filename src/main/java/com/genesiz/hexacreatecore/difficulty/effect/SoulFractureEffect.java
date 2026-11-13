package com.genesiz.hexacreatecore.difficulty.effect;

import com.genesiz.hexacreatecore.HexaCreateCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.EffectCure;

import java.util.Set;

public class SoulFractureEffect extends MobEffect {

  public SoulFractureEffect() {
    super(MobEffectCategory.HARMFUL, 0xFF8B0000);
    // Amplifier 0 -> -33%, Amplifier 1 -> -66%, Amplifier 2 -> -99% Max HP
    this.addAttributeModifier(
      Attributes.MAX_HEALTH,
      ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "soul_fracture_hp_penalty"),
      -0.33D,
      AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    );
  }

  @Override
  public void fillEffectCures(Set<EffectCure> cures, net.minecraft.world.effect.MobEffectInstance effectInstance) {
    // Empty set = completely uncurable by Milk Buckets, Honey, or standard cures
    cures.clear();
  }
}
