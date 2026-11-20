package com.genesiz.hexacreatecore.create.wonderum;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.item.ModItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record WonderumAnchorPayload(UUID subLevelId) implements CustomPacketPayload {

  public static final Type<WonderumAnchorPayload> TYPE = new Type<>(
    ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "wonderum_anchor")
  );

  public static final StreamCodec<ByteBuf, WonderumAnchorPayload> STREAM_CODEC = StreamCodec.composite(
    UUIDUtil.STREAM_CODEC, WonderumAnchorPayload::subLevelId,
    WonderumAnchorPayload::new
  );

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  public static void handleServer(WonderumAnchorPayload payload, IPayloadContext ctx) {
    ctx.enqueueWork(() -> {
      if (!(ctx.player() instanceof ServerPlayer sp)) return;
      if (!sp.getMainHandItem().is(ModItems.WONDERUM.get()) && !sp.getOffhandItem().is(ModItems.WONDERUM.get())) return;

      ServerLevel level = sp.serverLevel();
      try {
        Class<?> handlerClass = Class.forName("dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler");
        Object serverHandler = handlerClass.getMethod("get", ServerLevel.class).invoke(null, level);
        serverHandler.getClass().getMethod("toggleLock", UUID.class).invoke(serverHandler, payload.subLevelId());

        level.playSound(null, sp.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.75f, 1.35f);
        level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getEyeY(), sp.getZ(), 12, 0.25D, 0.25D, 0.25D, 0.04D);
      } catch (Throwable ignored) {}
    });
  }
}
