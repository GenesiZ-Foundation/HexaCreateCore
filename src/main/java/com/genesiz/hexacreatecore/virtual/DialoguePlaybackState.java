package com.genesiz.hexacreatecore.virtual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;

import java.util.Collections;
import java.util.List;

public class DialoguePlaybackState {
  private String cachedText = "";
  private int cachedMaxWidth = -1;
  private List<FormattedCharSequence> cachedFullLines = Collections.emptyList();
  private List<FormattedCharSequence> cachedVisibleLines = Collections.emptyList();
  public float boxWidth;
  public float boxHeight;

  public float revealedChars = 0.0f;
  private int lastVisibleInt = 0;
  private int lastTicks = 0;

  public void update(Font font, String text, int maxPixelWidth, float deltaSeconds, float charsPerSecond, SoundEvent blipSound) {
    if (!text.equals(this.cachedText) || maxPixelWidth != this.cachedMaxWidth) {
      this.cachedText = text;
      this.cachedMaxWidth = maxPixelWidth;
      this.revealedChars = 0.0f;
      this.lastVisibleInt = 0;

      this.cachedFullLines = font.split(Component.literal(text), maxPixelWidth);
      int longestLine = 0;
      for (int i = 0; i < this.cachedFullLines.size(); i++) {
        int w = font.width(this.cachedFullLines.get(i));
        if (w > longestLine) longestLine = w;
      }

      this.boxWidth = longestLine + 16.0f;
      this.boxHeight = (this.cachedFullLines.size() * font.lineHeight) + 12.0f;
      this.cachedVisibleLines = Collections.emptyList();
    }

    if (this.revealedChars < text.length()) {
      this.revealedChars = Math.min(text.length(), this.revealedChars + (charsPerSecond * deltaSeconds));
      int currentVisibleInt = (int) this.revealedChars;

      if (currentVisibleInt > this.lastVisibleInt) {
        this.cachedVisibleLines = font.split(Component.literal(text.substring(0, currentVisibleInt)), maxPixelWidth);

        if (blipSound != null && !Character.isWhitespace(text.charAt(currentVisibleInt - 1))) {
          float pitch = 0.95f + (float) (Math.random() * 0.1f);
          Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(blipSound, pitch, 0.5f));
        }

        this.lastVisibleInt = currentVisibleInt;
      }
    } else if (this.cachedVisibleLines != this.cachedFullLines) {
      this.cachedVisibleLines = this.cachedFullLines;
    }
  }

  public List<FormattedCharSequence> getVisibleLines() {
    return this.cachedVisibleLines;
  }

  public void onTicksSynced(int newTicks) {
    if (newTicks > this.lastTicks) {
      this.revealedChars = 0.0f;
      this.lastVisibleInt = 0;
      this.cachedVisibleLines = Collections.emptyList();
    }
    this.lastTicks = newTicks;
  }
}
