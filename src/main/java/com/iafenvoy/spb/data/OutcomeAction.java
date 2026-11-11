package com.iafenvoy.spb.data;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A delayed Saturn entity action belonging to an outcome. {@code delay} and {@code repeat} are
 * evaluated and frozen at commit time; {@code repeat} is clamped to a non-negative integer and capped
 * by the task limit.
 */
public record OutcomeAction(String key, NumberProvider delay, NumberProvider repeat, EntityAction action) {
    public static final Codec<OutcomeAction> CODEC = RecordCodecBuilder.create(i -> i.group(
            MiscCodecs.NOT_BLANK_STRING.fieldOf("key").forGetter(OutcomeAction::key),
            NumberProvider.CODEC.optionalFieldOf("delay", new Constant(1.0)).forGetter(OutcomeAction::delay),
            NumberProvider.CODEC.optionalFieldOf("repeat", new Constant(1.0)).forGetter(OutcomeAction::repeat),
            EntityAction.CODEC.fieldOf("action").forGetter(OutcomeAction::action)
    ).apply(i, OutcomeAction::new));
}
