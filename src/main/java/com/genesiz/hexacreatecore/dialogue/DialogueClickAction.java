/*
 * THIS PART OF THE CODE IS MEANT FOR DEVELOPERS
 * */
package com.genesiz.hexacreatecore.dialogue;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

@FunctionalInterface
public interface DialogueClickAction {
  void execute(Entity dialogueEntity, Player player);
}
