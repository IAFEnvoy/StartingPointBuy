package com.iafenvoy.spb.runtime.evaluation;

import net.minecraft.resources.Identifier;

/**
 * Per-unit and total contribution of one option to one resource.
 */
public record Contribution(Identifier resource, String key, double perUnit, double total) {
}
