package com.genesiz.hexacreatecore.hexcasting;

import net.minecraft.world.phys.Vec3;

public record ArcaneNode3D(
    int id,
    Vec3 worldPos,
    boolean isActivated,
    int nodeColor // Custom hex color
) {}
