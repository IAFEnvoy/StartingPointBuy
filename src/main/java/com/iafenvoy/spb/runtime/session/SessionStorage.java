package com.iafenvoy.spb.runtime.session;

import com.iafenvoy.spb.registry.SpbAttachments;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.PlayerDataStorage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Attachment updates and durable, read-back-verified checkpoints before reward side effects.
 */
final class SessionStorage {
    private SessionStorage() {
    }

    static void save(ServerPlayer player) {
        player.setData(SpbAttachments.SESSION, SessionService.attachment(player));
    }

    static void persist(ServerPlayer player) {
        save(player);
        // Vanilla's save catches I/O failures internally. Verify our exact checkpoint on disk
        // before executing a frozen action, rather than assuming a void return means success.
        try {
            PlayerDataStorage storage = player.level().getServer().getPlayerList().getPlayerIo();
            Tag expected = SessionAttachment.CODEC.codec().encodeStart(
                    RegistryOps.create(NbtOps.INSTANCE, player.registryAccess()), SessionService.attachment(player)).getOrThrow();
            storage.save(player);
            Path path = storage.getPlayerDir().toPath().resolve(player.getStringUUID() + ".dat");
            try (FileChannel file = FileChannel.open(path, StandardOpenOption.WRITE)) {
                file.force(true);
            }
            Tag actual = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap())
                    .getCompoundOrEmpty("neoforge:attachments").get("spb:session");
            if (!expected.equals(actual)) throw new IOException("SPB checkpoint differs from saved player data");
        } catch (Exception exception) {
            throw new CheckpointException(exception);
        }
    }
}
