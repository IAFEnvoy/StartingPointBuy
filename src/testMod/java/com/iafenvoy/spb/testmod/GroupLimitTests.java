package com.iafenvoy.spb.testmod;

import com.iafenvoy.spb.data.resolution.PlanIndex;
import com.iafenvoy.spb.network.SnapshotFactory;
import com.iafenvoy.spb.network.model.SelectionIntent;
import com.iafenvoy.spb.runtime.evaluation.EvaluationResult;
import com.iafenvoy.spb.runtime.evaluation.SelectionData;
import com.iafenvoy.spb.runtime.evaluation.SelectionEvaluator;
import com.iafenvoy.spb.runtime.session.SelectionSession;
import com.iafenvoy.spb.runtime.session.SessionService;
import com.iafenvoy.spb.runtime.session.SessionState;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Group limits are independent of each option's repeat limit and can change with the draft.
 */
final class GroupLimitTests {
    private static final Identifier BEAD = Identifier.parse("spb_test:bead");
    private static final Identifier RUNE = Identifier.parse("spb_test:rune");
    private static final Identifier EXTRA = Identifier.parse("spb_test:wide_choice");

    private GroupLimitTests() {
    }

    static void register(Map<String, Consumer<GameTestHelper>> tests) {
        tests.put("group_limits/repeated_single_kind", GroupLimitTests::repeatedSingleKind);
        tests.put("group_limits/total_quantity", GroupLimitTests::totalQuantity);
        tests.put("group_limits/pair_and_zero", GroupLimitTests::pairAndZero);
        tests.put("group_limits/dynamic_replacement", GroupLimitTests::dynamicReplacement);
    }

    private static ServerPlayer open(GameTestHelper helper, String name) {
        ServerPlayer player = TestPlayers.create(helper);
        SessionService.open(player, TestPlayers.plan(player, Identifier.parse("spb_test:" + name)));
        return player;
    }

    private static SelectionSession session(ServerPlayer player) {
        return SessionService.attachment(player).session();
    }

    private static void select(ServerPlayer player, Identifier option, int quantity) {
        SelectionSession session = session(player);
        SessionService.handle(player, SelectionIntent.setQuantity(session.id(), session.revision(), option, quantity));
    }

    private static EvaluationResult evaluate(ServerPlayer player) {
        SelectionSession session = session(player);
        return SelectionEvaluator.evaluate(PlanIndex.find(player.registryAccess(), session.planId()).orElseThrow(), player,
                new SelectionData(session.inputs(), session.baseline(), session.selections()));
    }

    private static void repeatedSingleKind(GameTestHelper helper) {
        ServerPlayer player = open(helper, "choice_single");
        SelectionSession session = session(player);
        SessionService.handle(player, SelectionIntent.applyPreset(session.id(), session.revision(), "preset_1"));
        helper.assertValueEqual(session.quantity(BEAD), 3, "a one-kind group accepts a preset with three copies");
        helper.assertTrue(evaluate(player).canComplete(), "one repeated kind satisfies a distinct maximum of one");
        int revision = session.revision();
        Map<Identifier, Double> draft = Map.copyOf(session.draft());
        SessionService.handle(player, SelectionIntent.preview(session.id(), revision, RUNE, 3));
        helper.assertValueEqual(session.quantity(BEAD), 3, "replacement preview leaves the actual selection intact");
        helper.assertValueEqual(session.revision(), revision, "preview does not advance the revision");
        helper.assertValueEqual(session.draft(), draft, "preview preserves draft resources");
        select(player, RUNE, 3);
        helper.assertValueEqual(session.quantity(BEAD), 0, "a new kind replaces the previous kind at a limit of one");
        helper.assertValueEqual(session.quantity(RUNE), 3, "replacement retains the requested repeat quantity");
        select(player, RUNE, 4);
        helper.assertTrue(evaluate(player).canComplete(), "changing the selected kind's quantity remains allowed");
        var snapshot = SnapshotFactory.build(evaluate(player), session);
        helper.assertValueEqual(snapshot.groups().getFirst().count(), 1, "the group header counts kinds");
        helper.assertValueEqual(snapshot.groups().getFirst().max(), 1, "the group header uses the evaluated maximum");
        helper.assertTrue(snapshot.options().stream().anyMatch(option -> option.id().equals(RUNE) && option.repeatable()),
                "a one-kind group still exposes quantity controls");
        SessionService.handle(player, SelectionIntent.submit(session.id(), session.revision()));
        helper.assertValueEqual(session.state(), SessionState.COMMITTED, "a repeated single kind can be committed");
        helper.succeed();
    }

    private static void totalQuantity(GameTestHelper helper) {
        ServerPlayer player = open(helper, "choice_total");
        select(player, BEAD, 2);
        helper.assertTrue(!evaluate(player).canComplete(), "a total limit of one rejects two copies at completion");
        helper.assertValueEqual(session(player).quantity(BEAD), 2, "the invalid draft stays editable");
        select(player, RUNE, 1);
        helper.assertValueEqual(session(player).quantity(BEAD), 0, "a new kind replaces the old choice at a total limit of one");
        helper.assertTrue(evaluate(player).canComplete(), "one copy satisfies the total limit");
        helper.succeed();
    }

    private static void pairAndZero(GameTestHelper helper) {
        ServerPlayer player = open(helper, "choice_pair");
        select(player, BEAD, 2);
        select(player, RUNE, 2);
        helper.assertTrue(evaluate(player).canComplete(), "a maximum of two keeps both selected kinds");
        select(player, EXTRA, 1);
        helper.assertTrue(!evaluate(player).canComplete(), "a third kind blocks completion without dropping choices");
        helper.assertValueEqual(session(player).quantity(BEAD), 2, "exceeding two does not replace the first kind");
        select(player, EXTRA, 0);
        helper.assertTrue(evaluate(player).canComplete(), "removing the excess kind repairs the draft");
        SessionService.unlockAll(player);
        SessionService.open(player, TestPlayers.plan(player, Identifier.parse("spb_test:choice_zero")));
        helper.assertTrue(evaluate(player).canComplete(), "a zero limit allows an empty selection");
        select(player, BEAD, 1);
        helper.assertTrue(!evaluate(player).canComplete(), "a zero limit blocks any selected option");
        helper.succeed();
    }

    private static void dynamicReplacement(GameTestHelper helper) {
        ServerPlayer player = open(helper, "choice_dynamic");
        select(player, BEAD, 2);
        select(player, EXTRA, 1);
        helper.assertValueEqual(session(player).quantity(BEAD), 2, "a candidate that expands the limit preserves the previous choice");
        helper.assertTrue(evaluate(player).canComplete(), "the candidate's expanded maximum allows both kinds");
        select(player, RUNE, 1);
        helper.assertTrue(!evaluate(player).canComplete(), "three kinds exceed the expanded maximum of two");
        select(player, EXTRA, 0);
        helper.assertTrue(!evaluate(player).canComplete(), "shrinking the maximum preserves the invalid choices for correction");
        select(player, RUNE, 0);
        select(player, RUNE, 2);
        helper.assertValueEqual(session(player).quantity(BEAD), 0, "replacement resumes after the maximum returns to one");
        helper.assertTrue(evaluate(player).canComplete(), "a single repeated kind satisfies the reduced maximum");
        helper.succeed();
    }
}
