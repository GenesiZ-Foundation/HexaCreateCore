package com.genesiz.hexacreatecore.create.wonderum.blueprint;

import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WonderumPaletteScreen extends Screen {

  private static final int COLS = 9;
  private static final int ROWS = 5;
  private static final int SLOT_SIZE = 20;

  private final List<Block> allPlaceableBlocks = new ArrayList<>();
  private final List<Block> filteredBlocks = new ArrayList<>();
  private EditBox searchBox;
  private int scrollRow = 0;

  public WonderumPaletteScreen() {
    super(Component.literal("Wonderum Hologram Palette"));
    for (Item item : BuiltInRegistries.ITEM) {
      if (item instanceof BlockItem bi) {
        Block b = bi.getBlock();
        if (b != Blocks.AIR) {
          allPlaceableBlocks.add(b);
        }
      }
    }
    filteredBlocks.addAll(allPlaceableBlocks);
  }

  @Override
  protected void init() {
    int panelW = (COLS * SLOT_SIZE) + 24;
    int panelH = (ROWS * SLOT_SIZE) + 46;
    int left = (this.width - panelW) / 2;
    int top = (this.height - panelH) / 2;

    this.searchBox = new EditBox(this.font, left + 12, top + 10, panelW - 24, 16, Component.literal("Search blocks..."));
    this.searchBox.setResponder(this::applyFilter);
    this.addRenderableWidget(this.searchBox);
    this.setInitialFocus(this.searchBox);
  }

  private void applyFilter(String query) {
    filteredBlocks.clear();
    scrollRow = 0;
    String q = (query == null) ? "" : query.trim().toLowerCase(Locale.ROOT);
    for (Block b : allPlaceableBlocks) {
      if (q.isEmpty()
        || b.getName().getString().toLowerCase(Locale.ROOT).contains(q)
        || BuiltInRegistries.BLOCK.getKey(b).toString().contains(q)) {
        filteredBlocks.add(b);
      }
    }
  }

  @Override
  public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
    this.renderBackground(gui, mouseX, mouseY, partialTick);

    int panelW = (COLS * SLOT_SIZE) + 24;
    int panelH = (ROWS * SLOT_SIZE) + 46;
    int left = (this.width - panelW) / 2;
    int top = (this.height - panelH) / 2;

    MultiBufferSource.BufferSource buf = this.minecraft.renderBuffers().bufferSource();
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RawGeometry.drawDialogueNode(
      gui.pose(), buf, this.font,
      left + (panelW * 0.5f), top + (panelH * 0.5f), 0.0f,
      panelW, panelH, 3.0f, 3.0f,
      0xEE0D0814, 0xFF00FFAA, List.of(), true
    );
    buf.endBatch(RawGeometry.DIALOGUE_SOLID);
    buf.endBatch();

    super.render(gui, mouseX, mouseY, partialTick);

    int gridLeft = left + 12;
    int gridTop = top + 34;
    int startIdx = scrollRow * COLS;

    for (int r = 0; ROWS > r; r++) {
      for (int c = 0; COLS > c; c++) {
        int idx = startIdx + (r * COLS) + c;
        if (idx >= filteredBlocks.size()) break;

        int sx = gridLeft + (c * SLOT_SIZE);
        int sy = gridTop + (r * SLOT_SIZE);
        Block block = filteredBlocks.get(idx);
        boolean hovered = mouseX >= sx && (sx + 18) > mouseX && mouseY >= sy && (sy + 18) > mouseY;
        boolean selected = block == WonderumDraftingSession.getSelectedBrushBlock();

        gui.fill(sx, sy, sx + 18, sy + 18, selected ? 0x6600FFAA : (hovered ? 0x44FFFFFF : 0x331E1B2E));
        gui.renderItem(new ItemStack(block), sx + 1, sy + 1);

        if (hovered) {
          gui.renderTooltip(this.font, block.getName(), mouseX, mouseY);
        }
      }
    }
  }

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    if (super.mouseClicked(mouseX, mouseY, button)) return true;

    int panelW = (COLS * SLOT_SIZE) + 24;
    int panelH = (ROWS * SLOT_SIZE) + 46;
    int gridLeft = ((this.width - panelW) / 2) + 12;
    int gridTop = ((this.height - panelH) / 2) + 34;
    int startIdx = scrollRow * COLS;

    for (int r = 0; ROWS > r; r++) {
      for (int c = 0; COLS > c; c++) {
        int idx = startIdx + (r * COLS) + c;
        if (idx >= filteredBlocks.size()) break;

        int sx = gridLeft + (c * SLOT_SIZE);
        int sy = gridTop + (r * SLOT_SIZE);
        if (mouseX >= sx && (sx + 18) > mouseX && mouseY >= sy && (sy + 18) > mouseY) {
          Block chosen = filteredBlocks.get(idx);
          WonderumDraftingSession.setSelectedBrushBlock(chosen);
          ThreeDifiedNotificationHud.show("§b[Wonderum] §fBrush: §a" + chosen.getName().getString() + " §8(Right-Click place / Left-Click erase)");
          this.onClose();
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    int maxRows = Math.max(0, (int) Math.ceil(filteredBlocks.size() / (double) COLS) - ROWS);
    scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxRows);
    return true;
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
