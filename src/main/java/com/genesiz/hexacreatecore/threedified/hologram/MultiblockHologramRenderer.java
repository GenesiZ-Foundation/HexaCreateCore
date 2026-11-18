package com.genesiz.hexacreatecore.threedified.hologram;

import com.genesiz.hexacreatecore.threedified.network.HologramProjectionPayload;
import com.genesiz.hexacreatecore.threedified.RawGeometry;
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

  private static final BlockPos.MutableBlockPos CURSOR_POS = new BlockPos.MutableBlockPos();
  private static final RandomSource RANDOM = RandomSource.create(42L);
  private static final GhostVertexConsumer GHOST_CONSUMER = new GhostVertexConsumer();

  private static boolean pendingPlacement = false;
  private static float promptYaw = 0.0f;
  private static float promptPitch = 0.0f;
  private static java.util.List<net.minecraft.util.FormattedCharSequence> promptLines = null;
  private static float promptBoxWidth = 0.0f;
  private static float promptBoxHeight = 0.0f;

  private static double smoothDirX = 0.0D;
  private static double smoothDirY = 0.0D;
  private static double smoothDirZ = 1.0D;
  private static boolean isOrbitInitialized = false;

  private static net.minecraft.world.level.block.Rotation activeRotation = net.minecraft.world.level.block.Rotation.NONE;
  private static net.minecraft.world.level.block.Mirror activeMirror = net.minecraft.world.level.block.Mirror.NONE;
  private static String cachedPromptLabel = "";

  private static Vec3 smoothedBlueprintListPos = null;
  private static float smoothedBlueprintListYaw = 0.0f;

  public static void setProjection(HologramProjectionPayload payload) {
    if (payload.durationTicks() == -1) {
      pendingPlacement = true;
      activeStructureId = payload.structureId().isEmpty() ? null : payload.structureId();
      anchorPos = null;
      remainingTicks = 0;
      activeRotation = net.minecraft.world.level.block.Rotation.NONE;
      activeMirror = net.minecraft.world.level.block.Mirror.NONE;
      promptLines = null;

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
      activeRotation = net.minecraft.world.level.block.Rotation.NONE;
      activeMirror = net.minecraft.world.level.block.Mirror.NONE;
      promptLines = null;
      isOrbitInitialized = false;
    } else {
      pendingPlacement = false;
      activeStructureId = payload.structureId();
      anchorPos = payload.anchorPos();
      remainingTicks = payload.durationTicks();
      elapsedSeconds = 0.0f;
      isOrbitInitialized = false;
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

    if (pendingPlacement) {
      renderPlacementPrompt(event.getPoseStack(), mc);
      renderBlueprintSelectionStack(event.getPoseStack(), mc);
      return;
    }

    if (activeStructureId == null || anchorPos == null) return;

    MultiblockBlueprintData blueprint = BlueprintReloadListener.getBlueprint(activeStructureId);
    if (blueprint == null) return;

    MultiblockBlueprintData.TransformedBlueprint transformed = getTransformedBlueprint(mc.level, anchorPos, blueprint);

    // Progress Assessment Phase
    int totalBlocks = transformed.blockCount();
    int matchingBlocks = checkStructureProgress(mc, transformed, totalBlocks);
    float ratio = totalBlocks > 0 ? (float) matchingBlocks / totalBlocks : 0.0f;
    
    // Condition check: stop logic flow instantly if structure is 100% finished
    if (ratio >= 1.0f){
      clearActiveHologramProjector();
      return;
    }

    // Structural Rendering Phase
    PoseStack poseStack = event.getPoseStack();
    Vec3 cam = mc.getEntityRenderDispatcher().camera.getPosition();
    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
    
    float deltaSeconds = mc.getTimer().getRealtimeDeltaTicks() / 20.0f;
    elapsedSeconds += deltaSeconds;

    renderGhostBlocks(mc, poseStack, bufferSource, cam, transformed, totalBlocks);

    // Decoupled Orbiter Interface Rendering Phase
    renderOrbitalProgressBar(mc, poseStack, bufferSource, cam, blueprint, transformed, totalBlocks, ratio);
  }

  private static void clearActiveHologramProjector() {
    activeStructureId = null;
    anchorPos = null;
    remainingTicks = 0;
    isOrbitInitialized = false;
    pendingPlacement = false;
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    if (mc.player != null) {
      net.neoforged.neoforge.network.PacketDistributor.sendToServer(
        new com.genesiz.hexacreatecore.threedified.network.DialogueActionPayload(mc.player.getId(), "hexacreatecore:clear_completed_hologram", true)
      );
    }
  }

  private static int checkStructureProgress(Minecraft mc, MultiblockBlueprintData.TransformedBlueprint blueprint, int totalBlocks) {
    int matchingBlocks = 0;
    int[] offX = blueprint.offsetX();
    int[] offY = blueprint.offsetY();
    int[] offZ = blueprint.offsetZ();
    BlockState[] states = blueprint.states();

    for (int i = 0; totalBlocks > i; i++) {
      CURSOR_POS.set(anchorPos.getX() + offX[i], anchorPos.getY() + offY[i], anchorPos.getZ() + offZ[i]);
      if (mc.level.getBlockState(CURSOR_POS).is(states[i].getBlock())) {
        matchingBlocks++;
      }
    }
    return matchingBlocks;
  }

  private static void renderGhostBlocks(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 cam, MultiblockBlueprintData.TransformedBlueprint blueprint, int totalBlocks) {
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

    for (int i = 0; totalBlocks > i; i++) {
      float delay = offY[i] * layerStaggerSec;
      float rawProgress = (elapsedSeconds - delay) / animDurationSec;

      BlockState expectedState = states[i];
      CURSOR_POS.set(anchorPos.getX() + offX[i], anchorPos.getY() + offY[i], anchorPos.getZ() + offZ[i]);
      BlockState worldState = mc.level.getBlockState(CURSOR_POS);

      if (worldState.is(expectedState.getBlock())) continue;
      if (0.0f >= rawProgress) continue;

      float t = rawProgress >= 1.0f ? 1.0f : rawProgress;
      float ease = 1.0f - (float) Math.pow(1.0f - t, 3);

      int currentAlpha = (int) (140.0f * ease);
      if (1 > currentAlpha) continue;

      boolean isWrongBlock = !worldState.isAir();

      poseStack.pushPose();
      if (isWrongBlock) {
        GHOST_CONSUMER.wrap(baseBuffer, Math.max(currentAlpha, 160), 1.0f, 0.2f, 0.2f);
        poseStack.translate(CURSOR_POS.getX() - cam.x - 0.005D, CURSOR_POS.getY() - cam.y - 0.005D, CURSOR_POS.getZ() - cam.z - 0.005D);
        poseStack.scale(1.01f, 1.01f, 1.01f);
      } else {
        GHOST_CONSUMER.wrap(baseBuffer, currentAlpha, 1.0f, 1.0f, 1.0f);
        double animatedDropY = (1.0f - ease) * dropOffsetBlocks;
        poseStack.translate(CURSOR_POS.getX() - cam.x + 0.01D, CURSOR_POS.getY() - cam.y + 0.01D + animatedDropY, CURSOR_POS.getZ() - cam.z + 0.01D);
        poseStack.scale(0.98f, 0.98f, 0.98f);
      }

      blockRenderer.renderBatched(expectedState, CURSOR_POS, mc.level, poseStack, GHOST_CONSUMER, false, RANDOM, ModelData.EMPTY, ghostRenderType);
      poseStack.popPose();
    }
    bufferSource.endBatch(ghostRenderType);
  }

  private static boolean isPlayerLookingAtStructure(BlockPos lookPos, MultiblockBlueprintData blueprint, int totalBlocks) {
    int[] offX = blueprint.offsetX();
    int[] offY = blueprint.offsetY();
    int[] offZ = blueprint.offsetZ();

    for (int i = 0; totalBlocks > i; i++) {
      if (lookPos.getX() == anchorPos.getX() + offX[i] && 
      lookPos.getY() == anchorPos.getY() + offY[i] && 
      lookPos.getZ() == anchorPos.getZ() + offZ[i]) {
        return true;
      }
    }
    return false;
  }

  private static void renderOrbitalProgressBar(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 cam, MultiblockBlueprintData blueprint, MultiblockBlueprintData.TransformedBlueprint transformed, int totalBlocks, float ratio) {
    int[] offX = transformed.offsetX();
    int[] offY = transformed.offsetY();
    int[] offZ = transformed.offsetZ();

    int maxBoundsX = 0;
    int maxBoundsY = 0;
    int maxBoundsZ = 0;

    for (int i = 0; totalBlocks > i; i++) {
      if (offX[i] > maxBoundsX) maxBoundsX = offX[i];
      if (offY[i] > maxBoundsY) maxBoundsY = offY[i];
      if (offZ[i] > maxBoundsZ) maxBoundsZ = offZ[i];
    }

    double centerX = anchorPos.getX() + (maxBoundsX * 0.5D) + 0.5D;
    double centerY = anchorPos.getY() + (maxBoundsY * 0.5D) + 0.5D;
    double centerZ = anchorPos.getZ() + (maxBoundsZ * 0.5D) + 0.5D;

    var camera = mc.getEntityRenderDispatcher().camera;
    double targetDirX = camera.getPosition().x - centerX;
    double targetDirY = camera.getPosition().y - centerY;
    double targetDirZ = camera.getPosition().z - centerZ;

    double targetDistance = Math.sqrt(targetDirX * targetDirX + targetDirY * targetDirY + targetDirZ * targetDirZ);
    if (targetDistance > 0.001D) {
      targetDirX /= targetDistance;
      targetDirY /= targetDistance;
      targetDirZ /= targetDistance;
    } else {
      targetDirZ = 1.0D;
    }

    float deltaSeconds = mc.getTimer().getRealtimeDeltaTicks() / 20.0f;
    float lerpFactor = 1.0f - (float) Math.exp(-10.0f * deltaSeconds);

    if (!isOrbitInitialized) {
      smoothDirX = targetDirX;
      smoothDirY = targetDirY;
      smoothDirZ = targetDirZ;
      isOrbitInitialized = true;
    } else {
      smoothDirX += (targetDirX - smoothDirX) * lerpFactor;
      smoothDirY += (targetDirY - smoothDirY) * lerpFactor;
      smoothDirZ += (targetDirZ - smoothDirZ) * lerpFactor;
    }

    double smoothDistance = Math.sqrt(smoothDirX * smoothDirX + smoothDirY * smoothDirY + smoothDirZ * smoothDirZ);
    if (smoothDistance > 0.001D) {
      smoothDirX /= smoothDistance;
      smoothDirY /= smoothDistance;
      smoothDirZ /= smoothDistance;
    }

    double maxRadius = Math.max(maxBoundsX, Math.max(maxBoundsY, maxBoundsZ)) * 0.5D;
    double orbitRadius = maxRadius + 2.0D;

    double barWorldX = centerX + (smoothDirX * orbitRadius);
    double barWorldY = centerY + (smoothDirY * orbitRadius) - 0.2D;
    double barWorldZ = centerZ + (smoothDirZ * orbitRadius);

    poseStack.pushPose();
    poseStack.translate(barWorldX - cam.x, barWorldY - cam.y, barWorldZ - cam.z);

    poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-camera.getYRot() - 180.0F));
    poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-camera.getXRot()));
    poseStack.scale(0.015F, -0.015F, 0.015F);

    net.minecraft.client.renderer.MultiBufferSource.BufferSource progressBuffers = mc.renderBuffers().bufferSource();

    RawGeometry.drawProgressBar(
      poseStack, progressBuffers,
      0.0f, 0.0f, 0.0f,
      70.0f, 10.0f, 2.0f,
      blueprint.bgColor(), blueprint.borderColor(), blueprint.progressColor(),
      ratio
    );

    progressBuffers.endBatch();
    poseStack.popPose();
  }

  private static void renderPlacementPrompt(PoseStack poseStack, Minecraft mc) {
    String rotKey = com.genesiz.hexacreatecore.create.wonderum.blueprint.WonderumBlueprintDrafter.ROTATE_HOLOGRAM_KEY
    .getTranslatedKeyMessage().getString();
    String mirKey = com.genesiz.hexacreatecore.create.wonderum.blueprint.WonderumBlueprintDrafter.MIRROR_HOLOGRAM_KEY
    .getTranslatedKeyMessage().getString();

    String label = "Use to place hologram\n§7" + formatOrientationLabel() + " §8([" + rotKey + "] Rot / [" + mirKey + "] Mirror)";
    if (!label.equals(cachedPromptLabel) || promptLines == null) {
      cachedPromptLabel = label;
      promptLines = mc.font.split(net.minecraft.network.chat.Component.literal(label), 240);
      int widest = 0;
      for (var seq : promptLines) {
        widest = Math.max(widest, mc.font.width(seq));
      }
      promptBoxWidth = widest + 16.0f;
      promptBoxHeight = (promptLines.size() * mc.font.lineHeight) + 12.0f;
    }

    var camera = mc.getEntityRenderDispatcher().camera;
    float deltaSeconds = mc.getTimer().getRealtimeDeltaTicks() / 20.0f;
    float factor = 1.0f - (float) Math.exp(-18.0f * deltaSeconds);

    promptYaw += net.minecraft.util.Mth.wrapDegrees(camera.getYRot() - promptYaw) * factor;
    promptPitch += (camera.getXRot() - promptPitch) * factor;

    float yawRad = (float) Math.toRadians(promptYaw);
    float pitchRad = (float) Math.toRadians(promptPitch);

    double forwardX = -Math.sin(yawRad) * Math.cos(pitchRad);
    double forwardY = -Math.sin(pitchRad);
    double forwardZ = Math.cos(yawRad) * Math.cos(pitchRad);

    double rightX = -Math.cos(yawRad);
    double rightZ = -Math.sin(yawRad);

    double worldRelX = (forwardX * 1.4D) + (rightX * 0.35D);
    double worldRelY = (forwardY * 1.4D) - 0.18D;
    double worldRelZ = (forwardZ * 1.4D) + (rightZ * 0.35D);

    poseStack.pushPose();
    poseStack.translate(worldRelX, worldRelY, worldRelZ);

    poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-promptYaw - 180.0F));
    poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-promptPitch));
    poseStack.scale(0.0065F, -0.0065F, 0.0065F);

    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
    RawGeometry.drawDialogueNode(
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

  private static void renderBlueprintSelectionStack(PoseStack poseStack, Minecraft mc) {
    if (!mc.player.getMainHandItem().is(com.genesiz.hexacreatecore.item.ModItems.WONDERUM.get())) {
      smoothedBlueprintListPos = null;
      return;
    }

    java.util.List<String> allIds = BlueprintReloadListener.getWonderumBlueprintIds();
    if (allIds.isEmpty()) {
      smoothedBlueprintListPos = null;
      return;
    }

    int selectedIdx = Math.floorMod(
      com.genesiz.hexacreatecore.create.wonderum.blueprint.WonderumBlueprintDrafter.getSelectedIndex(),
      allIds.size()
    );

    // Keep the 10-slot window centered around the currently selected blueprint
    int maxVisible = 10;
    int startIndex = Math.max(0, Math.min(selectedIdx - 4, Math.max(0, allIds.size() - maxVisible)));
    int endIndex = Math.min(allIds.size(), startIndex + maxVisible);

    var camera = mc.getEntityRenderDispatcher().camera;
    Vec3 camPos = camera.getPosition();
    float camYaw = camera.getYRot();

    double yawRad = Math.toRadians(camYaw);
    double lookX = -Math.sin(yawRad);
    double lookZ = Math.cos(yawRad);
    double leftX = Math.cos(yawRad);
    double leftZ = Math.sin(yawRad);

    double sineBob = Math.sin(System.currentTimeMillis() / 900.0D) * 0.04D;
    Vec3 goalPos = new Vec3(
      camPos.x + (lookX * 2.6D) + (leftX * 1.15D),
      camPos.y + 0.55D + sineBob,
      camPos.z + (lookZ * 2.6D) + (leftZ * 1.15D)
    );

    float dt = mc.getTimer().getRealtimeDeltaTicks() / 20.0f;
    double sineEase = Math.sin(Math.min(1.0D, dt * 4.5D) * (Math.PI / 2.0D));

    if (smoothedBlueprintListPos == null || smoothedBlueprintListPos.distanceToSqr(goalPos) > 64.0D) {
      smoothedBlueprintListPos = goalPos;
      smoothedBlueprintListYaw = camYaw;
    } else {
      smoothedBlueprintListPos = smoothedBlueprintListPos.lerp(goalPos, sineEase);
      smoothedBlueprintListYaw += net.minecraft.util.Mth.wrapDegrees(camYaw - smoothedBlueprintListYaw) * (float) sineEase;
    }

    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
    net.minecraft.client.gui.Font font = mc.font;

    poseStack.pushPose();
    poseStack.translate(smoothedBlueprintListPos.x - camPos.x, smoothedBlueprintListPos.y - camPos.y, smoothedBlueprintListPos.z - camPos.z);
    poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-smoothedBlueprintListYaw - 180.0F));
    poseStack.scale(0.0085F, -0.0085F, 0.0085F);

    float currentLocalY = 0.0f;
    int fixedBoxWidth = 156;
    int maxTextWidth = fixedBoxWidth - 16;

    var selectedStyle = com.genesiz.hexacreatecore.threedified.text.TextEffectStyle.aurora(0xFFFFFFFF, 0xFF00FFAA);
    var defaultStyle = com.genesiz.hexacreatecore.threedified.text.TextEffectStyle.aurora(0xFFE2E8F0, 0xFFA855F7);

    // Header badge showing slot position & Sneak+Left-Click cancel hint
    String headerText = "Blueprints (" + (selectedIdx + 1) + "/" + allIds.size() + ")";
    float headerHeight = font.lineHeight + 10.0f;
    RawGeometry.drawDialogueNode(
      poseStack, bufferSource, font,
      0.0f, currentLocalY, 0.0f,
      fixedBoxWidth, headerHeight, 2.5f, 2.5f,
      0xCC0D0814, 0xFF21B7D1, java.util.List.of(), false
    );
    poseStack.pushPose();
    poseStack.translate(0.0f, currentLocalY, 0.35f);
    com.genesiz.hexacreatecore.threedified.text.ThreeDifiedTextRenderer.draw3D(
      poseStack, bufferSource, font, headerText, -(font.width(headerText) * 0.5f), -(font.lineHeight * 0.5f), selectedStyle
    );
    poseStack.popPose();
    currentLocalY += headerHeight + 8.0f;

    // 10-slot scrollable blueprint stack
    for (int i = startIndex; endIndex > i; i++) {
      boolean isSelected = (i == selectedIdx);
      String idName = allIds.get(i);
      String idxPrefix = (isSelected ? "▶ [" : "[") + i + "] ";
      String trimmed = font.plainSubstrByWidth(idxPrefix + idName, maxTextWidth);
      float boxHeight = font.lineHeight + 10.0f;

      poseStack.pushPose();
      poseStack.translate(0.0f, currentLocalY, isSelected ? 4.0f : 0.0f);

      RawGeometry.drawDialogueNode(
        poseStack, bufferSource, font,
        0.0f, 0.0f, 0.0f,
        fixedBoxWidth, boxHeight, 2.5f, 2.5f,
        isSelected ? 0xDD111C24 : 0xAA110E1A,
        isSelected ? 0xFF00FFAA : 0xAA5A2E85,
        java.util.List.of(), false
      );

      poseStack.pushPose();
      poseStack.translate(0.0f, 0.0f, 0.35f);
      float textLeftX = -(fixedBoxWidth * 0.5f) + 8.0f;
      float textTopY = -(font.lineHeight * 0.5f);

      String rest = trimmed.startsWith(idxPrefix) ? trimmed.substring(idxPrefix.length()) : trimmed;
      font.drawInBatch(
        idxPrefix, textLeftX, textTopY, isSelected ? 0xFF00FFAA : 0xFFFFFF55, true,
        poseStack.last().pose(), bufferSource, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0,
        LightTexture.FULL_BRIGHT
      );
      com.genesiz.hexacreatecore.threedified.text.ThreeDifiedTextRenderer.draw3D(
        poseStack, bufferSource, font, rest, textLeftX + font.width(idxPrefix), textTopY,
        isSelected ? selectedStyle : defaultStyle
      );
      poseStack.popPose();

      poseStack.popPose();
      currentLocalY += boxHeight + 6.0f;
    }

    bufferSource.endBatch(RawGeometry.DIALOGUE_SOLID);
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
    @Override public VertexConsumer addVertex(float x, float y, float z) { return delegate.addVertex(x, y, z); }

    @Override public VertexConsumer setColor(int r, int g, int b, int a) { return delegate.setColor((int) (r * this.redMul), (int) (g * this.greenMul), (int) (b * this.blueMul), this.alpha); }

    @Override public VertexConsumer setUv(float u, float v) { return delegate.setUv(u, v); }

    @Override public VertexConsumer setUv1(int u, int v) { return delegate.setUv1(u, v); }

    @Override public VertexConsumer setUv2(int u, int v) { return delegate.setUv2(LightTexture.FULL_BRIGHT & 0xFFFF, LightTexture.FULL_BRIGHT >> 16); }

    @Override public VertexConsumer setNormal(float x, float y, float z) { return delegate.setNormal(x, y, z); }

    @Override
    public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float red, float green, float blue, float alpha, int packedLight, int packedOverlay) {
      delegate.putBulkData(pose, quad, red * this.redMul, green * this.greenMul, blue * this.blueMul, this.alpha / 255.0f, LightTexture.FULL_BRIGHT, packedOverlay);
    }
    @Override
    public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float red, float green, float blue, float alpha, int[] lightmap, int packedOverlay, boolean readAlpha) {
      delegate.putBulkData(pose, quad, brightness, red * this.redMul, green * this.greenMul, blue * this.blueMul, this.alpha / 255.0f, lightmap, packedOverlay, false);
    }
  }

  public static void cycleRotation() {
    activeRotation = activeRotation.getRotated(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
    promptLines = null;
  }

  public static void cycleMirror() {
    activeMirror = switch (activeMirror) {
      case NONE -> net.minecraft.world.level.block.Mirror.LEFT_RIGHT;
      case LEFT_RIGHT -> net.minecraft.world.level.block.Mirror.FRONT_BACK;
      case FRONT_BACK -> net.minecraft.world.level.block.Mirror.NONE;
    };
    promptLines = null;
  }

  public static MultiblockBlueprintData.TransformedBlueprint getTransformedBlueprint(net.minecraft.world.level.LevelAccessor level, BlockPos anchor, MultiblockBlueprintData blueprint) {
    return blueprint.transformed(level, anchor, activeRotation, activeMirror);
  }

  public static String formatOrientationLabel() {
    String rotDeg = switch (activeRotation) {
      case NONE -> "0°";
      case CLOCKWISE_90 -> "90°";
      case CLOCKWISE_180 -> "180°";
      case COUNTERCLOCKWISE_90 -> "270°";
    };
    String mirStr = switch (activeMirror) {
      case NONE -> "None";
      case LEFT_RIGHT -> "X-Axis";
      case FRONT_BACK -> "Z-Axis";
    };
    return "Rot: " + rotDeg + " | Mirror: " + mirStr;
  }


  public static boolean isPendingPlacement() {
    return pendingPlacement;
  }

  public static String getActiveStructureId() {
    return activeStructureId;
  }

  public static BlockPos getAnchorPos() {
    return anchorPos;
  }
  public static net.minecraft.world.level.block.Rotation getActiveRotation() { return activeRotation; }
  public static net.minecraft.world.level.block.Mirror getActiveMirror() { return activeMirror; }
}
