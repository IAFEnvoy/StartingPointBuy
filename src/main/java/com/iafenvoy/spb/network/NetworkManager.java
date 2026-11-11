package com.iafenvoy.spb.network;

import com.iafenvoy.spb.network.payload.IntentC2SPayload;
import com.iafenvoy.spb.network.payload.PreviewS2CPayload;
import com.iafenvoy.spb.network.payload.SnapshotS2CPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber
public final class NetworkManager {
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("4")
                .playToServer(IntentC2SPayload.TYPE, IntentC2SPayload.STREAM_CODEC, ServerNetworkHandler::onIntent)
                .playToClient(SnapshotS2CPayload.TYPE, SnapshotS2CPayload.STREAM_CODEC)
                .playToClient(PreviewS2CPayload.TYPE, PreviewS2CPayload.STREAM_CODEC);
    }
}
