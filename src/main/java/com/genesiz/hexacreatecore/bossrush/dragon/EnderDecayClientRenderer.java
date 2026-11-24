package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.registry.ModEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class EnderDecayClientRenderer {

  private static final class DecayAfterImage {
    final AbstractClientPlayer player;
    final Vec3 worldPos;
    final float bodyYaw;
    final float headYawRel;
    final float headPitch;
    final float limbSwing;
    final float limbSwingAmount;
    final float ageInTicks;
    final float attackAnim;
    final boolean crouching;
    final float swimAmount;
    final double spawnWallSec;

    DecayAfterImage(
      AbstractClientPlayer player,
      Vec3 worldPos,
      float bodyYaw,
      float headYawRel,
      float headPitch,
      float limbSwing,
      float limbSwingAmount,
      float ageInTicks,
      float attackAnim,
      boolean crouching,
      float swimAmount,
      double spawnWallSec
    ) {
      this.player = player;
      this.worldPos = worldPos;
      this.bodyYaw = bodyYaw;
      this.headYawRel = headYawRel;
      this.headPitch = headPitch;
      this.limbSwing = limbSwing;
      this.limbSwingAmount = limbSwingAmount;
      this.ageInTicks = ageInTicks;
      this.attackAnim = attackAnim;
      this.crouching = crouching;
      this.swimAmount = swimAmount;
      this.spawnWallSec = spawnWallSec;
    }
  }

  private static final CopyOnWriteArrayList AFTER_IMAGES = new CopyOnWriteArrayList();
  private static final Map LAST_CAPTURE_SEC = new HashMap();

  private EnderDecayClientRenderer() {}

  @SubscribeEvent
  public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
    Player player = event.getEntity();
    if (!(player instanceof AbstractClientPlayer clientPlayer)) return;
    if (!player.isAlive() || !player.hasEffect(ModEffects.ENDER_DECAY)) return;

    float partialTick = event.getPartialTick();
    PoseStack ps = event.getPoseStack();
    MultiBufferSource buf = event.getMultiBufferSource();
    PlayerRenderer renderer = event.getRenderer();
    PlayerModel model = renderer.getModel();

    float bodyYaw = Mth.rotLerp(partialTick, clientPlayer.yBodyRotO, clientPlayer.yBodyRot);
    float headYaw = Mth.rotLerp(partialTick, clientPlayer.yHeadRotO, clientPlayer.yHeadRot);
    float headPitch = Mth.lerp(partialTick, clientPlayer.xRotO, clientPlayer.getXRot());
    float headYawRel = Mth.wrapDegrees(headYaw - bodyYaw);
    float limbSwing = clientPlayer.walkAnimation.position(partialTick);
    float limbSwingAmount = clientPlayer.walkAnimation.speed(partialTick);
    float ageInTicks = clientPlayer.tickCount + partialTick;

    // 1. Faint Ender-Purple Enchanted Glint over the player's body
    ps.pushPose();
    ps.scale(-1.0f, -1.0f, 1.0f);
    ps.translate(0.0D, -1.501D, 0.0D);
    ps.mulPose(Axis.YP.rotationDegrees(180.0f + bodyYaw));

    model.attackTime = clientPlayer.getAttackAnim(partialTick);
    model.crouching = clientPlayer.isCrouching();
    model.swimAmount = clientPlayer.getSwimAmount(partialTick);
    model.setupAnim(clientPlayer, limbSwing, limbSwingAmount, ageInTicks, headYawRel, headPitch);

    float pulse = 0.75f + 0.25f * Mth.sin(ageInTicks * 0.25f);
    int glintAlpha = Mth.clamp((int) (78.0f * pulse), 35, 110); // Faint ender-purple shimmer
    int glintColor = FastColor.ARGB32.color(glintAlpha, 196, 77, 255);

    VertexConsumer glintVc = buf.getBuffer(RenderType.entityGlint());
    model.renderToBuffer(ps, glintVc, 15728880, OverlayTexture.NO_OVERLAY, glintColor);
    ps.popPose();

    // 2. Capture a static after-image every 0.5 seconds
    double nowSec = System.nanoTime() * 1.0e-9D;
    Double prevSec = (Double) LAST_CAPTURE_SEC.get(clientPlayer.getUUID());
    if (prevSec == null || (nowSec - prevSec) >= 0.50D) {
      LAST_CAPTURE_SEC.put(clientPlayer.getUUID(), nowSec);
      AFTER_IMAGES.add(new DecayAfterImage(
        clientPlayer,
        clientPlayer.getPosition(partialTick),
        bodyYaw,
        headYawRel,
        headPitch,
        limbSwing,
        limbSwingAmount,
        ageInTicks,
        clientPlayer.getAttackAnim(partialTick),
        clientPlayer.isCrouching(),
        clientPlayer.getSwimAmount(partialTick),
        nowSec
      ));
    }
  }

  @SubscribeEvent
  public static void onRenderWorldAfterEntities(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    if (AFTER_IMAGES.isEmpty()) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null) return;

    Vec3 cam = mc.getEntityRenderDispatcher().camera.getPosition();
    PoseStack ps = event.getPoseStack();
    MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
    double nowSec = System.nanoTime() * 1.0e-9D;

    Iterator it = AFTER_IMAGES.iterator();
    while (it.hasNext()) {
      DecayAfterImage snap = (DecayAfterImage) it.next();
      double ageSec = nowSec - snap.spawnWallSec;
      if (ageSec >= 3.0D || snap.player.level() != mc.level) {
        AFTER_IMAGES.remove(snap);
        continue;
      }

      if (!(mc.getEntityRenderDispatcher().getRenderer(snap.player) instanceof PlayerRenderer renderer)) {
        continue;
      }

      // Fade smoothly from 60% opacity to 0% over 3.0 seconds
      float fade = Mth.clamp(1.0f - (float) (ageSec / 3.0D), 0.0f, 1.0f);
      float opacity = 0.60f * fade;
      int alpha = Mth.clamp((int) (opacity * 255.0f), 0, 255);
      if (2 >= alpha) continue;

      PlayerModel model = renderer.getModel();
      ResourceLocation skin = renderer.getTextureLocation(snap.player);

      boolean prevCrouch = model.crouching;
      float prevSwim = model.swimAmount;
      float prevAttack = model.attackTime;

      ps.pushPose();
      ps.translate(snap.worldPos.x - cam.x, snap.worldPos.y - cam.y, snap.worldPos.z - cam.z);
      ps.scale(-0.9375f, -0.9375f, 0.9375f);
      ps.translate(0.0D, -1.501D, 0.0D);
      ps.mulPose(Axis.YP.rotationDegrees(180.0f + snap.bodyYaw));

      model.young = false;
      model.crouching = snap.crouching;
      model.swimAmount = snap.swimAmount;
      model.attackTime = snap.attackAnim;
      model.setupAnim(snap.player, snap.limbSwing, snap.limbSwingAmount, snap.ageInTicks, snap.headYawRel, snap.headPitch);

      int purpleTint = FastColor.ARGB32.color(alpha, 192, 92, 255);
      VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(skin));
      model.renderToBuffer(ps, vc, 15728880, OverlayTexture.NO_OVERLAY, purpleTint);

      ps.popPose();

      // Restore live model state so the actual player renderer is unaffected
      model.crouching = prevCrouch;
      model.swimAmount = prevSwim;
      model.attackTime = prevAttack;
    }

    buf.endBatch();
  }
}

