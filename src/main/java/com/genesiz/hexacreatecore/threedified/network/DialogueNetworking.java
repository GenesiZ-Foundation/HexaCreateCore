package com.genesiz.hexacreatecore.threedified.network;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.decorators.annotations.DialogueAction;
import com.genesiz.hexacreatecore.threedified.hologram.MultiblockHologramRenderer;
import com.genesiz.hexacreatecore.hexcasting.HexagonalGridManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforgespi.language.ModFileScanData;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.objectweb.asm.Type;
import software.bernie.geckolib.animatable.GeoEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

@EventBusSubscriber(modid = HexaCreateCore.MODID)
public final class DialogueNetworking {
  private record ActionEntry(BiConsumer<ServerPlayer, Entity> handler, boolean selfTargeted) {}
  private record PendingProjection(String structureId, int durationTicks, long queuedAtTick) {}

  private static final Map<String, ActionEntry> SERVER_ACTIONS = new HashMap<>();
  private static final Map<UUID, PendingProjection> PENDING_PLACEMENTS = new HashMap<>();

  public static void registerAction(String actionId, BiConsumer<ServerPlayer, Entity> handler) {
    registerAction(actionId, false, handler);
  }

  public static void registerAction(String actionId, boolean selfTargeted, BiConsumer<ServerPlayer, Entity> handler) {
    SERVER_ACTIONS.put(actionId, new ActionEntry(handler, selfTargeted));
  }

  public static void scanAnnotatedActions() {
    Type targetType = Type.getType(DialogueAction.class);
    int discoveredCount = 0;

    for (ModFileScanData scanData : ModList.get().getAllScanData()) {
      for (ModFileScanData.AnnotationData annotation : scanData.getAnnotations()) {
        if (!targetType.equals(annotation.annotationType())) continue;

        try {
          Class<?> ownerClass = Class.forName(annotation.clazz().getClassName());
          String rawMember = annotation.memberName();
          String methodName = rawMember.contains("(") ? rawMember.substring(0, rawMember.indexOf('(')) : rawMember;

          Method method = ownerClass.getDeclaredMethod(methodName, ServerPlayer.class, Entity.class);
          if (!Modifier.isStatic(method.getModifiers())) {
            HexaCreateCore.LOGGER.error("@DialogueAction method {}.{} must be static!", ownerClass.getName(), methodName);
            continue;
          }

          method.setAccessible(true);
          DialogueAction meta = method.getAnnotation(DialogueAction.class);

          registerAction(meta.value(), meta.selfTargeted(), (player, entity) -> {
            try {
              method.invoke(null, player, entity);
            } catch (Exception e) {
              HexaCreateCore.LOGGER.error("Failed executing @DialogueAction '{}'", meta.value(), e);
            }
          });
          discoveredCount++;
        } catch (Exception e) {
          HexaCreateCore.LOGGER.error("Failed registering @DialogueAction on {}", annotation.clazz().getClassName(), e);
        }
      }
    }

    HexaCreateCore.LOGGER.info("Auto-registered {} @DialogueAction handlers.", discoveredCount);
  }

  /**
   * Universally commands any GeoEntity's specified animation controller to force-switch
   * its active data-driven JSON sequence over the network pipeline.
   */
  public static void syncAnimationOverride(Entity npc, String controllerName, String sequenceName) {
    if (npc instanceof GeoEntity geoEntity) {
      geoEntity.triggerAnim(controllerName, "override:" + sequenceName);
    }
  }

  private static void handleOnServer(DialogueActionPayload payload, IPayloadContext context) {
    context.enqueueWork(() -> {
      if (!(context.player() instanceof ServerPlayer serverPlayer)) return;

      String rawActionId = payload.actionId();
      String lookupKey = rawActionId;

      if (rawActionId.contains(";")) {
        lookupKey = rawActionId.split(";")[0];
      }

      ActionEntry entry = SERVER_ACTIONS.get(lookupKey);
      if (entry != null) {
        if (entry.selfTargeted()) {
          serverPlayer.getPersistentData().putString("hexacreatecore_raw_action", rawActionId);
          serverPlayer.getPersistentData().putInt("hexacreatecore_raw_entity_id", payload.entityId());

          entry.handler().accept(serverPlayer, serverPlayer);
          return;
        }

        Entity targetEntity = serverPlayer.level().getEntity(payload.entityId());
        if (targetEntity == null) return;

        // Security check boundary: ensure player is within interaction reach (6 blocks)
        if (serverPlayer.distanceToSqr(targetEntity) > 36.0D) return;

        entry.handler().accept(serverPlayer, targetEntity);
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

  @EventBusSubscriber(modid = HexaCreateCore.MODID)
  public static final class ModBusRegistry {
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
      event.enqueueWork(DialogueNetworking::scanAnnotatedActions);
    }

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
          HexagonalGridManager.updateLiveCastingStack(payload.stackDescs(), payload.ravensmind());
        })
      );

      registrar.playToClient(
        com.genesiz.hexacreatecore.bossrush.network.BossRushSyncPayload.TYPE,
        com.genesiz.hexacreatecore.bossrush.network.BossRushSyncPayload.STREAM_CODEC.cast(),
      (payload, context) -> context.enqueueWork(() ->
          com.genesiz.hexacreatecore.bossrush.client.BossRushClientHud.handleSyncPacket(payload)
        )
      );

    }
  }
}
