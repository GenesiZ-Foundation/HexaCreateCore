package com.genesiz.hexacreatecore.threedified.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

public record DialogueStackSyncPayload(List<String> stackDescs, String ravensmind) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<DialogueStackSyncPayload> TYPE =
  new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("hexacreatecore", "dialogue_stack_sync"));

  public static final StreamCodec<ByteBuf, DialogueStackSyncPayload> STREAM_CODEC = StreamCodec.composite(
    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
    DialogueStackSyncPayload::stackDescs,
    ByteBufCodecs.STRING_UTF8,
    DialogueStackSyncPayload::ravensmind,
    DialogueStackSyncPayload::new
  );

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
