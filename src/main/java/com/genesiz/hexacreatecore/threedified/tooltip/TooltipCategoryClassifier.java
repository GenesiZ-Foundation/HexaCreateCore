package com.genesiz.hexacreatecore.threedified.tooltip;

import com.genesiz.hexacreatecore.threedified.text.TextEffectStyle;
import com.genesiz.hexacreatecore.threedified.tooltip.TooltipStyleRegistry.ItemTooltipConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.common.Tags;

import java.util.List;
import java.util.Locale;

public final class TooltipCategoryClassifier {

  private static final int DEFAULT_BG = 0xEE0D0A14;

  // 1. WOOD
  private static final ItemTooltipConfig WOOD_CFG = new ItemTooltipConfig(
    new TextEffectStyle(List.of(0xFF4A3219, 0xFF7D5A32, 0xFFB8945E), List.of(0.0f, 0.5f, 1.0f),
      0.0f, 0.15f, 0.25f, TextEffectStyle.BackdropMode.NONE, 0, 0, false),
    null, 0xFF5A4020, DEFAULT_BG
  );

  // 2. STONE (excluding netherrack/end_stone)
  private static final ItemTooltipConfig STONE_CFG = new ItemTooltipConfig(
    new TextEffectStyle(List.of(0xFF9A9A9A, 0xFF6B6B70, 0xFF3E3E44), List.of(0.0f, 0.5f, 1.0f),
      0.0f, 0.0f, 0.0f, TextEffectStyle.BackdropMode.NONE, 0, 0, false),
    null, 0xFF64646E, DEFAULT_BG
  );

  // 3. GENERIC INGREDIENTS
  private static final ItemTooltipConfig INGREDIENT_CFG = new ItemTooltipConfig(
    new TextEffectStyle(List.of(0xFFFFFFFF, 0xFFD4D4D8, 0xFFA1A1AA), List.of(0.0f, 0.5f, 1.0f),
      0.0f, 0.0f, 0.0f, TextEffectStyle.BackdropMode.NONE, 0, 0, false),
    null, 0xFFB8B8C2, DEFAULT_BG
  );

  // 4. INGOTS
  private static final ItemTooltipConfig INGOT_CFG = new ItemTooltipConfig(
    new TextEffectStyle(List.of(0xFFF4F4F5, 0xFFD8D8DC, 0xFFC87544, 0xFFE39464, 0xFFF4F4F5), List.of(0.0f, 0.25f, 0.5f, 0.75f, 1.0f),
      0.75f, 0.0f, 0.0f, TextEffectStyle.BackdropMode.NONE, 0, 0, true),
    null, 0xFF9A6B14, DEFAULT_BG
  );

  // 5. GEMS
  private static final ItemTooltipConfig GEM_CFG = new ItemTooltipConfig(
    new TextEffectStyle(List.of(0xFF5CE6E6, 0xFF28B8D8, 0xFF1E48A8, 0xFF5CE6E6), List.of(0.0f, 0.35f, 0.75f, 1.0f),
      1.1f, 0.22f, 0.28f, TextEffectStyle.BackdropMode.NONE, 0, 0, true),
    null, 0xFF1B7A88, DEFAULT_BG
  );

  // 6. AMETHYST & HEXCASTING STAFFS
  private static final ItemTooltipConfig AMETHYST_CFG = new ItemTooltipConfig(
    new TextEffectStyle(List.of(0xFFFFFFFF, 0xFFE9D5FF, 0xFFA855F7, 0xFFFFFFFF), List.of(0.0f, 0.35f, 0.7f, 1.0f),
      1.6f, 0.25f, 0.28f, TextEffectStyle.BackdropMode.PRISMATIC_OUTLINE, 0xFFFF66CC, 0xFF9333EA, true),
    null, 0xFF9333EA, 0xEE180A26, "amethyst"
  );

  // 7. FUNCTIONAL BLOCKS
  private static final ItemTooltipConfig FUNCTIONAL_CFG = new ItemTooltipConfig(
    new TextEffectStyle(List.of(0xFFFFFFFF, 0xFFD1D5DB, 0xFF8E929B, 0xFFFFFFFF), List.of(0.0f, 0.35f, 0.7f, 1.0f),
      1.2f, 0.16f, 0.26f, TextEffectStyle.BackdropMode.NONE, 0, 0, false),
    null, 0xFF8C8C94, 0xEE1C120A
  );

  // 8. REDSTONE (30deg angled gradient)
  private static final ItemTooltipConfig REDSTONE_CFG = new ItemTooltipConfig(
    new TextEffectStyle(List.of(0xFFFF1E1E, 0xFFFF5533, 0xFF8F6638, 0xFF6B4A24, 0xFFFF1E1E), List.of(0.0f, 0.30f, 0.55f, 0.80f, 1.0f),
      2.6f, 0.45f, 0.34f, TextEffectStyle.BackdropMode.NONE, 0, 0, false, false, false, 30.0f),
    null, 0xFFDC2626, 0xEE1C0606
  );

  // 9. WEAPONS
  private static final ItemTooltipConfig WEAPON_CFG = new ItemTooltipConfig(
    new TextEffectStyle(List.of(0xFFF4F4F5, 0xFFA8A29E, 0xFF574B47, 0xFF3B3230, 0xFFF4F4F5), List.of(0.0f, 0.30f, 0.60f, 0.82f, 1.0f),
      0.85f, 0.32f, 0.22f, TextEffectStyle.BackdropMode.PRISMATIC_OUTLINE, 0xFFE4E4E7, 0xFF786862, true),
    null, 0xFF7A6860, 0xF2060608, "weapon"
  );

  // 10. ARMOR
  private static final ItemTooltipConfig ARMOR_CFG = new ItemTooltipConfig(
    new TextEffectStyle(List.of(0xFF8C5332, 0xFFA86B44, 0xFF38BDF8, 0xFF67E8F9, 0xFF8C5332), List.of(0.0f, 0.28f, 0.58f, 0.80f, 1.0f),
      0.90f, 0.0f, 0.0f, TextEffectStyle.BackdropMode.GLOW_BLOOM, 0x6638BDF8, 0x448C5332, false),
    null, 0xFF8C5332, DEFAULT_BG
  );

  // 11. TOOLS
  private static final ItemTooltipConfig TOOL_CFG = new ItemTooltipConfig(
    new TextEffectStyle(List.of(0xFF9CA3AF, 0xFF6B7280, 0xFF4B423F, 0xFF362F2D, 0xFF9CA3AF), List.of(0.0f, 0.30f, 0.60f, 0.82f, 1.0f),
      0.65f, 0.0f, 0.0f, TextEffectStyle.BackdropMode.GLOW_BLOOM, 0x559CA3AF, 0x336B7280, true),
    null, 0xFF85858D, DEFAULT_BG
  );

  // 12. SPAWNERS & SPAWN EGGS
  private static final ItemTooltipConfig SPAWNER_CFG = new ItemTooltipConfig(
    new TextEffectStyle(List.of(0xFFFF7A1A, 0xFFFFB347, 0xFF2563EB, 0xFF38BDF8, 0xFFFF7A1A), List.of(0.0f, 0.28f, 0.55f, 0.80f, 1.0f),
      2.8f, 0.25f, 0.30f, TextEffectStyle.BackdropMode.PRISMATIC_OUTLINE, 0xFFFFFFFF, 0xFF38BDF8, false),
    null, 0xFF2563EB, 0xEE060F24, "spawner"
  );


  private static final TagKey<Item> TAG_INGOTS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ingots"));
  private static final TagKey<Item> TAG_GEMS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "gems"));
  private static final TagKey<Item> TAG_REDSTONE = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "dusts/redstone"));

  private TooltipCategoryClassifier() {}

  public static ItemTooltipConfig classify(ItemStack stack) {
    if (stack.isEmpty()) return null;
    Item item = stack.getItem();
    ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
    String path = id.getPath().toLowerCase(Locale.ROOT);
    String displayLower = stack.getHoverName().getString().toLowerCase(Locale.ROOT);

    if (path.contains("amethyst") || displayLower.contains("amethyst")
    || (id.getNamespace().equals("hexcasting") && path.contains("staff"))) {return AMETHYST_CFG;}
    if (item instanceof SpawnEggItem || path.contains("spawner") || path.contains("spawn_egg")) return SPAWNER_CFG;
    if (item instanceof SwordItem || item instanceof ProjectileWeaponItem || item instanceof TridentItem
    || item instanceof MaceItem || stack.is(ItemTags.WEAPON_ENCHANTABLE) || stack.is(Tags.Items.MELEE_WEAPON_TOOLS)
    || stack.is(Tags.Items.RANGED_WEAPON_TOOLS)) return WEAPON_CFG;
    if (item instanceof ArmorItem || item instanceof AnimalArmorItem || item instanceof ElytraItem
    || item instanceof ShieldItem || stack.is(ItemTags.ARMOR_ENCHANTABLE)) return ARMOR_CFG;
    if (item instanceof DiggerItem || item instanceof ShearsItem || item instanceof FlintAndSteelItem
    || item instanceof FishingRodItem || item instanceof BrushItem || item instanceof SpyglassItem
    || stack.is(Tags.Items.TOOLS) || path.contains("wrench")) return TOOL_CFG;
    if (isRedstoneItem(item, stack, path)) return REDSTONE_CFG;
    if (stack.is(TAG_INGOTS) || path.endsWith("_ingot") || path.contains("ingot_")) return INGOT_CFG;
    if (stack.is(TAG_GEMS) || path.endsWith("_gem") || item == Items.DIAMOND || item == Items.EMERALD
    || item == Items.LAPIS_LAZULI || item == Items.QUARTZ || item == Items.PRISMARINE_CRYSTALS) return GEM_CFG;

    if (item instanceof BlockItem blockItem) {
      Block block = blockItem.getBlock();
      if (isFunctionalBlock(block, path)) return FUNCTIONAL_CFG;
      ItemTooltipConfig colored = matchColoredBlock(path);
      if (colored != null) return colored;
      if (isWoodBlock(stack, path)) return WOOD_CFG;
      if (isStoneBlock(stack, path)) return STONE_CFG;
    }
    return INGREDIENT_CFG;
  }

  private static boolean isRedstoneItem(Item item, ItemStack stack, String path) {
    if (stack.is(TAG_REDSTONE)) return true;
    if (item == Items.REDSTONE || item == Items.REDSTONE_TORCH || item == Items.REPEATER
    || item == Items.COMPARATOR || item == Items.PISTON || item == Items.STICKY_PISTON
    || item == Items.OBSERVER || item == Items.DISPENSER || item == Items.DROPPER
    || item == Items.HOPPER || item == Items.LEVER || item == Items.DAYLIGHT_DETECTOR
    || item == Items.TARGET || item == Items.TRIPWIRE_HOOK || item == Items.NOTE_BLOCK
    || item == Items.TNT || item == Items.REDSTONE_LAMP || item == Items.CRAFTER) return true;
    if (item instanceof BlockItem bi) {
      Block b = bi.getBlock();
      if (b instanceof RedStoneWireBlock || b instanceof DiodeBlock || b instanceof RedstoneTorchBlock
      || b instanceof ButtonBlock || b instanceof PressurePlateBlock || b instanceof LeverBlock
      || b instanceof ObserverBlock || b instanceof DispenserBlock || b instanceof HopperBlock
      || b instanceof BaseRailBlock || b instanceof DaylightDetectorBlock || b instanceof TargetBlock) return true;
    }
    return path.contains("redstone") || path.contains("piston") || path.endsWith("_rail");
  }

  private static boolean isFunctionalBlock(Block block, String path) {
    return block instanceof CraftingTableBlock || block instanceof AbstractFurnaceBlock || block instanceof AnvilBlock
      || block instanceof EnchantingTableBlock || block instanceof BrewingStandBlock || block instanceof BeaconBlock
      || block instanceof EnderChestBlock || block instanceof ChestBlock || block instanceof BarrelBlock
      || block instanceof ShulkerBoxBlock || block instanceof GrindstoneBlock || block instanceof StonecutterBlock
      || block instanceof LoomBlock || block instanceof CartographyTableBlock || block instanceof SmithingTableBlock
      || block instanceof LecternBlock || block instanceof BellBlock || block instanceof RespawnAnchorBlock
      || block == Blocks.LODESTONE || block instanceof ScaffoldingBlock || block instanceof LadderBlock
      || block instanceof TorchBlock || block instanceof LanternBlock || block instanceof CampfireBlock
      || block instanceof BedBlock || block instanceof JukeboxBlock || block instanceof ChiseledBookShelfBlock
      || path.contains("table") || path.contains("furnace") || path.contains("anvil")
      || path.contains("chest") || path.contains("barrel") || path.contains("crate");
  }

  private static boolean isWoodBlock(ItemStack stack, String path) {
    if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS) || stack.is(ItemTags.WOODEN_SLABS)
    || stack.is(ItemTags.WOODEN_STAIRS) || stack.is(ItemTags.WOODEN_DOORS) || stack.is(ItemTags.WOODEN_TRAPDOORS)
    || stack.is(ItemTags.WOODEN_FENCES) || stack.is(ItemTags.FENCE_GATES) || stack.is(ItemTags.SAPLINGS)
    || stack.is(ItemTags.SIGNS) || stack.is(ItemTags.HANGING_SIGNS)) return true;
    return path.contains("_log") || path.contains("_wood") || path.contains("_planks")
    || path.contains("_stem") || path.contains("_hyphae") || path.contains("bamboo_mosaic");
  }

  private static boolean isStoneBlock(ItemStack stack, String path) {
    if (path.contains("netherrack") || path.contains("end_stone") || path.contains("blackstone")
      || path.contains("basalt") || path.contains("prismarine") || path.contains("sandstone")
      || path.contains("obsidian") || path.contains("quartz")) return false;
    if (stack.is(ItemTags.STONE_CRAFTING_MATERIALS) || stack.is(ItemTags.STONE_TOOL_MATERIALS)
      || stack.is(Tags.Items.STONES) || stack.is(Tags.Items.COBBLESTONES)) return true;
    return path.contains("stone") || path.contains("deepslate") || path.contains("granite")
      || path.contains("diorite") || path.contains("andesite") || path.contains("tuff")
      || path.contains("calcite") || path.contains("dripstone");
  }


  private static ItemTooltipConfig matchColoredBlock(String path) {
    boolean isColorable = path.contains("wool") || path.contains("concrete") || path.contains("terracotta")
    || path.contains("stained_glass") || path.contains("carpet") || path.contains("candle") || path.contains("banner");
    if (!isColorable) return null;

    if (path.startsWith("light_blue_")) return coloredConfig(0xFF7DD3FC, 0xFF0284C7);
    if (path.startsWith("light_gray_")) return coloredConfig(0xFFD4D4D8, 0xFF71717A);
    if (path.startsWith("white_")) return coloredConfig(0xFFFFFFFF, 0xFFCBD5E1);
    if (path.startsWith("orange_")) return coloredConfig(0xFFFB923C, 0xFFC2410C);
    if (path.startsWith("magenta_")) return coloredConfig(0xFFE879F9, 0xFFA21CAF);
    if (path.startsWith("yellow_")) return coloredConfig(0xFFFDE047, 0xFFCA8A04);
    if (path.startsWith("lime_")) return coloredConfig(0xFFA3E635, 0xFF4D7C0F);
    if (path.startsWith("pink_")) return coloredConfig(0xFFF9A8D4, 0xFFBE185D);
    if (path.startsWith("gray_")) return coloredConfig(0xFF9CA3AF, 0xFF374151);
    if (path.startsWith("cyan_")) return coloredConfig(0xFF22D3EE, 0xFF0E7490);
    if (path.startsWith("purple_")) return coloredConfig(0xFFC084FC, 0xFF6B21A8);
    if (path.startsWith("blue_")) return coloredConfig(0xFF60A5FA, 0xFF1D4ED8);
    if (path.startsWith("brown_")) return coloredConfig(0xFFA87654, 0xFF5C3A21);
    if (path.startsWith("green_")) return coloredConfig(0xFF4ADE80, 0xFF15803D);
    if (path.startsWith("red_")) return coloredConfig(0xFFF87171, 0xFFB91C1C);
    if (path.startsWith("black_")) return coloredConfig(0xFF52525B, 0xFF18181B);
    return null;
  }

  private static ItemTooltipConfig coloredConfig(int lightWool, int darkWool) {
    return new ItemTooltipConfig(
      new TextEffectStyle(List.of(lightWool, darkWool), List.of(0.0f, 1.0f),
        0.0f, 0.10f, 0.25f, TextEffectStyle.BackdropMode.NONE, 0, 0, false),
      null, lightWool, DEFAULT_BG
    );
  }
}
