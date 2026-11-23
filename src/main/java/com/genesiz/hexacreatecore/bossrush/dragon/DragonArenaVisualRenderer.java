package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.bossrush.dragon.DragonArenaController.ArenaState;
import com.genesiz.hexacreatecore.hexcasting.everbook.TruncatedIcosahedronCage;
import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.BlackHoleDictionary;
import com.genesiz.hexacreatecore.threedified.shaders.hooks.PortalShaderHook;
import com.genesiz.hexacreatecore.threedified.volumetrics.dictionaries.BlackHoleVolumetricDictionary;
import com.genesiz.hexacreatecore.threedified.volumetrics.hooks.VolumetricHook;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = "hexacreatecore", value = Dist.CLIENT)
public final class DragonArenaVisualRenderer {

  private static final Map<UUID, Float> SHIELD_FLOOR_BLEND = new HashMap<>();
  private static final Map<UUID, Double> NOVA_START_WALL_SEC = new HashMap<>();
  private static final Map<UUID, Double> DETONATE_START_WALL_SEC = new HashMap<>();
  private static double lastWallSec = -1.0D;

  private static final BlackHoleDictionary ENDER_SINGULARITY_DISK = new BlackHoleDictionary(
    0xFF05020A, // Pitch-black void core
    0xFFE879F9, // Bright magenta-purple photon ring peak
    0xFF9333EA, // Rich royal-purple mid accretion disk
    0xFF4C1D95  // Deep violet outer lensing halo
  );

  private static final BlackHoleVolumetricDictionary ENDER_SINGULARITY_VOL = new BlackHoleVolumetricDictionary(
    0xFF05020A, // Pitch-black 3D singularity core
    0xFFE879F9, // Bright magenta-purple photon horizon
    0xFF9333EA, // Royal-purple mid-lensing shell
    0xFF4C1D95  // Deep violet outer dissipation edge
  );

  private static final BlackHoleVolumetricDictionary PITCH_BLACK_CROWN = new BlackHoleVolumetricDictionary(
    0xFF3B0764, // Deeper purple core
    0xFF9333EA, // Purple inner wall
    0xFF160726, // Almost-black purple mid shockwave
    0xFF020104  // Black outer dissipation edge
  );

  private DragonArenaVisualRenderer() {}

  @SubscribeEvent
  public static void onRenderWorld(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;

    Vec3 cam = mc.getEntityRenderDispatcher().camera.getPosition();
    PoseStack ps = event.getPoseStack();
    MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
    float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
    float timeSec = (mc.level.getGameTime() + partialTick) * 0.05f;

    double nowWallSec = System.nanoTime() * 1.0e-9D;
    float dt = (0.0D > lastWallSec) ? 0.016f : (float) Mth.clamp(nowWallSec - lastWallSec, 0.0D, 0.1D);
    lastWallSec = nowWallSec;
    if (mc.isPaused()) {
      dt = 0.0f;
    }

    var dragons = mc.level.getEntitiesOfClass(
      EnderDragon.class,
      AABB.ofSize(mc.player.position(), 256.0D, 256.0D, 256.0D)
    );

    boolean anyCageRendered = false;
    for (EnderDragon dragon : dragons) {
      if (!(dragon instanceof IMhEnderDragon mh)) continue;

      Vec3 dPos = dragon.getPosition(partialTick);
      Vec3 origin = mh.hexacreatecore$getArenaOrigin();
      DragonAnimState.DragonAction act = mh.hexacreatecore$getActiveAction();
      boolean isChargingNova = (act == DragonAnimState.DragonAction.NOVA_PRE_CHARGE
        || act == DragonAnimState.DragonAction.NOVA_CHARGE
        || (act == DragonAnimState.DragonAction.NOVA_END_CHARGE && 80 > mh.hexacreatecore$getActionTick()));
      boolean hasShield = mh.hexacreatecore$hasBarrierShield();

      float targetBlend = isChargingNova ? 1.0f : 0.0f;
      float curBlend = SHIELD_FLOOR_BLEND.getOrDefault(dragon.getUUID(), targetBlend);
      curBlend = Mth.approach(curBlend, targetBlend, dt * 1.25f);
      SHIELD_FLOOR_BLEND.put(dragon.getUUID(), curBlend);

      if (hasShield || isChargingNova || curBlend > 0.001f) {
        anyCageRendered = true;
        float bodyYaw = Mth.rotLerp(partialTick, dragon.yBodyRotO, dragon.yBodyRot);
        Vec3 forward = Vec3.directionFromRotation(0.0f, bodyYaw).normalize();

        Vec3 normalCenter = dPos.subtract(forward.scale(2.0D)).add(0.0D, 2.2D, 0.0D);
        Vec3 floorCenter = new Vec3(dPos.x, origin.y + 0.5D, dPos.z);

        float ease = curBlend * curBlend * (3.0f - 2.0f * curBlend);
        Vec3 activeCenter = normalCenter.lerp(floorCenter, ease);
        float activeRadius = Mth.lerp(ease, 8.2f, 6.5f);

        TruncatedIcosahedronCage.renderCage(ps, buf, cam, activeCenter, activeRadius, timeSec);
      }

      if (isChargingNova) {
        anyCageRendered = true;
        for (EndCrystal crystal : mc.level.getEntitiesOfClass(
          EndCrystal.class,
          AABB.ofSize(origin, 120.0D, 90.0D, 120.0D)
        )) {
          TruncatedIcosahedronCage.renderCage(ps, buf, cam, crystal.position().add(0.0D, 1.0D, 0.0D), 2.4f, timeSec);
        }
      }
    }
    if (anyCageRendered) {
      buf.endBatch();
    }

    // 2. Growing & Collapsing Cubic Black Hole 10 blocks higher above the dragon across the 20s Nova Charge
    for (EnderDragon dragon : dragons) {
      if (!(dragon instanceof IMhEnderDragon mh)) continue;
      DragonAnimState.DragonAction act = mh.hexacreatecore$getActiveAction();
      boolean isChargingNova = (act == DragonAnimState.DragonAction.NOVA_PRE_CHARGE
        || act == DragonAnimState.DragonAction.NOVA_CHARGE
        || (act == DragonAnimState.DragonAction.NOVA_END_CHARGE && 80 > mh.hexacreatecore$getActionTick()));

      if (!isChargingNova) {
        NOVA_START_WALL_SEC.remove(dragon.getUUID());
        continue;
      }

      double startWall = NOVA_START_WALL_SEC.computeIfAbsent(dragon.getUUID(), id -> nowWallSec);
      float prog = Mth.clamp((float) ((nowWallSec - startWall) / 20.0D), 0.0f, 1.0f);

      float orbRadius;
      if (0.92f > prog) {
        orbRadius = 2.0f + (prog / 0.92f) * 9.5f;
      } else {
        float collapseT = Mth.clamp((1.0f - prog) / 0.08f, 0.0f, 1.0f);
        orbRadius = Math.max(0.25f, 11.5f * (collapseT * collapseT));
      }

      Vec3 novaCenter = dragon.getPosition(partialTick).add(0.0D, 29.5D, 0.0D);
      renderCubicBlackHole(ps, buf, cam, novaCenter, orbRadius, timeSec * 2.5f, 48, 12);
    }

    // 3. Quadruple Expanding Sift-Rift Shockwave Crowns starting at 4.0s (tick 80) of end_charging_nova_1
    for (EnderDragon dragon : dragons) {
      if (!(dragon instanceof IMhEnderDragon mh)) continue;
      DragonAnimState.DragonAction act = mh.hexacreatecore$getActiveAction();
      boolean isDetonating = (act == DragonAnimState.DragonAction.NOVA_END_CHARGE && mh.hexacreatecore$getActionTick() >= 80);

      if (!isDetonating) {
        DETONATE_START_WALL_SEC.remove(dragon.getUUID());
        continue;
      }

      double startWall = DETONATE_START_WALL_SEC.computeIfAbsent(dragon.getUUID(), id -> nowWallSec);
      float prog = Mth.clamp((float) ((nowWallSec - startWall) / 1.35D), 0.0f, 1.0f);

      Vec3 crownBase = dragon.getPosition(partialTick).subtract(0.0D, 30.0D, 0.0D);
      renderNovaSiftCrowns(ps, buf, cam, crownBase, prog, timeSec);
    }

    // 4. Phase 3 Island-Spanning Cubic Volumetric Black Hole (scoped to live Phase 3 dragon in this dimension)
    for (ArenaState state : DragonArenaController.getAllArenas().values()) {
      if (!state.blackholeActive) continue;
      boolean hasLiveArenaDragon = mc.level.getEntitiesOfClass(
        EnderDragon.class,
        AABB.ofSize(state.origin, 256.0D, 160.0D, 256.0D)
      ).stream().anyMatch(d -> d.isAlive() && d instanceof IMhEnderDragon mh && mh.hexacreatecore$getFightPhase() >= 3);

      if (hasLiveArenaDragon) {
        Vec3 islandCenter = new Vec3(state.origin.x, state.origin.y + 12.0D, state.origin.z);
        renderCubicBlackHole(ps, buf, cam, islandCenter, 42.5f, timeSec, 64, 14);
      }
    }
  }

  private static void renderNovaSiftCrowns(
    PoseStack ps,
    MultiBufferSource.BufferSource buf,
    Vec3 cam,
    Vec3 basePos,
    float prog,
    float timeSec
  ) {
    final float shieldEdgeRadius = 6.5f;
    final float[] targetDistances = { 50.0f, 100.0f, 150.0f, 200.0f };
    final float[] targetHeights   = { 40.0f,  50.0f,  60.0f,  70.0f };

    ps.pushPose();
    ps.translate(basePos.x - cam.x, basePos.y - cam.y, basePos.z - cam.z);
    ps.mulPose(Axis.XP.rotationDegrees(-90.0f));

    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.disableCull();
    RenderSystem.depthMask(false);

    for (int i = 0; 4 > i; i++) {
      // Stagger each crown slightly, then apply an easeOutQuart curve for a fast burst + smooth deceleration
      float rawT = Mth.clamp((prog - (i * 0.035f)) / (1.0f - (i * 0.035f)), 0.0f, 1.0f);
      if (0.001f >= rawT) continue;

      float inv = 1.0f - rawT;
      float easeOut = 1.0f - (inv * inv * inv * inv);
      float opacity = Mth.clamp(1.0f - rawT, 0.0f, 1.0f);

      float curOuterRadius = Mth.lerp(easeOut, shieldEdgeRadius + 1.0f, targetDistances[i]);
      float flareSpan = Math.max(0.5f, curOuterRadius - shieldEdgeRadius);
      float curHeight = targetHeights[i] * easeOut;

      com.genesiz.hexacreatecore.threedified.volumetrics.dictionaries.VolumetricDictionary fadingDict =
        (normX, normY, normZ, shellT, timeSeconds) -> {
          int base = PITCH_BLACK_CROWN.sampleShellArgb(normX, normY, normZ, shellT, timeSeconds);
          int baseAlpha = (base >>> 24) & 0xFF;
          int fullStartAlpha = Math.min(255, (int) (baseAlpha * (255.0f / 168.0f)));
          int finalAlpha = (int) (fullStartAlpha * opacity);
          return (base & 0x00FFFFFF) | (finalAlpha << 24);
        };

      ps.pushPose();
      ps.mulPose(Axis.ZP.rotationDegrees(i * 22.5f));
      VolumetricHook.bind(fadingDict)
        .at(0.0f, 0.0f, 0.0f)
        .coreSize(shieldEdgeRadius, shieldEdgeRadius, 0.0f)
        .renderCrown(ps, buf, flareSpan, curHeight, 12, timeSec * 1.8f);
      ps.popPose();
    }

    buf.endBatch(RawGeometry.DIALOGUE_SOLID);
    RenderSystem.depthMask(true);
    RenderSystem.enableCull();
    ps.popPose();
  }

  private static void renderCubicBlackHole(
    PoseStack ps,
    MultiBufferSource.BufferSource buf,
    Vec3 cam,
    Vec3 worldCenter,
    float coreHalf,
    float timeSec,
    int discSegments,
    int discRings
  ) {
    ps.pushPose();
    ps.translate(worldCenter.x - cam.x, worldCenter.y - cam.y, worldCenter.z - cam.z);
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.disableCull();

    // Horizontal relativistic purple accretion disk around the black hole equator (2x wider)
    float discHalf = coreHalf * 2.98f;
    ps.pushPose();
    ps.mulPose(Axis.XP.rotationDegrees(90.0f));
    PortalShaderHook.bind(ENDER_SINGULARITY_DISK)
      .size(discHalf, discHalf)
      .resolution(discSegments, discRings)
      .flushBatch()
      .render(ps, buf, timeSec);
    ps.popPose();

    // Full 3D cubic pitch-black superellipsoid core + rim-lit purple photon horizon
    VolumetricHook.bind(ENDER_SINGULARITY_VOL)
      .at(0.0f, 0.0f, 0.0f)
      .coreSize(coreHalf, coreHalf, coreHalf)
      .eventHorizon(Math.max(0.35f, coreHalf * 0.15f), 6)
      .render(ps, buf, timeSec);
    buf.endBatch(RawGeometry.DIALOGUE_SOLID);
    RenderSystem.enableCull();
    ps.popPose();
  }

  @SubscribeEvent
  public static void onComputeCameraAngles(ComputeCameraAngles event) {
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    if (mc.level == null || mc.player == null || mc.isPaused()) return;

    float partialTick = (float) event.getPartialTick();
    float maxIntensity = 0.0f;

    for (EnderDragon dragon : mc.level.getEntitiesOfClass(
      EnderDragon.class,
      mc.player.getBoundingBox().inflate(72.0D)
    )) {
      if (!dragon.isAlive() || !(dragon instanceof IMhEnderDragon mh)) continue;
      if (mh.hexacreatecore$getActiveAction() != DragonAnimState.DragonAction.SAFI_ROAR) continue;

      float elapsedSec = (mh.hexacreatecore$getActionTick() + partialTick) / 20.0f;
      if (elapsedSec >= 2.0f && 4.85f >= elapsedSec) {
        double dist = mc.player.distanceTo(dragon);
        float distFalloff = Mth.clamp(1.0f - (float) (dist / 64.0D), 0.0f, 1.0f);
        float env = (2.25f > elapsedSec)
        ? (elapsedSec - 2.0f) / 0.25f
        : Mth.clamp((4.85f - elapsedSec) / 2.60f, 0.0f, 1.0f);
        maxIntensity = Math.max(maxIntensity, distFalloff * env);
      }
    }

    if (maxIntensity > 0.001f) {
      float t = (mc.player.tickCount + partialTick) * 1.9f;
      float amp = maxIntensity * 3.2f;
      event.setYaw(event.getYaw() + (Mth.sin(t * 1.3f) * amp));
      event.setPitch(event.getPitch() + (Mth.cos(t * 1.7f) * amp * 0.85f));
      event.setRoll(event.getRoll() + (Mth.sin(t * 2.1f) * amp * 1.15f));
    }
  }
}
