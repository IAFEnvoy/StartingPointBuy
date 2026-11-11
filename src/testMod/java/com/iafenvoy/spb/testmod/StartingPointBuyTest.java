package com.iafenvoy.spb.testmod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Development-only companion: examples, datapack fixtures and GameTests never enter the main jar. */
@Mod(StartingPointBuyTest.MOD_ID)
public final class StartingPointBuyTest {
    public static final String MOD_ID = "spb_test";

    public StartingPointBuyTest(IEventBus bus) {
        SelectionGameTests.register(bus);
    }
}
