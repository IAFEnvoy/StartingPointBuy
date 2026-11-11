package com.iafenvoy.spb.testmod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.iafenvoy.saturn.util.formula.FormulaVariables;
import com.iafenvoy.spb.data.Group;
import com.iafenvoy.spb.data.Option;
import com.iafenvoy.spb.data.Plan;
import com.iafenvoy.spb.data.Preset;
import com.iafenvoy.spb.data.resolution.PlanIndex;
import com.iafenvoy.spb.data.resolution.ResolvedPlan;
import com.iafenvoy.spb.network.SnapshotFactory;
import com.iafenvoy.spb.network.model.SelectionSnapshot;
import com.iafenvoy.spb.runtime.evaluation.EvaluationResult;
import com.iafenvoy.spb.runtime.evaluation.SelectionData;
import com.iafenvoy.spb.runtime.evaluation.SelectionEvaluator;
import com.iafenvoy.spb.runtime.session.SelectionSession;
import com.iafenvoy.spb.util.DefinitionCodecs;
import com.iafenvoy.spb.util.DefinitionNames;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Generated names are exercised through real loaded definitions, formulas and network snapshots.
 */
final class GeneratedKeyTests {
    static void register(Map<String, Consumer<GameTestHelper>> tests) {
        tests.put("generated_key_codecs", GeneratedKeyTests::codecs);
        tests.put("generated_key_variables", GeneratedKeyTests::variables);
        tests.put("generated_key_stability", GeneratedKeyTests::stability);
        tests.put("generated_preset_identity", GeneratedKeyTests::presets);
    }

    private static ResolvedPlan plan(GameTestHelper helper) {
        return PlanIndex.find(helper.getLevel().registryAccess(), Identifier.parse("example:default")).orElseThrow();
    }

    private static void codecs(GameTestHelper helper) {
        ResolvedPlan resolved = plan(helper);
        RegistryOps<JsonElement> ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonObject plan = Plan.DIRECT_CODEC.encodeStart(ops, resolved.plan()).getOrThrow().getAsJsonObject();
        plan.getAsJsonArray("resources").forEach(binding -> helper.assertTrue(!binding.getAsJsonObject().has("key"), "resource bindings encode without key"));
        plan.getAsJsonArray("presets").forEach(preset -> helper.assertTrue(!preset.getAsJsonObject().has("key"), "presets encode without key"));
        resolved.groups().forEach(group -> helper.assertTrue(!Group.DIRECT_CODEC.encodeStart(ops, group.definition()).getOrThrow()
                .getAsJsonObject().has("key"), "groups encode without key"));
        resolved.orderedOptions().forEach(option -> helper.assertTrue(!Option.DIRECT_CODEC.encodeStart(ops, option.definition()).getOrThrow()
                .getAsJsonObject().has("key"), "options encode without key"));
        helper.assertTrue(DefinitionCodecs.INPUT_NAME.parse(JsonOps.INSTANCE, new JsonPrimitive("level2")).result().isPresent(), "named inputs remain supported");
        helper.assertTrue(DefinitionCodecs.INPUT_NAME.parse(JsonOps.INSTANCE, new JsonPrimitive("bad-name")).error().isPresent(), "input names cannot inject formula operators");
        String alias = DefinitionNames.key(Identifier.parse("my-pack:skills/fire.ball"));
        helper.assertValueEqual(alias, "my_pack_skills_fire_ball", "all registry-id separators are normalised");
        helper.assertTrue(FormulaVariables.isValidName(DefinitionNames.selectedVariable(alias)), "generated aliases are usable in Saturn formulas");
        helper.succeed();
    }

    private static EvaluationResult evaluate(GameTestHelper helper, ResolvedPlan plan) {
        return SelectionEvaluator.evaluate(plan, null, helper.getLevel().registryAccess(), new SelectionData(Map.of(),
                Map.of(Identifier.parse("example:points"), 8.0, Identifier.parse("example:fate"), 2.0, Identifier.parse("example:strength"), 0.0),
                plan.presets().getFirst().selections()));
    }

    private static void variables(GameTestHelper helper) {
        EvaluationResult result = evaluate(helper, plan(helper));
        helper.assertTrue(result.canComplete(), "real formulas referencing generated aliases complete the plan");
        helper.assertValueEqual(result.variables().get("selected_example_warrior"), 1.0, "option variable uses the full registry id");
        helper.assertValueEqual(result.variables().get("base_example_points"), 8.0, "baseline uses the resource registry id");
        helper.assertValueEqual(result.variables().get("resource_example_points"), 0.0, "final balance uses the same generated resource alias");
        helper.assertValueEqual(result.variables().get("group_example_profession_distinct"), 1.0, "group variable uses its registry id");
        helper.assertTrue(!result.variables().containsKey("selected_warrior"), "removed manual aliases do not shadow generated variables");
        helper.succeed();
    }

    private static void stability(GameTestHelper helper) {
        ResolvedPlan original = plan(helper);
        Plan p = original.plan();
        Plan reversed = new Plan(p.title(), p.description(), p.activation(), p.inputs(), p.resources().reversed(), p.presets(),
                p.groups().reversed(), p.completionRules(), p.outcomes(), p.ui());
        ResolvedPlan reordered = PlanIndex.resolve(helper.getLevel().registryAccess(), original.id(), reversed);
        helper.assertTrue(!reordered.hasErrors(), "reordered definitions still resolve");
        original.orderedOptions().forEach(option -> helper.assertValueEqual(reordered.option(option.id()).orElseThrow().key(), option.key(), "option aliases do not depend on ordering"));
        helper.assertValueEqual(evaluate(helper, original).variables(), evaluate(helper, reordered).variables(), "reordering bindings/groups does not rename formula variables");
        helper.succeed();
    }

    private static void presets(GameTestHelper helper) {
        ResolvedPlan plan = plan(helper);
        Preset first = plan.presets().getFirst();
        SelectionSession session = new SelectionSession(UUID.randomUUID(), plan.id(), 0);
        session.setSelections(new Object2IntOpenHashMap<>(first.selections()));
        SelectionSnapshot snapshot = SnapshotFactory.build(evaluate(helper, plan), session);
        helper.assertValueEqual(snapshot.presets().getFirst().key(), "preset_1", "first preset is automatically numbered");
        helper.assertValueEqual(snapshot.presets().get(1).key(), "preset_2", "second preset has its own generated identity");
        helper.assertValueEqual(snapshot.presetKey(), "preset_1", "matched preset uses the same generated identity");
        helper.assertTrue(plan.preset("preset_1").orElseThrow() == first, "server lookup accepts the generated preset identity");
        helper.assertTrue(plan.preset("warrior_start").isEmpty(), "removed manual preset keys are not accepted");
        Plan p = plan.plan();
        Plan duplicate = new Plan(p.title(), p.description(), p.activation(), p.inputs(), p.resources(), List.of(first, first),
                p.groups(), p.completionRules(), p.outcomes(), p.ui());
        RegistryOps<JsonElement> ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        helper.assertTrue(Plan.DIRECT_CODEC.encodeStart(ops, duplicate).result().isPresent(), "identical preset contents are valid because positions identify them");
        SelectionSnapshot terminal = SnapshotFactory.buildTerminal(plan, session);
        helper.assertValueEqual(terminal.presetKey(), snapshot.presetKey(), "terminal and editable snapshots agree on preset identities");
        helper.succeed();
    }
}
