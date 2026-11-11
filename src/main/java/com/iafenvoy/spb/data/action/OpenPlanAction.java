package com.iafenvoy.spb.data.action;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.iafenvoy.spb.data.Plan;
import com.iafenvoy.spb.runtime.session.SessionOpenSource;
import com.iafenvoy.spb.runtime.session.SessionService;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * {@code spb:open_plan}: opens the selection screen for the acting player. Usable from any Saturn
 * action context (item use, ability trigger, timeline, ...), which is the intended entry point for
 * other mods and data packs.
 */
public record OpenPlanAction(Optional<Holder<Plan>> plan) implements EntityAction {
    public static final MapCodec<OpenPlanAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Plan.CODEC.optionalFieldOf("plan").forGetter(OpenPlanAction::plan)
    ).apply(i, OpenPlanAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        if (ctx.entity() instanceof ServerPlayer player) {
            this.plan.ifPresentOrElse(plan -> SessionService.open(player, plan),
                    () -> SessionService.openCurrentOrAutomatic(player, SessionOpenSource.ACTION));
        }
    }

    @Override
    public @NonNull MapCodec<OpenPlanAction> codec() {
        return CODEC;
    }
}
