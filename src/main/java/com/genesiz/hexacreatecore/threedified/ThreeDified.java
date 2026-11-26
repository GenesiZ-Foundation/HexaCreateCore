/*
 * THIS PART OF THE CODE IS MEANT FOR DEVELOPERS
 * */
package com.genesiz.hexacreatecore.threedified;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/* FULL BREAKDOWN:
 *
 * -> Core developer interface that an Entity implements to use the default 3D dialogue system
 *
 * [.shouldShowDialogue()] returns true when the entity's 3D dialogue should render
 * [.mobInteract()] is self explanatory
 * [.getDialogueTransform()] is self explanatory
 * [.getDialoguePlayback()] is self explanatory
 * [.hasCustomRenderer()] return true to skip ThreeDifiedRenderer and draw your own UI
 *
 * */
// PUBLIC API INTERFACE
public interface ThreeDified {
  boolean shouldShowDialogue();
  InteractionResult mobInteract(Player player, InteractionHand hand);
  DialogueTransformState getDialogueTransform();
  DialoguePlaybackState getDialoguePlayback();
  default boolean hasCustomRenderer(){return false;}
}
