package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class EndermanAfterImageClientRenderer {

  private static final ResourceLocation ENDERMAN_TEXTURE =
    ResourceLocation.withDefaultNamespace("textures/entity/enderman/enderman.png");

  private static final class EndermanGhost {
    final EndermanAfterImagePayload data;
    final double spawnWallSec;

    EndermanGhost(EndermanAfterImagePayload data, double spawnWallSec) {
      this.data = data;
      this.spawnWallSec = spawnWallSec;
    }
  }

  private static final CopyOnWriteArrayList GHOSTS = new CopyOnWriteArrayList();
  private static EndermanModel cachedModel = null;
  private static EnderMan dummyEnderman = null;

  private EndermanAfterImageClientRenderer() {}

  public static void spawnAfterImage(EndermanAfterImagePayload payload) {
    GHOSTS.add(new EndermanGhost(payload, System.nanoTime() * 1.0e-9D));
  }

  @SubscribeEvent
  public static void onRenderWorldAfterEntities(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    if (GHOSTS.isEmpty()) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null) return;

    if (cachedModel == null) {
      cachedModel = new EndermanModel(mc.getEntityModels().bakeLayer(ModelLayers.ENDERMAN));
    }
    if (dummyEnderman == null || dummyEnderman.level() != mc.level) {
      dummyEnderman = new EnderMan(EntityType.ENDERMAN, mc.level);
    }

    Vec3 cam = mc.getEntityRenderDispatcher().camera.getPosition();
    PoseStack ps = event.getPoseStack();
    MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
    double nowSec = System.nanoTime() * 1.0e-9D;

    Iterator it = GHOSTS.iterator();
    while (it.hasNext()) {
      EndermanGhost ghost = (EndermanGhost) it.next();
      double ageSec = nowSec - ghost.spawnWallSec;
      if (ageSec >= 3.0D) {
        GHOSTS.remove(ghost);
        continue;
      }

      // Fade smoothly from 60% opacity to 0% over 3.0 seconds
      float fade = Mth.clamp(1.0f - (float) (ageSec / 3.0D), 0.0f, 1.0f);
      int alpha = Mth.clamp((int) (0.60f * fade * 255.0f), 0, 255);
      if (2 >= alpha) continue;

      EndermanAfterImagePayload d = ghost.data;

      ps.pushPose();
      ps.translate(d.x() - cam.x, d.y() - cam.y, d.z() - cam.z);
      ps.scale(-0.9375f, -0.9375f, 0.9375f);
      ps.translate(0.0D, -1.501D, 0.0D);
      ps.mulPose(Axis.YP.rotationDegrees(180.0f + d.bodyYaw()));

      cachedModel.young = false;
      cachedModel.crouching = false;
      cachedModel.creepy = d.creepy();
      cachedModel.carrying = false;
      cachedModel.attackTime = 0.0f;
      cachedModel.setupAnim(dummyEnderman, d.limbSwing(), d.limbSwingAmount(), 0.0f, d.headYawRel(), d.headPitch());

      int purpleTint = FastColor.ARGB32.color(alpha, 192, 92, 255);
      VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(ENDERMAN_TEXTURE));
      cachedModel.renderToBuffer(ps, vc, 15728880, OverlayTexture.NO_OVERLAY, purpleTint);
      ps.popPose();
    }

    buf.endBatch();
  }
}
