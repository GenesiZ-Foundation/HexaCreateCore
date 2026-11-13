package com.genesiz.hexacreatecore.difficulty;

import com.genesiz.hexacreatecore.difficulty.data.DifficultyData.DifficultyTier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class DifficultySavedData extends SavedData {
  private static final String DATA_NAME = "hexacreatecore_difficulty";

  private DifficultyTier activeTier = DifficultyTier.NORMAL;
  private boolean genesisUnlocked = false;

  public DifficultyTier getActiveTier() {
    return activeTier;
  }

  public void setActiveTier(DifficultyTier tier) {
    if (tier == DifficultyTier.GENESIS && !genesisUnlocked) return;
    this.activeTier = tier;
    setDirty();
  }

  public boolean isGenesisUnlocked() {
    return genesisUnlocked;
  }

  public void unlockGenesis() {
    if (!this.genesisUnlocked) {
      this.genesisUnlocked = true;
      setDirty();
    }
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
    tag.putString("active_tier", activeTier.id);
    tag.putBoolean("genesis_unlocked", genesisUnlocked);
    return tag;
  }

  public static DifficultySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
    DifficultySavedData data = new DifficultySavedData();
    data.genesisUnlocked = tag.getBoolean("genesis_unlocked");
    DifficultyTier loaded = DifficultyTier.fromId(tag.getString("active_tier"));
    data.activeTier = (loaded == DifficultyTier.GENESIS && !data.genesisUnlocked) ? DifficultyTier.ABSOLUTION : loaded;
    return data;
  }

  public static DifficultySavedData get(ServerLevel level) {
    ServerLevel overworld = level.getServer().overworld();
    return overworld.getDataStorage().computeIfAbsent(
      new SavedData.Factory<>(DifficultySavedData::new, DifficultySavedData::load),
      DATA_NAME
    );
  }
}
