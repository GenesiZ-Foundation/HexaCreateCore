package com.genesiz.hexacreatecore.virtual;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;

public interface Dialogue3DTypewriter extends Dialogue3D {

  EntityDataAccessor<Integer> getDialogueTicksAccessor();

  default void handleDialogueSync(EntityDataAccessor<?> key) {
    if (this instanceof Entity entity && entity.level().isClientSide) {
      if (getDialogueTicksAccessor().equals(key)) {
        int currentTicks = entity.getEntityData().get(getDialogueTicksAccessor());
        getDialoguePlayback().onTicksSynced(currentTicks);
      }
    }
  }
}
