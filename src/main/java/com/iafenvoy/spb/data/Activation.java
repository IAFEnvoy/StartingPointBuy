package com.iafenvoy.spb.data;

import com.iafenvoy.saturn.data.condition.AlwaysCondition;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * When a plan is offered automatically. The condition is evaluated against the player's real
 * resources and entity state, not against the session draft.
 */
public record Activation(boolean onFirstJoin, int priority, EntityCondition condition) {
    public static final Activation DEFAULT = new Activation(false, 0, AlwaysCondition.INSTANCE);

    public static final Codec<Activation> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("on_first_join", false).forGetter(Activation::onFirstJoin),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(Activation::priority),
            EntityCondition.optionalCodec("condition").forGetter(Activation::condition)
    ).apply(i, Activation::new));
}
