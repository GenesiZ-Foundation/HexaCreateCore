package com.genesiz.hexacreatecore.threedified.threedishapes_renderers;

import com.genesiz.hexacreatecore.threedified.threedishapes_factory.ShapeBlueprint;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

@FunctionalInterface
public interface IShapeRenderer {
  void render(
    PoseStack poseStack,
    MultiBufferSource bufferSource,
    ShapeBlueprint blueprint,
    float boxWidth,
    float boxHeight,
    float timeSec
  );
}
