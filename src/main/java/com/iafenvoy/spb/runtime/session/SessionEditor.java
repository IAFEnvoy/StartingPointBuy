package com.iafenvoy.spb.runtime.session;

import com.iafenvoy.spb.data.Preset;
import com.iafenvoy.spb.data.resolution.ResolvedGroup;
import com.iafenvoy.spb.data.resolution.ResolvedOption;
import com.iafenvoy.spb.data.resolution.ResolvedPlan;
import com.iafenvoy.spb.network.SnapshotFactory;
import com.iafenvoy.spb.network.model.SelectionIntent;
import com.iafenvoy.spb.network.payload.PreviewS2CPayload;
import com.iafenvoy.spb.runtime.evaluation.EvaluationResult;
import com.iafenvoy.spb.runtime.evaluation.GroupView;
import com.iafenvoy.spb.runtime.evaluation.OptionView;
import com.iafenvoy.spb.runtime.evaluation.SelectionData;
import com.iafenvoy.spb.runtime.evaluation.SelectionEvaluator;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * An editable request's resolved plan and session, reused for validation and evaluation.
 */
record SessionEditor(ServerPlayer player, SelectionSession session, ResolvedPlan plan) {
    @Nullable
    static SessionEditor require(ServerPlayer player, SelectionIntent intent) {
        SelectionSession session = SessionService.requireSession(player, intent);
        if (session == null) return null;
        if (!session.state().isEditable() || !SessionService.isUncommitted(player, session)) {
            SessionSync.sendCurrent(player, session);
            return null;
        }
        if (intent.revision() != session.revision()) {
            SessionService.message(player, "spb.message.stale");
            SessionSync.sendCurrent(player, session);
            return null;
        }
        ResolvedPlan plan = SessionService.resolvePlan(player, session.planId());
        if (plan == null) return null;
        if (!plan.fingerprint().equals(session.fingerprint())) {
            SessionService.requireRefresh(player, session, "spb.message.refresh_required");
            return null;
        }
        return new SessionEditor(player, session, plan);
    }

    void setQuantity(SelectionIntent intent) {
        ResolvedOption option = this.findOption(intent);
        if (option == null) return;
        int quantity = intent.quantity();
        if (!this.validQuantity(option, quantity)) return;
        if (quantity > this.session.quantity(option.id())) {
            OptionView view = this.evaluate().options().get(option.id());
            if (view != null && !view.enabled()) {
                this.reject("spb.message.option_disabled", option.definition().name().getString());
                return;
            }
        }
        this.applySelections(this.withQuantity(option, quantity), null);
    }

    void applyPreset(SelectionIntent intent) {
        if (intent.preset().isEmpty()) return;
        String key = intent.preset().get();
        Optional<Preset> found = this.plan.preset(key);
        if (found.isEmpty()) {
            this.reject("spb.message.unknown_preset", key);
            return;
        }
        Preset preset = found.get();
        Object2IntMap<Identifier> candidate = new Object2IntOpenHashMap<>();
        for (Object2IntMap.Entry<Identifier> entry : preset.selections().object2IntEntrySet()) {
            Optional<ResolvedOption> option = this.plan.option(entry.getKey());
            if (option.isEmpty()) {
                this.reject("spb.message.unknown_option", entry.getKey().toString());
                return;
            }
            int quantity = entry.getIntValue();
            if (quantity <= 0) continue;
            if (!this.validQuantity(option.get(), quantity)) return;
            candidate.put(entry.getKey(), quantity);
        }
        EvaluationResult evaluation = this.evaluate(candidate);
        for (Identifier selected : candidate.keySet()) {
            OptionView view = evaluation.options().get(selected);
            if (view != null && !view.enabled()) {
                this.reject("spb.message.preset_unavailable", preset.name().getString());
                return;
            }
        }
        this.applySelections(candidate, key);
    }

    void reset() {
        this.applySelections(Object2IntMaps.emptyMap(), null);
    }

    void preview(SelectionIntent intent) {
        // A malformed hover request is ignored without sending a replacement screen snapshot.
        if (intent.option().isEmpty()) return;
        Optional<ResolvedOption> found = this.plan.option(intent.option().get());
        if (found.isEmpty()) return;
        ResolvedOption option = found.get();
        int quantity = Math.max(0, Math.min(intent.quantity(), option.definition().hardMax()));
        EvaluationResult evaluation = this.evaluate(this.withQuantity(option, quantity));
        PacketDistributor.sendToPlayer(this.player,
                new PreviewS2CPayload(SnapshotFactory.preview(evaluation, option.id(), quantity)));
    }

    EvaluationResult evaluate() {
        return this.evaluate(this.session.selections());
    }

    private EvaluationResult evaluate(Map<Identifier, Integer> selections) {
        return SelectionEvaluator.evaluate(this.plan, this.player,
                new SelectionData(this.session.inputs(), this.session.baseline(), selections));
    }

    /**
     * Re-evaluates and publishes a draft after creation, refresh or an accepted edit.
     */
    void publish() {
        EvaluationResult evaluation = this.evaluate();
        SessionResources.applyDraft(this.session, evaluation);
        SessionStorage.save(this.player);
        SessionSync.send(this.player, evaluation, this.session);
    }

    private void applySelections(Object2IntMap<Identifier> selections, @Nullable String presetKey) {
        this.session.setSelections(selections);
        this.session.setPresetKey(presetKey);
        SessionService.advanceRevision(this.player, this.session);
        this.publish();
    }

    /**
     * A candidate whose group limit is one replaces other choices. The candidate is evaluated first
     * so an option that increases its group's limit can coexist with the current selection.
     */
    private Object2IntMap<Identifier> withQuantity(ResolvedOption option, int quantity) {
        Object2IntMap<Identifier> selections = new Object2IntOpenHashMap<>(this.session.selections());
        if (quantity > 0) {
            selections.put(option.id(), quantity);
            if (this.session.quantity(option.id()) == 0) {
                this.plan.groupOf(option.id()).ifPresent(group -> this.replaceOtherChoices(group, option, selections));
            }
        } else {
            selections.removeInt(option.id());
        }
        return selections;
    }

    private boolean validQuantity(ResolvedOption option, int quantity) {
        int hardMax = option.definition().hardMax();
        if (quantity < 0 || quantity > hardMax) {
            this.reject("spb.message.invalid_quantity", quantity, hardMax);
            return false;
        }
        return true;
    }

    private void replaceOtherChoices(ResolvedGroup group, ResolvedOption option, Object2IntMap<Identifier> selections) {
        if (group.options().stream().noneMatch(member -> !member.id().equals(option.id()) && selections.getInt(member.id()) > 0))
            return;
        GroupView view = this.evaluate(selections).groups().stream()
                .filter(candidate -> candidate.id().equals(group.id())).findFirst().orElseThrow();
        if (view.max() == 1 && view.min() >= 0 && view.min() <= 1) {
            for (ResolvedOption member : group.options()) {
                if (!member.id().equals(option.id())) selections.removeInt(member.id());
            }
        }
    }

    @Nullable
    private ResolvedOption findOption(SelectionIntent intent) {
        if (intent.option().isEmpty()) return null;
        Optional<ResolvedOption> option = this.plan.option(intent.option().get());
        if (option.isEmpty()) this.reject("spb.message.unknown_option", intent.option().get().toString());
        return option.orElse(null);
    }

    private void reject(String key, Object... args) {
        SessionService.message(this.player, key, args);
        SessionSync.sendCurrent(this.player, this.session);
    }
}
