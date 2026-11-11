package com.iafenvoy.spb.runtime.evaluation;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.spb.data.*;
import com.iafenvoy.spb.data.resolution.ResolvedGroup;
import com.iafenvoy.spb.data.resolution.ResolvedOption;
import com.iafenvoy.spb.data.resolution.ResolvedPlan;
import com.iafenvoy.spb.data.validation.Issue;
import com.iafenvoy.spb.util.DefinitionNames;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure re-evaluation of a complete choice set.
 *
 * <p>Fixed phases, so no evaluation cycle is possible:
 *
 * <pre>
 * inputs / baseline -&gt; quantities -&gt; group counts -&gt; per-option contributions
 *   -&gt; final resources -&gt; visibility / availability / ranges / rules -&gt; result
 * </pre>
 * <p>
 * Contribution formulas see inputs, baselines, quantities and group counts only. Everything else sees
 * the final resource variables as well. The same selection therefore always yields the same result,
 * independent of the click order or of previous balances.
 */
public final class SelectionEvaluator {
    public static final double EPSILON = 1.0E-8;
    public static final int MAX_TASKS = 1024;
    public static final int MAX_DELAY = 72_000;

    private SelectionEvaluator() {
    }

    public static EvaluationResult evaluate(ResolvedPlan plan, Player player, SelectionData data) {
        return evaluate(plan, player, player == null ? null : player.level().registryAccess(), data);
    }

    /**
     * Evaluation entry point that does not require a live player. Used by the self-test and by any
     * caller that only needs the numeric result; conditions that need an entity simply fail.
     */
    public static EvaluationResult evaluate(ResolvedPlan plan, @Nullable Player player,
                                            @Nullable HolderLookup.Provider access,
                                            SelectionData data) {
        List<Issue> issues = new ArrayList<>(plan.issues());
        String base = "plan/" + plan.id();

        Map<Identifier, Integer> quantities = new LinkedHashMap<>();
        for (ResolvedOption option : plan.orderedOptions()) {
            int raw = data.quantity(option.id());
            String path = base + "/options/" + option.id();
            if (raw < 0) {
                issues.add(Issue.error(path, Component.translatable("spb.issue.negative_quantity", raw)));
                raw = 0;
            }
            if (raw > option.definition().hardMax()) {
                issues.add(Issue.error(path, Component.translatable("spb.issue.hard_max_exceeded", raw, option.definition().hardMax())));
            }
            quantities.put(option.id(), raw);
        }

        Map<String, Double> baseVariables = buildBaseVariables(plan, data, quantities, issues, base);
        FormulaContext changeContext = context(player, access, baseVariables);
        Map<Identifier, Map<Identifier, Double>> perUnit = computeContributions(plan, changeContext, quantities, issues, base);
        Map<Identifier, Double> values = computeResources(plan, data, perUnit, quantities);

        Map<String, Double> fullVariables = new LinkedHashMap<>(baseVariables);
        for (ResourceBinding binding : plan.plan().resources()) {
            Double value = values.get(binding.resourceId());
            fullVariables.put(DefinitionNames.resourceVariable(binding.key()), value == null ? 0.0 : value);
        }
        FormulaContext fullContext = context(player, access, fullVariables);
        EvaluationDiagnostics sink = new EvaluationDiagnostics(player, fullContext, issues, base);

        List<RuleFailure> failures = new ArrayList<>();
        List<ResourceView> resources = evaluateResources(plan, data, values, fullContext, issues, failures, base);
        List<GroupView> groups = evaluateGroups(plan, fullContext, quantities, sink, failures, base);
        Map<Identifier, Boolean> groupVisible = new LinkedHashMap<>();
        for (GroupView group : groups) {
            groupVisible.put(group.id(), group.visible());
        }

        Map<Identifier, List<Component>> optionIssues = new LinkedHashMap<>();
        Map<Identifier, Boolean> optionVisible = new LinkedHashMap<>();
        Map<Identifier, Boolean> optionEnabled = new LinkedHashMap<>();
        Map<Identifier, Integer> optionMin = new LinkedHashMap<>();
        Map<Identifier, Integer> optionMax = new LinkedHashMap<>();
        evaluateOptions(plan, fullContext, quantities, sink, failures, base, groupVisible, optionIssues,
                optionVisible, optionEnabled, optionMin, optionMax);
        evaluateCompletionRules(plan.plan().completionRules(), sink, failures, base);

        List<OutcomePlan> outcomes = evaluateOutcomes(plan, fullContext, quantities, sink, issues, failures, base);
        Map<Identifier, List<Identifier>> optionAbilities = new LinkedHashMap<>();
        Map<Identifier, List<String>> optionActions = new LinkedHashMap<>();
        for (OutcomePlan outcome : outcomes) {
            if (outcome.scope().equals("plan")) {
                continue;
            }
            Identifier optionId = Identifier.tryParse(outcome.scope());
            if (optionId == null) {
                continue;
            }
            optionAbilities.computeIfAbsent(optionId, key -> new ArrayList<>()).addAll(outcome.abilities());
            optionActions.computeIfAbsent(optionId, key -> new ArrayList<>())
                    .addAll(outcome.actions().stream().map(ActionPlan::key).toList());
        }

        boolean configClean = issues.stream().noneMatch(Issue::isError);
        boolean canComplete = configClean && failures.isEmpty();

        List<OptionView> optionViews = new ArrayList<>();
        for (ResolvedOption option : plan.orderedOptions()) {
            int quantity = quantities.getOrDefault(option.id(), 0);
            List<Contribution> contributions = new ArrayList<>();
            Map<Identifier, Double> units = perUnit.getOrDefault(option.id(), Map.of());
            for (ResourceBinding binding : plan.plan().resources()) {
                double unit = units.getOrDefault(binding.resourceId(), 0.0);
                contributions.add(new Contribution(binding.resourceId(), binding.key(), unit, unit * quantity));
            }
            optionViews.add(new OptionView(option.id(), option, quantity,
                    optionVisible.getOrDefault(option.id(), true), optionEnabled.getOrDefault(option.id(), true),
                    optionMin.getOrDefault(option.id(), 0), optionMax.getOrDefault(option.id(), option.definition().hardMax()),
                    List.copyOf(optionIssues.getOrDefault(option.id(), List.of())), List.copyOf(contributions),
                    List.copyOf(optionAbilities.getOrDefault(option.id(), List.of())),
                    List.copyOf(optionActions.getOrDefault(option.id(), List.of()))));
        }
        Map<Identifier, OptionView> optionMap = new LinkedHashMap<>();
        optionViews.forEach(view -> optionMap.put(view.id(), view));

        return new EvaluationResult(plan, Map.copyOf(fullVariables), List.copyOf(resources), List.copyOf(groups),
                optionMap, List.copyOf(issues), List.copyOf(failures), List.copyOf(outcomes), canComplete);
    }

    private static Map<String, Double> buildBaseVariables(ResolvedPlan plan, SelectionData data,
                                                          Map<Identifier, Integer> quantities,
                                                          List<Issue> issues, String base) {
        Map<String, Double> variables = new LinkedHashMap<>();
        data.inputs().forEach((key, value) -> {
            if (value != null && Double.isFinite(value)) {
                variables.put(DefinitionNames.inputVariable(key), value);
            }
        });
        for (ResourceBinding binding : plan.plan().resources()) {
            Double baseline = data.base().get(binding.resourceId());
            if (baseline == null || !Double.isFinite(baseline)) {
                issues.add(Issue.error(base + "/resources/" + binding.key(),
                        Component.translatable("spb.issue.missing_baseline", binding.resourceId().toString())));
                baseline = 0.0;
            }
            variables.put(DefinitionNames.baseVariable(binding.key()), baseline);
        }
        for (ResolvedOption option : plan.orderedOptions()) {
            variables.put(DefinitionNames.selectedVariable(option.key()),
                    (double) quantities.getOrDefault(option.id(), 0));
        }
        for (ResolvedGroup group : plan.groups()) {
            int distinct = 0;
            int total = 0;
            for (ResolvedOption option : group.options()) {
                int quantity = quantities.getOrDefault(option.id(), 0);
                if (quantity > 0) {
                    distinct++;
                }
                total += quantity;
            }
            variables.put(DefinitionNames.groupDistinctVariable(group.key()), (double) distinct);
            variables.put(DefinitionNames.groupTotalVariable(group.key()), (double) total);
        }
        return variables;
    }

    private static Map<Identifier, Map<Identifier, Double>> computeContributions(ResolvedPlan plan, FormulaContext context,
                                                                                 Map<Identifier, Integer> quantities,
                                                                                 List<Issue> issues, String base) {
        Map<Identifier, Map<Identifier, Double>> perUnit = new LinkedHashMap<>();
        for (ResolvedOption option : plan.orderedOptions()) {
            boolean selected = quantities.getOrDefault(option.id(), 0) > 0;
            Map<Identifier, Double> contributions = new LinkedHashMap<>();
            option.definition().resourceChanges().forEach((holder, provider) -> {
                Identifier resource = holder.unwrapKey().orElseThrow().identifier();
                String path = base + "/options/" + option.id() + "/resource_changes/" + resource;
                double value;
                try {
                    value = provider.evaluate(context);
                } catch (RuntimeException exception) {
                    value = Double.NaN;
                    if (selected)
                        issues.add(Issue.error(path, Component.literal("Formula failed: " + exception.getMessage())));
                }
                // Prices of unselected options are display-only; only a selected option's broken
                // contribution blocks the session.
                if (!Double.isFinite(value) && selected) {
                    issues.add(Issue.error(path, Component.literal("number provider produced the non-finite value " + value)));
                }
                contributions.put(resource, value);
            });
            perUnit.put(option.id(), contributions);
        }
        return perUnit;
    }

    private static Map<Identifier, Double> computeResources(ResolvedPlan plan, SelectionData data,
                                                            Map<Identifier, Map<Identifier, Double>> perUnit,
                                                            Map<Identifier, Integer> quantities) {
        Map<Identifier, Double> values = new LinkedHashMap<>();
        for (ResourceBinding binding : plan.plan().resources()) {
            double value = data.base().getOrDefault(binding.resourceId(), 0.0);
            for (ResolvedOption option : plan.orderedOptions()) {
                int quantity = quantities.getOrDefault(option.id(), 0);
                if (quantity > 0) {
                    value += perUnit.getOrDefault(option.id(), Map.of()).getOrDefault(binding.resourceId(), 0.0) * quantity;
                }
            }
            values.put(binding.resourceId(), value);
        }
        return values;
    }

    private static List<ResourceView> evaluateResources(ResolvedPlan plan, SelectionData data,
                                                        Map<Identifier, Double> values, FormulaContext context,
                                                        List<Issue> issues, List<RuleFailure> failures,
                                                        String base) {
        List<ResourceView> views = new ArrayList<>();
        for (ResourceBinding binding : plan.plan().resources()) {
            Holder<Resource> holder = plan.resources().get(binding.key());
            if (holder == null) {
                continue;
            }
            double value = values.getOrDefault(binding.resourceId(), 0.0);
            double baseline = data.base().getOrDefault(binding.resourceId(), 0.0);
            double quantum = binding.quantum();
            String path = base + "/resources/" + binding.key();
            double min;
            double max;
            try {
                FormulaContext resourceContext = context.withResource(holder);
                min = holder.value().min().evaluate(resourceContext);
                max = holder.value().max().evaluate(resourceContext);
                if (min > max) throw new IllegalArgumentException("Resource minimum exceeds maximum");
            } catch (RuntimeException exception) {
                issues.add(Issue.error(path, Component.literal("Resource bounds failed: " + exception.getMessage())));
                continue;
            }
            double tolerance = quantum * EPSILON;
            boolean boundsOk = value >= min - tolerance && value <= max + tolerance;
            boolean quantumOk = aligned(value, quantum) && aligned(baseline, quantum);
            Component name = binding.displayName();
            if (!boundsOk) {
                failures.add(new RuleFailure(path,
                        Component.translatable("spb.rule.resource_bounds", name, value, min, max)));
            }
            if (!quantumOk) {
                failures.add(new RuleFailure(path,
                        Component.translatable("spb.rule.resource_quantum", name, value, quantum)));
            }
            views.add(new ResourceView(binding.resourceId(), binding.key(), name, baseline, value,
                    min, max, quantum, binding.display(), boundsOk, quantumOk));
        }
        return views;
    }

    private static List<GroupView> evaluateGroups(ResolvedPlan plan, FormulaContext context,
                                                  Map<Identifier, Integer> quantities, EvaluationDiagnostics sink,
                                                  List<RuleFailure> failures, String base) {
        List<GroupView> views = new ArrayList<>();
        for (ResolvedGroup group : plan.groups()) {
            Group definition = group.definition();
            String path = base + "/groups/" + group.id();
            int capacity = capacity(group);
            int min = ceil(definition.minCount(), sink, path + "/min_count", 0);
            int max = definition.maxCount().isPresent()
                    ? floor(definition.maxCount().get(), sink, path + "/max_count", capacity)
                    : capacity;
            if (max > capacity) {
                max = capacity;
            }
            int count = count(group, quantities);
            List<Component> groupIssues = new ArrayList<>();
            if (min < 0 || max < 0 || min > max) {
                sink.enter(path);
                sink.error("invalid group range: min=" + min + ", max=" + max);
                groupIssues.add(Component.translatable("spb.issue.group_range", min, max));
            } else {
                if (count < min) {
                    groupIssues.add(Component.translatable("spb.group.too_few", definition.title(), min, count));
                }
                if (count > max) {
                    groupIssues.add(Component.translatable("spb.group.too_many", definition.title(), max, count));
                }
            }
            boolean visible = test(definition.visibleCondition(), sink, path + "/visible_condition");
            for (Component component : groupIssues) {
                failures.add(new RuleFailure(path, component));
            }
            views.add(new GroupView(group.id(), definition, count, min, max, visible, List.copyOf(groupIssues)));
        }
        return views;
    }

    private static void evaluateOptions(ResolvedPlan plan, FormulaContext context, Map<Identifier, Integer> quantities,
                                        EvaluationDiagnostics sink, List<RuleFailure> failures, String base,
                                        Map<Identifier, Boolean> groupVisible,
                                        Map<Identifier, List<Component>> optionIssues,
                                        Map<Identifier, Boolean> optionVisible, Map<Identifier, Boolean> optionEnabled,
                                        Map<Identifier, Integer> optionMin, Map<Identifier, Integer> optionMax) {
        for (ResolvedOption option : plan.orderedOptions()) {
            String path = base + "/options/" + option.id();
            int quantity = quantities.getOrDefault(option.id(), 0);
            int hardMax = option.definition().hardMax();
            int min = ceil(option.definition().minCount(), sink, path + "/min_count", 0);
            int max = option.definition().maxCount().isPresent()
                    ? floor(option.definition().maxCount().get(), sink, path + "/max_count", hardMax)
                    : hardMax;
            int effectiveMax = Math.min(hardMax, Math.max(0, max));
            if (max < 0) {
                sink.enter(path + "/max_count");
                sink.error("invalid option max count: " + max);
            }
            boolean ownVisible = test(option.definition().visibleCondition(), sink, path + "/visible_condition");
            boolean groupIsVisible = groupVisible.getOrDefault(option.groupId(), true);
            boolean visible = ownVisible && groupIsVisible;
            boolean enabled = test(option.definition().enabledCondition(), sink, path + "/enabled_condition");

            List<Component> found = new ArrayList<>();
            if (quantity > 0) {
                if (quantity < min) {
                    found.add(Component.translatable("spb.option.too_few", option.definition().name(), min, quantity));
                }
                if (quantity > effectiveMax) {
                    found.add(Component.translatable("spb.option.too_many", option.definition().name(), effectiveMax, quantity));
                }
                if (!visible) {
                    found.add(Component.translatable("spb.option.selected_hidden", option.definition().name()));
                }
                if (!enabled) {
                    found.add(Component.translatable("spb.option.selected_disabled", option.definition().name()));
                }
                for (ValidationRule rule : option.definition().validationRules()) {
                    if (!test(rule.condition(), sink, path + "/validation_rules/" + rule.key())) {
                        found.add(rule.display());
                    }
                }
            }
            for (Component component : found) {
                failures.add(new RuleFailure(path, component));
            }
            optionIssues.put(option.id(), List.copyOf(found));
            optionVisible.put(option.id(), visible);
            optionEnabled.put(option.id(), enabled);
            optionMin.put(option.id(), min);
            optionMax.put(option.id(), effectiveMax);
        }
    }

    private static void evaluateCompletionRules(List<CompletionRule> rules, EvaluationDiagnostics sink,
                                                List<RuleFailure> failures, String base) {
        for (CompletionRule rule : rules) {
            String path = base + "/completion_rules/" + rule.key();
            if (!test(rule.condition(), sink, path)) {
                failures.add(new RuleFailure(path, rule.display()));
            }
        }
    }

    private static List<OutcomePlan> evaluateOutcomes(ResolvedPlan plan, FormulaContext context,
                                                      Map<Identifier, Integer> quantities, EvaluationDiagnostics sink,
                                                      List<Issue> issues, List<RuleFailure> failures,
                                                      String base) {
        List<OutcomePlan> result = new ArrayList<>();
        int[] taskBudget = {MAX_TASKS};
        for (Outcome outcome : plan.plan().outcomes()) {
            evaluateOutcome("plan", base + "/outcomes/" + outcome.key(), outcome, sink, issues, result, taskBudget);
        }
        for (ResolvedOption option : plan.orderedOptions()) {
            if (quantities.getOrDefault(option.id(), 0) <= 0) {
                continue;
            }
            for (Outcome outcome : option.definition().outcomes()) {
                evaluateOutcome(option.id().toString(), base + "/options/" + option.id() + "/outcomes/" + outcome.key(),
                        outcome, sink, issues, result, taskBudget);
            }
        }
        return result;
    }

    private static void evaluateOutcome(String scope, String path, Outcome outcome, EvaluationDiagnostics sink, List<Issue> issues,
                                        List<OutcomePlan> result, int[] taskBudget) {
        boolean active = outcome.condition().map(condition -> test(condition, sink, path + "/condition")).orElse(true);
        if (!active) {
            return;
        }
        List<ActionPlan> actions = new ArrayList<>();
        for (OutcomeAction action : outcome.actions()) {
            String actionPath = path + "/actions/" + action.key();
            int delay = ceil(action.delay(), sink, actionPath + "/delay", 1);
            if (delay < 1) {
                delay = 1;
            }
            if (delay > MAX_DELAY) {
                sink.enter(actionPath + "/delay");
                sink.error("delay exceeds the limit of " + MAX_DELAY + " ticks");
                delay = MAX_DELAY;
            }
            int repeat = floor(action.repeat(), sink, actionPath + "/repeat", 0);
            if (repeat < 0) {
                sink.enter(actionPath + "/repeat");
                sink.error("repeat must be non-negative, got " + repeat);
                repeat = 0;
            }
            if (repeat > MAX_TASKS) {
                sink.enter(actionPath + "/repeat");
                sink.error("repeat exceeds the limit of " + MAX_TASKS);
                repeat = MAX_TASKS;
            }
            taskBudget[0] -= repeat;
            if (taskBudget[0] < 0) {
                sink.enter(actionPath);
                sink.error("more than " + MAX_TASKS + " tasks would be scheduled");
            }
            actions.add(new ActionPlan(action.key(), delay, repeat, action.action()));
        }
        result.add(new OutcomePlan(scope, outcome, outcome.abilities().stream()
                .map(holder -> holder.unwrapKey().orElseThrow().identifier()).toList(), List.copyOf(actions)));
    }

    private static FormulaContext context(Player player, HolderLookup.Provider access,
                                          Map<String, Double> variables) {
        if (player != null) {
            return FormulaContext.of(player, variables);
        }
        return new FormulaContext(new LinkedHashMap<>(variables), RandomSource.create(),
                access, null, null, null, null, false);
    }

    private static boolean test(EntityCondition condition, EvaluationDiagnostics sink, String path) {
        sink.enter(path);
        EntityConditionContext conditionContext = new EntityConditionContext(sink.player(), sink.context());
        conditionContext.set(ContextKeys.EVAL_SINK, sink);
        try {
            return condition.test(conditionContext);
        } catch (RuntimeException exception) {
            sink.error("condition failed: " + exception.getMessage());
            return false;
        }
    }

    private static int ceil(NumberProvider provider, EvaluationDiagnostics sink, String path, int fallback) {
        sink.enter(path);
        double value = sink.number(provider, path);
        if (!Double.isFinite(value)) {
            sink.error("number provider produced the non-finite value " + value);
            return fallback;
        }
        return (int) Math.ceil(value);
    }

    private static int floor(NumberProvider provider, EvaluationDiagnostics sink, String path, int fallback) {
        sink.enter(path);
        double value = sink.number(provider, path);
        if (!Double.isFinite(value)) {
            sink.error("number provider produced the non-finite value " + value);
            return fallback;
        }
        return (int) Math.floor(value);
    }

    private static int capacity(ResolvedGroup group) {
        if (group.definition().countMode() == CountMode.TOTAL) {
            int capacity = 0;
            for (ResolvedOption option : group.options()) {
                capacity += option.definition().hardMax();
            }
            return capacity;
        }
        return group.options().size();
    }

    private static int count(ResolvedGroup group, Map<Identifier, Integer> quantities) {
        int count = 0;
        for (ResolvedOption option : group.options()) {
            int quantity = quantities.getOrDefault(option.id(), 0);
            count += group.definition().countMode() == CountMode.TOTAL
                    ? Math.max(0, quantity)
                    : quantity > 0 ? 1 : 0;
        }
        return count;
    }

    private static boolean aligned(double value, double quantum) {
        if (!Double.isFinite(value) || !Double.isFinite(quantum) || quantum <= 0.0) {
            return false;
        }
        double units = value / quantum;
        return Math.abs(units - Math.rint(units)) <= EPSILON;
    }
}
