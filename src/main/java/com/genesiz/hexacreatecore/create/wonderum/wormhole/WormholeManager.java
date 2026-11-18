package com.genesiz.hexacreatecore.create.wonderum.wormhole;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@EventBusSubscriber(modid = HexaCreateCore.MODID)
public final class WormholeManager {

  public static final double MIN_SAFE_DISTANCE = 10.0D;

  private WormholeManager() {}

  @SubscribeEvent(priority = EventPriority.HIGH)
  public static void onSneakRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
    if (!event.getItemStack().is(ModItems.WONDERUM.get())) return;
    if (!event.getEntity().isShiftKeyDown()) return;

    Level level = event.getLevel();
    BlockPos pos = event.getPos();
    Direction face = event.getFace() != null ? event.getFace() : Direction.UP;

    boolean isKinetic = isCreateKineticBlockEntity(level.getBlockEntity(pos));
    if (level.isClientSide) {
      if (isKinetic || WormholeClientRenderer.hasPendingAnchor()) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
      }
      return;
    }

    if (!(event.getEntity() instanceof ServerPlayer sp)) return;
    ServerLevel serverLevel = sp.serverLevel();
    WormholeSavedData data = WormholeSavedData.get(serverLevel.getServer());
    WormholeSavedData.Endpoint pending = data.getPending(sp.getUUID());

    if (!isKinetic && pending == null) return;

    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.CONSUME);

    ResourceKey<Level> dim = serverLevel.dimension();
    WormholeSavedData.Endpoint clicked = new WormholeSavedData.Endpoint(dim, pos.immutable(), face);

    if (pending == null) {
      data.setPending(sp.getUUID(), clicked);
      serverLevel.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 0.85f, 1.35f);
      serverLevel.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 16, 0.25D, 0.25D, 0.25D, 0.04D);
      com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud.sendTo(sp, "§b[Wonderum] §fWormhole Anchor §dA §fbound on §7" + face.getName().toUpperCase() + " §8— Sneak+Right-Click target node (>10 blocks away)");
      syncAllPlayers(serverLevel.getServer());
      return;
    }

    if (pending.dimension().equals(dim) && pending.pos().equals(pos)) {
      data.clearPending(sp.getUUID());
      serverLevel.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.7f, 0.9f);
      com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud.sendTo(sp, "§b[Wonderum] §fPending Wormhole Anchor §eCLEARED");
      syncAllPlayers(serverLevel.getServer());
      return;
    }

    if (pending.dimension().equals(dim)) {
      double distSq = pending.pos().distSqr(pos);
      if (MIN_SAFE_DISTANCE * MIN_SAFE_DISTANCE >= distSq) {
        data.clearPending(sp.getUUID());
        triggerSpatialImplosion(serverLevel, pending.pos(), pos);
        com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud.sendTo(sp, "§c[Wonderum] §4SPATIAL IMPLOSION! §fWormhole anchors cannot coexist within §e10 blocks§f!");
        syncAllPlayers(serverLevel.getServer());
        return;
      }
    }

    ServerLevel pendingLevel = serverLevel.getServer().getLevel(pending.dimension());
    if (pendingLevel != null && WormholeKineticBridge.getDriven(pendingLevel, pending.pos()) != null) {
      releaseDrivenNode(pendingLevel, pending.pos());
    }
    if (WormholeKineticBridge.getDriven(serverLevel, pos) != null) {
      releaseDrivenNode(serverLevel, pos);
    }

    data.clearPending(sp.getUUID());
    data.addPair(pending, clicked);
    serverLevel.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.7f, 1.45f);
    serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 24, 0.3D, 0.3D, 0.3D, 0.08D);
    com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud.sendTo(sp, "§b[Wonderum] §fGravitational Wormhole §aLINKED §8(" + pending.dimension().location().getPath() + " ↔ " + dim.location().getPath() + ")");
    syncAllPlayers(serverLevel.getServer());

  }

  @SubscribeEvent(priority = EventPriority.HIGH)
  public static void onSneakLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
    if (!event.getItemStack().is(ModItems.WONDERUM.get())) return;
    if (!event.getEntity().isShiftKeyDown()) return;
    if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer sp)) return;

    ServerLevel serverLevel = sp.serverLevel();
    MinecraftServer server = serverLevel.getServer();
    WormholeSavedData data = WormholeSavedData.get(server);
    ResourceKey<Level> dim = serverLevel.dimension();
    BlockPos pos = event.getPos();

    WormholeSavedData.WormholePair targetPair = null;
    for (WormholeSavedData.WormholePair pair : data.getPairs()) {
      if (pair.matches(dim, pos)) {
        targetPair = pair;
        break;
      }
    }

    if (targetPair != null && data.removeAt(dim, pos)) {
      event.setCanceled(true);
      ServerLevel levelA = server.getLevel(targetPair.a().dimension());
      ServerLevel levelB = server.getLevel(targetPair.b().dimension());
      if (levelA != null) releaseDrivenNode(levelA, targetPair.a().pos());
      if (levelB != null) releaseDrivenNode(levelB, targetPair.b().pos());

      serverLevel.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8f, 0.8f);
      com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud.sendTo(sp, "§b[Wonderum] §fGravitational Wormhole §eCOLLAPSED");
      syncAllPlayers(server);
    }
  }

  @SubscribeEvent
  public static void onServerTick(ServerTickEvent.Post event) {
    MinecraftServer server = event.getServer();
    WormholeSavedData data = WormholeSavedData.get(server);
    if (data.getPairs().isEmpty()) return;

    boolean changed = false;
    Iterator<WormholeSavedData.WormholePair> it = data.getPairs().iterator();
    while (it.hasNext()) {
      WormholeSavedData.WormholePair pair = it.next();
      ServerLevel levelA = server.getLevel(pair.a().dimension());
      ServerLevel levelB = server.getLevel(pair.b().dimension());
      if (levelA == null || levelB == null) continue;

      if (pair.a().dimension().equals(pair.b().dimension()) && MIN_SAFE_DISTANCE * MIN_SAFE_DISTANCE >= pair.a().pos().distSqr(pair.b().pos())) {
        it.remove();
        data.setDirty();
        changed = true;
        releaseDrivenNode(levelA, pair.a().pos());
        releaseDrivenNode(levelB, pair.b().pos());
        triggerSpatialImplosion(levelA, pair.a().pos(), pair.b().pos());
        continue;
      }

      if (!levelA.isLoaded(pair.a().pos()) || !levelB.isLoaded(pair.b().pos())) continue;

      BlockEntity beA = levelA.getBlockEntity(pair.a().pos());
      BlockEntity beB = levelB.getBlockEntity(pair.b().pos());
      if (beA == null || beB == null) {
        it.remove();
        data.setDirty();
        changed = true;
        releaseDrivenNode(levelA, pair.a().pos());
        releaseDrivenNode(levelB, pair.b().pos());
        continue;
      }

      bridgeKineticRotation(beA, beB);
    }

    if (changed) syncAllPlayers(server);
  }

  @SubscribeEvent
  public static void onPlayerLoginOrChangeDim(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer sp) syncPlayer(sp);
  }

  @SubscribeEvent
  public static void onPlayerChangedDim(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer sp) syncPlayer(sp);
  }

  public static void triggerSpatialImplosion(ServerLevel level, BlockPos posA, BlockPos posB) {
    Vec3 center = Vec3.atCenterOf(posA);
    level.playSound(null, posA, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.4f, 0.55f);
    level.playSound(null, posA, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.2f, 0.5f);
    level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 2, 0.4D, 0.4D, 0.4D, 0.0D);
    level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 60, 1.5D, 1.5D, 1.5D, 0.15D);

    AABB pullBox = new AABB(posA).inflate(7.0D);
    for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, pullBox)) {
      Vec3 pullVec = center.subtract(entity.position()).normalize().scale(1.15D);
      entity.setDeltaMovement(entity.getDeltaMovement().add(pullVec.x, 0.45D, pullVec.z));
      entity.hurtMarked = true;
      entity.hurt(level.damageSources().magic(), 8.0f);
    }
  }

  private static boolean isCreateKineticBlockEntity(BlockEntity be) {
    if (be == null) return false;
    for (Class<?> c = be.getClass(); c != null; c = c.getSuperclass()) {
      if (c.getName().equals("com.simibubi.create.content.kinetics.base.KineticBlockEntity")) return true;
    }
    return false;
  }

  private static void releaseDrivenNode(ServerLevel level, BlockPos pos) {
    WormholeKineticBridge.clearDriven(level.dimension(), pos);
    BlockEntity be = level.getBlockEntity(pos);
    if (be == null) return;
    try {
      Class<?> kbeClass = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
      if (!kbeClass.isInstance(be)) return;
      kbeClass.getMethod("detachKinetics").invoke(be);
      kbeClass.getMethod("setSpeed", float.class).invoke(be, 0.0f);
      kbeClass.getMethod("setNetwork", Long.class).invoke(be, (Long) null);
      kbeClass.getMethod("sendData").invoke(be);
    } catch (Throwable ignored) {}
  }

  private static void bridgeKineticRotation(BlockEntity beA, BlockEntity beB) {
    try {
      Class<?> kbeClass = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
      Class<?> netClass = Class.forName("com.simibubi.create.content.kinetics.KineticNetwork");
      if (!kbeClass.isInstance(beA) || !kbeClass.isInstance(beB)) return;

      Level levelA = beA.getLevel();
      Level levelB = beB.getLevel();
      if (levelA == null || levelB == null) return;

      boolean aDriven = WormholeKineticBridge.getDriven(levelA, beA.getBlockPos()) != null
      && !com.genesiz.hexacreatecore.create.wonderum.WonderumKineticImpulsePayload.isImpulseNode(levelA, beA.getBlockPos());
      boolean bDriven = WormholeKineticBridge.getDriven(levelB, beB.getBlockPos()) != null
      && !com.genesiz.hexacreatecore.create.wonderum.WonderumKineticImpulsePayload.isImpulseNode(levelB, beB.getBlockPos());

      Method getSpeed = kbeClass.getMethod("getSpeed");
      float speedA = (float) getSpeed.invoke(beA);
      float speedB = (float) getSpeed.invoke(beB);

      BlockEntity driver = beA;
      BlockEntity receiver = beB;
      float driverSpeed = speedA;

      if (aDriven && !bDriven) {
        driver = beB;
        receiver = beA;
        driverSpeed = speedB;
      } else if (!aDriven && !bDriven && Math.abs(speedB) > Math.abs(speedA)) {
        driver = beB;
        receiver = beA;
        driverSpeed = speedB;
      }

      Level recvLevel = receiver.getLevel();
      BlockPos recvPos = receiver.getBlockPos();
      if (recvLevel == null) return;

      Object driverNet = kbeClass.getMethod("getOrCreateNetwork").invoke(driver);
      float driverCapacity = driverNet != null ? (float) netClass.getMethod("calculateCapacity").invoke(driverNet) : 0.0f;
      float perRpmCap = Math.abs(driverSpeed) > 0.01f ? (driverCapacity / Math.abs(driverSpeed)) : 0.0f;

      WormholeKineticBridge.DrivenState prev = WormholeKineticBridge.getDriven(recvLevel, recvPos);
      boolean changed = prev == null
      || Math.abs(prev.speed() - driverSpeed) > 0.01f
      || Math.abs(prev.perRpmCapacity() - perRpmCap) > 0.05f;

      if (Math.abs(driverSpeed) > 0.01f) {
        WormholeKineticBridge.setDriven(recvLevel.dimension(), recvPos, driverSpeed, perRpmCap);
      } else {
        WormholeKineticBridge.clearDriven(recvLevel.dimension(), recvPos);
      }

      if (changed) {
        Method detachKinetics = kbeClass.getMethod("detachKinetics");
        Method attachKinetics = kbeClass.getMethod("attachKinetics");
        Method setSpeed = kbeClass.getMethod("setSpeed", float.class);
        Method setNetwork = kbeClass.getMethod("setNetwork", Long.class);
        Method getOrCreateNetwork = kbeClass.getMethod("getOrCreateNetwork");
        Method sendData = kbeClass.getMethod("sendData");

        detachKinetics.invoke(receiver);
        setSpeed.invoke(receiver, driverSpeed);

        if (Math.abs(driverSpeed) > 0.01f) {
          setNetwork.invoke(receiver, recvPos.asLong());
          attachKinetics.invoke(receiver);

          Class<?> propClass = Class.forName("com.simibubi.create.content.kinetics.RotationPropagator");
          propClass.getMethod("handleAdded", Level.class, BlockPos.class, kbeClass)
            .invoke(null, recvLevel, recvPos, receiver);

          Object recvNet = getOrCreateNetwork.invoke(receiver);
          if (recvNet != null) {
            netClass.getMethod("updateCapacityFor", kbeClass, float.class)
              .invoke(recvNet, receiver, perRpmCap);
            netClass.getMethod("updateStress").invoke(recvNet);
          }
        } else {
          setNetwork.invoke(receiver, (Long) null);
        }

        sendData.invoke(receiver);
        var bs = recvLevel.getBlockState(recvPos);
        recvLevel.sendBlockUpdated(recvPos, bs, bs, 3);
        recvLevel.updateNeighborsAt(recvPos, bs.getBlock());
      }
    } catch (Throwable ignored) {}
  }

  public static void syncAllPlayers(MinecraftServer server) {
    for (ServerPlayer sp : server.getPlayerList().getPlayers()) syncPlayer(sp);
  }

  public static void syncPlayer(ServerPlayer sp) {
    WormholeSavedData data = WormholeSavedData.get(sp.serverLevel().getServer());
    ResourceKey<Level> dim = sp.serverLevel().dimension();
    List<WormholeSyncPayload.SyncNode> nodes = new ArrayList<>();
    for (WormholeSavedData.WormholePair pair : data.getPairs()) {
      if (pair.a().dimension().equals(dim)) nodes.add(new WormholeSyncPayload.SyncNode(pair.a().pos(), pair.a().face()));
      if (pair.b().dimension().equals(dim)) nodes.add(new WormholeSyncPayload.SyncNode(pair.b().pos(), pair.b().face()));
    }
    WormholeSavedData.Endpoint pending = data.getPending(sp.getUUID());
    boolean hasPendingInDim = pending != null && pending.dimension().equals(dim);
    PacketDistributor.sendToPlayer(sp, new WormholeSyncPayload(
      nodes,
      hasPendingInDim,
      hasPendingInDim ? new WormholeSyncPayload.SyncNode(pending.pos(), pending.face()) : new WormholeSyncPayload.SyncNode(BlockPos.ZERO, Direction.UP)
    ));
  }
}
