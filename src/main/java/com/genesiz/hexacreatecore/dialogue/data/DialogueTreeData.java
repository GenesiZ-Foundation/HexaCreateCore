package com.genesiz.hexacreatecore.dialogue.data;

import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Map;

public record DialogueTreeData(
    ResourceLocation npcId,
    ResourceLocation blipSoundId,
    float charsPerSecond,
    int maxWidth,
    int bgColor,
    int borderColor,
    int buttonBgColor,
    int buttonBorderColor,
    int buttonHoverColor,
    String entryNode,
    Map<String, DialogueStepData> nodes
) {
  public record DialogueStepData(
      String text,
      int bgColor,
      int borderColor,
      List<DialogueOptionData> options
  ) {}

  public record DialogueOptionData(
      String label,
      String nextNode,
      String actionId,
      String rightClickLabel,
      int bgColor,
      int borderColor,
      int hoverColor
  ) {}
}
