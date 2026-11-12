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
  String statusBanner,
  int activeBossEntityId,
  String healthBarsJson
) implements CustomPacketPayload {

  public static final Type<BossRushSyncPayload> TYPE =
    new Type<>(ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "boss_rush_sync"));

  public static final StreamCodec<FriendlyByteBuf, BossRushSyncPayload> STREAM_CODEC = StreamCodec.of(
    (buf, p) -> {
      buf.writeBoolean(p.active());
      buf.writeUtf(p.phaseName() != null ? p.phaseName() : "", 32767);
      buf.writeUtf(p.phaseDescription() != null ? p.phaseDescription() : "", 32767);
      buf.writeUtf(p.musicId() != null ? p.musicId() : "", 32767);
      buf.writeUtf(p.musicTitle() != null ? p.musicTitle() : "", 32767);
      buf.writeUtf(p.totemItemId() != null ? p.totemItemId() : "", 32767);
      buf.writeUtf(p.totemSoundId() != null ? p.totemSoundId() : "", 32767);
      buf.writeUtf(p.statusBanner() != null ? p.statusBanner() : "", 32767);
      buf.writeInt(p.activeBossEntityId());
      buf.writeUtf(p.healthBarsJson() != null ? p.healthBarsJson() : "{}", 262144);
    },
    buf -> new BossRushSyncPayload(
      buf.readBoolean(),
      buf.readUtf(32767),
      buf.readUtf(32767),
      buf.readUtf(32767),
      buf.readUtf(32767),
      buf.readUtf(32767),
      buf.readUtf(32767),
      buf.readUtf(32767),
      buf.readInt(),
      buf.readUtf(262144)
    )
  );

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
