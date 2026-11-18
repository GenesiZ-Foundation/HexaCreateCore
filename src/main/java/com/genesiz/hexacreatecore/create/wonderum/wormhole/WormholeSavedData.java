package com.genesiz.hexacreatecore.create.wonderum.wormhole;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WormholeSavedData extends SavedData {

  public record Endpoint(ResourceKey<Level> dimension, BlockPos pos, Direction face) {
    public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("Dim", dimension.location().toString());
      tag.putLong("Pos", pos.asLong());
      tag.putString("Face", face.getName());
      return tag;
    }

    public static Endpoint load(CompoundTag tag) {
      ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dim")));
      BlockPos pos = BlockPos.of(tag.getLong("Pos"));
      Direction face = Direction.byName(tag.getString("Face"));
      return new Endpoint(dim, pos, face != null ? face : Direction.UP);
    }
  }

  public record WormholePair(UUID id, Endpoint a, Endpoint b) {
    public boolean matches(ResourceKey<Level> dim, BlockPos pos) {
      return (a.dimension().equals(dim) && a.pos().equals(pos))
          || (b.dimension().equals(dim) && b.pos().equals(pos));
    }
  }

  private static final String DATA_NAME = "hexacreatecore_wonderum_wormholes";
  private final List<WormholePair> pairs = new ArrayList<>();
  private final Map<UUID, Endpoint> pendingAnchors = new HashMap<>();

  public WormholeSavedData() {}

  public static WormholeSavedData get(MinecraftServer server) {
    ServerLevel overworld = server.overworld();
    return overworld.getDataStorage().computeIfAbsent(
      new Factory<>(WormholeSavedData::new, WormholeSavedData::load, null),
      DATA_NAME
    );
  }

  public List<WormholePair> getPairs() { return pairs; }
  public Endpoint getPending(UUID playerId) { return pendingAnchors.get(playerId); }

  public void setPending(UUID playerId, Endpoint endpoint) {
    pendingAnchors.put(playerId, endpoint);
    setDirty();
  }

  public void clearPending(UUID playerId) {
    if (pendingAnchors.remove(playerId) != null) setDirty();
  }

  public void addPair(Endpoint a, Endpoint b) {
    removeAt(a.dimension(), a.pos());
    removeAt(b.dimension(), b.pos());
    pairs.add(new WormholePair(UUID.randomUUID(), a, b));
    setDirty();
  }

  public boolean removeAt(ResourceKey<Level> dim, BlockPos pos) {
    boolean removed = pairs.removeIf(p -> p.matches(dim, pos));
    if (removed) setDirty();
    return removed;
  }

  public static WormholeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
    WormholeSavedData data = new WormholeSavedData();
    ListTag pairList = tag.getList("Pairs", Tag.TAG_COMPOUND);
    for (int i = 0; pairList.size() > i; i++) {
      CompoundTag entry = pairList.getCompound(i);
      UUID id = entry.hasUUID("Id") ? entry.getUUID("Id") : UUID.randomUUID();
      data.pairs.add(new WormholePair(id, Endpoint.load(entry.getCompound("A")), Endpoint.load(entry.getCompound("B"))));
    }
    ListTag pendingList = tag.getList("Pending", Tag.TAG_COMPOUND);
    for (int i = 0; pendingList.size() > i; i++) {
      CompoundTag entry = pendingList.getCompound(i);
      if (entry.hasUUID("Player")) {
        data.pendingAnchors.put(entry.getUUID("Player"), Endpoint.load(entry.getCompound("Endpoint")));
      }
    }
    return data;
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
    ListTag pairList = new ListTag();
    for (WormholePair p : pairs) {
      CompoundTag entry = new CompoundTag();
      entry.putUUID("Id", p.id());
      entry.put("A", p.a().save());
      entry.put("B", p.b().save());
      pairList.add(entry);
    }
    tag.put("Pairs", pairList);

    ListTag pendingList = new ListTag();
    for (Map.Entry<UUID, Endpoint> e : pendingAnchors.entrySet()) {
      CompoundTag entry = new CompoundTag();
      entry.putUUID("Player", e.getKey());
      entry.put("Endpoint", e.getValue().save());
      pendingList.add(entry);
    }
    tag.put("Pending", pendingList);
    return tag;
  }
}

