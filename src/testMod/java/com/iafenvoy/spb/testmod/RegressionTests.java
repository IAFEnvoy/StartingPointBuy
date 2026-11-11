package com.iafenvoy.spb.testmod;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.saturn.event.CollectAbilityGrantsEvent;
import com.iafenvoy.saturn.registry.SaturnAttachments;
import com.iafenvoy.saturn.runtime.Sources;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.spb.data.Outcome;
import com.iafenvoy.spb.data.Plan;
import com.iafenvoy.spb.data.Preset;
import com.iafenvoy.spb.data.UiSettings;
import com.iafenvoy.spb.data.action.OpenPlanAction;
import com.iafenvoy.spb.data.resolution.PlanIndex;
import com.iafenvoy.spb.data.resolution.ResolvedPlan;
import com.iafenvoy.spb.data.validation.Issue;
import com.iafenvoy.spb.event.PlayerSessionEvents;
import com.iafenvoy.spb.network.model.SelectionIntent;
import com.iafenvoy.spb.registry.SpbAttachments;
import com.iafenvoy.spb.runtime.evaluation.EvaluationResult;
import com.iafenvoy.spb.runtime.evaluation.SelectionData;
import com.iafenvoy.spb.runtime.evaluation.SelectionEvaluator;
import com.iafenvoy.spb.runtime.outcome.FrozenGrant;
import com.iafenvoy.spb.runtime.outcome.FrozenTask;
import com.iafenvoy.spb.runtime.outcome.OutcomeService;
import com.iafenvoy.spb.runtime.outcome.TaskState;
import com.iafenvoy.spb.runtime.session.CommitRecord;
import com.iafenvoy.spb.runtime.session.CompletionRecord;
import com.iafenvoy.spb.runtime.session.SelectionSession;
import com.iafenvoy.spb.runtime.session.SessionAttachment;


import com.iafenvoy.spb.runtime.session.SessionService;
import com.iafenvoy.spb.runtime.session.SessionState;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Regression tests exercise the public server entry points, real damage hooks and player save files.
 */
final class RegressionTests {
    private static final Identifier PLAN = Identifier.parse("example:default");
    private static final Identifier POINTS = Identifier.parse("example:points");
    private static final Identifier FATE = Identifier.parse("example:fate");
    private static final Identifier STRENGTH = Identifier.parse("example:strength");
    private static final Identifier ARCHERY = Identifier.parse("example:archery");

    static void register(Map<String, Consumer<GameTestHelper>> tests) {
        tests.put("regression_definition_errors", RegressionTests::definitionErrors);
        tests.put("regression_refresh_replay", RegressionTests::refreshReplay);
        tests.put("regression_prior_grants", RegressionTests::priorGrants);
        tests.put("regression_missing_baseline", RegressionTests::missingBaseline);
        tests.put("regression_missing_reference", RegressionTests::missingReference);
        tests.put("regression_preset_codec", RegressionTests::presetCodec);
        tests.put("regression_screen_protection", RegressionTests::screenProtection);
        tests.put("regression_task_checkpoint", RegressionTests::taskCheckpoint);
        tests.put("regression_checkpoint_failure", RegressionTests::checkpointFailure);
        tests.put("regression_refresh_baseline", RegressionTests::refreshBaseline);
        tests.put("regression_reset_flow", RegressionTests::resetFlow);
        tests.put("regression_unlock_flow", RegressionTests::unlockFlow);
        tests.put("regression_open_entry_points", RegressionTests::openEntryPoints);
        tests.put("regression_attachment_holders", RegressionTests::attachmentHolders);
    }

    private static ResolvedPlan resolve(GameTestHelper helper) {
        return PlanIndex.find(helper.getLevel().registryAccess(), PLAN).orElseThrow();
    }

    private static EvaluationResult evaluate(GameTestHelper helper, ResolvedPlan plan, Map<Identifier, Double> base,
                                             Map<Identifier, Integer> selection) {
        return SelectionEvaluator.evaluate(plan, null, helper.getLevel().registryAccess(),
                new SelectionData(Map.of(), base, selection));
    }

    private static void definitionErrors(GameTestHelper helper) {
        Plan p = resolve(helper).plan();
        Plan changed = new Plan(p.title(), p.description(), p.activation(), p.inputs(),
                List.of(p.resources().getFirst(), p.resources().getFirst()), p.presets(), p.groups(), p.completionRules(), p.outcomes(), p.ui());
        helper.assertTrue(Plan.DIRECT_CODEC.encodeStart(helper.getLevel().registryAccess()
                        .createSerializationContext(JsonOps.INSTANCE), changed).error().isPresent(),
                "duplicate resource bindings are rejected by the codec before a world can load");
        helper.succeed();
    }

    private static ServerPlayer open(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        SessionService.open(player, TestPlayers.plan(player, PLAN));
        helper.assertTrue(SessionService.attachment(player).session() != null, "server opens example plan");
        return player;
    }

    private static void selectAndSubmit(ServerPlayer player) {
        SelectionSession session = SessionService.attachment(player).session();
        SessionService.handle(player, SelectionIntent.applyPreset(session.id(), session.revision(), "preset_1"));
        SessionService.handle(player, SelectionIntent.submit(session.id(), session.revision()));
    }

    private static void refreshReplay(GameTestHelper helper) {
        ServerPlayer player = open(helper);
        selectAndSubmit(player);
        SelectionSession session = SessionService.attachment(player).session();
        helper.assertValueEqual(session.state(), SessionState.COMMITTED, "valid selection commits");
        UUID batch = session.commit().batchId();
        SessionService.attachment(player).complete(TestPlayers.plan(player, PLAN), new CompletionRecord(batch, 1L, true));
        session.setState(SessionState.COMPLETED);
        player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "starting_point_buy refresh");
        helper.assertValueEqual(session.state(), SessionState.COMPLETED, "refresh cannot reopen a completed plan");
        // Also reject a damaged/legacy save which claims EDITING but already has a frozen commit.
        session.setState(SessionState.EDITING);
        SessionService.handle(player, SelectionIntent.submit(session.id(), session.revision()));
        helper.assertValueEqual(session.commit().batchId(), batch, "existing commits are immutable even with stale editable state");
        session.setState(SessionState.NEEDS_REFRESH);
        SessionService.open(player, TestPlayers.plan(player, PLAN));
        helper.assertValueEqual(session.state(), SessionState.NEEDS_REFRESH, "rebaseline cannot erase an existing commit");
        SessionService.onLogout(player);
        helper.succeed();
    }

    private static void priorGrants(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        SessionAttachment attachment = SessionService.attachment(player);
        SelectionSession first = new SelectionSession(UUID.randomUUID(), PLAN, 1L);
        Identifier source = Sources.granted(new Sources.Grant("starting_point_buy"), Identifier.parse("spb:prior_test"));
        first.setCommit(new CommitRecord(UUID.randomUUID(), 1L, List.of(
                new FrozenGrant(source, "plan", "test", List.of(ARCHERY)))));
        first.setAbilitiesActive(true);
        first.setState(SessionState.COMPLETED);
        attachment.setSession(first, player.registryAccess());
        attachment.complete(TestPlayers.plan(player, PLAN), new CompletionRecord(first.commit().batchId(), 1L, true));
        // Exercise old-save migration and then replacement of the only active session.
        RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        CompoundTag oldSave = (CompoundTag) SessionAttachment.CODEC.codec().encodeStart(ops, attachment).getOrThrow();
        oldSave.remove("grants");
        attachment = SessionAttachment.CODEC.codec().parse(ops, oldSave).getOrThrow();
        player.setData(SpbAttachments.SESSION, attachment);
        attachment.setSession(new SelectionSession(UUID.randomUUID(), Identifier.parse("spb_test:optional"), 2L), player.registryAccess());
        CollectAbilityGrantsEvent event = new CollectAbilityGrantsEvent(player);
        PlayerSessionEvents.collectGrants(event);
        helper.assertValueEqual(event.getDesired().size(), 1, "a new plan preserves the prior permanent ability");
        SelectionSession second = attachment.session();
        second.setCommit(new CommitRecord(UUID.randomUUID(), 2L, List.of(new FrozenGrant(
                Identifier.parse("saturn:grant/starting_point_buy/spb/second_test"), "plan", "test", List.of(ARCHERY)))));
        second.setAbilitiesActive(true);
        event = new CollectAbilityGrantsEvent(player);
        PlayerSessionEvents.collectGrants(event);
        helper.assertValueEqual(event.getDesired().size(), 2, "both plans retain their own grant sources");
        SessionService.unlock(player, TestPlayers.plan(player, PLAN));
        event = new CollectAbilityGrantsEvent(player);
        PlayerSessionEvents.collectGrants(event);
        helper.assertValueEqual(event.getDesired().size(), 1, "unlock preserves the other plan grant");
        helper.succeed();
    }

    private static void missingBaseline(GameTestHelper helper) {
        Object2DoubleOpenHashMap<Identifier> primitive = new Object2DoubleOpenHashMap<>();
        Map<Identifier, Double> boxed = primitive;
        helper.assertTrue(primitive.getDouble(POINTS) == 0 && boxed.get(POINTS) == null, "absence differs from explicit zero");
        primitive.put(POINTS, 0.0);
        helper.assertTrue(boxed.get(POINTS) != null, "explicit zero remains present");
        EvaluationResult result = evaluate(helper, resolve(helper), Map.of(FATE, 2.0, STRENGTH, 0.0), Map.of());
        helper.assertTrue(!result.canComplete() && result.issues().stream().anyMatch(Issue::isError),
                "missing baseline reports a diagnostic instead of crashing or becoming legitimate zero");
        helper.succeed();
    }

    private static void missingReference(GameTestHelper helper) {
        RegistryOps<JsonElement> ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonElement json = JsonParser.parseString("{\"key\":\"missing\",\"abilities\":[\"spb:missing_ability\"]}");
        helper.assertTrue(Outcome.CODEC.parse(ops, json).error().isPresent(),
                "Holder codec rejects missing ability references");
        helper.succeed();
    }

    private static void presetCodec(GameTestHelper helper) {
        for (String value : List.of("\"not_a_number\"", "-1")) {
            JsonElement json = JsonParser.parseString("{\"name\":{\"text\":\"Broken\"},\"selections\":{\"example:warrior\":" + value + "}}");
            helper.assertTrue(Preset.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent(), "malformed quantities must fail decoding");
        }
        helper.succeed();
    }

    private static void screenProtection(GameTestHelper helper) {
        ServerPlayer player = open(helper);
        player.connection.markClientLoaded();
        player.getAbilities().invulnerable = false;
        player.setInvulnerable(false);
        SelectionSession session = SessionService.attachment(player).session();
        helper.assertTrue(!UiSettings.DEFAULT.allowCloseBeforeComplete(), "screen defaults to mandatory completion");
        float health = player.getHealth();
        for (DamageSource source : List.of(player.damageSources().generic(), player.damageSources().fellOutOfWorld(),
                player.damageSources().onFire(), player.damageSources().fall(), player.damageSources().starve())) {
            player.hurtServer(helper.getLevel(), source, 4.0F);
            helper.assertValueEqual(player.getHealth(), health, "open screen ignores " + source);
        }
        SessionService.handle(player, SelectionIntent.close(session.id(), session.revision()));
        helper.assertTrue(SessionService.isScreenProtected(player), "forged close cannot release a mandatory screen");
        UiSettings optionalUi = UiSettings.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"allow_close_before_complete\":true}")).getOrThrow();
        session.setAllowCloseBeforeComplete(optionalUi.allowCloseBeforeComplete());
        SessionService.handle(player, SelectionIntent.close(session.id(), session.revision()));
        helper.assertTrue(!SessionService.isScreenProtected(player), "optional close releases damage immunity");
        player.invulnerableTime = 0;
        player.hurtServer(helper.getLevel(), player.damageSources().generic(), 4.0F);
        helper.assertTrue(player.getHealth() < health, "damage resumes after optional close");
        SessionService.open(player, TestPlayers.plan(player, PLAN));
        helper.assertTrue(SessionService.isScreenProtected(player), "reopening restores immunity");
        selectAndSubmit(player);
        helper.assertValueEqual(session.state(), SessionState.COMMITTED, "confirmation succeeds");
        helper.assertTrue(!SessionService.isScreenProtected(player), "confirmation releases immunity");
        SessionService.open(player, TestPlayers.plan(player, PLAN));
        helper.assertTrue(SessionService.isScreenProtected(player), "explicitly reopening a result protects the visible screen");
        SessionService.unlock(player, TestPlayers.plan(player, PLAN));
        helper.assertTrue(!SessionService.isScreenProtected(player), "administrator unlock releases screen protection");
        SessionService.onLogout(player);
        helper.assertTrue(!session.screenOpen(), "logout clears transient screen state");
        helper.succeed();
    }

    static SessionAttachment saved(ServerPlayer player) {
        try {
            Path path = player.level().getServer().getPlayerList().getPlayerIo().getPlayerDir().toPath()
                    .resolve(player.getStringUUID() + ".dat");
            Tag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap())
                    .getCompoundOrEmpty("neoforge:attachments").get("spb:session");
            return SessionAttachment.CODEC.codec().parse(player.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static FrozenTask breadTask(ServerPlayer player, TaskState state) {
        EntityAction action = EntityAction.CODEC.parse(player.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                JsonParser.parseString("{\"type\":\"saturn:give_item\",\"stack\":{\"id\":\"minecraft:bread\",\"count\":2}}")).getOrThrow();
        Tag payload = EntityAction.CODEC.encodeStart(player.registryAccess().createSerializationContext(NbtOps.INSTANCE), action).getOrThrow();
        return new FrozenTask("checkpoint", "plan", "test", "bread", 0, 0, state, payload, Map.of(), "");
    }

    private static void taskCheckpoint(GameTestHelper helper) {
        ServerPlayer player = open(helper);
        SelectionSession session = SessionService.attachment(player).session();
        session.setCommit(new CommitRecord(UUID.randomUUID(), 0L, List.of()));
        session.setState(SessionState.APPLYING);
        session.setAbilitiesActive(true);
        session.setTasks(List.of(breadTask(player, TaskState.PENDING)));
        CheckpointObserver observer = new CheckpointObserver(player, helper, false);
        NeoForge.EVENT_BUS.register(observer);
        try {
            OutcomeService.tick(player);
        } finally {
            NeoForge.EVENT_BUS.unregister(observer);
        }
        helper.assertTrue(observer.sawRunning(), "RUNNING was written to disk before the action");
        helper.assertValueEqual(player.getInventory().countItem(Items.BREAD), 2, "frozen action executes after checkpoint");
        helper.assertValueEqual(saved(player).session().tasks().getFirst().state(), TaskState.DONE, "DONE also reaches disk");
        session.setState(SessionState.APPLYING);
        session.setTasks(List.of(breadTask(player, TaskState.RUNNING)));
        OutcomeService.tick(player);
        helper.assertValueEqual(session.tasks().getFirst().state(), TaskState.UNCERTAIN, "interrupted action is quarantined");
        helper.assertValueEqual(player.getInventory().countItem(Items.BREAD), 2, "interrupted action never replays");
        helper.succeed();
    }

    private static void checkpointFailure(GameTestHelper helper) {
        ServerPlayer player = open(helper);
        SelectionSession session = SessionService.attachment(player).session();
        session.setCommit(new CommitRecord(UUID.randomUUID(), 0L, List.of()));
        session.setState(SessionState.APPLYING);
        session.setAbilitiesActive(true);
        session.setTasks(List.of(breadTask(player, TaskState.PENDING)));
        CheckpointObserver observer = new CheckpointObserver(player, helper, true);
        NeoForge.EVENT_BUS.register(observer);
        try {
            OutcomeService.tick(player);
        } finally {
            NeoForge.EVENT_BUS.unregister(observer);
        }
        helper.assertValueEqual(session.state(), SessionState.NEEDS_REVIEW, "failed checkpoint halts the queue");
        helper.assertValueEqual(player.getInventory().countItem(Items.BREAD), 0, "no reward executes without its saved checkpoint");
        helper.succeed();
    }

    private static void refreshBaseline(GameTestHelper helper) {
        ServerPlayer player = open(helper);
        SelectionSession session = SessionService.attachment(player).session();
        Holder<Resource> holder = resolve(helper).resources().get("example_points");
        player.getData(SaturnAttachments.RESOURCE_HOLDER).set(holder, 3.0);
        player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "starting_point_buy refresh");
        helper.assertValueEqual(session.baseline().get(POINTS), 3.0, "refresh uses the changed real value instead of refilling points");
        helper.assertValueEqual(session.state(), SessionState.EDITING, "draft refresh remains available before commit");
        SessionService.onLogout(player);
        helper.succeed();
    }

    private static void resetFlow(GameTestHelper helper) {
        resetAndReselect(helper, "example:default", "preset_1");
        resetAndReselect(helper, "spb_test:linked", "preset_1");
        resetAndReselect(helper, "spb_test:readonly", "preset_1");
        helper.succeed();
    }

    private static void unlockFlow(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        SessionService.open(player, TestPlayers.plan(player, PLAN));
        SelectionSession session = SessionService.attachment(player).session();
        Map<Identifier, Double> original = Map.copyOf(session.baseline());
        SessionService.handle(player, SelectionIntent.applyPreset(session.id(), session.revision(), "preset_1"));
        SessionService.handle(player, SelectionIntent.submit(session.id(), session.revision()));
        helper.assertValueEqual(session.state(), SessionState.COMMITTED, "warrior preset commits");
        Holder<Plan> other = TestPlayers.plan(player, Identifier.parse("spb_test:optional"));
        SessionAttachment attachment = SessionService.attachment(player);
        attachment.complete(other, new CompletionRecord(UUID.randomUUID(), 1L, true));
        SessionService.unlock(player, other);
        helper.assertTrue(attachment.session() == session && session.state() == SessionState.COMMITTED,
                "unlocking another plan preserves the current committed session");
        helper.assertTrue(!attachment.isCompleted(Identifier.parse("spb_test:optional")), "only the target completion is cleared");
        SessionService.unlock(player, TestPlayers.plan(player, PLAN));
        SessionService.open(player, TestPlayers.plan(player, PLAN));
        SelectionSession reopened = SessionService.attachment(player).session();
        helper.assertValueEqual(Map.copyOf(reopened.baseline()), original, "unlock restores the pre-plan resource baseline");
        selectAndSubmit(player);
        attachment.complete(other, new CompletionRecord(UUID.randomUUID(), 1L, true));
        attachment.grants().put(other, List.of());
        SessionService.unlockAll(player);
        helper.assertTrue(attachment.session() == null && attachment.completions().isEmpty() && attachment.grants().isEmpty(),
                "unlockAll clears current state and both permanent ledgers");
        SessionService.open(player, TestPlayers.plan(player, PLAN));
        helper.assertValueEqual(Map.copyOf(attachment.session().baseline()), original, "unlockAll also restores the committed resource baseline");
        SessionService.onLogout(player);
        helper.succeed();
    }

    private static void openEntryPoints(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        RegistryOps<JsonElement> ops = player.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        OpenPlanAction automatic = OpenPlanAction.CODEC.codec().parse(ops, JsonParser.parseString("{}")).getOrThrow();
        EntityActionContext context = new EntityActionContext(player, FormulaContext.of(player));
        automatic.execute(context);
        SelectionSession current = SessionService.attachment(player).session();
        helper.assertTrue(current != null && current.planId().equals(PLAN), "an action without a plan selects the automatic plan");
        SessionService.handle(player, SelectionIntent.applyPreset(current.id(), current.revision(), "preset_1"));
        int revision = current.revision();
        automatic.execute(context);
        helper.assertTrue(SessionService.attachment(player).session() == current && current.revision() == revision,
                "the default entry resumes the current draft without reinitialising it");
        OpenPlanAction explicit = OpenPlanAction.CODEC.codec().parse(ops,
                JsonParser.parseString("{\"plan\":\"spb_test:optional\"}")).getOrThrow();
        explicit.execute(context);
        helper.assertTrue(SessionService.attachment(player).session() == current, "an explicit plan cannot replace an unfinished different plan");
        SessionService.unlockAll(player);
        explicit.execute(context);
        helper.assertValueEqual(SessionService.attachment(player).session().planId(), Identifier.parse("spb_test:optional"),
                "a Holder-backed action opens exactly the requested plan");
        helper.assertTrue(OpenPlanAction.CODEC.codec().parse(ops,
                        JsonParser.parseString("{\"plan\":\"spb_test:missing_plan\"}")).error().isPresent(),
                "a missing plan is rejected by the action codec");
        helper.assertValueEqual(OpenPlanAction.CODEC.codec().encodeStart(ops, explicit).getOrThrow()
                .getAsJsonObject().get("plan").getAsString(), "spb_test:optional", "Holder references retain the existing JSON ID format");
        SessionService.onLogout(player);
        helper.succeed();
    }

    private static void attachmentHolders(GameTestHelper helper) {
        ServerPlayer player = open(helper);
        SessionAttachment attachment = SessionService.attachment(player);
        Holder<Plan> plan = TestPlayers.plan(player, PLAN);
        attachment.complete(plan, new CompletionRecord(UUID.randomUUID(), 1L, true));
        attachment.grants().put(plan, List.of(new FrozenGrant(Identifier.parse("spb:test_source"), "plan", "test", List.of(ARCHERY))));
        RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        CompoundTag data = (CompoundTag) SessionAttachment.CODEC.codec().encodeStart(ops, attachment).getOrThrow();
        for (String name : List.of("completions", "grants")) {
            CompoundTag entries = data.getCompoundOrEmpty(name);
            helper.assertTrue(entries.contains(PLAN.toString()), "Holder map keys retain the previous save format");
            entries.put("spb_test:removed_plan", entries.get(PLAN.toString()).copy());
            entries.putString("spb_test:readonly", "invalid record");
        }
        SessionAttachment decoded = SessionAttachment.CODEC.codec().parse(ops, data).getOrThrow();
        helper.assertValueEqual(decoded.completions().size(), 1, "only missing or invalid completion entries are discarded");
        helper.assertValueEqual(decoded.grants().size(), 1, "only missing or invalid grant entries are discarded");
        helper.assertTrue(decoded.isCompleted(PLAN) && decoded.grants().containsKey(plan), "valid Holder entries survive filtering");
        helper.assertValueEqual(decoded.session().id(), attachment.session().id(), "one bad map entry does not discard the current session");
        helper.assertValueEqual(decoded.session().baseline(), attachment.session().baseline(), "map filtering preserves the current resource baseline");
        helper.succeed();
    }

    private static void resetAndReselect(GameTestHelper helper, String planName, String preset) {
        ServerPlayer player = TestPlayers.create(helper);
        SessionService.open(player, TestPlayers.plan(player, Identifier.parse(planName)));
        SelectionSession session = SessionService.attachment(player).session();
        Map<Identifier, Double> baseline = Map.copyOf(session.baseline());
        SessionService.handle(player, SelectionIntent.applyPreset(session.id(), session.revision(), preset));
        Map<Identifier, Double> selected = Map.copyOf(session.draft());
        SessionService.handle(player, SelectionIntent.reset(session.id(), session.revision()));
        helper.assertValueEqual(Map.copyOf(session.draft()), baseline, planName + ": reset restores the baseline draft");
        SessionService.handle(player, SelectionIntent.applyPreset(session.id(), session.revision(), preset));
        helper.assertValueEqual(Map.copyOf(session.draft()), selected, planName + ": reselection after reset matches the first selection");
        SessionService.onLogout(player);
    }
}
