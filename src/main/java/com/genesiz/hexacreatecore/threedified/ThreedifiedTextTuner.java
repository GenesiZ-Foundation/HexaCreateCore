package com.genesiz.hexacreatecore.threedified;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.math.Axis;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public final class ThreedifiedTextTuner {

  public static final float ITEM_PIXEL_UNIT = 1.0f / 16.0f;

  @FunctionalInterface
  public interface TextDrawer {
    void draw(PoseStack poseStack, MultiBufferSource bufferSource, Font font, int packedLight);
  }

  public static final class TextTarget {
    public float tx;
    public float ty;
    public float tz;
    public float rx;
    public float ry;
    public float rz;
    public float scale;
    public final float unitScale;

    public TextTarget(
      float tx, float ty, float tz,
      float rx, float ry, float rz,
      float scale, float unitScale
    ) {
      this.tx = tx;
      this.ty = ty;
      this.tz = tz;
      this.rx = rx;
      this.ry = ry;
      this.rz = rz;
      this.scale = scale;
      this.unitScale = unitScale;
    }

    public TextTarget copy() {
      return new TextTarget(tx, ty, tz, rx, ry, rz, scale, unitScale);
    }
  }

  private static final Map<String, TextTarget> TARGETS = new LinkedHashMap<>();
  private static final Map<String, TextTarget> DEFAULTS = new LinkedHashMap<>();

  private static String activeTarget = "";
  private static boolean showGizmos = false;

  private static final SuggestionProvider<CommandSourceStack> TARGET_SUGGESTIONS =
    (ctx, builder) -> SharedSuggestionProvider.suggest(TARGETS.keySet(), builder);

  private ThreedifiedTextTuner() {}

  /**
   * Registers or retrieves a named text target using item/bone pixel units (1/16 block).
   */
  public static TextTarget getOrRegister(
    String name,
    float tx, float ty, float tz,
    float rx, float ry, float rz,
    float scale
  ) {
    return getOrRegister(name, tx, ty, tz, rx, ry, rz, scale, ITEM_PIXEL_UNIT);
  }

  /**
   * Registers or retrieves a named text target with a custom translation unit multiplier
   * (e.g. ITEM_PIXEL_UNIT for items/bones, 1.0f for entities/world).
   */
  public static TextTarget getOrRegister(
    String name,
    float tx, float ty, float tz,
    float rx, float ry, float rz,
    float scale,
    float unitScale
  ) {
    DEFAULTS.computeIfAbsent(name, k -> new TextTarget(tx, ty, tz, rx, ry, rz, scale, unitScale));
    TextTarget target = TARGETS.computeIfAbsent(name, k -> new TextTarget(tx, ty, tz, rx, ry, rz, scale, unitScale));
    if (activeTarget.isEmpty()) {
      activeTarget = name;
    }
    return target;
  }

  /**
   * Hook entry point: ensures target is registered, applies its PoseStack transform,
   * optionally renders local XYZ axes, and invokes the caller's text drawer.
   */
  public static void draw(
    String name,
    PoseStack poseStack,
    MultiBufferSource bufferSource,
    Font font,
    int packedLight,
    float defaultTx, float defaultTy, float defaultTz,
    float defaultRx, float defaultRy, float defaultRz,
    float defaultScale,
    TextDrawer drawer
  ) {
    draw(
      name, poseStack, bufferSource, font, packedLight,
      defaultTx, defaultTy, defaultTz,
      defaultRx, defaultRy, defaultRz,
      defaultScale, ITEM_PIXEL_UNIT, drawer
    );
  }

  public static void draw(
    String name,
    PoseStack poseStack,
    MultiBufferSource bufferSource,
    Font font,
    int packedLight,
    float defaultTx, float defaultTy, float defaultTz,
    float defaultRx, float defaultRy, float defaultRz,
    float defaultScale,
    float unitScale,
    TextDrawer drawer
  ) {
    TextTarget t = getOrRegister(
      name,
      defaultTx, defaultTy, defaultTz,
      defaultRx, defaultRy, defaultRz,
      defaultScale, unitScale
    );

    poseStack.pushPose();
    poseStack.translate(t.tx * t.unitScale, t.ty * t.unitScale, t.tz * t.unitScale);
    if (Math.abs(t.rx) > 1.0e-4f) poseStack.mulPose(Axis.XP.rotationDegrees(t.rx));
    if (Math.abs(t.ry) > 1.0e-4f) poseStack.mulPose(Axis.YP.rotationDegrees(t.ry));
    if (Math.abs(t.rz) > 1.0e-4f) poseStack.mulPose(Axis.ZP.rotationDegrees(t.rz));

    if (showGizmos || ThreedifiedTransformTuner.isShowLocalGizmos()) {
      ThreedifiedTransformTuner.drawLocalAxes(poseStack, bufferSource, 0.15f);
    }

    if (Math.abs(t.scale - 1.0f) > 1.0e-4f) {
      poseStack.scale(t.scale, t.scale, t.scale);
    }

    drawer.draw(poseStack, bufferSource, font, packedLight);
    poseStack.popPose();
  }

  public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
    return Commands.literal("text")
      .then(Commands.literal("select")
        .then(Commands.argument("target", StringArgumentType.word())
          .suggests(TARGET_SUGGESTIONS)
          .executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "target");
            if (!TARGETS.containsKey(name)) {
              ctx.getSource().sendSuccess(() -> Component.literal("§c[Threedified Text] Unknown target: " + name), false);
              return 0;
            }
            activeTarget = name;
            sendStatus(ctx.getSource(), name, TARGETS.get(name));
            return 1;
          })))
      .then(Commands.literal("list")
        .executes(ctx -> {
          if (TARGETS.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§d[Threedified Text] §7No text targets registered yet."), false);
            return 1;
          }
          StringBuilder sb = new StringBuilder();
          for (String key : TARGETS.keySet()) {
            if (!sb.isEmpty()) sb.append("§7, ");
            sb.append(key.equals(activeTarget) ? "§a*" + key : "§b" + key);
          }
          ctx.getSource().sendSuccess(() -> Component.literal("§d[Threedified Text] §fTargets: " + sb), false);
          return 1;
        }))
      .then(Commands.literal("status")
        .executes(ctx -> {
          TextTarget t = requireActive(ctx.getSource());
          if (t == null) return 0;
          sendStatus(ctx.getSource(), activeTarget, t);
          return 1;
        }))
      .then(Commands.literal("gizmo")
        .executes(ctx -> {
          showGizmos = !showGizmos;
          ctx.getSource().sendSuccess(() -> Component.literal("§d[Threedified Text] §fText XYZ gizmos: " + (showGizmos ? "§aON" : "§cOFF")), false);
          return 1;
        }))
      .then(Commands.literal("pos")
        .then(Commands.argument("x", FloatArgumentType.floatArg())
          .then(Commands.argument("y", FloatArgumentType.floatArg())
            .then(Commands.argument("z", FloatArgumentType.floatArg())
              .executes(ctx -> {
                TextTarget t = requireActive(ctx.getSource());
                if (t == null) return 0;
                t.tx = FloatArgumentType.getFloat(ctx, "x");
                t.ty = FloatArgumentType.getFloat(ctx, "y");
                t.tz = FloatArgumentType.getFloat(ctx, "z");
                sendStatus(ctx.getSource(), activeTarget, t);
                return 1;
              })))))
      .then(Commands.literal("addpos")
        .then(Commands.argument("dx", FloatArgumentType.floatArg())
          .then(Commands.argument("dy", FloatArgumentType.floatArg())
            .then(Commands.argument("dz", FloatArgumentType.floatArg())
              .executes(ctx -> {
                TextTarget t = requireActive(ctx.getSource());
                if (t == null) return 0;
                t.tx += FloatArgumentType.getFloat(ctx, "dx");
                t.ty += FloatArgumentType.getFloat(ctx, "dy");
                t.tz += FloatArgumentType.getFloat(ctx, "dz");
                sendStatus(ctx.getSource(), activeTarget, t);
                return 1;
              })))))
      .then(Commands.literal("rot")
        .then(Commands.argument("rx", FloatArgumentType.floatArg())
          .then(Commands.argument("ry", FloatArgumentType.floatArg())
            .then(Commands.argument("rz", FloatArgumentType.floatArg())
              .executes(ctx -> {
                TextTarget t = requireActive(ctx.getSource());
                if (t == null) return 0;
                t.rx = FloatArgumentType.getFloat(ctx, "rx");
                t.ry = FloatArgumentType.getFloat(ctx, "ry");
                t.rz = FloatArgumentType.getFloat(ctx, "rz");
                sendStatus(ctx.getSource(), activeTarget, t);
                return 1;
              })))))
      .then(Commands.literal("addrot")
        .then(Commands.argument("drx", FloatArgumentType.floatArg())
          .then(Commands.argument("dry", FloatArgumentType.floatArg())
            .then(Commands.argument("drz", FloatArgumentType.floatArg())
              .executes(ctx -> {
                TextTarget t = requireActive(ctx.getSource());
                if (t == null) return 0;
                t.rx += FloatArgumentType.getFloat(ctx, "drx");
                t.ry += FloatArgumentType.getFloat(ctx, "dry");
                t.rz += FloatArgumentType.getFloat(ctx, "drz");
                sendStatus(ctx.getSource(), activeTarget, t);
                return 1;
              })))))
      .then(Commands.literal("scale")
        .then(Commands.argument("s", FloatArgumentType.floatArg(0.0001f, 50.0f))
          .executes(ctx -> {
            TextTarget t = requireActive(ctx.getSource());
            if (t == null) return 0;
            t.scale = FloatArgumentType.getFloat(ctx, "s");
            sendStatus(ctx.getSource(), activeTarget, t);
            return 1;
          })))
      .then(Commands.literal("reset")
        .executes(ctx -> {
          TextTarget def = DEFAULTS.get(activeTarget);
          if (def == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("§c[Threedified Text] No active target to reset."), false);
            return 0;
          }
          TARGETS.put(activeTarget, def.copy());
          sendStatus(ctx.getSource(), activeTarget, TARGETS.get(activeTarget));
          return 1;
        }))
      .then(Commands.literal("dump")
        .executes(ctx -> {
          TextTarget t = requireActive(ctx.getSource());
          if (t == null) return 0;
          sendDump(ctx.getSource(), activeTarget, t);
          return 1;
        }))
      .then(Commands.literal("dumpall")
        .executes(ctx -> {
          if (TARGETS.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§d[Threedified Text] §7No text targets registered yet."), false);
            return 1;
          }
          for (Map.Entry<String, TextTarget> entry : TARGETS.entrySet()) {
            sendDump(ctx.getSource(), entry.getKey(), entry.getValue());
          }
          return 1;
        }));
  }

  private static TextTarget requireActive(CommandSourceStack src) {
    TextTarget t = TARGETS.get(activeTarget);
    if (t == null) {
      src.sendSuccess(() -> Component.literal("§c[Threedified Text] Select a target first with /threedified tune text select <target>"), false);
    }
    return t;
  }

  private static void sendStatus(CommandSourceStack src, String name, TextTarget t) {
    String msg = String.format(
      Locale.ROOT,
      "§d[text:%s] §fpos=(§b%.3f, %.3f, %.3f§f) rot=(§e%.1f°, %.1f°, %.1f°§f) scale=§a%.5f",
      name, t.tx, t.ty, t.tz, t.rx, t.ry, t.rz, t.scale
    );
    src.sendSuccess(() -> Component.literal(msg), false);
  }

  private static void sendDump(CommandSourceStack src, String name, TextTarget t) {
    String line = String.format(
      Locale.ROOT,
      "\"%s\", %.3ff, %.3ff, %.3ff, %.1ff, %.1ff, %.1ff, %.5ff",
      name, t.tx, t.ty, t.tz, t.rx, t.ry, t.rz, t.scale
    );
    HexaCreateCore.LOGGER.info("[ThreedifiedTextDump] {}", line);
    src.sendSuccess(() -> Component.literal("§a" + line), false);
  }
}
