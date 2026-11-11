package com.iafenvoy.spb.registry;

import com.iafenvoy.spb.StartingPointBuy;
import com.iafenvoy.spb.data.Group;
import com.iafenvoy.spb.data.Option;
import com.iafenvoy.spb.data.Plan;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Datapack registry keys owned by SPB. The identifiers are also the folder names used by data packs:
 * {@code data/<namespace>/spb/plan/<name>.json} and so on.
 */
public final class SpbResourceKeys {
    public static final ResourceKey<Registry<Plan>> PLAN = create("plan");
    public static final ResourceKey<Registry<Group>> GROUP = create("group");
    public static final ResourceKey<Registry<Option>> OPTION = create("option");

    private static <T> ResourceKey<Registry<T>> create(String path) {
        return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(StartingPointBuy.MOD_ID, path));
    }
}
