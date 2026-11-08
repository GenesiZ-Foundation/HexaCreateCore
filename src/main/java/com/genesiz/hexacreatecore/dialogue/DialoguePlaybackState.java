/*
 * THIS PART OF THE CODE IS MEANT FOR DEVELOPERS
 * */
package com.genesiz.hexacreatecore.dialogue;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.Collections;
import java.util.List;

import com.genesiz.hexacreatecore.dialogue.data.DialogueReloadListener;
import com.genesiz.hexacreatecore.dialogue.data.DialogueTreeData;
import com.genesiz.hexacreatecore.dialogue.network.DialogueActionPayload;

/* FULL BREAKDOWN:
 *
 * -> This is where your dialogues lives, it is a cache holding every information of it
 *
 * (take your time to read the names of the different variables)
 *
 * [.update()] is meant to update the text and size of the text, as well as
 * updating if the text should be written with a typewriter effect and how fast
 * it goes
 *
 *
 * [.buildPreWrappedString()] is a helper method meant to be used by other methods
 * as the name implies, it builds a string for the dialogue boxes
 *
 *
 * [.updateFromEntity] is the method responsible for JSON bridging,
 *   ->if no JSON is found it will return the text: "..." with the
 *     Click sound and typewriter effect as a default
 *
 *   ->if a JSON is found it will run for the each nodes for the first time (and cache it)
 *     or when F3+T is pressed, it grabs various informations from the JSON through
 *     DialogueReloadListener;
 *
 *     It will check the current node it is in and will use the .update() methode
 *     with the information available
 *
 *    -> calls .loadStep() on the initial load (or after F3 + T) to load the
 *    JSON's entry_node
 *
 *  [.loadStep()] is responsible for the "next" action of the dialogue in the
 *  dialogue tree,
 *  -it clears the text on the next dialogue,
 *  -it resets the typewriter effect if there is one,
 *  -it auto-binds the action_id to a right click,
 *  -it auto-binds the left click to show the label on the action bar (text above the Exp Bar)
 *  -it rebuilds the option buttons for the new node
 *   (option in the JSON is a list, it will remake the option list with the insides
 *   of the JSON options block, all of them are customizable the same way the base
 *   dialogue box is, so you can have different kinds of buttons)
 *
 *
 * [.setShowing()] detects open and close states transitions for when a dialogue is
 * ended or there is another text to be shown (so it resets the typewriter effect, the text, etc...)
 *
 *
 * [.triggerLeftClick()] and .triggerRightClick() are self explanatory
 *
 *
 * [.resetTypewriter()] is self explanatory
 *
 *
 * [.isForceClosed()] is self explanatory, though it may be used as an other type of
 * check in custom renderers (be warned that it is not advised to modify it's intended purpose)
 *
 *
 * [.getVisibleLines() and .getFullLines()] are helper methods meant for other methods in
 * the class, however you can use them to fetch the currently visible text
 * and the full text within the node, mostly if you'd want to add your own
 * custom effects;
 * */
public class DialoguePlaybackState {
  private       String                        cachedText = "";
  private       String                        preWrappedText = "";
  private       int                           cachedMaxWidth = -1;
  private       List<FormattedCharSequence>   cachedFullLines = Collections.emptyList();
  private       List<FormattedCharSequence>   cachedVisibleLines = Collections.emptyList();
  public        float                         boxWidth;
  public        float                         boxHeight;

  private       boolean                       wasShowing = false;
  public        float                         revealedChars = 0.0f;
  private       int                           lastVisibleInt = 0;
  public        int                           hoveredOptionIndex = -1;

  private       DialogueTreeData              activeTree = null;
  private       String                        currentNodeId = null;
  private       boolean                       forceClosed = false;
  public final  java.util.List<DialogueNode>  options = new java.util.ArrayList<>(4);
  private       SoundEvent                    cachedBlipSound = SoundEvents.UI_BUTTON_CLICK.value();

  public        int                           currentBgColor = 0xFF000000;
  public        int                           currentBorderColor = 0xFFFFFFFF;



  public void update(Font font, String text, int maxPixelWidth, float deltaSeconds, float charsPerSecond, SoundEvent blipSound, boolean typewriter) {
    if (!text.equals(this.cachedText) || maxPixelWidth != this.cachedMaxWidth) {
      this.cachedText = text;
      this.cachedMaxWidth = maxPixelWidth;
      this.revealedChars = 0.0f;
      this.lastVisibleInt = 0;

      // 1. Pre-wrap the FULL text once so line breaks are locked in upfront
      this.preWrappedText = font.plainSubstrByWidth(text, maxPixelWidth); // Helper below builds full wrapped string
      this.preWrappedText = buildPreWrappedString(font, text, maxPixelWidth);

      this.cachedFullLines = font.split(Component.literal(this.preWrappedText), Integer.MAX_VALUE);
      int longestLine = 0;
      for (int i = 0; this.cachedFullLines.size() > i; i++) {
        int w = font.width(this.cachedFullLines.get(i));
        if (w > longestLine) longestLine = w;
      }

      this.boxWidth = longestLine + 16.0f;
      this.boxHeight = (this.cachedFullLines.size() * font.lineHeight) + 12.0f;
      this.cachedVisibleLines = Collections.emptyList();
    }

    if (!typewriter) {
      this.revealedChars = this.preWrappedText.length();
      this.lastVisibleInt = this.preWrappedText.length();
      this.cachedVisibleLines = this.cachedFullLines;
      return;
    }

    if (this.preWrappedText.length() > this.revealedChars) {
      this.revealedChars = Math.min(this.preWrappedText.length(), this.revealedChars + (charsPerSecond * deltaSeconds));
      int currentVisibleInt = (int) this.revealedChars;

      if (currentVisibleInt > this.lastVisibleInt) {
        // Split only on our pre-inserted '\n' characters (zero mid-word wrapping shifts)
        this.cachedVisibleLines = font.split(Component.literal(this.preWrappedText.substring(0, currentVisibleInt)), Integer.MAX_VALUE);

        if (blipSound != null && !Character.isWhitespace(this.preWrappedText.charAt(currentVisibleInt - 1))) {
          float pitch = 0.95f + (float) (Math.random() * 0.1f);
          Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(blipSound, pitch, 0.5f));
        }

        this.lastVisibleInt = currentVisibleInt;
      }
    } else if (this.cachedVisibleLines != this.cachedFullLines) {
      this.cachedVisibleLines = this.cachedFullLines;
    }
  }

  private static String buildPreWrappedString(Font font, String rawText, int maxWidth) {
    StringBuilder sb = new StringBuilder(rawText.length() + 16);
    font.getSplitter().splitLines(rawText, maxWidth, net.minecraft.network.chat.Style.EMPTY, false, (style, start, end) -> {
      if (!sb.isEmpty()) sb.append('\n');
      sb.append(rawText, start, end);
    });
    return sb.toString();
  }

  public void updateFromEntity(Font font, Entity entity, float deltaSeconds) {
    ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
    DialogueTreeData tree = DialogueReloadListener.getDialogueForEntity(entityId);

    if (tree == null) {
      update(font, "...", 120, deltaSeconds, 25.0f, SoundEvents.UI_BUTTON_CLICK.value(), true);
      return;
    }

    // Runs once on conversation start AND immediately after every F3 + T hot-reload
    if (this.activeTree != tree || this.currentNodeId == null) {
      this.cachedBlipSound = BuiltInRegistries.SOUND_EVENT
          .getOptional(tree.blipSoundId())
          .orElse(SoundEvents.UI_BUTTON_CLICK.value());
      loadStep(font, entity, tree, tree.entryNode());
    }

    DialogueTreeData.DialogueStepData step = tree.nodes().get(this.currentNodeId);
    if (step == null) return;

    update(font, step.text(), tree.maxWidth(), deltaSeconds, tree.charsPerSecond(), this.cachedBlipSound, true);
  }

  public boolean triggerLeftClick(Entity entity, Player player) {
    if (0 > this.hoveredOptionIndex || this.hoveredOptionIndex + 1 > this.options.size()) return false;
    DialogueNode node = this.options.get(this.hoveredOptionIndex);
    if (node != null && node.onLeftClick != null) {
      node.onLeftClick.execute(entity, player);
      return true;
    }
    return false;
  }

  public boolean triggerRightClick(Entity entity, Player player) {
    if (0 > this.hoveredOptionIndex || this.hoveredOptionIndex + 1 > this.options.size()) return false;
    DialogueNode node = this.options.get(this.hoveredOptionIndex);
    if (node != null && node.onRightClick != null) {
      node.onRightClick.execute(entity, player);
      return true;
    }
    return false;
  }

  public void resetTypewriter() {
    this.revealedChars = 0.0f;
    this.lastVisibleInt = 0;
    this.cachedVisibleLines = Collections.emptyList();
    this.forceClosed = false;
    this.currentNodeId = null;
  }

  public boolean isForceClosed() {
    return this.forceClosed;
  }


  public void setShowing(boolean isShowing) {
    if (!isShowing) {
      this.hoveredOptionIndex = -1;
      this.forceClosed = false;
      this.currentNodeId = null;
    } else if (!this.wasShowing) {
      this.revealedChars = 0.0f;
      this.lastVisibleInt = 0;
      this.cachedVisibleLines = Collections.emptyList();
      this.currentNodeId = null;
    }
    this.wasShowing = isShowing;
  }



  public void loadStep(Font font, Entity entity, DialogueTreeData tree, String nodeId) {
    if ("close".equals(nodeId)) {
      this.forceClosed = true;
      this.hoveredOptionIndex = -1;
      return;
    }

    DialogueTreeData.DialogueStepData step = tree.nodes().get(nodeId);
    if (step == null) return;

    this.activeTree = tree;
    this.currentNodeId = nodeId;
    this.revealedChars = 0.0f;
    this.lastVisibleInt = 0;
    this.cachedVisibleLines = Collections.emptyList();
    this.hoveredOptionIndex = -1;
    this.currentBgColor = step.bgColor();
    this.currentBorderColor = step.borderColor();


    // Rebuild option buttons from JSON
    this.options.clear();
    List<DialogueTreeData.DialogueOptionData> optList = step.options();
    for (int i = 0; optList.size() > i; i++) {
      DialogueTreeData.DialogueOptionData optData = optList.get(i);
      DialogueNode node = new DialogueNode();
      node.setText(font, optData.label(), tree.maxWidth());
      node.bgColor = optData.bgColor();
      node.borderColor = optData.borderColor();
      node.hoverBorderColor = optData.hoverColor();

      // Right-click: advance to next_node (and trigger action_id if present)
      node.onRightClick = (targetEntity, player) -> {
        if (!optData.actionId().isEmpty()) {
          PacketDistributor.sendToServer(
            new DialogueActionPayload(targetEntity.getId(), optData.actionId(), true)
          );
        }
        loadStep(font, targetEntity, tree, optData.nextNode());
      };

      // Left-click: show inspect hint if defined in JSON
      if (!optData.rightClickLabel().isEmpty()) {
        node.onLeftClick = (targetEntity, player) -> {
          player.displayClientMessage(Component.literal(optData.rightClickLabel()), true);
        };
      }

      this.options.add(node);
    }
  }



  public List<FormattedCharSequence> getVisibleLines() {
    return this.cachedVisibleLines;
  }
  public List<FormattedCharSequence> getFullLines() {
    return this.cachedFullLines;
  }
}
