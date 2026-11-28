package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.HexaCreateCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record DragonGeyserWavePayload(List<Entry> geysers) implements CustomPacketPayload {

  public record Entry(
    double x,
    double y,
    double z,
    float radius,
    float maxFlameHeight,
    int maxAgeTicks,
    int telegraphTicks
  ) {}

  public static final Type<DragonGeyserWavePayload> TYPE = new Type<>(
    ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "dragon_geyser_wave")
  );

  public static final StreamCodec<ByteBuf, DragonGeyserWavePayload> STREAM_CODEC = new StreamCodec<>() {
    @Override
    public DragonGeyserWavePayload decode(ByteBuf buf) {
      int count = ByteBufCodecs.VAR_INT.decode(buf);
      List<Entry> list = new ArrayList<>(Math.min(count, 64));
      for (int i = 0; count > i; i++) {
        list.add(new Entry(
          ByteBufCodecs.DOUBLE.decode(buf),
          ByteBufCodecs.DOUBLE.decode(buf),
          ByteBufCodecs.DOUBLE.decode(buf),
          ByteBufCodecs.FLOAT.decode(buf),
          ByteBufCodecs.FLOAT.decode(buf),
          ByteBufCodecs.VAR_INT.decode(buf),
          ByteBufCodecs.VAR_INT.decode(buf)
        ));
      }
      return new DragonGeyserWavePayload(list);
    }

    @Override
    public void encode(ByteBuf buf, DragonGeyserWavePayload payload) {
      List<Entry> list = payload.geysers();
      ByteBufCodecs.VAR_INT.encode(buf, list.size());
      for (Entry e : list) {
        ByteBufCodecs.DOUBLE.encode(buf, e.x());
        ByteBufCodecs.DOUBLE.encode(buf, e.y());
        ByteBufCodecs.DOUBLE.encode(buf, e.z());
        ByteBufCodecs.FLOAT.encode(buf, e.radius());
        ByteBufCodecs.FLOAT.encode(buf, e.maxFlameHeight());
        ByteBufCodecs.VAR_INT.encode(buf, e.maxAgeTicks());
        ByteBufCodecs.VAR_INT.encode(buf, e.telegraphTicks());
      }
    }
  };

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  public static void handleClient(DragonGeyserWavePayload payload, IPayloadContext ctx) {
    ctx.enqueueWork(() -> DragonGeyserManager.receiveClientWave(payload));
  }
}

