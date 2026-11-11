package com.iafenvoy.spb.testmod;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.condition.AlwaysCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.iafenvoy.saturn.util.formula.number.Expression;
import com.iafenvoy.saturn.util.math.CompareOperation;
import com.iafenvoy.spb.StartingPointBuy;
import com.iafenvoy.spb.data.Activation;
import com.iafenvoy.spb.data.CommitMode;
import com.iafenvoy.spb.data.CompletionRule;
import com.iafenvoy.spb.data.CountMode;
import com.iafenvoy.spb.data.Group;
import com.iafenvoy.spb.data.Option;
import com.iafenvoy.spb.data.Plan;
import com.iafenvoy.spb.data.Preset;
import com.iafenvoy.spb.data.ResourceBinding;
import com.iafenvoy.spb.data.UiSettings;
import com.iafenvoy.spb.data.condition.CompareEntityCondition;
import com.iafenvoy.spb.data.resolution.PlanIndex;
import com.iafenvoy.spb.data.resolution.ResolvedGroup;
import com.iafenvoy.spb.data.resolution.ResolvedOption;
import com.iafenvoy.spb.data.resolution.ResolvedPlan;
import com.iafenvoy.spb.data.validation.PlanValidator;
import com.iafenvoy.spb.data.validation.Issue;
import com.iafenvoy.spb.network.SnapshotFactory;
import com.iafenvoy.spb.network.model.SelectionSnapshot;
import com.iafenvoy.spb.runtime.evaluation.EvaluationResult;
import com.iafenvoy.spb.runtime.evaluation.GroupView;
import com.iafenvoy.spb.runtime.evaluation.OptionView;
import com.iafenvoy.spb.runtime.evaluation.SelectionData;
import com.iafenvoy.spb.runtime.evaluation.SelectionEvaluator;
import com.iafenvoy.spb.runtime.outcome.FrozenTask;
import com.iafenvoy.spb.runtime.outcome.TaskState;
import com.iafenvoy.spb.runtime.session.SelectionSession;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * GameTests for the example data and the evaluation contract. They run in the separate test mod;
 * lifecycle regressions use mock players in an isolated GameTest world.
 *
 * <p>Run with {@code gradlew runGameTestServer} (or {@code gradlew runGameTestServer --args=...}).
 */
public final class SelectionGameTests {
    private static final Identifier EXAMPLE = id("example", "default");
    private static final Identifier POINTS = id("example", "points");
    private static final Identifier FATE = id("example", "fate");
    private static final Identifier STRENGTH = id("example", "strength");
    private static final Identifier WARRIOR = id("example", "warrior");
    private static final Identifier SCHOLAR = id("example", "scholar");
    private static final Identifier ARCHERY = id("example", "archery");
    private static final Identifier ALCHEMY = id("example", "alchemy");
    private static final Identifier INDEBTED = id("example", "indebted");

    private SelectionGameTests() {
    }

    public static void register(IEventBus bus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        Map<String, Consumer<GameTestHelper>> tests = new LinkedHashMap<>();
        tests.put("example_warrior", SelectionGameTests::warrior);
        tests.put("example_scholar", SelectionGameTests::scholar);
        tests.put("dynamic_limit", SelectionGameTests::dynamicLimit);
        tests.put("multi_resource", SelectionGameTests::multiResource);
        tests.put("presets", SelectionGameTests::presets);
        tests.put("order_independence", SelectionGameTests::orderIndependence);
        tests.put("definition_phase", SelectionGameTests::definitionPhase);
        tests.put("compare_epsilon", SelectionGameTests::compareEpsilon);
        tests.put("persistence_roundtrip", SelectionGameTests::persistenceRoundtrip);
        tests.put("snapshot_roundtrip", SelectionGameTests::snapshotRoundtrip);
        RegressionTests.register(tests);
        RefreshTests.register(tests);
        DatapackTests.register(tests);
        ScreenLayoutTests.register(tests);
        GeneratedKeyTests.register(tests);
        GroupLimitTests.register(tests);

        DeferredRegister<Consumer<GameTestHelper>> functions = DeferredRegister.create(Registries.TEST_FUNCTION, StartingPointBuyTest.MOD_ID);
        tests.forEach((name, test) -> functions.register(name, () -> test));
        functions.register(bus);
        bus.addListener((RegisterGameTestsEvent event) -> tests.forEach((name, _) -> {
            Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(id(StartingPointBuyTest.MOD_ID, name),
                    new TestEnvironmentDefinition.AllOf(List.of()));
            event.registerTest(id(StartingPointBuyTest.MOD_ID, name), new FunctionGameTestInstance(
                    ResourceKey.create(Registries.TEST_FUNCTION, id(StartingPointBuyTest.MOD_ID, name)),
                    new TestData<>(environment, id(StartingPointBuyTest.MOD_ID, "gametest/empty"), 100, 0, true)));
        }));
    }

    // ------------------------------------------------------------------ tests

    private static void warrior(GameTestHelper helper) {
        ResolvedPlan plan = resolve(helper);
        EvaluationResult result = evaluate(helper, plan, selection(
                WARRIOR, 1, STRENGTH, 4, ARCHERY, 1));
        helper.assertValueEqual(number(result, "points"), 0.0, "warrior spends all points");
        helper.assertValueEqual(number(result, "fate"), 1.0, "warrior fate");
        helper.assertValueEqual(number(result, "strength"), 5.0, "warrior strength includes the profession bonus");
        helper.assertTrue(result.canComplete(), "warrior selection completes: " + result.failures());
        helper.assertValueEqual(optionMax(result, "strength"), 4, "warrior unlocks the 4 point strength cap");
        helper.assertTrue(abilities(result).contains("example:sword_training"), "warrior grants sword training");
        helper.assertTrue(abilities(result).contains("example:archery"), "warrior grants archery");
        helper.assertTrue(abilities(result).contains("example:strong_body"), "strength 5 grants strong body");
        helper.assertTrue(actionKeys(result).contains("starter_food"), "warrior schedules the starter food action");
        helper.succeed();
    }

    private static void scholar(GameTestHelper helper) {
        ResolvedPlan plan = resolve(helper);
        EvaluationResult result = evaluate(helper, plan, selection(
                SCHOLAR, 1, STRENGTH, 2, ARCHERY, 1, ALCHEMY, 1));
        helper.assertValueEqual(number(result, "points"), 0.0, "scholar spends all points");
        helper.assertValueEqual(number(result, "fate"), 0.0, "scholar fate");
        helper.assertValueEqual(number(result, "strength"), 2.0, "scholar strength");
        helper.assertTrue(result.canComplete(), "scholar selection completes: " + result.failures());
        helper.assertValueEqual(optionMax(result, "strength"), 2, "scholar keeps the 2 point strength cap");
        helper.assertValueEqual(groupCount(result, "talents"), 2, "scholar unlocks a second talent");
        helper.succeed();
    }

    private static void dynamicLimit(GameTestHelper helper) {
        ResolvedPlan plan = resolve(helper);
        Map<Identifier, Integer> switched = selection(SCHOLAR, 1, STRENGTH, 4, ARCHERY, 1);
        EvaluationResult result = evaluate(helper, plan, switched);
        helper.assertValueEqual(number(result, "points"), 0.0, "switching profession keeps the points balanced");
        helper.assertValueEqual(optionMax(result, "strength"), 2, "scholar lowers the strength cap");
        helper.assertTrue(!result.canComplete(), "a strength quantity above the new cap blocks completion");
        helper.assertTrue(result.failures().stream().anyMatch(failure -> failure.message().getString().contains("at most")),
                "the quantity failure is reported");
        helper.succeed();
    }

    private static void multiResource(GameTestHelper helper) {
        ResolvedPlan plan = resolve(helper);
        EvaluationResult result = evaluate(helper, plan, selection(
                WARRIOR, 1, STRENGTH, 4, ARCHERY, 1, INDEBTED, 1));
        helper.assertValueEqual(number(result, "points"), 2.0, "the drawback adds two points");
        helper.assertValueEqual(number(result, "fate"), 0.0, "the drawback spends one fate");
        helper.assertTrue(!result.canComplete(), "remaining points still block completion even when fate is non-negative");
        helper.assertTrue(result.failures().stream().anyMatch(failure -> failure.path().contains("spend_all_points")),
                "the points completion rule is the failure");
        helper.succeed();
    }

    private static void presets(GameTestHelper helper) {
        ResolvedPlan plan = resolve(helper);
        for (String key : List.of("preset_1", "preset_2")) {
            Optional<Preset> preset = plan.preset(key);
            helper.assertTrue(preset.isPresent(), "preset exists: " + key);
            Map<Identifier, Integer> selections = new LinkedHashMap<>();
            preset.orElseThrow().selections().forEach((id, quantity) -> {
                if (quantity > 0) {
                    selections.put(id, quantity);
                }
            });
            helper.assertTrue(SnapshotFactory.matches(preset.orElseThrow(), selections), "preset matches its own selection");
            EvaluationResult result = evaluate(helper, plan, selections);
            helper.assertValueEqual(number(result, "points"), 0.0, "preset spends all points: " + key);
            helper.assertTrue(result.canComplete(), "preset completes: " + key + " " + result.failures());
        }
        // A preset is only an edit: it does not change the baseline.
        EvaluationResult first = evaluate(helper, plan, normalize(plan.preset("preset_1").orElseThrow()));
        EvaluationResult second = evaluate(helper, plan, normalize(plan.preset("preset_2").orElseThrow()));
        EvaluationResult again = evaluate(helper, plan, normalize(plan.preset("preset_1").orElseThrow()));
        helper.assertValueEqual(first.variables(), again.variables(), "switching presets back restores the same values");
        helper.assertTrue(second.canComplete(), "switching to the other preset is independent: " + second.failures());
        helper.succeed();
    }

    private static void orderIndependence(GameTestHelper helper) {
        ResolvedPlan plan = resolve(helper);
        Map<Identifier, Integer> forward = selection(WARRIOR, 1, STRENGTH, 4, ARCHERY, 1);
        Map<Identifier, Integer> reverse = new LinkedHashMap<>();
        List<Map.Entry<Identifier, Integer>> entries = new ArrayList<>(forward.entrySet());
        for (int index = entries.size() - 1; index >= 0; index--) {
            reverse.put(entries.get(index).getKey(), entries.get(index).getValue());
        }
        EvaluationResult a = evaluate(helper, plan, forward);
        EvaluationResult b = evaluate(helper, plan, reverse);
        helper.assertValueEqual(a.variables(), b.variables(), "the same selection has the same variables regardless of insertion order");
        helper.assertValueEqual(values(a), values(b), "the same selection has the same resources");
        helper.assertValueEqual(a.canComplete(), b.canComplete(), "the same selection has the same completion result");
        helper.succeed();
    }

    private static void definitionPhase(GameTestHelper helper) {
        Identifier optionId = id(StartingPointBuy.MOD_ID, "gametest_option");
        Identifier groupId = id(StartingPointBuy.MOD_ID, "gametest_group");
        Option option = new Option(Component.literal("bad"), Optional.empty(), Optional.empty(), 1,
                new Constant(1.0), Optional.empty(), AlwaysCondition.INSTANCE, AlwaysCondition.INSTANCE, List.of(),
                Map.of(resolve(helper).resources().get("example_strength"), new Expression("resource_example_strength")), List.of());
        ResolvedOption resolvedOption = new ResolvedOption(optionId, option, groupId);
        Group group = new Group(Component.literal("gametest"), Optional.empty(),
                CountMode.DISTINCT, new Constant(0.0), Optional.empty(), AlwaysCondition.INSTANCE, List.of());
        ResolvedGroup resolvedGroup = new ResolvedGroup(groupId, group, List.of(resolvedOption));
        ResolvedPlan plan = synthetic(List.of(),
                List.of(new ResourceBinding(resolve(helper).resources().get("example_points"), Optional.empty(), 1.0, CommitMode.REPLACE, true, Optional.empty())),
                Map.of(optionId, resolvedOption), List.of(resolvedGroup));
        List<Issue> issues = new ArrayList<>();
        PlanValidator.validate(plan, issues);
        helper.assertTrue(issues.stream().anyMatch(issue -> issue.path().contains("resource_changes") && issue.isError()),
                "contribution formulas may not read final resources: " + issues);
        helper.succeed();
    }

    private static void compareEpsilon(GameTestHelper helper) {
        EntityConditionContext context = new EntityConditionContext(null, FormulaContext.EMPTY);
        helper.assertTrue(new CompareEntityCondition(new Constant(0.0), CompareOperation.EQUAL,
                new Constant(1.0E-10), 1.0E-8).test(context), "values within epsilon are equal");
        helper.assertTrue(!new CompareEntityCondition(new Constant(0.0), CompareOperation.EQUAL,
                new Constant(1.0E-6), 1.0E-8).test(context), "values outside epsilon are not equal");
        helper.succeed();
    }

    private static void persistenceRoundtrip(GameTestHelper helper) {
        DynamicOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        DynamicOps<JsonElement> jsonOps = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        EntityAction action = EntityAction.CODEC.parse(jsonOps, stringToJson("{\"type\":\"saturn:give_item\",\"stack\":{\"id\":\"minecraft:bread\",\"count\":2}}"))
                .getOrThrow();
        Tag payload = EntityAction.CODEC.encodeStart(ops, action).getOrThrow();
        SelectionSession session = new SelectionSession(UUID.randomUUID(), EXAMPLE, 100L);
        session.setFingerprint("test");
        session.setSelections(Object2IntMap.ofEntries(Object2IntMap.entry(WARRIOR, 1), Object2IntMap.entry(STRENGTH, 4), Object2IntMap.entry(ARCHERY, 1)));
        session.setBaseline(Object2DoubleMap.ofEntries(Object2DoubleMap.entry(POINTS, 8.0), Object2DoubleMap.entry(FATE, 2.0), Object2DoubleMap.entry(STRENGTH, 0.0)));
        session.setDraft(Object2DoubleMap.ofEntries(Object2DoubleMap.entry(POINTS, 0.0), Object2DoubleMap.entry(FATE, 1.0), Object2DoubleMap.entry(STRENGTH, 5.0)));
        session.setTasks(List.of(new FrozenTask("task", "plan", "starter", "starter_food", 0, 1,
                TaskState.PENDING, payload, Map.of("resource_example_points", 0.0), "")));
        Tag encoded = SelectionSession.CODEC.encodeStart(ops, session).getOrThrow();
        SelectionSession decoded = SelectionSession.CODEC.parse(ops, encoded).getOrThrow();
        helper.assertValueEqual(decoded.id(), session.id(), "session id survives persistence");
        helper.assertValueEqual(decoded.selections(), session.selections(), "selection survives persistence");
        helper.assertValueEqual(decoded.tasks().size(), 1, "frozen task survives persistence");
        helper.assertValueEqual(decoded.tasks().getFirst().payload(), payload, "frozen action payload is byte-identical");
        helper.assertValueEqual(decoded.draft(), session.draft(), "draft resources survive persistence");
        helper.succeed();
    }

    private static void snapshotRoundtrip(GameTestHelper helper) {
        ResolvedPlan plan = resolve(helper);
        Object2IntMap<Identifier> selections = Object2IntMap.ofEntries(Object2IntMap.entry(WARRIOR, 1), Object2IntMap.entry(STRENGTH, 4), Object2IntMap.entry(ARCHERY, 1));
        SelectionSession session = new SelectionSession(UUID.randomUUID(), EXAMPLE, 100L);
        session.setSelections(selections);
        session.setRevision(7);
        EvaluationResult result = evaluate(helper, plan, selections);
        SelectionSnapshot snapshot = SnapshotFactory.build(result, session);
        DynamicOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag encoded = SelectionSnapshot.CODEC.encodeStart(ops, snapshot).getOrThrow();
        SelectionSnapshot decoded =
                SelectionSnapshot.CODEC.parse(ops, encoded).getOrThrow();
        helper.assertValueEqual(decoded.sessionId(), snapshot.sessionId(), "snapshot keeps the session id");
        helper.assertValueEqual(decoded.revision(), 7, "snapshot keeps the revision");
        helper.assertValueEqual(decoded.title(), plan.plan().title(), "snapshot preserves the configured plan title");
        helper.assertValueEqual(decoded.options().size(), snapshot.options().size(), "snapshot keeps all option rows");
        helper.assertValueEqual(decoded.resources().size(), 3, "snapshot carries all three resources");
        helper.assertTrue(decoded.canComplete(), "a completed snapshot stays completable after roundtrip");
        helper.assertValueEqual(decoded.failures().size(), 0, "a valid selection has no failures");
        ResolvedPlan untitled = PlanIndex.find(helper.getLevel().registryAccess(), id("spb_test", "optional")).orElseThrow();
        helper.assertTrue(untitled.plan().title().isEmpty(), "plan title is optional in real datapack definitions");
        SelectionSnapshot terminal = SnapshotFactory.buildTerminal(untitled,
                new SelectionSession(UUID.randomUUID(), untitled.id(), 100L));
        Tag untitledEncoded = SelectionSnapshot.CODEC.encodeStart(ops, terminal).getOrThrow();
        helper.assertTrue(!((CompoundTag) untitledEncoded).contains("title"),
                "untitled snapshots omit the title field");
        helper.assertTrue(SelectionSnapshot.CODEC.parse(ops, untitledEncoded).getOrThrow().title().isEmpty(),
                "absent title stays absent across snapshot serialization");
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    private static ResolvedPlan resolve(GameTestHelper helper) {
        ResolvedPlan plan = PlanIndex.find(helper.getLevel().registryAccess(), EXAMPLE)
                .orElseThrow(() -> new IllegalStateException("example:default is not installed"));
        helper.assertTrue(!plan.hasErrors(), "example:default has definition errors: " + plan.errors());
        return plan;
    }

    private static EvaluationResult evaluate(GameTestHelper helper, ResolvedPlan plan, Map<Identifier, Integer> selections) {
        return SelectionEvaluator.evaluate(plan, null, helper.getLevel().registryAccess(),
                new SelectionData(Map.of(), Map.of(POINTS, 8.0, FATE, 2.0, STRENGTH, 0.0), selections));
    }

    private static ResolvedPlan synthetic(List<CompletionRule> rules, List<ResourceBinding> resources,
                                          Map<Identifier, ResolvedOption> options,
                                          List<ResolvedGroup> groups) {
        Identifier id = id(StartingPointBuy.MOD_ID, "gametest");
        Plan plan = new Plan(Optional.of(Component.literal("gametest")), Optional.empty(), Activation.DEFAULT, Map.of(),
                resources, List.of(), List.of(), rules, List.of(), UiSettings.DEFAULT);
        return new ResolvedPlan(id, plan, Map.of(), groups, options, List.of(), List.of(), "gametest");
    }

    private static Map<Identifier, Integer> normalize(Preset preset) {
        Map<Identifier, Integer> selections = new LinkedHashMap<>();
        preset.selections().forEach((id, quantity) -> {
            if (quantity > 0) {
                selections.put(id, quantity);
            }
        });
        return selections;
    }

    private static Object2IntMap<Identifier> selection(Object... pairs) {
        Object2IntMap<Identifier> result = new Object2IntOpenHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put((Identifier) pairs[index], (int) pairs[index + 1]);
        }
        return result;
    }

    private static Map<String, Double> values(EvaluationResult result) {
        Map<String, Double> values = new LinkedHashMap<>();
        result.resources().forEach(view -> values.put(view.key(), view.value()));
        return values;
    }

    private static double number(EvaluationResult result, String key) {
        return values(result).getOrDefault("example_" + key, Double.NaN);
    }

    private static int optionMax(EvaluationResult result, String key) {
        return result.plan().orderedOptions().stream()
                .filter(option -> option.key().equals("example_" + key))
                .map(option -> result.options().get(option.id()))
                .filter(Objects::nonNull)
                .map(OptionView::maxCount)
                .findFirst()
                .orElse(-1);
    }

    private static int groupCount(EvaluationResult result, String key) {
        return result.groups().stream()
                .filter(group -> group.key().equals("example_" + key))
                .mapToInt(GroupView::count)
                .findFirst()
                .orElse(-1);
    }

    private static List<String> abilities(EvaluationResult result) {
        List<String> abilities = new ArrayList<>();
        result.outcomes().forEach(outcome -> outcome.abilities().forEach(id -> abilities.add(id.toString())));
        return abilities;
    }

    private static List<String> actionKeys(EvaluationResult result) {
        List<String> keys = new ArrayList<>();
        result.outcomes().forEach(outcome -> outcome.actions().forEach(action -> keys.add(action.key())));
        return keys;
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    private static JsonElement stringToJson(String json) {
        return JsonParser.parseString(json);
    }
}
