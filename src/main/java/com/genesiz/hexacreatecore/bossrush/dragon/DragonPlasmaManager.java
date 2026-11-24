package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.registry.ModEffects;
import com.genesiz.hexacreatecore.threedified.volumetrics.dictionaries.OilFireVolumetricDictionary;
import com.genesiz.hexacreatecore.threedified.volumetrics.hooks.VolumetricHook;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@EventBusSubscriber(modid = HexaCreateCore.MODID)
public final class DragonPlasmaManager {

  public static final class PlasmaPatch {
    public final UUID id = UUID.randomUUID();
    public final ResourceKey dimension;
    public final Vec3 center;
    public final float radius;
    public int ageTicks = 0;
    public final int maxAgeTicks;

    public PlasmaPatch(ResourceKey dimension, Vec3 center, float radius, int maxAgeTicks) {
      this.dimension = dimension;
      this.center = center;
      this.radius = radius;
      this.maxAgeTicks = maxAgeTicks;
    }
  }

  private static final CopyOnWriteArrayList ACTIVE_PATCHES = new CopyOnWriteArrayList();

  private DragonPlasmaManager() {}

  public static void spawnPlasmaPatch(ServerLevel level, Vec3 impactPos) {
    BlockPos cursor = BlockPos.containing(impactPos.x, impactPos.y + 1.0D, impactPos.z);
    while (cursor.getY() > level.getMinBuildHeight() && level.getBlockState(cursor).isAir()) {
      cursor = cursor.below();
    }
    double groundY = cursor.getY() + 1.02D;
    Vec3 snapped = new Vec3(impactPos.x, groundY, impactPos.z);

    // Deduplicate overlapping cluster impacts within 2.5 blocks so 4 cluster shields don't stack 4x overdraw
    for (Object obj : ACTIVE_PATCHES) {
      PlasmaPatch existing = (PlasmaPatch) obj;
      if (existing.dimension.equals(level.dimension()) && 6.25D >= existing.center.distanceToSqr(snapped)) {
        existing.ageTicks = Math.min(existing.ageTicks, 8);
        return;
      }
    }

    // 4.0-block radius rounded square lasting 10 seconds (200 ticks)
    ACTIVE_PATCHES.add(new PlasmaPatch(level.dimension(), snapped, 4.0f, 200));
    level.sendParticles(ParticleTypes.DRAGON_BREATH, snapped.x, snapped.y + 0.2D, snapped.z, 70, 2.8D, 0.3D, 2.8D, 0.04D);
  }

  private static boolean isInsideRoundedSquare(double dx, double dz, double radius, double verticalDiff) {
    if (verticalDiff > 2.2D || -0.8D > verticalDiff) return false;
    double nx = Math.abs(dx) / radius;
    double nz = Math.abs(dz) / radius;
    if (nx > 1.05D || nz > 1.05D) return false;
    double p = 2.0D / 0.34D;
    return (Math.pow(nx, p) + Math.pow(nz, p)) <= 1.08D;
  }

  @SubscribeEvent
  public static void onServerTick(ServerTickEvent.Post event) {
    if (ACTIVE_PATCHES.isEmpty()) return;

    for (Object obj : ACTIVE_PATCHES) {
      PlasmaPatch patch = (PlasmaPatch) obj;
      patch.ageTicks++;
      if (patch.ageTicks > patch.maxAgeTicks) {
        ACTIVE_PATCHES.remove(patch);
        continue;
      }

      ServerLevel level = event.getServer().getLevel(patch.dimension);
      if (level == null) {
        ACTIVE_PATCHES.remove(patch);
        continue;
      }

      if (patch.ageTicks % 3 == 0) {
        level.sendParticles(
          ParticleTypes.WITCH,
          patch.center.x, patch.center.y + 0.35D, patch.center.z,
          8, patch.radius * 0.65D, 0.25D, patch.radius * 0.65D, 0.01D
        );
      }

      for (Player p : level.players()) {
        if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
        if (isInsideRoundedSquare(p.getX() - patch.center.x, p.getZ() - patch.center.z, patch.radius, p.getY() - patch.center.y)) {
          MobEffectInstance cur = p.getEffect(ModEffects.ENDER_DECAY);
          if (cur == null || 16 >= cur.getDuration()) {
            int syncedDuration = (cur != null) ? (16 + (cur.getDuration() % 4)) : 20;
            p.addEffect(new MobEffectInstance(ModEffects.ENDER_DECAY, syncedDuration, 0, false, true, true));
          }
        }
      }

      net.minecraft.world.phys.AABB patchBox = net.minecraft.world.phys.AABB.ofSize(
        patch.center, (patch.radius * 2.0D) + 1.0D, 4.0D, (patch.radius * 2.0D) + 1.0D
      );
      for (net.minecraft.world.entity.monster.EnderMan em : level.getEntitiesOfClass(
        net.minecraft.world.entity.monster.EnderMan.class, patchBox
      )) {
        if (!em.isAlive()) continue;
        if (isInsideRoundedSquare(em.getX() - patch.center.x, em.getZ() - patch.center.z, patch.radius, em.getY() - patch.center.y)) {
          EndermanVaporizeHandler.vaporizeEnderman(level, em);
        }
      }
    }
  }

  public static void renderAll(PoseStack ps, MultiBufferSource.BufferSource buf, Level level, Vec3 cam, float partialTick, float timeSec) {
    if (ACTIVE_PATCHES.isEmpty()) return;

    boolean anyRendered = false;
    for (Object obj : ACTIVE_PATCHES) {
      PlasmaPatch patch = (PlasmaPatch) obj;
      if (!patch.dimension.equals(level.dimension())) continue;

      float lifeRatio = Mth.clamp((float) patch.ageTicks / (float) patch.maxAgeTicks, 0.0f, 1.0f);
      float fadeIn = Mth.clamp((patch.ageTicks + partialTick) / 8.0f, 0.0f, 1.0f);
      float fadeOut = (lifeRatio > 0.85f) ? Mth.clamp((1.0f - lifeRatio) / 0.15f, 0.0f, 1.0f) : 1.0f;
      float env = fadeIn * fadeOut;
      if (0.01f >= env) continue;

      if (!anyRendered) {
        anyRendered = true;
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
      }

      float curRadius = patch.radius * Mth.lerp(fadeIn, 0.35f, 1.0f);
      float flameHeight = (1.35f + 0.22f * Mth.sin(timeSec * 4.5f)) * env;

      VolumetricHook.bind(OilFireVolumetricDictionary.DRAGON_PLASMA.withOpacity(env))
        .at(
          (float) (patch.center.x - cam.x),
          (float) (patch.center.y - cam.y),
          (float) (patch.center.z - cam.z)
        )
        .autoFlush(false)
        .renderOilFire(ps, buf, curRadius, flameHeight, 4, timeSec);
    }

    if (anyRendered) {
      buf.endBatch(com.genesiz.hexacreatecore.threedified.RawGeometry.DIALOGUE_SOLID);
      com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
      com.mojang.blaze3d.systems.RenderSystem.enableCull();
    }
  }
}

