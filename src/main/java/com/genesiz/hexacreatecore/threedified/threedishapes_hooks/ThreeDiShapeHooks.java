package com.genesiz.hexacreatecore.threedified.threedishapes_hooks;

import com.genesiz.hexacreatecore.threedified.threedishapes_factory.ShapeAnchor;
import com.genesiz.hexacreatecore.threedified.threedishapes_factory.ShapeBlueprint;
import com.genesiz.hexacreatecore.threedified.threedishapes_factory.ThreeDiShapeFactory;
import com.genesiz.hexacreatecore.threedified.threedishapes_renderers.ThreeDiShapeRendererRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ThreeDiShapeHooks {

  private static final Map<String, List<ShapeBlueprint>> ATTACHMENTS = new ConcurrentHashMap<>();

  static {
    attach(UiShapeTarget.BOSS_RUSH_TITLE, List.of(
      ThreeDiShapeFactory.sideWings(16.0f, 3.0f, 0xFFFFD700, 0xFFFF8C00),
      ThreeDiShapeFactory.floatingCrystal(ShapeAnchor.TOP_CENTER, 8.0f, -7.0f, 90.0f, 0xFFFFF59D, 0xFFF59E0B)
    ));
    attach(UiShapeTarget.DIFFICULTY_CARD, "genesis", List.of(
      ThreeDiShapeFactory.kingdomCrown(58.0f, -1.5f, 0xFF16141C, 0xFF6B7280)
    ));
    attach(UiShapeTarget.DIFFICULTY_DESCRIPTION, "genesis", List.of(
      ThreeDiShapeFactory.interlacedChains(7.0f, 2.2f, 22.0f, 0xFFFF1E56, 0xFF7F0019)
    ));
    attach(UiShapeTarget.DIFFICULTY_CARD, "absolution", List.of(
      ThreeDiShapeFactory.cornerBrackets(8.0f, 2.0f, 0xFFB233FF, 0xFF3B0764),
      ThreeDiShapeFactory.floatingCrystal(ShapeAnchor.TOP_CENTER, 7.0f, -6.0f, 65.0f, 0xFFE9D5FF, 0xFF7E22CE)
    ));
    attach(UiShapeTarget.DIFFICULTY_CARD, "ethereal", List.of(
      ThreeDiShapeFactory.cornerBrackets(7.0f, 2.0f, 0xFF38BDF8, 0xFF0284C7)
    ));
        attach(UiShapeTarget.TOOLTIP_FRAME, "genesis", List.of(
      ThreeDiShapeFactory.interlacedChains(5.0f, 1.6f, 20.0f, 0xFFFF1E56, 0xFF7F0019)
    ));
    attach(UiShapeTarget.TOOLTIP_FRAME, "amethyst", List.of(
      ThreeDiShapeFactory.cornerBrackets(6.0f, 1.8f, 0xFFA855F7, 0xFF3B0764),
      ThreeDiShapeFactory.floatingCrystal(ShapeAnchor.TOP_CENTER, 5.5f, -5.0f, 75.0f, 0xFFE9D5FF, 0xFF9333EA)
    ));
    attach(UiShapeTarget.TOOLTIP_FRAME, "spawner", List.of(
      ThreeDiShapeFactory.cornerBrackets(6.0f, 1.8f, 0xFF38BDF8, 0xFF1D4ED8)
    ));
    attach(UiShapeTarget.TOOLTIP_FRAME, "weapon", List.of(
      ThreeDiShapeFactory.cornerBrackets(6.0f, 1.8f, 0xFFA8A29E, 0xFF3B3230)
    ));
  }


  private ThreeDiShapeHooks() {}

  private static String key(UiShapeTarget target, String variant) {
    return (variant == null || variant.isEmpty()) ? target.name() : (target.name() + ":" + variant.toLowerCase());
  }

  public static void attach(UiShapeTarget target, List<ShapeBlueprint> shapes) {
    ATTACHMENTS.put(key(target, ""), new ArrayList<>(shapes));
  }

  public static void attach(UiShapeTarget target, String variant, List<ShapeBlueprint> shapes) {
    ATTACHMENTS.put(key(target, variant), new ArrayList<>(shapes));
  }

  public static void renderAttachedShapes(PoseStack poseStack, MultiBufferSource bufferSource, UiShapeTarget target, String variant, float centerX, float centerY, float centerZ, float boxWidth, float boxHeight) {
    List<ShapeBlueprint> shapes = (variant != null && !variant.isEmpty()) ? ATTACHMENTS.get(key(target, variant)) : null;
    if (shapes == null) shapes = ATTACHMENTS.get(key(target, ""));
    if (shapes == null || shapes.isEmpty()) return;

    float timeSec = (System.currentTimeMillis() % 600000L) / 1000.0f;
    poseStack.pushPose();
    poseStack.translate(centerX, centerY, centerZ);
    for (ShapeBlueprint bp : shapes) {
      ThreeDiShapeRendererRegistry.renderBlueprint(poseStack, bufferSource, bp, boxWidth, boxHeight, timeSec);
    }
    poseStack.popPose();
  }
}
