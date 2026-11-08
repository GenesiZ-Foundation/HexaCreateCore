package com.genesiz.hexacreatecore.dialogue.hologram;

import net.minecraft.world.level.block.state.BlockState;

public record MultiblockBlueprintData(
    String structureId,
    int[] offsetX,
    int[] offsetY,
    int[] offsetZ,
    BlockState[] states
) {
  public int blockCount() {
    return this.states.length;
  }
}
