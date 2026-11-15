package com.genesiz.hexacreatecore.item.custom;

import com.genesiz.hexacreatecore.hexcasting.everbook.EverbookData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class EverbookItem extends Item {

  public EverbookItem(Properties properties) {
    super(properties.stacksTo(1).fireResistant());
  }

  @Override
  public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
    EverbookData.BookState state = EverbookData.read(stack);
    int iotaCount = state.currentPage().entries().size();
    tooltipComponents.add(Component.literal("§7Chapter: §d" + state.currentPage().title() + " §8(" + iotaCount + (iotaCount == 1 ? " Iota)" : " Iotas)")));
    tooltipComponents.add(Component.literal("§8Right-Click: §fOpen 3D Soul Grid & Nebula Rift"));
    tooltipComponents.add(Component.literal("§8Sprint + Left-Click (in Grid): §fLock Rift & Mini-Grid"));
    tooltipComponents.add(Component.empty());
    tooltipComponents.add(
      Component.literal("One of the concepts of the world, myths said that it could weave imagination itself, on the back of the book is written \"The Creator's favorite\"")
        .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.ITALIC)
    );
  }
}
