/*
 * THIS PART OF THE CODE IS MEANT FOR DEVELOPERS
 * */
package com.genesiz.hexacreatecore.threedified;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

public record DialogueText(List<FormattedCharSequence> lines, float boxWidth, float boxHeight) {

  public static DialogueText calculate(Font font, String fullText, int maxPixelWidth, int visibleChars) {
    List<FormattedCharSequence> fullLines = font.split(Component.literal(fullText), maxPixelWidth);

    int longestLineWidth = 0;
    for (FormattedCharSequence line : fullLines) {
      int lineWidth = font.width(line);
      if (lineWidth > longestLineWidth) {
        longestLineWidth = lineWidth;
      }
    }

    float finalWidth = longestLineWidth + 16.0f;
    float finalHeight = (fullLines.size() * font.lineHeight) + 12.0f;

    // 2. Clamp and slice the string for the typewriter reveal
    int safeLimit = Math.max(0, Math.min(visibleChars, fullText.length()));
    String visibleSubstring = fullText.substring(0, safeLimit);
    List<FormattedCharSequence> visibleLines = font.split(Component.literal(visibleSubstring), maxPixelWidth);

    return new DialogueText(visibleLines, finalWidth, finalHeight);
  }
}
