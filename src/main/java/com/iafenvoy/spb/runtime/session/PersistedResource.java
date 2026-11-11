package com.iafenvoy.spb.runtime.session;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * A single persisted resource value. Values are stored as plain identifiers so a save can survive a
 * data pack removing a resource: unknown entries stay in the list and are reported instead of making
 * the whole attachment fail to decode.
 */
public record PersistedResource(Identifier id, double value) {
    public static final Codec<PersistedResource> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(PersistedResource::id),
            Codec.DOUBLE.fieldOf("value").forGetter(PersistedResource::value)
    ).apply(i, PersistedResource::new));
}
