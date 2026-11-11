package com.iafenvoy.spb.data;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * A prerequisite, exclusion or combination rule checked while the option is selected. Unlike
 * {@code enabled_condition} these rules may be temporarily violated and only block completion.
 */
public record ValidationRule(String key, EntityCondition condition, Optional<Component> message) {
    public static final Codec<ValidationRule> CODEC = RecordCodecBuilder.create(i -> i.group(
            MiscCodecs.NOT_BLANK_STRING.fieldOf("key").forGetter(ValidationRule::key),
            EntityCondition.CODEC.fieldOf("condition").forGetter(ValidationRule::condition),
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("message").forGetter(ValidationRule::message)
    ).apply(i, ValidationRule::new));

    public Component display() {
        return this.message.orElseGet(() -> Component.translatable("spb.rule.validation_failed", this.key));
    }
}
