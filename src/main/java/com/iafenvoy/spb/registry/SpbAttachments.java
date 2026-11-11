package com.iafenvoy.spb.registry;

import com.iafenvoy.spb.StartingPointBuy;
import com.iafenvoy.spb.runtime.session.SessionAttachment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The single player attachment holding the session and completion records. It is copied on death and
 * never synced as an attachment: the client receives explicit snapshots instead.
 */
public final class SpbAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTRY = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, StartingPointBuy.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SessionAttachment>> SESSION = REGISTRY.register("session", () -> AttachmentType.builder(SessionAttachment::new).serialize(SessionAttachment.CODEC).copyOnDeath().build());
}
