/*
 * THIS PART OF THE CODE IS MEANT FOR DEVELOPERS
 * */
package com.genesiz.hexacreatecore.threedified;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/* FULL BREAKDOWN:
 *
 * -> Callback interface invoked when a player left- or right-clicks a 3D dialogue option node
 *
 * [.execute()] is self explanatory
 *
 * */
@FunctionalInterface
public interface DialogueClickAction {
  void execute(Entity dialogueEntity, Player player);
}
