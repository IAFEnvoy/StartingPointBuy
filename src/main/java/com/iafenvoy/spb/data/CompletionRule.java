package com.iafenvoy.spb.data;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * A mandatory condition for finishing a plan. All completion rules must pass; the message is shown
 * when the rule fails.
 */
public record CompletionRule(String key, EntityCondition condition, Optional<Component> message) {
    public static final Codec<CompletionRule> CODEC = RecordCodecBuilder.create(i -> i.group(
            MiscCodecs.NOT_BLANK_STRING.fieldOf("key").forGetter(CompletionRule::key),
            EntityCondition.CODEC.fieldOf("condition").forGetter(CompletionRule::condition),
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("message").forGetter(CompletionRule::message)
    ).apply(i, CompletionRule::new));

    public Component display() {
        return this.message.orElseGet(() -> Component.translatable("spb.rule.completion_failed", this.key));
    }
}
