package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.registry.ModEffects;
import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.AuroraRimDictionary;
import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.CompositeDictionary;
import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.CosmicOceanDictionary;
import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.ShaderDictionary;
import com.genesiz.hexacreatecore.threedified.shaders.hooks.PortalShaderHook;
import com.genesiz.hexacreatecore.threedified.volumetrics.dictionaries.OilFireVolumetricDictionary;
import com.genesiz.hexacreatecore.threedified.volumetrics.hooks.VolumetricHook;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@EventBusSubscriber(modid = HexaCreateCore.MODID)
public final class DragonGeyserManager {

  private static final CompositeDictionary GEYSER_POOL_SHADER = new CompositeDictionary(
    new CosmicOceanDictionary(
      0xEA0B0318, // Deep void-black purple center
      0xEA3B0764, // Dark frenzy violet swirl
      0xEA7E22CE, // Rich purple current
      0xEA581C87, // Deep royal purple secondary current
      0x00000000,
      0.0f
    ),
    new AuroraRimDictionary(
      0xE6C026D3, // Vivid magenta inner rim
      0xE6F0ABFC, // Bright lavender outer edge
      0.72f,
      0.90f
    )
  );

  // Pre-cached 12-step opacity LUT for the ground pool
  private static final ShaderDictionary[] POOL_OPACITY_LUT = new ShaderDictionary[6 + 6];
  static {
    for (int i = 0; i < POOL_OPACITY_LUT.length; i++) {
      final float alphaScale = (i + 1) / (float) POOL_OPACITY_LUT.length;
      POOL_OPACITY_LUT[i] = (localX, localY, angleRad, radialT, timeSeconds) -> {
        int raw = GEYSER_POOL_SHADER.sampleArgb(localX, localY, angleRad, radialT, timeSeconds);
        int baseAlpha = (raw >>> 24) & 0xFF;
        int scaledAlpha = Mth.clamp((int) (baseAlpha * alphaScale), 0, 255);
        return (raw & 0x00FFFFFF) | (scaledAlpha << 24);
      };
    }
  }

  private static ShaderDictionary samplePoolByOpacity(float opacity) {
    int idx = Mth.clamp((int) (opacity * (POOL_OPACITY_LUT.length - 0.01f)), 0, POOL_OPACITY_LUT.length - 1);
    return POOL_OPACITY_LUT[idx];
  }

  // Pre-cached 16-bucket opacity LUT for the erupting plasma pillar
  private static final OilFireVolumetricDictionary[] PLASMA_OPACITY_LUT = new OilFireVolumetricDictionary[8 + 8];
  static {
    for (int i = 0; i < PLASMA_OPACITY_LUT.length; i++) {
      float alpha = (i + 1) / (float) PLASMA_OPACITY_LUT.length;
      PLASMA_OPACITY_LUT[i] = OilFireVolumetricDictionary.GEYSER_PLASMA.withOpacity(alpha);
    }
  }

  private static OilFireVolumetricDictionary samplePlasmaByOpacity(float opacity) {
    int idx = Mth.clamp((int) (opacity * (PLASMA_OPACITY_LUT.length - 0.01f)), 0, PLASMA_OPACITY_LUT.length - 1);
    return PLASMA_OPACITY_LUT[idx];
  }

  private static final class ServerGeyser {
    final ResourceKey<Level> dimension;
    final Vec3 basePos;
    final float radius;
    final float maxFlameHeight;
    final int maxAgeTicks;
    final int telegraphTicks;
    int ageTicks = 0;

    ServerGeyser(ResourceKey<Level> dimension, Vec3 basePos, float radius, float maxFlameHeight, int maxAgeTicks, int telegraphTicks) {
      this.dimension = dimension;
      this.basePos = basePos;
      this.radius = radius;
      this.maxFlameHeight = maxFlameHeight;
      this.maxAgeTicks = maxAgeTicks;
      this.telegraphTicks = telegraphTicks;
    }

    boolean isErupting() {
      return ageTicks >= telegraphTicks && ageTicks <= (maxAgeTicks - 6);
    }
  }

  private static final class ClientGeyser {
    final ResourceKey<Level> dimension;
    final Vec3 basePos;
    final float radius;
    final float maxFlameHeight;
    final float maxDurationSec;
    final float telegraphDurationSec;
    long firstFrameNanos = -1L;
    long pausedAccumNanos = 0L;
    long lastPauseNanos = -1L;

    ClientGeyser(ResourceKey<Level> dimension, DragonGeyserWavePayload.Entry e) {
      this.dimension = dimension;
      this.basePos = new Vec3(e.x(), e.y(), e.z());
      this.radius = e.radius();
      this.maxFlameHeight = e.maxFlameHeight();
      this.maxDurationSec = e.maxAgeTicks() / 20.0f;
      this.telegraphDurationSec = e.telegraphTicks() / 20.0f;
    }

    float sampleRenderAgeSeconds(long nowNanos, boolean paused) {
      if (firstFrameNanos < 0L) {
        firstFrameNanos = nowNanos;
      }
      if (paused) {
        if (lastPauseNanos < 0L) {
          lastPauseNanos = nowNanos;
        }
        return (float) ((lastPauseNanos - firstFrameNanos - pausedAccumNanos) * 1.0e-9D);
      } else if (lastPauseNanos >= 0L) {
        pausedAccumNanos += (nowNanos - lastPauseNanos);
        lastPauseNanos = -1L;
      }
      return (float) ((nowNanos - firstFrameNanos - pausedAccumNanos) * 1.0e-9D);
    }
  }

  private static final CopyOnWriteArrayList<ServerGeyser> SERVER_GEYSERS = new CopyOnWriteArrayList<>();
  private static final CopyOnWriteArrayList<ClientGeyser> CLIENT_GEYSERS = new CopyOnWriteArrayList<>();
  private static final int MAX_ACTIVE_GEYSERS = 75;
  private static final double NOVA_MAX_RADIUS = 200.0D;
  private static final double SAFE_ZONE_EXCLUSION_SQ = 64.0D;
  private static final double MAX_RENDER_DIST_SQ = 105.0D * 105.0D;

  private DragonGeyserManager() {}

  public static void receiveClientWave(DragonGeyserWavePayload payload) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null) return;
    ResourceKey<Level> dim = mc.level.dimension();

    for (DragonGeyserWavePayload.Entry entry : payload.geysers()) {
      if (CLIENT_GEYSERS.size() >= MAX_ACTIVE_GEYSERS + 20) {
        CLIENT_GEYSERS.remove(0);
      }
      CLIENT_GEYSERS.add(new ClientGeyser(dim, entry));
    }
  }

  public static void spawnNovaGeyserWave(ServerLevel level, EnderDragon dragon) {
    if (SERVER_GEYSERS.size() >= MAX_ACTIVE_GEYSERS) return;

    RandomSource rng = level.getRandom();
    double dX = dragon.getX();
    double dY = dragon.getY();
    double dZ = dragon.getZ();
    int maxGroundY = (int) Math.floor(dY) - 1;
    int minScanY = Math.max(level.getMinBuildHeight(), maxGroundY - 56);

    List<DragonGeyserWavePayload.Entry> spawnedBatch = new ArrayList<>(16);

    for (int i = 0; i < 7; i++) {
      if (SERVER_GEYSERS.size() >= MAX_ACTIVE_GEYSERS) break;
      double angle = rng.nextDouble() * Math.PI * 2.0D;
      double dist = Math.sqrt(rng.nextDouble()) * NOVA_MAX_RADIUS;
      double gx = dX + Math.cos(angle) * dist;
      double gz = dZ + Math.sin(angle) * dist;
      trySpawnSingleGeyser(level, dX, dZ, gx, gz, maxGroundY, minScanY, rng, spawnedBatch);
    }

    for (Player p : level.players()) {
      if (!p.isAlive() || p.isSpectator()) continue;
      for (int i = 0; i < 3; i++) {
        if (SERVER_GEYSERS.size() >= MAX_ACTIVE_GEYSERS) break;
        double angle = rng.nextDouble() * Math.PI * 2.0D;
        double dist = 3.2D + Math.sqrt(rng.nextDouble()) * 22.0D;
        double gx = p.getX() + Math.cos(angle) * dist;
        double gz = p.getZ() + Math.sin(angle) * dist;
        trySpawnSingleGeyser(level, dX, dZ, gx, gz, maxGroundY, minScanY, rng, spawnedBatch);
      }
    }

    if (!spawnedBatch.isEmpty()) {
      PacketDistributor.sendToPlayersNear(
        level, null, dX, dY, dZ, 225.0D,
        new DragonGeyserWavePayload(spawnedBatch)
      );
    }
  }

  private static void trySpawnSingleGeyser(
    ServerLevel level,
    double dragonX, double dragonZ,
    double targetX, double targetZ,
    int maxGroundY, int minScanY,
    RandomSource rng,
    List<DragonGeyserWavePayload.Entry> outBatch
  ) {
    double dxDragon = targetX - dragonX;
    double dzDragon = targetZ - dragonZ;
    double distSq = (dxDragon * dxDragon) + (dzDragon * dzDragon);

    if (distSq <= SAFE_ZONE_EXCLUSION_SQ || distSq > (NOVA_MAX_RADIUS * NOVA_MAX_RADIUS)) {
      return;
    }

    int bx = Mth.floor(targetX);
    int bz = Mth.floor(targetZ);
    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    double groundY = Double.NaN;

    for (int y = Math.min(level.getMaxBuildHeight() - 1, maxGroundY); y >= minScanY; y--) {
      cursor.set(bx, y, bz);
      if (!level.getBlockState(cursor).isAir() && level.getBlockState(cursor.above()).isAir()) {
        groundY = y + 1.03D;
        break;
      }
    }
    if (Double.isNaN(groundY)) return;

    Vec3 snapped = new Vec3(targetX, groundY, targetZ);

    for (ServerGeyser existing : SERVER_GEYSERS) {
      if (existing.dimension.equals(level.dimension()) && existing.basePos.distanceToSqr(snapped) < 12.25D) {
        return;
      }
    }

    float radius = 1.85f + rng.nextFloat() * 0.60f;
    float flameHeight = 5.0f + rng.nextFloat() * 5.0f;

    int maxAgeTicks = 56 + rng.nextInt(10);
    int telegraphTicks = 17;
    SERVER_GEYSERS.add(new ServerGeyser(
      level.dimension(),
      snapped,
      radius,
      flameHeight,
      maxAgeTicks,
      telegraphTicks
    ));
    outBatch.add(new DragonGeyserWavePayload.Entry(
      snapped.x, snapped.y, snapped.z,
      radius, flameHeight, maxAgeTicks, telegraphTicks
    ));
  }
  @SubscribeEvent
  public static void onServerTick(ServerTickEvent.Post event) {
    if (SERVER_GEYSERS.isEmpty()) return;
    for (ServerGeyser geyser : SERVER_GEYSERS) {
      geyser.ageTicks++;
      if (geyser.ageTicks > geyser.maxAgeTicks) {
        SERVER_GEYSERS.remove(geyser);
        continue;
      }
      ServerLevel level = event.getServer().getLevel(geyser.dimension);
      if (level == null) {
        SERVER_GEYSERS.remove(geyser);
        continue;
      }
      if (geyser.ageTicks == geyser.telegraphTicks) {
        level.playSound(
          null, geyser.basePos.x, geyser.basePos.y, geyser.basePos.z,
          SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.70f, 1.38f
        );
      }
      if (geyser.ageTicks < geyser.telegraphTicks) {
        if (geyser.ageTicks % 3 == 0) {
          level.sendParticles(
            ParticleTypes.WITCH,
            geyser.basePos.x, geyser.basePos.y + 0.08D, geyser.basePos.z,
            4, geyser.radius * 0.55D, 0.03D, geyser.radius * 0.55D, 0.005D
          );
        }
      } else if (geyser.isErupting()) {
        double hitRadiusSq = (geyser.radius * 0.90D) * (geyser.radius * 0.90D);
        for (Player p : level.players()) {
          if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
          double dx = p.getX() - geyser.basePos.x;
          double dz = p.getZ() - geyser.basePos.z;
          double dy = p.getY() - geyser.basePos.y;
          if ((dx * dx + dz * dz) <= hitRadiusSq && dy >= -0.6D && dy <= (geyser.maxFlameHeight + 0.6D)) {
            if (p.hurt(level.damageSources().dragonBreath(), 6.5f)) {
              p.setDeltaMovement(p.getDeltaMovement().x, Math.max(p.getDeltaMovement().y, 0.62D), p.getDeltaMovement().z);
              p.hurtMarked = true;
            }
            MobEffectInstance cur = p.getEffect(ModEffects.ENDER_DECAY);
            if (cur == null || 16 >= cur.getDuration()) {
              p.addEffect(new MobEffectInstance(ModEffects.ENDER_DECAY, 30, 0, false, true, true));
            }
          }
        }
      }
    }
  }
  public static void renderAll(PoseStack ps, MultiBufferSource.BufferSource buf, Level level, Vec3 cam, float partialTick, float timeSec) {
    if (CLIENT_GEYSERS.isEmpty()) return;
    Minecraft mc = Minecraft.getInstance();
    long nowNanos = System.nanoTime();
    boolean paused = mc.isPaused();
    Vector3f look = mc.gameRenderer.getMainCamera().getLookVector();
    float lookX = look.x();
    float lookZ = look.z();
    boolean anyRendered = false;
    for (ClientGeyser geyser : CLIENT_GEYSERS) {
      if (!geyser.dimension.equals(level.dimension())) {
        CLIENT_GEYSERS.remove(geyser);
        continue;
      }
      float ageSec = geyser.sampleRenderAgeSeconds(nowNanos, paused);
      if (ageSec >= geyser.maxDurationSec) {
        CLIENT_GEYSERS.remove(geyser);
        continue;
      }
      float rx = (float) (geyser.basePos.x - cam.x);
      float ry = (float) (geyser.basePos.y - cam.y);
      float rz = (float) (geyser.basePos.z - cam.z);
      double horizDistSq = (rx * rx) + (rz * rz);
      if (horizDistSq > MAX_RENDER_DIST_SQ) continue;
      if (((rx * lookX) + (rz * lookZ)) < -6.5f) continue;
      float lifeRatio = Mth.clamp(ageSec / geyser.maxDurationSec, 0.0f, 1.0f);
      float fadeOut = (lifeRatio > 0.84f) ? Mth.clamp((1.0f - lifeRatio) / 0.16f, 0.0f, 1.0f) : 1.0f;
      if (fadeOut <= 0.01f) continue;
      if (!anyRendered) {
        anyRendered = true;
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
      }
      int poolSegs, poolRings, pillarSegs, pillarSlices;
      if (horizDistSq < (34.0D * 34.0D)) {
        poolSegs = 14; poolRings = 3;
        pillarSegs = 8; pillarSlices = 6;
      } else if (horizDistSq < (68.0D * 68.0D)) {
        poolSegs = 10; poolRings = 2;
        pillarSegs = 7; pillarSlices = 4;
      } else {
        poolSegs = 8; poolRings = 2;
        pillarSegs = 6; pillarSlices = 3;
      }
      float poolAlpha = (ageSec < geyser.telegraphDurationSec)
      ? Mth.clamp(ageSec / 0.18f, 0.0f, 1.0f)
      : Mth.clamp(1.0f - ((ageSec - geyser.telegraphDurationSec) / 0.35f), 0.0f, 1.0f);
      if (poolAlpha > 0.02f) {
        float telegraphProg = Mth.clamp(ageSec / geyser.telegraphDurationSec, 0.0f, 1.0f);
        float poolScale = Mth.lerp(telegraphProg * telegraphProg, 0.30f, 1.0f);
        float poolRadius = geyser.radius * poolScale;
        ps.pushPose();
        ps.translate(rx, ry, rz);
        ps.mulPose(Axis.XP.rotationDegrees(90.0f));
        PortalShaderHook.bind(samplePoolByOpacity(poolAlpha * poolAlpha))
          .circular(true)
          .at(0.0f, 0.0f)
          .size(poolRadius, poolRadius)
          .resolution(poolSegs, poolRings)
          .render(ps, buf, timeSec);
        ps.popPose();
      }
      if (ageSec >= geyser.telegraphDurationSec) {
        float snapProg = Mth.clamp((ageSec - geyser.telegraphDurationSec) / 0.15f, 0.0f, 1.0f);
        float easeSnap = 1.0f - (1.0f - snapProg) * (1.0f - snapProg);
        float pulse = 1.0f + 0.05f * Mth.sin((timeSec * 14.0f) + (float) geyser.basePos.x);
        float curHeight = geyser.maxFlameHeight * easeSnap * pulse * fadeOut;
        VolumetricHook.bind(samplePlasmaByOpacity(fadeOut))
          .at(rx, ry, rz)
          .autoFlush(false)
          .renderGeyserPillar(ps, buf, geyser.radius * 0.88f, curHeight, pillarSegs, pillarSlices, timeSec);
      }
    }
    if (anyRendered) {
      buf.endBatch(RawGeometry.DIALOGUE_SOLID);
      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
    }
  }
}
