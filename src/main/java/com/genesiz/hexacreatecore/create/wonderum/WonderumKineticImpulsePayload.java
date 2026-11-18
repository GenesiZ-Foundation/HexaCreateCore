package com.genesiz.hexacreatecore.create.wonderum;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.create.wonderum.wormhole.WormholeKineticBridge;
import com.genesiz.hexacreatecore.item.ModItems;
import com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = HexaCreateCore.MODID)
public record WonderumKineticImpulsePayload(BlockPos targetPos) implements CustomPacketPayload {

  public static final Type<WonderumKineticImpulsePayload> TYPE = new Type<>(
    ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "wonderum_kinetic_impulse")
  );

  public static final StreamCodec<ByteBuf, WonderumKineticImpulsePayload> STREAM_CODEC = StreamCodec.composite(
    BlockPos.STREAM_CODEC, WonderumKineticImpulsePayload::targetPos,
    WonderumKineticImpulsePayload::new
  );

  private record ImpulseKey(ResourceKey<Level> dimension, BlockPos pos) {}
  private static final Map<ImpulseKey, Integer> ACTIVE_IMPULSES = new ConcurrentHashMap<>();

  private static final float IMPULSE_RPM = 64.0f;
  private static final float PER_RPM_SU = 16.0f; // 64 RPM * 16 = 1024 SU
  private static final int DURATION_TICKS = 400; // 20 seconds (20 TPS * 20s)

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  public static void handleServer(WonderumKineticImpulsePayload payload, IPayloadContext ctx) {
    ctx.enqueueWork(() -> {
      if (!(ctx.player() instanceof ServerPlayer sp)) return;
      if (!sp.getMainHandItem().is(ModItems.WONDERUM.get()) && !sp.getOffhandItem().is(ModItems.WONDERUM.get())) return;

      ServerLevel level = sp.serverLevel();
      BlockPos pos = payload.targetPos().immutable();
      if (!level.isLoaded(pos) || sp.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 144.0D) return;

      BlockEntity be = level.getBlockEntity(pos);
      if (be == null) return;

      ImpulseKey key = new ImpulseKey(level.dimension(), pos);

      // If this exact block already has an active impulse, stop its SU generation & let neighboring sources take over
      if (ACTIVE_IMPULSES.remove(key) != null) {
        releaseImpulseNetwork(level, pos, be);
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.5f, 1.2f);
        ThreeDifiedNotificationHud.sendTo(sp, "§b[Wonderum] §eKinetic Impulse Stopped");
        return;
      }

      if (applyImpulseNetwork(level, pos, be, IMPULSE_RPM, PER_RPM_SU)) {
        ACTIVE_IMPULSES.put(key, DURATION_TICKS);
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.55f, 1.6f);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 12, 0.2D, 0.2D, 0.2D, 0.03D);
        ThreeDifiedNotificationHud.sendTo(sp, "§b[Wonderum] §fKinetic Impulse: §e1024 SU §7(64 RPM • 20s)");
      }
    });
  }

  @SubscribeEvent
  public static void onServerTick(ServerTickEvent.Post event) {
    if (ACTIVE_IMPULSES.isEmpty()) return;
    MinecraftServer server = event.getServer();

    Iterator<Map.Entry<ImpulseKey, Integer>> it = ACTIVE_IMPULSES.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<ImpulseKey, Integer> entry = it.next();
      ImpulseKey key = entry.getKey();
      ServerLevel level = server.getLevel(key.dimension());

      if (level == null) {
        WormholeKineticBridge.clearDriven(key.dimension(), key.pos());
        it.remove();
        continue;
      }

      if (!level.isLoaded(key.pos())) continue;

      BlockEntity be = level.getBlockEntity(key.pos());
      int nextTicks = entry.getValue() - 1;
      if (be == null || 0 >= nextTicks) {
        if (be != null) {
          releaseImpulseNetwork(level, key.pos(), be);
        } else {
          WormholeKineticBridge.clearDriven(key.dimension(), key.pos());
        }
        level.playSound(null, key.pos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.45f, 1.2f);
        it.remove();
      } else {
        entry.setValue(nextTicks);
      }
    }
  }

  private static boolean applyImpulseNetwork(ServerLevel level, BlockPos pos, BlockEntity be, float speed, float perRpmCap) {
    try {
      Class<?> kbeClass = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
      Class<?> netClass = Class.forName("com.simibubi.create.content.kinetics.KineticNetwork");
      Class<?> propClass = Class.forName("com.simibubi.create.content.kinetics.RotationPropagator");
      if (!kbeClass.isInstance(be)) return false;

      // 1. Register this node as a 64 RPM / 16 SU-per-RPM (1024 SU) source
      WormholeKineticBridge.setDriven(level.dimension(), pos, speed, perRpmCap);

      // 2. If not already attached to a spinning network, assign this block as a source root
      Object existingNet = kbeClass.getMethod("getOrCreateNetwork").invoke(be);
      float currentSpeed = (float) kbeClass.getMethod("getSpeed").invoke(be);
      if (existingNet == null || Math.abs(currentSpeed) < 0.01f) {
        kbeClass.getMethod("detachKinetics").invoke(be);
        kbeClass.getMethod("setSpeed", float.class).invoke(be, speed);
        kbeClass.getMethod("setNetwork", Long.class).invoke(be, pos.asLong());
        kbeClass.getMethod("attachKinetics").invoke(be);
      }

      // 3. Propagate through the shared network and add this node's 1024 SU capacity to the network total
      propClass.getMethod("handleAdded", Level.class, BlockPos.class, kbeClass)
        .invoke(null, level, pos, be);

      Object net = kbeClass.getMethod("getOrCreateNetwork").invoke(be);
      if (net != null) {
        netClass.getMethod("add", kbeClass).invoke(net, be);
        netClass.getMethod("updateCapacityFor", kbeClass, float.class).invoke(net, be, perRpmCap);
        netClass.getMethod("updateStress").invoke(net);
      }

      kbeClass.getMethod("sendData").invoke(be);
      var bs = level.getBlockState(pos);
      level.sendBlockUpdated(pos, bs, bs, 3);
      level.updateNeighborsAt(pos, bs.getBlock());
      return true;
    } catch (Throwable t) {
      t.printStackTrace();
      return false;
    }
  }

  private static void releaseImpulseNetwork(ServerLevel level, BlockPos pos, BlockEntity be) {
    try {
      Class<?> kbeClass = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
      Class<?> netClass = Class.forName("com.simibubi.create.content.kinetics.KineticNetwork");
      Class<?> propClass = Class.forName("com.simibubi.create.content.kinetics.RotationPropagator");
      if (!kbeClass.isInstance(be)) {
        WormholeKineticBridge.clearDriven(level.dimension(), pos);
        return;
      }

      // 1. Subtract this node's 1024 SU capacity from the network before clearing its source flag
      Object net = kbeClass.getMethod("getOrCreateNetwork").invoke(be);
      if (net != null) {
        netClass.getMethod("updateCapacityFor", kbeClass, float.class).invoke(net, be, 0.0f);
        netClass.getMethod("updateStress").invoke(net);
      }

      // 2. Clear driven state on this node
      WormholeKineticBridge.clearDriven(level.dimension(), pos);
      propClass.getMethod("handleRemoved", Level.class, BlockPos.class, kbeClass)
        .invoke(null, level, pos, be);

      kbeClass.getMethod("detachKinetics").invoke(be);
      kbeClass.getMethod("setSpeed", float.class).invoke(be, 0.0f);
      kbeClass.getMethod("setNetwork", Long.class).invoke(be, (Long) null);
      kbeClass.getMethod("sendData").invoke(be);

      var bs = level.getBlockState(pos);
      level.sendBlockUpdated(pos, bs, bs, 3);
      level.updateNeighborsAt(pos, bs.getBlock());

      // 3. Re-propagate any remaining active Wonderum impulses in this dimension so surviving sources keep the line spinning!
      for (ImpulseKey otherKey : ACTIVE_IMPULSES.keySet()) {
        if (!otherKey.dimension().equals(level.dimension()) || otherKey.pos().equals(pos)) continue;
        if (!level.isLoaded(otherKey.pos())) continue;
        BlockEntity otherBe = level.getBlockEntity(otherKey.pos());
        if (otherBe != null && kbeClass.isInstance(otherBe)) {
          applyImpulseNetwork(level, otherKey.pos(), otherBe, IMPULSE_RPM, PER_RPM_SU);
        }
      }

      // 4. Re-attach direct neighbors so any permanent Create generators (waterwheels, motors, wormholes) keep driving this node
      Method handleAdded = propClass.getMethod("handleAdded", Level.class, BlockPos.class, kbeClass);
      for (Direction dir : Direction.values()) {
        BlockPos neighborPos = pos.relative(dir);
        if (!level.isLoaded(neighborPos)) continue;
        BlockEntity neighborBe = level.getBlockEntity(neighborPos);
        if (neighborBe != null && kbeClass.isInstance(neighborBe)) {
          float nSpeed = (float) kbeClass.getMethod("getSpeed").invoke(neighborBe);
          if (Math.abs(nSpeed) > 0.01f) {
            handleAdded.invoke(null, level, neighborPos, neighborBe);
            Object nNet = kbeClass.getMethod("getOrCreateNetwork").invoke(neighborBe);
            if (nNet != null) {
              netClass.getMethod("updateStress").invoke(nNet);
            }
          }
        }
      }
    } catch (Throwable ignored) {
      WormholeKineticBridge.clearDriven(level.dimension(), pos);
    }
  }

  public static boolean isImpulseNode(Level level, BlockPos pos) {
    return level != null && ACTIVE_IMPULSES.containsKey(new ImpulseKey(level.dimension(), pos));
  }
}
