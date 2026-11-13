package com.genesiz.hexacreatecore.difficulty.network;

import com.genesiz.hexacreatecore.HexaCreateCore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class DifficultyPackets {

  public record SyncPayload(
    String activeTierId,
    boolean genesisUnlocked,
    int riposteCharges,
    boolean ripostePrimed,
    long parryFlowRemainingMs,
    String descriptionsJson,
    String gaugeBarsJson,
    String parrySoundsJson
  ) implements CustomPacketPayload {
    public static final Type<SyncPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "difficulty_sync"));

    public static final StreamCodec<FriendlyByteBuf, SyncPayload> STREAM_CODEC = StreamCodec.of(
      (buf, p) -> {
        buf.writeUtf(p.activeTierId(), 64);
        buf.writeBoolean(p.genesisUnlocked());
        buf.writeInt(p.riposteCharges());
        buf.writeBoolean(p.ripostePrimed());
        buf.writeLong(p.parryFlowRemainingMs());
        buf.writeUtf(p.descriptionsJson(), 65536);
        buf.writeUtf(p.gaugeBarsJson(), 65536);
        buf.writeUtf(p.parrySoundsJson(), 16384);
      },
      buf -> new SyncPayload(
        buf.readUtf(64), buf.readBoolean(), buf.readInt(),
        buf.readBoolean(), buf.readLong(), buf.readUtf(65536), buf.readUtf(65536), buf.readUtf(16384)
      )

    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
  }


  public record ActionPayload(
    String actionType,
    String stringParam,
    int intParam
  ) implements CustomPacketPayload {
    public static final Type<ActionPayload> TYPE =
    new Type<>(ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "difficulty_action"));

    public static final StreamCodec<FriendlyByteBuf, ActionPayload> STREAM_CODEC = StreamCodec.of(
    (buf, p) -> {
        buf.writeUtf(p.actionType(), 64);
        buf.writeUtf(p.stringParam(), 128);
        buf.writeInt(p.intParam());
      },
      buf -> new ActionPayload(buf.readUtf(64), buf.readUtf(128), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
  }

  public record SoulSyncPayload(
    boolean inSoulForm,
    double bodyX,
    double bodyY,
    double bodyZ,
    double launchX,
    double launchY,
    double launchZ,
    int remainingTicks,
    int maxTicks,
    int fractureStacks
  ) implements CustomPacketPayload {
    public static final Type<SoulSyncPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "soul_sync"));

    public static final StreamCodec<FriendlyByteBuf, SoulSyncPayload> STREAM_CODEC = StreamCodec.of(
      (buf, p) -> {
        buf.writeBoolean(p.inSoulForm);
        buf.writeDouble(p.bodyX);
        buf.writeDouble(p.bodyY);
        buf.writeDouble(p.bodyZ);
        buf.writeDouble(p.launchX);
        buf.writeDouble(p.launchY);
        buf.writeDouble(p.launchZ);
        buf.writeInt(p.remainingTicks);
        buf.writeInt(p.maxTicks);
        buf.writeInt(p.fractureStacks);
      },
      buf -> new SoulSyncPayload(
        buf.readBoolean(),
        buf.readDouble(), buf.readDouble(), buf.readDouble(),
        buf.readDouble(), buf.readDouble(), buf.readDouble(),
        buf.readInt(), buf.readInt(), buf.readInt()
      )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
      return TYPE;
    }
  }

  public record SoulActionPayload(String action) implements CustomPacketPayload {
    public static final Type<SoulActionPayload> TYPE =
    new Type<>(ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "soul_action"));

    public static final StreamCodec<FriendlyByteBuf, SoulActionPayload> STREAM_CODEC = StreamCodec.of(
    (buf, p) -> buf.writeUtf(p.action),
      buf -> new SoulActionPayload(buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
      return TYPE;
    }
  }


}
