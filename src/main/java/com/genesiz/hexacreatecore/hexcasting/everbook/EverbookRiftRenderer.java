package com.genesiz.hexacreatecore.hexcasting.everbook;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.hexcasting.HexagonalGridManager;
import com.genesiz.hexacreatecore.threedified.RawGeometry;
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
    float timeSec = (System.currentTimeMillis() % 600000L) / 1000.0f;

    RenderSystem.disableDepthTest();
    RenderSystem.enableBlend();
    ps.pushPose();
    ps.translate(renderAnchor.x - cam.x, renderAnchor.y - cam.y, renderAnchor.z - cam.z);
    ps.mulPose(Axis.YP.rotationDegrees(-renderYaw - 180.0f));

    EverbookNebulaPortal.renderPortal(buf.getBuffer(RawGeometry.DIALOGUE_SOLID), ps.last().pose(), 0.0f, 0.0f, RIFT_HALF_W, RIFT_HALF_H, timeSec, 32, 22, false, false);
    renderWrappedPatterns(ps, buf, font, timeSec);
    if (EverbookSessionState.isRiftMode()) { renderLeftEvaluationPanel(ps, buf, font, timeSec); renderRightFlatMiniGrid(ps, buf); }
    ps.popPose();

    if (EverbookSessionState.isRiftMode()) TruncatedIcosahedronCage.renderCage(ps, buf, cam, renderAnchor, TruncatedIcosahedronCage.CAGE_RADIUS, timeSec);
    buf.endBatch(RawGeometry.DIALOGUE_SOLID);
    buf.endBatch(RenderType.lines());
    buf.endBatch();
    RenderSystem.enableDepthTest();
  }

  private static void renderWrappedPatterns(PoseStack ps, MultiBufferSource buf, Font font, float timeSec) {
    List<EverbookData.PatternEntry> entries = EverbookSessionState.getCurrentEntries();
    float startX = -((COLS_PER_ROW - 1) * CELL_SIZE) * 0.5f, scroll = EverbookSessionState.getScrollRowOffset();
    VertexConsumer quads = buf.getBuffer(RawGeometry.DIALOGUE_SOLID);

    int introspectionDepth = 0;
    for (int i = 0; entries.size() > i; i++) {
      EverbookData.PatternEntry e = entries.get(i);
      String sig = e.angles();

      // Retrospection ("eee") closes the current introspection scope on itself
      if ("eee".equals(sig) && introspectionDepth > 0) {
        introspectionDepth--;
      }

      float visRow = (i / COLS_PER_ROW) - scroll;
      if (visRow >= -0.6f && 5.2f >= visRow) {
        drawPatternStrokes(ps, quads, startX + ((i % COLS_PER_ROW) * CELL_SIZE), PATTERN_TOP_Y - (visRow * CELL_SIZE), 0.008f, CELL_SIZE * 0.36f,
          sig, e.startDir(), EverbookSessionState.getSelectedIds().contains(e.id()), i == EverbookSessionState.getHoveredPatternIdx(), introspectionDepth, timeSec);
      }

      // Introspection ("qqq") shifts all glyphs AFTER it to the next color tier (Cyan -> Deep Blue -> Purple)
      if ("qqq".equals(sig)) {
        introspectionDepth++;
      }
    }

    ps.pushPose();
    ps.translate(0.0f, 0.96f, 0.01f);
    ps.scale(0.0085f, -0.0085f, 0.0085f);
    String mode = EverbookSessionState.isRiftMode() ? "[RIFT LOCKED]" : "[SOUL CASTING]", count = "  " + entries.size() + " sigils";
    float w = font.width(mode + count);
    ThreeDifiedTextRenderer.draw3D(ps, buf, font, mode, -w * 0.5f, 0.0f, TextEffectStyle.spectrum());
    font.drawInBatch(Component.literal("§f" + count), (-w * 0.5f) + font.width(mode), 0.0f, 0xFFFFFFFF, false, ps.last().pose(), buf, Font.DisplayMode.SEE_THROUGH, 0, 15728880);
    ps.popPose();
  }

  private static void renderLeftEvaluationPanel(PoseStack ps, MultiBufferSource.BufferSource buf, Font font, float timeSec) {
    boolean hov = EverbookSessionState.isEvalHovered();
    float panelX = -2.42f;

    // Keep the miniature wobbling Nebula Rift behind Evaluate
    EverbookNebulaPortal.renderPortal(buf.getBuffer(RawGeometry.DIALOGUE_SOLID), ps.last().pose(), panelX, 0.36f, 0.44f, 0.15f, timeSec, 12, 8, hov, false);
    buf.endBatch(RawGeometry.DIALOGUE_SOLID);

    // Strip the rectangular BackdropMode.GLOW_BLOOM box while keeping the Starly gradient + 4-point sparkles
    TextEffectStyle baseStarly = TextEffectStyle.starly(hov ? 0xFFFFD700 : 0xFFD8DEE9);
    TextEffectStyle borderlessStarly = new TextEffectStyle(
      baseStarly.colors(), baseStarly.stops(), baseStarly.scrollSpeed(),
      baseStarly.waveAmplitude(), baseStarly.waveFrequency(),
      TextEffectStyle.BackdropMode.NONE,
      baseStarly.glowColor(), baseStarly.secondaryGlowColor(), true
    );

    ps.pushPose();
    ps.translate(panelX, 0.39f, 0.045f);
    ps.scale(0.0085f, -0.0085f, 0.0085f);
    String label = "Evaluate";
    ThreeDifiedTextRenderer.draw3D(ps, buf, font, label, -font.width(label) * 0.5f, 0.0f, borderlessStarly);
    ps.popPose();

    if (!EverbookSessionState.hasEvalResult()) return;
    boolean col = EverbookSessionState.isEvalCollapsed(), ok = EverbookSessionState.isEvalSuccess();
    EverbookNebulaPortal.renderPortal(buf.getBuffer(RawGeometry.DIALOGUE_SOLID), ps.last().pose(), panelX, -0.06f, 0.58f, 0.22f, timeSec, 14, 10, ok && !col, col);
    buf.endBatch(RawGeometry.DIALOGUE_SOLID);

    drawCenteredText(ps, buf, font, panelX, 0.05f, 0.0068f, (ok ? "§aResult: §f" : "§cResult: §f") + EverbookSessionState.getEvalSummary());
    drawCenteredText(ps, buf, font, panelX, -0.04f, 0.0068f, col ? "§cToo many recursions, collapsed rift" : ("§7Recursions: §b" + EverbookSessionState.getEvalRecursions()));
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
