package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.bossrush.dragon.DragonArenaController.ArenaState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DragonArenaHazards {

  private DragonArenaHazards() {}

  public static void tickHazards(ServerLevel level, ArenaState state) {
    // 1. Event Horizon Blackhole insta-kill zone at the lower arena
    if (state.blackholeActive) {
      Vec3 bh = state.origin;
      if (level.getGameTime() % 4 == 0) {
        level.sendParticles(ParticleTypes.PORTAL, bh.x, bh.y + 1.0D, bh.z, 40, 28.0D, 2.5D, 28.0D, 0.2D);
      }
      AABB blackholeBox = AABB.ofSize(bh, 96.0D, 28.0D, 96.0D);
      for (Player p : level.getEntitiesOfClass(Player.class, blackholeBox)) {
        if (p.isAlive() && !p.isCreative() && !p.isSpectator()) {
          p.hurt(level.damageSources().fellOutOfWorld(), 9999.0f);
        }
      }
    }

    // 2. Special 2-minute Void-Gold fire on ignited sky islands
    for (int i = 0; state.islandCenters.size() > i; i++) {
      if (state.islandFireTicks[i] > 0) {
        state.islandFireTicks[i]--;
        Vec3 island = state.islandCenters.get(i);
        if (level.getGameTime() % 5 == 0) {
          level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, island.x, island.y + 1.2D, island.z, 25, 7.5D, 0.5D, 7.5D, 0.02D);
          level.sendParticles(ParticleTypes.FLAME, island.x, island.y + 1.2D, island.z, 20, 7.5D, 0.5D, 7.5D, 0.02D);
        }
        if (level.getGameTime() % 10 == 0) {
          for (Player p : level.getEntitiesOfClass(Player.class, AABB.ofSize(island.add(0.0D, 2.0D, 0.0D), 22.0D, 5.0D, 22.0D))) {
            if (p.isAlive() && !p.isCreative() && !p.isSpectator()) {
              p.igniteForSeconds(4.0f);
              p.hurt(level.damageSources().inFire(), 7.0f);
            }
          }
        }
      }
    }
  }

  public static void rainSkyfallFireballs(ServerLevel level, EnderDragon dragon, ArenaState state) {
    if (state == null || state.islandCenters.isEmpty()) return;
    for (Vec3 island : state.islandCenters) {
      double rx = island.x + (level.random.nextDouble() - 0.5D) * 16.0D;
      double rz = island.z + (level.random.nextDouble() - 0.5D) * 16.0D;
      DragonFireball fb = new DragonFireball(level, dragon, new Vec3(0.0D, -1.25D, 0.0D));
      fb.setPos(rx, island.y + 24.0D, rz);
      level.addFreshEntity(fb);
    }
  }

  public static void detonatePerimeterStars(ServerLevel level, EnderDragon dragon, ArenaState state) {
    if (state == null || state.islandCenters.isEmpty()) return;
    Vec3 skyCenter = state.origin.add(0.0D, 104.0D, 0.0D);

    // 6 perimeter stars orbiting at r = 50 fire 3 lasers toward islands, then explode (safe near island centers)
    for (int s = 0; 6 > s; s++) {
      double angle = (2.0D * Math.PI * s) / 6.0D;
      Vec3 starPos = new Vec3(skyCenter.x + Math.cos(angle) * 50.0D, skyCenter.y, skyCenter.z + Math.sin(angle) * 50.0D);
      level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, starPos.x, starPos.y, starPos.z, 3, 1.5D, 1.5D, 1.5D, 0.0D);
      level.playSound(null, dragon.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.5f, 0.7f);

      for (int shot = 0; 3 > shot; shot++) {
        Vec3 targetIsland = state.islandCenters.get((s + shot) % state.islandCenters.size());
        Vec3 dir = targetIsland.subtract(starPos).normalize();
        DragonCombatExecutor.firePrecisionLaser(level, dragon, starPos, dir, 55.0D, 14.0f);
      }
    }

    // Insta-kill radial star blast unless standing within 4.5 blocks of an island's center
    for (Player p : level.players()) {
      if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
      boolean safeAtCenter = false;
      for (Vec3 island : state.islandCenters) {
        if (4.5D >= Math.hypot(p.getX() - island.x, p.getZ() - island.z)) {
          safeAtCenter = true;
          break;
        }
      }
      if (!safeAtCenter && 75.0D >= p.position().distanceTo(skyCenter)) {
        p.hurt(level.damageSources().magic(), 9999.0f);
      }
    }
  }
}
