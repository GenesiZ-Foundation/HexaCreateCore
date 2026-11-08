package com.genesiz.hexacreatecore.dialogue;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.Collections;
import java.util.List;

public class DialogueNode {
  public float baseWidth;
  public float baseHeight;
  public float width;
  public float height;
  public int bgColor;
  public int borderColor;
  public int hoverBorderColor;
  public List<FormattedCharSequence> lines = Collections.emptyList();
  public DialogueClickAction onLeftClick;
  public DialogueClickAction onRightClick;

  public void setText(Font font, String text, int maxWidth) {
    this.lines = font.split(Component.literal(text), maxWidth);
    int longest = 0;
    for (int i = 0; this.lines.size() > i; i++) {
      int w = font.width(this.lines.get(i));
      if (w > longest) longest = w;
    }
    this.baseWidth = longest + 16.0f;
    this.baseHeight = (this.lines.size() * font.lineHeight) + 12.0f;
    this.width = this.baseWidth;
    this.height = this.baseHeight;
  }
  public void updatePressScale(boolean isHeldDown, float deltaSeconds) {
    float targetScale = isHeldDown ? 0.85f : 1.0f;
    float targetW = this.baseWidth * targetScale;
    float targetH = this.baseHeight * targetScale;

    float factor = 1.0f - (float) Math.exp(-25.0f * deltaSeconds);
    this.width += (targetW - this.width) * factor;
    this.height += (targetH - this.height) * factor;
  }
}
