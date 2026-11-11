package com.iafenvoy.spb.registry;

import com.iafenvoy.spb.data.Group;
import com.iafenvoy.spb.data.Option;
import com.iafenvoy.spb.data.Plan;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * Registers the three SPB datapack registries. They are intentionally not synced to clients: the
 * client only renders what the server puts into a session snapshot, so a dedicated server never needs
 * to push (or the client to decode) Saturn action trees. This keeps data-pack error handling on the
 * server and lets the snapshot carry stable display data.
 */
@EventBusSubscriber
public final class SpbDatapackRegistries {
    @SubscribeEvent
    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(SpbResourceKeys.PLAN, Plan.DIRECT_CODEC, Plan.DIRECT_CODEC);
        event.dataPackRegistry(SpbResourceKeys.GROUP, Group.DIRECT_CODEC, Group.DIRECT_CODEC);
        event.dataPackRegistry(SpbResourceKeys.OPTION, Option.DIRECT_CODEC, Option.DIRECT_CODEC);
    }
}
