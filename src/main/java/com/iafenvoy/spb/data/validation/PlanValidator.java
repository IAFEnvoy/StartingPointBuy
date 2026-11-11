package com.iafenvoy.spb.data.validation;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.condition.builtin.entity.meta.AndEntityCondition;
import com.iafenvoy.saturn.data.condition.builtin.entity.meta.NotEntityCondition;
import com.iafenvoy.saturn.data.condition.builtin.entity.meta.OrEntityCondition;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.iafenvoy.saturn.util.formula.FormulaVariables;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.iafenvoy.saturn.util.formula.number.Expression;
import com.iafenvoy.saturn.util.formula.number.Sum;
import com.iafenvoy.spb.data.*;
import com.iafenvoy.spb.data.condition.CompareEntityCondition;
import com.iafenvoy.spb.data.resolution.ResolvedGroup;
import com.iafenvoy.spb.data.resolution.ResolvedOption;
import com.iafenvoy.spb.data.resolution.ResolvedPlan;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Set;

/**
 * Checks formula variable visibility against the complete resolved plan. Number decoding and
 * evaluation follow Saturn; captured inputs and initial values are outside this scope check.
 * Contribution formulas cannot read final resources, preventing an implicit fixed point.
 */
public final class PlanValidator {
    private PlanValidator() {
    }

    public static void validate(ResolvedPlan resolved, List<Issue> issues) {
        Plan plan = resolved.plan();
        String base = "plan/" + resolved.id();
        VariableScope scope = VariableScope.of(plan, resolved);

        validateCondition(plan.activation().condition(), base + "/activation/condition", scope, issues, false);
        for (ResolvedGroup group : resolved.groups()) {
            String path = base + "/groups/" + group.id();
            validateProvider(group.definition().minCount(), path + "/min_count", scope.full(), issues);
            group.definition().maxCount().ifPresent(provider -> validateProvider(provider, path + "/max_count", scope.full(), issues));
            validateCondition(group.definition().visibleCondition(), path + "/visible_condition", scope, issues, false);
        }
        for (ResolvedOption option : resolved.orderedOptions()) {
            String path = base + "/options/" + option.id();
            validateProvider(option.definition().minCount(), path + "/min_count", scope.full(), issues);
            option.definition().maxCount().ifPresent(provider -> validateProvider(provider, path + "/max_count", scope.full(), issues));
            validateCondition(option.definition().visibleCondition(), path + "/visible_condition", scope, issues, false);
            validateCondition(option.definition().enabledCondition(), path + "/enabled_condition", scope, issues, true);
            option.definition().resourceChanges().forEach((resource, provider) ->
                    validateProvider(provider, path + "/resource_changes/" + resource.unwrapKey().orElseThrow().identifier(), scope.change(), issues));
            for (ValidationRule rule : option.definition().validationRules()) {
                String rulePath = path + "/validation_rules/" + rule.key();
                validateCondition(rule.condition(), rulePath, scope, issues, true);
            }
            validateOutcomes(option.definition().outcomes(), path + "/outcomes", issues, scope);
        }

        for (CompletionRule rule : plan.completionRules()) {
            String path = base + "/completion_rules/" + rule.key();
            validateCondition(rule.condition(), path, scope, issues, true);
        }
        validateOutcomes(plan.outcomes(), base + "/outcomes", issues, scope);
    }

    private static void validateOutcomes(List<Outcome> outcomes, String base, List<Issue> issues, VariableScope scope) {
        for (Outcome outcome : outcomes) {
            String path = base + "/" + outcome.key();
            outcome.condition().ifPresent(condition -> validateCondition(condition, path + "/condition", scope, issues, true));
            for (OutcomeAction action : outcome.actions()) {
                String actionPath = path + "/actions/" + action.key();
                validateProvider(action.delay(), actionPath + "/delay", scope.full(), issues);
                validateProvider(action.repeat(), actionPath + "/repeat", scope.full(), issues);
            }
        }
    }

    private static void validateProvider(NumberProvider provider, String path, Set<String> allowed, List<Issue> issues) {
        if (provider instanceof Constant) {
            return;
        }
        if (provider instanceof Expression expression) {
            Set<String> params = expression.params().keySet();
            expression.params().forEach((name, param) ->
                    validateProvider(param, path + "/params/" + name, allowed, issues));
            for (String variable : FormulaVariables.find(expression.source())) {
                if (params.contains(variable)) {
                    continue;
                }
                if (!allowed.contains(variable)) {
                    issues.add(Issue.error(path, Component.translatable("spb.issue.unknown_variable", variable)));
                }
            }
            return;
        }
        if (provider instanceof Sum(List<NumberProvider> summands)) {
            summands.forEach(summand -> validateProvider(summand, path, allowed, issues));
        }
    }

    private static void validateCondition(EntityCondition condition, String path, VariableScope scope,
                                          List<Issue> issues, boolean rejectChance) {
        if (condition == null) {
            return;
        }
        Identifier type = SaturnRegistries.ENTITY_CONDITION_TYPE.getKey(condition.codec());
        if (!rejectChance && type.getPath().equals("chance")) {
            issues.add(Issue.warning(path, Component.translatable("spb.issue.random_condition")));
        }
        switch (condition) {
            case AndEntityCondition(List<EntityCondition> conditions1) -> {
                for (EntityCondition child : conditions1) {
                    validateCondition(child, path, scope, issues, rejectChance);
                }
            }
            case OrEntityCondition(List<EntityCondition> conditions) -> {
                for (EntityCondition child : conditions) {
                    validateCondition(child, path, scope, issues, rejectChance);
                }
            }
            case NotEntityCondition(EntityCondition condition1) ->
                    validateCondition(condition1, path, scope, issues, rejectChance);
            case CompareEntityCondition compare -> {
                validateProvider(compare.left(), path + "/left", scope.full(), issues);
                validateProvider(compare.right(), path + "/right", scope.full(), issues);
            }
            default -> {
            }
        }
    }
}
