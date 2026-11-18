package com.genesiz.hexacreatecore.threedified.network;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.threedified.ThreeDifiedNotificationHud;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ThreeDifiedNotifyPayload(
  String message,
  int borderColor,
  int durationTicks
) implements CustomPacketPayload {

  public static final Type<ThreeDifiedNotifyPayload> TYPE = new Type<>(
    ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "threedified_notify")
  );

  public static final StreamCodec<ByteBuf, ThreeDifiedNotifyPayload> STREAM_CODEC = StreamCodec.composite(
    ByteBufCodecs.STRING_UTF8, ThreeDifiedNotifyPayload::message,
    ByteBufCodecs.VAR_INT, ThreeDifiedNotifyPayload::borderColor,
    ByteBufCodecs.VAR_INT, ThreeDifiedNotifyPayload::durationTicks,
    ThreeDifiedNotifyPayload::new
  );

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  public static void handleClient(ThreeDifiedNotifyPayload payload, IPayloadContext ctx) {
    ctx.enqueueWork(() -> ThreeDifiedNotificationHud.show(
      payload.message(),
      payload.borderColor(),
      payload.durationTicks()
    ));
  }
}
