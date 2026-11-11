package com.genesiz.hexacreatecore.dialogue.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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

@EventBusSubscriber(modid = HexaCreateCore.MODID)
public final class DialogueNetworking {
  private static final Map<String, BiConsumer<ServerPlayer, Entity>> SERVER_ACTIONS = new HashMap<>();
  private static final Map<UUID, PendingProjection> PENDING_PLACEMENTS = new HashMap<>();

  private record PendingProjection(String structureId, int durationTicks, long queuedAtTick) {}

  public static void registerAction(String actionId, BiConsumer<ServerPlayer, Entity> handler) {
    SERVER_ACTIONS.put(actionId, handler);
  }

  /**
   * Universally commands any GeoEntity's specified animation controller to force-switch
   * its active data-driven JSON sequence over the network pipeline.
   */
  public static void syncAnimationOverride(Entity npc, String controllerName, String sequenceName) {
    if (npc instanceof software.bernie.geckolib.animatable.GeoEntity geoEntity) {
      geoEntity.triggerAnim(controllerName, "override:" + sequenceName);
    }
  }

  private static void handleOnServer(DialogueActionPayload payload, IPayloadContext context) {
    context.enqueueWork(() -> {
      if (!(context.player() instanceof ServerPlayer serverPlayer)) return;

      String rawActionId = payload.actionId();
      String lookupKey = rawActionId;
      
      // Separate the packet tracking ID from metadata parameters using a split token delimiter
      if (rawActionId.contains(";")) {
        lookupKey = rawActionId.split(";")[0];
      }

      BiConsumer<ServerPlayer, Entity> handler = SERVER_ACTIONS.get(lookupKey);
      if (handler != null) {
        // Special Case: Spatial spell actions do not require validation of world entity instances
        if ("hexacreatecore:cast_3d_spell".equals(lookupKey)
            || "hexacreatecore:clear_3d_stack".equals(lookupKey)
            || "hexacreatecore:overreach_penalty".equals(lookupKey)) {
          serverPlayer.getPersistentData().putString("hexacreatecore_raw_action", rawActionId);
          serverPlayer.getPersistentData().putInt("hexacreatecore_raw_entity_id", payload.entityId());

          handler.accept(serverPlayer, serverPlayer);
          return;
        }

        Entity targetEntity = serverPlayer.level().getEntity(payload.entityId());
        if (targetEntity == null) return;

        // Security check boundary: ensure player is within interaction reach (6 blocks)
        if (serverPlayer.distanceToSqr(targetEntity) > 36.0D) return;

        handler.accept(serverPlayer, targetEntity);
      }
    });
  }

  public static void queueHologramPlacement(ServerPlayer player, String structureId, int durationTicks) {
    PENDING_PLACEMENTS.put(
      player.getUUID(),
      new PendingProjection(structureId, durationTicks, player.level().getGameTime())
    );
    // Sends a dummy confirmation down to client to visually indicate ghost placement mode is active
    PacketDistributor.sendToPlayer(
      player,
      new HologramProjectionPayload(structureId, BlockPos.ZERO, -1)
    );
  }

  public static void clearPendingPlayerPlacement(ServerPlayer player) {
    PENDING_PLACEMENTS.remove(player.getUUID());
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

  /**
   * Internal specialized bus handler dedicated purely to the Mod Event Bus lifecycle events.
   */
  @EventBusSubscriber(modid = HexaCreateCore.MODID)
  public static final class ModBusRegistry {
    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
      final PayloadRegistrar registrar = event.registrar("1.0");

      registrar.playToServer(
        DialogueActionPayload.TYPE,
        DialogueActionPayload.STREAM_CODEC.cast(),
        DialogueNetworking::handleOnServer
      );

      registrar.playToClient(
        HologramProjectionPayload.TYPE,
        HologramProjectionPayload.STREAM_CODEC.cast(),
        (payload, context) -> context.enqueueWork(() -> MultiblockHologramRenderer.setProjection(payload))
      );

      registrar.playToClient(
        LiveStackUpdatePayload.TYPE,
        LiveStackUpdatePayload.STREAM_CODEC.cast(),
        (payload, context) -> context.enqueueWork(() -> {
          com.genesiz.hexacreatecore.hexcasting.HexagonalGridManager.updateLiveCastingStack(payload.stackDescs(), payload.ravensmind());
        })
      );
    }
  }
}
