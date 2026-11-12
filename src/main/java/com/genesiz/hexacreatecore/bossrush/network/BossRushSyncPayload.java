package com.genesiz.hexacreatecore.bossrush.network;

import com.genesiz.hexacreatecore.HexaCreateCore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BossRushSyncPayload(
  boolean active,
  String phaseName,
  String phaseDescription,
  String musicId,
  String musicTitle,
  String totemItemId,
  String totemSoundId,
  String statusBanner
) implements CustomPacketPayload {

  public static final Type<BossRushSyncPayload> TYPE =
  new Type<>(ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "boss_rush_sync"));

  public static final StreamCodec<FriendlyByteBuf, BossRushSyncPayload> STREAM_CODEC = StreamCodec.of(
  (buf, p) -> {
      buf.writeBoolean(p.active());
      buf.writeUtf(p.phaseName() != null ? p.phaseName() : "");
      buf.writeUtf(p.phaseDescription() != null ? p.phaseDescription() : "");
      buf.writeUtf(p.musicId() != null ? p.musicId() : "");
      buf.writeUtf(p.musicTitle() != null ? p.musicTitle() : "");
      buf.writeUtf(p.totemItemId() != null ? p.totemItemId() : "");
      buf.writeUtf(p.totemSoundId() != null ? p.totemSoundId() : "");
      buf.writeUtf(p.statusBanner() != null ? p.statusBanner() : "");
    },
    buf -> new BossRushSyncPayload(
      buf.readBoolean(),
      buf.readUtf(),
      buf.readUtf(),
      buf.readUtf(),
      buf.readUtf(),
      buf.readUtf(),
      buf.readUtf(),
      buf.readUtf()
    )
  );

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
