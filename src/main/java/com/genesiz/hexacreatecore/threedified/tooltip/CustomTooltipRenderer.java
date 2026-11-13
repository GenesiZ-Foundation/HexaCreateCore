package com.genesiz.hexacreatecore.threedified.tooltip;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.genesiz.hexacreatecore.threedified.text.TextEffectStyle;
import com.genesiz.hexacreatecore.threedified.text.ThreeDifiedTextRenderer;
import com.genesiz.hexacreatecore.threedified.tooltip.TooltipStyleRegistry.ItemTooltipConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class CustomTooltipRenderer {

  private static final Pattern INLINE_TAG = Pattern.compile("^\\[([a-zA-Z0-9_#:-]+)](.*)\\[/[a-zA-Z0-9_#:-]+]$");

  private record StyledLine(
    String plainText,
    FormattedCharSequence fallbackSeq,
    TextEffectStyle style,
    boolean bold
  ) {}

  private CustomTooltipRenderer() {}

  @SubscribeEvent(priority = EventPriority.HIGH)
  public static void onRenderTooltipPre(RenderTooltipEvent.Pre event) {
    ItemStack stack = event.getItemStack();
    if (stack.isEmpty()) return;

    ItemTooltipConfig config = TooltipStyleRegistry.getForStack(stack);
    List<ClientTooltipComponent> components = event.getComponents();
    if (components.isEmpty()) return;

    Font font = event.getFont();
    List<StyledLine> parsedLines = new ArrayList<>(components.size());
    boolean hasAnyCustomEffect = (config != null);

    for (int i = 0; components.size() > i; i++) {
      ClientTooltipComponent comp = components.get(i);
      if (!(comp instanceof ClientTextTooltip textTooltip)) {
        // Non-text tooltip elements (e.g. bundle grid): let vanilla render if no config
        if (!hasAnyCustomEffect) return;
        continue;
      }

      FormattedCharSequence seq = extractSequence(textTooltip);
      String raw = toPlainString(seq);
      String stripped = ChatFormatting.stripFormatting(raw);
      if (stripped == null) stripped = raw;

      TextEffectStyle lineStyle = null;
      String displayText = stripped;

      // 1. Check inline [style]...[/style] tag on this line
      Matcher m = INLINE_TAG.matcher(stripped.trim());
      if (m.matches()) {
        lineStyle = TooltipStyleRegistry.parseStyleSpec(m.group(1));
        displayText = m.group(2);
        if (lineStyle != null) hasAnyCustomEffect = true;
      }

      // 2. Apply item config title_style (line 0) or lore_style (lines > 0)
      if (lineStyle == null && config != null) {
        if (i == 0 && config.titleStyle() != null) {
          lineStyle = config.titleStyle();
        } else if (i > 0 && config.loreStyle() != null && !displayText.isBlank()) {
          lineStyle = config.loreStyle();
        }
      }

      parsedLines.add(new StyledLine(displayText, seq, lineStyle, i == 0));
    }

    if (!hasAnyCustomEffect || parsedLines.isEmpty()) return;

    event.setCanceled(true);

    GuiGraphics gui = event.getGraphics();
    int screenW = event.getScreenWidth();
    int screenH = event.getScreenHeight();

    int maxW = 0;
    int totalH = 0;
    for (int i = 0; parsedLines.size() > i; i++) {
      StyledLine line = parsedLines.get(i);
      int w = line.bold()
      ? font.width(Component.literal(line.plainText()).withStyle(Style.EMPTY.withBold(true)))
      : font.width(line.plainText());
      maxW = Math.max(maxW, w);
      totalH += (i == 0) ? (font.lineHeight + 4) : (font.lineHeight + 2);
    }

    int padX = 10;
    int padY = 8;
    int boxW = maxW + (padX * 2);
    int boxH = totalH + (padY * 2);

    int x = event.getX() + 12;
    int y = event.getY() - 12;
    if (x + boxW > screenW - 4) {
      x = Math.max(4, event.getX() - 16 - boxW);
    }
    if (y + boxH > screenH - 4) {
      y = Math.max(4, screenH - boxH - 4);
    }
    if (4 > y) y = 4;

    int borderColor = (config != null) ? config.borderColor() : 0xFF8A4FFF;
    int bgColor = (config != null) ? config.bgColor() : 0xEE0D0A14;

    PoseStack poseStack = gui.pose();
    poseStack.pushPose();
    poseStack.translate(0.0f, 0.0f, 400.0f);

    Minecraft mc = Minecraft.getInstance();
    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();

    RawGeometry.drawDialogueNode(
      poseStack, bufferSource, font,
      x + (boxW * 0.5f), y + (boxH * 0.5f), 0.0f,
      boxW, boxH, 3.0f, 3.0f,
      bgColor, borderColor, List.of(), true
    );

    if (config != null && config.shapeVariant() != null && !config.shapeVariant().isEmpty()) {
      com.genesiz.hexacreatecore.threedified.threedishapes_hooks.ThreeDiShapeHooks.renderAttachedShapes(
        poseStack, bufferSource,
        com.genesiz.hexacreatecore.threedified.threedishapes_hooks.UiShapeTarget.TOOLTIP_FRAME,
        config.shapeVariant(),
        x + (boxW * 0.5f), y + (boxH * 0.5f), 0.0f, boxW, boxH
      );
    }

    bufferSource.endBatch(RawGeometry.DIALOGUE_SOLID);
    bufferSource.endBatch();
    RenderSystem.disableDepthTest();

    float cursorY = y + padY;
    float textX = x + padX;

    for (int i = 0; parsedLines.size() > i; i++) {
      StyledLine line = parsedLines.get(i);
      if (line.style() != null) {
        if (line.bold()) {
          ThreeDifiedTextRenderer.draw2DBold(gui, font, line.plainText(), textX, cursorY, line.style());
        } else {
          ThreeDifiedTextRenderer.draw2D(gui, font, line.plainText(), textX, cursorY, line.style());
        }
      } else {
        gui.drawString(font, line.fallbackSeq(), Math.round(textX), Math.round(cursorY), 0xFFAAAAAA, true);
      }
      cursorY += (i == 0) ? (font.lineHeight + 4) : (font.lineHeight + 2);
    }

    poseStack.popPose();
  }

  private static FormattedCharSequence extractSequence(ClientTextTooltip tooltip) {
    try {
      var field = ClientTextTooltip.class.getDeclaredField("text");
      field.setAccessible(true);
      return (FormattedCharSequence) field.get(tooltip);
    } catch (Exception e) {
      return FormattedCharSequence.EMPTY;
    }
  }

  private static String toPlainString(FormattedCharSequence seq) {
    StringBuilder sb = new StringBuilder();
    seq.accept((idx, style, codePoint) -> {
      sb.appendCodePoint(codePoint);
      return true;
    });
    return sb.toString();
  }
}
