package com.genesiz.hexacreatecore.create.wonderum.wormhole;

import com.genesiz.hexacreatecore.HexaCreateCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record WormholeSyncPayload(
  List<SyncNode> activeNodesInDim,
  boolean hasPending,
  SyncNode pendingNode
) implements CustomPacketPayload {

  public record SyncNode(BlockPos pos, Direction face) {
    public static final StreamCodec<ByteBuf, SyncNode> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, SyncNode::pos,
      Direction.STREAM_CODEC, SyncNode::face,
      SyncNode::new
    );
  }

  public static final Type<WormholeSyncPayload> TYPE = new Type<>(
    ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "wonderum_wormhole_sync")
  );

  public static final StreamCodec<ByteBuf, WormholeSyncPayload> STREAM_CODEC = StreamCodec.composite(
    SyncNode.STREAM_CODEC.apply(ByteBufCodecs.list()), WormholeSyncPayload::activeNodesInDim,
    ByteBufCodecs.BOOL, WormholeSyncPayload::hasPending,
    SyncNode.STREAM_CODEC, WormholeSyncPayload::pendingNode,
    WormholeSyncPayload::new
  );

  @Override
  public Type<? extends CustomPacketPayload> type() { return TYPE; }

  public static void handleClient(WormholeSyncPayload payload, IPayloadContext ctx) {
    ctx.enqueueWork(() -> {
      WormholeClientRenderer.updateSyncState(
        new ArrayList<>(payload.activeNodesInDim()),
        payload.hasPending() ? payload.pendingNode() : null
      );
    });
  }
}
