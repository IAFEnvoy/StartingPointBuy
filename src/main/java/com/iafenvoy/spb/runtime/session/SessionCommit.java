package com.iafenvoy.spb.runtime.session;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.runtime.Sources;
import com.iafenvoy.spb.StartingPointBuy;
import com.iafenvoy.spb.data.resolution.ResolvedPlan;
import com.iafenvoy.spb.runtime.evaluation.ActionPlan;
import com.iafenvoy.spb.runtime.evaluation.EvaluationResult;
import com.iafenvoy.spb.runtime.evaluation.OutcomePlan;
import com.iafenvoy.spb.runtime.evaluation.SelectionEvaluator;
import com.iafenvoy.spb.runtime.outcome.FrozenGrant;
import com.iafenvoy.spb.runtime.outcome.FrozenTask;
import com.iafenvoy.spb.runtime.outcome.TaskState;
import com.iafenvoy.spb.util.DefinitionNames;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Freezes rewards and commits a validated draft before delayed effects are allowed to run.
 */
final class SessionCommit {
    private SessionCommit() {
    }

    static void submit(SessionEditor editor) {
        ServerPlayer player = editor.player();
        SelectionSession session = editor.session();
        ResolvedPlan plan = editor.plan();
        if (plan.hasErrors()) {
            SessionService.message(player, "spb.message.not_complete");
            SessionSync.sendCurrent(player, session);
            return;
        }
        EvaluationResult evaluation = editor.evaluate();
        if (!evaluation.canComplete()) {
            SessionService.message(player, "spb.message.not_complete");
            SessionSync.send(player, evaluation, session);
            return;
        }
        String conflict = SessionResources.checkRealResources(player, plan, session);
        if (conflict != null) {
            SessionService.requireRefresh(player, session, "spb.message.resource_conflict", conflict);
            return;
        }

        UUID batchId = UUID.randomUUID();
        List<FrozenGrant> grants = freezeGrants(evaluation);
        List<FrozenTask> tasks = freezeTasks(player, evaluation, batchId);
        if (tasks == null) return;
        Object2DoubleMap<Identifier> committed = SessionResources.commit(player, evaluation);
        if (committed == null) return;

        session.setDraft(SessionResources.materialise(plan, SessionResources.evaluatedValues(evaluation)));
        session.setCommitted(committed);
        session.setState(SessionState.COMMITTED);
        session.setCommit(new CommitRecord(batchId, player.level().getGameTime(), grants));
        session.setTasks(tasks);
        session.setAbilitiesActive(false);
        session.setScreenOpen(false);
        SessionService.advanceRevision(player, session);
        SessionStorage.save(player);
        try {
            SessionStorage.persist(player);
        } catch (CheckpointException exception) {
            SessionService.checkpointFailed(player, exception);
            return;
        }
        SessionService.message(player, "spb.message.committed");
        SessionSync.sendCurrent(player, session);
        StartingPointBuy.LOGGER.debug("Committed starting-point session {} for {}: {} grants, {} tasks",
                session.id(), player.getScoreboardName(), grants.size(), tasks.size());
    }

    private static List<FrozenGrant> freezeGrants(EvaluationResult evaluation) {
        List<FrozenGrant> grants = new ArrayList<>();
        Set<Identifier> sources = new LinkedHashSet<>();
        for (OutcomePlan outcome : evaluation.outcomes()) {
            if (outcome.abilities().isEmpty()) continue;
            Identifier source = grantSource(evaluation.plan().id(), outcome.scope(), outcome.outcome().key(), sources);
            grants.add(new FrozenGrant(source, outcome.scope(), outcome.outcome().key(), outcome.abilities()));
        }
        return grants;
    }

    @Nullable
    private static List<FrozenTask> freezeTasks(ServerPlayer player, EvaluationResult evaluation, UUID batchId) {
        List<FrozenTask> tasks = new ArrayList<>();
        for (OutcomePlan outcome : evaluation.outcomes()) {
            for (ActionPlan action : outcome.actions()) {
                if (action.repeat() <= 0) continue;
                Tag payload = freezeAction(player, action.action());
                if (payload == null) {
                    SessionService.message(player, "spb.message.freeze_failed", action.key());
                    return null;
                }
                for (int repeat = 0; repeat < action.repeat(); repeat++) {
                    String taskId = batchId + ":" + tasks.size();
                    tasks.add(new FrozenTask(taskId, outcome.scope(), outcome.outcome().key(), action.key(),
                            repeat, action.delay(), TaskState.PENDING, payload, evaluation.variables(), ""));
                }
            }
        }
        if (tasks.size() > SelectionEvaluator.MAX_TASKS) {
            SessionService.message(player, "spb.message.task_limit", tasks.size());
            return null;
        }
        return tasks;
    }

    private static Identifier grantSource(Identifier planId, String scope, String outcomeKey, Set<Identifier> used) {
        String basePath = planId.getNamespace() + "/" + DefinitionNames.sanitize(planId.getPath()) + "/"
                + DefinitionNames.sanitize(scope) + "/" + DefinitionNames.sanitize(outcomeKey);
        Identifier content = Identifier.fromNamespaceAndPath(StartingPointBuy.MOD_ID, basePath);
        Identifier source = Sources.granted(new Sources.Grant("starting_point_buy"), content);
        int suffix = 2;
        while (!used.add(source)) {
            source = Sources.granted(new Sources.Grant("starting_point_buy"),
                    Identifier.fromNamespaceAndPath(StartingPointBuy.MOD_ID, basePath + "_" + suffix));
            suffix++;
        }
        return source;
    }

    @Nullable
    private static Tag freezeAction(ServerPlayer player, EntityAction action) {
        DataResult<Tag> result = EntityAction.CODEC.encodeStart(
                RegistryOps.create(NbtOps.INSTANCE, player.registryAccess()), action);
        if (result.error().isPresent()) {
            StartingPointBuy.LOGGER.error("Failed to freeze starting-point action: {}", result.error().get().message());
            return null;
        }
        return result.result().orElse(null);
    }
}
