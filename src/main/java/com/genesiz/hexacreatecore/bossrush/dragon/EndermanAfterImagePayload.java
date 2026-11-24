package com.genesiz.hexacreatecore.bossrush.dragon;

import com.genesiz.hexacreatecore.HexaCreateCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record EndermanAfterImagePayload(
  double x,
  double y,
  double z,
  float bodyYaw,
  float headYawRel,
  float headPitch,
  float limbSwing,
  float limbSwingAmount,
  boolean creepy
) implements CustomPacketPayload {

  public static final Type<EndermanAfterImagePayload> TYPE = new Type<>(
    ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "enderman_vaporize_afterimage")
  );

  public static final StreamCodec<ByteBuf, EndermanAfterImagePayload> STREAM_CODEC = new StreamCodec<>() {
    @Override
    public EndermanAfterImagePayload decode(ByteBuf buf) {
      return new EndermanAfterImagePayload(
        ByteBufCodecs.DOUBLE.decode(buf),
        ByteBufCodecs.DOUBLE.decode(buf),
        ByteBufCodecs.DOUBLE.decode(buf),
        ByteBufCodecs.FLOAT.decode(buf),
        ByteBufCodecs.FLOAT.decode(buf),
        ByteBufCodecs.FLOAT.decode(buf),
        ByteBufCodecs.FLOAT.decode(buf),
        ByteBufCodecs.FLOAT.decode(buf),
        ByteBufCodecs.BOOL.decode(buf)
      );
    }

    @Override
    public void encode(ByteBuf buf, EndermanAfterImagePayload p) {
      ByteBufCodecs.DOUBLE.encode(buf, p.x());
      ByteBufCodecs.DOUBLE.encode(buf, p.y());
      ByteBufCodecs.DOUBLE.encode(buf, p.z());
      ByteBufCodecs.FLOAT.encode(buf, p.bodyYaw());
      ByteBufCodecs.FLOAT.encode(buf, p.headYawRel());
      ByteBufCodecs.FLOAT.encode(buf, p.headPitch());
      ByteBufCodecs.FLOAT.encode(buf, p.limbSwing());
      ByteBufCodecs.FLOAT.encode(buf, p.limbSwingAmount());
      ByteBufCodecs.BOOL.encode(buf, p.creepy());
    }
  };

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  public static void handleClient(EndermanAfterImagePayload payload, IPayloadContext ctx) {
    ctx.enqueueWork(() -> EndermanAfterImageClientRenderer.spawnAfterImage(payload));
  }
}
