package com.iafenvoy.spb.runtime.session;

import com.iafenvoy.saturn.runtime.ability.AbilityGrantService;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.spb.StartingPointBuy;
import com.iafenvoy.spb.data.Plan;
import com.iafenvoy.spb.data.resolution.PlanIndex;
import com.iafenvoy.spb.data.resolution.ResolvedPlan;
import com.iafenvoy.spb.data.validation.Issue;
import com.iafenvoy.spb.network.model.SelectionIntent;
import com.iafenvoy.spb.registry.SpbAttachments;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Server-thread entry point for session lifecycle, client intents and administrator recovery.
 * Draft editing, resource accounting, commit preparation, persistence and snapshots have separate
 * package-private collaborators; callers continue to use this facade.
 */
public final class SessionService {
    private static final Set<UUID> PENDING_LOGIN = new HashSet<>();

    private SessionService() {
    }

    public static SessionAttachment attachment(ServerPlayer player) {
        return player.getData(SpbAttachments.SESSION);
    }

    public static void onLogin(ServerPlayer player) {
        PENDING_LOGIN.add(player.getUUID());
    }

    /**
     * Restores the session one tick after login, once attachments and registries are ready.
     */
    public static void tickLogin(ServerPlayer player) {
        if (!PENDING_LOGIN.remove(player.getUUID())) {
            return;
        }
        SessionAttachment attachment = attachment(player);
        SelectionSession session = attachment.session();
        long now = gameTime(player);
        if (session != null) {
            if (session.abilitiesActive() || !attachment.grants().isEmpty()) {
                // Idempotent reconcile: other systems may already have rebuilt their grant set.
                AbilityGrantService.recalculate(player);
            }
            if (session.state() == SessionState.NEEDS_REFRESH) {
                markScreenOpen(player, session);
                rebaseline(player, session);
                return;
            }
            if (session.state() == SessionState.COMPLETED) {
                // Automatic plans are offered serially: the next applicable plan starts on a later login.
                ResolvedPlan next = selectAutomatic(player, attachment);
                if (next != null) {
                    startNew(player, attachment, next, now);
                    return;
                }
            }
            if (session.commit() == null && session.state().isEditable()) markScreenOpen(player, session);
            SessionSync.sendCurrent(player, session);
            return;
        }
        ResolvedPlan plan = selectAutomatic(player, attachment);
        if (plan != null) {
            startNew(player, attachment, plan, now);
        }
    }

    public static void onLogout(ServerPlayer player) {
        PENDING_LOGIN.remove(player.getUUID());
        SelectionSession session = attachment(player).session();
        if (session != null) session.setScreenOpen(false);
    }

    /**
     * Resumes the current session, or selects an applicable automatic plan when none exists.
     */
    public static void openCurrentOrAutomatic(ServerPlayer player, SessionOpenSource source) {
        SessionAttachment attachment = attachment(player);
        SelectionSession existing = attachment.session();
        if (existing != null) {
            reopen(player, existing);
            return;
        }
        ResolvedPlan target = selectAutomatic(player, attachment);
        if (target == null) {
            if (source == SessionOpenSource.COMMAND)
                message(player, "spb.message.no_plan");
            return;
        }
        startNew(player, attachment, target, gameTime(player));
    }

    /**
     * Opens a registered plan without an implicit fallback to another plan.
     */
    public static void open(ServerPlayer player, @NotNull Holder<Plan> plan) {
        Identifier planId = plan.unwrapKey().orElseThrow().identifier();
        SessionAttachment attachment = attachment(player);
        SelectionSession existing = attachment.session();
        if (existing != null) {
            if (planId.equals(existing.planId())) {
                reopen(player, existing);
                return;
            }
            if (!existing.state().isTerminal()) {
                message(player, "spb.message.session_busy", existing.planId().toString());
                return;
            }
        }
        if (attachment.isCompleted(planId)) {
            message(player, "spb.message.already_completed");
            return;
        }
        startNew(player, attachment, PlanIndex.resolve(player.registryAccess(), plan), gameTime(player));
    }

    private static void reopen(ServerPlayer player, SelectionSession session) {
        markScreenOpen(player, session);
        if (session.state() == SessionState.NEEDS_REFRESH && isUncommitted(player, session))
            rebaseline(player, session);
        else
            SessionSync.sendCurrent(player, session);
    }

    private static void startNew(ServerPlayer player, SessionAttachment attachment, ResolvedPlan plan, long now) {
        Object2DoubleMap<String> inputs = SessionResources.captureInputs(player, plan);
        if (inputs == null) {
            return;
        }
        Object2DoubleMap<Identifier> baseline = SessionResources.captureBaseline(player, plan, inputs);
        if (baseline == null) {
            return;
        }
        Object2DoubleMap<Identifier> real = SessionResources.captureCommitSnapshot(player, plan);
        SelectionSession session = new SelectionSession(UUID.randomUUID(), plan.id(), now);
        session.setFingerprint(plan.fingerprint());
        session.setInputs(inputs);
        session.setBaseline(baseline);
        session.setDraft(baseline);
        session.setRealSnapshot(real);
        session.setAbilitiesActive(false);
        session.setScreenOpen(true);
        session.setAllowCloseBeforeComplete(plan.plan().ui().allowCloseBeforeComplete());
        attachment.setSession(session, player.registryAccess());
        new SessionEditor(player, session, plan).publish();
        StartingPointBuy.LOGGER.debug("Opened starting-point session {} for {} ({})", session.id(), player.getScoreboardName(), plan.id());
    }

    public static boolean refresh(ServerPlayer player) {
        SessionAttachment attachment = attachment(player);
        SelectionSession session = attachment.session();
        if (session == null) {
            message(player, "spb.message.no_session");
            return false;
        }
        if (!isUncommitted(player, session)
                || !session.state().isEditable() && session.state() != SessionState.NEEDS_REFRESH) {
            message(player, "spb.message.already_completed");
            return false;
        }
        markScreenOpen(player, session);
        return rebaseline(player, session);
    }

    private static boolean rebaseline(ServerPlayer player, SelectionSession session) {
        if (!isUncommitted(player, session)) return false;
        ResolvedPlan plan = resolvePlan(player, session.planId());
        if (plan == null) return false;
        Object2IntMap<Identifier> kept = new Object2IntOpenHashMap<>();
        for (Object2IntMap.Entry<Identifier> entry : session.selections().object2IntEntrySet()) {
            if (plan.options().containsKey(entry.getKey())) {
                kept.put(entry.getKey(), entry.getIntValue());
            }
        }
        Object2DoubleMap<String> inputs = SessionResources.captureInputs(player, plan);
        if (inputs == null) {
            return false;
        }
        // Initial values belong to session creation. A refresh takes current real values and
        // must never re-evaluate an initial provider (including a captured random expression).
        Object2DoubleMap<Identifier> baseline = SessionResources.captureCurrent(player, plan);
        session.setSelections(kept);
        session.setPresetKey(null);
        session.setInputs(inputs);
        session.setBaseline(baseline);
        session.setDraft(baseline);
        session.setRealSnapshot(SessionResources.captureCommitSnapshot(player, plan));
        session.setFingerprint(plan.fingerprint());
        session.setState(SessionState.EDITING);
        session.setAllowCloseBeforeComplete(plan.plan().ui().allowCloseBeforeComplete());
        advanceRevision(player, session);
        new SessionEditor(player, session, plan).publish();
        message(player, "spb.message.refreshed");
        return true;
    }

    private static void refresh(ServerPlayer player, SelectionIntent intent) {
        SelectionSession session = requireSession(player, intent);
        if (session == null) return;
        // Recovery accepts NEEDS_REFRESH, unlike editing. Reject replays before recapturing inputs.
        if (intent.revision() != session.revision() || session.state() != SessionState.NEEDS_REFRESH
                || !isUncommitted(player, session)) {
            SessionSync.sendCurrent(player, session);
            return;
        }
        if (!rebaseline(player, session)) SessionSync.sendCurrent(player, session);
    }

    /**
     * Routes lifecycle intents directly; edits and submission share one validation gate.
     */
    public static void handle(ServerPlayer player, SelectionIntent intent) {
        switch (intent.op()) {
            case OPEN -> openCurrentOrAutomatic(player, SessionOpenSource.INTENT);
            case REFRESH -> refresh(player, intent);
            case CLOSE -> close(player, intent);
            default -> {
                SessionEditor editor = SessionEditor.require(player, intent);
                if (editor == null) return;
                switch (intent.op()) {
                    case SET_QUANTITY -> editor.setQuantity(intent);
                    case APPLY_PRESET -> editor.applyPreset(intent);
                    case RESET -> editor.reset();
                    case PREVIEW -> editor.preview(intent);
                    case SUBMIT -> SessionCommit.submit(editor);
                    default -> throw new IllegalStateException("Unexpected edit operation: " + intent.op());
                }
            }
        }
    }

    private static void close(ServerPlayer player, SelectionIntent intent) {
        SelectionSession session = attachment(player).session();
        if (session == null || !session.id().equals(intent.sessionId())) return;
        // A stale close must not dismiss a newer edit; completion always releases the UI.
        if (session.commit() == null && (!session.allowCloseBeforeComplete() || intent.revision() != session.revision())) {
            SessionSync.sendCurrent(player, session);
            return;
        }
        session.setScreenOpen(false);
        SessionSync.sendCurrent(player, session);
    }

    public static boolean isScreenProtected(ServerPlayer player) {
        SelectionSession session = attachment(player).session();
        return session != null && session.screenOpen();
    }

    private static void markScreenOpen(ServerPlayer player, SelectionSession session) {
        PlanIndex.find(player.registryAccess(), session.planId()).ifPresent(plan -> {
            session.setAllowCloseBeforeComplete(plan.plan().ui().allowCloseBeforeComplete());
            session.setScreenOpen(true);
        });
    }

    /**
     * Clears only this plan's records, closing and rolling back its current session when present.
     */
    public static void unlock(ServerPlayer player, @NotNull Holder<Plan> plan) {
        SessionAttachment attachment = attachment(player);
        SelectionSession current = attachment.session();
        if (current != null && current.planId().equals(plan.unwrapKey().orElseThrow().identifier()))
            clearCurrent(player, current);
        attachment.uncomplete(plan);
        attachment.grants().remove(plan);
        finishUnlock(player);
    }

    /**
     * Clears every plan's records and rolls back the current committed session when present.
     */
    public static void unlockAll(ServerPlayer player) {
        SessionAttachment attachment = attachment(player);
        SelectionSession current = attachment.session();
        if (current != null) clearCurrent(player, current);
        attachment.completions().clear();
        attachment.grants().clear();
        finishUnlock(player);
    }

    private static void clearCurrent(ServerPlayer player, SelectionSession session) {
        if (session.commit() != null) SessionResources.restoreBaseline(player, session);
        session.setScreenOpen(false);
        SessionSync.sendCurrent(player, session);
        attachment(player).clearSession(player.registryAccess());
    }

    private static void finishUnlock(ServerPlayer player) {
        SessionStorage.save(player);
        AbilityGrantService.recalculate(player);
    }

    /**
     * Persists and verifies the checkpoint before delayed side effects may run.
     */
    public static void persist(ServerPlayer player) {
        SessionStorage.persist(player);
    }

    public static void checkpointFailed(ServerPlayer player, RuntimeException exception) {
        SelectionSession session = attachment(player).session();
        if (session != null) {
            session.setState(SessionState.NEEDS_REVIEW);
            session.setScreenOpen(false);
        }
        StartingPointBuy.LOGGER.error("SPB checkpoint failed for {}; side effects halted", player.getScoreboardName(), exception);
        message(player, "spb.message.checkpoint_failed");
        refreshClient(player);
    }

    /**
     * Re-sends the current state without reopening a closed screen.
     */
    public static void refreshClient(ServerPlayer player) {
        SelectionSession session = attachment(player).session();
        if (session != null) {
            SessionSync.sendCurrent(player, session);
        }
    }

    @Nullable
    private static ResolvedPlan selectAutomatic(ServerPlayer player, SessionAttachment attachment) {
        ResolvedPlan best = null;
        for (ResolvedPlan plan : PlanIndex.all(player.level().registryAccess())) {
            if (!plan.plan().activation().onFirstJoin() || attachment.isCompleted(plan.id())) {
                continue;
            }
            try {
                if (!plan.plan().activation().condition().test(player, FormulaContext.of(player))) {
                    continue;
                }
            } catch (RuntimeException exception) {
                StartingPointBuy.LOGGER.warn("Activation condition of plan '{}' failed", plan.id(), exception);
                continue;
            }
            if (best == null
                    || plan.plan().activation().priority() > best.plan().activation().priority()
                    || plan.plan().activation().priority() == best.plan().activation().priority()
                    && plan.id().toString().compareTo(best.id().toString()) < 0) {
                best = plan;
            }
        }
        return best;
    }

    public static void logIssues(ResolvedPlan plan) {
        for (Issue issue : plan.issues()) {
            if (issue.isError()) {
                StartingPointBuy.LOGGER.error("{}: {}", issue.path(), issue.message().getString());
            } else {
                StartingPointBuy.LOGGER.warn("{}: {}", issue.path(), issue.message().getString());
            }
        }
    }

    @Nullable
    static SelectionSession requireSession(ServerPlayer player, SelectionIntent intent) {
        SelectionSession session = attachment(player).session();
        if (session == null) {
            message(player, "spb.message.no_session");
            return null;
        }
        if (!session.id().equals(intent.sessionId())) {
            SessionSync.sendCurrent(player, session);
            return null;
        }
        return session;
    }

    static boolean isUncommitted(ServerPlayer player, SelectionSession session) {
        return session.commit() == null && !attachment(player).isCompleted(session.planId());
    }

    @Nullable
    static ResolvedPlan resolvePlan(ServerPlayer player, Identifier planId) {
        ResolvedPlan plan = PlanIndex.find(player.registryAccess(), planId).orElse(null);
        if (plan == null) message(player, "spb.message.missing_plan", planId.toString());
        return plan;
    }

    static void requireRefresh(ServerPlayer player, SelectionSession session, String key, Object... args) {
        session.setState(SessionState.NEEDS_REFRESH);
        SessionStorage.save(player);
        message(player, key, args);
        SessionSync.sendCurrent(player, session);
    }

    static void advanceRevision(ServerPlayer player, SelectionSession session) {
        session.setRevision(session.revision() + 1);
        session.touch(gameTime(player));
    }

    private static long gameTime(ServerPlayer player) {
        return player.level().getGameTime();
    }

    static void message(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args));
    }
}
