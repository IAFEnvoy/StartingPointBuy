package com.iafenvoy.spb.testmod;

import com.iafenvoy.saturn.attachment.ResourceHolderAttachment;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.saturn.registry.SaturnAttachments;
import com.iafenvoy.spb.data.resolution.PlanIndex;
import com.iafenvoy.spb.data.resolution.ResolvedPlan;
import com.iafenvoy.spb.network.model.SelectionIntent;
import com.iafenvoy.spb.runtime.outcome.FrozenTask;
import com.iafenvoy.spb.runtime.session.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Recovery from a stale draft must work without closing a mandatory screen.
 */
final class RefreshTests {
    private static final Identifier PLAN = Identifier.parse("example:default");
    private static final Identifier POINTS = Identifier.parse("example:points");
    private static final Identifier STRENGTH = Identifier.parse("example:strength");

    static void register(Map<String, Consumer<GameTestHelper>> tests) {
        tests.put("refresh_definition", RefreshTests::definition);
        tests.put("refresh_resource_conflict", RefreshTests::resourceConflict);
        tests.put("refresh_stale_requests", RefreshTests::staleRequests);
        tests.put("refresh_committed", RefreshTests::committed);
        tests.put("refresh_initial", RefreshTests::initial);
        tests.put("refresh_fingerprint_stability", RefreshTests::fingerprintStability);
        tests.put("refresh_intent_codec", RefreshTests::intentCodec);
    }

    private static ResolvedPlan plan(ServerPlayer player) {
        return PlanIndex.find(player.registryAccess(), SessionService.attachment(player).session().planId()).orElseThrow();
    }

    private static ServerPlayer open(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        SessionService.open(player, TestPlayers.plan(player, PLAN));
        SelectionSession session = SessionService.attachment(player).session();
        SessionService.handle(player, SelectionIntent.applyPreset(session.id(), session.revision(), "preset_1"));
        return player;
    }

    private static SelectionIntent refresh(SelectionSession session) {
        return SelectionIntent.refresh(session.id(), session.revision());
    }

    private static void definition(GameTestHelper helper) {
        ServerPlayer player = open(helper);
        SelectionSession session = SessionService.attachment(player).session();
        Map<Identifier, Integer> selections = Map.copyOf(session.selections());
        session.setFingerprint("old-definition");
        SessionService.handle(player, SelectionIntent.submit(session.id(), session.revision()));
        helper.assertValueEqual(session.state(), SessionState.NEEDS_REFRESH, "old definitions require refresh before commit");
        SessionService.handle(player, SelectionIntent.close(session.id(), session.revision()));
        helper.assertTrue(SessionService.isScreenProtected(player), "mandatory stale screen stays open and protected");
        float health = player.getHealth();
        player.hurtServer(helper.getLevel(), player.damageSources().fellOutOfWorld(), 100.0F);
        helper.assertValueEqual(player.getHealth(), health, "stale screen still ignores void damage");
        int revision = session.revision();
        SessionService.handle(player, refresh(session));
        helper.assertTrue(SessionService.attachment(player).session() == session, "refresh keeps the same session");
        helper.assertValueEqual(session.state(), SessionState.EDITING, "refresh restores editing without closing");
        helper.assertValueEqual(session.revision(), revision + 1, "refresh advances the revision once");
        helper.assertValueEqual(session.selections(), selections, "refresh keeps recognised choices");
        helper.assertValueEqual(session.fingerprint(), plan(player).fingerprint(), "refresh adopts the current definition");
        helper.assertTrue(SessionService.isScreenProtected(player), "refresh preserves screen protection");
        SessionService.handle(player, SelectionIntent.setQuantity(session.id(), session.revision(), STRENGTH, 3));
        SessionService.handle(player, SelectionIntent.setQuantity(session.id(), session.revision(), STRENGTH, 4));
        SessionService.handle(player, SelectionIntent.submit(session.id(), session.revision()));
        helper.assertValueEqual(session.state(), SessionState.COMMITTED, "the refreshed draft can be edited and submitted");
        helper.assertTrue(!SessionService.isScreenProtected(player), "confirmation releases protection");
        SessionService.onLogout(player);
        helper.succeed();
    }

    private static void resourceConflict(GameTestHelper helper) {
        ServerPlayer player = open(helper);
        SelectionSession session = SessionService.attachment(player).session();
        Holder<Resource> resource = plan(player).resources().get("example_points");
        ResourceHolderAttachment real = player.getData(SaturnAttachments.RESOURCE_HOLDER);
        real.set(resource, 7.0);
        SessionService.handle(player, SelectionIntent.submit(session.id(), session.revision()));
        helper.assertValueEqual(session.state(), SessionState.NEEDS_REFRESH, "external writes require refresh");
        helper.assertValueEqual(real.get(resource), 7.0, "a conflict cannot overwrite the external value");
        SessionService.handle(player, refresh(session));
        helper.assertValueEqual(session.baseline().get(POINTS), 7.0, "refresh adopts current real points");
        helper.assertValueEqual(session.realSnapshot().getDouble(POINTS), 7.0, "refresh updates the conflict snapshot");
        helper.assertValueEqual(session.draft().getDouble(POINTS), -1.0, "retained selections are recalculated against the new baseline");
        SessionService.handle(player, SelectionIntent.setQuantity(session.id(), session.revision(), STRENGTH, 3));
        SessionService.handle(player, SelectionIntent.submit(session.id(), session.revision()));
        helper.assertValueEqual(session.state(), SessionState.COMMITTED, "a corrected refreshed draft no longer conflicts");
        helper.assertValueEqual(real.get(resource), 0.0, "only the successful commit writes the final balance");
        SessionService.onLogout(player);
        helper.succeed();
    }

    private static void staleRequests(GameTestHelper helper) {
        ServerPlayer player = open(helper);
        SelectionSession session = SessionService.attachment(player).session();
        session.setFingerprint("stale");
        SessionService.handle(player, SelectionIntent.submit(session.id(), session.revision()));
        Map<Identifier, Double> baseline = Map.copyOf(session.baseline());
        int revision = session.revision();
        Holder<Resource> resource = plan(player).resources().get("example_points");
        player.getData(SaturnAttachments.RESOURCE_HOLDER).set(resource, 6.0);
        SessionService.handle(player, SelectionIntent.refresh(UUID.randomUUID(), revision));
        SessionService.handle(player, SelectionIntent.refresh(session.id(), revision - 1));
        SessionService.handle(player, SelectionIntent.refresh(session.id(), revision + 1));
        helper.assertValueEqual(session.baseline(), baseline, "wrong session and stale/future revisions cannot rebaseline");
        helper.assertValueEqual(session.state(), SessionState.NEEDS_REFRESH, "invalid refresh requests keep recovery pending");
        SelectionIntent request = refresh(session);
        SessionService.handle(player, request);
        Map<Identifier, Double> refreshed = Map.copyOf(session.baseline());
        player.getData(SaturnAttachments.RESOURCE_HOLDER).set(resource, 3.0);
        SessionService.handle(player, request);
        SessionService.handle(player, refresh(session));
        helper.assertValueEqual(session.baseline(), refreshed, "replayed requests and refreshes while editing cannot change the baseline");
        helper.assertValueEqual(session.revision(), revision + 1, "only the accepted recovery advances the revision");
        SessionService.onLogout(player);
        helper.succeed();
    }

    private static void committed(GameTestHelper helper) {
        ServerPlayer player = open(helper);
        SelectionSession session = SessionService.attachment(player).session();
        SessionService.handle(player, SelectionIntent.submit(session.id(), session.revision()));
        CommitRecord commit = session.commit();
        helper.assertTrue(commit != null, "fixture has a frozen commit");
        int revision = session.revision();
        Map<Identifier, Double> baseline = Map.copyOf(session.baseline());
        List<FrozenTask> tasks = List.copyOf(session.tasks());
        SessionService.handle(player, refresh(session));
        helper.assertTrue(!SessionService.refresh(player), "command cannot refresh a committed draft");
        // A legacy/damaged save must not bypass the frozen commit or completion ledger.
        session.setState(SessionState.NEEDS_REFRESH);
        SessionService.handle(player, refresh(session));
        SessionService.attachment(player).complete(TestPlayers.plan(player, PLAN), new CompletionRecord(commit.batchId(), 1L, true));
        session.setState(SessionState.COMPLETED);
        SessionService.handle(player, refresh(session));
        helper.assertTrue(session.commit() == commit, "refresh never replaces the frozen reward batch");
        helper.assertValueEqual(session.tasks(), tasks, "refresh never recreates pending rewards");
        helper.assertValueEqual(session.baseline(), baseline, "committed baseline stays immutable");
        helper.assertValueEqual(session.revision(), revision, "rejected refreshes do not advance revisions");
        helper.assertTrue(!SessionService.isScreenProtected(player), "late refresh cannot reopen a closed result");
        SessionService.onLogout(player);
        helper.succeed();
    }

    private static void initial(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        SessionService.open(player, TestPlayers.plan(player, Identifier.parse("spb_test:readonly")));
        SelectionSession session = SessionService.attachment(player).session();
        ResolvedPlan plan = plan(player);
        Holder<Resource> resource = plan.resources().get("spb_test_budget");
        player.getData(SaturnAttachments.RESOURCE_HOLDER).set(resource, 2.0);
        session.setFingerprint("old");
        SessionService.handle(player, SelectionIntent.reset(session.id(), session.revision()));
        SessionService.handle(player, refresh(session));
        helper.assertValueEqual(session.state(), SessionState.EDITING, "initial-value plan recovers from stale definitions");
        helper.assertValueEqual(session.baseline().get(Identifier.parse("spb_test:budget")), 2.0,
                "GUI refresh uses the real balance instead of replaying initial=5");
        Map<String, Double> captured = Map.copyOf(session.inputs());
        SessionService.handle(player, refresh(session));
        helper.assertValueEqual(session.inputs(), captured, "duplicate refresh does not reroll inputs");
        SessionService.onLogout(player);
        helper.succeed();
    }

    private static void fingerprintStability(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        for (int i = 0; i < 3; i++) {
            RegistryAccess.Frozen reloaded = DatapackLoadTests.load(helper, helper.getLevel().getServer().getResourceManager());
            for (ResolvedPlan original : PlanIndex.all(access)) {
                helper.assertValueEqual(PlanIndex.find(reloaded, original.id()).orElseThrow().fingerprint(), original.fingerprint(),
                        original.id() + ": reloading unchanged definitions keeps the fingerprint");
            }
        }
        helper.succeed();
    }

    private static void intentCodec(GameTestHelper helper) {
        SelectionIntent intent = SelectionIntent.refresh(UUID.randomUUID(), 42);
        ByteBuf buffer = Unpooled.buffer();
        try {
            SelectionIntent.STREAM_CODEC.encode(buffer, intent);
            helper.assertValueEqual(SelectionIntent.STREAM_CODEC.decode(buffer), intent, "refresh intent survives the network codec");
            helper.assertTrue(!buffer.isReadable(), "refresh consumes exactly one intent");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }
}
