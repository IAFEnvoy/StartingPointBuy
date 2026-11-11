package com.iafenvoy.spb.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NonNull;

/**
 * A reference to a data-pack entry: either a plain id, or {@code #namespace:path} for a tag.
 * <p>
 * Direct references are checked by the registry loader; tags are expanded after all registries have
 * frozen. The JSON format remains a plain id or a string starting with {@code #}.
 */
public record EntryRef<T>(Either<Holder<T>, TagKey<T>> value) {
    public static <T> Codec<EntryRef<T>> codec(ResourceKey<? extends Registry<T>> registry) {
        return Codec.either(RegistryFixedCodec.create(registry), TagKey.hashedCodec(registry))
                .xmap(EntryRef::new, EntryRef::value);
    }

    public static <T> EntryRef<T> of(Holder<T> holder) {
        return new EntryRef<>(Either.left(holder));
    }

    public static <T> EntryRef<T> tag(TagKey<T> tag) {
        return new EntryRef<>(Either.right(tag));
    }

    public Identifier id() {
        return this.value.map(holder -> holder.unwrapKey().orElseThrow().identifier(), TagKey::location);
    }

    public boolean tag() {
        return this.value.right().isPresent();
    }

    public String serialize() {
        return this.tag() ? "#" + this.id() : this.id().toString();
    }

    @Override
    public @NonNull String toString() {
        return this.serialize();
    }
}
