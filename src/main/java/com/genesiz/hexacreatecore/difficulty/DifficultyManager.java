package com.genesiz.hexacreatecore.difficulty;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.decorators.annotations.DifficultyAttackCallback;
import com.genesiz.hexacreatecore.difficulty.data.DifficultyData.*;
import com.genesiz.hexacreatecore.difficulty.data.DifficultyReloadListener;
import com.genesiz.hexacreatecore.difficulty.network.DifficultyPackets;
import com.google.gson.Gson;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = HexaCreateCore.MODID)
public final class DifficultyManager {
  private static final ResourceLocation HP_MOD_ID = ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "difficulty_hp_scale");
  private static final Map<String, AttackCallbackFn> CALLBACKS = new HashMap<>();
  private static final Map<UUID, PlayerCombatState> PLAYER_STATES = new ConcurrentHashMap<>();
  private static boolean callbacksScanned = false;
  private static int genesisHeartbeatTicksRemaining = 0;

  @FunctionalInterface
  public interface AttackCallbackFn {
    void invoke(ServerLevel level, LivingEntity attacker, LivingEntity target, float damage);
  }

  public static final class PlayerCombatState {
    public String activeParryZone = "NONE";
    public long parryExpiresAtMs = 0L;
    public long parryFlowExpiresAtMs = 0L;
    public int riposteCharges = 0;
    public boolean ripostePrimed = false;
  }

  public static PlayerCombatState getState(UUID uuid) {
    return PLAYER_STATES.computeIfAbsent(uuid, k -> new PlayerCombatState());
  }

  public static void scanCallbacksIfNeeded() {
    if (callbacksScanned) return;
    callbacksScanned = true;
    Type targetType = Type.getType(DifficultyAttackCallback.class);
    for (ModFileScanData scanData : ModList.get().getAllScanData()) {
      for (ModFileScanData.AnnotationData ad : scanData.getAnnotations()) {
        if (!targetType.equals(ad.annotationType())) continue;
        try {
          Class<?> owner = Class.forName(ad.clazz().getClassName());
          String methodName = ad.memberName().substring(0, ad.memberName().indexOf('('));
          Method m = owner.getDeclaredMethod(methodName, ServerLevel.class, LivingEntity.class, LivingEntity.class, float.class);
          m.setAccessible(true);
          String id = (String) ad.annotationData().get("value");
          CALLBACKS.put(id, (lvl, atk, tgt, dmg) -> {
            try { m.invoke(null, lvl, atk, tgt, dmg); } catch (Exception e) { HexaCreateCore.LOGGER.error("Callback error {}", id, e); }
          });
        } catch (Exception e) {
          HexaCreateCore.LOGGER.error("Failed registering @DifficultyAttackCallback", e);
        }
      }
    }
  }

  public static void handleClientAction(ServerPlayer player, DifficultyPackets.ActionPayload payload) {
    DifficultySavedData data = DifficultySavedData.get(player.serverLevel());
    PlayerCombatState state = getState(player.getUUID());
    long now = System.currentTimeMillis();

    switch (payload.actionType()) {
      case "SELECT_TIER" -> {
        DifficultyTier chosen = DifficultyTier.fromId(payload.stringParam());
        if (chosen == data.getActiveTier()) {
          return;
        }
        if (chosen == DifficultyTier.GENESIS && !data.isGenesisUnlocked()) {
          return;
        }
        ServerLevel sLevel = player.serverLevel();
        data.setActiveTier(chosen);
        data.setDirty();
        for (ServerPlayer p : sLevel.getServer().getPlayerList().getPlayers()) {
          syncToPlayer(p);
        }

        if (chosen == DifficultyTier.ETHEREAL) {
          genesisHeartbeatTicksRemaining = 0;
          sLevel.playSound(null, player.blockPosition(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.MASTER, 1.0f, 0.5f);
        } else if (chosen == DifficultyTier.ABSOLUTION) {
          genesisHeartbeatTicksRemaining = 0;
          sLevel.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 1.0f, 0.7f);
        } else if (chosen == DifficultyTier.GENESIS) {
          sLevel.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_DEATH, SoundSource.MASTER, 0.8f, 0.5f);
          genesisHeartbeatTicksRemaining = 500; // 25 seconds at 20 TPS
        } else {
          genesisHeartbeatTicksRemaining = 0;
        }
      }

      case "PARRY_WINDOW" -> {
        if (data.getActiveTier() == DifficultyTier.NORMAL) return;
        state.activeParryZone = payload.stringParam();
        state.parryExpiresAtMs = now + Math.max(80L, payload.intParam());
      }
      case "UNLEASH_RIPOSTE" -> {
        DifficultyTier tier = data.getActiveTier();
        if (tier == DifficultyTier.NORMAL || tier.riposteMaxCharges == 0) return;
        if (state.riposteCharges >= tier.riposteMaxCharges && !state.ripostePrimed) {
          state.ripostePrimed = true;
          player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.8f, 1.6f);
          syncToPlayer(player);
        }
      }
      case "BREAK_FLOW" -> {
        state.parryFlowExpiresAtMs = 0L;
        state.parryExpiresAtMs = 0L;
      }
    }
  }

  @SubscribeEvent
  public static void onEntityJoin(EntityJoinLevelEvent event) {
    if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) return;
    if (mob.getPersistentData().getBoolean("hexacreate_scaled")) return;

    DifficultyTier tier = DifficultySavedData.get((ServerLevel) event.getLevel()).getActiveTier();
    if (tier == DifficultyTier.NORMAL) return;

    DifficultyProfile profile = DifficultyReloadListener.getProfile(tier.id);
    String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
    EntityOverride ov = profile.entityOverrides().get(typeId);
    if (ov == null && !(mob instanceof Enemy)) return;

    double hpMult = (ov != null) ? ov.hpMultiplier() : profile.globalHpMultiplier();
    if (hpMult != 1.0D && mob.getAttribute(Attributes.MAX_HEALTH) != null) {
      var attr = mob.getAttribute(Attributes.MAX_HEALTH);
      attr.removeModifier(HP_MOD_ID);
      attr.addPermanentModifier(new AttributeModifier(HP_MOD_ID, hpMult - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
      mob.setHealth(mob.getMaxHealth());
    }

    applyEffectList(mob, profile.globalSpawnBuffs());
    if (ov != null) applyEffectList(mob, ov.spawnBuffs());
    mob.getPersistentData().putBoolean("hexacreate_scaled", true);
  }

  @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
  public static void onIncomingDamage(LivingIncomingDamageEvent event) {
    if (event.getEntity().level().isClientSide()) return;
    scanCallbacksIfNeeded();
    ServerLevel level = (ServerLevel) event.getEntity().level();
    DifficultyTier tier = DifficultySavedData.get(level).getActiveTier();
    if (tier == DifficultyTier.NORMAL) return;

    // 1. Player attacking an entity with Primed Riposte (flat 5% Max HP true damage)
    if (event.getSource().getEntity() instanceof ServerPlayer attacker && event.getEntity() != attacker) {
      PlayerCombatState atkState = getState(attacker.getUUID());
      if (atkState.ripostePrimed) {
        atkState.ripostePrimed = false;
        atkState.riposteCharges = 0;
        LivingEntity target = event.getEntity();
        float trueDamage = Math.max(1.0f, target.getMaxHealth() * 0.05f);
        float newHp = Math.max(0.0f, target.getHealth() - trueDamage);
        target.setHealth(newHp);
        if (0.0f >= newHp) {
          target.die(attacker.damageSources().playerAttack(attacker));
        }
        level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.9f, 1.5f);
        syncToPlayer(attacker);
      }
    }

    // 2. Player receiving ANY damage (melee, projectiles, explosions, fall, void, or even /kill)
    if (event.getEntity() instanceof ServerPlayer player) {
      PlayerCombatState state = getState(player.getUUID());
      long now = System.currentTimeMillis();

      if (event.getSource().getEntity() instanceof LivingEntity mobAttacker) {
        DifficultyProfile profile = DifficultyReloadListener.getProfile(tier.id);
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(mobAttacker.getType()).toString();
        EntityOverride ov = profile.entityOverrides().get(typeId);
        double dmgMult = (ov != null) ? ov.damageMultiplier() : profile.globalDamageMultiplier();
        event.setAmount((float) (event.getAmount() * dmgMult));

        for (String cb : profile.globalAttackCallbacks()) {
          if (CALLBACKS.containsKey(cb)) CALLBACKS.get(cb).invoke(level, mobAttacker, player, event.getAmount());
        }
        if (ov != null) {
          for (String cb : ov.attackCallbacks()) {
            if (CALLBACKS.containsKey(cb)) CALLBACKS.get(cb).invoke(level, mobAttacker, player, event.getAmount());
          }
        }
      }

      if (state.parryExpiresAtMs >= now && !"FAIL".equals(state.activeParryZone) && !"NONE".equals(state.activeParryZone)) {
        String zone = state.activeParryZone;
        state.parryExpiresAtMs = 0L;
        if ("PURE".equals(zone)) {
          event.setCanceled(true);
          if (event.getSource().getEntity() instanceof LivingEntity atk) {
            atk.hurt(player.damageSources().thorns(player), event.getAmount());
          }
          rewardParrySuccess(player, state, tier, 1.8f);
          return;
        } else if ("PARRY".equals(zone) || "FLOW".equals(zone)) {
          event.setCanceled(true);
          player.invulnerableTime = 2;
          rewardParrySuccess(player, state, tier, 1.4f);
          return;
        } else if ("SHALLOW".equals(zone)) {
          event.setAmount(event.getAmount() * 0.25f);
          level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 0.9f);
          return;
        }
      }

      // Unparried hit: lose 1/5 of max gauge and break Parry Flow
      if (!isPassiveDoTDamage(event.getSource())) {
        int penalty = Math.max(1, tier.riposteMaxCharges / 5);
        state.riposteCharges = Math.max(0, state.riposteCharges - penalty);
        state.parryFlowExpiresAtMs = 0L;
        syncToPlayer(player);
      }

      if (event.getSource().getEntity() instanceof LivingEntity mobAttacker) {
        DifficultyProfile profile = DifficultyReloadListener.getProfile(tier.id);
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(mobAttacker.getType()).toString();
        EntityOverride ov = profile.entityOverrides().get(typeId);
        applyEffectList(player, profile.globalOnHitDebuffs());
        if (ov != null) applyEffectList(player, ov.onHitDebuffs());
      }
    }
  }

  private static void rewardParrySuccess(ServerPlayer player, PlayerCombatState state, DifficultyTier tier, float pitch) {
    state.riposteCharges = Math.min(tier.riposteMaxCharges, state.riposteCharges + 1);
    state.parryFlowExpiresAtMs = System.currentTimeMillis() + 3000L;
    syncToPlayer(player);
  }

  private static void applyEffectList(LivingEntity target, List<MobEffectEntry> effects) {
    for (MobEffectEntry entry : effects) {
      if (target.getRandom().nextFloat() > entry.chance()) continue;
      ResourceLocation loc = ResourceLocation.tryParse(entry.effectId());
      if (loc != null && BuiltInRegistries.MOB_EFFECT.containsKey(loc)) {
        var holder = BuiltInRegistries.MOB_EFFECT.getHolder(loc);
        holder.ifPresent(h -> target.addEffect(new MobEffectInstance(h, entry.durationTicks(), entry.amplifier())));
      }
    }
  }

  public static void unlockGenesisForServer(ServerLevel level) {
    DifficultySavedData data = DifficultySavedData.get(level);
    if (!data.isGenesisUnlocked()) {
      data.unlockGenesis();
      for (ServerPlayer sp : level.getServer().getPlayerList().getPlayers()) {
        syncToPlayer(sp);
        sp.sendSystemMessage(Component.literal("§4§l[SECRET UNLOCKED] §cGenesis Difficulty is now available!"));
      }
    }
  }

  public static void syncToPlayer(ServerPlayer player) {
    DifficultySavedData data = DifficultySavedData.get(player.serverLevel());
    PlayerCombatState state = getState(player.getUUID());
    long flowLeft = Math.max(0L, state.parryFlowExpiresAtMs - System.currentTimeMillis());

    Map<String, List<String>> descMap = new HashMap<>();
    for (DifficultyTier t : DifficultyTier.values()) {
      if (t == DifficultyTier.NORMAL) continue;
      descMap.put(t.id, DifficultyReloadListener.getProfile(t.id).description());
    }

    PacketDistributor.sendToPlayer(player, new DifficultyPackets.SyncPayload(
      data.getActiveTier().id, data.isGenesisUnlocked(), state.riposteCharges,
      state.ripostePrimed, flowLeft, new Gson().toJson(descMap),
      DifficultyReloadListener.getRawGaugeBarsJson(), DifficultyReloadListener.getRawParrySoundsJson()
    ));
  }

  private static boolean isPassiveDoTDamage(net.minecraft.world.damagesource.DamageSource source) {
    if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) return true;
    if (source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING)) return true;
    if (source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)) return true;

    // Any status-effect tick (vanilla Poison/Wither or ANY modded DoT effect) has no attacker and no projectile
    if (source.getEntity() == null && source.getDirectEntity() == null) {
      if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) return false;
      if (source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING)) return false;
      if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) return false;
      return true;
    }

    // Some mods attribute DoTs to the caster with no direct projectile while a harmful effect is active
    if (source.getDirectEntity() == null && source.is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO)) {
      return true;
    }
    return false;
  }

  @SubscribeEvent
  public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    if (genesisHeartbeatTicksRemaining > 0) {
      int elapsed = 500 - genesisHeartbeatTicksRemaining;
      int beatOffset = elapsed % 20;
      if (beatOffset == 0 || beatOffset == 4) {
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
          p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.MASTER, 2.0f, 0.8f);
        }
      }
      genesisHeartbeatTicksRemaining--;
    }
  }


  @SubscribeEvent
  public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer sp) syncToPlayer(sp);
  }

  @SubscribeEvent
  public static void onRegisterCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("hexadifficulty")
      .then(Commands.argument("tier", StringArgumentType.word())
        .executes(ctx -> {
          ServerPlayer sp = ctx.getSource().getPlayerOrException();
          String arg = StringArgumentType.getString(ctx, "tier");
          handleClientAction(sp, new DifficultyPackets.ActionPayload("SELECT_TIER", arg, 0));
          return 1;
        })
      )
    );
  }
}

