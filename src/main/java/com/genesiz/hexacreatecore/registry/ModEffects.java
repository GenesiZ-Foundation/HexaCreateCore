package com.genesiz.hexacreatecore.registry;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.difficulty.effect.SoulFractureEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
  public static final DeferredRegister<MobEffect> MOB_EFFECTS =
    DeferredRegister.create(Registries.MOB_EFFECT, HexaCreateCore.MODID);

  public static final Holder<MobEffect> SOUL_FRACTURE =
    MOB_EFFECTS.register("soul_fracture", SoulFractureEffect::new);

  private ModEffects() {}

  public static void register(IEventBus bus) {
    MOB_EFFECTS.register(bus);
  }
}
