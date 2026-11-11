package com.iafenvoy.spb;

import com.iafenvoy.spb.registry.SpbAttachments;
import com.iafenvoy.spb.registry.SpbEntityActions;
import com.iafenvoy.spb.registry.SpbEntityConditions;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(StartingPointBuy.MOD_ID)
public class StartingPointBuy {
    public static final String MOD_ID = "spb";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StartingPointBuy(IEventBus bus) {
        SpbAttachments.REGISTRY.register(bus);
        SpbEntityActions.REGISTRY.register(bus);
        SpbEntityConditions.REGISTRY.register(bus);
    }
}
