package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.CosmicOceanDictionary;
import com.genesiz.hexacreatecore.threedified.shaders.hooks.PolyhedronFaceShaderHook;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@EventBusSubscriber(modid = HexaCreateCore.MODID)
public final class DragonHexShieldManager {

  private record Edge(int a, int b) {}

  public static final class HexShieldInstance {
    public final UUID dragonId;
    public final boolean isCluster;
    public final float yawOffsetDeg;
    public Vec3 prevPos;
    public Vec3 pos;
    public Vec3 forward;
    public Vec3 right;
    public Vec3 up;
    public Vec3 velocity = Vec3.ZERO;
    public final float scale;
    public boolean launched = false;
    public boolean damageActive = false;
    public int ageTicks = 0;
    public final int maxAgeTicks;
    public final Set<UUID> hitPlayers = ConcurrentHashMap.newKeySet();
    public final Set<Integer> deflectedProjectiles = ConcurrentHashMap.newKeySet();

    public HexShieldInstance(
      UUID dragonId, boolean isCluster, float yawOffsetDeg,
      Vec3 pos, Vec3 forward, float scale, int maxAgeTicks
    ) {
      this.dragonId = dragonId;
      this.isCluster = isCluster;
      this.yawOffsetDeg = yawOffsetDeg;
      this.prevPos = pos;
      this.pos = pos;
      this.scale = scale;
      this.maxAgeTicks = maxAgeTicks;
      updateOrientation(forward);
    }

    public void updateOrientation(Vec3 forwardDir) {
      this.forward = forwardDir.normalize();
      Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
      Vec3 r = worldUp.cross(this.forward);
      if (r.lengthSqr() < 1.0e-5D) {
        r = new Vec3(1.0D, 0.0D, 0.0D);
      }
      this.right = r.normalize();
      this.up = this.forward.cross(this.right).normalize();
    }
  }

  public static void activateShieldDamage(EnderDragon dragon) {
    for (var s : ACTIVE_SHIELDS) {
      if (s.dragonId.equals(dragon.getUUID())) {
        s.damageActive = true;
      }
    }
  }

  private static final CopyOnWriteArrayList<HexShieldInstance> ACTIVE_SHIELDS = new CopyOnWriteArrayList<>();

  private static final List<Vec3> UNIT_HEX_VERTS = java.util.stream.IntStream.range(0, 6)
  .mapToObj(i -> {
    double angle = Math.toRadians(60.0D * i + 30.0D);
    return new Vec3(Math.cos(angle), Math.sin(angle), 0.0D);
  })
  .toList();

  private static final List<List<Vec3>> CLUSTER_LOCAL_FACES = List.of(
    List.of(
      new Vec3( 0.20177D, -0.34948D,  0.17474D),
      new Vec3( 0.40355D,  0.00000D,  0.17474D),
      new Vec3( 0.20177D,  0.34948D,  0.17474D),
      new Vec3(-0.20177D,  0.34948D,  0.17474D),
      new Vec3(-0.40355D,  0.00000D,  0.17474D),
      new Vec3(-0.20177D, -0.34948D,  0.17474D)
    ),
    List.of(
      new Vec3(-0.20177D,  0.34948D,  0.17474D),
      new Vec3( 0.20177D,  0.34948D,  0.17474D),
      new Vec3( 0.40355D,  0.60997D, -0.05825D),
      new Vec3( 0.20177D,  0.87046D, -0.29124D),
      new Vec3(-0.20177D,  0.87046D, -0.29124D),
      new Vec3(-0.40355D,  0.60997D, -0.05825D)
    ),
    List.of(
      new Vec3( 0.40355D,  0.00000D,  0.17474D),
      new Vec3( 0.20177D, -0.34948D,  0.17474D),
      new Vec3( 0.32648D, -0.65447D, -0.05825D),
      new Vec3( 0.65295D, -0.60997D, -0.29124D),
      new Vec3( 0.85473D, -0.26049D, -0.29124D),
      new Vec3( 0.73003D,  0.04450D, -0.05825D)
    ),
    List.of(
      new Vec3(-0.20177D, -0.34948D,  0.17474D),
      new Vec3(-0.40355D,  0.00000D,  0.17474D),
      new Vec3(-0.73003D,  0.04450D, -0.05825D),
      new Vec3(-0.85473D, -0.26049D, -0.29124D),
      new Vec3(-0.65295D, -0.60997D, -0.29124D),
      new Vec3(-0.32648D, -0.65447D, -0.05825D)
    )
  );

  private static final List<Vec3> CLUSTER_UNIQUE_VERTS;
  private static final List<Edge> CLUSTER_UNIQUE_EDGES;

  static {
    var verts = new ArrayList<Vec3>();
    var edges = new ArrayList<Edge>();

    for (var face : CLUSTER_LOCAL_FACES) {
      var faceIdx = new ArrayList<Integer>(face.size());
      for (var v : face) {
        int found = -1;
        for (int j = 0; verts.size() > j; j++) {
          if (verts.get(j).distanceToSqr(v) < 1.0e-6D) {
            found = j;
            break;
          }
        }
        if (found == -1) {
          found = verts.size();
          verts.add(v);
        }
        faceIdx.add(found);
      }

      for (int i = 0; faceIdx.size() > i; i++) {
        int a = faceIdx.get(i);
        int b = faceIdx.get((i + 1) % faceIdx.size());
        boolean exists = false;
        for (var e : edges) {
          if ((e.a() == a && e.b() == b) || (e.a() == b && e.b() == a)) {
            exists = true;
            break;
          }
        }
        if (!exists) {
          edges.add(new Edge(a, b));
        }
      }
    }

    CLUSTER_UNIQUE_VERTS = List.copyOf(verts);
    CLUSTER_UNIQUE_EDGES = List.copyOf(edges);
  }

  private DragonHexShieldManager() {}

  public static List<HexShieldInstance> getActiveShields() {
    return ACTIVE_SHIELDS;
  }

  private static Vec3 getClusterAimDir(EnderDragon dragon) {
    return Vec3.directionFromRotation(40.0f, dragon.getYRot()).normalize();
  }

  private static Vec3 getHorizontalSpreadDir(EnderDragon dragon, float yawOffsetDeg) {
    return Vec3.directionFromRotation(0.0f, dragon.getYRot() + yawOffsetDeg).normalize();
  }

  public static void spawnClusterShield(ServerLevel level, EnderDragon dragon) {
    ACTIVE_SHIELDS.removeIf(s -> s.dragonId.equals(dragon.getUUID()) && !s.launched);
    Vec3 aimDir = getClusterAimDir(dragon);
    Vec3 spawnPos = dragon.position().add(aimDir.scale(4.4D)).add(0.0D, 6.4D, 0.0D);
    ACTIVE_SHIELDS.add(new HexShieldInstance(dragon.getUUID(), true, 0.0f, spawnPos, aimDir, 4.65f, 95));
    level.playSound(null, dragon.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.5f, 1.35f);
  }

  public static void spawnSpreadShields(ServerLevel level, EnderDragon dragon) {
    ACTIVE_SHIELDS.removeIf(s -> s.dragonId.equals(dragon.getUUID()) && !s.launched);
    for (float offsetDeg : List.of(0.0f, -45.0f, 45.0f)) {
      Vec3 dir = getHorizontalSpreadDir(dragon, offsetDeg);
      Vec3 spawnPos = dragon.position().add(dir.scale(5.4D)).add(0.0D, 2.1D, 0.0D);
      ACTIVE_SHIELDS.add(new HexShieldInstance(dragon.getUUID(), false, offsetDeg, spawnPos, dir, 1.85f, 95));
    }
    level.playSound(null, dragon.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.5f, 1.55f);
  }

  public static void launchShields(ServerLevel level, EnderDragon dragon, boolean spreadAngles) {
    for (var s : ACTIVE_SHIELDS) {
      if (!s.dragonId.equals(dragon.getUUID()) || s.launched) continue;
      s.launched = true;
      if (s.isCluster || !spreadAngles) {
        Vec3 aimDir = getClusterAimDir(dragon);
        s.updateOrientation(aimDir);
        s.velocity = aimDir.scale(1.55D);
      } else {
        Vec3 launchDir = getHorizontalSpreadDir(dragon, s.yawOffsetDeg);
        s.updateOrientation(launchDir);
        s.velocity = launchDir.scale(1.55D);
      }
    }
    level.playSound(null, dragon.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.4f, 1.25f);
  }

  @SubscribeEvent
  public static void onServerTick(ServerTickEvent.Post event) {
    if (ACTIVE_SHIELDS.isEmpty()) return;
    for (var s : ACTIVE_SHIELDS) {
      s.prevPos = s.pos;
      s.ageTicks++;
      if (s.ageTicks > s.maxAgeTicks) {
        ACTIVE_SHIELDS.remove(s);
        continue;
      }

      ServerLevel level = null;
      EnderDragon ownerDragon = null;
      for (ServerLevel sl : event.getServer().getAllLevels()) {
        if (sl.getEntity(s.dragonId) instanceof EnderDragon ed && ed.isAlive()) {
          level = sl;
          ownerDragon = ed;
          break;
        }
      }
      if (level == null || ownerDragon == null) {
        ACTIVE_SHIELDS.remove(s);
        continue;
      }

      if (!s.launched) {
        if (s.isCluster) {
          Vec3 aimDir = getClusterAimDir(ownerDragon);
          s.updateOrientation(aimDir);
          s.pos = ownerDragon.position().add(aimDir.scale(4.4D)).add(0.0D, 6.4D, 0.0D);
        } else {
          Vec3 dir = getHorizontalSpreadDir(ownerDragon, s.yawOffsetDeg);
          s.updateOrientation(dir);
          s.pos = ownerDragon.position().add(dir.scale(5.4D)).add(0.0D, 2.1D, 0.0D);
        }
      } else {
        s.pos = s.pos.add(s.velocity);
        BlockPos hitBlock = BlockPos.containing(s.pos);
        if (!level.getBlockState(hitBlock).isAir() && !level.getBlockState(hitBlock).getCollisionShape(level, hitBlock).isEmpty()) {
          level.playSound(null, hitBlock, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.2f, 0.65f);
          level.sendParticles(ParticleTypes.WITCH, s.pos.x, s.pos.y + 0.3D, s.pos.z, 35, 1.8D, 0.6D, 1.8D, 0.08D);
          if (s.isCluster) {
            DragonPlasmaManager.spawnPlasmaPatch(level, s.pos);
          }
          ACTIVE_SHIELDS.remove(s);
          continue;
        }
      }

      double halfSpan = s.isCluster ? (s.scale * 0.85D) : (s.scale * 1.15D);
      AABB shieldBox = AABB.ofSize(s.pos, halfSpan * 2.0D, halfSpan * 2.0D, halfSpan * 2.0D);

      // 1. Deflect projectiles immediately on spawn (0.50s+) by inverting their exact velocity vector
      for (Projectile proj : level.getEntitiesOfClass(Projectile.class, shieldBox.inflate(0.75D))) {
        if (!proj.isAlive() || proj.getOwner() == ownerDragon || proj instanceof net.minecraft.world.entity.projectile.ThrownEnderpearl) continue;
        if (!s.deflectedProjectiles.add(proj.getId())) continue;
        Vec3 invVel = proj.getDeltaMovement().scale(-1.0D);
        if (invVel.lengthSqr() < 0.01D) invVel = s.forward.scale(1.4D);
        proj.setDeltaMovement(invVel);
        proj.setYRot(Mth.wrapDegrees(proj.getYRot() + 180.0f));
        proj.setXRot(-proj.getXRot());
        proj.setOwner(ownerDragon);
        proj.hasImpulse = true;
        level.playSound(null, proj.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.6f, 1.45f);
        level.sendParticles(ParticleTypes.END_ROD, proj.getX(), proj.getY(), proj.getZ(), 10, 0.2D, 0.2D, 0.2D, 0.08D);
      }

      // 2. Deal 3 hearts (6.0 HP) on Full Netherite Prot IV in Ethereal (27.9f raw) only at >= 1.85s
      if (s.damageActive || s.ageTicks >= 27) {
        for (Player p : level.getEntitiesOfClass(Player.class, shieldBox)) {
          if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
          if (s.hitPlayers.add(p.getUUID())) {
            p.hurt(level.damageSources().mobAttack(ownerDragon), 27.9f);
            Vec3 knock = s.forward.scale(1.65D).add(0.0D, 0.48D, 0.0D);
            p.push(knock.x, knock.y, knock.z);
            p.hurtMarked = true;
          }
        }
      }
    }
  }

  private static Vec3 localToCameraWorld(HexShieldInstance s, Vec3 centerCamRel, Vec3 local, float scale) {
    return centerCamRel
    .add(s.right.scale(local.x * scale))
    .add(s.up.scale(local.y * scale))
    .add(s.forward.scale(local.z * scale));
  }
  public static void renderAll(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam, float partialTick, float timeSec) {
    if (ACTIVE_SHIELDS.isEmpty()) return;
    for (var s : ACTIVE_SHIELDS) {
      Vec3 interpPos = s.prevPos.lerp(s.pos, partialTick);
      Vec3 centerCamRel = interpPos.subtract(cam);
      float smoothAge = s.ageTicks + partialTick;
      float scaleIn = Mth.clamp(smoothAge / 6.0f, 0.1f, 1.0f);
      float curScale = s.scale * scaleIn;
      if (s.isCluster) {
        var unitPolys = CLUSTER_LOCAL_FACES.stream()
        .map(face -> face.stream()
          .map(lv -> s.right.scale(lv.x).add(s.up.scale(lv.y)).add(s.forward.scale(lv.z)))
          .toList())
        .toList();
        ps.pushPose();
        ps.translate(centerCamRel.x, centerCamRel.y, centerCamRel.z);
        PolyhedronFaceShaderHook
          .bind(CosmicOceanDictionary.DEFAULT)
          .opacity(0.46f)
          .rings(3)
          .renderFaces(ps, buf, unitPolys, curScale, timeSec);
        ps.popPose();
        var camVerts = CLUSTER_UNIQUE_VERTS.stream()
        .map(lv -> localToCameraWorld(s, centerCamRel, lv, curScale))
        .toList();
        for (Vec3 wv : camVerts) {
          RawGeometry.drawArcaneNodePrism(ps, buf, (float) wv.x, (float) wv.y, (float) wv.z, 0.055f, 0xDDE066FF);
        }
        VertexConsumer lineConsumer = buf.getBuffer(RenderType.lines());
        Matrix4f mat = ps.last().pose();
        for (var edge : CLUSTER_UNIQUE_EDGES) {
          Vec3 v0 = camVerts.get(edge.a());
          Vec3 v1 = camVerts.get(edge.b());
          lineConsumer.addVertex(mat, (float) v0.x, (float) v0.y, (float) v0.z)
            .setColor(224, 102, 255, 220).setNormal(0.0f, 1.0f, 0.0f);
          lineConsumer.addVertex(mat, (float) v1.x, (float) v1.y, (float) v1.z)
            .setColor(52, 211, 153, 220).setNormal(0.0f, 1.0f, 0.0f);
        }
      } else {
        var unitPoly = UNIT_HEX_VERTS.stream()
        .map(uv -> s.right.scale(uv.x).add(s.up.scale(uv.y)))
        .toList();
        var camVerts = unitPoly.stream()
        .map(dir -> centerCamRel.add(dir.scale(curScale)))
        .toList();
        ps.pushPose();
        ps.translate(centerCamRel.x, centerCamRel.y, centerCamRel.z);
        PolyhedronFaceShaderHook
          .bind(CosmicOceanDictionary.DEFAULT)
          .opacity(0.46f)
          .rings(3)
          .renderFaces(ps, buf, List.of(unitPoly), curScale, timeSec);
        ps.popPose();
        for (Vec3 v0 : camVerts) {
          RawGeometry.drawArcaneNodePrism(ps, buf, (float) v0.x, (float) v0.y, (float) v0.z, 0.055f, 0xDDE066FF);
        }
        VertexConsumer lineConsumer = buf.getBuffer(RenderType.lines());
        Matrix4f mat = ps.last().pose();
        for (int i = 0; 6 > i; i++) {
          Vec3 v0 = camVerts.get(i);
          Vec3 v1 = camVerts.get((i + 1) % 6);
          lineConsumer.addVertex(mat, (float) v0.x, (float) v0.y, (float) v0.z)
            .setColor(224, 102, 255, 220).setNormal(0.0f, 1.0f, 0.0f);
          lineConsumer.addVertex(mat, (float) v1.x, (float) v1.y, (float) v1.z)
            .setColor(52, 211, 153, 220).setNormal(0.0f, 1.0f, 0.0f);
        }
      }
    }
    buf.endBatch();
  }
}
