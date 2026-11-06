package com.genesiz.hexacreatecore.entity.client;

import com.genesiz.hexacreatecore.entity.custom.CursedBlacksmithEntity;

import software.bernie.geckolib.renderer.GeoEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class CursedBlacksmithRenderer extends GeoEntityRenderer<CursedBlacksmithEntity> {

  public CursedBlacksmithRenderer(EntityRendererProvider.Context ctx) {
    super (ctx, new CursedBlacksmithModel());
  }
}
