package com.iafenvoy.spb.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * What happens to a bound resource on commit. {@link #REPLACE} writes the final evaluated value into
 * the player's real Saturn resource holder, {@link #DISCARD} only uses the value while allocating and
 * never touches the real holder.
 */
public enum CommitMode implements StringRepresentable {
    REPLACE, DISCARD;
    public static final Codec<CommitMode> CODEC = StringRepresentable.fromValues(CommitMode::values);

    @Override
    public @NonNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
