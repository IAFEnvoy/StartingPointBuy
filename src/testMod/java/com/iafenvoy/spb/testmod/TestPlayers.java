package com.iafenvoy.spb.testmod;

import com.iafenvoy.spb.data.Plan;
import com.iafenvoy.spb.registry.SpbResourceKeys;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.UUID;

/**
 * Isolated server players with a no-op connection, without entering the online player list.
 */
final class TestPlayers {
    private TestPlayers() {
    }

    static Holder.Reference<Plan> plan(ServerPlayer player, Identifier id) {
        return player.registryAccess().lookupOrThrow(SpbResourceKeys.PLAN)
                .getOrThrow(ResourceKey.create(SpbResourceKeys.PLAN, id));
    }

    static ServerPlayer create(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "spb-test-player"));
        player.connection.markClientLoaded();
        // FakePlayer starts invulnerable; damage tests must exercise SPB's protection instead.
        player.setInvulnerable(false);
        player.setPos(helper.absoluteVec(new Vec3(0.5, 2.0, 0.5)));
        return player;
    }
}
