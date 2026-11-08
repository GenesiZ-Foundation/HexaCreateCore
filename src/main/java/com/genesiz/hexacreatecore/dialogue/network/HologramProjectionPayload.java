package com.genesiz.hexacreatecore.dialogue.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HologramProjectionPayload(
    String structureId,
    BlockPos anchorPos,
    int durationTicks
) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<HologramProjectionPayload> TYPE =
      new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("hexacreatecore", "hologram_projection"));

  public static final StreamCodec<ByteBuf, HologramProjectionPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.STRING_UTF8,
      HologramProjectionPayload::structureId,
      BlockPos.STREAM_CODEC,
      HologramProjectionPayload::anchorPos,
      ByteBufCodecs.VAR_INT,
      HologramProjectionPayload::durationTicks,
      HologramProjectionPayload::new
  );

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
