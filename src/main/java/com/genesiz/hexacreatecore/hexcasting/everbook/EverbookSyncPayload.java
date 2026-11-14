package com.genesiz.hexacreatecore.hexcasting.everbook;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.item.custom.EverbookItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record EverbookSyncPayload(
  boolean isOffhand,
  String action,
  CompoundTag bookNbt
) implements CustomPacketPayload {

  public static final Type<EverbookSyncPayload> TYPE = new Type<>(
    ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "everbook_sync")
  );

  public static final StreamCodec<ByteBuf, EverbookSyncPayload> STREAM_CODEC = StreamCodec.composite(
    ByteBufCodecs.BOOL, EverbookSyncPayload::isOffhand,
    ByteBufCodecs.STRING_UTF8, EverbookSyncPayload::action,
    ByteBufCodecs.COMPOUND_TAG, EverbookSyncPayload::bookNbt,
    EverbookSyncPayload::new
  );

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  public static void handleServer(EverbookSyncPayload payload, IPayloadContext ctx) {
    ctx.enqueueWork(() -> {
      if (!(ctx.player() instanceof ServerPlayer sp)) return;
      InteractionHand hand = payload.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
      ItemStack bookStack = sp.getItemInHand(hand);
      if (!(bookStack.getItem() instanceof EverbookItem)) return;

      EverbookData.BookState state = EverbookData.BookState.fromNbt(payload.bookNbt());
      EverbookData.write(bookStack, state);
    });
  }
}
