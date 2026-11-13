package com.genesiz.hexacreatecore.bossrush;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.bossrush.data.BossRushData.CustomEffectScript;
import com.genesiz.hexacreatecore.bossrush.data.BossRushData.PhaseConfig;
import com.genesiz.hexacreatecore.bossrush.data.BossRushData.SequenceConfig;
import com.genesiz.hexacreatecore.bossrush.data.BossRushReloadListener;
import com.genesiz.hexacreatecore.bossrush.network.BossRushSyncPayload;
import com.genesiz.hexacreatecore.decorators.annotations.DialogueAction;
import com.genesiz.hexacreatecore.difficulty.DifficultyManager;
import com.genesiz.hexacreatecore.difficulty.DifficultySavedData;
import com.genesiz.hexacreatecore.difficulty.data.DifficultyData.DifficultyTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = HexaCreateCore.MODID)
public final class BossRushManager {

  private static final class RushSession {
    final UUID playerUuid;
    final ResourceLocation dimension;
    final Vec3 originPos;
    final SequenceConfig sequence;
    int phaseIndex = 0;
    int bossIndexInPhase = 0;
    UUID currentBossUuid = null;
    final Set<UUID> allSpawnedEntities = new HashSet<>();

    RushSession(ServerPlayer player, SequenceConfig sequence) {
      this.playerUuid = player.getUUID();
      this.dimension = player.serverLevel().dimension().location();
      this.originPos = player.position();
      this.sequence = sequence;
    }
  }

  private static final Map<UUID, RushSession> ACTIVE_SESSIONS = new HashMap<>();

  private BossRushManager() {}

  public static boolean isPlayerInRush(UUID playerUuid) {
    return ACTIVE_SESSIONS.containsKey(playerUuid);
  }

  @DialogueAction(value = "hexacreatecore:start_boss_rush", selfTargeted = true)
  public static void startBossRushAction(ServerPlayer player, Entity proxy) {
    startBossRush(player);
  }

  public static boolean startBossRush(ServerPlayer player) {
    if (ACTIVE_SESSIONS.containsKey(player.getUUID())) {
      player.sendSystemMessage(Component.literal("§cA Boss Rush is already active!"));
      return false;
    }

    SequenceConfig seq = BossRushReloadListener.getSequence();
    if (seq == null || seq.phases().isEmpty()) {
      player.sendSystemMessage(Component.literal("§cNo Boss Rush sequence.json loaded!"));
      return false;
    }

    RushSession session = new RushSession(player, seq);
    ACTIVE_SESSIONS.put(player.getUUID(), session);

    beginPhase(player, session, 0, seq.onTriggerItem(), seq.onTriggerSound());
    return true;
  }


  private static void beginPhase(ServerPlayer player, RushSession session, int index, String totemItemId, String totemSoundId) {
    session.phaseIndex = index;
    session.bossIndexInPhase = 0;
    PhaseConfig phase = session.sequence.phases().get(index);

    playOriginEffect(player.serverLevel(), session.originPos, phase.effects().onStart());

    PacketDistributor.sendToPlayer(player, new BossRushSyncPayload(
      true,
      phase.name(),
      phase.description(),
      phase.music(),
      phase.musicTitle(),
      totemItemId,
      totemSoundId,
      "",
      -1,
      BossRushReloadListener.getRawHealthBarsJson()
    ));

    spawnNextBossInPhase(player, session);
  }

  private static void spawnNextBossInPhase(ServerPlayer player, RushSession session) {
    PhaseConfig phase = session.sequence.phases().get(session.phaseIndex);

    if (session.bossIndexInPhase >= phase.bosses().size()) {
      playOriginEffect(player.serverLevel(), session.originPos, phase.effects().onEnd());

      int nextPhase = session.phaseIndex + 1;
      if (session.sequence.phases().size() > nextPhase) {
        beginPhase(player, session, nextPhase, phase.onCompletionItem(), phase.onCompletionSound());
      } else {
        ACTIVE_SESSIONS.remove(player.getUUID());
        if (DifficultySavedData.get(player.serverLevel()).getActiveTier() == DifficultyTier.ABSOLUTION) {
          DifficultyManager.unlockGenesisForServer(player.serverLevel());
        }
        PacketDistributor.sendToPlayer(player, new BossRushSyncPayload(
          false,
          "",
          "",
          session.sequence.endingMusic(),
          session.sequence.endingMusicTitle(),
          phase.onCompletionItem(),
          phase.onCompletionSound(),
          "§6Boss Rush Completed!",
          -1,
          "{}"
        ));
      }
      return;
    }

    String rawEntityId = phase.bosses().get(session.bossIndexInPhase);
    ResourceLocation entityLoc = ResourceLocation.tryParse(rawEntityId);
    if (entityLoc == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(entityLoc)) {
      HexaCreateCore.LOGGER.warn("Boss Rush skipped unknown entity ID: {}", rawEntityId);
      session.bossIndexInPhase++;
      spawnNextBossInPhase(player, session);
      return;
    }

    var entityType = BuiltInRegistries.ENTITY_TYPE.get(entityLoc);
    ServerLevel level = player.serverLevel();

    Vec3 look = player.getLookAngle();
    double spawnX = session.originPos.x + (look.x * 6.0D);
    double spawnZ = session.originPos.z + (look.z * 6.0D);
    double spawnY = session.originPos.y + 1.0D;

    BlockPos spawnPos = BlockPos.containing(spawnX, spawnY, spawnZ);
    Entity spawned = entityType.spawn(level, spawnPos, MobSpawnType.EVENT);

    if (spawned != null) {
      if (spawned instanceof EnderDragon dragon) {
        dragon.setPos(spawnX, spawnY + 8.0D, spawnZ);
        dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
      }
      session.currentBossUuid = spawned.getUUID();
      session.allSpawnedEntities.add(spawned.getUUID());

      PacketDistributor.sendToPlayer(player, new BossRushSyncPayload(
        true,
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        spawned.getId(),
        BossRushReloadListener.getRawHealthBarsJson()
      ));
    }
  }

  public static void failAndCleanupSession(ServerPlayer player, RushSession session, String reasonBanner) {
    ACTIVE_SESSIONS.remove(session.playerUuid);
    ServerLevel level = player.serverLevel();

    for (UUID entityUuid : session.allSpawnedEntities) {
      Entity e = level.getEntity(entityUuid);
      if (e != null && e.isAlive()) {
        e.discard();
      }
    }
    session.allSpawnedEntities.clear();

      PacketDistributor.sendToPlayer(player, new BossRushSyncPayload(
      false, "", "", "", "", "", "", reasonBanner, -1, "{}"
    ));
  }

  @SubscribeEvent
  public static void onServerTick(ServerTickEvent.Post event) {
    if (ACTIVE_SESSIONS.isEmpty()) return;

    for (RushSession session : new HashSet<>(ACTIVE_SESSIONS.values())) {
      ServerPlayer player = event.getServer().getPlayerList().getPlayer(session.playerUuid);
      if (player == null || !player.isAlive()) {
        if (player != null) {
          failAndCleanupSession(player, session, "§cBoss Rush Failed");
        } else {
          ACTIVE_SESSIONS.remove(session.playerUuid);
        }
        continue;
      }

      ResourceLocation currentDim = player.serverLevel().dimension().location();
      double maxDist = session.sequence.areaOfEffect();
      if (!currentDim.equals(session.dimension) || player.position().distanceToSqr(session.originPos) > (maxDist * maxDist)) {
        failAndCleanupSession(player, session, "§cLeft Boss Rush Arena");
      }
    }
  }

  @SubscribeEvent
  public static void onBossDeath(LivingDeathEvent event) {
    if (ACTIVE_SESSIONS.isEmpty() || event.getEntity().level().isClientSide()) return;
    UUID deadUuid = event.getEntity().getUUID();

    for (RushSession session : new HashSet<>(ACTIVE_SESSIONS.values())) {
      if (deadUuid.equals(session.currentBossUuid)) {
        ServerPlayer player = event.getEntity().getServer().getPlayerList().getPlayer(session.playerUuid);
        if (player != null && player.isAlive()) {
          session.bossIndexInPhase++;
          spawnNextBossInPhase(player, session);
        }
        break;
      }
    }
  }

  @SubscribeEvent
  public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
    if (event.getEntity() instanceof ServerPlayer sp) {
      RushSession session = ACTIVE_SESSIONS.get(sp.getUUID());
      if (session != null) {
        failAndCleanupSession(sp, session, "");
      }
    }
  }

  private static void playOriginEffect(ServerLevel level, Vec3 origin, String effectId) {
    if (effectId == null || effectId.isEmpty()) return;

    CustomEffectScript script = BossRushReloadListener.getCustomEffect(effectId);
    if (script != null) {
      if (script.lightning()) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
          bolt.moveTo(origin.x, origin.y, origin.z);
          bolt.setVisualOnly(true);
          level.addFreshEntity(bolt);
        }
      }
      if (!script.sound().isEmpty()) {
        ResourceLocation soundLoc = ResourceLocation.tryParse(script.sound());
        if (soundLoc != null && BuiltInRegistries.SOUND_EVENT.containsKey(soundLoc)) {
          SoundEvent se = BuiltInRegistries.SOUND_EVENT.get(soundLoc);
          level.playSound(null, BlockPos.containing(origin), se, SoundSource.AMBIENT, 1.5f, 1.0f);
        }
      }
      spawnParticleRing(level, origin, script.particle(), script.count(), script.radius());
    } else {
      spawnParticleRing(level, origin, effectId, 48, 3.0D);
    }
  }

  private static void spawnParticleRing(ServerLevel level, Vec3 origin, String particleId, int count, double radius) {
    ParticleOptions options = ParticleTypes.END_ROD;
    ResourceLocation loc = ResourceLocation.tryParse(particleId);
    if (loc != null && BuiltInRegistries.PARTICLE_TYPE.containsKey(loc)) {
      if (BuiltInRegistries.PARTICLE_TYPE.get(loc) instanceof ParticleOptions directOptions) {
        options = directOptions;
      }
    }

    for (int i = 0; count > i; i++) {
      double angle = (2.0D * Math.PI * i) / count;
      double px = origin.x + Math.cos(angle) * radius;
      double pz = origin.z + Math.sin(angle) * radius;
      level.sendParticles(options, px, origin.y + 0.5D, pz, 2, 0.1D, 0.4D, 0.1D, 0.02D);
    }
  }

  @SubscribeEvent
  public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer sp) {
      PacketDistributor.sendToPlayer(sp, new BossRushSyncPayload(
        false, "", "", "", "", "", "", "", -1, BossRushReloadListener.getRawHealthBarsJson()
      ));
    }
  }

  @SubscribeEvent
  public static void onDatapackSync(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
    String json = BossRushReloadListener.getRawHealthBarsJson();
    if (event.getPlayer() != null) {
      PacketDistributor.sendToPlayer(event.getPlayer(), new BossRushSyncPayload(
        isPlayerInRush(event.getPlayer().getUUID()), "", "", "", "", "", "", "", -1, json
      ));
    } else {
      for (ServerPlayer sp : event.getPlayerList().getPlayers()) {
        PacketDistributor.sendToPlayer(sp, new BossRushSyncPayload(
          isPlayerInRush(sp.getUUID()), "", "", "", "", "", "", "", -1, json
        ));
      }
    }
  }
}
