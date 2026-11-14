package com.genesiz.hexacreatecore.hexcasting.everbook;

import com.genesiz.hexacreatecore.HexaCreateCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record EverbookEvalPayload(
  boolean isResponse,
  CompoundTag data
) implements CustomPacketPayload {

  public static final Type<EverbookEvalPayload> TYPE = new Type<>(
    ResourceLocation.fromNamespaceAndPath(HexaCreateCore.MODID, "everbook_eval")
  );

  public static final StreamCodec<ByteBuf, EverbookEvalPayload> STREAM_CODEC = StreamCodec.composite(
    ByteBufCodecs.BOOL, EverbookEvalPayload::isResponse,
    ByteBufCodecs.COMPOUND_TAG, EverbookEvalPayload::data,
    EverbookEvalPayload::new
  );

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  public static EverbookEvalPayload request(List<EverbookData.PatternEntry> entries) {
    CompoundTag tag = new CompoundTag();
    ListTag list = new ListTag();
    for (EverbookData.PatternEntry e : entries) list.add(e.toNbt());
    tag.put("entries", list);
    return new EverbookEvalPayload(false, tag);
  }

  public static void handle(EverbookEvalPayload payload, IPayloadContext ctx) {
    ctx.enqueueWork(() -> {
      if (!payload.isResponse() && ctx.player() instanceof ServerPlayer sp) {
        List<EverbookData.PatternEntry> entries = new ArrayList<>();
        if (payload.data().contains("entries", Tag.TAG_LIST)) {
          ListTag list = payload.data().getList("entries", Tag.TAG_COMPOUND);
          for (int i = 0; list.size() > i; i++) {
            entries.add(EverbookData.PatternEntry.fromNbt(list.getCompound(i)));
          }
        }
        EverbookSandboxEvaluator.EvalResult res = EverbookSandboxEvaluator.evaluateNonDestructive(sp, entries);
        CompoundTag out = new CompoundTag();
        out.putBoolean("success", res.success());
        out.putBoolean("collapsed", res.collapsedRift());
        out.putInt("recursions", res.recursionCount());
        out.putString("summary", res.summaryText());
        ListTag stackList = new ListTag();
        for (String s : res.stackPreview()) stackList.add(StringTag.valueOf(s));
        out.put("stack", stackList);
        PacketDistributor.sendToPlayer(sp, new EverbookEvalPayload(true, out));
      } else if (payload.isResponse()) {
        CompoundTag d = payload.data();
        List<String> stack = new ArrayList<>();
        if (d.contains("stack", Tag.TAG_LIST)) {
          ListTag list = d.getList("stack", Tag.TAG_STRING);
          for (int i = 0; list.size() > i; i++) stack.add(list.getString(i));
        }
        EverbookSessionState.onEvaluationResult(
          d.getBoolean("success"),
          d.getBoolean("collapsed"),
          d.getInt("recursions"),
          d.getString("summary"),
          stack
        );
      }
    });
  }
}
