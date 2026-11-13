package com.genesiz.hexacreatecore.threedified.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

public record LiveStackUpdatePayload(List<String> stackDescs, String ravensmind) implements CustomPacketPayload {
  public static final Type<LiveStackUpdatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("hexacreatecore", "live_stack_update"));

  public LiveStackUpdatePayload(List<String> stackDescs) {
    this(stackDescs, "");
  }

  public static final StreamCodec<FriendlyByteBuf, LiveStackUpdatePayload> STREAM_CODEC = StreamCodec.of(
  (buf, payload) -> {
      buf.writeInt(payload.stackDescs().size());
      for (String str : payload.stackDescs()) {
        buf.writeUtf(str);
      }
      buf.writeUtf(payload.ravensmind() != null ? payload.ravensmind() : "");
    },
    buf -> {
      int size = buf.readInt();
      java.util.List<String> list = new java.util.ArrayList<>(size);
      for (int i = 0; size > i; i++) {
        list.add(buf.readUtf());
      }
      String raven = buf.readUtf();
      return new LiveStackUpdatePayload(list, raven);
    }
  );

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}

