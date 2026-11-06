package com.genesiz.hexacreatecore.virtual;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

public record DialogueText(List<FormattedCharSequence> lines, float boxWidth, float boxHeight) {

  public static DialogueText calculate(Font font, String text, int maxPixelWidth) {
    List<FormattedCharSequence> wrappedLines = font.split(Component.literal(text), maxPixelWidth);

    int longestLineWidth = 0;
    for (FormattedCharSequence line : wrappedLines) {
      int lineWidth = font.width(line);
      if (lineWidth > longestLineWidth) {
        longestLineWidth = lineWidth;
      }
    }

    float finalWidth = longestLineWidth + 16.0f;

    float finalHeight = (wrappedLines.size() * font.lineHeight) + 12.0f;

    return new DialogueText(wrappedLines, finalWidth, finalHeight);
  }
}
