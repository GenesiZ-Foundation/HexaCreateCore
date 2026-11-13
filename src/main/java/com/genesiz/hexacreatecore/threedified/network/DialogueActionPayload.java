package com.genesiz.hexacreatecore.threedified.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DialogueActionPayload(int entityId, String actionId, boolean isRightClick) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<DialogueActionPayload> TYPE =
      new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("hexacreatecore", "dialogue_action"));

  public static final StreamCodec<ByteBuf, DialogueActionPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT,
      DialogueActionPayload::entityId,
      ByteBufCodecs.STRING_UTF8,
      DialogueActionPayload::actionId,
      ByteBufCodecs.BOOL,
      DialogueActionPayload::isRightClick,
      DialogueActionPayload::new
  );

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
