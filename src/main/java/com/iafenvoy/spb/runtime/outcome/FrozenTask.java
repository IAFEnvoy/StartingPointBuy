package com.iafenvoy.spb.runtime.outcome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ExtraCodecs;

import java.util.Map;

/**
 * A frozen, delayed Saturn action. The action itself is stored as the NBT produced at commit time, so
 * a later data-pack change cannot swap in a different reward, and a missing dependency fails only this
 * task instead of the whole attachment.
 *
 * <p>Only data is stored: no entity, level or Java closure is persisted.
 */
public record FrozenTask(String id, String scope, String outcomeKey, String actionKey, int repeatIndex,
                         int delayRemaining, TaskState state, Tag payload, Map<String, Double> variables,
                         String failure) {
    public static final Codec<FrozenTask> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(FrozenTask::id),
            Codec.STRING.optionalFieldOf("scope", "").forGetter(FrozenTask::scope),
            Codec.STRING.optionalFieldOf("outcome", "").forGetter(FrozenTask::outcomeKey),
            Codec.STRING.optionalFieldOf("action", "").forGetter(FrozenTask::actionKey),
            Codec.INT.optionalFieldOf("repeat_index", 0).forGetter(FrozenTask::repeatIndex),
            Codec.INT.optionalFieldOf("delay_remaining", 0).forGetter(FrozenTask::delayRemaining),
            TaskState.CODEC.optionalFieldOf("state", TaskState.PENDING).forGetter(FrozenTask::state),
            ExtraCodecs.NBT.fieldOf("payload").forGetter(FrozenTask::payload),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("variables", Map.of()).forGetter(FrozenTask::variables),
            Codec.STRING.optionalFieldOf("failure", "").forGetter(FrozenTask::failure)
    ).apply(i, FrozenTask::new));

    public FrozenTask withState(TaskState newState) {
        return new FrozenTask(this.id, this.scope, this.outcomeKey, this.actionKey, this.repeatIndex,
                this.delayRemaining, newState, this.payload, this.variables, this.failure);
    }

    public FrozenTask withDelay(int newDelay) {
        return new FrozenTask(this.id, this.scope, this.outcomeKey, this.actionKey, this.repeatIndex,
                newDelay, this.state, this.payload, this.variables, this.failure);
    }

    public FrozenTask withFailure(String message) {
        return new FrozenTask(this.id, this.scope, this.outcomeKey, this.actionKey, this.repeatIndex,
                this.delayRemaining, this.state, this.payload, this.variables, message);
    }
}
