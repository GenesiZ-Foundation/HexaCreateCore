package com.genesiz.hexacreatecore.threedified.threedishapes_factory;

public final class ThreeDiShapeFactory {

  private ThreeDiShapeFactory() {}

  /** Extruded 3D corner brackets that hug all 4 corners of any dialogue/tooltip box */
  public static ShapeBlueprint cornerBrackets(float size, float depth, int primaryColor, int secondaryColor) {
    return new ShapeBlueprint(
      "corner_brackets", ShapeAnchor.ALL_CORNERS,
      size, depth, 0.0f, 0.0f, 1.5f, 0.0f, primaryColor, secondaryColor, 1
    );
  }

  /** Floating rotating 3D octahedral diamond crystal anchored at top/bottom/center */
  public static ShapeBlueprint floatingCrystal(ShapeAnchor anchor, float size, float offsetY, float spinSpeed, int primaryColor, int secondaryColor) {
    return new ShapeBlueprint(
      "floating_crystal", anchor,
      size, size * 0.6f, 0.0f, offsetY, 2.0f, spinSpeed, primaryColor, secondaryColor, 4
    );
  }

  /** Spinning segmented arcane halo ring behind a node */
  public static ShapeBlueprint arcaneRing(float radius, float depth, float spinSpeed, int segments, int primaryColor, int secondaryColor) {
    return new ShapeBlueprint(
      "arcane_ring", ShapeAnchor.CENTER,
      radius, depth, 0.0f, 0.0f, -1.5f, spinSpeed, primaryColor, secondaryColor, Math.max(4, segments)
    );
  }

  /** Swept geometric side-wings flanking left and right edges of a banner or title box */
  public static ShapeBlueprint sideWings(float wingSpan, float depth, int primaryColor, int secondaryColor) {
    return new ShapeBlueprint(
      "side_wings", ShapeAnchor.LEFT_AND_RIGHT,
      wingSpan, depth, 0.0f, 0.0f, 1.0f, 0.0f, primaryColor, secondaryColor, 2
    );
  }

  /** Kingdom Two Crowns-inspired black & gray 5-spire crown with 5 graduated Genesis diamond crystals */
  public static ShapeBlueprint kingdomCrown(float width, float offsetY, int crownDarkColor, int crownGrayColor) {
    return new ShapeBlueprint(
      "kingdom_crown", ShapeAnchor.TOP_CENTER,
      width, 3.5f, 0.0f, offsetY, 1.5f, 0.0f, crownDarkColor, crownGrayColor, 5
    );
  }

  /** Two interlacing Minecraft-style crimson chains weaving infinity (∞) loops in an oval behind a box */
  public static ShapeBlueprint interlacedChains(float padding, float linkThickness, float flowSpeedDeg, int chainPrimary, int chainSecondary) {
    return new ShapeBlueprint(
      "interlaced_chains", ShapeAnchor.CENTER,
      padding, linkThickness, 0.0f, 0.0f, -3.8f, flowSpeedDeg, chainPrimary, chainSecondary, 56
    );
  }
}
