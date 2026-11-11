package com.iafenvoy.spb.testmod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iafenvoy.saturn.attachment.ResourceHolderAttachment;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.saturn.event.CollectAbilityGrantsEvent;
import com.iafenvoy.saturn.registry.SaturnAttachments;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.spb.data.Plan;
import com.iafenvoy.spb.data.Group;
import com.iafenvoy.spb.data.Option;
import com.iafenvoy.spb.data.validation.Issue;
import com.iafenvoy.spb.data.Preset;
import com.iafenvoy.spb.data.ResourceBinding;
import com.iafenvoy.spb.data.resolution.PlanIndex;
import com.iafenvoy.spb.data.resolution.ResolvedOption;
import com.iafenvoy.spb.data.resolution.ResolvedPlan;
import com.iafenvoy.spb.event.PlayerSessionEvents;
import com.iafenvoy.spb.network.model.SelectionIntent;
import com.iafenvoy.spb.runtime.evaluation.ActionPlan;
import com.iafenvoy.spb.runtime.evaluation.EvaluationResult;
import com.iafenvoy.spb.runtime.evaluation.GroupView;
import com.iafenvoy.spb.runtime.evaluation.ResourceView;
import com.iafenvoy.spb.runtime.evaluation.SelectionData;
import com.iafenvoy.spb.runtime.evaluation.SelectionEvaluator;
import com.iafenvoy.spb.runtime.outcome.TaskState;
import com.iafenvoy.spb.runtime.session.SelectionSession;

import com.iafenvoy.spb.runtime.session.SessionService;
import com.iafenvoy.spb.runtime.session.SessionState;
import com.iafenvoy.spb.util.DefinitionNames;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Tests JSON loaded by Minecraft's datapack registries, plus invalid fixtures outside data/.
 */
final class DatapackTests {
    private DatapackTests() {
    }

    static void register(Map<String, Consumer<GameTestHelper>> tests) {
        DatapackLoadTests.register(tests);
        for (JsonElement element : read("fixtures/evaluations.json").getAsJsonArray()) {
            JsonObject fixture = element.getAsJsonObject();
            add(tests, "datapack/" + fixture.get("name").getAsString(), helper -> evaluateFixture(helper, fixture));
        }
        for (JsonElement element : read("fixtures/invalid/index.json").getAsJsonArray()) {
            JsonObject fixture = element.getAsJsonObject();
            add(tests, "invalid/" + fixture.get("name").getAsString(), helper -> invalidFixture(helper, fixture));
        }
        add(tests, "datapack/registry_contract", DatapackTests::registryContract);
        add(tests, "lifecycle/data_defined_close", DatapackTests::configuredClose);
        add(tests, "lifecycle/data_defined_switch", DatapackTests::switchProfession);
        add(tests, "lifecycle/data_defined_discard", DatapackTests::discardAndInputs);
        add(tests, "lifecycle/data_defined_rewards", DatapackTests::delayedRewards);
    }

    private static void add(Map<String, Consumer<GameTestHelper>> tests, String name, Consumer<GameTestHelper> test) {
        if (tests.putIfAbsent(name, test) != null) throw new IllegalArgumentException("Duplicate test name: " + name);
    }

    static JsonElement read(String path) {
        try (InputStream input = DatapackTests.class.getResourceAsStream("/" + path)) {
            if (input == null) throw new IllegalArgumentException("Missing test resource: " + path);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }

    private static Identifier id(String path) {
        return path.contains(":") ? Identifier.parse(path) : Identifier.fromNamespaceAndPath(StartingPointBuyTest.MOD_ID, path);
    }

    private static ResolvedPlan plan(GameTestHelper helper, String name) {
        ResolvedPlan plan = PlanIndex.find(helper.getLevel().registryAccess(), id(name)).orElseThrow();
        helper.assertTrue(!plan.hasErrors(), "datapack definition must be valid: " + name + " " + plan.errors());
        return plan;
    }

    private static SelectionData data(GameTestHelper helper, ResolvedPlan plan, JsonObject selections, JsonObject baseline) {
        Map<String, Double> inputs = new LinkedHashMap<>();
        FormulaContext context = FormulaContext.of(helper.getLevel());
        plan.plan().inputs().forEach((key, provider) -> inputs.put(key, provider.evaluate(context)));
        Map<String, Double> variables = new LinkedHashMap<>();
        inputs.forEach((key, value) -> variables.put(DefinitionNames.inputVariable(key), value));
        FormulaContext captured = FormulaContext.of(helper.getLevel(), variables);
        Map<Identifier, Double> base = new LinkedHashMap<>();
        for (ResourceBinding binding : plan.plan().resources()) {
            Holder<Resource> holder = plan.resources().get(binding.key());
            FormulaContext context1 = captured.withResource(holder);
            double value = binding.initial().orElse(holder.value().defaultValue()).evaluate(context1);
            if (baseline != null && baseline.has(binding.resourceId().toString()))
                value = baseline.get(binding.resourceId().toString()).getAsDouble();
            base.put(binding.resourceId(), value);
        }
        Map<Identifier, Integer> quantities = new LinkedHashMap<>();
        selections.asMap().forEach((key, value) -> quantities.put(id(key), value.getAsInt()));
        return new SelectionData(inputs, base, quantities);
    }

    private static void evaluateFixture(GameTestHelper helper, JsonObject fixture) {
        ResolvedPlan plan = plan(helper, fixture.get("plan").getAsString());
        SelectionData data = data(helper, plan, fixture.getAsJsonObject("selections"), fixture.getAsJsonObject("baseline"));
        EvaluationResult result = SelectionEvaluator.evaluate(plan, null, helper.getLevel().registryAccess(), data);
        helper.assertValueEqual(result.canComplete(), fixture.get("can_complete").getAsBoolean(),
                fixture.get("name") + ": completion; errors=" + result.issues() + ", failures=" + result.failures());
        if (fixture.has("has_errors"))
            helper.assertValueEqual(result.hasErrors(), fixture.get("has_errors").getAsBoolean(), "formula error propagation");
        each(fixture, "resources", (key, expected) -> {
            ResourceView view = result.resources().stream().filter(r -> r.key().equals(key)).findFirst().orElseThrow();
            helper.assertTrue(Math.abs(view.value() - expected.getAsDouble()) < 1e-8, "resource " + key + " expected " + expected + ", got " + view.value());
        });
        each(fixture, "max_counts", (key, expected) -> helper.assertValueEqual(result.option(id(key)).maxCount(), expected.getAsInt(), "option maximum " + key));
        each(fixture, "visible", (key, expected) -> helper.assertValueEqual(result.option(id(key)).visible(), expected.getAsBoolean(), "option visibility " + key));
        each(fixture, "enabled", (key, expected) -> helper.assertValueEqual(result.option(id(key)).enabled(), expected.getAsBoolean(), "option availability " + key));
        each(fixture, "groups", (key, expected) -> helper.assertValueEqual(group(result, key).count(), expected.getAsInt(), "group count " + key));
        each(fixture, "group_visible", (key, expected) -> helper.assertValueEqual(group(result, key).visible(), expected.getAsBoolean(), "group visibility " + key));
        if (fixture.has("abilities")) {
            Set<String> actual = new TreeSet<>();
            result.outcomes().forEach(outcome -> outcome.abilities().forEach(ability -> actual.add(ability.toString())));
            Set<String> expected = new TreeSet<>();
            fixture.getAsJsonArray("abilities").forEach(ability -> expected.add(ability.getAsString()));
            helper.assertValueEqual(actual, expected, "conditional ability selection");
        }
        if (fixture.has("tasks")) {
            int tasks = result.outcomes().stream().flatMap(o -> o.actions().stream()).mapToInt(ActionPlan::repeat).sum();
            helper.assertValueEqual(tasks, fixture.get("tasks").getAsInt(), "expanded task count");
        }
        if (fixture.has("order")) {
            List<Identifier> expected = new ArrayList<>();
            fixture.getAsJsonArray("order").forEach(value -> expected.add(id(value.getAsString())));
            helper.assertValueEqual(plan.orderedOptions().stream().map(ResolvedOption::id).toList(), expected, "tag expansion is sorted and deduplicated");
            helper.assertValueEqual(plan.groups().size(), 1, "duplicate group reference is deduplicated");
        }
        // Session entry points are exercised separately by the lifecycle fixtures below.
        helper.succeed();
    }

    private static void each(JsonObject fixture, String field, BiConsumer<String, JsonElement> action) {
        if (fixture.has(field)) fixture.getAsJsonObject(field).asMap().forEach(action);
    }

    private static GroupView group(EvaluationResult result, String key) {
        return result.groups().stream().filter(group -> group.key().equals(key)).findFirst().orElseThrow();
    }

    private static void invalidFixture(GameTestHelper helper, JsonObject fixture) {
        RegistryOps<JsonElement> ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        Codec<?> codec = switch (fixture.get("kind").getAsString()) {
            case "plan" -> Plan.DIRECT_CODEC;
            case "group" -> Group.DIRECT_CODEC;
            case "option" -> Option.DIRECT_CODEC;
            default -> throw new IllegalArgumentException("Unknown fixture kind: " + fixture);
        };
        DataResult<?> decoded = codec.parse(ops, read(fixture.get("file").getAsString()));
        if (fixture.get("stage").getAsString().equals("decode")) {
            helper.assertTrue(decoded.error().isPresent(), "invalid datapack JSON must fail decoding: " + fixture.get("name"));
        } else {
            List<Issue> errors = new ArrayList<>();
            switch (decoded.getOrThrow()) {
                case Plan definition -> {
                    ResolvedPlan plan = PlanIndex.resolve(helper.getLevel().registryAccess(), id("invalid_fixture"), definition);
                    errors.addAll(plan.errors());
                    EvaluationResult result = SelectionEvaluator.evaluate(plan, null, helper.getLevel().registryAccess(),
                            new SelectionData(Map.of(), Map.of(id("budget"), 6.0), Map.of()));
                    helper.assertTrue(!result.canComplete(), "invalid definition cannot submit");
                }
                case Group definition ->
                        PlanIndex.resolveOptions(helper.getLevel().registryAccess(), id("invalid_fixture"), definition, errors);
                default -> throw new IllegalArgumentException("Fixture has no semantic validator: " + fixture);
            }
            helper.assertTrue(errors.stream().anyMatch(Issue::isError), "invalid definition must be diagnosed: " + fixture.get("name"));
            String path = fixture.get("path").getAsString();
            helper.assertTrue(errors.stream().anyMatch(issue -> issue.path().contains(path)), "error reports field path: " + path);
        }
        helper.succeed();
    }

    private static void registryContract(GameTestHelper helper) {
        int presets = 0;
        for (JsonElement entry : read("fixtures/plans.json").getAsJsonArray()) {
            ResolvedPlan plan = plan(helper, entry.getAsString());
            for (Preset preset : plan.presets()) {
                JsonObject selections = new JsonObject();
                preset.selections().forEach((option, quantity) -> selections.addProperty(option.toString(), quantity));
                EvaluationResult result = SelectionEvaluator.evaluate(plan, null, helper.getLevel().registryAccess(), data(helper, plan, selections, null));
                helper.assertTrue(result.canComplete(), "datapack preset completes: " + plan.id() + "/" + preset.name().getString() + " " + result.issues() + result.failures());
                presets++;
            }
        }
        helper.assertTrue(presets >= 4, "exercise presets from several data-defined plans");
        helper.succeed();
    }

    private static ServerPlayer open(GameTestHelper helper, String name) {
        ServerPlayer player = TestPlayers.create(helper);
        SessionService.open(player, TestPlayers.plan(player, id(name)));
        helper.assertValueEqual(session(player).planId(), id(name), "server opened requested datapack plan");
        return player;
    }

    private static SelectionSession session(ServerPlayer player) {
        return SessionService.attachment(player).session();
    }

    private static void select(ServerPlayer player, String option, int quantity) {
        SelectionSession session = session(player);
        SessionService.handle(player, SelectionIntent.setQuantity(session.id(), session.revision(), id(option), quantity));
    }

    private static void preset(ServerPlayer player, String key) {
        SelectionSession session = session(player);
        SessionService.handle(player, SelectionIntent.applyPreset(session.id(), session.revision(), key));
    }

    private static void submit(ServerPlayer player) {
        SelectionSession session = session(player);
        SessionService.handle(player, SelectionIntent.submit(session.id(), session.revision()));
    }

    private static void configuredClose(GameTestHelper helper) {
        ServerPlayer player = open(helper, "optional");
        SelectionSession session = session(player);
        helper.assertTrue(session.allowCloseBeforeComplete(), "close preference comes from the datapack");
        select(player, "reward", 1);
        int revision = session.revision();
        SessionService.handle(player, SelectionIntent.close(session.id(), revision - 1));
        helper.assertTrue(SessionService.isScreenProtected(player), "stale close cannot dismiss the updated draft");
        SessionService.handle(player, SelectionIntent.close(session.id(), revision));
        helper.assertTrue(!SessionService.isScreenProtected(player), "data-defined optional close releases immunity");
        UUID before = session.id();
        SessionService.open(player, TestPlayers.plan(player, id("optional")));
        helper.assertValueEqual(session(player).id(), before, "reopening resumes the same session");
        helper.assertValueEqual(session.quantity(id("reward")), 1, "closing preserves selections");
        helper.assertTrue(session.commit() == null && session.tasks().isEmpty(), "previewing or closing never schedules actions");
        helper.assertValueEqual(player.getInventory().countItem(Items.APPLE), 0, "closing never grants a reward");
        SessionService.onLogout(player);
        helper.succeed();
    }

    private static void switchProfession(GameTestHelper helper) {
        ServerPlayer player = open(helper, "linked");
        SelectionSession session = session(player);
        preset(player, "preset_1");
        Map<Identifier, Integer> before = Map.copyOf(session.selections());
        Map<Identifier, Double> draft = Map.copyOf(session.draft());
        int originalRevision = session.revision();
        UUID wrongSession = UUID.randomUUID();
        for (SelectionIntent request : List.of(
                SelectionIntent.setQuantity(wrongSession, originalRevision, id("sage"), 1),
                SelectionIntent.applyPreset(wrongSession, originalRevision, "preset_2"),
                SelectionIntent.reset(wrongSession, originalRevision),
                SelectionIntent.preview(wrongSession, originalRevision, id("sage"), 1),
                SelectionIntent.submit(wrongSession, originalRevision),
                SelectionIntent.setQuantity(session.id(), originalRevision - 1, id("sage"), 1),
                SelectionIntent.applyPreset(session.id(), originalRevision - 1, "preset_2"),
                SelectionIntent.reset(session.id(), originalRevision - 1),
                SelectionIntent.preview(session.id(), originalRevision - 1, id("sage"), 1),
                SelectionIntent.submit(session.id(), originalRevision - 1),
                SelectionIntent.preview(session.id(), originalRevision, id("sage"), 99),
                SelectionIntent.preview(session.id(), originalRevision, id("train"), -1))) {
            SessionService.handle(player, request);
            helper.assertValueEqual(session.selections(), before, "rejected intents and previews preserve the actual choices");
            helper.assertValueEqual(session.draft(), draft, "rejected intents and previews preserve draft resources");
            helper.assertValueEqual(session.revision(), originalRevision, "rejected intents and previews do not advance the revision");
            helper.assertTrue(session.commit() == null && session.tasks().isEmpty(), "only an accepted submit can freeze rewards");
        }
        select(player, "sage", 1);
        helper.assertValueEqual(session.quantity(id("sword")), 0, "single choice replaces the previous role");
        helper.assertValueEqual(session.quantity(id("train")), 4, "role change preserves an over-limit draft");
        submit(player);
        helper.assertTrue(session.commit() == null, "reduced dynamic limit blocks submission");
        int revision = session.revision();
        SessionService.handle(player, SelectionIntent.setQuantity(session.id(), revision - 1, id("train"), 0));
        helper.assertValueEqual(session.quantity(id("train")), 4, "stale edit does not change the draft");
        select(player, "train", -1);
        select(player, "train", 5);
        helper.assertValueEqual(session.quantity(id("train")), 4, "raw quantity validation rejects negative and over-hardmax edits");
        preset(player, "preset_2");
        submit(player);
        helper.assertValueEqual(session.state(), SessionState.COMMITTED, "valid replacement preset commits");
        helper.succeed();
    }

    private static void discardAndInputs(GameTestHelper helper) {
        ServerPlayer player = open(helper, "readonly");
        SelectionSession session = session(player);
        ResolvedPlan plan = plan(helper, "readonly");
        Map<String, Double> captured = new LinkedHashMap<>(session.inputs());
        helper.assertTrue(captured.containsKey("roll") && Double.isFinite(captured.get("roll")), "random input captured once");
        preset(player, "preset_1");
        helper.assertValueEqual(session.draft().getDouble(id("readonly")), 5.0, "discard resource participates in draft accounting");
        SessionService.open(player, TestPlayers.plan(player, id("readonly")));
        helper.assertValueEqual(session.inputs(), captured, "ordinary reopen does not recapture random inputs");
        SessionService.handle(player, SelectionIntent.reset(session.id(), session.revision()));
        helper.assertValueEqual(session.baseline().get(id("budget")), 5.0, "reset retains the once-only initial value");
        helper.assertValueEqual(session.inputs(), captured, "reset preserves captured inputs");
        preset(player, "preset_1");
        submit(player);
        helper.assertValueEqual(session.state(), SessionState.COMMITTED, "discard plan submits");
        ResourceHolderAttachment real = player.getData(SaturnAttachments.RESOURCE_HOLDER);
        helper.assertValueEqual(real.get(plan.resources().get("spb_test_budget")), 1.0, "replace writes the final budget");
        helper.assertValueEqual(real.get(plan.resources().get("spb_test_readonly")), 7.0, "discard leaves the real resource untouched");
        helper.succeed();
    }

    private static void delayedRewards(GameTestHelper helper) {
        ServerPlayer player = open(helper, "rewards");
        preset(player, "preset_1");
        helper.assertTrue(session(player).tasks().isEmpty(), "selecting a preset has no reward side effect");
        submit(player);
        SelectionSession session = session(player);
        helper.assertValueEqual(session.state(), SessionState.COMMITTED, "reward plan committed");
        helper.assertValueEqual(session.tasks().size(), 5, "JSON formulas expand repeat into five tasks");
        helper.assertValueEqual(session.tasks().getFirst().delayRemaining(), 2, "fractional delay rounds up");
        helper.assertValueEqual(player.getInventory().countItem(Items.BREAD), 0, "actions do not execute in the submit tick");
        helper.assertTrue(!session.abilitiesActive(), "abilities activate in a later tick");
        // FakePlayer's no-op connection never drives doTick().
        // Publish the real event once per GameTest tick to exercise the registered queue handler.
        helper.onEachTick(() -> NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player)));
        helper.runAfterDelay(8, () -> {
            helper.assertValueEqual(session.state(), SessionState.COMPLETED, "online ticks finish the frozen queue");
            helper.assertValueEqual(player.getInventory().countItem(Items.BREAD), 3, "data-defined repeat gives three bread");
            helper.assertValueEqual(player.getInventory().countItem(Items.APPLE), 2, "option outcome gives two apples");
            helper.assertTrue(session.tasks().stream().allMatch(task -> task.state() == TaskState.DONE), "all tasks reach DONE");
            CollectAbilityGrantsEvent event = new CollectAbilityGrantsEvent(player);
            PlayerSessionEvents.collectGrants(event);
            helper.assertValueEqual(event.getDesired().size(), 2, "plan and option abilities have independent sources");
            UUID batch = session.commit().batchId();
            submit(player);
            helper.assertValueEqual(session.commit().batchId(), batch, "repeated submit preserves the batch");
            helper.assertValueEqual(player.getInventory().countItem(Items.BREAD), 3, "repeated submit does not duplicate rewards");
            helper.succeed();
        });
    }
}
