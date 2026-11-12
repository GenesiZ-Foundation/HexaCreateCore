package com.genesiz.hexacreatecore.hexcasting.actions;

import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.genesiz.hexacreatecore.decorators.annotations.DialogueAction;
import com.genesiz.hexacreatecore.dialogue.network.LiveStackUpdatePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HexCastingActions {

  @DialogueAction(value = "hexacreatecore:cast_3d_spell", selfTargeted = true)
  public static void cast3DSpell(ServerPlayer serverPlayer, Entity proxyEntity) {
    try {
      String rawActionString = serverPlayer.getPersistentData().getString("hexacreatecore_raw_action");
      int directionOrdinal = 0;
      String anglesSignature = "w";

      if (rawActionString != null && rawActionString.contains(";")) {
        String[] tokensArray = rawActionString.split(";");
        if (tokensArray.length >= 3) {
          directionOrdinal = Integer.parseInt(tokensArray[1]);
          anglesSignature = tokensArray[2];
        }
      }


      HexDir[] directionValues = HexDir.values();
      if (directionOrdinal < 0 || directionOrdinal >= directionValues.length) directionOrdinal = 0;
      HexDir startDir = directionValues[directionOrdinal];

      HexPattern pattern = null;
      try {
        Class<?> signatureClass = Class.forName("at.petrak.hexcasting.api.casting.math.HexSignature");
        Method factoryMethod = signatureClass.getMethod("fromAnglesStringUnchecked", String.class);
        factoryMethod.setAccessible(true);
        Object hexSignatureInstance = factoryMethod.invoke(null, anglesSignature);

        if (hexSignatureInstance != null) {
          Class<?> patternClass = Class.forName("at.petrak.hexcasting.api.casting.math.HexPattern");
          Constructor<?> patternCtor = patternClass.getConstructor(HexDir.class, signatureClass);
          patternCtor.setAccessible(true);
          pattern = (HexPattern) patternCtor.newInstance(startDir, hexSignatureInstance);
        }
      } catch (Throwable fatalAllocationError) {
        fatalAllocationError.printStackTrace();
      }

      if (pattern == null) return;

      CastingVM liveCastingVM = IXplatAbstractions.INSTANCE.getStaffcastVM(serverPlayer, InteractionHand.MAIN_HAND);
      if (liveCastingVM != null) {
        liveCastingVM.queueExecuteAndWrapIota(new PatternIota(pattern), serverPlayer.serverLevel());
        IXplatAbstractions.INSTANCE.setStaffcastImage(serverPlayer, liveCastingVM.getImage());

        CastingImage updatedImageSnapshot = liveCastingVM.getImage();
        if (updatedImageSnapshot != null) {
          List<Iota> activeIotaStack = updatedImageSnapshot.getStack();
          List<String> synchronizedStackList = new ArrayList<>();

          if (activeIotaStack != null && !activeIotaStack.isEmpty()) {
            for (Iota activeItemIota : activeIotaStack) {
              if (activeItemIota == null) continue;
              Component localizedDisplayComponent = activeItemIota.display();
              String readableIotaText = (localizedDisplayComponent != null) ? localizedDisplayComponent.getString() : activeItemIota.toString();
              synchronizedStackList.add(readableIotaText);
            }
          }

          String readableRavensmind = "";
          try {
            Field compField = updatedImageSnapshot.getClass().getDeclaredField("components");
            compField.setAccessible(true);
            Object compObj = compField.get(updatedImageSnapshot);

            if (compObj instanceof Map<?, ?> componentsMap) {
              for (Map.Entry<?, ?> entry : componentsMap.entrySet()) {
                if (entry.getKey() != null && entry.getKey().toString().contains("ravenmind")) {
                  if (entry.getValue() instanceof Iota ravenIota) {
                    Component comp = ravenIota.display();
                    readableRavensmind = (comp != null) ? comp.getString() : ravenIota.toString();
                  }
                  break;
                }
              }
            }
          } catch (Throwable t) {
            t.printStackTrace();
          }

          PacketDistributor.sendToPlayer(
            serverPlayer,
            new LiveStackUpdatePayload(synchronizedStackList, readableRavensmind)
          );
        }
      }
    } catch (Exception serverException) {
      serverException.printStackTrace();
    }
  }

  @DialogueAction(value = "hexacreatecore:clear_3d_stack", selfTargeted = true)
  public static void clear3DStack(ServerPlayer serverPlayer, Entity proxyEntity) {
    IXplatAbstractions.INSTANCE.clearCastingData(serverPlayer);
    IXplatAbstractions.INSTANCE.setPatterns(serverPlayer, List.of());
    IXplatAbstractions.INSTANCE.setStaffcastImage(serverPlayer, new CastingImage());

    PacketDistributor.sendToPlayer(serverPlayer, new LiveStackUpdatePayload(List.of(), ""));

    try {
      Class<?> packetClass = Class.forName("at.petrak.hexcasting.common.msgs.MsgClearSpiralPatternsS2C");
      Constructor<?> ctor = packetClass.getConstructor(UUID.class);
      CustomPacketPayload msg = (CustomPacketPayload) ctor.newInstance(serverPlayer.getUUID());
      IXplatAbstractions.INSTANCE.sendPacketToPlayer(serverPlayer, msg);
      IXplatAbstractions.INSTANCE.sendPacketTracking(serverPlayer, msg);
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  @DialogueAction(value = "hexacreatecore:overreach_penalty", selfTargeted = true)
  public static void overreachPenalty(ServerPlayer serverPlayer, Entity proxyEntity) {
    var darkness = serverPlayer.getEffect(MobEffects.DARKNESS);
    if (darkness == null || darkness.getDuration() < 60) {
      serverPlayer.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, false));
    }

    var blindness = serverPlayer.getEffect(MobEffects.BLINDNESS);
    if (blindness == null || blindness.getDuration() < 60) {
      serverPlayer.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false, false));
    }

    String rawAction = serverPlayer.getPersistentData().getString("hexacreatecore_raw_action");
    if (rawAction != null && rawAction.endsWith(";wither")) {
      var wither = serverPlayer.getEffect(MobEffects.WITHER);
      if (wither == null || wither.getAmplifier() < 1 || wither.getDuration() < 60) {
        serverPlayer.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1, false, false, false));
      }
    }
  }
}

