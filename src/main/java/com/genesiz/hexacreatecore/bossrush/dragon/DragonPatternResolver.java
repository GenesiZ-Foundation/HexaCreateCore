package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.bossrush.dragon.DragonAnimState.DragonAction;
import com.genesiz.hexacreatecore.bossrush.dragon.DragonSpatialSelector.DirectionCone;
import com.genesiz.hexacreatecore.bossrush.dragon.DragonSpatialSelector.RangeRing;
import com.genesiz.hexacreatecore.bossrush.dragon.DragonSpatialSelector.SpatialContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class DragonPatternResolver {

  public record PatternNode(
    String animKey,
    DragonAction action,
    EnumSet allowedCones,
    int durationTicks,
    int endCooldownTicks,
    PatternNode nextClose,
    PatternNode nextMid,
    PatternNode nextFar
  ) {
    public boolean isTerminal() {
      return action == null || action == DragonAction.NONE;
    }

    public boolean matchesCone(DirectionCone cone) {
      return allowedCones.isEmpty() || allowedCones.contains(cone);
    }

    public PatternNode branchForRing(RangeRing ring) {
      return switch (ring) {
        case CLOSE -> nextClose;
        case MEDIUM -> nextMid;
        case LONG -> nextFar;
      };
    }

    public void triggerOn(IMhEnderDragon mh) {
      int dur = (durationTicks > 0) ? durationTicks : action.defaultDurationTicks;
      if (action == DragonAction.CUSTOM_CLIP) {
        mh.hexacreatecore$triggerCustomClip(animKey, dur);
      } else {
        mh.hexacreatecore$triggerAction(action, dur);
      }
    }
  }

  public record WeightedOpener(int weight, PatternNode root) {}

  public record PhasePatternSet(
    List close,
    List mid,
    List far
  ) {
    public static final PhasePatternSet EMPTY = new PhasePatternSet(List.of(), List.of(), List.of());

    public List poolForRing(RangeRing ring) {
      return switch (ring) {
        case CLOSE -> close;
        case MEDIUM -> mid;
        case LONG -> far;
      };
    }
  }

  public record PatternTreeSet(
    PhasePatternSet phase1,
    PhasePatternSet phase2,
    PhasePatternSet phase3
  ) {
    public static final PatternTreeSet EMPTY = new PatternTreeSet(
      PhasePatternSet.EMPTY, PhasePatternSet.EMPTY, PhasePatternSet.EMPTY
    );

    public PhasePatternSet forPhase(int phase) {
      if (phase <= 1) return phase1;
      if (phase == 2) return phase2;
      return phase3;
    }
  }

  private DragonPatternResolver() {}

  public static PatternNode selectOpener(int phase, SpatialContext ctx, RandomSource rng) {
    PatternTreeSet tree = DragonPatternReloadListener.getPatternTree();
    PhasePatternSet phaseSet = tree.forPhase(phase);
    List pool = phaseSet.poolForRing(ctx.ring());
    if (pool.isEmpty()) return null;

    var candidates = new ArrayList(pool.size());
    int totalWeight = 0;
    for (Object obj : pool) {
      WeightedOpener entry = (WeightedOpener) obj;
      if (entry.weight() > 0 && !entry.root().isTerminal() && entry.root().matchesCone(ctx.cone())) {
        candidates.add(entry);
        totalWeight += entry.weight();
      }
    }
    if (candidates.isEmpty() || 0 >= totalWeight) return null;

    float roll = rng.nextFloat();
    float cumulative = 0.0f;
    for (Object obj : candidates) {
      WeightedOpener entry = (WeightedOpener) obj;
      cumulative += (float) entry.weight() / (float) totalWeight;
      if (cumulative > roll) {
        return entry.root();
      }
    }
    return ((WeightedOpener) candidates.get(candidates.size() - 1)).root();
  }

  public static PatternNode resolveNextStep(PatternNode current, SpatialContext ctx) {
    if (current == null) return null;
    PatternNode branch = current.branchForRing(ctx.ring());
    if (branch == null || branch.isTerminal()) return null;
    if (!branch.matchesCone(ctx.cone())) return null;
    return branch;
  }

  public static PatternTreeSet parsePatternTree(JsonObject root, Map keys) {
    PhasePatternSet p1 = root.has("phase_1") && root.get("phase_1").isJsonObject()
      ? parsePhaseSet(root.getAsJsonObject("phase_1"), keys)
      : PhasePatternSet.EMPTY;

    PhasePatternSet p2 = root.has("phase_2") && root.get("phase_2").isJsonObject()
      ? parsePhaseSet(root.getAsJsonObject("phase_2"), keys)
      : parsePhaseSet(root, keys);

    PhasePatternSet p3 = root.has("phase_3") && root.get("phase_3").isJsonObject()
      ? parsePhaseSet(root.getAsJsonObject("phase_3"), keys)
      : PhasePatternSet.EMPTY;

    return new PatternTreeSet(p1, p2, p3);
  }

  private static PhasePatternSet parsePhaseSet(JsonObject obj, Map keys) {
    return new PhasePatternSet(
      parseOpenerList(obj, "close", keys),
      parseOpenerList(obj, "mid", keys),
      parseOpenerList(obj, "far", keys)
    );
  }

  private static List parseOpenerList(JsonObject root, String key, Map keys) {
    if (!root.has(key) || !root.get(key).isJsonArray()) return List.of();
    var list = new ArrayList();
    for (JsonElement el : root.getAsJsonArray(key)) {
      if (!el.isJsonObject()) continue;
      JsonObject obj = el.getAsJsonObject();
      int weight = obj.has("weight") ? obj.get("weight").getAsInt() : 10;
      if (!obj.has("parts")) continue;
      PatternNode node = parseNode(obj.get("parts"), keys);
      if (node != null && !node.isTerminal()) {
        list.add(new WeightedOpener(weight, node));
      }
    }
    return List.copyOf(list);
  }

  private static PatternNode parseNode(JsonElement el, Map keys) {
    if (el == null || el.isJsonNull()) return null;

    if (el.isJsonPrimitive()) {
      String animKey = el.getAsString().trim();
      if (animKey.isEmpty()) return null;
      DragonAction act = resolveAction(animKey, keys);
      return new PatternNode(animKey, act, EnumSet.noneOf(DirectionCone.class), -1, -1, null, null, null);
    }

    if (!el.isJsonObject()) return null;
    JsonObject obj = el.getAsJsonObject();
    if (obj.entrySet().isEmpty() || !obj.has("name")) return null;

    String animKey = obj.get("name").getAsString().trim();
    DragonAction act = resolveAction(animKey, keys);
    EnumSet cones = parsePositionCones(obj.get("position"));
    int duration = obj.has("duration") ? obj.get("duration").getAsInt() : -1;
    int cooldown = obj.has("cooldown") ? obj.get("cooldown").getAsInt() : -1;

    PatternNode nextClose = null;
    PatternNode nextMid = null;
    PatternNode nextFar = null;

    if (obj.has("next") && obj.get("next").isJsonObject()) {
      JsonObject nextObj = obj.getAsJsonObject("next");
      if (!nextObj.entrySet().isEmpty()) {
        nextClose = parseNode(nextObj.get("close"), keys);
        nextMid   = parseNode(nextObj.get("mid"), keys);
        nextFar   = parseNode(nextObj.get("far"), keys);
      }
    }

    return new PatternNode(animKey, act, cones, duration, cooldown, nextClose, nextMid, nextFar);
  }

  private static DragonAction resolveAction(String animKey, Map keys) {
    Object mapped = keys.get(animKey);
    if (mapped instanceof DragonAction act) return act;
    try {
      return DragonAction.valueOf(animKey.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      // Any unmapped clip name automatically plays as a full-keyframe CUSTOM_CLIP
      return DragonAction.CUSTOM_CLIP;
    }
  }

  private static EnumSet parsePositionCones(JsonElement posEl) {
    var set = EnumSet.noneOf(DirectionCone.class);
    if (posEl == null || posEl.isJsonNull()) return set;

    if (posEl.isJsonPrimitive()) {
      addConeToken(set, posEl.getAsString());
    } else if (posEl.isJsonArray()) {
      for (JsonElement item : posEl.getAsJsonArray()) {
        if (item.isJsonPrimitive()) addConeToken(set, item.getAsString());
      }
    }
    return set;
  }

  private static void addConeToken(EnumSet set, String raw) {
    switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "forward", "front" -> set.add(DirectionCone.FRONT);
      case "left" -> set.add(DirectionCone.LEFT);
      case "right" -> set.add(DirectionCone.RIGHT);
      case "backward", "back" -> set.add(DirectionCone.BACK);
      case "any", "all" -> set.addAll(EnumSet.allOf(DirectionCone.class));
    }
  }
}
