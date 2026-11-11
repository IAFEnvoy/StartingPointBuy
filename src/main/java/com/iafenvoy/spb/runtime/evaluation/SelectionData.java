package com.iafenvoy.spb.runtime.evaluation;

import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * The immutable input of an evaluation: captured external inputs, frozen baseline values and the
 * complete choice set. This is exactly the state stored in a session, minus session metadata.
 */
public record SelectionData(Map<String, Double> inputs, Map<Identifier, Double> base,
                            Map<Identifier, Integer> selections) {
    public static SelectionData empty() {
        return new SelectionData(Map.of(), Map.of(), Map.of());
    }

    public int quantity(Identifier option) {
        return this.selections.getOrDefault(option, 0);
    }
}
