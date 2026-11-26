package com.genesiz.hexacreatecore.hexcasting.everbook;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.item.ModItems;
import com.genesiz.hexacreatecore.item.custom.EverbookItem;
import com.genesiz.hexacreatecore.threedified.ThreedifiedTextTuner;
import com.genesiz.hexacreatecore.threedified.ThreedifiedTransformTuner;
import com.genesiz.hexacreatecore.threedified.text.TextEffectStyle;
import com.genesiz.hexacreatecore.threedified.text.ThreeDifiedTextRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.List;
import java.util.UUID;

public class EverbookItemRenderer extends GeoItemRenderer<EverbookItem> {

  private static final long HELD_INSTANCE_ID = 0x45564552424F4F4BL;
  private static final long GUI_INSTANCE_ID = 0x1122334455667788L;

  private static final String[] RUNE_LINES = {
    "Entropy",
    "AetherIota",
    "SoulCipher",
    "Genesis",
    "ArcaneFlow",
    "Eternity"
  };

  private static final TextEffectStyle BOOK_ABSOLUTION_TITLE_STYLE = new TextEffectStyle(
    List.of(0xFF140724, 0xFF581C87, 0xFF9333EA, 0xFF140724),
    List.of(0.0f, 0.35f, 0.7f, 1.0f),
    2.0f, 0.70f, 0.35f,
    TextEffectStyle.BackdropMode.NONE,
    0, 0, false, false, true
  );

  private static final TextEffectStyle BOOK_ABSOLUTION_DESC_STYLE = new TextEffectStyle(
    List.of(0xFF3B0764, 0xFF7E22CE, 0xFFD8B4FE, 0xFF7E22CE, 0xFF3B0764),
    List.of(0.0f, 0.25f, 0.5f, 0.75f, 1.0f),
    1.8f, 0.70f, 0.25f,
    TextEffectStyle.BackdropMode.NONE,
    0, 0, false, false, false
  );

  private static boolean wasHeldOpen = false;
  private float currentOpenEase = 0.0f;
  private UUID currentRenderHolderId = null;

  public EverbookItemRenderer() {
    super(new DefaultedItemGeoModel<>(ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "everbook")));
  }

  public static boolean wasHeldOpen() {
    return wasHeldOpen;
  }

  public static void markWasHeldOpen(boolean open) {
    wasHeldOpen = open;
  }

  public static boolean consumeWasHeldOpen() {
    if (wasHeldOpen) {
      wasHeldOpen = false;
      return true;
    }
    return false;
  }

  private static boolean isHandPerspective(ItemDisplayContext ctx) {
    return ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
      || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
      || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
      || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
  }

  @Override
  public long getInstanceId(EverbookItem animatable) {
    return isHandPerspective(this.renderPerspective) ? HELD_INSTANCE_ID : GUI_INSTANCE_ID;
  }

  @Override
  public void renderByItem(
    ItemStack stack, ItemDisplayContext transformType,
    PoseStack poseStack, MultiBufferSource bufferSource,
    int packedLight, int packedOverlay
  ) {
    this.renderPerspective = transformType;

    UUID holderId = EverbookPlayerAnimController.resolveStackHolder(stack, transformType);
    this.currentRenderHolderId = holderId;
    Player holder = (holderId != null && Minecraft.getInstance().level != null)
      ? Minecraft.getInstance().level.getPlayerByUUID(holderId)
      : null;
    float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
    float prog = EverbookPlayerAnimController.getPlayerOpenProgress(holder, pt);
    float ease = prog * prog * (3.0f - 2.0f * prog);
    this.currentOpenEase = ease;

    poseStack.pushPose();
    if (transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
      float side = (transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) ? 1.0f : -1.0f;
      poseStack.translate(0.5f, 0.5f, 0.5f);

      var closed = ThreedifiedTransformTuner.getOrRegister("everbook_closed", 0.5f, -1.2f, 1.0f, -90.0f, 180.0f, 0.0f, 1.0f);
      var open = ThreedifiedTransformTuner.getOrRegister("everbook_open", -2.00f, 3.00f, -1.50f, 45.0f, 162.5f, 0.0f, 1.0f);

      float tx = Mth.lerp(ease, closed.tx * side, open.tx * side) / 16.0f;
      float ty = Mth.lerp(ease, closed.ty, open.ty) / 16.0f;
      float tz = Mth.lerp(ease, closed.tz, open.tz) / 16.0f;
      poseStack.translate(tx, ty, tz);

      float closedRy = (side > 0.0f) ? closed.ry : (closed.ry - 180.0f);
      float rx = Mth.lerp(ease, closed.rx, open.rx);
      float ry = Mth.lerp(ease, closedRy, open.ry);
      float rz = Mth.lerp(ease, closed.rz * side, open.rz * side);
      poseStack.mulPose(Axis.XP.rotationDegrees(rx));
      poseStack.mulPose(Axis.YP.rotationDegrees(ry));
      poseStack.mulPose(Axis.ZP.rotationDegrees(rz));

      float sc = Mth.lerp(ease, closed.scale, open.scale);
      if (Math.abs(sc - 1.0f) > 1.0e-4f) {
        poseStack.scale(sc, sc, sc);
      }

      if (ease > 0.55f && holderId != null) {
        EverbookPageVfxManager.recordOpenBookWorldPose(
          holderId,
          poseStack.last().pose(),
          Minecraft.getInstance().getEntityRenderDispatcher().camera.getPosition()
        );
      }

      if (ThreedifiedTransformTuner.isShowLocalGizmos()
        && ("everbook_closed".equals(ThreedifiedTransformTuner.getActiveSlot())
          || "everbook_open".equals(ThreedifiedTransformTuner.getActiveSlot()))) {
        ThreedifiedTransformTuner.drawLocalAxes(poseStack, bufferSource, 0.30f);
      }

      poseStack.translate(-0.5f, -0.5f, -0.5f);
    } else if (transformType == ItemDisplayContext.GUI) {
      poseStack.translate(0.5f, 0.36f, 0.5f);
      poseStack.mulPose(Axis.XP.rotationDegrees(25.0f));
      poseStack.mulPose(Axis.YP.rotationDegrees(-35.0f));
      poseStack.scale(1.45f, 1.45f, 1.45f);
      poseStack.translate(-0.5f, -0.5f, -0.5f);
    }

    super.renderByItem(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay);
    poseStack.popPose();
  }

  @Override
  public void renderRecursively(
    PoseStack poseStack, EverbookItem animatable, GeoBone bone,
    RenderType renderType, MultiBufferSource bufferSource,
    VertexConsumer buffer, boolean isReRender, float partialTick,
    int packedLight, int packedOverlay, int colour
  ) {
    super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);

    if (this.currentOpenEase <= 0.35f || !isHandPerspective(this.renderPerspective)) return;

    String name = bone.getName();
    boolean isLeftTopPage = "left_side_page_stack_3".equals(name);
    boolean isRightTopPage = "right_side_page_stack_3".equals(name);
    if (!isLeftTopPage && !isRightTopPage) return;

    int textAlpha = Mth.clamp((int) (((this.currentOpenEase - 0.35f) / 0.65f) * 245.0f), 0, 245);
    if (textAlpha <= 8) return;

    Font font = Minecraft.getInstance().font;

    String targetName = isLeftTopPage ? "everbook_left" : "everbook_right";
    float defTx = isLeftTopPage ? 0.200f : -0.750f;
    float defTy = isLeftTopPage ? 0.250f : 4.250f;
    float defTz = 2.000f;
    float defRx = isLeftTopPage ? -90.0f : 90.0f;
    float defRy = isLeftTopPage ? -80.0f : 100.0f;
    float defRz = isLeftTopPage ? 0.0f : -180.0f;
    float defScale = 0.004f;
    Style obfuscatedStyle = Style.EMPTY.withObfuscated(true);

    ThreedifiedTextTuner.draw(
      targetName,
      poseStack,
      bufferSource,
      font,
      15728880,
      defTx, defTy, defTz,
      defRx, defRy, defRz,
      defScale,
    (ps, buf, fnt, light) -> {
        for (int i = 0; RUNE_LINES.length > i; i++) {
          String line = isLeftTopPage ? RUNE_LINES[i] : RUNE_LINES[(i + 3) % RUNE_LINES.length];
          if (i == 0) {
            ThreeDifiedTextRenderer.draw3DBold(
              ps, buf, fnt, line, 0.0f, 0.0f, BOOK_ABSOLUTION_TITLE_STYLE, Style.EMPTY
            );
          } else {
            ThreeDifiedTextRenderer.draw3D(
              ps, buf, fnt, line, 0.0f, i * 10.0f, BOOK_ABSOLUTION_DESC_STYLE, obfuscatedStyle
            );
          }
        }
      }
    );
  }


  @EventBusSubscriber(modid = HexaCreateCore.MODID, value = Dist.CLIENT)
  public static final class ClientExtensionRegistry {
    private static EverbookItemRenderer instance;

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
      event.registerItem(new IClientItemExtensions() {
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
          if (instance == null) {
            instance = new EverbookItemRenderer();
          }
          return instance;
        }

        @Override
        public boolean applyForgeHandTransform(
          PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
          ItemStack itemInHand, float partialTick, float equipProcess, float swingProcess
        ) {
          float openProg = EverbookPlayerAnimController.getLocalOpenProgress(partialTick);
          if (0.001f >= openProg) {
            return false;
          }
          float ease = openProg * openProg * (3.0f - 2.0f * openProg);
          float sideSign = (arm == HumanoidArm.RIGHT) ? 1.0f : -1.0f;

          poseStack.translate(
            Mth.lerp(ease, sideSign * 0.56f, 0.0f),
            Mth.lerp(ease, -0.52f + (equipProcess * -0.60f), -0.26f),
            Mth.lerp(ease, -0.72f, -0.54f)
          );
          poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(ease, 0.0f, 58.0f)));
          poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(ease, 0.0f, 180.0f)));
          return true;
        }
      }, ModItems.EVERBOOK.get());
    }
  }
}
