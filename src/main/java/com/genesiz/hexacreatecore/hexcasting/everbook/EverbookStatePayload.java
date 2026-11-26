package com.genesiz.hexacreatecore.hexcasting.everbook;

import com.genesiz.hexacreatecore.HexaCreateCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record EverbookStatePayload(
  UUID playerId,
  boolean isOpen
) implements CustomPacketPayload {

  public static final Type<EverbookStatePayload> TYPE = new Type<>(
    ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "everbook_pose_state")
  );

  public static final StreamCodec<ByteBuf, EverbookStatePayload> STREAM_CODEC = StreamCodec.composite(
    UUIDUtil.STREAM_CODEC, EverbookStatePayload::playerId,
    ByteBufCodecs.BOOL, EverbookStatePayload::isOpen,
    EverbookStatePayload::new
  );

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  public static void handleClient(EverbookStatePayload payload, IPayloadContext ctx) {
    ctx.enqueueWork(() -> EverbookPlayerAnimController.setRemotePlayerOpen(payload.playerId(), payload.isOpen()));
  }
}

