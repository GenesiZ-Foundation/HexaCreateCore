package com.genesiz.hexacreatecore.create.wonderum.blueprint;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.create.wonderum.blueprint.WonderumDraftingSession.ColorTarget;
import com.genesiz.hexacreatecore.item.ModItems;
import com.genesiz.hexacreatecore.threedified.DialogueRaycaster;
import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class WonderumDraftingMenuRenderer {

  private static final RandomSource RANDOM = RandomSource.create(99L);
  private static final DraftGhostConsumer GHOST_CONSUMER = new DraftGhostConsumer();

  private static double smoothDirX = 0.0D;
  private static double smoothDirY = 0.0D;
  private static double smoothDirZ = 1.0D;
  private static boolean orbitInit = false;

  private static String hoveredWidgetId = "";
  private static float hoveredSliderNormX = 0.5f;

  private WonderumDraftingMenuRenderer() {}

  public static boolean hasHoveredWidget() {
    return !hoveredWidgetId.isEmpty();
  }

  public static boolean handleMenuRightClick(Minecraft mc) {
    if (hoveredWidgetId.isEmpty() || mc.player == null) return false;

    mc.player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.7f, 1.35f);

    switch (hoveredWidgetId) {
      case "btn_name" -> {
        WonderumDraftingSession.setAwaitingChatName(true);
        mc.setScreen(new ChatScreen(""));
        ThreeDifiedNotificationHud.show("§b[Wonderum] §fType the blueprint name in chat and press §aEnter");
      }
      case "btn_colors" -> WonderumDraftingSession.setColorPickerOpen(!WonderumDraftingSession.isColorPickerOpen());
      case "btn_dismantle" -> {
        WonderumDraftingSession.dismantle();
        orbitInit = false;
      }
      case "btn_save" -> WonderumDraftingSession.saveBlueprintJson();
      case "cp_bg" -> WonderumDraftingSession.setActiveColorTarget(ColorTarget.BG);
      case "cp_border" -> WonderumDraftingSession.setActiveColorTarget(ColorTarget.BORDER);
      case "cp_progress" -> WonderumDraftingSession.setActiveColorTarget(ColorTarget.PROGRESS);
      case "cp_close" -> WonderumDraftingSession.setColorPickerOpen(false);
      case "cp_hue_bar" -> WonderumDraftingSession.updateHsva(
        hoveredSliderNormX, 0.90f, Math.max(0.25f, WonderumDraftingSession.getValue()), WonderumDraftingSession.getAlpha()
      );
      case "cp_val_bar" -> WonderumDraftingSession.updateHsva(
        WonderumDraftingSession.getHue(), WonderumDraftingSession.getSaturation(), hoveredSliderNormX, WonderumDraftingSession.getAlpha()
      );
      case "cp_alpha_bar" -> WonderumDraftingSession.updateHsva(
        WonderumDraftingSession.getHue(), WonderumDraftingSession.getSaturation(), WonderumDraftingSession.getValue(), hoveredSliderNormX
      );
    }
    return true;
  }

  @SubscribeEvent
  public static void onClientChat(ClientChatEvent event) {
    if (!WonderumDraftingSession.isAwaitingChatName()) return;
    WonderumDraftingSession.setAwaitingChatName(false);
    String typed = event.getMessage().trim();
    if (!typed.isEmpty()) {
      WonderumDraftingSession.setStructureName(typed);
      ThreeDifiedNotificationHud.show("§b[Wonderum] §fBlueprint Name: §e" + typed + " §8(" + WonderumDraftingSession.toSmallSnakeCase(typed) + ".json)");
    }
    event.setCanceled(true);
  }

  @SubscribeEvent
  public static void onRenderLevelStage(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;

    Map<BlockPos, BlockState> blocks = WonderumDraftingSession.getBlocks();
    if (blocks.isEmpty()) {
      orbitInit = false;
      hoveredWidgetId = "";
      return;
    }

    PoseStack poseStack = event.getPoseStack();
    Vec3 cam = mc.getEntityRenderDispatcher().camera.getPosition();
    MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

    // 1. Render all player-drafted ghost blocks
    BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
    RenderType ghostType = Sheets.translucentCullBlockSheet();
    VertexConsumer ghostBuffer = bufferSource.getBuffer(ghostType);
    GHOST_CONSUMER.wrap(ghostBuffer, 155, 0.75f, 0.95f, 1.0f);

    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
      BlockPos bp = entry.getKey();
      if (minX > bp.getX()) minX = bp.getX();
      if (minY > bp.getY()) minY = bp.getY();
      if (minZ > bp.getZ()) minZ = bp.getZ();
      if (bp.getX() > maxX) maxX = bp.getX();
      if (bp.getY() > maxY) maxY = bp.getY();
      if (bp.getZ() > maxZ) maxZ = bp.getZ();

      poseStack.pushPose();
      poseStack.translate(bp.getX() - cam.x + 0.01D, bp.getY() - cam.y + 0.01D, bp.getZ() - cam.z + 0.01D);
      poseStack.scale(0.98f, 0.98f, 0.98f);
      blockRenderer.renderBatched(entry.getValue(), bp, mc.level, poseStack, GHOST_CONSUMER, false, RANDOM, ModelData.EMPTY, ghostType);
      poseStack.popPose();
    }
    bufferSource.endBatch(ghostType);

    // 2. Render Orbiting ThreeDified Control Menu (only while holding Wonderum)
    if (!mc.player.getMainHandItem().is(ModItems.WONDERUM.get())) {
      hoveredWidgetId = "";
      return;
    }

    double centerX = (minX + maxX + 1) * 0.5D;
    double centerY = (minY + maxY + 1) * 0.5D;
    double centerZ = (minZ + maxZ + 1) * 0.5D;

    var camera = mc.getEntityRenderDispatcher().camera;
    double targetDirX = camera.getPosition().x - centerX;
    double targetDirY = camera.getPosition().y - centerY;
    double targetDirZ = camera.getPosition().z - centerZ;
    double dist = Math.sqrt(targetDirX * targetDirX + targetDirY * targetDirY + targetDirZ * targetDirZ);
    if (dist > 0.001D) {
      targetDirX /= dist;
      targetDirY /= dist;
      targetDirZ /= dist;
    } else {
      targetDirZ = 1.0D;
    }

    float dt = mc.getTimer().getRealtimeDeltaTicks() / 20.0f;
    float lerp = 1.0f - (float) Math.exp(-10.0f * dt);
    if (!orbitInit) {
      smoothDirX = targetDirX;
      smoothDirY = targetDirY;
      smoothDirZ = targetDirZ;
      orbitInit = true;
    } else {
      smoothDirX += (targetDirX - smoothDirX) * lerp;
      smoothDirY += (targetDirY - smoothDirY) * lerp;
      smoothDirZ += (targetDirZ - smoothDirZ) * lerp;
    }

    double sDist = Math.sqrt(smoothDirX * smoothDirX + smoothDirY * smoothDirY + smoothDirZ * smoothDirZ);
    if (sDist > 0.001D) {
      smoothDirX /= sDist;
      smoothDirY /= sDist;
      smoothDirZ /= sDist;
    }

    double maxRadius = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) * 0.5D;
    double orbitRadius = maxRadius + 2.4D;

    double menuX = centerX + (smoothDirX * orbitRadius);
    double menuY = centerY + (smoothDirY * orbitRadius) - 0.15D;
    double menuZ = centerZ + (smoothDirZ * orbitRadius);

    poseStack.pushPose();
    poseStack.translate(menuX - cam.x, menuY - cam.y, menuZ - cam.z);
    poseStack.mulPose(Axis.YP.rotationDegrees(-camera.getYRot() - 180.0F));
    poseStack.mulPose(Axis.XP.rotationDegrees(-camera.getXRot()));
    poseStack.scale(0.0115F, -0.0115F, 0.0115F);

    float realTick = mc.getTimer().getGameTimeDeltaTicks();
    Vec3 eyePos = mc.player.getEyePosition(realTick);
    Vec3 viewVec = mc.player.getViewVector(realTick);
    Vector3f lookDir = new Vector3f((float) viewVec.x, (float) viewVec.y, (float) viewVec.z);
    float rayOriginX = (float) (eyePos.x - cam.x);
    float rayOriginY = (float) (eyePos.y - cam.y);
    float rayOriginZ = (float) (eyePos.z - cam.z);

    Matrix4f mat = poseStack.last().pose();
    Font font = mc.font;
    hoveredWidgetId = "";

    // Main Menu Buttons stacked with generous 30px pitch (18px height + 12px breathing room)
    String rawName = WonderumDraftingSession.getStructureName();
    String nameDisplay = rawName.isEmpty() ? "Name: §8<Right-Click>" : ("Name: §e" + rawName);
    float btnW = Math.max(132.0f, font.width(Component.literal(nameDisplay)) + 22.0f);

    drawMenuButton(poseStack, bufferSource, font, mat, rayOriginX, rayOriginY, rayOriginZ, lookDir, "btn_name", nameDisplay, 0.0f, -45.0f, btnW, 18.0f, 0xFF00FFAA);

    drawMenuButton(poseStack, bufferSource, font, mat, rayOriginX, rayOriginY, rayOriginZ, lookDir, "btn_colors", "Colors", 0.0f, -15.0f, btnW, 18.0f, 0xFF21B7D1);
    drawMenuButton(poseStack, bufferSource, font, mat, rayOriginX, rayOriginY, rayOriginZ, lookDir, "btn_dismantle", "§cDismantle", 0.0f, 15.0f, btnW, 18.0f, 0xFFFF1E56);
    drawMenuButton(poseStack, bufferSource, font, mat, rayOriginX, rayOriginY, rayOriginZ, lookDir, "btn_save", "§aSave", 0.0f, 45.0f, btnW, 18.0f, 0xFF10B981);
    // Optional Color Picker Sub-Panel placed cleanly to the right with 20px gap
    if (WonderumDraftingSession.isColorPickerOpen()) {
      float panelX = (btnW * 0.5f) + 78.0f;
      renderColorPickerPanel(poseStack, bufferSource, font, mat, rayOriginX, rayOriginY, rayOriginZ, lookDir, panelX);
    }
    bufferSource.endBatch(RawGeometry.DIALOGUE_SOLID);
    bufferSource.endBatch();
    poseStack.popPose();
  }

  private static void drawMenuButton(
    PoseStack ps, MultiBufferSource buf, Font font, Matrix4f mat,
    float rx, float ry, float rz, Vector3f look,
    String id, String label, float cx, float cy, float w, float h, int baseBorder
  ) {
    boolean hovered = DialogueRaycaster.isLookingAtNode(mat, rx, ry, rz, look, cx, cy, 2.0f, w, h, 8.0D);
    if (hovered) hoveredWidgetId = id;
    List lines = List.of(Component.literal(label).getVisualOrderText());
    RawGeometry.drawDialogueNode(
      ps, buf, font,
      cx, cy, hovered ? 3.5f : 1.5f,
      w, h, 2.5f, 3.0f,
      hovered ? 0xEE1A1429 : 0xDD0D0814,
      hovered ? 0xFFFFFFFF : baseBorder,
      lines, true
    );
  }

  private static void renderColorPickerPanel(
    PoseStack ps, MultiBufferSource buf, Font font, Matrix4f mat,
    float rx, float ry, float rz, Vector3f look, float panelX
  ) {
    float panelW = 122.0f;
    float panelH = 166.0f;
    RawGeometry.drawDialogueNode(
      ps, buf, font,
      panelX, 0.0f, 0.0f,
      panelW, panelH, 2.5f, 3.0f,
      0xEE0D0814, 0xFF21B7D1, List.of(), true
    );
    ColorTarget active = WonderumDraftingSession.getActiveColorTarget();
    drawMenuButton(ps, buf, font, mat, rx, ry, rz, look, "cp_bg",
    (active == ColorTarget.BG ? "§a► " : "") + "Background Color", panelX, -64.0f, 106.0f, 16.0f, WonderumDraftingSession.getBgColor() | 0xFF000000);
    drawMenuButton(ps, buf, font, mat, rx, ry, rz, look, "cp_border",
    (active == ColorTarget.BORDER ? "§a► " : "") + "Border Color", panelX, -42.0f, 106.0f, 16.0f, WonderumDraftingSession.getBorderColor() | 0xFF000000);
    drawMenuButton(ps, buf, font, mat, rx, ry, rz, look, "cp_progress",
    (active == ColorTarget.PROGRESS ? "§a► " : "") + "Progress Color", panelX, -20.0f, 106.0f, 16.0f, WonderumDraftingSession.getProgressColor() | 0xFF000000);
    float sliderW = 96.0f;
    float hueY = 4.0f;
    float valY = 24.0f;
    float alphaY = 44.0f;
    Float hueHit = raycastSliderNormX(mat, rx, ry, rz, look, panelX, hueY, 2.5f, sliderW, 13.0f);
    if (hueHit != null) {
      hoveredWidgetId = "cp_hue_bar";
      hoveredSliderNormX = hueHit;
    }
    Float valHit = raycastSliderNormX(mat, rx, ry, rz, look, panelX, valY, 2.5f, sliderW, 13.0f);
    if (valHit != null) {
      hoveredWidgetId = "cp_val_bar";
      hoveredSliderNormX = valHit;
    }
    Float alphaHit = raycastSliderNormX(mat, rx, ry, rz, look, panelX, alphaY, 2.5f, sliderW, 13.0f);
    if (alphaHit != null) {
      hoveredWidgetId = "cp_alpha_bar";
      hoveredSliderNormX = alphaHit;
    }
    int activeArgb = switch (active) {
      case BG -> WonderumDraftingSession.getBgColor();
      case BORDER -> WonderumDraftingSession.getBorderColor();
      case PROGRESS -> WonderumDraftingSession.getProgressColor();
    };
    int pureHueRgb = (java.awt.Color.HSBtoRGB(WonderumDraftingSession.getHue(), 1.0f, 1.0f) & 0x00FFFFFF) | 0xFF000000;
    // 1. Hue Slider (Spectrum rainbow gradient)
    drawRainbowHueSlider(ps, buf, panelX, hueY, sliderW, 9.0f, WonderumDraftingSession.getHue(), "cp_hue_bar".equals(hoveredWidgetId));
    // 2. Brightness Slider (Black -> Pure Hue)
    drawGradientSlider(ps, buf, panelX, valY, sliderW, 9.0f, 0xFF121218, pureHueRgb, WonderumDraftingSession.getValue(), "cp_val_bar".equals(hoveredWidgetId));
    // 3. Alpha Slider (Transparent -> Solid White)
    drawGradientSlider(ps, buf, panelX, alphaY, sliderW, 9.0f, 0xFF2A2638, 0xFFFFFFFF, WonderumDraftingSession.getAlpha(), "cp_alpha_bar".equals(hoveredWidgetId));
    // Bottom Row: Live Swatch + Close Button with clean gap
    RawGeometry.drawDialogueNode(
      ps, buf, font,
      panelX - 28.0f, 66.0f, 2.0f,
      42.0f, 16.0f, 1.5f, 1.5f,
      activeArgb, 0xFFFFFFFF, List.of(), true
    );
    drawMenuButton(ps, buf, font, mat, rx, ry, rz, look, "cp_close", "Close", panelX + 25.0f, 66.0f, 50.0f, 16.0f, 0xFFFF1E56);
  }

  private static Float raycastSliderNormX(
    Matrix4f mat, float rx, float ry, float rz, Vector3f look,
    float cx, float cy, float cz, float w, float h
  ) {
    if (!DialogueRaycaster.isLookingAtNode(mat, rx, ry, rz, look, cx, cy, cz, w + 4.0f, h, 8.0D)) {
      return null;
    }
    Vector3f center = new Vector3f(cx, cy, cz);
    mat.transformPosition(center);
    Vector3f normal = new Vector3f(0.0f, 0.0f, 1.0f);
    mat.transformDirection(normal).normalize();
    float denom = normal.dot(look);
    if (Math.abs(denom) < 1e-5f) return 0.5f;
    float t = ((center.x - rx) * normal.x + (center.y - ry) * normal.y + (center.z - rz) * normal.z) / denom;
    Vector3f hit = new Vector3f(rx + look.x * t, ry + look.y * t, rz + look.z * t);
    Matrix4f inv = new Matrix4f(mat).invert();
    inv.transformPosition(hit);
    return Mth.clamp((hit.x - (cx - w * 0.5f)) / w, 0.0f, 1.0f);
  }

  private static void drawRainbowHueSlider(PoseStack ps, MultiBufferSource buf, float cx, float cy, float w, float h, float normVal, boolean hovered) {
    VertexConsumer vc = buf.getBuffer(RawGeometry.DIALOGUE_SOLID);
    int[] stops = { 0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000 };
    int segs = stops.length - 1;
    float segW = w / segs;
    for (int i = 0; segs > i; i++) {
      float sx = (cx - w * 0.5f) + (i * segW) + (segW * 0.5f);
      ps.pushPose();
      ps.translate(sx, cy, 2.5f);
      RawGeometry.drawRectangle(vc, ps.last().pose(), segW + 0.3f, h, stops[i]);
      ps.popPose();
    }
    drawSliderKnob(ps, vc, cx, cy, w, h, normVal, hovered);
  }

  private static void drawGradientSlider(PoseStack ps, MultiBufferSource buf, float cx, float cy, float w, float h, int leftCol, int rightCol, float normVal, boolean hovered) {
    VertexConsumer vc = buf.getBuffer(RawGeometry.DIALOGUE_SOLID);
    int steps = 12;
    float stepW = w / steps;
    for (int i = 0; steps > i; i++) {
      float t = i / (float) (steps - 1);
      int col = lerpArgb(leftCol, rightCol, t);
      float sx = (cx - w * 0.5f) + (i * stepW) + (stepW * 0.5f);
      ps.pushPose();
      ps.translate(sx, cy, 2.5f);
      RawGeometry.drawRectangle(vc, ps.last().pose(), stepW + 0.3f, h, col);
      ps.popPose();
    }
    drawSliderKnob(ps, vc, cx, cy, w, h, normVal, hovered);
  }

  private static void drawSliderKnob(PoseStack ps, VertexConsumer vc, float cx, float cy, float w, float h, float normVal, boolean hovered) {
    float knobX = (cx - w * 0.5f) + (Mth.clamp(normVal, 0.0f, 1.0f) * w);
    ps.pushPose();
    ps.translate(knobX, cy, 3.2f);
    RawGeometry.drawRectangle(vc, ps.last().pose(), hovered ? 4.0f : 3.0f, h + 4.0f, 0xFFFFFFFF);
    ps.popPose();
  }

  private static int lerpArgb(int c0, int c1, float t) {
    int a = Math.round(Mth.lerp(t, (c0 >>> 24) & 0xFF, (c1 >>> 24) & 0xFF));
    int r = Math.round(Mth.lerp(t, (c0 >>> 16) & 0xFF, (c1 >>> 16) & 0xFF));
    int g = Math.round(Mth.lerp(t, (c0 >>> 8) & 0xFF, (c1 >>> 8) & 0xFF));
    int b = Math.round(Mth.lerp(t, c0 & 0xFF, c1 & 0xFF));
    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  private static final class DraftGhostConsumer implements VertexConsumer {
    private VertexConsumer delegate;
    private int alpha;
    private float rMul, gMul, bMul;
    void wrap(VertexConsumer delegate, int alpha, float r, float g, float b) {
      this.delegate = delegate;
      this.alpha = alpha;
      this.rMul = r;
      this.gMul = g;
      this.bMul = b;
    }

    @Override public VertexConsumer addVertex(float x, float y, float z) { return delegate.addVertex(x, y, z); }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) { return delegate.setColor((int) (r * rMul), (int) (g * gMul), (int) (b * bMul), alpha); }
    @Override public VertexConsumer setUv(float u, float v) { return delegate.setUv(u, v); }
    @Override public VertexConsumer setUv1(int u, int v) { return delegate.setUv1(u, v); }
    @Override public VertexConsumer setUv2(int u, int v) { return delegate.setUv2(LightTexture.FULL_BRIGHT & 0xFFFF, LightTexture.FULL_BRIGHT >> 16); }
    @Override public VertexConsumer setNormal(float x, float y, float z) { return delegate.setNormal(x, y, z); }
    @Override
    public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float red, float green, float blue, float alpha, int packedLight, int packedOverlay) {
      delegate.putBulkData(pose, quad, red * rMul, green * gMul, blue * bMul, this.alpha / 255.0f, LightTexture.FULL_BRIGHT, packedOverlay);
    }
    @Override
    public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float red, float green, float blue, float alpha, int[] lightmap, int packedOverlay, boolean readAlpha) {
      delegate.putBulkData(pose, quad, brightness, red * rMul, green * gMul, blue * bMul, this.alpha / 255.0f, lightmap, packedOverlay, false);
    }
  }
}
