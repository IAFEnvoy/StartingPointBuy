package com.iafenvoy.spb.runtime.session;

import com.iafenvoy.spb.runtime.outcome.FrozenGrant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.List;
import java.util.UUID;

/**
 * The frozen result of a successful submit. Keeping the batch id makes repeated submit requests
 * idempotent and gives delayed tasks a stable identity.
 */
public record CommitRecord(UUID batchId, long gameTime, List<FrozenGrant> grants) {
    public static final Codec<CommitRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("batch_id").forGetter(CommitRecord::batchId),
            Codec.LONG.optionalFieldOf("game_time", 0L).forGetter(CommitRecord::gameTime),
            FrozenGrant.CODEC.listOf().optionalFieldOf("grants", List.of()).forGetter(CommitRecord::grants)
    ).apply(i, CommitRecord::new));
}
