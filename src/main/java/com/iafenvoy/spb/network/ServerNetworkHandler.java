package com.iafenvoy.spb.network;

import com.iafenvoy.spb.network.payload.IntentC2SPayload;
import com.iafenvoy.spb.runtime.session.SessionService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server handlers for server-bound payloads. Every request is checked here: the payload only says what
 * the client wants, never what it computed, and a small per-tick budget rejects intent spam.
 */
public final class ServerNetworkHandler {
    private static final int MAX_INTENTS_PER_TICK = 64;
    private static final Map<UUID, long[]> BUDGET = new HashMap<>();

    private ServerNetworkHandler() {
    }

    public static void onIntent(IntentC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        long now = player.level().getGameTime();
        long[] budget = BUDGET.computeIfAbsent(player.getUUID(), key -> new long[]{-1L, 0L});
        if (budget[0] != now) {
            budget[0] = now;
            budget[1] = 0;
        }
        if (++budget[1] > MAX_INTENTS_PER_TICK) {
            return;
        }
        SessionService.handle(player, payload.intent());
    }
}
