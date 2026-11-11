package com.iafenvoy.spb.runtime.evaluation;

import com.iafenvoy.spb.data.resolution.ResolvedPlan;
import com.iafenvoy.spb.data.validation.Issue;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Full result of re-evaluating one complete choice set. Contains everything needed both for the
 * client snapshot and for commit: candidate resource values (never clamped), per-option
 * contributions, quantity ranges, visibility/availability, rule failures, definition issues and the
 * frozen outcome plan.
 */
public record EvaluationResult(ResolvedPlan plan, Map<String, Double> variables, List<ResourceView> resources,
                               List<GroupView> groups, Map<Identifier, OptionView> options, List<Issue> issues,
                               List<RuleFailure> failures, List<OutcomePlan> outcomes, boolean canComplete) {
    public boolean hasErrors() {
        return this.issues.stream().anyMatch(Issue::isError);
    }

    public OptionView option(Identifier id) {
        return this.options.get(id);
    }
}
