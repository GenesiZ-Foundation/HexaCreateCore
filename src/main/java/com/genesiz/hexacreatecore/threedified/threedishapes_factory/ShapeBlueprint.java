package com.genesiz.hexacreatecore.threedified.threedishapes_factory;

public record ShapeBlueprint(
  String rendererId,
  ShapeAnchor anchor,
  float size,
  float depth,
  float offsetX,
  float offsetY,
  float offsetZ,
  float spinSpeedDegPerSec,
  int primaryColor,
  int secondaryColor,
  int segments
) {
  public ShapeBlueprint withColors(int primary, int secondary) {
    return new ShapeBlueprint(rendererId, anchor, size, depth, offsetX, offsetY, offsetZ, spinSpeedDegPerSec, primary, secondary, segments);
  }
}
