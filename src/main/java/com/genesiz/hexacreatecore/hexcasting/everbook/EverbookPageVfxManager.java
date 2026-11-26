package com.genesiz.hexacreatecore.hexcasting.everbook;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.item.custom.EverbookItem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class EverbookPageVfxManager {

  // Exact page dimensions from everbook.geo.json: 4.75 x 6.5 sixteenths of a block
  private static final float PAGE_HALF_W = (4.75f * 0.5f) / 16.0f;
  private static final float PAGE_HALF_L = (6.50f * 0.5f) / 16.0f;
  private static final int MAX_ACTIVE_PAGES = 160;

  private static final RenderType PAGE_QUAD_TYPE = RenderType.create(
    "everbook_flying_pages",
    DefaultVertexFormat.POSITION_COLOR,
    VertexFormat.Mode.QUADS,
    256,
    false,
    true,
    RenderType.CompositeState.builder()
    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
    .setCullState(RenderStateShard.NO_CULL)
    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
    .createCompositeState(false)
  );

  private record BookEmitterPose(Vec3 origin, Vec3 normal, Vec3 rightAxis, Vec3 spineAxis, long updatedNanos) {}

  private enum Stage {
    RISING,
    ORBITING,
    FLUTTER_FALL,
    GROUND_FADE
  }

  private static final class FlyingPage {
    final UUID ownerId;
    final double anchorX;
    final double anchorZ;
    final double targetTopY;
    final double dropThresholdY;
    Stage stage = Stage.RISING;

    double x, y, z;
    double prevX, prevY, prevZ;
    double vx, vy, vz;

    float yawDeg;
    float pitchDeg;
    float rollDeg;
    float prevYawDeg;
    float prevPitchDeg;
    float prevRollDeg;

    final float sideRollSign;
    float orbitAngle;
    float orbitSpeed;
    float orbitRadiusBase;
    float orbitRadiusAmp;
    float orbitWobbleFreq;
    float descentSpeed;
    float yawSpinOffset;
    float flutterPhase;
    float flutterFreq;
    float flutterHeading;
    float shimmerPhase;
    float stageTime = 0.0f;
    float groundY = 0.0f;
    float opacity = 1.0f;

    FlyingPage(
      UUID ownerId,
      double anchorX, double anchorZ, double targetTopY, double dropThresholdY,
      double x, double y, double z, double vx, double vy, double vz,
      float spineYawDeg, RandomSource rng
    ) {
      this.ownerId = ownerId;
      this.anchorX = anchorX;
      this.anchorZ = anchorZ;
      this.targetTopY = targetTopY;
      this.dropThresholdY = dropThresholdY;
      this.x = this.prevX = x;
      this.y = this.prevY = y;
      this.z = this.prevZ = z;
      this.vx = vx;
      this.vy = vy;
      this.vz = vz;

      this.sideRollSign = rng.nextBoolean() ? 1.0f : -1.0f;
      this.yawDeg = this.prevYawDeg = spineYawDeg;
      this.pitchDeg = this.prevPitchDeg = 0.0f;
      this.rollDeg = this.prevRollDeg = 90.0f * this.sideRollSign;

      float dir = rng.nextBoolean() ? 1.0f : -1.0f;
      this.orbitAngle = rng.nextFloat() * Mth.TWO_PI;
      this.orbitSpeed = dir * (0.65f + rng.nextFloat() * 1.45f);
      this.orbitRadiusBase = 0.78f + rng.nextFloat() * 0.44f;
      this.orbitRadiusAmp = 0.08f + rng.nextFloat() * 0.16f;
      this.orbitWobbleFreq = 1.4f + rng.nextFloat() * 1.8f;
      this.descentSpeed = 0.28f + rng.nextFloat() * 0.26f;
      this.yawSpinOffset = (rng.nextFloat() - 0.5f) * 140.0f;

      this.flutterPhase = rng.nextFloat() * Mth.TWO_PI;
      this.flutterFreq = 4.0f + rng.nextFloat() * 1.8f;
      this.flutterHeading = rng.nextFloat() * Mth.TWO_PI;
      this.shimmerPhase = rng.nextFloat() * Mth.TWO_PI;
    }
  }

  private static final Map<UUID, BookEmitterPose> EMITTERS = new ConcurrentHashMap<>();
  private static final List<FlyingPage> PAGES = new ArrayList<>();
  private static int spawnCooldownTicks = 0;

  private EverbookPageVfxManager() {}

  public static void recordOpenBookWorldPose(UUID playerId, Matrix4f viewPose, Vec3 camPos) {
    if (playerId == null) return;
    Vector3f localOrigin = new Vector3f(0.0f, 0.0f, 0.0f);
    viewPose.transformPosition(localOrigin);
    Vec3 worldOrigin = new Vec3(camPos.x + localOrigin.x, camPos.y + localOrigin.y, camPos.z + localOrigin.z);

    Vector3f localUp = new Vector3f(0.0f, -1.0f, 0.0f);
    viewPose.transformDirection(localUp);
    if (localUp.y < 0.25f) {
      localUp.set(0.0f, 1.0f, 0.0f);
    }
    localUp.normalize();

    Vector3f localRight = new Vector3f(1.0f, 0.0f, 0.0f);
    viewPose.transformDirection(localRight).normalize();

    Vector3f localSpine = new Vector3f(0.0f, 0.0f, 1.0f);
    viewPose.transformDirection(localSpine).normalize();

    EMITTERS.put(
      playerId,
      new BookEmitterPose(
        worldOrigin,
        new Vec3(localUp.x, localUp.y, localUp.z),
        new Vec3(localRight.x, localRight.y, localRight.z),
        new Vec3(localSpine.x, localSpine.y, localSpine.z),
        System.nanoTime()
      )
    );
  }

  @SubscribeEvent
  public static void onClientTick(ClientTickEvent.Post event) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null) {
      PAGES.clear();
      EMITTERS.clear();
      return;
    }
    if (mc.isPaused()) return;

    final float dt = 0.05f;
    RandomSource rng = mc.level.random;

    if (spawnCooldownTicks > 0) {
      spawnCooldownTicks--;
    } else {
      boolean spawnedAny = false;
      for (Player player : mc.level.players()) {
        boolean holdingBook = player.getMainHandItem().getItem() instanceof EverbookItem
        || player.getOffhandItem().getItem() instanceof EverbookItem;
        if (!holdingBook || !EverbookPlayerAnimController.isPlayerBookOpen(player)) continue;
        if (PAGES.size() >= MAX_ACTIVE_PAGES) break;

        BookEmitterPose emitter = EMITTERS.get(player.getUUID());
        Vec3 spawnPos;
        Vec3 normal;
        Vec3 rightAxis;
        Vec3 spineAxis;

        long now = System.nanoTime();
        if (emitter != null && 250_000_000L > (now - emitter.updatedNanos())) {
          spawnPos = emitter.origin();
          normal = emitter.normal();
          rightAxis = emitter.rightAxis();
          spineAxis = emitter.spineAxis();
        } else {
          float bodyRad = player.yBodyRot * Mth.DEG_TO_RAD;
          Vec3 forward = new Vec3(-Mth.sin(bodyRad), 0.0D, Mth.cos(bodyRad));
          rightAxis = new Vec3(Mth.cos(bodyRad), 0.0D, Mth.sin(bodyRad));
          spineAxis = forward;
          spawnPos = player.position().add(forward.scale(0.45D)).add(0.0D, 1.15D, 0.0D);
          normal = forward.scale(0.42D).add(0.0D, 0.90D, 0.0D).normalize();
        }

        float sideVarRad = ((rng.nextFloat() * 20.0f) - 10.0f) * Mth.DEG_TO_RAD;
        Vec3 launchDir = normal.scale(Mth.cos(sideVarRad))
        .add(rightAxis.scale(Mth.sin(sideVarRad)))
        .normalize();

        double speed = 1.35D + rng.nextDouble() * 0.25D;
        float spineYawDeg = (float) (-Math.atan2(spineAxis.x, spineAxis.z) * Mth.RAD_TO_DEG);
        PAGES.add(new FlyingPage(
          player.getUUID(),
          player.getX(),
          player.getZ(),
          player.getY() + player.getBbHeight() + 1.0D,
          player.getY() + 0.5D,
          spawnPos.x, spawnPos.y, spawnPos.z,
          launchDir.x * speed, launchDir.y * speed, launchDir.z * speed,
          spineYawDeg, rng
        ));
        spawnedAny = true;
      }
      if (spawnedAny) {
        spawnCooldownTicks = 4;
      }
    }

    Iterator<FlyingPage> it = PAGES.iterator();
    while (it.hasNext()) {
      FlyingPage p = it.next();
      p.prevX = p.x;
      p.prevY = p.y;
      p.prevZ = p.z;
      p.prevYawDeg = p.yawDeg;
      p.prevPitchDeg = p.pitchDeg;
      p.prevRollDeg = p.rollDeg;
      p.stageTime += dt;
      p.shimmerPhase += dt * 5.2f;

      switch (p.stage) {
        case RISING -> {
          p.x += p.vx * dt;
          p.y += p.vy * dt;
          p.z += p.vz * dt;

          double dx = p.x - p.anchorX;
          double dz = p.z - p.anchorZ;
          double dist = Math.sqrt(dx * dx + dz * dz);
          if (dist > 0.05D) {
            double radialPull = (p.orbitRadiusBase - dist) * 1.6D;
            p.x += (dx / dist) * radialPull * dt;
            p.z += (dz / dist) * radialPull * dt;
          }

          float riseFrac = Mth.clamp(p.stageTime / 0.85f, 0.0f, 1.0f);
          float smoothLevel = riseFrac * riseFrac * (3.0f - 2.0f * riseFrac);
          p.rollDeg = Mth.lerp(smoothLevel, 90.0f * p.sideRollSign, Mth.cos(p.stageTime * 5.0f) * 14.0f);
          p.pitchDeg = Mth.sin(p.stageTime * 5.5f) * 16.0f * smoothLevel;
          p.yawDeg += p.yawSpinOffset * dt * smoothLevel;

          if (p.y >= p.targetTopY || p.stageTime >= 1.15f) {
            p.orbitAngle = (float) Math.atan2(p.z - p.anchorZ, p.x - p.anchorX);
            p.stage = Stage.ORBITING;
            p.stageTime = 0.0f;
          }
        }

        case ORBITING -> {
          float wobble = Mth.sin(p.stageTime * p.orbitWobbleFreq + p.flutterPhase);
          p.orbitAngle += (p.orbitSpeed + wobble * 0.45f) * dt;
          double currentRadius = p.orbitRadiusBase + wobble * p.orbitRadiusAmp;

          double desiredX = p.anchorX + Math.cos(p.orbitAngle) * currentRadius;
          double desiredZ = p.anchorZ + Math.sin(p.orbitAngle) * currentRadius;
          p.x = Mth.lerp(0.22D, p.x, desiredX);
          p.z = Mth.lerp(0.22D, p.z, desiredZ);

          p.y -= p.descentSpeed * dt;

          p.yawDeg += (p.orbitSpeed * Mth.RAD_TO_DEG + p.yawSpinOffset * 0.45f) * dt;
          p.pitchDeg = Mth.sin(p.shimmerPhase * 0.65f + p.flutterPhase) * 18.0f;
          p.rollDeg = Mth.cos(p.shimmerPhase * 0.55f + p.orbitAngle) * 16.0f;

          if (p.dropThresholdY >= p.y) {
            p.stage = Stage.FLUTTER_FALL;
            p.stageTime = 0.0f;
            p.flutterHeading = p.orbitAngle + (p.orbitSpeed >= 0.0f ? Mth.HALF_PI : -Mth.HALF_PI);
          }
        }

        case FLUTTER_FALL -> {
          p.flutterPhase += p.flutterFreq * dt;
          float sway = Mth.sin(p.flutterPhase);
          double glideSpeed = sway * 0.52D;

          p.x += Math.cos(p.flutterHeading) * glideSpeed * dt;
          p.z += Math.sin(p.flutterHeading) * glideSpeed * dt;
          p.flutterHeading += 0.35f * dt;

          double fallSpeed = 0.52D - Math.abs(sway) * 0.16D;
          double nextY = p.y - fallSpeed * dt;

          p.rollDeg = sway * 32.0f;
          p.pitchDeg = Mth.cos(p.flutterPhase * 0.85f) * 18.0f;
          p.yawDeg = -p.flutterHeading * Mth.RAD_TO_DEG;

          Vec3 from = new Vec3(p.x, p.y + 0.05D, p.z);
          Vec3 to = new Vec3(p.x, nextY - 0.02D, p.z);
          BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
          if (hit.getType() == HitResult.Type.BLOCK) {
            p.y = hit.getLocation().y + 0.006D;
            p.groundY = (float) p.y;
            p.pitchDeg = 0.0f;
            p.rollDeg = 0.0f;
            p.stage = Stage.GROUND_FADE;
            p.stageTime = 0.0f;
          } else {
            p.y = nextY;
            if (p.stageTime > 8.0f) {
              it.remove();
            }
          }
        }

        case GROUND_FADE -> {
          p.y = p.groundY;
          p.pitchDeg = Mth.lerp(0.35f, p.pitchDeg, 0.0f);
          p.rollDeg = Mth.lerp(0.35f, p.rollDeg, 0.0f);
          float fadeProg = p.stageTime / 1.4f;
          if (fadeProg >= 1.0f) {
            it.remove();
          } else {
            p.opacity = 1.0f - fadeProg;
          }
        }
      }
    }
  }

  @SubscribeEvent
  public static void onRenderPages(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
    if (PAGES.isEmpty()) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null) return;

    float pt = mc.getTimer().getGameTimeDeltaPartialTick(true);
    Vec3 cam = mc.getEntityRenderDispatcher().camera.getPosition();
    PoseStack ps = event.getPoseStack();
    MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.disableCull();
    RenderSystem.depthMask(false);

    VertexConsumer vc = buf.getBuffer(PAGE_QUAD_TYPE);

    for (FlyingPage p : PAGES) {
      double rx = Mth.lerp(pt, p.prevX, p.x) - cam.x;
      double ry = Mth.lerp(pt, p.prevY, p.y) - cam.y;
      double rz = Mth.lerp(pt, p.prevZ, p.z) - cam.z;

      float yaw = Mth.lerp(pt, p.prevYawDeg, p.yawDeg);
      float pitch = Mth.lerp(pt, p.prevPitchDeg, p.pitchDeg);
      float roll = Mth.lerp(pt, p.prevRollDeg, p.rollDeg);

      float shimmer = 0.90f + 0.10f * Mth.sin(p.shimmerPhase + pt * 0.25f);
      int alpha = Mth.clamp((int) (255.0f * shimmer * p.opacity), 0, 255);
      if (alpha <= 2) continue;

      ps.pushPose();
      ps.translate(rx, ry, rz);
      ps.mulPose(Axis.YP.rotationDegrees(yaw));
      ps.mulPose(Axis.XP.rotationDegrees(pitch));
      ps.mulPose(Axis.ZP.rotationDegrees(roll));

      Matrix4f m = ps.last().pose();
      int rOuter = 148, gOuter = 52, bOuter = 232;
      int rInner = 176, gInner = 82, bInner = 250;

      vc.addVertex(m, -PAGE_HALF_W, 0.0f, -PAGE_HALF_L).setColor(rOuter, gOuter, bOuter, alpha);
      vc.addVertex(m, PAGE_HALF_W, 0.0f, -PAGE_HALF_L).setColor(rInner, gInner, bInner, alpha);
      vc.addVertex(m, PAGE_HALF_W, 0.0f, PAGE_HALF_L).setColor(rInner, gInner, bInner, alpha);
      vc.addVertex(m, -PAGE_HALF_W, 0.0f, PAGE_HALF_L).setColor(rOuter, gOuter, bOuter, alpha);

      ps.popPose();
    }

    buf.endBatch(PAGE_QUAD_TYPE);
    RenderSystem.depthMask(true);
    RenderSystem.enableCull();
  }
}

