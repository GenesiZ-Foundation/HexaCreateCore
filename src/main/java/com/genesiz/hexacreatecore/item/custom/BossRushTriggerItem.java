package com.genesiz.hexacreatecore.item.custom;

import com.genesiz.hexacreatecore.bossrush.BossRushManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BossRushTriggerItem extends Item {
  public BossRushTriggerItem(Properties properties) {
    super(properties);
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);
    if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
      if (BossRushManager.startBossRush(serverPlayer)) {
        if (!serverPlayer.getAbilities().instabuild) {
          stack.shrink(1);
        }
        return InteractionResultHolder.consume(stack);
      }
    }
    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
  }
}
