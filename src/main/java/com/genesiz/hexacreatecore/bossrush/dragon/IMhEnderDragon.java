package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.bossrush.dragon.DragonAnimState.ClientAnimData;
import com.genesiz.hexacreatecore.bossrush.dragon.DragonAnimState.DragonAction;
import com.genesiz.hexacreatecore.bossrush.dragon.DragonAnimState.DragonPoseMode;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;

public interface IMhEnderDragon extends GeoEntity {

  boolean hexacreatecore$isMhDragon();
  void hexacreatecore$setMhDragon(boolean enabled, Vec3 arenaOrigin);
  Vec3 hexacreatecore$getArenaOrigin();

  DragonPoseMode hexacreatecore$getPoseMode();
  void hexacreatecore$setPoseMode(DragonPoseMode mode);

  DragonAction hexacreatecore$getActiveAction();
  int hexacreatecore$getActionTick();
  int hexacreatecore$getActionDuration();
  void hexacreatecore$triggerAction(DragonAction action, int durationTicks);

  boolean hexacreatecore$hasBarrierShield();
  void hexacreatecore$setBarrierShield(boolean shielded);

  boolean hexacreatecore$isSupercritical();
  void hexacreatecore$setSupercritical(boolean supercritical);

  int hexacreatecore$getFightPhase();
  void hexacreatecore$setFightPhase(int phase);

  ClientAnimData hexacreatecore$getClientAnimData();
  void hexacreatecore$setActionTick(int tick);

}
