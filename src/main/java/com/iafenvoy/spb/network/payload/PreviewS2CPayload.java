package com.iafenvoy.spb.network.payload;

import com.iafenvoy.spb.StartingPointBuy;
import com.iafenvoy.spb.network.model.SelectionPreview;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Candidate calculation for one option; it never mutates the draft on either side.
 */
public record PreviewS2CPayload(SelectionPreview preview) implements CustomPacketPayload {
    public static final Type<PreviewS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(StartingPointBuy.MOD_ID, "preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PreviewS2CPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(SelectionPreview.CODEC).map(PreviewS2CPayload::new, PreviewS2CPayload::preview);

    @Override
    public @NonNull Type<PreviewS2CPayload> type() {
        return TYPE;
    }
}
