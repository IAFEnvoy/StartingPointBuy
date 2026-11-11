package com.iafenvoy.spb.event;

import com.iafenvoy.saturn.data.ability.Ability;
import com.iafenvoy.saturn.event.CollectAbilityGrantsEvent;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.spb.StartingPointBuy;
import com.iafenvoy.spb.runtime.outcome.FrozenGrant;
import com.iafenvoy.spb.runtime.outcome.OutcomeService;
import com.iafenvoy.spb.runtime.session.SessionAttachment;
import com.iafenvoy.spb.runtime.session.SessionService;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server-side entry points: login restore, per-player tick and permanent ability contributions.
 */
@EventBusSubscriber
public final class PlayerSessionEvents {
    private PlayerSessionEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            SessionService.onLogin(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            SessionService.onLogout(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInvulnerability(EntityInvulnerabilityCheckEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SessionService.isScreenProtected(player))
            event.setInvulnerable(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SessionService.isScreenProtected(player))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player)
            OutcomeService.tick(player);
    }

    /**
     * Contributes the frozen abilities while the session is in its applying/completed phase. Each
     * outcome has its own Saturn grant source, so revoking this plan never removes an ability granted
     * by another system.
     */
    @SubscribeEvent
    public static void collectGrants(CollectAbilityGrantsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SessionAttachment attachment = SessionService.attachment(player);
        attachment.captureActiveGrants(player.registryAccess());
        for (FrozenGrant grant : attachment.grants().values().stream().flatMap(List::stream).toList()) {
            List<Holder<Ability>> resolved = new ArrayList<>();
            Registry<Ability> registry = player.level().registryAccess().lookupOrThrow(SaturnResourceKeys.ABILITY);
            grant.abilities().stream().map(registry::get).flatMap(Optional::stream).forEach(resolved::add);
            if (resolved.size() != grant.abilities().size())
                StartingPointBuy.LOGGER.warn("Starting-point grant '{}' of {} references a missing ability", grant.source(), player.getScoreboardName());
            if (!resolved.isEmpty()) event.wantResolved(grant.source(), resolved);
        }
    }
}
