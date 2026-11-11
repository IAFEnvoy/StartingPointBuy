package com.iafenvoy.spb.network.model;

import com.iafenvoy.saturn.util.codec.MiscStreamCodecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

public enum IntentOperation implements StringRepresentable {
    OPEN, SET_QUANTITY, APPLY_PRESET, PREVIEW, RESET, SUBMIT, CLOSE, REFRESH;
    public static final StreamCodec<ByteBuf, IntentOperation> STREAM_CODEC = MiscStreamCodecs.enumCodec(IntentOperation.class);

    @Override
    public @NonNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
