package com.genesiz.hexacreatecore.create.wonderum.wormhole;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WormholeKineticBridge {

  public record DrivenState(float speed, float perRpmCapacity) {}

  private static final Map<ResourceKey<Level>, Map<BlockPos, DrivenState>> DRIVEN_NODES = new ConcurrentHashMap<>();

  private WormholeKineticBridge() {}

  public static void setDriven(ResourceKey<Level> dim, BlockPos pos, float speed, float perRpmCapacity) {
    DRIVEN_NODES.computeIfAbsent(dim, k -> new ConcurrentHashMap<>())
      .put(pos.immutable(), new DrivenState(speed, perRpmCapacity));
  }

  public static void clearDriven(ResourceKey<Level> dim, BlockPos pos) {
    Map<BlockPos, DrivenState> map = DRIVEN_NODES.get(dim);
    if (map != null) {
      map.remove(pos);
    }
  }

  public static DrivenState getDriven(Level level, BlockPos pos) {
    if (level == null || level.isClientSide) return null;
    Map<BlockPos, DrivenState> map = DRIVEN_NODES.get(level.dimension());
    return (map != null) ? map.get(pos) : null;
  }
}
