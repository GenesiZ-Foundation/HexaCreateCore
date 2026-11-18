package com.genesiz.hexacreatecore.create.wonderum.blueprint;

import com.genesiz.hexacreatecore.HexaCreateCore;
import com.genesiz.hexacreatecore.item.ModItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WonderumDraftBlockPayload(BlockPos targetPos, int blockStateId) implements CustomPacketPayload {

  public static final Type<WonderumDraftBlockPayload> TYPE = new Type<>(
    ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "wonderum_draft_block")
  );

  public static final StreamCodec<ByteBuf, WonderumDraftBlockPayload> STREAM_CODEC = StreamCodec.composite(
    BlockPos.STREAM_CODEC, WonderumDraftBlockPayload::targetPos,
    ByteBufCodecs.VAR_INT, WonderumDraftBlockPayload::blockStateId,
    WonderumDraftBlockPayload::new
  );

  @Override
  public Type<? extends CustomPacketPayload> type() { return TYPE; }

  public static void handleServer(WonderumDraftBlockPayload payload, IPayloadContext ctx) {
    ctx.enqueueWork(() -> {
      if (!(ctx.player() instanceof ServerPlayer sp)) return;
      if (!sp.getMainHandItem().is(ModItems.WONDERUM.get())) return;

      ServerLevel level = sp.serverLevel();
      BlockPos pos = payload.targetPos();
      if (!level.isLoaded(pos) || sp.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 144.0D) return;

      BlockState desired = Block.stateById(payload.blockStateId());
      if (desired.isAir()) return;

      BlockState existing = level.getBlockState(pos);
      if (!existing.isAir() && !existing.canBeReplaced()) return;

      if (!sp.getAbilities().instabuild) {
        Item needed = desired.getBlock().asItem();
        if (!consumeOne(sp, needed)) return;
      }

      level.setBlock(pos, desired, 3);
      level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 8, 0.25D, 0.25D, 0.25D, 0.02D);
    });
  }

  private static boolean consumeOne(ServerPlayer sp, Item item) {
    for (int i = 0; sp.getInventory().getContainerSize() > i; i++) {
      ItemStack stack = sp.getInventory().getItem(i);
      if (!stack.isEmpty() && stack.is(item)) {
        stack.shrink(1);
        return true;
      }
    }
    return false;
  }
}
