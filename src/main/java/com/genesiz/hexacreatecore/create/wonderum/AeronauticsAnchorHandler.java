package com.genesiz.hexacreatecore.create.wonderum;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.item.ModItems;
import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class AeronauticsAnchorHandler {

  public static final double ANCHOR_RANGE = 24.0D;
  private static final long EPOCH_NANOS = System.nanoTime();

  private AeronauticsAnchorHandler() {}

  public static boolean tryAnchorSubLevelClient(Level level, Player player) {
    if (!level.isClientSide) return false;
    HitResult hit = player.pick(ANCHOR_RANGE, 1.0f, false);
    if (hit == null || hit.getType() == HitResult.Type.MISS) return false;

    Vec3 hitLocation = hit.getLocation();
    Object subLevel = getContainingClientSubLevel(level, hitLocation);
    if (subLevel == null) return false;

    return sendAnchorPacket(level, subLevel, hitLocation);
  }

  private static boolean sendAnchorPacket(Level level, Object subLevel, Vec3 hitLocation) {
    try {
      UUID subLevelId = (UUID) subLevel.getClass().getMethod("getUniqueId").invoke(subLevel);
      PacketDistributor.sendToServer(new WonderumAnchorPayload(subLevelId));

      for (int i = 0; 10 > i; i++) {
        level.addParticle(
          ParticleTypes.END_ROD,
          hitLocation.x, hitLocation.y, hitLocation.z,
          (level.random.nextDouble() - 0.5D) * 0.18D,
          (level.random.nextDouble() - 0.5D) * 0.18D,
          (level.random.nextDouble() - 0.5D) * 0.18D
        );
      }
      return true;
    } catch (Throwable t) {
      t.printStackTrace();
      return false;
    }
  }

  public static Object getContainingClientSubLevel(Level level, Vec3 hitLocation) {
    try {
      Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
      Object helper = sableClass.getField("HELPER").get(null);
      for (Method m : helper.getClass().getMethods()) {
        if (m.getName().contains("Containing")) {
          List<Class<?>> params = Arrays.asList(m.getParameterTypes());
          if (params.size() == 1 && params.get(0).isAssignableFrom(Vec3.class)) {
            Object res = m.invoke(helper, hitLocation);
            if (res != null) return res;
          }
          if (params.size() == 2 && params.get(0).isAssignableFrom(Level.class) && params.get(1).isAssignableFrom(Vec3.class)) {
            Object res = m.invoke(helper, level, hitLocation);
            if (res != null) return res;
          }
        }
      }
    } catch (Throwable ignored) {}
    return null;
  }

  @SubscribeEvent
  public static void onClientUseKey(InputEvent.InteractionKeyMappingTriggered event) {
    if (!event.isUseItem()) return;
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null || mc.level == null) return;
    if (!mc.player.getItemInHand(event.getHand()).is(ModItems.WONDERUM.get())) return;

    if (tryAnchorSubLevelClient(mc.level, mc.player)) {
      event.setSwingHand(true);
      event.setCanceled(true);
    }
  }

  @SubscribeEvent
  public static void onRenderWorld(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null || mc.level == null) return;
    if (!mc.player.getMainHandItem().is(ModItems.WONDERUM.get()) && !mc.player.getOffhandItem().is(ModItems.WONDERUM.get())) return;

    try {
      Class<?> simClientClass = Class.forName("dev.simulated_team.simulated.SimulatedClient");
      Object staffHandler = simClientClass.getField("PHYSICS_STAFF_CLIENT_HANDLER").get(null);
      Method getLocks = staffHandler.getClass().getDeclaredMethod("getLocks", Level.class);
      getLocks.setAccessible(true);
      @SuppressWarnings("unchecked")
      List<UUID> locks = (List<UUID>) getLocks.invoke(staffHandler, mc.level);
      if (locks == null || locks.isEmpty()) return;

      Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
      Object container = containerClass.getMethod("getContainer", Level.class).invoke(null, mc.level);
      Method getSubLevel = containerClass.getMethod("getSubLevel", UUID.class);

      Vec3 cam = mc.getEntityRenderDispatcher().camera.getPosition();
      PoseStack ps = event.getPoseStack();
      MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
      float timeSec = (float) ((System.nanoTime() - EPOCH_NANOS) * 1.0e-9D);

      for (UUID lockId : locks) {
        Object sub = getSubLevel.invoke(container, lockId);
        if (sub == null) continue;

        Vec3 worldPos = findOpenAirWorldAboveContraption(sub);
        if (cam.distanceToSqr(worldPos) > 128.0D * 128.0D) continue;

        float bobY = (float) Math.sin(timeSec * 2.6f) * 0.08f;
        float spinDeg = (timeSec * 75.0f) % 360.0f;

        ps.pushPose();
        ps.translate(worldPos.x - cam.x, worldPos.y - cam.y + 0.45D + bobY, worldPos.z - cam.z);
        ps.mulPose(Axis.YP.rotationDegrees(spinDeg));

        draw3DPadlock(ps, buf.getBuffer(RawGeometry.DIALOGUE_SOLID), 0.28f);
        ps.popPose();
      }
      buf.endBatch(RawGeometry.DIALOGUE_SOLID);
    } catch (Throwable ignored) {}
  }

  private static Vec3 findOpenAirWorldAboveContraption(Object sub) throws Exception {
    Class<?> clientSubIface = Class.forName("dev.ryanhcode.sable.sublevel.ClientSubLevel");
    Class<?> poseIface = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
    Class<?> vec3dcIface = Class.forName("org.joml.Vector3dc");

    Object pose = clientSubIface.getMethod("renderPose").invoke(sub);

    // 1. Read SubLevel-local pivot from Pose3dc#rotationPoint()
    Object rotPt = poseIface.getMethod("rotationPoint").invoke(pose);
    double lx = (double) vec3dcIface.getMethod("x").invoke(rotPt);
    double ly = (double) vec3dcIface.getMethod("y").invoke(rotPt);
    double lz = (double) vec3dcIface.getMethod("z").invoke(rotPt);

    // 2. Scan all non-air blocks around the SubLevel-local pivot to find exact (lenX/2, lenY/2, lenZ/2) center & top air block
    net.minecraft.world.level.BlockGetter subBlocks =
      (net.minecraft.world.level.BlockGetter) clientSubIface.getMethod("getLevel").invoke(sub);
    BlockPos seed = BlockPos.containing(lx, ly, lz);

    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
    boolean foundAny = false;

    for (int dx = -16; 16 >= dx; dx++) {
      for (int dy = -16; 16 >= dy; dy++) {
        for (int dz = -16; 16 >= dz; dz++) {
          BlockPos p = seed.offset(dx, dy, dz);
          if (!subBlocks.getBlockState(p).isAir()) {
            foundAny = true;
            if (minX > p.getX()) minX = p.getX();
            if (p.getX() > maxX) maxX = p.getX();
            if (minY > p.getY()) minY = p.getY();
            if (p.getY() > maxY) maxY = p.getY();
            if (minZ > p.getZ()) minZ = p.getZ();
            if (p.getZ() > maxZ) maxZ = p.getZ();
          }
        }
      }
    }

    double centerX = foundAny ? (minX + maxX + 1.0D) * 0.5D : lx;
    double midY = foundAny ? (minY + maxY + 1.0D) * 0.5D : ly;
    double centerZ = foundAny ? (minZ + maxZ + 1.0D) * 0.5D : lz;

    BlockPos check = BlockPos.containing(centerX, midY, centerZ);
    for (int step = 0; 64 > step; step++) {
      if (subBlocks.getBlockState(check).isAir()) {
        Vec3 localAirPos = new Vec3(centerX, check.getY() + 0.35D, centerZ);
        return (Vec3) poseIface.getMethod("transformPosition", Vec3.class).invoke(pose, localAirPos);
      }
      check = check.above();
    }

    Vec3 fallbackLocal = new Vec3(centerX, maxY + 1.35D, centerZ);
    return (Vec3) poseIface.getMethod("transformPosition", Vec3.class).invoke(pose, fallbackLocal);
  }

  private static void draw3DPadlock(PoseStack ps, VertexConsumer vc, float s) {
    // 1. 3D Padlock Body (Silver-Platinum block)
    ps.pushPose();
    ps.translate(0.0f, -s * 0.325f, 0.0f);
    RawGeometry.drawPrism(vc, ps.last().pose(), s * 1.30f, s * 0.85f, s * 0.48f, 0xFFF8FAFC);
    ps.popPose();

    // 2. Dark Void Keyhole Recess passing through Front & Back faces
    ps.pushPose();
    ps.translate(0.0f, -s * 0.33f, 0.0f);
    RawGeometry.drawPrism(vc, ps.last().pose(), s * 0.24f, s * 0.32f, s * 0.51f, 0xFF0F172A);
    ps.popPose();

    // 3. 3D Extruded Cyan U-Shackle (Left Pillar, Right Pillar, Top Arch)
    int shackleColor = 0xFF38BDF8;

    ps.pushPose();
    ps.translate(-s * 0.34f, s * 0.41f, 0.0f);
    RawGeometry.drawPrism(vc, ps.last().pose(), s * 0.20f, s * 0.62f, s * 0.24f, shackleColor);
    ps.popPose();

    ps.pushPose();
    ps.translate(s * 0.34f, s * 0.41f, 0.0f);
    RawGeometry.drawPrism(vc, ps.last().pose(), s * 0.20f, s * 0.62f, s * 0.24f, shackleColor);
    ps.popPose();

    ps.pushPose();
    ps.translate(0.0f, s * 0.65f, 0.0f);
    RawGeometry.drawPrism(vc, ps.last().pose(), s * 0.88f, s * 0.22f, s * 0.24f, shackleColor);
    ps.popPose();
  }
}
