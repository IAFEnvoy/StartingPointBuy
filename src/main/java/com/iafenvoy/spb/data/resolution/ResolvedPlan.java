package com.iafenvoy.spb.data.resolution;

import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.spb.data.Plan;
import com.iafenvoy.spb.data.Preset;
import com.iafenvoy.spb.data.ResourceBinding;
import com.iafenvoy.spb.data.validation.Issue;
import com.iafenvoy.spb.util.DefinitionNames;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A plan with all group/option references resolved. Errors carry definition paths; the datapack
 * loader rejects them before world loading. Runtime evaluation also checks errors defensively.
 */
public record ResolvedPlan(Identifier id, Plan plan, Map<String, Holder<Resource>> resources,
                           List<ResolvedGroup> groups, Map<Identifier, ResolvedOption> options,
                           List<Preset> presets, List<Issue> issues, String fingerprint) {
    public List<ResolvedOption> orderedOptions() {
        List<ResolvedOption> result = new ArrayList<>();
        for (ResolvedGroup group : this.groups) {
            result.addAll(group.options());
        }
        return result;
    }

    public Optional<ResolvedOption> option(Identifier optionId) {
        return Optional.ofNullable(this.options.get(optionId));
    }

    public Optional<ResolvedGroup> group(Identifier groupId) {
        for (ResolvedGroup group : this.groups) {
            if (group.id().equals(groupId)) {
                return Optional.of(group);
            }
        }
        return Optional.empty();
    }

    public Optional<ResolvedGroup> groupOf(Identifier optionId) {
        ResolvedOption option = this.options.get(optionId);
        if (option == null || option.groupId() == null) {
            return Optional.empty();
        }
        return this.group(option.groupId());
    }

    public Optional<ResourceBinding> binding(Identifier resourceId) {
        for (ResourceBinding binding : this.plan.resources()) {
            if (binding.resourceId().equals(resourceId)) {
                return Optional.of(binding);
            }
        }
        return Optional.empty();
    }

    public Optional<ResourceBinding> bindingByKey(String key) {
        for (ResourceBinding binding : this.plan.resources()) {
            if (binding.key().equals(key)) {
                return Optional.of(binding);
            }
        }
        return Optional.empty();
    }

    public Optional<Preset> preset(String key) {
        for (int index = 0; index < this.presets.size(); index++) {
            if (DefinitionNames.presetKey(index).equals(key)) {
                return Optional.of(this.presets.get(index));
            }
        }
        return Optional.empty();
    }

    public List<Issue> errors() {
        return this.issues.stream().filter(Issue::isError).toList();
    }

    public boolean hasErrors() {
        return this.issues.stream().anyMatch(Issue::isError);
    }
}
