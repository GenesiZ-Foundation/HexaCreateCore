package com.genesiz.hexacreatecore.threedified.tooltip;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.threedified.text.TextEffectStyle;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = HexaCreateCore.MODID)
public final class TooltipStyleRegistry extends SimpleJsonResourceReloadListener {

  public record ItemTooltipConfig(
    TextEffectStyle titleStyle,
    TextEffectStyle loreStyle,
    int borderColor,
    int bgColor,
    String shapeVariant
  ) {
    public ItemTooltipConfig(TextEffectStyle titleStyle, TextEffectStyle loreStyle, int borderColor, int bgColor) {
      this(titleStyle, loreStyle, borderColor, bgColor, "");
    }
  }

  private static final Map<ResourceLocation, ItemTooltipConfig> ITEM_CONFIGS = new ConcurrentHashMap<>();
  private static final Map<TagKey<Item>, ItemTooltipConfig> TAG_CONFIGS = new LinkedHashMap<>();
  private static final Map<String, ItemTooltipConfig> CONTAINS_CONFIGS = new LinkedHashMap<>();

  public TooltipStyleRegistry() {
    super(new Gson(), "tooltip_styles");
  }

  @SubscribeEvent
  public static void onAddReloadListeners(AddReloadListenerEvent event) {
    event.addListener(new TooltipStyleRegistry());
  }

  @Override
  protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager rm, ProfilerFiller profiler) {
    ITEM_CONFIGS.clear();
    TAG_CONFIGS.clear();
    CONTAINS_CONFIGS.clear();

    for (var entry : objects.entrySet()) {
      if (!entry.getValue().isJsonObject()) continue;
      JsonObject root = entry.getValue().getAsJsonObject();

      for (var itemEntry : root.entrySet()) {
        if (!itemEntry.getValue().isJsonObject()) continue;
        JsonObject cfgObj = itemEntry.getValue().getAsJsonObject();

        TextEffectStyle titleStyle = cfgObj.has("title_style") ? parseStyleElement(cfgObj.get("title_style")) : null;
        TextEffectStyle loreStyle = cfgObj.has("lore_style") ? parseStyleElement(cfgObj.get("lore_style")) : null;
        int borderCol = cfgObj.has("border_color") ? TextEffectStyle.parseHex(cfgObj.get("border_color").getAsString()) : 0xFF8A4FFF;
        int bgCol = cfgObj.has("bg_color") ? TextEffectStyle.parseHex(cfgObj.get("bg_color").getAsString()) : 0xEE0D0A14;

        String titleSpec = (cfgObj.has("title_style") && cfgObj.get("title_style").isJsonPrimitive())
        ? cfgObj.get("title_style").getAsString().toLowerCase(Locale.ROOT) : "";
        String shapeVariant = cfgObj.has("shape_variant")
        ? cfgObj.get("shape_variant").getAsString().toLowerCase(Locale.ROOT)
        : titleSpec;
        ItemTooltipConfig config = new ItemTooltipConfig(titleStyle, loreStyle, borderCol, bgCol, shapeVariant);

        String key = itemEntry.getKey();
        if (key.startsWith("*") && key.endsWith("*") && key.length() > 2) {
          CONTAINS_CONFIGS.put(key.substring(1, key.length() - 1).toLowerCase(Locale.ROOT), config);
        } else if (key.startsWith("#")) {
          ResourceLocation tagLoc = ResourceLocation.tryParse(key.substring(1));
          if (tagLoc != null) TAG_CONFIGS.put(TagKey.create(Registries.ITEM, tagLoc), config);
        } else {
          ResourceLocation itemLoc = ResourceLocation.tryParse(key);
          if (itemLoc != null) ITEM_CONFIGS.put(itemLoc, config);
        }
      }
    }
  }

  public static ItemTooltipConfig getForStack(ItemStack stack) {
    if (stack.isEmpty()) return null;
    ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
    ItemTooltipConfig direct = ITEM_CONFIGS.get(id);
    if (direct != null) return direct;

    String path = id.getPath().toLowerCase(Locale.ROOT);
    String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
    for (var sub : CONTAINS_CONFIGS.entrySet()) {
      if (path.contains(sub.getKey()) || name.contains(sub.getKey())) return sub.getValue();
    }

    for (var tagEntry : TAG_CONFIGS.entrySet()) {
      if (stack.is(tagEntry.getKey())) return tagEntry.getValue();
    }
    return TooltipCategoryClassifier.classify(stack);
  }

  public static TextEffectStyle parseStyleElement(JsonElement el) {
    if (el == null || el.isJsonNull()) return null;
    if (el.isJsonPrimitive()) return parseStyleSpec(el.getAsString());
    if (!el.isJsonObject()) return null;

    JsonObject o = el.getAsJsonObject();
    List<Integer> colors = new ArrayList<>();
    if (o.has("colors")) {
      for (JsonElement c : o.getAsJsonArray("colors")) colors.add(TextEffectStyle.parseHex(c.getAsString()));
    }
    if (colors.isEmpty()) colors.add(0xFFFFFFFF);

    List<Float> stops = new ArrayList<>();
    if (o.has("stops")) {
      for (JsonElement s : o.getAsJsonArray("stops")) stops.add(s.getAsFloat());
    }

    float scrollSpeed = o.has("scroll_speed") ? o.get("scroll_speed").getAsFloat() : 0.0f;
    float waveAmp = o.has("wave_amplitude") ? o.get("wave_amplitude").getAsFloat() : 0.0f;
    float waveFreq = o.has("wave_frequency") ? o.get("wave_frequency").getAsFloat() : 0.25f;
    String modeStr = o.has("backdrop") ? o.get("backdrop").getAsString().toUpperCase(Locale.ROOT) : "NONE";
    TextEffectStyle.BackdropMode mode = TextEffectStyle.BackdropMode.valueOf(modeStr);
    int glow1 = o.has("glow_color") ? TextEffectStyle.parseHex(o.get("glow_color").getAsString()) : 0;
    int glow2 = o.has("secondary_glow_color") ? TextEffectStyle.parseHex(o.get("secondary_glow_color").getAsString()) : 0;
    boolean sparkles = o.has("sparkles") && o.get("sparkles").getAsBoolean();
    boolean rainbow = o.has("rainbow_stroke") && o.get("rainbow_stroke").getAsBoolean();
    boolean prismatic = o.has("prismatic_outline") && o.get("prismatic_outline").getAsBoolean();
    float angle = o.has("gradient_angle") ? o.get("gradient_angle").getAsFloat() : 0.0f;

    return new TextEffectStyle(colors, stops, scrollSpeed, waveAmp, waveFreq, mode, glow1, glow2, sparkles, rainbow, prismatic, angle);
  }

  public static TextEffectStyle parseStyleSpec(String spec) {
    if (spec == null || spec.isEmpty()) return null;
    String[] parts = spec.trim().split(":", 2);
    String name = parts[0].toLowerCase(Locale.ROOT);
    String arg = parts.length > 1 ? parts[1].trim() : "";

    return switch (name) {
      case "spectrum", "eternity" -> TextEffectStyle.spectrum();
      case "prismatic", "prismatic_outline", "penetrator" -> TextEffectStyle.prismaticOutline();
      case "glitch", "chromatic_glitch" -> arg.isEmpty() ? TextEffectStyle.glitch() : TextEffectStyle.glitch(arg);
      case "aurora" -> TextEffectStyle.aurora(arg.isEmpty() ? "#21B7D1" : arg);
      case "starly" -> TextEffectStyle.starly(arg.isEmpty() ? "#FFD700" : arg);
      case "outline" -> TextEffectStyle.outline(arg.isEmpty() ? "#CD7F32" : arg);
      case "genesis" -> new TextEffectStyle(
        List.of(0xFFFF1E56, 0xFF8B0000, 0xFFDC2626, 0xFFFF1E56),
        List.of(0.0f, 0.35f, 0.7f, 1.0f),
        2.4f, 0.45f, 0.38f,
        TextEffectStyle.BackdropMode.CHROMATIC_GLITCH, 0x88FF1E56, 0x88660011, true, true, false, 0.0f
      );
      default -> null;
    };
  }
}
