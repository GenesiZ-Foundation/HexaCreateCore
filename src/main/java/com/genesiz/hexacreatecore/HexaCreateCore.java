package com.genesiz.hexacreatecore;

import org.slf4j.Logger;
import com.genesiz.hexacreatecore.datagen.ModModelProvider;
import com.genesiz.hexacreatecore.dialogue.data.DialogueReloadListener;
import com.genesiz.hexacreatecore.dialogue.hologram.BlueprintReloadListener;
import com.genesiz.hexacreatecore.dialogue.network.DialogueNetworking;
import com.genesiz.hexacreatecore.entity.ModEntities;
import com.genesiz.hexacreatecore.entity.custom.CursedBlacksmithEntity;
import com.genesiz.hexacreatecore.item.HexaCreateCoreItems;
import com.genesiz.hexacreatecore.item.ModCreativeModeTabs;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.fml.ModContainer;

@Mod(HexaCreateCore.MODID)
public class HexaCreateCore {
  public static final String MODID = "hexacreatecore";
  public static final Logger LOGGER = LogUtils.getLogger();

  static {
    // Standard gameplay experience logic hook
    DialogueNetworking.registerAction("hexacreatecore:start_forging", (serverPlayer, npcEntity) -> {
      if (npcEntity instanceof CursedBlacksmithEntity) {
        serverPlayer.giveExperienceLevels(1);
      }
    });

    DialogueNetworking.registerAction("hexacreatecore:show_tutorial", (serverPlayer, npcEntity) -> {
      DialogueNetworking.queueHologramPlacement(serverPlayer, "cobble_frame", 600);
      DialogueNetworking.syncAnimationOverride(npcEntity, "cursed_blacksmith_controller", "idle");
    });

    DialogueNetworking.registerAction("hexacreatecore:clear_completed_hologram", (serverPlayer, dummyEntity) -> {
      DialogueNetworking.clearPendingPlayerPlacement(serverPlayer);
    });

    DialogueNetworking.registerAction("hexacreatecore:cast_3d_spell", (serverPlayer, proxyEntity) -> {
      try {
        String rawActionString = serverPlayer.getPersistentData().getString("hexacreatecore_raw_action");
        int directionOrdinal = 0;
        String anglesSignature = "w";

        if (rawActionString != null && rawActionString.contains(";")) {
          String[] tokensArray = rawActionString.split(";");
          if (tokensArray.length >= 3) {
            directionOrdinal = Integer.parseInt((String) java.lang.reflect.Array.get(tokensArray, 1));
            anglesSignature = (String) java.lang.reflect.Array.get(tokensArray, 2);
          }
        }

        at.petrak.hexcasting.api.casting.math.HexDir[] directionValues = at.petrak.hexcasting.api.casting.math.HexDir.values();
        if (directionOrdinal < 0 || directionOrdinal >= directionValues.length) directionOrdinal = 0;
        at.petrak.hexcasting.api.casting.math.HexDir startDir = directionValues[directionOrdinal];

        serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
          "§d[3D Grid] Drawn Angle Path: §e" + anglesSignature + " §7(Start: " + startDir.name() + ")"
        ));

        at.petrak.hexcasting.api.casting.math.HexPattern pattern = null;
        try {
          Class<?> signatureClass = Class.forName("at.petrak.hexcasting.api.casting.math.HexSignature");
          java.lang.reflect.Method factoryMethod = signatureClass.getMethod("fromAnglesStringUnchecked", String.class);
          factoryMethod.setAccessible(true);
          Object hexSignatureInstance = factoryMethod.invoke(null, anglesSignature);

          if (hexSignatureInstance != null) {
            Class<?> patternClass = Class.forName("at.petrak.hexcasting.api.casting.math.HexPattern");
            java.lang.reflect.Constructor<?> patternCtor = patternClass.getConstructor(at.petrak.hexcasting.api.casting.math.HexDir.class, signatureClass);
            patternCtor.setAccessible(true);
            pattern = (at.petrak.hexcasting.api.casting.math.HexPattern) patternCtor.newInstance(startDir, hexSignatureInstance);
          }
        } catch (Throwable fatalAllocationError) {
          fatalAllocationError.printStackTrace();
        }

        if (pattern == null) {
          serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[3D Grid Error] Critical Allocation Failure: HexPattern initialization blocked."));
          return;
        }

        at.petrak.hexcasting.api.casting.eval.vm.CastingVM liveCastingVM = 
        at.petrak.hexcasting.xplat.IXplatAbstractions.INSTANCE.getStaffcastVM(serverPlayer, net.minecraft.world.InteractionHand.MAIN_HAND);

        if (liveCastingVM != null) {
          try {
            at.petrak.hexcasting.api.casting.iota.PatternIota patternIotaInstance = new at.petrak.hexcasting.api.casting.iota.PatternIota(pattern);
            liveCastingVM.queueExecuteAndWrapIota(patternIotaInstance, serverPlayer.serverLevel());
            at.petrak.hexcasting.xplat.IXplatAbstractions.INSTANCE.setStaffcastImage(serverPlayer, liveCastingVM.getImage());

            at.petrak.hexcasting.api.casting.eval.vm.CastingImage updatedImageSnapshot = liveCastingVM.getImage();
            if (updatedImageSnapshot != null) {
              java.util.List<at.petrak.hexcasting.api.casting.iota.Iota> activeIotaStack = updatedImageSnapshot.getStack();
              java.util.List<String> synchronizedStackList = new java.util.ArrayList<>();
              
              if (activeIotaStack != null && !activeIotaStack.isEmpty()) {
                serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[3D Grid Stack Output]:"));
                int stackIndexValue = 0;

                for (at.petrak.hexcasting.api.casting.iota.Iota activeItemIota : activeIotaStack) {
                  if (activeItemIota == null) continue;

                  net.minecraft.network.chat.Component localizedDisplayComponent = activeItemIota.display();
                  String readableIotaText = (localizedDisplayComponent != null) ? localizedDisplayComponent.getString() : activeItemIota.toString();

                  serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal("  §e[" + stackIndexValue + "] §f" + readableIotaText));
                  
                  synchronizedStackList.add(readableIotaText);
                  stackIndexValue++;
                }
              }

              // TRANSMIT SECURELY: Broadcast the list using our dedicated client-bound update payload channel!
              net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                serverPlayer,
                new com.genesiz.hexacreatecore.dialogue.network.LiveStackUpdatePayload(synchronizedStackList)
              );
            }
          } catch (Exception executionProcessingException) {
            executionProcessingException.printStackTrace();
          }
        }
      } catch (Exception serverException) {
        serverException.printStackTrace();
      }
    });

    DialogueNetworking.registerAction("hexacreatecore:clear_3d_stack", (serverPlayer, proxyEntity) -> {
      at.petrak.hexcasting.xplat.IXplatAbstractions.INSTANCE.clearCastingData(serverPlayer);
      at.petrak.hexcasting.xplat.IXplatAbstractions.INSTANCE.setPatterns(serverPlayer, java.util.List.of());
      at.petrak.hexcasting.xplat.IXplatAbstractions.INSTANCE.setStaffcastImage(
        serverPlayer,
        new at.petrak.hexcasting.api.casting.eval.vm.CastingImage()
      );

      // Broadcast HexCasting's native spiral-clear packet to remove orbiting runes on all clients
      try {
        Class<?> packetClass = Class.forName("at.petrak.hexcasting.common.msgs.MsgClearSpiralPatternsS2C");
        java.lang.reflect.Constructor<?> ctor = packetClass.getConstructor(java.util.UUID.class);
        Object msg = ctor.newInstance(serverPlayer.getUUID());
        at.petrak.hexcasting.xplat.IXplatAbstractions.INSTANCE.sendPacketToPlayer(
          serverPlayer,
        (net.minecraft.network.protocol.common.custom.CustomPacketPayload) msg
        );
        at.petrak.hexcasting.xplat.IXplatAbstractions.INSTANCE.sendPacketTracking(
          serverPlayer,
          (net.minecraft.network.protocol.common.custom.CustomPacketPayload) msg
        );
      } catch (Throwable t) {
        t.printStackTrace();
      }
      net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
        serverPlayer,
        new com.genesiz.hexacreatecore.dialogue.network.LiveStackUpdatePayload(java.util.List.of())
      );
    });
  }

  public HexaCreateCore(IEventBus modEventBus, ModContainer modContainer) {
    modEventBus.addListener(this::addCreative);
    modEventBus.addListener(this::gatherData);

    ModCreativeModeTabs.register(modEventBus);
    HexaCreateCoreItems.register(modEventBus);
    ModEntities.register(modEventBus);

    if (FMLEnvironment.dist.isClient()) {
      modEventBus.addListener(DialogueReloadListener::onRegisterClientReloadListeners);
      modEventBus.addListener(BlueprintReloadListener::onRegisterClientReloadListeners);
      modEventBus.addListener(com.genesiz.hexacreatecore.animations.AnimationSequenceReloadListener::onRegisterClientReloadListeners);
    }
  }

  private void addCreative(BuildCreativeModeTabContentsEvent event) {
    // Hook for appending items directly to creative tabs dynamically
  }

  private void gatherData(final GatherDataEvent event) {
    event.getGenerator().addProvider(
      event.includeClient(), 
      new ModModelProvider(event.getGenerator().getPackOutput(), event.getExistingFileHelper())
    );
  }
}
