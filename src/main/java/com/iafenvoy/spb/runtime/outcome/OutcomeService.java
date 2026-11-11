package com.iafenvoy.spb.runtime.outcome;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.iafenvoy.saturn.runtime.ability.AbilityGrantService;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.spb.StartingPointBuy;
import com.iafenvoy.spb.registry.SpbAttachments;
import com.iafenvoy.spb.registry.SpbResourceKeys;
import com.iafenvoy.spb.runtime.evaluation.ContextKeys;
import com.iafenvoy.spb.runtime.session.*;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Delayed application of a committed session: ability grants first, then the frozen action queue.
 *
 * <p>Execution only happens while the player is online and the world is available. A task is marked
 * {@code RUNNING} and the attachment persisted before the side effect is invoked; a crash between
 * those steps leaves {@code RUNNING} in the save, which is converted to {@code UNCERTAIN} on the next
 * login and never replayed automatically. This is a recovery protocol, not an exactly-once guarantee
 * for arbitrary Saturn actions.
 */
public final class OutcomeService {
    private OutcomeService() {
    }

    public static void tick(ServerPlayer player) {
        try {
            tickChecked(player);
        } catch (CheckpointException exception) {
            SessionService.checkpointFailed(player, exception);
        }
    }

    private static void tickChecked(ServerPlayer player) {
        SessionService.tickLogin(player);
        SessionAttachment attachment = SessionService.attachment(player);
        SelectionSession session = attachment.session();
        if (session == null) return;
        long now = player.level().getGameTime();
        if (session.state() == SessionState.COMMITTED) {
            CommitRecord commit = session.commit();
            if (commit == null) {
                session.setState(SessionState.NEEDS_REVIEW);
                SessionService.persist(player);
                SessionService.refreshClient(player);
                return;
            }
            if (now <= commit.gameTime()) return;
            session.setState(SessionState.APPLYING);
            session.touch(now);
            SessionService.persist(player);
        }
        if (session.state() != SessionState.APPLYING) return;

        boolean changed = false;
        boolean failed = false;
        if (!session.abilitiesActive()) {
            session.setAbilitiesActive(true);
            try {
                SessionService.persist(player);
            } catch (RuntimeException exception) {
                session.setAbilitiesActive(false);
                throw exception;
            }
            attachment.captureActiveGrants(player.registryAccess());
            AbilityGrantService.recalculate(player);
            changed = true;
        }

        for (FrozenTask task : List.copyOf(session.tasks())) {
            if (task.state() == TaskState.RUNNING) {
                // Persisted RUNNING means the previous process died around the side effect.
                session.replaceTask(task.withState(TaskState.UNCERTAIN)
                        .withFailure("interrupted while running; not replayed"));
                StartingPointBuy.LOGGER.error("Starting-point task '{}' of {} was interrupted; marked uncertain",
                        task.id(), player.getScoreboardName());
                failed = true;
                changed = true;
                continue;
            }
            if (task.state() != TaskState.PENDING) continue;
            if (task.delayRemaining() > 0) {
                session.replaceTask(task.withDelay(task.delayRemaining() - 1));
                changed = true;
                continue;
            }
            FrozenTask running = task.withState(TaskState.RUNNING);
            session.replaceTask(running);
            session.touch(now);
            SessionService.persist(player);
            changed = true;
            try {
                EntityAction action = decode(player, task.payload());
                if (action == null) {
                    session.replaceTask(running.withState(TaskState.FAILED).withFailure("the frozen action could not be decoded (missing dependency?)"));
                    StartingPointBuy.LOGGER.error("Starting-point task '{}' of {} references a missing action", task.id(), player.getScoreboardName());
                    failed = true;
                    continue;
                }
                FormulaContext formula = FormulaContext.of(player, task.variables());
                EntityActionContext context = new EntityActionContext(player, formula);
                context.set(ContextKeys.SESSION, session.id());
                action.execute(context);
                session.replaceTask(running.withState(TaskState.DONE));
            } catch (Throwable throwable) {
                session.replaceTask(running.withState(TaskState.FAILED).withFailure(String.valueOf(throwable)));
                StartingPointBuy.LOGGER.error("Starting-point task '{}' of {} failed", task.id(), player.getScoreboardName(), throwable);
                failed = true;
            }
            SessionService.persist(player);
        }

        boolean anyPending = session.tasks().stream().anyMatch(task -> task.state() == TaskState.PENDING);
        if (failed) {
            session.setState(SessionState.NEEDS_REVIEW);
            SessionService.persist(player);
            SessionService.refreshClient(player);
            player.sendSystemMessage(Component.translatable("spb.message.needs_review"));
            return;
        }
        if (!anyPending) {
            session.setState(SessionState.COMPLETED);
            session.touch(now);
            CommitRecord commit = session.commit();
            if (commit != null) {
                player.registryAccess().lookupOrThrow(SpbResourceKeys.PLAN)
                        .get(ResourceKey.create(SpbResourceKeys.PLAN, session.planId()))
                        .ifPresent(plan -> attachment.complete(plan, new CompletionRecord(commit.batchId(), now, true)));
            }
            SessionService.persist(player);
            SessionService.refreshClient(player);
            player.sendSystemMessage(Component.translatable("spb.message.completed", session.planId().toString()));
        } else if (changed) {
            // Delay countdowns may use the regular player autosave. Only transitions around
            // side effects need a synchronous checkpoint on the server thread.
            player.setData(SpbAttachments.SESSION, attachment);
        }
    }

    @Nullable
    private static EntityAction decode(ServerPlayer player, Tag payload) {
        DynamicOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, player.level().registryAccess());
        return EntityAction.CODEC.parse(ops, payload).result().orElse(null);
    }

    /**
     * Re-drives a session that an administrator released from NEEDS_REVIEW.
     */
    public static void resume(ServerPlayer player, SessionAttachment attachment, SelectionSession session) {
        boolean hasUncertain = session.tasks().stream().anyMatch(task -> task.state() == TaskState.UNCERTAIN);
        if (hasUncertain) {
            List<FrozenTask> tasks = session.tasks().stream()
                    .map(task -> task.state() == TaskState.UNCERTAIN ? task.withState(TaskState.FAILED) : task)
                    .toList();
            session.setTasks(tasks);
        }
        session.setState(SessionState.APPLYING);
        session.touch(player.level().getGameTime());
        SessionService.persist(player);
        SessionService.refreshClient(player);
    }
}
