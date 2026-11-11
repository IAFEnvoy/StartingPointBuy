package com.iafenvoy.spb.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Selection screen settings. Dimensions use GUI logical pixels; closing requires a server decision.
 *
 * <p>{@code max_width}/{@code max_height} of {@code 0} mean "use the logical screen size"; a positive
 * value is an optional cap for data packs that want a fixed-size window.
 */
public record UiSettings(int presetSidebarWidth, int maxWidth, int maxHeight, boolean allowCloseBeforeComplete) {
    public static final UiSettings DEFAULT = new UiSettings(100, 0, 0, false);

    public static final Codec<UiSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("preset_sidebar_width", 100).forGetter(UiSettings::presetSidebarWidth),
            Codec.INT.optionalFieldOf("max_width", 0).forGetter(UiSettings::maxWidth),
            Codec.INT.optionalFieldOf("max_height", 0).forGetter(UiSettings::maxHeight),
            Codec.BOOL.optionalFieldOf("allow_close_before_complete", false).forGetter(UiSettings::allowCloseBeforeComplete)
    ).apply(i, UiSettings::new));
}
