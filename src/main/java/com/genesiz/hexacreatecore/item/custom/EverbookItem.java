package com.genesiz.hexacreatecore.item.custom;

import com.genesiz.hexacreatecore.hexcasting.everbook.EverbookData;
import com.genesiz.hexacreatecore.hexcasting.everbook.EverbookSyncPayload;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.List;

public class EverbookItem extends Item {

  private static String toRoman(int num) {
    if (1 > num || num > 9999) return String.valueOf(num);
    int[] values = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
    String[] symbols = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };
    StringBuilder sb = new StringBuilder();
    int remaining = num;
    for (int i = 0; values.length > i; i++) {
      while (remaining >= values[i]) {
        sb.append(symbols[i]);
        remaining -= values[i];
      }
    }
    return sb.toString();
  }

  public EverbookItem(Properties properties) {
    super(properties.stacksTo(1).fireResistant());
  }

  @Override
  public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
    if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
      if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)) {
        tooltipComponents.add(Component.literal("HexaCreate"));
      }
    }
    EverbookData.BookState state = EverbookData.read(stack);
    int iotaCount = state.currentPage().entries().size();
    tooltipComponents.add(Component.literal("§7Chapter: §d" + state.currentPage().title() + " §8(" + iotaCount + (iotaCount == 1 ? " Iota)" : " Iotas)")));
    tooltipComponents.add(Component.literal("§8Sneak + Scroll: §fSwitch / Create Chapter"));
    tooltipComponents.add(Component.literal("§8Right-Click: §fOpen 3D Soul Grid & Nebula Rift"));
    tooltipComponents.add(Component.literal("§8Sprint + Left-Click (in Grid): §fLock Rift & Mini-Grid"));
    tooltipComponents.add(Component.empty());
    tooltipComponents.add(
      Component.literal("One of the concepts of the world, myths said that it could weave imagination itself, on the back of the book is written \"The Creator's favorite\"")
        .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.ITALIC)
    );
  }

  @EventBusSubscriber(modid = com.genesiz.hexacreatecore.HexaCreateCore.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
  public static final class ClientChapterScroll {
    @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onSneakScrollChapter(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc.level == null || mc.player == null || mc.screen != null) return;
      if (!mc.player.isShiftKeyDown()) return;

      boolean mainIsBook = mc.player.getMainHandItem().getItem() instanceof EverbookItem;
      boolean offIsBook = mc.player.getOffhandItem().getItem() instanceof EverbookItem;
      if (!mainIsBook && !offIsBook) return;

      boolean isOffhand = !mainIsBook;
      ItemStack bookStack = isOffhand ? mc.player.getOffhandItem() : mc.player.getMainHandItem();
      EverbookData.BookState state = EverbookData.read(bookStack);

      java.util.List<EverbookData.BookPage> pages = new java.util.ArrayList<>(state.pages());
      if (pages.isEmpty()) {
        pages.add(new EverbookData.BookPage("Chapter I", new java.util.ArrayList<>()));
      }

      int current = Math.max(0, Math.min(state.activePage(), pages.size() - 1));
      int nextIdx = current;

      if (event.getScrollDeltaY() > 0.0D) {
        // Scroll Up -> Previous Chapter
        if (current > 0) {
          nextIdx = current - 1;
        }
      } else if (0.0D > event.getScrollDeltaY()) {
        // Scroll Down -> Next Chapter (auto-creates a new chapter if scrolling past the last non-empty chapter)
        if (pages.size() - 1 > current) {
          nextIdx = current + 1;
        } else if (!pages.get(current).entries().isEmpty() || pages.size() == 1) {
          String nextTitle = "Chapter " + toRoman(pages.size() + 1);
          pages.add(new EverbookData.BookPage(nextTitle, new java.util.ArrayList<>()));
          nextIdx = pages.size() - 1;
        }
      }

      if (nextIdx != current || pages.size() != state.pages().size()) {
        EverbookData.BookState updated = new EverbookData.BookState(nextIdx, pages);
        EverbookData.write(bookStack, updated);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
          new EverbookSyncPayload(isOffhand, "SWITCH_CHAPTER", updated.toNbt())
        );

        EverbookData.BookPage activePage = updated.currentPage();
        mc.player.playNotifySound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, net.minecraft.sounds.SoundSource.PLAYERS, 0.85f, 1.1f);
        com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud.show(
          "§d[Everbook] §f" + activePage.title() + " §8(" + (nextIdx + 1) + "/" + pages.size() + " • " + activePage.entries().size() + " Iotas)"
        );
      }

      event.setCanceled(true);
    }
  }
}
