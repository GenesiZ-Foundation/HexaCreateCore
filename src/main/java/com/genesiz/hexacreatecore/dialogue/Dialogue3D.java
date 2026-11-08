/*
 * THIS PART OF THE CODE IS MEANT FOR DEVELOPERS
 * */
package com.genesiz.hexacreatecore.dialogue;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

// PUBLIC API INTERFACE
public interface Dialogue3D {
  boolean shouldShowDialogue();
  InteractionResult mobInteract(Player player, InteractionHand hand);
  DialogueTransformState getDialogueTransform();
  DialoguePlaybackState getDialoguePlayback();
  default boolean hasCustomRenderer(){return false;}
}
