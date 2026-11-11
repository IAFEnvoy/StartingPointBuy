package com.iafenvoy.spb.network.payload;

import com.iafenvoy.spb.StartingPointBuy;
import com.iafenvoy.spb.network.model.SelectionSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * The complete client view of one session, plus whether the client should open the screen for it.
 */
public record SnapshotS2CPayload(SelectionSnapshot snapshot, boolean openScreen) implements CustomPacketPayload {
    public static final Type<SnapshotS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(StartingPointBuy.MOD_ID, "snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodecWithRegistries(SelectionSnapshot.CODEC), SnapshotS2CPayload::snapshot,
            ByteBufCodecs.BOOL, SnapshotS2CPayload::openScreen,
            SnapshotS2CPayload::new);

    @Override
    public @NonNull Type<SnapshotS2CPayload> type() {
        return TYPE;
    }
}
