package com.iafenvoy.spb.runtime.session;

import com.iafenvoy.saturn.attachment.ResourceHolderAttachment;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.saturn.registry.SaturnAttachments;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.spb.StartingPointBuy;
import com.iafenvoy.spb.data.CommitMode;
import com.iafenvoy.spb.data.ResourceBinding;
import com.iafenvoy.spb.data.resolution.ResolvedPlan;
import com.iafenvoy.spb.runtime.evaluation.EvaluationResult;
import com.iafenvoy.spb.runtime.evaluation.ResourceView;
import com.iafenvoy.spb.runtime.evaluation.SelectionEvaluator;
import com.iafenvoy.spb.util.DefinitionNames;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Captures session resources and performs Saturn-backed draft, commit and rollback writes.
 */
final class SessionResources {
    private SessionResources() {
    }

    /**
     * Refresh reads real values for every binding and never replays an initial provider.
     */
    static Object2DoubleMap<Identifier> captureCurrent(ServerPlayer player, ResolvedPlan plan) {
        return captureReal(player, plan, false);
    }

    static Object2DoubleMap<Identifier> captureCommitSnapshot(ServerPlayer player, ResolvedPlan plan) {
        return captureReal(player, plan, true);
    }

    @Nullable
    static Object2DoubleMap<String> captureInputs(ServerPlayer player, ResolvedPlan plan) {
        Object2DoubleMap<String> inputs = new Object2DoubleOpenHashMap<>();
        if (plan.plan().inputs().isEmpty()) return inputs;
        FormulaContext context = FormulaContext.of(player);
        for (Map.Entry<String, NumberProvider> entry : plan.plan().inputs().entrySet()) {
            double value;
            try {
                value = entry.getValue().evaluate(context);
            } catch (RuntimeException exception) {
                SessionService.message(player, "spb.message.input_failed", entry.getKey(), String.valueOf(exception.getMessage()));
                return null;
            }
            if (!Double.isFinite(value)) {
                String error = "the formula produced the non-finite value " + value;
                StartingPointBuy.LOGGER.error("Failed to capture input '{}' of plan '{}': {}",
                        entry.getKey(), plan.id(), error);
                SessionService.message(player, "spb.message.input_failed", entry.getKey(), error);
                return null;
            }
            inputs.put(entry.getKey(), value);
        }
        return inputs;
    }

    @Nullable
    static Object2DoubleMap<Identifier> captureBaseline(ServerPlayer player, ResolvedPlan plan, Map<String, Double> inputs) {
        ResourceHolderAttachment real = player.getData(SaturnAttachments.RESOURCE_HOLDER);
        ResourceHolderAttachment baseline = new ResourceHolderAttachment();
        FormulaContext context = FormulaContext.of(player, buildInputVariables(inputs));
        for (ResourceBinding binding : plan.plan().resources()) {
            Holder<Resource> resource = plan.resources().get(binding.key());
            if (resource == null) {
                continue;
            }
            if (!real.contains(resource)) {
                try {
                    FormulaContext resourceContext = context.withResource(resource);
                    double min = resource.value().min().evaluate(resourceContext);
                    double max = resource.value().max().evaluate(resourceContext);
                    if (min > max) throw new IllegalArgumentException("Resource minimum exceeds maximum");
                    double value = resource.value().defaultValue().evaluate(resourceContext);
                    real.set(resource, Math.max(min, Math.min(max, value)), min, max, -1L, "spb_initialize");
                } catch (RuntimeException exception) {
                    SessionService.message(player, "spb.message.initial_failed", binding.resourceId().toString(), String.valueOf(exception.getMessage()));
                    return null;
                }
            }
            if (binding.initial().isPresent()) {
                double value;
                try {
                    FormulaContext context1 = context.withResource(resource);
                    value = binding.initial().get().evaluate(context1);
                } catch (RuntimeException exception) {
                    SessionService.message(player, "spb.message.initial_failed", binding.resourceId().toString(), String.valueOf(exception.getMessage()));
                    return null;
                }
                if (!Double.isFinite(value)) {
                    String error = "the formula produced the non-finite value " + value;
                    StartingPointBuy.LOGGER.error("Failed to initialise resource '{}' of plan '{}': {}",
                            binding.resourceId(), plan.id(), error);
                    SessionService.message(player, "spb.message.initial_failed", binding.resourceId().toString(), error);
                    return null;
                }
                baseline.set(resource, value);
            } else {
                baseline.set(resource, real.get(resource));
            }
        }
        return values(baseline);
    }

    private static Object2DoubleMap<Identifier> captureReal(ServerPlayer player, ResolvedPlan plan, boolean replaceOnly) {
        ResourceHolderAttachment real = player.getData(SaturnAttachments.RESOURCE_HOLDER);
        Object2DoubleMap<Identifier> snapshot = new Object2DoubleOpenHashMap<>();
        for (ResourceBinding binding : plan.plan().resources()) {
            if (replaceOnly && binding.commit() != CommitMode.REPLACE) {
                continue;
            }
            Holder<Resource> resource = plan.resources().get(binding.key());
            if (resource != null) {
                snapshot.put(binding.resourceId(), real.get(resource));
            }
        }
        return snapshot;
    }

    private static Map<String, Double> buildInputVariables(Map<String, Double> inputs) {
        Map<String, Double> variables = new LinkedHashMap<>();
        inputs.forEach((key, value) -> variables.put(DefinitionNames.inputVariable(key), value));
        return variables;
    }

    static void applyDraft(SelectionSession session, EvaluationResult evaluation) {
        if (evaluation.resources().size() != evaluation.plan().plan().resources().size()) {
            return;
        }
        if (!evaluation.resources().stream().allMatch(ResourceView::valid)) {
            return;
        }
        session.setDraft(materialise(evaluation.plan(), evaluatedValues(evaluation)));
    }

    static Object2DoubleMap<Identifier> materialise(ResolvedPlan plan, Object2DoubleMap<Identifier> values) {
        ResourceHolderAttachment holder = new ResourceHolderAttachment();
        for (ResourceBinding binding : plan.plan().resources()) {
            Holder<Resource> resource = plan.resources().get(binding.key());
            double value = values.getDouble(binding.resourceId());
            if (resource != null && values.containsKey(binding.resourceId()))
                holder.set(resource, value);
        }
        return values(holder);
    }

    @Nullable
    static String checkRealResources(ServerPlayer player, ResolvedPlan plan, SelectionSession session) {
        ResourceHolderAttachment real = player.getData(SaturnAttachments.RESOURCE_HOLDER);
        for (ResourceBinding binding : plan.plan().resources()) {
            if (binding.commit() != CommitMode.REPLACE) {
                continue;
            }
            Holder<Resource> resource = plan.resources().get(binding.key());
            if (resource == null) {
                return binding.resourceId().toString();
            }
            Object2DoubleMap<Identifier> snapshot = session.realSnapshot();
            if (!snapshot.containsKey(binding.resourceId()))
                return binding.resourceId().toString();
            double captured = snapshot.getDouble(binding.resourceId());
            double current = real.get(resource);
            double tolerance = binding.quantum() * SelectionEvaluator.EPSILON;
            if (Math.abs(current - captured) > tolerance) {
                return binding.resourceId().toString();
            }
        }
        return null;
    }

    static void restoreBaseline(ServerPlayer player, SelectionSession session) {
        ResolvedPlan plan = SessionService.resolvePlan(player, session.planId());
        if (plan == null) return;
        ResourceHolderAttachment real = player.getData(SaturnAttachments.RESOURCE_HOLDER);
        for (ResourceBinding binding : plan.plan().resources()) {
            if (binding.commit() != CommitMode.REPLACE) {
                continue;
            }
            Holder<Resource> resource = plan.resources().get(binding.key());
            Double baseline = session.baseline().get(binding.resourceId());
            if (resource != null && baseline != null) {
                real.set(resource, baseline);
            }
        }
    }

    /**
     * Validates every target before writing any real resource. Discard bindings stay untouched.
     */
    @Nullable
    static Object2DoubleMap<Identifier> commit(ServerPlayer player, EvaluationResult evaluation) {
        ResolvedPlan plan = evaluation.plan();
        Object2DoubleMap<Identifier> values = evaluatedValues(evaluation);
        for (ResourceBinding binding : plan.plan().resources()) {
            if (binding.commit() == CommitMode.REPLACE
                    && (!plan.resources().containsKey(binding.key()) || !values.containsKey(binding.resourceId()))) {
                SessionService.message(player, "spb.message.missing_resource", binding.resourceId().toString());
                return null;
            }
        }
        ResourceHolderAttachment real = player.getData(SaturnAttachments.RESOURCE_HOLDER);
        Object2DoubleMap<Identifier> committed = new Object2DoubleOpenHashMap<>();
        for (ResourceBinding binding : plan.plan().resources()) {
            if (binding.commit() != CommitMode.REPLACE) continue;
            double value = values.getDouble(binding.resourceId());
            real.set(plan.resources().get(binding.key()), value);
            committed.put(binding.resourceId(), value);
        }
        return committed;
    }

    static Object2DoubleMap<Identifier> evaluatedValues(EvaluationResult evaluation) {
        Object2DoubleMap<Identifier> values = new Object2DoubleOpenHashMap<>();
        evaluation.resources().forEach(view -> values.put(view.id(), view.value()));
        return values;
    }

    private static Object2DoubleMap<Identifier> values(ResourceHolderAttachment holder) {
        Object2DoubleMap<Identifier> result = new Object2DoubleOpenHashMap<>();
        for (Object2DoubleMap.Entry<Holder<Resource>> entry : holder.values().object2DoubleEntrySet()) {
            entry.getKey().unwrapKey().ifPresent(key -> result.put(key.identifier(), entry.getDoubleValue()));
        }
        return result;
    }
}
