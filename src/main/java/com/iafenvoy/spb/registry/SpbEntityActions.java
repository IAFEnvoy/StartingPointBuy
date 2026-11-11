package com.iafenvoy.spb.registry;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.iafenvoy.spb.StartingPointBuy;
import com.iafenvoy.spb.data.action.OpenPlanAction;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * SPB content registered into Saturn's extension registries: the {@code spb:compare} entity
 * condition and the {@code spb:open_plan} entity action.
 */
@SuppressWarnings("unused")
public final class SpbEntityActions {
    public static final DeferredRegister<MapCodec<? extends EntityAction>> REGISTRY = DeferredRegister.create(SaturnRegistries.ENTITY_ACTION_TYPE, StartingPointBuy.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<OpenPlanAction>> OPEN_PLAN = REGISTRY.register("open_plan", () -> OpenPlanAction.CODEC);
}
