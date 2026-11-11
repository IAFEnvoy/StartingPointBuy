package com.iafenvoy.spb.network;

import com.iafenvoy.spb.network.model.SelectionIntent;
import com.iafenvoy.spb.network.payload.IntentC2SPayload;
import com.iafenvoy.spb.network.payload.PreviewS2CPayload;
import com.iafenvoy.spb.network.payload.SnapshotS2CPayload;
import com.iafenvoy.spb.screen.PlanScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client handlers for client-bound payloads.
 *
 * <p>This class is only loaded on the physical client: {@code StartingPointBuyClient} names these
 * handlers from a {@code Dist.CLIENT} subscriber, so a dedicated server never resolves the screen
 * classes referenced in the bodies below.
 */
public final class ClientNetworkHandler {
    private ClientNetworkHandler() {
    }

    public static void sendIntent(SelectionIntent intent) {
        ClientPacketDistributor.sendToServer(new IntentC2SPayload(intent));
    }

    public static void onSnapshot(SnapshotS2CPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PlanScreen screen) {
            if (screen.accept(payload.snapshot()) && !payload.openScreen()) screen.releaseFromServer();
        } else if (payload.openScreen()) {
            minecraft.setScreen(new PlanScreen(payload.snapshot()));
        }
    }

    public static void onPreview(PreviewS2CPayload payload, IPayloadContext context) {
        if (Minecraft.getInstance().screen instanceof PlanScreen screen) {
            screen.acceptPreview(payload.preview());
        }
    }
}
