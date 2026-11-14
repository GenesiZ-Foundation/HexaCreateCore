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
  public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    EverbookData.BookState state = EverbookData.read(stack);
    int totalPatterns = state.pages().stream().mapToInt(p -> p.entries().size()).sum();
    tooltip.add(Component.literal("§7Chapter: §d" + state.currentPage().title() + " §8(" + totalPatterns + " sigils)"));
    tooltip.add(Component.literal("§8Sneak + Right-Click: Open 3D Soul Grid & Nebula Rift"));
    tooltip.add(Component.literal("§8Sprint + Left-Click (in Grid): Lock Rift & Mini-Grid"));
  }
}
