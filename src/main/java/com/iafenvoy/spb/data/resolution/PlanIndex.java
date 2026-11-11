package com.iafenvoy.spb.data.resolution;

import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.spb.data.*;
import com.iafenvoy.spb.data.validation.Issue;
import com.iafenvoy.spb.data.validation.PlanValidator;
import com.iafenvoy.spb.registry.SpbResourceKeys;
import com.iafenvoy.spb.util.DefinitionNames;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.*;

/**
 * Resolves a plan against frozen datapack registries. Codec checks handle local constraints; this
 * class checks ownership, bindings and presets across definitions. Load-time errors are collected
 * with paths and rejected by DatapackValidator before a world can be opened.
 */
public final class PlanIndex {
    private PlanIndex() {
    }

    public static List<ResolvedPlan> all(HolderLookup.Provider access) {
        return access.lookupOrThrow(SpbResourceKeys.PLAN).listElements()
                .map(holder -> resolve(access, holder))
                .sorted(Comparator.comparing(plan -> plan.id().toString()))
                .toList();
    }

    public static Optional<ResolvedPlan> find(HolderLookup.Provider access, Identifier id) {
        return access.lookupOrThrow(SpbResourceKeys.PLAN).get(ResourceKey.create(SpbResourceKeys.PLAN, id))
                .map(holder -> resolve(access, holder));
    }

    public static ResolvedPlan resolve(HolderLookup.Provider access, Holder<Plan> plan) {
        return resolve(access, plan.unwrapKey().orElseThrow().identifier(), plan.value());
    }

    public static ResolvedPlan resolve(HolderLookup.Provider access, Identifier id, Plan plan) {
        List<Issue> issues = new ArrayList<>();
        String base = "plan/" + id;
        Map<String, Holder<Resource>> resources = new LinkedHashMap<>();
        plan.resources().forEach(binding -> resources.put(binding.key(), binding.resource()));
        GroupResolution result = resolveGroups(access, plan, base, issues);
        List<ResolvedGroup> groups = result.groups();
        Map<Identifier, ResolvedOption> options = result.options();

        resolvePresets(plan, options, base, issues);
        resolveResourceChanges(plan, options, base, issues);

        if (plan.inputs().isEmpty() && plan.resources().isEmpty()) {
            issues.add(Issue.warning(base, Component.translatable("spb.issue.no_resources")));
        }
        if (groups.isEmpty()) {
            issues.add(Issue.warning(base + "/groups", Component.translatable("spb.issue.no_groups")));
        }

        List<ResolvedOption> ordered = new ArrayList<>();
        groups.forEach(group -> ordered.addAll(group.options()));
        ResolvedPlan draft = new ResolvedPlan(id, plan, Map.copyOf(resources), List.copyOf(groups), Map.copyOf(options),
                List.copyOf(plan.presets()), issues, "");
        PlanValidator.validate(draft, issues);
        String fingerprint = Fingerprint.of(access, id, plan, resources, groups, ordered);
        return new ResolvedPlan(id, plan, Map.copyOf(resources), List.copyOf(groups), Map.copyOf(options),
                List.copyOf(plan.presets()), List.copyOf(issues), fingerprint);
    }

    private static GroupResolution resolveGroups(HolderLookup.Provider access, Plan plan, String base, List<Issue> issues) {
        List<ResolvedGroup> groups = new ArrayList<>();
        Map<Identifier, ResolvedOption> options = new LinkedHashMap<>();
        Map<String, Identifier> groupKeys = new LinkedHashMap<>();
        Map<String, Identifier> optionKeys = new LinkedHashMap<>();

        for (Holder.Reference<Group> groupHolder : expand(access, SpbResourceKeys.GROUP, plan.groups(), issues, base + "/groups")) {
            Identifier groupId = groupHolder.key().identifier();
            Group group = groupHolder.value();
            String path = base + "/groups/" + groupId;
            if (!addAlias(groupKeys, groupId, path, issues)) continue;

            List<ResolvedOption> groupOptions = new ArrayList<>();
            for (Holder.Reference<Option> optionHolder : resolveOptions(access, groupId, group, issues)) {
                Identifier optionId = optionHolder.key().identifier();
                Option option = optionHolder.value();
                String optionPath = path + "/options/" + optionId;
                if (options.containsKey(optionId)) {
                    issues.add(Issue.error(optionPath, Component.translatable("spb.issue.option_owned_twice", optionId.toString())));
                    continue;
                }
                if (!addAlias(optionKeys, optionId, optionPath, issues)) continue;
                ResolvedOption resolved = new ResolvedOption(optionId, option, groupId);
                groupOptions.add(resolved);
                options.put(optionId, resolved);
            }
            if (groupOptions.isEmpty()) {
                issues.add(Issue.warning(path, Component.translatable("spb.issue.empty_group")));
            }
            groups.add(new ResolvedGroup(groupId, group, List.copyOf(groupOptions)));
        }
        return new GroupResolution(groups, options);
    }

    public static List<Holder.Reference<Option>> resolveOptions(HolderLookup.Provider access, Identifier id,
                                                                Group group, List<Issue> issues) {
        String path = "group/" + id + "/options";
        List<Holder.Reference<Option>> options = expand(access, SpbResourceKeys.OPTION, group.options(), issues, path);
        Map<String, Identifier> keys = new LinkedHashMap<>();
        for (Holder.Reference<Option> option : options) {
            addAlias(keys, option.key().identifier(), path + "/" + option.key().identifier(), issues);
        }
        return options;
    }

    private static void resolvePresets(Plan plan, Map<Identifier, ResolvedOption> options,
                                       String base, List<Issue> issues) {
        for (int index = 0; index < plan.presets().size(); index++) {
            Preset preset = plan.presets().get(index);
            String path = base + "/presets/" + DefinitionNames.presetKey(index);
            preset.selections().forEach((optionId, quantity) -> {
                ResolvedOption option = options.get(optionId);
                if (option == null) {
                    issues.add(Issue.error(path + "/selections/" + optionId,
                            Component.translatable("spb.issue.preset_unknown_option", optionId.toString())));
                    return;
                }
                if (quantity < 0 || quantity > option.definition().hardMax()) {
                    issues.add(Issue.error(path + "/selections/" + optionId,
                            Component.translatable("spb.issue.preset_quantity", quantity, option.definition().hardMax())));
                }
            });
        }
    }

    private static boolean addAlias(Map<String, Identifier> aliases, Identifier id, String path, List<Issue> issues) {
        String alias = DefinitionNames.key(id);
        Identifier previous = aliases.putIfAbsent(alias, id);
        if (previous == null) return true;
        issues.add(Issue.error(path, Component.translatable("spb.issue.alias_collision", alias, previous.toString(), id.toString())));
        return false;
    }

    private static void resolveResourceChanges(Plan plan, Map<Identifier, ResolvedOption> options,
                                               String base, List<Issue> issues) {
        Set<Holder<Resource>> bound = new LinkedHashSet<>();
        plan.resources().forEach(binding -> bound.add(binding.resource()));
        for (ResolvedOption option : options.values()) {
            option.definition().resourceChanges().keySet().forEach(resource -> {
                if (!bound.contains(resource)) {
                    issues.add(Issue.error(base + "/options/" + option.id() + "/resource_changes/" + resource.unwrapKey().orElseThrow().identifier(),
                            Component.translatable("spb.issue.unbound_resource", resource.unwrapKey().orElseThrow().identifier().toString())));
                }
            });
        }
    }

    private static <T> List<Holder.Reference<T>> expand(HolderLookup.Provider access, ResourceKey<? extends Registry<T>> key,
                                                        List<EntryRef<T>> refs, List<Issue> issues, String path) {
        HolderLookup.RegistryLookup<T> lookup = access.lookupOrThrow(key);
        List<Holder.Reference<T>> result = new ArrayList<>();
        Set<ResourceKey<T>> seen = new LinkedHashSet<>();
        for (int index = 0; index < refs.size(); index++) {
            EntryRef<T> ref = refs.get(index);
            String entryPath = path + "[" + index + "]";
            if (ref.tag()) {
                Optional<HolderSet.Named<T>> tag = lookup.get(ref.value().right().orElseThrow());
                if (tag.isEmpty()) {
                    issues.add(Issue.error(entryPath, Component.translatable("spb.issue.tag_missing", ref.serialize())));
                    continue;
                }
                List<Holder.Reference<T>> members = tag.get().stream()
                        .filter(Holder.Reference.class::isInstance)
                        .map(holder -> (Holder.Reference<T>) holder)
                        .sorted(Comparator.comparing(holder -> holder.key().identifier()))
                        .toList();
                for (Holder.Reference<T> member : members) {
                    if (seen.add(member.key())) {
                        result.add(member);
                    }
                }
            } else {
                Optional<Holder<T>> holder = ref.value().left().filter(Holder::isBound);
                if (holder.isEmpty()) {
                    issues.add(Issue.error(entryPath, Component.translatable("spb.issue.missing_entry", ref.id().toString())));
                    continue;
                }
                if (seen.add(holder.get().unwrapKey().orElseThrow())) {
                    result.add((Holder.Reference<T>) holder.get());
                }
            }
        }
        return result;
    }
}
