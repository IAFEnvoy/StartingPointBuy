package com.iafenvoy.spb.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * How a group counts its current selection. {@link #DISTINCT} counts selected option kinds,
 * {@link #TOTAL} counts the sum of all option quantities.
 */
public enum CountMode implements StringRepresentable {
    DISTINCT, TOTAL;
    public static final Codec<CountMode> CODEC = StringRepresentable.fromValues(CountMode::values);

    @Override
    public @NonNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
