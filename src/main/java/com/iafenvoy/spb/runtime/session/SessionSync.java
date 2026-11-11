package com.iafenvoy.spb.runtime.session;

import com.iafenvoy.spb.data.resolution.ResolvedPlan;
import com.iafenvoy.spb.network.SnapshotFactory;
import com.iafenvoy.spb.network.model.SelectionSnapshot;
import com.iafenvoy.spb.network.payload.SnapshotS2CPayload;
import com.iafenvoy.spb.runtime.evaluation.EvaluationResult;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Projects current server state without changing draft resources or opening a closed screen.
 */
final class SessionSync {
    private SessionSync() {
    }

    static void send(ServerPlayer player, EvaluationResult evaluation, SelectionSession session) {
        send(player, session, SnapshotFactory.build(evaluation, session));
    }

    static void sendCurrent(ServerPlayer player, SelectionSession session) {
        ResolvedPlan plan = SessionService.resolvePlan(player, session.planId());
        if (plan == null) return;
        if (session.commit() == null && (session.state().isEditable() || session.state() == SessionState.NEEDS_REFRESH)) {
            send(player, new SessionEditor(player, session, plan).evaluate(), session);
        } else {
            send(player, session, SnapshotFactory.buildTerminal(plan, session));
        }
    }

    private static void send(ServerPlayer player, SelectionSession session, SelectionSnapshot snapshot) {
        PacketDistributor.sendToPlayer(player, new SnapshotS2CPayload(snapshot, session.screenOpen()));
    }
}
