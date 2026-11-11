package com.iafenvoy.spb.runtime.evaluation;

import com.iafenvoy.spb.network.model.ResourceLine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A candidate resource value with its physical bounds and precision state.
 */
public record ResourceView(Identifier id, String key, Component name, double base, double value, double min,
                           double max, double quantum, boolean display, boolean boundsOk, boolean quantumOk) {
    public boolean valid() {
        return this.boundsOk && this.quantumOk;
    }

    public ResourceLine line() {
        return new ResourceLine(this.key, this.name, this.base, this.value, this.min, this.max, this.display, this.valid());
    }
}
