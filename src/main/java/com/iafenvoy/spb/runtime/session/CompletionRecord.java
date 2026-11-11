package com.iafenvoy.spb.runtime.session;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * Marks that a player finished a plan in this world. Completions are checked by every entry point
 * (automatic login, command, action) so a plan is only played once by default.
 */
public record CompletionRecord(UUID batchId, long gameTime, boolean rewardsComplete) {
    public static final Codec<CompletionRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("batch_id").forGetter(CompletionRecord::batchId),
            Codec.LONG.optionalFieldOf("game_time", 0L).forGetter(CompletionRecord::gameTime),
            Codec.BOOL.optionalFieldOf("rewards_complete", false).forGetter(CompletionRecord::rewardsComplete)
    ).apply(i, CompletionRecord::new));
}
