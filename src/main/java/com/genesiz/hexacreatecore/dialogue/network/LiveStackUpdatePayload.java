package com.genesiz.hexacreatecore.dialogue.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

public record LiveStackUpdatePayload(List<String> stackDescs) implements CustomPacketPayload {
  public static final Type<LiveStackUpdatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("hexacreatecore", "live_stack_update"));

  public static final StreamCodec<FriendlyByteBuf, LiveStackUpdatePayload> STREAM_CODEC = StreamCodec.of(
  (buf, payload) -> {
      buf.writeInt(payload.stackDescs().size());
      for (String str : payload.stackDescs()) {
        buf.writeUtf(str);
      }
    },
    buf -> {
      int size = buf.readInt();
      java.util.List<String> list = new java.util.ArrayList<>(size);
      for (int i = 0; size > i; i++) {
        list.add(buf.readUtf());
      }
      return new LiveStackUpdatePayload(list);
    }
  );

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
