package com.genesiz.hexacreatecore.bossrush.dragon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = "hexacreatecore")
public final class DragonArenaController {

  public static final class ArenaState {
    public final Vec3 origin;
    public final boolean generatedCustomPillars;
    public final Set<BlockPos> placedBlocks = new HashSet<>();
    public final List<Vec3> islandCenters = new ArrayList<>();
    public final int[] islandFireTicks = new int[] { 0, 0, 0 };
    public boolean blackholeActive = false;

    public ArenaState(Vec3 origin, boolean customPillars) {
      this.origin = origin;
      this.generatedCustomPillars = customPillars;
    }
  }

  private static final Map<BlockPos, ArenaState> ARENAS = new HashMap<>();

  private DragonArenaController() {}

  public static ArenaState getArena(Vec3 origin) {
    return ARENAS.get(BlockPos.containing(origin));
  }

  public static Map<BlockPos, ArenaState> getAllArenas() {
    return ARENAS;
  }

  public static void setupBossRushArena(ServerLevel level, Vec3 origin) {
    BlockPos key = BlockPos.containing(origin);
    ArenaState state = new ArenaState(origin, true);
    ARENAS.put(key, state);

    // Spawn 8 obsidian pillars + End Crystals in a 28-block ring around Boss Rush origin
    int pillarCount = 8;
    for (int i = 0; pillarCount > i; i++) {
      double angle = (2.0D * Math.PI * i) / pillarCount;
      int px = (int) Math.round(origin.x + Math.cos(angle) * 28.0D);
      int pz = (int) Math.round(origin.z + Math.sin(angle) * 28.0D);
      int height = 14 + (i % 3) * 4;
      int baseY = (int) Math.floor(origin.y);

      for (int y = 0; height > y; y++) {
        for (int dx = -1; 1 >= dx; dx++) {
          for (int dz = -1; 1 >= dz; dz++) {
            BlockPos pos = new BlockPos(px + dx, baseY + y, pz + dz);
            if (level.getBlockState(pos).isAir()) {
              level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
              state.placedBlocks.add(pos);
            }
          }
        }
      }

      BlockPos capPos = new BlockPos(px, baseY + height, pz);
      level.setBlockAndUpdate(capPos, Blocks.BEDROCK.defaultBlockState());
      state.placedBlocks.add(capPos);

      EndCrystal crystal = EntityType.END_CRYSTAL.create(level);
      if (crystal != null) {
        crystal.moveTo(px + 0.5D, baseY + height + 1.0D, pz + 0.5D, 0.0f, 0.0f);
        crystal.setShowBottom(false);
        level.addFreshEntity(crystal);
      }
    }
  }

  public static void triggerPhase3IslandTransition(ServerLevel level, Vec3 origin) {
    ArenaState state = ARENAS.computeIfAbsent(BlockPos.containing(origin), k -> new ArenaState(origin, false));
    state.blackholeActive = true;
    state.islandCenters.clear();

    level.playSound(null, BlockPos.containing(origin), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0f, 0.5f);
    level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, origin.x, origin.y + 2.0D, origin.z, 8, 12.0D, 2.0D, 12.0D, 0.0D);

    // Build 3 floating End Stone islands +100 blocks above origin in an equilateral triangle (r = 36)
    double skyY = origin.y + 100.0D;
    for (int i = 0; 3 > i; i++) {
      double angle = (2.0D * Math.PI * i) / 3.0D;
      double ix = origin.x + Math.cos(angle) * 36.0D;
      double iz = origin.z + Math.sin(angle) * 36.0D;
      Vec3 center = new Vec3(ix, skyY, iz);
      state.islandCenters.add(center);
      buildSkyIslandDisk(level, state, BlockPos.containing(center), 11);
    }

    // Immediately ignite island 0 for 2 minutes (2400 ticks)
    state.islandFireTicks[0] = 2400;
  }

  private static void buildSkyIslandDisk(ServerLevel level, ArenaState state, BlockPos center, int radius) {
    for (int dx = -radius; radius >= dx; dx++) {
      for (int dz = -radius; radius >= dz; dz++) {
        double dist = Math.hypot(dx, dz);
        if (radius >= dist) {
          int depth = Math.max(1, (int) Math.round((1.0D - (dist / radius)) * 5.0D));
          for (int dy = 0; depth > dy; dy++) {
            BlockPos pos = center.offset(dx, -dy, dz);
            if (level.getBlockState(pos).isAir()) {
              level.setBlockAndUpdate(pos, Blocks.END_STONE.defaultBlockState());
              state.placedBlocks.add(pos);
            }
          }
        }
      }
    }
  }

  public static void igniteNextSkyIsland(ServerLevel level, Vec3 origin) {
    ArenaState state = getArena(origin);
    if (state == null || state.islandCenters.isEmpty()) return;
    for (int i = 0; 3 > i; i++) {
      if (0 >= state.islandFireTicks[i]) {
        state.islandFireTicks[i] = 2400;
        Vec3 c = state.islandCenters.get(i);
        level.playSound(null, BlockPos.containing(c), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 3.0f, 0.6f);
        break;
      }
    }
  }

  public static void extinguishAllIslandFires(ServerLevel level, Vec3 origin) {
    ArenaState state = getArena(origin);
    if (state != null) {
      state.islandFireTicks[0] = 0;
      state.islandFireTicks[1] = 0;
      state.islandFireTicks[2] = 0;
    }
  }

  public static Vec3 getUnburnedIslandTop(Vec3 origin) {
    ArenaState state = getArena(origin);
    if (state == null || state.islandCenters.isEmpty()) return origin.add(0.0D, 101.0D, 0.0D);
    for (int i = 0; 3 > i; i++) {
      if (0 >= state.islandFireTicks[i]) return state.islandCenters.get(i).add(0.0D, 1.0D, 0.0D);
    }
    return state.islandCenters.get(0).add(0.0D, 1.0D, 0.0D);
  }

  public static boolean isPlayerNearIslandEdge(Vec3 origin, Vec3 playerPos) {
    ArenaState state = getArena(origin);
    if (state == null) return false;
    for (Vec3 c : state.islandCenters) {
      double d = Math.hypot(playerPos.x - c.x, playerPos.z - c.z);
      if (d >= 7.5D && 12.5D >= d) return true;
    }
    return false;
  }

  public static void rainSkyfallFireballs(ServerLevel level, EnderDragon dragon, Vec3 origin) {
    DragonArenaHazards.rainSkyfallFireballs(level, dragon, getArena(origin));
  }

  public static void detonatePerimeterStars(ServerLevel level, EnderDragon dragon, Vec3 origin) {
    DragonArenaHazards.detonatePerimeterStars(level, dragon, getArena(origin));
  }

  public static void cleanupTemporaryArena(ServerLevel level, Vec3 origin) {
    ArenaState state = ARENAS.remove(BlockPos.containing(origin));
    if (state == null) return;
    for (BlockPos pos : state.placedBlocks) {
      if (!level.getBlockState(pos).isAir()) {
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
      }
    }
    for (EndCrystal c : level.getEntitiesOfClass(EndCrystal.class, AABB.ofSize(origin, 120.0D, 90.0D, 120.0D))) {
      c.discard();
    }
  }

  @SubscribeEvent
  public static void onServerTick(ServerTickEvent.Post event) {
    for (ServerLevel level : event.getServer().getAllLevels()) {
      for (ArenaState state : ARENAS.values()) {
        DragonArenaHazards.tickHazards(level, state);
      }
    }
  }
}
