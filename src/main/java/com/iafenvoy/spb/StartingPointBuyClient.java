package com.iafenvoy.spb;

import com.iafenvoy.spb.network.ClientNetworkHandler;
import com.iafenvoy.spb.network.payload.PreviewS2CPayload;
import com.iafenvoy.spb.network.payload.SnapshotS2CPayload;
import com.iafenvoy.spb.screen.PlanScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Client-only entry points for screens and payload handlers. Common network registration never
 * resolves client handlers, so dedicated servers do not load GUI or render code.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class StartingPointBuyClient {
    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(SnapshotS2CPayload.TYPE, ClientNetworkHandler::onSnapshot);
        event.register(PreviewS2CPayload.TYPE, ClientNetworkHandler::onPreview);
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && event.getCurrentScreen() instanceof PlanScreen screen
                && screen.isCloseLocked() && event.getNewScreen() != screen
                && !(event.getNewScreen() instanceof TitleScreen)
                && !(event.getNewScreen() instanceof DisconnectedScreen)) event.setCanceled(true);
    }
}
