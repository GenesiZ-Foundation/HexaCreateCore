package com.genesiz.hexacreatecore.hexcasting.everbook;

import at.petrak.hexcasting.api.HexAPI;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class EverbookHexWriter {

  private EverbookHexWriter() {}

  public static void inscribeToHexItem(ServerPlayer player, ItemStack targetStack, List<EverbookData.PatternEntry> entries) {
    if (targetStack.isEmpty() || entries.isEmpty()) return;

    List<Iota> iotas = new ArrayList<>(entries.size());
    for (EverbookData.PatternEntry entry : entries) {
      HexDir startDir;
      try { startDir = HexDir.valueOf(entry.startDir()); } catch (Exception ignored) { startDir = HexDir.EAST; }
      HexPattern pattern = buildPattern(startDir, entry.angles());
      if (pattern != null) iotas.add(new PatternIota(pattern));
    }
    if (iotas.isEmpty()) return;

    var iotaHolder = IXplatAbstractions.INSTANCE.findDataHolder(targetStack);
    if (iotaHolder != null) {
      ListIota listIota = new ListIota(iotas);
      if (iotaHolder.writeIota(listIota, true)) {
        iotaHolder.writeIota(listIota, false);
        com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud.sendTo(player, "§d[Everbook] §aInscribed " + iotas.size() + " Iotas!");
        return;
      }
    }

    var hexHolder = IXplatAbstractions.INSTANCE.findHexHolder(targetStack);
    if (hexHolder != null && !hexHolder.hasHex()) {
      hexHolder.writeHex(iotas, HexAPI.instance().getColorizer(player), 0L);
      com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud.sendTo(player, "§d[Everbook] §aInscribed " + iotas.size() + " Iotas into casting item!");
    }
  }

  private static HexPattern buildPattern(HexDir startDir, String angles) {
    try {
      Class<?> signatureClass = Class.forName("at.petrak.hexcasting.api.casting.math.HexSignature");
      var factoryMethod = signatureClass.getMethod("fromAnglesStringUnchecked", String.class);
      Object signature = factoryMethod.invoke(null, angles);
      var constructor = HexPattern.class.getConstructor(HexDir.class, signatureClass);
      constructor.setAccessible(true);
      return constructor.newInstance(startDir, signature);
    } catch (Throwable ignored) {
      return null;
    }
  }
}
