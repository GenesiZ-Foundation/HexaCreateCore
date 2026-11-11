package com.genesiz.hexacreatecore.hexcasting;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record Cast3DSpellPayload(int entityId, String startDirName, String anglesSignature) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<Cast3DSpellPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("hexacreatecore", "cast_3d_spell_packet"));

    public static final StreamCodec<ByteBuf, Cast3DSpellPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, Cast3DSpellPayload::entityId,
        ByteBufCodecs.STRING_UTF8, Cast3DSpellPayload::startDirName,
        ByteBufCodecs.STRING_UTF8, Cast3DSpellPayload::anglesSignature,
        Cast3DSpellPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
