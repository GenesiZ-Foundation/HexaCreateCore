/*
 * THIS PART OF THE CODE IS MEANT FOR DEVELOPERS
 * */
package com.genesiz.hexacreatecore.threedified;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/* FULL BREAKDOWN:
 *
 * -> In-game /threedified client command tuner for live item/entity transforms, world markers, and text tuning
 *
 * [.isShowLocalGizmos()] is self explanatory
 * [.getActiveSlot()] is self explanatory
 * [.getOrRegister()] registers or retrieves a named TransformEntry slot so live /threedified tune edits apply immediately
 * [.applyItemSixteenths()] applies a slot's transform in 1/16th block units with left/right hand mirroring
 * [.applyEntityBlocks()] applies a slot's transform directly in block units
 * [.drawLocalAxes()] draws RGB XYZ gizmo lines at the current PoseStack origin
 * [.onRenderWorldMarkers()] is self explanatory
 * [.onRegisterClientCommands()] registers /threedified (tune, tune text, marker, clear)
 *
 * */
@EventBusSubscriber(modid = "hexacreatecore", value = Dist.CLIENT)
public final class ThreedifiedTransformTuner {

  public static final class TransformEntry {
    public  float  tx;
    public  float  ty;
    public  float  tz;
    public  float  rx;
    public  float  ry;
    public  float  rz;
    public  float  scale;

    public TransformEntry(float tx, float ty, float tz, float rx, float ry, float rz, float scale) {
      this.tx = tx;
      this.ty = ty;
      this.tz = tz;
      this.rx = rx;
      this.ry = ry;
      this.rz = rz;
      this.scale = scale;
    }

    public TransformEntry copy() {
      return new TransformEntry(tx, ty, tz, rx, ry, rz, scale);
    }
  }

  public record WorldMarker(Vec3 pos, String label) {}

  private static final  Map<String, TransformEntry>               SLOTS = new ConcurrentHashMap<>();
  private static final  Map<String, TransformEntry>               DEFAULTS = new ConcurrentHashMap<>();
  private static final  List<WorldMarker>                         WORLD_MARKERS = new ArrayList<>();

  private static        String                                    activeSlot = "";
  private static        boolean                                   showLocalGizmos = false;

  private static final  SuggestionProvider<CommandSourceStack>    SLOT_SUGGESTIONS =
    (ctx, builder) -> SharedSuggestionProvider.suggest(SLOTS.keySet(), builder);

  private ThreedifiedTransformTuner() {}

  public static boolean isShowLocalGizmos() {
    return showLocalGizmos;
  }

  public static String getActiveSlot() {
    return activeSlot;
  }

  public static TransformEntry getOrRegister(
    String slotName,
    float defaultTx, float defaultTy, float defaultTz,
    float defaultRx, float defaultRy, float defaultRz,
    float defaultScale
  ) {
    DEFAULTS.computeIfAbsent(slotName, k -> new TransformEntry(defaultTx, defaultTy, defaultTz, defaultRx, defaultRy, defaultRz, defaultScale));
    return SLOTS.computeIfAbsent(slotName, k -> new TransformEntry(defaultTx, defaultTy, defaultTz, defaultRx, defaultRy, defaultRz, defaultScale));
  }

  public static void applyItemSixteenths(
    String slotName,
    PoseStack poseStack,
    MultiBufferSource bufferSource,
    float sideSign,
    float defaultTx, float defaultTy, float defaultTz,
    float defaultRx, float defaultRy, float defaultRz
  ) {
    TransformEntry e = getOrRegister(slotName, defaultTx, defaultTy, defaultTz, defaultRx, defaultRy, defaultRz, 1.0f);
    poseStack.translate((e.tx * sideSign) / 16.0f, e.ty / 16.0f, e.tz / 16.0f);
    poseStack.mulPose(Axis.XP.rotationDegrees(e.rx));
    poseStack.mulPose(Axis.YP.rotationDegrees(e.ry * sideSign));
    poseStack.mulPose(Axis.ZP.rotationDegrees(e.rz * sideSign));
    if (Math.abs(e.scale - 1.0f) > 1.0e-4f) {
      poseStack.scale(e.scale, e.scale, e.scale);
    }
    if (showLocalGizmos && slotName.equals(activeSlot) && bufferSource != null) {
      drawLocalAxes(poseStack, bufferSource, 0.32f);
    }
  }

  public static void applyEntityBlocks(
    String slotName,
    PoseStack poseStack,
    MultiBufferSource bufferSource,
    float defaultTx, float defaultTy, float defaultTz,
    float defaultRx, float defaultRy, float defaultRz
  ) {
    TransformEntry e = getOrRegister(slotName, defaultTx, defaultTy, defaultTz, defaultRx, defaultRy, defaultRz, 1.0f);
    poseStack.translate(e.tx, e.ty, e.tz);
    poseStack.mulPose(Axis.XP.rotationDegrees(e.rx));
    poseStack.mulPose(Axis.YP.rotationDegrees(e.ry));
    poseStack.mulPose(Axis.ZP.rotationDegrees(e.rz));
    if (Math.abs(e.scale - 1.0f) > 1.0e-4f) {
      poseStack.scale(e.scale, e.scale, e.scale);
    }
    if (showLocalGizmos && slotName.equals(activeSlot) && bufferSource != null) {
      drawLocalAxes(poseStack, bufferSource, 0.85f);
    }
  }

  public static void drawLocalAxes(PoseStack poseStack, MultiBufferSource bufferSource, float length) {
    VertexConsumer vc = bufferSource.getBuffer(RenderType.lines());
    Matrix4f m = poseStack.last().pose();
    vc.addVertex(m, 0.0f, 0.0f, 0.0f).setColor(255, 50, 50, 255).setNormal(1.0f, 0.0f, 0.0f);
    vc.addVertex(m, length, 0.0f, 0.0f).setColor(255, 50, 50, 255).setNormal(1.0f, 0.0f, 0.0f);
    vc.addVertex(m, 0.0f, 0.0f, 0.0f).setColor(50, 255, 80, 255).setNormal(0.0f, 1.0f, 0.0f);
    vc.addVertex(m, 0.0f, length, 0.0f).setColor(50, 255, 80, 255).setNormal(0.0f, 1.0f, 0.0f);
    vc.addVertex(m, 0.0f, 0.0f, 0.0f).setColor(60, 140, 255, 255).setNormal(0.0f, 0.0f, 1.0f);
    vc.addVertex(m, 0.0f, 0.0f, length).setColor(60, 140, 255, 255).setNormal(0.0f, 0.0f, 1.0f);
  }

  @SubscribeEvent
  public static void onRenderWorldMarkers(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    if (WORLD_MARKERS.isEmpty()) return;

    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;

    Vec3 cam = mc.getEntityRenderDispatcher().camera.getPosition();
    PoseStack ps = event.getPoseStack();
    MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

    RenderSystem.disableDepthTest();
    for (WorldMarker marker : WORLD_MARKERS) {
      ps.pushPose();
      ps.translate(marker.pos().x - cam.x, marker.pos().y - cam.y, marker.pos().z - cam.z);
      drawLocalAxes(ps, buf, 0.75f);
      RawGeometry.drawArcaneNodePrism(ps, buf, 0.0f, 0.0f, 0.0f, 0.07f, 0xFFFFD700);
      if (!marker.label().isEmpty()) {
        ps.pushPose();
        ps.translate(0.0f, 0.22f, 0.0f);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-0.014f, -0.014f, 0.014f);
        mc.font.drawInBatch(
          Component.literal("§e" + marker.label()),
          -mc.font.width(marker.label()) * 0.5f, 0.0f,
          0xFFFFFFFF, false, ps.last().pose(), buf,
          Font.DisplayMode.SEE_THROUGH, 0, 15728880
        );
        ps.popPose();
      }
      ps.popPose();
    }
    buf.endBatch(RenderType.lines());
    buf.endBatch();
    RenderSystem.enableDepthTest();
  }

  @SubscribeEvent
  public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
    event.getDispatcher().register(
      Commands.literal("threedified")
        .then(Commands.literal("tune")
          .then(ThreedifiedTextTuner.buildCommand())
          .then(Commands.literal("select")
            .then(Commands.argument("slot", StringArgumentType.word())
              .suggests(SLOT_SUGGESTIONS)
              .executes(ctx -> {
                activeSlot = StringArgumentType.getString(ctx, "slot");
                SLOTS.computeIfAbsent(activeSlot, k -> new TransformEntry(0, 0, 0, 0, 0, 0, 1));
                ctx.getSource().sendSuccess(() -> Component.literal("§d[Threedified] §fActive slot: §e" + activeSlot + " §8(Gizmos: Red=+X, Green=+Y, Blue=+Z)"), false);
                return 1;
              })))
          .then(Commands.literal("list")
            .executes(ctx -> {
              ctx.getSource().sendSuccess(() -> Component.literal("§d[Threedified] §fRegistered slots: §b" + String.join(", ", SLOTS.keySet())), false);
              return 1;
            }))
          .then(Commands.literal("gizmo")
            .executes(ctx -> {
              showLocalGizmos = !showLocalGizmos;
              ctx.getSource().sendSuccess(() -> Component.literal("§d[Threedified] §fLocal XYZ gizmos: " + (showLocalGizmos ? "§aON" : "§cOFF")), false);
              return 1;
            }))
          .then(Commands.literal("pos")
            .then(Commands.argument("x", FloatArgumentType.floatArg())
              .then(Commands.argument("y", FloatArgumentType.floatArg())
                .then(Commands.argument("z", FloatArgumentType.floatArg())
                  .executes(ctx -> {
                    if (activeSlot.isEmpty()) {
                      ctx.getSource().sendSuccess(() -> Component.literal("§c[Threedified] Select a slot first with /threedified tune select <slot>"), false);
                      return 0;
                    }
                    TransformEntry e = SLOTS.computeIfAbsent(activeSlot, k -> new TransformEntry(0, 0, 0, 0, 0, 0, 1));
                    e.tx = FloatArgumentType.getFloat(ctx, "x");
                    e.ty = FloatArgumentType.getFloat(ctx, "y");
                    e.tz = FloatArgumentType.getFloat(ctx, "z");
                    sendStatus(ctx.getSource(), e);
                    return 1;
                  })))))
          .then(Commands.literal("addpos")
            .then(Commands.argument("dx", FloatArgumentType.floatArg())
              .then(Commands.argument("dy", FloatArgumentType.floatArg())
                .then(Commands.argument("dz", FloatArgumentType.floatArg())
                  .executes(ctx -> {
                    if (activeSlot.isEmpty()) {
                      ctx.getSource().sendSuccess(() -> Component.literal("§c[Threedified] Select a slot first with /threedified tune select <slot>"), false);
                      return 0;
                    }
                    TransformEntry e = SLOTS.computeIfAbsent(activeSlot, k -> new TransformEntry(0, 0, 0, 0, 0, 0, 1));
                    e.tx += FloatArgumentType.getFloat(ctx, "dx");
                    e.ty += FloatArgumentType.getFloat(ctx, "dy");
                    e.tz += FloatArgumentType.getFloat(ctx, "dz");
                    sendStatus(ctx.getSource(), e);
                    return 1;
                  })))))
          .then(Commands.literal("rot")
            .then(Commands.argument("rx", FloatArgumentType.floatArg())
              .then(Commands.argument("ry", FloatArgumentType.floatArg())
                .then(Commands.argument("rz", FloatArgumentType.floatArg())
                  .executes(ctx -> {
                    if (activeSlot.isEmpty()) {
                      ctx.getSource().sendSuccess(() -> Component.literal("§c[Threedified] Select a slot first with /threedified tune select <slot>"), false);
                      return 0;
                    }
                    TransformEntry e = SLOTS.computeIfAbsent(activeSlot, k -> new TransformEntry(0, 0, 0, 0, 0, 0, 1));
                    e.rx = FloatArgumentType.getFloat(ctx, "rx");
                    e.ry = FloatArgumentType.getFloat(ctx, "ry");
                    e.rz = FloatArgumentType.getFloat(ctx, "rz");
                    sendStatus(ctx.getSource(), e);
                    return 1;
                  })))))
          .then(Commands.literal("addrot")
            .then(Commands.argument("drx", FloatArgumentType.floatArg())
              .then(Commands.argument("dry", FloatArgumentType.floatArg())
                .then(Commands.argument("drz", FloatArgumentType.floatArg())
                  .executes(ctx -> {
                    if (activeSlot.isEmpty()) {
                      ctx.getSource().sendSuccess(() -> Component.literal("§c[Threedified] Select a slot first with /threedified tune select <slot>"), false);
                      return 0;
                    }
                    TransformEntry e = SLOTS.computeIfAbsent(activeSlot, k -> new TransformEntry(0, 0, 0, 0, 0, 0, 1));
                    e.rx += FloatArgumentType.getFloat(ctx, "drx");
                    e.ry += FloatArgumentType.getFloat(ctx, "dry");
                    e.rz += FloatArgumentType.getFloat(ctx, "drz");
                    sendStatus(ctx.getSource(), e);
                    return 1;
                  })))))
          .then(Commands.literal("scale")
            .then(Commands.argument("s", FloatArgumentType.floatArg(0.01f, 20.0f))
              .executes(ctx -> {
                if (activeSlot.isEmpty()) {
                  ctx.getSource().sendSuccess(() -> Component.literal("§c[Threedified] Select a slot first with /threedified tune select <slot>"), false);
                  return 0;
                }
                TransformEntry e = SLOTS.computeIfAbsent(activeSlot, k -> new TransformEntry(0, 0, 0, 0, 0, 0, 1));
                e.scale = FloatArgumentType.getFloat(ctx, "s");
                sendStatus(ctx.getSource(), e);
                return 1;
              })))
          .then(Commands.literal("reset")
            .executes(ctx -> {
              TransformEntry def = DEFAULTS.get(activeSlot);
              if (def != null) {
                SLOTS.put(activeSlot, def.copy());
                sendStatus(ctx.getSource(), SLOTS.get(activeSlot));
              }
              return 1;
            }))
          .then(Commands.literal("dump")
            .executes(ctx -> {
              TransformEntry e = SLOTS.get(activeSlot);
              if (e != null) {
                String code = String.format(
                  Locale.ROOT,
                  "[%s] pos=(%.2ff, %.2ff, %.2ff) rot=(%.1ff, %.1ff, %.1ff) scale=%.2ff",
                  activeSlot, e.tx, e.ty, e.tz, e.rx, e.ry, e.rz, e.scale
                );
                com.genesiz.hexacreatecore.HexaCreateCore.LOGGER.info("ThreedifiedTuneDump: {}", code);
                ctx.getSource().sendSuccess(() -> Component.literal("§a" + code), false);
              }
              return 1;
            }))
          .then(Commands.literal("dumpall")
            .executes(ctx -> {
              if (SLOTS.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal("§d[Threedified] §7No slots registered yet."), false);
                return 1;
              }
              for (Map.Entry<String, TransformEntry> entry : SLOTS.entrySet()) {
                TransformEntry e = entry.getValue();
                String code = String.format(
                  Locale.ROOT,
                  "[%s] pos=(%.2ff, %.2ff, %.2ff) rot=(%.1ff, %.1ff, %.1ff) scale=%.2ff",
                  entry.getKey(), e.tx, e.ty, e.tz, e.rx, e.ry, e.rz, e.scale
                );
                com.genesiz.hexacreatecore.HexaCreateCore.LOGGER.info("ThreedifiedTuneDump: {}", code);
                ctx.getSource().sendSuccess(() -> Component.literal("§a" + code), false);
              }
              return 1;
            })))
        .then(Commands.literal("marker")
          .then(Commands.literal("here")
            .executes(ctx -> {
              var p = Minecraft.getInstance().player;
              if (p != null) {
                Vec3 pos = p.position();
                String lbl = String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", pos.x, pos.y, pos.z);
                WORLD_MARKERS.add(new WorldMarker(pos, lbl));
                ctx.getSource().sendSuccess(() -> Component.literal("§d[Threedified] §fAdded world marker at §e" + lbl), false);
              }
              return 1;
            })
            .then(Commands.argument("label", StringArgumentType.greedyString())
              .executes(ctx -> {
                var p = Minecraft.getInstance().player;
                if (p != null) {
                  Vec3 pos = p.position();
                  String lbl = StringArgumentType.getString(ctx, "label");
                  WORLD_MARKERS.add(new WorldMarker(pos, lbl));
                  ctx.getSource().sendSuccess(() -> Component.literal("§d[Threedified] §fAdded marker §e" + lbl), false);
                }
                return 1;
              })))
          .then(Commands.literal("world")
            .then(Commands.argument("x", FloatArgumentType.floatArg())
              .then(Commands.argument("y", FloatArgumentType.floatArg())
                .then(Commands.argument("z", FloatArgumentType.floatArg())
                  .executes(ctx -> {
                    Vec3 pos = new Vec3(
                      FloatArgumentType.getFloat(ctx, "x"),
                      FloatArgumentType.getFloat(ctx, "y"),
                      FloatArgumentType.getFloat(ctx, "z")
                    );
                  String lbl = String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", pos.x, pos.y, pos.z);
                  WORLD_MARKERS.add(new WorldMarker(pos, lbl));
                  ctx.getSource().sendSuccess(() -> Component.literal("§d[Threedified] §fAdded marker at §e" + lbl), false);
                  return 1;
                })))))
        .then(Commands.literal("clear")
          .executes(ctx -> {
            WORLD_MARKERS.clear();
            ctx.getSource().sendSuccess(() -> Component.literal("§d[Threedified] §fCleared all world markers."), false);
            return 1;
          })))
    );
  }
  private static void sendStatus(CommandSourceStack src, TransformEntry e) {
    String msg = String.format(
      Locale.ROOT,
      "§d[%s] §fpos=(§b%.2f, %.2f, %.2f§f) rot=(§e%.1f°, %.1f°, %.1f°§f) scale=§a%.2f",
      activeSlot, e.tx, e.ty, e.tz, e.rx, e.ry, e.rz, e.scale
    );
    src.sendSuccess(() -> Component.literal(msg), false);
  }
}
