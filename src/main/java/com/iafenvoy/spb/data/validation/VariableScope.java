package com.iafenvoy.spb.data.validation;

import com.iafenvoy.spb.data.Plan;
import com.iafenvoy.spb.data.resolution.ResolvedGroup;
import com.iafenvoy.spb.data.resolution.ResolvedOption;
import com.iafenvoy.spb.data.resolution.ResolvedPlan;
import com.iafenvoy.spb.util.DefinitionNames;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Variable names usable from a plan-owned definition, split by evaluation phase.
 */
record VariableScope(Set<String> change, Set<String> full) {
    public static VariableScope of(Plan plan, ResolvedPlan resolved) {
        Set<String> base = new LinkedHashSet<>();
        plan.inputs().keySet().forEach(key -> base.add(DefinitionNames.inputVariable(key)));
        plan.resources().forEach(binding -> base.add(DefinitionNames.baseVariable(binding.key())));
        for (ResolvedOption option : resolved.orderedOptions()) {
            base.add(DefinitionNames.selectedVariable(option.key()));
        }
        for (ResolvedGroup group : resolved.groups()) {
            base.add(DefinitionNames.groupDistinctVariable(group.key()));
            base.add(DefinitionNames.groupTotalVariable(group.key()));
        }
        Set<String> full = new LinkedHashSet<>(base);
        plan.resources().forEach(binding -> full.add(DefinitionNames.resourceVariable(binding.key())));
        return new VariableScope(Set.copyOf(base), Set.copyOf(full));
    }
}
