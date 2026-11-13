package com.genesiz.hexacreatecore.difficulty.network;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.difficulty.DifficultyManager;
import com.genesiz.hexacreatecore.difficulty.client.DifficultyClientState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = HexaCreateCore.MODID)
public final class DifficultyNetworking {

  @SubscribeEvent
  public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
    PayloadRegistrar registrar = event.registrar("1");

    registrar.playToClient(
      DifficultyPackets.SyncPayload.TYPE,
      DifficultyPackets.SyncPayload.STREAM_CODEC,
    (payload, ctx) -> ctx.enqueueWork(() -> DifficultyClientState.handleSync(payload))
    );

    registrar.playToClient(
      DifficultyPackets.SoulSyncPayload.TYPE,
      DifficultyPackets.SoulSyncPayload.STREAM_CODEC,
    (payload, context) -> context.enqueueWork(() ->
        com.genesiz.hexacreatecore.difficulty.client.SoulRecoveryClientRenderer.handleSoulSync(payload)
      )
    );

    registrar.playToServer(
      DifficultyPackets.SoulActionPayload.TYPE,
      DifficultyPackets.SoulActionPayload.STREAM_CODEC,
    (payload, context) -> context.enqueueWork(() -> {
        if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
          com.genesiz.hexacreatecore.difficulty.SoulRecoveryManager.handleSoulAction(sp, payload.action());
        }
      })
    );

    registrar.playToServer(
      DifficultyPackets.ActionPayload.TYPE,
      DifficultyPackets.ActionPayload.STREAM_CODEC,
    (payload, ctx) -> ctx.enqueueWork(() -> {
        if (ctx.player() instanceof ServerPlayer sp) {
          DifficultyManager.handleClientAction(sp, payload);
        }
      })
    );
  }
}
