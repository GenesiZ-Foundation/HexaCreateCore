package com.genesiz.hexacreatecore.hexcasting.everbook;

import at.petrak.hexcasting.api.HexAPI;
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

public final class EverbookDryRunEnv extends PlayerBasedCastEnv {

  public EverbookDryRunEnv(ServerPlayer caster) {
    super(caster, InteractionHand.MAIN_HAND);
  }

  @Override
  protected long extractMediaEnvironment(long cost, boolean simulate) {
    return 0L;
  }

  @Override
  public InteractionHand getCastingHand() {
    return InteractionHand.MAIN_HAND;
  }

  @Override
  public FrozenPigment getPigment() {
    return HexAPI.instance().getColorizer(this.caster);
  }

  @Override
  public void printMessage(Component message) {
    // Suppress live chat mishap spam during dry-run Rift simulation
  }
}
