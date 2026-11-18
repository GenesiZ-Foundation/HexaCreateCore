package com.genesiz.hexacreatecore.threedified.hologram;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
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

  public record TransformedBlueprint(
    int[] offsetX,
    int[] offsetY,
    int[] offsetZ,
    BlockState[] states
  ) {
    public int blockCount() {
      return this.states.length;
    }
  }

  public TransformedBlueprint transformed(net.minecraft.world.level.LevelAccessor level, BlockPos anchor, Rotation rotation, Mirror mirror) {
    int count = blockCount();
    int[] tx = new int[count];
    int[] ty = new int[count];
    int[] tz = new int[count];
    BlockState[] tStates = new BlockState[count];

    int minX = Integer.MAX_VALUE;
    int minZ = Integer.MAX_VALUE;

    for (int i = 0; count > i; i++) {
      int mx = switch (mirror) {
        case FRONT_BACK -> -offsetX[i];
        default -> offsetX[i];
      };
      int mz = switch (mirror) {
        case LEFT_RIGHT -> -offsetZ[i];
        default -> offsetZ[i];
      };

      BlockPos p = new BlockPos(mx, offsetY[i], mz).rotate(rotation);
      tx[i] = p.getX();
      ty[i] = p.getY();
      tz[i] = p.getZ();
      if (minX > tx[i]) minX = tx[i];
      if (minZ > tz[i]) minZ = tz[i];
    }

    int shiftX = (count > 0) ? minX : 0;
    int shiftZ = (count > 0) ? minZ : 0;

    for (int i = 0; count > i; i++) {
      tx[i] -= shiftX;
      tz[i] -= shiftZ;
      BlockPos worldPos = anchor.offset(tx[i], ty[i], tz[i]);
      BlockState mirrored = states[i].mirror(mirror);
      tStates[i] = (level != null) ? mirrored.rotate(level, worldPos, rotation) : mirrored;
    }

    return new TransformedBlueprint(tx, ty, tz, tStates);
  }
}
