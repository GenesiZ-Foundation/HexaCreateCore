package com.genesiz.hexacreatecore.hexcasting.everbook;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.hexcasting.HexagonalGridManager;
import com.genesiz.hexacreatecore.threedified.RawGeometry;
import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.CompositeDictionary;
import com.genesiz.hexacreatecore.threedified.shaders.dictionaries.ShaderDictionary;
import com.genesiz.hexacreatecore.threedified.shaders.hooks.PortalShaderHook;
import com.genesiz.hexacreatecore.threedified.text.TextEffectStyle;
import com.genesiz.hexacreatecore.threedified.text.ThreeDifiedTextRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

import static com.genesiz.hexacreatecore.hexcasting.everbook.EverbookRiftMath.*;

@EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
public final class EverbookRiftRenderer {

  private static Vec3 smoothedRiftPos = null;
  private static float smoothedRiftYaw = 0.0f;
  private static double smoothedRiftDropY = 0.0D;
  private static final long RENDER_EPOCH_NANOS = System.nanoTime();

  private EverbookRiftRenderer() {}

  @SubscribeEvent
  public static void onRenderWorld(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    if (!EverbookSessionState.isActive()) { smoothedRiftPos = null; smoothedRiftDropY = 0.0D; return; }
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;

    Vec3 cam = mc.getEntityRenderDispatcher().camera.getPosition();
    Vec3 center = HexagonalGridManager.getCenterAnchorPoint();
    float camYaw = HexagonalGridManager.getFreeCamYaw();

    float dt = mc.getTimer().getRealtimeDeltaTicks() / 20.0f;
    double sineEase = Math.sin(Math.min(1.0D, dt * 2.8D) * (Math.PI / 2.0D));
    smoothedRiftDropY = Mth.lerp(sineEase, smoothedRiftDropY, EverbookSessionState.isRiftMode() ? -2.0D : 0.0D);

    if (!EverbookSessionState.isRiftMode() && center != null) {
      double dx = cam.x - center.x, dz = cam.z - center.z;
      double orbitRadius = 2.0D * ((int) (Math.sqrt(dx * dx + dz * dz) / 2.0D) + 2);
      double yawRad = Math.toRadians(camYaw), lookX = -Math.sin(yawRad), lookZ = Math.cos(yawRad);
      double b = dx * lookX + dz * lookZ, disc = (b * b) - ((dx * dx + dz * dz) - (orbitRadius * orbitRadius));
      double goalX = (disc >= 0.0D) ? (cam.x + lookX * (-b + Math.sqrt(disc))) : (center.x + lookX * orbitRadius);
      double goalZ = (disc >= 0.0D) ? (cam.z + lookZ * (-b + Math.sqrt(disc))) : (center.z + lookZ * orbitRadius);
      Vec3 goalPos = new Vec3(goalX, center.y + 4.50D + Math.sin(System.currentTimeMillis() / 900.0D) * 0.06D, goalZ);

      if (smoothedRiftPos == null || smoothedRiftPos.distanceToSqr(goalPos) > 100.0D) { smoothedRiftPos = goalPos; smoothedRiftYaw = camYaw; }
      else { smoothedRiftPos = smoothedRiftPos.lerp(goalPos, sineEase); smoothedRiftYaw += Mth.wrapDegrees(camYaw - smoothedRiftYaw) * (float) sineEase; }
    }

    Vec3 renderAnchor = ((smoothedRiftPos != null) ? smoothedRiftPos : EverbookSessionState.getRiftAnchorPos()).add(0.0D, smoothedRiftDropY, 0.0D);
    float renderYaw = (smoothedRiftPos != null) ? smoothedRiftYaw : EverbookSessionState.getRiftYaw();
    EverbookSessionState.syncLockedTransform(renderAnchor, renderYaw, 0.0f);

    PoseStack ps = event.getPoseStack();
    MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
    Font font = mc.font;
    float timeSec = (float) ((System.nanoTime() - RENDER_EPOCH_NANOS) * 1.0e-9D);

    RenderSystem.disableDepthTest();
    RenderSystem.enableBlend();
    ps.pushPose();
    ps.translate(renderAnchor.x - cam.x, renderAnchor.y - cam.y, renderAnchor.z - cam.z);
    ps.mulPose(Axis.YP.rotationDegrees(-renderYaw - 180.0f));

    PortalShaderHook.bind(CompositeDictionary.EVERBOOK_DEFAULT)
      .size(RIFT_HALF_W, RIFT_HALF_H)
      .resolution(64, 12)
      .render(ps, buf, timeSec);

    renderWrappedPatterns(ps, buf, font, timeSec);
    if (EverbookSessionState.isRiftMode()) { renderLeftEvaluationPanel(ps, buf, font, timeSec); renderRightFlatMiniGrid(ps, buf); }
    ps.popPose();

    if (EverbookSessionState.isRiftMode()) TruncatedIcosahedronCage.renderCage(ps, buf, cam, renderAnchor, TruncatedIcosahedronCage.CAGE_RADIUS, timeSec);
    buf.endBatch(RawGeometry.DIALOGUE_SOLID);
    buf.endBatch(RenderType.lines());
    buf.endBatch();
    RenderSystem.enableDepthTest();
  }

  private static void renderWrappedPatterns(PoseStack poseStack, MultiBufferSource bufferSource, Font font, float timeSeconds) {
    List<EverbookData.PatternEntry> patternEntries = EverbookSessionState.getCurrentEntries();
    float gridStartX = -((COLS_PER_ROW - 1) * CELL_SIZE) * 0.5f;
    float scrollRowOffset = EverbookSessionState.getScrollRowOffset();
    VertexConsumer quadConsumer = bufferSource.getBuffer(RawGeometry.DIALOGUE_SOLID);

    int introspectionDepth = 0;
    for (int index = 0; patternEntries.size() > index; index++) {
      EverbookData.PatternEntry patternEntry = patternEntries.get(index);
      String anglesSignature = patternEntry.angles();

      // Retrospection ("eee") closes the current introspection scope on itself
      if ("eee".equals(anglesSignature) && introspectionDepth > 0) {
        introspectionDepth--;
      }

      float visibleRow = (index / COLS_PER_ROW) - scrollRowOffset;
      if (visibleRow >= -0.6f && 5.2f >= visibleRow) {
        float cellCenterX = gridStartX + ((index % COLS_PER_ROW) * CELL_SIZE);
        float cellCenterY = PATTERN_TOP_Y - (visibleRow * CELL_SIZE);
        boolean isSelected = EverbookSessionState.getSelectedIds().contains(patternEntry.id());
        boolean isHovered = index == EverbookSessionState.getHoveredPatternIdx();

        drawPatternStrokes(
          poseStack, quadConsumer, cellCenterX, cellCenterY, 0.008f, CELL_SIZE * 0.36f,
          anglesSignature, patternEntry.startDir(), isSelected, isHovered, introspectionDepth, timeSeconds
        );
      }

      // Introspection ("qqq") shifts all glyphs AFTER it to the next color tier (Cyan -> Deep Blue -> Purple)
      if ("qqq".equals(anglesSignature)) {
        introspectionDepth++;
      }
    }

    poseStack.pushPose();
    poseStack.translate(0.0f, 0.96f, 0.01f);
    poseStack.scale(0.0085f, -0.0085f, 0.0085f);

    String modeLabel = EverbookSessionState.isRiftMode() ? "[RIFT LOCKED]" : "[SOUL CASTING]";
    String iotaCountLabel = "  " + patternEntries.size() + (patternEntries.size() == 1 ? " Iota" : " Iotas");
    float totalHeaderWidth = font.width(modeLabel + iotaCountLabel);

    ThreeDifiedTextRenderer.draw3D(poseStack, bufferSource, font, modeLabel, -totalHeaderWidth * 0.5f, 0.0f, TextEffectStyle.spectrum());
    font.drawInBatch(
      Component.literal("§f" + iotaCountLabel),
      (-totalHeaderWidth * 0.5f) + font.width(modeLabel),
      0.0f,
      0xFFFFFFFF,
      false,
      poseStack.last().pose(),
      bufferSource,
      Font.DisplayMode.SEE_THROUGH,
      0,
      15728880
    );
    poseStack.popPose();
  }


  private static void renderLeftEvaluationPanel(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Font font, float timeSeconds) {
    boolean inscribeHover = EverbookSessionState.isInscribeHovered();
    boolean evalHover = EverbookSessionState.isEvalHovered();
    float panelX = -2.42f;

    // 1. "Inscribe" Nebula Portal + Borderless Absolution (spectrum) Text
    PortalShaderHook.bind(inscribeHover ? CompositeDictionary.EVERBOOK_EMERALD : CompositeDictionary.EVERBOOK_DEFAULT)
      .at(panelX, 0.72f)
      .size(0.44f, 0.15f)
      .resolution(48, 8)
      .flushBatch()
      .render(poseStack, bufferSource, timeSeconds);

    TextEffectStyle rawSpectrum = TextEffectStyle.spectrum();
    TextEffectStyle borderlessAbsolution = new TextEffectStyle(
      rawSpectrum.colors(), rawSpectrum.stops(), rawSpectrum.scrollSpeed(),
      rawSpectrum.waveAmplitude(), rawSpectrum.waveFrequency(),
      TextEffectStyle.BackdropMode.NONE,
      rawSpectrum.glowColor(), rawSpectrum.secondaryGlowColor(), true, true, false, 0.0f
    );

    poseStack.pushPose();
    poseStack.translate(panelX, 0.75f, 0.045f);
    poseStack.scale(0.0085f, -0.0085f, 0.0085f);
    String inscribeLabel = "Inscribe";
    ThreeDifiedTextRenderer.draw3D(poseStack, bufferSource, font, inscribeLabel, -font.width(inscribeLabel) * 0.5f, 0.0f, borderlessAbsolution);
    poseStack.popPose();

    // 2. "Evaluate" Nebula Portal + Borderless Starly Text
    PortalShaderHook.bind(evalHover ? CompositeDictionary.EVERBOOK_EMERALD : CompositeDictionary.EVERBOOK_DEFAULT)
      .at(panelX, 0.36f)
      .size(0.44f, 0.15f)
      .resolution(48, 8)
      .flushBatch()
      .render(poseStack, bufferSource, timeSeconds);

    TextEffectStyle baseStarly = TextEffectStyle.starly(evalHover ? 0xFFFFD700 : 0xFFD8DEE9);
    TextEffectStyle borderlessStarly = new TextEffectStyle(
      baseStarly.colors(), baseStarly.stops(), baseStarly.scrollSpeed(),
      baseStarly.waveAmplitude(), baseStarly.waveFrequency(),
      TextEffectStyle.BackdropMode.NONE,
      baseStarly.glowColor(), baseStarly.secondaryGlowColor(), true
    );

    poseStack.pushPose();
    poseStack.translate(panelX, 0.39f, 0.045f);
    poseStack.scale(0.0085f, -0.0085f, 0.0085f);
    String evalLabel = "Evaluate";
    ThreeDifiedTextRenderer.draw3D(poseStack, bufferSource, font, evalLabel, -font.width(evalLabel) * 0.5f, 0.0f, borderlessStarly);
    poseStack.popPose();

    // 3. Evaluation Result Portal
    if (!EverbookSessionState.hasEvalResult()) return;
    boolean collapsed = EverbookSessionState.isEvalCollapsed();
    boolean success = EverbookSessionState.isEvalSuccess();

    ShaderDictionary resultDictionary = collapsed ? CompositeDictionary.EVERBOOK_CRIMSON
      : (success ? CompositeDictionary.EVERBOOK_EMERALD : CompositeDictionary.EVERBOOK_DEFAULT);

    PortalShaderHook.bind(resultDictionary)
      .at(panelX, -0.06f)
      .size(0.58f, 0.22f)
      .resolution(48, 8)
      .flushBatch()
      .render(poseStack, bufferSource, timeSeconds);

    drawCenteredText(poseStack, bufferSource, font, panelX, 0.05f, 0.0068f, (success ? "§aResult: §f" : "§cResult: §f") + EverbookSessionState.getEvalSummary());
    drawCenteredText(poseStack, bufferSource, font, panelX, -0.04f, 0.0068f, collapsed ? "§cToo many recursions, collapsed rift" : ("§7Recursions: §b" + EverbookSessionState.getEvalRecursions()));
  }

  private static void drawCenteredText(PoseStack ps, MultiBufferSource buf, Font font, float x, float y, float sc, String raw) {
    ps.pushPose();
    ps.translate(x, y, 0.045f);
    ps.scale(sc, -sc, sc);
    Component c = Component.literal(raw);
    font.drawInBatch(c, -font.width(c) * 0.5f, 0.0f, 0xFFFFFFFF, false, ps.last().pose(), buf, Font.DisplayMode.SEE_THROUGH, 0, 15728880);
    ps.popPose();
  }

  private static void renderRightFlatMiniGrid(PoseStack ps, MultiBufferSource buf) {
    EverbookMiniGridConfig.ensureLoaded();
    int hov = EverbookSessionState.getHoveredMiniNode();
    List<Integer> path = EverbookSessionState.getMiniGridPath();
    for (int r = 0; MINI_ROWS > r; r++) {
      for (int c = 0; MINI_COLS > c; c++) {
        int id = (r * MINI_COLS) + c;
        Point2D p = getMiniNodePoint(r, c);
        int col = (id == hov) ? EverbookMiniGridConfig.pointHoverColor : (path.contains(id) ? EverbookMiniGridConfig.pointActiveColor : EverbookMiniGridConfig.pointColor);
        RawGeometry.drawArcaneNodePrism(ps, buf, p.x(), p.y(), 0.01f, (id == hov || path.contains(id)) ? 0.025f : 0.015f, col);
      }
    }
    if (2 > path.size()) return;
    VertexConsumer vc = buf.getBuffer(RawGeometry.DIALOGUE_SOLID);
    Matrix4f mat = ps.last().pose();
    float core = EverbookMiniGridConfig.lineSize, out = core * EverbookMiniGridConfig.outlineSizeMultiplier;
    for (int i = 0; path.size() - 1 > i; i++) {
      Point2D pa = getMiniNodePoint(path.get(i) / MINI_COLS, path.get(i) % MINI_COLS), pb = getMiniNodePoint(path.get(i + 1) / MINI_COLS, path.get(i + 1) % MINI_COLS);
      drawMiniRibbon(vc, mat, pa.x(), pa.y(), pb.x(), pb.y(), 0.011f, out, EverbookMiniGridConfig.outlineColor);
      drawMiniRibbon(vc, mat, pa.x(), pa.y(), pb.x(), pb.y(), 0.013f, core, EverbookMiniGridConfig.lineColor);
    }
  }

  private static void drawMiniRibbon(VertexConsumer vc, Matrix4f mat, float x0, float y0, float x1, float y1, float z, float hw, int argb) {
    float dx = x1 - x0, dy = y1 - y0, len = (float) Math.hypot(dx, dy);
    if (1.0e-5f > len) return;
    float nx = (-dy / len) * hw, ny = (dx / len) * hw;
    RawGeometry.vertex(vc, mat, x0 - nx, y0 - ny, z, argb); RawGeometry.vertex(vc, mat, x1 - nx, y1 - ny, z, argb);
    RawGeometry.vertex(vc, mat, x1 + nx, y1 + ny, z, argb); RawGeometry.vertex(vc, mat, x0 + nx, y0 + ny, z, argb);
    RawGeometry.vertex(vc, mat, x0 + nx, y0 + ny, z, argb); RawGeometry.vertex(vc, mat, x1 + nx, y1 + ny, z, argb);
    RawGeometry.vertex(vc, mat, x1 - nx, y1 - ny, z, argb); RawGeometry.vertex(vc, mat, x0 - nx, y0 - ny, z, argb);
  }
}
