package com.iafenvoy.spb.network;

import com.iafenvoy.spb.data.*;
import com.iafenvoy.spb.data.resolution.ResolvedGroup;
import com.iafenvoy.spb.data.resolution.ResolvedOption;
import com.iafenvoy.spb.data.resolution.ResolvedPlan;
import com.iafenvoy.spb.data.validation.Issue;
import com.iafenvoy.spb.network.model.*;
import com.iafenvoy.spb.runtime.evaluation.*;
import com.iafenvoy.spb.runtime.session.SelectionSession;
import com.iafenvoy.spb.util.DefinitionNames;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds client snapshots from server-side evaluation results. All numbers, rule outcomes and
 * visibility flags come from {@link EvaluationResult}; the client never recomputes them.
 */
public final class SnapshotFactory {
    private SnapshotFactory() {
    }

    public static SelectionSnapshot build(EvaluationResult result, SelectionSession session) {
        ResolvedPlan plan = result.plan();
        Plan definition = plan.plan();
        List<ResourceLine> resources = new ArrayList<>();
        for (ResourceView view : result.resources()) {
            resources.add(view.line());
        }

        List<PresetLine> presets = new ArrayList<>();
        String matched = "";
        for (int index = 0; index < plan.presets().size(); index++) {
            Preset preset = plan.presets().get(index);
            String key = DefinitionNames.presetKey(index);
            boolean matches = matches(preset, session.nonZeroSelections());
            if (matches && matched.isEmpty()) {
                matched = key;
            }
            presets.add(presetLine(plan, preset, key, matches));
        }

        List<GroupLine> groups = new ArrayList<>();
        for (GroupView view : result.groups()) {
            groups.add(new GroupLine(view.key(), view.definition().title(),
                    view.definition().description(),
                    view.definition().countMode().getSerializedName(), view.count(), view.min(), view.max(), view.visible()));
        }

        List<OptionLine> options = new ArrayList<>();
        for (ResolvedOption option : plan.orderedOptions()) {
            OptionView view = result.options().get(option.id());
            if (view == null) {
                continue;
            }
            List<ContributionLine> contributions = new ArrayList<>();
            for (Contribution contribution : view.contributions()) {
                contributions.add(new ContributionLine(contribution.key(), contribution.perUnit(), contribution.total()));
            }
            options.add(new OptionLine(option.id(), groupKey(plan, option.id()),
                    option.key(), option.definition().name(), option.definition().description(),
                    option.definition().icon(), view.quantity(), option.definition().hardMax(), view.minCount(), view.maxCount(),
                    view.visible(), view.enabled(), view.repeatable(), view.issues(), contributions, effectLines(view)));
        }

        List<Component> failures = result.failures().stream().map(RuleFailure::message).toList();
        List<Component> issues = result.issues().stream().map(Issue::display).toList();
        UiSettings ui = definition.ui();
        boolean editable = session.state().isEditable();
        return new SelectionSnapshot(plan.id(), session.id(), session.revision(), session.state().getSerializedName(),
                editable, editable && result.canComplete(), matched, definition.title(), definition.description(),
                List.copyOf(resources), List.copyOf(presets), List.copyOf(groups), List.copyOf(options),
                List.copyOf(failures), List.copyOf(issues), ui);
    }

    /**
     * Terminal snapshot for completed or locked sessions. It shows the committed resources and the
     * frozen selection but nothing is editable.
     */
    public static SelectionSnapshot buildTerminal(ResolvedPlan plan, SelectionSession session) {
        Plan definition = plan.plan();
        List<ResourceLine> resources = new ArrayList<>();
        for (ResourceBinding binding : definition.resources()) {
            double value = terminalResourceValue(session, binding.resourceId());
            resources.add(new ResourceLine(binding.key(), binding.displayName(), value, value, value, value,
                    binding.display(), true));
        }
        List<PresetLine> presets = new ArrayList<>();
        String matched = "";
        for (int index = 0; index < plan.presets().size(); index++) {
            Preset preset = plan.presets().get(index);
            String key = DefinitionNames.presetKey(index);
            boolean matches = matches(preset, session.nonZeroSelections());
            if (matches && matched.isEmpty()) {
                matched = key;
            }
            presets.add(presetLine(plan, preset, key, matches));
        }
        List<GroupLine> groups = new ArrayList<>();
        for (ResolvedGroup group : plan.groups()) {
            int count = 0;
            for (ResolvedOption option : group.options()) {
                int quantity = session.quantity(option.id());
                count += group.definition().countMode() == CountMode.TOTAL
                        ? Math.max(0, quantity)
                        : quantity > 0 ? 1 : 0;
            }
            groups.add(new GroupLine(group.key(), group.definition().title(),
                    group.definition().description(),
                    group.definition().countMode().getSerializedName(), count, 0, count, true));
        }
        List<OptionLine> options = new ArrayList<>();
        for (ResolvedOption option : plan.orderedOptions()) {
            List<Component> effects = new ArrayList<>();
            for (Outcome outcome : option.definition().outcomes()) {
                outcome.abilities().forEach(ability -> effects.add(Component.translatable("spb.effect.ability", ability.unwrapKey().orElseThrow().identifier().toString())));
                for (OutcomeAction action : outcome.actions()) {
                    effects.add(Component.translatable("spb.effect.action", action.key()));
                }
            }
            options.add(new OptionLine(option.id(), groupKey(plan, option.id()),
                    option.key(), option.definition().name(), option.definition().description(),
                    option.definition().icon(), session.quantity(option.id()), option.definition().hardMax(),
                    0, option.definition().hardMax(), true, false, false, List.of(), List.of(), List.copyOf(effects)));
        }
        UiSettings ui = definition.ui();
        return new SelectionSnapshot(plan.id(), session.id(), session.revision(), session.state().getSerializedName(),
                false, false, matched, definition.title(), definition.description(), List.copyOf(resources),
                List.copyOf(presets), List.copyOf(groups), List.copyOf(options), List.of(), List.of(), ui);
    }

    public static SelectionPreview preview(EvaluationResult result, Identifier option, int quantity) {
        List<ResourceLine> resources = new ArrayList<>();
        for (ResourceView view : result.resources()) {
            resources.add(new ResourceLine(view.key(), view.name(), view.base(), view.value(),
                    view.min(), view.max(), view.display(), view.valid()));
        }
        List<Component> failures = result.failures().stream().map(RuleFailure::message).toList();
        return new SelectionPreview(option, quantity, result.canComplete(), List.copyOf(resources), List.copyOf(failures));
    }

    private static PresetLine presetLine(ResolvedPlan plan, Preset preset, String key, boolean matches) {
        return new PresetLine(key, preset.name(), preset.description(), preset.icon(), matches,
                presetEntries(plan, preset));
    }

    private static List<Component> presetEntries(ResolvedPlan plan, Preset preset) {
        List<Component> entries = new ArrayList<>();
        for (ResolvedOption option : plan.orderedOptions()) {
            int quantity = preset.selections().getOrDefault(option.id(), 0);
            if (quantity <= 0) {
                continue;
            }
            Component name = option.definition().name();
            entries.add(quantity == 1
                    ? name
                    : Component.empty().append(name).append(Component.literal(" ×" + quantity)));
        }
        return List.copyOf(entries);
    }

    private static String groupKey(ResolvedPlan plan, Identifier option) {
        return plan.groupOf(option).map(ResolvedGroup::key).orElse("");
    }

    private static List<Component> effectLines(OptionView view) {
        List<Component> effects = new ArrayList<>();
        for (Identifier ability : view.abilities()) {
            effects.add(Component.translatable("spb.effect.ability", ability.toString()));
        }
        for (String action : view.actionKeys()) {
            effects.add(Component.translatable("spb.effect.action", action));
        }
        return effects;
    }

    public static boolean matches(Preset preset, Map<Identifier, Integer> selections) {
        Map<Identifier, Integer> expected = new LinkedHashMap<>();
        for (Object2IntMap.Entry<Identifier> entry : preset.selections().object2IntEntrySet()) {
            int quantity = entry.getIntValue();
            if (quantity > 0) {
                expected.put(entry.getKey(), quantity);
            }
        }
        return expected.equals(selections);
    }

    private static double terminalResourceValue(SelectionSession session, Identifier resource) {
        if (session.committed().containsKey(resource)) {
            return session.committed().getDouble(resource);
        }
        if (session.draft().containsKey(resource)) {
            return session.draft().getDouble(resource);
        }
        return session.baseline().getOrDefault(resource, 0.0);
    }
}
