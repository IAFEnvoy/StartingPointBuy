package com.iafenvoy.spb.runtime.evaluation;

import com.iafenvoy.spb.data.Outcome;
import net.minecraft.resources.Identifier;

import java.util.List;

public record OutcomePlan(String scope, Outcome outcome, List<Identifier> abilities, List<ActionPlan> actions) {
}
