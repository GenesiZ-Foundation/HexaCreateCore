package com.genesiz.hexacreatecore.threedified.hologram;

import net.minecraft.world.level.block.state.BlockState;

public record MultiblockBlueprintData(
    String structureId,
    int bgColor,
    int borderColor,
    int progressColor,
    int[] offsetX,
    int[] offsetY,
    int[] offsetZ,
    BlockState[] states
) {
  public int blockCount() {
    return this.states.length;
  }
}
