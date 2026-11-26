package com.genesiz.hexacreatecore.hexcasting.everbook;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.item.custom.EverbookItem;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class EverbookPlayerAnimController {

  private static final Set<UUID> REMOTE_OPEN_PLAYERS = ConcurrentHashMap.newKeySet();
  private static final Map<UUID, Float> PREV_OPEN_PROG = new ConcurrentHashMap<>();
  private static final Map<UUID, Float> CURR_OPEN_PROG = new ConcurrentHashMap<>();

  private EverbookPlayerAnimController() {}

  public static void setRemotePlayerOpen(UUID playerId, boolean open) {
    if (open) {
      REMOTE_OPEN_PLAYERS.add(playerId);
    } else {
      REMOTE_OPEN_PLAYERS.remove(playerId);
    }
  }

  public static boolean isPlayerBookOpen(Player player) {
    if (player == null) return false;
    return isPlayerIdBookOpen(player.getUUID());
  }

  public static boolean isPlayerIdBookOpen(UUID playerId) {
    if (playerId == null) return false;
    Minecraft mc = Minecraft.getInstance();
    if (mc.player != null && playerId.equals(mc.player.getUUID())) {
      return EverbookSessionState.isActive();
    }
    return REMOTE_OPEN_PLAYERS.contains(playerId);
  }

  public static float getPlayerOpenProgress(Player player, float partialTick) {
    if (player == null) return 0.0f;
    UUID id = player.getUUID();
    float prev = PREV_OPEN_PROG.getOrDefault(id, 0.0f);
    float curr = CURR_OPEN_PROG.getOrDefault(id, 0.0f);
    return Mth.lerp(partialTick, prev, curr);
  }

  public static float getLocalOpenProgress(float partialTick) {
    Minecraft mc = Minecraft.getInstance();
    return getPlayerOpenProgress(mc.player, partialTick);
  }

  public static UUID resolveStackHolder(ItemStack stack, ItemDisplayContext ctx) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || stack == null || stack.isEmpty()) return null;

    if (ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
      return (mc.player != null) ? mc.player.getUUID() : null;
    }
    if (ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
      if (mc.player != null && (ItemStack.isSameItemSameComponents(mc.player.getMainHandItem(), stack)
        || ItemStack.isSameItemSameComponents(mc.player.getOffhandItem(), stack))) {
        return mc.player.getUUID();
      }
      for (Player p : mc.level.players()) {
        if (ItemStack.isSameItemSameComponents(p.getMainHandItem(), stack)
          || ItemStack.isSameItemSameComponents(p.getOffhandItem(), stack)) {
          return p.getUUID();
        }
      }
    }
    return null;
  }

  @SubscribeEvent
  public static void onClientTick(ClientTickEvent.Post event) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null) {
      REMOTE_OPEN_PLAYERS.clear();
      PREV_OPEN_PROG.clear();
      CURR_OPEN_PROG.clear();
      return;
    }

    for (Player player : mc.level.players()) {
      UUID id = player.getUUID();
      boolean holdingBook = player.getMainHandItem().getItem() instanceof EverbookItem
        || player.getOffhandItem().getItem() instanceof EverbookItem;
      boolean shouldBeOpen = holdingBook && isPlayerBookOpen(player);

      float curr = CURR_OPEN_PROG.getOrDefault(id, 0.0f);
      PREV_OPEN_PROG.put(id, curr);

      float target = shouldBeOpen ? 1.0f : 0.0f;
      float step = shouldBeOpen ? 0.16f : 0.22f;
      float next = Mth.clamp(curr + Math.signum(target - curr) * Math.min(step, Math.abs(target - curr)), 0.0f, 1.0f);
      CURR_OPEN_PROG.put(id, next);
    }
  }
}
