package com.iafenvoy.spb.network.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Candidate calculation for one option, requested while hovering the increment/decrement control.
 * Preview never mutates the draft.
 */
public record SelectionPreview(Identifier option, int quantity, boolean allowed, List<ResourceLine> resources,
                               List<Component> failures) {
    public static final Codec<SelectionPreview> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("option").forGetter(SelectionPreview::option),
            Codec.INT.optionalFieldOf("quantity", 0).forGetter(SelectionPreview::quantity),
            Codec.BOOL.optionalFieldOf("allowed", false).forGetter(SelectionPreview::allowed),
            ResourceLine.CODEC.listOf().optionalFieldOf("resources", List.of()).forGetter(SelectionPreview::resources),
            ComponentSerialization.CODEC.listOf().optionalFieldOf("failures", List.of()).forGetter(SelectionPreview::failures)
    ).apply(i, SelectionPreview::new));
}
