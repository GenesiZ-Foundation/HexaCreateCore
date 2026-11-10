package com.genesiz.hexacreatecore.components;

import net.minecraft.world.entity.Entity;

public interface IEntityComponent {
  void onInit(Entity entity);
  void onTick();
}

