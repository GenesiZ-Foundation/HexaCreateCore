package com.genesiz.hexacreatecore.hexcasting.everbook;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class EverbookSandboxEvaluator {

  public record EvalResult(
    boolean success,
    boolean collapsedRift,
    int recursionCount,
    String summaryText,
    List<String> stackPreview
  ) {}

  // Patterns that mutate the physical world (intercepted in dry-run to return True/False instead of grieving/executing)
  private static final Set<String> WORLD_EVENT_SIGNATURES = Set.of(
    "qaqqqqq",   // Break Block
    "eeeeedw",   // Place Block
    "waaw",      // Ignite / Fire
    "waadw",     // Summon Lightning
    "aawaawaa",  // Explosion
    "awddw",     // Impulse
    "edqde",     // Create Water / Lava
    "wqaqw"      // Conjure Block / Light
  );

  private static final Set<String> RECURSIVE_SIGNATURES = Set.of(
    "deaqq",     // Hermes' Gambit
    "dadad",     // Thoth's Gambit
    "qwaeawqaeaqa" // Iris's Gambit
  );

  private EverbookSandboxEvaluator() {}

  public static EvalResult evaluateNonDestructive(ServerPlayer player, List<EverbookData.PatternEntry> entries) {
    if (entries == null || entries.isEmpty()) {
      return new EvalResult(false, false, 0, "Empty Rift", List.of());
    }

    int maxRecursions = 256;

    // 1. Pre-scan recursion depth & world-mutating patterns
    int estimatedRecursions = 0;
    boolean containsWorldEvent = false;
    List<Iota> iotas = new ArrayList<>();

    for (EverbookData.PatternEntry entry : entries) {
      String sig = entry.angles();
      if (RECURSIVE_SIGNATURES.contains(sig)) {
        estimatedRecursions++;
      }
      if (WORLD_EVENT_SIGNATURES.contains(sig)) {
        containsWorldEvent = true;
      }
      HexDir dir = parseDir(entry.startDir());
      HexPattern pat = buildPatternFromAngles(dir, sig);
      iotas.add(new PatternIota(pat));
    }

    // 2. If world-mutating actions are present, validate safely without mutating the world -> True / False
    if (containsWorldEvent) {
      if (estimatedRecursions > maxRecursions) {
        return new EvalResult(false, true, estimatedRecursions, "False (Too many recursions, collapsed rift)", List.of("False"));
      }
      boolean validArgs = entries.size() >= 2;
      return new EvalResult(
        validArgs,
        false,
        estimatedRecursions,
        validArgs ? "True (World Event Simulated)" : "False (Missing Target/Args)",
        List.of(validArgs ? "True" : "False")
      );
    }

    // 3. Pure stack evaluation via HexCasting's VM on a fresh isolated CastingImage (never consumes Everbook entries!)
    try {
      CastingEnvironment env = new EverbookDryRunEnv(player);
      CastingImage image = new CastingImage();
      CastingVM vm = new CastingVM(image, env);

      var outcome = vm.queueExecuteAndWrapIotas(iotas, player.serverLevel());
      int actualRecursions = Math.max(estimatedRecursions, countFrames(vm.getImage()));

      if (actualRecursions > maxRecursions || outcome.getResolutionType().name().contains("ERROR")) {
        boolean collapsed = actualRecursions > maxRecursions;
        String msg = collapsed ? "False (Too many recursions, collapsed rift)" : "False (Mishap)";
        return new EvalResult(false, collapsed, actualRecursions, msg, List.of("False"));
      }

      List<String> preview = new ArrayList<>();
      for (Iota iota : vm.getImage().getStack()) {
        preview.add(iota.display().getString());
      }
      String topStr = preview.isEmpty() ? "True" : preview.get(preview.size() - 1);
      return new EvalResult(true, false, actualRecursions, topStr, preview);
    } catch (Throwable t) {
      if (estimatedRecursions > maxRecursions) {
        return new EvalResult(false, true, estimatedRecursions, "False (Too many recursions, collapsed rift)", List.of("False"));
      }
      return new EvalResult(false, false, estimatedRecursions, "False", List.of("False"));
    }
  }

  private static int countFrames(CastingImage img) {
    try {
      return img.getParenCount();
    } catch (Throwable ignored) {
      return 0;
    }
  }

  private static HexDir parseDir(String name) {
    try {
      return HexDir.valueOf(name);
    } catch (Exception e) {
      return HexDir.EAST;
    }
  }

  private static HexPattern buildPatternFromAngles(HexDir startDir, String angles) {
    try {
      Class<?> signatureClass = Class.forName("at.petrak.hexcasting.api.casting.math.HexSignature");
      var factoryMethod = signatureClass.getMethod("fromAnglesStringUnchecked", String.class);
      Object hexSignature = factoryMethod.invoke(null, angles);
      var patternCtor = HexPattern.class.getConstructor(HexDir.class, signatureClass);
      patternCtor.setAccessible(true);
      return patternCtor.newInstance(startDir, hexSignature);
    } catch (Throwable t) {
      throw new RuntimeException("Failed to build HexPattern for angles: " + angles, t);
    }
  }
}
