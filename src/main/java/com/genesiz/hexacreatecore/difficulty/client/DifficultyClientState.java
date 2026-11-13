package com.genesiz.hexacreatecore.difficulty.client;

import com.genesiz.hexacreatecore.bossrush.data.BossHealthBarData.HealthBarStyle;
import com.genesiz.hexacreatecore.bossrush.data.BossRushReloadListener;
import com.genesiz.hexacreatecore.difficulty.data.DifficultyData.DifficultyProfile;
import com.genesiz.hexacreatecore.difficulty.data.DifficultyData.DifficultyTier;
import com.genesiz.hexacreatecore.difficulty.network.DifficultyPackets;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DifficultyClientState {
  public static DifficultyTier activeTier = DifficultyTier.NORMAL;
  public static boolean genesisUnlocked = false;
  public static int riposteCharges = 0;
  public static boolean ripostePrimed = false;
  public static long parryFlowEndsAtMs = 0L;
  public static final Map<String, List<String>> TIER_DESCRIPTIONS = new HashMap<>();
  public static final Map<String, HealthBarStyle> TIER_GAUGE_STYLES = new HashMap<>();

  // 500ms (0.5s) cubic-bezier(.35, 0, .65, 1) parry sweep state
  private static final float SWEEP_DURATION_MS = 500.0f;
  public static boolean isHoldingParry = false;
  public static long parryChargeStartMs = 0L;
  public static String lastResultLabel = "";
  public static int lastResultColor = 0xFFFFFFFF;
  public static float lockedCursorProgress = -1.0f;
  public static long resultDisplayUntilMs = 0L;

  // 3D Ring Difficulty Selector state
  public static boolean selectorOpen = false;
  public static float selectorBaseYaw = 0.0f;
  public static String hoveredTierId = "";
  public static boolean flowTapPending = false;
  public static long flowWindowEndsAtMs = 0L;

  public record ParrySoundEntry(String soundId, float volume, float pitch) {}
  public static final Map<String, ParrySoundEntry> PARRY_SOUNDS = new HashMap<>(Map.of(
    "red", new ParrySoundEntry("minecraft:block.amethyst_block.chime", 0.7f, 0.8f),
    "orange", new ParrySoundEntry("minecraft:block.amethyst_block.break", 0.8f, 1.0f),
    "green", new ParrySoundEntry("minecraft:block.amethyst_block.resonate", 0.9f, 1.2f),
    "white", new ParrySoundEntry("minecraft:block.amethyst_cluster.fall", 1.0f, 1.4f)
  ));

  public static void handleSync(DifficultyPackets.SyncPayload payload) {
    activeTier = DifficultyTier.fromId(payload.activeTierId());
    genesisUnlocked = payload.genesisUnlocked();
    riposteCharges = payload.riposteCharges();
    ripostePrimed = payload.ripostePrimed();
    parryFlowEndsAtMs = (payload.parryFlowRemainingMs() > 0L)
    ? (System.currentTimeMillis() + payload.parryFlowRemainingMs()) : 0L;

    if (payload.descriptionsJson() != null && !payload.descriptionsJson().isEmpty()) {
      Type mapType = new TypeToken<Map<String, List<String>>>() {}.getType();
      Map<String, List<String>> parsed = new Gson().fromJson(payload.descriptionsJson(), mapType);
      if (parsed != null) {
        TIER_DESCRIPTIONS.clear();
        TIER_DESCRIPTIONS.putAll(parsed);
      }
    }

    if (payload.gaugeBarsJson() != null && !payload.gaugeBarsJson().isEmpty() && !"{}".equals(payload.gaugeBarsJson())) {
      try {
        var obj = JsonParser.parseString(payload.gaugeBarsJson()).getAsJsonObject();
        TIER_GAUGE_STYLES.clear();
        TIER_GAUGE_STYLES.putAll(BossRushReloadListener.parseHealthBarsMap(obj));
      } catch (Exception ignored) {}
    }

    if (payload.parryFlowRemainingMs() > 0L) {
      parryFlowEndsAtMs = System.currentTimeMillis() + payload.parryFlowRemainingMs();
      flowTapPending = false;
    } else {
      parryFlowEndsAtMs = 0L;
      flowTapPending = false;
    }

    if (payload.parrySoundsJson() != null && !payload.parrySoundsJson().isEmpty() && !"{}".equals(payload.parrySoundsJson())) {
      try {
        var root = JsonParser.parseString(payload.parrySoundsJson()).getAsJsonObject();
        for (String key : List.of("red", "orange", "green", "white")) {
          if (!root.has(key)) continue;
          var elem = root.get(key);
          if (elem.isJsonPrimitive()) {
            PARRY_SOUNDS.put(key, new ParrySoundEntry(elem.getAsString(), 0.9f, 1.0f));
          } else if (elem.isJsonObject()) {
            var obj = elem.getAsJsonObject();
            String id = obj.has("sound") ? obj.get("sound").getAsString() : PARRY_SOUNDS.get(key).soundId();
            float vol = obj.has("volume") ? obj.get("volume").getAsFloat() : 0.9f;
            float pit = obj.has("pitch") ? obj.get("pitch").getAsFloat() : 1.0f;
            PARRY_SOUNDS.put(key, new ParrySoundEntry(id, vol, pit));
          }
        }
      } catch (Exception ignored) {}
    }
  }

  public static HealthBarStyle getActiveGaugeStyle() {
    return TIER_GAUGE_STYLES.getOrDefault(activeTier.id, DifficultyProfile.defaultGauge(activeTier.accentColor));
  }

  public static boolean isInParryFlow() {
    return parryFlowEndsAtMs > System.currentTimeMillis();
  }

  /** Evaluates CSS cubic-bezier(0.35, 0.0, 0.65, 1.0) for normalized elapsed time x in [0, 1] */
  public static float evalCubicBezier(float x) {
    if (0.0f >= x) return 0.0f;
    if (x >= 1.0f) return 1.0f;
    // Binary search parameter u in [0, 1] such that Bx(u) == x, with x1=0.35, x2=0.65
    float lo = 0.0f, hi = 1.0f, u = x;
    for (int i = 0; 12 > i; i++) {
      u = 0.5f * (lo + hi);
      float inv = 1.0f - u;
      float bx = 3.0f * inv * inv * u * 0.35f + 3.0f * inv * u * u * 0.65f + u * u * u;
      if (x > bx) lo = u;
      else hi = u;
    }
    // Evaluate By(u) with y1=0.0, y2=1.0 -> 3*(1-u)*u^2 + u^3
    float inv = 1.0f - u;
    return 3.0f * inv * u * u + u * u * u;
  }

  public static float getSweepProgress() {
    if (!isHoldingParry) return lockedCursorProgress;
    float linearT = Math.min(1.0f, Math.max(0.0f, (System.currentTimeMillis() - parryChargeStartMs) / SWEEP_DURATION_MS));
    return evalCubicBezier(linearT);
  }

  public static void onParryKeyPressed() {
    if (activeTier == DifficultyTier.NORMAL || selectorOpen) return;
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null) return;

    if (isInParryFlow()) {
      if (flowTapPending) return;
      isHoldingParry = false;
      flowTapPending = true;
      flowWindowEndsAtMs = System.currentTimeMillis() + 120L;
      lockedCursorProgress = 0.5f;
      lastResultLabel = "FLOW PARRY";
      lastResultColor = 0xFF21B7D1;
      resultDisplayUntilMs = System.currentTimeMillis() + 300L;
      playParrySound("white");
      PacketDistributor.sendToServer(new DifficultyPackets.ActionPayload("PARRY_WINDOW", "FLOW", 120));
      return;
    }

    if (!isHoldingParry) {
      isHoldingParry = true;
      parryChargeStartMs = System.currentTimeMillis();
      lockedCursorProgress = -1.0f;
    }
  }

  public static void onParryKeyReleased() {
    if (!isHoldingParry) return;
    float p = getSweepProgress();
    isHoldingParry = false;
    lockedCursorProgress = p;
    evaluateRelease(p);
  }

  public static void playParrySound(String zoneKey) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null) return;
    ParrySoundEntry entry = PARRY_SOUNDS.getOrDefault(zoneKey, PARRY_SOUNDS.get("red"));
    net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation.tryParse(entry.soundId());
    if (loc != null) {
      net.minecraft.sounds.SoundEvent se = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(loc)
      ? net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(loc)
      : net.minecraft.sounds.SoundEvent.createVariableRangeEvent(loc);
      mc.player.playSound(se, entry.volume(), entry.pitch());
    }
  }

  public static void tickParryAutoFail() {
    long now = System.currentTimeMillis();
    if (isHoldingParry && (now - parryChargeStartMs) >= (long) SWEEP_DURATION_MS) {
      isHoldingParry = false;
      lockedCursorProgress = 1.0f;
      evaluateRelease(1.0f);
    }
    if (flowTapPending && now > flowWindowEndsAtMs) {
      flowTapPending = false;
      parryFlowEndsAtMs = 0L;
      playParrySound("red");
      PacketDistributor.sendToServer(new DifficultyPackets.ActionPayload("BREAK_FLOW", "", 0));
    }
  }

  private static void evaluateRelease(float p) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null) return;

    String zone;
    int windowMs;
    if (p >= 0.45f && 0.55f >= p) {
      zone = "PURE";
      windowMs = 150;
      lastResultLabel = "PURE PARRY";
      lastResultColor = 0xFFFFFFFF;
      playParrySound("white");
    } else if ((p >= 0.30f && 0.45f > p) || (p > 0.55f && 0.70f >= p)) {
      zone = "PARRY";
      windowMs = 200;
      lastResultLabel = "PARRY";
      lastResultColor = 0xFF4ADE80;
      playParrySound("green");
    } else if ((p >= 0.10f && 0.30f > p) || (p > 0.70f && 0.90f >= p)) {
      zone = "SHALLOW";
      windowMs = 200;
      lastResultLabel = "SHALLOW";
      lastResultColor = 0xFFFB923C;
      playParrySound("orange");
    } else {
      zone = "FAIL";
      windowMs = 0;
      lastResultLabel = "FAILED";
      lastResultColor = 0xFFEF4444;
      playParrySound("red");
    }

    resultDisplayUntilMs = System.currentTimeMillis() + 450L;
    PacketDistributor.sendToServer(new DifficultyPackets.ActionPayload("PARRY_WINDOW", zone, windowMs));
  }
}
