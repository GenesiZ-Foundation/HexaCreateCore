package com.genesiz.hexacreatecore.datagen;

import com.genesiz.hexacreatecore.HexaCreateCore;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModModelProvider extends ItemModelProvider {
  public ModModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
    super(output, HexaCreateCore.MODID, existingFileHelper);
  }
  @Override
  protected void registerModels() {
    withExistingParent("cursed_blacksmith_spawn_egg", mcLoc("item/template_spawn_egg"));
  }
}
