package com.genesiz.hexacreatecore.dialogue.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.dialogue.hologram.MultiblockHologramRenderer;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class DialogueNetworking {
  private static final Map<String, BiConsumer<ServerPlayer, Entity>> SERVER_ACTIONS = new HashMap<>();
  private static final Map<UUID, PendingProjection> PENDING_PLACEMENTS = new HashMap<>();

  private record PendingProjection(String structureId, int durationTicks, long queuedAtTick) {}

  public static void registerAction(String actionId, BiConsumer<ServerPlayer, Entity> handler) {
    SERVER_ACTIONS.put(actionId, handler);
  }

  @SubscribeEvent
  public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
    final PayloadRegistrar registrar = event.registrar("1.0");
    registrar.playToServer(
      DialogueActionPayload.TYPE,
      DialogueActionPayload.STREAM_CODEC,
      DialogueNetworking::handleOnServer
    );
    registrar.playToClient(
      HologramProjectionPayload.TYPE,
      HologramProjectionPayload.STREAM_CODEC,
    (payload, context) -> context.enqueueWork(() -> MultiblockHologramRenderer.setProjection(payload))
    );
  }

  private static void handleOnServer(DialogueActionPayload payload, IPayloadContext context) {
    context.enqueueWork(() -> {
      if (!(context.player() instanceof ServerPlayer serverPlayer)) return;

      Entity targetEntity = serverPlayer.level().getEntity(payload.entityId());
      if (targetEntity == null) return;

      // Security check: ensure player is within interaction reach (6 blocks)
      if (serverPlayer.distanceToSqr(targetEntity) > 36.0D) return;

      BiConsumer<ServerPlayer, Entity> handler = SERVER_ACTIONS.get(payload.actionId());
      if (handler != null) {
        handler.accept(serverPlayer, targetEntity);
      }
    });
  }

  public static void queueHologramPlacement(ServerPlayer player, String structureId, int durationTicks) {
    PENDING_PLACEMENTS.put(
        player.getUUID(),
        new PendingProjection(structureId, durationTicks, player.level().getGameTime())
    );
    PacketDistributor.sendToPlayer(
        player,
        new HologramProjectionPayload(structureId, BlockPos.ZERO, -1)
    );
  }


  @SubscribeEvent
  public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
    if (event.getLevel().isClientSide) return;
    if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

    PendingProjection pending = PENDING_PLACEMENTS.get(serverPlayer.getUUID());
    if (pending == null) return;

    // Ignore the residual right-click from the dialogue button itself (wait at least 3 ticks)
    if (3L > serverPlayer.level().getGameTime() - pending.queuedAtTick()) {
      event.setCanceled(true);
      return;
    }

    PENDING_PLACEMENTS.remove(serverPlayer.getUUID());
    BlockPos anchor = event.getPos().above();

    PacketDistributor.sendToPlayer(
        serverPlayer,
        new HologramProjectionPayload(pending.structureId(), anchor, pending.durationTicks())
    );

    event.setCanceled(true);
  }
}
