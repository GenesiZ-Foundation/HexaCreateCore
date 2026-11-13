/*
 * THIS PART OF THE CODE IS MEANT FOR DEVELOPERS
 * */
package com.genesiz.hexacreatecore.threedified;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.Collections;
import java.util.List;

/* FULL BREAKDOWN:
 *
 * -> stores a size, bg_color, border_color, hover_color, text, left/right click callbacks
 *
 * baseHeight and baseWidth are the normal sizes of the dialogue box
 *
 * width and height are the "modified" sizes intended to be used with updatePressScale
 *
 * onLeftClick is the Left Click callback that is going to be used in the DialoguePlaybackState class
 * with the .triggerLeftClick() method
 *
 * onRightClick is the Right Click callback that is going to be used in the DialoguePlaybackState class
 * with the .triggerRightClick() method
 * 
 * */
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
  public void updatePressScale(boolean isHeldDown, float toScale, float deltaSeconds) {
    float targetScale = isHeldDown ? toScale : 1.0f;
    float targetW = this.baseWidth * targetScale;
    float targetH = this.baseHeight * targetScale;

    float factor = 1.0f - (float) Math.exp(-25.0f * deltaSeconds);
    this.width += (targetW - this.width) * factor;
    this.height += (targetH - this.height) * factor;
  }
}
