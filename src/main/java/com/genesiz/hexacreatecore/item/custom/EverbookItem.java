package com.genesiz.hexacreatecore.item.custom;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.hexcasting.everbook.EverbookData;
import com.genesiz.hexacreatecore.hexcasting.everbook.EverbookItemRenderer;
import com.genesiz.hexacreatecore.hexcasting.everbook.EverbookSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

public class EverbookItem extends Item implements GeoItem {

  private static final RawAnimation ANIM_CLOSED_IDLE = RawAnimation.begin().thenLoop("closed_idle");
  private static final RawAnimation ANIM_OPEN = RawAnimation.begin().thenPlay("open");
  private static final RawAnimation ANIM_OPEN_IDLE = RawAnimation.begin().thenLoop("open_idle");
  private static final RawAnimation ANIM_CLOSE = RawAnimation.begin().thenPlay("close");

  private static long openAnimStartNanos = 0L;
  private static long closeAnimStartNanos = 0L;

  private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

  public EverbookItem(Properties properties) {
    super(properties.stacksTo(1).fireResistant());
    SingletonGeoAnimatable.registerSyncedAnimatable(this);
  }

  @Override
  public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    controllers.add(new AnimationController<>(this, "book_controller", 2, state -> {
      net.minecraft.world.item.ItemDisplayContext perspective =
        state.getData(software.bernie.geckolib.constant.DataTickets.ITEM_RENDER_PERSPECTIVE);
      boolean inHand = perspective == net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
        || perspective == net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND
        || perspective == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
        || perspective == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND;

      boolean wantOpen = inHand && com.genesiz.hexacreatecore.hexcasting.everbook.EverbookSessionState.isActive();
      var ctrl = state.getController();
      long now = System.nanoTime();

      if (wantOpen) {
        if (!EverbookItemRenderer.wasHeldOpen()) {
          EverbookItemRenderer.markWasHeldOpen(true);
          openAnimStartNanos = now;
          ctrl.forceAnimationReset();
          return state.setAndContinue(ANIM_OPEN);
        }
        if (500_000_000L > (now - openAnimStartNanos)) {
          return state.setAndContinue(ANIM_OPEN);
        }
        return state.setAndContinue(ANIM_OPEN_IDLE);
      } else {
        if (inHand && EverbookItemRenderer.consumeWasHeldOpen()) {
          closeAnimStartNanos = now;
          ctrl.forceAnimationReset();
          return state.setAndContinue(ANIM_CLOSE);
        }
        if (inHand && closeAnimStartNanos != 0L && 334_000_000L > (now - closeAnimStartNanos)) {
          return state.setAndContinue(ANIM_CLOSE);
        }
        if (inHand) {
          closeAnimStartNanos = 0L;
        }
        return state.setAndContinue(ANIM_CLOSED_IDLE);
      }
    }));
  }

  @Override
  public AnimatableInstanceCache getAnimatableInstanceCache() {
    return this.cache;
  }

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

  @Override
  public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
    if (FMLEnvironment.dist.isClient()) {
      if (!(Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen)) {
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
        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC)
    );
  }

  @EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
  public static final class ClientChapterScroll {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSneakScrollChapter(InputEvent.MouseScrollingEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null || mc.screen != null) return;
      if (!mc.player.isShiftKeyDown()) return;

      boolean mainIsBook = mc.player.getMainHandItem().getItem() instanceof EverbookItem;
      boolean offIsBook = mc.player.getOffhandItem().getItem() instanceof EverbookItem;
      if (!mainIsBook && !offIsBook) return;

      boolean isOffhand = !mainIsBook;
      ItemStack bookStack = isOffhand ? mc.player.getOffhandItem() : mc.player.getMainHandItem();
      EverbookData.BookState state = EverbookData.read(bookStack);

      List<EverbookData.BookPage> pages = new ArrayList<>(state.pages());
      if (pages.isEmpty()) {
        pages.add(new EverbookData.BookPage("Chapter I", new ArrayList<>()));
      }

      int current = Math.max(0, Math.min(state.activePage(), pages.size() - 1));
      int nextIdx = current;

      if (event.getScrollDeltaY() > 0.0D) {
        if (current > 0) {
          nextIdx = current - 1;
        }
      } else if (0.0D > event.getScrollDeltaY()) {
        if (pages.size() - 1 > current) {
          nextIdx = current + 1;
        } else if (!pages.get(current).entries().isEmpty() || pages.size() == 1) {
          String nextTitle = "Chapter " + toRoman(pages.size() + 1);
          pages.add(new EverbookData.BookPage(nextTitle, new ArrayList<>()));
          nextIdx = pages.size() - 1;
        }
      }

      if (nextIdx != current || pages.size() != state.pages().size()) {
        EverbookData.BookState updated = new EverbookData.BookState(nextIdx, pages);
        EverbookData.write(bookStack, updated);
        PacketDistributor.sendToServer(
          new EverbookSyncPayload(isOffhand, "SWITCH_CHAPTER", updated.toNbt())
        );

        EverbookData.BookPage activePage = updated.currentPage();
        mc.player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.85f, 1.1f);
        com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud.show(
          "§d[Everbook] §f" + activePage.title() + " §8(" + (nextIdx + 1) + "/" + pages.size() + " • " + activePage.entries().size() + " Iotas)"
        );
      }

      event.setCanceled(true);
    }
  }
}
