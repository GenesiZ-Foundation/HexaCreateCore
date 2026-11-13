package com.genesiz.hexacreatecore.threedified.actions;

import com.genesiz.hexacreatecore.decorators.annotations.DialogueAction;
import com.genesiz.hexacreatecore.threedified.network.DialogueNetworking;
import com.genesiz.hexacreatecore.entity.custom.CursedBlacksmithEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class CursedBlacksmithActions {

  @DialogueAction("hexacreatecore:start_forging")
  public static void startForging(ServerPlayer serverPlayer, Entity npcEntity) {
    if (npcEntity instanceof CursedBlacksmithEntity) {
      serverPlayer.giveExperienceLevels(1);
    }
  }

  @DialogueAction("hexacreatecore:show_tutorial")
  public static void showTutorial(ServerPlayer serverPlayer, Entity npcEntity) {
    DialogueNetworking.queueHologramPlacement(serverPlayer, "cobble_frame", 600);
    DialogueNetworking.syncAnimationOverride(npcEntity, "cursed_blacksmith_controller", "idle");
  }

  @DialogueAction("hexacreatecore:clear_completed_hologram")
  public static void clearCompletedHologram(ServerPlayer serverPlayer, Entity dummyEntity) {
    DialogueNetworking.clearPendingPlayerPlacement(serverPlayer);
  }
}
