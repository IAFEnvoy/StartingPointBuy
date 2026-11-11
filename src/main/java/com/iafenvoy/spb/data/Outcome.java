package com.iafenvoy.spb.data;

import com.iafenvoy.saturn.data.ability.Ability;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.spb.util.DefinitionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.List;
import java.util.Optional;

/**
 * A conditional bundle of permanent abilities and delayed actions. Plan outcomes are evaluated once
 * against the committed result; option outcomes additionally require {@code q > 0}.
 */
public record Outcome(String key, Optional<EntityCondition> condition, List<Holder<Ability>> abilities,
                      List<OutcomeAction> actions) {
    public static final Codec<Outcome> CODEC = RecordCodecBuilder.create(i -> i.group(
            MiscCodecs.NOT_BLANK_STRING.fieldOf("key").forGetter(Outcome::key),
            EntityCondition.CODEC.optionalFieldOf("condition").forGetter(Outcome::condition),
            Ability.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(Outcome::abilities),
            DefinitionCodecs.uniqueList(OutcomeAction.CODEC, OutcomeAction::key, "action key").optionalFieldOf("actions", List.of()).forGetter(Outcome::actions)
    ).apply(i, Outcome::new));
}
