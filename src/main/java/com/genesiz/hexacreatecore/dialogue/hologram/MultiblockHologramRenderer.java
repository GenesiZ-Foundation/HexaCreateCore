package com.genesiz.hexacreatecore.dialogue.hologram;

import com.genesiz.hexacreatecore.dialogue.network.HologramProjectionPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

@EventBusSubscriber(modid = "hexacreatecore", value = Dist.CLIENT)
public final class MultiblockHologramRenderer {
  private static String activeStructureId = null;
  private static BlockPos anchorPos = null;
  private static int remainingTicks = 0;
  private static float elapsedSeconds = 0.0f;

  // Reusable scratch objects (zero per-frame heap allocations)
  private static final BlockPos.MutableBlockPos CURSOR_POS = new BlockPos.MutableBlockPos();
  private static final RandomSource RANDOM = RandomSource.create(42L);
  private static final GhostVertexConsumer GHOST_CONSUMER = new GhostVertexConsumer();

  private static boolean pendingPlacement = false;
  private static float promptYaw = 0.0f;
  private static float promptPitch = 0.0f;
  private static java.util.List<net.minecraft.util.FormattedCharSequence> promptLines = null;
  private static float promptBoxWidth = 0.0f;
  private static float promptBoxHeight = 0.0f;

  public static void setProjection(HologramProjectionPayload payload) {
    if (payload.durationTicks() == -1) {
      pendingPlacement = true;
      activeStructureId = null;
      anchorPos = null;
      remainingTicks = 0;

      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
        promptYaw = mc.player.getYRot();
        promptPitch = mc.player.getXRot();
      }
    } else if (payload.durationTicks() == 0 || payload.structureId().isEmpty()) {
      pendingPlacement = false;
      activeStructureId = null;
      anchorPos = null;
      remainingTicks = 0;
    } else {
      // Blueprint anchored in world: hide prompt and start bottom-to-top build animation
      pendingPlacement = false;
      activeStructureId = payload.structureId();
      anchorPos = payload.anchorPos();
      remainingTicks = payload.durationTicks();
      elapsedSeconds = 0.0f;
    }
  }

  @SubscribeEvent
  public static void onClientTick(ClientTickEvent.Pre event) {
    if (remainingTicks > 0) {
      remainingTicks--;
      if (remainingTicks == 0) {
        activeStructureId = null;
        anchorPos = null;
      }
    }
  }

  @SubscribeEvent
  public static void onRenderLevelStage(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;

    // Check placement prompt FIRST (because activeStructureId & anchorPos are null while waiting to place!)
    if (pendingPlacement) {
      renderPlacementPrompt(event.getPoseStack(), mc);
      return;
    }

    if (activeStructureId == null || anchorPos == null) return;

    MultiblockBlueprintData blueprint = BlueprintReloadListener.getBlueprint(activeStructureId);
    if (blueprint == null) return;


    float deltaSeconds = mc.getTimer().getRealtimeDeltaTicks() / 20.0f;
    elapsedSeconds += deltaSeconds;

    PoseStack poseStack = event.getPoseStack();
    Vec3 cam = mc.getEntityRenderDispatcher().camera.getPosition();
    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
    BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();

    RenderType ghostRenderType = Sheets.translucentCullBlockSheet();
    VertexConsumer baseBuffer = bufferSource.getBuffer(ghostRenderType);

    int[] offX = blueprint.offsetX();
    int[] offY = blueprint.offsetY();
    int[] offZ = blueprint.offsetZ();
    BlockState[] states = blueprint.states();

    final float layerStaggerSec = 0.12f;
    final float animDurationSec = 0.35f;
    final float dropOffsetBlocks = 0.65f;

    for (int i = 0; blueprint.blockCount() > i; i++) {
      // Bottom-to-top cascade (Y = 0 starts first, higher layers stagger upward)
      float delay = offY[i] * layerStaggerSec;
      float rawProgress = (elapsedSeconds - delay) / animDurationSec;
      if (0.0f >= rawProgress) continue;

      float t = rawProgress >= 1.0f ? 1.0f : rawProgress;
      float inv = 1.0f - t;
      float ease = 1.0f - (inv * inv * inv); // Cubic ease-out

      BlockState expectedState = states[i];
      CURSOR_POS.set(anchorPos.getX() + offX[i], anchorPos.getY() + offY[i], anchorPos.getZ() + offZ[i]);

      BlockState worldState = mc.level.getBlockState(CURSOR_POS);

      // 1. Correct block already placed -> hide ghost
      if (worldState.is(expectedState.getBlock())) continue;

      int currentAlpha = (int) (140.0f * ease);
      if (1 > currentAlpha) continue;

      // 2. Wrong non-air block placed -> wrap in red error shell
      boolean isWrongBlock = !worldState.isAir();

      poseStack.pushPose();
      if (isWrongBlock) {
        GHOST_CONSUMER.wrap(baseBuffer, Math.max(currentAlpha, 160), 1.0f, 0.2f, 0.2f);
        poseStack.translate(
            CURSOR_POS.getX() - cam.x - 0.005D,
            CURSOR_POS.getY() - cam.y - 0.005D,
            CURSOR_POS.getZ() - cam.z - 0.005D
        );
        poseStack.scale(1.01f, 1.01f, 1.01f);
      } else {
        GHOST_CONSUMER.wrap(baseBuffer, currentAlpha, 1.0f, 1.0f, 1.0f);
        double animatedDropY = (1.0f - ease) * dropOffsetBlocks;
        poseStack.translate(
            CURSOR_POS.getX() - cam.x + 0.01D,
            CURSOR_POS.getY() - cam.y + 0.01D + animatedDropY,
            CURSOR_POS.getZ() - cam.z + 0.01D
        );
        poseStack.scale(0.98f, 0.98f, 0.98f);
      }

      blockRenderer.renderBatched(
          expectedState,
          CURSOR_POS,
          mc.level,
          poseStack,
          GHOST_CONSUMER,
          false,
          RANDOM,
          ModelData.EMPTY,
          ghostRenderType
      );

      poseStack.popPose();
    }

    bufferSource.endBatch(ghostRenderType);
  }


  private static void renderPlacementPrompt(PoseStack poseStack, Minecraft mc) {
    if (promptLines == null) {
      String label = "Use to place hologram";
      promptLines = mc.font.split(net.minecraft.network.chat.Component.literal(label), 160);
      promptBoxWidth = mc.font.width(label) + 16.0f;
      promptBoxHeight = mc.font.lineHeight + 12.0f;
    }

    var camera = mc.getEntityRenderDispatcher().camera;
    float deltaSeconds = mc.getTimer().getRealtimeDeltaTicks() / 20.0f;
    float factor = 1.0f - (float) Math.exp(-18.0f * deltaSeconds);

    promptYaw += net.minecraft.util.Mth.wrapDegrees(camera.getYRot() - promptYaw) * factor;
    promptPitch += (camera.getXRot() - promptPitch) * factor;

    // Compute world-relative position 1.4 blocks in front of the player's lagged look direction
    float yawRad = (float) Math.toRadians(promptYaw);
    float pitchRad = (float) Math.toRadians(promptPitch);

    // Standard Minecraft forward vector from yaw & pitch
    double forwardX = -Math.sin(yawRad) * Math.cos(pitchRad);
    double forwardY = -Math.sin(pitchRad);
    double forwardZ = Math.cos(yawRad) * Math.cos(pitchRad);

    // Right vector (perpendicular to forward on XZ plane)
    double rightX = -Math.cos(yawRad);
    double rightZ = -Math.sin(yawRad);

    // Position 1.4 blocks forward, 0.35 blocks right, 0.18 blocks down from eye position
    double worldRelX = (forwardX * 1.4D) + (rightX * 0.35D);
    double worldRelY = (forwardY * 1.4D) - 0.18D;
    double worldRelZ = (forwardZ * 1.4D) + (rightZ * 0.35D);

    poseStack.pushPose();
    poseStack.translate(worldRelX, worldRelY, worldRelZ);

    // Exact same billboard orientation as HolographicPanelRenderer
    poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-promptYaw - 180.0F));
    poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-promptPitch));
    poseStack.scale(0.0065F, -0.0065F, 0.0065F);

    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
    com.genesiz.hexacreatecore.dialogue.RawGeometry.drawDialogueNode(
        poseStack,
        bufferSource,
        mc.font,
        0.0f, 0.0f, 0.0f,
        promptBoxWidth,
        promptBoxHeight,
        3.0f,
        3.0f,
        0xDD0D0814,
        0xFF00FFAA,
        promptLines,
        true
    );

    bufferSource.endBatch();
    poseStack.popPose();
  }


  private static final class GhostVertexConsumer implements VertexConsumer {
    private VertexConsumer delegate;
    private int alpha;
    private float redMul = 1.0f;
    private float greenMul = 1.0f;
    private float blueMul = 1.0f;

    void wrap(VertexConsumer delegate, int alpha, float r, float g, float b) {
      this.delegate = delegate;
      this.alpha = alpha;
      this.redMul = r;
      this.greenMul = g;
      this.blueMul = b;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
      return delegate.addVertex(x, y, z);
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
      return delegate.setColor(
      (int) (r * this.redMul),
      (int) (g * this.greenMul),
      (int) (b * this.blueMul),
        this.alpha
      );
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
      return delegate.setUv(u, v);
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
      return delegate.setUv1(u, v);
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
      return delegate.setUv2(LightTexture.FULL_BRIGHT & 0xFFFF, LightTexture.FULL_BRIGHT >> 16);
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
      return delegate.setNormal(x, y, z);
    }

    @Override
    public void putBulkData(
      PoseStack.Pose pose,
      BakedQuad quad,
      float red, float green, float blue, float alpha,
      int packedLight, int packedOverlay
    ) {
      delegate.putBulkData(
        pose, quad,
        red * this.redMul, green * this.greenMul, blue * this.blueMul,
        this.alpha / 255.0f, LightTexture.FULL_BRIGHT, packedOverlay
      );
    }

    @Override
    public void putBulkData(
      PoseStack.Pose pose,
      BakedQuad quad,
      float[] brightness,
      float red, float green, float blue, float alpha,
      int[] lightmap, int packedOverlay, boolean readAlpha
    ) {
      delegate.putBulkData(
        pose, quad, brightness,
        red * this.redMul, green * this.greenMul, blue * this.blueMul,
        this.alpha / 255.0f, lightmap, packedOverlay, false
      );
    }
  }
}
