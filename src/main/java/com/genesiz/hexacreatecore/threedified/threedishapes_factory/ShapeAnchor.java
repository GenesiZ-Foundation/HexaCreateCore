package com.genesiz.hexacreatecore.threedified.threedishapes_factory;

public enum ShapeAnchor {
  CENTER(0.0f, 0.0f),
  TOP_CENTER(0.0f, -0.5f),
  BOTTOM_CENTER(0.0f, 0.5f),
  LEFT_CENTER(-0.5f, 0.0f),
  RIGHT_CENTER(0.5f, 0.0f),
  TOP_LEFT(-0.5f, -0.5f),
  TOP_RIGHT(0.5f, -0.5f),
  BOTTOM_LEFT(-0.5f, 0.5f),
  BOTTOM_RIGHT(0.5f, 0.5f),
  ALL_CORNERS(0.0f, 0.0f),
  LEFT_AND_RIGHT(0.0f, 0.0f);

  public final float normX;
  public final float normY;

  ShapeAnchor(float normX, float normY) {
    this.normX = normX;
    this.normY = normY;
  }
}

