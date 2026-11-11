package com.iafenvoy.spb.runtime.session;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The persisted choice set: an option id to quantity map plus the last applied preset key (which is
 * only display metadata, never the source of truth for the numbers).
 */
public record SelectionState(Map<Identifier, Integer> selections, Optional<String> presetKey) {
    public static final SelectionState EMPTY = new SelectionState(Map.of(), Optional.empty());
    public static final Codec<SelectionState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("selections", Map.of()).forGetter(SelectionState::selections),
            Codec.STRING.optionalFieldOf("preset_key").forGetter(SelectionState::presetKey)
    ).apply(i, SelectionState::new));

    public static SelectionState of(Map<Identifier, Integer> selections, String presetKey) {
        return new SelectionState(new LinkedHashMap<>(selections), Optional.ofNullable(presetKey));
    }
}
