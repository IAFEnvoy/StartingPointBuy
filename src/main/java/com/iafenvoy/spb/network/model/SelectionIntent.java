package com.iafenvoy.spb.network.model;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.UUID;

/**
 * A client intent. It carries only the operation and the raw target quantity; the server owns the
 * session, the definitions and every computed number.
 */
public record SelectionIntent(IntentOperation op, UUID sessionId, int revision, Optional<Identifier> option,
                              int quantity,
                              Optional<String> preset) {
    public static final StreamCodec<ByteBuf, SelectionIntent> STREAM_CODEC = StreamCodec.composite(
            IntentOperation.STREAM_CODEC, SelectionIntent::op,
            UUIDUtil.STREAM_CODEC, SelectionIntent::sessionId,
            ByteBufCodecs.VAR_INT, SelectionIntent::revision,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), SelectionIntent::option,
            ByteBufCodecs.VAR_INT, SelectionIntent::quantity,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), SelectionIntent::preset,
            SelectionIntent::new);

    public static SelectionIntent open() {
        return new SelectionIntent(IntentOperation.OPEN, new UUID(0L, 0L), 0, Optional.empty(), 0, Optional.empty());
    }

    public static SelectionIntent close(UUID sessionId, int revision) {
        return new SelectionIntent(IntentOperation.CLOSE, sessionId, revision, Optional.empty(), 0, Optional.empty());
    }

    public static SelectionIntent setQuantity(UUID sessionId, int revision, Identifier option, int quantity) {
        return new SelectionIntent(IntentOperation.SET_QUANTITY, sessionId, revision, Optional.of(option), quantity, Optional.empty());
    }

    public static SelectionIntent applyPreset(UUID sessionId, int revision, String preset) {
        return new SelectionIntent(IntentOperation.APPLY_PRESET, sessionId, revision, Optional.empty(), 0, Optional.of(preset));
    }

    public static SelectionIntent preview(UUID sessionId, int revision, Identifier option, int quantity) {
        return new SelectionIntent(IntentOperation.PREVIEW, sessionId, revision, Optional.of(option), quantity, Optional.empty());
    }

    public static SelectionIntent reset(UUID sessionId, int revision) {
        return new SelectionIntent(IntentOperation.RESET, sessionId, revision, Optional.empty(), 0, Optional.empty());
    }

    public static SelectionIntent refresh(UUID sessionId, int revision) {
        return new SelectionIntent(IntentOperation.REFRESH, sessionId, revision, Optional.empty(), 0, Optional.empty());
    }

    public static SelectionIntent submit(UUID sessionId, int revision) {
        return new SelectionIntent(IntentOperation.SUBMIT, sessionId, revision, Optional.empty(), 0, Optional.empty());
    }
}
