package com.iafenvoy.spb.network.payload;

import com.iafenvoy.spb.StartingPointBuy;
import com.iafenvoy.spb.network.model.SelectionIntent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * A client intent: the operation plus a raw target quantity. It never carries a computed number - the
 * server owns the session, the definitions and every result.
 */
public record IntentC2SPayload(SelectionIntent intent) implements CustomPacketPayload {
    public static final Type<IntentC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(StartingPointBuy.MOD_ID, "intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, IntentC2SPayload> STREAM_CODEC = StreamCodec.composite(
            SelectionIntent.STREAM_CODEC, IntentC2SPayload::intent,
            IntentC2SPayload::new);

    @Override
    public @NonNull Type<IntentC2SPayload> type() {
        return TYPE;
    }
}
