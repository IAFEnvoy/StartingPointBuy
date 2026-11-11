package com.iafenvoy.spb.network.model;

import com.iafenvoy.saturn.data.IconReference;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

public record OptionLine(Identifier id, String groupKey, String key, Component name,
                         Optional<Component> description,
                         Optional<IconReference> icon, int quantity, int hardMax, int minCount, int maxCount,
                         boolean visible, boolean enabled, boolean repeatable, List<Component> issues,
                         List<ContributionLine> contributions, List<Component> effects) {
    public static final Codec<OptionLine> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(OptionLine::id),
            Codec.STRING.optionalFieldOf("group", "").forGetter(OptionLine::groupKey),
            Codec.STRING.fieldOf("key").forGetter(OptionLine::key),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(OptionLine::name),
            ComponentSerialization.CODEC.optionalFieldOf("description").forGetter(OptionLine::description),
            IconReference.CODEC.optionalFieldOf("icon").forGetter(OptionLine::icon),
            Codec.INT.optionalFieldOf("quantity", 0).forGetter(OptionLine::quantity),
            Codec.INT.optionalFieldOf("hard_max", 1).forGetter(OptionLine::hardMax),
            Codec.INT.optionalFieldOf("min_count", 1).forGetter(OptionLine::minCount),
            Codec.INT.optionalFieldOf("max_count", 1).forGetter(OptionLine::maxCount),
            Codec.BOOL.optionalFieldOf("visible", true).forGetter(OptionLine::visible),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(OptionLine::enabled),
            Codec.BOOL.optionalFieldOf("repeatable", false).forGetter(OptionLine::repeatable),
            ComponentSerialization.CODEC.listOf().optionalFieldOf("issues", List.of()).forGetter(OptionLine::issues),
            ContributionLine.CODEC.listOf().optionalFieldOf("contributions", List.of()).forGetter(OptionLine::contributions),
            ComponentSerialization.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(OptionLine::effects)
    ).apply(i, OptionLine::new));
}
