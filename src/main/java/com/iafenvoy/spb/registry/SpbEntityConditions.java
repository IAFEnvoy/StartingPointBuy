package com.iafenvoy.spb.registry;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.iafenvoy.spb.StartingPointBuy;
import com.iafenvoy.spb.data.condition.CompareEntityCondition;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * SPB content registered into Saturn's extension registries: the {@code spb:compare} entity
 * condition and the {@code spb:open_plan} entity action.
 */
@SuppressWarnings("unused")
public final class SpbEntityConditions {
    public static final DeferredRegister<MapCodec<? extends EntityCondition>> REGISTRY = DeferredRegister.create(SaturnRegistries.ENTITY_CONDITION_TYPE, StartingPointBuy.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<CompareEntityCondition>> COMPARE = REGISTRY.register("compare", () -> CompareEntityCondition.CODEC);
}
